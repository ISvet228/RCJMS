import Helpers.*;
import MainMenuComponents.*;
import StyleUI.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

public class MainMenuView extends JPanel implements MainMenuHost {
    //region Variables
    private enum Screen { MAIN, PLAY, SETTINGS, CONTROLS }

    private static final Color BACKGROUND = new Color(15, 15, 35);
    private static final Color DIM = new Color(0, 0, 0, 120);

    private final ArrayList<Star> stars = new ArrayList<>();
    private final Random random = new Random();
    private final MainMenuCanvas canvas = new MainMenuCanvas(RCJMS.SCREEN_WIDTH, RCJMS.SCREEN_HEIGHT);
    private final ArrayList<JComponent> mainComponents = new ArrayList<>();
    private final ArrayList<MainMenuPanel> screens = new ArrayList<>();
    private final Timer starTimer;
    private static final long IDLE_BEFORE_DRIFT_MS = 15_000;
    private static final double DRIFT_CENTER_X = 480, DRIFT_CENTER_Y = 343;
    private static final double DRIFT_SPEED_MIN = 22, DRIFT_SPEED_MAX = 38;
    private static final double DRIFT_SPIN = 0.8;
    private static final double LINEAR_DAMPING = 0.25, SPIN_DAMPING = 0.35;
    private static final double KICK_THRESHOLD = 6, KICK_COOLDOWN = 0.3;
    private static final double KICK_SPEED_MIN = 24, KICK_SPEED_MAX = 34, KICK_SPIN = 0.9;
    private static final double WALL_BOUNCE = 0.6, BODY_BOUNCE = 0.55;
    private static final double MAX_DRIFT_SPEED = 60, MAX_DRIFT_SPIN = 2.5;
    private static final double RETURN_STIFFNESS = 28, RETURN_DAMPING = 8.5;
    private static final double WALL_MARGIN = 4, PHYSICS_STEP = 1.0 / 120;
    private static final int COLLISION_PASSES = 3;
    private static final float TITLE_FONT_SIZE = 55f;
    private static final double TITLE_BOX_WIDTH = 400, TITLE_BOX_HEIGHT = 126;
    private static final double TITLE_CENTER_X = 480, TITLE_CENTER_Y = 81;
    private static final int TITLE_DEPTH = 6;
    private static final double TITLE_PULSE = 0.06, TITLE_ROTATION = 0.05;
    private enum Phase { REST, DRIFT, RETURN }

    private final ArrayList<Body> bodies = new ArrayList<>();
    private final HashMap<JComponent, Body> bodyByComponent = new HashMap<>();
    private final AWTEventListener activityListener = this::noteActivity;
    private Phase phase = Phase.REST;
    private long lastActivityMillis = System.currentTimeMillis();
    private long lastPhysicsNanos = System.nanoTime();

    private Style currentStyle = SaveData.load().theme;
    private Screen screen = Screen.MAIN;
    private boolean fullscreen = false;
    private int windowWidth = RCJMS.SCREEN_WIDTH;
    private int windowHeight = RCJMS.SCREEN_HEIGHT;

    private final MenuTitle title = new MenuTitle("RCJMS", Animated3DText.AnimationType.ROTATE);
    private final StyledLabel tagline = new StyledLabel(currentStyle, "mm.tagline", false, false);
    private final StyledButton playButton = createButton("mm.play");
    private final StyledButton mapEditorButton = createButton("mm.map_editor");
    private final StyledButton textureEditorButton = createButton("mm.texture_editor");
    private final StyledButton settingsButton = createButton("mm.settings");
    private final StyledButton creditsButton = createButton("mm.credits");
    private final StyledButton exitButton = createButton("exit");
    private final StyledButton controlsButton = createButton("mm.controls");

    private final MainMenuPlayScreen playScreen;
    private final MainMenuSettingsScreen settingsScreen;
    private final MainMenuControlsScreen controlsScreen;
    //endregion

