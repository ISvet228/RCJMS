import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.Random;

public class RCM3DMM extends JFrame {
    public RCM3DMM() {
        setTitle("Main Menu");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(GamePanel.SCREEN_WIDTH, GamePanel.SCREEN_HEIGHT);
        setLocationRelativeTo(null);
        add(new StarPanel(this));
        setResizable(false);
        setVisible(true);
    }
    static void main(String[] args) {
        SwingUtilities.invokeLater(RCM3DMM::new);
    }
}
class StarPanel extends JPanel {
    private final Color backgroundColor = new Color(15, 15, 35);
    private final ArrayList<Star> stars = new ArrayList<>();
    private final Random random = new Random();
    private static final String[] modes = { "OPPOSITE CORNER", "CENTER", "RANDOM EDGE" };
    private static final JComboBox<String> modeBox = new JComboBox<>(modes);
    private static final JTextField xField = new JTextField("25");
    private static final JTextField yField = new JTextField("25");

    public StarPanel(JFrame menuFrame) {
        setLayout(null);

        AnimatedTitle title = new AnimatedTitle("MAZE 3D");
        title.setBounds(300, 40, 400, 100);
        add(title);

        JButton playButton = getJButton(menuFrame);
        add(playButton);

        JLabel modeLabel = new JLabel("MODE");
        modeLabel.setForeground(Color.WHITE);
        modeLabel.setFont(new Font("Arial", Font.BOLD, 20));
        modeLabel.setBounds(170, 270, 100, 30);
        add(modeLabel);

        modeBox.setFont(new Font("Arial", Font.PLAIN, 18));
        modeBox.setBounds(100, 310, 200, 30);
        add(modeBox);

        JLabel xLabel = new JLabel("X");
        xLabel.setForeground(Color.WHITE);
        xLabel.setFont(new Font("Arial", Font.BOLD, 20));
        xLabel.setBounds(760, 250, 40, 30);
        add(xLabel);

        xField.setFont(new Font("Arial", Font.PLAIN, 20));
        xField.setBounds(720, 290, 120, 40);
        add(xField);

        JLabel yLabel = new JLabel("Y");
        yLabel.setForeground(Color.WHITE);
        yLabel.setFont(new Font("Arial", Font.BOLD, 20));
        yLabel.setBounds(760, 360, 40, 30);
        add(yLabel);

        yField.setFont(new Font("Arial", Font.PLAIN, 20));
        yField.setBounds(720, 400, 120, 40);
        add(yField);

        for (int i = 0; i < 80; i++) stars.add(createRandomStar());
        Timer timer = new Timer(16, e -> { updateStars(); repaint(); });
        timer.start();
    }
    private static JButton getJButton(JFrame menuFrame) {
        JButton playButton = new JButton("PLAY");

        playButton.setFont(new Font("Arial", Font.BOLD, 32));
        playButton.setFocusPainted(false);

        playButton.setBounds(390, 300, 220, 80);
        playButton.addActionListener(e -> {
            menuFrame.dispose();
            JFrame frame = new JFrame("RAYCAST ME!");
            GamePanel panel = new GamePanel(frame, Integer.parseInt(xField.getText()), Integer.parseInt(yField.getText()), modeBox.getSelectedIndex());
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);
            frame.add(panel);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            panel.start();
        });
        return playButton;
    }
    private Star createRandomStar() {
        int w = Math.max(getWidth(), 1000);
        int h = Math.max(getHeight(), 700);
        return new Star(random.nextInt(w), random.nextInt(h), 15 + random.nextInt(30),
                1 + random.nextDouble() * 4, random.nextDouble() * 360, -5 + random.nextDouble() * 10);
    }
    private void updateStars() {
        for (Star star : stars) {
            star.y += star.speed;
            star.rotation += star.rotationSpeed;
            if (star.y - star.size > getHeight()) {
                star.y = -star.size;
                star.x = random.nextInt(getWidth());
                star.speed = 1 + random.nextDouble() * 4;
            }
        }
    }
    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(backgroundColor);
        g2.fillRect(0, 0, getWidth(), getHeight());
        for (Star star : stars) drawStar(g2, star);
    }
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
}
class Star {
    double x, y, speed, rotation, rotationSpeed;
    int size;
    public Star(double x, double y, int size, double speed, double rotation, double rotationSpeed) {
        this.x = x;
        this.y = y;
        this.size = size;
        this.speed = speed;
        this.rotation = rotation;
        this.rotationSpeed = rotationSpeed;
    }
}
class AnimatedTitle extends JComponent {
    private final String text;
    private double time = 0;
    public AnimatedTitle(String text) {
        this.text = text;
        Timer timer = new Timer(16, e -> { time += 0.05; repaint(); });
        timer.start();
    }
    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int centerX = getWidth() / 2;
        int centerY = getHeight() / 2;
        double scale = 1 + Math.sin(time) * 0.08;
        double rotation = Math.sin(time * 0.7) * 0.08;

        AffineTransform old = g2d.getTransform();
        g2d.translate(centerX, centerY);
        g2d.rotate(rotation);
        g2d.scale(scale, scale);

        Font font = new Font("Arial", Font.BOLD, 54);
        g2d.setFont(font);

        FontMetrics fm = g2d.getFontMetrics();
        int width = fm.stringWidth(text);

        g2d.setColor(new Color(120, 180, 255, 80));

        for (int i = 8; i >= 1; i--)
            g2d.drawString(text, -width / 2 - i / 2, i);
        g2d.setColor(Color.WHITE);
        g2d.drawString(text, -width / 2, 0);
        g2d.setTransform(old);
    }
}