package GameViewComponents;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;
import java.util.stream.Stream;

public final class RenderEngines {
    private static final String ENGINE_PACKAGE = "Engines";
    private static volatile List<RenderEngine> engines;

    private RenderEngines() {}

    //region Public API
    public static List<RenderEngine> all() {
        List<RenderEngine> list = engines;
        if (list == null) {
            synchronized (RenderEngines.class) {
                if (engines == null) engines = discover();
                list = engines;
            }
        }
        return list;
    }
    public static RenderEngine find(String id) {
        List<RenderEngine> list = all();
        if (list.isEmpty()) return null;
        for (RenderEngine e : list) if (e.id().equals(id)) return e;
        return list.getFirst();
    }
    public static RenderEngine forMapSize(String id, int width, int height) {
        List<RenderEngine> list = all();
        if (list.isEmpty()) return null;
        int start = list.indexOf(find(id));
        for (int i = 0; i < list.size(); i++) {
            RenderEngine candidate = list.get((start + i) % list.size());
            if (candidate.supportsMapSize(width, height)) return candidate;
        }
        return null;
    }
    public static String defaultId() {
        List<RenderEngine> list = all();
        return list.isEmpty() ? "" : list.getFirst().id();
    }
    public static int indexOf(String id) {
        List<RenderEngine> list = all();
        for (int i = 0; i < list.size(); i++) if (list.get(i).id().equals(id)) return i;
        return 0;
    }
    public static String idAt(int index) {
        List<RenderEngine> list = all();
        if (list.isEmpty()) return "";
        return list.get(Math.clamp(index, 0, list.size() - 1)).id();
    }
    public static String[] names() {
        return all().stream().map(RenderEngine::displayName).toArray(String[]::new);
    }
    public static String[] hintKeys() {
        return all().stream().map(RenderEngine::hintKey).toArray(String[]::new);
    }
    public static String[] qualityHintKeys() { return all().stream().map(RenderEngine::qualityHintKey).toArray(String[]::new); }
    //endregion

    //region Discovery
    private static List<RenderEngine> discover() {
        String pkgPath = ENGINE_PACKAGE;
        Map<String, RenderEngine> byId = new LinkedHashMap<>();
        for (String className : moduleClassNames(pkgPath)) {
            try {
                Class<?> type = Class.forName(className, true, RenderEngines.class.getClassLoader());
                if (!RenderEngine.class.isAssignableFrom(type)) continue;
                if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) continue;
                RenderEngine engine = (RenderEngine) type.getDeclaredConstructor().newInstance();
                byId.putIfAbsent(engine.id(), engine);
            } catch (ReflectiveOperationException | LinkageError e) { System.err.println("Render engine module skipped: " + className + " (" + e + ")"); }
        }
        List<RenderEngine> result = new ArrayList<>(byId.values());
        result.addAll(ExternalEngines.load());
        result.sort(Comparator.comparingInt(RenderEngine::order).thenComparing(RenderEngine::displayName));
        return List.copyOf(result);
    }
    private static List<String> moduleClassNames(String pkgPath) {
        Set<String> names = new TreeSet<>();
        String pkgName = pkgPath.replace('/', '.');
        try {
            var codeSource = RenderEngines.class.getProtectionDomain().getCodeSource();
            if (codeSource != null) {
                Path location = Paths.get(codeSource.getLocation().toURI());
                if (Files.isDirectory(location)) collectFromDirectory(location.resolve(pkgPath), pkgName, names);
                else collectFromJar(location.toFile(), pkgPath, pkgName, names);
            } else {
                Enumeration<URL> roots = RenderEngines.class.getClassLoader().getResources(pkgPath);
                while (roots.hasMoreElements()) {
                    URL root = roots.nextElement();
                    if ("file".equals(root.getProtocol())) collectFromDirectory(Paths.get(root.toURI()), pkgName, names);
                    else if ("jar".equals(root.getProtocol())) collectFromJarUrl(root, pkgPath, pkgName, names);
                }
            }
        } catch (IOException | java.net.URISyntaxException | RuntimeException e) { System.err.println("Render engine discovery failed: " + e); }
        return new ArrayList<>(names);
    }
    private static void collectFromDirectory(Path dir, String pkgName, Set<String> names) throws IOException {
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> files = Files.list(dir)) {
            files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".class") && !n.contains("$"))
                    .forEach(n -> names.add(pkgName + "." + n.substring(0, n.length() - ".class".length())));
        }
    }
    private static void collectFromJar(java.io.File jarFile, String pkgPath, String pkgName, Set<String> names) throws IOException {
        if (!jarFile.isFile()) return;
        try (JarFile jar = new JarFile(jarFile)) { addJarEntries(jar, pkgPath, pkgName, names); }
    }
    private static void collectFromJarUrl(URL root, String pkgPath, String pkgName, Set<String> names) throws IOException {
        JarURLConnection connection = (JarURLConnection) root.openConnection();
        connection.setUseCaches(false);
        try (JarFile jar = connection.getJarFile()) {
            addJarEntries(jar, pkgPath, pkgName, names);
        }
    }
    private static void addJarEntries(JarFile jar, String pkgPath, String pkgName, Set<String> names) {
        Enumeration<java.util.jar.JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            String entry = entries.nextElement().getName();
            if (!entry.startsWith(pkgPath + "/") || !entry.endsWith(".class")) continue;
            String simple = entry.substring(pkgPath.length() + 1);
            if (simple.contains("/") || simple.contains("$")) continue;
            names.add(pkgName + "." + simple.substring(0, simple.length() - ".class".length()));
        }
    }
    //endregion
}