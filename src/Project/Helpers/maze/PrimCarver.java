package Project.Helpers.maze;

import Project.Helpers.MazeAlgorithm;

import java.util.*;
import java.util.function.DoubleConsumer;

public final class PrimCarver implements MazeAlgorithm {
    //region Variables
    public static final String ID = "prim";
    //endregion

    //region Identity
    @Override public String id() { return ID; }
    @Override public String nameKey() { return "maze.alg.prim"; }
    //endregion

    //region Carving
    @Override public void carve(int[][] grid, Random random, DoubleConsumer progress) {
        int cw = MazeCells.count(grid[0].length), ch = MazeCells.count(grid.length);
        if (cw == 0 || ch == 0) return;
        boolean[][] inTree = new boolean[ch][cw];
        List<int[]> frontier = new ArrayList<>();
        int total = cw * ch, reached = 1;

        int startX = random.nextInt(cw), startY = random.nextInt(ch);
        inTree[startY][startX] = true;
        MazeCells.openCell(grid, startX, startY);
        addFrontier(frontier, inTree, startX, startY);
        while (!frontier.isEmpty()) {
            int index = random.nextInt(frontier.size());
            int[] edge = frontier.get(index);
            frontier.set(index, frontier.getLast());
            frontier.removeLast();

            int tx = edge[0] + edge[2], ty = edge[1] + edge[3];
            if (inTree[ty][tx]) continue;
            MazeCells.link(grid, edge[0], edge[1], edge[2], edge[3]);
            inTree[ty][tx] = true;
            if ((++reached & 255) == 0) progress.accept(reached / (double) total);
            addFrontier(frontier, inTree, tx, ty);
        }
    }
    private static void addFrontier(List<int[]> frontier, boolean[][] inTree, int cx, int cy) {
        for (int[] dir : MazeCells.DIRECTIONS) {
            int nx = cx + dir[0], ny = cy + dir[1];
            if (nx >= 0 && ny >= 0 && nx < inTree[0].length && ny < inTree.length && !inTree[ny][nx]) frontier.add(new int[]{cx, cy, dir[0], dir[1]});
        }
    }
    //endregion
}