package Helpers;

import StyleUI.Style;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class SaveData {
    public static final String VERSION = "1.95";
    public static final String DEFAULT_LANGUAGE = Locale.getDefault().getLanguage();
    public static final Style DEFAULT_THEME = Style.FLAT;
    public static final double DEFAULT_RENDER_SCALE = 1.0;
    public static final boolean DEFAULT_VSYNC = false;
    public static final boolean DEFAULT_RAY_TRACING = false;
    public static final int DEFAULT_RAY_TRACING_QUALITY = 1;
    public static final int DEFAULT_WINDOW_WIDTH = 960;
    public static final int DEFAULT_WINDOW_HEIGHT = 540;
    public static final boolean DEFAULT_FULLSCREEN = false;

    private SaveData() { }
    public static final class Data {
        public String language = DEFAULT_LANGUAGE;
        public Style theme = DEFAULT_THEME;
        public double renderScale = DEFAULT_RENDER_SCALE;
        public boolean vsync = DEFAULT_VSYNC;
        public boolean rayTracing = DEFAULT_RAY_TRACING;
        public int rayTracingQuality = DEFAULT_RAY_TRACING_QUALITY;
        public int windowWidth = DEFAULT_WINDOW_WIDTH;
        public int windowHeight = DEFAULT_WINDOW_HEIGHT;
        public boolean fullscreen = DEFAULT_FULLSCREEN;
        public int[][] map;
    }
    public static Data load() {
        Data data = new Data();
        Path file = AppPaths.SAVE_FILE;
        if (!Files.exists(file)) return data;
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            String section = "";
            List<int[]> rows = new ArrayList<>();
            int expectedWidth = -1;
            boolean readingMap = false;

            for (String raw : lines) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.startsWith("[") && line.endsWith("]")) {
                    section = line.substring(1, line.length() - 1).trim().toLowerCase(Locale.ROOT);
                    readingMap = section.equals("map");
                    continue;
                }

                if (section.equals("settings")) {
                    int eq = line.indexOf('=');
                    if (eq <= 0) continue;
                    String key = line.substring(0, eq).trim();
                    String value = line.substring(eq + 1).trim();
                    try {
                        switch (key) {
                            case "language" -> { if (!value.isBlank()) data.language = value; }
                            case "theme" -> data.theme = Style.valueOf(value);
                            case "renderQuality" -> {
                                double scale = Double.parseDouble(value);
                                if (scale >= 0.1 && scale <= 1.0) data.renderScale = scale;
                            }
                            case "vsync" -> data.vsync = Boolean.parseBoolean(value);
                            case "rayTracing" -> data.rayTracing = Boolean.parseBoolean(value);
                            case "rayTracingQuality" -> data.rayTracingQuality = Math.clamp(Integer.parseInt(value), 0, 2);
                            case "windowWidth" -> { int w = Integer.parseInt(value); if (w >= 960) data.windowWidth = w; }
                            case "windowHeight" -> { int h = Integer.parseInt(value); if (h >= 540) data.windowHeight = h; }
                            case "fullscreen" -> data.fullscreen = Boolean.parseBoolean(value);
                        }
                    } catch (IllegalArgumentException ignored) { }
                }
                else if (readingMap) {
                    String[] values = line.split("[,;\\s]+");
                    if (expectedWidth == -1) expectedWidth = values.length;
                    if (values.length != expectedWidth) { rows.clear(); break; }
                    int[] row = new int[values.length];
                    try {
                        for (int x = 0; x < values.length; x++) {
                            int value = Integer.parseInt(values[x]);
                            if (value < 0 || value > 2) throw new NumberFormatException();
                            row[x] = value;
                        }
                        rows.add(row);
                    } catch (NumberFormatException ignored) { rows.clear(); break; }
                }
            }
            if (!rows.isEmpty()) data.map = rows.toArray(new int[0][]);
        } catch (IOException ignored) { }
        return data;
    }
    public static void saveSettings(String language, Style theme, double renderScale, boolean vsync, boolean rayTracing, int rayTracingQuality, int windowWidth, int windowHeight, boolean fullscreen) throws IOException {
        Data data = load();
        data.language = language;
        data.theme = theme;
        data.renderScale = renderScale;
        data.vsync = vsync;
        data.rayTracing = rayTracing;
        data.rayTracingQuality = Math.clamp(rayTracingQuality, 0, 2);
        data.windowWidth = windowWidth >= 960 ? windowWidth : DEFAULT_WINDOW_WIDTH;
        data.windowHeight = windowHeight >= 540 ? windowHeight : DEFAULT_WINDOW_HEIGHT;
        data.fullscreen = fullscreen;
        save(data);
    }
    public static void saveMap(int[][] map, String language, Style theme, double renderScale) throws IOException {
        Data data = load();
        data.language = language;
        data.theme = theme;
        data.renderScale = renderScale;
        data.map = map;
        save(data);
    }
    private static void save(Data data) throws IOException {
        Files.createDirectories(AppPaths.DATA_DIR);
        Path temp = AppPaths.SAVE_FILE.resolveSibling(AppPaths.SAVE_FILE.getFileName() + ".tmp");
        try (BufferedWriter writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            writer.write("#Save Version " + VERSION); writer.newLine(); writer.newLine();
            writer.write("[settings]"); writer.newLine();
            writer.write("language=" + safe(data.language)); writer.newLine();
            writer.write("theme=" + data.theme.name()); writer.newLine();
            writer.write("renderQuality=" + formatScale(data.renderScale)); writer.newLine();
            writer.write("vsync=" + data.vsync); writer.newLine();
            writer.write("rayTracing=" + data.rayTracing); writer.newLine();
            writer.write("rayTracingQuality=" + data.rayTracingQuality); writer.newLine();
            writer.write("windowWidth=" + data.windowWidth); writer.newLine();
            writer.write("windowHeight=" + data.windowHeight); writer.newLine();
            writer.write("fullscreen=" + data.fullscreen); writer.newLine(); writer.newLine();
            writer.write("[map]"); writer.newLine();
            if (data.map != null) {
                for (int y = 0; y < data.map.length; y++) {
                    for (int x = 0; x < data.map[y].length; x++) {
                        if (x > 0) writer.write(',');
                        writer.write(Integer.toString(data.map[y][x]));
                    }
                    writer.newLine();
                }
            }
        }
        try { Files.move(temp, AppPaths.SAVE_FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException e) { Files.move(temp, AppPaths.SAVE_FILE, StandardCopyOption.REPLACE_EXISTING); }
    }
    private static String formatScale(double scale) {
        if (scale >= 0.99) return "1.0";
        if (scale >= 0.74) return "0.75";
        if (scale >= 0.49) return "0.5";
        return "0.1";
    }
    private static String safe(String value) { return value == null || value.isBlank() ? DEFAULT_LANGUAGE : value.trim(); }
}