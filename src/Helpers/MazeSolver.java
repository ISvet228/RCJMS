package Helpers;

import java.util.ArrayDeque;

public final class MazeSolver {
    //region Variables
    private static final int WALL = 1, FINISH = 2, STAIRS_UP = 3, STAIRS_DOWN = 4, PORTAL = 5;
    private static final int[][] DIRECTIONS = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
    //endregion

    //region Constructors
    private MazeSolver() {}
    //endregion

    //region Public API
    public static int[] findReachableFinish(int[][][] floors, PortalData[] portals, int startFloor, int startX, int startY) {
        int floorCount = floors.length;
        int height = floors[0].length, width = floors[0][0].length;
        boolean[][][] visited = new boolean[floorCount][height][width];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{startFloor, startX, startY});
        visited[startFloor][startY][startX] = true;

        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            int f = current[0], x = current[1], y = current[2];
            int[][] floorMap = floors[f];
            int cell = floorMap[y][x];

            if (cell == FINISH) return current;

            for (int[] direction : DIRECTIONS) {
                int nx = x + direction[0], ny = y + direction[1];
                if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
                if (floorMap[ny][nx] == WALL) continue;
                visit(visited, queue, f, nx, ny);
            }

            if (cell == STAIRS_UP && f + 1 < floorCount && floors[f + 1][y][x] != WALL) visit(visited, queue, f + 1, x, y);
            if (cell == STAIRS_DOWN && f > 0 && floors[f - 1][y][x] != WALL) visit(visited, queue, f - 1, x, y);

            if (cell == PORTAL && portals != null && f < portals.length && portals[f] != null) {
                PortalData.Portal portal = portals[f].get(x, y);
                if (portal == null) continue;
                int targetFloor = portal.linkedFloor >= 0 ? portal.linkedFloor : f;
                if (targetFloor >= floorCount) continue;
                int ex = portal.exitCellX(), ey = portal.exitCellY();
                if (ex < 0 || ey < 0 || ex >= width || ey >= height) continue;
                if (floors[targetFloor][ey][ex] == WALL) continue;
                visit(visited, queue, targetFloor, ex, ey);
            }
        }
        return null;
    }

    public static boolean portalExitsAreOpen(int[][][] floors, PortalData[] portals) { return findBlockedPortalExit(floors, portals) == null; }
    public static int[] findBlockedPortalExit(int[][][] floors, PortalData[] portals) {
        int height = floors[0].length, width = floors[0][0].length;
        for (int f = 0; f < floors.length && portals != null && f < portals.length; f++) {
            if (portals[f] == null) continue;
            for (PortalData.Portal portal : portals[f].values()) {
                int targetFloor = portal.linkedFloor >= 0 ? portal.linkedFloor : f;
                int ex = portal.exitCellX(), ey = portal.exitCellY();
                boolean blocked = targetFloor >= floors.length || ex < 0 || ey < 0 || ex >= width || ey >= height || floors[targetFloor][ey][ex] == WALL;
                if (blocked) return new int[]{f, portal.x, portal.y};
            }
        }
        return null;
    }
    //endregion

    //region Helpers
    private static void visit(boolean[][][] visited, ArrayDeque<int[]> queue, int f, int x, int y) {
        if (visited[f][y][x]) return;
        visited[f][y][x] = true;
        queue.add(new int[]{f, x, y});
    }
    //endregion
}