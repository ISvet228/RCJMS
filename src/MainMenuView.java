import StyleUI.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.geom.AffineTransform;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Random;

public class MainMenuView extends JPanel {
    //region Variables
    private final Color backgroundColor = new Color(15, 15, 35);
    private final ArrayList<Star> stars = new ArrayList<>();
    private final Random random = new Random();
    private Style currentStyle = loadTheme();
    private static final Path THEME_DIR = Paths.get("tmp");
    private static final Path THEME_FILE = THEME_DIR.resolve("theme.txt");

    private boolean fullscreen = false;

    private static final String[] modes = {"OPPOSITE CORNER", "CENTER", "RANDOM EDGE"};
    private final StyledComboBox modeBox = new StyledComboBox(currentStyle, modes);

    private final StyledTextField xField = new StyledTextField(currentStyle,"25");
    private final StyledTextField yField = new StyledTextField(currentStyle, "25");
    private final StyledTextField seedField = new StyledTextField(currentStyle, "");

    private Animated3DText title;

    private final StyledButton playButton, textureEditorButton, mapEditorButton, creditsButton, exitButton, settingsButton, infoButton;
    private StyledButton settingsCloseButton, infoCloseButton;

    private final StyledLabel mazeDimensionsLabel, modeLabel, xLabel, yLabel, seedLabel;
    private StyledLabel themesTitle;

    private StyledToggle fullscreenToggle = new StyledToggle(currentStyle, "Fullscreen");
    private JDialog settingsDialog, infoDialog;

    private final Timer starTimer;
    //endregion

