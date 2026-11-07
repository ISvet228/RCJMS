package Project.Game;

import Project.Helpers.*;

import java.awt.*;
import java.awt.image.BufferedImage;

final class TextMode {
    //region Variables
    //region Palette
    static final Color BRIGHT = new Color(0x7C, 0xFF, 0x6A), DIM = new Color(0x2A, 0x6B, 0x22), BLACK = Color.BLACK;
    //endregion
    //region Minimap Size
    static final int MAP_CELLS_X = 31, MAP_CELLS_Y = 17;
    private static final int FRAME_COLS = MAP_CELLS_X + 2, FRAME_ROWS = MAP_CELLS_Y + 2;
    private static final double PANEL_SHARE = 0.40;
    //endregion
    //region View
    private static final double PLANE = 0.66, MAX_DISTANCE = 40;
    //endregion
    //endregion

    //region Constructors
    private TextMode() {}
    //endregion

    //region Layout
    static BufferedImage build(int w, int h, int[][] map, float[][] known, double px, double py, double angle, String input, String status) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = img.createGraphics();
        g2d.setColor(BLACK);
        g2d.fillRect(0, 0, w, h);
        Font mainFont = FontCache.get(Font.MONOSPACED, Font.PLAIN, Math.max(10, h / 36));
        g2d.setFont(mainFont);
        FontMetrics fontMetrics = g2d.getFontMetrics();
        int cw = fontMetrics.charWidth('M'), ch = fontMetrics.getHeight();
        int cols = Math.max(20, w / cw), rows = Math.max(12, h / ch);

        int panelW = (int) (w * PANEL_SHARE), panelH = (rows - 3) * ch;
        Font miniFont = fitMinimapFont(g2d, panelW, panelH);
        g2d.setFont(miniFont);
        FontMetrics mfm = g2d.getFontMetrics();
        int mcw = mfm.charWidth('M'), mch = mfm.getHeight();
        int gridX = Math.max(0, (panelW - mcw * FRAME_COLS) / 2), gridY = Math.max(0, (panelH - mch * FRAME_ROWS) / 2);
        drawMinimap(g2d, mfm, mcw, mch, gridX, gridY, map, known, px, py, angle);
        drawFrame(g2d, mfm, mcw, mch, gridX, gridY);

