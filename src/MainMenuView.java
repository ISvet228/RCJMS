import StyleUI.*;
import Helpers.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

public class MainMenuView extends JPanel {
    //region Variables
    private final ArrayList<Star> stars = new ArrayList<>();
    private final Random random = new Random();

    private Style currentStyle = SaveData.load().theme;

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
    private static final String[] geometryModes = {"mm.euclidean_geometry", "mm.wrong_geometry", "mm.looped_geometry"};
    private final StyledComboBox geometryBox = new StyledComboBox(currentStyle, geometryModes);
    private final String[] languages = {"en", "ru"};
    private final String[] languageNames = {"English", "Русский"};
    private final String[] renderScales = {"100%", "75%", "50%", "10%"};
    private final String[] RayTracingQuality = {"Low", "Medium", "High"};
    private StyledComboBox languageBox, renderScaleBox, rayTracingQualityBox, resolutionBox;

    private static final int RES_STEP_WIDTH = 480;
    private static final int RES_STEP_HEIGHT = 270;
    private static final int RES_STEP_COUNT = 4;
    private static final int[][] resolutionOptions = buildResolutionOptions();
    private static final String[] resolutionLabels = buildResolutionLabels();

    private final StyledLabel mazeDimensionsLabel = createLabel(currentStyle,"mm.maze_dimensions");
    private final StyledLabel modeLabel = createLabel(currentStyle,"mm.mode");
    private final StyledLabel geometryLabel = createLabel(currentStyle, "mm.geometry");
    private final StyledLabel floorsLabel = createLabel(currentStyle, "mm.floors_count");
    private final StyledLabel xLabel = createLabel(currentStyle,"X");
    private final StyledLabel yLabel = createLabel(currentStyle,"Y");
    private final StyledLabel seedLabel = createLabel(currentStyle,"mm.custom_seed");
    private final StyledLabel languageTitle = createLabel(currentStyle, "st.language");
    private final StyledLabel themesTitle = createLabel(currentStyle, "st.themes");
    private final StyledLabel renderScaleTitle = createLabel(currentStyle, "st.render_scale");

    private final StyledTextField xField = new StyledTextField(currentStyle,"25");
    private final StyledTextField yField = new StyledTextField(currentStyle, "25");
    private final StyledTextField seedField = new StyledTextField(currentStyle, "");
    private final StyledTextField floorsField = new StyledTextField(currentStyle, "3");

    private final StyledToggle mode3DToggle = new StyledToggle(currentStyle, "mm.3d_mode");
    private StyledToggle fullscreenToggle, vsyncToggle, rayTracingToggle;
    private boolean fullscreen = false;
    private int windowWidth = RCJMS.SCREEN_WIDTH;
    private int windowHeight = RCJMS.SCREEN_HEIGHT;

    private JDialog settingsDialog, infoDialog;
    private final Timer starTimer;
    //endregion

    //region Constructor
    public MainMenuView() {
        applySavedSettings();

        setPreferredSize(new Dimension(RCJMS.SCREEN_WIDTH, RCJMS.SCREEN_HEIGHT));
        setLayout(null);

        setupTitle();
        setupFields();
        mode3DToggle.addActionListener(ev -> {
            floorsLabel.setVisible(mode3DToggle.isSelected());
            floorsField.setVisible(mode3DToggle.isSelected());
        });

        addComponents();
        setupMainButtons();
        settingsButton.addActionListener(e -> openSettingsDialog());
        infoButton.addActionListener(e -> openInfoDialog());
        for (int i = 0; i < 80; i++) stars.add(createRandomStar());
        starTimer = new Timer(16, e -> { updateStars(); repaint(); });
        starTimer.start();
    }

