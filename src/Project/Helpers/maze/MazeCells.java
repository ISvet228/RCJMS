package Project.Helpers.maze;

import Project.Helpers.MazeGenerator;

final class MazeCells {
    //region Variables
    static final int[][] DIRECTIONS = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
    //endregion

    //region Constructors
    private MazeCells() {}
    //endregion

    //region Geometry
    static int count(int size) { return Math.max(0, (size - 1) / 2); }
    static void openCell(int[][] grid, int cx, int cy) { grid[2 * cy + 1][2 * cx + 1] = MazeGenerator.OPEN; }
    static void link(int[][] grid, int cx, int cy, int dx, int dy) {
        openCell(grid, cx, cy);
        grid[2 * cy + 1 + dy][2 * cx + 1 + dx] = MazeGenerator.OPEN;
        openCell(grid, cx + dx, cy + dy);
    }
    //endregion
}