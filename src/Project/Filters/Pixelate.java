package Project.Filters;

/** Turns the frame into large flat blocks, each in the average color of its area. */
public final class Pixelate implements Filter {
    //region Variables
    private static final int BLOCK = 8;
    //endregion

    //region Identity
    @Override public String id() { return "pixelate"; }
    @Override public String nameKey() { return "filter.name.pixelate"; }
    @Override public int order() { return 3; }
    //endregion

    //region Painting
    @Override public void apply(int[] pixels, int width, int height, double time) {
        for (int by = 0; by < height; by += BLOCK)
            for (int bx = 0; bx < width; bx += BLOCK) fillBlock(pixels, width, bx, by, Math.min(BLOCK, width - bx), Math.min(BLOCK, height - by));
    }

    private static void fillBlock(int[] pixels, int width, int bx, int by, int blockWidth, int blockHeight) {
        long r = 0, g = 0, b = 0;
        for (int y = by; y < by + blockHeight; y++) {
            for (int x = bx; x < bx + blockWidth; x++) {
                int p = pixels[y * width + x];
                r += FilterMath.red(p);
                g += FilterMath.green(p);
                b += FilterMath.blue(p);
            }
        }
        int count = blockWidth * blockHeight;
        int average = FilterMath.pack((int) (r / count), (int) (g / count), (int) (b / count));
        for (int y = by; y < by + blockHeight; y++) java.util.Arrays.fill(pixels, y * width + bx, y * width + bx + blockWidth, average);
    }
    //endregion
}