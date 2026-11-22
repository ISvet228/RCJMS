package Project.Filters;

/** Reduces every channel to four levels, which gives flat poster-like areas. */
public final class Posterize implements Filter {
    //region Identity
    @Override public String id() { return "posterize"; }
    @Override public String nameKey() { return "filter.name.posterize"; }
    @Override public int order() { return 9; }
    //endregion

    //region Painting
    @Override public void apply(int[] pixels, int width, int height, double time) {
        for (int i = 0; i < width * height; i++) {
            int p = pixels[i];
            pixels[i] = FilterMath.pack((FilterMath.red(p) >> 6) * 85, (FilterMath.green(p) >> 6) * 85, (FilterMath.blue(p) >> 6) * 85);
        }
    }
    //endregion
}