package MainMenuComponents;

import Helpers.*;
import StyleUI.*;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.List;

public final class MainMenuSettingsScreen extends MainMenuPanel {
    //region Variables
    private static final String[] LANGUAGE_CODES = {"en", "ru"};
    private static final String[] LANGUAGE_NAMES = {"English", "Русский"};
    private static final Style[] THEMES = {Style.FLAT, Style.NEUMORPHIC, Style.GLASS};
    private static final String[] THEME_KEYS = {"st.flat", "st.neumorphic", "st.glass"};
    private static final String[] RENDER_SCALE_NAMES = {"100%", "75%", "50%", "10%"};
    private static final double[] RENDER_SCALES = {1.0, 0.75, 0.5, 0.1};
    private static final String[] QUALITY_KEYS = {"mm.quality_low", "mm.quality_medium", "mm.quality_high"};
    private static final int RESOLUTION_STEPS = 4, STEP_WIDTH = 480, STEP_HEIGHT = 270;
    private final int[][] resolutions = buildResolutions();
    private final String[] resolutionNames = buildResolutionNames();

    private final ButtonGroup themeGroup = new ButtonGroup();
    private final StyledToggleButton[] themeButtons = new StyledToggleButton[THEMES.length];
    private final StyledComboBox languageBox, renderScaleBox, resolutionBox, qualityBox;
    private final StyledToggle fullscreenToggle, vsyncToggle, rayTracingToggle;
    private final StyledLabel heading, sectionInterface, languageCaption, themeCaption, sectionDisplay, vsyncHint;
    private final StyledLabel resolutionCaption, resolutionHint, scaleCaption, scaleHint, sectionLighting, rayTracingHint, qualityCaption;
    private final StyledButton backButton;
    private boolean syncing;
    //endregion

    //region Constructors
    public MainMenuSettingsScreen(MainMenuHost menu) {
        super(menu);
        Style style = menu.style();

        heading = label("mm.settings", MainMenuCanvas.ACCENT);
        sectionInterface = label("mm.section_interface", MainMenuCanvas.ACCENT);
        sectionDisplay = label("mm.section_display", MainMenuCanvas.ACCENT);
        sectionLighting = label("mm.section_lighting", MainMenuCanvas.ACCENT);
        languageCaption = label("st.language", MainMenuCanvas.TEXT);
        themeCaption = label("st.themes", MainMenuCanvas.TEXT);
        vsyncHint = label("mm.vsync_hint", MainMenuCanvas.MUTED);
        resolutionCaption = label("st.resolution", MainMenuCanvas.TEXT);
        resolutionHint = label("mm.resolution_hint", MainMenuCanvas.MUTED);
        scaleCaption = label("st.render_scale", MainMenuCanvas.TEXT);
        scaleHint = label("mm.render_scale_hint", MainMenuCanvas.MUTED);
        rayTracingHint = label("mm.rt_hint", MainMenuCanvas.MUTED);
        qualityCaption = label("st.ray_tracing_quality", MainMenuCanvas.TEXT);

        languageBox = new StyledComboBox(style, LANGUAGE_NAMES);
        renderScaleBox = new StyledComboBox(style, RENDER_SCALE_NAMES);
        resolutionBox = new StyledComboBox(style, resolutionNames);
        qualityBox = new StyledComboBox(style, QUALITY_KEYS);
        fullscreenToggle = new StyledToggle(style, "st.fullscreen");
        vsyncToggle = new StyledToggle(style, "mm.vsync");
        rayTracingToggle = new StyledToggle(style, "st.ray_tracing");
        backButton = button("mm.back");
        for (int i = 0; i < THEMES.length; i++) {
            themeButtons[i] = new StyledToggleButton(style, THEME_KEYS[i]);
            themeButtons[i].setFocusable(false);
            themeGroup.add(themeButtons[i]);
        }

        add(heading);
        add(sectionInterface);
        add(languageCaption);
        add(languageBox);
        add(themeCaption);
        for (StyledToggleButton themeButton : themeButtons) add(themeButton);
        add(sectionLighting);
        add(rayTracingToggle);
        add(rayTracingHint);
        add(qualityCaption);
        add(qualityBox);
        add(sectionDisplay);
        add(fullscreenToggle);
        add(vsyncToggle);
        add(vsyncHint);
        add(resolutionCaption);
        add(resolutionBox);
        add(resolutionHint);
        add(scaleCaption);
        add(renderScaleBox);
        add(scaleHint);
        add(backButton);

        attachListeners();
        refresh();
    }
    //endregion

