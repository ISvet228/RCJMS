package Helpers;

import StyleUI.Style;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class SaveData {
    //region Variables
    public static final String VERSION = "1.999";
    public static final String DEFAULT_LANGUAGE = Locale.getDefault().getLanguage();
    public static final Style DEFAULT_THEME = Style.FLAT;
    public static final double DEFAULT_RENDER_SCALE = 1.0;
    public static final boolean DEFAULT_VSYNC = false;
    public static final boolean DEFAULT_RAY_TRACING = false;
    public static final int DEFAULT_RAY_TRACING_QUALITY = 1;
    public static final int DEFAULT_WINDOW_WIDTH = 960;
    public static final int DEFAULT_WINDOW_HEIGHT = 540;
    public static final boolean DEFAULT_FULLSCREEN = false;
    public static final int DEFAULT_MAZE_SIZE = 25, DEFAULT_MAZE_FLOORS = 3;
    //endregion

    //region Constructors
    private SaveData() { }
    //endregion

    //region Public API
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
            boolean readingFloor = false;
            int readingFloorIndex = -1;
            boolean legacyMapValid = true;
            Map<Integer, int[][]> floorRows = new TreeMap<>();
            List<int[]> portalLinks = new ArrayList<>();
            boolean portalsValid = true;

            for (String raw : lines) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.startsWith("[") && line.endsWith("]")) {
                    if (readingMap && !rows.isEmpty() && legacyMapValid) data.map = rows.toArray(new int[0][]);
                    if (readingFloor && !rows.isEmpty()) floorRows.put(readingFloorIndex, rows.toArray(new int[0][]));

                    section = line.substring(1, line.length() - 1).trim().toLowerCase(Locale.ROOT);
                    readingMap = section.equals("map");
                    readingFloor = section.matches("floor\\d+");
                    readingFloorIndex = readingFloor ? Integer.parseInt(section.substring(5)) : -1;
                    rows = new ArrayList<>();
                    expectedWidth = -1;
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
                else if (section.equals("maze")) {
                    int eq = line.indexOf('=');
                    if (eq <= 0) continue;
                    String key = line.substring(0, eq).trim();
                    String value = line.substring(eq + 1).trim();
                    try {
                        switch (key) {
                            case "width" -> data.mazeWidth = Math.clamp(Integer.parseInt(value), 5, 200);
                            case "height" -> data.mazeHeight = Math.clamp(Integer.parseInt(value), 5, 200);
                            case "finishMode" -> data.mazeFinishMode = Math.clamp(Integer.parseInt(value), 0, 2);
                            case "geometry" -> data.mazeGeometry = Math.clamp(Integer.parseInt(value), 0, 2);
                            case "mode3D" -> data.maze3D = Boolean.parseBoolean(value);
                            case "floors" -> data.mazeFloors = Math.clamp(Integer.parseInt(value), 2, 20);
                            case "fog" -> data.mazeFog = Boolean.parseBoolean(value);
                        }
                    } catch (NumberFormatException ignored) { }
                }
                else if (section.equals("mapmeta")) {
                    int eq = line.indexOf('=');
                    if (eq <= 0) continue;
                    String key = line.substring(0, eq).trim();
                    String value = line.substring(eq + 1).trim();
                    if (key.equals("geometry")) {
                        try { data.mapGeometryMode = Math.max(0, Integer.parseInt(value)); } catch (NumberFormatException ignored) { }
                    }
                }
                else if (section.equals("portals")) {
                    String[] values = line.split("[,;\\s]+");
                    if (values.length != 9 && values.length != 11 && values.length != 12) { portalsValid = false; continue; }
                    try {
                        int[] link = new int[values.length];
                        for (int i = 0; i < link.length; i++) link[i] = Integer.parseInt(values[i]);
                        portalLinks.add(link);
                    } catch (NumberFormatException ignored) { portalsValid = false; }
                }
                else if (readingMap || readingFloor) {
                    String[] values = line.split("[,;\\s]+");
                    if (expectedWidth == -1) expectedWidth = values.length;
                    if (values.length != expectedWidth) { rows.clear(); if (readingMap) legacyMapValid = false; break; }
                    int[] row = new int[values.length];
                    try {
                        for (int x = 0; x < values.length; x++) {
                            int value = Integer.parseInt(values[x]);
                            if (value < 0 || value > 5) throw new NumberFormatException();
                            row[x] = value;
                        }
                        rows.add(row);
                    } catch (NumberFormatException ignored) { rows.clear(); if (readingMap) legacyMapValid = false; break; }
                }
            }
            if (readingMap && !rows.isEmpty() && legacyMapValid) data.map = rows.toArray(new int[0][]);
            if (readingFloor && !rows.isEmpty()) floorRows.put(readingFloorIndex, rows.toArray(new int[0][]));

            if (!floorRows.isEmpty()) {
                data.mapFloors = new int[floorRows.size()][][];
                int i = 0;
                for (int[][] floor : floorRows.values()) data.mapFloors[i++] = floor;
                if (portalsValid) data.mapPortalLinks = portalLinks;
            }
            else if (data.map != null) {
                data.mapFloors = new int[][][]{data.map};
            }
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
    public static void saveMazeSettings(int width, int height, int finishMode, int geometry, boolean mode3D, int floors, boolean fog) throws IOException {
        Data data = load();
        data.mazeWidth = Math.clamp(width, 5, 200);
        data.mazeHeight = Math.clamp(height, 5, 200);
        data.mazeFinishMode = Math.clamp(finishMode, 0, 2);
        data.mazeGeometry = Math.clamp(geometry, 0, 2);
        data.maze3D = mode3D;
        data.mazeFloors = Math.clamp(floors, 2, 20);
        data.mazeFog = fog;
        save(data);
    }
    public static void saveMap(int[][][] floors, List<int[]> portalLinks, int geometryMode, String language, Style theme, double renderScale) throws IOException {
        Data data = load();
        data.language = language;
        data.theme = theme;
        data.renderScale = renderScale;
        data.mapFloors = floors;
        data.map = (floors != null && floors.length > 0) ? floors[0] : null;
        data.mapPortalLinks = portalLinks != null ? portalLinks : new ArrayList<>();
        data.mapGeometryMode = geometryMode;
        save(data);
    }
    //endregion

    //region Helpers
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

            writer.write("[maze]"); writer.newLine();
            writer.write("width=" + data.mazeWidth); writer.newLine();
            writer.write("height=" + data.mazeHeight); writer.newLine();
            writer.write("finishMode=" + data.mazeFinishMode); writer.newLine();
            writer.write("geometry=" + data.mazeGeometry); writer.newLine();
            writer.write("mode3D=" + data.maze3D); writer.newLine();
            writer.write("floors=" + data.mazeFloors); writer.newLine();
            writer.write("fog=" + data.mazeFog); writer.newLine(); writer.newLine();

            writer.write("[mapmeta]"); writer.newLine();
            writer.write("geometry=" + data.mapGeometryMode); writer.newLine(); writer.newLine();

            if (data.mapFloors != null && data.mapFloors.length > 0) {
                for (int f = 0; f < data.mapFloors.length; f++) {
                    writer.write("[floor" + f + "]"); writer.newLine();
                    int[][] floor = data.mapFloors[f];
                    if (floor != null) {
                        for (int[] row : floor) {
                            for (int x = 0; x < row.length; x++) {
                                if (x > 0) writer.write(',');
                                writer.write(Integer.toString(row[x]));
                            }
                            writer.newLine();
                        }
                    }
                    writer.newLine();
                }
            }
            else {
                writer.write("[floor0]"); writer.newLine();
                if (data.map != null) {
                    for (int[] row : data.map) {
                        for (int x = 0; x < row.length; x++) {
                            if (x > 0) writer.write(',');
                            writer.write(Integer.toString(row[x]));
                        }
                        writer.newLine();
                    }
                }
                writer.newLine();
            }

            writer.write("[portals]"); writer.newLine();
            if (data.mapPortalLinks != null) {
                for (int[] link : data.mapPortalLinks) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < link.length; i++) { if (i > 0) sb.append(','); sb.append(link[i]); }
                    writer.write(sb.toString()); writer.newLine();
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
    //endregion

    //region Nested Types
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
        public int[][][] mapFloors;
        public List<int[]> mapPortalLinks = new ArrayList<>();
        public int mapGeometryMode = 0;
        public int mazeWidth = DEFAULT_MAZE_SIZE, mazeHeight = DEFAULT_MAZE_SIZE;
        public int mazeFinishMode = 0;
        public int mazeGeometry = 0;
        public boolean maze3D = false;
        public int mazeFloors = DEFAULT_MAZE_FLOORS;
        public boolean mazeFog = false;
    }
    //endregion
}