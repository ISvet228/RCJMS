package Helpers;

import java.util.*;

public class MazeGenerator3D {
    public static final int OPEN = 0, WALL = 1, FINISH = 2, STAIRS_DOWN = 3, STAIRS_UP = 4;

    private final int width, height, floorCount;
    private final Long seed;
    private final Random random;
    private final MazeGenerator.GeometryMode geometryMode;

    private int[][][] floors;
    private int[] entryX, entryY;
    private int finishFloor, finishX, finishY;

    //region Constructors
    public MazeGenerator3D(int width, int height, int floorCount, MazeGenerator.GeometryMode geometryMode) {
        this(width, height, floorCount, null, geometryMode);}
    public MazeGenerator3D(int width, int height, int floorCount, Long seed, MazeGenerator.GeometryMode geometryMode) {
        this.width = width;
        this.height = height;
        this.floorCount = Math.max(2, floorCount);
        this.seed = seed;
        this.random = seed != null ? new Random(seed) : new Random();
        this.geometryMode = geometryMode == null ? MazeGenerator.GeometryMode.EUCLIDEAN : geometryMode;
    }
    public int[][][] generate(MazeGenerator.FinishMode mode) {
        floors = new int[floorCount][][];
        entryX = new int[floorCount];
        entryY = new int[floorCount];

        for (int f = 0; f < floorCount; f++) {
            Long floorSeed = seed != null ? seed + f * 104729L : null;
            MazeGenerator generator = floorSeed != null ? new MazeGenerator(width, height, floorSeed, geometryMode) : new MazeGenerator(width, height, geometryMode);
            floors[f] = generator.generateRaw();
        }
        entryX[0] = entryY[0] = 1;

        boolean enforceSeparation = mode != MazeGenerator.FinishMode.RANDOM_EDGE;
        for (int f = 0; f < floorCount - 1; f++) {
            int[] stairPos = pickStairPosition(floors[f], entryX[f], entryY[f], enforceSeparation);
            floors[f][stairPos[1]][stairPos[0]] = STAIRS_DOWN;

            ensureOpenAndConnected(floors[f + 1], 1, 1, stairPos[0], stairPos[1]);
            floors[f + 1][stairPos[1]][stairPos[0]] = STAIRS_UP;

            entryX[f + 1] = stairPos[0];
            entryY[f + 1] = stairPos[1];
        }

        placeFinish(mode);
        return floors;
    }
    //endregion

