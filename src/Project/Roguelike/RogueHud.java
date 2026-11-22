package Project.Roguelike;

import Project.Helpers.NSLocalizedString;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

public final class RogueHud {
    //region Constants
    private static final int PANEL_WIDTH = 300, PANEL_PADDING = 12, MARGIN = 16;
    private static final Font TITLE_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 13);
    private static final Font TEXT_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
    private static final Font LOG_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    private static final Font DEATH_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 48);
    private static final Font HINT_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 18);
    //endregion

    //region Constructors
    private RogueHud() {}
    //endregion

    //region Panel
    public static void paintPanel(Graphics2D g2d, int height, RogueStats stats, List<String> log, String controls, int logLines) {
        Graphics2D g = (Graphics2D) g2d.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int lineHeight = 17;
            int panelHeight = PANEL_PADDING * 2 + 22 + 30 + lineHeight + lineHeight + 8 + logLines * lineHeight;
            int x = MARGIN, y = height - panelHeight - MARGIN;
            Shape panel = new RoundRectangle2D.Double(x, y, PANEL_WIDTH, panelHeight, 14, 14);
            g.setColor(new Color(0, 0, 0, 150));
            g.fill(panel);
            g.setColor(new Color(255, 255, 255, 60));
            g.draw(panel);
            g.setClip(panel);
            int left = x + PANEL_PADDING, right = x + PANEL_WIDTH - PANEL_PADDING;
            int cursor = y + PANEL_PADDING;
            g.setFont(TITLE_FONT);
            g.setColor(new Color(240, 200, 120));
            g.drawString(NSLocalizedString.get("rogue.hud.title"), left, cursor + 13);
            cursor += 22;
            paintHealth(g, left, cursor, right - left, stats);
            cursor += 30;
            g.setFont(TEXT_FONT);
            g.setColor(Color.WHITE);
            g.drawString(NSLocalizedString.format("rogue.hud.potions", stats.potionsLeft()), left, cursor + 12);
            String kills = NSLocalizedString.format("rogue.hud.kills", stats.kills());
            g.drawString(kills, right - g.getFontMetrics().stringWidth(kills), cursor + 12);
            cursor += lineHeight;
            g.setFont(LOG_FONT);
            g.setColor(new Color(200, 200, 200));
            g.drawString(controls, left, cursor + 12);
            cursor += lineHeight + 8;
            g.setColor(new Color(230, 230, 230));
            for (String line : log) {
                g.drawString(line, left, cursor + 12);
                cursor += lineHeight;
            }
        }
        finally { g.dispose(); }
    }

    private static void paintHealth(Graphics2D g, int left, int top, int width, RogueStats stats) {
        int barHeight = 12;
        double fraction = Math.clamp(stats.hp() / (double) RogueStats.MAX_HP, 0, 1);
        g.setColor(new Color(60, 60, 60, 200));
        g.fillRoundRect(left, top + 14, width, barHeight, 6, 6);
        g.setColor(new Color((int) (230 * (1 - fraction) + 60 * fraction), (int) (60 * (1 - fraction) + 200 * fraction), 70));
        g.fillRoundRect(left, top + 14, (int) Math.round(width * fraction), barHeight, 6, 6);
        g.setFont(TEXT_FONT);
        g.setColor(Color.WHITE);
        g.drawString(NSLocalizedString.format("rogue.hud.hp", stats.hp(), RogueStats.MAX_HP), left, top + 11);
    }
    //endregion

    //region Death
    public static void paintDeath(Graphics2D g2d, int width, int height, DeathSequence death) {
        if (!death.active()) return;
        Graphics2D g = (Graphics2D) g2d.create();
        try {
            g.setColor(new Color(170, 0, 0, (int) Math.round(190 * death.progress())));
            g.fillRect(0, 0, width, height);
            if (!death.ready()) return;
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            drawCentered(g, NSLocalizedString.get("rogue.death.title"), DEATH_FONT, width, height / 2 - 10, Color.WHITE);
            drawCentered(g, NSLocalizedString.get("rogue.death.hint"), HINT_FONT, width, height / 2 + 36, new Color(255, 230, 230));
        }
        finally { g.dispose(); }
    }
    private static void drawCentered(Graphics2D g, String text, Font font, int width, int baseline, Color color) {
        g.setFont(font);
        FontMetrics metrics = g.getFontMetrics();
        g.setColor(color);
        g.drawString(text, (width - metrics.stringWidth(text)) / 2, baseline);
    }
    //endregion
}