    private void setupTitle() {
        title.setBaseFontSize(55f);
        title.setAutoScale(true);
        title.setAnimationSpeed(1.0);
        title.setPulseAmount(0.06);
        title.setRotationAmount(0.05);
        title.setDepth(6);
        title.setTextColor(new Color(255, 127, 0));
        title.setDepthColor(Color.cyan);
        title.addActionListener((ActionEvent e) -> title.setText(title.getText().equals("RCJMS") ? "RayCasting Java Maze Simulator" : "RCJMS"));
    }
    private void setupFields() {
        xField.setHorizontalAlignment(JLabel.CENTER);
        yField.setHorizontalAlignment(JLabel.CENTER);

        floorsLabel.setVisible(false);
        floorsField.setHorizontalAlignment(JLabel.CENTER);
        floorsField.setVisible(false);
    }
    private void addComponents() {
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
        add(geometryBox);
        add(geometryLabel);
        add(floorsLabel);
        add(xLabel);
        add(yLabel);
        add(seedLabel);
        add(yField);
        add(xField);
        add(seedField);
        add(floorsField);
        add(mode3DToggle);
    }

    private void setupMainButtons() {
        playButton.addActionListener(e -> StartGameView());
        textureEditorButton.addActionListener(e -> {
            try { RCJMS.instance.ChangeView(RCJMS.instance.textureEditorView = new TextureEditorView(), "te.texture_editor"); }
            catch (IOException ex) { throw new RuntimeException(ex);
            }});
        mapEditorButton.addActionListener(e -> RCJMS.instance.ChangeView(RCJMS.instance.mapEditorView = new MapEditorView(), "me.map_editor"));
        creditsButton.addActionListener(e -> RCJMS.instance.ChangeView(RCJMS.instance.creditsView = new CreditsView(), "cv.credits"));
        exitButton.addActionListener(e -> System.exit(0));
    }
    //endregion

