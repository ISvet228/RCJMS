import javax.swing.*; //Frame Library
import java.awt.*; //Graphics Library
import java.awt.event.*; //Input Library
import java.awt.image.BufferedImage; //Buffer Library
import java.awt.image.DataBufferInt;

public class GamePanel extends JPanel implements Runnable, KeyListener, MouseMotionListener {
    //region Variables
    public static final int SCREEN_WIDTH = 960;
    public static final int SCREEN_HEIGHT = 540;
    public static int MAZE_WIDTH = 51;
    public static int MAZE_HEIGHT = 51;
    public static MazeGenerator.FinishMode MAZE_MODE = MazeGenerator.FinishMode.RANDOM_EDGE;
    private static JFrame frame;

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
    public GamePanel(JFrame frame, int mazeWidth, int mazeHeight, int mazeMode) {
        GamePanel.frame = frame;
        setPreferredSize(new Dimension(SCREEN_WIDTH, SCREEN_HEIGHT));
        setFocusable(true); requestFocus();

        bufferedImage = new BufferedImage(SCREEN_WIDTH, SCREEN_HEIGHT, BufferedImage.TYPE_INT_RGB);
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
            frame.dispose();
            new VictoryScreen(elapsedSeconds);
            gameThread.interrupt();
        }
    }
    //endregion/

    //region Rendering
    private void render() {
        int ceilingColor = 0x816E1E, floorColor = 0xD3AF63;
        int horizon = SCREEN_HEIGHT / 2 + (int)(cameraPitch * 220);

        for (int y = 0; y < SCREEN_HEIGHT; y++) {
            boolean isCeiling = y < horizon;
            int baseColor = isCeiling ? ceilingColor : floorColor;

            double distanceFromCenter = Math.abs(y - horizon);
            double distance = (distanceFromCenter / (SCREEN_HEIGHT / 2.0)) * flashlightDistance;

            double brightness = distance / (flashlightDistance *
                    (flashlightDistance * flashlightDistance - 20 * flashlightDistance + 83) / 8.0); //flashlight 3 multiple by 4, flashlight 5 multiple by 1, flashlight 1 multiple by 8
            brightness = Math.clamp(brightness, 0.05, 1.0);
            int shadedColor = applyBrightness(baseColor, brightness);

            for (int x = 0; x < SCREEN_WIDTH; x++) {
                pixels[x + y * SCREEN_WIDTH] = shadedColor;
            }
        }
        for (int x = 0; x < SCREEN_WIDTH; x++) { //RAY RENDER

            double FOV = Math.PI / 3.0;
            double rayAngle = cameraAngle - FOV / 2 + (x / (double) SCREEN_WIDTH) * FOV;
            double rayX = playerX, rayY = playerY, step = 0.02, distance = 0;
            int hitType = 0;

            while (distance < (Math.max(MAZE_WIDTH, MAZE_HEIGHT))) {
                rayX += Math.cos(rayAngle) * step;
                rayY += Math.sin(rayAngle) * step;
                distance += step;
                if (rayX < 0 || rayY < 0 || rayX >= map[0].length || rayY >= map.length) break;

                hitType = map[(int) rayY][(int) rayX];
                if (hitType == 1 || hitType == 2) break;
            }

            double correctedDistance = distance * Math.cos(rayAngle - cameraAngle);
            correctedDistance = Math.max(correctedDistance, 0.0001);
            int wallHeight = (int) (SCREEN_HEIGHT  / correctedDistance);

            int start = horizon - wallHeight / (hitType == 2 ? 32 : 2);
            int end = horizon + wallHeight / 2;

            if (start < 0) start = 0;
            if (end >= SCREEN_HEIGHT) end = SCREEN_HEIGHT - 1;

            int wallColor = hitType == 2 ? 0x33FF66 : 0xECD485;
            double brightness = 1.0 - (correctedDistance / flashlightDistance);

            brightness = Math.clamp(brightness, 0.03, 1.0);
            wallColor = applyBrightness(wallColor, brightness);
            for (int y = start; y < end; y++) pixels[x + y * SCREEN_WIDTH] = wallColor;
        }

        drawTimer();
        if (isDebugMode && !isPaused) drawMiniMap();
        if (isPaused) drawPauseMenu();
    }
    private void drawMiniMap() {
        int scale = 7;
        for (int y = 0; y < map.length; y++) {
            for (int x = 0; x < map[0].length; x++) {
                int color = 0x000000;
                if (map[y][x] == 1) color = 0xFFFFFF;
                if (map[y][x] == 2) color = 0x00FF00;
                for (int yy = 0; yy < scale; yy++) {
                    for (int xx = 0; xx < scale; xx++) {
                        int px = x * scale + xx;
                        int py = y * scale + yy;
                        if (px >= 0 && py >= 0 && px < SCREEN_WIDTH && py < SCREEN_HEIGHT)
                            pixels[px + py * SCREEN_WIDTH] = color;
                    }
                }
            }
        }

        int playerPixelX = (int)(playerX * scale);
        int playerPixelY = (int)(playerY * scale);
        for (int yy = -2; yy <= 2; yy++) {
            for (int xx = -2; xx <= 2; xx++) {
                int px = playerPixelX + xx;
                int py = playerPixelY + yy;
                if (px >= 0 && py >= 0 && px < SCREEN_WIDTH && py < SCREEN_HEIGHT)
                    pixels[px + py * SCREEN_WIDTH] = 0xFF0000;
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
        g.drawString(timerText, SCREEN_WIDTH - 156, 34);

        g.setColor(Color.WHITE);
        g.drawString(timerText, SCREEN_WIDTH - 158, 32);
        g.dispose();
    }

    private void drawPauseMenu() {
        Graphics2D g = bufferedImage.createGraphics();
        g.setColor(new Color(0,0,0,180));
        g.fillRect(0,0, SCREEN_WIDTH, SCREEN_HEIGHT);

        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 42));
        g.drawString("PAUSED", SCREEN_WIDTH / 2 - 100, SCREEN_HEIGHT / 2 - 20);

        g.setFont(new Font("Arial", Font.PLAIN, 20));
        g.drawString("ESC - Continue", SCREEN_WIDTH / 2 - 90, SCREEN_HEIGHT / 2 + 20);
        g.drawString("R - Restart", SCREEN_WIDTH / 2 - 90, SCREEN_HEIGHT / 2 + 50);
        g.drawString("SHIFT - Run", SCREEN_WIDTH / 2 - 90, SCREEN_HEIGHT / 2 + 80);
        g.drawString("Movement Speed = " + ((shift) ? runSpeed : moveSpeed), SCREEN_WIDTH / 2 - 90, SCREEN_HEIGHT / 2 + 110);
        g.drawString("Mouse Sensitivity = " + mouseSensitivity, SCREEN_WIDTH / 2 - 90, SCREEN_HEIGHT / 2 + 140);
        g.drawString("NoColip = " + noClip, SCREEN_WIDTH / 2 - 90, SCREEN_HEIGHT / 2 + 170);

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
    @Override public void mouseMoved(MouseEvent e) {

        if (isPaused) return;
        if (isRecentering) {
            isRecentering = false;
            return;
        }

        Point panelLocation = getLocationOnScreen();
        int centerX = panelLocation.x + SCREEN_WIDTH / 2;
        int centerY = panelLocation.y + SCREEN_HEIGHT / 2;
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
        g.drawImage(bufferedImage, 0, 0, null);
    }
    // endregion
}
