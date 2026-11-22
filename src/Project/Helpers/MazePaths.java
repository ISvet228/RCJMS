package Project.Helpers;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;

public final class MazePaths {
    //region Constructors
    private MazePaths() {}
    //endregion

    //region Connecting
    public static void connect(int[][] grid, int fromX, int fromY, int toX, int toY) {
        int height = grid.length, width = grid[0].length;
        if (fromX < 1 || fromY < 1 || toX < 1 || toY < 1 || fromX >= width - 1 || toX >= width - 1 || fromY >= height - 1 || toY >= height - 1) return;
        int[][] cost = new int[height][width];
        int[][] previous = new int[height][width];
        for (int[] row : cost) java.util.Arrays.fill(row, Integer.MAX_VALUE);
        Deque<int[]> queue = new ArrayDeque<>();
        cost[fromY][fromX] = 0;
        previous[fromY][fromX] = -1;
        queue.add(new int[]{fromX, fromY});
        int[][] dirs = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        while (!queue.isEmpty()) {
            int[] cell = queue.pollFirst();
            if (cell[0] == toX && cell[1] == toY) break;
            for (int[] dir : dirs) {
                int nx = cell[0] + dir[0], ny = cell[1] + dir[1];
                if (nx < 1 || ny < 1 || nx >= width - 1 || ny >= height - 1) continue;
                boolean wall = grid[ny][nx] == MazeGenerator.WALL;
                int next = cost[cell[1]][cell[0]] + (wall ? 1 : 0);
                if (next >= cost[ny][nx]) continue;
                cost[ny][nx] = next;
                previous[ny][nx] = cell[1] * width + cell[0];
                if (wall) queue.addLast(new int[]{nx, ny});
                else queue.addFirst(new int[]{nx, ny});
            }
        }
        int x = toX, y = toY;
        while (true) {
            if (grid[y][x] == MazeGenerator.WALL) grid[y][x] = MazeGenerator.OPEN;
            int p = previous[y][x];
            if (p < 0) break;
            x = p % width;
            y = p / width;
        }
    }
    public static void joinTo(int[][] grid, boolean[][] joined, int tx, int ty, Random random) {
        int height = grid.length, width = grid[0].length;
        int[][] cost = new int[height][width];
        int[][] previous = new int[height][width];
        for (int[] row : cost) java.util.Arrays.fill(row, Integer.MAX_VALUE);
        Deque<int[]> queue = new ArrayDeque<>();
        for (int y = 1; y < height - 1; y++)
            for (int x = 1; x < width - 1; x++)
                if (joined[y][x]) { cost[y][x] = 0; previous[y][x] = -1; queue.addFirst(new int[]{x, y}); }
        if (queue.isEmpty()) return;
        int[][] dirs = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        while (!queue.isEmpty()) {
            int[] cell = queue.pollFirst();
            if (cell[0] == tx && cell[1] == ty) break;
            shuffle(dirs, random);
            for (int[] dir : dirs) {
                int nx = cell[0] + dir[0], ny = cell[1] + dir[1];
                if (nx < 1 || ny < 1 || nx >= width - 1 || ny >= height - 1) continue;
                boolean wall = grid[ny][nx] == MazeGenerator.WALL;
                int next = cost[cell[1]][cell[0]] + (wall ? 1 : 0);
                if (next >= cost[ny][nx]) continue;
                cost[ny][nx] = next;
                previous[ny][nx] = cell[1] * width + cell[0];
                if (wall) queue.addLast(new int[]{nx, ny});
                else queue.addFirst(new int[]{nx, ny});
            }
        }
        int x = tx, y = ty;
        while (true) {
            if (grid[y][x] == MazeGenerator.WALL) grid[y][x] = MazeGenerator.OPEN;
            joined[y][x] = true;
            int p = previous[y][x];
            if (p < 0) break;
            x = p % width;
            y = p / width;
        }
    }
    private static void shuffle(int[][] dirs, Random random) {
        for (int i = dirs.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int[] tmp = dirs[i]; dirs[i] = dirs[j]; dirs[j] = tmp;
        }
    }
    //endregion
}