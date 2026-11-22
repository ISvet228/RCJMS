package Project.Filters;

final class FilterMath {
    //region Constructors
    private FilterMath() {}
    //endregion

    //region Channels
    static int clamp(int value) { return value < 0 ? 0 : (Math.min(value, 255)); }
    static int red(int pixel) { return (pixel >> 16) & 0xFF; }
    static int green(int pixel) { return (pixel >> 8) & 0xFF; }
    static int blue(int pixel) { return pixel & 0xFF; }
    static int pack(int r, int g, int b) { return (clamp(r) << 16) | (clamp(g) << 8) | clamp(b); }
    static int luma(int pixel) { return (77 * red(pixel) + 150 * green(pixel) + 29 * blue(pixel)) >> 8; }
    static int scale(int pixel, int factor) { return (((red(pixel) * factor) >> 8) << 16) | (((green(pixel) * factor) >> 8) << 8) | ((blue(pixel) * factor) >> 8); }
    //endregion
    static int[] buffer(int[] current, int length) { return current != null && current.length >= length ? current : new int[length]; }
}
