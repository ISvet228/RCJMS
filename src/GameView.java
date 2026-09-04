import Helpers.MazeGenerator;

import javax.swing.*; //Frame Library
import java.awt.*; //Graphics Library
import java.awt.event.*; //Input Library
import java.awt.image.BufferedImage; //Buffer Library
import java.awt.image.DataBufferInt;

public class GameView extends JPanel implements Runnable, KeyListener, MouseMotionListener {
    //region Variables
    public static int MINI_MAP_WIDTH = 250;
    public static int MINI_MAP_HEIGHT = 250;
    public static int MAZE_WIDTH = 51;
    public static int MAZE_HEIGHT = 51;
    public static MazeGenerator.FinishMode MAZE_MODE = MazeGenerator.FinishMode.RANDOM_EDGE;

    //region Dependencies
    private MazeGenerator mazeGenerator;
    private final BufferedImage bufferedImage;
    private final Cursor invisibleCursor;
    private Robot cursorRobot; //BOBR KURSOR JA PERDOLE
    private Thread gameThread;
    //endregion

    private final int[] pixels; //BRUH JUST PIXELS IN IMAGE
    private int[][] map;

    //region Dynamic Player Stats
    private double playerX = 1.5;
    private double playerY = 1.5;
    private double cameraAngle = 0;
    private double cameraPitch = 0;
    private boolean w, a, s, d, shift;
    //endregion

    //region Player Stats
    private final double moveSpeed = 1.5;
    private final double runSpeed = 3;
    private final double mouseSensitivity = 0.003;
    private final double flashlightDistance = 2;
    private boolean noClip = false;
    //endregion

    //region Other Stuff
    private boolean isGameRunning = false;
    private boolean isRecentering = false; //Mouse Recursion Helper
    private boolean isPaused = false;
    private boolean isDebugMode = false;
    private long gameStartTime = System.currentTimeMillis();
    private long pauseStartTime = 0;
    private long pausedTime = 0;
    private long elapsedSeconds;
    //endregion
    //endregion

    //region Helpers
    public GameView(int mazeWidth, int mazeHeight, int mazeMode) {
        setPreferredSize(new Dimension(RCJMS.SCREEN_WIDTH, RCJMS.SCREEN_HEIGHT));
        setFocusable(true); requestFocus();

        bufferedImage = new BufferedImage(RCJMS.SCREEN_WIDTH, RCJMS.SCREEN_HEIGHT, BufferedImage.TYPE_INT_RGB);
        pixels = ((DataBufferInt) bufferedImage.getRaster().getDataBuffer()).getData();

        mazeGenerator = new MazeGenerator(MAZE_WIDTH = mazeWidth, MAZE_HEIGHT = mazeHeight);
        map = mazeGenerator.generate(MAZE_MODE = MazeGenerator.FinishMode.values()[mazeMode]);

        addKeyListener(this);
        addMouseMotionListener(this);

        try { cursorRobot = new Robot(); }
        catch (Exception e) { e.printStackTrace(); }

        Toolkit toolkit = Toolkit.getDefaultToolkit();
        Image img = toolkit.createImage(new byte[0]);
        invisibleCursor = toolkit.createCustomCursor(img, new Point(0,0), "hidden");
        hideCursor();
    }
    private void hideCursor() { setCursor(invisibleCursor); }
    private void showCursor() { setCursor(Cursor.getDefaultCursor()); }
    //endregion

