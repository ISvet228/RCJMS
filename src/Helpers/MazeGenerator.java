package Helpers;

import java.util.*;

public class MazeGenerator {
    public enum FinishMode { OPPOSITE_CORNER, CENTER, RANDOM_EDGE }
    public enum GeometryMode { EUCLIDEAN, WRONG, LOOPED}

    public static final int OPEN = 0, WALL = 1, FINISH = 2, PORTAL = 5;

    private final int width, height;
    private final int[][] maze;
    private final Random random;
    private final GeometryMode geometryMode;
    private final PortalData portals = new PortalData();
    public PortalData getPortals() { return portals; }

    //region Constructors
    public MazeGenerator(int width, int height, GeometryMode geometryMode) {
        this.width = width % 2 == 0 ? width + 1 : width;
        this.height = height % 2 == 0 ? height + 1 : height;
        maze = new int[this.height][this.width];
        random = new Random();
        this.geometryMode = geometryMode == null ? GeometryMode.EUCLIDEAN : geometryMode;
    }
    public MazeGenerator(int width, int height, long seed, GeometryMode geometryMode) {
        this.width = width % 2 == 0 ? width + 1 : width;
        this.height = height % 2 == 0 ? height + 1 : height;
        maze = new int[this.height][this.width];
        random = new Random(seed);
        this.geometryMode = geometryMode == null ? GeometryMode.EUCLIDEAN : geometryMode;
    }
    public int[][] generate(FinishMode mode) {
        generateRaw();
        placeFinish(mode);
        return maze;
    }
    public int[][] generateRaw() {
        for (int y = 0; y < height; y++) Arrays.fill(maze[y], 1);
        carve(1, 1);
        addBranchesFUN();
        generatePortals();
        return maze;
    }
    //endregion

    //region Portals
    private void generatePortals() {
        if (geometryMode != GeometryMode.LOOPED) return;

        List<int[]> deadEnds = findDeadEnds();
        if (deadEnds.isEmpty()) return;
        Collections.shuffle(deadEnds, random);

        int used = 0;
        int maxPairs = Math.max(1, (width * height) / 220);

        int[] doorEnd = deadEnds.removeLast();
        int roomSize = 5;
        int[] block = null;
        while (roomSize >= 3 && block == null) {
            block = findFreeBlock(roomSize);
            if (block == null) roomSize--;
        }
        if (block != null) {
            for (int yy = block[1]; yy < block[1] + roomSize; yy++)
                for (int xx = block[0]; xx < block[0] + roomSize; xx++)
                    maze[yy][xx] = OPEN;

            int exitX = block[0] + roomSize / 2, exitY = block[1] - 1;
            int[] roomExit = {exitX, exitY, 0, -1};
            linkPortalPair(doorEnd, roomExit);
            used++;
        }

        for (int i = 0; i + 1 < deadEnds.size() && used < maxPairs; i += 2) {
            linkPortalPair(deadEnds.get(i), deadEnds.get(i + 1));
            used++;
        }
    }
    private List<int[]> findDeadEnds() {
        List<int[]> result = new ArrayList<>();
        int[][] dirs = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};

        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                if (maze[y][x] != OPEN) continue;
                if (x == 1 && y == 1) continue;

                int exits = 0, toNeighborX = 0, toNeighborY = 0;
                for (int[] dir : dirs) {
                    int ax = x + dir[0], ay = y + dir[1];
                    if (ax < 0 || ay < 0 || ax >= width || ay >= height) continue;
                    if (maze[ay][ax] == OPEN) { exits++; toNeighborX = dir[0]; toNeighborY = dir[1]; }
                }
                if (exits != 1) continue;

                int normalDx = -toNeighborX, normalDy = -toNeighborY;
                int px = x + normalDx, py = y + normalDy;
                if (px <= 0 || py <= 0 || px >= width - 1 || py >= height - 1) continue;
                if (maze[py][px] != WALL) continue;

                int tx = -normalDy, ty = normalDx;
                if (!isWallFlanked(x, y, tx, ty) || !isWallFlanked(px, py, tx, ty)) continue;

