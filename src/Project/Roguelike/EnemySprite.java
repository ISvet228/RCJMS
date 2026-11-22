package Project.Roguelike;

import Project.Game.*;

import java.awt.*;

public final class EnemySprite {
    //region Constants
    private static final Color[] BODIES = { new Color(120, 170, 70), new Color(150, 90, 170), new Color(190, 110, 60), new Color(70, 140, 170) };
    private static final Color POTION = new Color(230, 70, 110);
    //endregion

    //region Constructors
    private EnemySprite() {}
    //endregion

    //region Enemies
    public static void paintEnemy(Graphics2D g2d, Projection.Sprite sprite, int variant, double hpFraction) {
        Graphics2D g = (Graphics2D) g2d.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int size = Math.max(4, sprite.size());
            int cx = sprite.centerX(), bottom = sprite.bottomY();
            float[] light = GameView.spriteLight(sprite.distance());
            Color body = SpriteLight.tint(BODIES[Math.floorMod(variant, BODIES.length)], light);
            int bodyWidth = size / 2, bodyHeight = size * 3 / 4;
            int top = bottom - bodyHeight;
            g.setColor(new Color(0, 0, 0, 90));
            g.fillOval(cx - bodyWidth / 2, bottom - Math.max(2, size / 12), bodyWidth, Math.max(2, size / 12));
            g.setStroke(new BasicStroke(Math.max(2f, size / 22f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(body.darker());
            g.drawLine(cx - bodyWidth / 4, bottom, cx - bodyWidth / 3, bottom - size / 8);
            g.drawLine(cx + bodyWidth / 4, bottom, cx + bodyWidth / 3, bottom - size / 8);
            g.setColor(body);
            g.fillRoundRect(cx - bodyWidth / 2, top + bodyHeight / 3, bodyWidth, bodyHeight * 2 / 3, bodyWidth / 2, bodyWidth / 2);
            g.fillOval(cx - bodyWidth * 2 / 5, top, bodyWidth * 4 / 5, bodyHeight / 2);
            paintFace(g, cx, top + bodyHeight / 4, size, variant);
            if (hpFraction < 1.0) paintHealth(g, cx, top - size / 10, bodyWidth, hpFraction);
        }
        finally { g.dispose(); }
    }

    private static void paintFace(Graphics2D g, int cx, int eyeY, int size, int variant) {
        int eye = Math.max(2, size / 14);
        g.setColor(Color.WHITE);
        g.fillOval(cx - size / 9 - eye, eyeY, eye * 2, eye * 2);
        g.fillOval(cx + size / 9 - eye, eyeY, eye * 2, eye * 2);
        g.setColor(new Color(170, 20, 20));
        g.fillOval(cx - size / 9 - eye / 2, eyeY + eye / 2, Math.max(1, eye), Math.max(1, eye));
        g.fillOval(cx + size / 9 - eye / 2, eyeY + eye / 2, Math.max(1, eye), Math.max(1, eye));
        if (variant % 2 == 1) {
            g.setStroke(new BasicStroke(Math.max(2f, size / 30f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(Color.DARK_GRAY);
            g.drawLine(cx - size / 6, eyeY - eye, cx - size / 14, eyeY);
            g.drawLine(cx + size / 6, eyeY - eye, cx + size / 14, eyeY);
        }
    }

    private static void paintHealth(Graphics2D g, int cx, int y, int width, double fraction) {
        int barWidth = Math.max(8, width), barHeight = 4;
        g.setColor(new Color(0, 0, 0, 160));
        g.fillRect(cx - barWidth / 2, y, barWidth, barHeight);
        g.setColor(new Color(220, 60, 60));
        g.fillRect(cx - barWidth / 2, y, (int) Math.round(barWidth * Math.clamp(fraction, 0, 1)), barHeight);
    }
    //endregion

    //region Potions
    public static void paintPotion(Graphics2D g2d, Projection.Sprite sprite) {
        Graphics2D g = (Graphics2D) g2d.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int size = Math.max(6, sprite.size() / 3);
            int cx = sprite.centerX(), bottom = sprite.bottomY();
            int width = size / 2;
            g.setColor(new Color(0, 0, 0, 90));
            g.fillOval(cx - width / 2, bottom - 3, width, 4);
            g.setColor(POTION);
            g.fillRoundRect(cx - width / 2, bottom - size, width, size, width / 2, width / 2);
            g.setColor(new Color(240, 240, 240));
            g.fillRect(cx - width / 4, bottom - size - size / 5, width / 2, size / 5);
        }
        finally { g.dispose(); }
    }
    //endregion

    //region Helpers
    private static Color shade(Color color, float factor) { return new Color(Math.round(color.getRed() * factor), Math.round(color.getGreen() * factor), Math.round(color.getBlue() * factor)); }
    //endregion
}