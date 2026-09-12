package Helpers;

import java.io.IOException;
import java.nio.file.*;

public final class AppPaths {
    private static final String APP_FOLDER_NAME = "RCJMS";
    public static final Path DATA_DIR = resolveDataDir();
    public static final Path THEME_FILE = DATA_DIR.resolve("theme.txt");
    public static final Path LANGUAGE_FILE = DATA_DIR.resolve("language.txt");
    public static final Path MAP_FILE = DATA_DIR.resolve("map.txt");
    public static final Path WALL_TEXTURE_FILE = DATA_DIR.resolve("walltexture.rtex");
    public static final Path FLOOR_TEXTURE_FILE = DATA_DIR.resolve("floortexture.rtex");
    public static final Path CEILING_TEXTURE_FILE = DATA_DIR.resolve("ceilingtexture.rtex");
    public static final Path FINISH_TEXTURE_FILE = DATA_DIR.resolve("finishtexture.rtex");

    private AppPaths() { }
    public static Path file(String name) { return DATA_DIR.resolve(name); }
    private static Path resolveDataDir() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String userHome = System.getProperty("user.home", ".");
        Path base;

        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            base = (appData != null && !appData.isBlank()) ? Paths.get(appData) : Paths.get(userHome, "AppData", "Roaming");
        }
        else if (os.contains("mac"))  base = Paths.get(userHome, "Library", "Application Support");
        else {
            String xdgData = System.getenv("XDG_DATA_HOME");
            base = (xdgData != null && !xdgData.isBlank()) ? Paths.get(xdgData) : Paths.get(userHome, ".local", "share");
        }

        Path dataDir = base.resolve(APP_FOLDER_NAME);
        try { Files.createDirectories(dataDir); } catch (IOException e) {
            dataDir = Paths.get(userHome, "." + APP_FOLDER_NAME.toLowerCase());
            try { Files.createDirectories(dataDir); }
            catch (IOException ignored) { throw new RuntimeException("Omegatron Fotla"); }
        }
        return dataDir;
    }
}