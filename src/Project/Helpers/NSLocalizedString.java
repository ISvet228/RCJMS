package Project.Helpers;
import Project.EngineCreator.*;
import Project.ThemeCreator.*;
import Project.CreatorKit.*;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.*;
import java.util.regex.*;
import java.lang.reflect.Method;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.awt.AWTEvent;
import java.awt.Toolkit;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;

public final class NSLocalizedString {
    //region Variables
    //region Translation Tables
    private static final String FILE_NAME = "Localizable.strings";
    private static final String FALLBACK_LANGUAGE = "en";
    private static final Pattern LINE_PATTERN = Pattern.compile("^\\s*\"((?:\\\\.|[^\"])*)\"\\s*=\\s*\"((?:\\\\.|[^\"])*)\"\\s*;?\\s*(?://.*)?$");
    private static final Map<String, String> strings = new LinkedHashMap<>();
    private static String language = Locale.getDefault().getLanguage();
    private static Path baseDirectory = Paths.get(".");
    //endregion
    //region Binding State
    private static final Map<Object, Binding> bindings = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, String> trackedText = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Set<Object> internalUpdates = Collections.newSetFromMap(new WeakHashMap<>());
    private static volatile boolean autoTrackingInstalled;
    private static final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    //endregion
    //endregion

    //region Setup
    static { reload(); installAutomaticTracking(); }
    //endregion

    //region Lookup
    public static LocalizedValue of(String key) { return new LocalizedValue(key); }
    public static String value(String key) { return localized(key); }
    public static String get(String key) { return localized(key); }
    public static String localized(String key) {
        if (key == null) return "";
        synchronized(strings) { return strings.getOrDefault(key, key); }
    }
    public static String format(String key, Object... args) { return String.format(localized(key), args); }
    public static String localizeDynamic(String source) {
        if (source == null || source.isEmpty()) return source == null ? "" : source;
        String exact = localized(source);
        if (!exact.equals(source)) return exact;

        List<Map.Entry<String, String>> entries;
        synchronized (strings) { entries = new ArrayList<>(strings.entrySet()); }
        entries.removeIf(e -> e.getKey().isEmpty() || e.getKey().equals(e.getValue()));
        entries.sort((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()));

        String result = source;
        for (Map.Entry<String, String> e : entries) {
            String key = e.getKey();
            if (!result.contains(key)) continue;
            result = result.replace(key, e.getValue());
        }
        return result;
    }
    public static boolean hasKey(String key) {
        if (key == null) return true;
        synchronized (strings) { return !strings.containsKey(key); }
    }
    //endregion

