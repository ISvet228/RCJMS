package Project.Net;

import Project.Game.*;
import Project.Roguelike.Projection;

import java.awt.*;
import java.awt.geom.*;

final class PlayerSprite {
    //region Constants
    private static final Color LEG = new Color(38, 36, 44), SHOE = new Color(18, 16, 20);
    private static final Color SKIN = new Color(214, 180, 150), HAIR = new Color(34, 24, 18), EYE = new Color(18, 18, 22);
    private static final double STRIDE_CELLS = 1.1;
    private static final double FULL_SPEED = 2.5;
    //endregion

    //region Constructors
    private PlayerSprite() {}
    //endregion

    //region Painting
    static void paint(Graphics2D g2d, Projection.Sprite sprite, Rival rival, Color color, String label, double seconds) {
        Graphics2D g = (Graphics2D) g2d.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, sprite.size() >= 60 ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
            double s = Math.max(4, sprite.size()) / 100.0;
            float[] light = GameView.spriteLight(sprite.distance());
            double cx = sprite.centerX(), bottom = sprite.bottomY();
            Figure f = new Figure(g, cx, bottom, s);

            double stride = Math.clamp(rival.speed() / FULL_SPEED, 0, 1);
            double swing = Math.sin(rival.walked() * 2 * Math.PI / STRIDE_CELLS) * stride;
            double breath = Math.sin(seconds * 2.2) * 1.4 * (1 - stride);
            double hipY = 40, shoulderY = 76 + breath, headY = 92 + breath * 1.2;

            g.setColor(new Color(0, 0, 0, 110));
            g.fill(new Ellipse2D.Double(cx - 22 * s, bottom - 3 * s, 44 * s, 7 * s));

            Color shirt = SpriteLight.tint(color, light), pants = SpriteLight.tint(LEG, light), shoe = SpriteLight.tint(SHOE, light);
            g.setStroke(new BasicStroke((float) Math.max(2, 7 * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int side = -1; side <= 1; side += 2) {
                double lift = side * swing;
                double footX = side * 8 + lift * 12;
                f.line(pants, side * 6, hipY, side * 6 + lift * 6, hipY * 0.5, footX, 2);
                f.dot(shoe, footX, 2, 6);
            }
            f.fill(shirt, new double[] { -13, shoulderY, 13, shoulderY, 10, hipY, -10, hipY });
            for (int side = -1; side <= 1; side += 2) {
                double hand = -side * swing;   // the arm on the side of the leg that goes back swings forward
                double handX = side * 19 + hand * 7, handY = shoulderY - 44 + Math.abs(hand) * 3;
                f.line(shirt, side * 12, shoulderY - 4, side * 18, shoulderY - 26, handX, handY);
                f.dot(SpriteLight.tint(SKIN, light), handX, handY, 5);
            }
            AffineTransform saved = g.getTransform();
            g.translate(f.px(0), f.py(headY));
            g.setColor(SpriteLight.tint(SKIN, light));
            g.fill(new Ellipse2D.Double(-10 * s, -12 * s, 20 * s, 24 * s));
            g.setColor(SpriteLight.tint(HAIR, light));
            g.fill(new Ellipse2D.Double(-11 * s, -13 * s, 22 * s, 13 * s));
            g.setColor(SpriteLight.tint(EYE, light));
            g.fill(new Ellipse2D.Double(-5 * s, -1 * s, 3 * s, 4 * s));
            g.fill(new Ellipse2D.Double(2 * s, -1 * s, 3 * s, 4 * s));
            g.setTransform(saved);

            RivalSprite.drawLabel(g, label, (int) cx, (int) f.py(headY + 16));
        } finally { g.dispose(); }
    }

    private static Color shade(Color color, float factor) {
        return new Color(Math.round(color.getRed() * factor), Math.round(color.getGreen() * factor), Math.round(color.getBlue() * factor));
    }
    //endregion

    //region Figure
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

        void dot(Color color, double ux, double uy, double radius) {
            g.setColor(color);
            double r = radius * s;
            g.fill(new Ellipse2D.Double(px(ux) - r, py(uy) - r, 2 * r, 2 * r));
        }
    }
    //endregion
}