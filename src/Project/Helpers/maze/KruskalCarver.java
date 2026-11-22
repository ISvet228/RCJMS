package Project.Helpers.maze;

import Project.Helpers.MazeAlgorithm;

import java.util.*;
import java.util.function.DoubleConsumer;

public final class KruskalCarver implements MazeAlgorithm {
    //region Variables
    public static final String ID = "kruskal";
    //endregion

    //region Identity
    @Override public String id() { return ID; }
    @Override public String nameKey() { return "maze.alg.kruskal"; }
    //endregion

    //region Carving
    @Override public void carve(int[][] grid, Random random, DoubleConsumer progress) {
        int cw = MazeCells.count(grid[0].length), ch = MazeCells.count(grid.length);
        if (cw == 0 || ch == 0) return;
        for (int cy = 0; cy < ch; cy++)
            for (int cx = 0; cx < cw; cx++) MazeCells.openCell(grid, cx, cy);

        List<int[]> edges = new ArrayList<>();
        for (int cy = 0; cy < ch; cy++) {
            for (int cx = 0; cx < cw; cx++) {
                if (cx + 1 < cw) edges.add(new int[]{cx, cy, 1, 0});
                if (cy + 1 < ch) edges.add(new int[]{cx, cy, 0, 1});
            }
        }
        Collections.shuffle(edges, random);

        int[] parent = new int[cw * ch];
        for (int i = 0; i < parent.length; i++) parent[i] = i;
        int done = 0;
        for (int[] edge : edges) {
            int a = find(parent, edge[1] * cw + edge[0]);
            int b = find(parent, (edge[1] + edge[3]) * cw + edge[0] + edge[2]);
            if (a != b) {
                parent[a] = b;
                MazeCells.link(grid, edge[0], edge[1], edge[2], edge[3]);
            }
            if ((++done & 1023) == 0) progress.accept(done / (double) edges.size());
        }
    }
    private static int find(int[] parent, int index) {
        while (parent[index] != index) {
            parent[index] = parent[parent[index]];
            index = parent[index];
        }
        return index;
    }
    //endregion
}