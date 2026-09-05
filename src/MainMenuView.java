import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

public class MainMenuView extends JPanel {
    //region Variables
    private final Color backgroundColor = new Color(15, 15, 35);
    private final ArrayList<Star> stars = new ArrayList<>();
    private final Random random = new Random();

    private static final String[] modes = {"OPPOSITE CORNER", "CENTER", "RANDOM EDGE"};

    private final JComboBox<String> modeBox = new JComboBox<>(modes);

    private final JTextField xField = new JTextField("25");
    private final JTextField yField = new JTextField("25");
    private final JTextField seedField = new JTextField();

    private final AnimatedTitle title;

    private final JButton playButton, textureEditorButton, mapEditorButton, creditsButton, exitButton, settingsButton, infoButton;
    private final JLabel mazeDimensionsLabel, modeLabel, xLabel, yLabel, seedLabel;
    //endregion

    public MainMenuView() {
        setPreferredSize(new Dimension(RCJMS.SCREEN_WIDTH, RCJMS.SCREEN_HEIGHT));
        setLayout(null);

        title = new AnimatedTitle("RCJMS");

        modeLabel = createLabel("MODE", 18);
        modeBox.setFont(new Font("Arial", Font.PLAIN, 18));
        modeBox.setFocusable(false);

        mazeDimensionsLabel = createLabel("MAZE DIMENSIONS", 20);
        xLabel = createLabel("X", 18);
        xField.setFont(new Font("Arial", Font.PLAIN, 20));
        xField.setHorizontalAlignment(JTextField.CENTER);
        yLabel = createLabel("Y", 18);
        yField.setFont(new Font("Arial", Font.PLAIN, 20));
        yField.setHorizontalAlignment(JTextField.CENTER);

        seedLabel = createLabel("CUSTOM SEED", 18);
        seedField.setFont(new Font("Arial", Font.PLAIN, 20));
        seedField.setHorizontalAlignment(JTextField.CENTER);

        playButton = createButton("PLAY", 22);
        textureEditorButton = createButton("TEXTURE EDITOR", 17);
        mapEditorButton = createButton("MAP EDITOR", 16);
        creditsButton = createButton("CREDITS", 15);
        exitButton = createButton("EXIT", 14);

        settingsButton = createIconButton("…");
        infoButton = createIconButton("i");

        add(title);
        add(modeLabel);
        add(modeBox);
        add(mazeDimensionsLabel);
        add(xLabel);
        add(xField);
        add(yLabel);
        add(yField);
        add(seedLabel);
        add(seedField);
        add(playButton);
        add(textureEditorButton);
        add(mapEditorButton);
        add(creditsButton);
        add(exitButton);
        add(settingsButton);
        add(infoButton);

        for (int i = 0; i < 80; i++) stars.add(createRandomStar());

        playButton.addActionListener(e -> StartGameView());
        textureEditorButton.addActionListener(e -> {
            try {RCJMS.instance.ChangeView(RCJMS.instance.textureEditorView = new TextureEditorView(), "Texture Editor");}
            catch (IOException ex) {throw new RuntimeException(ex);}});
        exitButton.addActionListener(e -> System.exit(0));
        settingsButton.addActionListener(e -> JOptionPane.showMessageDialog(this, "Settings",
                "Settings", JOptionPane.INFORMATION_MESSAGE));
        infoButton.addActionListener(e -> JOptionPane.showMessageDialog(this, "RayCast Me!\n3D Maze Game",
                "Info", JOptionPane.INFORMATION_MESSAGE));

        Timer timer = new Timer(16, e -> {updateStars();repaint();});
        timer.start();
    }

    private void StartGameView() {
        try {
            int mazeWidth = Math.clamp(Integer.parseInt(xField.getText()), 5, 200);
            int mazeHeight = Math.clamp(Integer.parseInt(yField.getText()), 5, 200);
            int mode = modeBox.getSelectedIndex();
            String seedText = seedField.getText().trim();
            if (seedText.isEmpty()) RCJMS.instance.ChangeView(RCJMS.instance.gameView = new GameView(mazeWidth, mazeHeight, mode), "RayCast Me!");
            else {
                long seed;
                try {seed = Long.parseLong(seedText);}
                catch (NumberFormatException ex) {seed = seedText.hashCode();}
                RCJMS.instance.ChangeView(RCJMS.instance.gameView = new GameView(mazeWidth, mazeHeight, mode, seed), "RayCast Me!");
            }
            RCJMS.instance.gameView.start();
        } catch (NumberFormatException | IOException ex) {
            JOptionPane.showMessageDialog(this, "Maze size must be a number.", "Invalid Input", JOptionPane.ERROR_MESSAGE);
        }
    }

