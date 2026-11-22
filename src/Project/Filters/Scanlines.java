package Project.Filters;

/** Darkens every second row, like an old interlaced display. */
public final class Scanlines implements Filter {
    //region Identity
    @Override public String id() { return "scanlines"; }
    @Override public String nameKey() { return "filter.name.scanlines"; }
    @Override public int order() { return 1; }
    //endregion

    //region Painting
    @Override public void apply(int[] pixels, int width, int height, double time) {
        for (int y = 1; y < height; y += 2) {
            int row = y * width;
            for (int i = row; i < row + width; i++) pixels[i] = FilterMath.scale(pixels[i], 150);
        }
    }
    //endregion
}