    //region Constructors
    public MainMenuView() {
        applySavedSettings();
        setPreferredSize(new Dimension(RCJMS.SCREEN_WIDTH, RCJMS.SCREEN_HEIGHT));
        setLayout(null);

        playScreen = new MainMenuPlayScreen(this);
        settingsScreen = new MainMenuSettingsScreen(this);
        controlsScreen = new MainMenuControlsScreen(this);
        screens.add(playScreen);
        screens.add(settingsScreen);
        screens.add(controlsScreen);

        setupTitle();
        tagline.setForeground(new Color(200, 200, 215));
        setupMainButtons();
        addMainComponents();
        for (MainMenuPanel panel : screens) add(panel);
        installKeys();
        showScreen(Screen.MAIN);

        for (int i = 0; i < 80; i++) stars.add(createRandomStar());
        starTimer = new Timer(16, e -> { updateStars(); updateDrift(); repaint(); });
        starTimer.start();
    }
    @Override public void addNotify() {
        super.addNotify();
        if (starTimer != null && !starTimer.isRunning()) starTimer.start();
        Toolkit.getDefaultToolkit().addAWTEventListener(activityListener,
                AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.KEY_EVENT_MASK);
    }
    @Override public void removeNotify() {
        Toolkit.getDefaultToolkit().removeAWTEventListener(activityListener);
        if (starTimer != null && starTimer.isRunning()) starTimer.stop();
        super.removeNotify();
    }
    //endregion

    //region Public API
    public void syncFullscreen(boolean value) {
        fullscreen = value;
        settingsScreen.syncFullscreen(value);
    }
    @Override public int screenWidth() { return RCJMS.SCREEN_WIDTH; }
    @Override public int screenHeight() { return RCJMS.SCREEN_HEIGHT; }
    @Override public Style style() { return currentStyle; }
    @Override public int windowWidth() { return windowWidth; }
    @Override public boolean isFullscreen() { return fullscreen; }
    @Override public void showMain() { showScreen(Screen.MAIN); }
    @Override public void startGame() {
        playScreen.saveMazeSettings();
        try {
            GameView gameView = createGameView(playScreen.mazeWidth(), playScreen.mazeHeight(), playScreen.finishMode(), playScreen.geometry(), playScreen.seed(), playScreen.layers());
            gameView.setFogOfWarMinimap(playScreen.fogMinimap());
            RCJMS.instance.changeView(RCJMS.instance.gameView = gameView, "RayCast Me!");
            RCJMS.instance.gameView.start();
        }
        catch (NumberFormatException | IOException ex) {
            JOptionPane.showMessageDialog(this, "mm.maze_size_error", "mm.invalid_input", JOptionPane.ERROR_MESSAGE);
        }
    }
    @Override public void changeStyle(Style next) {
        if (next == currentStyle) return;
        currentStyle = next;
        saveSettings();
        canvas.applyStyle(this, currentStyle);
        for (MainMenuPanel panel : screens) panel.applyStyle(currentStyle);
    }
    @Override public void changeLanguage(String code) {
        if (code.equalsIgnoreCase(NSLocalizedString.getLanguage())) return;
        saveSettings(code, currentStyle);
        NSLocalizedString.setLanguage(code);
    }
    @Override public void applyFullscreen(boolean value) {
        fullscreen = value;
        RCJMS.instance.setFullscreen(value, false);
    }
    @Override public void setWindowSize(int width, int height) {
        windowWidth = width;
        windowHeight = height;
        RCJMS.GAME_WIDTH = width;
        RCJMS.GAME_HEIGHT = height;
        if (!fullscreen) applyWindowSize(width, height);
        saveSettings();
    }
    @Override public void saveSettings() { saveSettings(NSLocalizedString.getLanguage(), currentStyle); }
    @Override public double getRenderScale() { return GameView.getRenderScale(); }
    @Override public void setRenderScale(double scale) { GameView.setRenderScale(scale); }
    @Override public boolean isVSyncEnabled() { return GameView.isVSyncEnabled(); }
    @Override public void setVSyncEnabled(boolean enabled) { GameView.setVSyncEnabled(enabled); }
    @Override public boolean isRayTracingEnabled() { return GameView.isRayTracingEnabled(); }
    @Override public void setRayTracingEnabled(boolean enabled) { GameView.setRayTracingEnabled(enabled); }
    @Override public int getRayTracingQuality() { return GameView.getRayTracingQuality(); }
    @Override public void setRayTracingQuality(int quality) { GameView.setRayTracingQuality(quality); }
    //endregion