        g2d.setFont(mainFont);
        int viewX = panelW + cw;
        int viewW = w - viewX - cw, viewH = (rows - 4) * ch;
        if (viewW > 40 && viewH > 40) {
            BufferedImage view = renderView(Math.max(40, viewW / 4), Math.max(30, viewH / 4), map, px, py, angle);
            g2d.drawImage(view, viewX, ch, viewW, viewH, null);
            g2d.setColor(DIM);
            g2d.drawRect(viewX - 1, ch - 1, viewW + 1, viewH + 1);
        }
        g2d.setColor(DIM);
        g2d.drawString(clip(status, cols - 2), cw, (rows - 3) * ch + fontMetrics.getAscent());
        g2d.setColor(BRIGHT);
        String prompt = "> " + input + "_";
        g2d.drawString(clip(prompt, cols - 2), cw, (rows - 1) * ch + fontMetrics.getAscent());
        g2d.dispose();
        return img;
    }
    //endregion

    //region Rendering
    static BufferedImage renderView(int w, int h, int[][] map, double px, double py, double angle) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int[] pixels = ((java.awt.image.DataBufferInt) img.getRaster().getDataBuffer()).getData();
        java.util.Arrays.fill(pixels, BLACK.getRGB());
        double dirX = Math.cos(angle), dirY = Math.sin(angle);
        double planeX = -dirY * PLANE, planeY = dirX * PLANE;
        for (int x = 0; x < w; x++) {
            double camera = 2.0 * x / w - 1;
            double rayX = dirX + planeX * camera, rayY = dirY + planeY * camera;
            double dist = 0;
            boolean sideY = false;
            int mapX = (int) Math.floor(px), mapY = (int) Math.floor(py);
            double deltaX = rayX == 0 ? 1e30 : Math.abs(1 / rayX);
            double deltaY = rayY == 0 ? 1e30 : Math.abs(1 / rayY);
            int stepX = rayX < 0 ? -1 : 1, stepY = rayY < 0 ? -1 : 1;
            double sideX = (rayX < 0 ? px - mapX : mapX + 1.0 - px) * deltaX;
            double sideYDist = (rayY < 0 ? py - mapY : mapY + 1.0 - py) * deltaY;
            boolean hit = false;
            while (!hit && dist < MAX_DISTANCE) {
                if (sideX < sideYDist) { sideX += deltaX; mapX += stepX; sideY = false; }
                else { sideYDist += deltaY; mapY += stepY; sideY = true; }
                hit = mapY < 0 || mapX < 0 || mapY >= map.length || mapX >= map[0].length || map[mapY][mapX] == MazeGenerator.WALL;
                dist = sideY ? sideYDist - deltaY : sideX - deltaX;
            }
            if (!hit) continue;
            double perp = Math.max(0.05, sideY ? sideYDist - deltaY : sideX - deltaX);
            int lineH = (int) Math.min(h * 4.0, h / perp);
            int from = Math.max(0, (h - lineH) / 2), to = Math.min(h - 1, (h + lineH) / 2);
            int color = sideY ? DIM.getRGB() : BRIGHT.getRGB();
            for (int y = from; y <= to; y++) pixels[y * w + x] = color;
        }
        return img;
    }
    static char cellSymbol(int type) {
        return switch (type) {
            case MazeGenerator.WALL -> '#';
            case MazeGenerator.FINISH -> '*';
            case 3, 4 -> '=';
            case MazeGenerator.PORTAL -> ' ';
            default -> '.';
        };
    }
    private static char facing(double angle) {
        int quarter = (int) Math.floorMod(Math.round(angle / (Math.PI / 2)), 4L);
        return switch (quarter) {
            case 0 -> '>';
            case 1 -> 'v';
            case 2 -> '<';
            default -> '^';
        };
    }
    //endregion

    //region Minimap
    private static void drawMinimap(Graphics2D g2d, FontMetrics fontMetrics, int cw, int ch, int gridX, int gridY,
                                    int[][] map, float[][] known, double px, double py, double angle) {
        int cx = (int) Math.floor(px), cy = (int) Math.floor(py);
        int left = cx - MAP_CELLS_X / 2, top = cy - MAP_CELLS_Y / 2;
        for (int r = 0; r < MAP_CELLS_Y; r++) {
            for (int c = 0; c < MAP_CELLS_X; c++) {
                int mx = left + c, my = top + r;
                boolean inside = my >= 0 && my < map.length && mx >= 0 && mx < map[0].length;
                char symbol;
                Color color = BRIGHT;
                if (c == MAP_CELLS_X / 2 && r == MAP_CELLS_Y / 2) symbol = facing(angle);
                else if (!inside) symbol = ' ';
                else if (known != null && known[my][mx] <= 0) symbol = ' ';
                else {
                    symbol = cellSymbol(map[my][mx]);
                    if (known != null && known[my][mx] < 0.5f) color = DIM;
                }
                if (symbol == ' ') continue;
                g2d.setColor(color);
                g2d.drawString(String.valueOf(symbol), gridX + (1 + c) * cw, gridY + (1 + r) * ch + fontMetrics.getAscent());
            }
        }
    }
    private static Font fitMinimapFont(Graphics2D g2d, int panelW, int panelH) {
        int pad = 6;
        for (int size = 40; size >= 6; size--) {
            Font font = FontCache.get(Font.MONOSPACED, Font.PLAIN, size);
            FontMetrics fontMetrics = g2d.getFontMetrics(font);
            if (fontMetrics.charWidth('M') * FRAME_COLS <= panelW - 2 * pad && fontMetrics.getHeight() * FRAME_ROWS <= panelH - 2 * pad) return font;
        }
        return FontCache.get(Font.MONOSPACED, Font.PLAIN, 6);
    }
    private static void drawFrame(Graphics2D g2d, FontMetrics fontMetrics, int cw, int ch, int x0, int y0) {
        g2d.setColor(DIM);
        int base = fontMetrics.getAscent();
        StringBuilder top = new StringBuilder("┌"), bottom = new StringBuilder("└");
        for (int i = 0; i < TextMode.FRAME_COLS - 2; i++) { top.append('─'); bottom.append('─'); }
        top.append('┐');
        bottom.append('┘');
        g2d.drawString(top.toString(), x0, y0 + base);
        g2d.drawString(bottom.toString(), x0, y0 + (TextMode.FRAME_ROWS - 1) * ch + base);
        for (int r = 1; r < TextMode.FRAME_ROWS - 1; r++) {
            g2d.drawString("│", x0, y0 + r * ch + base);
            g2d.drawString("│", x0 + (TextMode.FRAME_COLS - 1) * cw, y0 + r * ch + base);
        }
    }
    private static String clip(String s, int max) {
        if (s.length() <= max) return s;
        return s.substring(0, Math.max(0, max));
    }
    //endregion
}
