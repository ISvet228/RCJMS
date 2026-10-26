package Project.EngineCreator;

import Project.Game.*;
import Project.Helpers.RuntimeTexture;

import javax.tools.*;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Predicate;

/** Validates, generates, test-compiles, smoke-runs and saves an engine into Mods/ExternalEngines. */
public final class EngineBuilder {
    static final String FOLDER = "Mods/ExternalEngines";
    static final String PROJECTS = "_projects";

    record Result(boolean saved, List<Issue> issues, List<String> messages, Path file) {
        boolean hasErrors() { return issues.stream().anyMatch(Issue::error); }
    }

    private EngineBuilder() {}

    static Path folder() {
        List<Path> bases = new ArrayList<>();
        String app = System.getProperty("jpackage.app-path");
        if (app != null && !app.isBlank()) {
            Path parent = Paths.get(app).toAbsolutePath().getParent();
            if (parent != null) bases.add(parent);
        }
        try {
            var src = EngineBuilder.class.getProtectionDomain().getCodeSource();
            if (src != null) {
                Path loc = Paths.get(src.getLocation().toURI());
                if (Files.isRegularFile(loc) && loc.getParent() != null) bases.add(loc.toAbsolutePath().getParent());
            }
        } catch (Exception ignored) { }
        bases.add(Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath());
        for (Path b : bases) if (Files.isDirectory(b.resolve(FOLDER))) return b.resolve(FOLDER);
        return bases.getFirst().resolve(FOLDER);
    }

    static Path javaFileFor(EcProject p) { return folder().resolve(CodeGen.className(p) + ".java"); }

    static Result build(EcProject p, boolean save, Predicate<Path> allowOverwrite) {
        List<Issue> issues = new ArrayList<>(Validator.validate(p));
        List<String> msg = new ArrayList<>();
        if (issues.stream().anyMatch(Issue::error)) return new Result(false, issues, msg, null);

        String java = CodeGen.javaSource(p);
        String cl = p.openCl ? CodeGen.clSource(p) : null;

        Path tmp = null;
        try {
            tmp = Files.createTempDirectory("engine-check");
            // the engine reads its kernel from Ext_<name>.cl beside the class, so the check needs the file too
            if (cl != null) Files.writeString(tmp.resolve(CodeGen.className(p) + ".cl"), cl, StandardCharsets.UTF_8);
            List<String> compileErrors = compile(CodeGen.className(p), java, tmp);
            if (compileErrors == null) { msg.add(L.t("ec.b.noCompiler")); }
            else if (!compileErrors.isEmpty()) {
                for (String e : compileErrors) issues.add(err(-1, L.f("ec.b.compileError", e)));
                return new Result(false, issues, msg, null);
            } else {
                msg.add(L.t("ec.b.compileOk"));
                issues.addAll(smokeTest(p, tmp));
                issues.addAll(checkKernels(p, cl));
            }
        } catch (IOException e) { issues.add(err(-1, L.f("ec.b.checkFailed", e.getMessage()))); }
        finally { deleteQuietly(tmp); }

        if (issues.stream().anyMatch(Issue::error)) return new Result(false, issues, msg, null);
        if (!save) return new Result(false, issues, msg, null);

        Path target = javaFileFor(p);
        if (Files.exists(target) && !allowOverwrite.test(target)) {
            msg.add(L.t("ec.b.cancelled"));
            return new Result(false, issues, msg, target);
        }
        try {
            Path dir = folder();
            Files.createDirectories(dir);
            Files.writeString(target, java, StandardCharsets.UTF_8);
            Path clFile = dir.resolve(CodeGen.className(p) + ".cl");
            if (cl != null) Files.writeString(clFile, cl, StandardCharsets.UTF_8);
            else Files.deleteIfExists(clFile);
            Path projects = dir.resolve(PROJECTS);
            Files.createDirectories(projects);
            Files.writeString(projects.resolve(p.name + ".engine.json"), ProjectIO.toJson(p), StandardCharsets.UTF_8);
            msg.add(L.f("ec.b.saved", dir.toString()));
            RenderEngines.reload();
            return new Result(true, issues, msg, target);
        } catch (IOException e) {
            issues.add(err(-1, L.f("ec.b.writeFailed", e.getMessage())));
            return new Result(false, issues, msg, null);
        }
    }
    private static List<String> compile(String cls, String source, Path dir) throws IOException {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        if (javac == null) return null;
        Path file = dir.resolve(cls + ".java");
        Files.writeString(file, source, StandardCharsets.UTF_8);
        List<String> errors = new ArrayList<>();
        DiagnosticCollector<JavaFileObject> diags = new DiagnosticCollector<>();
        try (StandardJavaFileManager fm = javac.getStandardFileManager(diags, null, StandardCharsets.UTF_8)) {
            String level = String.valueOf(Runtime.version().feature());
            List<String> opts = List.of("--enable-preview", "-source", level, "-target", level,
                    "-classpath", System.getProperty("java.class.path", ""),
                    "-encoding", "UTF-8", "-proc:none", "-nowarn", "-d", dir.toString());
            boolean ok = javac.getTask(null, fm, diags, opts, null, fm.getJavaFileObjects(file.toFile())).call();
            if (!ok) {
                for (Diagnostic<? extends JavaFileObject> d : diags.getDiagnostics())
                    if (d.getKind() == Diagnostic.Kind.ERROR)
                        errors.add(L.f("ec.b.line", d.getLineNumber(), d.getMessage(Locale.ROOT)));
                if (errors.isEmpty()) errors.add("?");
            }
        }
        return errors;
    }
    private static List<Issue> smokeTest(EcProject p, Path dir) {
        List<Issue> r = new ArrayList<>();
        try (URLClassLoader loader = new URLClassLoader(new URL[]{dir.toUri().toURL()}, EngineBuilder.class.getClassLoader())) {
            Class<?> type = Class.forName(CodeGen.className(p), true, loader);
            RenderEngine engine = (RenderEngine) type.getDeclaredConstructor().newInstance();
            RenderFrame frame = testFrame();
            engine.render(frame);
            Set<Integer> colors = new HashSet<>();
            for (int c : frame.renderPixels) colors.add(c);
            if (colors.size() <= 1) r.add(new Issue(false, -1, L.t("ec.b.flat")));
            else r.add(new Issue(false, -1, L.f("ec.b.smokeOk", colors.size())));
        } catch (Throwable t) { r.add(err(-1, L.f("ec.b.smokeError", t.toString()))); }
        return r;
    }
    private static List<Issue> checkKernels(EcProject p, String clSource) {
        List<Issue> r = new ArrayList<>();
        if (!p.openCl || clSource == null) return r;
        long until = System.currentTimeMillis() + 4000;
        while (Project.OpenCl.GpuRuntime.healthFor(p.name) == Project.OpenCl.GpuRuntime.Health.PROBING && System.currentTimeMillis() < until)
            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        switch (Project.OpenCl.GpuRuntime.healthFor(p.name)) {
            case READY -> {
                for (int k = 0; k < p.passCount(); k++) {
                    try { new Project.OpenCl.GpuProgram(clSource, CodeGen.kernelName(p, k)).close(); }
                    catch (RuntimeException e) {
                        r.add(err(-1, L.f("ec.b.kernelError", e.getMessage())));
                        return r;
                    }
                }
                r.add(new Issue(false, -1, L.t("ec.b.kernelOk")));
            }
            case UNAVAILABLE -> r.add(new Issue(false, -1, L.f("ec.b.noOpenCl", Project.OpenCl.GpuRuntime.reasonFor(p.name))));
            case PROBING -> r.add(new Issue(false, -1, L.t("ec.b.probing")));
            case OFF -> r.add(new Issue(false, -1, L.t("ec.b.clOff")));
        }
        return r;
    }

