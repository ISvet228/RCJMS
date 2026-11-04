package Project.MainMenu;
import Project.EngineCreator.*;
import Project.ThemeCreator.*;
import Project.CreatorKit.*;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.util.stream.Stream;

public final class MenuThemeRegistry {
    //region Variables
    //region Registry
    private static final String PACKAGE = "MenuThemes";
    public static final String DEFAULT_ID = "space";
    private static volatile List<MenuTheme> themes;
    //endregion
    //region Clock
    private static final long CLOCK_START = System.nanoTime();
    //endregion
    //endregion

    //region Constructors
    private MenuThemeRegistry() {}
    //endregion

    //region Theme Lookup
    public static double clock() { return (System.nanoTime() - CLOCK_START) / 1e9; }
    public static List<MenuTheme> all() {
        List<MenuTheme> list = themes;
        if (list == null) {
            synchronized (MenuThemeRegistry.class) {
                if (themes == null) themes = discover();
                list = themes;
            }
        }
        return list;
    }
    public static MenuTheme find(String id) {
        List<MenuTheme> list = all();
        for (MenuTheme theme : list) if (theme.id().equals(id)) return theme;
        return list.isEmpty() ? null : list.getFirst();
    }
    public static void reload() { themes = null; }
    public static MenuTheme refresh(MenuTheme theme) {
        if (theme == null) return null;
        for (MenuTheme menuTheme : all()) if (menuTheme.id().equals(theme.id())) return menuTheme;
        return theme;
    }
    //endregion

    //region Theme Discovery
    private static List<MenuTheme> discover() {
        Map<String, MenuTheme> byId = new LinkedHashMap<>();
        for (String className : moduleClassNames()) {
            try {
                Class<?> type = Class.forName(className, true, MenuThemeRegistry.class.getClassLoader());
                if (!MenuTheme.class.isAssignableFrom(type)) continue;
                if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) continue;
                MenuTheme theme = (MenuTheme) type.getDeclaredConstructor().newInstance();
                byId.putIfAbsent(theme.id(), theme);
            } catch (ReflectiveOperationException | LinkageError exception) { System.err.println("Menu theme skipped: " + className + " (" + exception + ")"); }
        }
        List<MenuTheme> builtIn = new ArrayList<>(byId.values());
        builtIn.sort(Comparator.comparingInt(MenuTheme::order).thenComparing(MenuTheme::id));
        Map<String, MenuTheme> all = new LinkedHashMap<>();
        for (MenuTheme theme : builtIn) all.put(theme.id(), theme);
        for (MenuTheme theme : Project.ThemeCreator.ThemeLibrary.userThemes()) all.putIfAbsent(theme.id(), theme);
        return List.copyOf(all.values());
    }
    private static List<String> moduleClassNames() {
        Set<String> names = new TreeSet<>();
        try {
            var codeSource = MenuThemeRegistry.class.getProtectionDomain().getCodeSource();
            if (codeSource != null) {
                Path location = Paths.get(codeSource.getLocation().toURI());
                if (Files.isDirectory(location)) collectFromDirectory(location.resolve(PACKAGE), names);
                else collectFromJar(location.toFile(), names);
            }
            else {
                Enumeration<URL> roots = MenuThemeRegistry.class.getClassLoader().getResources(PACKAGE);
                while (roots.hasMoreElements()) {
                    URL root = roots.nextElement();
                    if ("file".equals(root.getProtocol())) collectFromDirectory(Paths.get(root.toURI()), names);
                    else if ("jar".equals(root.getProtocol())) collectFromJarUrl(root, names);
                }
            }
        } catch (IOException | URISyntaxException | RuntimeException exception) { System.err.println("Menu theme discovery failed: " + exception); }
        return new ArrayList<>(names);
    }
    private static void collectFromDirectory(Path dir, Set<String> names) throws IOException {
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> files = Files.list(dir)) {
            files.map(path -> path.getFileName().toString()).filter(n -> n.endsWith(".class") && !n.contains("$"))
                    .forEach(n -> names.add(PACKAGE + "." + n.substring(0, n.length() - ".class".length())));
        }
    }
    private static void collectFromJar(java.io.File jarFile, Set<String> names) throws IOException {
        if (!jarFile.isFile()) return;
        try (JarFile jar = new JarFile(jarFile)) { addJarEntries(jar, names); }
    }
    private static void collectFromJarUrl(URL root, Set<String> names) throws IOException {
        JarURLConnection connection = (JarURLConnection) root.openConnection();
        connection.setUseCaches(false);
        try (JarFile jar = connection.getJarFile()) { addJarEntries(jar, names); }
    }
    private static void addJarEntries(JarFile jar, Set<String> names) {
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            String entry = entries.nextElement().getName();
            if (!entry.startsWith(PACKAGE + "/") || !entry.endsWith(".class")) continue;
            String simple = entry.substring(PACKAGE.length() + 1);
            if (simple.contains("/") || simple.contains("$")) continue;
            names.add(PACKAGE + "." + simple.substring(0, simple.length() - ".class".length()));
        }
    }
    //endregion
}