    //region Life Status Operations
    public void start() {
        isGameRunning = true;
        gameThread = new Thread(this);
        gameThread.start();
    }
    @Override public void run() { //OMG IT'S DA GAME CYCLE!!!
        long lastFrameTime = System.nanoTime();
        while (isGameRunning) {
            long currentFrameTime = System.nanoTime();
            double deltaTime = (currentFrameTime - lastFrameTime) / 1_000_000_000.0;
            lastFrameTime = currentFrameTime;
            if (!isPaused) update(deltaTime);

            render(); repaint();

            try {
                Thread.sleep(16); //IDK HELPS WITH FPS LIMIT BRUH 1000/60 = 16.66666666666667
            } catch (Exception ignored) {}
        }
    }
    private void update(double deltaTiime) {
        double speed = ((shift) ? runSpeed : moveSpeed) * deltaTiime;

        double strafeX = Math.cos(cameraAngle + Math.PI / 2);
        double strafeY = Math.sin(cameraAngle + Math.PI / 2);
        double nextX = playerX;
        double nextY = playerY;

        if (w) {
            nextX += Math.cos(cameraAngle) * speed;
            nextY += Math.sin(cameraAngle) * speed;
        }
        if (s) {
            nextX -= Math.cos(cameraAngle) * speed;
            nextY -= Math.sin(cameraAngle) * speed;
        }
        if (a) {
            nextX -= strafeX * speed;
            nextY -= strafeY * speed;
        }
        if (d) {
            nextX += strafeX * speed;
            nextY += strafeY * speed;
        }

        if (noClip) {
            playerX = nextX;
            playerY = nextY;

        } else {
            if (map[(int)Math.floor(playerY)][(int)Math.floor(nextX)] != 1) {
                playerX = nextX;
            } else {
                if (playerX < nextX) {
                    playerX = Math.ceil(playerX) - 0.0001;
                } else {
                    playerX = Math.floor(playerX);
                }
            }
            if (map[(int)Math.floor(nextY)][(int)Math.floor(playerX)] != 1) {
                playerY = nextY;
            } else {
                if (playerY < nextY) {
                    playerY = Math.ceil(playerY) - 0.0001;
                } else {
                    playerY = Math.floor(playerY);
                }
            }
        }

        if (map[(int) playerY][(int) playerX] == 2 && isGameRunning) {
            isGameRunning = false;
            playerX = 1.5;
            playerY = 1.5;
            cameraAngle = 0;
            cameraPitch = 0;
            RCJMS.instance.remove(RCJMS.instance.gameView);
            RCJMS.instance.add(RCJMS.instance.victoryView = new VictoryView(elapsedSeconds));
            RCJMS.instance.setTitle("Victory!");
            RCJMS.instance.pack();
            RCJMS.instance.revalidate();
            gameThread.interrupt();
        }
    }
    //endregion/

    //region Rendering
    private void render() {
        final int width = RCJMS.SCREEN_WIDTH;
        final int height = RCJMS.SCREEN_HEIGHT;

        int ceilingColor = 0x816E1E;
        int floorColor = 0xD3AF63;

        double FOV = Math.PI / 3.0;

        double dirX = Math.cos(cameraAngle);
        double dirY = Math.sin(cameraAngle);

        double planeLength = Math.tan(FOV / 2.0);

        double planeX = -dirY * planeLength;
        double planeY =  dirX * planeLength;

        double horizon = height / 2.0 + cameraPitch * height / 2.0;

        for (int y = 0; y < height; y++) {
            boolean isCeiling = y < horizon;
            int baseColor = isCeiling ? ceilingColor : floorColor;

            double distanceFromCenter = Math.abs(y - horizon);
            double distance = (distanceFromCenter / (height / 2.0)) * flashlightDistance;
            double brightness = distance / (flashlightDistance * (flashlightDistance * flashlightDistance - 20 * flashlightDistance + 83) / 8.0);

            brightness = Math.clamp(brightness, 0.05, 1.0);

            int shadedColor = applyBrightness(baseColor, brightness);
            int offset = y * width;

            for (int x = 0; x < width; x++) pixels[offset + x] = shadedColor;
        }

        for (int x = 0; x < width; x++) {
            double cameraX = 2.0 * x / (double) width - 1.0;

            double rayDirX = dirX + planeX * cameraX;
            double rayDirY = dirY + planeY * cameraX;

            int mapX = (int) playerX;
            int mapY = (int) playerY;

            double deltaDistX = rayDirX == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / rayDirX);
            double deltaDistY = rayDirY == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / rayDirY);

            int stepX, stepY;

            double sideDistX, sideDistY;

            if (rayDirX < 0) {
                stepX = -1;
                sideDistX = (playerX - mapX) * deltaDistX;
            } else {
                stepX = 1;
                sideDistX = (mapX + 1.0 - playerX) * deltaDistX;
            }

            if (rayDirY < 0) {
                stepY = -1;
                sideDistY = (playerY - mapY) * deltaDistY;
            } else {
                stepY = 1;
                sideDistY = (mapY + 1.0 - playerY) * deltaDistY;
            }

            boolean hit = false;
            int side = 0;
            int hitType = 0;

            while (!hit) {
                if (sideDistX < sideDistY) {
                    sideDistX += deltaDistX;
                    mapX += stepX;

                    side = 0;
                } else {
                    sideDistY += deltaDistY;
                    mapY += stepY;

                    side = 1;
                }

                if (mapX < 0 || mapY < 0 || mapX >= map[0].length || mapY >= map.length) break;
                hitType = map[mapY][mapX];

                if (hitType == 1 || hitType == 2) hit = true;
            }

            if (!hit) continue;

            double perpendicularDistance;
            if (side == 0) perpendicularDistance = sideDistX - deltaDistX;
            else perpendicularDistance = sideDistY - deltaDistY;
            perpendicularDistance = Math.max(perpendicularDistance, 0.0001);

            int wallHeight = (int) (height / perpendicularDistance);

            int start, end;

