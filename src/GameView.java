import Helpers.*;

import javax.swing.*; //Frame Library
import java.awt.*; //Graphics Library
import java.awt.event.*; //Input Library
import java.awt.image.*; //Buffer Library
import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.function.IntConsumer;

public class GameView extends JPanel implements Runnable, KeyListener, MouseMotionListener {
    //region Variables

    //region Maze Configuration
    public static int MAZE_WIDTH, MAZE_HEIGHT;
    public static MazeGenerator.FinishMode MAZE_MODE = MazeGenerator.FinishMode.RANDOM_EDGE;
    public static MazeGenerator.GeometryMode GEOMETRY_MODE = MazeGenerator.GeometryMode.EUCLIDEAN;
    public static double HYPERBOLIC_CURVATURE = HyperbolicMath.DEFAULT_CURVATURE;
    public static boolean MAZE_3D = false;
    public static int MAZE_FLOORS = 1;
    public static double STAIR_ANIMATION_SPEED = 1.0; // multiplier for the floor-transition animation; higher = faster
    //endregion

    //region Dependencies
    private MazeGenerator mazeGenerator;
    private MazeGenerator3D mazeGenerator3D;
    private int[][][] map3D;
    private int currentFloor = 0;
    private boolean onStairsLastFrame = false;
    private final Cursor invisibleCursor;
    private Robot cursorRobot; //BOBR KURSOR JA PERDOLE
    private Thread gameThread;
    private int[][] map;
    private PortalData portals = new PortalData();
    private final Long mazeSeed;
    private final boolean isCustomMap;
    //endregion

    //region Render Buffers
    public static int MINI_MAP_WIDTH = 180, MINI_MAP_HEIGHT = 180;
    private final BufferedImage bufferedImage;
    private final BufferedImage renderImage;
    private final int[] pixels, renderPixels; //PUXELS
    private final int renderWidth, renderHeight;
    //endregion

    //region Textures & Colors
    private final TextureEditorView.RuntimeTexture wallTexture = TextureEditorView.readRuntimeTexture(AppPaths.WALL_TEXTURE_FILE, TextureEditorView.MAX_TEXTURE_SIZE, TextureEditorView.MAX_TEXTURE_SIZE);
    private final TextureEditorView.RuntimeTexture floorTexture = TextureEditorView.readRuntimeTexture(AppPaths.FLOOR_TEXTURE_FILE, TextureEditorView.MAX_TEXTURE_SIZE, TextureEditorView.MAX_TEXTURE_SIZE);
    private final TextureEditorView.RuntimeTexture ceilingTexture = TextureEditorView.readRuntimeTexture(AppPaths.CEILING_TEXTURE_FILE, TextureEditorView.MAX_TEXTURE_SIZE, TextureEditorView.MAX_TEXTURE_SIZE);
    private final TextureEditorView.RuntimeTexture finishTexture = TextureEditorView.readRuntimeTexture(AppPaths.FINISH_TEXTURE_FILE, TextureEditorView.MAX_TEXTURE_SIZE, TextureEditorView.MAX_FINISH_HEIGHT);

    private final int wallBaseColor = 0xECD485;
    private final int floorBaseColor = 0xD3AF63;
    private final int ceilingBaseColor = 0x816E1E;
    //endregion

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
    private final int renderThreadCount = Math.clamp(Runtime.getRuntime().availableProcessors(), 1, 8);
    private final RenderWorkers renderWorkers = new RenderWorkers(renderThreadCount);
    //endregion

    //region Graphics Settings
    private static volatile double renderScale = 1.0;
    private static volatile boolean vsync = false;
    private static volatile boolean rayTracing = false;
    private static volatile int rayTracingQuality = 1;
    private static volatile double glossiness = .75;
    //endregion

    //region Game State
    private boolean isGameRunning = false;
    private boolean isPaused = false, isDebugMode = false;
    private long gameStartTime = System.currentTimeMillis();
    private long pauseStartTime = 0, pausedTime = 0, elapsedSeconds;
    //endregion

    //region Mouse / Input Compatibility
    private boolean isRecentering = false; //Mouse Recursion Helper
    private boolean hasLastMousePos = false; //Wayland fallback delta-based mouse-look
    private int lastMouseScreenX, lastMouseScreenY; //Wayland fallback cursor position
    private static final boolean MOUSE_WARP_SUPPORTED = detectMouseWarpSupport();
    private final double[] cameraXCache;
    //endregion

    //region FPS Counter
    private int frameCounter = 0, currentFps = 0;
    private long fpsWindowStart = System.currentTimeMillis();
    private final Font fpsFont = new Font(Font.MONOSPACED, Font.BOLD, 20);
    private final Font timerFont = new Font(Font.MONOSPACED, Font.BOLD, 24);
    //endregion

    //region Floor Transition Animation
    private static final double FADE_OUT_DURATION = 0.6;
    private static final double FADE_IN_DURATION = 0.6;
    private static final double CAPTION_ROLL_DURATION = 0.5;
    private static final double CAPTION_HOLD_DURATION = 0.9;
    private static final double CAPTION_FADE_DURATION = 0.4;

    private boolean isFloorTransitioning = false, floorSwitchApplied = false;
    private double floorTransitionTime = 0;
    private int transitionFromFloor = 0, transitionToFloor = 0;
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
        setPreferredSize(new Dimension(RCJMS.GAME_WIDTH, RCJMS.GAME_HEIGHT));
        setFocusable(true);
        requestFocus();

        bufferedImage = new BufferedImage(RCJMS.GAME_WIDTH, RCJMS.GAME_HEIGHT, BufferedImage.TYPE_INT_RGB);
        pixels = ((DataBufferInt) bufferedImage.getRaster().getDataBuffer()).getData();
        renderWidth = Math.max(1, (int) Math.round(RCJMS.GAME_WIDTH * renderScale));
        renderHeight = Math.max(1, (int) Math.round(RCJMS.GAME_HEIGHT * renderScale));
        renderImage = new BufferedImage(renderWidth, renderHeight, BufferedImage.TYPE_INT_RGB);
        renderPixels = ((DataBufferInt) renderImage.getRaster().getDataBuffer()).getData();
        cameraXCache = buildCameraXCache();

        mazeSeed = seed;
        isCustomMap = false;
        GEOMETRY_MODE = MazeGenerator.GeometryMode.values()[Math.clamp(geometryMode, 0, MazeGenerator.GeometryMode.values().length - 1)];
        MAZE_MODE = MazeGenerator.FinishMode.values()[mazeMode];

        MAZE_3D = floors > 1;
        MAZE_FLOORS = MAZE_3D ? floors : 1;

