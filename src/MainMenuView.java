import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.Random;

public class MainMenuView extends JPanel {
    private final Color backgroundColor = new Color(15, 15, 35);
    private final ArrayList<Star> stars = new ArrayList<>();
    private final Random random = new Random();
    private static final String[] modes = {"OPPOSITE CORNER", "CENTER", "RANDOM EDGE"};

    private final JComboBox<String> modeBox = new JComboBox<>(modes);
    private final JTextField xField = new JTextField("25");
    private final JTextField yField = new JTextField("25");

    private final AnimatedTitle title;
    private static final int VIRTUAL_WIDTH = RCJMS.SCREEN_WIDTH;
    private static final int VIRTUAL_HEIGHT = RCJMS.SCREEN_HEIGHT;

    public MainMenuView() {
        setPreferredSize(new Dimension(RCJMS.SCREEN_WIDTH, RCJMS.SCREEN_HEIGHT));
        setLayout(null);

        title = new AnimatedTitle("MAZE 3D");
        add(title);

        JButton playButton = createPlayButton();
        add(playButton);

        JLabel modeLabel = new JLabel("MODE");
        modeLabel.setForeground(Color.WHITE);
        modeLabel.setFont(new Font("Arial", Font.BOLD, 20));
        add(modeLabel);

        modeBox.setFont(new Font("Arial", Font.PLAIN, 18));
        add(modeBox);

        JLabel xLabel = new JLabel("X");
        xLabel.setForeground(Color.WHITE);
        xLabel.setFont(new Font("Arial", Font.BOLD, 20));
        add(xLabel);

        xField.setFont(new Font("Arial", Font.PLAIN, 20));
        add(xField);

        JLabel yLabel = new JLabel("Y");
        yLabel.setForeground(Color.WHITE);
        yLabel.setFont(new Font("Arial", Font.BOLD, 20));
        add(yLabel);

        yField.setFont(new Font("Arial", Font.PLAIN, 20));
        add(yField);

        for (int i = 0; i < 80; i++) stars.add(createRandomStar());

        Timer timer = new Timer(16, e -> {updateStars();repaint();});
        timer.start();

        addComponentListener(new ComponentAdapter() {@Override public void componentResized(ComponentEvent e) {updateLayout();}});
        SwingUtilities.invokeLater(this::updateLayout);
    }

