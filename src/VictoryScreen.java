import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.Random;

public class VictoryScreen extends JFrame {
    public VictoryScreen(long elapsedSeconds) {
        setTitle("Victory");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(GamePanel.SCREEN_WIDTH, GamePanel.SCREEN_HEIGHT);
        setLocationRelativeTo(null);
        setResizable(false);
        add(new VictoryPanel(this, elapsedSeconds));
        setVisible(true);
    }
}
class VictoryPanel extends JPanel {
    private final ArrayList<Firework> fireworks = new ArrayList<>();
    private final Random random = new Random();
    private final AnimatedCongrats title;
    private final JLabel timeLabel;
    private final JButton restartButton, exitButton;

    public VictoryPanel(JFrame frame, long elapsedSeconds) {
        setLayout(null);
        setBackground(Color.BLACK);

        title = new AnimatedCongrats("CONGRATULATIONS");
        add(title);

        Timer layoutTimer = new Timer(16, e -> updateLayout());
        layoutTimer.start();
        timeLabel = new JLabel("You completed maze in " + elapsedSeconds / 3600 + " hour(s) "
                + (elapsedSeconds % 3600) / 60 + " minute(s) " + elapsedSeconds % 60 + " second(s)");

        timeLabel.setForeground(Color.WHITE);
        timeLabel.setFont(new Font("Arial", Font.BOLD, 28));
        add(timeLabel);

        restartButton = new JButton("RESTART");
        restartButton.setFont(new Font("Arial", Font.BOLD, 32));
        restartButton.addActionListener(e -> { frame.dispose(); new RCM3DMM(); });
        add(restartButton);

        exitButton = new JButton("EXIT");
        exitButton.setFont(new Font("Arial", Font.BOLD, 18));
        exitButton.addActionListener(e -> System.exit(0));
        add(exitButton);

        for (int i = 0; i < 300; i++)
            fireworks.add(createParticle());

        Timer timer = new Timer(16, e -> { updateFireworks(); repaint(); });
        timer.start();
    }
    private Firework createParticle() {
        return new Firework(
                random.nextInt(1200), random.nextInt(800),
                random.nextDouble() * 6 - 3, random.nextDouble() * 6 - 3,
                2 + random.nextInt(8),
                new Color(random.nextInt(256), random.nextInt(256), random.nextInt(256)),
                40 + random.nextInt(80));
    }
    private void updateFireworks() {
        for (Firework f : fireworks) {
            f.x += f.vx;
            f.y += f.vy;
            f.life--;
            f.vy += 0.03;

            if (f.life <= 0) {
                f.x = random.nextInt(getWidth());
                f.y = random.nextInt(getHeight());

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
        Graphics2D g2d = (Graphics2D) g;

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        for (Firework f : fireworks) {
            g2d.setColor(f.color);
            g2d.fillRect((int) f.x, (int) f.y, f.size, f.size);
        }
    }
    private void updateLayout() {
        int w = getWidth();
        int h = getHeight();

        title.setBounds(w / 2 - w / 4, h / 20, w / 2, h / 8);
        timeLabel.setBounds(w / 2 - 400, h / 4, 800, 40);
        restartButton.setBounds(w / 2 - 170, h / 2 - 40, 340, 90);
        exitButton.setBounds(w / 2 - 100, h / 2 + 100, 200, 50);
    }
}
class Firework {
    double x, y, vx, vy;
    int size, life;
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
        Timer timer = new Timer(16, e -> { time += 0.05; repaint(); });
        timer.start();
    }
    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
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
        for (int i = 8; i >= 1; i--)
            g2d.drawString(text, -w / 2 - i / 2, i);

        g2d.setColor(Color.WHITE);
        g2d.drawString(text, -w / 2, 0);
        g2d.setTransform(old);
    }
}