    public MainMenuView() {
        setPreferredSize(new Dimension(RCJMS.SCREEN_WIDTH, RCJMS.SCREEN_HEIGHT));
        setLayout(null);
        title = new Animated3DText(
                "RCJMS",
                Animated3DText.AnimationType.ROTATE
        );

        title.setBaseFontSize(55f);
        title.setAutoScale(true);

        title.setAnimationSpeed(1.0);

        title.setPulseAmount(0.06);
        title.setRotationAmount(0.05);

        title.setDepth(8);

        title.setTextColor(Color.WHITE);
        title.setDepthColor(
                new Color(255, 255, 120, 80)
        );
        title.addActionListener(e -> {title.setText(title.getText() == "RCJMS" ? "RayCasting Java Maze Simulator" : "RCJMS");
        });

        add(title);

        modeLabel = createLabel(currentStyle,"MODE");

        mazeDimensionsLabel = createLabel(currentStyle,"MAZE DIMENSIONS");
        xLabel = createLabel(currentStyle,"X");
        yLabel = createLabel(currentStyle,"Y");

        seedLabel = createLabel(currentStyle,"CUSTOM SEED");

        playButton = createButton(currentStyle, "PLAY");
        textureEditorButton = createButton(currentStyle, "TEXTURE EDITOR");
        mapEditorButton = createButton(currentStyle, "MAP EDITOR");
        creditsButton = createButton(currentStyle, "CREDITS");
        exitButton = createButton(currentStyle, "EXIT");

        settingsButton = createIconButton(currentStyle, "…");
        infoButton = createIconButton(currentStyle, "i");

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
        mapEditorButton.addActionListener(e -> RCJMS.instance.ChangeView(RCJMS.instance.mapEditorView = new MapEditorView(), "Map Editor"));
        creditsButton.addActionListener(e -> RCJMS.instance.ChangeView(RCJMS.instance.creditsView = new CreditsView(), "Credits"));
        exitButton.addActionListener(e -> System.exit(0));

        settingsButton.addActionListener(e -> {
            if (settingsDialog != null && settingsDialog.isVisible()) { settingsDialog.toFront(); return; }

            fullscreenToggle = new StyledToggle(currentStyle, "Fullscreen");
            fullscreenToggle.setSelected(fullscreen);
            fullscreenToggle.addActionListener(ev -> setFullscreen(fullscreenToggle.isSelected()));

            StyledButton flatButton = createButton(Style.FLAT, "Flat");
            StyledButton neumorphicButton = createButton(Style.NEUMORPHIC, "Neumorphic");
            StyledButton glassButton = createButton(Style.GLASS, "Glass");
            for (StyledButton b : new StyledButton[]{flatButton, neumorphicButton, glassButton}) b.setPreferredSize(new Dimension(110, 40));

            settingsCloseButton = createButton(currentStyle, "Close");

            ActionListener themeListener = ev -> {
                Style newStyle = ev.getSource() == flatButton ? Style.FLAT : ev.getSource() == neumorphicButton ? Style.NEUMORPHIC : Style.GLASS;
                if (newStyle == currentStyle) return;
                currentStyle = newStyle;
                saveTheme(newStyle);
                SyncAllUI(newStyle);
            };
            flatButton.addActionListener(themeListener);
            neumorphicButton.addActionListener(themeListener);
            glassButton.addActionListener(themeListener);

            themesTitle = createLabel(currentStyle, "Themes");
            themesTitle.setForeground(Color.WHITE);
            themesTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

            JPanel themeRow = new JPanel(new GridLayout(1, 3, 8, 0));
            themeRow.setOpaque(false);
            themeRow.add(flatButton);
            themeRow.add(neumorphicButton);
            themeRow.add(glassButton);

            JPanel themesBlock = new JPanel();
            themesBlock.setOpaque(false);
            themesBlock.setLayout(new BoxLayout(themesBlock, BoxLayout.Y_AXIS));
            themesBlock.add(themesTitle);
            themesBlock.add(Box.createVerticalStrut(8));
            themesBlock.add(themeRow);

            JPanel contentPanel = new JPanel(new BorderLayout(0, 16));
            contentPanel.setOpaque(false);
            contentPanel.add(fullscreenToggle, BorderLayout.NORTH);
            contentPanel.add(themesBlock, BorderLayout.CENTER);

            JPanel panel = new JPanel(new BorderLayout(0, 16));
            panel.setBackground(new Color(28, 28, 32));
            panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
            panel.add(contentPanel, BorderLayout.CENTER);

            JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            bottomPanel.setOpaque(false);
            bottomPanel.add(settingsCloseButton);
            panel.add(bottomPanel, BorderLayout.SOUTH);

            settingsDialog = new JDialog();
            settingsDialog.setTitle("Settings");
            settingsCloseButton.addActionListener(ev -> settingsDialog.dispose());
            settingsDialog.setContentPane(panel);
            settingsDialog.pack();
            settingsDialog.setLocationRelativeTo(this);
            settingsDialog.setAlwaysOnTop(true);
            settingsDialog.setResizable(false);
            settingsDialog.setVisible(true);
        });
        infoButton.addActionListener(e -> {
            if (infoDialog != null && infoDialog.isVisible()) { infoDialog.toFront(); return; }

            JLabel label = new JLabel("<html>Controls:<br>WASD - Move<br>SHIFT - Run</html>");
            label.setForeground(Color.WHITE);
            label.setFont(new Font("Arial", Font.PLAIN, 16));

            infoCloseButton = createButton(currentStyle, "Close");

            JPanel panel = new JPanel(new BorderLayout(0, 16));
            panel.setBackground(new Color(28, 28, 32));
            panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
            panel.add(label, BorderLayout.CENTER);

            JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            bottomPanel.setOpaque(false);
            bottomPanel.add(infoCloseButton);
            panel.add(bottomPanel, BorderLayout.SOUTH);

            infoDialog = new JDialog();
            infoDialog.setTitle("Info");
            infoCloseButton.addActionListener(ev -> infoDialog.dispose());
            infoDialog.setContentPane(panel);
            infoDialog.pack();
            infoDialog.setLocationRelativeTo(this);
            infoDialog.setAlwaysOnTop(true);
            infoDialog.setResizable(false);
            infoDialog.setVisible(true);
        });

        starTimer = new Timer(16, e -> {updateStars();repaint();});
        starTimer.start();
    }
    @Override public void addNotify() {
        super.addNotify();
        if (starTimer != null && !starTimer.isRunning()) starTimer.start();
    }
    @Override public void removeNotify() {
        if (starTimer != null && starTimer.isRunning()) starTimer.stop();
        super.removeNotify();
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
    private void setFullscreen(boolean fullscreen) {
        this.fullscreen = fullscreen;

        JFrame frame = RCJMS.instance;

        frame.dispose();

        if (fullscreen) {
            frame.setUndecorated(true);
            frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        } else {
            frame.setUndecorated(false);
            frame.setExtendedState(JFrame.NORMAL);
            frame.setSize(RCJMS.SCREEN_WIDTH, RCJMS.SCREEN_HEIGHT);
            frame.setLocationRelativeTo(null);
        }

        frame.setVisible(true);
        frame.revalidate();
        frame.repaint();
    }

    //region UI
    private StyledLabel createLabel(Style style, String text) {
        StyledLabel label = new StyledLabel(style, text);
        label.setForeground(Color.WHITE);
        return label;
    }
    private StyledButton createButton(Style style, String text) {
        StyledButton button = new StyledButton(style, text);
        button.setFocusable(false);
        return button;
    }
    private StyledButton createIconButton(Style style, String text) {
        StyledButton button = new StyledButton(style, text);
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
        xField.setHorizontalAlignment(JLabel.CENTER);
        yField.setHorizontalAlignment(JLabel.CENTER);

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

    //region Themes
    private void saveTheme(Style style) {
        try {
            Files.createDirectories(THEME_DIR);
            Files.writeString(THEME_FILE, style.name());
        } catch (IOException ex) { ex.printStackTrace(); }
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
    private void SyncAllUI(Style style) {
        modeBox.setStyle(style);

        xField.setStyle(style);
        yField.setStyle(style);
        seedField.setStyle(style);

        for (StyledButton SB : new StyledButton[]{playButton, textureEditorButton, mapEditorButton, creditsButton, exitButton, settingsButton, infoButton, settingsCloseButton, infoCloseButton})
            if (SB != null) SB.setStyle(style);
        for (StyledLabel SL : new StyledLabel[]{mazeDimensionsLabel, modeLabel, xLabel, yLabel, seedLabel, themesTitle})
            if (SL != null) SL.setStyle(style);
        if (fullscreenToggle != null) fullscreenToggle.setStyle(style);
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
        private final Timer titleTimer;

        public AnimatedTitle(String text) {
            this.text = text;
            titleTimer = new Timer(16, e -> {time += 0.05;repaint();});
            titleTimer.start();
        }

        @Override
        public void addNotify() {
            super.addNotify();
            if (titleTimer != null && !titleTimer.isRunning()) titleTimer.start();
        }

        @Override
        public void removeNotify() {
            if (titleTimer != null && titleTimer.isRunning()) titleTimer.stop();
            super.removeNotify();
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