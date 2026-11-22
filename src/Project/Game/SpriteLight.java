package Project.Game;

import java.awt.Color;
public final class SpriteLight {
    private SpriteLight() {}
    public static Color tint(Color color, float[] light) {
        return new Color(clamp(color.getRed() * light[0]), clamp(color.getGreen() * light[1]), clamp(color.getBlue() * light[2]));
    }
    private static int clamp(float value) { return Math.clamp(Math.round(value), 0, 255); }
}