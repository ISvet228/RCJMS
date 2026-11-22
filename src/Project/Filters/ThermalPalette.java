package Project.Filters;

/** Heat-camera colors: brightness mapped to black, violet, red, yellow and white. */
public final class ThermalPalette implements Filter {
    //region Variables
    private static final int[] PALETTE = buildPalette();
    //endregion

    //region Identity
    @Override public String id() { return "thermal"; }
    @Override public String nameKey() { return "filter.name.thermal"; }
    @Override public int order() { return 8; }
    //endregion

    @Override public void apply(int[] pixels, int width, int height, double time) { for (int i = 0; i < width * height; i++) pixels[i] = PALETTE[FilterMath.luma(pixels[i])]; }

    //region Palette
    private static int[] buildPalette() {
        int[][] stops = {{0, 0, 0}, {60, 0, 120}, {220, 30, 30}, {255, 220, 0}, {255, 255, 255}};
        int[] table = new int[256];
        for (int v = 0; v < 256; v++) {
            int segment = Math.min(stops.length - 2, v / 64);
            int[] from = stops[segment], to = stops[segment + 1];
            int t = v - segment * 64;
            table[v] = FilterMath.pack(from[0] + (to[0] - from[0]) * t / 63, from[1] + (to[1] - from[1]) * t / 63, from[2] + (to[2] - from[2]) * t / 63);
        }
        return table;
    }
    //endregion
}