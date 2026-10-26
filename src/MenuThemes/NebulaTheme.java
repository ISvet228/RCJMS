package MenuThemes;

import Project.MainMenu.MenuTheme;
import java.awt.*;
import java.awt.geom.Point2D;

/** Two drifting colored clouds of gas. */
public final class NebulaTheme implements MenuTheme {
    private static final Color BASE = new Color(15, 15, 35);

    @Override public String id() { return "nebula"; }
    @Override public String nameKey() { return "mm.bg_nebula"; }
    @Override public int order() { return 1; }
    @Override public void paintBase(Graphics2D g, int w, int h, double t) {
        g.setColor(BASE);
        g.fillRect(0, 0, w, h);
        glow(g, w, h, (float) (300 + 45 * Math.sin(t * 0.11)), (float) (200 + 35 * Math.cos(t * 0.13)), 380,
                new Color(120, 40, 150, (int) (140 + 25 * Math.sin(t * 0.5))));
        glow(g, w, h, (float) (700 + 40 * Math.cos(t * 0.09)), (float) (380 + 30 * Math.sin(t * 0.1)), 420,
                new Color(20, 120, 150, (int) (110 + 25 * Math.cos(t * 0.4))));
    }
    private static void glow(Graphics2D g, int w, int h, float cx, float cy, float radius, Color color) {
        Color clear = new Color(color.getRed(), color.getGreen(), color.getBlue(), 0);
        g.setPaint(new RadialGradientPaint(new Point2D.Float(cx, cy), radius, new float[]{0f, 0.5f, 1f},
                new Color[]{color, new Color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha() * 3 / 4), clear}));
        g.fillRect(0, 0, w, h);
    }
}