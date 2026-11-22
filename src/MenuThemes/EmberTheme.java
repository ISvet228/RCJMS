package MenuThemes;

import Project.MainMenu.MenuTheme;
import java.awt.*;
import java.awt.geom.Point2D;

/** Dark warm night over a glowing ember bed; sparks rise, flicker and cool as they fade. */
public final class EmberTheme implements MenuTheme {
    //region Variables
    private static final int COUNT = 110;
    private static final Color TOP = new Color(14, 5, 2), BOTTOM = new Color(44, 16, 4);
    //endregion

    //region Identity
    @Override public String id() { return "ember"; }
    @Override public String nameKey() { return "mm.bg_ember"; }
    @Override public int order() { return 6; }
    //endregion

    //region Painting
    @Override public void paintBase(Graphics2D g2d, int w, int h, double t) {
        g2d.setPaint(new GradientPaint(0, 0, TOP, 0, h, BOTTOM));
        g2d.fillRect(0, 0, w, h);
        double pulse = 0.85 + 0.15 * Math.sin(t * 1.4) + 0.05 * Math.sin(t * 3.1);
        glow(g2d, w, h, w / 2f, h * 1.05f, w * 0.75f, new Color(255, 120, 30), (int) (120 * pulse));
        glow(g2d, w, h, w * 0.5f, h * 1.1f, w * 0.3f, new Color(255, 200, 90), (int) (70 * pulse));
        for (int i = 0; i < COUNT; i++) drawEmber(g2d, w, h, t, i);
    }
    private static void drawEmber(Graphics2D g2d, int w, int h, double t, int i) {
        double speed = 0.04 + 0.08 * unit(i, 2);
        double phase = (t * speed + unit(i, 3)) % 1.0;
        double x = unit(i, 4) * w + Math.sin(t * (0.6 + unit(i, 5)) + i) * (12 + 20 * unit(i, 6));
        double y = h * 1.02 - phase * h * 1.1;
        double life = Math.sin(Math.PI * phase);
        double flicker = 0.7 + 0.3 * Math.sin(t * 9 + i);
        double size = (1.2 + 2.8 * unit(i, 7)) * (1 - 0.5 * phase);
        int alpha = (int) Math.clamp(255 * life * flicker, 0, 255);
        if (alpha <= 0) return;
        int green = (int) (220 * (1 - phase) + 60 * phase);
        int blue = (int) (120 * (1 - phase));
        g2d.setColor(new Color(255, green, blue, alpha / 5));
        g2d.fill(new java.awt.geom.Ellipse2D.Double(x - size * 2.5, y - size * 2.5, size * 5, size * 5));
        g2d.setColor(new Color(255, green, blue, alpha));
        g2d.fill(new java.awt.geom.Ellipse2D.Double(x - size, y - size, size * 2, size * 2));
    }
    private static void glow(Graphics2D g2d, int w, int h, float cx, float cy, float radius, Color color, int alpha) {
        Color clear = new Color(color.getRed(), color.getGreen(), color.getBlue(), 0);
        Color core = new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.clamp(alpha, 0, 255));
        g2d.setPaint(new RadialGradientPaint(new Point2D.Float(cx, cy), radius, new float[]{0f, 1f}, new Color[]{core, clear}));
        g2d.fillRect(0, 0, w, h);
    }
    //endregion

    //region Hashing
    private static double unit(int index, int salt) {
        long z = index * 0x9E3779B97F4A7C15L + salt * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return (z >>> 11) * 0x1.0p-53;
    }
    //endregion
}