    //region Public API
    public void refresh() {
        syncing = true;
        try {
            languageBox.setSelectedIndex(NSLocalizedString.getLanguage().equalsIgnoreCase("ru") ? 1 : 0);
            for (int i = 0; i < THEMES.length; i++)
                if (THEMES[i] == menu.style()) themeButtons[i].setSelected(true);
            fullscreenToggle.setSelected(menu.isFullscreen());
            resolutionBox.setSelectedIndex(resolutionIndexFor(menu.windowWidth()));
            resolutionBox.setEnabled(!menu.isFullscreen());
            renderScaleBox.setSelectedIndex(renderScaleIndex(menu.getRenderScale()));
            vsyncToggle.setSelected(menu.isVSyncEnabled());
            rayTracingToggle.setSelected(menu.isRayTracingEnabled());
            qualityBox.setSelectedIndex(menu.getRayTracingQuality());
            qualityBox.setEnabled(menu.isRayTracingEnabled());
        } finally {
            syncing = false;
        }
    }
    public void syncFullscreen(boolean value) {
        fullscreenToggle.setSelected(value);
        resolutionBox.setEnabled(!value);
    }
    //endregion

    //region Layout
    @Override protected void layoutDesign() {
        setCard(120, 22, 720, 496);
        canvas.text(heading, 26, 120, 40, 720, 40);

        canvas.text(sectionInterface, 13, 150, 96, 310, 20);
        canvas.text(languageCaption, 13, 150, 124, 310, 18);
        canvas.combo(languageBox, 150, 144, 310, 36);
        canvas.text(themeCaption, 13, 150, 194, 310, 18);
        themeButtons[0].setStyle(Style.FLAT);
        themeButtons[1].setStyle(Style.NEUMORPHIC);
        themeButtons[2].setStyle(Style.GLASS);
        for (int i = 0; i < themeButtons.length; i++) canvas.place(themeButtons[i], 150 + i * 106, 216, 98, 36);
        canvas.text(sectionLighting, 13, 150, 276, 310, 20);
        canvas.place(rayTracingToggle, 150, 304, 310, 34);
        canvas.text(rayTracingHint, 12, 150, 342, 310, 18);
        canvas.text(qualityCaption, 13, 150, 372, 310, 18);
        canvas.combo(qualityBox, 150, 392, 310, 36);

        canvas.text(sectionDisplay, 13, 500, 96, 310, 20);
        canvas.place(fullscreenToggle, 500, 124, 310, 34);
        canvas.place(vsyncToggle, 500, 166, 310, 34);
        canvas.text(vsyncHint, 12, 500, 204, 310, 18);
        canvas.text(resolutionCaption, 13, 500, 232, 310, 18);
        canvas.combo(resolutionBox, 500, 252, 310, 36);
        canvas.text(resolutionHint, 12, 500, 290, 310, 18);
        canvas.text(scaleCaption, 13, 500, 318, 310, 18);
        canvas.combo(renderScaleBox, 500, 338, 310, 36);
        canvas.text(scaleHint, 12, 500, 376, 310, 18);

        canvas.place(backButton, 690, 448, 120, 38);
    }
    @Override protected void paintDecorations(Graphics g) {
        canvas.paintDivider(g, menu.style(), 480, 96, 428);
    }
    //endregion

    //region Helpers
    private void attachListeners() {
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
            resolutionBox.setEnabled(!menu.isFullscreen());
            menu.saveSettings();
        });
        vsyncToggle.addActionListener(e -> {
            menu.setVSyncEnabled(vsyncToggle.isSelected());
            menu.saveSettings();
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
        rayTracingToggle.addActionListener(e -> {
            boolean enabled = rayTracingToggle.isSelected();
            menu.setRayTracingEnabled(enabled);
            qualityBox.setEnabled(enabled);
            menu.saveSettings();
        });
        qualityBox.addActionListener(e -> {
            if (!syncing && qualityBox.isEnabled()) {
                menu.setRayTracingQuality(qualityBox.getSelectedIndex());
                menu.saveSettings();
            }
        });
        backButton.addActionListener(e -> menu.showMain());
    }
    private int resolutionIndexFor(int width) {
        int index = 0;
        for (int i = 0; i < resolutions.length; i++)
            if (resolutions[i][0] <= width) index = i;
        return index;
    }
    private static int renderScaleIndex(double scale) {
        return scale >= 0.99 ? 0 : scale >= 0.74 ? 1 : scale >= 0.49 ? 2 : 3;
    }
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
    //region Styling
    @Override protected Collection<? extends Component> unstyledComponents() { return List.of(themeButtons); }
    //endregion
}