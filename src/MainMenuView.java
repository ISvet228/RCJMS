import StyleUI.*;
import Helpers.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Random;

public class MainMenuView extends JPanel {
    //region Variables
    private final ArrayList<Star> stars = new ArrayList<>();
    private final Random random = new Random();

    private Style currentStyle = loadTheme();

    private final Animated3DText title = new Animated3DText("RCJMS", Animated3DText.AnimationType.ROTATE);

    private final StyledButton playButton = createButton(currentStyle, "mm.play");
    private final StyledButton textureEditorButton = createButton(currentStyle, "mm.texture_editor");
    private final StyledButton mapEditorButton = createButton(currentStyle, "mm.map_editor");
    private final StyledButton creditsButton = createButton(currentStyle, "mm.credits");
    private final StyledButton exitButton = createButton(currentStyle, "exit");
    private final StyledButton settingsButton = createIconButton(currentStyle, "…");
    private final StyledButton infoButton = createIconButton(currentStyle, "i");
    private StyledButton settingsCloseButton, infoCloseButton;

    private static final String[] modes = {"mm.opposite_corner", "mm.center", "mm.random_edge"};
    private final StyledComboBox modeBox = new StyledComboBox(currentStyle, modes);
    private final String[] languages = {"en", "ru"};
    private final String[] languageNames = {"English", "Русский"};
    private StyledComboBox languageBox;

    private final StyledLabel mazeDimensionsLabel = createLabel(currentStyle,"mm.maze_dimensions");
    private final StyledLabel modeLabel = createLabel(currentStyle,"mm.mode");
    private final StyledLabel floorsLabel = createLabel(currentStyle, "mm.floors_count");
    private final StyledLabel xLabel = createLabel(currentStyle,"X");
    private final StyledLabel yLabel = createLabel(currentStyle,"Y");
    private final StyledLabel seedLabel = createLabel(currentStyle,"mm.custom_seed");
    private final StyledLabel languageTitle = createLabel(currentStyle, "st.language");
    private final StyledLabel themesTitle = createLabel(currentStyle, "st.themes");

    private final StyledTextField xField = new StyledTextField(currentStyle,"25");
    private final StyledTextField yField = new StyledTextField(currentStyle, "25");
    private final StyledTextField seedField = new StyledTextField(currentStyle, "");
    private final StyledTextField floorsField = new StyledTextField(currentStyle, "3");

    private final StyledToggle geometryToggle = new StyledToggle(currentStyle, "mm.wrong_geometry");
    private final StyledToggle mode3DToggle = new StyledToggle(currentStyle, "mm.3d_mode");
    private StyledToggle fullscreenToggle;
    private boolean fullscreen = false;

    private JDialog settingsDialog, infoDialog;
    private final Timer starTimer;
    //endregion

