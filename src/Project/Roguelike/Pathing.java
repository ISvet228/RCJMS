package Project.Roguelike;

import Project.Helpers.*;
import java.util.*;

public final class Pathing {
    //region Variables
    private static final int[][] STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    //endregion

    //region Constructors
    private Pathing() {}
    //endregion

    //region Cells
    public static boolean inside(int[][] map, int x, int y) { return y >= 0 && y < map.length && x >= 0 && x < map[y].length; }
    public static boolean walkable(int[][] map, int x, int y) {
        if (!inside(map, x, y)) return false;
        int cell = map[y][x];
        return cell != MazeGenerator.WALL && cell != MazeGenerator.FINISH && cell != MazeGenerator.PORTAL;
    }
    public static boolean blocksSight(int[][] map, int x, int y) { return !inside(map, x, y) || map[y][x] == MazeGenerator.WALL; }
    //endregion

    //region Paths
    public static int[] path(int[][] map, int startX, int startY, int goalX, int goalY) { return path(map, null, startX, startY, goalX, goalY); }
    public static int[] path(int[][] map, PortalData portals, int startX, int startY, int goalX, int goalY) {
        if (!inside(map, startX, startY) || !inside(map, goalX, goalY)) return new int[0];
        if (startX == goalX && startY == goalY) return new int[]{startX, startY};
        int width = 0;
        for (int[] row : map) width = Math.max(width, row.length);
        int[] previous = new int[map.length * width];
        Arrays.fill(previous, -1);
        int startIndex = startY * width + startX, goalIndex = goalY * width + goalX;
        previous[startIndex] = startIndex;
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(startIndex);
        while (!queue.isEmpty() && previous[goalIndex] < 0) {
            int current = queue.poll();
            int cx = current % width, cy = current / width;
            PortalData.Portal portal = portals != null && map[cy][cx] == MazeGenerator.PORTAL ? portals.get(cx, cy) : null;
            if (portal != null && previous[current] != current) {
                int before = previous[current];
                boolean fromCorridor = before % width == cx - portal.dirX && before / width == cy - portal.dirY;
                if (fromCorridor) {
                    int ex = portal.linkedX + portal.linkedDirX, ey = portal.linkedY + portal.linkedDirY;
                    int exit = ey * width + ex;
                    if (inside(map, ex, ey) && previous[exit] < 0 && walkable(map, ex, ey)) {
                        previous[exit] = current;
                        queue.add(exit);
                    }
                    continue;
                }
            }
            for (int[] step : STEPS) {
                int nx = cx + step[0], ny = cy + step[1];
                if (!inside(map, nx, ny)) continue;
                int next = ny * width + nx;
                if (previous[next] >= 0) continue;
                boolean linkedPortal = portals != null && map[ny][nx] == MazeGenerator.PORTAL && portals.get(nx, ny) != null;
                if (next != goalIndex && !walkable(map, nx, ny) && !linkedPortal) continue;
                previous[next] = current;
                queue.add(next);
            }
        }
        if (previous[goalIndex] < 0) return new int[0];
        return rebuild(previous, width, startIndex, goalIndex);
    }

    private static int[] rebuild(int[] previous, int width, int startIndex, int goalIndex) {
        int length = 0;
        for (int at = goalIndex; ; at = previous[at]) {
            length++;
            if (at == startIndex) break;
        }
        int[] path = new int[length * 2];
        int at = goalIndex;
        for (int i = length - 1; i >= 0; i--) {
            path[i * 2] = at % width;
            path[i * 2 + 1] = at / width;
            at = previous[at];
        }
        return path;
    }
    //endregion

    //region Sight
    public static boolean lineOfSight(int[][] map, int x0, int y0, int x1, int y1) {
        int dx = Math.abs(x1 - x0), sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0), sy = y0 < y1 ? 1 : -1;
        int error = dx + dy;
        int x = x0, y = y0;
        while (x != x1 || y != y1) {
            int doubled = 2 * error;
            if (doubled >= dy) { error += dy; x += sx; }
            if (doubled <= dx) { error += dx; y += sy; }
            if (x == x1 && y == y1) return true;
            if (blocksSight(map, x, y)) return false;
        }
        return true;
    }
    //endregion
}