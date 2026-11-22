package Project.MainMenu;

import Project.Helpers.Settings;

import Project.Horror.HorrorModule;
import Project.Helpers.SeedUtil;
import Project.Net.MazeSpec;

final class MainMenuMultiplayerSpec {
    //region Constructors
    private MainMenuMultiplayerSpec() {}
    //endregion

    //region Building
    static MazeSpec fromMaze(MazeSettingsPanel maze) {
        String seed = maze.seed();
        boolean horror = Settings.getBoolean(HorrorModule.SETTING, false);
        return new MazeSpec(seed == null ? SeedUtil.randomSeed() : seed, odd(maze.mazeWidth()), odd(maze.mazeHeight()),
                maze.layers(), maze.geometry(), maze.hasFloors(), maze.fogMinimap(), maze.algorithm().id(), maze.finishMode(), horror);
    }
    static int odd(int size) { return size % 2 == 0 ? size + 1 : size; }
    //endregion
}