    private JButton createPlayButton() {
        JButton playButton = new JButton("PLAY");

        playButton.setFont(new Font("Arial", Font.BOLD, 32));
        playButton.setFocusPainted(false);
        playButton.addActionListener(e -> {
            try {
                int mazeWidth = Integer.parseInt(xField.getText());
                int mazeHeight = Integer.parseInt(yField.getText());

                int mode = modeBox.getSelectedIndex();

                GameView panel = RCJMS.instance.gameView = new GameView(mazeWidth, mazeHeight, mode);
                RCJMS.instance.add(panel);
                RCJMS.instance.remove(RCJMS.instance.mainMenuView);
                RCJMS.instance.setTitle("RayCast Me!");
                //RCJMS.instance.pack();
                RCJMS.instance.revalidate();
                panel.start();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Maze size must be a number.", "Invalid Input", JOptionPane.ERROR_MESSAGE);
            }
        });
        return playButton;
    }
    private void updateLayout() {
        int width = getWidth();
        int height = getHeight();

        if (width <= 0 || height <= 0) return;

        double scaleX = (double) width / VIRTUAL_WIDTH;
        double scaleY = (double) height / VIRTUAL_HEIGHT;
        double scale = Math.min(scaleX, scaleY);

        int scaledWidth = (int) (VIRTUAL_WIDTH * scale);
        int scaledHeight = (int) (VIRTUAL_HEIGHT * scale);

        int offsetX = (width - scaledWidth) / 2;
        int offsetY = (height - scaledHeight) / 2;

        setScaledBounds(title, 300, 40, 400, 100, scale, offsetX, offsetY);

        Component playButton = getComponentByName("PLAY");

        if (playButton != null) setScaledBounds(playButton, 390, 300, 220, 80, scale, offsetX, offsetY);
        Component modeLabel = getComponentByClass(JLabel.class, 0);

        if (modeLabel != null) {
            setScaledBounds(modeLabel, 170, 270, 100, 30, scale, offsetX, offsetY);
            modeLabel.setFont(new Font("Arial", Font.BOLD, Math.max(1, (int) (20 * scale))));
        }

        setScaledBounds(modeBox, 100, 310, 200, 30, scale, offsetX, offsetY);
        modeBox.setFont(new Font("Arial", Font.PLAIN, Math.max(1, (int) (18 * scale))));

        Component xLabel = getComponentByClass(JLabel.class, 1);
        if (xLabel != null) {
            setScaledBounds(xLabel, 760, 250, 40, 30, scale, offsetX, offsetY);
            xLabel.setFont(new Font("Arial", Font.BOLD, Math.max(1, (int) (20 * scale))));
        }
        setScaledBounds(xField, 720, 290, 120, 40, scale, offsetX, offsetY);

        xField.setFont(new Font("Arial", Font.PLAIN, Math.max(1, (int) (20 * scale))));
        Component yLabel = getComponentByClass(JLabel.class, 2);

        if (yLabel != null) {
            setScaledBounds(yLabel, 760, 360, 40, 30, scale, offsetX, offsetY);
            yLabel.setFont(new Font("Arial", Font.BOLD, Math.max(1, (int) (20 * scale))));
        }
        setScaledBounds(yField, 720, 400, 120, 40, scale, offsetX, offsetY);
        yField.setFont(new Font("Arial", Font.PLAIN, Math.max(1, (int) (20 * scale))));
    }
    private void setScaledBounds(Component component, int x, int y, int width, int height, double scale, int offsetX, int offsetY) {
        component.setBounds(offsetX + (int) (x * scale), offsetY + (int) (y * scale), Math.max(1, (int) (width * scale)), Math.max(1, (int) (height * scale)));
    }
    private Component getComponentByName(String text) {
        for (Component component : getComponents())
            if (component instanceof JButton button)
                if (button.getText().equals(text)) return component;
        return null;
    }
    private Component getComponentByClass(Class<?> type, int index) {
        int currentIndex = 0;
        for (Component component : getComponents()) {
            if (type.isInstance(component)) {
                if (component == title) continue;
                if (component == modeBox) continue;
                if (component == xField) continue;
                if (component == yField) continue;
                if (currentIndex == index) return component;
                currentIndex++;
            }
        }
        return null;
    }
    private Star createRandomStar() {
        int w = Math.max(getWidth(), VIRTUAL_WIDTH);
        int h = Math.max(getHeight(), VIRTUAL_HEIGHT);

        return new Star(random.nextInt(w), random.nextInt(h), 15 + random.nextInt(30), 1 + random.nextDouble() * 4,
                random.nextDouble() * 360, -5 + random.nextDouble() * 10);
    }
    private void updateStars() {
        for (Star star : stars) {
            star.y += star.speed;
            star.rotation += star.rotationSpeed;
            if (star.y - star.size > getHeight()) {
                star.y = -star.size;
                star.x = getWidth() > 0 ? random.nextInt(getWidth()) : 0;
                star.speed = 1 + random.nextDouble() * 4;
            }
        }
    }
    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(backgroundColor);
        g2.fillRect(0, 0, getWidth(), getHeight());

        for (Star star : stars) drawStar(g2, star);
        g2.dispose();
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
        Timer timer = new Timer(16, e -> {
            time += 0.05;
            repaint();
        });
        timer.start();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();
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
        for (int i = 8; i >= 1; i--) g2d.drawString(text, -width / 2 - i / 2, i);

        g2d.setColor(Color.WHITE);
        g2d.drawString(text, -width / 2, 0);
        g2d.setTransform(old);
        g2d.dispose();
    }
}