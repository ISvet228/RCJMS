import Helpers.*;
import GameViewComponents.*;
import static GameViewComponents.RenderEngineConfig.*;

import javax.swing.*; //Frame Library
import java.awt.*; //Graphics Library
import java.awt.event.*; //Input Library
import java.awt.image.*; //Buffer Library
import java.awt.geom.Point2D;
import java.io.IOException;

public class GameView extends JPanel implements Runnable, KeyListener, MouseMotionListener {
    //region Variables
    //region Maze Configuration
    private static MazeGenerator.FinishMode MAZE_MODE = MazeGenerator.FinishMode.RANDOM_EDGE;
    private static boolean MAZE_3D = false;
    private static int MAZE_FLOORS = 1;
    private static final double STAIR_ANIMATION_SPEED = 1.0;
    //endregion
    //region Dependencies
    private MazeGenerator mazeGenerator;
    private MazeGenerator3D mazeGenerator3D;
    private int[][][] map3D;
    private PortalData[] floorPortalsAll;
    private int currentFloor = 0;
    private boolean onStairsLastFrame = false;
    private final Cursor invisibleCursor;
    private Robot cursorRobot; //BOBR KURSOR JA PERDOLE
    private Thread gameThread;
    private int[][] map;
    private PortalData portals = new PortalData();
    private final String customSeed;
    private String mazeSeedText = "";
    private final boolean isCustomMap;
    private final int requestedWidth, requestedHeight;
    private int[][][] customFloorsForRestart;
    private PortalData[] customPortalsForRestart;
    private int customGeometryModeForRestart;
    //endregion
    //region Lighting And Camera Constants
    private static final double CAMERA_HEIGHT = 0.5;
    private double cameraHeight = CAMERA_HEIGHT;
    //endregion
    //region Render Buffers
    public static int MINI_MAP_WIDTH = 180, MINI_MAP_HEIGHT = 180;
    private static final double MINI_MAP_MIN_CELL_PIXELS = 4.0;
    private static volatile int miniMapCellPixels = 6;
    private final BufferedImage bufferedImage;
    private final BufferedImage renderImage;
    private final int[] pixels, renderPixels; //PUXELS
    private final int renderWidth, renderHeight;
    //endregion
    //region Textures & Colors
    private final RuntimeTexture wallTexture = TextureEditorView.readRuntimeTexture(AppPaths.WALL_TEXTURE_FILE, TextureEditorView.MAX_TEXTURE_SIZE, TextureEditorView.MAX_TEXTURE_SIZE);
    private final RuntimeTexture floorTexture = TextureEditorView.readRuntimeTexture(AppPaths.FLOOR_TEXTURE_FILE, TextureEditorView.MAX_TEXTURE_SIZE, TextureEditorView.MAX_TEXTURE_SIZE);
    private final RuntimeTexture ceilingTexture = TextureEditorView.readRuntimeTexture(AppPaths.CEILING_TEXTURE_FILE, TextureEditorView.MAX_TEXTURE_SIZE, TextureEditorView.MAX_TEXTURE_SIZE);
    private final RuntimeTexture finishTexture = TextureEditorView.readRuntimeTexture(AppPaths.FINISH_TEXTURE_FILE, TextureEditorView.MAX_TEXTURE_SIZE, TextureEditorView.MAX_FINISH_HEIGHT);

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
    private boolean noClip = false;
    //endregion
    //region Multi-Thread Render
    private final int renderThreadCount = Runtime.getRuntime().availableProcessors(); //fck limits
    private final RenderWorkers renderWorkers = new RenderWorkers(renderThreadCount);
    //endregion
    //region Graphics Settings
    private static volatile double renderScale = 1.0;
    private static volatile boolean vsync = false;
    public static final int ENGINE_DDA = 0, ENGINE_RAY_TRACING = 1, ENGINE_RAY_MARCHING = 2, ENGINE_RAY_CASTING = 3;
    private static volatile int renderEngine = ENGINE_DDA;
    //endregion
    //region Game State
    private boolean isGameRunning = false;
    private boolean isPaused = false, isDebugMode = false;
    private long gameStartTime = System.currentTimeMillis();
    private long pauseStartTime = 0, pausedTime = 0, elapsedSeconds;
    //endregion
    //region Mouse / Input Compatibility
    private FocusListener focusWatcher;
    private Window watchedWindow;
    private WindowListener windowWatcher;
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
    private static final Font BADGE_FONT = new Font(Font.MONOSPACED, Font.BOLD, 16);
    private static final Font BIG_SANS_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 80);
    private static final Font SMALL_SANS_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 20);
    private static final Font BIG_MONO_FONT = new Font(Font.MONOSPACED, Font.BOLD, 56);
    //endregion
    //region Fog Of War Minimap
    private static final double FOG_REVEAL_RADIUS = 4.5, FOG_REVEAL_SPEED = 3.0, FOG_RAY_STEP = 0.08;
    private static final int FOG_RAY_COUNT = 120;
    private static final Font FOG_HINT_FONT = new Font(Font.MONOSPACED, Font.BOLD, 12);
    private boolean fogMinimap = false;
    private boolean fogMapVisible = true;
    private float[][][] explored;
    private int[][] fogVisitStamp;
    private int fogFrame = 0;
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

    //region Constructors
    public GameView(int mazeWidth, int mazeHeight, int mazeMode, int geometryMode) throws IOException {
        this(mazeWidth, mazeHeight, mazeMode, geometryMode, (String) null, 1); }
    public GameView(int mazeWidth, int mazeHeight, int mazeMode, int geometryMode, String seed) throws IOException {
        this(mazeWidth, mazeHeight, mazeMode, geometryMode, seed, 1); }
    public GameView(int mazeWidth, int mazeHeight, int mazeMode, int geometryMode, int floors) throws IOException {
        this(mazeWidth, mazeHeight, mazeMode, geometryMode, (String) null, floors); }
    public GameView(int mazeWidth, int mazeHeight, int mazeMode, int geometryMode, String seed, int floors) throws IOException {
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

        String typedSeed = SeedUtil.normalize(seed);
        customSeed = typedSeed.isEmpty() ? null : typedSeed;
        isCustomMap = false;
        requestedWidth = mazeWidth;
        requestedHeight = mazeHeight;
        GEOMETRY_MODE = MazeGenerator.GeometryMode.values()[Math.clamp(geometryMode, 0, MazeGenerator.GeometryMode.values().length - 1)];
        MAZE_MODE = MazeGenerator.FinishMode.values()[mazeMode];

        MAZE_3D = floors > 1;
        MAZE_FLOORS = MAZE_3D ? floors : 1;

        if (MAZE_3D) {
            mazeGenerator3D = new MazeGenerator3D(mazeWidth, mazeHeight, MAZE_FLOORS, chooseSeed(), GEOMETRY_MODE);

            map3D = mazeGenerator3D.generate(MAZE_MODE);
            currentFloor = 0;
            floorPortalsAll = new PortalData[MAZE_FLOORS];
            for (int f = 0; f < MAZE_FLOORS; f++) floorPortalsAll[f] = mazeGenerator3D.getPortals(f);
            map = map3D[currentFloor];
            portals = floorPortalsAll[currentFloor];
        }
        else {
            mazeGenerator = new MazeGenerator(mazeWidth, mazeHeight, chooseSeed(), GEOMETRY_MODE);
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
    public GameView(int[][][] customFloors, PortalData[] customPortals, int geometryMode) throws IOException {
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
        customSeed = null;
        isCustomMap = true;
        requestedWidth = requestedHeight = -1;

        int floors = Math.max(1, customFloors.length);
        MAZE_3D = floors > 1;
        MAZE_FLOORS = floors;
        GEOMETRY_MODE = MazeGenerator.GeometryMode.values()[Math.clamp(geometryMode, 0, MazeGenerator.GeometryMode.values().length - 1)];

        map3D = customFloors;
        floorPortalsAll = new PortalData[floors];
        for (int f = 0; f < floors; f++) floorPortalsAll[f] = (customPortals != null && f < customPortals.length && customPortals[f] != null) ? customPortals[f] : new PortalData();

        currentFloor = 0;
        map = map3D[currentFloor];
        portals = floorPortalsAll[currentFloor];

        customFloorsForRestart = map3D;
        customPortalsForRestart = floorPortalsAll;
        customGeometryModeForRestart = geometryMode;

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
        customSeed = null;
        isCustomMap = true;
        requestedWidth = requestedHeight = -1;
        MAZE_3D = false;
        GEOMETRY_MODE = MazeGenerator.GeometryMode.EUCLIDEAN;
        MAZE_FLOORS = 1;
        map = customMap;
        addKeyListener(this);
        addMouseMotionListener(this);

        try {cursorRobot = new Robot();}
        catch (Exception e) {e.printStackTrace();}

        Toolkit toolkit = Toolkit.getDefaultToolkit();
        invisibleCursor = toolkit.createCustomCursor(toolkit.createImage(new byte[0]), new Point(0, 0), "hidden");
        hideCursor();
    }
    //endregion

    //region Showcase
    public void setShowcaseCamera(double x, double y, double angle, double pitch, double eyeHeight) {
        playerX = x;
        playerY = y;
        cameraAngle = angle;
        cameraPitch = pitch;
        cameraHeight = Math.clamp(eyeHeight, 0.05, 0.95);
    }
    public BufferedImage renderShowcaseFrame() {
        renderSelectedEngine();
        copyRenderBuffer();
        return bufferedImage;
    }
    public void releaseShowcase() { renderWorkers.shutdown(); }
    private void copyRenderBuffer() {
        if (renderWidth == bufferedImage.getWidth() && renderHeight == bufferedImage.getHeight()) {
            System.arraycopy(renderPixels, 0, pixels, 0, pixels.length);
        } else {
            Graphics2D g2d = bufferedImage.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g2d.drawImage(renderImage, 0, 0, RCJMS.GAME_WIDTH, RCJMS.GAME_HEIGHT, null);
            g2d.dispose();
        }
    }
    //endregion

    //region Game Loop
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

            boolean errorReported = false;
            while (isGameRunning) {
                try {
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
                        Thread.sleep(1);
                        nextVSyncTime = System.nanoTime();
                    }
                    trackFps();
                }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                catch (Throwable t) {
                    if (!errorReported) { errorReported = true; t.printStackTrace(); }
                    try { Thread.sleep(5); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                    lastFrameTime = System.nanoTime();
                    nextVSyncTime = lastFrameTime;
                }
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
                portals = floorPortalsAll[currentFloor];
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
        double movementX, movementY;

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
        nextX = Math.clamp(nextX, 0.0001, mapWidth() - 0.0001);
        nextY = Math.clamp(nextY, 0.0001, mapHeight() - 0.0001);

        if (noClip) {
            playerX = nextX;
            playerY = nextY;
        }
        else {
            int nextMapX = (int)Math.floor(nextX);
            int currentMapY = (int)Math.floor(playerY);

            if (nextMapX >= 0 && nextMapX < mapWidth() && currentMapY >= 0 && currentMapY < mapHeight() && map[currentMapY][nextMapX] != 1) playerX = nextX;
            else {
                if (playerX < nextX) playerX = Math.min(Math.ceil(playerX) - 0.0001, mapWidth() - 0.0001);
                else if (playerX > nextX) playerX = Math.max(Math.floor(playerX) + 0.0001, 0.0001);
            }

            int currentMapX = (int)Math.floor(playerX);
            int nextMapY = (int)Math.floor(nextY);

            if (currentMapX >= 0 && currentMapX < mapWidth() && nextMapY >= 0 && nextMapY < mapHeight() && map[nextMapY][currentMapX] != 1) playerY = nextY;
            else {
                if (playerY < nextY) playerY = Math.min(Math.ceil(playerY) - 0.0001, mapHeight() - 0.0001);
                else if (playerY > nextY) playerY = Math.max(Math.floor(playerY) + 0.0001, 0.0001);
            }
        }

        playerX = Math.clamp(playerX, 0.0001, mapWidth() - 0.0001);
        playerY = Math.clamp(playerY, 0.0001, mapHeight() - 0.0001);

        int portalCellY = Math.clamp((int) playerY, 0, mapHeight() - 1);
        int portalCellX = Math.clamp((int) playerX, 0, mapWidth() - 1);
        if (map[portalCellY][portalCellX] == MazeGenerator.PORTAL && movementX * movementX + movementY * movementY > 1e-12) {
            PortalData.Portal portal = portals.get(portalCellX, portalCellY);
            if (portal != null) {
                double movementLength = Math.sqrt(movementX * movementX + movementY * movementY);
                double moveDirX = movementX / movementLength, moveDirY = movementY / movementLength;
                if (PortalData.entersFromCorrectSide(portal, moveDirX, moveDirY)) {
                    double[] warped = PortalData.warp(portal, playerX, playerY, moveDirX, moveDirY);
                    if (map3D != null && portal.linkedFloor >= 0 && portal.linkedFloor != currentFloor && portal.linkedFloor < map3D.length) {
                        currentFloor = portal.linkedFloor;
                        map = map3D[currentFloor];
                        portals = floorPortalsAll[currentFloor];
                    }
                    playerX = Math.clamp(warped[0], 0.0001, mapWidth() - 0.0001);
                    playerY = Math.clamp(warped[1], 0.0001, mapHeight() - 0.0001);
                    double[] warpedFacing = PortalData.transformDirection(portal, Math.cos(cameraAngle), Math.sin(cameraAngle));
                    cameraAngle = Math.atan2(warpedFacing[1], warpedFacing[0]);
                    hasLastMousePos = false;
                }
            }
        }

        int cellY = Math.clamp((int) playerY, 0, mapHeight() - 1);
        int cellX = Math.clamp((int) playerX, 0, mapWidth() - 1);
        int cellType = map[cellY][cellX];

        if (cellType == 2 && isGameRunning) {
            isGameRunning = false;
            playerX = playerY = 1.5;
            cameraAngle = cameraPitch = 0;
            RCJMS.instance.changeView(RCJMS.instance.victoryView = new VictoryView(elapsedSeconds, requestedWidth, requestedHeight, isCustomMap), "vv.victory");
            gameThread.interrupt();
            return;
        }

        if (fogMinimap) updateFog(deltaTiime);
        if (is3D()) handleStairs(cellType);
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
    //endregion

    //region Rendering
    private void render() {
        renderSelectedEngine();
        drawTimer();
        int hudLine = 0;
        if (!isCustomMap) drawSeedBadge(hudLine++);
        if (GEOMETRY_MODE == MazeGenerator.GeometryMode.WRONG) drawGeometryBadge(hudLine++);
        if (GEOMETRY_MODE == MazeGenerator.GeometryMode.LOOPED) drawGeometryBadge(hudLine++, NSLocalizedString.get("mm.looped_geometry"));
        if (is3D()) drawFloorIndicator(hudLine);
        if (isDebugMode && !isPaused) { drawMiniMap(); drawFpsCounter(); }
        if (fogMinimap && fogMapVisible) drawFogMiniMap();
        if (isPaused) drawPauseMenu();
        if (isFloorTransitioning) drawFloorTransitionEffects();
    }
    //endregion

    //region Renderers
    private final DDAEngine DDAEngine = new DDAEngine();
    private final SoftwareRayCastingEngine softwareRayCastingEngine = new SoftwareRayCastingEngine();
    private final RayMarchingEngine rayMarchingEngine = new RayMarchingEngine();
    private final RayTracingEngine rayTracingEngine = new RayTracingEngine();
    private void syncRenderer(RenderEngineBase r) {
        r.bufferedImage = bufferedImage;
        r.cameraAngle = cameraAngle;
        r.cameraHeight = cameraHeight;
        r.cameraPitch = cameraPitch;
        r.cameraXCache = cameraXCache;
        r.ceilingTexture = ceilingTexture;
        r.finishTexture = finishTexture;
        r.floorPortalsAll = floorPortalsAll;
        r.floorTexture = floorTexture;
        RenderEngineConfig.outputWidth = RCJMS.GAME_WIDTH;
        RenderEngineConfig.outputHeight = RCJMS.GAME_HEIGHT;
        r.map = map;
        r.map3D = map3D;
        r.pixels = pixels;
        r.playerX = playerX;
        r.playerY = playerY;
        r.portals = portals;
        r.renderHeight = renderHeight;
        r.renderImage = renderImage;
        r.renderPixels = renderPixels;
        r.renderWidth = renderWidth;
        r.renderWorkers = renderWorkers;
        r.wallTexture = wallTexture;
    }
    private void renderSelectedEngine() {
        switch (renderEngine) {
            case ENGINE_RAY_TRACING -> { syncRenderer(rayTracingEngine); rayTracingEngine.renderRayTracing(); }
            case ENGINE_RAY_MARCHING -> { syncRenderer(rayMarchingEngine); rayMarchingEngine.renderRayMarching(); }
            case ENGINE_RAY_CASTING -> { syncRenderer(softwareRayCastingEngine); softwareRayCastingEngine.renderSoftwareRayCasting(); }
            default -> { syncRenderer(DDAEngine); DDAEngine.renderRayCasting(); }
        }
    }
    //endregion

    //region Fog Of War Logic
    private float[][] exploredGrid() {
        int floors = floorCount(), h = mapHeight(), w = mapWidth();
        if (explored == null || explored.length != floors || explored[0].length != h || explored[0][0].length != w) {
            explored = new float[floors][h][w];
            fogVisitStamp = new int[h][w];
        }
        return explored[currentFloor];
    }
    private void updateFog(double dt) {
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
    private void drawFogMiniMap() {
        float[][] grid = exploredGrid();
        final int w = mapWidth(), h = mapHeight();
        double s = uiScale();
        int cell = Math.max(3, (int) Math.round(miniMapCellPixels * s));
        int cells = Math.max(7, (int) Math.round(MINI_MAP_WIDTH * s) / cell);
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
        drawMiniMapLine(px, py, px + (int) Math.round(Math.cos(cameraAngle) * tick), py + (int) Math.round(Math.sin(cameraAngle) * tick), 0xFFAA00);
        int half = Math.max(1, cell / 3);
        fillMiniRect(px - half, py - half, px + half + 1, py + half + 1, 0xFF0000, margin, margin, margin + box, margin + box);

        Graphics2D g2d = bufferedImage.createGraphics();
        g2d.scale(s, s);
        g2d.setFont(FOG_HINT_FONT);
        g2d.setColor(new Color(150, 150, 190));
        g2d.drawString("M", (float) ((margin + box) / s - 10), (float) ((margin + box) / s + 14));
        g2d.dispose();
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
        drawMiniMapLine(px, py, px + (int) Math.round(Math.cos(cameraAngle) * tick), py + (int) Math.round(Math.sin(cameraAngle) * tick), 0xFFAA00);
        int half = Math.max(1, cell / 3);
        fillMiniRect(px - half, py - half, px + half + 1, py + half + 1, 0xFF0000, margin, margin, margin + boxW, margin + boxH);
    }
    private void fillMiniRect(int x0, int y0, int x1, int y1, int color, int clipX0, int clipY0, int clipX1, int clipY1) {
        x0 = Math.max(x0, Math.max(clipX0, 0)); y0 = Math.max(y0, Math.max(clipY0, 0));
        x1 = Math.min(x1, Math.min(clipX1, RCJMS.GAME_WIDTH)); y1 = Math.min(y1, Math.min(clipY1, RCJMS.GAME_HEIGHT));
        for (int yy = y0; yy < y1; yy++) {
            int row = yy * RCJMS.GAME_WIDTH;
            for (int xx = x0; xx < x1; xx++) pixels[row + xx] = color;
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

        g2d.setFont(BADGE_FONT);
        g2d.setColor(Color.BLACK);
        g2d.drawString(text, RCJMS.SCREEN_WIDTH - 198, RCJMS.SCREEN_HEIGHT - (26 + line * 24) + 2);
        g2d.setColor(new Color(150, 170, 255));
        g2d.drawString(text, RCJMS.SCREEN_WIDTH - 200, RCJMS.SCREEN_HEIGHT - (26 + line * 24));
        g2d.dispose();
    }
    private void drawSeedBadge(int line) {
        Graphics2D g2d = bufferedImage.createGraphics();
        g2d.scale(uiScale(), uiScale());
        String text = NSLocalizedString.get("gv.seed") + mazeSeedText;

        g2d.setFont(BADGE_FONT);
        g2d.setColor(Color.BLACK);
        g2d.drawString(text, RCJMS.SCREEN_WIDTH - 198, RCJMS.SCREEN_HEIGHT - (26 + line * 24) + 2);
        g2d.setColor(new Color(170, 230, 170));
        g2d.drawString(text, RCJMS.SCREEN_WIDTH - 200, RCJMS.SCREEN_HEIGHT - (26 + line * 24));
        g2d.dispose();
    }
    private void drawFloorIndicator(int line) {
        Graphics2D g2d = bufferedImage.createGraphics();
        g2d.scale(uiScale(), uiScale());
        String text = NSLocalizedString.get("floor") + (currentFloor + 1) + "/" + map3D.length;

        g2d.setFont(BADGE_FONT);
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
        g2d.setFont(BIG_SANS_FONT);
        int pausedWidth = g2d.getFontMetrics().stringWidth(pausedText);
        g2d.drawString(pausedText, (RCJMS.SCREEN_WIDTH - pausedWidth) / 2, RCJMS.SCREEN_HEIGHT / 2 - 50);

        g2d.setFont(SMALL_SANS_FONT);
        g2d.drawString(NSLocalizedString.get("gv.continue"), RCJMS.SCREEN_WIDTH / 2 - 90, RCJMS.SCREEN_HEIGHT / 2 + 20);
        g2d.drawString(NSLocalizedString.get("gv.restart"), RCJMS.SCREEN_WIDTH / 2 - 90, RCJMS.SCREEN_HEIGHT / 2 + 50);
        g2d.drawString(NSLocalizedString.get("run"), RCJMS.SCREEN_WIDTH / 2 - 90, RCJMS.SCREEN_HEIGHT / 2 + 80);
        g2d.drawString(NSLocalizedString.get("gv.no_clip") + noClip, RCJMS.SCREEN_WIDTH / 2 - 90, RCJMS.SCREEN_HEIGHT / 2 + 110);
        g2d.drawString(NSLocalizedString.get("gv.geometry") + NSLocalizedString.get(
                GEOMETRY_MODE == MazeGenerator.GeometryMode.WRONG ? "gv.wrong_geometry" : "gv.euclidean"), RCJMS.SCREEN_WIDTH / 2 - 90, RCJMS.SCREEN_HEIGHT / 2 + 140);
        if (is3D()) g2d.drawString(NSLocalizedString.get("floor") + (currentFloor + 1) + "/" + map3D.length,
                RCJMS.SCREEN_WIDTH / 2 - 90, RCJMS.SCREEN_HEIGHT / 2 + 170);

        int exitHintY = RCJMS.SCREEN_HEIGHT / 2 + 170 + (is3D() ? 30 : 0);
        g2d.drawString(NSLocalizedString.get("gv.exit_hint"), RCJMS.SCREEN_WIDTH / 2 - 90, exitHintY);
        if (fogMinimap) g2d.drawString(NSLocalizedString.get("gv.map_toggle_hint"), RCJMS.SCREEN_WIDTH / 2 - 90, exitHintY + 30);

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
        g2d.setFont(BIG_MONO_FONT);
        FontMetrics fm = g2d.getFontMetrics();

        float alpha;
        double rollProgress;

        if (captionTime < CAPTION_ROLL_DURATION) {
            alpha = Math.clamp((float) (captionTime / Math.max(0.001, CAPTION_ROLL_DURATION * 0.4)), 0f, 1f);
            rollProgress = 1f - Math.pow(1f - Math.clamp((float) (captionTime / CAPTION_ROLL_DURATION), 0f, 1f), 3);
        } else if (captionTime < (CAPTION_ROLL_DURATION + CAPTION_HOLD_DURATION)) {
            alpha = 1f;
            rollProgress = 1;
        } else {
            alpha = Math.clamp((float) (1.0 - (captionTime - (CAPTION_ROLL_DURATION + CAPTION_HOLD_DURATION)) / CAPTION_FADE_DURATION), 0f, 1f);
            rollProgress = 1;
        }

        String prefix = NSLocalizedString.get("floor");
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

    //region Input
    @Override public void keyPressed(KeyEvent e) {
        int key = e.getKeyCode();
        if (key == KeyEvent.VK_W) w = true;
        if (key == KeyEvent.VK_S) s = true;
        if (key == KeyEvent.VK_A) a = true;
        if (key == KeyEvent.VK_D) d = true;
        if (key == KeyEvent.VK_B) isDebugMode = !isDebugMode;
        if (key == KeyEvent.VK_M && fogMinimap) fogMapVisible = !fogMapVisible;
        if (key == KeyEvent.VK_SHIFT) shift = true;
        if (key == KeyEvent.VK_F1) noClip = !noClip;
        if (key == KeyEvent.VK_EQUALS || key == KeyEvent.VK_PLUS || key == KeyEvent.VK_ADD) miniMapCellPixels = Math.min(20, miniMapCellPixels + 1);
        if (key == KeyEvent.VK_MINUS || key == KeyEvent.VK_SUBTRACT) miniMapCellPixels = Math.max(3, miniMapCellPixels - 1);
        if (key == KeyEvent.VK_ESCAPE) {
            setPaused(!isPaused);
        }
        if (isPaused && e.isControlDown() && key == KeyEvent.VK_C) {
            isGameRunning = false;
            showCursor();
            RCJMS.instance.changeView(RCJMS.instance.mainMenuView = new MainMenuView(), "main_menu");
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
                    GameView gameView = is3D() ? new GameView(customFloorsForRestart, customPortalsForRestart, customGeometryModeForRestart) : new GameView(map);
                    gameView.setFogOfWarMinimap(fogMinimap);
                    RCJMS.instance.changeView(gameView, "Raycast Me!");
                    gameThread.interrupt();
                    gameView.start();
                } catch (IOException ex) {throw new RuntimeException(ex);}
                return;
            }
            else if (is3D()) {
                mazeGenerator3D = new MazeGenerator3D(mapWidth(), mapHeight(), floorCount(), chooseSeed(), GEOMETRY_MODE);
                map3D = mazeGenerator3D.generate(MAZE_MODE);
                currentFloor = 0;
                floorPortalsAll = new PortalData[floorCount()];
                for (int f = 0; f < floorCount(); f++) floorPortalsAll[f] = mazeGenerator3D.getPortals(f);
                assert map3D != null;
                map = map3D[currentFloor];
                portals = floorPortalsAll[currentFloor];
            }
            else {
                mazeGenerator = new MazeGenerator(mapWidth(), mapHeight(), chooseSeed(), GEOMETRY_MODE);
                map = mazeGenerator.generate(MAZE_MODE);
                portals = mazeGenerator.getPortals();
            }

            playerX = playerY = 1.5;
            cameraAngle = cameraPitch = 0;
            explored = null;
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
    @Override public void mouseMoved(MouseEvent e) { handleMouseLook(e); }
    @Override public void mouseDragged(MouseEvent e) { handleMouseLook(e); }
    private void handleMouseLook(MouseEvent e) {
        if (isPaused) return;
        if (isFloorTransitioning && floorTransitionTime < FADE_OUT_DURATION + FADE_IN_DURATION) return;
        if (MOUSE_WARP_SUPPORTED && cursorRobot != null) mouseMovedWithWarp(e);
        else mouseMovedWithDelta(e);
    }
    private void mouseMovedWithWarp(MouseEvent e) {
        if (!isShowing()) return;
        Point onScreen;
        try { onScreen = getLocationOnScreen(); }
        catch (IllegalComponentStateException ex) { return; }
        int centerX = onScreen.x + getWidth() / 2;
        int centerY = onScreen.y + getHeight() / 2;
        int dx = e.getXOnScreen() - centerX, dy = e.getYOnScreen() - centerY;
        if (dx == 0 && dy == 0) return;
        cameraAngle += dx * mouseSensitivity;
        cameraPitch = Math.clamp(cameraPitch - dy * mouseSensitivity, -1.2, 1.2);
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

        cameraAngle += (x - lastMouseScreenX) * mouseSensitivity;
        cameraPitch -= (y - lastMouseScreenY) * mouseSensitivity;
        cameraPitch = Math.clamp(cameraPitch, -1.2, 1.2);

        lastMouseScreenX = x;
        lastMouseScreenY = y;
    }
    //endregion

    //region Component Overrides
    @Override public void keyTyped(KeyEvent e) {}
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
    //endregion

    //region Helpers
    private boolean is3D() { return map3D != null && map3D.length > 1; }
    private int floorCount() { return map3D == null ? 1 : map3D.length; }
    private int mapWidth() { return map[0].length; }
    private int mapHeight() { return map.length; }
    private long chooseSeed() {
        mazeSeedText = customSeed != null ? customSeed : SeedUtil.randomSeed();
        return SeedUtil.toLong(mazeSeedText);
    }
    private double[] buildCameraXCache() {
        double[] values = new double[renderWidth];
        for (int x = 0; x < values.length; x++) values[x] = 2.0 * x / (double) values.length - 1.0;
        return values;
    }
    private void hideCursor() { setCursor(invisibleCursor); hasLastMousePos = false; }
    private void showCursor() { setCursor(Cursor.getDefaultCursor()); hasLastMousePos = false; }
    private void setPaused(boolean paused) {
        if (paused == isPaused) return;
        isPaused = paused;
        if (paused) {
            pauseStartTime = System.currentTimeMillis();
            showCursor();
        } else {
            pausedTime += System.currentTimeMillis() - pauseStartTime;
            hideCursor();
        }
    }
    private void releaseInput() {
        w = a = s = d = shift = false;
        hasLastMousePos = false;
    }
    private void onFocusLost() {
        releaseInput();
        if (isGameRunning) setPaused(true);
        repaint();
    }
    private void onFocusGained() {
        releaseInput();
        requestFocusInWindow();
        repaint();
    }
    @Override public void addNotify() {
        super.addNotify();
        if (focusWatcher == null) {
            focusWatcher = new FocusAdapter() {
                @Override public void focusLost(FocusEvent e) { if (!e.isTemporary()) onFocusLost(); }
                @Override public void focusGained(FocusEvent e) { onFocusGained(); }
            };
            addFocusListener(focusWatcher);
        }
        Window window = SwingUtilities.getWindowAncestor(this);
        if (window != null && watchedWindow != window) {
            if (watchedWindow != null) watchedWindow.removeWindowListener(windowWatcher);
            watchedWindow = window;
            windowWatcher = new WindowAdapter() {
                @Override public void windowDeactivated(WindowEvent e) { onFocusLost(); }
                @Override public void windowIconified(WindowEvent e) { onFocusLost(); }
                @Override public void windowActivated(WindowEvent e) { onFocusGained(); }
                @Override public void windowDeiconified(WindowEvent e) { onFocusGained(); }
            };
            window.addWindowListener(windowWatcher);
        }
        SwingUtilities.invokeLater(this::requestFocusInWindow);
    }
    @Override public void removeNotify() {
        if (watchedWindow != null && windowWatcher != null) watchedWindow.removeWindowListener(windowWatcher);
        watchedWindow = null; windowWatcher = null;
        super.removeNotify();
    }
    public void setFogOfWarMinimap(boolean enabled) { fogMinimap = enabled; explored = null; fogMapVisible = true; }
    public static double getRenderScale() { return renderScale; }
    public static void setRenderScale(double scale) { renderScale = Math.clamp(scale, 0.1, 1.0); }
    public static boolean isVSyncEnabled() { return vsync; }
    public static void setVSyncEnabled(boolean enabled) { vsync = enabled; }
    public static int getRenderEngine() { return renderEngine; }
    public static void setRenderEngine(int engine) { renderEngine = Math.clamp(engine, ENGINE_DDA, ENGINE_RAY_CASTING); }
    public static int getRenderEngineQuality() { return renderEngineQuality; }
    public static void setRenderEngineQuality(int quality) { renderEngineQuality = Math.clamp(quality, 0, 2); }
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