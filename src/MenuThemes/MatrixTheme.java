package MenuThemes;

import Project.MainMenu.MenuTheme;
import java.awt.*;

/** Black screen with columns of green glyphs falling at different speeds. Glyphs are drawn from code, no font needed. */
public final class MatrixTheme implements MenuTheme {
    //region Variables
    private static final int COLUMN_WIDTH = 14, CELL_HEIGHT = 16, PIXEL = 2;
    private static final int GLYPH_COLUMNS = 5, GLYPH_ROWS = 7;
    private static final Color BASE = new Color(0, 6, 2), HEAD = new Color(225, 255, 225), TRAIL = new Color(40, 255, 110);
    //endregion

    //region Identity
    @Override public String id() { return "matrix"; }
    @Override public String nameKey() { return "mm.bg_matrix"; }
    @Override public int order() { return 7; }
    //endregion

    //region Painting
    @Override public void paintBase(Graphics2D g2d, int w, int h, double t) {
        g2d.setColor(BASE);
        g2d.fillRect(0, 0, w, h);
        int columns = w / COLUMN_WIDTH + 1;
        for (int column = 0; column < columns; column++) paintColumn(g2d, h, t, column);
    }
    private static void paintColumn(Graphics2D g2d, int h, double t, int column) {
        int length = 6 + (int) (unit(column, 1) * 14);
        double speed = 40 + 90 * unit(column, 2);
        double span = h + length * CELL_HEIGHT;
        double head = (unit(column, 3) * span + t * speed) % span - length * CELL_HEIGHT;
        int x = column * COLUMN_WIDTH + 2;
        for (int k = 0; k < length; k++) {
            double y = head - k * CELL_HEIGHT;
            if (y < -CELL_HEIGHT || y > h) continue;
            double fade = 1 - (double) k / length;
            int alpha = (int) (255 * fade * fade * 0.9);
            Color color = k == 0 ? HEAD : TRAIL;
            long glyph = (long) column * 7919 + (long) (t * (2 + unit(column, 4) * 3)) * 31 + k * 131L;
            drawGlyph(g2d, x, (int) Math.round(y), glyph, new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.clamp(alpha, 0, 255)));
        }
    }
    private static void drawGlyph(Graphics2D g2d, int x, int y, long key, Color color) {
        g2d.setColor(color);
        for (int row = 0; row < GLYPH_ROWS; row++)
            for (int column = 0; column < GLYPH_COLUMNS; column++) {
                if (unit(key, row * GLYPH_COLUMNS + column + 16) < 0.5) continue;
                g2d.fillRect(x + column * PIXEL, y + row * PIXEL, PIXEL, PIXEL);
            }
    }
    //endregion

    //region Hashing
    private static double unit(long key, int salt) {
        long z = key * 0x9E3779B97F4A7C15L + salt * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return (z >>> 11) * 0x1.0p-53;
    }
    //endregion
}