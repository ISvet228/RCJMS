package Project.Game;

import Project.Core.*;
import Project.Helpers.Settings;

import Project.*;
import Project.Helpers.FontCache;
import Project.MainMenu.MainMenuView;
import Project.RCJMS;
import Project.TextureEditor.TextureEditorView;
import Project.Victory.VictoryView;
import Project.Helpers.*;
import Project.Helpers.maze.MazeAlgorithms;
import static Project.Game.RenderEngineConfig.*;
import Project.Filters.*;
import Project.Game.Pause.*;

import javax.swing.*; //Frame Library
import java.awt.*; //Graphics Library
import java.awt.event.*; //Input Library
import java.awt.image.*; //Buffer Library
import java.io.IOException;

public class GameView extends JPanel implements Runnable, KeyListener, MouseMotionListener, MouseListener, MouseWheelListener {
    //region Variables
    //region Maze Configuration
    private MazeGenerator.FinishMode MAZE_MODE = MazeGenerator.FinishMode.RANDOM_EDGE;
    private String mazeAlgorithmId = MazeAlgorithms.selected().id();
    private boolean MAZE_HAS_FLOORS = false;
    private int MAZE_FLOORS = 1;
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
    private MazeData sharedMaze;
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
    private static final double BOB_RATE = 2 * Math.PI * ((double) 8192 / 20) * DOOM_TICS_PER_SECOND / 8192;
    //endregion
    //region Textures
    private final RuntimeTexture wallTexture = TextureEditorView.readRuntimeTexture(AppPaths.WALL_TEXTURE_FILE, TextureEditorView.MAX_TEXTURE_SIZE, TextureEditorView.MAX_TEXTURE_SIZE);
    private final RuntimeTexture floorTexture = TextureEditorView.readRuntimeTexture(AppPaths.FLOOR_TEXTURE_FILE, TextureEditorView.MAX_TEXTURE_SIZE, TextureEditorView.MAX_TEXTURE_SIZE);
    private final RuntimeTexture ceilingTexture = TextureEditorView.readRuntimeTexture(AppPaths.CEILING_TEXTURE_FILE, TextureEditorView.MAX_TEXTURE_SIZE, TextureEditorView.MAX_TEXTURE_SIZE);
    private final RuntimeTexture finishTexture = TextureEditorView.readRuntimeTexture(AppPaths.FINISH_TEXTURE_FILE, TextureEditorView.MAX_TEXTURE_SIZE, TextureEditorView.MAX_FINISH_HEIGHT);
    //endregion
    //region Mini Map State
    public static int MINI_MAP_WIDTH = 180, MINI_MAP_HEIGHT = 180;
    //endregion
    //region Fonts
    private static final Font SMALL_SANS_FONT = FontCache.get(Font.SANS_SERIF, Font.PLAIN, 20);
    //endregion
    private final MiniMap miniMap = new MiniMap();
    private final Hud hud = new Hud();
    //region Text Mode State
    private final StringBuilder textInput = new StringBuilder();
    private String textStatus = "type f, b, l, r (tiles) or tl, tr (quarter turns), e.g. f3 tr f2";
    private BufferedImage textScreen;
    private static final java.util.regex.Pattern TEXT_MOVE = java.util.regex.Pattern.compile("(t[lr]|[fblr])(\\d*)");
    //endregion
    private final FloorTransition transition = new FloorTransition();
    //region Loading State
    private volatile boolean renderPrepPending = true;
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
    private volatile boolean pauseFreezesWorld = true;
    private long gameStartTime = System.currentTimeMillis();
    private long pauseStartTime = 0, pausedTime = 0, elapsedSeconds;
    //endregion
    //region Game Modules
    private final java.util.List<GameModule> features = ModuleRegistry.create();
    private final java.util.Set<GameModule> brokenModules = new java.util.HashSet<>();
    private final java.util.Map<GameModule, Integer> moduleFailures = new java.util.HashMap<>();
    private static final int MODULE_FAILURE_LIMIT = 120;
    private final GameContext gameContext = new ModuleContext();
    private volatile String flashText = "";
    private volatile long flashUntilMillis = 0;
    private volatile boolean hudVisible = true;
    //endregion
    //region Pause Menu State
    private final PauseMenuView pauseMenu = new PauseMenuView(new PauseMenuState(), this::onPauseItem);
    private String pauseFilterName = "";
    //endregion
    //region FPS Counter State
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
        this(mazeWidth, mazeHeight, mazeMode, geometryMode, seed, floors, null); }
    public GameView(int mazeWidth, int mazeHeight, int mazeMode, int geometryMode, String seed, int floors, String algorithm) throws IOException {
        this(mazeWidth, mazeHeight, mazeMode, geometryMode, seed, floors, algorithm, null);
    }
    public GameView(MazeData maze) throws IOException {
        this(maze.width(), maze.height(), maze.finishMode(), maze.geometry(), maze.seed(), maze.hasFloors() ? maze.floors() : 1, maze.algorithm(), maze);
    }
    private GameView(int mazeWidth, int mazeHeight, int mazeMode, int geometryMode, String seed, int floors, String algorithm,
                     MazeData shared) throws IOException {
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
        sharedMaze = shared;
        requestedWidth = mazeWidth;
        requestedHeight = mazeHeight;
        GEOMETRY_MODE = MazeGenerator.GeometryMode.values()[Math.clamp(geometryMode, 0, MazeGenerator.GeometryMode.values().length - 1)];
        MAZE_MODE = MazeGenerator.FinishMode.values()[mazeMode];
        mazeAlgorithmId = (algorithm != null ? MazeAlgorithms.find(algorithm) : MazeAlgorithms.selected()).id();

        MAZE_HAS_FLOORS = floors > 1;
        MAZE_FLOORS = MAZE_HAS_FLOORS ? floors : 1;

        if (shared != null) applyShared(shared);
        else if (MAZE_HAS_FLOORS) {
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
        catch (Exception exception) { Log.error(exception); }

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
        MAZE_HAS_FLOORS = floors > 1;
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
        catch (Exception exception) { Log.error(exception); }

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
        MAZE_HAS_FLOORS = false;
        GEOMETRY_MODE = MazeGenerator.GeometryMode.EUCLIDEAN;
        MAZE_FLOORS = 1;
        map = customMap;
        addKeyListener(this);
        addMouseMotionListener(this);
        addMouseListener(this);
        addMouseWheelListener(this);

        try {cursorRobot = new Robot();}
        catch (Exception exception) {Log.error(exception);}

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
            new GameConsoleCommands(new ConsoleBridge()).register();
            eachModule(feature -> feature.onConsoleReady(gameContext));
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
                        if (pendingWork == null && !renderPrepPending) onLoadingFinished();
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
                        else if (!pauseFreezesWorld) notifyUpdate(deltaTime);   // live multiplayer: the room (stalker, other players) keeps going while this player is in the pause menu
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
                    hud.trackFps();
                }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); break; }
                catch (Throwable exception) {
                    if (!errorReported) { errorReported = true; Log.error(exception); }
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
        if (paused) pauseFreezesWorld = Project.Net.NetSession.current() == null;   // a race in a room cannot be paused for everybody
        notifyPause(paused);
        console.log(DevConsole.Level.INFO, paused ? "paused" : "resumed");
        if (paused) {
            pauseStartTime = System.currentTimeMillis();
            showCursor();
            pauseFilterName = currentFilterName();
            pauseMenu.open(hudVisible, pauseFilterName);
        } else {
            if (pauseFreezesWorld) pausedTime += System.currentTimeMillis() - pauseStartTime;
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
    private boolean paintErrorReported;   // game thread only: report a failing paint once, not every frame
    private void paintFrameSynchronously() {
        if (!SwingUtilities.isEventDispatchThread()) {
            try { SwingUtilities.invokeAndWait(() -> paintImmediately(0, 0, getWidth(), getHeight())); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); }   // keep the stop request, the loop must see it
            catch (java.lang.reflect.InvocationTargetException exception) {
                if (!paintErrorReported) { paintErrorReported = true; exception.getCause().printStackTrace(); }
            }
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
        if (notifyKey(keyEvent, true)) return;
        boolean loading = isBusy();
        boolean pauseKey = KeyBindings.matches(KeyBindings.Action.PAUSE, keyEvent);
        if (loading && (pauseKey || KeyBindings.matches(KeyBindings.Action.RESTART, keyEvent))) return;
        if (isPaused && pauseMenu.key(keyEvent)) return;
        if (textMode && !isPaused && !pauseKey) {
            textKey(keyEvent);
            return;
        }
        if (KeyBindings.matches(KeyBindings.Action.FORWARD, keyEvent)) w = !loading;
        if (KeyBindings.matches(KeyBindings.Action.BACK, keyEvent)) s = !loading;
        if (KeyBindings.matches(KeyBindings.Action.LEFT, keyEvent)) a = !loading;
        if (KeyBindings.matches(KeyBindings.Action.RIGHT, keyEvent)) d = !loading;
        if (key == KeyEvent.VK_B) isDebugMode = !isDebugMode;
        if (key == KeyEvent.VK_M) miniMap.toggleFogVisible();
        if (KeyBindings.matches(KeyBindings.Action.SPRINT, keyEvent)) shift = !loading;
        if (key == KeyEvent.VK_F1) noClip = !noClip;
        if (key == KeyEvent.VK_EQUALS || key == KeyEvent.VK_PLUS || key == KeyEvent.VK_ADD) MiniMap.zoomIn();
        if (key == KeyEvent.VK_MINUS || key == KeyEvent.VK_SUBTRACT) MiniMap.zoomOut();
        if (pauseKey) {
            setPaused(!isPaused);
        }
        if (isPaused && keyEvent.isControlDown() && key == KeyEvent.VK_C) {
            returnToMainMenu();
            return;
        }
        if (KeyBindings.matches(KeyBindings.Action.RESTART, keyEvent)) restartGame();
    }
    @Override public void keyReleased(KeyEvent keyEvent) {
        notifyKey(keyEvent, false);
        if (KeyBindings.matches(KeyBindings.Action.FORWARD, keyEvent)) w = false;
        if (KeyBindings.matches(KeyBindings.Action.BACK, keyEvent)) s = false;
        if (KeyBindings.matches(KeyBindings.Action.LEFT, keyEvent)) a = false;
        if (KeyBindings.matches(KeyBindings.Action.RIGHT, keyEvent)) d = false;
        if (KeyBindings.matches(KeyBindings.Action.SPRINT, keyEvent)) shift = false;
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
                repaint();
            }
        }
    }
    //endregion
    //region Mouse
    @Override public void mouseMoved(MouseEvent mouseEvent) {
        if (isPaused && !console.isOpen()) {
            Point point = toFrame(mouseEvent);
            pauseMenu.mouseMoved(point.x, point.y);
            return;
        }
        handleMouseLook(mouseEvent);
    }
    @Override public void mouseDragged(MouseEvent mouseEvent) {
        if (isPaused && !console.isOpen()) {
            Point point = toFrame(mouseEvent);
            pauseMenu.mouseDragged(point.x, point.y);
            return;
        }
        if (console.isOpen()) {
            console.mouseDragged(mouseEvent.getX(), mouseEvent.getY());
            repaint();
            return;
        }
        handleMouseLook(mouseEvent);
    }
    @Override public void mousePressed(MouseEvent mouseEvent) {
        if (isPaused && !console.isOpen()) {
            Point point = toFrame(mouseEvent);
            pauseMenu.mousePressed(point.x, point.y);
            repaint();
            return;
        }
        if (!console.isOpen()) return;
        console.mousePressed(mouseEvent.getX(), mouseEvent.getY());
        repaint();
    }
    @Override public void mouseReleased(MouseEvent mouseEvent) {
        if (isPaused && !console.isOpen()) {
            pauseMenu.mouseReleased();
            return;
        }
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
        if (transition.locksControls()) return;
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
    private Point toFrame(MouseEvent mouseEvent) {
        double scale = Math.min(getWidth() / (double) bufferedImage.getWidth(), getHeight() / (double) bufferedImage.getHeight());
        int drawX = (getWidth() - (int) (bufferedImage.getWidth() * scale)) / 2;
        int drawY = (getHeight() - (int) (bufferedImage.getHeight() * scale)) / 2;
        return new Point((int) ((mouseEvent.getX() - drawX) / scale), (int) ((mouseEvent.getY() - drawY) / scale));
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
        notifyUpdate(deltaTiime);
        if (transition.active()) {
            if (transition.advance(deltaTiime)) {
                currentFloor = transition.toFloor();
                map = map3D[currentFloor];
                portals = floorPortalsAll[currentFloor];
            }
            if (transition.climbing()) {
                playerX = transition.climbX(cameraAngle);
                playerY = transition.climbY(cameraAngle);
            }
            if (transition.locksControls()) return;
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

        applyPortalStep(moveStartX, moveStartY, movementX, movementY);
        if (reachedFinish()) return;

        int cellY = Math.clamp((int) playerY, 0, mapHeight() - 1);
        int cellX = Math.clamp((int) playerX, 0, mapWidth() - 1);
        int cellType = map[cellY][cellX];

        syncMiniMap();
        if (miniMap.fogEnabled() || textMode) miniMap.updateFog(deltaTiime);
        if (hasFloors()) handleStairs(cellType);
    }
    private boolean applyPortalStep(double startX, double startY, double moveX, double moveY) {
        double moveLength = Math.hypot(moveX, moveY);
        if (moveLength <= 1e-12) return false;
        int cellX = Math.clamp((int) playerX, 0, mapWidth() - 1), cellY = Math.clamp((int) playerY, 0, mapHeight() - 1);
        int fromX = Math.clamp((int) startX, 0, mapWidth() - 1), fromY = Math.clamp((int) startY, 0, mapHeight() - 1);
        if ((cellX == fromX && cellY == fromY) || map[cellY][cellX] != MazeGenerator.PORTAL) return false;
        PortalData.Portal portal = portals.get(cellX, cellY);
        if (portal == null) return false;
        if (!PortalData.entersFromCorrectSide(portal, moveX / moveLength, moveY / moveLength)) return false;
        double[] warped = PortalData.arrive(portal, playerX, playerY);
        if (map3D != null && portal.linkedFloor >= 0 && portal.linkedFloor != currentFloor && portal.linkedFloor < map3D.length) {
            int fromFloor = currentFloor;
            currentFloor = portal.linkedFloor;
            map = map3D[currentFloor];
            portals = floorPortalsAll[currentFloor];
            if (!transition.active()) transition.beginPortal(fromFloor, currentFloor);
        }
        playerX = Math.clamp(warped[0], 0.0001, mapWidth() - 0.0001);
        playerY = Math.clamp(warped[1], 0.0001, mapHeight() - 0.0001);
        if (!noClip) pushPlayerOutOfWalls(map);
        double[] warpedFacing = PortalData.transformDirection(portal, Math.cos(cameraAngle), Math.sin(cameraAngle));
        cameraAngle = Math.atan2(warpedFacing[1], warpedFacing[0]);
        hasLastMousePos = false;
        return true;
    }
    private boolean reachedFinish() {
        int cellY = Math.clamp((int) playerY, 0, mapHeight() - 1);
        int cellX = Math.clamp((int) playerX, 0, mapWidth() - 1);
        if (map[cellY][cellX] != MazeGenerator.FINISH || !isGameRunning) return false;
        isGameRunning = false;
        notifyRunFinish();
        playerX = playerY = 1.5;
        cameraAngle = cameraPitch = 0;
        RCJMS.instance.changeView(RCJMS.instance.victoryView = new VictoryView(elapsedSeconds, requestedWidth, requestedHeight, isCustomMap), "vv.victory");
        gameThread.interrupt();
        return true;
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
        if (drawX > 0) { g2d.fillRect(0, 0, drawX, panelHeight); g2d.fillRect(drawX + drawWidth, 0, panelWidth - drawX - drawWidth, panelHeight); }
        if (drawY > 0) { g2d.fillRect(drawX, 0, drawWidth, drawY); g2d.fillRect(drawX, drawY + drawHeight, drawWidth, panelHeight - drawY - drawHeight); }
        if (drawWidth < panelWidth - 2 * drawX || drawHeight < panelHeight - 2 * drawY) g2d.fillRect(0, 0, panelWidth, panelHeight);   // rounding left a gap: clear everything once
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
        updateElapsed();
        if (hudVisible) drawStatusHud();
        syncMiniMap();
        if (isDebugMode && !isPaused) { miniMap.drawMiniMap(); drawDebugHud(); }
        if (miniMap.fogVisible()) miniMap.drawFogMiniMap();
        paintModules();
        if (isPaused) drawPauseMenu();
        if (transition.active()) transition.drawEffects(bufferedImage, floorCount());
    }
    private void syncRenderer(RenderFrame renderFrame) {
        renderFrame.bufferedImage = bufferedImage;
        renderFrame.cameraAngle = cameraAngle + transition.landingShake();
        renderFrame.cameraHeight = Math.clamp(cameraHeight + viewBob() + transition.stepHeightOffset(), 0.05, 0.95);
        renderFrame.cameraPitch = cameraPitch + transition.stepPitchOffset();
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
        spriteHyperbolic = GEOMETRY_MODE == MazeGenerator.GeometryMode.WRONG;
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
    private static volatile boolean spriteHyperbolic;
    public static float[] spriteLight(double distance) {
        RenderEngine engine = currentEngine();
        return engine == null ? new float[]{1f, 1f, 1f} : engine.spriteLight(distance, spriteHyperbolic);
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
    //endregion

    //region Loading
    private boolean isBusy() { return busyWorker != null; }
    private void onLoadingFinished() {
        gameStartTime = System.currentTimeMillis();
        pausedTime = 0;
        releaseInput();
        notifyRunStart();
    }
    private void prepareRenderer() {
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
            catch (Throwable exception) { Log.error(exception); }
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
    //region Overlay Data
    private void updateElapsed() { elapsedSeconds = ((isPaused && pauseFreezesWorld ? pauseStartTime : System.currentTimeMillis()) - gameStartTime - pausedTime) / 1000; }
    private void drawStatusHud() {
        Hud.Badge seed = isCustomMap ? null : new Hud.Badge(NSLocalizedString.get("gv.seed") + ": " + mazeSeedText, Hud.SEED_COLOR);
        Hud.Badge geometry = switch (GEOMETRY_MODE) {
            case WRONG -> new Hud.Badge(NSLocalizedString.get("gv.wrong_geometry"), Hud.GEOMETRY_COLOR);
            case LOOPED -> new Hud.Badge(NSLocalizedString.get("looped_geometry"), Hud.GEOMETRY_COLOR);
            default -> null;
        };
        Hud.Badge floor = hasFloors() ? new Hud.Badge(NSLocalizedString.get("floor") + ": " + (currentFloor + 1) + "/" + map3D.length, Hud.FLOOR_COLOR) : null;
        hud.drawStatus(bufferedImage, elapsedSeconds, seed, geometry, floor);
    }
    private void drawDebugHud() {
        boolean auto = renderQualitySetting == RenderEngineConfig.QUALITY_AUTO;
        String fpsText = "FPS: " + hud.fps() + (auto ? "  AUTO " + Math.round(renderHeight * 100.0 / RCJMS.GAME_HEIGHT) + "%" : "");
        RenderEngine engine = RenderEngines.find(renderEngineId);
        boolean on = openClAcceleration && engine != null && engine.hasOpenCL();
        Project.OpenCl.GpuRuntime.Health health = engine == null ? Project.OpenCl.GpuRuntime.Health.OFF : Project.OpenCl.GpuRuntime.healthFor(engine.openClKey());
        Color color = !on ? Color.RED : health == Project.OpenCl.GpuRuntime.Health.READY ? Color.GREEN : health == Project.OpenCl.GpuRuntime.Health.PROBING ? new Color(0xF1C40F) : Color.RED;
        hud.drawDebug(bufferedImage, fpsText, "OpenCL acceleration: " + (on ? "ON" : "OFF"), color);
    }
    //endregion
    //region Pause Menu
    private void drawPauseMenu() {
        Graphics2D g2d = bufferedImage.createGraphics();
        try {
            pauseMenu.setStatus(hudVisible, pauseFilterName);
            pauseMenu.paint(g2d, bufferedImage.getWidth(), bufferedImage.getHeight());
        }
        finally { g2d.dispose(); }
    }
    private void onPauseItem(PauseMenuState.Item item) {
        switch (item) {
            case RESUME -> setPaused(false);
            case RESTART -> restartGame();
            case TOGGLE_HUD -> hudVisible = !hudVisible;
            case CYCLE_FILTER -> cyclePauseFilter();
            case MAIN_MENU -> returnToMainMenu();
            default -> { }
        }
    }
    private void cyclePauseFilter() {
        java.util.List<Filter> filters = FilterRegistry.create();
        String id = Settings.getString("filters.current", "none");
        int index = -1;
        for (int i = 0; i < filters.size(); i++) if (filters.get(i).id().equals(id)) index = i;
        int next = index + 1 < filters.size() ? index + 1 : -1;
        Settings.set("filters.current", next < 0 ? "none" : filters.get(next).id());
        pauseFilterName = currentFilterName();
    }
    private String currentFilterName() {
        String id = Settings.getString("filters.current", "none");
        for (Filter filter : FilterRegistry.create()) {
            if (filter.id().equals(id)) return NSLocalizedString.get(filter.nameKey());
        }
        return NSLocalizedString.get("filter.none");
    }
    private void returnToMainMenu() {
        isGameRunning = false;
        showCursor();
        RCJMS.instance.changeView(RCJMS.instance.mainMenuView = new MainMenuView(), "main_menu");
        gameThread.interrupt();
    }
    //endregion
    //endregion

    //region Floor Transitions
    private void handleStairs(int cellType) {
        boolean onStairsNow = cellType == 3 || cellType == 4;

        if (onStairsNow && !onStairsLastFrame && !transition.active()) {
            int targetFloor = currentFloor;
            if (cellType == 3 && currentFloor < map3D.length - 1) targetFloor = currentFloor + 1;
            else if (cellType == 4 && currentFloor > 0) targetFloor = currentFloor - 1;

            if (targetFloor != currentFloor) {
                transition.beginClimb(currentFloor, targetFloor, playerX, playerY);
                hasLastMousePos = false;
            }
        }
        onStairsLastFrame = onStairsNow;
    }
    //endregion

    //region Maze
    //region Maze Generation
    private long chooseSeed() {
        mazeSeedText = customSeed != null ? customSeed : SeedUtil.randomSeed();
        return SeedUtil.toLong(mazeSeedText);
    }
    private void applyShared(MazeData data) {
        mazeSeedText = data.seed();
        customSeed = data.seed();
        GEOMETRY_MODE = MazeGenerator.GeometryMode.values()[Math.clamp(data.geometry(), 0, MazeGenerator.GeometryMode.values().length - 1)];
        MAZE_MODE = MazeGenerator.FinishMode.values()[Math.clamp(data.finishMode(), 0, MazeGenerator.FinishMode.values().length - 1)];
        mazeAlgorithmId = data.algorithm();
        MAZE_HAS_FLOORS = data.hasFloors();
        MAZE_FLOORS = data.floors();
        requestedWidth = data.width();
        requestedHeight = data.height();
        mazeGenerator = null;
        mazeGenerator3D = null;
        currentFloor = 0;
        if (data.hasFloors()) {
            map3D = new int[data.floors()][][];
            floorPortalsAll = new PortalData[data.floors()];
            for (int f = 0; f < data.floors(); f++) {
                map3D[f] = copyCells(data.cells()[f]);
                floorPortalsAll[f] = data.portals()[f].copy();
            }
            map = map3D[0];
            portals = floorPortalsAll[0];
        }
        else {
            map3D = null;
            floorPortalsAll = null;
            map = copyCells(data.cells()[0]);
            portals = data.portals()[0].copy();
        }
    }
    private static int[][] copyCells(int[][] cells) {
        int[][] copy = new int[cells.length][];
        for (int y = 0; y < cells.length; y++) copy[y] = cells[y].clone();
        return copy;
    }

    private void regenerateMaze() {
        LevelSpec level = pendingLevel;
        pendingLevel = null;
        int width = level != null ? level.width() : mapWidth();
        int height = level != null ? level.height() : mapHeight();
        MazeAlgorithm chosenAlgorithm = level != null && level.algorithm() != null ? MazeAlgorithms.find(level.algorithm()) : MazeAlgorithms.find(mazeAlgorithmId);
        mazeAlgorithmId = chosenAlgorithm.id();
        if (level != null) {
            if (level.finish() != null) MAZE_MODE = level.finish();
            MAZE_HAS_FLOORS = level.floors() > 1;
            MAZE_FLOORS = MAZE_HAS_FLOORS ? level.floors() : 1;
            GEOMETRY_MODE = level.geometry();
            customSeed = level.seed();
            requestedWidth = width;
            requestedHeight = height;
        }
        mapEngine = null;

        if (sharedMaze != null) applyShared(sharedMaze);
        else if (MAZE_HAS_FLOORS) {
            mazeGenerator3D = new MazeGenerator3D(width, height, MAZE_FLOORS, chooseSeed(), GEOMETRY_MODE).useAlgorithm(chosenAlgorithm);
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
            mazeGenerator = new MazeGenerator(width, height, chooseSeed(), GEOMETRY_MODE).useAlgorithm(chosenAlgorithm);
            map = mazeGenerator.generate(MAZE_MODE);
            portals = mazeGenerator.getPortals();
        }

        playerX = playerY = 1.5;
        cameraAngle = cameraPitch = 0;
        miniMap.resetFog();
        onStairsLastFrame = false;
        transition.end();
        gameStartTime = System.currentTimeMillis();
        pausedTime = 0;
        renderPrepPending = true;
    }
    private boolean hasFloors() { return map3D != null && map3D.length > 1; }
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
                GameView gameView = sharedMaze != null ? new GameView(sharedMaze)
                        : hasFloors() ? new GameView(customFloorsForRestart, customPortalsForRestart, customGeometryModeForRestart) : new GameView(map);
                gameView.setFogOfWarMinimap(miniMap.fogEnabled());
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
    record LevelSpec(int width, int height, int floors, String seed, MazeGenerator.GeometryMode geometry,
                             String algorithm, MazeGenerator.FinishMode finish) {
        LevelSpec(int width, int height, int floors, String seed, MazeGenerator.GeometryMode geometry) {
            this(width, height, floors, seed, geometry, null, null);
        }
    }
    //endregion
    //endregion

    //region Mini Map Bridge
    static double uiScale() { return RCJMS.GAME_WIDTH / (double) RCJMS.SCREEN_WIDTH; }
    public void setFogOfWarMinimap(boolean enabled) { miniMap.setFogEnabled(enabled); }
    private void syncMiniMap() {
        miniMap.sync(pixels, bufferedImage, map, playerX, playerY, cameraAngle, GEOMETRY_MODE == MazeGenerator.GeometryMode.WRONG, currentFloor, floorCount());
    }
    public static volatile java.util.function.IntFunction<java.util.List<MiniMapMarker>> miniMapMarkers;
    public record MiniMapMarker(double x, double y, int color, String label) {}
    //endregion

    //region Module Hooks
    private void notifyRunStart() { eachModule(feature -> feature.onRunStart(gameContext)); }
    private void notifyRunFinish() { eachModule(feature -> feature.onRunFinish(gameContext)); }
    private void notifyPause(boolean paused) { eachModule(feature -> feature.onPauseChanged(gameContext, paused)); }
    private void notifyUpdate(double seconds) { eachModule(feature -> feature.update(gameContext, seconds)); }
    private boolean notifyKey(KeyEvent keyEvent, boolean down) {
        boolean[] consumed = {false};
        eachModule(feature -> {
            if (!consumed[0] && feature.onKey(gameContext, keyEvent, down)) consumed[0] = true;
        });
        return consumed[0];
    }
    private void eachModule(java.util.function.Consumer<GameModule> action) {
        for (GameModule feature : features) {
            if (brokenModules.contains(feature)) continue;
            try {
                action.accept(feature);
                moduleFailures.remove(feature);
            }
            catch (RuntimeException exception) {
                int failures = moduleFailures.merge(feature, 1, Integer::sum);
                if (failures == 1) console.log(DevConsole.Level.WARN, "feature " + feature.id() + " failed: " + exception);
                if (failures >= MODULE_FAILURE_LIMIT) {
                    brokenModules.add(feature);
                    console.log(DevConsole.Level.WARN, "feature " + feature.id() + " was switched off after repeated failures: " + exception);
                }
            }
        }
    }
    private void paintModules() {
        int width = bufferedImage.getWidth(), height = bufferedImage.getHeight();
        Graphics2D g2d = bufferedImage.createGraphics();
        try {
            if (hudVisible) eachModule(feature -> feature.paintHud(gameContext, g2d, width, height));
            drawFlash(g2d, width, height);
        } finally { g2d.dispose(); }
        eachModule(feature -> feature.postProcess(gameContext, pixels, width, height));
    }
    private void drawFlash(Graphics2D g2d, int width, int height) {
        String text = flashText;
        if (text.isEmpty() || System.currentTimeMillis() >= flashUntilMillis) return;
        g2d.setFont(SMALL_SANS_FONT);
        FontMetrics metrics = g2d.getFontMetrics();
        int textWidth = metrics.stringWidth(text);
        int x = (width - textWidth) / 2, baseline = height - 24;
        g2d.setColor(new Color(0, 0, 0, 170));
        g2d.fillRoundRect(x - 10, baseline - metrics.getAscent() - 4, textWidth + 20, metrics.getHeight() + 8, 12, 12);
        g2d.setColor(Color.WHITE);
        g2d.drawString(text, x, baseline);
    }
    private final class ModuleContext implements GameContext {
        @Override public double playerX() { return playerX; }
        @Override public double playerY() { return playerY; }
        @Override public double cameraAngle() { return cameraAngle; }
        @Override public double cameraPitch() { return cameraPitch + transition.stepPitchOffset(); }
        @Override public double eyeHeight() { return Math.clamp(cameraHeight + viewBob() + transition.stepHeightOffset(), 0.05, 0.95); }
        @Override public void moveTo(double x, double y, double angle) { playerX = x; playerY = y; cameraAngle = angle; }
        @Override public int[][] map() { return map; }
        @Override public int[][] floorMap(int floor) { return map3D == null ? map : map3D[floor]; }
        @Override public PortalData portalsOf(int floor) {
            return floorPortalsAll != null && floor >= 0 && floor < floorPortalsAll.length ? floorPortalsAll[floor] : portals;
        }
        @Override public int currentFloor() { return currentFloor; }
        @Override public int floorCount() { return map3D == null ? 1 : map3D.length; }
        @Override public boolean hasFloors() { return GameView.this.hasFloors(); }
        @Override public int mazeWidth() { return mapWidth(); }
        @Override public int mazeHeight() { return mapHeight(); }
        @Override public boolean isPaused() { return isPaused; }
        @Override public double elapsedSeconds() { return elapsedSeconds; }
        @Override public String seed() { return isCustomMap ? "" : mazeSeedText; }
        @Override public boolean isCustomMap() { return isCustomMap; }
        @Override public int geometry() { return GEOMETRY_MODE.ordinal(); }
        @Override public boolean fogOfWar() { return miniMap.fogEnabled(); }
        @Override public String algorithm() { return mazeAlgorithmId; }
        @Override public int finishMode() { return MAZE_MODE.ordinal(); }
        @Override public void startSeeded(String seed, int width, int height, int floors, int geometry, boolean hasFloors, boolean fog,
                                          String algorithm, int finishMode) {
            if (isCustomMap) return;
            sharedMaze = null;
            String normalized = SeedUtil.normalize(seed);
            MazeGenerator.GeometryMode mode = MazeGenerator.GeometryMode.values()[Math.clamp(geometry, 0, MazeGenerator.GeometryMode.values().length - 1)];
            setFogOfWarMinimap(fog);
            MazeGenerator.FinishMode finish = MazeGenerator.FinishMode.values()[Math.clamp(finishMode, 0, MazeGenerator.FinishMode.values().length - 1)];
            restartGame(new LevelSpec(width, height, hasFloors ? Math.max(2, floors) : 1, normalized.isEmpty() ? null : normalized, mode, MazeAlgorithms.find(algorithm).id(), finish));
        }
        @Override public void flash(String text) {
            flashText = text == null ? "" : text;
            flashUntilMillis = System.currentTimeMillis() + 2500;
        }
        @Override public void restart() { restartGame(); }
        @Override public void useMaze(MazeData maze, boolean fog) {
            setFogOfWarMinimap(fog);
            sharedMaze = maze;
            restartGame();
        }
        @Override public void clearMaze() { sharedMaze = null; }
        @Override public MazeData currentMaze() {
            if (!isCustomMap && sharedMaze == null) return null;
            int[][][] cells = map3D != null ? map3D : new int[][][]{map};
            PortalData[] linked = floorPortalsAll != null ? floorPortalsAll : new PortalData[]{portals};
            int[][][] cellCopy = new int[cells.length][][];
            for (int f = 0; f < cells.length; f++) cellCopy[f] = copyCells(cells[f]);
            PortalData[] portalCopy = new PortalData[linked.length];
            for (int f = 0; f < linked.length; f++) portalCopy[f] = linked[f].copy();
            return new MazeData(isCustomMap ? MazeData.CUSTOM_SEED : mazeSeedText, mapWidth(), mapHeight(), cellCopy.length,
                    cellCopy.length > 1, GEOMETRY_MODE.ordinal(), mazeAlgorithmId, MAZE_MODE.ordinal(), cellCopy, portalCopy);
        }
        @Override public void command(String name, String usage, DevConsole.Command command) { console.register(name, usage, command); }
        @Override public boolean hudVisible() { return hudVisible; }
        @Override public void setHudVisible(boolean visible) { hudVisible = visible; }
    }
    //endregion

    //region Text Mode
    private void textKey(KeyEvent keyEvent) {
        int key = keyEvent.getKeyCode();
        if (key == KeyEvent.VK_ENTER) {
            String line = textInput.toString();
            textInput.setLength(0);
            textCommand(line);
        }
        else if (key == KeyEvent.VK_BACK_SPACE && !textInput.isEmpty()) textInput.setLength(textInput.length() - 1);
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
            double[] base = {cameraAngle};
            for (int i = 0; i < count; i++) {
                if (!textStep(dir, base)) {
                    textStatus = "blocked after " + steps + " steps, at " + token;
                    return;
                }
                steps++;
                if (onFinishCell()) return;
            }
        }
        textStatus = steps + " steps" + (turned ? ", turned" : "");
    }
    private boolean textStep(char dir, double[] base) {
        int fx = (int) Math.round(Math.cos(base[0])), fy = (int) Math.round(Math.sin(base[0]));
        int vx, vy;
        switch (dir) {
            case 'f' -> { vx = fx; vy = fy; }
            case 'b' -> { vx = -fx; vy = -fy; }
            case 'l' -> { vx = fy; vy = -fx; }
            default -> { vx = -fy; vy = fx; }
        }
        int tx = (int) Math.floor(playerX) + vx, ty = (int) Math.floor(playerY) + vy;
        if (!noClip && isSolidForPlayer(map, tx, ty)) return false;
        double startX = playerX, startY = playerY;
        playerX = tx + 0.5;
        playerY = ty + 0.5;
        cameraAngle = Math.atan2(vy, vx);
        if (applyPortalStep(startX, startY, vx, vy)) base[0] = cameraAngle;
        return true;
    }
    private boolean onFinishCell() {
        int cellY = Math.clamp((int) playerY, 0, mapHeight() - 1);
        int cellX = Math.clamp((int) playerX, 0, mapWidth() - 1);
        return isGameRunning && map[cellY][cellX] == MazeGenerator.FINISH;
    }
    private void snapToTile() {
        playerX = Math.floor(playerX) + 0.5;
        playerY = Math.floor(playerY) + 0.5;
        cameraAngle = Math.floorMod(Math.round(cameraAngle / (Math.PI / 2)), 4L) * (Math.PI / 2);
        cameraPitch = 0;
    }
    private void renderText() {
        snapToTile();
        syncMiniMap();
        int w = bufferedImage.getWidth(), h = bufferedImage.getHeight();
        textScreen = TextMode.build(w, h, map, miniMap.exploredGrid(), playerX, playerY, cameraAngle, textInput.toString(), textStatus);
        Graphics2D g2d = bufferedImage.createGraphics();
        g2d.drawImage(textScreen, 0, 0, null);
        g2d.dispose();
        if (isPaused) drawPauseMenu();
    }
    //endregion

    //region Console Bridge
    private final class ConsoleBridge implements GameConsoleCommands.Host {
        @Override public DevConsole console() { return console; }
        @Override public boolean busy() { return isBusy(); }
        @Override public boolean paused() { return isPaused; }
        @Override public void setPaused(boolean paused) { GameView.this.setPaused(paused); }
        @Override public boolean customMap() { return isCustomMap; }
        @Override public int mapWidth() { return GameView.this.mapWidth(); }
        @Override public int mapHeight() { return GameView.this.mapHeight(); }
        @Override public boolean hasFloors() { return GameView.this.hasFloors(); }
        @Override public int floorCount() { return GameView.this.floorCount(); }
        @Override public int[][][] floorLayers() { return hasFloors() ? map3D : new int[][][]{map}; }
        @Override public MazeGenerator.GeometryMode geometry() { return GEOMETRY_MODE; }
        @Override public MazeGenerator.FinishMode finishMode() { return MAZE_MODE; }
        @Override public String seedText() { return mazeSeedText; }
        @Override public RenderEngine currentEngine() { return GameView.currentEngine(); }
        @Override public String renderEngineId() { return renderEngineId; }
        @Override public void setRenderEngineId(String id) { renderEngineId = id; }
        @Override public boolean openClAcceleration() { return openClAcceleration; }
        @Override public void setOpenClAcceleration(boolean enabled) { GameView.setOpenClAcceleration(enabled); }
        @Override public void setDetailedLog(boolean enabled) {
            verboseLog = enabled;
            detailKey = null;
            detailFrames = 0;
            detailNanos = 0;
        }
        @Override public void restart(LevelSpec level) { restartGame(level); }
    }
    //endregion
    //region Console
    //region Console Toggle
    private void toggleConsole() {
        boolean wasOpen = console.isOpen();
        console.toggle();
        if (console.isOpen()) {
            releaseInput();
            showCursor();
        }
        applyConsoleClosed(wasOpen);
        repaint();
    }
    private void consoleKey(KeyEvent keyEvent) {
        boolean wasOpen = console.isOpen();
        console.handleKey(keyEvent);
        String line = console.takeSubmitted();
        if (line != null) console.execute(line);
        applyConsoleClosed(wasOpen);
        repaint();
    }
    private void applyConsoleClosed(boolean wasOpen) { if (wasOpen && !console.isOpen() && !isPaused) hideCursor(); }
    //endregion
    //endregion
}