    public MainMenuView() {
        NSLocalizedString.setLanguage(loadLanguage());
        setPreferredSize(new Dimension(RCJMS.SCREEN_WIDTH, RCJMS.SCREEN_HEIGHT));
        setLayout(null);

        title.setBaseFontSize(55f);
        title.setAutoScale(true);
        title.setAnimationSpeed(1.0);
        title.setPulseAmount(0.06);
        title.setRotationAmount(0.05);
        title.setDepth(6);
        title.setTextColor(new Color(255, 127, 0));
        title.setDepthColor(Color.cyan);
        title.addActionListener((ActionEvent e) -> title.setText(title.getText().equals("RCJMS") ? "RayCasting Java Maze Simulator" : "RCJMS"));

        xField.setHorizontalAlignment(JLabel.CENTER);
        yField.setHorizontalAlignment(JLabel.CENTER);

        floorsLabel.setVisible(false);
        floorsField.setHorizontalAlignment(JLabel.CENTER);
        floorsField.setVisible(false);
        mode3DToggle.addActionListener(ev -> { floorsLabel.setVisible(mode3DToggle.isSelected()); floorsField.setVisible(mode3DToggle.isSelected()); });

        add(title);
        add(playButton);
        add(textureEditorButton);
        add(mapEditorButton);
        add(creditsButton);
        add(exitButton);
        add(settingsButton);
        add(infoButton);
        add(modeBox);
        add(mazeDimensionsLabel);
        add(modeLabel);
        add(floorsLabel);
        add(xLabel);
        add(yLabel);
        add(seedLabel);
        add(yField);
        add(xField);
        add(seedField);
        add(floorsField);
        add(geometryToggle);
        add(mode3DToggle);

        for (int i = 0; i < 80; i++) stars.add(createRandomStar());

        playButton.addActionListener(e -> StartGameView());
        textureEditorButton.addActionListener(e -> {
            try {RCJMS.instance.ChangeView(RCJMS.instance.textureEditorView = new TextureEditorView(), "te.texture_editor");}
            catch (IOException ex) {throw new RuntimeException(ex);}});
        mapEditorButton.addActionListener(e -> RCJMS.instance.ChangeView(RCJMS.instance.mapEditorView = new MapEditorView(), "me.map_editor"));
        creditsButton.addActionListener(e -> RCJMS.instance.ChangeView(RCJMS.instance.creditsView = new CreditsView(), "cv.credits"));
        exitButton.addActionListener(e -> System.exit(0));

        settingsButton.addActionListener(e -> {
            if (settingsDialog != null && settingsDialog.isVisible()) { settingsDialog.toFront(); return; }

            fullscreenToggle = new StyledToggle(currentStyle, "st.fullscreen");
            fullscreenToggle.setSelected(fullscreen);
            fullscreenToggle.addActionListener(ev -> setFullscreen(fullscreenToggle.isSelected()));

            StyledButton flatButton = createButton(Style.FLAT, "st.flat");
            StyledButton neumorphicButton = createButton(Style.NEUMORPHIC, "st.neumorphic");
            StyledButton glassButton = createButton(Style.GLASS, "st.glass");

            languageTitle.setForeground(Color.WHITE);
            languageTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

            languageBox = new StyledComboBox(currentStyle, languageNames);
            languageBox.setSelectedIndex(NSLocalizedString.getLanguage().equalsIgnoreCase("ru") ? 1 : 0);
            languageBox.addActionListener(ev -> {
                int index = languageBox.getSelectedIndex();
                if (index >= 0 && index < languages.length && !languages[index].equalsIgnoreCase(NSLocalizedString.getLanguage())) {
                    saveLanguage(languages[index]);
                    NSLocalizedString.setLanguage(languages[index]);
                    NSLocalizedString.refresh(settingsDialog);
                }
            });
            for (StyledButton b : new StyledButton[]{flatButton, neumorphicButton, glassButton}) b.setPreferredSize(new Dimension(110, 40));
            settingsCloseButton = createButton(currentStyle, "close");

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

            themesBlock.add(languageTitle);
            themesBlock.add(Box.createVerticalStrut(8));
            themesBlock.add(languageBox);
            themesBlock.add(Box.createVerticalStrut(16));
            themesBlock.add(themesTitle);
            themesBlock.add(Box.createVerticalStrut(8));
            themesBlock.add(themeRow);

            JPanel contentPanel = new JPanel(new BorderLayout(0, 16));
            contentPanel.setOpaque(false);
            contentPanel.add(fullscreenToggle, BorderLayout.NORTH);
            contentPanel.add(themesBlock, BorderLayout.CENTER);

            JPanel panel = new JPanel(new BorderLayout(0, 16));
            panel.setBackground(RCJMS.MY_FAV_GRAY);
            panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
            panel.add(contentPanel, BorderLayout.CENTER);

            JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            bottomPanel.setOpaque(false);
            bottomPanel.add(settingsCloseButton);
            panel.add(bottomPanel, BorderLayout.SOUTH);

            settingsDialog = new JDialog();
            NSLocalizedString.bind(settingsDialog, "st.settings", settingsDialog::setTitle);
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

            JLabel label = new JLabel("<html>if.controls<br>if.move<br>run</html>");
            label.setForeground(Color.WHITE);
            label.setFont(new Font("Arial", Font.PLAIN, 16));

            infoCloseButton = createButton(currentStyle, "close");

            JPanel panel = new JPanel(new BorderLayout(0, 16));
            panel.setBackground(RCJMS.MY_FAV_GRAY);
            panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
            panel.add(label, BorderLayout.CENTER);

            JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            bottomPanel.setOpaque(false);
            bottomPanel.add(infoCloseButton);
            panel.add(bottomPanel, BorderLayout.SOUTH);

            infoDialog = new JDialog();
            NSLocalizedString.bind(infoDialog, "if.info", infoDialog::setTitle);
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
        if (settingsDialog != null && settingsDialog.isDisplayable()) settingsDialog.dispose();
        if (infoDialog != null && infoDialog.isDisplayable()) infoDialog.dispose();
        super.removeNotify();
    }
    private void StartGameView() {
        try {
            GameView gameView;
            int mazeWidth = Math.clamp(Integer.parseInt(xField.getText()), 5, 200);
            int mazeHeight = Math.clamp(Integer.parseInt(yField.getText()), 5, 200);
            int mode = modeBox.getSelectedIndex();
            int geometryMode = geometryToggle.isSelected() ? 1 : 0;

            String seedText = seedField.getText().trim();
            Long seed = null;
            if (!seedText.isEmpty()) {
                try {seed = Long.parseLong(seedText);}
                catch (NumberFormatException ex) {seed = (long) seedText.hashCode();}
            }
            int layers = !floorsField.getText().trim().isEmpty() ? Math.clamp(Integer.parseInt(floorsField.getText()), 2, 20) : 3;
            if (mode3DToggle.isSelected())
                gameView = seed == null ? new GameView(mazeWidth, mazeHeight, mode, geometryMode, layers) : new GameView(mazeWidth, mazeHeight, mode, geometryMode, seed, layers);
            else gameView = seed == null ? new GameView(mazeWidth, mazeHeight, mode, geometryMode) : new GameView(mazeWidth, mazeHeight, mode, geometryMode, seed);

            RCJMS.instance.ChangeView(RCJMS.instance.gameView = gameView, "RayCast Me!");
            RCJMS.instance.gameView.start();
        }
        catch (NumberFormatException | IOException ex) {
            JOptionPane.showMessageDialog(this, "mm.maze_size_error", "mm.invalid_input", JOptionPane.ERROR_MESSAGE);
        }
    }
    private void setFullscreen(boolean fullscreen) {
        this.fullscreen = fullscreen;
        JFrame frame = RCJMS.instance;
        frame.dispose();

        if (fullscreen) {
            frame.setUndecorated(true);
            frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        }
        else {
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
        int width = getWidth(), height = getHeight();
        if (width <= 0 || height <= 0) return;

        double scale = Math.min((double) width / RCJMS.SCREEN_WIDTH, (double) height / RCJMS.SCREEN_HEIGHT);

        int backgroundX = (width - (int) (RCJMS.SCREEN_WIDTH * scale)) / 2;
        int backgroundY = (height - (int) (RCJMS.SCREEN_HEIGHT * scale)) / 2;

        setScaledBounds(title, 300, 45, 400, 130, scale, backgroundX, backgroundY);

        setScaledBounds(modeBox, 90, 305, 220, 40, scale, backgroundX, backgroundY);

        setScaledBounds(playButton, 390, 280, 220, 60, scale, backgroundX, backgroundY);
        setScaledBounds(textureEditorButton, 395, 350, 210, 40, scale, backgroundX, backgroundY);
        setScaledBounds(mapEditorButton, 400, 400, 200, 35, scale, backgroundX, backgroundY);
        setScaledBounds(creditsButton, 405, 445, 190, 30, scale, backgroundX, backgroundY);
        setScaledBounds(exitButton, 410, 485, 180, 28, scale, backgroundX, backgroundY);
        setScaledBounds(settingsButton, RCJMS.SCREEN_WIDTH - 80, 20, 60, 60, scale, backgroundX, backgroundY);
        setScaledBounds(infoButton, 20, RCJMS.SCREEN_HEIGHT - 80, 60, 60, scale, backgroundX, backgroundY);

        setScaledBounds(mazeDimensionsLabel, 660, 270, 270, 30, scale, backgroundX, backgroundY);
        setScaledBounds(modeLabel, 100, 270, 200, 30, scale, backgroundX, backgroundY);
        setScaledBounds(xLabel, 650, 315, 30, 35, scale, backgroundX, backgroundY);
        setScaledBounds(yLabel, 790, 315, 30, 35, scale, backgroundX, backgroundY);
        setScaledBounds(seedLabel, 400, 180, 200, 30, scale, backgroundX, backgroundY);
        setScaledBounds(floorsLabel, 90, 431, 130, 30, scale, backgroundX, backgroundY);

        setScaledBounds(yField, 820, 310, 90, 45, scale, backgroundX, backgroundY);
        setScaledBounds(xField, 680, 310, 90, 45, scale, backgroundX, backgroundY);
        setScaledBounds(seedField, 400, 220, 200, 40, scale, backgroundX, backgroundY);
        setScaledBounds(floorsField, 225, 428, 75, 36, scale, backgroundX, backgroundY);

        setScaledBounds(geometryToggle, 90, 353, 220, 34, scale, backgroundX, backgroundY);
        setScaledBounds(mode3DToggle, 90, 393, 220, 34, scale, backgroundX, backgroundY);
    }
    private void setScaledBounds(JComponent component, int x, int y, int width, int height, double scale, int backgroundX, int backgroundY) {
        component.setBounds(backgroundX + (int) (x * scale), backgroundY + (int) (y * scale), Math.max(1, (int) (width * scale)), Math.max(1, (int) (height * scale)));
    }
    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        int width = getWidth(), height = getHeight();
        if (width <= 0 || height <= 0) return;

        double scale = Math.min((double) width / RCJMS.SCREEN_WIDTH, (double) height / RCJMS.SCREEN_HEIGHT);

        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, width, height);
        g2d.translate((width - (int) (RCJMS.SCREEN_WIDTH * scale)) / 2, (height - (int) (RCJMS.SCREEN_HEIGHT * scale)) / 2);
        g2d.scale(scale, scale);
        g2d.setColor(new Color(15, 15, 35));
        g2d.fillRect(0, 0, RCJMS.SCREEN_WIDTH, RCJMS.SCREEN_HEIGHT);

        for (Star star : stars) drawStar(g2d, star);

        g2d.dispose();
    }
    //endregion

    //region Saves
    private void saveLanguage(String language) {
        try {
            Files.createDirectories(AppPaths.DATA_DIR);
            Files.writeString(AppPaths.LANGUAGE_FILE, language);
        } catch (IOException ex) { ex.printStackTrace(); }
    }
    private String loadLanguage() {
        try {
            if (Files.exists(AppPaths.LANGUAGE_FILE)) {
                String language = Files.readString(AppPaths.LANGUAGE_FILE).trim();
                if (!language.isBlank()) return language;
            }
        } catch (IOException ex) { ex.printStackTrace(); }
        return Locale.getDefault().getLanguage();
    }

    private void saveTheme(Style style) {
        try {
            Files.createDirectories(AppPaths.DATA_DIR);
            Files.writeString(AppPaths.THEME_FILE, style.name());
        } catch (IOException ex) { ex.printStackTrace(); }
    }
    private Style loadTheme() {
        try {
            if (Files.exists(AppPaths.THEME_FILE)) {
                String name = Files.readString(AppPaths.THEME_FILE).trim();
                return Style.valueOf(name);
            }
        } catch (IOException | IllegalArgumentException ex) { ex.printStackTrace(); }
        return Style.FLAT;
    }
    private void SyncAllUI(Style style) {
        modeBox.setStyle(style);
        geometryToggle.setStyle(style);
        mode3DToggle.setStyle(style);

        for (StyledTextField STF : new StyledTextField[]{xField, yField, floorsField, seedField}) if (STF != null) STF.setStyle(style);
        for (StyledButton SB : new StyledButton[]{playButton, textureEditorButton, mapEditorButton, creditsButton, exitButton, settingsButton, infoButton, settingsCloseButton, infoCloseButton})
            if (SB != null) SB.setStyle(style);
        for (StyledLabel SL : new StyledLabel[]{mazeDimensionsLabel, modeLabel, xLabel, yLabel, seedLabel, floorsLabel, themesTitle, languageTitle})
            if (SL != null) SL.setStyle(style);
        if (fullscreenToggle != null) fullscreenToggle.setStyle(style);
        if (languageBox != null) languageBox.setStyle(style);
    }
    //endregion

    //region STARES
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
        private double x, y, speed, rotation, rotationSpeed;
        private final int size;
        public Star(double x, double y, int size, double speed, double rotation, double rotationSpeed) {
            this.x = x; this.y = y; this.size = size; this.speed = speed; this.rotation = rotation; this.rotationSpeed = rotationSpeed;
        }
    }
    //endregion
}