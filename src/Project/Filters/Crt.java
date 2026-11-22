package Project.Filters;

/** A curved tube screen: barrel distortion, scanlines and dark corners. The distortion map is kept per frame size. */
public final class Crt implements Filter {
    //region Variables
    private static final double CURVATURE = 0.12;
    private int[] source = new int[0];
    private int[] sourceIndex = new int[0];
    private int[] shade = new int[0];
    private int mapWidth = -1, mapHeight = -1;
    //endregion

    //region Identity
    @Override public String id() { return "crt"; }
    @Override public String nameKey() { return "filter.name.crt"; }
    @Override public int order() { return 2; }
    //endregion

    //region Painting
    @Override public void apply(int[] pixels, int width, int height, double time) {
        int count = width * height;
        if (width != mapWidth || height != mapHeight) buildMap(width, height);
        source = FilterMath.buffer(source, count);
        System.arraycopy(pixels, 0, source, 0, count);
        for (int i = 0; i < count; i++) {
            int index = sourceIndex[i];
            pixels[i] = index < 0 ? 0 : FilterMath.scale(source[index], shade[i]);
        }
    }
    //endregion

    //region Map
    private void buildMap(int width, int height) {
        int count = width * height;
        sourceIndex = new int[count];
        shade = new int[count];
        for (int y = 0; y < height; y++) {
            double ny = height > 1 ? 2.0 * y / (height - 1) - 1.0 : 0.0;
            for (int x = 0; x < width; x++) {
                double nx = width > 1 ? 2.0 * x / (width - 1) - 1.0 : 0.0;
                double r2 = nx * nx + ny * ny;
                double scale = 1.0 + CURVATURE * r2;
                int sx = (int) Math.round((nx * scale + 1.0) * 0.5 * (width - 1));
                int sy = (int) Math.round((ny * scale + 1.0) * 0.5 * (height - 1));
                boolean inside = sx >= 0 && sx < width && sy >= 0 && sy < height;
                int index = y * width + x;
                sourceIndex[index] = inside ? sy * width + sx : -1;
                double scanline = (y % 3 == 2) ? 0.78 : 1.0;
                double vignette = Math.max(0.35, 1.0 - 0.45 * r2 / 2.0);
                shade[index] = (int) (256 * scanline * vignette);
            }
        }
        mapWidth = width;
        mapHeight = height;
    }
    //endregion
}