package Project.Helpers;

import java.util.Random;
import java.util.function.DoubleConsumer;

public interface MazeAlgorithm {
    String id();
    String nameKey();
    void carve(int[][] grid, Random random, DoubleConsumer progress);
}