package Project.Core;

import Project.Helpers.*;

public interface GameContext {
    //region Player
    double playerX();
    double playerY();
    double cameraAngle();
    double cameraPitch();
    double eyeHeight();
    void moveTo(double x, double y, double angle);
    //endregion

    //region Maze
    int[][] map();
    int[][] floorMap(int floor);
    default PortalData portalsOf(int floor) { return null; }
    int currentFloor();
    int floorCount();
    boolean hasFloors();
    int mazeWidth();
    int mazeHeight();
    String seed();
    boolean isCustomMap();
    int geometry();
    String algorithm();
    int finishMode();
    boolean fogOfWar();
    void startSeeded(String seed, int width, int height, int floors, int geometry, boolean hasFloors, boolean fog, String algorithm, int finishMode);
    void useMaze(MazeData maze, boolean fog);
    void clearMaze();
    //endregion

    boolean isPaused();
    double elapsedSeconds();
    void command(String name, String usage, Project.Game.DevConsole.Command command);
    boolean hudVisible();
    void setHudVisible(boolean visible);
    void flash(String text);
    void restart();
}