    //region Settings Dialog
    private void openSettingsDialog() {
        if (settingsDialog != null && settingsDialog.isVisible()) {
            settingsDialog.toFront();
            return;
        }

        languageBox = new StyledComboBox(currentStyle, languageNames);
        languageBox.setSelectedIndex(NSLocalizedString.getLanguage().equalsIgnoreCase("ru") ? 1 : 0);
        languageBox.addActionListener(ev -> {
            int index = languageBox.getSelectedIndex();
            if (index >= 0 && index < languages.length && !languages[index].equalsIgnoreCase(NSLocalizedString.getLanguage())) {
                saveSettings(languages[index], currentStyle);
                NSLocalizedString.setLanguage(languages[index]);
                NSLocalizedString.refresh(settingsDialog);
            }
        });

        renderScaleBox = new StyledComboBox(currentStyle, renderScales);
        int renderScaleIndex = GameView.getRenderScale() >= 0.99 ? 0 : GameView.getRenderScale() >= 0.74 ? 1 : GameView.getRenderScale() >= 0.49 ? 2 : 3;
        renderScaleBox.setSelectedIndex(renderScaleIndex);
        renderScaleBox.addActionListener(ev -> {
            int index = renderScaleBox.getSelectedIndex();
            GameView.setRenderScale(index == 0 ? 1.0 : index == 1 ? 0.75 : index == 2 ? 0.5 : 0.1);
            saveSettings();
        });

        resolutionBox = new StyledComboBox(currentStyle, resolutionLabels);
        resolutionBox.setSelectedIndex(resolutionIndexFor(windowWidth));
        resolutionBox.setEnabled(!fullscreen);
        resolutionBox.addActionListener(ev -> {
            int index = resolutionBox.getSelectedIndex();
            if (index < 0 || index >= resolutionOptions.length) return;
            windowWidth = resolutionOptions[index][0];
            windowHeight = resolutionOptions[index][1];
            RCJMS.GAME_WIDTH = windowWidth;
            RCJMS.GAME_HEIGHT = windowHeight;
            if (!fullscreen) applyWindowSize(windowWidth, windowHeight);
            saveSettings();
        });

        createGraphicsToggles();
        JTabbedPane tabs = createSettingsTabs();
        settingsCloseButton = createButton(currentStyle, "close");
        settingsCloseButton.addActionListener(ev -> settingsDialog.dispose());
        settingsDialog = createSettingsDialog(tabs);
    }
    private void createGraphicsToggles() {
        fullscreenToggle = new StyledToggle(currentStyle, "st.fullscreen");
        fullscreenToggle.setSelected(fullscreen);
        fullscreenToggle.addActionListener(ev -> {
            setFullscreen(fullscreenToggle.isSelected());
            if (resolutionBox != null) resolutionBox.setEnabled(!fullscreen);
            saveSettings();
        });

        vsyncToggle = new StyledToggle(currentStyle, "VSync");
        vsyncToggle.setSelected(GameView.isVSyncEnabled());
        vsyncToggle.addActionListener(ev -> {
            GameView.setVSyncEnabled(vsyncToggle.isSelected());
            saveSettings();
        });

        rayTracingToggle = new StyledToggle(currentStyle, "st.ray_tracing");
        rayTracingToggle.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        rayTracingToggle.setSelected(GameView.isRayTracingEnabled());

        rayTracingQualityBox = new StyledComboBox(currentStyle, RayTracingQuality);
        rayTracingQualityBox.setSelectedIndex(GameView.getRayTracingQuality());
        rayTracingQualityBox.setEnabled(GameView.isRayTracingEnabled());

        rayTracingToggle.addActionListener(ev -> {
            boolean enabled = rayTracingToggle.isSelected();
            GameView.setRayTracingEnabled(enabled);
            rayTracingQualityBox.setEnabled(enabled);
            saveSettings();
        });
        rayTracingQualityBox.addActionListener(ev -> {
            if (rayTracingQualityBox.isEnabled()) {
                GameView.setRayTracingQuality(rayTracingQualityBox.getSelectedIndex());
                saveSettings();
            }
        });
    }
    private JTabbedPane createSettingsTabs() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        tabs.setUI(new javax.swing.plaf.basic.BasicTabbedPaneUI() {
            @Override protected void paintTabBackground(Graphics g, int tabPlacement, int tabIndex, int x, int y, int w, int h, boolean isSelected) {
                g.setColor(isSelected ? new Color(75, 75, 75) : new Color(48, 48, 48));
                tabs.setForeground(isSelected ? Color.WHITE : Color.lightGray);
            }
            @Override protected void paintFocusIndicator(Graphics g, int tabPlacement, Rectangle[] rects, int tabIndex, Rectangle iconRect, Rectangle textRect, boolean isSelected) { }
            @Override protected void paintContentBorder(Graphics g, int tabPlacement, int selectedIndex) { }
        });
        tabs.addTab("Graphics", createGraphicsPage());
        tabs.addTab("UI", createUIPage());
        return tabs;
    }
    private JPanel createGraphicsPage() {
        JPanel graphics = createSettingsPage();
        graphics.add(toggleRow(fullscreenToggle, vsyncToggle)); graphics.add(Box.createVerticalStrut(14));
        graphics.add(settingsRow("st.resolution", resolutionBox)); graphics.add(Box.createVerticalStrut(14));
        graphics.add(settingsRow("st.render_scale", renderScaleBox)); graphics.add(Box.createVerticalStrut(14));
        graphics.add(rayTracingToggle); graphics.add(Box.createVerticalStrut(14));
        graphics.add(settingsRow("st.ray_tracing_quality", rayTracingQualityBox)); graphics.add(Box.createVerticalStrut(12));
        return graphics;
    }
    private JPanel createUIPage() {
        JPanel ui = createSettingsPage();
        ui.add(settingsRow("st.language", languageBox));
        ui.add(Box.createVerticalStrut(14));

        themesTitle.setPreferredSize(new Dimension(180, 20));
        ui.add(themesTitle); ui.add(Box.createVerticalStrut(14));
        StyledButton flatButton = createThemeButton(Style.FLAT, "st.flat");
        StyledButton neumorphicButton = createThemeButton(Style.NEUMORPHIC, "st.neumorphic");
        StyledButton glassButton = createThemeButton(Style.GLASS, "st.glass");
        ActionListener themeListener = ev -> {
            Style newStyle = ev.getSource() == flatButton ? Style.FLAT : ev.getSource() == neumorphicButton ? Style.NEUMORPHIC : Style.GLASS;
            if (newStyle != currentStyle) { currentStyle = newStyle; saveSettings(NSLocalizedString.getLanguage(), newStyle); SyncAllUI(newStyle); }
        };
        flatButton.addActionListener(themeListener);
        neumorphicButton.addActionListener(themeListener);
        glassButton.addActionListener(themeListener);

        JPanel themeRow = new JPanel(new GridLayout(1, 3, 10, 0));
        themeRow.setOpaque(false);
        themeRow.add(flatButton);
        themeRow.add(neumorphicButton);
        themeRow.add(glassButton);
        ui.add(themeRow);
        return ui;
    }
    private StyledButton createThemeButton(Style style, String label) {
        StyledButton button = createButton(style, label);
        button.setPreferredSize(new Dimension(2, 20));
        return button;
    }
    private JDialog createSettingsDialog(JComponent content) {
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        bottom.setOpaque(false);
        bottom.add(settingsCloseButton);

        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBackground(RCJMS.MY_FAV_GRAY);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panel.add(content, BorderLayout.CENTER);
        panel.add(bottom, BorderLayout.SOUTH);

        JDialog dialog = new JDialog();
        NSLocalizedString.bind(dialog, "st.settings", dialog::setTitle);
        dialog.setContentPane(panel);
        dialog.setSize(560, 390);
        dialog.setLocationRelativeTo(this);
        dialog.setAlwaysOnTop(true);
        dialog.setResizable(false);
        dialog.setVisible(true);
        return dialog;
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
            frame.setSize(windowWidth, windowHeight);
            frame.setLocationRelativeTo(null);
        }

        frame.setVisible(true);
        frame.revalidate();
        frame.repaint();
    }
    private void applyWindowSize(int width, int height) {
        if (fullscreen) return;
        JFrame frame = RCJMS.instance;
        if (frame == null) return;
        frame.setSize(width, height);
        frame.setLocationRelativeTo(null);
        frame.revalidate();
        frame.repaint();
    }
    //endregion

    private void openInfoDialog() {
        if (infoDialog != null && infoDialog.isVisible()) {
            infoDialog.toFront();
            return;
        }

        JLabel label = new JLabel("<html>if.controls<br>if.move<br>run</html>");
        label.setForeground(Color.WHITE);
        label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));

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
    }

    //region Settings Layout Helpers
    private JPanel createSettingsPage() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(18, 24, 18, 24));
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        return panel;
    }

    private JPanel settingsRow(String name, JComponent component) {
        JPanel row = new JPanel(new BorderLayout(18, 0));
        row.setOpaque(false);
        StyledLabel label = new StyledLabel(currentStyle, name, false, false);
        label.setForeground(Color.WHITE);
        label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        row.add(label, BorderLayout.WEST);
        row.add(component, BorderLayout.CENTER);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        return row;
    }

    private JPanel toggleRow(JComponent left, JComponent right) {
        JPanel row = new JPanel(new GridLayout(1, 2, 18, 0));
        row.setOpaque(false);
        row.add(left);
        row.add(right);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        return row;
    }
    //endregion

    //region Game Launch
    private void StartGameView() {
        try {
            GameView gameView = createGameView(Math.clamp(Integer.parseInt(xField.getText()), 5, 200), Math.clamp(Integer.parseInt(yField.getText()), 5, 200),
                    modeBox.getSelectedIndex(), geometryBox.getSelectedIndex(), parseSeed(), parseLayers());
            RCJMS.instance.ChangeView(RCJMS.instance.gameView = gameView, "RayCast Me!");
            RCJMS.instance.gameView.start();
        }
        catch (NumberFormatException | IOException ex) {
            JOptionPane.showMessageDialog(this, "mm.maze_size_error", "mm.invalid_input", JOptionPane.ERROR_MESSAGE);
        }
    }
    private Long parseSeed() {
        String seedText = seedField.getText().trim();
        if (seedText.isEmpty()) return null;
        try { return Long.parseLong(seedText); }
        catch (NumberFormatException ex) { return (long) seedText.hashCode(); }
    }
    private int parseLayers() {
        String text = floorsField.getText().trim();
        return text.isEmpty() ? 3 : Math.clamp(Integer.parseInt(text), 2, 20);
    }
    private GameView createGameView(int w, int h, int mode, int geometry, Long seed, int layers) throws IOException {
        if (mode3DToggle.isSelected()) return seed == null ? new GameView(w, h, mode, geometry, layers) : new GameView(w, h, mode, geometry, seed, layers);
        return seed == null ? new GameView(w, h, mode, geometry) : new GameView(w, h, mode, geometry, seed);
    }
    //endregion

    //region UI Factories
    private StyledLabel createLabel(Style style, String text) {
        StyledLabel label = new StyledLabel(style, text); label.setForeground(Color.WHITE); return label; }
    private StyledButton createButton(Style style, String text) {
        StyledButton button = new StyledButton(style, text); button.setFocusable(false); return button; }
    private StyledButton createIconButton(Style style, String text) {
        StyledButton button = new StyledButton(style, text); button.setFocusable(false); return button; }
    //endregion

    //region Layout
    @Override public void doLayout() {
        int width = getWidth(), height = getHeight();
        if (width <= 0 || height <= 0) return;

        double scale = Math.min((double) width / RCJMS.SCREEN_WIDTH, (double) height / RCJMS.SCREEN_HEIGHT);

        int backgroundX = (width - (int) (RCJMS.SCREEN_WIDTH * scale)) / 2;
        int backgroundY = (height - (int) (RCJMS.SCREEN_HEIGHT * scale)) / 2;

        setScaledBounds(title, 300, 45, 400, 130, scale, backgroundX, backgroundY);

        setScaledBounds(modeBox, 90, 235, 220, 40, scale, backgroundX, backgroundY);//
        setScaledBounds(mazeDimensionsLabel, 660, 230, 270, 30, scale, backgroundX, backgroundY);
        setScaledBounds(modeLabel, 100, 200, 200, 30, scale, backgroundX, backgroundY);//
        setScaledBounds(xLabel, 650, 275, 30, 35, scale, backgroundX, backgroundY);
        setScaledBounds(yLabel, 790, 275, 30, 35, scale, backgroundX, backgroundY);
        setScaledBounds(seedLabel, 400, 180, 200, 30, scale, backgroundX, backgroundY);
        setScaledBounds(floorsLabel, 90, 425, 130, 30, scale, backgroundX, backgroundY);
        setScaledBounds(yField, 820, 270, 90, 45, scale, backgroundX, backgroundY);
        setScaledBounds(xField, 680, 270, 90, 45, scale, backgroundX, backgroundY);
        setScaledBounds(seedField, 400, 220, 200, 40, scale, backgroundX, backgroundY);
        setScaledBounds(floorsField, 225, 420, 75, 36, scale, backgroundX, backgroundY);
        setScaledBounds(geometryLabel, 100, 290, 200, 30, scale, backgroundX, backgroundY);//
        setScaledBounds(geometryBox, 90, 325, 220, 40, scale, backgroundX, backgroundY);//
        setScaledBounds(mode3DToggle, 90, 385, 220, 34, scale, backgroundX, backgroundY);//

        setScaledBounds(playButton, 390, 280, 220, 60, scale, backgroundX, backgroundY);
        setScaledBounds(textureEditorButton, 395, 350, 210, 40, scale, backgroundX, backgroundY);
        setScaledBounds(mapEditorButton, 400, 400, 200, 35, scale, backgroundX, backgroundY);
        setScaledBounds(creditsButton, 405, 445, 190, 30, scale, backgroundX, backgroundY);
        setScaledBounds(exitButton, 410, 485, 180, 28, scale, backgroundX, backgroundY);

        setScaledBounds(settingsButton, RCJMS.SCREEN_WIDTH - 80, 20, 60, 60, scale, backgroundX, backgroundY);
        setScaledBounds(infoButton, 20, RCJMS.SCREEN_HEIGHT - 80, 60, 60, scale, backgroundX, backgroundY);
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
    private void saveSettings() { saveSettings(NSLocalizedString.getLanguage(),  currentStyle); }
    private void saveSettings(String language, Style style) {
        try { SaveData.saveSettings(language, style, GameView.getRenderScale(), GameView.isVSyncEnabled(), GameView.isRayTracingEnabled(), GameView.getRayTracingQuality(), windowWidth, windowHeight, fullscreen);}
        catch (IOException ex) { ex.printStackTrace(); }
    }
    private void applySavedSettings() {
        SaveData.Data savedData = SaveData.load();
        GameView.setRenderScale(savedData.renderScale);
        GameView.setVSyncEnabled(savedData.vsync);
        GameView.setRayTracingEnabled(savedData.rayTracing);
        GameView.setRayTracingQuality(savedData.rayTracingQuality);
        NSLocalizedString.setLanguage(savedData.language);
        windowWidth = savedData.windowWidth;
        windowHeight = savedData.windowHeight;
        fullscreen = savedData.fullscreen;
        RCJMS.GAME_WIDTH = windowWidth;
        RCJMS.GAME_HEIGHT = windowHeight;
    }
    private int resolutionIndexFor(int width) {
        int index = 0;
        for (int i = 0; i < resolutionOptions.length; i++)
            if (resolutionOptions[i][0] <= width) index = i;
        return index;
    }
    private void SyncAllUI(Style style) {
        for (StyledTextField STF : new StyledTextField[]{xField, yField, floorsField, seedField})
            if (STF != null) STF.setStyle(style);
        for (StyledButton SB : new StyledButton[]{playButton, textureEditorButton, mapEditorButton, creditsButton, exitButton, settingsButton, infoButton, settingsCloseButton, infoCloseButton})
            if (SB != null) SB.setStyle(style);
        for (StyledLabel SL : new StyledLabel[]{mazeDimensionsLabel, modeLabel, xLabel, yLabel, floorsLabel, seedLabel, themesTitle, languageTitle, renderScaleTitle, geometryLabel})
            if (SL != null) SL.setStyle(style);
        for (StyledComboBox SCB : new StyledComboBox[]{modeBox, geometryBox, languageBox, renderScaleBox, rayTracingQualityBox, resolutionBox})
            if (SCB != null) SCB.setStyle(style);
        for (StyledToggle ST : new StyledToggle[]{mode3DToggle, fullscreenToggle, vsyncToggle, rayTracingToggle})
            if (ST != null) ST.setStyle(style);
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

    //region Helpers
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
    private static int[][] buildResolutionOptions() {
        int[][] options = new int[RES_STEP_COUNT + 1][2];
        for (int i = 0; i <= RES_STEP_COUNT; i++) {
            options[i][0] = RCJMS.SCREEN_WIDTH + RES_STEP_WIDTH * i;
            options[i][1] = RCJMS.SCREEN_HEIGHT + RES_STEP_HEIGHT * i;
        }
        return options;
    }
    private static String[] buildResolutionLabels() {
        String[] labels = new String[resolutionOptions.length];
        for (int i = 0; i < resolutionOptions.length; i++) labels[i] = resolutionOptions[i][0] + "x" + resolutionOptions[i][1];
        return labels;
    }
    //endregion
}