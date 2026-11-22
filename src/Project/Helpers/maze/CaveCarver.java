package Project.Helpers.maze;

import Project.Helpers.*;

import java.util.*;
import java.util.function.DoubleConsumer;

public final class CaveCarver implements MazeAlgorithm {
    //region Variables
    public static final String ID = "caves";
    private static final double OPEN_CHANCE = 0.47;
    private static final int SMOOTH_STEPS = 4, ATTEMPTS = 6, MAX_ATTEMPTS = 80;
    private static final double SPECK_SHARE = 1.0 / 400, MIN_CAVE = 4;
    //endregion

    //region Identity
    @Override public String id() { return ID; }
    @Override public String nameKey() { return "maze.alg.caves"; }
    //endregion

    //region Carving
    @Override public void carve(int[][] grid, Random random, DoubleConsumer progress) {
        int height = grid.length, width = grid[0].length;
        if (width < 5 || height < 5) { grid[1][1] = MazeGenerator.OPEN; return; }
        int interior = (width - 2) * (height - 2);
        int[][] best = null;
        int bestCaves = -1;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            int[][] candidate = noise(width, height, random);
            for (int step = 0; step < SMOOTH_STEPS; step++) candidate = smooth(candidate);
            int caves = joinCaves(candidate, random, Math.max(MIN_CAVE, interior * SPECK_SHARE));
            if (caves > bestCaves) { best = candidate; bestCaves = caves; }
            progress.accept(Math.min(1.0, (attempt + 1) / (double) ATTEMPTS));
            if (attempt + 1 >= ATTEMPTS && bestCaves >= 3) break;
        }
        for (int y = 0; y < height; y++) {
            assert best != null;
            System.arraycopy(best[y], 0, grid[y], 0, width);
        }
    }
    private static int[][] noise(int width, int height, Random random) {
        int[][] cells = new int[height][width];
        for (int y = 0; y < height; y++) Arrays.fill(cells[y], MazeGenerator.WALL);
        for (int y = 1; y < height - 1; y++)
            for (int x = 1; x < width - 1; x++)
                cells[y][x] = random.nextDouble() < OPEN_CHANCE ? MazeGenerator.OPEN : MazeGenerator.WALL;
        return cells;
    }
    private static int[][] smooth(int[][] cells) {
        int height = cells.length, width = cells[0].length;
        int[][] next = new int[height][width];
        for (int y = 0; y < height; y++) Arrays.fill(next[y], MazeGenerator.WALL);
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                int walls = 0;
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++)
                        if (cells[y + dy][x + dx] == MazeGenerator.WALL) walls++;
                next[y][x] = walls >= 5 ? MazeGenerator.WALL : MazeGenerator.OPEN;
            }
        }
        return next;
    }
    private static int joinCaves(int[][] cells, Random random, double minimum) {
        int height = cells.length, width = cells[0].length;
        cells[1][1] = MazeGenerator.OPEN;
        int[][] label = new int[height][width];
        List<List<int[]>> caves = new ArrayList<>();
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                if (cells[y][x] != MazeGenerator.OPEN || label[y][x] != 0) continue;
                List<int[]> cave = new ArrayList<>();
                Deque<int[]> queue = new ArrayDeque<>();
                queue.add(new int[]{x, y});
                label[y][x] = caves.size() + 1;
                while (!queue.isEmpty()) {
                    int[] cell = queue.poll();
                    cave.add(cell);
                    for (int[] dir : MazeCells.DIRECTIONS) {
                        int nx = cell[0] + dir[0], ny = cell[1] + dir[1];
                        if (nx < 1 || ny < 1 || nx >= width - 1 || ny >= height - 1) continue;
                        if (label[ny][nx] != 0 || cells[ny][nx] != MazeGenerator.OPEN) continue;
                        label[ny][nx] = caves.size() + 1;
                        queue.add(new int[]{nx, ny});
                    }
                }
                caves.add(cave);
            }
        }
        int startCave = label[1][1] - 1;
        boolean[][] joined = new boolean[height][width];
        List<Integer> others = new ArrayList<>();
        for (int i = 0; i < caves.size(); i++) {
            if (i == startCave) continue;
            if (caves.get(i).size() < minimum) {
                for (int[] cell : caves.get(i)) cells[cell[1]][cell[0]] = MazeGenerator.WALL;
            } else others.add(i);
        }
        for (int[] cell : caves.get(startCave)) joined[cell[1]][cell[0]] = true;
        Collections.shuffle(others, random);
        int kept = 1;
        for (int index : others) {
            List<int[]> cave = caves.get(index);
            int[] pick = cave.get(random.nextInt(cave.size()));
            MazePaths.joinTo(cells, joined, pick[0], pick[1], random);
            for (int[] cell : cave) joined[cell[1]][cell[0]] = true;
            kept++;
        }
        return kept;
    }
    //endregion
}