    //region Helpers
    private int[] pickStairPosition(int[][] floorMaze, int fromX, int fromY, boolean enforceMinDistance) {
        int[][] dist = bfsDistances(floorMaze, fromX, fromY);
        int minDistance = enforceMinDistance ? Math.max(4, Math.min(width, height) / 2) : 1;

        List<int[]> candidates = new ArrayList<>();
        int maxDistFound = 0;

        for (int y = 0; y < floorMaze.length; y++) {
            for (int x = 0; x < floorMaze[0].length; x++) {
                int d = dist[y][x];
                if (d <= 0) continue;
                maxDistFound = Math.max(maxDistFound, d);
                if (d >= minDistance) candidates.add(new int[]{x, y});
            }
        }

        if (candidates.isEmpty()) {
            for (int y = 0; y < floorMaze.length; y++)
                for (int x = 0; x < floorMaze[0].length; x++)
                    if (dist[y][x] == maxDistFound && maxDistFound > 0) candidates.add(new int[]{x, y});
        }

        if (candidates.isEmpty()) return new int[]{fromX, fromY};
        return candidates.get(random.nextInt(candidates.size()));
    }
    private List<int[]> bfsOpenCells(int[][] maze, int startX, int startY) {
        List<int[]> result = new ArrayList<>();
        int mazeHeight = maze.length, mazeWidth = maze[0].length;
        boolean[][] visited = new boolean[mazeHeight][mazeWidth];
        Deque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{startX, startY});
        visited[startY][startX] = true;
        int[][] dirs = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            result.add(current);
            for (int[] dir : dirs) {
                int nx = current[0] + dir[0], ny = current[1] + dir[1];
                if (nx >= 0 && ny >= 0 && nx < mazeWidth && ny < mazeHeight && !visited[ny][nx] && maze[ny][nx] != WALL) {
                    visited[ny][nx] = true;
                    queue.add(new int[]{nx, ny});
                }
            }
        }
        return result;
    }
    private int[][] bfsDistances(int[][] maze, int startX, int startY) {
        int mazeHeight = maze.length, mazeWidth = maze[0].length;
        int[][] dist = new int[mazeHeight][mazeWidth];
        for (int[] row : dist) Arrays.fill(row, -1);

        Deque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{startX, startY});
        dist[startY][startX] = 0;
        int[][] dirs = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            int cx = current[0], cy = current[1];
            for (int[] dir : dirs) {
                int nx = cx + dir[0], ny = cy + dir[1];
                if (nx >= 0 && ny >= 0 && nx < mazeWidth && ny < mazeHeight && dist[ny][nx] == -1 && maze[ny][nx] != WALL) {
                    dist[ny][nx] = dist[cy][cx] + 1;
                    queue.add(new int[]{nx, ny});
                }
            }
        }
        return dist;
    }
    private int[][] combinedMinDistance(int[][] floorMaze, List<int[]> avoidPoints) {
        int mazeHeight = floorMaze.length, mazeWidth = floorMaze[0].length;
        int[][] result = new int[mazeHeight][mazeWidth];
        for (int[] row : result) Arrays.fill(row, Integer.MAX_VALUE);

        for (int[] point : avoidPoints) {
            int[][] dist = bfsDistances(floorMaze, point[0], point[1]);
            for (int y = 0; y < mazeHeight; y++)
                for (int x = 0; x < mazeWidth; x++)
                    if (dist[y][x] >= 0) result[y][x] = Math.min(result[y][x], dist[y][x]);
        }
        return result;
    }
    private List<int[]> collectAvoidPoints(int finishFloor) {
        List<int[]> avoid = new ArrayList<>();
        avoid.add(new int[]{entryX[finishFloor], entryY[finishFloor]}); //where you arrive on this floor (start or stairs up)
        if (finishFloor + 1 < floorCount) avoid.add(new int[]{entryX[finishFloor + 1], entryY[finishFloor + 1]}); //this floor's stairs down
        return avoid;
    }
    private boolean hasPath(int[][] maze, int fromX, int fromY, int toX, int toY) {
        for (int[] cell : bfsOpenCells(maze, fromX, fromY)) if (cell[0] == toX && cell[1] == toY) return true;
        return false;
    }
    private void ensureOpenAndConnected(int[][] maze, int fromX, int fromY, int toX, int toY) {
        if (maze[toY][toX] == WALL || !hasPath(maze, fromX, fromY, toX, toY)) carveDirect(maze, fromX, fromY, toX, toY);
    }
    private void carveDirect(int[][] maze, int fromX, int fromY, int toX, int toY) {
        int x = fromX, y = fromY;
        while (x != toX) { maze[y][x] = OPEN; x += Integer.compare(toX, x); }
        while (y != toY) { maze[y][x] = OPEN; y += Integer.compare(toY, y); }
        maze[toY][toX] = OPEN;
    }
    //endregion

    private void placeFinish(MazeGenerator.FinishMode mode) {
        int mazeHeight = floors[0].length, mazeWidth = floors[0][0].length;

        switch (mode) {
            case OPPOSITE_CORNER -> {
                finishFloor = floorCount - 1;
                int[] picked = pickTargetFinish(floors[finishFloor], finishFloor, mazeWidth - 2, mazeHeight - 2, mazeWidth, mazeHeight);
                finishX = picked[0];
                finishY = picked[1];
            }
            case CENTER -> {
                finishFloor = floorCount / 2;
                int[] picked = pickTargetFinish(floors[finishFloor], finishFloor, mazeWidth / 2, mazeHeight / 2, mazeWidth, mazeHeight);
                finishX = picked[0];
                finishY = picked[1];
            }
            case RANDOM_EDGE -> {
                finishFloor = floorCount == 1 ? 0 : 1 + random.nextInt(floorCount - 1);
                int[][] floorMaze = floors[finishFloor];
                List<int[]> edgeDeadEnds = new ArrayList<>();

                for (int y = 1; y < mazeHeight - 1; y++) {
                    for (int x = 1; x < mazeWidth - 1; x++) {
                        if (floorMaze[y][x] != OPEN) continue;
                        boolean nearEdge = x == 1 || y == 1 || x == mazeWidth - 2 || y == mazeHeight - 2;
                        if (!nearEdge) continue;

                        int exits = 0;
                        if (floorMaze[y - 1][x] == OPEN) exits++;
                        if (floorMaze[y + 1][x] == OPEN) exits++;
                        if (floorMaze[y][x - 1] == OPEN) exits++;
                        if (floorMaze[y][x + 1] == OPEN) exits++;
                        if (exits == 1) edgeDeadEnds.add(new int[]{x, y});
                    }
                }
                if (!edgeDeadEnds.isEmpty()) {
                    int[] pos = edgeDeadEnds.get(random.nextInt(edgeDeadEnds.size()));
                    finishX = pos[0];
                    finishY = pos[1];
                } else {
                    finishX = mazeWidth - 2;
                    finishY = mazeHeight - 2;
                }
            }
        }

        int[][] finishMaze = floors[finishFloor];
        ensureOpenAndConnected(finishMaze, entryX[finishFloor], entryY[finishFloor], finishX, finishY);
        finishMaze[finishY][finishX] = FINISH;
    }
    private int[] pickTargetFinish(int[][] floorMaze, int finishFloor, int targetX, int targetY, int mazeWidth, int mazeHeight) {
        List<int[]> avoidPoints = collectAvoidPoints(finishFloor);
        int[][] minDist = combinedMinDistance(floorMaze, avoidPoints);
        int minDistance = Math.max(4, Math.min(width, height) / 2);

        int[] best = null, fallback = null;
        int bestScore = Integer.MAX_VALUE, fallbackScore = Integer.MAX_VALUE;

        for (int y = 1; y < mazeHeight - 1; y++) {
            for (int x = 1; x < mazeWidth - 1; x++) {
                if (floorMaze[y][x] == WALL) continue;

                int distToTarget = Math.abs(x - targetX) + Math.abs(y - targetY);
                if (distToTarget < fallbackScore) { fallbackScore = distToTarget; fallback = new int[]{x, y}; }
                if (minDist[y][x] >= minDistance && distToTarget < bestScore) { bestScore = distToTarget; best = new int[]{x, y}; }
            }
        }

        if (best != null) return best;
        if (fallback != null) return fallback;
        return new int[]{Math.clamp(targetX, 1, mazeWidth - 2), Math.clamp(targetY, 1, mazeHeight - 2)};
    }
}