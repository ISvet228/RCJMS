package Project.Game;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;
import java.util.stream.Stream;

public final class RenderEngines {
    //region Variables
    private static final String ENGINE_PACKAGE = "Engines";
    private static volatile List<RenderEngine> engines;
    //endregion

    //region Constructors
    private RenderEngines() {}
    //endregion

    //region Engine Lookup
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
    public static void reload() {
        synchronized (RenderEngines.class) { engines = null; }
        Thread rescan = new Thread(RenderEngines::all, "render-engines-rescan");
        rescan.setDaemon(true);
        rescan.start();
    }
    public static RenderEngine find(String id) {
        List<RenderEngine> list = all();
        if (list.isEmpty()) return null;
        for (RenderEngine renderEngine : list) if (renderEngine.id().equals(id)) return renderEngine;
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

    //region Engine Discovery
    private static List<RenderEngine> discover() {
        Map<String, RenderEngine> byId = new LinkedHashMap<>();
        for (String className : moduleClassNames()) {
            try {
                Class<?> type = Class.forName(className, true, RenderEngines.class.getClassLoader());
                if (!RenderEngine.class.isAssignableFrom(type)) continue;
                if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) continue;
                RenderEngine engine = (RenderEngine) type.getDeclaredConstructor().newInstance();
                byId.putIfAbsent(engine.id(), engine);
            } catch (ReflectiveOperationException | LinkageError exception) { System.err.println("Render engine module skipped: " + className + " (" + exception + ")"); }
        }
        List<RenderEngine> result = new ArrayList<>(byId.values());
        result.addAll(ExternalEngines.load());
        result.sort(Comparator.comparingInt(RenderEngine::order).thenComparing(RenderEngine::displayName));
        return List.copyOf(result);
    }
    private static List<String> moduleClassNames() {
        Set<String> names = new TreeSet<>();
        String pkgName = RenderEngines.ENGINE_PACKAGE.replace('/', '.');
        try {
            var codeSource = RenderEngines.class.getProtectionDomain().getCodeSource();
            if (codeSource != null) {
                Path location = Paths.get(codeSource.getLocation().toURI());
                if (Files.isDirectory(location)) collectFromDirectory(location.resolve(RenderEngines.ENGINE_PACKAGE), pkgName, names);
                else collectFromJar(location.toFile(), pkgName, names);
            } else {
                Enumeration<URL> roots = RenderEngines.class.getClassLoader().getResources(RenderEngines.ENGINE_PACKAGE);
                while (roots.hasMoreElements()) {
                    URL root = roots.nextElement();
                    if ("file".equals(root.getProtocol())) collectFromDirectory(Paths.get(root.toURI()), pkgName, names);
                    else if ("jar".equals(root.getProtocol())) collectFromJarUrl(root, pkgName, names);
                }
            }
        } catch (IOException | java.net.URISyntaxException | RuntimeException exception) { System.err.println("Render engine discovery failed: " + exception); }
        return new ArrayList<>(names);
    }
    private static void collectFromDirectory(Path dir, String pkgName, Set<String> names) throws IOException {
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> files = Files.list(dir)) {
            files.map(path -> path.getFileName().toString()).filter(n -> n.endsWith(".class") && !n.contains("$"))
                    .forEach(n -> names.add(pkgName + "." + n.substring(0, n.length() - ".class".length())));
        }
    }
    private static void collectFromJar(File jarFile, String pkgName, Set<String> names) throws IOException {
        if (!jarFile.isFile()) return;
        try (JarFile jar = new JarFile(jarFile)) { addJarEntries(jar, pkgName, names); }
    }
    private static void collectFromJarUrl(URL root, String pkgName, Set<String> names) throws IOException {
        JarURLConnection connection = (JarURLConnection) root.openConnection();
        connection.setUseCaches(false);
        try (JarFile jar = connection.getJarFile()) {
            addJarEntries(jar, pkgName, names);
        }
    }
    private static void addJarEntries(JarFile jar, String pkgName, Set<String> names) {
        Enumeration<java.util.jar.JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            String entry = entries.nextElement().getName();
            if (!entry.startsWith(RenderEngines.ENGINE_PACKAGE + "/") || !entry.endsWith(".class")) continue;
            String simple = entry.substring(RenderEngines.ENGINE_PACKAGE.length() + 1);
            if (simple.contains("/") || simple.contains("$")) continue;
            names.add(pkgName + "." + simple.substring(0, simple.length() - ".class".length()));
        }
    }
    //endregion
}