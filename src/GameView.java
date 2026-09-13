import Helpers.*;

import javax.swing.*; //Frame Library
import java.awt.*; //Graphics Library
import java.awt.event.*; //Input Library
import java.awt.image.*; //Buffer Library
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.IntConsumer;

public class GameView extends JPanel implements Runnable, KeyListener, MouseMotionListener {
    //region Variables
    public static int MINI_MAP_WIDTH = 250;
    public static int MINI_MAP_HEIGHT = 250;
    public static int MAZE_WIDTH, MAZE_HEIGHT;
    public static MazeGenerator.FinishMode MAZE_MODE = MazeGenerator.FinishMode.RANDOM_EDGE;
    public static MazeGenerator.GeometryMode GEOMETRY_MODE = MazeGenerator.GeometryMode.EUCLIDEAN;
    public static double HYPERBOLIC_CURVATURE = HyperbolicMath.DEFAULT_CURVATURE;
    public static boolean MAZE_3D = false;
    public static int MAZE_FLOORS = 1;

    //region Dependencies
    private MazeGenerator mazeGenerator;
    private MazeGenerator3D mazeGenerator3D;
    private int[][][] map3D;
    private int currentFloor = 0;
    private boolean onStairsLastFrame = false;
    private final BufferedImage bufferedImage;
    private final Cursor invisibleCursor;
    private Robot cursorRobot; //BOBR KURSOR JA PERDOLE
    private Thread gameThread;
    //endregion

    private final int[] pixels; //BRUH JUST PIXELS IN IMAGE
    private int[][] map;

    private final Long mazeSeed;

    private final int[][] wallTexture = TextureEditorView.readTexture(AppPaths.WALL_TEXTURE_FILE);
    private final int[][] floorTexture = TextureEditorView.readTexture(AppPaths.FLOOR_TEXTURE_FILE);
    private final int[][] ceilingTexture = TextureEditorView.readTexture(AppPaths.CEILING_TEXTURE_FILE);
    private final int[][] finishTexture = TextureEditorView.readFinishTexture(AppPaths.FINISH_TEXTURE_FILE);

    private final int wallBaseColor = 0xECD485;
    private final int floorBaseColor = 0xD3AF63;
    private final int ceilingBaseColor = 0x816E1E;

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

    //region Multi - Thread render
    private final int renderThreadCount = Math.max(2, Runtime.getRuntime().availableProcessors());
    private final ExecutorService renderExecutor = Executors.newFixedThreadPool(renderThreadCount, r -> {
        Thread t = new Thread(r, "GameViewRenderWorker");
        t.setDaemon(true);
        return t;
    });
    //endregion

    //region Other Stuff
    private boolean isGameRunning = false;
    private boolean isRecentering = false; //Mouse Recursion Helper
    private boolean isPaused = false;
    private boolean isDebugMode = false;
    private final boolean isCustomMap;
    private long gameStartTime = System.currentTimeMillis();
    private long pauseStartTime = 0;
    private long pausedTime = 0;
    private  long elapsedSeconds;
    //endregion

    //region FPS Counter
    private int frameCounter = 0, currentFps = 0;
    private long fpsWindowStart = System.currentTimeMillis();
    //endregion
    //endregion

