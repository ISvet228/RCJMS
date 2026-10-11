import StyleUI.*;
import Helpers.*;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Random;

public class VictoryView extends JPanel {
    //region Variables
    private static final double SPIN_SPEED = 0.1;
    private static final double SPIN_WOBBLE = 0.0;
    private static final double EYE_HEIGHT = 0.5;
    private static final double PLAYER_X = 2.5, PLAYER_Y = 2.5;
    private static final int ROOM_SIZE = 5;

    public static final boolean RAINBOW_FILTER_DEFAULT = true;
    private static final float RAINBOW_STRENGTH = 0.22f;

    private static final Color BG_TOP = new Color(3, 5, 22);
    private static final Color BG_BOTTOM = new Color(8, 30, 40);
    private static final Color TEAL = new Color(90, 255, 214);

    private static final int TITLE_X = 150, TITLE_Y = 44, TITLE_W = 660, TITLE_H = 104;
    private static final int CARD_X = 250, CARD_Y = 176, CARD_W = 460, CARD_H = 112;
    private static final int RESTART_X = 330, RESTART_Y = 330, RESTART_W = 300, RESTART_H = 70;
    private static final int EXIT_X = 380, EXIT_Y = 418, EXIT_W = 200, EXIT_H = 48;

    private final Style currentStyle = loadTheme();
    private final long elapsedSeconds;
    private final Random random = new Random();

    private final GameView showcase;
    private final boolean rainbowFilter;
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
    public VictoryView(long elapsedSeconds) {
        this(elapsedSeconds, RAINBOW_FILTER_DEFAULT);
    }

    public VictoryView(long elapsedSeconds, boolean rainbowFilter) {
        this.elapsedSeconds = elapsedSeconds;
        this.rainbowFilter = rainbowFilter;
        setPreferredSize(new Dimension(RCJMS.SCREEN_WIDTH, RCJMS.SCREEN_HEIGHT));
        setLayout(null);
        setBackground(BG_BOTTOM);

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
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        int w = getWidth(), h = getHeight();
        g2.setPaint(new GradientPaint(0, 0, BG_TOP, 0, h, BG_BOTTOM));
        g2.fillRect(0, 0, w, h);

        g2.translate(viewportX, viewportY);
        g2.scale(scale, scale);
        g2.clipRect(0, 0, RCJMS.SCREEN_WIDTH, RCJMS.SCREEN_HEIGHT);

        if (frame != null) g2.drawImage(frame, 0, 0, RCJMS.SCREEN_WIDTH, RCJMS.SCREEN_HEIGHT, null);
        if (rainbowFilter) paintRainbow(g2);
        paintEyebrow(g2);
        paintTimeCard(g2);
        paintVignette(g2);
        g2.dispose();
    }

    private void paintRainbow(Graphics2D g2) {
        double t = timeSec;
        int stops = 7;
        float[] fractions = new float[stops];
        Color[] colors = new Color[stops];
        for (int i = 0; i < stops; i++) {
            fractions[i] = i / (float) (stops - 1);
            float hue = (float) ((i / (double) (stops - 1) + t * 0.08) % 1.0);
            colors[i] = Color.getHSBColor(hue, 0.85f, 1f);
        }
        float shift = (float) ((t * 140) % RCJMS.SCREEN_WIDTH);
        LinearGradientPaint paint = new LinearGradientPaint(
                new Point2D.Float(shift - 480, 0), new Point2D.Float(shift + 480, RCJMS.SCREEN_HEIGHT),
                fractions, colors, MultipleGradientPaint.CycleMethod.REPEAT);

        Composite old = g2.getComposite();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, RAINBOW_STRENGTH));
        g2.setPaint(paint);
        g2.fillRect(0, 0, RCJMS.SCREEN_WIDTH, RCJMS.SCREEN_HEIGHT);
        g2.setComposite(old);
    }

    private void paintEyebrow(Graphics2D g2) {
        String text = NSLocalizedString.value("vv.victory");
        Font font = new Font(Font.SANS_SERIF, Font.BOLD, 18);
        g2.setFont(font);
        FontMetrics fm = g2.getFontMetrics(font);
        int x = (RCJMS.SCREEN_WIDTH - fm.stringWidth(text)) / 2;
        g2.setColor(new Color(90, 255, 214, 60));
        g2.drawString(text, x, 32);
        g2.setColor(TEAL);
        g2.drawString(text, x, 31);
    }

    private void paintTimeCard(Graphics2D g2) {
        RoundRectangle2D pill = new RoundRectangle2D.Double(CARD_X, CARD_Y, CARD_W, CARD_H, CARD_H, CARD_H);
        g2.setColor(new Color(10, 30, 40, 170));
        g2.fill(pill);
        g2.setPaint(new GradientPaint(0, CARD_Y, new Color(255, 255, 255, 46), 0, CARD_Y + CARD_H / 2f, new Color(255, 255, 255, 0)));
        g2.fill(pill);
        g2.setColor(new Color(255, 255, 255, 80));
        g2.setStroke(new BasicStroke(1.5f));
        g2.draw(pill);

        long h = elapsedSeconds / 3600;
        long m = (elapsedSeconds % 3600) / 60;
        long s = elapsedSeconds % 60;
        drawCentered(g2, String.format("%02d:%02d:%02d", h, m, s), new Font(Font.SANS_SERIF, Font.BOLD, 46),
                new Color(220, 255, 250), CARD_Y + 62);
        String caption = String.format(NSLocalizedString.value("vv.victory_time"), h, m, s);
        drawCentered(g2, caption, new Font(Font.SANS_SERIF, Font.PLAIN, 15), new Color(200, 230, 235), CARD_Y + 94);
    }

    private void paintVignette(Graphics2D g2) {
        g2.setPaint(new RadialGradientPaint(new Point2D.Double(480, 270), 640f,
                new float[]{0.6f, 1f}, new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 150)}));
        g2.fill(new Rectangle2D.Double(0, 0, RCJMS.SCREEN_WIDTH, RCJMS.SCREEN_HEIGHT));
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
    //endregion
}