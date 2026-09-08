import StyleUI.*;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Random;

public class VictoryView extends JPanel {
    private final ArrayList<Firework> fireworks = new ArrayList<>();
    private final Random random = new Random();

    private final Animated3DText title;
    private final StyledLabel timeLabel;
    private final StyledButton restartButton, exitButton;

    private int viewportX, viewportY;

    private double scale = 1.0;
    private final Timer fireworksTimer;

    public VictoryView(long elapsedSeconds) {
        setPreferredSize(new Dimension(RCJMS.SCREEN_WIDTH, RCJMS.SCREEN_HEIGHT));
        setLayout(null);
        setBackground(Color.BLACK);

        title = new Animated3DText("CONGRATULATIONS", Animated3DText.AnimationType.FULL);
        title.setTextColor(Color.WHITE);
        title.setDepthColor(new Color(255, 255, 120, 80));
        add(title);

        timeLabel = new StyledLabel(Style.GLASS, "You completed maze in " + elapsedSeconds / 3600 + " hour(s) " + (elapsedSeconds % 3600) / 60 +
                " minute(s) " + elapsedSeconds % 60 + " second(s)");
        timeLabel.setForeground(Color.WHITE);
        timeLabel.setFont(new Font("Arial", Font.BOLD, 25));

        add(timeLabel);

        restartButton = new StyledButton(Style.GLASS, "RESTART");
        restartButton.setFont(new Font("Arial", Font.BOLD, 32));

        restartButton.addActionListener(e -> {RCJMS.instance.ChangeView(RCJMS.instance.mainMenuView = new MainMenuView(), "Main Menu");});

        add(restartButton);

        exitButton = new StyledButton(Style.GLASS, "EXIT");
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

    @Override
    public void addNotify() {
        super.addNotify();
        if (fireworksTimer != null && !fireworksTimer.isRunning()) fireworksTimer.start();
        if (title != null) title.addNotify();
    }

    @Override
    public void removeNotify() {
        if (fireworksTimer != null && fireworksTimer.isRunning()) fireworksTimer.stop();
        if (title != null) title.removeNotify();
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
        int windowWidth = getWidth();
        int windowHeight = getHeight();

        if (windowWidth <= 0 || windowHeight <= 0) return;

        double scaleX = (double) windowWidth / RCJMS.SCREEN_WIDTH;
        double scaleY = (double) windowHeight / RCJMS.SCREEN_HEIGHT;
        scale = Math.min(scaleX, scaleY);

        int viewportWidth = (int) Math.round(RCJMS.SCREEN_WIDTH * scale);
        int viewportHeight = (int) Math.round(RCJMS.SCREEN_HEIGHT * scale);
        viewportX = (windowWidth - viewportWidth) / 2;
        viewportY = (windowHeight - viewportHeight) / 2;

        int titleX = RCJMS.SCREEN_WIDTH / 2 - RCJMS.SCREEN_WIDTH / 4;
        int titleY = RCJMS.SCREEN_HEIGHT / 20;
        int titleWidth = RCJMS.SCREEN_WIDTH / 2;
        int titleHeight = RCJMS.SCREEN_HEIGHT / 8;

        title.setBounds(viewportX + (int) (titleX * scale), viewportY + (int) (titleY * scale), (int) (titleWidth * scale), (int) (titleHeight * scale));

        int timeWidth = 800;
        int timeHeight = 40;
        int timeX = RCJMS.SCREEN_WIDTH / 2 - timeWidth / 2;
        int timeY = RCJMS.SCREEN_HEIGHT / 4;

        timeLabel.setBounds(viewportX + (int) (timeX * scale), viewportY + (int) (timeY * scale), (int) (timeWidth * scale), (int) (timeHeight * scale));

        int restartWidth = 340;
        int restartHeight = 90;
        int restartX = RCJMS.SCREEN_WIDTH / 2 - restartWidth / 2;
        int restartY = RCJMS.SCREEN_HEIGHT / 2 - 40;
        restartButton.setBounds(viewportX + (int) (restartX * scale), viewportY + (int) (restartY * scale), (int) (restartWidth * scale), (int) (restartHeight * scale));

        int exitWidth = 200;
        int exitHeight = 50;
        int exitX = RCJMS.SCREEN_WIDTH / 2 - exitWidth / 2;
        int exitY = RCJMS.SCREEN_HEIGHT / 2 + 100;
        exitButton.setBounds(viewportX + (int) (exitX * scale), viewportY + (int) (exitY * scale), (int) (exitWidth * scale), (int) (exitHeight * scale));
        revalidate();
        repaint();
    }
}
class Firework {
    double x, y, vx, vy;
    int size,life;
    Color color;

    public Firework(double x, double y, double vx, double vy, int size, Color color, int life) {
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.size = size;
        this.color = color;
        this.life = life;
    }
}