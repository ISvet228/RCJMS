package Project.Game;
import Project.EngineCreator.*;
import Project.ThemeCreator.*;
import Project.CreatorKit.*;
import Project.MainMenu.MainMenuView;
import Project.RCJMS;
import Project.TextureEditor.TextureEditorView;
import Project.Victory.VictoryView;
import Project.Helpers.*;
import static Project.Game.RenderEngineConfig.*;

import javax.swing.*; //Frame Library
import java.awt.*; //Graphics Library
import java.awt.event.*; //Input Library
import java.awt.image.*; //Buffer Library
import java.awt.geom.Point2D;
import java.io.IOException;

public class GameView extends JPanel implements Runnable, KeyListener, MouseMotionListener, MouseListener, MouseWheelListener {
    //region Variables
    //region Maze Configuration
    private static MazeGenerator.FinishMode MAZE_MODE = MazeGenerator.FinishMode.RANDOM_EDGE;
    private static boolean MAZE_3D = false;
    private static int MAZE_FLOORS = 1;
    private String customSeed;
    private String mazeSeedText = "";
    private final boolean isCustomMap;
    private int requestedWidth, requestedHeight;
    private int[][][] customFloorsForRestart;
    private PortalData[] customPortalsForRestart;
    private int customGeometryModeForRestart;
    //endregion
    //region Maze State
    private MazeGenerator mazeGenerator;
    private MazeGenerator3D mazeGenerator3D;
    private int[][][] map3D;
    private PortalData[] floorPortalsAll;
    private int[][] map;
    private PortalData portals = new PortalData();
    private int currentFloor = 0;
    private boolean onStairsLastFrame = false;
    //endregion
    //region Player State
    private double playerX = 1.5;
    private double playerY = 1.5;
    private static final double PLAYER_RADIUS = 0.01;
    private double cameraAngle = 0;
    private double cameraPitch = 0;
    private boolean w, a, s, d, shift;
    private final double moveSpeed = 1.5;
    private final double runSpeed = 3;
    private final double mouseSensitivity = 0.003;
    private boolean noClip = false;
    private static final double CAMERA_HEIGHT = 0.5;
    private double cameraHeight = CAMERA_HEIGHT;
    private double bobPhase, bobAmount;
    //endregion
    //region Input State
    private final Cursor invisibleCursor;
    private Robot cursorRobot;
    private boolean hasLastMousePos = false;
    //Wayland fallback delta-based mouse-look
    private int lastMouseScreenX, lastMouseScreenY;
    //Wayland fallback cursor position
    private static final int EDGE_MARGIN = 96, MAX_LOOK_STEP = 80;
    private FocusListener focusWatcher;
    private Window watchedWindow;
    private WindowListener windowWatcher;
    //endregion
    //region Render Buffers
    private final BufferedImage bufferedImage;
    private BufferedImage renderImage;
    private final int[] pixels;
    private int[] renderPixels;
    //PUXELS
    private int renderWidth, renderHeight;
    private final int renderThreadCount = Runtime.getRuntime().availableProcessors();
    //fck limits
    private final RenderWorkers renderWorkers = new RenderWorkers(renderThreadCount);
    private double[] cameraXCache;
    private final RenderFrame renderFrame = new RenderFrame();
    //endregion
    //region Render Settings
    private static volatile double renderScale = 1.0;
    private static volatile boolean vsync = false;
    private static volatile boolean openClAcceleration = true;
    private static volatile String renderEngineId = "";
    private static volatile int renderQualitySetting = 1;
    private final AutoRenderController autoRender = new AutoRenderController();
    private RenderEngine mapEngine;
    private String mapEngineId;
    //endregion
    //region Gameplay Options
    private static volatile boolean doomMovement = SaveData.load().doomMovement;
    private static volatile boolean noCeiling = SaveData.load().noCeiling;
    private static volatile boolean textMode = SaveData.load().textMode;
    private static final double DOOM_UNITS_PER_CELL = 128, DOOM_TICS_PER_SECOND = 35, DOOM_BOB_CAP = 16;
    private static final double BOB_RATE = 2 * Math.PI * (8192 / 20) * DOOM_TICS_PER_SECOND / 8192;
    //endregion
    //region Textures
    private final RuntimeTexture wallTexture = TextureEditorView.readRuntimeTexture(AppPaths.WALL_TEXTURE_FILE, TextureEditorView.MAX_TEXTURE_SIZE, TextureEditorView.MAX_TEXTURE_SIZE);
    private final RuntimeTexture floorTexture = TextureEditorView.readRuntimeTexture(AppPaths.FLOOR_TEXTURE_FILE, TextureEditorView.MAX_TEXTURE_SIZE, TextureEditorView.MAX_TEXTURE_SIZE);
    private final RuntimeTexture ceilingTexture = TextureEditorView.readRuntimeTexture(AppPaths.CEILING_TEXTURE_FILE, TextureEditorView.MAX_TEXTURE_SIZE, TextureEditorView.MAX_TEXTURE_SIZE);
    private final RuntimeTexture finishTexture = TextureEditorView.readRuntimeTexture(AppPaths.FINISH_TEXTURE_FILE, TextureEditorView.MAX_TEXTURE_SIZE, TextureEditorView.MAX_FINISH_HEIGHT);
    //endregion
    //region Mini Map State
    public static int MINI_MAP_WIDTH = 180, MINI_MAP_HEIGHT = 180;
    private static final double MINI_MAP_MIN_CELL_PIXELS = 4.0;
    private static volatile int miniMapCellPixels = 6;
    //endregion
    //region Fonts
    private final Font fpsFont = new Font(Font.MONOSPACED, Font.BOLD, 20);
    private final Font openClFont = new Font(Font.MONOSPACED, Font.BOLD, 16);
    private final Font timerFont = new Font(Font.MONOSPACED, Font.BOLD, 24);
    private static final Font BADGE_FONT = new Font(Font.MONOSPACED, Font.BOLD, 16);
    private static final Font BIG_SANS_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 80);
    private static final Font SMALL_SANS_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 20);
    private static final Font BIG_MONO_FONT = new Font(Font.MONOSPACED, Font.BOLD, 56);
    private static final Font FOG_HINT_FONT = new Font(Font.MONOSPACED, Font.BOLD, 12);
    //endregion
    //region Fog Of War State
    private static final double FOG_REVEAL_RADIUS = 4.5, FOG_REVEAL_SPEED = 3.0, FOG_RAY_STEP = 0.08;
    private static final int FOG_RAY_COUNT = 120;
    private boolean fogMinimap = false;
    private boolean fogMapVisible = true;
    private float[][][] explored;
    private int[][] fogVisitStamp;
    private int fogFrame = 0;
    //endregion
    //region Text Mode State
    private final StringBuilder textInput = new StringBuilder();
    private String textStatus = "type f, b, l, r (tiles) or tl, tr (quarter turns), e.g. f3 tr f2";
    private BufferedImage textScreen;
    private int[][] textScreenMap;
    private boolean textDirty = true;
    private static final java.util.regex.Pattern TEXT_MOVE = java.util.regex.Pattern.compile("(t[lr]|[fblr])(\\d*)");
    //endregion
    //region Floor Transition State
    private static final double STAIR_ANIMATION_SPEED = 1.0;
    private static final double STEP_DURATION = 1.6;
    private static final double CROSS_DURATION = 1.1;
    private static final double STEP_HEIGHT = 0.3;
    private static final double STEP_LEAN = 0.12;
    private static final double STEP_WALK = 0.3;
    private static final int STAIR_STEPS = 5;
    private static final double FOOTFALL = 0.03;
    private static final double LAND_SHAKE = 0.03;
    private static final double CAPTION_ROLL_DURATION = 0.5;
    private static final double CAPTION_HOLD_DURATION = 0.9;
    private static final double CAPTION_FADE_DURATION = 0.4;
    private boolean isFloorTransitioning = false, floorSwitchApplied = false;
    private double floorTransitionTime = 0;
    private int transitionFromFloor = 0, transitionToFloor = 0;
    private int transitionDirection = 0;
    private BufferedImage transitionSnapshot;
    private double climbStartX, climbStartY;
    //endregion
    //region Loading State
    private volatile boolean renderPrepPending = true;
    private int preparedFloor = -1;
    private volatile Thread busyWorker;
    private volatile Runnable pendingWork;
    private volatile LevelSpec pendingLevel;
    private final Progress busyProgress = new Progress();
    private volatile long busyStartNanos;
    private static final int BUSY_FRAME_MS = 16;
    //endregion
    //region Game State
    //BOBR KURSOR JA PERDOLE
    private Thread gameThread;
    private boolean isGameRunning = false;
    private boolean isPaused = false, isDebugMode = false;
    private long gameStartTime = System.currentTimeMillis();
    private long pauseStartTime = 0, pausedTime = 0, elapsedSeconds;
    //endregion
    //region FPS Counter State
    private int frameCounter = 0, currentFps = 0;
    private long fpsWindowStart = System.currentTimeMillis();
    //endregion
    //region Console State
    private final DevConsole console = new DevConsole();
    private boolean consoleReady = false;
    //endregion
    //region Diagnostics State
    private boolean verboseLog;
    private String detailKey;
    private long detailFrames, detailNanos;
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
        addMouseListener(this);
        addMouseWheelListener(this);

        try { cursorRobot = new Robot(); }
        catch (Exception exception) { exception.printStackTrace(); }

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
        addMouseListener(this);
        addMouseWheelListener(this);

        try { cursorRobot = new Robot(); }
        catch (Exception exception) { exception.printStackTrace(); }

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
        addMouseListener(this);
        addMouseWheelListener(this);

        try {cursorRobot = new Robot();}
        catch (Exception exception) {exception.printStackTrace();}

        Toolkit toolkit = Toolkit.getDefaultToolkit();
        invisibleCursor = toolkit.createCustomCursor(toolkit.createImage(new byte[0]), new Point(0, 0), "hidden");
        hideCursor();
    }
    //endregion

    //region Game Loop
    //region Lifecycle
    public void start() {
        if (isGameRunning) return;
        isGameRunning = true;
        if (!consoleReady) {
            consoleReady = true;
            registerConsoleCommands();
            Project.OpenCl.GpuRuntime.setLogSink(line -> console.log(line.contains("disabled") || line.contains("unavailable") ? DevConsole.Level.WARN : DevConsole.Level.INFO, line));
            console.log(DevConsole.Level.INFO, "developer console: press ~ and type --help");
            console.log(DevConsole.Level.INFO, "engine " + renderEngineId + ", OpenCL acceleration " + (openClAcceleration ? "ON" : "OFF"));
        }
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
                    if (busyWorker != null && !busyWorker.isAlive()) {
                        busyWorker = null;
                        autoRender.reset();
                        lastFrameTime = System.nanoTime();
                        onLoadingFinished();
                    }
                    if (busyWorker == null) startNextBusyWork();
                    long currentFrameTime = System.nanoTime();
                    double frameSeconds = (currentFrameTime - lastFrameTime) / 1_000_000_000.0;
                    lastFrameTime = currentFrameTime;
                    double deltaTime = Math.min(frameSeconds, 0.1);
                    if (busyWorker != null) {
                        drawBusyOverlay();
                    }
                    else {
                        if (!isPaused) {
                            update(deltaTime);
                            autoRender.update(frameSeconds);
                        }
                        render();
                    }
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
                        Thread.sleep(busyWorker != null ? BUSY_FRAME_MS : 1);
                        nextVSyncTime = System.nanoTime();
                    }
                    trackFps();
                }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); break; }
                catch (Throwable exception) {
                    if (!errorReported) { errorReported = true; exception.printStackTrace(); }
                    try { Thread.sleep(5); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                    lastFrameTime = System.nanoTime();
                    nextVSyncTime = lastFrameTime;
                }
            }
        }
        finally { renderWorkers.shutdown(); }
    }
    private void setPaused(boolean paused) {
        if (paused == isPaused) return;
        if (paused && isBusy()) return;
        isPaused = paused;
        console.log(DevConsole.Level.INFO, paused ? "paused" : "resumed");
        if (paused) {
            pauseStartTime = System.currentTimeMillis();
            showCursor();
        } else {
            pausedTime += System.currentTimeMillis() - pauseStartTime;
            hideCursor();
        }
    }
    @Override public void addNotify() {
        super.addNotify();
        if (focusWatcher == null) {
            focusWatcher = new FocusAdapter() {
                @Override public void focusLost(FocusEvent focusEvent) { if (!focusEvent.isTemporary()) onFocusLost(); }
                @Override public void focusGained(FocusEvent focusEvent) { onFocusGained(); }
            };
            addFocusListener(focusWatcher);
        }
        Window window = SwingUtilities.getWindowAncestor(this);
        if (window != null && watchedWindow != window) {
            if (watchedWindow != null) watchedWindow.removeWindowListener(windowWatcher);
            watchedWindow = window;
            windowWatcher = new WindowAdapter() {
                @Override public void windowDeactivated(WindowEvent windowEvent) { onFocusLost(); }
                @Override public void windowIconified(WindowEvent windowEvent) { onFocusLost(); }
                @Override public void windowActivated(WindowEvent windowEvent) { onFocusGained(); }
                @Override public void windowDeiconified(WindowEvent windowEvent) { onFocusGained(); }
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
    //endregion
    //region Frame Pacing
    private long getDisplayRefreshIntervalNanos() {
        int refreshRate = 60;
        try {
            GraphicsConfiguration graphicsConfiguration = getGraphicsConfiguration();
            if (graphicsConfiguration != null && graphicsConfiguration.getDevice() != null) {
                int rate = graphicsConfiguration.getDevice().getDisplayMode().getRefreshRate();
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
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); return; }
            }
            else Thread.onSpinWait();
        }
    }
    //endregion
    //endregion

    //region Input
    //region Keyboard
    @Override public void keyPressed(KeyEvent keyEvent) {
        int key = keyEvent.getKeyCode();
        if (key == KeyEvent.VK_BACK_QUOTE || DevConsole.isToggleChar(keyEvent.getKeyChar())) {
            toggleConsole();
            return;
        }
        if (console.isOpen()) {
            consoleKey(keyEvent);
            return;
        }
        boolean loading = isBusy();
        if (loading && (key == KeyEvent.VK_ESCAPE || key == KeyEvent.VK_R)) return;
        if (textMode && !isPaused && key != KeyEvent.VK_ESCAPE) {
            textKey(keyEvent);
            return;
        }
        if (key == KeyEvent.VK_W) w = !loading;
        if (key == KeyEvent.VK_S) s = !loading;
        if (key == KeyEvent.VK_A) a = !loading;
        if (key == KeyEvent.VK_D) d = !loading;
        if (key == KeyEvent.VK_B) isDebugMode = !isDebugMode;
        if (key == KeyEvent.VK_M && fogMinimap) fogMapVisible = !fogMapVisible;
        if (key == KeyEvent.VK_SHIFT) shift = !loading;
        if (key == KeyEvent.VK_F1) noClip = !noClip;
        if (key == KeyEvent.VK_EQUALS || key == KeyEvent.VK_PLUS || key == KeyEvent.VK_ADD) miniMapCellPixels = Math.min(20, miniMapCellPixels + 1);
        if (key == KeyEvent.VK_MINUS || key == KeyEvent.VK_SUBTRACT) miniMapCellPixels = Math.max(3, miniMapCellPixels - 1);
        if (key == KeyEvent.VK_ESCAPE) {
            setPaused(!isPaused);
        }
        if (isPaused && keyEvent.isControlDown() && key == KeyEvent.VK_C) {
            isGameRunning = false;
            showCursor();
            RCJMS.instance.changeView(RCJMS.instance.mainMenuView = new MainMenuView(), "main_menu");
            gameThread.interrupt();
            return;
        }
        if (key == KeyEvent.VK_R) restartGame();
    }
    @Override public void keyReleased(KeyEvent keyEvent) {
        int key = keyEvent.getKeyCode();
        if (key == KeyEvent.VK_W) w = false;
        if (key == KeyEvent.VK_S) s = false;
        if (key == KeyEvent.VK_A) a = false;
        if (key == KeyEvent.VK_D) d = false;
        if (key == KeyEvent.VK_SHIFT) shift = false;
    }
    @Override public void keyTyped(KeyEvent keyEvent) {
        if (console.isOpen()) {
            console.typed(keyEvent.getKeyChar());
            repaint();
        }
        else if (textMode && !isPaused) {
            char c = Character.toLowerCase(keyEvent.getKeyChar());
            if ((Character.isLetterOrDigit(c) || c == ' ') && textInput.length() < 60) {
                textInput.append(c);
                textDirty = true;
                repaint();
            }
        }
    }
    //endregion
    //region Mouse
    @Override public void mouseMoved(MouseEvent mouseEvent) { handleMouseLook(mouseEvent); }
    @Override public void mouseDragged(MouseEvent mouseEvent) {
        if (console.isOpen()) {
            console.mouseDragged(mouseEvent.getX(), mouseEvent.getY());
            repaint();
            return;
        }
        handleMouseLook(mouseEvent);
    }
    @Override public void mousePressed(MouseEvent mouseEvent) {
        if (!console.isOpen()) return;
        console.mousePressed(mouseEvent.getX(), mouseEvent.getY());
        repaint();
    }
    @Override public void mouseReleased(MouseEvent mouseEvent) {
        if (!console.isOpen()) return;
        console.mouseReleased();
        repaint();
    }
    @Override public void mouseClicked(MouseEvent mouseEvent) {}
    @Override public void mouseEntered(MouseEvent mouseEvent) {}
    @Override public void mouseExited(MouseEvent mouseEvent) {}
    @Override public void mouseWheelMoved(MouseWheelEvent mouseWheelEvent) {
        if (!console.isOpen()) return;
        console.wheel(mouseWheelEvent.getWheelRotation());
        repaint();
    }
    private void handleMouseLook(MouseEvent mouseEvent) {
        if (textMode || isPaused || isBusy() || console.isOpen()) return;
        if (transitionLocksControls()) return;
        if (!isShowing()) return;
        Point origin;
        try { origin = getLocationOnScreen(); }
        catch (IllegalComponentStateException exception) { return; }

        int x = mouseEvent.getXOnScreen(), y = mouseEvent.getYOnScreen();
        if (!hasLastMousePos) { setLastMouse(x, y); return; }

        int dx = x - lastMouseScreenX, dy = y - lastMouseScreenY;
        setLastMouse(x, y);
        if (Math.abs(dx) > MAX_LOOK_STEP || Math.abs(dy) > MAX_LOOK_STEP) return;

        cameraAngle += dx * mouseSensitivity;
        cameraPitch = Math.clamp(cameraPitch - dy * mouseSensitivity, -1.2, 1.2);

        if (cursorRobot != null && nearViewEdge(x, y, origin)) recentreCursor(origin);
    }
    private void setLastMouse(int x, int y) {
        lastMouseScreenX = x;
        lastMouseScreenY = y;
        hasLastMousePos = true;
    }
    private boolean nearViewEdge(int x, int y, Point origin) {
        int left = x - origin.x, top = y - origin.y;
        return left < EDGE_MARGIN || top < EDGE_MARGIN || getWidth() - left < EDGE_MARGIN || getHeight() - top < EDGE_MARGIN;
    }
    private void recentreCursor(Point origin) {
        int cx = origin.x + getWidth() / 2, cy = origin.y + getHeight() / 2;
        try {
            cursorRobot.mouseMove(cx, cy);
            setLastMouse(cx, cy);
        }
        catch (RuntimeException exception) { cursorRobot = null; }
    }
    private void hideCursor() {
        if (console.isOpen()) { showCursor(); return; }
        setCursor(invisibleCursor);
        hasLastMousePos = false;
    }
    private void showCursor() { setCursor(Cursor.getDefaultCursor()); hasLastMousePos = false; }
    //endregion
    //region Focus
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
    private void releaseInput() {
        w = a = s = d = shift = false;
        hasLastMousePos = false;
    }
    //endregion
    //endregion

    //region Player Movement
    private void update(double deltaTiime) {
        if (isFloorTransitioning) {
            double speed = Math.max(0.05, STAIR_ANIMATION_SPEED);
            floorTransitionTime += deltaTiime * speed;

            if (!floorSwitchApplied && floorTransitionTime >= STEP_DURATION) {
                currentFloor = transitionToFloor;
                map = map3D[currentFloor];
                portals = floorPortalsAll[currentFloor];
                floorSwitchApplied = true;
            }

            if (floorTransitionTime >= transitionTotalDuration()) endFloorTransition();
            advanceClimb();
            if (transitionLocksControls()) return;
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

        double moveStartX = playerX, moveStartY = playerY;
        movementX = nextX - playerX;
        movementY = nextY - playerY;
        double moved = Math.hypot(movementX, movementY);
        double momentumUnits = deltaTiime > 0 ? moved / deltaTiime * DOOM_UNITS_PER_CELL / DOOM_TICS_PER_SECOND : 0;
        double bobTarget = Math.min(DOOM_BOB_CAP, momentumUnits * momentumUnits / 4) / DOOM_BOB_CAP;
        bobAmount += (bobTarget - bobAmount) * Math.min(1, deltaTiime * 10);
        bobPhase = (bobPhase + deltaTiime * BOB_RATE) % (2 * Math.PI);
        nextX = Math.clamp(nextX, 0.0001, mapWidth() - 0.0001);
        nextY = Math.clamp(nextY, 0.0001, mapHeight() - 0.0001);

        if (noClip) {
            playerX = nextX;
            playerY = nextY;
        }
        else {
            double totalDX = nextX - playerX, totalDY = nextY - playerY;
            int substeps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(totalDX), Math.abs(totalDY)) / 0.05));
            double stepDX = totalDX / substeps, stepDY = totalDY / substeps;
            for (int i = 0; i < substeps; i++) {
                double tryX = playerX + stepDX;
                if (!isPlayerBlocked(map, tryX, playerY)) playerX = tryX;
                double tryY = playerY + stepDY;
                if (!isPlayerBlocked(map, playerX, tryY)) playerY = tryY;
            }
            pushPlayerOutOfWalls(map);
        }

        playerX = Math.clamp(playerX, 0.0001, mapWidth() - 0.0001);
        playerY = Math.clamp(playerY, 0.0001, mapHeight() - 0.0001);

        double moveLength = Math.hypot(movementX, movementY);
        int portalCellY = Math.clamp((int) moveStartY, 0, mapHeight() - 1);
        int portalCellX = Math.clamp((int) moveStartX, 0, mapWidth() - 1);
        if (moveLength > 1e-12 && map[portalCellY][portalCellX] == MazeGenerator.PORTAL) {
            PortalData.Portal portal = portals.get(portalCellX, portalCellY);
            if (portal != null) {
                double moveDirX = movementX / moveLength, moveDirY = movementY / moveLength;
                if (PortalData.entersFromCorrectSide(portal, moveDirX, moveDirY)
                        && PortalData.crossesWindow(portal, moveStartX, moveStartY, playerX, playerY)) {
                    double[] warped = PortalData.teleport(portal, playerX, playerY);
                    if (map3D != null && portal.linkedFloor >= 0 && portal.linkedFloor != currentFloor && portal.linkedFloor < map3D.length) {
                        int fromFloor = currentFloor;
                        currentFloor = portal.linkedFloor;
                        map = map3D[currentFloor];
                        portals = floorPortalsAll[currentFloor];
                        if (!isFloorTransitioning) startPortalTransition(fromFloor, currentFloor);
                    }
                    playerX = Math.clamp(warped[0], 0.0001, mapWidth() - 0.0001);
                    playerY = Math.clamp(warped[1], 0.0001, mapHeight() - 0.0001);
                    if (!noClip) pushPlayerOutOfWalls(map);
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

        if (fogMinimap || textMode) updateFog(deltaTiime);
        if (is3D()) handleStairs(cellType);
    }
    private static boolean isSolidForPlayer(int[][] m, int x, int y) {
        if (y < 0 || x < 0 || y >= m.length || x >= m[0].length) return true;
        return m[y][x] == 1;
    }
    private static boolean isPlayerBlocked(int[][] m, double x, double y) {
        final double r = PLAYER_RADIUS;
        int minX = (int) Math.floor(x - r), maxX = (int) Math.floor(x + r);
        int minY = (int) Math.floor(y - r), maxY = (int) Math.floor(y + r);
        for (int cy = minY; cy <= maxY; cy++) {
            for (int cx = minX; cx <= maxX; cx++) {
                if (!isSolidForPlayer(m, cx, cy)) continue;
                double nearX = Math.clamp(x, cx, cx + 1.0), nearY = Math.clamp(y, cy, cy + 1.0);
                double ddx = x - nearX, ddy = y - nearY;
                if (ddx * ddx + ddy * ddy < r * r) return true;
            }
        }
        return false;
    }
    private void pushPlayerOutOfWalls(int[][] m) {
        final double r = PLAYER_RADIUS;
        for (int iter = 0; iter < 4; iter++) {
            boolean moved = false;
            int minX = (int) Math.floor(playerX - r), maxX = (int) Math.floor(playerX + r);
            int minY = (int) Math.floor(playerY - r), maxY = (int) Math.floor(playerY + r);
            for (int cy = minY; cy <= maxY; cy++) {
                for (int cx = minX; cx <= maxX; cx++) {
                    if (!isSolidForPlayer(m, cx, cy)) continue;
                    double nearX = Math.clamp(playerX, cx, cx + 1.0), nearY = Math.clamp(playerY, cy, cy + 1.0);
                    double ddx = playerX - nearX, ddy = playerY - nearY;
                    double d2 = ddx * ddx + ddy * ddy;
                    if (d2 >= r * r) continue;
                    if (d2 > 1e-12) {
                        double d = Math.sqrt(d2), push = (r - d) / d;
                        playerX += ddx * push;
                        playerY += ddy * push;
                    }
                    else {
                        double left = playerX - cx, right = cx + 1.0 - playerX;
                        double up = playerY - cy, down = cy + 1.0 - playerY;
                        double best = Math.min(Math.min(left, right), Math.min(up, down));
                        if (best == left) playerX = cx - r;
                        else if (best == right) playerX = cx + 1.0 + r;
                        else if (best == up) playerY = cy - r;
                        else playerY = cy + 1.0 + r;
                    }
                    moved = true;
                }
            }
            if (!moved) break;
        }
    }
    private double viewBob() {
        return doomMovement ? (DOOM_BOB_CAP / 2.0) / DOOM_UNITS_PER_CELL * bobAmount * Math.sin(bobPhase) : 0;
    }
    //endregion

    //region Rendering
    //region Frame Rendering
    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
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

        Graphics2D g2d = (Graphics2D) graphics.create();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, panelWidth, panelHeight);
        g2d.drawImage(bufferedImage, drawX, drawY, drawWidth, drawHeight, null);
        if (console.isOpen()) console.draw(g2d, panelWidth, panelHeight);
        g2d.dispose();
    }
    private void render() {
        if (textMode) {
            renderText();
            return;
        }
        applyRenderQuality();
        renderSelectedEngine();
        drawTimer();
        int hudLine = 0;
        if (!isCustomMap) drawSeedBadge(hudLine++);
        if (GEOMETRY_MODE == MazeGenerator.GeometryMode.WRONG) drawGeometryBadge(hudLine++);
        if (GEOMETRY_MODE == MazeGenerator.GeometryMode.LOOPED) drawGeometryBadge(hudLine++, NSLocalizedString.get("looped_geometry"));
        if (is3D()) drawFloorIndicator(hudLine);
        if (isDebugMode && !isPaused) { drawMiniMap(); drawFpsCounter(); drawOpenClBadge(); }
        if (fogMinimap && fogMapVisible) drawFogMiniMap();
        if (isPaused) drawPauseMenu();
        if (isFloorTransitioning) drawFloorTransitionEffects();
    }
    private void syncRenderer(RenderFrame renderFrame) {
        renderFrame.bufferedImage = bufferedImage;
        renderFrame.cameraAngle = cameraAngle + landingShake();
        renderFrame.cameraHeight = Math.clamp(cameraHeight + viewBob() + stepHeightOffset(), 0.05, 0.95);
        renderFrame.cameraPitch = cameraPitch + stepPitchOffset();
        renderFrame.skyCeiling = ceilingFreeActive();
        renderFrame.skyTime = System.nanoTime() / 1e9;
        renderFrame.cameraXCache = cameraXCache;
        renderFrame.ceilingTexture = ceilingTexture;
        renderFrame.finishTexture = finishTexture;
        renderFrame.floorPortalsAll = floorPortalsAll;
        renderFrame.floorTexture = floorTexture;
        renderFrame.outputWidth = RCJMS.GAME_WIDTH;
        renderFrame.outputHeight = RCJMS.GAME_HEIGHT;
        renderFrame.quality = RenderEngineConfig.renderEngineQuality;
        renderFrame.geometry = GEOMETRY_MODE;
        renderFrame.curvature = HYPERBOLIC_CURVATURE;
        renderFrame.map = map;
        renderFrame.map3D = map3D;
        renderFrame.pixels = pixels;
        renderFrame.playerX = playerX;
        renderFrame.playerY = playerY;
        renderFrame.portals = portals;
        renderFrame.renderHeight = renderHeight;
        renderFrame.renderImage = renderImage;
        renderFrame.renderPixels = renderPixels;
        renderFrame.renderWidth = renderWidth;
        renderFrame.renderWorkers = renderWorkers;
        renderFrame.wallTexture = wallTexture;
    }
    private void renderSelectedEngine() {
        syncRenderer(renderFrame);
        RenderEngine engine = resolveRenderEngine();
        if (!verboseLog) {
            engine.render(renderFrame);
            return;
        }
        logEngineState(engine);
        long start = System.nanoTime();
        engine.render(renderFrame);
        detailNanos += System.nanoTime() - start;
        if (++detailFrames % 120 == 0) {
            console.log(DevConsole.Level.INFO, String.format("[detail] %s: %.2f ms per frame over the last 120 frames, %dx%d",
                    engine.id(), detailNanos / 120.0 / 1e6, renderFrame.renderWidth, renderFrame.renderHeight));
            detailNanos = 0;
        }
    }
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
    private boolean ceilingFreeActive() {
        return noCeiling && GEOMETRY_MODE != MazeGenerator.GeometryMode.WRONG;
    }
    //endregion
    //region Render Engine
    public RenderEngine resolveRenderEngine() {
        String wanted = renderEngineId;
        if (mapEngine != null && wanted.equals(mapEngineId)) return mapEngine;
        int[][][] floors = map3D != null ? map3D : new int[][][]{map};
        int width = 0, height = 0;
        for (int[][] floor : floors) { width = Math.max(width, floor[0].length); height = Math.max(height, floor.length); }
        RenderEngine engine = RenderEngines.forMapSize(wanted, width, height);
        if (engine == null) throw new NoRenderEngineException(width, height);
        mapEngine = engine;
        mapEngineId = wanted;
        return engine;
    }
    public static int getRenderEngine() { return RenderEngines.indexOf(renderEngineId); }
    public static void setRenderEngine(int index) { renderEngineId = RenderEngines.idAt(index); }
    public static String getRenderEngineId() { return renderEngineId; }
    public static void setRenderEngineId(String id) {
        RenderEngine engine = RenderEngines.find(id);
        if (engine != null) renderEngineId = engine.id();
    }
    private static RenderEngine currentEngine() { return RenderEngines.find(renderEngineId); }
    private static RenderEngine findEngine(String name) {
        for (RenderEngine renderEngine : RenderEngines.all())
            if (renderEngine.id().equalsIgnoreCase(name) || renderEngine.displayName().equalsIgnoreCase(name)) return renderEngine;
        return null;
    }
    private static String nameOf(RenderEngine engine) {
        return engine == null ? "none" : engine.displayName();
    }
    private void reportGpuState(RenderEngine engine) {
        if (engine == null || !openClAcceleration || !engine.hasOpenCL()) return;
        Project.OpenCl.GpuRuntime.Health health = Project.OpenCl.GpuRuntime.healthFor(engine.openClKey());
        if (health == Project.OpenCl.GpuRuntime.Health.UNAVAILABLE)
            console.log(DevConsole.Level.WARN, "GPU is not usable: " + Project.OpenCl.GpuRuntime.reasonFor(engine.openClKey()));
        else if (health == Project.OpenCl.GpuRuntime.Health.PROBING)
            console.log(DevConsole.Level.INFO, "GPU is still being checked");
    }
    private void logEngineState(RenderEngine engine) {
        String key = engine.id() + '|' + GEOMETRY_MODE + '|' + openClAcceleration + '|' + noCeiling + '|' + textMode
                + '|' + renderFrame.renderWidth + 'x' + renderFrame.renderHeight + '|' + renderFrame.quality
                + '|' + (renderFrame.map3D == null ? 1 : renderFrame.map3D.length) + '|' + mapWidth() + 'x' + mapHeight();
        if (key.equals(detailKey)) return;
        detailKey = key;
        console.log(DevConsole.Level.INFO, "[detail] engine " + engine.id() + " (" + engine.displayName() + ")"
                + ", hasOpenCL " + engine.hasOpenCL() + ", OpenCL setting " + (openClAcceleration ? "ON" : "OFF")
                + ", geometry " + GEOMETRY_MODE + ", map " + mapWidth() + "x" + mapHeight()
                + ", floors " + (renderFrame.map3D == null ? 1 : renderFrame.map3D.length)
                + ", render " + renderFrame.renderWidth + "x" + renderFrame.renderHeight
                + ", quality " + renderFrame.quality + ", noCeiling " + noCeiling + ", text mode " + textMode
                + ", sky " + renderFrame.skyCeiling);
    }
    //endregion
    //region Render Quality
    public static double getRenderScale() { return renderScale; }
    public static void setRenderScale(double scale) { renderScale = Math.clamp(scale, 0.1, 1.0); }
    public static int getRenderEngineQuality() { return renderQualitySetting; }
    public static void setRenderEngineQuality(int quality) {
        renderQualitySetting = Math.clamp(quality, 0, RenderEngineConfig.QUALITY_AUTO);
        if (renderQualitySetting != RenderEngineConfig.QUALITY_AUTO) RenderEngineConfig.renderEngineQuality = renderQualitySetting;
    }
    private void applyRenderQuality() {
        double scale = renderScale;
        if (renderQualitySetting == RenderEngineConfig.QUALITY_AUTO) {
            double level = autoRender.level();
            RenderEngineConfig.renderEngineQuality = AutoRenderController.tierFor(level);
            scale = AutoRenderController.scaleFor(level);
        }
        resizeRenderBuffers(scale);
    }
    private void resizeRenderBuffers(double scale) {
        int w = Math.max(1, (int) Math.round(RCJMS.GAME_WIDTH * scale));
        int h = Math.max(1, (int) Math.round(RCJMS.GAME_HEIGHT * scale));
        if (w == renderWidth && h == renderHeight) return;
        renderWidth = w;
        renderHeight = h;
        renderImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        renderPixels = ((DataBufferInt) renderImage.getRaster().getDataBuffer()).getData();
        cameraXCache = buildCameraXCache();
    }
    private double[] buildCameraXCache() {
        double[] values = new double[renderWidth];
        for (int x = 0; x < values.length; x++) values[x] = 2.0 * x / (double) values.length - 1.0;
        return values;
    }
    public static boolean isVSyncEnabled() { return vsync; }
    public static void setVSyncEnabled(boolean enabled) { vsync = enabled; }
    public static boolean isOpenClAcceleration() { return openClAcceleration; }
    public static void setOpenClAcceleration(boolean enabled) {
        openClAcceleration = enabled;
        Project.OpenCl.GpuRuntime.setEnabled(enabled);
    }
    static boolean resolveOpenClForSwap(boolean supported, Boolean requested, boolean savedOpenCl) {
        boolean wanted = requested != null ? requested : savedOpenCl;
        return wanted && supported;
    }
    //endregion
    //region Showcase Frame
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
    //endregion
    //endregion

    //region Settings
    //region Gameplay Options
    public static boolean isDoomMovement() { return doomMovement; }
    public static void setDoomMovement(boolean enabled) { doomMovement = enabled; }
    public static boolean isNoCeiling() { return noCeiling; }
    public static void setNoCeiling(boolean enabled) { noCeiling = enabled; }
    public static boolean isTextMode() { return textMode; }
    public static void setTextMode(boolean enabled) { textMode = enabled; }
    //endregion
    //region Persistence
    private void persistSettings(String engineId, boolean openCl) {
        try {
            SaveData.Data data = SaveData.load();
            SaveData.saveSettings(data.language, data.theme, data.renderScale, data.vsync, engineId,
                    data.renderEngineQuality, data.windowWidth, data.windowHeight, data.fullscreen, openCl);
            console.log(DevConsole.Level.INFO, "saved to the settings file");
        } catch (IOException exception) { throw new IllegalArgumentException("could not save settings: " + exception.getMessage()); }
    }
    //endregion
    //endregion

    //region Loading
    private boolean isBusy() { return busyWorker != null; }
    private void onLoadingFinished() {
        gameStartTime = System.currentTimeMillis();
        pausedTime = 0;
        releaseInput();
    }
    private void prepareRenderer() {
        preparedFloor = currentFloor;
        syncRenderer(renderFrame);
        resolveRenderEngine().prepare(renderFrame);
    }
    private void startNextBusyWork() {
        Runnable work = pendingWork;
        if (work != null) {
            pendingWork = null;
            startBusy(work, "ld.title");
        }
        else if (renderPrepPending) {
            renderPrepPending = false;
            startBusy(this::prepareRenderer, "render");
        }
    }
    private void startBusy(Runnable work, String stage) {
        busyProgress.set(stage, 0);
        busyStartNanos = System.nanoTime();
        busyWorker = new Thread(() -> {
            Progress.bind(busyProgress);
            try { work.run(); }
            catch (Throwable exception) { exception.printStackTrace(); }
            finally { Progress.unbind(); }
        }, "game-background-work");
        busyWorker.setDaemon(true);
        busyWorker.start();
    }
    private void drawBusyOverlay() {
        Graphics2D g2d = bufferedImage.createGraphics();
        double seconds = (System.nanoTime() - busyStartNanos) / 1e9;
        LoadingScreen.paint(g2d, bufferedImage.getWidth(), bufferedImage.getHeight(), busyProgress.fraction(), NSLocalizedString.get(busyProgress.stage()), seconds);
        g2d.dispose();
        repaint();
    }
    //endregion

    //region HUD
    //region FPS and Timer
    private void trackFps() {
        frameCounter++;
        if (System.currentTimeMillis() - fpsWindowStart >= 1000) {
            currentFps = frameCounter;
            frameCounter = 0;
            fpsWindowStart = System.currentTimeMillis();
        }
    }
    private void drawFpsCounter() {
        Graphics2D g2d = bufferedImage.createGraphics();
        g2d.scale(uiScale(), uiScale());
        boolean auto = renderQualitySetting == RenderEngineConfig.QUALITY_AUTO;
        String fpsText = "FPS: " + currentFps + (auto ? "  AUTO " + Math.round(renderHeight * 100.0 / RCJMS.GAME_HEIGHT) + "%" : "");

        g2d.setFont(fpsFont);
        g2d.setColor(Color.BLACK);
        g2d.drawString(fpsText, 40, RCJMS.SCREEN_HEIGHT - 28);

        g2d.setColor(Color.GREEN);
        g2d.drawString(fpsText, 38, RCJMS.SCREEN_HEIGHT - 30);
        g2d.dispose();
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
    //endregion
    //region Badges
    private void drawOpenClBadge() {
        RenderEngine engine = RenderEngines.find(renderEngineId);
        boolean supported = engine != null && engine.hasOpenCL();
        boolean on = openClAcceleration && supported;
        Project.OpenCl.GpuRuntime.Health health = engine == null ? Project.OpenCl.GpuRuntime.Health.OFF : Project.OpenCl.GpuRuntime.healthFor(engine.openClKey());
        Color color;
        if (!on) color = Color.RED;
        else if (health == Project.OpenCl.GpuRuntime.Health.READY) color = Color.GREEN;
        else if (health == Project.OpenCl.GpuRuntime.Health.PROBING) color = new Color(0xF1C40F);
        else color = Color.RED;

        Graphics2D g2d = bufferedImage.createGraphics();
        g2d.scale(uiScale(), uiScale());
        String text = "OpenCL acceleration: " + (on ? "ON" : "OFF");
        g2d.setFont(openClFont);
        g2d.setColor(Color.BLACK);
        g2d.drawString(text, 40, RCJMS.SCREEN_HEIGHT - 54);
        g2d.setColor(color);
        g2d.drawString(text, 38, RCJMS.SCREEN_HEIGHT - 56);
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
        String text = NSLocalizedString.get("gv.seed") + ": " + mazeSeedText;

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
        String text = NSLocalizedString.get("floor") + ": " + (currentFloor + 1) + "/" + map3D.length;

        g2d.setFont(BADGE_FONT);
        g2d.setColor(Color.BLACK);
        g2d.drawString(text, RCJMS.SCREEN_WIDTH - 198, RCJMS.SCREEN_HEIGHT - (26 + line * 24) + 2);
        g2d.setColor(new Color(255, 200, 120));
        g2d.drawString(text, RCJMS.SCREEN_WIDTH - 200, RCJMS.SCREEN_HEIGHT - (26 + line * 24));
        g2d.dispose();
    }
    //endregion
    //region Pause Menu
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
        g2d.drawString(NSLocalizedString.get("restart"), RCJMS.SCREEN_WIDTH / 2 - 90, RCJMS.SCREEN_HEIGHT / 2 + 50);
        g2d.drawString(NSLocalizedString.get("run"), RCJMS.SCREEN_WIDTH / 2 - 90, RCJMS.SCREEN_HEIGHT / 2 + 80);
        g2d.drawString(NSLocalizedString.get("gv.no_clip") + " = " + noClip, RCJMS.SCREEN_WIDTH / 2 - 90, RCJMS.SCREEN_HEIGHT / 2 + 110);
        g2d.drawString(NSLocalizedString.get("gv.geometry") + " = " + NSLocalizedString.get(
                GEOMETRY_MODE == MazeGenerator.GeometryMode.WRONG ? "gv.wrong_geometry" : "gv.euclidean"), RCJMS.SCREEN_WIDTH / 2 - 90, RCJMS.SCREEN_HEIGHT / 2 + 140);
        if (is3D()) g2d.drawString(NSLocalizedString.get("floor") + ": " + (currentFloor + 1) + "/" + map3D.length,
                RCJMS.SCREEN_WIDTH / 2 - 90, RCJMS.SCREEN_HEIGHT / 2 + 170);

        int exitHintY = RCJMS.SCREEN_HEIGHT / 2 + 170 + (is3D() ? 30 : 0);
        g2d.drawString(NSLocalizedString.get("gv.exit_hint"), RCJMS.SCREEN_WIDTH / 2 - 90, exitHintY);
        if (fogMinimap) g2d.drawString(NSLocalizedString.get("gv.map_toggle_hint"), RCJMS.SCREEN_WIDTH / 2 - 90, exitHintY + 30);

        g2d.dispose();
    }
    //endregion
    //endregion

    //region Mini Map
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
    //endregion

    //region Fog Of War
    public void setFogOfWarMinimap(boolean enabled) { fogMinimap = enabled; explored = null; fogMapVisible = true; }
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

    //region Floor Transitions
    //region Stairs Climb
    private void handleStairs(int cellType) {
        boolean onStairsNow = cellType == 3 || cellType == 4;

        if (onStairsNow && !onStairsLastFrame && !isFloorTransitioning) {
            int targetFloor = currentFloor;
            if (cellType == 3 && currentFloor < map3D.length - 1) targetFloor = currentFloor + 1;
            else if (cellType == 4 && currentFloor > 0) targetFloor = currentFloor - 1;

            if (targetFloor != currentFloor) {
                transitionFromFloor = currentFloor;
                transitionToFloor = targetFloor;
                transitionDirection = targetFloor > currentFloor ? 1 : -1;
                climbStartX = playerX;
                climbStartY = playerY;
                isFloorTransitioning = true;
                hasLastMousePos = floorSwitchApplied = false;
                floorTransitionTime = 0;
            }
        }
        onStairsLastFrame = onStairsNow;
    }
    private void advanceClimb() {
        if (transitionDirection == 0 || floorSwitchApplied) return;
        double walk = STEP_WALK * smooth(stepPhase());
        int cellX = (int) Math.floor(climbStartX), cellY = (int) Math.floor(climbStartY);
        playerX = Math.clamp(climbStartX + Math.cos(cameraAngle) * walk, cellX + 0.15, cellX + 0.85);
        playerY = Math.clamp(climbStartY + Math.sin(cameraAngle) * walk, cellY + 0.15, cellY + 0.85);
    }
    private double stepHeightOffset() {
        if (!isFloorTransitioning || transitionDirection == 0) return 0;
        if (!floorSwitchApplied) {
            double bob = FOOTFALL * Math.abs(Math.sin(Math.PI * STAIR_STEPS * stepPhase()));
            return transitionDirection * STEP_HEIGHT * smooth(stepPhase()) + bob;
        }
        return transitionDirection * STEP_HEIGHT * (1 - smooth(crossPhase()));
    }
    private double stepPitchOffset() {
        if (!isFloorTransitioning || transitionDirection == 0) return 0;
        if (!floorSwitchApplied) return transitionDirection * STEP_LEAN * smooth(stepPhase());
        return transitionDirection * STEP_LEAN * (1 - smooth(crossPhase()));
    }
    private double landingShake() {
        if (!isFloorTransitioning || !floorSwitchApplied || transitionDirection == 0) return 0;
        double t = crossPhase();
        if (t >= 1) return 0;
        return LAND_SHAKE * Math.exp(-4 * t) * Math.sin(26 * t);
    }
    private static double smooth(double t) { return t * t * (3 - 2 * t); }
    private double stepPhase() { return Math.clamp(floorTransitionTime / STEP_DURATION, 0, 1); }
    private double crossPhase() {
        return Math.clamp((floorTransitionTime - crossStart()) / CROSS_DURATION, 0, 1);
    }
    private double crossStart() { return transitionDirection == 0 ? 0 : STEP_DURATION; }
    private double transitionTotalDuration() {
        return transitionDirection == 0 ? CROSS_DURATION
                : STEP_DURATION + CROSS_DURATION + CAPTION_ROLL_DURATION + CAPTION_HOLD_DURATION + CAPTION_FADE_DURATION;
    }
    private boolean transitionLocksControls() {
        return isFloorTransitioning && transitionDirection != 0 && floorTransitionTime < STEP_DURATION + CROSS_DURATION;
    }
    //endregion
    //region Portal Transition
    private void startPortalTransition(int fromFloor, int toFloor) {
        transitionSnapshot = copyOfPicture();
        transitionFromFloor = fromFloor;
        transitionToFloor = toFloor;
        transitionDirection = 0;
        isFloorTransitioning = true;
        floorSwitchApplied = true;
        floorTransitionTime = 0;
    }
    private void endFloorTransition() {
        isFloorTransitioning = false;
        floorSwitchApplied = false;
        floorTransitionTime = 0;
        transitionDirection = 0;
        transitionSnapshot = null;
    }
    private BufferedImage copyOfPicture() {
        BufferedImage copy = new BufferedImage(bufferedImage.getWidth(), bufferedImage.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = copy.createGraphics();
        g2d.drawImage(bufferedImage, 0, 0, null);
        g2d.dispose();
        return copy;
    }
    private void drawPortalCrossfade() {
        if (!floorSwitchApplied || transitionSnapshot == null) return;
        double t = smooth(crossPhase());
        float alpha = (float) (1 - t);
        if (alpha <= 0.01f) return;
        double zoom = 1 + 0.12 * t;
        int width = bufferedImage.getWidth(), height = bufferedImage.getHeight();
        java.awt.geom.AffineTransform transform = new java.awt.geom.AffineTransform();
        transform.translate(width / 2.0, height / 2.0);
        transform.scale(zoom, zoom);
        transform.translate(-width / 2.0, -height / 2.0);
        Graphics2D g2d = bufferedImage.createGraphics();
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g2d.drawImage(transitionSnapshot, transform, null);
        g2d.dispose();
    }
    //endregion
    //region Floor Caption and Effects
    private void drawFloorTransitionEffects() {
        if (transitionDirection == 0) {
            drawPortalCrossfade();
            return;
        }
        if (!floorSwitchApplied) {
            drawDarkness(smooth(stepPhase()));
            return;
        }
        drawDarkness(1 - smooth(crossPhase()));
        double captionTime = floorTransitionTime - (STEP_DURATION + CROSS_DURATION);
        if (captionTime >= 0 && captionTime < CAPTION_ROLL_DURATION + CAPTION_HOLD_DURATION + CAPTION_FADE_DURATION) {
            drawFloorCaption(captionTime);
        }
    }
    private void drawDarkness(double amount) {
        float alpha = (float) Math.clamp(amount, 0, 1);
        if (alpha <= 0.005f) return;
        Graphics2D g2d = bufferedImage.createGraphics();
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, bufferedImage.getWidth(), bufferedImage.getHeight());
        g2d.dispose();
    }
    private void drawFloorCaption(double captionTime) {
        Graphics2D g2d = bufferedImage.createGraphics();
        g2d.scale(uiScale(), uiScale());
        g2d.setFont(BIG_MONO_FONT);
        FontMetrics fontMetrics = g2d.getFontMetrics();

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

        String prefix = NSLocalizedString.get("floor") + ": ";
        String suffix = "/" + map3D.length;
        String oldNumber = String.valueOf(transitionFromFloor + 1);
        String newNumber = String.valueOf(transitionToFloor + 1);

        int prefixWidth = fontMetrics.stringWidth(prefix);
        int numWidth = Math.max(fontMetrics.stringWidth(oldNumber), fontMetrics.stringWidth(newNumber));

        int baseX = (RCJMS.SCREEN_WIDTH - (prefixWidth + numWidth +  fontMetrics.stringWidth(suffix))) / 2;
        int rowHeight = fontMetrics.getHeight();

        Composite oldComposite = g2d.getComposite();
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

        g2d.setColor(Color.BLACK);
        g2d.drawString(prefix, baseX + 2, 112);
        g2d.setColor(Color.WHITE);
        g2d.drawString(prefix, baseX, 110);

        int numX = baseX + prefixWidth;
        Shape oldClip = g2d.getClip();
        g2d.clipRect(numX, 110 - fontMetrics.getAscent(), numWidth, rowHeight);

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
    //endregion

    //region Maze
    //region Maze Generation
    private long chooseSeed() {
        mazeSeedText = customSeed != null ? customSeed : SeedUtil.randomSeed();
        return SeedUtil.toLong(mazeSeedText);
    }
    private void regenerateMaze() {
        LevelSpec level = pendingLevel;
        pendingLevel = null;
        int width = level != null ? level.width() : mapWidth();
        int height = level != null ? level.height() : mapHeight();
        if (level != null) {
            MAZE_3D = level.floors() > 1;
            MAZE_FLOORS = MAZE_3D ? level.floors() : 1;
            GEOMETRY_MODE = level.geometry();
            customSeed = level.seed();
            requestedWidth = width;
            requestedHeight = height;
        }
        mapEngine = null;

        if (MAZE_3D) {
            mazeGenerator3D = new MazeGenerator3D(width, height, MAZE_FLOORS, chooseSeed(), GEOMETRY_MODE);
            map3D = mazeGenerator3D.generate(MAZE_MODE);
            currentFloor = 0;
            floorPortalsAll = new PortalData[MAZE_FLOORS];
            for (int f = 0; f < MAZE_FLOORS; f++) floorPortalsAll[f] = mazeGenerator3D.getPortals(f);
            map = map3D[currentFloor];
            portals = floorPortalsAll[currentFloor];
        }
        else {
            map3D = null;
            floorPortalsAll = null;
            currentFloor = 0;
            mazeGenerator = new MazeGenerator(width, height, chooseSeed(), GEOMETRY_MODE);
            map = mazeGenerator.generate(MAZE_MODE);
            portals = mazeGenerator.getPortals();
        }

        playerX = playerY = 1.5;
        cameraAngle = cameraPitch = 0;
        explored = null;
        onStairsLastFrame = false;
        endFloorTransition();
        gameStartTime = System.currentTimeMillis();
        pausedTime = 0;
        renderPrepPending = true;
    }
    private boolean is3D() { return map3D != null && map3D.length > 1; }
    private int floorCount() { return map3D == null ? 1 : map3D.length; }
    private int mapWidth() { return map[0].length; }
    private int mapHeight() { return map.length; }
    //endregion
    //region Restart
    private void restartGame() { restartGame(null); }
    private void restartGame(LevelSpec level) {
        if (isPaused) {
            pausedTime += System.currentTimeMillis() - pauseStartTime;
            isPaused = false;
            hideCursor();
        }
        if (isCustomMap) {
            try {
                isGameRunning = false;
                GameView gameView = is3D() ? new GameView(customFloorsForRestart, customPortalsForRestart, customGeometryModeForRestart) : new GameView(map);
                gameView.setFogOfWarMinimap(fogMinimap);
                RCJMS.instance.changeView(gameView, "Raycast Me!");
                gameThread.interrupt();
                gameView.start();
            } catch (IOException exception) { throw new RuntimeException(exception); }
            return;
        }
        pendingLevel = level;
        pendingWork = this::regenerateMaze;
        if (level == null) console.log(DevConsole.Level.INFO, "restarting the level");
    }
    private record LevelSpec(int width, int height, int floors, String seed, MazeGenerator.GeometryMode geometry) {

    }
    //endregion
    //endregion

    //region Text Mode
    private void textKey(KeyEvent keyEvent) {
        int key = keyEvent.getKeyCode();
        if (key == KeyEvent.VK_ENTER) {
            String line = textInput.toString();
            textInput.setLength(0);
            textCommand(line);
        }
        else if (key == KeyEvent.VK_BACK_SPACE && textInput.length() > 0) {
            textInput.setLength(textInput.length() - 1);
        }
        textDirty = true;
        repaint();
    }
    private void textCommand(String line) {
        String src = line.trim().toLowerCase(java.util.Locale.ROOT);
        if (src.isEmpty()) return;
        snapToTile();
        int steps = 0;
        boolean turned = false;
        for (String token : src.split("\\s+")) {
            java.util.regex.Matcher matcher = TEXT_MOVE.matcher(token);
            if (!matcher.matches()) {
                textStatus = "? " + token;
                return;
            }
            String kind = matcher.group(1);
            int count = matcher.group(2).isEmpty() ? 1 : Math.min(99, Integer.parseInt(matcher.group(2)));
            if (kind.length() == 2) {
                cameraAngle += (kind.charAt(1) == 'l' ? -1 : 1) * count * Math.PI / 2;
                snapToTile();
                turned = true;
                continue;
            }
            char dir = kind.charAt(0);
            double base = cameraAngle;
            for (int i = 0; i < count; i++) {
                if (!textStep(dir, base)) {
                    textStatus = "blocked after " + steps + " steps, at " + token;
                    return;
                }
                steps++;
            }
        }
        textStatus = steps + " steps" + (turned ? ", turned" : "");
    }
    private boolean textStep(char dir, double base) {
        int fx = (int) Math.round(Math.cos(base)), fy = (int) Math.round(Math.sin(base));
        int vx, vy;
        switch (dir) {
            case 'f' -> { vx = fx; vy = fy; }
            case 'b' -> { vx = -fx; vy = -fy; }
            case 'l' -> { vx = fy; vy = -fx; }
            default -> { vx = -fy; vy = fx; }
        }
        int tx = (int) Math.floor(playerX) + vx, ty = (int) Math.floor(playerY) + vy;
        if (!noClip) {
            if (ty < 0 || ty >= map.length || tx < 0 || tx >= map[ty].length) return false;
            int cell = map[ty][tx];
            if (cell == MazeGenerator.WALL || cell == MazeGenerator.FINISH || cell == MazeGenerator.PORTAL) return false;
        }
        playerX = tx + 0.5;
        playerY = ty + 0.5;
        cameraAngle = Math.atan2(vy, vx);
        return true;
    }
    private void snapToTile() {
        playerX = Math.floor(playerX) + 0.5;
        playerY = Math.floor(playerY) + 0.5;
        cameraAngle = Math.floorMod(Math.round(cameraAngle / (Math.PI / 2)), 4L) * (Math.PI / 2);
        cameraPitch = 0;
    }
    private void renderText() {
        snapToTile();
        int w = bufferedImage.getWidth(), h = bufferedImage.getHeight();
        textScreen = TextMode.build(w, h, map, exploredGrid(), playerX, playerY, cameraAngle, textInput.toString(), textStatus);
        textScreenMap = map;
        textDirty = false;
        Graphics2D g2d = bufferedImage.createGraphics();
        g2d.drawImage(textScreen, 0, 0, null);
        g2d.dispose();
        if (isPaused) drawPauseMenu();
    }
    //endregion

    //region Console
    //region Console Toggle
    private void toggleConsole() {
        console.toggle();
        if (console.isOpen()) {
            releaseInput();
            showCursor();
        }
        else if (!isPaused) hideCursor();
        repaint();
    }
    private void consoleKey(KeyEvent keyEvent) {
        console.handleKey(keyEvent);
        String line = console.takeSubmitted();
        if (line != null) console.execute(line);
        repaint();
    }
    //endregion
    //region Command Registration
    private void registerConsoleCommands() {
        console.register("--help", "--help", args -> {
            args.info("commands:");
            for (String line : console.helpLines()) args.info("  " + line);
            args.info("options start with a dash; -s saves a change to the settings file");
        });
        console.register("engine", "engine [<name>] [-l] [-on|-off](openCL) [-s]  (no name: current engine, -l: list engines)", this::engineCommand);
        console.register("log", "log [-s] [-c] [-f [-off]]  (-s: save the terminal to a file, -c: clear the terminal, -f: detailed logs on, -f -off: off)", this::logCommand);
        console.register("openCL", "openCL [-on|-off] [-s]  (no option: show the state)", this::openClCommand);
        console.register("pause", "pause", args -> {
            if (isBusy()) throw new IllegalArgumentException("the level is still loading");
            if (isPaused) args.info("already paused");
            else setPaused(true);
        });
        console.alias("--p", "pause");
        console.register("resume", "resume", args -> {
            if (!isPaused) args.info("the game is not paused");
            else setPaused(false);
        });
        console.alias("--r", "resume");
        console.register("restart", "restart [W [H]] [-fN] [-sSEED] [-g1|-g2|-g3]  (no options: same level)", this::restartCommand);
        console.alias("--rs", "restart");
        console.register("status", "status", args -> {
            RenderEngine engine = currentEngine();
            args.info("engine: " + (engine == null ? "none" : engine.id() + " (" + engine.displayName() + ")"));
            args.info("OpenCL acceleration: " + (openClAcceleration ? "ON" : "OFF"));
            if (engine != null && openClAcceleration && engine.hasOpenCL()) {
                Project.OpenCl.GpuRuntime.Health health = Project.OpenCl.GpuRuntime.healthFor(engine.openClKey());
                String reason = Project.OpenCl.GpuRuntime.reasonFor(engine.openClKey());
                args.info("GPU: " + health + (reason.isEmpty() ? "" : " (" + reason + ")"));
            }
            args.info("paused: " + (isPaused ? "yes" : "no") + ", loading: " + (isBusy() ? "yes" : "no"));
        });
        console.alias("--s", "status");
        console.register("maze", "maze  (size, floors, finish, geometry, seed, finish position)", this::mazeCommand);
        console.alias("--m", "maze");
    }
    //endregion
    //region Command Handlers
    private void restartCommand(DevConsole.Args args) {
        if (isBusy()) throw new IllegalArgumentException("the level is still loading, try again");
        java.util.List<String> values = args.values();
        String floorsText = args.optionValue("-f");
        String seedText = args.optionValue("-s");
        String geometryText = args.optionValue("-g");
        if (values.isEmpty() && floorsText == null && seedText == null && geometryText == null) {
            restartGame(null);
            return;
        }
        if (isCustomMap) throw new IllegalArgumentException("a custom map keeps its own size: restart it without options");
        if (values.size() > 2) throw new IllegalArgumentException("restart takes a width and an optional height: restart 90 [70]");

        int width = values.isEmpty() ? mapWidth() : parseSize(values.get(0), "width");
        int height = values.size() == 2 ? parseSize(values.get(1), "height") : (values.isEmpty() ? mapHeight() : width);
        int floors = floorsText == null ? 1 : parseFloors(floorsText);
        String seed = seedText == null ? null : seedFrom(seedText, args);
        MazeGenerator.GeometryMode geometry = geometryText == null ? MazeGenerator.GeometryMode.EUCLIDEAN : geometryFrom(geometryText);

        RenderEngine engine = currentEngine();
        if (geometry == MazeGenerator.GeometryMode.WRONG && (engine == null || !engine.hasHyperbolicGeometry()))
            throw new IllegalArgumentException("engine " + nameOf(engine) + " does not support WRONG geometry (-g2)");
        if (geometry == MazeGenerator.GeometryMode.WRONG && openClAcceleration)
            args.warn("OpenCL is not used in WRONG geometry; it applies again in other geometries");
        if (engine != null && !engine.supportsMapSize(width, height))
            args.warn("engine " + engine.displayName() + " does not support " + width + "x" + height + ", another engine may draw the frame");
        args.info("restarting: " + width + "x" + height + ", " + (floors > 1 ? floors + " floors" : "one floor") + ", "
                + geometry + (seed != null ? ", seed " + seed : ", random seed"));
        restartGame(new LevelSpec(width, height, floors, seed, geometry));
    }
    private void mazeCommand(DevConsole.Args args) {
        boolean three = is3D();
        args.info("size: " + mapWidth() + " x " + mapHeight() + (three ? ", " + floorCount() + " floors" : ", one floor"));
        args.info("finish mode: " + MAZE_MODE);
        args.info("geometry: " + GEOMETRY_MODE);
        args.info("seed: " + (isCustomMap ? "none (custom map)" : mazeSeedText));
        int[][][] layers = three ? map3D : new int[][][]{map};
        int found = 0;
        for (int f = 0; f < layers.length; f++)
            for (int y = 0; y < layers[f].length; y++)
                for (int x = 0; x < layers[f][y].length; x++)
                    if (layers[f][y][x] == 2) {
                        found++;
                        args.info("finish: " + (three ? "floor " + (f + 1) + ", " : "") + "x " + x + ", y " + y);
                    }
        if (found == 0) args.info("finish: none");
    }
    private void logCommand(DevConsole.Args args) {
        boolean save = args.flag("-s");
        boolean clear = args.flag("-c");
        boolean detailed = args.flag("-f");
        boolean off = args.flag("-off");
        if (detailed) {
            if (save || clear || !args.values().isEmpty()) throw new IllegalArgumentException("-f works alone, add -off to stop");
            verboseLog = !off;
            detailKey = null;
            detailFrames = 0;
            detailNanos = 0;
            args.info("detailed log: " + (verboseLog ? "ON (engine, geometry and frame timing are printed)" : "OFF"));
            return;
        }
        if (off) throw new IllegalArgumentException("-off only works with -f");
        if (!args.values().isEmpty()) throw new IllegalArgumentException("unexpected argument: " + args.values().getFirst());
        if (!save && !clear) {
            args.info("usage: log [-s] [-c]");
            return;
        }
        String text = console.transcript();
        java.nio.file.Path file = null;
        if (save) {
            String stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            file = Project.Helpers.AppPaths.file("log_" + stamp + ".txt");
            try { java.nio.file.Files.writeString(file, text, java.nio.charset.StandardCharsets.UTF_8); }
            catch (IOException exception) { throw new IllegalArgumentException("could not save the log: " + exception.getMessage()); }
        }
        if (clear) console.clearLog();
        if (file != null) args.info("terminal saved to " + file);
    }
    private void openClCommand(DevConsole.Args args) {
        boolean on = args.flag("-on");
        boolean off = args.flag("-off");
        boolean save = args.flag("-s");
        if (!args.values().isEmpty()) throw new IllegalArgumentException("unexpected argument: " + args.values().getFirst());
        if (on && off) throw new IllegalArgumentException("use either -on or -off, not both");
        RenderEngine engine = currentEngine();
        if (on && GEOMETRY_MODE == MazeGenerator.GeometryMode.WRONG) {
            args.warn("OpenCL is not used in WRONG geometry: the GPU path renders only Euclidean and looped maps");
            throw new IllegalArgumentException("cannot turn OpenCL ON in WRONG geometry (-g2)");
        }
        if (!on && !off) {
            if (save) throw new IllegalArgumentException("-s needs -on or -off");
            args.info("OpenCL acceleration: " + (openClAcceleration ? "ON" : "OFF"));
            reportGpuState(engine);
            return;
        }
        if (on && (engine == null || !engine.hasOpenCL()))
            throw new IllegalArgumentException("engine " + nameOf(engine) + " has no OpenCL path, OpenCL stays OFF");
        setOpenClAcceleration(on);
        args.info("OpenCL acceleration " + (on ? "ON" : "OFF"));
        reportGpuState(engine);
        if (save) persistSettings(renderEngineId, on);
    }
    private void engineCommand(DevConsole.Args args) {
        boolean list = args.flag("-l");
        boolean on = args.flag("-on");
        boolean off = args.flag("-off");
        boolean save = args.flag("-s");
        java.util.List<String> values = args.values();
        RenderEngine current = currentEngine();

        if (list) {
            if (!values.isEmpty() || on || off || save) throw new IllegalArgumentException("-l only lists the engines");
            for (RenderEngine renderEngine : RenderEngines.all())
                args.info((renderEngine == current ? "* " : "  ") + renderEngine.id() + " = " + renderEngine.displayName() + (renderEngine.hasOpenCL() ? "  [OpenCL]" : ""));
            return;
        }
        if (values.isEmpty() && !on && !off && !save) {
            if (current == null) { args.info("engine: none"); return; }
            args.info("engine: " + current.id() + " (" + current.displayName() + "), OpenCL acceleration " + (openClAcceleration ? "ON" : "OFF"));
            reportGpuState(current);
            return;
        }
        if (values.size() > 1) throw new IllegalArgumentException("too many arguments for engine: " + values.subList(1, values.size()));
        if (on && off) throw new IllegalArgumentException("use either -on or -off, not both");
        RenderEngine target = values.isEmpty() ? current : findEngine(values.getFirst());
        if (target == null && values.isEmpty()) throw new IllegalArgumentException("no engine is selected (use engine <name>, see engine -l)");
        if (target == null) throw new IllegalArgumentException("unknown engine: " + values.getFirst() + " (see engine -l)");
        Boolean requested = on ? Boolean.TRUE : off ? Boolean.FALSE : null;
        if (on && GEOMETRY_MODE == MazeGenerator.GeometryMode.WRONG) {
            args.warn("OpenCL is not used in WRONG geometry: the GPU path renders only Euclidean and looped maps");
            throw new IllegalArgumentException("cannot turn OpenCL ON in WRONG geometry (-g2)");
        }

        renderEngineId = target.id();
        boolean savedOpenCl = SaveData.load().openCl;
        boolean resolved = resolveOpenClForSwap(target.hasOpenCL(), requested, savedOpenCl);
        if (requested != null && requested && !target.hasOpenCL())
            args.warn("engine " + target.displayName() + " has no OpenCL path, OpenCL stays OFF");
        else if (requested == null && savedOpenCl && !target.hasOpenCL())
            args.info("saved OpenCL setting is ON, but engine " + target.displayName() + " has no OpenCL path: OFF");
        setOpenClAcceleration(resolved);
        if (!target.supportsMapSize(mapWidth(), mapHeight()))
            args.warn("engine " + target.displayName() + " does not support this map size, another engine may draw the frame");
        args.info("engine: " + target.id() + " (" + target.displayName() + "), OpenCL acceleration " + (openClAcceleration ? "ON" : "OFF"));
        reportGpuState(target);
        if (save) persistSettings(target.id(), openClAcceleration);
    }
    //endregion
    //region Argument Parsing
    private static int parseSize(String text, String what) {
        int value;
        try { value = Integer.parseInt(text); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException(what + " must be a number, got " + text); }
        if (value < 5 || value > 200) throw new IllegalArgumentException(what + " must be between 5 and 200");
        return value;
    }
    private static int parseFloors(String text) {
        int value;
        try { value = Integer.parseInt(text); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException("-f needs a number of floors, for example -f15"); }
        if (value != 1 && (value < 2 || value > 20)) throw new IllegalArgumentException("floors must be 1 or 2..20");
        return value;
    }
    private static String seedFrom(String text, DevConsole.Args args) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) throw new IllegalArgumentException("-s needs a seed, for example -sABC123");
        String seed = SeedUtil.normalize(trimmed);
        if (seed.length() < trimmed.replaceAll("\\s", "").length())
            args.warn("seed is longer than " + SeedUtil.LENGTH + " characters, shortened to " + seed);
        return seed;
    }
    private static MazeGenerator.GeometryMode geometryFrom(String text) {
        return switch (text.trim()) {
            case "1" -> MazeGenerator.GeometryMode.EUCLIDEAN;
            case "2" -> MazeGenerator.GeometryMode.WRONG;
            case "3" -> MazeGenerator.GeometryMode.LOOPED;
            default -> throw new IllegalArgumentException("geometry must be -g1 (Euclidean), -g2 (wrong) or -g3 (looped)");
        };
    }
    //endregion
    //endregion
}