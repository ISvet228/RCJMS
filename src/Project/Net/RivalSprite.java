package Project.Net;

import Project.Game.*;
import Project.Roguelike.Projection;
import Project.Helpers.FontCache;

import java.awt.*;

final class RivalSprite {
    //region Constants
    private static final Font LABEL_FONT = FontCache.get(Font.SANS_SERIF, Font.BOLD, 12);
    //endregion

    //region Constructors
    private RivalSprite() {}
    //endregion

    //region Painting
    static void paint(Graphics2D g2d, Projection.Sprite sprite, Color color, String label) {
        Graphics2D g = (Graphics2D) g2d.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int size = Math.max(6, sprite.size());
            int cx = sprite.centerX(), bottom = sprite.bottomY();
            float[] light = GameView.spriteLight(sprite.distance());
            Color body = SpriteLight.tint(color, light);
            int bodyWidth = Math.max(4, size / 2), bodyHeight = size * 3 / 4;
            int top = bottom - bodyHeight;
            g.setColor(new Color(0, 0, 0, 90));
            g.fillOval(cx - bodyWidth / 2, bottom - 2, bodyWidth, 4);
            g.setColor(body);
            g.fillRoundRect(cx - bodyWidth / 2, top + bodyHeight / 3, bodyWidth, bodyHeight * 2 / 3, bodyWidth / 2, bodyWidth / 2);
            g.fillOval(cx - bodyWidth * 2 / 5, top, bodyWidth * 4 / 5, bodyHeight / 2);
            g.setColor(Color.WHITE);
            int eye = Math.max(2, size / 14);
            g.fillOval(cx - eye * 2, top + bodyHeight / 8, eye, eye);
            g.fillOval(cx + eye, top + bodyHeight / 8, eye, eye);
            drawLabel(g, label, cx, top - 6);
        } finally { g.dispose(); }
    }
    static void drawLabel(Graphics2D g, String label, int cx, int baseline) {
        g.setFont(LABEL_FONT);
        int x = cx - g.getFontMetrics().stringWidth(label) / 2;
        g.setColor(new Color(0, 0, 0, 200));
        g.drawString(label, x + 1, baseline + 1);
        g.setColor(Color.WHITE);
        g.drawString(label, x, baseline);
    }
    private static Color shade(Color color, float factor) { return new Color(Math.round(color.getRed() * factor), Math.round(color.getGreen() * factor), Math.round(color.getBlue() * factor)); }
    //endregion
}