package Project.Filters;

/** Darkens the corners of the frame. The falloff is kept per frame size. */
public final class Vignette implements Filter {
    //region Variables
    private int[] shade = new int[0];
    private int mapWidth = -1, mapHeight = -1;
    //endregion

    //region Identity
    @Override public String id() { return "vignette"; }
    @Override public String nameKey() { return "filter.name.vignette"; }
    @Override public int order() { return 10; }
    //endregion

    //region Painting
    @Override public void apply(int[] pixels, int width, int height, double time) {
        if (width != mapWidth || height != mapHeight) buildShade(width, height);
        for (int i = 0; i < width * height; i++) pixels[i] = FilterMath.scale(pixels[i], shade[i]);
    }

    private void buildShade(int width, int height) {
        shade = new int[width * height];
        for (int y = 0; y < height; y++) {
            double ny = height > 1 ? 2.0 * y / (height - 1) - 1.0 : 0.0;
            for (int x = 0; x < width; x++) {
                double nx = width > 1 ? 2.0 * x / (width - 1) - 1.0 : 0.0;
                double falloff = Math.min(1.0, (nx * nx + ny * ny) / 2.0);
                shade[y * width + x] = (int) (256 * Math.max(0.3, 1.0 - 0.7 * falloff * falloff));
            }
        }
        mapWidth = width;
        mapHeight = height;
    }
    //endregion
}