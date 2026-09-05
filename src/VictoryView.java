import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.Random;

public class VictoryView extends JPanel {
    private final ArrayList<Firework> fireworks = new ArrayList<>();
    private final Random random = new Random();

    private final AnimatedCongrats title;
    private final JLabel timeLabel;
    private final JButton restartButton, exitButton;

    private int viewportX, viewportY;

    private double scale = 1.0;

    public VictoryView(long elapsedSeconds) {

        setPreferredSize(new Dimension(RCJMS.SCREEN_WIDTH, RCJMS.SCREEN_HEIGHT));
        setLayout(null);
        setBackground(Color.BLACK);

        title = new AnimatedCongrats("CONGRATULATIONS");
        add(title);

        timeLabel = new JLabel("You completed maze in " + elapsedSeconds / 3600 + " hour(s) " + (elapsedSeconds % 3600) / 60 +
                " minute(s) " + elapsedSeconds % 60 + " second(s)");

        timeLabel.setForeground(Color.WHITE);
        timeLabel.setFont(new Font("Arial", Font.BOLD, 28));
        timeLabel.setHorizontalAlignment(SwingConstants.CENTER);

        add(timeLabel);

        restartButton = new JButton("RESTART");
        restartButton.setFont(new Font("Arial", Font.BOLD, 32));

        restartButton.addActionListener(e -> {
            MainMenuView panel = RCJMS.instance.mainMenuView = new MainMenuView();

            RCJMS.instance.add(panel);
            RCJMS.instance.remove(RCJMS.instance.victoryView);

            RCJMS.instance.setTitle("Main Menu");
            RCJMS.instance.pack();
            RCJMS.instance.revalidate();
            RCJMS.instance.repaint();
        });

        add(restartButton);

        exitButton = new JButton("EXIT");
        exitButton.setFont(new Font("Arial", Font.BOLD, 18));
        exitButton.addActionListener(e -> System.exit(0));
        add(exitButton);

        for (int i = 0; i < 300; i++) fireworks.add(createParticle());

        Timer timer = new Timer(16, e -> {updateFireworks();repaint();});
        timer.start();

        updateLayout();

        addComponentListener(new java.awt.event.ComponentAdapter() {@Override public void componentResized(java.awt.event.ComponentEvent e) {updateLayout();}});
    }
    private Firework createParticle() {
        return new Firework(random.nextInt(RCJMS.SCREEN_WIDTH), random.nextInt(RCJMS.SCREEN_HEIGHT),
                random.nextDouble() * 6 - 3, random.nextDouble() * 6 - 3,
                2 + random.nextInt(8),
                new Color(random.nextInt(256), random.nextInt(256), random.nextInt(256)),
                40 + random.nextInt(80));
    }
    private void resetParticle(Firework f) {
        f.x = random.nextInt(RCJMS.SCREEN_WIDTH);
        f.y = random.nextInt(RCJMS.SCREEN_HEIGHT);

        f.vx = random.nextDouble() * 6 - 3;
        f.vy = random.nextDouble() * 6 - 3;

        f.size = 2 + random.nextInt(8);
        f.life = 40 + random.nextInt(80);
        f.color = new Color(random.nextInt(256), random.nextInt(256), random.nextInt(256));
    }
    private void updateFireworks() {
        for (Firework f : fireworks) {
            f.x += f.vx;
            f.y += f.vy;

            f.life--;
            f.vy += 0.03;

            if (f.life <= 0 || f.x < -20 || f.x > RCJMS.SCREEN_WIDTH + 20 || f.y < -20 || f.y > RCJMS.SCREEN_HEIGHT + 20) resetParticle(f);
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
class AnimatedCongrats extends JComponent {
    private final String text;
    private double time = 0;

    public AnimatedCongrats(String text) {
        this.text = text;
        Timer timer = new Timer(16, e -> {time += 0.05;repaint();});
        timer.start();
    }
    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int cx = getWidth() / 2;
        int cy = getHeight() / 2;

        double scale = 1 + Math.sin(time) * 0.06;
        double rotation = Math.sin(time * 0.7) * 0.05;

        AffineTransform old = g2d.getTransform();

        g2d.translate(cx, cy);
        g2d.rotate(rotation);
        g2d.scale(scale, scale);

        Font font = new Font("Arial", Font.BOLD, 29);
        g2d.setFont(font);

        FontMetrics fm = g2d.getFontMetrics();
        int w = fm.stringWidth(text);

        g2d.setColor(new Color(255, 255, 120, 80));

        for (int i = 8; i >= 1; i--) g2d.drawString(text, -w / 2 - i / 2, i);
        g2d.setColor(Color.WHITE);
        g2d.drawString(text, -w / 2, 0);
        g2d.setTransform(old);
        g2d.dispose();
    }
}