    //region Language
    //region Current Language
    public static String getLanguage() { return language; }
    public static void setLanguage(String newLanguage) {
        if (newLanguage == null || newLanguage.isBlank()) return;
        language = newLanguage.trim();
        reload();
        refreshAll();
    }
    public static void setBaseDirectory(Path directory) {
        baseDirectory = directory == null ? Paths.get(".") : directory;
        reload();
        refreshAll();
    }
    public static void addLanguageChangeListener(Runnable listener) { if (listener != null) listeners.add(listener); }
    public static void removeLanguageChangeListener(Runnable listener) { listeners.remove(listener); }
    //endregion
    //region Loading Translations
    public static void reload() {
        Map<String, String> loaded = loadForLanguage(language);
        synchronized (strings) {
            strings.clear();
            strings.putAll(loaded);
        }
    }
    private static Map<String, String> loadForLanguage(String lang) {
        Map<String, String> result = new LinkedHashMap<>();
        loadInto(result, filesystemCandidates(null), classpathCandidates(null));
        if (!FALLBACK_LANGUAGE.equals(lang)) loadInto(result, filesystemCandidates(FALLBACK_LANGUAGE), classpathCandidates(FALLBACK_LANGUAGE));
        loadInto(result, filesystemCandidates(lang), classpathCandidates(lang));
        return result;
    }
    private static List<Path> localizationRoots() {
        List<Path> roots = new ArrayList<>();
        roots.add(baseDirectory.toAbsolutePath().normalize());
        try {
            Path where = Paths.get(NSLocalizedString.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path dir = Files.isDirectory(where) ? where : where.getParent();
            for (int i = 0; dir != null && i < 7; i++, dir = dir.getParent()) if (!roots.contains(dir)) roots.add(dir);
        }
        catch (Exception ignored) {}
        return roots;
    }
    private static List<Path> filesystemCandidates(String lang) {
        List<Path> result = new ArrayList<>();
        for (Path root : localizationRoots()) {
            if (lang == null) {
                result.add(root.resolve(FILE_NAME));
                result.add(root.resolve("src/Localization").resolve(FILE_NAME));
                result.add(root.resolve("localization").resolve(FILE_NAME));
                result.add(root.resolve("src").resolve("Localization").resolve(FILE_NAME));
            } else {
                String folder = lang + ".lproj";
                result.add(root.resolve("src/Localization").resolve(folder).resolve(FILE_NAME));
                result.add(root.resolve("localization").resolve(folder).resolve(FILE_NAME));
                result.add(root.resolve(folder).resolve(FILE_NAME));
                result.add(root.resolve("src").resolve("Localization").resolve(folder).resolve(FILE_NAME));
            }
        }
        return result;
    }
    private static List<String> classpathCandidates(String lang) {
        if (lang == null) return List.of("/" + FILE_NAME, "/Localization/" + FILE_NAME, "/localization/" + FILE_NAME);
        String folder = lang + ".lproj";
        return List.of("/Localization/" + folder + "/" + FILE_NAME, "/localization/" + folder + "/" + FILE_NAME, "/" + folder + "/" +
                FILE_NAME);
    }
    private static void loadInto(Map<String, String> target, List<Path> paths, List<String> resources) {
        for (Path path : paths) {
            if (!Files.isRegularFile(path)) continue;
            try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { parse(reader, target); return; }
            catch (IOException ignored) {}
        }
        for (String resource : resources) {
            try (InputStream in = NSLocalizedString.class.getResourceAsStream(resource)) {
                if (in == null) continue;
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) { parse(reader, target); return; }
            } catch (IOException ignored) {}
        }
    }
    private static void parse(BufferedReader reader, Map<String, String> target) throws IOException {
        String line;
        boolean firstLine = true;
        while ((line = reader.readLine()) != null) {
            if (firstLine && !line.isEmpty() && line.charAt(0) == '\uFEFF') line = line.substring(1);
            firstLine = false;
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.startsWith("#")) continue;
            Matcher matcher = LINE_PATTERN.matcher(line);
            if (!matcher.matches()) continue;
            target.put(unescape(matcher.group(1)), unescape(matcher.group(2)));
        }
    }
    private static String unescape(String value) {
        StringBuilder out = new StringBuilder(value.length());
        boolean escape = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!escape) {
                if (c == '\\') escape = true;
                else out.append(c);
                continue;
            }
            switch (c) {
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case '\\' -> out.append('\\');
                case '"' -> out.append('"');
                default -> out.append(c);
            }
            escape = false;
        }
        if (escape) out.append('\\');
        return out.toString();
    }
    //endregion
    //endregion

    //region Component Binding
    //region Binding Methods
    public static void bind(Object owner, String key, Consumer<String> setter) {
        if (owner == null || key == null || setter == null || hasKey(key)) return;
        Runnable refresh = () -> { internalUpdates.add(owner); try { setter.accept(localized(key)); } finally { internalUpdates.remove(owner); } };
        bindings.put(owner, new Binding(refresh));
        runOnEdt(refresh);
    }
    public static void bind(JLabel label, String key) { bind(label, key, label::setText); }
    public static void bind(AbstractButton button, String key) { bind(button, key, button::setText); }
    public static void bind(JTextComponent textComponent, String key) { bind(textComponent, key, textComponent::setText); }
    public static void bind(JFrame frame, String key) { bind(frame, key, frame::setTitle); }
    public static void bind(JDialog dialog, String key) { bind(dialog, key, dialog::setTitle); }
    public static boolean bind(Component component, String key) {
        if (component == null || key == null || hasKey(key)) return false;
        switch (component) {
            case JLabel label -> { bind((JLabel) label, key); return true; }
            case AbstractButton button -> { bind((AbstractButton) button, key); return true; }
            case JTextComponent textComponent -> { bind((JTextComponent) textComponent, key); return true; }
            case JFrame frame -> { bind((JFrame) frame, key); return true; }
            case JDialog dialog -> { bind((JDialog) dialog, key); return true; }
            default -> {}
        }
        try {
            Method setText = component.getClass().getMethod("setText", String.class);
            bind(component, key, value -> {try {setText.invoke(component, value);} catch (ReflectiveOperationException ignored) {}});
            return true;
        }
        catch (NoSuchMethodException ignored) { return false; }
    }
    public static void bindFormat(Object owner, String key, Supplier<Object[]> arguments, Consumer<String> setter) {
        if (owner == null || key == null || arguments == null || setter == null || hasKey(key)) return;
        Runnable refresh = () -> { internalUpdates.add(owner); try { setter.accept(format(key, arguments.get())); } finally { internalUpdates.remove(owner); } };
        bindings.put(owner, new Binding(refresh));
        runOnEdt(refresh);
    }
    public static void unbind(Object owner) { if (owner != null) bindings.remove(owner); }
    public static void refresh(Object owner) {
        if (owner == null) return;
        Binding binding = bindings.get(owner);
        if (binding != null) runOnEdt(binding.refresh);
    }
    //endregion
    //region Automatic Tracking
    private static void installAutomaticTracking() {
        if (autoTrackingInstalled) return;
        autoTrackingInstalled = true;
        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            try {if (event instanceof WindowEvent windowEvent && windowEvent.getID() == WindowEvent.WINDOW_OPENED) registerComponentTree(windowEvent.getWindow());
                else if (event instanceof HierarchyEvent hierarchyEvent && (hierarchyEvent.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) registerComponentTree(hierarchyEvent.getComponent());
            } catch (RuntimeException ignored) {}
        }, AWTEvent.WINDOW_EVENT_MASK | AWTEvent.HIERARCHY_EVENT_MASK);
    }
    private static void registerComponentTree(Component component) {
        if (component == null) return;
        registerComponent(component);
        if (component instanceof Container container) for (Component child : container.getComponents()) registerComponentTree(child);
    }
    private static void registerComponent(Component component) {
        if (component == null) return;
        if (component instanceof Window window) {
            window.addPropertyChangeListener("title", propertyChangeEvent -> {
                if (internalUpdates.contains(window)) return;
                String source = String.valueOf(propertyChangeEvent.getNewValue() == null ? "" : propertyChangeEvent.getNewValue());
                trackedText.put(window, source);
                String localized = localizeDynamic(source);
                if (!localized.equals(source)) {
                    internalUpdates.add(window);
                    try { window.setName(window.getName()); setWindowTitle(window, localized); }
                    finally { internalUpdates.remove(window); }
                }
            });
            String title = getWindowTitle(window);
            if (!title.isEmpty()) {
                trackedText.put(window, title);
                String localizedTitle = localizeDynamic(title);
                if (!localizedTitle.equals(title)) {
                    internalUpdates.add(window);
                    try { setWindowTitle(window, localizedTitle); }
                    finally { internalUpdates.remove(window); }
                }
            }
        }
        if (component instanceof javax.swing.JComponent jc) jc.addPropertyChangeListener("text", propertyChangeEvent -> onSwingTextChanged(jc, propertyChangeEvent));
        String current = getComponentText(component);
        if (current != null && !trackedText.containsKey(component)) {
            trackedText.put(component, current);
            String localized = localizeDynamic(current);
            if (!localized.equals(current)) setComponentText(component, localized);
        }
    }
    private static void onSwingTextChanged(Object owner, PropertyChangeEvent propertyChangeEvent) {
        if (internalUpdates.contains(owner)) return;
        String source = propertyChangeEvent.getNewValue() == null ? "" : String.valueOf(propertyChangeEvent.getNewValue());
        trackedText.put(owner, source);
        String localized = localizeDynamic(source);
        if (!localized.equals(source)) {
            internalUpdates.add(owner);
            try { setComponentText((Component) owner, localized); }
            finally { internalUpdates.remove(owner); }
        }
    }
    private static void refreshAll() {
        runOnEdt(() -> {
            List<Binding> copy;
            synchronized (bindings) { copy = new ArrayList<>(bindings.values()); }
            for (Binding binding : copy) {
                try { binding.refresh.run(); }
                catch (RuntimeException ignored) {}
            }
            synchronized (trackedText) {
                for (Map.Entry<Object, String> entry : new ArrayList<>(trackedText.entrySet())) {
                    Object owner = entry.getKey();
                    String source = entry.getValue();
                    if (owner instanceof Component component && component.isDisplayable()) {
                        String localized = localizeDynamic(source);
                        if (!localized.equals(getComponentText(component))) {
                            internalUpdates.add(owner);
                            try { setComponentText(component, localized); }
                            finally { internalUpdates.remove(owner); }
                        }
                    } else if (owner instanceof Window window) {
                        String localized = localizeDynamic(source);
                        if (!localized.equals(getWindowTitle(window))) {
                            internalUpdates.add(owner);
                            try { setWindowTitle(window, localized); }
                            finally { internalUpdates.remove(owner); }
                        }
                    }
                }
            }
            for (Runnable listener : listeners) {
                try { listener.run(); }
                catch (RuntimeException ignored) {}
            }
        });
    }
    public static void trackText(Object owner, String source, Consumer<String> setter) {
        if (owner == null || setter == null) return;
        unbind(owner);
        String raw = source == null ? "" : source;
        trackedText.put(owner, raw);
        Runnable refresh = () -> {
            internalUpdates.add(owner);
            try { setter.accept(localizeDynamic(trackedText.getOrDefault(owner, raw))); }
            finally { internalUpdates.remove(owner); }
        };
        bindings.put(owner, new Binding(refresh));
        runOnEdt(refresh);
    }
    public static void externalTextChanged(Object owner, String source, Consumer<String> setter) { trackText(owner, source, setter); }
    //endregion
    //endregion

    //region Component Text
    private static String getComponentText(Component component) {
        if (component instanceof JLabel label) return label.getText();
        if (component instanceof AbstractButton abstractButton) return abstractButton.getText();
        if (component instanceof JTextComponent textComponent) return textComponent.getText();
        return null;
    }
    private static void setComponentText(Component component, String text) {
        if (component instanceof JLabel label) label.setText(text);
        else if (component instanceof AbstractButton abstractButton) abstractButton.setText(text);
        else if (component instanceof JTextComponent textComponent) textComponent.setText(text);
    }
    private static String getWindowTitle(Window window) {
        if (window instanceof JFrame frame) return frame.getTitle();
        if (window instanceof JDialog dialog) return dialog.getTitle();
        return "";
    }
    private static void setWindowTitle(Window window, String title) {
        if (window instanceof JFrame frame) frame.setTitle(title);
        else if (window instanceof JDialog dialog) dialog.setTitle(title);
    }
    private static void runOnEdt(Runnable runnable) {
        if (SwingUtilities.isEventDispatchThread()) runnable.run();
        else SwingUtilities.invokeLater(runnable);
    }
    //endregion

    //region Types
    public static final class LocalizedValue {
        private final String key;

        private LocalizedValue(String key) { this.key = key == null ? "" : key; }

        public String getKey() { return key; }
        public String get() { return localized(key); }

        @Override public String toString() { return get(); }
    }
    private record Binding(Runnable refresh) {

    }
    //endregion
}