    //region Screens
    private void showScreen(Screen next) {
        screen = next;
        for (JComponent component : mainComponents) component.setVisible(next == Screen.MAIN);
        playScreen.setVisible(next == Screen.PLAY);
        settingsScreen.setVisible(next == Screen.SETTINGS);
        controlsScreen.setVisible(next == Screen.CONTROLS);
        if (next == Screen.PLAY) playScreen.refresh();
        if (next == Screen.SETTINGS) settingsScreen.refresh();
        repaint();
    }
    private void setupMainButtons() {
        playButton.addActionListener(e -> showScreen(Screen.PLAY));
        settingsButton.addActionListener(e -> showScreen(Screen.SETTINGS));
        controlsButton.addActionListener(e -> showScreen(Screen.CONTROLS));
        mapEditorButton.addActionListener(e -> RCJMS.instance.changeView(RCJMS.instance.mapEditorView = new MapEditorView(), "me.map_editor"));
        textureEditorButton.addActionListener(e -> {
            try { RCJMS.instance.changeView(RCJMS.instance.textureEditorView = new TextureEditorView(), "te.texture_editor"); }
            catch (IOException ex) { throw new RuntimeException(ex); }
        });
        creditsButton.addActionListener(e -> RCJMS.instance.changeView(RCJMS.instance.creditsView = new CreditsView(), "cv.credits"));
        exitButton.addActionListener(e -> System.exit(0));
    }
    private void installKeys() {
        InputMap inputMap = getInputMap(WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = getActionMap();
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "menuBack");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "menuConfirm");
        actionMap.put("menuBack", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { if (screen != Screen.MAIN) showScreen(Screen.MAIN); }
        });
        actionMap.put("menuConfirm", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { if (screen == Screen.PLAY) startGame(); }
        });
    }
    //endregion

    //region Game Launch
    private GameView createGameView(int w, int h, int mode, int geometry, String seed, int layers) throws IOException {
        if (playScreen.is3D()) return seed == null ? new GameView(w, h, mode, geometry, layers) : new GameView(w, h, mode, geometry, seed, layers);
        return seed == null ? new GameView(w, h, mode, geometry) : new GameView(w, h, mode, geometry, seed);
    }
    //endregion

    //region Settings Helpers
    private void applyWindowSize(int width, int height) {
        if (fullscreen) return;
        JFrame frame = RCJMS.instance;
        if (frame == null) return;
        frame.setSize(width, height);
        frame.setLocationRelativeTo(null);
        frame.revalidate();
        frame.repaint();
    }
    private void saveSettings(String language, Style style) {
        try { SaveData.saveSettings(language, style, GameView.getRenderScale(), GameView.isVSyncEnabled(), GameView.isRayTracingEnabled(), GameView.getRayTracingQuality(), windowWidth, windowHeight, fullscreen); }
        catch (IOException ex) { ex.printStackTrace(); }
    }
    private void applySavedSettings() {
        SaveData.Data savedData = SaveData.load();
        GameView.setRenderScale(savedData.renderScale);
        GameView.setVSyncEnabled(savedData.vsync);
        GameView.setRayTracingEnabled(savedData.rayTracing);
        GameView.setRayTracingQuality(savedData.rayTracingQuality);
        NSLocalizedString.setLanguage(savedData.language);
        windowWidth = savedData.windowWidth;
        windowHeight = savedData.windowHeight;
        fullscreen = savedData.fullscreen;
        RCJMS.GAME_WIDTH = windowWidth;
        RCJMS.GAME_HEIGHT = windowHeight;
    }
    //endregion

    //region Layout
    @Override public void doLayout() {
        if (!canvas.measure(this)) return;
        if (canvas.sizeChanged(this)) canvas.resetStyleUi(this);
        for (MainMenuPanel panel : screens) panel.setBounds(0, 0, getWidth(), getHeight());
        fitTitle();
        canvas.text(tagline, 16, 230, 146, 500, 24);
        record(tagline, 230, 146, 500, 24);
        record(playButton, 320, 186, 320, 60);
        record(mapEditorButton, 320, 256, 320, 42);
        record(textureEditorButton, 320, 306, 320, 42);
        record(settingsButton, 320, 356, 320, 42);
        record(creditsButton, 320, 406, 320, 42);
        record(exitButton, 320, 462, 320, 38);
        record(controlsButton, 20, 486, 170, 34);
        applyBodies();
    }
    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (!canvas.measure(this)) return;

        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, getWidth(), getHeight());
        g2d.translate(canvas.x(0), canvas.y(0));
        g2d.scale(canvas.scale(), canvas.scale());
        g2d.setColor(BACKGROUND);
        g2d.fillRect(0, 0, RCJMS.SCREEN_WIDTH, RCJMS.SCREEN_HEIGHT);

        for (Star star : stars) drawStar(g2d, star);
        if (screen == Screen.MAIN) paintMenuBackdrop(g2d);
        else {
            g2d.setColor(DIM);
            g2d.fillRect(0, 0, RCJMS.SCREEN_WIDTH, RCJMS.SCREEN_HEIGHT);
        }
        g2d.dispose();
    }
    private void paintMenuBackdrop(Graphics2D g2d) {
        g2d.setPaint(new RadialGradientPaint(new Point2D.Float(RCJMS.SCREEN_WIDTH / 2f, 280f), 430f,
                new float[]{0f, 0.5f, 1f},
                new Color[]{new Color(15, 15, 35, 200), new Color(15, 15, 35, 150), new Color(15, 15, 35, 0)}));
        g2d.fillRect(0, 0, RCJMS.SCREEN_WIDTH, RCJMS.SCREEN_HEIGHT);
    }
    //endregion

    //region Stars
    private void drawStar(Graphics2D g2d, Star star) {
        AffineTransform old = g2d.getTransform();
        g2d.translate(star.x, star.y);
        g2d.rotate(Math.toRadians(star.rotation));
        int s = star.size;

        Polygon p = new Polygon();
        p.addPoint(0, -s);
        p.addPoint(s / 4, -s / 4);
        p.addPoint(s, 0);
        p.addPoint(s / 4, s / 4);
        p.addPoint(0, s);
        p.addPoint(-s / 4, s / 4);
        p.addPoint(-s, 0);
        p.addPoint(-s / 4, -s / 4);

        g2d.setColor(new Color(255, 255, 180, 80));
        g2d.fillOval(-s, -s, s * 2, s * 2);
        g2d.setColor(Color.WHITE);
        g2d.fillPolygon(p);
        g2d.setTransform(old);
    }
    private Star createRandomStar() {
        return new Star(random.nextInt(RCJMS.SCREEN_WIDTH), random.nextInt(RCJMS.SCREEN_HEIGHT), 15 + random.nextInt(30),
                1 + random.nextDouble() * 4, random.nextDouble() * 360, -5 + random.nextDouble() * 10);
    }
    private void updateStars() {
        for (Star star : stars) {
            star.y += star.speed;
            star.rotation += star.rotationSpeed;
            if (star.y - star.size > RCJMS.SCREEN_HEIGHT) {
                star.y = -star.size;
                star.x = random.nextInt(RCJMS.SCREEN_WIDTH);
                star.speed = 1 + random.nextDouble() * 4;
            }
        }
    }
    //endregion

    //region Helpers
    private void setupTitle() {
        title.setAutoScale(false);
        title.setAnimationSpeed(1.0);
        title.setPulseAmount(TITLE_PULSE);
        title.setRotationAmount(TITLE_ROTATION);
        title.setDepth(TITLE_DEPTH);
        title.setTextColor(new Color(255, 127, 0));
        title.setDepthColor(Color.cyan);
        title.addActionListener((ActionEvent e) -> {
            title.setText(title.getText().equals("RCJMS") ? "RayCasting Java Maze Simulator" : "RCJMS");
            fitTitle();
            applyBodies();
        });
    }
    private void fitTitle() {
        double scale = canvas.scale();
        FontMetrics probe = title.getFontMetrics(canvas.font(TITLE_FONT_SIZE, Font.BOLD));
        double textWidth = probe.stringWidth(title.getText()) / scale, textHeight = probe.getHeight() / scale;
        double sin = Math.sin(TITLE_ROTATION), cos = Math.cos(TITLE_ROTATION), pulse = 1 + TITLE_PULSE;
        double rotatedWidth = textWidth * cos + textHeight * sin * pulse, rotatedHeight = textWidth * sin + textHeight * cos * pulse;
        double fit = Math.min((TITLE_BOX_WIDTH - 2 * TITLE_DEPTH + 12) / rotatedWidth, (TITLE_BOX_HEIGHT - 2 * TITLE_DEPTH + 12) / rotatedHeight);
        title.setBaseFont(canvas.font((float) (TITLE_FONT_SIZE * fit), Font.BOLD));
        FontMetrics fitted = title.getFontMetrics(title.getBaseFont());
        double width = fitted.stringWidth(title.getText()) / scale + 2 * TITLE_DEPTH;
        double height = fitted.getHeight() / scale + 2 * TITLE_DEPTH;
        record(title, TITLE_CENTER_X - width / 2, TITLE_CENTER_Y - height / 2, width, height);
    }
    private void addMainComponents() {
        mainComponents.add(title);
        mainComponents.add(tagline);
        mainComponents.add(playButton);
        mainComponents.add(mapEditorButton);
        mainComponents.add(textureEditorButton);
        mainComponents.add(settingsButton);
        mainComponents.add(creditsButton);
        mainComponents.add(exitButton);
        mainComponents.add(controlsButton);
        for (JComponent component : mainComponents) add(component);
    }
    private StyledButton createButton(String key) {
        StyledButton button = new MenuButton(currentStyle, key);
        button.setFocusable(false);
        return button;
    }
    //endregion

    //region Idle space mode
    private void noteActivity(AWTEvent event) {
        if (event instanceof InputEvent input && input.getSource() instanceof Component source && SwingUtilities.isDescendingFrom(source, this))
            lastActivityMillis = System.currentTimeMillis();
    }
    private void record(JComponent component, double designX, double designY, double designWidth, double designHeight) {
        Body body = bodyByComponent.get(component);
        if (body == null) {
            body = new Body(component);
            bodies.add(body);
            bodyByComponent.put(component, body);
        }
        body.w = designWidth;
        body.h = designHeight;
        body.homeX = designX + designWidth / 2.0;
        body.homeY = designY + designHeight / 2.0;
        if (phase == Phase.REST) {
            body.x = body.homeX;
            body.y = body.homeY;
            body.vx = body.vy = body.spin = body.angle = 0;
        }
    }
    private void applyBodies() {
        if (!canvas.measure(this)) return;
        for (Body body : bodies) {
            canvas.placeCentered(body.component, body.x, body.y, body.w, body.h);
            if (body.component instanceof MenuButton button) button.angle = body.angle;
            if (body.component instanceof MenuTitle titleText) titleText.angle = body.angle;
        }
    }
    private void updateDrift() {
        long now = System.nanoTime();
        double dt = Math.min(0.05, (now - lastPhysicsNanos) / 1e9);
        lastPhysicsNanos = now;
        boolean idle = screen == Screen.MAIN && System.currentTimeMillis() - lastActivityMillis >= IDLE_BEFORE_DRIFT_MS;
        if (phase == Phase.REST && idle) startDrift();
        else if (phase == Phase.DRIFT && !idle) beginReturn();
        else if (phase == Phase.RETURN && idle) phase = Phase.DRIFT;
        if (phase == Phase.REST) return;
        stepPhysics(dt);
        applyBodies();
    }
    private void startDrift() {
        phase = Phase.DRIFT;
        for (Body body : bodies) {
            double dx = body.homeX - DRIFT_CENTER_X, dy = body.homeY - DRIFT_CENTER_Y;
            double length = Math.hypot(dx, dy);
            if (length < 1) {
                double direction = random.nextDouble() * Math.PI * 2;
                dx = Math.cos(direction);
                dy = Math.sin(direction);
                length = 1;
            }
            double speed = DRIFT_SPEED_MIN + random.nextDouble() * (DRIFT_SPEED_MAX - DRIFT_SPEED_MIN);
            body.vx = dx / length * speed;
            body.vy = dy / length * speed;
            body.spin = (random.nextDouble() * 2 - 1) * DRIFT_SPIN;
            body.kickCooldown = 0;
        }
    }
    private void beginReturn() {
        phase = Phase.RETURN;
        for (Body body : bodies) body.angle = Math.IEEEremainder(body.angle, Math.PI * 2);
    }
    private void stepPhysics(double dt) {
        int steps = Math.max(1, (int) Math.ceil(dt / PHYSICS_STEP));
        double h = dt / steps;
        for (int i = 0; i < steps && phase != Phase.REST; i++) {
            if (phase == Phase.DRIFT) integrateDrift(h);
            else integrateReturn(h);
            if (phase == Phase.DRIFT) {
                resolveWalls();
                for (int pass = 0; pass < COLLISION_PASSES; pass++) resolveCollisions();
            }
            if (phase == Phase.RETURN && settledAtHome()) snapHome();
        }
    }
    private void integrateDrift(double h) {
        double linearKeep = Math.max(0, 1 - LINEAR_DAMPING * h), spinKeep = Math.max(0, 1 - SPIN_DAMPING * h);
        for (Body body : bodies) {
            body.vx *= linearKeep;
            body.vy *= linearKeep;
            body.spin *= spinKeep;
            double speed = Math.hypot(body.vx, body.vy);
            if (speed > MAX_DRIFT_SPEED) { body.vx *= MAX_DRIFT_SPEED / speed; body.vy *= MAX_DRIFT_SPEED / speed; }
            body.spin = Math.clamp(body.spin, -MAX_DRIFT_SPIN, MAX_DRIFT_SPIN);
            body.x += body.vx * h;
            body.y += body.vy * h;
            body.angle = Math.IEEEremainder(body.angle + body.spin * h, Math.PI * 2);
            body.kickCooldown -= h;
            if (Math.hypot(body.vx, body.vy) < KICK_THRESHOLD && body.kickCooldown <= 0) kick(body);
        }
    }
    private void kick(Body body) {
        double direction = random.nextDouble() * Math.PI * 2;
        double speed = KICK_SPEED_MIN + random.nextDouble() * (KICK_SPEED_MAX - KICK_SPEED_MIN);
        body.vx = Math.cos(direction) * speed;
        body.vy = Math.sin(direction) * speed;
        body.spin += (random.nextDouble() * 2 - 1) * KICK_SPIN;
        body.kickCooldown = KICK_COOLDOWN;
    }
    private void integrateReturn(double h) {
        for (Body body : bodies) {
            double alpha = -RETURN_STIFFNESS * body.angle - RETURN_DAMPING * body.spin;
            body.vx += (-RETURN_STIFFNESS * (body.x - body.homeX) - RETURN_DAMPING * body.vx) * h;
            body.vy += (-RETURN_STIFFNESS * (body.y - body.homeY) - RETURN_DAMPING * body.vy) * h;
            body.spin += alpha * h;
            body.x += body.vx * h;
            body.y += body.vy * h;
            body.angle += body.spin * h;
        }
    }
    private boolean settledAtHome() {
        for (Body body : bodies)
            if (Math.abs(body.x - body.homeX) > 0.3 || Math.abs(body.y - body.homeY) > 0.3 || Math.abs(body.angle) > 0.003
                    || Math.hypot(body.vx, body.vy) > 0.5 || Math.abs(body.spin) > 0.01) return false;
        return true;
    }
    private void snapHome() {
        for (Body body : bodies) {
            body.x = body.homeX;
            body.y = body.homeY;
            body.vx = body.vy = body.spin = body.angle = 0;
        }
        phase = Phase.REST;
    }
    private void resolveWalls() {
        double areaWidth = RCJMS.SCREEN_WIDTH, areaHeight = RCJMS.SCREEN_HEIGHT;
        for (Body body : bodies) {
            double c = Math.abs(Math.cos(body.angle)), s = Math.abs(Math.sin(body.angle));
            double extentX = c * body.w / 2 + s * body.h / 2, extentY = s * body.w / 2 + c * body.h / 2;
            double minX = WALL_MARGIN + extentX, maxX = areaWidth - WALL_MARGIN - extentX;
            double minY = WALL_MARGIN + extentY, maxY = areaHeight - WALL_MARGIN - extentY;
            if (body.x < minX) {
                body.x = minX;
                if (body.vx < 0) { body.vx = -body.vx * WALL_BOUNCE; body.spin += body.vy * 0.004; }
            } else if (body.x > maxX) {
                body.x = maxX;
                if (body.vx > 0) { body.vx = -body.vx * WALL_BOUNCE; body.spin -= body.vy * 0.004; }
            }
            if (body.y < minY) {
                body.y = minY;
                if (body.vy < 0) { body.vy = -body.vy * WALL_BOUNCE; body.spin -= body.vx * 0.004; }
            } else if (body.y > maxY) {
                body.y = maxY;
                if (body.vy > 0) { body.vy = -body.vy * WALL_BOUNCE; body.spin += body.vx * 0.004; }
            }
        }
    }
    private void resolveCollisions() {
        for (int i = 0; i < bodies.size(); i++)
            for (int j = i + 1; j < bodies.size(); j++) collide(bodies.get(i), bodies.get(j));
    }
    private void collide(Body a, Body b) {
        double dx = b.x - a.x, dy = b.y - a.y;
        double reach = Math.hypot(a.w, a.h) / 2 + Math.hypot(b.w, b.h) / 2;
        if (dx * dx + dy * dy >= reach * reach) return;
        double bestOverlap = Double.MAX_VALUE, nx = 0, ny = 0;
        for (int axis = 0; axis < 4; axis++) {
            Body owner = axis < 2 ? a : b;
            double ax = axis % 2 == 0 ? Math.cos(owner.angle) : -Math.sin(owner.angle);
            double ay = axis % 2 == 0 ? Math.sin(owner.angle) : Math.cos(owner.angle);
            double distance = dx * ax + dy * ay;
            double overlap = radiusOn(a, ax, ay) + radiusOn(b, ax, ay) - Math.abs(distance);
            if (overlap <= 0) return;
            if (overlap < bestOverlap) {
                bestOverlap = overlap;
                nx = distance < 0 ? -ax : ax;
                ny = distance < 0 ? -ay : ay;
            }
        }
        double invA = 1.0 / (a.w * a.h), invB = 1.0 / (b.w * b.h), total = invA + invB;
        a.x -= nx * bestOverlap * invA / total;
        a.y -= ny * bestOverlap * invA / total;
        b.x += nx * bestOverlap * invB / total;
        b.y += ny * bestOverlap * invB / total;
        double[] supportA = corner(a, nx, ny, 1), supportB = corner(b, nx, ny, -1);
        double px = (supportA[0] + supportB[0]) / 2, py = (supportA[1] + supportB[1]) / 2;
        double rax = px - a.x, ray = py - a.y, rbx = px - b.x, rby = py - b.y;
        double relative = (b.vx - b.spin * rby - (a.vx - a.spin * ray)) * nx + (b.vy + b.spin * rbx - (a.vy + a.spin * rax)) * ny;
        if (relative >= 0) return;
        double invIA = 12.0 / (a.w * a.h * (a.w * a.w + a.h * a.h));
        double invIB = 12.0 / (b.w * b.h * (b.w * b.w + b.h * b.h));
        double crossA = rax * ny - ray * nx, crossB = rbx * ny - rby * nx;
        double denominator = invA + invB + invIA * crossA * crossA + invIB * crossB * crossB;
        double impulse = -(1 + BODY_BOUNCE) * relative / denominator;
        a.vx -= impulse * invA * nx;
        a.vy -= impulse * invA * ny;
        a.spin -= impulse * invIA * crossA;
        b.vx += impulse * invB * nx;
        b.vy += impulse * invB * ny;
        b.spin += impulse * invIB * crossB;
    }
    private static double[] corner(Body body, double nx, double ny, double sign) {
        double c = Math.cos(body.angle), s = Math.sin(body.angle);
        double best = -Double.MAX_VALUE, bestX = body.x, bestY = body.y;
        for (int i = -1; i <= 1; i += 2)
            for (int j = -1; j <= 1; j += 2) {
                double lx = i * body.w / 2, ly = j * body.h / 2;
                double wx = body.x + lx * c - ly * s, wy = body.y + lx * s + ly * c;
                double reach = sign * ((wx - body.x) * nx + (wy - body.y) * ny);
                if (reach > best) { best = reach; bestX = wx; bestY = wy; }
            }
        return new double[]{bestX, bestY};
    }
    private static double radiusOn(Body body, double ax, double ay) {
        double c = Math.cos(body.angle), s = Math.sin(body.angle);
        return Math.abs(ax * c + ay * s) * body.w / 2 + Math.abs(-ax * s + ay * c) * body.h / 2;
    }
    @Override protected void paintChildren(Graphics g) {
        Component[] children = getComponents();
        for (int i = children.length - 1; i >= 0; i--) {
            Component child = children[i];
            if (!child.isVisible()) continue;
            Body body = bodyByComponent.get(child);
            if (body != null && body.angle != 0) {
                Graphics2D g2d = (Graphics2D) g.create();
                try {
                    g2d.translate(child.getX() + child.getWidth() / 2.0, child.getY() + child.getHeight() / 2.0);
                    g2d.rotate(body.angle);
                    g2d.translate(-child.getWidth() / 2.0, -child.getHeight() / 2.0);
                    child.paint(g2d);
                } finally {
                    g2d.dispose();
                }
            } else {
                Graphics gCopy = g.create(child.getX(), child.getY(), child.getWidth(), child.getHeight());
                try {
                    child.paint(gCopy);
                } finally {
                    gCopy.dispose();
                }
            }
        }
    }
    //endregion

    //region Nested Types
    static class Star {
        private double x, y, speed, rotation, rotationSpeed;
        private final int size;
        public Star(double x, double y, int size, double speed, double rotation, double rotationSpeed) {
            this.x = x; this.y = y; this.size = size; this.speed = speed; this.rotation = rotation; this.rotationSpeed = rotationSpeed;
        }
    }
    private static final class Body {
        final JComponent component;
        double w, h, homeX, homeY, x, y, vx, vy, angle, spin, kickCooldown;
        Body(JComponent component) { this.component = component; }
    }
    private static boolean hitsRotated(JComponent component, double angle, int x, int y) {
        int width = component.getWidth(), height = component.getHeight();
        if (angle == 0) return x >= 0 && y >= 0 && x < width && y < height;
        double cx = width / 2.0, cy = height / 2.0;
        double dx = x - cx, dy = y - cy;
        double c = Math.cos(angle), s = Math.sin(angle);
        double localX = cx + dx * c + dy * s, localY = cy - dx * s + dy * c;
        return localX >= 0 && localY >= 0 && localX < width && localY < height;
    }
    private static final class MenuButton extends StyledButton {
        double angle;
        MenuButton(Style style, String text) { super(style, text); }
        @Override public boolean contains(int x, int y) { return hitsRotated(this, angle, x, y); }
    }
    private static final class MenuTitle extends Animated3DText {
        double angle;
        MenuTitle(String text, AnimationType animationType) { super(text, animationType); }
        @Override public boolean contains(int x, int y) { return hitsRotated(this, angle, x, y); }
    }
    //endregion
}