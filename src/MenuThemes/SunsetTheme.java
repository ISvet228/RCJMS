package MenuThemes;

import Project.MainMenu.MenuTheme;
import java.awt.*;
import java.awt.geom.Ellipse2D;

/** Retro sun slowly breathing over a striped horizon, with shimmering reflections on the sea. */
public final class SunsetTheme implements MenuTheme {
    @Override public String id() { return "sunset"; }
    @Override public String nameKey() { return "mm.bg_sunset"; }
    @Override public int order() { return 4; }

    @Override public void paintBase(Graphics2D g, int w, int h, double t) {
        double horizon = h * 0.62;
        g.setPaint(new LinearGradientPaint(0, 0, 0, (float) horizon, new float[]{0f, 0.6f, 1f},
                new Color[]{new Color(18, 6, 40), new Color(120, 30, 90), new Color(255, 120, 70)}));
        g.fillRect(0, 0, w, (int) horizon + 1);

        double sunR = h * (0.24 + 0.006 * Math.sin(t * 0.6)), sunX = w / 2.0, sunY = horizon - h * 0.02;
        g.setPaint(new LinearGradientPaint(0, (float) (sunY - sunR), 0, (float) sunY,
                new float[]{0f, 1f}, new Color[]{new Color(255, 230, 120), new Color(255, 90, 90)}));
        g.fill(new Ellipse2D.Double(sunX - sunR, sunY - sunR, sunR * 2, sunR * 2));
        g.setColor(new Color(120, 30, 90));
        for (int i = 0; i < 5; i++) {
            double y = sunY - sunR * 0.35 + i * sunR * 0.22 + i * i * sunR * 0.02;
            g.fillRect(0, (int) y, w, Math.max(2, (int) (sunR * 0.05)));
        }

        g.setPaint(new GradientPaint(0, (float) horizon, new Color(40, 8, 50), 0, h, new Color(8, 2, 16)));
        g.fillRect(0, (int) horizon, w, h - (int) horizon);
        for (int i = 1; i <= 6; i++) {
            int y = (int) (horizon + i * i * h * 0.006);
            int half = (int) (sunR * (1.1 - i * 0.12));
            int shift = (int) (12 * Math.sin(t * 0.9 + i));
            g.setColor(new Color(255, 120, 70, 110 - i * 12));
            g.fillRect((int) sunX - half + shift, y, half * 2, 2);
        }
    }
}