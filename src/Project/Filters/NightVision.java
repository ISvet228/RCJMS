package Project.Filters;

/** Green phosphor image intensifier look. */
public final class NightVision implements Filter {
    //region Identity
    @Override public String id() { return "nightvision"; }
    @Override public String nameKey() { return "filter.name.nightvision"; }
    @Override public int order() { return 7; }
    //endregion

    //region Painting
    @Override public void apply(int[] pixels, int width, int height, double time) {
        for (int i = 0; i < width * height; i++) {
            int lum = FilterMath.luma(pixels[i]);
            pixels[i] = FilterMath.pack(lum >> 4, Math.min(255, lum * 5 / 4 + 6), lum >> 2);
        }
    }
    //endregion
}