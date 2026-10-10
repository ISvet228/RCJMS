package Helpers;

import java.util.*;

public class MazeGenerator3D {
    //region Variables
    public static final int OPEN = 0, WALL = 1, FINISH = 2, STAIRS_DOWN = 3, STAIRS_UP = 4;

    private final int width, height, floorCount;
    private final Long seed;
    private final Random random;
    private final MazeGenerator.GeometryMode geometryMode;

    private int[][][] floors;
    private PortalData[] floorPortals;
    private int[] entryX, entryY;
    private int finishFloor, finishX, finishY;
    //endregion

    //region Constructors
    public MazeGenerator3D(int width, int height, int floorCount, Long seed, MazeGenerator.GeometryMode geometryMode) {
        this.width = width;
        this.height = height;
        this.floorCount = Math.max(2, floorCount);
        this.seed = seed;
        this.random = seed != null ? new Random(seed) : new Random();
        this.geometryMode = geometryMode == null ? MazeGenerator.GeometryMode.EUCLIDEAN : geometryMode;
    }
    //endregion

    //region Public API
    public PortalData getPortals(int floor) { return floorPortals[floor]; }
    public int[][][] generate(MazeGenerator.FinishMode mode) {
        floors = new int[floorCount][][];
        floorPortals = new PortalData[floorCount];
        entryX = new int[floorCount];
        entryY = new int[floorCount];

        for (int f = 0; f < floorCount; f++) {
            Long floorSeed = seed != null ? seed + f * 104729L : null;
            MazeGenerator generator = floorSeed != null ? new MazeGenerator(width, height, floorSeed, geometryMode) : new MazeGenerator(width, height, geometryMode);
            floors[f] = generator.generateRaw();
            floorPortals[f] = generator.getPortals();
        }
        entryX[0] = entryY[0] = 1;

        boolean enforceSeparation = mode != MazeGenerator.FinishMode.RANDOM_EDGE;
        for (int f = 0; f < floorCount - 1; f++) {
            int[] stairPos = pickStairPosition(floors[f], floors[f + 1], entryX[f], entryY[f], enforceSeparation);
            floors[f][stairPos[1]][stairPos[0]] = STAIRS_DOWN;

            ensureOpenAndConnected(floors[f + 1], 1, 1, stairPos[0], stairPos[1]);
            floors[f + 1][stairPos[1]][stairPos[0]] = STAIRS_UP;

            entryX[f + 1] = stairPos[0];
            entryY[f + 1] = stairPos[1];
        }

        placeFinish(mode);
        if (geometryMode == MazeGenerator.GeometryMode.LOOPED) addFloorPortals();
        return floors;
    }
    //endregion

    //region Portals between floors
    private void addFloorPortals() {
        List<List<int[]>> slots = new ArrayList<>();
        for (int f = 0; f < floorCount; f++) {
            List<int[]> usable = new ArrayList<>();
            for (int[] slot : MazeGenerator.findPortalSlots(floors[f])) if (exitIsOpen(f, slot)) usable.add(slot);
            Collections.shuffle(usable, random);
            slots.add(usable);
        }

        List<int[]> added = new ArrayList<>(); //{floorA, ax, ay, floorB, bx, by} aaaaaaaaaaaaaaaaaaaaa
        int pairsPerFloor = Math.max(1, (width * height) / 330);
        for (int f = 0; f < floorCount; f++) {
            for (int n = 0; n < pairsPerFloor; n++) {
                int g = f + 1 < floorCount && random.nextInt(4) != 0 ? f + 1 : random.nextInt(floorCount);
                if (g == f) continue;
                int[] a = takeSlot(f, slots.get(f)), b = takeSlot(g, slots.get(g));
                if (a == null || b == null) continue;

                floors[f][a[1]][a[0]] = MazeGenerator.PORTAL;
                floors[g][b[1]][b[0]] = MazeGenerator.PORTAL;
                PortalData.linkFloors(floorPortals[f], f, a[0], a[1], a[2], a[3], false, floorPortals[g], g, b[0], b[1], b[2], b[3], false);
                added.add(new int[]{f, a[0], a[1], g, b[0], b[1]});
            }
        }

        if (!added.isEmpty() && !(MazeSolver.findReachableFinish(floors, floorPortals, 0, entryX[0], entryY[0]) != null
                && MazeSolver.portalExitsAreOpen(floors, floorPortals))) {
            for (int[] link : added) {
                floors[link[0]][link[2]][link[1]] = MazeGenerator.WALL;
                floors[link[3]][link[5]][link[4]] = MazeGenerator.WALL;
                floorPortals[link[0]].remove(link[1], link[2]);
                floorPortals[link[3]].remove(link[4], link[5]);
            }
        }
    }
    private boolean exitIsOpen(int floor, int[] slot) {
        int bx = slot[0] + slot[2], by = slot[1] + slot[3];
        return bx > 0 && by > 0 && bx < width - 1 && by < height - 1 && floors[floor][by][bx] == OPEN;
    }
    private int[] takeSlot(int floor, List<int[]> slots) {
        while (!slots.isEmpty()) {
            int[] slot = slots.removeLast();
            int deadEndX = slot[0] - slot[2], deadEndY = slot[1] - slot[3];
            if (floors[floor][slot[1]][slot[0]] == WALL && floors[floor][deadEndY][deadEndX] == OPEN) return slot;
        }
        return null;
    }
    //endregion

