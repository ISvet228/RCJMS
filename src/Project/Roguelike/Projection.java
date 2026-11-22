package Project.Roguelike;

import Project.Game.Fov;

import java.awt.Graphics2D;
import java.util.function.Consumer;

public final class Projection {
    //region Constants
    public static double halfFov() { return Fov.halfRadians(); }
    public static final double MAX_DISTANCE = 14.0;
    private static final double MARGIN = 0.15;
    private static final double SIGHT_STEP = 0.05;
    private static final double FIGURE_HEIGHT_SCREEN = 0.45;
    private static final double SPRITE_HALF_WIDTH = 0.3;
    private static final int SPAN_SAMPLES = 8;
    //endregion

    //region Variables
    public record Sprite(int centerX, int bottomY, int size, double distance, int clipLeft, int clipRight) {
        public void paint(Graphics2D g2d, int height, Consumer<Graphics2D> painter) {
            Graphics2D g = (Graphics2D) g2d.create();
            try {
                g.clip(new java.awt.Rectangle(clipLeft, 0, Math.max(1, clipRight - clipLeft), height));
                painter.accept(g);
            } finally { g.dispose(); }
        }
    }
    //endregion

    //region Constructors
    private Projection() {}
    //endregion

    //region Projection
    public static Sprite project(double px, double py, double angle, double pitch, double eyeHeight, double ex, double ey,
                                 int width, int height, int[][] map) {
        return projectFigure(px, py, angle, pitch, eyeHeight, ex, ey, width, height, map, FIGURE_HEIGHT_SCREEN);
    }
    public static Sprite projectFigure(double px, double py, double angle, double pitch, double eyeHeight, double ex, double ey,
                                       int width, int height, int[][] map, double figureScreen) {
        double dx = ex - px, dy = ey - py;
        double distance = Math.hypot(dx, dy);
        if (distance < 1e-6 || distance > MAX_DISTANCE) return null;
        double bearing = Combat.wrapAngle(Math.atan2(dy, dx) - angle);
        if (Math.abs(bearing) > halfFov() + MARGIN) return null;

        double forward = distance * Math.cos(bearing);
        double right = distance * Math.sin(bearing);
        double focal = width / (2 * Math.tan(halfFov()));
        double cosP = Math.cos(pitch), sinP = Math.sin(pitch);
        double figureTop = figureHeight(width, height, figureScreen) - eyeHeight;
        double[] feet = pitched(forward, right, -eyeHeight, cosP, sinP);
        double[] top = pitched(forward, right, figureTop, cosP, sinP);
        if (feet[0] <= 0.05 || top[0] <= 0.05) return null;
        double slope = Math.tan(halfFov() + MARGIN);
        if (Math.abs(feet[1] / feet[0]) > slope) return null;

        int centerX = (int) Math.round(width / 2.0 + focal * feet[1] / feet[0]);
        int bottomY = (int) Math.round(height / 2.0 - focal * feet[2] / feet[0]);
        int topY = (int) Math.round(height / 2.0 - focal * top[2] / top[0]);
        int size = Math.min(bottomY - topY, (int) (height * 0.9));
        if (size < 1) return null;
        int[] span = visibleSpan(px, py, angle, pitch, eyeHeight, ex, ey, width, map, focal, cosP, sinP);
        if (span == null) return null;
        return new Sprite(centerX, bottomY, size, distance, span[0], span[1]);
    }
    private static int[] visibleSpan(double px, double py, double angle, double pitch, double eyeHeight, double ex, double ey,
                                     int width, int[][] map, double focal, double cosP, double sinP) {
        double rightX = -Math.sin(angle), rightY = Math.cos(angle);
        int left = Integer.MAX_VALUE, right = Integer.MIN_VALUE, visible = 0;
        for (int i = 0; i <= SPAN_SAMPLES; i++) {
            double s = SPRITE_HALF_WIDTH * (2.0 * i / SPAN_SAMPLES - 1.0);
            double sx = ex + rightX * s, sy = ey + rightY * s;
            if (!clearLine(map, px, py, sx, sy)) continue;
            double dx = sx - px, dy = sy - py;
            double forward = dx * Math.cos(angle) + dy * Math.sin(angle);
            double lateral = dy * Math.cos(angle) - dx * Math.sin(angle);
            double[] feet = pitched(forward, lateral, -eyeHeight, cosP, sinP);
            if (feet[0] <= 0.05) continue;
            int x = (int) Math.round(width / 2.0 + focal * feet[1] / feet[0]);
            left = Math.min(left, x);
            right = Math.max(right, x);
            visible++;
        }
        if (visible == 0) return null;
        if (visible == SPAN_SAMPLES + 1) return new int[] { 0, width };
        return new int[] { left - 2, right + 2 };
    }
    private static double figureHeight(int width, int height, double figureScreen) {
        double focal = width / (2 * Math.tan(halfFov()));
        return figureScreen * height / focal;
    }
    private static double[] pitched(double forward, double right, double up, double cosP, double sinP) {
        return new double[] { forward * cosP + up * sinP, right, -forward * sinP + up * cosP };
    }
    public static boolean clearLine(int[][] map, double px, double py, double ex, double ey) {
        double length = Math.hypot(ex - px, ey - py);
        int steps = Math.max(1, (int) Math.ceil(length / SIGHT_STEP));
        for (int i = 1; i < steps; i++) {
            double t = i / (double) steps;
            int cellX = (int) Math.floor(px + (ex - px) * t);
            int cellY = (int) Math.floor(py + (ey - py) * t);
            if (Pathing.blocksSight(map, cellX, cellY)) return false;
        }
        return true;
    }
    //endregion
}