    // region Constructors
    public GameView(int mazeWidth, int mazeHeight, int mazeMode, int geometryMode) throws IOException {
        this(mazeWidth, mazeHeight, mazeMode, geometryMode, null, 1); }
    public GameView(int mazeWidth, int mazeHeight, int mazeMode, int geometryMode, Long seed) throws IOException {
        this(mazeWidth, mazeHeight, mazeMode, geometryMode, seed, 1); }
    public GameView(int mazeWidth, int mazeHeight, int mazeMode, int geometryMode, int floors) throws IOException {
        this(mazeWidth, mazeHeight, mazeMode, geometryMode, null, floors); }
    public GameView(int mazeWidth, int mazeHeight, int mazeMode, int geometryMode, Long seed, int floors) throws IOException {
        setPreferredSize(new Dimension(RCJMS.SCREEN_WIDTH, RCJMS.SCREEN_HEIGHT));
        setFocusable(true);
        requestFocus();

        bufferedImage = new BufferedImage(RCJMS.SCREEN_WIDTH, RCJMS.SCREEN_HEIGHT, BufferedImage.TYPE_INT_RGB);
        pixels = ((DataBufferInt) bufferedImage.getRaster().getDataBuffer()).getData();

        mazeSeed = seed;
        isCustomMap = false;
        GEOMETRY_MODE = MazeGenerator.GeometryMode.values()[Math.clamp(geometryMode, 0, MazeGenerator.GeometryMode.values().length - 1)];
        MAZE_MODE = MazeGenerator.FinishMode.values()[mazeMode];

        MAZE_3D = floors > 1;
        MAZE_FLOORS = MAZE_3D ? Math.max(2, floors) : 1;

        if (MAZE_3D) {
            mazeGenerator3D = mazeSeed != null ? new MazeGenerator3D(mazeWidth, mazeHeight, MAZE_FLOORS, mazeSeed, GEOMETRY_MODE)
                    : new MazeGenerator3D(mazeWidth, mazeHeight, MAZE_FLOORS, GEOMETRY_MODE);

            map3D = mazeGenerator3D.generate(MAZE_MODE);
            MAZE_HEIGHT = map3D[0].length;
            MAZE_WIDTH = map3D[0][0].length;
            currentFloor = 0;
            map = map3D[currentFloor];
        }
        else {
            mazeGenerator = mazeSeed != null ? new MazeGenerator(MAZE_WIDTH = mazeWidth, MAZE_HEIGHT = mazeHeight, mazeSeed, GEOMETRY_MODE)
                    : new MazeGenerator(MAZE_WIDTH = mazeWidth, MAZE_HEIGHT = mazeHeight, GEOMETRY_MODE);
            map = mazeGenerator.generate(MAZE_MODE);
        }

        addKeyListener(this);
        addMouseMotionListener(this);

        try { cursorRobot = new Robot(); }
        catch (Exception e) { e.printStackTrace(); }

        Toolkit toolkit = Toolkit.getDefaultToolkit();
        invisibleCursor = toolkit.createCustomCursor(toolkit.createImage(new byte[0]), new Point(0, 0), "hidden");
        hideCursor();
    }
    public GameView(int[][] customMap) throws IOException {
        setPreferredSize(new Dimension(RCJMS.SCREEN_WIDTH, RCJMS.SCREEN_HEIGHT));
        setFocusable(true);
        requestFocus();

        bufferedImage = new BufferedImage(RCJMS.SCREEN_WIDTH, RCJMS.SCREEN_HEIGHT, BufferedImage.TYPE_INT_RGB);
        pixels = ((DataBufferInt) bufferedImage.getRaster().getDataBuffer()).getData();
        mazeSeed = null;
        isCustomMap = true;
        MAZE_3D = false;
        MAZE_FLOORS = 1;
        map = customMap;
        MAZE_HEIGHT = map.length;
        MAZE_WIDTH = map[0].length;

        addKeyListener(this);
        addMouseMotionListener(this);

        try {cursorRobot = new Robot();}
        catch (Exception e) {e.printStackTrace();}

        Toolkit toolkit = Toolkit.getDefaultToolkit();
        invisibleCursor = toolkit.createCustomCursor(toolkit.createImage(new byte[0]), new Point(0, 0), "hidden");
        hideCursor();
    }
    //endregion

    //region Helpers
    private void hideCursor() { setCursor(invisibleCursor); }
    private void showCursor() { setCursor(Cursor.getDefaultCursor()); }
    @Override public void addNotify() {
        super.addNotify();
        SwingUtilities.invokeLater(this::requestFocusInWindow);
    }
    private void parallelRange(int start, int end, IntConsumer task) {
        int range = end - start;
        if (range <= 0) return;
        int threads = Math.min(renderThreadCount, range);
        int chunkSize = (int) Math.ceil(range / (double) threads);

        List<Future<?>> futures = new ArrayList<>(threads);
        for (int t = 0; t < threads; t++) {
            int from = start + t * chunkSize;
            int to = Math.min(end, from + chunkSize);
            if (from >= to) continue;

            futures.add(renderExecutor.submit(() -> { for (int i = from; i < to; i++) task.accept(i); }));
        }
        for (Future<?> future : futures) {
            try { future.get(); }
            catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            catch (Exception e) { throw new RuntimeException(e); }
        }
    }
    private int getTextureWidth(int[][] texture) {
        if (texture == null || texture.length == 0) return 0;
        return texture[0] == null ? 0 : texture[0].length;
    }
    private int getTextureHeight(int[][] texture) {
        if (texture == null || texture.length == 0) return 0;
        if (getTextureWidth(texture) == 0) return 0;
        for (int[] ints : texture) if (ints == null || ints.length != getTextureWidth(texture)) return 0;
        return texture.length;
    }
    //endregion

