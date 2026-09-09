import StyleUI.*;
import Helpers.NSLocalizableString;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Random;

public class VictoryView extends JPanel {
    private static final Path TMP_DIR = Paths.get("tmp");
    private static final Path THEME_FILE = TMP_DIR.resolve("theme.txt");
    private final Style currentStyle = loadTheme();

    private final ArrayList<Firework> fireworks = new ArrayList<>();
    private final Random random = new Random();

    private final Animated3DText title = new Animated3DText("CONGRATULATIONS", Animated3DText.AnimationType.FULL);
    private final StyledLabel timeLabel = new StyledLabel(currentStyle, "");
    private final StyledButton restartButton = new StyledButton(currentStyle, "RESTART");
    private final StyledButton exitButton = new StyledButton(currentStyle, "EXIT");

    private int viewportX, viewportY;

    private double scale = 1.0;
    private final Timer fireworksTimer;

    public VictoryView(long elapsedSeconds) {
        setPreferredSize(new Dimension(RCJMS.SCREEN_WIDTH, RCJMS.SCREEN_HEIGHT));
        setLayout(null);
        setBackground(Color.BLACK);

        NSLocalizableString.bind(title, "CONGRATULATIONS");
        title.setTextColor(Color.WHITE);
        title.setDepthColor(new Color(255, 255, 120, 80));
        add(title);

        timeLabel.setLocalizationFormat("victory.time", () -> new Object[]{
                elapsedSeconds / 3600, (elapsedSeconds % 3600) / 60, elapsedSeconds % 60});
        timeLabel.setForeground(Color.WHITE);
        timeLabel.setFont(new Font("Arial", Font.BOLD, 25));
        add(timeLabel);

        restartButton.setFont(new Font("Arial", Font.BOLD, 32));
        restartButton.addActionListener(e -> {RCJMS.instance.ChangeView(RCJMS.instance.mainMenuView = new MainMenuView(), "Main Menu");});
        add(restartButton);

        exitButton.setFont(new Font("Arial", Font.BOLD, 18));
        exitButton.addActionListener(e -> System.exit(0));
        add(exitButton);

        for (int i = 0; i < 300; i++) fireworks.add(
                new Firework(random.nextInt(RCJMS.SCREEN_WIDTH), random.nextInt(RCJMS.SCREEN_HEIGHT), random.nextDouble() * 6 - 3, random.nextDouble() * 6 - 3,
                        2 + random.nextInt(8), new Color(random.nextInt(256), random.nextInt(256), random.nextInt(256)), 40 + random.nextInt(80))
        );

        fireworksTimer = new Timer(16, e -> {updateFireworks();repaint();});
        fireworksTimer.start();

        updateLayout();
        addComponentListener(new java.awt.event.ComponentAdapter() {@Override public void componentResized(java.awt.event.ComponentEvent e) {updateLayout();}});
    }

    @Override public void addNotify() {
        super.addNotify();
        if (fireworksTimer != null && !fireworksTimer.isRunning()) fireworksTimer.start();
    }
    @Override public void removeNotify() {
        if (fireworksTimer != null && fireworksTimer.isRunning()) fireworksTimer.stop();
        super.removeNotify();
    }
    private void updateFireworks() {
        for (Firework f : fireworks) {
            f.x += f.vx;
            f.y += f.vy;

            f.life--;
            f.vy += 0.03;

            if (f.life <= 0 || f.x < -20 || f.x > RCJMS.SCREEN_WIDTH + 20 || f.y < -20 || f.y > RCJMS.SCREEN_HEIGHT + 20){
                f.x = random.nextInt(RCJMS.SCREEN_WIDTH);
                f.y = random.nextInt(RCJMS.SCREEN_HEIGHT);

                f.vx = random.nextDouble() * 6 - 3;
                f.vy = random.nextDouble() * 6 - 3;

                f.size = 2 + random.nextInt(8);
                f.life = 40 + random.nextInt(80);
                f.color = new Color(random.nextInt(256), random.nextInt(256), random.nextInt(256));
            }
        }
    }
    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g2d.translate(viewportX, viewportY);
        g2d.scale(scale, scale);
        g2d.clipRect(0, 0, RCJMS.SCREEN_WIDTH, RCJMS.SCREEN_HEIGHT);

        for (Firework f : fireworks) {
            g2d.setColor(f.color);
            g2d.fillRect((int) f.x, (int) f.y, f.size, f.size);
        }
        g2d.dispose();
    }
    private void updateLayout() {
        int width = getWidth(), height = getHeight();
        if (width <= 0 || height <= 0) return;

        scale = Math.min((double) width / RCJMS.SCREEN_WIDTH, (double) height / RCJMS.SCREEN_HEIGHT);

        viewportX = (width - (int)Math.round(RCJMS.SCREEN_WIDTH * scale)) / 2;
        viewportY = (height - (int)Math.round(RCJMS.SCREEN_HEIGHT * scale)) / 2;

        title.setBounds(viewportX + (int)(((double)RCJMS.SCREEN_WIDTH / 2 - (double)RCJMS.SCREEN_WIDTH / 4) * scale), viewportY +
                (int)(((double)RCJMS.SCREEN_HEIGHT / 20) * scale), (int)((double)RCJMS.SCREEN_WIDTH / 2 * scale), (int)((double)RCJMS.SCREEN_HEIGHT / 8 * scale));
        timeLabel.setBounds(viewportX + (int)(((double)RCJMS.SCREEN_WIDTH / 2 - 400) * scale), viewportY +
                (int)((double)RCJMS.SCREEN_HEIGHT / 4 * scale), (int)(800 * scale), (int)(40 * scale));
        restartButton.setBounds(viewportX + (int)(((double)RCJMS.SCREEN_WIDTH / 2 - 170) * scale), viewportY +
                (int)(((double)RCJMS.SCREEN_HEIGHT / 2 - 40) * scale), (int)(340 * scale), (int)(90 * scale));
        exitButton.setBounds(viewportX + (int)(((double)RCJMS.SCREEN_WIDTH / 2 - 100) * scale), viewportY +
                (int)(((double)RCJMS.SCREEN_HEIGHT / 2 + 100) * scale), (int)(200 * scale), (int)(50 * scale));
        revalidate();
        repaint();
    }
    private Style loadTheme() {
        try {
            if (Files.exists(THEME_FILE)) {
                String name = Files.readString(THEME_FILE).trim();
                return Style.valueOf(name);
            }
        } catch (IOException | IllegalArgumentException ex) { ex.printStackTrace(); }
        return Style.FLAT;
    }
}
class Firework {
    double x, y, vx, vy;
    int size, life;
    Color color;
    public Firework(double x, double y, double vx, double vy, int size, Color color, int life) {
        this.x = x; this.y = y; this.vx = vx; this.vy = vy; this.size = size; this.color = color; this.life = life;
    }
}