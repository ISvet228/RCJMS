package Project.Horror;

import Project.Game.*;
import Project.Roguelike.Projection;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.util.Random;

final class StalkerSprite {
    //region Constants
    static final double LUNGE_SECONDS = 0.35;
    private static final Color BODY = new Color(14, 12, 14);
    private static final Color SKIN = new Color(190, 184, 172);
    private static final Color EYE_HUNT = new Color(255, 40, 40);
    private static final Color EYE_WATCH = new Color(230, 230, 215);
    //endregion

    //region Constructors
    private StalkerSprite() {}
    //endregion

    //region World
    static void paint(Graphics2D g2d, Projection.Sprite sprite, Stalker stalker) {
        Graphics2D g = (Graphics2D) g2d.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    sprite.size() >= 120 ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
            double s = Math.max(4, sprite.size()) / 100.0;
            float[] light = GameView.spriteLight(sprite.distance());
            double cx = sprite.centerX(), bottom = sprite.bottomY();
            Figure f = new Figure(g, cx, bottom, s);
            double lean = stalker.lean(), duck = stalker.duck(), arms = stalker.arms(), stride = stalker.stride();
            double alert = stalker.alert(), scan = stalker.scan(), clock = stalker.clock();

            g.setColor(new Color(0, 0, 0, 110));
        g.fill(new Ellipse2D.Double(cx - 26 * s, bottom - 3 * s, 52 * s, 7 * s));

        double breath = Math.sin(clock * 2.2) * 1.4 * (1 - stride);
            double crouch = duck * 26;
            double hipY = 40 - crouch * 0.6, shoulderY = 76 - crouch + breath, headY = 92 - crouch + breath * 1.2;
            double leanX = lean * 5 + duck * 5;
            double swing = Math.sin(clock * 7) * 16 * stride + Math.sin(clock * 2.1) * 2 * (1 - stride);

            Color limb = SpriteLight.tint(BODY, light);
            g.setStroke(new BasicStroke((float) Math.max(2, 6 * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int side = -1; side <= 1; side += 2) {
                double phase = side * swing / 16.0;
                f.line(limb, side * 6, hipY, side * 6 + phase * 6, hipY * 0.5, side * 8 + phase * 12, 0);
            }
            f.fill(limb, new double[] { -13 + leanX, shoulderY, 13 + leanX, shoulderY, 9 + leanX * 0.5, hipY, -9 + leanX * 0.5, hipY });
            for (int side = -1; side <= 1; side += 2) {
                double elbowX = side * (17 + arms * 5), elbowY = shoulderY - 26 + arms * 34;
                double handX = side * (20 + arms * 14), handY = shoulderY - 50 + arms * 76 + (stride * Math.sin(clock * 7 + side) * 4);
                f.line(limb, side * 12 + leanX, shoulderY - 4, elbowX + leanX, elbowY, handX + leanX, handY);
            }
            double headX = leanX + scan * 6 + lean * 4;
            double tilt = scan * 0.3 + lean * 0.12;
            AffineTransform saved = g.getTransform();
            g.translate(f.px(headX), f.py(headY));
            g.rotate(tilt);
            g.setColor(SpriteLight.tint(SKIN, light));
            g.fill(new Ellipse2D.Double(-11 * s, -13 * s, 22 * s, 26 * s));
            Color glow = alert > 0.4 ? EYE_HUNT : EYE_WATCH;
            g.setColor(new Color(0, 0, 0));
            g.fill(new Ellipse2D.Double(-8 * s, -3 * s, 6 * s, 5 * s));
            g.fill(new Ellipse2D.Double(2 * s, -3 * s, 6 * s, 5 * s));
            if (alert > 0.05) {
                g.setColor(withAlpha(glow, (int) (200 * alert)));
                g.fill(new Ellipse2D.Double(-6.5 * s, -2 * s, 3 * s, 3 * s));
                g.fill(new Ellipse2D.Double(3.5 * s, -2 * s, 3 * s, 3 * s));
                g.setColor(withAlpha(glow, (int) (60 * alert)));
                g.fill(new Ellipse2D.Double(-12 * s, -8 * s, 24 * s, 14 * s));
            }
            g.setTransform(saved);
        } finally { g.dispose(); }
    }
    //endregion

    //region Jump Scare
    static void paintJumpscare(Graphics2D g2d, int width, int height, double elapsed, Random random) {
        Graphics2D g = (Graphics2D) g2d.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (elapsed < 0.12) {
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, width, height);
                return;
            }
            g.setColor(new Color(0, 0, 0));
            g.fillRect(0, 0, width, height);
            double lunge = Math.min(1, elapsed / LUNGE_SECONDS);
            double scale = height * (0.55 + 0.5 * lunge * lunge) / 100.0;
            double jitter = elapsed < 1.6 ? 14 : 2;
            double cx = width / 2.0 + (random.nextDouble() - 0.5) * jitter;
            double cy = height / 2.0 + (random.nextDouble() - 0.5) * jitter;
            AffineTransform saved = g.getTransform();
            g.translate(cx, cy);
            g.scale(scale, scale);
            g.setColor(SKIN);
            g.fill(new Ellipse2D.Double(-40, -52, 80, 104));
            for (int side = -1; side <= 1; side += 2) {
                g.setColor(Color.BLACK);
                g.fill(new Ellipse2D.Double(side * 22 - 14, -22, 28, 22));
                g.setColor(EYE_HUNT);
                g.fill(new Ellipse2D.Double(side * 22 - 5, -15, 10, 10));
                g.setColor(Color.WHITE);
                g.fill(new Ellipse2D.Double(side * 22 - 1, -13, 3, 3));
            }
            GeneralPath mouth = new GeneralPath();
            mouth.moveTo(-34, 14);
            mouth.lineTo(34, 14);
            mouth.lineTo(28, 40);
            mouth.lineTo(-28, 40);
            mouth.closePath();
            g.setColor(new Color(40, 0, 0));
            g.fill(mouth);
            g.setColor(Color.WHITE);
            for (int i = 0; i < 6; i++) {
                double tx = -28 + i * 11.2;
                GeneralPath tooth = new GeneralPath();
                tooth.moveTo(tx, 14);
                tooth.lineTo(tx + 6, 14);
                tooth.lineTo(tx + 3, 26);
                tooth.closePath();
                g.fill(tooth);
            }
            g.setTransform(saved);
            g.setColor(new Color(160, 0, 0, 70));
            for (int i = 0; i < 40; i++) g.fillRect(random.nextInt(width), random.nextInt(height), 2 + random.nextInt(40), 1 + random.nextInt(3));
        } finally { g.dispose(); }
    }
    //endregion

    //region Helpers
    private static Color shade(Color color, float factor) {
        return new Color(Math.round(color.getRed() * factor), Math.round(color.getGreen() * factor), Math.round(color.getBlue() * factor));
    }
    private static Color withAlpha(Color color, int alpha) { return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.clamp(alpha, 0, 255)); }
    private static final class Figure {
        private final Graphics2D g;
        private final double cx, bottom, s;

        Figure(Graphics2D g, double cx, double bottom, double s) {
            this.g = g;
            this.cx = cx;
            this.bottom = bottom;
            this.s = s;
        }

        double px(double ux) { return cx + ux * s; }
        double py(double uy) { return bottom - uy * s; }

        void line(Color color, double... points) {
            g.setColor(color);
            GeneralPath path = new GeneralPath();
            path.moveTo(px(points[0]), py(points[1]));
            for (int i = 2; i + 1 < points.length; i += 2) path.lineTo(px(points[i]), py(points[i + 1]));
            g.draw(path);
        }

        void fill(Color color, double[] polygon) {
            g.setColor(color);
            GeneralPath path = new GeneralPath();
            path.moveTo(px(polygon[0]), py(polygon[1]));
            for (int i = 2; i + 1 < polygon.length; i += 2) path.lineTo(px(polygon[i]), py(polygon[i + 1]));
            path.closePath();
            g.fill(path);
        }
    }
    //endregion
}