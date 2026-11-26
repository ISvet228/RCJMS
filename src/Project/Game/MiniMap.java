package Project.Game;

import Project.RCJMS;
import Project.Helpers.*;

import java.awt.*;
import java.awt.image.BufferedImage;

final class MiniMap {
    //region State
    private static final Font MINI_MARKER_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 10);
    private static final double MINI_MAP_MIN_CELL_PIXELS = 4.0;
    private static volatile int miniMapCellPixels = 6;
    private static final Font FOG_HINT_FONT = FontCache.get(Font.MONOSPACED, Font.BOLD, 12);
    private static final double FOG_REVEAL_RADIUS = 4.5, FOG_REVEAL_SPEED = 3.0, FOG_RAY_STEP = 0.08;
    private static final int FOG_RAY_COUNT = 120;
    private boolean fogMinimap, fogMapVisible = true;
    private float[][][] explored;
    private int[][] fogVisitStamp;
    private int fogFrame;

    private int[] pixels;
    private BufferedImage image;
    private int[][] map;
    private double playerX, playerY, cameraAngle;
    private boolean hyperbolic;
    private int floor, floors;
    //endregion

    //region Frame Data
    void sync(int[] pixels, BufferedImage image, int[][] map, double playerX, double playerY, double cameraAngle, boolean hyperbolic, int floor, int floors) {
        this.pixels = pixels;
        this.image = image;
        this.map = map;
        this.playerX = playerX;
        this.playerY = playerY;
        this.cameraAngle = cameraAngle;
        this.hyperbolic = hyperbolic;
        this.floor = floor;
        this.floors = floors;
    }
    static void zoomIn() { miniMapCellPixels = Math.min(20, miniMapCellPixels + 1); }
    static void zoomOut() { miniMapCellPixels = Math.max(3, miniMapCellPixels - 1); }
    private int mapWidth() { return map[0].length; }
    private int mapHeight() { return map.length; }
    //endregion

    //region Mini Map
    void drawMiniMap() {
        if (hyperbolic) { drawCoolMiniMap(); return; }
        double s = GameView.uiScale();
        int miniMapWidth = Math.max(1, (int) Math.round(GameView.MINI_MAP_WIDTH * s));
        int miniMapHeight = Math.max(1, (int) Math.round(GameView.MINI_MAP_HEIGHT * s));
        int margin = Math.max(1, (int) Math.round(10 * s));
        int mapWidth = map[0].length, mapHeight = map.length;
        double scale = Math.min((double) miniMapWidth / mapWidth, (double) miniMapHeight / mapHeight);
        if (scale < MINI_MAP_MIN_CELL_PIXELS * s) { drawLocalMiniMap(miniMapWidth, miniMapHeight, margin, s); return; }

        int offsetX = margin + (miniMapWidth - (int) (mapWidth * scale)) / 2;
        int offsetY = margin + (miniMapHeight - (int) (mapHeight * scale)) / 2;

        for (int y = 0; y < mapHeight; y++) {
            for (int x = 0; x < mapWidth; x++) {
                int color = 0x000000;
                if (map[y][x] == 1) color = 0xFFFFFF;
                if (map[y][x] == 2) color = 0x00FF00;
                if (map[y][x] == 3) color = 0x3399FF;
                if (map[y][x] == 4) color = 0xFFAA33;
                if (map[y][x] == MazeGenerator.PORTAL) color = 0xCC33FF;

                int startX = offsetX + (int) (x * scale), startY = offsetY + (int) (y * scale);
                int endX = offsetX + (int) ((x + 1) * scale), endY = offsetY + (int) ((y + 1) * scale);

                for (int py = startY; py < endY; py++) for (int px = startX; px < endX; px++)
                    if (px >= 0 && py >= 0 && px < RCJMS.GAME_WIDTH && py < RCJMS.GAME_HEIGHT) pixels[px + py * RCJMS.GAME_WIDTH] = color;
            }
        }

        drawMiniMapMarkers(offsetX, offsetY, scale, offsetX, offsetY, offsetX + (int) (mapWidth * scale), offsetY + (int) (mapHeight * scale));
        int playerSize = Math.max(1, (int)Math.floor(scale * 0.25));
        for (int yy = -playerSize; yy <= playerSize; yy++) {
            for (int xx = -playerSize; xx <= playerSize; xx++) {
                int px = offsetX + (int)(playerX * scale) + xx;
                int py = offsetY + (int)(playerY * scale) + yy;
                if (px >= 0 && py >= 0 && px < RCJMS.GAME_WIDTH && py < RCJMS.GAME_HEIGHT) pixels[px + py * RCJMS.GAME_WIDTH] = 0xFF0000;
            }
        }
    }
    private void drawLocalMiniMap(int width, int height, int margin, double s) {
        int cell = Math.max(2, (int) Math.round(miniMapCellPixels * s));
        int mapWidth = map[0].length, mapHeight = map.length;
        double visibleX = Math.min(mapWidth, (double) width / cell), visibleY = Math.min(mapHeight, (double) height / cell);
        double viewLeft = Math.clamp(playerX - visibleX / 2.0, 0.0, mapWidth - visibleX);
        double viewTop = Math.clamp(playerY - visibleY / 2.0, 0.0, mapHeight - visibleY);

        int boxW = (int) Math.min(width, Math.ceil(visibleX * cell)), boxH = (int) Math.min(height, Math.ceil(visibleY * cell));
        fillMiniRect(margin - 1, margin - 1, margin + boxW + 1, margin + boxH + 1, 0x8888FF, 0, 0, RCJMS.GAME_WIDTH, RCJMS.GAME_HEIGHT);
        fillMiniRect(margin, margin, margin + boxW, margin + boxH, 0x000000, 0, 0, RCJMS.GAME_WIDTH, RCJMS.GAME_HEIGHT);

        int firstX = (int) Math.floor(viewLeft), lastX = Math.min(mapWidth - 1, (int) Math.ceil(viewLeft + visibleX));
        int firstY = (int) Math.floor(viewTop), lastY = Math.min(mapHeight - 1, (int) Math.ceil(viewTop + visibleY));
        for (int y = firstY; y <= lastY; y++) {
            for (int x = firstX; x <= lastX; x++) {
                int type = map[y][x];
                if (type == 0) continue;
                int color = type == 2 ? 0x00FF00 : type == 3 ? 0x3399FF : type == 4 ? 0xFFAA33 : type == MazeGenerator.PORTAL ? 0xCC33FF : 0xFFFFFF;
                int x0 = margin + (int) Math.round((x - viewLeft) * cell), x1 = margin + (int) Math.round((x + 1 - viewLeft) * cell);
                int y0 = margin + (int) Math.round((y - viewTop) * cell), y1 = margin + (int) Math.round((y + 1 - viewTop) * cell);
                x1 = Math.max(x0 + 1, x1); y1 = Math.max(y0 + 1, y1);
                fillMiniRect(x0, y0, x1, y1, color, margin, margin, margin + boxW, margin + boxH);
            }
        }

        int px = margin + (int) Math.round((playerX - viewLeft) * cell), py = margin + (int) Math.round((playerY - viewTop) * cell);
        int tick = Math.max(cell, (int) Math.round(cell * 1.8));
        drawMiniMapLine(px, py, px + (int) Math.round(Math.cos(cameraAngle) * tick), py + (int) Math.round(Math.sin(cameraAngle) * tick));
        int half = Math.max(1, cell / 3);
        fillMiniRect(px - half, py - half, px + half + 1, py + half + 1, 0xFF0000, margin, margin, margin + boxW, margin + boxH);
        drawMiniMapMarkers(margin - viewLeft * cell, margin - viewTop * cell, cell, margin, margin, margin + boxW, margin + boxH);
    }

    //region Rival Markers
    private java.util.List<GameView.MiniMapMarker> miniMapMarkersHere() {
        java.util.function.IntFunction<java.util.List<GameView.MiniMapMarker>> source = GameView.miniMapMarkers;
        if (source == null) return java.util.List.of();
        try {
            java.util.List<GameView.MiniMapMarker> markers = source.apply(floor);
            return markers == null ? java.util.List.of() : markers;
        } catch (RuntimeException exception) {
            return java.util.List.of();
        }
    }
    private void drawMiniMapMarkers(double originX, double originY, double cell, int clipX0, int clipY0, int clipX1, int clipY1) {
        java.util.List<GameView.MiniMapMarker> markers = miniMapMarkersHere();
        if (markers.isEmpty()) return;
        Graphics2D g = image.createGraphics();
        try {
            setupMarkerPainting(g, clipX0, clipY0, clipX1, clipY1);
            for (GameView.MiniMapMarker marker : markers) {
                if (!Double.isFinite(marker.x()) || !Double.isFinite(marker.y())) continue;
                double px = clampTo(originX + marker.x() * cell, clipX0 + 4, clipX1 - 4);
                double py = clampTo(originY + marker.y() * cell, clipY0 + 4, clipY1 - 4);
                paintMarker(g, marker, px, py);
            }
        } finally { g.dispose(); }
    }
    private void drawCoolMiniMapMarkers(int centerX, int centerY, int radiusPx) {
        java.util.List<GameView.MiniMapMarker> markers = miniMapMarkersHere();
        if (markers.isEmpty()) return;
        Graphics2D g = image.createGraphics();
        try {
            setupMarkerPainting(g, centerX - radiusPx, centerY - radiusPx, centerX + radiusPx, centerY + radiusPx);
            for (GameView.MiniMapMarker marker : markers) {
                if (!Double.isFinite(marker.x()) || !Double.isFinite(marker.y())) continue;
                double dx = marker.x() - playerX, dy = marker.y() - playerY;
                double diskR = Math.min(HyperbolicMath.poincareRadius(Math.hypot(dx, dy), RenderEngineConfig.HYPERBOLIC_CURVATURE), 0.98);
                double angle = Math.atan2(dy, dx);
                paintMarker(g, marker, centerX + Math.cos(angle) * diskR * radiusPx, centerY + Math.sin(angle) * diskR * radiusPx);
            }
        } finally { g.dispose(); }
    }

    private static void setupMarkerPainting(Graphics2D g, int x0, int y0, int x1, int y1) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setClip(x0, y0, x1 - x0, y1 - y0);
        g.setFont(MINI_MARKER_FONT);
    }
    private static void paintMarker(Graphics2D g, GameView.MiniMapMarker marker, double px, double py) {
        int x = (int) Math.round(px), y = (int) Math.round(py);
        g.setColor(Color.BLACK);
        g.fillOval(x - 4, y - 4, 9, 9);
        g.setColor(new Color(marker.color()));
        g.fillOval(x - 3, y - 3, 7, 7);
        g.setColor(Color.BLACK);
        g.drawString(marker.label(), x + 6, y + 4);
        g.setColor(Color.WHITE);
        g.drawString(marker.label(), x + 5, y + 3);
    }

    private static double clampTo(double value, double min, double max) { return Math.min(Math.max(value, min), max); }
    //endregion

    private void fillMiniRect(int x0, int y0, int x1, int y1, int color, int clipX0, int clipY0, int clipX1, int clipY1) {
        x0 = Math.max(x0, Math.max(clipX0, 0)); y0 = Math.max(y0, Math.max(clipY0, 0));
        x1 = Math.min(x1, Math.min(clipX1, RCJMS.GAME_WIDTH)); y1 = Math.min(y1, Math.min(clipY1, RCJMS.GAME_HEIGHT));
        for (int yy = y0; yy < y1; yy++) {
            int row = yy * RCJMS.GAME_WIDTH;
            for (int xx = x0; xx < x1; xx++) pixels[row + xx] = color;
        }
    }
    private void drawCoolMiniMap() {
        double s = GameView.uiScale();
        int miniMapWidth = Math.max(1, (int) Math.round(GameView.MINI_MAP_WIDTH * s));
        int miniMapHeight = Math.max(1, (int) Math.round(GameView.MINI_MAP_HEIGHT * s));
        int margin = Math.max(1, (int) Math.round(10 * s));
        int diskRadiusPx = Math.min(miniMapWidth, miniMapHeight) / 2;
        int centerX = margin + diskRadiusPx, centerY = margin + diskRadiusPx;

        for (int yy = -diskRadiusPx; yy <= diskRadiusPx; yy++) {
            for (int xx = -diskRadiusPx; xx <= diskRadiusPx; xx++) {
                if (xx * xx + yy * yy > diskRadiusPx * diskRadiusPx) continue;
                int px = centerX + xx, py = centerY + yy;
                if (px >= 0 && py >= 0 && px < RCJMS.GAME_WIDTH && py < RCJMS.GAME_HEIGHT) pixels[px + py * RCJMS.GAME_WIDTH] = 0x0A0A14;
            }
        }

        for (int y = 0; y < map.length; y++) {
            for (int x = 0; x < map[0].length; x++) {
                if (map[y][x] == 0) continue;

                double diskR = HyperbolicMath.poincareRadius(Math.hypot((x + 0.5) - playerX, (y + 0.5) - playerY), RenderEngineConfig.HYPERBOLIC_CURVATURE);
                if (diskR >= 0.995) continue;

                double angle = Math.atan2((y + 0.5) - playerY, (x + 0.5) - playerX);
                int color = map[y][x] == 2 ? 0x00FF00 : map[y][x] == 3 ? 0x3399FF : map[y][x] == 4 ? 0xFFAA33 : map[y][x] == MazeGenerator.PORTAL ? 0xCC33FF : 0xFFFFFF;
                int cellPixelSize = Math.max(1, (int) Math.round((1.0 - diskR) * 4 * s));

                for (int oy = -cellPixelSize; oy <= cellPixelSize; oy++) {
                    for (int ox = -cellPixelSize; ox <= cellPixelSize; ox++) {
                        if (ox * ox + oy * oy > cellPixelSize * cellPixelSize) continue;
                        int fx = centerX + (int)Math.round(Math.cos(angle) * diskR * diskRadiusPx) + ox;
                        int fy = centerY + (int)Math.round(Math.sin(angle) * diskR * diskRadiusPx) + oy;
                        if (fx < 0 || fy < 0 || fx >= RCJMS.GAME_WIDTH || fy >= RCJMS.GAME_HEIGHT) continue;
                        if ((fx - centerX) * (fx - centerX) + (fy - centerY) * (fy - centerY) > diskRadiusPx * diskRadiusPx) continue;
                        pixels[fx + fy * RCJMS.GAME_WIDTH] = color;
                    }
                }
            }
        }
        drawCoolMiniMapMarkers(centerX, centerY, diskRadiusPx);
        for (double a = 0; a < Math.PI * 2; a += 0.01) {
            int px = centerX + (int) Math.round(Math.cos(a) * diskRadiusPx);
            int py = centerY + (int) Math.round(Math.sin(a) * diskRadiusPx);
            if (px >= 0 && py >= 0 && px < RCJMS.GAME_WIDTH && py < RCJMS.GAME_HEIGHT) pixels[px + py * RCJMS.GAME_WIDTH] = 0x8888FF;
        }
        int tickLen = diskRadiusPx - Math.max(1, (int) Math.round(4 * s));
        drawMiniMapLine(centerX, centerY, centerX + (int)Math.round(Math.cos(cameraAngle) * tickLen),
                centerY + (int)Math.round(Math.sin(cameraAngle) * tickLen));
        int playerSize = Math.max(1, (int) Math.round(3 * s));
        for (int yy = -playerSize; yy <= playerSize; yy++) {
            for (int xx = -playerSize; xx <= playerSize; xx++) {
                int px = centerX + xx, py = centerY + yy;
                if (px >= 0 && py >= 0 && px < RCJMS.GAME_WIDTH && py < RCJMS.GAME_HEIGHT) pixels[px + py * RCJMS.GAME_WIDTH] = 0xFF0000;
            }
        }
    }
    private void drawMiniMapLine(int x0, int y0, int x1, int y1) {
        int dx = Math.abs(x1 - x0), sx = x0 < x1 ? 1 : -1, dy = -Math.abs(y1 - y0), sy = y0 < y1 ? 1 : -1, err = dx + dy;
        while (true) {
            if (x0 >= 0 && y0 >= 0 && x0 < RCJMS.GAME_WIDTH && y0 < RCJMS.GAME_HEIGHT) pixels[x0 + y0 * RCJMS.GAME_WIDTH] = 16755200;
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x0 += sx; }
            if (e2 <= dx) { err += dx; y0 += sy; }
        }
    }
    //endregion

    //region Fog Of War
    void setFogEnabled(boolean enabled) { fogMinimap = enabled; explored = null; fogMapVisible = true; }
    boolean fogEnabled() { return fogMinimap; }
    void toggleFogVisible() { if (fogMinimap) fogMapVisible = !fogMapVisible; }
    boolean fogVisible() { return fogMinimap && fogMapVisible; }
    void resetFog() { explored = null; }
    float[][] exploredGrid() {
        int h = mapHeight(), w = mapWidth();
        if (explored == null || explored.length != floors || explored[0].length != h || explored[0][0].length != w) {
            explored = new float[floors][h][w];
            fogVisitStamp = new int[h][w];
        }
        return explored[floor];
    }
    void updateFog(double dt) {
        float[][] grid = exploredGrid();
        final int w = mapWidth(), h = mapHeight();
        final int stamp = ++fogFrame;
        final double ox = playerX, oy = playerY;
        final int startX = Math.clamp((int) ox, 0, w - 1), startY = Math.clamp((int) oy, 0, h - 1);
        revealFogCell(grid, startX, startY, 0, dt, stamp);
        for (int i = 0; i < FOG_RAY_COUNT; i++) {
            double angle = i * (2.0 * Math.PI / FOG_RAY_COUNT);
            double dx = Math.cos(angle), dy = Math.sin(angle);
            int lastX = startX, lastY = startY;
            for (double dist = FOG_RAY_STEP; dist <= FOG_REVEAL_RADIUS; dist += FOG_RAY_STEP) {
                int cx = (int) Math.floor(ox + dx * dist), cy = (int) Math.floor(oy + dy * dist);
                if (cx < 0 || cy < 0 || cx >= w || cy >= h) break;
                if (cx == lastX && cy == lastY) continue;
                lastX = cx; lastY = cy;
                revealFogCell(grid, cx, cy, dist, dt, stamp);
                int type = map[cy][cx];
                if (type == 1 || type == 2) break;
            }
        }
    }
    private void revealFogCell(float[][] grid, int x, int y, double distance, double dt, int stamp) {
        if (fogVisitStamp[y][x] == stamp) return;
        fogVisitStamp[y][x] = stamp;
        double closeness = 1.0 - Math.min(1.0, distance / FOG_REVEAL_RADIUS);
        grid[y][x] = Math.min(1f, grid[y][x] + (float) (dt * FOG_REVEAL_SPEED * (0.15 + 0.85 * closeness)));
    }
    private static int fogCellColor(int type) {
        return type == 1 ? 0xD8D8E6 : type == 2 ? 0x00FF66 : type == 3 ? 0x3399FF : type == 4 ? 0xFFAA33 : type == MazeGenerator.PORTAL ? 0xCC33FF : 0x2E2E44;
    }
    private static int mixRgb(int from, int to, double amount) {
        amount = Math.clamp(amount, 0.0, 1.0);
        int r = (int) (((from >> 16) & 255) * (1 - amount) + ((to >> 16) & 255) * amount);
        int g = (int) (((from >> 8) & 255) * (1 - amount) + ((to >> 8) & 255) * amount);
        int b = (int) ((from & 255) * (1 - amount) + (to & 255) * amount);
        return (r << 16) | (g << 8) | b;
    }
    void drawFogMiniMap() {
        float[][] grid = exploredGrid();
        final int w = mapWidth(), h = mapHeight();
        double s = GameView.uiScale();
        int cell = Math.max(3, (int) Math.round(miniMapCellPixels * s));
        int cells = Math.max(7, (int) Math.round(GameView.MINI_MAP_WIDTH * s) / cell);
        if (cells % 2 == 0) cells++;
        int box = cells * cell;
        int margin = Math.max(1, (int) Math.round(10 * s));
        double viewLeft = playerX - cells / 2.0, viewTop = playerY - cells / 2.0;

        fillMiniRect(margin - 2, margin - 2, margin + box + 2, margin + box + 2, 0x5C5C8A, 0, 0, RCJMS.GAME_WIDTH, RCJMS.GAME_HEIGHT);
        int firstX = (int) Math.floor(viewLeft), firstY = (int) Math.floor(viewTop);
        for (int vy = 0; vy <= cells; vy++) {
            for (int vx = 0; vx <= cells; vx++) {
                int cx = firstX + vx, cy = firstY + vy;
                int color;
                if (cx < 0 || cy < 0 || cx >= w || cy >= h) color = 0x050509;
                else {
                    int fog = ((cx + cy) & 1) == 0 ? 0x16162A : 0x12121F;
                    color = grid[cy][cx] <= 0 ? fog : mixRgb(fog, fogCellColor(map[cy][cx]), grid[cy][cx]);
                }
                int x0 = margin + (int) Math.round((cx - viewLeft) * cell), x1 = margin + (int) Math.round((cx + 1 - viewLeft) * cell);
                int y0 = margin + (int) Math.round((cy - viewTop) * cell), y1 = margin + (int) Math.round((cy + 1 - viewTop) * cell);
                fillMiniRect(x0, y0, x1, y1, color, margin, margin, margin + box, margin + box);
            }
        }

        int px = margin + (int) Math.round((playerX - viewLeft) * cell), py = margin + (int) Math.round((playerY - viewTop) * cell);
        int tick = Math.max(cell, (int) Math.round(cell * 1.8));
        drawMiniMapLine(px, py, px + (int) Math.round(Math.cos(cameraAngle) * tick), py + (int) Math.round(Math.sin(cameraAngle) * tick));
        int half = Math.max(1, cell / 3);
        fillMiniRect(px - half, py - half, px + half + 1, py + half + 1, 0xFF0000, margin, margin, margin + box, margin + box);
        drawMiniMapMarkers(margin - viewLeft * cell, margin - viewTop * cell, cell, margin, margin, margin + box, margin + box);

        Graphics2D g2d = image.createGraphics();
        g2d.scale(s, s);
        g2d.setFont(FOG_HINT_FONT);
        g2d.setColor(new Color(150, 150, 190));
        g2d.drawString("M", (float) ((margin + box) / s - 10), (float) ((margin + box) / s + 14));
        g2d.dispose();
    }
    //endregion
}