package Project.Game;

import Project.RCJMS;
import Project.Helpers.FontCache;

import java.awt.*;
import java.awt.image.BufferedImage;

final class Hud {
    //region Look
    private static final Font FPS_FONT = FontCache.get(Font.MONOSPACED, Font.BOLD, 20);
    private static final Font OPENCL_FONT = FontCache.get(Font.MONOSPACED, Font.BOLD, 16);
    private static final Font TIMER_FONT = FontCache.get(Font.MONOSPACED, Font.BOLD, 24);
    private static final Font BADGE_FONT = FontCache.get(Font.MONOSPACED, Font.BOLD, 16);
    static final Color SEED_COLOR = new Color(170, 230, 170);
    static final Color GEOMETRY_COLOR = new Color(150, 170, 255);
    static final Color FLOOR_COLOR = new Color(255, 200, 120);
    private static final int BADGE_RIGHT_X = 200, BADGE_BOTTOM_Y = 26, BADGE_LINE_HEIGHT = 24;
    //endregion

    //region Frame Rate
    private int frames, fps;
    private long windowStart = System.currentTimeMillis();

    void trackFps() {
        frames++;
        long now = System.currentTimeMillis();
        if (now - windowStart >= 1000) {
            fps = frames;
            frames = 0;
            windowStart = now;
        }
    }
    int fps() { return fps; }
    //endregion

    //region Clock
    private long clockSeconds = -1;
    private String clockText = "";
    private String clock(long elapsedSeconds) {
        if (clockSeconds != elapsedSeconds) {
            clockSeconds = elapsedSeconds;
            clockText = String.format("%02d:%02d:%02d", elapsedSeconds / 3600, (elapsedSeconds % 3600) / 60, elapsedSeconds % 60);
        }
        return clockText;
    }
    //endregion

    //region Drawing
    record Badge(String text, Color color) { }
    void drawStatus(BufferedImage image, long elapsedSeconds, Badge... badges) {
        Graphics2D g = image.createGraphics();
        try {
            g.scale(GameView.uiScale(), GameView.uiScale());
            String time = clock(elapsedSeconds);
            g.setFont(TIMER_FONT);
            shadowed(g, time, RCJMS.SCREEN_WIDTH - 158, 32, Color.WHITE);

            g.setFont(BADGE_FONT);
            int line = 0;
            for (Badge badge : badges) {
                if (badge == null || badge.text() == null) continue;
                shadowed(g, badge.text(), RCJMS.SCREEN_WIDTH - BADGE_RIGHT_X, RCJMS.SCREEN_HEIGHT - (BADGE_BOTTOM_Y + line * BADGE_LINE_HEIGHT), badge.color());
                line++;
            }
        } finally { g.dispose(); }
    }
    void drawDebug(BufferedImage image, String fpsText, String openClText, Color openClColor) {
        Graphics2D g = image.createGraphics();
        try {
            g.scale(GameView.uiScale(), GameView.uiScale());
            g.setFont(FPS_FONT);
            shadowed(g, fpsText, 38, RCJMS.SCREEN_HEIGHT - 30, Color.GREEN);
            g.setFont(OPENCL_FONT);
            shadowed(g, openClText, 38, RCJMS.SCREEN_HEIGHT - 56, openClColor);
        } finally { g.dispose(); }
    }
    private static void shadowed(Graphics2D g, String text, int x, int y, Color color) {
        g.setColor(Color.BLACK);
        g.drawString(text, x + 2, y + 2);
        g.setColor(color);
        g.drawString(text, x, y);
    }
    //endregion
}