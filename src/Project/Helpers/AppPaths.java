package Project.Helpers;

import java.nio.file.*;

public final class AppPaths {
    //region Variables
    //region Folders
    private static final String APP_FOLDER_NAME = "RCJMS";
    public static final Path DATA_DIR = resolveDataDir();
    //endregion
    //region Files
    public static final Path SETTINGS_FILE = DATA_DIR.resolve("settings.prt");
    public static final Path MAP_FILE = DATA_DIR.resolve("map.prt");
    public static final Path ACHIEVEMENTS_FILE = DATA_DIR.resolve("achivements.prt");
    public static final Path LEADERBOARD_FILE = DATA_DIR.resolve("leaderboard.prt");
    public static final Path WALL_TEXTURE_FILE = DATA_DIR.resolve("walltexture.rctx");
    public static final Path FLOOR_TEXTURE_FILE = DATA_DIR.resolve("floortexture.rctx");
    public static final Path CEILING_TEXTURE_FILE = DATA_DIR.resolve("ceilingtexture.rctx");
    public static final Path FINISH_TEXTURE_FILE = DATA_DIR.resolve("finishtexture.rctx");
    //endregion
    //endregion

    //region Constructors
    private AppPaths() { }
    //endregion

    //region Paths
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
        return base.resolve(APP_FOLDER_NAME);
    }
    public static Path file(String name) { return DATA_DIR.resolve(name); }
    //endregion
}