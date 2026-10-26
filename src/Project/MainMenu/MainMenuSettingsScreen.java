package Project.MainMenu;
import Project.Game.*;
import Project.Helpers.*;
import Project.StyleUI.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class MainMenuSettingsScreen extends MainMenuPanel {
    //region Variables
    private static final String[] LANGUAGE_CODES = {"en", "ru", "sr"};
    private static final String[] LANGUAGE_NAMES = {"English", "Русский", "Srpski"};
    private static final Style[] THEMES = {Style.FLAT, Style.NEUMORPHIC, Style.GLASS};
    private static final String[] THEME_KEYS = {"st.flat", "st.neumorphic", "st.glass"};
    private static final String[] RENDER_SCALE_NAMES = {"100%", "75%", "50%", "10%"};
    private static final double[] RENDER_SCALES = {1.0, 0.75, 0.5, 0.1};
    private static final String[] QUALITY_KEYS = {"mm.quality_low", "mm.quality_medium", "mm.quality_high", "mm.quality_auto"};
    private static final String QUALITY_AUTO_HINT_KEY = "mm.quality_auto_hint";
    private static final int RESOLUTION_STEPS = 4, STEP_WIDTH = 480, STEP_HEIGHT = 270;
    private final int[][] resolutions = buildResolutions();
    private final String[] resolutionNames = buildResolutionNames();
    private static final String NO_ENGINE_LABEL = "—";
    private final String[] engineNames = RenderEngines.names().length == 0 ? new String[]{NO_ENGINE_LABEL} : RenderEngines.names();
    private final String[] engineHintKeys = RenderEngines.hintKeys();
    private final String[] qualityHintKeys = RenderEngines.qualityHintKeys();

    // Theme previews fill this design-space area; the grid grows or shrinks with the number of themes.
    private static final int PREVIEW_AREA_X = 500, PREVIEW_AREA_Y = 162, PREVIEW_AREA_W = 310, PREVIEW_AREA_H = 274;
    private static final int PREVIEW_MIN_GAP = 8, PREVIEW_MAX_GAP_Y = 20, PREVIEW_CAPTION_H = 22, PREVIEW_MAX_W = 160;
    private enum Page { INTERFACE, DISPLAY }

    private final ButtonGroup pageGroup = new ButtonGroup();
    private final StyledToggleButton interfaceTab, displayTab;
    private final ButtonGroup themeGroup = new ButtonGroup();
    private final StyledToggleButton[] themeButtons = new StyledToggleButton[THEMES.length];
    private final BackgroundPreview[] backgroundPreviews;
    private final StyledLabel[] backgroundCaptions;
    private final StyledComboBox languageBox, renderScaleBox, resolutionBox, engineBox, qualityBox;
    private final StyledToggle fullscreenToggle, vsyncToggle, openClToggle;
    private final StyledLabel heading, sectionInterface, languageCaption, themeCaption, sectionBackground;
    private final StyledLabel sectionDisplay, vsyncHint, openClHint, resolutionCaption, resolutionHint, scaleCaption, scaleHint, sectionRender;
    private final StyledLabel engineCaption, engineHint, qualityCaption, qualityHint;
    private final StyledButton backButton;
    private final StatusDot openClDot = new StatusDot();
    private final javax.swing.Timer probeWatcher = new javax.swing.Timer(400, e -> updateOpenClState());
    private final javax.swing.Timer previewAnimator = new javax.swing.Timer(40, e -> repaintBackgroundPreviews());
    private final List<Component> interfaceControls = new ArrayList<>();
    private final List<Component> displayControls = new ArrayList<>();
    private Page page = Page.DISPLAY;
    private boolean syncing;
    //endregion

    //region Constructors
    public MainMenuSettingsScreen(MainMenuHost menu) {
        super(menu);
        Style style = menu.style();

        heading = label("settings", MainMenuCanvas.ACCENT);
        interfaceTab = new StyledToggleButton(style, "mm.page_interface");
        displayTab = new StyledToggleButton(style, "mm.page_display");
        sectionInterface = label("mm.section_interface", MainMenuCanvas.ACCENT);
        sectionBackground = label("mm.section_background", MainMenuCanvas.ACCENT);
        sectionDisplay = label("mm.section_display", MainMenuCanvas.ACCENT);
        sectionRender = label("mm.section_render", MainMenuCanvas.ACCENT);
        languageCaption = label("st.language", MainMenuCanvas.TEXT);
        themeCaption = label("st.themes", MainMenuCanvas.TEXT);
        vsyncHint = label("mm.vsync_hint", MainMenuCanvas.MUTED);
        openClHint = label("mm.opencl_hint", MainMenuCanvas.MUTED);
        resolutionCaption = label("st.resolution", MainMenuCanvas.TEXT);
        resolutionHint = label("mm.resolution_hint", MainMenuCanvas.MUTED);
        scaleCaption = label("st.render_scale", MainMenuCanvas.TEXT);
        scaleHint = label("mm.render_scale_hint", MainMenuCanvas.MUTED);
        engineCaption = label("st.render_engine", MainMenuCanvas.TEXT);
        engineHint = label("engine_dda", MainMenuCanvas.MUTED);
        qualityCaption = label("st.render_quality", MainMenuCanvas.TEXT);
        qualityHint = label("quality_dda", MainMenuCanvas.MUTED);

        languageBox = new StyledComboBox(style, LANGUAGE_NAMES);
        renderScaleBox = new StyledComboBox(style, RENDER_SCALE_NAMES);
        resolutionBox = new StyledComboBox(style, resolutionNames);
        engineBox = new StyledComboBox(style, engineNames);
        qualityBox = new StyledComboBox(style, QUALITY_KEYS);
        fullscreenToggle = new StyledToggle(style, "st.fullscreen");
        vsyncToggle = new StyledToggle(style, "mm.vsync");
        openClToggle = new StyledToggle(style, "mm.opencl");
        backButton = button("mm.back");
        for (int i = 0; i < THEMES.length; i++) {
            themeButtons[i] = new StyledToggleButton(style, THEME_KEYS[i]);
            themeButtons[i].setFocusable(false);
            themeGroup.add(themeButtons[i]);
        }
        List<MenuTheme> themes = MenuThemeRegistry.all();
        backgroundPreviews = new BackgroundPreview[themes.size()];
        backgroundCaptions = new StyledLabel[themes.size()];
        for (int i = 0; i < themes.size(); i++) {
            MenuTheme theme = themes.get(i);
            backgroundPreviews[i] = new BackgroundPreview(theme, menu.screenWidth(), menu.screenHeight(),
                    () -> selectTheme(theme));
            backgroundCaptions[i] = label(theme.nameKey(), MainMenuCanvas.MUTED);
        }
        interfaceTab.setFocusable(false);
        displayTab.setFocusable(false);
        pageGroup.add(interfaceTab);
        pageGroup.add(displayTab);

        add(heading);
        add(displayTab);
        add(interfaceTab);
        add(backButton);

        interfaceControls.add(sectionInterface);
        interfaceControls.add(languageCaption);
        interfaceControls.add(languageBox);
        interfaceControls.add(themeCaption);
        interfaceControls.addAll(List.of(themeButtons));
        interfaceControls.add(sectionBackground);
        interfaceControls.addAll(List.of(backgroundPreviews));
        interfaceControls.addAll(List.of(backgroundCaptions));

        displayControls.add(sectionRender);
        displayControls.add(engineCaption);
        displayControls.add(engineBox);
        displayControls.add(engineHint);
        displayControls.add(qualityCaption);
        displayControls.add(qualityBox);
        displayControls.add(qualityHint);
        displayControls.add(scaleCaption);
        displayControls.add(renderScaleBox);
        displayControls.add(scaleHint);
        displayControls.add(sectionDisplay);
        displayControls.add(fullscreenToggle);
        displayControls.add(vsyncToggle);
        displayControls.add(vsyncHint);
        displayControls.add(openClToggle);
        displayControls.add(openClHint);
        displayControls.add(openClDot);
        displayControls.add(resolutionCaption);
        displayControls.add(resolutionBox);
        displayControls.add(resolutionHint);

        for (Component component : interfaceControls) add(component);
        for (Component component : displayControls) add(component);

        attachListeners();
        showPage(Page.DISPLAY);
        refresh();
    }
    //endregion

    //region Lifecycle=
    @Override public void addNotify() {
        super.addNotify();
        if (!previewAnimator.isRunning()) previewAnimator.start();
    }
    @Override public void removeNotify() {
        previewAnimator.stop();
        probeWatcher.stop();
        super.removeNotify();
    }
    //endregion

    //region Public API
    public void refresh() {
        syncing = true;
        try {
            languageBox.setSelectedIndex(languageIndexFor(NSLocalizedString.getLanguage()));
            for (int i = 0; i < THEMES.length; i++)
                if (THEMES[i] == menu.style()) themeButtons[i].setSelected(true);
            fullscreenToggle.setSelected(menu.isFullscreen());
            resolutionBox.setSelectedIndex(resolutionIndexFor(menu.windowWidth()));
            renderScaleBox.setSelectedIndex(renderScaleIndex(menu.getRenderScale()));
            vsyncToggle.setSelected(menu.isVSyncEnabled());
            int engine = Math.clamp(menu.getRenderEngine(), 0, Math.max(0, engineNames.length - 1));
            engineBox.setSelectedIndex(engine);
            engineBox.setEnabled(!RenderEngines.all().isEmpty());
            qualityBox.setSelectedIndex(menu.getRenderEngineQuality());
            updateEngineHints(engine);
            updateOpenClState();
            syncBackgroundSelection();
        } finally { syncing = false; }
    }
    public void syncFullscreen(boolean value) {
        fullscreenToggle.setSelected(value);
    }
    //endregion

    //region Layout
    @Override protected void layoutDesign() {
        setCard(120, 22, 720, 496);
        canvas.text(heading, 26, 120, 40, 720, 40);
        canvas.place(displayTab, 327, 88, 150, 32);
        canvas.place(interfaceTab, 483, 88, 150, 32);

        // Interface page: language and themes on the left, menu backgrounds on the right.
        canvas.text(sectionInterface, 13, 150, 134, 310, 20);
        canvas.text(languageCaption, 13, 150, 162, 310, 18);
        canvas.combo(languageBox, 150, 182, 310, 36);
        canvas.text(themeCaption, 13, 150, 232, 310, 18);
        themeButtons[0].setStyle(Style.FLAT);
        themeButtons[1].setStyle(Style.NEUMORPHIC);
        themeButtons[2].setStyle(Style.GLASS);
        for (int i = 0; i < themeButtons.length; i++) canvas.place(themeButtons[i], 150 + i * 106, 254, 98, 36);

        canvas.text(sectionBackground, 13, 500, 134, 310, 20);
        layoutBackgroundPreviews();

        // Display page: render engine, quality and scale on the left, window and display options on the right.
        canvas.text(sectionRender, 13, 150, 134, 310, 20);
        canvas.text(engineCaption, 13, 150, 162, 310, 18);
        canvas.combo(engineBox, 150, 182, 310, 36);
        canvas.text(engineHint, 12, 150, 222, 310, 18);
        canvas.text(qualityCaption, 13, 150, 248, 310, 18);
        canvas.combo(qualityBox, 150, 268, 310, 36);
        canvas.text(qualityHint, 12, 150, 308, 310, 18);
        canvas.text(scaleCaption, 13, 150, 334, 310, 18);
        canvas.combo(renderScaleBox, 150, 354, 310, 36);
        canvas.text(scaleHint, 12, 150, 394, 310, 18);

        canvas.text(sectionDisplay, 13, 500, 134, 310, 20);
        canvas.place(fullscreenToggle, 500, 162, 310, 34);
        canvas.place(vsyncToggle, 500, 204, 310, 34);
        canvas.text(vsyncHint, 12, 500, 242, 310, 18);
        canvas.place(openClToggle, 500, 264, 310, 34);
        canvas.place(openClDot, 818, 275, 12, 12);
        canvas.text(openClHint, 12, 500, 302, 310, 18);
        canvas.text(resolutionCaption, 13, 500, 324, 310, 18);
        canvas.combo(resolutionBox, 500, 344, 310, 36);
        canvas.text(resolutionHint, 12, 500, 382, 310, 18);

        canvas.place(backButton, 690, 448, 120, 38);
    }

    private void layoutBackgroundPreviews() {
        int count = backgroundPreviews.length;
        if (count == 0) return;
        int bestCols = 1, bestW = -1;
        for (int cols = 1; cols <= count; cols++) {
            int rows = (count + cols - 1) / cols;
            int widthLimit = (PREVIEW_AREA_W - (cols - 1) * PREVIEW_MIN_GAP) / cols;
            double rowHeight = (PREVIEW_AREA_H - (rows - 1) * PREVIEW_MIN_GAP) / (double) rows;
            int heightLimit = (int) ((rowHeight - PREVIEW_CAPTION_H) * 16 / 9);
            int w = Math.min(PREVIEW_MAX_W, Math.min(widthLimit, heightLimit));
            if (w > bestW) { bestW = w; bestCols = cols; }
        }
        int w = Math.max(8, bestW), h = Math.max(5, w * 9 / 16);
        int cols = bestCols, rows = (count + cols - 1) / cols;
        int gapX = cols > 1 ? Math.max(0, (PREVIEW_AREA_W - cols * w) / (cols - 1)) : 0;
        int cellH = h + PREVIEW_CAPTION_H;
        int gapY = rows > 1 ? Math.clamp((PREVIEW_AREA_H - (long) rows * cellH) / (rows - 1), 0, PREVIEW_MAX_GAP_Y) : 0;
        for (int i = 0; i < count; i++) {
            int x = PREVIEW_AREA_X + (i % cols) * (w + gapX);
            int y = PREVIEW_AREA_Y + (i / cols) * (cellH + gapY);
            canvas.place(backgroundPreviews[i], x, y, w, h);
            canvas.text(backgroundCaptions[i], 12, x, y + h + 3, w, PREVIEW_CAPTION_H - 3);
        }
    }
    @Override protected void paintDecorations(Graphics g) {
        canvas.paintDivider(g, menu.style(), 480, 134, 428);
        StyledToggleButton active = page == Page.INTERFACE ? interfaceTab : displayTab;
        g.setColor(MainMenuCanvas.ACCENT);
        g.fillRect(active.getX(), active.getY() + active.getHeight() + 3, active.getWidth(), Math.max(2, Math.round(3 * (float) canvas.scale())));
    }
    //endregion

    //region Helpers
    private void attachListeners() {
        interfaceTab.addActionListener(e -> showPage(Page.INTERFACE));
        displayTab.addActionListener(e -> showPage(Page.DISPLAY));
        languageBox.addActionListener(e -> {
            int index = languageBox.getSelectedIndex();
            if (!syncing && index >= 0 && index < LANGUAGE_CODES.length) menu.changeLanguage(LANGUAGE_CODES[index]);
        });
        for (int i = 0; i < THEMES.length; i++) {
            int index = i;
            themeButtons[i].addActionListener(e -> menu.changeStyle(THEMES[index]));
        }
        fullscreenToggle.addActionListener(e -> {
            menu.applyFullscreen(fullscreenToggle.isSelected());
            menu.saveSettings();
        });
        vsyncToggle.addActionListener(e -> {
            menu.setVSyncEnabled(vsyncToggle.isSelected());
            menu.saveSettings();
        });
        openClToggle.addActionListener(e -> {
            menu.setOpenClAcceleration(openClToggle.isSelected());
            menu.saveSettings();
            updateOpenClState();
        });
        resolutionBox.addActionListener(e -> {
            int index = resolutionBox.getSelectedIndex();
            if (!syncing && index >= 0 && index < resolutions.length) menu.setWindowSize(resolutions[index][0], resolutions[index][1]);
        });
        renderScaleBox.addActionListener(e -> {
            int index = renderScaleBox.getSelectedIndex();
            if (!syncing && index >= 0 && index < RENDER_SCALES.length) {
                menu.setRenderScale(RENDER_SCALES[index]);
                menu.saveSettings();
            }
        });
        engineBox.addActionListener(e -> {
            int index = engineBox.getSelectedIndex();
            if (!syncing && index >= 0 && index < engineNames.length && !RenderEngines.all().isEmpty()) {
                menu.setRenderEngine(index);
                updateEngineHints(index);
                updateOpenClState();
                menu.saveSettings();
            }
        });
        qualityBox.addActionListener(e -> {
            int index = qualityBox.getSelectedIndex();
            if (!syncing && index >= 0 && index < QUALITY_KEYS.length) {
                menu.setRenderEngineQuality(index);
                updateEngineHints(engineBox.getSelectedIndex());
                menu.saveSettings();
            }
        });
        backButton.addActionListener(e -> menu.showMain());
    }
    private void showPage(Page next) {
        page = next;
        for (Component component : interfaceControls) component.setVisible(next == Page.INTERFACE);
        for (Component component : displayControls) component.setVisible(next == Page.DISPLAY);
        interfaceTab.setSelected(next == Page.INTERFACE);
        displayTab.setSelected(next == Page.DISPLAY);
        updateEngineHints(engineBox.getSelectedIndex());
        repaint();
    }
    private void selectTheme(MenuTheme theme) {
        if (syncing) return;
        menu.setMenuTheme(theme);
        syncBackgroundSelection();
    }
    private void repaintBackgroundPreviews() { for (BackgroundPreview preview : backgroundPreviews) if (preview.isShowing()) preview.repaint(); }
    private void syncBackgroundSelection() {
        MenuTheme current = menu.getMenuTheme();
        for (BackgroundPreview preview : backgroundPreviews) preview.setSelected(preview.theme() == current);
    }
    private void updateOpenClState() {
        int index = engineBox.getSelectedIndex();
        List<RenderEngine> engines = RenderEngines.all();
        RenderEngine engine = index >= 0 && index < engines.size() ? engines.get(index) : null;
        boolean supported = engine != null && engine.hasOpenCL();
        if (!supported && menu.isOpenClAcceleration()) menu.setOpenClAcceleration(false);
        boolean on = menu.isOpenClAcceleration();

        Project.OpenCl.GpuRuntime.Health health = !on || engine == null ? Project.OpenCl.GpuRuntime.Health.OFF
                : Project.OpenCl.GpuRuntime.healthFor(engine.openClKey());
        openClToggle.setEnabled(supported);
        openClToggle.setSelected(on);
        if (!supported) {
            openClHint.setToolTipText(null);
            openClHint.setLocalizationKey("mm.opencl_unsupported");
        }
        else if (health == Project.OpenCl.GpuRuntime.Health.UNAVAILABLE) showOpenClProblem(Project.OpenCl.GpuRuntime.reasonFor(engine.openClKey()));
        else {
            openClHint.setToolTipText(null);
            openClHint.setLocalizationKey("mm.opencl_hint");
        }

        Color dot = switch (health) {
            case READY -> new Color(0x2ECC71);
            case PROBING -> new Color(0xF1C40F);
            case UNAVAILABLE -> new Color(0xE74C3C);
            case OFF -> Color.GRAY;
        };
        openClDot.setColor(dot);

        if (health == Project.OpenCl.GpuRuntime.Health.PROBING) {
            if (!probeWatcher.isRunning()) probeWatcher.start();
        } else probeWatcher.stop();
    }
    private void showOpenClProblem(String reason) {
        String full = reason == null ? "" : reason.strip();
        if (full.isEmpty()) {
            openClHint.setToolTipText(null);
            openClHint.setLocalizationKey("mm.opencl_unavailable");
            return;
        }
        String brief = full.lines().findFirst().orElse("").strip().replaceFirst("^OpenCL unavailable: ", "");
        if (brief.length() > 60) brief = brief.substring(0, 57) + "...";
        String summary = brief;
        openClHint.setLocalizationFormat("mm.opencl_reason", () -> new Object[]{summary});
        openClHint.setToolTipText(full);
    }
    private void updateEngineHints(int engine) {
        if (engine >= 0 && engine < engineHintKeys.length && engineHintKeys[engine] != null) {
            engineHint.setLocalizationKey(engineHintKeys[engine]);
            setDisplayVisible(engineHint, true);
        } else setDisplayVisible(engineHint, false);
        boolean auto = qualityBox.getSelectedIndex() == RenderEngineConfig.QUALITY_AUTO;
        String qualityKey = auto ? QUALITY_AUTO_HINT_KEY : engine >= 0 && engine < qualityHintKeys.length ? qualityHintKeys[engine] : null;
        if (qualityKey != null) {
            qualityHint.setLocalizationKey(qualityKey);
            setDisplayVisible(qualityHint, true);
        } else setDisplayVisible(qualityHint, false);
        renderScaleBox.setEnabled(!auto);
    }

    private void setDisplayVisible(Component component, boolean visible) { component.setVisible(visible && page == Page.DISPLAY); }
    private static int languageIndexFor(String code) {
        if (code == null) return 0;
        String normalized = code.trim().toLowerCase(java.util.Locale.ROOT);
        for (int i = 0; i < LANGUAGE_CODES.length; i++) {
            String c = LANGUAGE_CODES[i];
            if (normalized.equals(c) || normalized.startsWith(c + "-") || normalized.startsWith(c + "_")) return i;
        }
        return 0;
    }
    private int resolutionIndexFor(int width) {
        int index = 0;
        for (int i = 0; i < resolutions.length; i++)
            if (resolutions[i][0] <= width) index = i;
        return index;
    }
    private static int renderScaleIndex(double scale) { return scale >= 0.99 ? 0 : scale >= 0.74 ? 1 : scale >= 0.49 ? 2 : 3; }
    private int[][] buildResolutions() {
        int[][] options = new int[RESOLUTION_STEPS + 1][2];
        for (int i = 0; i <= RESOLUTION_STEPS; i++) {
            options[i][0] = menu.screenWidth() + STEP_WIDTH * i;
            options[i][1] = menu.screenHeight() + STEP_HEIGHT * i;
        }
        return options;
    }
    private String[] buildResolutionNames() {
        String[] names = new String[resolutions.length];
        for (int i = 0; i < resolutions.length; i++) names[i] = resolutions[i][0] + "x" + resolutions[i][1];
        return names;
    }
    //endregion

    //region Components
    private static final class BackgroundPreview extends JComponent {
        private final MenuTheme theme;
        private final int designWidth, designHeight;
        private boolean selected;
        BackgroundPreview(MenuTheme theme, int designWidth, int designHeight, Runnable onSelect) {
            this.theme = theme;
            this.designWidth = designWidth;
            this.designHeight = designHeight;
            setOpaque(false);
            setFocusable(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() { @Override public void mouseClicked(MouseEvent e) { onSelect.run(); } });
        }
        MenuTheme theme() { return theme; }
        void setSelected(boolean value) {
            if (value != selected) { selected = value; repaint(); }
        }
        @Override protected void paintComponent(Graphics g) {
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            RoundRectangle2D shape = new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, 8, 8);
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.clip(new RoundRectangle2D.Double(0, 0, w, h, 8, 8));
                g2.scale((double) w / designWidth, (double) h / designHeight);
                theme.paintBase(g2, designWidth, designHeight, MenuThemeRegistry.clock());
                theme.paintOverlay(g2, designWidth, designHeight);
            } finally { g2.dispose(); }

            Graphics2D border = (Graphics2D) g.create();
            try {
                border.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                border.setStroke(new BasicStroke(selected ? 2f : 1f));
                border.setColor(selected ? MainMenuCanvas.ACCENT : new Color(255, 255, 255, 70));
                border.draw(shape);
            } finally { border.dispose(); }
        }
    }
    private static final class StatusDot extends JComponent {
        private Color color = Color.GRAY;
        StatusDot() { setOpaque(false); }
        void setColor(Color value) {
            if (!value.equals(color)) { color = value; repaint(); }
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.fillOval(0, 0, getWidth() - 1, getHeight() - 1);
            g2.dispose();
        }
    }
    //endregion

    //region Styling
    @Override protected Collection<? extends Component> unstyledComponents() { return List.of(themeButtons); }
    //endregion
}