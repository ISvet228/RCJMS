package Project.Helpers.maze;

import Project.Helpers.MazeAlgorithm;

import java.util.*;
import java.util.function.DoubleConsumer;

public final class EllerCarver implements MazeAlgorithm {
    //region Variables
    public static final String ID = "eller";
    //endregion

    //region Identity
    @Override public String id() { return ID; }
    @Override public String nameKey() { return "maze.alg.eller"; }
    //endregion

    //region Carving
    @Override public void carve(int[][] grid, Random random, DoubleConsumer progress) {
        int cw = MazeCells.count(grid[0].length), ch = MazeCells.count(grid.length);
        if (cw == 0 || ch == 0) return;
        int[] rowSets = new int[cw];
        int nextSet = 0;
        for (int cx = 0; cx < cw; cx++) rowSets[cx] = nextSet++;

        for (int cy = 0; cy < ch; cy++) {
            boolean last = cy == ch - 1;
            for (int cx = 0; cx < cw; cx++) MazeCells.openCell(grid, cx, cy);
            joinRow(grid, random, rowSets, cy, last);
            if (last) break;

            int[] below = new int[cw];
            Arrays.fill(below, -1);
            Map<Integer, List<Integer>> groups = new TreeMap<>();
            for (int cx = 0; cx < cw; cx++) groups.computeIfAbsent(rowSets[cx], k -> new ArrayList<>()).add(cx);
            for (List<Integer> members : groups.values()) {
                Collections.shuffle(members, random);
                int down = 1 + random.nextInt(members.size());
                for (int i = 0; i < down; i++) {
                    int cx = members.get(i);
                    MazeCells.link(grid, cx, cy, 0, 1);
                    below[cx] = rowSets[cx];
                }
            }
            for (int cx = 0; cx < cw; cx++) if (below[cx] < 0) below[cx] = nextSet++;
            rowSets = below;
            progress.accept((cy + 1) / (double) ch);
        }
    }
    private static void joinRow(int[][] grid, Random random, int[] rowSets, int cy, boolean last) {
        for (int cx = 0; cx + 1 < rowSets.length; cx++) {
            int a = rowSets[cx], b = rowSets[cx + 1];
            if (a == b) continue;
            if (last || random.nextBoolean()) {
                MazeCells.link(grid, cx, cy, 1, 0);
                for (int i = 0; i < rowSets.length; i++) if (rowSets[i] == b) rowSets[i] = a;
            }
        }
    }
    //endregion
}