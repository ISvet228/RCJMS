package Project.Victory;

import Project.Helpers.FontCache;
import Project.Game.GameView;
import Project.MainMenu.MainMenuView;
import Project.RCJMS;
import Project.StyleUI.*;
import Project.Helpers.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class VictoryView extends JPanel {
    //region Variables
    //region Showcase State
    private static final double SPIN_SPEED = 0.4;
    private static final double SPIN_WOBBLE = 0.0;
    private static final double EYE_HEIGHT = 0.5;
    private static final double PLAYER_X = 2.5, PLAYER_Y = 2.5;
    private static final int ROOM_SIZE = 5;
    private static final int[] PRESET_SIZES = {15, 25, 40};
    private final Style currentStyle = loadTheme();
    private final GameView showcase;
    //endregion
    //region Firework State
    private static final int FIREWORK_COUNT = 300;
    private final List<Firework> fireworks = new ArrayList<>();
    private boolean fireworksEnabled;
    private final Random random = new Random();
    //endregion
    //region Visual Effects
    private static final float RAINBOW_STRENGTH = 0.22f;
    private static final Color BG_TOP = new Color(3, 5, 22);
    private static final Color BG_BOTTOM = new Color(8, 30, 40);
    private boolean rainbowFilter;
    //endregion
    //region Layout State
    private static final int TITLE_X = 150, TITLE_Y = 44, TITLE_W = 660, TITLE_H = 104;
    private static final int CARD_X = 250, CARD_Y = 176, CARD_W = 460, CARD_H = 112;
    private static final int RESTART_X = 330, RESTART_Y = 330, RESTART_W = 300, RESTART_H = 70;
    private static final int EXIT_X = 380, EXIT_Y = 418, EXIT_W = 200, EXIT_H = 48;
    private int viewportX, viewportY;
    private double scale = 1.0;
    //endregion
    //region Button State
    private final Animated3DText title = new Animated3DText("", Animated3DText.AnimationType.FULL);
    private final StyledButton restartButton = new StyledButton(currentStyle, "vv.restart");
    private final StyledButton exitButton = new StyledButton(currentStyle, "exit");
    private final boolean keysEnabled;
    //endregion
    //region Animation State
    private final long elapsedSeconds;
    private BufferedImage frame;
    private double timeSec = 0;
    private long lastNanos = System.nanoTime();
    private final Timer animationTimer;
    //endregion
    //endregion

    //region Constructors
    public VictoryView(long elapsedSeconds) { this(elapsedSeconds, -1, -1, true); }
    public VictoryView(long elapsedSeconds, int width, int height, boolean customMap) {
        this.elapsedSeconds = elapsedSeconds;
        int preset = presetIndex(width, height);
        this.keysEnabled = customMap || preset < 0;
        this.rainbowFilter = !keysEnabled && (preset == 0 || preset == 2);
        this.fireworksEnabled = !keysEnabled && (preset == 1 || preset == 2);
        setPreferredSize(new Dimension(RCJMS.SCREEN_WIDTH, RCJMS.SCREEN_HEIGHT));
        setLayout(null);
        setBackground(BG_BOTTOM);

        for (int i = 0; i < FIREWORK_COUNT; i++) fireworks.add(newFirework());

        showcase = createShowcase();

        NSLocalizedString.bind(title, "vv.title");
        title.setTextColor(Color.WHITE);
        title.setDepthColor(new Color(90, 255, 214, 90));
        add(title);

        restartButton.addActionListener(_ -> RCJMS.instance.changeView(RCJMS.instance.mainMenuView = new MainMenuView(), "main_menu"));
        add(restartButton);
        exitButton.addActionListener(_ -> System.exit(0));
        add(exitButton);

        animationTimer = new Timer(16, _ -> { tick(); repaint(); });
        animationTimer.start();

        if (keysEnabled) {
            bindKey(KeyEvent.VK_R, "toggleRainbow", () -> this.rainbowFilter = !this.rainbowFilter);
            bindKey(KeyEvent.VK_F, "toggleFireworks", () -> this.fireworksEnabled = !this.fireworksEnabled);
        }

        updateLayout();
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent componentEvent) { updateLayout(); }
        });
    }
    //endregion

    //region Lifecycle
    @Override public void addNotify() {
        super.addNotify();
        if (animationTimer != null && !animationTimer.isRunning()) animationTimer.start();
    }
    @Override public void removeNotify() {
        if (animationTimer != null && animationTimer.isRunning()) animationTimer.stop();
        if (showcase != null) showcase.releaseShowcase();
        super.removeNotify();
    }
    private Style loadTheme() { return SaveData.load().theme; }
    //endregion

    //region Input
    private void bindKey(int keyCode, String name, Runnable action) {
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(keyCode, 0), name);
        getActionMap().put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent actionEvent) {
                action.run();
                repaint();
            }
        });
    }
    //endregion

    //region Animation
    private void tick() {
        long now = System.nanoTime();
        double elapsed = (now - lastNanos) / 1e9;
        lastNanos = now;
        timeSec += elapsed;

        double yaw = SPIN_SPEED * timeSec + SPIN_WOBBLE * (1.0 - Math.cos(0.5 * timeSec)) / 0.5;

        if (showcase != null) {
            showcase.setShowcaseCamera(PLAYER_X, PLAYER_Y, yaw, 0.0, EYE_HEIGHT);
            frame = showcase.renderShowcaseFrame();
        }

        updateFireworks();
    }
    //endregion

    //region Room Showcase
    private static int presetIndex(int width, int height) {
        for (int i = 0; i < PRESET_SIZES.length; i++)
            if (width == PRESET_SIZES[i] && height == PRESET_SIZES[i]) return i;
        return -1;
    }
    private static int[][] buildRoomMap() {
        int[][] room = new int[ROOM_SIZE][ROOM_SIZE];
        for (int y = 0; y < ROOM_SIZE; y++)
            for (int x = 0; x < ROOM_SIZE; x++)
                room[y][x] = (x == 0 || y == 0 || x == ROOM_SIZE - 1 || y == ROOM_SIZE - 1) ? MazeGenerator.WALL : MazeGenerator.OPEN;
        return room;
    }
    private GameView createShowcase() {
        try { return new GameView(buildRoomMap()); }
        catch (IOException exception) {
            exception.printStackTrace();
            return null;
        }
    }
    //endregion

    //region Fireworks
    private void updateFireworks() {
        for (Firework firework : fireworks) {
            firework.x += firework.vx;
            firework.y += firework.vy;
            firework.life--;
            firework.vy += 0.03;
            if (firework.life <= 0 || firework.x < -20 || firework.x > RCJMS.SCREEN_WIDTH + 20 || firework.y < -20 || firework.y > RCJMS.SCREEN_HEIGHT + 20) {
                respawn(firework);
            }
        }
    }
    private Firework newFirework() {
        Color color = randomColor();
        int avg = (color.getRed() + color.getGreen() + color.getGreen()) / 3;
        if (avg < 95)
            color = new Color(
                    (int)(color.getRed() + (255 - color.getRed()) * 0.8),
                    (int)(color.getGreen() + (255 - color.getGreen()) * 0.8),
                    (int)(color.getBlue() + (255 - color.getBlue()) * 0.8)
            );
        return new Firework(random.nextInt(RCJMS.SCREEN_WIDTH), random.nextInt(RCJMS.SCREEN_HEIGHT),
                random.nextDouble() * 6 - 3, random.nextDouble() * 6 - 3,
                2 + random.nextInt(8), color, 40 + random.nextInt(80));
    }
    private void respawn(Firework firework) {
        firework.x = random.nextInt(RCJMS.SCREEN_WIDTH);
        firework.y = random.nextInt(RCJMS.SCREEN_HEIGHT);
        firework.vx = random.nextDouble() * 6 - 3;
        firework.vy = random.nextDouble() * 6 - 3;
        firework.size = 2 + random.nextInt(8);
        firework.life = 40 + random.nextInt(80);
        Color color = randomColor();
        int avg = (color.getRed() + color.getGreen() + color.getGreen()) / 3;
        if (avg < 95)
            color = new Color(
                    (int)(color.getRed() + (255 - color.getRed()) * 0.8),
                    (int)(color.getGreen() + (255 - color.getGreen()) * 0.8),
                    (int)(color.getBlue() + (255 - color.getBlue()) * 0.8)
            );
        firework.color = color;
    }
    private Color randomColor() {
        return new Color(random.nextInt(256), random.nextInt(256), random.nextInt(256));
    }
    private void paintFireworks(Graphics2D g2d) {
        for (Firework firework : fireworks) {
            g2d.setColor(firework.color);
            g2d.fillRect((int) firework.x, (int) firework.y, firework.size, firework.size);
        }
    }
    static class Firework {
        double x, y, vx, vy;
        int size, life;
        Color color;
        Firework(double x, double y, double vx, double vy, int size, Color color, int life) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy; this.size = size; this.color = color; this.life = life;
        }
    }
    //endregion

    //region Painting
    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g2d = (Graphics2D) graphics.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        int w = getWidth(), h = getHeight();
        g2d.setPaint(new GradientPaint(0, 0, BG_TOP, 0, h, BG_BOTTOM));
        g2d.fillRect(0, 0, w, h);

        g2d.translate(viewportX, viewportY);
        g2d.scale(scale, scale);
        g2d.clipRect(0, 0, RCJMS.SCREEN_WIDTH, RCJMS.SCREEN_HEIGHT);

        if (frame != null) g2d.drawImage(frame, 0, 0, RCJMS.SCREEN_WIDTH, RCJMS.SCREEN_HEIGHT, null);
        if (rainbowFilter) paintRainbow(g2d);
        if (fireworksEnabled) paintFireworks(g2d);
        paintTimeCard(g2d);
        paintVignette(g2d);
        g2d.dispose();
    }
    private void paintRainbow(Graphics2D g2d) {
        double t = timeSec;
        int stops = 7;
        float[] fractions = new float[stops];
        Color[] colors = new Color[stops];
        for (int i = 0; i < stops; i++) {
            fractions[i] = i / (float) (stops - 1);
            float hue = (float) ((i / (double) (stops - 1) + t * 0.08) % 1.0);
            colors[i] = Color.getHSBColor(hue, 0.85f, 1f);
        }
        LinearGradientPaint paint = new LinearGradientPaint(
                new Point2D.Float(0, 0), new Point2D.Float(RCJMS.SCREEN_WIDTH, RCJMS.SCREEN_HEIGHT),
                fractions, colors, MultipleGradientPaint.CycleMethod.NO_CYCLE);

        Composite old = g2d.getComposite();
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, RAINBOW_STRENGTH));
        g2d.setPaint(paint);
        g2d.fillRect(0, 0, RCJMS.SCREEN_WIDTH, RCJMS.SCREEN_HEIGHT);
        g2d.setComposite(old);
    }
    private void paintTimeCard(Graphics2D g2d) {
        RoundRectangle2D pill = new RoundRectangle2D.Double(CARD_X, CARD_Y, CARD_W, CARD_H, CARD_H, CARD_H);
        g2d.setColor(new Color(10, 30, 40, 170));
        g2d.fill(pill);
        g2d.setPaint(new GradientPaint(0, CARD_Y, new Color(255, 255, 255, 46), 0, CARD_Y + CARD_H / 2f, new Color(255, 255, 255, 0)));
        g2d.fill(pill);
        g2d.setColor(new Color(255, 255, 255, 80));
        g2d.setStroke(new BasicStroke(1.5f));
        g2d.draw(pill);

        long h = elapsedSeconds / 3600;
        long m = (elapsedSeconds % 3600) / 60;
        long s = elapsedSeconds % 60;
        drawCentered(g2d, String.format("%02d:%02d:%02d", h, m, s), FontCache.get(Font.SANS_SERIF, Font.BOLD, 46),
                new Color(220, 255, 250), CARD_Y + 62);
        String caption = String.format(NSLocalizedString.value("vv.victory_time"), h, m, s);
        drawCentered(g2d, caption, FontCache.get(Font.SANS_SERIF, Font.PLAIN, 15), new Color(200, 230, 235), CARD_Y + 94);
    }
    private void paintVignette(Graphics2D g2d) {
        g2d.setPaint(new RadialGradientPaint(new Point2D.Double(480, 270), 640f,
                new float[]{0.6f, 1f}, new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 150)}));
        g2d.fill(new Rectangle2D.Double(0, 0, RCJMS.SCREEN_WIDTH, RCJMS.SCREEN_HEIGHT));
    }
    private void drawCentered(Graphics2D g2d, String text, Font font, Color color, int baselineY) {
        g2d.setFont(font);
        FontMetrics fontMetrics = g2d.getFontMetrics(font);
        g2d.setColor(color);
        g2d.drawString(text, (RCJMS.SCREEN_WIDTH - fontMetrics.stringWidth(text)) / 2, baselineY);
    }
    //endregion

    //region Layout
    private void updateLayout() {
        int width = getWidth(), height = getHeight();
        if (width <= 0 || height <= 0) return;

        scale = Math.min((double) width / RCJMS.SCREEN_WIDTH, (double) height / RCJMS.SCREEN_HEIGHT);
        viewportX = (width - (int) Math.round(RCJMS.SCREEN_WIDTH * scale)) / 2;
        viewportY = (height - (int) Math.round(RCJMS.SCREEN_HEIGHT * scale)) / 2;

        title.setBounds(toSX(TITLE_X), toSY(TITLE_Y), toS(TITLE_W), toS(TITLE_H));
        restartButton.setBounds(toSX(RESTART_X), toSY(RESTART_Y), toS(RESTART_W), toS(RESTART_H));
        exitButton.setBounds(toSX(EXIT_X), toSY(EXIT_Y), toS(EXIT_W), toS(EXIT_H));
        revalidate();
        repaint();
    }
    private int toSX(double v) { return viewportX + (int) Math.round(v * scale); }
    private int toSY(double v) { return viewportY + (int) Math.round(v * scale); }
    private int toS(double v) { return (int) Math.round(v * scale); }
    //endregion
}