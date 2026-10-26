package MenuThemes;

import Project.MainMenu.MenuTheme;
import java.awt.*;
import java.awt.geom.Path2D;

/** Night sky with curtains of northern lights that ripple and sway over a dark horizon. */
public final class AuroraTheme implements MenuTheme {
    private static final Color[] CURTAINS = {new Color(60, 255, 150), new Color(40, 220, 255), new Color(160, 90, 255)};

    @Override public String id() { return "aurora"; }
    @Override public String nameKey() { return "mm.bg_aurora"; }
    @Override public int order() { return 3; }
    @Override public void paintBase(Graphics2D g, int w, int h, double t) {
        g.setPaint(new GradientPaint(0, 0, new Color(3, 6, 20), 0, h, new Color(12, 20, 40)));
        g.fillRect(0, 0, w, h);
        for (int k = 0; k < CURTAINS.length; k++) {
            double base = h * (0.18 + 0.12 * k) + Math.sin(t * 0.3 + k) * h * 0.02;
            double amp = h * 0.06 * (1 + 0.3 * Math.sin(t * 0.7 + k));
            double freq = 0.011 + 0.004 * k, phase = k * 1.7 + t * (0.5 + 0.2 * k);
            Path2D.Double band = new Path2D.Double();
            band.moveTo(0, base);
            for (int x = 0; x <= w; x += 8) band.lineTo(x, base + Math.sin(x * freq + phase) * amp);
            double bottom = h * 0.62;
            for (int x = w; x >= 0; x -= 8) band.lineTo(x, bottom + Math.sin(x * freq * 0.7 + phase) * amp * 0.5);
            band.closePath();
            Color c = CURTAINS[k];
            int shimmer = (int) (10 * Math.sin(t * 1.3 + k * 2));
            g.setPaint(new LinearGradientPaint(0, (float) base, 0, (float) bottom, new float[]{0f, 0.6f, 1f},
                    new Color[]{new Color(c.getRed(), c.getGreen(), c.getBlue(), 150 + shimmer),
                                new Color(c.getRed(), c.getGreen(), c.getBlue(), 40), new Color(c.getRed(), c.getGreen(), c.getBlue(), 0)}));
            g.fill(band);
        }
        g.setColor(new Color(2, 4, 8));
        Path2D.Double ground = new Path2D.Double();
        ground.moveTo(0, h);
        ground.lineTo(0, h * 0.86);
        ground.curveTo(w * 0.2, h * 0.78, w * 0.35, h * 0.92, w * 0.55, h * 0.84);
        ground.curveTo(w * 0.72, h * 0.78, w * 0.85, h * 0.9, w, h * 0.82);
        ground.lineTo(w, h);
        ground.closePath();
        g.fill(ground);
    }
}