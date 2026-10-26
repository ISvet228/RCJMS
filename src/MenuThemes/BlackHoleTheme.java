package MenuThemes;

import Project.MainMenu.MenuTheme;
import java.awt.*;
import java.awt.geom.*;
import java.util.Random;

/** Black hole with a turning accretion disk of particles and clumps, and a bright photon ring. */
public final class BlackHoleTheme implements MenuTheme {
    private static final double HOLE_CENTER_X = 0.72, HOLE_CENTER_Y = 0.5, HOLE_RADIUS = 0.2;
    private static final double TILT_ANGLE = Math.toRadians(-14), TILT_COS = Math.cos(TILT_ANGLE), TILT_SIN = Math.sin(TILT_ANGLE);
    private static final double DISK_FLATNESS = 0.3;
    private static final double[][] DISK_DOTS = buildDisk();
    private static final double[][] DISK_CLUMPS = buildClumps();

    @Override public String id() { return "blackhole"; }
    @Override public String nameKey() { return "mm.bg_blackhole"; }
    @Override public int order() { return 2; }
    @Override public void paintBase(Graphics2D g, int w, int h, double t) {
        g.setColor(new Color(3, 3, 8));
        g.fillRect(0, 0, w, h);
        double cx = w * HOLE_CENTER_X, cy = h * HOLE_CENTER_Y, r = h * HOLE_RADIUS;
        glow(g, w, h, (float) cx, (float) cy, (float) (r * 4.5), new Color(255, 140, 40, 60));
        glow(g, w, h, (float) cx, (float) cy, (float) (r * 2.2), new Color(255, 170, 80, 80));

        AffineTransform old = g.getTransform();
        g.rotate(TILT_ANGLE, cx, cy);
        for (int i = 0; i < 24; i++) {
            double u = i / 23.0;
            double rx = r * (2.9 - 1.6 * u), ry = rx * DISK_FLATNESS;
            g.setColor(new Color(255, (int) (110 + 120 * u), (int) (40 + 150 * u), (int) (30 + 30 * u)));
            g.fill(new Ellipse2D.Double(cx - rx, cy - ry, rx * 2, ry * 2));
        }
        g.setTransform(old);

        double[] p = new double[2];
        for (double[] dot : DISK_DOTS) drawDot(g, dot, cx, cy, r, t, false, p);
        for (double[] clump : DISK_CLUMPS) drawClump(g, clump, cx, cy, r, t, false);

        g.setColor(Color.BLACK);
        g.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));

        int ringAlpha = (int) (200 + 40 * Math.sin(t * 2));
        g.setStroke(new BasicStroke((float) (r * 0.07)));
        g.setColor(new Color(255, 230, 180, ringAlpha));
        g.draw(new Ellipse2D.Double(cx - r * 1.04, cy - r * 1.04, r * 2.08, r * 2.08));
        g.setStroke(new BasicStroke((float) (r * 0.02)));
        g.setColor(new Color(255, 200, 120, 60));
        g.draw(new Ellipse2D.Double(cx - r * 1.35, cy - r * 1.35, r * 2.7, r * 2.7));

        for (double[] dot : DISK_DOTS) drawDot(g, dot, cx, cy, r, t, true, p);
        for (double[] clump : DISK_CLUMPS) drawClump(g, clump, cx, cy, r, t, true);
    }

    private static void glow(Graphics2D g, int w, int h, float cx, float cy, float radius, Color color) {
        Color clear = new Color(color.getRed(), color.getGreen(), color.getBlue(), 0);
        g.setPaint(new RadialGradientPaint(new Point2D.Float(cx, cy), radius, new float[]{0f, 0.5f, 1f},
                new Color[]{color, new Color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha() * 3 / 4), clear}));
        g.fillRect(0, 0, w, h);
    }
    private static void project(double cx, double cy, double r, double radius, double theta, double[] out) {
        double x = radius * Math.cos(theta) * r, y = radius * Math.sin(theta) * r * DISK_FLATNESS;
        out[0] = cx + x * TILT_COS - y * TILT_SIN;
        out[1] = cy + x * TILT_SIN + y * TILT_COS;
    }
    private static void drawDot(Graphics2D g, double[] dot, double cx, double cy, double r, double t, boolean front, double[] out) {
        double theta = dot[1] + dot[2] * t;
        if ((Math.sin(theta) > 0) != front) return;
        double radius = dot[0];
        project(cx, cy, r, radius, theta, out);
        double heat = Math.clamp((radius - 1.2) / 1.7, 0, 1);
        double doppler = 0.55 + 0.45 * (0.5 + 0.5 * Math.cos(theta - 0.6));
        int alpha = (int) Math.clamp(255 * dot[3] * doppler * (1 - 0.5 * heat), 0, 255);
        g.setColor(new Color(255, (int) (210 - 120 * heat), (int) (160 - 140 * heat), alpha));
        double size = Math.max(1.0, dot[4] * r * (1.2 - 0.4 * heat));
        g.fill(new Ellipse2D.Double(out[0] - size / 2, out[1] - size / 2, size, size));
    }
    private static void drawClump(Graphics2D g, double[] clump, double cx, double cy, double r, double t, boolean front) {
        double theta = clump[1] + clump[2] * t;
        double mid = theta + clump[4] / 2;
        if ((Math.sin(mid) > 0) != front) return;
        double radius = clump[0];
        Path2D.Double arc = new Path2D.Double();
        double[] p = new double[2];
        for (int i = 0; i <= 6; i++) {
            project(cx, cy, r, radius, theta + clump[4] * i / 6.0, p);
            if (i == 0) arc.moveTo(p[0], p[1]); else arc.lineTo(p[0], p[1]);
        }
        double doppler = 0.6 + 0.4 * (0.5 + 0.5 * Math.cos(mid - 0.6));
        g.setStroke(new BasicStroke((float) Math.max(1.5, r * 0.035), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(255, 235, 190, (int) Math.clamp(230 * clump[3] * doppler, 0, 255)));
        g.draw(arc);
    }
    private static double[][] buildDisk() {
        Random random = new Random(11L);
        double[][] dots = new double[1400][];
        for (int i = 0; i < 1400; i++) {
            double radius = 1.22 + 1.7 * Math.pow(random.nextDouble(), 1.5);
            dots[i] = new double[]{ radius, random.nextDouble() * Math.PI * 2, 0.9 / Math.pow(radius, 1.5), 0.35 + 0.65 * random.nextDouble(), 0.012 + 0.02 * random.nextDouble() };
        }
        return dots;
    }
    private static double[][] buildClumps() {
        Random random = new Random(12L);
        double[][] clumps = new double[48][];
        for (int i = 0; i < 48; i++) {
            double radius = 1.4 + 1.2 * random.nextDouble();
            clumps[i] = new double[]{ radius, random.nextDouble() * Math.PI * 2, 0.9 / Math.pow(radius, 1.5), 0.6 + 0.4 * random.nextDouble(), 0.18 + 0.25 * random.nextDouble() };
        }
        return clumps;
    }
}