        if (MAZE_3D) {
            mazeGenerator3D = mazeSeed != null ? new MazeGenerator3D(mazeWidth, mazeHeight, MAZE_FLOORS, mazeSeed, GEOMETRY_MODE)
                    : new MazeGenerator3D(mazeWidth, mazeHeight, MAZE_FLOORS, GEOMETRY_MODE);

            map3D = mazeGenerator3D.generate(MAZE_MODE);
            MAZE_HEIGHT = map3D[0].length;
            MAZE_WIDTH = map3D[0][0].length;
            currentFloor = 0;
            map = map3D[currentFloor];
            portals = mazeGenerator3D.getPortals(currentFloor);
        }
        else {
            mazeGenerator = mazeSeed != null ? new MazeGenerator(MAZE_WIDTH = mazeWidth, MAZE_HEIGHT = mazeHeight, mazeSeed, GEOMETRY_MODE)
                    : new MazeGenerator(MAZE_WIDTH = mazeWidth, MAZE_HEIGHT = mazeHeight, GEOMETRY_MODE);
            map = mazeGenerator.generate(MAZE_MODE);
            portals = mazeGenerator.getPortals();
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
        setPreferredSize(new Dimension(RCJMS.GAME_WIDTH, RCJMS.GAME_HEIGHT));
        setFocusable(true);
        requestFocus();

        bufferedImage = new BufferedImage(RCJMS.GAME_WIDTH, RCJMS.GAME_HEIGHT, BufferedImage.TYPE_INT_RGB);
        pixels = ((DataBufferInt) bufferedImage.getRaster().getDataBuffer()).getData();
        renderWidth = Math.max(1, (int) Math.round(RCJMS.GAME_WIDTH * renderScale));
        renderHeight = Math.max(1, (int) Math.round(RCJMS.GAME_HEIGHT * renderScale));
        renderImage = new BufferedImage(renderWidth, renderHeight, BufferedImage.TYPE_INT_RGB);
        renderPixels = ((DataBufferInt) renderImage.getRaster().getDataBuffer()).getData();
        cameraXCache = buildCameraXCache();
        mazeSeed = null;
        isCustomMap = true;
        MAZE_3D = false;
        GEOMETRY_MODE = MazeGenerator.GeometryMode.EUCLIDEAN;
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

    //region Life Status Operations
    public void start() {
        if (isGameRunning) return;
        isGameRunning = true;
        gameThread = new Thread(this);
        gameThread.start();
    }
    @Override public void run() { //OMG IT'S DA GAME CYCLE!!!
        try {
            long lastFrameTime = System.nanoTime(), nextVSyncTime = lastFrameTime;
            final long vsyncInterval = getDisplayRefreshIntervalNanos();

            while (isGameRunning) {
                long currentFrameTime = System.nanoTime();
                double deltaTime = (currentFrameTime - lastFrameTime) / 1_000_000_000.0;
                lastFrameTime = currentFrameTime;
                deltaTime = Math.min(deltaTime, 0.1);
                if (!isPaused) update(deltaTime);

                render();
                if (isVSyncEnabled()) {
                    paintFrameSynchronously();
                    Toolkit.getDefaultToolkit().sync();
                    nextVSyncTime += vsyncInterval;
                    waitUntil(nextVSyncTime);
                    long now = System.nanoTime();
                    if (now > nextVSyncTime + vsyncInterval * 2) nextVSyncTime = now;
                }
                else {
                    repaint();
                    try { Thread.sleep(1); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                    nextVSyncTime = System.nanoTime();
                }
                trackFps();
            }
        }
        finally { renderWorkers.shutdown(); }
    }
    private long getDisplayRefreshIntervalNanos() {
        int refreshRate = 60;
        try {
            GraphicsConfiguration gc = getGraphicsConfiguration();
            if (gc != null && gc.getDevice() != null) {
                int rate = gc.getDevice().getDisplayMode().getRefreshRate();
                if (rate > 1 && rate <= 1000) refreshRate = rate;
            }
        } catch (Exception ignored) { }
        return 1_000_000_000L / refreshRate;
    }
    private void paintFrameSynchronously() {
        if (!SwingUtilities.isEventDispatchThread()) {
            try { SwingUtilities.invokeAndWait(() -> paintImmediately(0, 0, getWidth(), getHeight())); }
            catch (Exception ignored) { }
        }
        else paintImmediately(0, 0, getWidth(), getHeight());
    }
    private void waitUntil(long targetNanos) {
        while (true) {
            long remaining = targetNanos - System.nanoTime();
            if (remaining <= 0) return;
            if (remaining > 2_000_000L) {
                try { Thread.sleep(Math.max(1, (remaining - 1_000_000L) / 1_000_000L)); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
            }
            else Thread.onSpinWait();
        }
    }
    private void update(double deltaTiime) {
        if (isFloorTransitioning) {
            double speed = Math.max(0.05, STAIR_ANIMATION_SPEED);
            floorTransitionTime += deltaTiime * speed;

            if (!floorSwitchApplied && floorTransitionTime >= FADE_OUT_DURATION) {
                currentFloor = transitionToFloor;
                map = map3D[currentFloor];
                portals = mazeGenerator3D.getPortals(currentFloor);
                floorSwitchApplied = true;
            }

            double controlsLockedUntil = FADE_OUT_DURATION + FADE_IN_DURATION;
            double totalDuration = controlsLockedUntil + CAPTION_ROLL_DURATION + CAPTION_HOLD_DURATION + CAPTION_FADE_DURATION;

            if (floorTransitionTime >= totalDuration) {
                isFloorTransitioning = false;
                floorSwitchApplied = false;
                floorTransitionTime = 0;
            }

            if (floorTransitionTime < controlsLockedUntil) return;
        }

        double speed = ((shift) ? runSpeed : moveSpeed) * deltaTiime;

        double dirX = Math.cos(cameraAngle), dirY = Math.sin(cameraAngle);
        double nextX = playerX;
        double nextY = playerY;
        double movementX = 0, movementY = 0;

        if (w) {
            nextX += dirX * speed;
            nextY += dirY * speed;
        }
        if (s) {
            nextX -= dirX * speed;
            nextY -= dirY * speed;
        }
        if (a) {
            nextX -= -dirY * speed;
            nextY -= dirX * speed;
        }
        if (d) {
            nextX += -dirY * speed;
            nextY += dirX * speed;
        }

        movementX = nextX - playerX;
        movementY = nextY - playerY;
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

        int portalCellY = Math.clamp((int) playerY, 0, MAZE_HEIGHT - 1);
        int portalCellX = Math.clamp((int) playerX, 0, MAZE_WIDTH - 1);
        if (map[portalCellY][portalCellX] == MazeGenerator.PORTAL && movementX * movementX + movementY * movementY > 1e-12) {
            PortalData.Portal portal = portals.get(portalCellX, portalCellY);
            if (portal != null) {
                double movementLength = Math.sqrt(movementX * movementX + movementY * movementY);
                double moveDirX = movementX / movementLength, moveDirY = movementY / movementLength;
                if (PortalData.entersFromCorrectSide(portal, moveDirX, moveDirY)) {
                    double[] warped = PortalData.warp(portal, playerX, playerY, moveDirX, moveDirY);
                    playerX = Math.clamp(warped[0], 0.0001, MAZE_WIDTH - 0.0001);
                    playerY = Math.clamp(warped[1], 0.0001, MAZE_HEIGHT - 0.0001);
                    double[] warpedFacing = PortalData.transformDirection(portal, Math.cos(cameraAngle), Math.sin(cameraAngle));
                    cameraAngle = Math.atan2(warpedFacing[1], warpedFacing[0]);
                    hasLastMousePos = false;
                }
            }
        }

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

        if (onStairsNow && !onStairsLastFrame && !isFloorTransitioning) {
            int targetFloor = currentFloor;
            if (cellType == 3 && currentFloor < map3D.length - 1) targetFloor = currentFloor + 1;
            else if (cellType == 4 && currentFloor > 0) targetFloor = currentFloor - 1;

            if (targetFloor != currentFloor) {
                transitionFromFloor = currentFloor;
                transitionToFloor = targetFloor;
                isFloorTransitioning = true;
                hasLastMousePos = floorSwitchApplied = false;
                floorTransitionTime = 0;
            }
        }
        onStairsLastFrame = onStairsNow;
    }
    //endregion/

    //region Rendering
    private void render() {
        if (rayTracing && (GEOMETRY_MODE != MazeGenerator.GeometryMode.WRONG)) renderRayTracing();
        else renderRayCasting();
        drawTimer();
        int hudLine = 0;
        if (GEOMETRY_MODE == MazeGenerator.GeometryMode.WRONG) drawGeometryBadge(hudLine++);
        if (GEOMETRY_MODE == MazeGenerator.GeometryMode.LOOPED) drawGeometryBadge(hudLine++, NSLocalizedString.get("mm.looped_geometry"));
        if (MAZE_3D) drawFloorIndicator(hudLine);
        if (isDebugMode && !isPaused) { drawMiniMap(); drawFpsCounter(); }
        if (isPaused) drawPauseMenu();
        if (isFloorTransitioning) drawFloorTransitionEffects();
    }

    private void renderRayCasting() {
        boolean wrongGeometry = GEOMETRY_MODE == MazeGenerator.GeometryMode.WRONG;
        double curvature = HYPERBOLIC_CURVATURE;
        double dirX = Math.cos(cameraAngle), dirY = Math.sin(cameraAngle);
        double planeLength = Math.tan((wrongGeometry ? Math.toRadians(140) : Math.PI / 3.0) / 2.0);
        double planeX = -dirY * planeLength, planeY = dirX * planeLength;
        double horizon = renderHeight / 2.0 + cameraPitch * renderHeight / 2.0;

        final int wallWidth = wallTexture.width, wallHeight = wallTexture.height;
        final int finishWidth = finishTexture.width, finishHeight = finishTexture.height;
        final int screenWidth = renderWidth, screenHeight = renderHeight;
        final int[] wallPixels = wallTexture.pixels, finishPixels = finishTexture.pixels;
        final int mapWidth = map[0].length, mapHeight = map.length;
        final double lightDistance = effectiveFlashlightDistance(wrongGeometry);

        renderHorizontalSurfaces(dirX, dirY, planeX, planeY, horizon);

        parallelRange(0, screenWidth, x -> {
            double ox = playerX, oy = playerY;
            double rayDirX = dirX + planeX * cameraXCache[x], rayDirY = dirY + planeY * cameraXCache[x];
            double totalDistance = 0, localPerp = 0;
            int hops = 0,  side = 0, hitType = 0;
            boolean hit = false;

            segments:
            while (!hit) {
                int mapX = (int) Math.floor(ox), mapY = (int) Math.floor(oy);
                double deltaDistX = rayDirX == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / rayDirX);
                double deltaDistY = rayDirY == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / rayDirY);
                double sideDistX = (rayDirX < 0 ? ox - mapX : mapX + 1.0 - ox) * deltaDistX;
                double sideDistY = (rayDirY < 0 ? oy - mapY : mapY + 1.0 - oy) * deltaDistY;

                while (true) {
                    if (sideDistX < sideDistY) {
                        sideDistX += deltaDistX;
                        mapX += rayDirX < 0 ? -1 : 1;
                        side = 0;
                    } else {
                        sideDistY += deltaDistY;
                        mapY += rayDirY < 0 ? -1 : 1;
                        side = 1;
                    }
                    if (mapX < 0 || mapY < 0 || mapX >= mapWidth || mapY >= mapHeight) return;
                    hitType = map[mapY][mapX];
                    if (hitType == MazeGenerator.PORTAL && hops < 8) {
                        PortalData.Portal portal = portals.get(mapX, mapY);
                        if (portal != null && PortalData.entersFromCorrectSide(portal, rayDirX, rayDirY)) {
                            double t = side == 0 ? sideDistX - deltaDistX : sideDistY - deltaDistY;
                            double hx = ox + rayDirX * t, hy = oy + rayDirY * t;
                            totalDistance += t;
                            double[] w = PortalData.warp(portal, hx, hy, rayDirX, rayDirY);
                            ox = w[0]; oy = w[1]; rayDirX = w[2]; rayDirY = w[3];
                            hops++;
                            continue segments;
                        }
                    }

                    if (hitType == 1 || hitType == 2) {
                        localPerp = side == 0 ? sideDistX - deltaDistX : sideDistY - deltaDistY;
                        hit = true;
                        break;
                    }
                }
            }

            double perpendicularDistance = Math.max(totalDistance + localPerp, 0.0001);
            double projectedDistance = wrongGeometry ? HyperbolicMath.hyperbolicDistance(perpendicularDistance, curvature) : perpendicularDistance;
            int wallHeightPx = (int) (screenHeight / projectedDistance);
            int drawStart = Math.max(hitType == 2 ? (int) horizon : (int) (horizon - wallHeightPx / 2.0), 0);
            int drawEnd = Math.min((int) (horizon + wallHeightPx / 2.0), screenHeight - 1);
            if (drawStart > drawEnd) return;

            double wallX = side == 0 ? oy + localPerp * rayDirY : ox + localPerp * rayDirX;
            wallX -= Math.floor(wallX);
            if (side == 0 && rayDirX < 0) wallX = 1.0 - wallX;
            if (side == 1 && rayDirY > 0) wallX = 1.0 - wallX;

            double brightness = Math.clamp(1.0 - projectedDistance / lightDistance, 0.03, 1.0);
            if (side == 1) brightness *= 0.85;
            int texX = Math.min((int)(wallX * (hitType == 2 ? finishWidth : wallWidth)), (hitType == 2 ? finishWidth : wallWidth) - 1);
            if (texX < 0) texX = 0;
            int offset = drawStart * screenWidth + x;

            for (int y = drawStart; y <= drawEnd; y++) {
                double wallPosition = hitType == 2 ? (y - horizon) / Math.max(wallHeightPx / 2.0, 1.0) : (y - (horizon - wallHeightPx / 2.0)) / Math.max(wallHeightPx, 1.0);
                int texY = (int)(Math.clamp(wallPosition, 0.0, 0.999999) * (hitType == 2 ? finishHeight : wallHeight));
                int color;
                if (hitType == 2 && finishWidth > 0 && finishHeight > 0) color = finishPixels[texY * finishWidth + texX];
                else if (hitType != 2 && wallWidth > 0 && wallHeight > 0) color = wallPixels[texY * wallWidth + texX];
                else color = hitType == 2 ? 0x33FF66 : wallBaseColor;
                renderPixels[offset] = applyBrightness(color, brightness);
                offset += screenWidth;
            }
        });

        if (renderScale == 1.0) System.arraycopy(renderPixels, 0, pixels, 0, pixels.length);
        else {
            Graphics2D g2d = bufferedImage.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g2d.drawImage(renderImage, 0, 0, RCJMS.GAME_WIDTH, RCJMS.GAME_HEIGHT, null);
            g2d.dispose();
        }
    }

    private void renderRayTracing() {
        final int targetWidth = renderWidth, targetHeight = renderHeight;
        final double internalScale = rayTracingQuality == 0 ? 0.35 : rayTracingQuality == 1 ? 0.50 : 1;
        final int width = Math.max(240, (int) Math.round(targetWidth * internalScale));
        final int height = Math.max(135, (int) Math.round(targetHeight * internalScale));
        final int[] rtPixels = new int[width * height];
        final double tanHalfFov = Math.tan(Math.toRadians(60.0) * 0.5);
        final double dirX = Math.cos(cameraAngle), dirY = Math.sin(cameraAngle);
        final double cp = Math.cos(cameraPitch), sp = Math.sin(cameraPitch);

        parallelRange(0, height, y -> {
            for (int x = 0; x < width; x++) {
                double sx = ((x + 0.5) / width * 2.0 - 1.0) * tanHalfFov;
                double sy = (1.0 - (y + 0.5) / height * 2.0) * (tanHalfFov / ((double) width / height));
                double rx = dirX * cp + (-dirY) * sx + (-dirX * sp) * sy;
                double ry = dirY * cp + dirX * sx + (-dirY * sp) * sy;
                double rz = sp + cp * sy;
                double inv = 1.0 / Math.sqrt(rx * rx + ry * ry + rz * rz);
                rx *= inv; ry *= inv; rz *= inv;
                TraceHit hit = traceRay(playerX, playerY, 0.5, rx, ry, rz);
                rtPixels[y * width + x] = shadeRay(hit, rx, ry, rz, rayTracingQuality >= 1, rayTracingQuality >= 2, false);
            }
        });

        BufferedImage rtImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int[] dst = ((DataBufferInt) rtImage.getRaster().getDataBuffer()).getData();
        System.arraycopy(rtPixels, 0, dst, 0, rtPixels.length);
        Graphics2D g2d = renderImage.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.drawImage(rtImage, 0, 0, targetWidth, targetHeight, null);
        g2d.dispose();
        copyRenderBuffer();
    }
    private static final class TraceHit {
        double x, y, z, distance;
        int type, side;
        double wallU, wallV;
        double dirX, dirY, dirZ;
        double rotCos = 1, rotSin = 0;
        boolean hit, isPlane;
    }
    private static double wallHeightForType(int type) { return type == 2 ? 0.5 : 1.0; }
    private TraceHit traceRay(double ox, double oy, double oz, double rx, double ry, double rz) {
        TraceHit result = new TraceHit();
        final int mapWidth = map[0].length, mapHeight = map.length;

        double totalDistance = 0;
        int hops = 0;
        double wallT = Double.POSITIVE_INFINITY;
        int wallType = 0, wallSide = 0;
        double rotCos = 1, rotSin = 0;

        segments:
        while (true) {
            double horizontal = Math.sqrt(rx * rx + ry * ry);
            if (horizontal <= 1e-9) break;

            double dx = rx / horizontal, dy = ry / horizontal;
            int mapX = (int) Math.floor(ox), mapY = (int) Math.floor(oy);
            double deltaX = Math.abs(1.0 / dx), deltaY = Math.abs(1.0 / dy);
            double sideX = (dx < 0 ? ox - mapX : mapX + 1.0 - ox) * deltaX;
            double sideY = (dy < 0 ? oy - mapY : mapY + 1.0 - oy) * deltaY;

            while (mapX >= 0 && mapY >= 0 && mapX < mapWidth && mapY < mapHeight) {
                double horizontalT;
                int side;
                if (sideX < sideY) {
                    horizontalT = sideX;
                    sideX += deltaX;
                    mapX += dx < 0 ? -1 : 1;
                    side = 0;
                }
                else {
                    horizontalT = sideY;
                    sideY += deltaY;
                    mapY += dy < 0 ? -1 : 1;
                    side = 1;
                }

                if (mapX < 0 || mapY < 0 || mapX >= mapWidth || mapY >= mapHeight) break;
                int type = map[mapY][mapX];
                if (type == MazeGenerator.PORTAL && hops < 8) {
                    PortalData.Portal portal = portals.get(mapX, mapY);
                    if (portal != null && PortalData.entersFromCorrectSide(portal, rx, ry)) {
                        double candidateT = horizontalT / horizontal;
                        double candidateZ = oz + rz * candidateT;
                        if (candidateZ >= 0.0 && candidateZ <= 1.0) {
                            double hx = ox + rx * candidateT, hy = oy + ry * candidateT;
                            totalDistance += candidateT;
                            double[] w = PortalData.warp(portal, hx, hy, rx, ry);
                            ox = w[0]; oy = w[1]; rx = w[2]; ry = w[3];
                            oz = candidateZ;
                            hops++;
                            double portalCos = portal.dirX * portal.linkedDirX + portal.dirY * portal.linkedDirY;
                            double portalSin = portal.dirX * portal.linkedDirY - portal.dirY * portal.linkedDirX;
                            double newRotCos = rotCos * portalCos - rotSin * portalSin;
                            double newRotSin = rotCos * portalSin + rotSin * portalCos;
                            rotCos = newRotCos; rotSin = newRotSin;
                            continue segments;
                        }
                    }
                }

                if (type != 1 && type != 2) continue;

                double candidateT = horizontalT / horizontal;
                double candidateZ = oz + rz * candidateT;
                if (candidateZ >= 0.0 && candidateZ <= wallHeightForType(type)) {
                    wallT = candidateT;
                    wallType = type;
                    wallSide = side;
                    break segments;
                }
            }
            break;
        }

        double planeT = Double.POSITIVE_INFINITY;
        int planeType = 0;
        if (rz < -1e-9) {
            planeT = -oz / rz;
            planeType = -1;
        }
        else if (rz > 1e-9) {
            planeT = (1.0 - oz) / rz;
            planeType = 1;
        }
        if (wallT < planeT && Double.isFinite(wallT)) {
            double wallHeight = wallHeightForType(wallType);
            result.hit = true;
            result.distance = totalDistance + wallT;
            result.x = ox + rx * wallT;
            result.y = oy + ry * wallT;
            result.z = Math.clamp(oz + rz * wallT, 0.0, wallHeight);
            result.type = wallType;
            result.side = wallSide;
            result.dirX = rx; result.dirY = ry; result.dirZ = rz;
            result.rotCos = rotCos; result.rotSin = rotSin;

            double wallU = wallSide == 0 ? result.y : result.x;
            wallU -= Math.floor(wallU);
            if ((wallSide == 0 && rx < 0) || (wallSide == 1 && ry > 0)) wallU = 1.0 - wallU;
            result.wallU = Math.clamp(wallU, 0.0, 0.999999);
            result.wallV = Math.clamp(result.z / wallHeight, 0.0, 0.999999);
        }
        else if (Double.isFinite(planeT) && planeT > 0) {
            result.hit = true;
            result.distance = totalDistance + planeT;
            result.x = ox + rx * planeT;
            result.y = oy + ry * planeT;
            result.z = planeType < 0 ? 0 : 1;
            result.type = planeType;
            result.side = 0;
            result.dirX = rx; result.dirY = ry; result.dirZ = rz;
            result.rotCos = rotCos; result.rotSin = rotSin;
            result.isPlane = true;
        }
        return result;
    }
    private double computeDirectLight(TraceHit hit, double nx, double ny, double nz, boolean wrongGeometry, boolean softShadows) {
        double lx = -hit.dirX, ly = -hit.dirY, lz = -hit.dirZ + 0.18;
        double len = Math.sqrt(lx * lx + ly * ly + lz * lz);
        if (len > 1e-9) { lx /= len; ly /= len; lz /= len; }

        double ndotl = Math.max(0, nx * lx + ny * ly + nz * lz);
        double direct = 0.16 + ndotl * 0.95;
        double lightDistance = Math.max(hit.distance, 0);
        double effectiveLightDistance = wrongGeometry ? HyperbolicMath.hyperbolicDistance(lightDistance, HYPERBOLIC_CURVATURE) : lightDistance;
        direct *= Math.clamp(1.0 - effectiveLightDistance / (effectiveFlashlightDistance(wrongGeometry) * 2.5), 0.18, 1.0);
        return Math.clamp(direct, 0.04, 1.35);
    }
    private int shadeRay(TraceHit hit, double rx, double ry, double rz, boolean reflections, boolean softShadows, boolean wrongGeometry) {
        if (!hit.hit) return ceilingBaseColor;
        int base;
        double nx, ny, nz, reflectivity;

        if (hit.isPlane) {
            TextureEditorView.RuntimeTexture texture = hit.type == -1 ? floorTexture : ceilingTexture;
            base = sampleTexture(texture, hit.x, hit.y, hit.type == -1 ? floorBaseColor : ceilingBaseColor);
            nx = 0; ny = 0; nz = hit.type == -1 ? 1 : -1;
            reflectivity = hit.type == -1 ? 0.12 : 0.06;
        }
        else {
            TextureEditorView.RuntimeTexture texture = hit.type == 2 ? finishTexture : wallTexture;
            base = sampleWallTexture(texture, hit, rx, ry);
            nx = hit.side == 0 ? (hit.dirX > 0 ? -1 : 1) : 0;
            ny = hit.side == 1 ? (hit.dirY > 0 ? -1 : 1) : 0;
            nz = 0;
            reflectivity = hit.type == 2 ? 0.08 : 0.24;
        }

        double baseDirect = computeDirectLight(hit, nx, ny, nz, wrongGeometry, softShadows);
        int lit = applyBrightness(base, baseDirect);

        reflectivity *= glossiness;
        reflectivity *= Math.clamp(baseDirect, 0.15, 1.0);
        if (!reflections || reflectivity <= 0) return lit;

        double dot = hit.dirX * nx + hit.dirY * ny + hit.dirZ * nz;
        double rrx = hit.dirX - 2.0 * dot * nx;
        double rry = hit.dirY - 2.0 * dot * ny;
        double rrz = hit.dirZ - 2.0 * dot * nz;
        double len = Math.sqrt(rrx * rrx + rry * rry + rrz * rrz);
        if (len < 1e-9) return lit;
        rrx /= len; rry /= len; rrz /= len;

        TraceHit reflected = traceRay(hit.x + nx * 0.004, hit.y + ny * 0.004, hit.z + nz * 0.004, rrx, rry, rrz);
        int reflectedColor = reflected.hit ? sampleReflection(reflected, rrx, rry, rrz, wrongGeometry) : ceilingBaseColor;
        return mixColor(lit, reflectedColor, reflectivity);
    }
    private int sampleReflection(TraceHit hit, double rx, double ry, double rz, boolean wrongGeometry) {
        int base;
        double nx, ny, nz;
        if (hit.isPlane) {
            TextureEditorView.RuntimeTexture texture = hit.type == -1 ? floorTexture : ceilingTexture;
            base = sampleTexture(texture, hit.x, hit.y, hit.type == -1 ? floorBaseColor : ceilingBaseColor);
            nx = 0; ny = 0; nz = hit.type == -1 ? 1 : -1;
        }
        else {
            TextureEditorView.RuntimeTexture texture = hit.type == 2 ? finishTexture : wallTexture;
            base = sampleWallTexture(texture, hit, rx, ry);
            nx = hit.side == 0 ? (hit.dirX > 0 ? -1 : 1) : 0;
            ny = hit.side == 1 ? (hit.dirY > 0 ? -1 : 1) : 0;
            nz = 0;
        }
        return applyBrightness(base, computeDirectLight(hit, nx, ny, nz, wrongGeometry, false));
    }
    private int mixColor(int a, int b, double amount) {
        amount = Math.clamp(amount, 0, 1);
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return ((int)(ar * (1 - amount) + br * amount) << 16) | ((int)(ag * (1 - amount) + bg * amount) << 8) | (int)(ab * (1 - amount) + bb * amount);
    }
    private int sampleWallTexture(TextureEditorView.RuntimeTexture texture, TraceHit hit, double rx, double ry) {
        if (texture.width <= 0 || texture.height <= 0) return hit.type == 2 ? 0x33FF66 : wallBaseColor;
        double wallX = hit.wallU;
        int tx = Math.min((int) (wallX * texture.width), texture.width - 1);
        int ty = Math.min((int) ((1.0 - hit.wallV) * texture.height), texture.height - 1);
        return texture.pixels[ty * texture.width + Math.max(0, tx)];
    }
    private void copyRenderBuffer() {
        if (renderScale == 1.0) System.arraycopy(renderPixels, 0, pixels, 0, pixels.length);
        else {
            Graphics2D g2d = bufferedImage.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g2d.drawImage(renderImage, 0, 0, RCJMS.GAME_WIDTH, RCJMS.GAME_HEIGHT, null);
            g2d.dispose();
        }
    }
    private void renderHorizontalSurfaces(double dirX, double dirY, double planeX, double planeY, double horizon) {
        final boolean hyperbolic = GEOMETRY_MODE == MazeGenerator.GeometryMode.WRONG;
        final double minDistance = 0.0001, lightDistance = effectiveFlashlightDistance(hyperbolic);
        final double leftRayX = dirX - planeX, leftRayY = dirY - planeY;
        final double rayStepX = (2.0 * planeX) / renderWidth, rayStepY = (2.0 * planeY) / renderWidth;
        final int width = renderWidth;

        parallelRange(Math.max(0, (int)Math.ceil(horizon)), renderHeight, y -> {
            double delta = y - horizon;
            if (delta < minDistance) return;
            double rowDistance = renderHeight / (2.0 * delta);
            double brightness = Math.clamp(1.0 - (hyperbolic ? HyperbolicMath.hyperbolicDistance(rowDistance, HYPERBOLIC_CURVATURE) : rowDistance) / lightDistance, 0.05, 1.0);
            double worldX = playerX + rowDistance * leftRayX, worldY = playerY + rowDistance * leftRayY;
            double stepX = rowDistance * rayStepX, stepY = rowDistance * rayStepY;
            int offset = y * width;
            for (int x = 0; x < width; x++) {
                int color = sampleTexture(floorTexture, worldX, worldY, floorBaseColor);
                if (MAZE_3D) color = tintStairs(color, worldX, worldY);
                renderPixels[offset + x] = applyBrightness(color, brightness);
                worldX += stepX; worldY += stepY;
            }
        });

        parallelRange(0, Math.min(renderHeight - 1, (int)Math.floor(horizon) - 1) + 1, y -> {
            double delta = horizon - y;
            if (delta < minDistance) return;
            double rowDistance = renderHeight / (2.0 * delta);
            double brightness = Math.clamp(1.0 - (hyperbolic ? HyperbolicMath.hyperbolicDistance(rowDistance, HYPERBOLIC_CURVATURE) : rowDistance) / lightDistance, 0.05, 1.0);
            double worldX = playerX + rowDistance * leftRayX;
            double worldY = playerY + rowDistance * leftRayY;
            double stepX = rowDistance * rayStepX;
            double stepY = rowDistance * rayStepY;
            int offset = y * width;
            for (int x = 0; x < width; x++) {
                int color = sampleTexture(ceilingTexture, worldX, worldY, ceilingBaseColor);
                renderPixels[offset + x] = applyBrightness(color, brightness);
                worldX += stepX; worldY += stepY;
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
        r = (int)(r * (1 - amount) + tr * amount); g = (int)(g * (1 - amount) + tg * amount); b = (int)(b * (1 - amount) + tb * amount);
        return (r << 16) | (g << 8) | b;
    }
    private int applyBrightness(int color, double brightness) {
        int r = (int)(((color >> 16) & 255) * brightness);
        int g = (int)(((color >> 8) & 255) * brightness);
        int b = (int)((color & 255) * brightness);
        return (r << 16) | (g << 8) | b;
    }
    private int sampleTexture(TextureEditorView.RuntimeTexture texture, double worldX, double worldY, int baseColor) {
        int width = texture.width, height = texture.height;
        if (width <= 0 || height <= 0) return baseColor;
        double fracX = worldX - Math.floor(worldX);
        double fracY = worldY - Math.floor(worldY);
        int tx = Math.min((int)(fracX * width), width - 1);
        int ty = Math.min((int)(fracY * height), height - 1);
        return texture.pixels[ty * width + tx];
    }
    //endregion

    //region HUD
    private static double uiScale() { return RCJMS.GAME_WIDTH / (double) RCJMS.SCREEN_WIDTH; }
    private void drawMiniMap() {
        if (GEOMETRY_MODE == MazeGenerator.GeometryMode.WRONG) { drawCoolMiniMap(); return; }
        double s = uiScale();
        int miniMapWidth = Math.max(1, (int) Math.round(MINI_MAP_WIDTH * s));
        int miniMapHeight = Math.max(1, (int) Math.round(MINI_MAP_HEIGHT * s));
        int margin = Math.max(1, (int) Math.round(10 * s));
        int mapWidth = map[0].length, mapHeight = map.length;
        double scale = Math.min((double) miniMapWidth / mapWidth, (double) miniMapHeight / mapHeight);

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

        int playerSize = Math.max(1, (int)Math.floor(scale * 0.25));
        for (int yy = -playerSize; yy <= playerSize; yy++) {
            for (int xx = -playerSize; xx <= playerSize; xx++) {
                int px = offsetX + (int)(playerX * scale) + xx;
                int py = offsetY + (int)(playerY * scale) + yy;
                if (px >= 0 && py >= 0 && px < RCJMS.GAME_WIDTH && py < RCJMS.GAME_HEIGHT) pixels[px + py * RCJMS.GAME_WIDTH] = 0xFF0000;
            }
        }
    }
    private void drawCoolMiniMap() {
        double s = uiScale();
        int miniMapWidth = Math.max(1, (int) Math.round(MINI_MAP_WIDTH * s));
        int miniMapHeight = Math.max(1, (int) Math.round(MINI_MAP_HEIGHT * s));
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

        for (int y = 0; y < map[0].length; y++) {
            for (int x = 0; x < map.length; x++) {
                if (map[y][x] == 0) continue;

                double diskR = HyperbolicMath.poincareRadius(Math.hypot((x + 0.5) - playerX, (y + 0.5) - playerY), HYPERBOLIC_CURVATURE);
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
        for (double a = 0; a < Math.PI * 2; a += 0.01) {
            int px = centerX + (int) Math.round(Math.cos(a) * diskRadiusPx);
            int py = centerY + (int) Math.round(Math.sin(a) * diskRadiusPx);
            if (px >= 0 && py >= 0 && px < RCJMS.GAME_WIDTH && py < RCJMS.GAME_HEIGHT) pixels[px + py * RCJMS.GAME_WIDTH] = 0x8888FF;
        }
        int tickLen = diskRadiusPx - Math.max(1, (int) Math.round(4 * s));
        drawMiniMapLine(centerX, centerY, centerX + (int)Math.round(Math.cos(cameraAngle) * tickLen),
                centerY + (int)Math.round(Math.sin(cameraAngle) * tickLen), 0xFFAA00);
        int playerSize = Math.max(1, (int) Math.round(3 * s));
        for (int yy = -playerSize; yy <= playerSize; yy++) {
            for (int xx = -playerSize; xx <= playerSize; xx++) {
                int px = centerX + xx, py = centerY + yy;
                if (px >= 0 && py >= 0 && px < RCJMS.GAME_WIDTH && py < RCJMS.GAME_HEIGHT) pixels[px + py * RCJMS.GAME_WIDTH] = 0xFF0000;
            }
        }
    }
    private void drawMiniMapLine(int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0), sx = x0 < x1 ? 1 : -1, dy = -Math.abs(y1 - y0), sy = y0 < y1 ? 1 : -1, err = dx + dy;
        while (true) {
            if (x0 >= 0 && y0 >= 0 && x0 < RCJMS.GAME_WIDTH && y0 < RCJMS.GAME_HEIGHT) pixels[x0 + y0 * RCJMS.GAME_WIDTH] = color;
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x0 += sx; }
            if (e2 <= dx) { err += dx; y0 += sy; }
        }
    }
    private void drawFpsCounter() {
        Graphics2D g2d = bufferedImage.createGraphics();
        g2d.scale(uiScale(), uiScale());
        String fpsText = "FPS: " + currentFps;

        g2d.setFont(fpsFont);
        g2d.setColor(Color.BLACK);
        g2d.drawString(fpsText, 40, RCJMS.SCREEN_HEIGHT - 28);

        g2d.setColor(Color.GREEN);
        g2d.drawString(fpsText, 38, RCJMS.SCREEN_HEIGHT - 30);
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
        g2d.scale(uiScale(), uiScale());
        elapsedSeconds = ((isPaused ? pauseStartTime : System.currentTimeMillis()) - gameStartTime - pausedTime) / 1000;

        long hours = elapsedSeconds / 3600;
        long minutes = (elapsedSeconds % 3600) / 60;
        long seconds = elapsedSeconds % 60;

        String timerText = String.format("%02d:%02d:%02d", hours, minutes, seconds);

        g2d.setFont(timerFont);
        g2d.setColor(Color.BLACK);
        g2d.drawString(timerText, RCJMS.SCREEN_WIDTH - 156, 34);

        g2d.setColor(Color.WHITE);
        g2d.drawString(timerText, RCJMS.SCREEN_WIDTH - 158, 32);
        g2d.dispose();
    }
    private void drawGeometryBadge(int line) { drawGeometryBadge(line, NSLocalizedString.get("gv.wrong_geometry")); }
    private void drawGeometryBadge(int line, String text) {
        Graphics2D g2d = bufferedImage.createGraphics();
        g2d.scale(uiScale(), uiScale());

        g2d.setFont(new Font(Font.MONOSPACED, Font.BOLD, 16));
        g2d.setColor(Color.BLACK);
        g2d.drawString(text, RCJMS.SCREEN_WIDTH - 198, RCJMS.SCREEN_HEIGHT - (26 + line * 24) + 2);
        g2d.setColor(new Color(150, 170, 255));
        g2d.drawString(text, RCJMS.SCREEN_WIDTH - 200, RCJMS.SCREEN_HEIGHT - (26 + line * 24));
        g2d.dispose();
    }
    private void drawFloorIndicator(int line) {
        Graphics2D g2d = bufferedImage.createGraphics();
        g2d.scale(uiScale(), uiScale());
        String text = NSLocalizedString.get("gv.floor") + (currentFloor + 1) + "/" + map3D.length;

        g2d.setFont(new Font(Font.MONOSPACED, Font.BOLD, 16));
        g2d.setColor(Color.BLACK);
        g2d.drawString(text, RCJMS.SCREEN_WIDTH - 198, RCJMS.SCREEN_HEIGHT - (26 + line * 24) + 2);
        g2d.setColor(new Color(255, 200, 120));
        g2d.drawString(text, RCJMS.SCREEN_WIDTH - 200, RCJMS.SCREEN_HEIGHT - (26 + line * 24));
        g2d.dispose();
    }
    private void drawPauseMenu() {
        Graphics2D g2d = bufferedImage.createGraphics();
        g2d.scale(uiScale(), uiScale());
        g2d.setColor(new Color(0,0,0,180));
        g2d.fillRect(0,0, RCJMS.SCREEN_WIDTH, RCJMS.SCREEN_HEIGHT);

        String pausedText = NSLocalizedString.get("gv.paused");

        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 80));
        int pausedWidth = g2d.getFontMetrics().stringWidth(pausedText);
        g2d.drawString(pausedText, (RCJMS.SCREEN_WIDTH - pausedWidth) / 2, RCJMS.SCREEN_HEIGHT / 2 - 50);

        g2d.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 20));
        g2d.drawString(NSLocalizedString.get("gv.continue"), RCJMS.SCREEN_WIDTH / 2 - 90, RCJMS.SCREEN_HEIGHT / 2 + 20);
        g2d.drawString(NSLocalizedString.get("gv.restart"), RCJMS.SCREEN_WIDTH / 2 - 90, RCJMS.SCREEN_HEIGHT / 2 + 50);
        g2d.drawString(NSLocalizedString.get("run"), RCJMS.SCREEN_WIDTH / 2 - 90, RCJMS.SCREEN_HEIGHT / 2 + 80);
        g2d.drawString(NSLocalizedString.get("gv.no_clip") + noClip, RCJMS.SCREEN_WIDTH / 2 - 90, RCJMS.SCREEN_HEIGHT / 2 + 110);
        g2d.drawString(NSLocalizedString.get("gv.geometry") + NSLocalizedString.get(
                GEOMETRY_MODE == MazeGenerator.GeometryMode.WRONG ? "gv.wrong_geometry" : "gv.euclidean"), RCJMS.SCREEN_WIDTH / 2 - 90, RCJMS.SCREEN_HEIGHT / 2 + 140);
        if (MAZE_3D) g2d.drawString(NSLocalizedString.get("gv.floor") + (currentFloor + 1) + "/" + map3D.length,
                RCJMS.SCREEN_WIDTH / 2 - 90, RCJMS.SCREEN_HEIGHT / 2 + 170);

        int exitHintY = RCJMS.SCREEN_HEIGHT / 2 + 170 + (MAZE_3D ? 30 : 0);
        g2d.drawString(NSLocalizedString.get("gv.exit_hint"), RCJMS.SCREEN_WIDTH / 2 - 90, exitHintY);

        g2d.dispose();
    }
    private void drawFloorTransitionEffects() {
        if (floorTransitionTime < FADE_OUT_DURATION)
            drawStairTransitionOverlay((float)Math.clamp(floorTransitionTime / FADE_OUT_DURATION, 0.0, 1.0), true);
        else if (floorTransitionTime < (FADE_OUT_DURATION + FADE_IN_DURATION))
            drawStairTransitionOverlay((float)Math.clamp((floorTransitionTime - FADE_OUT_DURATION) / FADE_IN_DURATION, 0.0, 1.0), false);
        else {
            double captionTime = floorTransitionTime - (FADE_OUT_DURATION + FADE_IN_DURATION);
            if (captionTime < (CAPTION_ROLL_DURATION + CAPTION_HOLD_DURATION + CAPTION_FADE_DURATION)) drawFloorCaption(captionTime);
        }
    }
    private void drawStairTransitionOverlay(float progress, boolean closing) {
        Graphics2D g2d = bufferedImage.createGraphics();
        g2d.scale(uiScale(), uiScale());
        float centerX = RCJMS.SCREEN_WIDTH / 2f;
        float centerY = RCJMS.SCREEN_HEIGHT / 2f;

        float edgeParam = closing ? progress : (1f - progress);
        float[] fractions = {0f, Math.clamp(1f - edgeParam - 0.16f, 0f, 1f), Math.clamp(1f - edgeParam, 0f, 1f), 1f};

        for (int i = 1; i < fractions.length; i++) if (fractions[i] <= fractions[i - 1]) fractions[i] = fractions[i - 1] + 0.0001f;
        if (fractions[3] > 1f) {
            float overflow = fractions[3] - 1f;
            for (int i = 0; i < fractions.length; i++) fractions[i] = Math.max(0f, fractions[i] - overflow);
            fractions[3] = 1f;
        }

        Color transparent = new Color(0, 0, 0, 0);
        Color opaque = new Color(0, 0, 0, 255);
        Color[] colors = {transparent, transparent, opaque, opaque};
        RadialGradientPaint paint = new RadialGradientPaint(new Point2D.Float(centerX, centerY), (float)Math.hypot(centerX, centerY), fractions, colors);

        Paint oldPaint = g2d.getPaint();
        g2d.setPaint(paint);
        g2d.fillRect(0, 0, RCJMS.SCREEN_WIDTH, RCJMS.SCREEN_HEIGHT);
        g2d.setPaint(oldPaint);
        g2d.dispose();
    }
    private void drawFloorCaption(double captionTime) {
        Graphics2D g2d = bufferedImage.createGraphics();
        g2d.scale(uiScale(), uiScale());
        g2d.setFont(new Font(Font.MONOSPACED, Font.BOLD, 56));
        FontMetrics fm = g2d.getFontMetrics();

        float alpha;
        double rollProgress;

        if (captionTime < CAPTION_ROLL_DURATION) {
            alpha = Math.clamp((float) (captionTime / Math.max(0.001, CAPTION_ROLL_DURATION * 0.4)), 0f, 1f);
            float inv = 1f - Math.clamp((float)(captionTime / CAPTION_ROLL_DURATION), 0f, 1f);
            rollProgress = 1f - Math.pow(inv, 3);
        } else if (captionTime < (CAPTION_ROLL_DURATION + CAPTION_HOLD_DURATION)) {
            alpha = 1f;
            rollProgress = 1;
        } else {
            alpha = Math.clamp((float) (1.0 - (captionTime - (CAPTION_ROLL_DURATION + CAPTION_HOLD_DURATION)) / CAPTION_FADE_DURATION), 0f, 1f);
            rollProgress = 1;
        }

        String prefix = NSLocalizedString.get("gv.floor");
        String suffix = "/" + map3D.length;
        String oldNumber = String.valueOf(transitionFromFloor + 1);
        String newNumber = String.valueOf(transitionToFloor + 1);

        int prefixWidth = fm.stringWidth(prefix);
        int numWidth = Math.max(fm.stringWidth(oldNumber), fm.stringWidth(newNumber));

        int baseX = (RCJMS.SCREEN_WIDTH - (prefixWidth + numWidth +  fm.stringWidth(suffix))) / 2;
        int rowHeight = fm.getHeight();

        Composite oldComposite = g2d.getComposite();
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

        g2d.setColor(Color.BLACK);
        g2d.drawString(prefix, baseX + 2, 112);
        g2d.setColor(Color.WHITE);
        g2d.drawString(prefix, baseX, 110);

        int numX = baseX + prefixWidth;
        Shape oldClip = g2d.getClip();
        g2d.clipRect(numX, 110 - fm.getAscent(), numWidth, rowHeight);

        int slideOffset = (int) (rollProgress * rowHeight);
        g2d.setColor(Color.BLACK);
        g2d.drawString(oldNumber, numX + 2, 112 - slideOffset);
        g2d.setColor(Color.WHITE);
        g2d.drawString(oldNumber, numX, 110 - slideOffset);

        g2d.setColor(Color.BLACK);
        g2d.drawString(newNumber, numX + 2, 112 + rowHeight - slideOffset);
        g2d.setColor(Color.WHITE);
        g2d.drawString(newNumber, numX, 110 + rowHeight - slideOffset);

        g2d.setClip(oldClip);

        g2d.setColor(Color.BLACK);
        g2d.drawString(suffix, numX + numWidth + 2, 110 + 2);
        g2d.setColor(Color.WHITE);
        g2d.drawString(suffix, numX + numWidth, 110);

        g2d.setComposite(oldComposite);
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
        if (isPaused && e.isControlDown() && key == KeyEvent.VK_C) {
            isGameRunning = false;
            showCursor();
            RCJMS.instance.ChangeView(RCJMS.instance.mainMenuView = new MainMenuView(), "main_menu");
            gameThread.interrupt();
            return;
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
                portals = mazeGenerator3D.getPortals(currentFloor);
            }
            else {
                if (mazeSeed != null) mazeGenerator = new MazeGenerator(MAZE_WIDTH, MAZE_HEIGHT, mazeSeed, GEOMETRY_MODE);
                else mazeGenerator = new MazeGenerator(MAZE_WIDTH, MAZE_HEIGHT, GEOMETRY_MODE);
                map = mazeGenerator.generate(MAZE_MODE);
                portals = mazeGenerator.getPortals();
            }

            playerX = playerY = 1.5;
            cameraAngle = cameraPitch = 0;
            onStairsLastFrame = false;
            isFloorTransitioning = false;
            floorSwitchApplied = false;
            floorTransitionTime = 0;
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
        if (isFloorTransitioning && floorTransitionTime < FADE_OUT_DURATION + FADE_IN_DURATION) return;
        if (MOUSE_WARP_SUPPORTED) mouseMovedWithWarp(e);
        else mouseMovedWithDelta(e);
    }
    private void mouseMovedWithWarp(MouseEvent e) {
        if (isRecentering) { isRecentering = false; return; }
        int centerX = getLocationOnScreen().x + getWidth() / 2;
        int centerY = getLocationOnScreen().y + getHeight() / 2;

        cameraAngle += (e.getXOnScreen() - centerX) * mouseSensitivity;
        cameraPitch -= (e.getYOnScreen() - centerY) * mouseSensitivity;
        cameraPitch = Math.clamp(cameraPitch, -1.2, 1.2);

        isRecentering = true;
        cursorRobot.mouseMove(centerX, centerY);
    }
    private void mouseMovedWithDelta(MouseEvent e) {
        int x = e.getXOnScreen();
        int y = e.getYOnScreen();

        if (!hasLastMousePos) {
            lastMouseScreenX = x;
            lastMouseScreenY = y;
            hasLastMousePos = true;
            return;
        }

        int dx = x - lastMouseScreenX;
        int dy = y - lastMouseScreenY;

        cameraAngle += dx * mouseSensitivity;
        cameraPitch -= dy * mouseSensitivity;
        cameraPitch = Math.clamp(cameraPitch, -1.2, 1.2);

        lastMouseScreenX = x;
        lastMouseScreenY = y;
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

    //region Helpers
    private double[] buildCameraXCache() {
        double[] values = new double[renderWidth];
        for (int x = 0; x < values.length; x++) values[x] = 2.0 * x / (double) values.length - 1.0;
        return values;
    }
    private void hideCursor() { setCursor(invisibleCursor); hasLastMousePos = false; }
    private void showCursor() { setCursor(Cursor.getDefaultCursor()); hasLastMousePos = false; }
    @Override public void addNotify() {
        super.addNotify();
        SwingUtilities.invokeLater(this::requestFocusInWindow);
    }
    private void parallelRange(int start, int end, IntConsumer task) {
        int range = end - start;
        if (range <= 0) return;
        renderWorkers.execute(start, end, task);
    }
    private static final class RenderWorkers {
        private final Object lock = new Object();
        private final Worker[] workers;
        private IntConsumer task;
        private int start, end, remaining;
        private long generation;
        private boolean stopping;

        RenderWorkers(int count) {
            workers = new Worker[count];
            for (int i = 0; i < count; i++) {
                workers[i] = new Worker("GameViewRenderWorker-" + i, i);
                workers[i].start();
            }
        }

        void execute(int start, int end, IntConsumer task) {
            synchronized (lock) {
                this.start = start;
                this.end = end;
                this.task = task;
                remaining = workers.length;
                generation++;
                lock.notifyAll();
                while (remaining > 0 && !stopping) {
                    try { lock.wait(); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                }
            }
        }
        void shutdown() {
            synchronized (lock) {
                stopping = true;
                lock.notifyAll();
            }
        }
        private final class Worker extends Thread {
            private long seenGeneration;
            private final int index;
            Worker(String name, int index) { super(name); this.index = index; setDaemon(true); }
            @Override public void run() {
                while (true) {
                    IntConsumer localTask;
                    int localStart, localEnd, index;
                    synchronized (lock) {
                        while (!stopping && seenGeneration == generation) {
                            try { lock.wait(); }
                            catch (InterruptedException e) { if (stopping) return; }
                        }
                        if (stopping) return;
                        seenGeneration = generation;
                        index = this.index;
                        int range = end - start;
                        int chunk = (range + workers.length - 1) / workers.length;
                        localStart = start + index * chunk;
                        localEnd = Math.min(end, localStart + chunk);
                        localTask = task;
                    }
                    if (localStart < localEnd) for (int i = localStart; i < localEnd; i++) localTask.accept(i);
                    synchronized (lock) { if (--remaining == 0) lock.notifyAll(); }
                }
            }
        }
    }
    public static double getRenderScale() { return renderScale; }
    public static void setRenderScale(double scale) { renderScale = Math.clamp(scale, 0.1, 1.0); }
    public static boolean isVSyncEnabled() { return vsync; }
    public static void setVSyncEnabled(boolean enabled) { vsync = enabled; }
    public static boolean isRayTracingEnabled() { return rayTracing; }
    public static void setRayTracingEnabled(boolean enabled) { rayTracing = enabled; }
    public static int getRayTracingQuality() { return rayTracingQuality; }
    public static void setRayTracingQuality(int quality) { rayTracingQuality = Math.clamp(quality, 0, 2); }
    private static boolean detectMouseWarpSupport() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (!(os.contains("nux") || os.contains("nix"))) return true;
        String sessionType = System.getenv("XDG_SESSION_TYPE");
        if (sessionType != null && sessionType.equalsIgnoreCase("wayland")) return false;
        String waylandDisplay = System.getenv("WAYLAND_DISPLAY");
        return waylandDisplay == null || waylandDisplay.isEmpty();
    }
    //endregion
}