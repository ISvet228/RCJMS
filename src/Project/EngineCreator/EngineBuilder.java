package Project.EngineCreator;
import Project.CreatorKit.*;

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
    //region Variables
    //region Folders
    static final String FOLDER = "Mods/ExternalEngines";
    static final String PROJECTS = "_projects";
    //endregion
    //endregion

    //region Constructors
    private EngineBuilder() {}
    //endregion

    //region Folders
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
        for (Path path : bases) if (Files.isDirectory(path.resolve(FOLDER))) return path.resolve(FOLDER);
        return bases.getFirst().resolve(FOLDER);
    }
    //endregion

    //region Build Pipeline
    //region Building
    static Result build(CkProject ckProject, boolean save, Predicate<Path> allowOverwrite) {
        List<Issue> issues = new ArrayList<>(Validator.validate(ckProject));
        List<String> msg = new ArrayList<>();
        if (issues.stream().anyMatch(Issue::error)) return new Result(false, issues, msg, null);

        CkProject flat;
        try { flat = CustomNodes.flatten(ckProject); }
        catch (IllegalStateException | IllegalArgumentException exception) { issues.add(err(-1, L.f("ec.b.checkFailed", exception.getMessage()))); return new Result(false, issues, msg, null); }
        String java = CodeGen.javaSource(flat);
        String cl = ckProject.openCl ? CodeGen.clSource(flat) : null;

        Path tmp = null;
        try {
            tmp = Files.createTempDirectory("engine-check");
            // the engine reads its kernel from Ext_<name>.cl beside the class, so the check needs the file too
            if (cl != null) Files.writeString(tmp.resolve(CodeGen.className(ckProject) + ".cl"), cl, StandardCharsets.UTF_8);
            List<String> compileErrors = compile(CodeGen.className(ckProject), java, tmp);
            if (compileErrors == null) { msg.add(L.t("ec.b.noCompiler")); }
            else if (!compileErrors.isEmpty()) {
                for (String e : compileErrors) issues.add(err(-1, L.f("ec.b.compileError", e)));
                return new Result(false, issues, msg, null);
            } else {
                msg.add(L.t("ec.b.compileOk"));
                issues.addAll(smokeTest(ckProject, tmp));
                issues.addAll(checkKernels(ckProject, cl));
            }
        } catch (IOException exception) { issues.add(err(-1, L.f("ec.b.checkFailed", exception.getMessage()))); }
        finally { deleteQuietly(tmp); }

        if (issues.stream().anyMatch(Issue::error)) return new Result(false, issues, msg, null);
        if (!save) return new Result(false, issues, msg, null);

        Path target = javaFileFor(ckProject);
        if (Files.exists(target) && !allowOverwrite.test(target)) {
            msg.add(L.t("ec.b.cancelled"));
            return new Result(false, issues, msg, target);
        }
        try {
            Path dir = folder();
            Files.createDirectories(dir);
            Files.writeString(target, java, StandardCharsets.UTF_8);
            Path clFile = dir.resolve(CodeGen.className(ckProject) + ".cl");
            if (cl != null) Files.writeString(clFile, cl, StandardCharsets.UTF_8);
            else Files.deleteIfExists(clFile);
            Path projects = dir.resolve(PROJECTS);
            Files.createDirectories(projects);
            Files.writeString(projects.resolve(ckProject.name + ".engine.json"), ProjectIO.toJson(ckProject), StandardCharsets.UTF_8);
            msg.add(L.f("ec.b.saved", dir.toString()));
            RenderEngines.reload();
            return new Result(true, issues, msg, target);
        } catch (IOException exception) {
            issues.add(err(-1, L.f("ec.b.writeFailed", exception.getMessage())));
            return new Result(false, issues, msg, null);
        }
    }
    static PreviewBuild buildForPreview(CkProject ckProject) {
        List<Issue> issues = new ArrayList<>(Validator.validate(ckProject));
        if (issues.stream().anyMatch(Issue::error)) return new PreviewBuild(null, issues, L.t("ec.ui.previewFix"));
        try {
            CkProject flat = CustomNodes.flatten(ckProject);
            Path dir = Files.createTempDirectory("engine-preview");
            if (ckProject.openCl) Files.writeString(dir.resolve(CodeGen.className(ckProject) + ".cl"), CodeGen.clSource(flat), StandardCharsets.UTF_8);
            List<String> errors = compile(CodeGen.className(ckProject), CodeGen.javaSource(flat), dir);
            if (errors == null) return new PreviewBuild(null, issues, L.t("ec.b.noCompiler"));
            if (!errors.isEmpty()) return new PreviewBuild(null, issues, L.f("ec.b.compileError", String.join("; ", errors)));
            URLClassLoader loader = new URLClassLoader(new URL[]{dir.toUri().toURL()}, EngineBuilder.class.getClassLoader());
            RenderEngine engine = (RenderEngine) Class.forName(CodeGen.className(ckProject), true, loader).getDeclaredConstructor().newInstance();
            return new PreviewBuild(engine, issues, null);
        } catch (Throwable exception) { return new PreviewBuild(null, issues, L.f("ec.b.smokeError", exception.toString())); }
    }
    static Path javaFileFor(CkProject ckProject) { return folder().resolve(CodeGen.className(ckProject) + ".java"); }
    static RenderFrame testFrame() {
        RenderFrame renderFrame = new RenderFrame();
        int w = 64, h = 40;
        renderFrame.renderWidth = w;
        renderFrame.renderHeight = h;
        renderFrame.renderPixels = new int[w * h];
        renderFrame.quality = 1;
        renderFrame.cameraAngle = 0.4;
        renderFrame.cameraHeight = 0.5;
        renderFrame.cameraPitch = 0;
        renderFrame.playerX = 2.5;
        renderFrame.playerY = 2.5;
        int[][] map = new int[10][10];
        for (int i = 0; i < 10; i++) {
            map[0][i] = 1; map[9][i] = 1; map[i][0] = 1; map[i][9] = 1;
        }
        map[5][3] = 2;
        map[3][6] = 1;
        renderFrame.map = map;
        renderFrame.wallTexture = texture(4, 4, 0x80);
        renderFrame.floorTexture = texture(4, 4, 0x40);
        renderFrame.ceilingTexture = texture(4, 4, 0x20);
        renderFrame.finishTexture = texture(4, 4, 0xC0);
        return renderFrame;
    }
    //endregion
    //region Checks
    private static List<Issue> smokeTest(CkProject ckProject, Path dir) {
        List<Issue> r = new ArrayList<>();
        try (URLClassLoader loader = new URLClassLoader(new URL[]{dir.toUri().toURL()}, EngineBuilder.class.getClassLoader())) {
            Class<?> type = Class.forName(CodeGen.className(ckProject), true, loader);
            RenderEngine engine = (RenderEngine) type.getDeclaredConstructor().newInstance();
            RenderFrame frame = testFrame();
            engine.render(frame);
            Set<Integer> colors = new HashSet<>();
            for (int c : frame.renderPixels) colors.add(c);
            if (colors.size() <= 1) r.add(new Issue(false, -1, L.t("ec.b.flat")));
            else r.add(new Issue(false, -1, L.f("ec.b.smokeOk", colors.size())));
        } catch (Throwable exception) { r.add(err(-1, L.f("ec.b.smokeError", exception.toString()))); }
        return r;
    }
    private static List<Issue> checkKernels(CkProject ckProject, String clSource) {
        List<Issue> r = new ArrayList<>();
        if (!ckProject.openCl || clSource == null) return r;
        long until = System.currentTimeMillis() + 4000;
        while (Project.OpenCl.GpuRuntime.healthFor(ckProject.name) == Project.OpenCl.GpuRuntime.Health.PROBING && System.currentTimeMillis() < until)
            try { Thread.sleep(100); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); break; }
        switch (Project.OpenCl.GpuRuntime.healthFor(ckProject.name)) {
            case READY -> {
                for (int k = 0; k < ckProject.passCount(); k++) {
                    try { new Project.OpenCl.GpuProgram(clSource, CodeGen.kernelName(ckProject, k)).close(); }
                    catch (RuntimeException exception) {
                        r.add(err(-1, L.f("ec.b.kernelError", exception.getMessage())));
                        return r;
                    }
                }
                r.add(new Issue(false, -1, L.t("ec.b.kernelOk")));
            }
            case UNAVAILABLE -> r.add(new Issue(false, -1, L.f("ec.b.noOpenCl", Project.OpenCl.GpuRuntime.reasonFor(ckProject.name))));
            case PROBING -> r.add(new Issue(false, -1, L.t("ec.b.probing")));
            case OFF -> r.add(new Issue(false, -1, L.t("ec.b.clOff")));
        }
        return r;
    }
    private static RuntimeTexture texture(int w, int h, int seed) {
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                px[y * w + x] = 0xFF000000 | ((seed + x * 40) & 255) << 16 | ((seed + y * 50) & 255) << 8 | (x + y) * 20 & 255;
        return new RuntimeTexture(w, h, px);
    }
    private static Issue err(int node, String text) { return new Issue(true, node, text); }
    //endregion
    //region Compilation
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
    private static void deleteQuietly(Path dir) {
        if (dir == null) return;
        try (var walk = Files.walk(dir)) { walk.sorted(Comparator.reverseOrder()).forEach(path -> { try { Files.deleteIfExists(path); } catch (IOException ignored) { } }); }
        catch (IOException ignored) { }
    }
    //endregion
    //endregion

    //region Types
    record Result(boolean saved, List<Issue> issues, List<String> messages, Path file) {
    boolean hasErrors() { return issues.stream().anyMatch(Issue::error); }
    }
    record PreviewBuild(RenderEngine engine, List<Issue> issues, String error) {

    }
    //endregion
}