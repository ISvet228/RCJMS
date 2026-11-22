package Project.Filters;

/** Negative colors. */
public final class Invert implements Filter {
    //region Identity
    @Override public String id() { return "invert"; }
    @Override public String nameKey() { return "filter.name.invert"; }
    @Override public int order() { return 5; }
    //endregion

    @Override public void apply(int[] pixels, int width, int height, double time) { for (int i = 0; i < width * height; i++) pixels[i] = 0xFFFFFF - (pixels[i] & 0xFFFFFF); }
}