    //region Helpers
    private int[] pickStairPosition(int[][] floorMaze, int[][] nextFloorMaze, int fromX, int fromY, boolean enforceMinDistance) {
        int[][] dist = bfsDistances(floorMaze, fromX, fromY);
        int minDistance = enforceMinDistance ? Math.max(4, Math.min(width, height) / 2) : 1;

        List<int[]> candidates = new ArrayList<>();
        int maxDistFound = 0;

        for (int y = 0; y < floorMaze.length; y++) {
            for (int x = 0; x < floorMaze[0].length; x++) {
                int d = dist[y][x];
                if (d <= 0) continue;
                if (floorMaze[y][x] != OPEN || nextFloorMaze[y][x] == MazeGenerator.PORTAL) continue;
                maxDistFound = Math.max(maxDistFound, d);
                if (d >= minDistance) candidates.add(new int[]{x, y});
            }
        }

        if (candidates.isEmpty()) {
            for (int y = 0; y < floorMaze.length; y++)
                for (int x = 0; x < floorMaze[0].length; x++)
                    if (dist[y][x] == maxDistFound && maxDistFound > 0 && floorMaze[y][x] == OPEN && nextFloorMaze[y][x] != MazeGenerator.PORTAL) candidates.add(new int[]{x, y});
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
        avoid.add(new int[]{entryX[finishFloor], entryY[finishFloor]});
        if (finishFloor + 1 < floorCount) avoid.add(new int[]{entryX[finishFloor + 1], entryY[finishFloor + 1]});
        return avoid;
    }
    private boolean hasPath(int[][] maze, int fromX, int fromY, int toX, int toY) {
        for (int[] cell : bfsOpenCells(maze, fromX, fromY)) if (cell[0] == toX && cell[1] == toY) return true;
        return false;
    }
    private void ensureOpenAndConnected(int[][] maze, int fromX, int fromY, int toX, int toY) {
        if (maze[toY][toX] != WALL && hasPath(maze, fromX, fromY, toX, toY)) return;
        carveShortConnection(maze, fromX, fromY, toX, toY);
    }
    private void carveShortConnection(int[][] maze, int fromX, int fromY, int toX, int toY) {
        int mazeHeight = maze.length, mazeWidth = maze[0].length;
        boolean[][] reachable = new boolean[mazeHeight][mazeWidth];
        for (int[] cell : bfsOpenCells(maze, fromX, fromY)) reachable[cell[1]][cell[0]] = true;
        if (maze[toY][toX] != WALL && reachable[toY][toX]) return;

        int[][] dirs = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        int[][] parent = new int[mazeHeight][mazeWidth];
        for (int[] row : parent) Arrays.fill(row, -2);
        Deque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{toX, toY});
        parent[toY][toX] = -1;
        int foundX = -1, foundY = -1;

        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            if (reachable[current[1]][current[0]] && (current[0] != toX || current[1] != toY)) { foundX = current[0]; foundY = current[1]; break; }
            for (int[] dir : dirs) {
                int nx = current[0] + dir[0], ny = current[1] + dir[1];
                if (nx < 1 || ny < 1 || nx >= mazeWidth - 1 || ny >= mazeHeight - 1 || parent[ny][nx] != -2) continue;
                if (maze[ny][nx] == MazeGenerator.PORTAL) continue;
                parent[ny][nx] = current[1] * mazeWidth + current[0];
                queue.add(new int[]{nx, ny});
            }
        }

        if (foundX < 0) { carveDirect(maze, fromX, fromY, toX, toY); return; }
        int cx = foundX, cy = foundY;
        while (cx >= 0 && cy >= 0) {
            if (maze[cy][cx] == WALL) maze[cy][cx] = OPEN;
            int p = parent[cy][cx];
            if (p < 0) break;
            cx = p % mazeWidth; cy = p / mazeWidth;
        }
    }
    private void carveDirect(int[][] maze, int fromX, int fromY, int toX, int toY) {
        int x = fromX, y = fromY;
        while (x != toX) { if (maze[y][x] != MazeGenerator.PORTAL) maze[y][x] = OPEN; x += Integer.compare(toX, x); }
        while (y != toY) { if (maze[y][x] != MazeGenerator.PORTAL) maze[y][x] = OPEN; y += Integer.compare(toY, y); }
        if (maze[toY][toX] != MazeGenerator.PORTAL) maze[toY][toX] = OPEN;
    }

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
                if (floorMaze[y][x] != OPEN) continue;

                int distToTarget = Math.abs(x - targetX) + Math.abs(y - targetY);
                if (distToTarget < fallbackScore) { fallbackScore = distToTarget; fallback = new int[]{x, y}; }
                if (minDist[y][x] >= minDistance && distToTarget < bestScore) { bestScore = distToTarget; best = new int[]{x, y}; }
            }
        }

        if (best != null) return best;
        if (fallback != null) return fallback;
        return new int[]{Math.clamp(targetX, 1, mazeWidth - 2), Math.clamp(targetY, 1, mazeHeight - 2)};
    }
    //endregion
}