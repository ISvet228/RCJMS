package GameViewComponents;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

final class ExternalEngines {
    static final String FOLDER = "ExternalEngines";
    static final String PREFIX = "_External_";
    private static final String ERROR_FILE = "errors.txt";
    private static final String BUILD_DIR = ".build";
    private static final Pattern PACKAGE = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);

    private ExternalEngines() {}

    static List<RenderEngine> load() {
        List<RenderEngine> result = new ArrayList<>();
        for (Path folder : folders()) {
            List<String> problems = new ArrayList<>();
            List<Path> sources = javaSources(folder, problems);
            if (!sources.isEmpty()) loadFolder(folder, sources, result, problems);
            reportProblems(folder, problems);
        }
        return result;
    }

    //region Locations
    static List<Path> folders() {
        Set<Path> bases = new LinkedHashSet<>();
        String app = System.getProperty("jpackage.app-path");
        if (app != null && !app.isBlank()) {
            Path parent = Paths.get(app).toAbsolutePath().getParent();
            if (parent != null) bases.add(parent);
        }
        Path jar = runningJar();
        if (jar != null) bases.add(jar.getParent());
        if (bases.isEmpty()) bases.add(Paths.get(System.getProperty("user.dir")).toAbsolutePath());
        List<Path> folders = new ArrayList<>();
        for (Path base : bases) {
            Path folder = base.resolve(FOLDER);
            if (Files.isDirectory(folder)) folders.add(folder);
        }
        return folders;
    }
    private static Path runningJar() {
        try {
            var source = ExternalEngines.class.getProtectionDomain().getCodeSource();
            if (source == null) return null;
            Path location = Paths.get(source.getLocation().toURI());
            return Files.isRegularFile(location) ? location.toAbsolutePath() : null;
        } catch (URISyntaxException | RuntimeException e) { return null; }
    }
    private static List<Path> javaSources(Path folder, List<String> problems) {
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".java"))
                    .sorted().toList();
        } catch (IOException e) {
            problems.add("cannot read folder: " + e.getMessage());
            return List.of();
        }
    }
    //endregion

    //region Compile and load
    private static void loadFolder(Path folder, List<Path> sources, List<RenderEngine> out, List<String> problems) {
        if (!compilerPresent()) {
            problems.add("this runtime has no Java compiler (include the jdk.compiler module); " + sources.size() + " script(s) skipped");
            return;
        }
        Path build = prepareBuildDir(folder, problems);
        if (build == null) return;
        List<Path> compiled = ScriptCompiler.compile(sources, build, absoluteClasspath(), problems);
        try {
            URLClassLoader loader = new URLClassLoader(new URL[]{build.toUri().toURL()}, ExternalEngines.class.getClassLoader());
            for (Path source : compiled) register(loader, qualifiedName(source), out, problems);
        } catch (IOException e) { problems.add("cannot load compiled scripts: " + e.getMessage()); }
    }
    private static boolean compilerPresent() {
        try { return ScriptCompiler.available(); }
        catch (LinkageError e) { return false; }
    }
    private static void register(ClassLoader loader, String className, List<RenderEngine> out, List<String> problems) {
        try {
            Class<?> type = Class.forName(className, true, loader);
            if (type.getClassLoader() != loader) {
                problems.add(className + ": the name is already used by a built-in class; rename the class");
                return;
            }
            if (!RenderEngine.class.isAssignableFrom(type) || type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
                problems.add(className + ": does not implement RenderEngine");
                return;
            }
            RenderEngine engine = (RenderEngine) type.getDeclaredConstructor().newInstance();
            out.add(new Prefixed(engine));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) { problems.add(className + ": " + e); }
    }
    private static Path prepareBuildDir(Path folder, List<String> problems) {
        try {
            Path build = folder.resolve(BUILD_DIR);
            deleteTree(build);
            Files.createDirectories(build);
            return build;
        } catch (IOException e) {
            try {
                Path temp = Files.createTempDirectory("rcjms-external-engines");
                temp.toFile().deleteOnExit();
                return temp;
            } catch (IOException ex) {
                problems.add("cannot create a build folder: " + ex.getMessage());
                return null;
            }
        }
    }
    private static String absoluteClasspath() {
        StringJoiner joiner = new StringJoiner(java.io.File.pathSeparator);
        for (String entry : System.getProperty("java.class.path", "").split(java.io.File.pathSeparator))
            if (!entry.isBlank()) joiner.add(Paths.get(entry).toAbsolutePath().toString());
        return joiner.toString();
    }
    private static String qualifiedName(Path source) {
        String simple = source.getFileName().toString();
        simple = simple.substring(0, simple.length() - ".java".length());
        try {
            var matcher = PACKAGE.matcher(Files.readString(source, StandardCharsets.UTF_8));
            return matcher.find() ? matcher.group(1) + "." + simple : simple;
        } catch (IOException e) { return simple; }
    }
    //endregion

    //region Reporting
    private static void reportProblems(Path folder, List<String> problems) {
        Path file = folder.resolve(ERROR_FILE);
        try {
            if (problems.isEmpty()) { Files.deleteIfExists(file); return; }
            for (String problem : problems) System.err.println("External engine: " + problem);
            Files.write(file, problems, StandardCharsets.UTF_8);
        } catch (IOException e) { for (String problem : problems) System.err.println("External engine: " + problem); }
    }
    private static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) { for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p); }
    }
    //endregion

    static final class Prefixed implements RenderEngine {
        private final RenderEngine inner;
        Prefixed(RenderEngine inner) { this.inner = inner; }
        @Override public String id() { return PREFIX + inner.id(); }
        @Override public String displayName() { return PREFIX + inner.displayName(); }
        @Override public int order() { return 1000 + inner.order(); }
        @Override public String hintKey() { return inner.hintKey(); }
        @Override public String qualityHintKey() { return inner.qualityHintKey(); }
        @Override public boolean hasHyperbolicGeometry() { return inner.hasHyperbolicGeometry(); }
        @Override public boolean supportsMapSize(int width, int height) { return inner.supportsMapSize(width, height); }
        @Override public void prepare(RenderFrame frame) { inner.prepare(frame); }
        @Override public void render(RenderFrame frame) { inner.render(frame); }
    }
}