    //region Life Status Operations
    public void start() {
        if (isGameRunning) return;
        isGameRunning = true;
        gameThread = new Thread(this);
        gameThread.start();
    }
    @Override public void run() { //OMG IT'S DA GAME CYCLE!!!
        try {
            long lastFrameTime = System.nanoTime();
            while (isGameRunning) {
                long currentFrameTime = System.nanoTime();
                double deltaTime = (currentFrameTime - lastFrameTime) / 1_000_000_000.0;
                lastFrameTime = currentFrameTime;
                if (!isPaused) update(deltaTime);

                render(); repaint();
                trackFps();

                try {Thread.sleep(5); //FPS limit, you can experiment with it, but lower sleeptime means more artifacts
                } catch (Exception ignored) {}
            }
        } finally {
            renderExecutor.shutdownNow(); //stop worker threads once the game loop ends
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

        nextX = Math.clamp(nextX, 0.0001, MAZE_WIDTH - 0.0001);
        nextY = Math.clamp(nextY, 0.0001, MAZE_HEIGHT - 0.0001);

        if (noClip) {
            playerX = nextX;
            playerY = nextY;
        }
        else {
            int nextMapX = (int)Math.floor(nextX);
            int currentMapY = (int)Math.floor(playerY);

            if (nextMapX >= 0 && nextMapX < MAZE_WIDTH && currentMapY >= 0 && currentMapY < MAZE_HEIGHT && map[currentMapY][nextMapX] != 1) playerX = nextX;
            else {
                if (playerX < nextX) playerX = Math.min(Math.ceil(playerX) - 0.0001, MAZE_WIDTH - 0.0001);
                else if (playerX > nextX) playerX = Math.max(Math.floor(playerX) + 0.0001, 0.0001);
            }

            int currentMapX = (int)Math.floor(playerX);
            int nextMapY = (int)Math.floor(nextY);

            if (currentMapX >= 0 && currentMapX < MAZE_WIDTH && nextMapY >= 0 && nextMapY < MAZE_HEIGHT && map[nextMapY][currentMapX] != 1) playerY = nextY;
            else {
                if (playerY < nextY) playerY = Math.min(Math.ceil(playerY) - 0.0001, MAZE_HEIGHT - 0.0001);
                else if (playerY > nextY) playerY = Math.max(Math.floor(playerY) + 0.0001, 0.0001);
            }
        }

        playerX = Math.clamp(playerX, 0.0001, MAZE_WIDTH - 0.0001);
        playerY = Math.clamp(playerY, 0.0001, MAZE_HEIGHT - 0.0001);

        int cellY = Math.clamp((int) playerY, 0, MAZE_HEIGHT - 1);
        int cellX = Math.clamp((int) playerX, 0, MAZE_WIDTH - 1);
        int cellType = map[cellY][cellX];

        if (cellType == 2 && isGameRunning) {
            isGameRunning = false;
            playerX = playerY = 1.5;
            cameraAngle = cameraPitch = 0;
            RCJMS.instance.ChangeView(RCJMS.instance.victoryView = new VictoryView(elapsedSeconds), "vv.victory");
            gameThread.interrupt();
            return;
        }

        if (MAZE_3D) handleStairs(cellType);
    }
    private void handleStairs(int cellType) {
        boolean onStairsNow = cellType == 3 || cellType == 4;

        if (onStairsNow && !onStairsLastFrame) {
            if (cellType == 3 && currentFloor < map3D.length - 1) currentFloor++;
            else if (cellType == 4 && currentFloor > 0) currentFloor--;
            map = map3D[currentFloor];
        }
        onStairsLastFrame = onStairsNow;
    }
    //endregion/

    //region Rendering
    private void render() {
        boolean wrongGeometry = GEOMETRY_MODE == MazeGenerator.GeometryMode.WRONG;
        double curvature = HYPERBOLIC_CURVATURE;

        double dirX = Math.cos(cameraAngle);
        double dirY = Math.sin(cameraAngle);

        double planeLength = Math.tan((wrongGeometry ? Math.toRadians(140) : Math.PI / 3.0) / 2.0);
        double planeX = -dirY * planeLength;
        double planeY =  dirX * planeLength;

        double horizon = RCJMS.SCREEN_HEIGHT / 2.0 + cameraPitch * RCJMS.SCREEN_HEIGHT / 2.0;
        renderHorizontalSurfaces(dirX, dirY, planeX, planeY, horizon);

        int textureHeight = getTextureHeight(wallTexture);
        int textureWidth = getTextureWidth(wallTexture);
        int finishTextureHeight = getTextureHeight(finishTexture);
        int finishTextureWidth = getTextureWidth(finishTexture);

        parallelRange(0, RCJMS.SCREEN_WIDTH, x -> {
            double cameraX = 2.0 * x / (double) RCJMS.SCREEN_WIDTH - 1.0;

            double rayDirX = dirX + planeX * cameraX;
            double rayDirY = dirY + planeY * cameraX;

            int mapX = (int) playerX;
            int mapY = (int) playerY;

            double deltaDistX = rayDirX == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / rayDirX);
            double deltaDistY = rayDirY == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / rayDirY);

            double sideDistX = (rayDirX < 0 ? (playerX - mapX) : (mapX + 1.0 - playerX)) * deltaDistX;
            double sideDistY = (rayDirY < 0 ? (playerY - mapY) : (mapY + 1.0 - playerY)) * deltaDistY;

            boolean hit = false;
            int side = 0, hitType = 0;

            while (!hit) {
                if (sideDistX < sideDistY) {
                    sideDistX += deltaDistX;
                    mapX += rayDirX < 0 ? -1 : 1;
                    side = 0;
                } else {
                    sideDistY += deltaDistY;
                    mapY += rayDirY < 0 ? -1 : 1;
                    side = 1;
                }

                if (mapX < 0 || mapY < 0 || mapX >= map[0].length || mapY >= map.length) break;
                hitType = map[mapY][mapX];
                if (hitType == 1 || hitType == 2) hit = true;
            }

            if (!hit) return;

            double perpendicularDistance = side == 0 ? sideDistX - deltaDistX : sideDistY - deltaDistY;
            perpendicularDistance = Math.max(perpendicularDistance, 0.0001);

            double projectedDistance = wrongGeometry ? HyperbolicMath.hyperbolicDistance(perpendicularDistance, curvature) : perpendicularDistance;

            int wallHeight = (int) (RCJMS.SCREEN_HEIGHT / projectedDistance);
            int drawStart = Math.max(hitType == 2 ? (int) horizon : (int) (horizon - wallHeight / 2.0), 0);
            int drawEnd = Math.min((int) (horizon + wallHeight / 2.0), RCJMS.SCREEN_HEIGHT - 1);

            if (drawStart > drawEnd) return;
            double wallX;
            wallX = side == 0 ?  playerY + perpendicularDistance * rayDirY : playerX + perpendicularDistance * rayDirX;
            wallX -= Math.floor(wallX);

            if (side == 0 && rayDirX > 0) wallX = 1.0 - wallX;
            if (side == 1 && rayDirY < 0) wallX = 1.0 - wallX;

            double brightness = 1.0 - (projectedDistance / effectiveFlashlightDistance(wrongGeometry));
            brightness = Math.clamp(brightness, 0.03, 1.0);

            if (side == 1) brightness *= 0.85;

            int offset = drawStart * RCJMS.SCREEN_WIDTH;

            for (int y = drawStart; y <= drawEnd; y++) {
                double wallPosition;
                if (hitType == 2) wallPosition = (y - horizon) / Math.max(wallHeight / 2.0, 1.0);
                else wallPosition = (y - (horizon - wallHeight / 2.0)) / Math.max(wallHeight, 1.0);
                wallPosition = Math.clamp(wallPosition, 0.0, 0.999999);

                int color;
                if (hitType == 2) {
                    if (finishTextureWidth > 0 && finishTextureHeight > 0)
                        color = finishTexture[Math.clamp((int) (wallPosition * finishTextureHeight), 0, finishTextureHeight - 1)]
                                [Math.clamp((int) (wallX * finishTextureWidth), 0, finishTextureWidth - 1)];
                    else color = 0x33FF66;
                } else if (textureWidth > 0 && textureHeight > 0) {
                    color = wallTexture[Math.clamp((int) (wallPosition * textureHeight), 0, textureHeight - 1)][Math.clamp((int) (wallX * textureWidth), 0, textureWidth - 1)];
                } else color = wallBaseColor;

                pixels[offset + x] = applyBrightness(color, brightness);
                offset += RCJMS.SCREEN_WIDTH;
            }
        });

