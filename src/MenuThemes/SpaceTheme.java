package MenuThemes;

import Project.MainMenu.MenuTheme;
import java.awt.*;
import java.awt.geom.Point2D;

/** Dark space with drifting dust; the only theme that shows the starfield. */
public final class SpaceTheme implements MenuTheme {
    private static final Color BASE = new Color(15, 15, 35);

    @Override public String id() { return "space"; }
    @Override public String nameKey() { return "mm.bg_space"; }
    @Override public int order() { return 0; }
    @Override public boolean hasStars() { return true; }
    @Override public void paintBase(Graphics2D g, int w, int h, double t) {
        g.setColor(BASE);
        g.fillRect(0, 0, w, h);
        glow(g, w, h, (float) (w * 0.3 + 30 * Math.sin(t * 0.05)), (float) (h * 0.4 + 20 * Math.cos(t * 0.07)), 300, new Color(70, 60, 140, 40));
        glow(g, w, h, (float) (w * 0.75 + 25 * Math.cos(t * 0.04)), (float) (h * 0.7 + 20 * Math.sin(t * 0.06)), 260, new Color(40, 90, 160, 35));
    }
    @Override public void paintOverlay(Graphics2D g, int w, int h) { glow(g, w, h, w / 2f, 280, 430, new Color(15, 15, 35, 200)); }
    private static void glow(Graphics2D g, int w, int h, float cx, float cy, float radius, Color color) {
        Color clear = new Color(color.getRed(), color.getGreen(), color.getBlue(), 0);
        g.setPaint(new RadialGradientPaint(new Point2D.Float(cx, cy), radius, new float[]{0f, 0.5f, 1f}, new Color[]{color, new Color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha() * 3 / 4), clear}));
        g.fillRect(0, 0, w, h);
    }
}
