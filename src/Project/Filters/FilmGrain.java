package Project.Filters;

public final class FilmGrain implements Filter {
    //region Variables
    private static final double FRAMES_PER_SECOND = 24.0;
    private static final int TABLE_MASK = 0xFFFF;
    private final byte[] table = new byte[TABLE_MASK + 1];
    //endregion

    //region Constructors
    public FilmGrain() { this(0x5EED); }
    public FilmGrain(int seed) { for (int i = 0; i < table.length; i++) table[i] = (byte) ((hash(seed, i) & 0x1F) - 16); }
    //endregion

    //region Identity
    @Override public String id() { return "grain"; }
    @Override public String nameKey() { return "filter.name.grain"; }
    @Override public int order() { return 11; }
    //endregion

    //region Painting
    @Override public void apply(int[] pixels, int width, int height, double time) {
        int frameOffset = (int) Math.floor(time * FRAMES_PER_SECOND) * 0x2F1;
        for (int y = 0; y < height; y++) {
            int row = y * width;
            int rowOffset = y * 0x1F3D + frameOffset;
            for (int x = 0; x < width; x++) {
                int noise = table[(rowOffset + x) & TABLE_MASK];
                int p = pixels[row + x];
                pixels[row + x] = FilterMath.pack(FilterMath.red(p) + noise, FilterMath.green(p) + noise, FilterMath.blue(p) + noise);
            }
        }
    }
    private static int hash(int seed, int index) {
        int h = seed ^ (index * 0x27D4EB2D);
        h = (h ^ (h >>> 15)) * 0x2C1B3C6D;
        h = (h ^ (h >>> 12)) * 0x297A2D39;
        return h ^ (h >>> 15);
    }
    //endregion
}