            if (hitType == 2) {
                start = (int) horizon;
                end = (int) (horizon + wallHeight / 2.0);
            } else {
                start = (int) (horizon - wallHeight / 2.0);
                end = (int) (horizon + wallHeight / 2.0);
            }

            int drawStart = Math.max(start, 0);
            int drawEnd = Math.min(end, height - 1);

            if (drawStart >= drawEnd) continue;

            int wallColor;
            if (hitType == 2) wallColor = 0x33FF66;
            else wallColor = 0xECD485;

            double brightness = 1.0 - (perpendicularDistance / flashlightDistance);
            brightness = Math.clamp(brightness, 0.03, 1.0);

            if (side == 1) brightness *= 0.85;

            wallColor = applyBrightness(wallColor, brightness);

            int offset = drawStart * width;

            for (int y = drawStart; y <= drawEnd; y++) {
                pixels[offset + x] = wallColor;
                offset += width;
            }
        }

        drawTimer();

        if (isDebugMode && !isPaused) drawMiniMap();
        if (isPaused) drawPauseMenu();
    }
    private void drawMiniMap() {
        int mapWidth = map[0].length;
        int mapHeight = map.length;

        double scaleX = (double) MINI_MAP_WIDTH / mapWidth;
        double scaleY = (double) MINI_MAP_HEIGHT / mapHeight;
        double scale = Math.min(scaleX, scaleY);

        int actualWidth = (int) (mapWidth * scale);
        int actualHeight = (int) (mapHeight * scale);

        int offsetX = 10 + (MINI_MAP_WIDTH - actualWidth) / 2;
        int offsetY = 10 + (MINI_MAP_HEIGHT - actualHeight) / 2;

        for (int y = 0; y < mapHeight; y++) {
            for (int x = 0; x < mapWidth; x++) {
                int color = 0x000000;
                if (map[y][x] == 1) color = 0xFFFFFF;
                if (map[y][x] == 2) color = 0x00FF00;

                int startX = offsetX + (int) (x * scale);
                int startY = offsetY + (int) (y * scale);
                int endX = offsetX + (int) ((x + 1) * scale);
                int endY = offsetY + (int) ((y + 1) * scale);

                for (int py = startY; py < endY; py++) {
                    for (int px = startX; px < endX; px++) {
                        if (px >= 0 && py >= 0 && px < RCJMS.SCREEN_WIDTH && py < RCJMS.SCREEN_HEIGHT)
                            pixels[px + py * RCJMS.SCREEN_WIDTH] = color;
                    }
                }
            }
        }

        int playerPixelX = offsetX + (int) (playerX * scale);
        int playerPixelY = offsetY + (int) (playerY * scale);
        int playerSize = Math.max(1, (int) Math.floor(scale * 0.25));

        for (int yy = -playerSize; yy <= playerSize; yy++) {
            for (int xx = -playerSize; xx <= playerSize; xx++) {
                int px = playerPixelX + xx;
                int py = playerPixelY + yy;

                if (px >= 0 && py >= 0 && px < RCJMS.SCREEN_WIDTH && py < RCJMS.SCREEN_HEIGHT)
                    pixels[px + py * RCJMS.SCREEN_WIDTH] = 0xFF0000;
            }
        }
    }
    private void drawTimer() {
        Graphics2D g = bufferedImage.createGraphics();

        long currentTime = isPaused ? pauseStartTime : System.currentTimeMillis();
        elapsedSeconds = (currentTime - gameStartTime - pausedTime) / 1000;

        long hours = elapsedSeconds / 3600;
        long minutes = (elapsedSeconds % 3600) / 60;
        long seconds = elapsedSeconds % 60;

        String timerText = String.format("%02d:%02d:%02d", hours, minutes, seconds);

        g.setFont(new Font("Consolas", Font.BOLD, 24));
        g.setColor(Color.BLACK);
        g.drawString(timerText, RCJMS.SCREEN_WIDTH - 156, 34);

        g.setColor(Color.WHITE);
        g.drawString(timerText, RCJMS.SCREEN_WIDTH - 158, 32);
        g.dispose();
    }

    private void drawPauseMenu() {
        Graphics2D g = bufferedImage.createGraphics();
        g.setColor(new Color(0,0,0,180));
        g.fillRect(0,0, RCJMS.SCREEN_WIDTH, RCJMS.SCREEN_HEIGHT);

        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 42));
        g.drawString("PAUSED", RCJMS.SCREEN_WIDTH / 2 - 100, RCJMS.SCREEN_HEIGHT / 2 - 20);

        g.setFont(new Font("Arial", Font.PLAIN, 20));
        g.drawString("ESC - Continue", RCJMS.SCREEN_WIDTH / 2 - 90, RCJMS.SCREEN_HEIGHT / 2 + 20);
        g.drawString("R - Restart", RCJMS.SCREEN_WIDTH / 2 - 90, RCJMS.SCREEN_HEIGHT / 2 + 50);
        g.drawString("SHIFT - Run", RCJMS.SCREEN_WIDTH / 2 - 90, RCJMS.SCREEN_HEIGHT / 2 + 80);
        g.drawString("Movement Speed = " + ((shift) ? runSpeed : moveSpeed), RCJMS.SCREEN_WIDTH / 2 - 90, RCJMS.SCREEN_HEIGHT / 2 + 110);
        g.drawString("Mouse Sensitivity = " + mouseSensitivity, RCJMS.SCREEN_WIDTH / 2 - 90, RCJMS.SCREEN_HEIGHT / 2 + 140);
        g.drawString("NoColip = " + noClip, RCJMS.SCREEN_WIDTH / 2 - 90, RCJMS.SCREEN_HEIGHT / 2 + 170);

        g.dispose();
    }
    private int applyBrightness(int color, double brightness) {
        int r = (color >> 16) & 255, g = (color >> 8) & 255, b = color & 255;

        r = (int)(r * brightness);
        g = (int)(g * brightness);
        b = (int)(b * brightness);

        return (r << 16) | (g << 8) | b;
    }