                result.add(new int[]{px, py, normalDx, normalDy});
            }
        }
        return result;
    }
    private boolean isWallFlanked(int cx, int cy, int tx, int ty) {
        int lx = cx + tx, ly = cy + ty;
        int rx = cx - tx, ry = cy - ty;
        if (lx < 0 || ly < 0 || lx >= width || ly >= height || maze[ly][lx] != WALL) return false;
        if (rx < 0 || ry < 0 || rx >= width || ry >= height || maze[ry][rx] != WALL) return false;
        return true;
    }
    private int[] findFreeBlock(int size) {
        for (int y = 2; y + size < height - 2; y++) {
            outer:
            for (int x = 2; x + size < width - 2; x++) {for (int yy = y - 1; yy <= y + size; yy++)
                for (int xx = x - 1; xx <= x + size; xx++) if (maze[yy][xx] != WALL)
                    continue outer;
                return new int[]{x, y};
            }
        }
        return null;
    }
    private void linkPortalPair(int[] a, int[] b) {
        maze[a[1]][a[0]] = PORTAL;
        maze[b[1]][b[0]] = PORTAL;
        portals.link(a[0], a[1], a[2], a[3], b[0], b[1], b[2], b[3]);
    }
    //endregion

    //region Helpers
    private void carve(int x, int y) {
        maze[y][x] = 0;
        int[][] dirs = {{0, -2}, {2, 0}, {0, 2}, {-2, 0}};
        List<int[]> directions = Arrays.asList(dirs);
        Collections.shuffle(directions, random);

        for (int[] dir : directions) {
            int nx = x + dir[0];
            int ny = y + dir[1];

            if (nx > 0 && ny > 0 && nx < width - 1 && ny < height - 1 && maze[ny][nx] == 1) {
                maze[y + dir[1] / 2][x + dir[0] / 2] = 0;
                carve(nx, ny);
            }
        }
    }
    private void addBranchesFUN() {
        boolean wrongGeometry = geometryMode == GeometryMode.WRONG;
        int extraOpenings = wrongGeometry ? (width * height) / 10 : (width * height) / 20;

        for (int i = 0; i < extraOpenings; i++) {
            int x = random.nextInt(width - 2) + 1;
            int y = random.nextInt(height - 2) + 1;

            if (maze[y][x] != 1) continue;

            if (wrongGeometry) {
                double normalizedDist = Math.hypot(x - 1, y - 1) / Math.hypot(width, height);
                double acceptWeight = Math.tanh(normalizedDist * 3.0);
                if (random.nextDouble() > acceptWeight) continue;
            }

            int openSides = 0;
            if (maze[y - 1][x] == 0) openSides++;
            if (maze[y + 1][x] == 0) openSides++;
            if (maze[y][x - 1] == 0) openSides++;
            if (maze[y][x + 1] == 0) openSides++;
            if (openSides >= 2) maze[y][x] = 0;
        }
    }
    private void placeFinish(FinishMode mode) {
        int fx = width - 2;
        int fy = height - 2;

        switch (mode) {
            case OPPOSITE_CORNER:
                fx = width - 2;
                fy = height - 2;
                break;
            case CENTER:
                fx = width / 2;
                fy = height / 2;
                while (maze[fy][fx] == 1) {
                    fx += random.nextBoolean() ? 1 : -1;
                    fy += random.nextBoolean() ? 1 : -1;
                    fx = Math.clamp(fx, 1, width - 2);
                    fy = Math.clamp(fy, 1, height - 2);
                }
                break;
            case RANDOM_EDGE:
                List<int[]> edgeDeadEnds = new ArrayList<>();
                for (int y = 1; y < height - 1; y++) {
                    for (int x = 1; x < width - 1; x++) {
                        if (maze[y][x] != 0) continue;
                        if (x == 1 && y == 1) continue;
                        boolean nearEdge = x == 1 || y == 1 || x == width - 2 || y == height - 2;

                        if (!nearEdge) continue;

                        int exits = 0;
                        if (maze[y - 1][x] == 0) exits++;
                        if (maze[y + 1][x] == 0) exits++;
                        if (maze[y][x - 1] == 0) exits++;
                        if (maze[y][x + 1] == 0) exits++;
                        if (exits == 1) edgeDeadEnds.add(new int[]{x, y});
                    }
                }

                if (!edgeDeadEnds.isEmpty()) {
                    int[] pos = edgeDeadEnds.get(random.nextInt(edgeDeadEnds.size()));
                    fx = pos[0];
                    fy = pos[1];
                } else {
                    fx = width - 2;
                    fy = height - 2;
                }
                break;
        }

        if (!hasPath(fx, fy)) createDirectPath(fx, fy);
        maze[fy][fx] = 2;
    }
    private boolean hasPath(int fx, int fy) {
        boolean[][] visited = new boolean[height][width];
        Queue<int[]> queue = new LinkedList<>();
        queue.add(new int[]{1, 1});
        visited[1][1] = true;
        int[][] dirs = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            int x = current[0];
            int y = current[1];
            if (x == fx && y == fy) return true;

            for (int[] dir : dirs) {
                int nx = x + dir[0];
                int ny = y + dir[1];

                if (nx >= 0 && ny >= 0 && nx < width && ny < height && !visited[ny][nx] && maze[ny][nx] != 1) {
                    visited[ny][nx] = true;
                    queue.add(new int[]{nx, ny});
                }
            }
        }
        return false;
    }
    private void createDirectPath(int fx, int fy) {
        int x = 1;
        int y = 1;

        while (x != fx) {
            if (maze[y][x] != PORTAL) maze[y][x] = 0;
            x += Integer.compare(fx, x);
        }
        while (y != fy) {
            if (maze[y][x] != PORTAL) maze[y][x] = 0;
            y += Integer.compare(fy, y);
        }
        if (maze[fy][fx] != PORTAL) maze[fy][fx] = 0;
    }
}