package Project.Helpers.maze;

import Project.Helpers.MazeAlgorithm;

import java.util.*;
import java.util.function.DoubleConsumer;

/** The original recursive backtracker. Its random sequence is what the seeds have always produced, so it must not change. */
public final class ClassicCarver implements MazeAlgorithm {
    //region Variables
    public static final String ID = "classic";
    //endregion

    //region Identity
    @Override public String id() { return ID; }
    @Override public String nameKey() { return "maze.alg.classic"; }
    //endregion

    //region Carving
    @Override public void carve(int[][] maze, Random random, DoubleConsumer progress) {
        int width = maze[0].length, height = maze.length;
        Session session = new Session(maze, random, progress, Math.max(1, (width / 2) * (height / 2)));
        Deque<CarveFrame> stack = new ArrayDeque<>();
        session.enterCarveCell(1, 1, stack);
        while (!stack.isEmpty()) {
            CarveFrame frame = stack.peek();
            if (frame.next >= frame.directions.size()) { stack.pop(); continue; }
            int[] dir = frame.directions.get(frame.next++);
            int nx = frame.x + dir[0];
            int ny = frame.y + dir[1];

            if (nx > 0 && ny > 0 && nx < width - 1 && ny < height - 1 && maze[ny][nx] == 1) {
                maze[frame.y + dir[1] / 2][frame.x + dir[0] / 2] = 0;
                session.enterCarveCell(nx, ny, stack);
            }
        }
    }
    //endregion

    //region Session
    private static final class Session {
        private final int[][] maze;
        private final Random random;
        private final DoubleConsumer progress;
        private final int total;
        private int carvedCells;

        Session(int[][] maze, Random random, DoubleConsumer progress, int total) {
            this.maze = maze;
            this.random = random;
            this.progress = progress;
            this.total = total;
        }

        void enterCarveCell(int x, int y, Deque<CarveFrame> stack) {
            if ((++carvedCells & 511) == 0) progress.accept(carvedCells / (double) total);
            maze[y][x] = 0;
            int[][] dirs = {{0, -2}, {2, 0}, {0, 2}, {-2, 0}};
            List<int[]> directions = Arrays.asList(dirs);
            Collections.shuffle(directions, random);
            stack.push(new CarveFrame(x, y, directions));
        }
    }
    private static final class CarveFrame {
        final int x, y;
        final List<int[]> directions;
        int next;

        CarveFrame(int x, int y, List<int[]> directions) { this.x = x; this.y = y; this.directions = directions; }
    }
    //endregion
}