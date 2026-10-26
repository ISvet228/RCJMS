package MenuThemes;

import Project.MainMenu.MenuTheme;
import java.awt.*;
import java.awt.geom.*;
import java.util.Random;

/** Underwater: slanted light shafts sway, and bubbles rise through the water. */
public final class OceanTheme implements MenuTheme {
    private static final double[][] RAYS = random(5L, 9);
    private static final double[][] BUBBLES = random(6L, 60);

    @Override public String id() { return "ocean"; }
    @Override public String nameKey() { return "mm.bg_ocean"; }
    @Override public int order() { return 5; }
    @Override public void paintBase(Graphics2D g, int w, int h, double t) {
        g.setPaint(new GradientPaint(0, 0, new Color(10, 70, 100), 0, h, new Color(2, 8, 26)));
        g.fillRect(0, 0, w, h);
        for (double[] ray : RAYS) {
            double x = ray[0] * w + Math.sin(t * 0.3 + ray[3] * 6) * 30;
            double top = ray[1] * w * 0.04, spread = ray[2] * w * 0.09 + 30;
            int alpha = (int) (ray[3] * 45) + 15;
            Path2D.Double shaft = new Path2D.Double();
            shaft.moveTo(x, -10);
            shaft.lineTo(x + top, -10);
            shaft.lineTo(x + top + spread, h);
            shaft.lineTo(x + spread * 0.2, h);
            shaft.closePath();
            g.setPaint(new LinearGradientPaint((float) x, 0, (float) (x + spread), (float) h,
                    new float[]{0f, 1f}, new Color[]{new Color(180, 240, 255, alpha), new Color(180, 240, 255, 0)}));
            g.fill(shaft);
        }
        for (double[] bubble : BUBBLES) {
            double speed = 25 + bubble[3] * 40;
            double y = h - ((t * speed + bubble[1] * h) % (h + 20));
            double x = bubble[0] * w + Math.sin(t * 1.5 + bubble[2] * 10) * 6;
            double size = 2 + bubble[2] * 4;
            g.setColor(new Color(200, 240, 255, 90));
            g.setStroke(new BasicStroke(1f));
            g.draw(new Ellipse2D.Double(x - size / 2, y - size / 2, size, size));
        }
    }
    private static double[][] random(long seed, int count) {
        Random random = new Random(seed);
        double[][] values = new double[count][4];
        for (double[] row : values) for (int c = 0; c < 4; c++) row[c] = random.nextDouble();
        return values;
    }
}