//endregion

    //region INPUT
    @Override public void keyPressed(KeyEvent e) {

        int key = e.getKeyCode();
        if (key == KeyEvent.VK_W) w = true;
        if (key == KeyEvent.VK_S) s = true;
        if (key == KeyEvent.VK_A) a = true;
        if (key == KeyEvent.VK_D) d = true;
        if (key == KeyEvent.VK_B) isDebugMode = !isDebugMode;
        if (key == KeyEvent.VK_SHIFT) shift = true;
        if (key == KeyEvent.VK_F1) noClip = !noClip;
        if (key == KeyEvent.VK_ESCAPE) {
            isPaused = !isPaused;
            if (isPaused) {
                pauseStartTime = System.currentTimeMillis();
                showCursor();
            } else {
                pausedTime += System.currentTimeMillis() - pauseStartTime;
                hideCursor();
            }
        }
        if (key == KeyEvent.VK_R) {
            mazeGenerator = new MazeGenerator(MAZE_WIDTH, MAZE_HEIGHT);
            map = mazeGenerator.generate(MAZE_MODE);
            playerX = 1.5;
            playerY = 1.5;
            cameraAngle = 0;
            cameraPitch = 0;
            gameStartTime = System.currentTimeMillis();
            pausedTime = 0;
        }
    }
    @Override public void keyReleased(KeyEvent e) {
        int key = e.getKeyCode();
        if (key == KeyEvent.VK_W) w = false;
        if (key == KeyEvent.VK_S) s = false;
        if (key == KeyEvent.VK_A) a = false;
        if (key == KeyEvent.VK_D) d = false;
        if (key == KeyEvent.VK_SHIFT) shift = false;
    }
    @Override
    public void mouseMoved(MouseEvent e) {
        if (isPaused) return;
        if (isRecentering) {
            isRecentering = false;
            return;
        }

        Point panelLocation = getLocationOnScreen();

        int centerX = panelLocation.x + getWidth() / 2;
        int centerY = panelLocation.y + getHeight() / 2;

        int dx = e.getXOnScreen() - centerX;
        int dy = e.getYOnScreen() - centerY;

        cameraAngle += dx * mouseSensitivity;
        cameraPitch -= dy * mouseSensitivity;
        cameraPitch = Math.clamp(cameraPitch, -1.2, 1.2);

        isRecentering = true;
        cursorRobot.mouseMove(centerX, centerY);
    }
    //endregion

    //region JUST EXIST
    @Override public void keyTyped(KeyEvent e) {}
    @Override public void mouseDragged(MouseEvent e){}
    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        int panelWidth = getWidth();
        int panelHeight = getHeight();
        int imageWidth = bufferedImage.getWidth();
        int imageHeight = bufferedImage.getHeight();

        double scaleX = (double) panelWidth / imageWidth;
        double scaleY = (double) panelHeight / imageHeight;
        double scale = Math.min(scaleX, scaleY);

        int drawWidth = (int) (imageWidth * scale);
        int drawHeight = (int) (imageHeight * scale);

        int drawX = (panelWidth - drawWidth) / 2;
        int drawY = (panelHeight - drawHeight) / 2;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
        g2.setColor(Color.BLACK);
        g2.fillRect(0, 0, panelWidth, panelHeight);
        g2.drawImage(bufferedImage, drawX, drawY, drawWidth, drawHeight, null);
        g2.dispose();
    }
    // endregion
}
