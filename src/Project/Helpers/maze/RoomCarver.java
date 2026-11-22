package Project.Helpers.maze;

import Project.Helpers.*;

import java.util.*;
import java.util.function.DoubleConsumer;

public final class RoomCarver implements MazeAlgorithm {
    //region Variables
    public static final String ID = "rooms";
    private static final int ROOM_CELLS_PER_ROOM = 150, PLACEMENT_TRIES = 30;
    //endregion

    //region Identity
    @Override public String id() { return ID; }
    @Override public String nameKey() { return "maze.alg.rooms"; }
    //endregion

    //region Carving
    @Override public void carve(int[][] grid, Random random, DoubleConsumer progress) {
        int width = grid[0].length, height = grid.length;
        grid[1][1] = MazeGenerator.OPEN;
        List<int[]> rooms = placeRooms(width, height, random);
        List<int[]> nodes = new ArrayList<>();
        nodes.add(new int[]{1, 1});
        for (int[] room : rooms) {
            openRoom(grid, room);
            nodes.add(new int[]{room[0] + room[2] / 2, room[1] + room[3] / 2});
        }
        progress.accept(0.5);
        joinNodes(grid, nodes, random);
        progress.accept(1.0);
    }
    private static List<int[]> placeRooms(int width, int height, Random random) {
        List<int[]> rooms = new ArrayList<>();
        int target = Math.max(2, (width - 2) * (height - 2) / ROOM_CELLS_PER_ROOM);
        for (int attempt = 0; attempt < target * PLACEMENT_TRIES && rooms.size() < target; attempt++) {
            int rw = 3 + 2 * random.nextInt(3), rh = 3 + 2 * random.nextInt(3);
            int maxX = width - 2 - rw, maxY = height - 2 - rh;
            if (maxX < 1 || maxY < 1) continue;
            int x = 1 + random.nextInt(maxX), y = 1 + random.nextInt(maxY);
            if (overlaps(rooms, x - 1, y - 1, rw + 2, rh + 2)) continue;
            rooms.add(new int[]{x, y, rw, rh});
        }
        return rooms;
    }
    private static boolean overlaps(List<int[]> rooms, int x, int y, int w, int h) {
        for (int[] room : rooms)
            if (x < room[0] + room[2] && room[0] < x + w && y < room[1] + room[3] && room[1] < y + h) return true;
        return false;
    }
    private static void openRoom(int[][] grid, int[] room) {
        for (int y = room[1]; y < room[1] + room[3]; y++)
            for (int x = room[0]; x < room[0] + room[2]; x++) grid[y][x] = MazeGenerator.OPEN;
    }
    private static void joinNodes(int[][] grid, List<int[]> nodes, Random random) {
        int n = nodes.size();
        List<int[]> edges = new ArrayList<>();
        for (int i = 0; i < n; i++)
            for (int j = i + 1; j < n; j++) edges.add(new int[]{i, j, distance(nodes.get(i), nodes.get(j))});
        Collections.shuffle(edges, random);
        edges.sort(Comparator.comparingInt(edge -> edge[2]));

        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;
        for (int[] edge : edges) {
            int a = find(parent, edge[0]), b = find(parent, edge[1]);
            if (a == b) continue;
            parent[a] = b;
            int[] from = nodes.get(edge[0]), to = nodes.get(edge[1]);
            carveL(grid, from, to, random.nextBoolean());
        }
    }
    private static int distance(int[] a, int[] b) { return Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]); }
    private static int find(int[] parent, int index) {
        while (parent[index] != index) {
            parent[index] = parent[parent[index]];
            index = parent[index];
        }
        return index;
    }
    private static void carveL(int[][] grid, int[] from, int[] to, boolean horizontalFirst) {
        int cornerX = horizontalFirst ? to[0] : from[0];
        int cornerY = horizontalFirst ? from[1] : to[1];
        carveLine(grid, from[0], from[1], cornerX, cornerY);
        carveLine(grid, cornerX, cornerY, to[0], to[1]);
    }
    private static void carveLine(int[][] grid, int x0, int y0, int x1, int y1) {
        int x = x0, y = y0;
        grid[y][x] = MazeGenerator.OPEN;
        while (x != x1) { x += Integer.signum(x1 - x); grid[y][x] = MazeGenerator.OPEN; }
        while (y != y1) { y += Integer.signum(y1 - y); grid[y][x] = MazeGenerator.OPEN; }
    }
    //endregion
}