        drawTimer();
        int hudLine = 0;
        if (wrongGeometry) drawGeometryBadge(hudLine++);
        if (MAZE_3D) drawFloorIndicator(hudLine);

        if (isDebugMode && !isPaused) { drawMiniMap(); drawFpsCounter(); }
        if (isPaused) drawPauseMenu();
    }
    private void renderHorizontalSurfaces(double dirX, double dirY, double planeX, double planeY, double horizon) {
        final double minDistance = 0.0001;
        final boolean hyperbolic = GEOMETRY_MODE == MazeGenerator.GeometryMode.WRONG;

        double leftRayX = dirX - planeX, leftRayY = dirY - planeY;
        double rightRayX = dirX + planeX,  rightRayY = dirY + planeY;

        parallelRange(Math.max(0, (int) Math.ceil(horizon)), RCJMS.SCREEN_HEIGHT, y -> {
            if ((y - horizon) < minDistance) return;

            double rowDistance = RCJMS.SCREEN_HEIGHT / (2.0 * (y - horizon));
            double brightnessDistance = hyperbolic ? HyperbolicMath.hyperbolicDistance(rowDistance, HYPERBOLIC_CURVATURE) : rowDistance;

            double floorX = playerX + rowDistance * leftRayX;
            double floorY = playerY + rowDistance * leftRayY;

            for (int x = 0; x < RCJMS.SCREEN_WIDTH; x++) {
                double brightness = 1.0 - (brightnessDistance / effectiveFlashlightDistance(hyperbolic));
                brightness = Math.clamp(brightness, 0.05, 1.0);

                int color = sampleTexture(floorTexture, floorX, floorY, floorBaseColor);
                if (MAZE_3D) color = tintStairs(color, floorX, floorY);
                pixels[y * RCJMS.SCREEN_WIDTH + x] = applyBrightness(color, brightness);

                floorX += rowDistance * (rightRayX - leftRayX) / RCJMS.SCREEN_WIDTH;
                floorY += rowDistance * (rightRayY - leftRayY) / RCJMS.SCREEN_WIDTH;
            }
        });

        parallelRange(0, Math.min(RCJMS.SCREEN_HEIGHT - 1, (int) Math.floor(horizon) - 1) + 1, y -> {
            if (horizon - y < minDistance) return;
            double rowDistance =  RCJMS.SCREEN_HEIGHT / (2.0 * (horizon - y));
            double brightnessDistance = hyperbolic ? HyperbolicMath.hyperbolicDistance(rowDistance, HYPERBOLIC_CURVATURE) : rowDistance;

            double ceilingX = playerX + rowDistance * leftRayX;
            double ceilingY = playerY + rowDistance * leftRayY;

            for (int x = 0; x < RCJMS.SCREEN_WIDTH; x++) {
                int color = sampleTexture(ceilingTexture, ceilingX, ceilingY, ceilingBaseColor);
                double brightness = 1.0 - (brightnessDistance / effectiveFlashlightDistance(hyperbolic));
                brightness = Math.clamp(brightness, 0.05, 1.0);
                pixels[y * RCJMS.SCREEN_WIDTH + x] = applyBrightness(color, brightness);

                ceilingX += rowDistance * (rightRayX - leftRayX) / RCJMS.SCREEN_WIDTH;
                ceilingY += rowDistance * (rightRayY - leftRayY) / RCJMS.SCREEN_WIDTH;
            }
        });
    }
    private double effectiveFlashlightDistance(boolean hyperbolic) { return hyperbolic ? flashlightDistance * 1.6 : flashlightDistance; }
    private int tintStairs(int color, double worldX, double worldY) {
        int fx = (int) Math.floor(worldX), fy = (int) Math.floor(worldY);
        if (fx < 0 || fy < 0 || fx >= MAZE_WIDTH || fy >= MAZE_HEIGHT) return color;

        int cell = map[fy][fx];
        if (cell == 3) return tintColor(color, 0x3399FF, 0.45);
        if (cell == 4) return tintColor(color, 0xFFAA33, 0.45);
        return color;
    }
    private int tintColor(int color, int tint, double amount) {
        int r = (color >> 16) & 255, g = (color >> 8) & 255, b = color & 255;
        int tr = (tint >> 16) & 255, tg = (tint >> 8) & 255, tb = tint & 255;

        r = (int) (r * (1 - amount) + tr * amount);
        g = (int) (g * (1 - amount) + tg * amount);
        b = (int) (b * (1 - amount) + tb * amount);

        return (r << 16) | (g << 8) | b;
    }
    private int applyBrightness(int color, double brightness) {
        int r = (color >> 16) & 255, g = (color >> 8) & 255, b = color & 255;

        r = (int)(r * brightness);
        g = (int)(g * brightness);
        b = (int)(b * brightness);

        return (r << 16) | (g << 8) | b;
    }
    private int sampleTexture(int[][] texture, double worldX, double worldY, int baseColor) {
        int textureHeight = getTextureHeight(texture), textureWidth = getTextureWidth(texture);
        if (textureWidth <= 0 || textureHeight <= 0) return baseColor;

        return texture[Math.clamp((int)((worldY - Math.floor(worldY)) * textureHeight), 0, textureHeight - 1)]
                [Math.clamp((int)((worldX - Math.floor(worldX)) * textureWidth), 0, textureWidth - 1)];
    }
    //endregion

    //region HUD
    private void drawMiniMap() {
        if (GEOMETRY_MODE == MazeGenerator.GeometryMode.WRONG) { drawCoolMiniMap(); return; }
        int mapWidth = map[0].length, mapHeight = map.length;
        double scale = Math.min((double) MINI_MAP_WIDTH / mapWidth, (double) MINI_MAP_HEIGHT / mapHeight);

        int offsetX = 10 + (MINI_MAP_WIDTH - (int) (mapWidth * scale)) / 2;
        int offsetY = 10 + (MINI_MAP_HEIGHT - (int) (mapHeight * scale)) / 2;

        for (int y = 0; y < mapHeight; y++) {
            for (int x = 0; x < mapWidth; x++) {
                int color = 0x000000;
                if (map[y][x] == 1) color = 0xFFFFFF;
                if (map[y][x] == 2) color = 0x00FF00;
                if (map[y][x] == 3) color = 0x3399FF;
                if (map[y][x] == 4) color = 0xFFAA33;

                int startX = offsetX + (int) (x * scale);
                int startY = offsetY + (int) (y * scale);
                int endX = offsetX + (int) ((x + 1) * scale);
                int endY = offsetY + (int) ((y + 1) * scale);

                for (int py = startY; py < endY; py++) for (int px = startX; px < endX; px++)
                    if (px >= 0 && py >= 0 && px < RCJMS.SCREEN_WIDTH && py < RCJMS.SCREEN_HEIGHT) pixels[px + py * RCJMS.SCREEN_WIDTH] = color;
            }
        }

        int playerSize = Math.max(1, (int)Math.floor(scale * 0.25));
        for (int yy = -playerSize; yy <= playerSize; yy++) {
            for (int xx = -playerSize; xx <= playerSize; xx++) {
                int px = offsetX + (int)(playerX * scale) + xx;
                int py = offsetY + (int)(playerY * scale) + yy;
                if (px >= 0 && py >= 0 && px < RCJMS.SCREEN_WIDTH && py < RCJMS.SCREEN_HEIGHT) pixels[px + py * RCJMS.SCREEN_WIDTH] = 0xFF0000;
            }
        }
    }
    private void drawCoolMiniMap() {
        int diskRadiusPx = Math.min(MINI_MAP_WIDTH, MINI_MAP_HEIGHT) / 2;
        int centerX = 10 + diskRadiusPx, centerY = 10 + diskRadiusPx;

        for (int yy = -diskRadiusPx; yy <= diskRadiusPx; yy++) {
            for (int xx = -diskRadiusPx; xx <= diskRadiusPx; xx++) {
                if (xx * xx + yy * yy > diskRadiusPx * diskRadiusPx) continue;
                int px = 10 + diskRadiusPx + xx, py = centerY + yy;
                if (px >= 0 && py >= 0 && px < RCJMS.SCREEN_WIDTH && py < RCJMS.SCREEN_HEIGHT) pixels[px + py * RCJMS.SCREEN_WIDTH] = 0x0A0A14;
            }
        }

        for (int y = 0; y < map[0].length; y++) {
            for (int x = 0; x < map.length; x++) {
                if (map[y][x] == 0) continue;

                double diskR = HyperbolicMath.poincareRadius(Math.hypot((x + 0.5) - playerX, (y + 0.5) - playerY), HYPERBOLIC_CURVATURE);
                if (diskR >= 0.995) continue;

                double angle = Math.atan2((y + 0.5) - playerY, (x + 0.5) - playerX);
                int color = map[y][x] == 2 ? 0x00FF00 : map[y][x] == 3 ? 0x3399FF : map[y][x] == 4 ? 0xFFAA33 : 0xFFFFFF;
                int cellPixelSize = Math.max(1, (int) Math.round((1.0 - diskR) * 4));

                for (int oy = -cellPixelSize; oy <= cellPixelSize; oy++) {
                    for (int ox = -cellPixelSize; ox <= cellPixelSize; ox++) {
                        if (ox * ox + oy * oy > cellPixelSize * cellPixelSize) continue;
                        int fx = centerX + (int)Math.round(Math.cos(angle) * diskR * diskRadiusPx) + ox;
                        int fy = centerY + (int)Math.round(Math.sin(angle) * diskR * diskRadiusPx) + oy;
                        if (fx < 0 || fy < 0 || fx >= RCJMS.SCREEN_WIDTH || fy >= RCJMS.SCREEN_HEIGHT) continue;
                        if ((fx - centerX) * (fx - centerX) + (fy - centerY) * (fy - centerY) > diskRadiusPx * diskRadiusPx) continue;
                        pixels[fx + fy * RCJMS.SCREEN_WIDTH] = color;
                    }
                }
            }
        }
        for (double a = 0; a < Math.PI * 2; a += 0.01) {
            int px = centerX + (int) Math.round(Math.cos(a) * diskRadiusPx);
            int py = centerY + (int) Math.round(Math.sin(a) * diskRadiusPx);
            if (px >= 0 && py >= 0 && px < RCJMS.SCREEN_WIDTH && py < RCJMS.SCREEN_HEIGHT) pixels[px + py * RCJMS.SCREEN_WIDTH] = 0x8888FF;
        }
        int tickLen = diskRadiusPx - 4;
        drawMiniMapLine(centerX, centerY, centerX + (int)Math.round(Math.cos(cameraAngle) * tickLen),
                centerY + (int)Math.round(Math.sin(cameraAngle) * tickLen), 0xFFAA00);
        int playerSize = 3;
        for (int yy = -playerSize; yy <= playerSize; yy++) {
            for (int xx = -playerSize; xx <= playerSize; xx++) {
                int px = centerX + xx, py = centerY + yy;
                if (px >= 0 && py >= 0 && px < RCJMS.SCREEN_WIDTH && py < RCJMS.SCREEN_HEIGHT) pixels[px + py * RCJMS.SCREEN_WIDTH] = 0xFF0000;
            }
        }
    }
    private void drawMiniMapLine(int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0), sx = x0 < x1 ? 1 : -1, dy = -Math.abs(y1 - y0), sy = y0 < y1 ? 1 : -1, err = dx + dy;
        while (true) {
            if (x0 >= 0 && y0 >= 0 && x0 < RCJMS.SCREEN_WIDTH && y0 < RCJMS.SCREEN_HEIGHT) pixels[x0 + y0 * RCJMS.SCREEN_WIDTH] = color;
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x0 += sx; }
            if (e2 <= dx) { err += dx; y0 += sy; }
        }
    }
    private void drawFpsCounter() {
        Graphics2D g2d = bufferedImage.createGraphics();
        String fpsText = "FPS: " + currentFps;

        g2d.setFont(new Font("Consolas", Font.BOLD, 20));
        g2d.setColor(Color.BLACK);
        g2d.drawString(fpsText, 40, RCJMS.SCREEN_HEIGHT - 30);

        g2d.setColor(Color.GREEN);
        g2d.drawString(fpsText, 38, RCJMS.SCREEN_HEIGHT - 28);
        g2d.dispose();
    }
    private void trackFps() {
        frameCounter++;
        if (System.currentTimeMillis() - fpsWindowStart >= 1000) {
            currentFps = frameCounter;
            frameCounter = 0;
            fpsWindowStart = System.currentTimeMillis();
        }
    }
    private void drawTimer() {
        Graphics2D g2d = bufferedImage.createGraphics();
        elapsedSeconds = ((isPaused ? pauseStartTime : System.currentTimeMillis()) - gameStartTime - pausedTime) / 1000;

        long hours = elapsedSeconds / 3600;
        long minutes = (elapsedSeconds % 3600) / 60;
        long seconds = elapsedSeconds % 60;

        String timerText = String.format("%02d:%02d:%02d", hours, minutes, seconds);

        g2d.setFont(new Font("Consolas", Font.BOLD, 24));
        g2d.setColor(Color.BLACK);
        g2d.drawString(timerText, RCJMS.SCREEN_WIDTH - 156, 34);

        g2d.setColor(Color.WHITE);
        g2d.drawString(timerText, RCJMS.SCREEN_WIDTH - 158, 32);
        g2d.dispose();
    }
    private void drawGeometryBadge(int line) {
        Graphics2D g2d = bufferedImage.createGraphics();
        String text = NSLocalizedString.get("gv.wrong_geometry");

        g2d.setFont(new Font("Consolas", Font.BOLD, 16));
        g2d.setColor(Color.BLACK);
        g2d.drawString(text, RCJMS.SCREEN_WIDTH - 202, RCJMS.SCREEN_HEIGHT - (26 + line * 24) + 2);
        g2d.setColor(new Color(150, 170, 255));
        g2d.drawString(text, RCJMS.SCREEN_WIDTH - 200, RCJMS.SCREEN_HEIGHT - (26 + line * 24));
        g2d.dispose();
    }
    private void drawFloorIndicator(int line) {
        Graphics2D g2d = bufferedImage.createGraphics();
        String text = NSLocalizedString.get("gv.floor") + (currentFloor + 1) + "/" + map3D.length;

        g2d.setFont(new Font("Consolas", Font.BOLD, 16));
        g2d.setColor(Color.BLACK);
        g2d.drawString(text, RCJMS.SCREEN_WIDTH - 202, RCJMS.SCREEN_HEIGHT - (26 + line * 24) + 2);
        g2d.setColor(new Color(255, 200, 120));
        g2d.drawString(text, RCJMS.SCREEN_WIDTH - 200, RCJMS.SCREEN_HEIGHT - (26 + line * 24));
        g2d.dispose();
    }
    private void drawPauseMenu() {
        Graphics2D g2d = bufferedImage.createGraphics();
        g2d.setColor(new Color(0,0,0,180));
        g2d.fillRect(0,0, RCJMS.SCREEN_WIDTH, RCJMS.SCREEN_HEIGHT);

        String pausedText = NSLocalizedString.get("gv.paused");

        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.BOLD, 80));
        int pausedWidth = g2d.getFontMetrics().stringWidth(pausedText);
        g2d.drawString(pausedText, (RCJMS.SCREEN_WIDTH - pausedWidth) / 2, RCJMS.SCREEN_HEIGHT / 2 - 50);

        g2d.setFont(new Font("Arial", Font.PLAIN, 20));
        g2d.drawString(NSLocalizedString.get("gv.continue"), RCJMS.SCREEN_WIDTH / 2 - 90, RCJMS.SCREEN_HEIGHT / 2 + 20);
        g2d.drawString(NSLocalizedString.get("gv.restart"), RCJMS.SCREEN_WIDTH / 2 - 90, RCJMS.SCREEN_HEIGHT / 2 + 50);
        g2d.drawString(NSLocalizedString.get("run"), RCJMS.SCREEN_WIDTH / 2 - 90, RCJMS.SCREEN_HEIGHT / 2 + 80);
        g2d.drawString(NSLocalizedString.get("gv.no_clip") + noClip, RCJMS.SCREEN_WIDTH / 2 - 90, RCJMS.SCREEN_HEIGHT / 2 + 110);
        g2d.drawString(NSLocalizedString.get("gv.geometry") + NSLocalizedString.get(
                GEOMETRY_MODE == MazeGenerator.GeometryMode.WRONG ? "gv.wrong_geometry" : "gv.euclidean_mode"), RCJMS.SCREEN_WIDTH / 2 - 90, RCJMS.SCREEN_HEIGHT / 2 + 140);
        if (MAZE_3D) g2d.drawString(NSLocalizedString.get("gv.floor") + (currentFloor + 1) + "/" + map3D.length,
                RCJMS.SCREEN_WIDTH / 2 - 90, RCJMS.SCREEN_HEIGHT / 2 + 170);

        g2d.dispose();
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
        if (key == KeyEvent.VK_G) GEOMETRY_MODE = GEOMETRY_MODE == MazeGenerator.GeometryMode.EUCLIDEAN
                ? MazeGenerator.GeometryMode.WRONG : MazeGenerator.GeometryMode.EUCLIDEAN;
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
            if (isPaused) {
                pausedTime += System.currentTimeMillis() - pauseStartTime;
                isPaused = false;
                hideCursor();
            }
            if (isCustomMap){
                try {
                    isGameRunning = false;
                    GameView gameView = new GameView(map);
                    RCJMS.instance.ChangeView(gameView, "Raycast Me!");
                    gameThread.interrupt();
                    gameView.start();
                } catch (IOException ex) {throw new RuntimeException(ex);}
                return;
            }
            else if (MAZE_3D) {
                mazeGenerator3D = mazeSeed != null ? new MazeGenerator3D(MAZE_WIDTH, MAZE_HEIGHT, MAZE_FLOORS, mazeSeed, GEOMETRY_MODE)
                        : new MazeGenerator3D(MAZE_WIDTH, MAZE_HEIGHT, MAZE_FLOORS, GEOMETRY_MODE);
                map3D = mazeGenerator3D.generate(MAZE_MODE);
                currentFloor = 0;
                map = map3D[currentFloor];
            }
            else {
                if (mazeSeed != null) mazeGenerator = new MazeGenerator(MAZE_WIDTH, MAZE_HEIGHT, mazeSeed, GEOMETRY_MODE);
                else mazeGenerator = new MazeGenerator(MAZE_WIDTH, MAZE_HEIGHT, GEOMETRY_MODE);
                map = mazeGenerator.generate(MAZE_MODE);
            }

            playerX = playerY = 1.5;
            cameraAngle = cameraPitch = 0;
            onStairsLastFrame = false;
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

        int centerX = panelLocation.x + getWidth() / 2;
        int centerY = panelLocation.y + getHeight() / 2;

        cameraAngle += (e.getXOnScreen() - centerX) * mouseSensitivity;
        cameraPitch -= (e.getYOnScreen() - centerY) * mouseSensitivity;
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

        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, panelWidth, panelHeight);
        g2d.drawImage(bufferedImage, drawX, drawY, drawWidth, drawHeight, null);
        g2d.dispose();
    }
    // endregion
}