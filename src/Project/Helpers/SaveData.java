package Project.Helpers;

import Project.Game.RenderEngines;
import Project.StyleUI.Style;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class SaveData {
    //region Variables
    //region Format
    public static final String VERSION = "3.0";
    //endregion
    //region Default Settings
    public static final String DEFAULT_LANGUAGE = Locale.getDefault().getLanguage();
    public static final Style DEFAULT_THEME = Style.FLAT;
    public static final double DEFAULT_RENDER_SCALE = 1.0;
    public static final boolean DEFAULT_VSYNC = false;
    public static final boolean DEFAULT_OPENCL = true;
    public static final int DEFAULT_RENDER_ENGINE_QUALITY = 1;
    public static final String DEFAULT_RENDER_ENGINE = "";
    public static final int DEFAULT_WINDOW_WIDTH = 960;
    public static final int DEFAULT_WINDOW_HEIGHT = 540;
    public static final boolean DEFAULT_FULLSCREEN = false;
    public static final int DEFAULT_MAZE_SIZE = 25, DEFAULT_MAZE_FLOORS = 3;
    public static final String DEFAULT_MENU_THEME = "space";
    //endregion
    //endregion

    //region Constructors
    private SaveData() { }
    //endregion

    //region Loading
    public static Data load() {
        Data data = new Data();
        read(AppPaths.SETTINGS_FILE, data);
        read(AppPaths.MAP_FILE, data);
        return data;
    }

    private static void read(Path file, Data data) {
        if (!Files.isRegularFile(file)) return;
        try { parse(Files.readAllLines(file, StandardCharsets.UTF_8), data); }
        catch (IOException ignored) { }
    }

    private static void parse(List<String> lines, Data data) {
        String section = "";
        int floorIndex = -1;
        List<int[]> rows = new ArrayList<>();
        int expectedWidth = -1;
        boolean rowsValid = true;
        Map<Integer, int[][]> floorRows = new TreeMap<>();
        List<int[]> portalLinks = new ArrayList<>();
        boolean portalsValid = true;
        boolean portalsSeen = false;

        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.startsWith("[") && line.endsWith("]")) {
                if (floorIndex >= 0 && rowsValid && !rows.isEmpty()) floorRows.put(floorIndex, rows.toArray(new int[0][]));
                section = line.substring(1, line.length() - 1).trim().toLowerCase(Locale.ROOT);
                floorIndex = section.matches("floor\\d+") ? Integer.parseInt(section.substring(5)) : -1;
                rows = new ArrayList<>();
                expectedWidth = -1;
                rowsValid = true;
                if (section.equals("portals")) portalsSeen = true;
                continue;
            }
            int eq = line.indexOf('=');
            switch (section) {
                case "settings" -> { if (eq > 0) readSetting(line.substring(0, eq).trim(), line.substring(eq + 1).trim(), data); }
                case "maze" -> { if (eq > 0) readMaze(line.substring(0, eq).trim(), line.substring(eq + 1).trim(), data); }
                case "victory" -> { if (eq > 0) readVictory(line.substring(0, eq).trim(), line.substring(eq + 1).trim(), data); }
                case "modules" -> { if (eq > 0) data.modules.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim()); }
                case "mapmeta" -> {
                    if (eq > 0 && line.substring(0, eq).trim().equals("geometry")) {
                        try { data.mapGeometryMode = Math.max(0, Integer.parseInt(line.substring(eq + 1).trim())); }
                        catch (NumberFormatException ignored) { }
                    }
                }
                case "portals" -> {
                    if (!portalsValid) continue;
                    String[] values = line.split("[,;\\s]+");
                    if (values.length != 9 && values.length != 11 && values.length != 12) { portalsValid = false; continue; }
                    try {
                        int[] link = new int[values.length];
                        for (int i = 0; i < link.length; i++) link[i] = Integer.parseInt(values[i]);
                        portalLinks.add(link);
                    } catch (NumberFormatException ignored) { portalsValid = false; }
                }
                default -> {
                    if (floorIndex < 0 || !rowsValid) continue;
                    String[] values = line.split("[,;\\s]+");
                    if (expectedWidth == -1) expectedWidth = values.length;
                    int[] row = parseRow(values, expectedWidth);
                    if (row == null) { rowsValid = false; rows.clear(); }
                    else rows.add(row);
                }
            }
        }
        if (floorIndex >= 0 && rowsValid && !rows.isEmpty()) floorRows.put(floorIndex, rows.toArray(new int[0][]));
        if (!floorRows.isEmpty()) data.mapFloors = floorRows.values().toArray(new int[0][][]);
        if (portalsSeen && portalsValid) data.mapPortalLinks = portalLinks;
    }
    private static int[] parseRow(String[] values, int width) {
        if (values.length != width) return null;
        int[] row = new int[values.length];
        for (int x = 0; x < values.length; x++) {
            try {
                int value = Integer.parseInt(values[x]);
                if (value < 0 || value > 5) return null;
                row[x] = value;
            } catch (NumberFormatException exception) { return null; }
        }
        return row;
    }

    private static void readSetting(String key, String value, Data data) {
        try {
            switch (key) {
                case "language" -> { if (!value.isBlank()) data.language = value; }
                case "theme" -> data.theme = Style.valueOf(value);
                case "renderQuality" -> {
                    double scale = Double.parseDouble(value);
                    if (scale >= 0.1 && scale <= 1.0) data.renderScale = scale;
                }
                case "vsync" -> data.vsync = Boolean.parseBoolean(value);
                case "openCl" -> data.openCl = Boolean.parseBoolean(value);
                case "doomMovement" -> data.doomMovement = Boolean.parseBoolean(value);
                case "noCeiling" -> data.noCeiling = Boolean.parseBoolean(value);
                case "textMode" -> data.textMode = Boolean.parseBoolean(value);
                case "renderEngine" -> data.renderEngine = parseRenderEngine(value);
                case "renderEngineQuality" -> data.renderEngineQuality = Math.clamp(Integer.parseInt(value), 0, 3);
                case "windowWidth" -> { int w = Integer.parseInt(value); if (w >= 960) data.windowWidth = w; }
                case "windowHeight" -> { int h = Integer.parseInt(value); if (h >= 540) data.windowHeight = h; }
                case "fullscreen" -> data.fullscreen = Boolean.parseBoolean(value);
                case "menuTheme" -> { if (!value.isBlank()) data.menuTheme = value; }
            }
        } catch (IllegalArgumentException ignored) { }
    }

    private static void readMaze(String key, String value, Data data) {
        try {
            switch (key) {
                case "width" -> data.mazeWidth = Math.clamp(Integer.parseInt(value), 5, 1000);
                case "height" -> data.mazeHeight = Math.clamp(Integer.parseInt(value), 5, 1000);
                case "finishMode" -> data.mazeFinishMode = Math.clamp(Integer.parseInt(value), 0, 2);
                case "geometry" -> data.mazeGeometry = Math.clamp(Integer.parseInt(value), 0, 2);
                case "hasFloors" -> data.mazeHasFloors = Boolean.parseBoolean(value);
                case "floors" -> data.mazeFloors = Math.clamp(Integer.parseInt(value), 2, 20);
                case "fog" -> data.mazeFog = Boolean.parseBoolean(value);
            }
        } catch (NumberFormatException ignored) { }
    }

    private static void readVictory(String key, String value, Data data) {
        switch (key) {
            case "rainbow" -> data.victoryRainbow = Boolean.parseBoolean(value);
            case "fireworks" -> data.victoryFireworks = Boolean.parseBoolean(value);
        }
    }

    private static String parseRenderEngine(String value) {
        String trimmed = value.trim();
        if (trimmed.matches("\\d{1,3}")) return RenderEngines.idAt(Integer.parseInt(trimmed));
        return trimmed;
    }
    //endregion

    //region Saving
    public static void saveSettings(String language, Style theme, double renderScale, boolean vsync, String renderEngine, int renderEngineQuality, int windowWidth, int windowHeight, boolean fullscreen, boolean openCl) throws IOException {
        Data data = load();
        data.language = language;
        data.theme = theme;
        data.renderScale = renderScale;
        data.vsync = vsync;
        data.renderEngine = renderEngine == null || renderEngine.isBlank() ? DEFAULT_RENDER_ENGINE : renderEngine.trim();
        data.renderEngineQuality = Math.clamp(renderEngineQuality, 0, 3);
        data.windowWidth = windowWidth >= 960 ? windowWidth : DEFAULT_WINDOW_WIDTH;
        data.windowHeight = windowHeight >= 540 ? windowHeight : DEFAULT_WINDOW_HEIGHT;
        data.fullscreen = fullscreen;
        data.openCl = openCl;
        save(data);
    }
    public static void saveGameplay(boolean doomMovement, boolean noCeiling, boolean textMode) throws IOException {
        Data data = load();
        data.doomMovement = doomMovement;
        data.noCeiling = noCeiling;
        data.textMode = textMode;
        save(data);
    }
    public static void saveMenuTheme(String themeId) throws IOException {
        Data data = load();
        data.menuTheme = themeId == null || themeId.isBlank() ? DEFAULT_MENU_THEME : themeId;
        save(data);
    }
    public static void saveMazeSettings(int width, int height, int finishMode, int geometry, boolean hasFloors, int floors, boolean fog) throws IOException {
        Data data = load();
        data.mazeWidth = Math.clamp(width, 5, 1000);
        data.mazeHeight = Math.clamp(height, 5, 1000);
        data.mazeFinishMode = Math.clamp(finishMode, 0, 2);
        data.mazeGeometry = Math.clamp(geometry, 0, 2);
        data.mazeHasFloors = hasFloors;
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
        data.mapPortalLinks = portalLinks != null ? portalLinks : new ArrayList<>();
        data.mapGeometryMode = geometryMode;
        save(data);
    }
    public static void saveModules(Map<String, String> values) throws IOException {
        Data data = load();
        data.modules = new LinkedHashMap<>(values);
        save(data);
    }
    //endregion

    //region Writing Files
    private static void save(Data data) throws IOException {
        Files.createDirectories(AppPaths.DATA_DIR);
        writeFile(AppPaths.SETTINGS_FILE, settingsText(data));
        writeFile(AppPaths.MAP_FILE, mapText(data));
    }

    private static String settingsText(Data data) {
        StringBuilder text = new StringBuilder();
        text.append("#RCJMS settings ").append(VERSION).append("\n\n");
        text.append("[settings]\n");
        text.append("language=").append(safe(data.language)).append('\n');
        text.append("theme=").append(data.theme.name()).append('\n');
        text.append("renderQuality=").append(formatScale(data.renderScale)).append('\n');
        text.append("vsync=").append(data.vsync).append('\n');
        text.append("renderEngine=").append(data.renderEngine).append('\n');
        text.append("renderEngineQuality=").append(data.renderEngineQuality).append('\n');
        text.append("windowWidth=").append(data.windowWidth).append('\n');
        text.append("windowHeight=").append(data.windowHeight).append('\n');
        text.append("fullscreen=").append(data.fullscreen).append('\n');
        text.append("openCl=").append(data.openCl).append('\n');
        text.append("doomMovement=").append(data.doomMovement).append('\n');
        text.append("noCeiling=").append(data.noCeiling).append('\n');
        text.append("textMode=").append(data.textMode).append('\n');
        text.append("menuTheme=").append(data.menuTheme).append("\n\n");

        text.append("[maze]\n");
        text.append("width=").append(data.mazeWidth).append('\n');
        text.append("height=").append(data.mazeHeight).append('\n');
        text.append("finishMode=").append(data.mazeFinishMode).append('\n');
        text.append("geometry=").append(data.mazeGeometry).append('\n');
        text.append("hasFloors=").append(data.mazeHasFloors).append('\n');
        text.append("floors=").append(data.mazeFloors).append('\n');
        text.append("fog=").append(data.mazeFog).append("\n\n");

        text.append("[victory]\n");
        text.append("rainbow=").append(data.victoryRainbow).append('\n');
        text.append("fireworks=").append(data.victoryFireworks).append("\n\n");

        text.append("[modules]\n");
        for (Map.Entry<String, String> entry : data.modules.entrySet()) {
            text.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
        }
        return text.toString();
    }

    private static String mapText(Data data) {
        StringBuilder text = new StringBuilder();
        text.append("#RCJMS map ").append(VERSION).append("\n\n");
        if (data.mapFloors != null) {
            for (int f = 0; f < data.mapFloors.length; f++) {
                text.append("[floor").append(f).append("]\n");
                int[][] floor = data.mapFloors[f];
                if (floor != null) {
                    for (int[] row : floor) {
                        for (int x = 0; x < row.length; x++) {
                            if (x > 0) text.append(',');
                            text.append(row[x]);
                        }
                        text.append('\n');
                    }
                }
                text.append('\n');
            }
        }
        text.append("[portals]\n");
        if (data.mapPortalLinks != null) {
            for (int[] link : data.mapPortalLinks) {
                for (int i = 0; i < link.length; i++) {
                    if (i > 0) text.append(',');
                    text.append(link[i]);
                }
                text.append('\n');
            }
        }
        text.append("\n[mapmeta]\n");
        text.append("geometry=").append(data.mapGeometryMode).append('\n');
        return text.toString();
    }
    private static void writeFile(Path file, String text) throws IOException {
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temp, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        try { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException exception) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
    }

    private static String formatScale(double scale) {
        if (scale >= 0.99) return "1.0";
        if (scale >= 0.74) return "0.75";
        if (scale >= 0.49) return "0.5";
        return "0.1";
    }
    private static String safe(String value) { return value == null || value.isBlank() ? DEFAULT_LANGUAGE : value.trim(); }
    //endregion

    //region Data Model
    public static final class Data {
        public String language = DEFAULT_LANGUAGE;
        public Style theme = DEFAULT_THEME;
        public double renderScale = DEFAULT_RENDER_SCALE;
        public boolean vsync = DEFAULT_VSYNC;
        public boolean openCl = DEFAULT_OPENCL;
        public int renderEngineQuality = DEFAULT_RENDER_ENGINE_QUALITY;
        public String renderEngine = DEFAULT_RENDER_ENGINE;
        public int windowWidth = DEFAULT_WINDOW_WIDTH;
        public int windowHeight = DEFAULT_WINDOW_HEIGHT;
        public boolean fullscreen = DEFAULT_FULLSCREEN;
        public String menuTheme = DEFAULT_MENU_THEME;
        public boolean doomMovement = false, noCeiling = false, textMode = false;
        public boolean victoryRainbow = true;
        public boolean victoryFireworks = true;
        public int mazeWidth = DEFAULT_MAZE_SIZE, mazeHeight = DEFAULT_MAZE_SIZE;
        public int mazeFinishMode = 0;
        public int mazeGeometry = 0;
        public boolean mazeHasFloors = false;
        public int mazeFloors = DEFAULT_MAZE_FLOORS;
        public boolean mazeFog = false;
        public int[][][] mapFloors;
        public List<int[]> mapPortalLinks = new ArrayList<>();
        public int mapGeometryMode = 0;
        public Map<String, String> modules = new LinkedHashMap<>();
    }
    //endregion
}