    //region UI
    private JLabel createLabel(String text, int fontSize) {
        JLabel label = new JLabel(text);
        label.setForeground(Color.WHITE);
        label.setFont(new Font("Arial", Font.BOLD, fontSize));
        label.setHorizontalAlignment(SwingConstants.CENTER);
        return label;
    }
    private JButton createButton(String text, int fontSize) {
        JButton button = new JButton(text);
        button.setFont(new Font("Arial", Font.BOLD, fontSize));
        button.setFocusPainted(false);
        button.setFocusable(false);
        return button;
    }
    private JButton createIconButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font("Arial", Font.BOLD, 30));
        button.setFocusPainted(false);
        button.setFocusable(false);
        return button;
    }
    @Override public void doLayout() {
        int width = getWidth();
        int height = getHeight();

        if (width <= 0 || height <= 0) return;

        double scale = Math.min((double) width / RCJMS.SCREEN_WIDTH, (double) height / RCJMS.SCREEN_HEIGHT);

        int backgroundX = (width - (int) (RCJMS.SCREEN_WIDTH * scale)) / 2;
        int backgroundY = (height - (int) (RCJMS.SCREEN_HEIGHT * scale)) / 2;

        setScaledBounds(title, 300, 45, 400, 130, scale, backgroundX, backgroundY);

        setScaledBounds(modeLabel, 100, 270, 200, 30, scale, backgroundX, backgroundY);
        setScaledBounds(modeBox, 90, 305, 220, 40, scale, backgroundX, backgroundY);

        setScaledBounds(mazeDimensionsLabel, 660, 270, 270, 30, scale, backgroundX, backgroundY);

        setScaledBounds(xLabel, 650, 315, 30, 35, scale, backgroundX, backgroundY);
        setScaledBounds(xField, 680, 310, 90, 45, scale, backgroundX, backgroundY);

        setScaledBounds(yLabel, 790, 315, 30, 35, scale, backgroundX, backgroundY);
        setScaledBounds(yField, 820, 310, 90, 45, scale, backgroundX, backgroundY);

        setScaledBounds(seedLabel, 400, 180, 200, 30, scale, backgroundX, backgroundY);
        setScaledBounds(seedField, 400, 220, 200, 40, scale, backgroundX, backgroundY);

        setScaledBounds(playButton, 390, 280, 220, 60, scale, backgroundX, backgroundY);
        setScaledBounds(textureEditorButton, 395, 350, 210, 40, scale, backgroundX, backgroundY);
        setScaledBounds(mapEditorButton, 400, 400, 200, 35, scale, backgroundX, backgroundY);
        setScaledBounds(creditsButton, 405, 445, 190, 30, scale, backgroundX, backgroundY);
        setScaledBounds(exitButton, 410, 485, 180, 28, scale, backgroundX, backgroundY);

        setScaledBounds(settingsButton, RCJMS.SCREEN_WIDTH - 80, 20, 60, 60, scale, backgroundX, backgroundY);
        setScaledBounds(infoButton, 20, RCJMS.SCREEN_HEIGHT - 80, 60, 60, scale, backgroundX, backgroundY);
    }
    private void setScaledBounds(Component component, int x, int y, int width, int height, double scale, int backgroundX, int backgroundY) {
        component.setBounds(backgroundX + (int) (x * scale), backgroundY + (int) (y * scale), Math.max(1, (int) (width * scale)), Math.max(1, (int) (height * scale)));
    }
    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;

        double scale = Math.min((double) width / RCJMS.SCREEN_WIDTH, (double) height / RCJMS.SCREEN_HEIGHT);

        int offsetX = (width - (int) (RCJMS.SCREEN_WIDTH * scale)) / 2;
        int offsetY = (height - (int) (RCJMS.SCREEN_HEIGHT * scale)) / 2;

        Graphics2D g2 = (Graphics2D) g.create();

        g2.setColor(Color.BLACK);
        g2.fillRect(0, 0, width, height);
        g2.translate(offsetX, offsetY);
        g2.scale(scale, scale);
        g2.setColor(backgroundColor);
        g2.fillRect(0, 0, RCJMS.SCREEN_WIDTH, RCJMS.SCREEN_HEIGHT);

        for (Star star : stars) drawStar(g2, star);

        g2.dispose();
    }
    //endregion

    //region Decorations
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
    static class Star {
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
    static class AnimatedTitle extends JComponent {
        private final String text;
        private double time = 0;

        public AnimatedTitle(String text) {
            this.text = text;
            Timer timer = new Timer(16, e -> {time += 0.05;repaint();});
            timer.start();
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            AffineTransform old = g2d.getTransform();

            g2d.translate(getWidth() / 2, getHeight() / 2);
            g2d.rotate(Math.sin(time * 0.7) * 0.08);
            g2d.scale(1 + Math.sin(time) * 0.08, 1 + Math.sin(time) * 0.08);
            g2d.setFont(new Font("Arial", Font.BOLD, 55));

            int width = g2d.getFontMetrics().stringWidth(text);
            g2d.setColor(new Color(120, 180, 255, 80));
            for (int i = 8; i >= 1; i--) g2d.drawString(text, -width / 2 - i / 2, i);

            g2d.setColor(Color.WHITE);
            g2d.drawString(text, -width / 2, 0);
            g2d.setTransform(old);
            g2d.dispose();
        }
    }
    //endregion
}