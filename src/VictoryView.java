import StyleUI.*;
import Helpers.*;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class VictoryView extends JPanel {
    //region Variables
    private static final double SPIN_SPEED = 5;
    private static final double SPIN_WOBBLE = 0.0;
    private static final double EYE_HEIGHT = 0.5;
    private static final double PLAYER_X = 2.5, PLAYER_Y = 2.5;
    private static final int ROOM_SIZE = 5;
    private static final int FIREWORK_COUNT = 300;

    public static final boolean RAINBOW_FILTER_DEFAULT = true;
    public static final boolean FIREWORKS_DEFAULT = true;
    private static final float RAINBOW_STRENGTH = 0.22f;

    private static final Color BG_TOP = new Color(3, 5, 22);
    private static final Color BG_BOTTOM = new Color(8, 30, 40);

    private static final int TITLE_X = 150, TITLE_Y = 44, TITLE_W = 660, TITLE_H = 104;
    private static final int CARD_X = 250, CARD_Y = 176, CARD_W = 460, CARD_H = 112;
    private static final int RESTART_X = 330, RESTART_Y = 330, RESTART_W = 300, RESTART_H = 70;
    private static final int EXIT_X = 380, EXIT_Y = 418, EXIT_W = 200, EXIT_H = 48;

    private final Style currentStyle = loadTheme();
    private final long elapsedSeconds;
    private final Random random = new Random();

    private final GameView showcase;
    private final boolean rainbowFilter;
    private final boolean fireworksEnabled;
    private final List<Firework> fireworks = new ArrayList<>();
    private BufferedImage frame;

    private double yaw = 0.0, spin = SPIN_SPEED;
    private double timeSec = 0;
    private long lastNanos = System.nanoTime();

    private final Animated3DText title = new Animated3DText("", Animated3DText.AnimationType.FULL);
    private final StyledButton restartButton = new StyledButton(currentStyle, "vv.restart");
    private final StyledButton exitButton = new StyledButton(currentStyle, "exit");

    private final Timer animationTimer;
    private int viewportX, viewportY, ticks;
    private double scale = 1.0;
    //endregion

    //region Constructors
    public VictoryView(long elapsedSeconds) { this(elapsedSeconds, RAINBOW_FILTER_DEFAULT, FIREWORKS_DEFAULT); }
    public VictoryView(long elapsedSeconds, boolean rainbowFilter) { this(elapsedSeconds, rainbowFilter, FIREWORKS_DEFAULT); }
    public VictoryView(long elapsedSeconds, boolean rainbowFilter, boolean fireworksEnabled) {
        this.elapsedSeconds = elapsedSeconds;
        this.rainbowFilter = rainbowFilter;
        this.fireworksEnabled = fireworksEnabled;
        setPreferredSize(new Dimension(RCJMS.SCREEN_WIDTH, RCJMS.SCREEN_HEIGHT));
        setLayout(null);
        setBackground(BG_BOTTOM);

        for (int i = 0; i < FIREWORK_COUNT; i++) fireworks.add(newFirework());

        showcase = createShowcase();

        NSLocalizedString.bind(title, "vv.title");
        title.setTextColor(Color.WHITE);
        title.setDepthColor(new Color(90, 255, 214, 90));
        add(title);

        restartButton.addActionListener(e -> RCJMS.instance.changeView(RCJMS.instance.mainMenuView = new MainMenuView(), "main_menu"));
        add(restartButton);
        exitButton.addActionListener(e -> System.exit(0));
        add(exitButton);

        animationTimer = new Timer(16, e -> { tick(); repaint(); });
        animationTimer.start();

        updateLayout();
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) { updateLayout(); }
        });
    }

    private static int[][] buildRoomMap() {
        int[][] room = new int[ROOM_SIZE][ROOM_SIZE];
        for (int y = 0; y < ROOM_SIZE; y++)
            for (int x = 0; x < ROOM_SIZE; x++)
                room[y][x] = (x == 0 || y == 0 || x == ROOM_SIZE - 1 || y == ROOM_SIZE - 1) ? MazeGenerator.WALL : MazeGenerator.OPEN;
        return room;
    }

    private GameView createShowcase() {
        try {
            return new GameView(buildRoomMap());
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override public void addNotify() {
        super.addNotify();
        if (animationTimer != null && !animationTimer.isRunning()) animationTimer.start();
    }
    @Override public void removeNotify() {
        if (animationTimer != null && animationTimer.isRunning()) animationTimer.stop();
        if (showcase != null) showcase.releaseShowcase();
        super.removeNotify();
    }
    //endregion/

    //region Other
    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g.create();
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

    private void paintFireworks(Graphics2D g2d) {
        for (Firework f : fireworks) {
            g2d.setColor(f.color);
            g2d.fillRect((int) f.x, (int) f.y, f.size, f.size);
        }
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
        drawCentered(g2d, String.format("%02d:%02d:%02d", h, m, s), new Font(Font.SANS_SERIF, Font.BOLD, 46),
                new Color(220, 255, 250), CARD_Y + 62);
        String caption = String.format(NSLocalizedString.value("vv.victory_time"), h, m, s);
        drawCentered(g2d, caption, new Font(Font.SANS_SERIF, Font.PLAIN, 15), new Color(200, 230, 235), CARD_Y + 94);
    }

    private void paintVignette(Graphics2D g2d) {
        g2d.setPaint(new RadialGradientPaint(new Point2D.Double(480, 270), 640f,
                new float[]{0.6f, 1f}, new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 150)}));
        g2d.fill(new Rectangle2D.Double(0, 0, RCJMS.SCREEN_WIDTH, RCJMS.SCREEN_HEIGHT));
    }

    private void drawCentered(Graphics2D g2, String text, Font font, Color color, int baselineY) {
        g2.setFont(font);
        FontMetrics fm = g2.getFontMetrics(font);
        g2.setColor(color);
        g2.drawString(text, (RCJMS.SCREEN_WIDTH - fm.stringWidth(text)) / 2, baselineY);
    }

    private void tick() {
        ticks++;
        long now = System.nanoTime();
        double elapsed = (now - lastNanos) / 1e9;
        lastNanos = now;
        timeSec += elapsed;

        spin = SPIN_SPEED + SPIN_WOBBLE * Math.sin(0.5 * timeSec);
        yaw = SPIN_SPEED * timeSec + SPIN_WOBBLE * (1.0 - Math.cos(0.5 * timeSec)) / 0.5;

        if (showcase != null) {
            showcase.setShowcaseCamera(PLAYER_X, PLAYER_Y, yaw, 0.0, EYE_HEIGHT);
            frame = showcase.renderShowcaseFrame();
        }

        if (fireworksEnabled) updateFireworks();
    }

    private void updateFireworks() {
        for (Firework f : fireworks) {
            f.x += f.vx;
            f.y += f.vy;
            f.life--;
            f.vy += 0.03;
            if (f.life <= 0 || f.x < -20 || f.x > RCJMS.SCREEN_WIDTH + 20 || f.y < -20 || f.y > RCJMS.SCREEN_HEIGHT + 20) {
                respawn(f);
            }
        }
    }

    private Firework newFirework() {
        Color c = randomColor();
        int avg = (c.getRed() + c.getGreen() + c.getGreen()) / 3;
        if (avg < 95)
            c = new Color(
                    (int)(c.getRed() + (255 - c.getRed()) * 0.8),
                    (int)(c.getGreen() + (255 - c.getGreen()) * 0.8),
                    (int)(c.getBlue() + (255 - c.getBlue()) * 0.8)
            );
        return new Firework(random.nextInt(RCJMS.SCREEN_WIDTH), random.nextInt(RCJMS.SCREEN_HEIGHT),
                random.nextDouble() * 6 - 3, random.nextDouble() * 6 - 3,
                2 + random.nextInt(8), c, 40 + random.nextInt(80));
    }

    private void respawn(Firework f) {
        f.x = random.nextInt(RCJMS.SCREEN_WIDTH);
        f.y = random.nextInt(RCJMS.SCREEN_HEIGHT);
        f.vx = random.nextDouble() * 6 - 3;
        f.vy = random.nextDouble() * 6 - 3;
        f.size = 2 + random.nextInt(8);
        f.life = 40 + random.nextInt(80);
        Color c = randomColor();
        int avg = (c.getRed() + c.getGreen() + c.getGreen()) / 3;
        if (avg < 95)
            c = new Color(
                    (int)(c.getRed() + (255 - c.getRed()) * 0.8),
                    (int)(c.getGreen() + (255 - c.getGreen()) * 0.8),
                    (int)(c.getBlue() + (255 - c.getBlue()) * 0.8)
            );
        f.color = c;
    }

    private Color randomColor() {
        return new Color(random.nextInt(256), random.nextInt(256), random.nextInt(256));
    }

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
    private Style loadTheme() { return SaveData.load().theme; }
    static class Firework {
        double x, y, vx, vy;
        int size, life;
        Color color;
        Firework(double x, double y, double vx, double vy, int size, Color color, int life) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy; this.size = size; this.color = color; this.life = life;
        }
    }
    //endregion
}