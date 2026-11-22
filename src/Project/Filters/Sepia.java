package Project.Filters;

/** Warm old-photograph tones. */
public final class Sepia implements Filter {
    //region Identity
    @Override public String id() { return "sepia"; }
    @Override public String nameKey() { return "filter.name.sepia"; }
    @Override public int order() { return 4; }
    //endregion

    //region Painting
    @Override public void apply(int[] pixels, int width, int height, double time) {
        for (int i = 0; i < width * height; i++) {
            int p = pixels[i];
            int r = FilterMath.red(p), g = FilterMath.green(p), b = FilterMath.blue(p);
            pixels[i] = FilterMath.pack((101 * r + 197 * g + 48 * b) >> 8, (89 * r + 176 * g + 43 * b) >> 8, (70 * r + 137 * g + 34 * b) >> 8);
        }
    }
    //endregion
}