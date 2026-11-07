package Project.ThemeCreator;

import Project.MainMenu.MenuTheme;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.util.stream.Stream;
import javax.tools.*;

public final class ThemeLibrary {
    //region Variables
    static { ThemeGpu.warmUp(); }
    private static final java.util.concurrent.ExecutorService FRAMES = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread th = new Thread(r, "theme-frames");
        th.setDaemon(true);
        return th;
    });
    private static final double TICK = 1 / 60.0;
    //endregion

    //region Constructors
    private ThemeLibrary() {}
    //endregion

    //region Theme Listing
    public static List<MenuTheme> userThemes() {
        List<MenuTheme> out = new ArrayList<>(javaThemes());
        out.addAll(savedThemes());
        return out;
    }
    public static List<MenuTheme> savedThemes() {
        List<MenuTheme> out = new ArrayList<>();
        for (String id : ThemeStore.ids()) {
            try { out.add(new Adapter(ThemeStore.load(id))); }
            catch (IOException | RuntimeException exception) { System.err.println("Theme skipped: " + id + " (" + exception + ")"); }
        }
        out.sort(Comparator.comparingInt(MenuTheme::order).thenComparing(MenuTheme::id));
        return out;
    }
    public static List<MenuTheme> javaThemes() {
        List<MenuTheme> out = new ArrayList<>();
        Path folder = ThemeStore.folder();
        if (!Files.isDirectory(folder)) return out;
        List<URL> roots = new ArrayList<>();
        List<String> names = new ArrayList<>();
        try {
            Path build = Files.createTempDirectory("rcjms-themes");
            build.toFile().deleteOnExit();
            int n = 0;
            for (Path src : listed(folder, ".java")) {
                Path dir = build.resolve("j" + n++);
                Files.createDirectories(dir);
                if (!compile(src, dir)) continue;
                roots.add(dir.toUri().toURL());
                names.addAll(classNames(dir));
            }
            roots.add(folder.toUri().toURL());
            names.addAll(classNames(folder));
            for (Path jar : listed(folder, ".jar")) {
                roots.add(jar.toUri().toURL());
                names.addAll(jarClassNames(jar));
            }
        } catch (IOException exception) {
            System.err.println("Theme folder not read: " + exception);
            return out;
        }
        try (URLClassLoader loader = new URLClassLoader(roots.toArray(URL[]::new), ThemeLibrary.class.getClassLoader())) {
            for (String name : names) {
                try {
                    Class<?> type = Class.forName(name, true, loader);
                    if (!MenuTheme.class.isAssignableFrom(type) || type.isInterface()
                            || java.lang.reflect.Modifier.isAbstract(type.getModifiers())) continue;
                    out.add((MenuTheme) type.getDeclaredConstructor().newInstance());
                } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
                    System.err.println("Java theme skipped: " + name + " (" + exception + ")");
                }
            }
        } catch (IOException exception) {
            System.err.println("Java themes not loaded: " + exception);
        }
        out.sort(Comparator.comparingInt(MenuTheme::order).thenComparing(MenuTheme::id));
        return out;
    }
    private static List<Path> listed(Path folder, String ext) throws IOException {
        List<Path> out = new ArrayList<>();
        try (Stream<Path> files = Files.list(folder)) { files.filter(path -> path.getFileName().toString().endsWith(ext)).sorted().forEach(out::add); }
        return out;
    }
    //endregion

    //region Java Compilation
    private static boolean compile(Path src, Path dir) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            System.err.println("Java theme skipped: " + src.getFileName() + " (no JDK to compile it)");
            return false;
        }
        ByteArrayOutputStream log = new ByteArrayOutputStream();
        String cp = System.getProperty("java.class.path", "");
        int code = compiler.run(null, null, new PrintStream(log, true, java.nio.charset.StandardCharsets.UTF_8),
                "-d", dir.toString(), "-cp", cp, "-proc:none", "-nowarn", src.toString());
        if (code != 0) {
            System.err.println("Java theme not compiled: " + src.getFileName() + "\n" + log);
            return false;
        }
        return true;
    }
    private static List<String> classNames(Path root) throws IOException {
        List<String> out = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path path : (Iterable<Path>) files::iterator) {
                String rel = root.relativize(path).toString().replace(File.separatorChar, '/');
                if (!rel.endsWith(".class") || rel.contains("$")) continue;
                out.add(rel.substring(0, rel.length() - ".class".length()).replace('/', '.'));
            }
        }
        return out;
    }
    private static List<String> jarClassNames(Path jar) throws IOException {
        List<String> out = new ArrayList<>();
        try (JarFile file = new JarFile(jar.toFile())) {
            for (JarEntry jarEntry : Collections.list(file.entries())) {
                String name = jarEntry.getName();
                if (!name.endsWith(".class") || name.contains("$") || name.startsWith("META-INF/")) continue;
                out.add(name.substring(0, name.length() - ".class".length()).replace('/', '.'));
            }
        }
        return out;
    }
    //endregion

    //region Theme Adapter
    public static final class Adapter implements MenuTheme {
        private final ThemeDoc doc;
        private java.util.concurrent.Future<ThemeRenderer.Frame> ahead;
        private double aheadTime = Double.NaN;
        Adapter(ThemeDoc doc) { this.doc = doc; }
        @Override public String id() { return doc.id; }
        @Override public String nameKey() { return doc.nameKey != null ? doc.nameKey : doc.name; }
        @Override public int order() { return doc.order; }
        @Override public void paintBase(java.awt.Graphics2D g2d, int w, int h, double t) {
            ThemeRenderer.Frame frame = null;
            if (ahead != null && Math.abs(aheadTime - t) < TICK / 2) {
                try { frame = ahead.get(); }
                catch (InterruptedException exception) {Thread.currentThread().interrupt(); }
                catch (java.util.concurrent.ExecutionException _) {}
            }
            if (frame == null) frame = ThemeRenderer.frame(doc, t);
            double next = t + TICK;
            ahead = FRAMES.submit(() -> ThemeRenderer.frame(doc, next));
            aheadTime = next;
            ThemeRenderer.paint(g2d, frame, w, h);
        }
    }
    //endregion
}
