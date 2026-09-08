package StyleUI;

import java.awt.*;

public enum Style {
    FLAT("01", "FLAT", new Color(31, 33, 37), new Color(57, 79, 124),
            new Color(72, 99, 151), new Color(225, 225, 230), new Color(71, 73, 79), new Color(25, 26, 29)),
    NEUMORPHIC("02", "NEUMORPHIC", new Color(45, 47, 50), new Color(61, 63, 66),
            new Color(70, 72, 75), new Color(220, 220, 220), new Color(82, 84, 87), new Color(36, 38, 41)),
    GLASS("03", "GLASS", new Color(35, 45, 55), new Color(255, 255, 255, 28),
            new Color(255, 255, 255, 55), Color.WHITE, new Color(255, 255, 255, 90), new Color(255, 255, 255, 22));
    final String number, title;
    final Color panel, normal, hover, text, accent, field;
    Style(String number, String title, Color panel, Color normal, Color hover, Color text, Color accent, Color field) {
        this.number = number;
        this.title = title;
        this.panel = panel;
        this.normal = normal;
        this.hover = hover;
        this.text = text;
        this.accent = accent;
        this.field = field;
    }
}