    record PreviewBuild(RenderEngine engine, List<Issue> issues, String error) {}

    static PreviewBuild buildForPreview(EcProject p) {
        List<Issue> issues = new ArrayList<>(Validator.validate(p));
        if (issues.stream().anyMatch(Issue::error)) return new PreviewBuild(null, issues, L.t("ec.ui.previewFix"));
        try {
            Path dir = Files.createTempDirectory("engine-preview");
            if (p.openCl) Files.writeString(dir.resolve(CodeGen.className(p) + ".cl"), CodeGen.clSource(p), StandardCharsets.UTF_8);
            List<String> errors = compile(CodeGen.className(p), CodeGen.javaSource(p), dir);
            if (errors == null) return new PreviewBuild(null, issues, L.t("ec.b.noCompiler"));
            if (!errors.isEmpty()) return new PreviewBuild(null, issues, L.f("ec.b.compileError", String.join("; ", errors)));
            URLClassLoader loader = new URLClassLoader(new URL[]{dir.toUri().toURL()}, EngineBuilder.class.getClassLoader());
            RenderEngine engine = (RenderEngine) Class.forName(CodeGen.className(p), true, loader).getDeclaredConstructor().newInstance();
            return new PreviewBuild(engine, issues, null);
        } catch (Throwable t) { return new PreviewBuild(null, issues, L.f("ec.b.smokeError", t.toString())); }
    }

    static RenderFrame testFrame() {
        RenderFrame f = new RenderFrame();
        int w = 64, h = 40;
        f.renderWidth = w;
        f.renderHeight = h;
        f.renderPixels = new int[w * h];
        f.quality = 1;
        f.cameraAngle = 0.4;
        f.cameraHeight = 0.5;
        f.cameraPitch = 0;
        f.playerX = 2.5;
        f.playerY = 2.5;
        int[][] map = new int[10][10];
        for (int i = 0; i < 10; i++) {
            map[0][i] = 1; map[9][i] = 1; map[i][0] = 1; map[i][9] = 1;
        }
        map[5][3] = 2;
        map[3][6] = 1;
        f.map = map;
        f.wallTexture = texture(4, 4, 0x80);
        f.floorTexture = texture(4, 4, 0x40);
        f.ceilingTexture = texture(4, 4, 0x20);
        f.finishTexture = texture(4, 4, 0xC0);
        return f;
    }

    private static RuntimeTexture texture(int w, int h, int seed) {
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                px[y * w + x] = 0xFF000000 | ((seed + x * 40) & 255) << 16 | ((seed + y * 50) & 255) << 8 | (x + y) * 20 & 255;
        return new RuntimeTexture(w, h, px);
    }

    private static Issue err(int node, String text) { return new Issue(true, node, text); }
    private static void deleteQuietly(Path dir) {
        if (dir == null) return;
        try (var walk = Files.walk(dir)) { walk.sorted(Comparator.reverseOrder()).forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) { } }); }
        catch (IOException ignored) { }
    }
}