package Project.Net;

import Project.Game.*;
import Project.Player.PlayerRig;
import Project.Roguelike.Projection;

import java.awt.*;

final class PlayerSprite {
    private static final double FULL_SPEED = 2.5;
    private PlayerSprite() {}

    //region Painting
    static void paint(Graphics2D g2d, Projection.Sprite sprite, Rival rival, Color color, String label, double seconds) {
        Graphics2D g = (Graphics2D) g2d.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, sprite.size() >= 60 ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
            float[] light = GameView.spriteLight(sprite.distance());
            double size = Math.max(4, sprite.size());
            PlayerRig.original().drawLive(g, sprite.centerX(), sprite.bottomY(), size, seconds, Math.clamp(rival.speed() / FULL_SPEED, 0, 1), light, color.getRGB());
            RivalSprite.drawLabel(g, label, (int) sprite.centerX(), (int) (sprite.bottomY() - size * 1.04));
        } finally { g.dispose(); }
    }
    //endregion
}