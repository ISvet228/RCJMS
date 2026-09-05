package Helpers;

import java.util.*;

public class MazeGenerator {
    public enum FinishMode {OPPOSITE_CORNER, CENTER, RANDOM_EDGE}

    private final int width;
    private final int height;
    private final int[][] maze;
    private final Random random;

    public MazeGenerator(int width, int height) {
        this.width = width % 2 == 0 ? width + 1 : width;
        this.height = height % 2 == 0 ? height + 1 : height;
        maze = new int[this.height][this.width];
        random = new Random();
    }
    public MazeGenerator(int width, int height, long seed) {
        this.width = width % 2 == 0 ? width + 1 : width;
        this.height = height % 2 == 0 ? height + 1 : height;
        maze = new int[this.height][this.width];
        random = new Random(seed);
    }
    public int[][] generate(FinishMode mode) {
        for (int y = 0; y < height; y++) Arrays.fill(maze[y], 1);

        carve(1, 1);
        addBranchesFUN();
        placeFinish(mode);
        return maze;
    }
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
        int extraOpenings = (width * height) / 20;
        for (int i = 0; i < extraOpenings; i++) {
            int x = random.nextInt(width - 2) + 1;
            int y = random.nextInt(height - 2) + 1;

            if (maze[y][x] != 1) continue;
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

        if (!hasPath(fx, fy)) createDirectPath(1, 1, fx, fy);
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
    private void createDirectPath(int sx, int sy, int fx, int fy) {
        int x = sx;
        int y = sy;

        while (x != fx) {
            maze[y][x] = 0;
            x += Integer.compare(fx, x);
        }
        while (y != fy) {
            maze[y][x] = 0;
            y += Integer.compare(fy, y);
        }
        maze[fy][fx] = 0;
    }
}
