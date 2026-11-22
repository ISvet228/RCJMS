package Project.Filters;

/** Splits the color channels sideways, as a lens with a color fringe does. Red moves left, blue moves right. */
public final class ChromaticAberration implements Filter {
    //region Variables
    private int[] source = new int[0];
    //endregion

    //region Identity
    @Override public String id() { return "chromatic"; }
    @Override public String nameKey() { return "filter.name.chromatic"; }
    @Override public int order() { return 6; }
    //endregion

    //region Painting
    @Override public void apply(int[] pixels, int width, int height, double time) {
        int count = width * height;
        source = FilterMath.buffer(source, count);
        System.arraycopy(pixels, 0, source, 0, count);
        int shift = Math.max(1, width / 320);
        for (int y = 0; y < height; y++) {
            int row = y * width;
            for (int x = 0; x < width; x++) {
                int left = source[row + Math.max(0, x - shift)];
                int center = source[row + x];
                int right = source[row + Math.min(width - 1, x + shift)];
                pixels[row + x] = (FilterMath.red(left) << 16) | (FilterMath.green(center) << 8) | FilterMath.blue(right);
            }
        }
    }
    //endregion
}