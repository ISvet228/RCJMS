package Project.MainMenu;

import Project.Helpers.*;
import Project.Helpers.maze.MazeAlgorithms;
import Project.Net.MazeSpec;
import Project.StyleUI.*;

import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.time.LocalDate;

public final class MazeSettingsPanel extends JPanel {
    //region Variables
    //region Layout
    public static final int WIDTH = 500;
    private static final int FOOTER_BASIC = 204, FOOTER_ADVANCED = 376;
    private static final int FOOTER_HEIGHT = 60;
    public static final int BASIC_HEIGHT = FOOTER_BASIC + FOOTER_HEIGHT, ADVANCED_HEIGHT = FOOTER_ADVANCED + FOOTER_HEIGHT;
    public static final int SECTION_WIDTH = 428;
    public static final int STACKED_HEIGHT = 440;
    private static final int STACK_ROGUE_ROW = 318, STACK_CREATE_ROW = 360, STACK_SUMMARY_ROW = 412;
    private boolean savedAdvanced;
    private static final int PRESET_GAP = 16, SEED_WIDTH = 180, DAILY_WIDTH = 150, FIELD_GAP = 10;
    private static final String HORROR_KEY = "horror.enabled";
    //endregion
    //region Mode Options
    private static final int[] PRESET_SIZES = {15, 25, 40};
    private static final String[] PRESET_KEYS = {"mm.size_small", "mm.size_medium", "mm.size_large"};
    private static final String[] FINISH_MODES = {"opposite_corner", "center", "random_edge"};
    private static final String[] GEOMETRY_MODES = {"mm.euclidean_geometry", "wrong_geometry", "looped_geometry"};
    private static final int GEOMETRY_EUCLIDEAN = 0, GEOMETRY_WRONG = 1, GEOMETRY_LOOPED = 2;
    private static final int MIN_MAZE_SIZE = 5, MAX_MAZE_SIZE = 200, MAX_MAZE_SIZE_CTRL = 1000, MIN_FLOORS = 2, MAX_FLOORS = 20;
    //endregion
    //region State
    private final MainMenuView menu;
    private final MainMenuCanvas canvas = new MainMenuCanvas(WIDTH, BASIC_HEIGHT);
    private int columnWidth = WIDTH;
    private boolean engineAllowsWrong = true;
    private boolean wrongGeometryAllowed = true;
    private boolean wheelHeld;
    private boolean loadingValues;
    private boolean advancedOpen;
    private boolean roguelikeOption;
    private Runnable startAction = () -> { };
    //endregion
    //region Presets
    private final ButtonGroup presetGroup = new ButtonGroup();
    private final StyledToggleButton[] presetButtons = new StyledToggleButton[PRESET_SIZES.length];
    private final StyledLabel[] presetSizeLabels = new StyledLabel[PRESET_SIZES.length];
    //endregion
    //region Form Controls
    private final StyledTextField xField, yField, seedField, floorsField;
    private final StyledComboBox finishBox, geometryBox, algorithmBox;
    private final StyledToggle floorsToggle, fogToggle, advancedToggle, roguelikeToggle, horrorToggle;
    private final StyledButton dailyButton;
    //endregion
    //region Labels
    private final StyledLabel sizeCaption, seedCaption, seedHint, summary, dimensionsCaption, floorsCaption;
    private final StyledLabel finishCaption, geometryCaption, algorithmCaption, timesLabel;
    //endregion
    //endregion

    //region Constructors
    public MazeSettingsPanel(MainMenuView menu) {
        this.menu = menu;
        Style style = menu.style();
        setOpaque(false);
        setLayout(null);

        sizeCaption = label("mm.size", MainMenuCanvas.TEXT);
        seedCaption = label("custom_seed", MainMenuCanvas.TEXT);
        seedHint = label("mm.seed_hint", MainMenuCanvas.MUTED);
        dimensionsCaption = label("mm.maze_dimensions", MainMenuCanvas.TEXT);
        floorsCaption = label("floors_count", MainMenuCanvas.TEXT);
        finishCaption = label("mm.finish_position", MainMenuCanvas.TEXT);
        geometryCaption = label("mm.geometry", MainMenuCanvas.TEXT);
        algorithmCaption = label("mm.algorithm", MainMenuCanvas.TEXT);
        timesLabel = label("×", MainMenuCanvas.TEXT);
        summary = label("", MainMenuCanvas.MUTED);

        xField = new StyledTextField(style, "25");
        yField = new StyledTextField(style, "25");
        seedField = new StyledTextField(style, "");
        floorsField = new StyledTextField(style, "3");
        xField.setHorizontalAlignment(JTextField.CENTER);
        yField.setHorizontalAlignment(JTextField.CENTER);
        floorsField.setHorizontalAlignment(JTextField.CENTER);
        seedField.limitInput(SeedUtil.LENGTH, SeedUtil::isAllowedChar);

        finishBox = new StyledComboBox(style, FINISH_MODES);
        geometryBox = new StyledComboBox(style, GEOMETRY_MODES);
        algorithmBox = new StyledComboBox(style, MazeAlgorithms.all().stream().map(MazeAlgorithm::nameKey).toArray());
        floorsToggle = new StyledToggle(style, "mm.3d_mode");
        fogToggle = new StyledToggle(style, "mm.fog_minimap");
        advancedToggle = new StyledToggle(style, "mm.advanced");
        roguelikeToggle = new StyledToggle(style, "mode.menu.roguelike");
        horrorToggle = new StyledToggle(style, "mode.menu.horror");
        roguelikeToggle.setVisible(false);
        dailyButton = new StyledButton(style, "mm.daily");
        dailyButton.setFocusable(false);

        for (int i = 0; i < PRESET_SIZES.length; i++) {
            presetButtons[i] = new StyledToggleButton(style, PRESET_KEYS[i]);
            presetButtons[i].setFocusable(false);
            presetSizeLabels[i] = label(PRESET_SIZES[i] + " × " + PRESET_SIZES[i], MainMenuCanvas.MUTED);
            presetGroup.add(presetButtons[i]);
        }

        for (int i = 0; i < PRESET_SIZES.length; i++) {
            add(presetButtons[i]);
            add(presetSizeLabels[i]);
        }
        add(sizeCaption);
        add(seedCaption);
        add(seedField);
        add(dailyButton);
        add(seedHint);
        add(dimensionsCaption);
        add(summary);
        add(xField);
        add(timesLabel);
        add(yField);
        add(algorithmCaption);
        add(algorithmBox);
        add(floorsToggle);
        add(fogToggle);
        add(horrorToggle);
        add(floorsCaption);
        add(floorsField);
        add(finishCaption);
        add(finishBox);
        add(geometryCaption);
        add(geometryBox);
        add(advancedToggle);
        add(roguelikeToggle);

        loadSavedMazeSettings();
        attachListeners();
        applyAdvancedVisibility();
        updateSummary();
    }
    //endregion

    //region Hosting
    public void attachTo(MainMenuPanel host) {
        startAction = host.mazeAction();
        setRoguelikeOption(host.mazeRoguelikeOption());
        if (getParent() != host) host.add(this);
        host.doLayout();
        host.revalidate();
        host.repaint();
    }
    public void refresh() {
        loadingValues = true;
        try {
            syncPresetSelection();
            algorithmBox.setSelectedIndex(MazeAlgorithms.all().indexOf(MazeAlgorithms.selected()));
        } finally { loadingValues = false; }
    }
    public void applyStyle(Style style) { canvas.applyStyle(this, style); }
    @Override public void doLayout() { layoutNow(); }
    public void layoutNow() {
        canvas.resize(designWidth(), designHeight());
        if (!canvas.measureWidth(this)) return;
        if (canvas.sizeChanged(this)) canvas.resetStyleUi(this);
        layoutFields();
    }
    //endregion

    //region Layout
    private void caption(StyledLabel label, float size, int x, int y, int w, int h) { canvas.text(label, size, x, y, w, h); }
    private void layoutFields() {
        int w = designWidth();
        if (roguelikeOption) { layoutStacked(w); return; }
        int presetWidth = (w - 2 * PRESET_GAP) / 3;
        for (int i = 0; i < PRESET_SIZES.length; i++) {
            int x = i * (presetWidth + PRESET_GAP);
            canvas.place(presetButtons[i], x, 22, presetWidth, 46);
            canvas.text(presetSizeLabels[i], 12, x, 70, presetWidth, 18);
        }
        caption(sizeCaption, 12, 0, 0, w, 18);
        caption(seedCaption, 12, 0, 102, w, 18);
        int dailyX = w - DAILY_WIDTH;
        canvas.place(seedField, dailyX - FIELD_GAP - SEED_WIDTH, 124, SEED_WIDTH, 36);
        canvas.place(dailyButton, dailyX, 124, DAILY_WIDTH, 36);
        caption(seedHint, 12, 0, 164, w, 18);
        layoutAdvanced(w);
        layoutFooter(w);
    }
    private void layoutAdvanced(int w) {
        int leftWidth = (w - 20) * 250 / 480, rightX = leftWidth + 20, rightWidth = w - rightX;
        caption(finishCaption, 12, 0, 192, leftWidth, 14);
        caption(geometryCaption, 12, rightX, 192, rightWidth, 14);
        canvas.combo(finishBox, 0, 208, leftWidth, 26);
        canvas.combo(geometryBox, rightX, 208, rightWidth, 26);
        caption(algorithmCaption, 12, 0, 240, leftWidth, 14);
        canvas.combo(algorithmBox, 0, 256, leftWidth, 26);
        canvas.place(fogToggle, rightX, 256, rightWidth, 26);
        caption(dimensionsCaption, 12, 0, 288, 240, 14);
        caption(floorsCaption, 12, w - 70, 288, 70, 14);
        int fieldWidth = roguelikeOption ? 100 : 110, yX = roguelikeOption ? 124 : 140;
        canvas.place(horrorToggle, 0, 336, w, 26);
        canvas.place(xField, 0, 304, fieldWidth, 26);
        canvas.text(timesLabel, 18, fieldWidth, 304, 24, 26);
        canvas.place(yField, yX, 304, fieldWidth, 26);
        if (roguelikeOption) canvas.place(floorsToggle, yX + fieldWidth + 12, 304, w - 70 - 12 - (yX + fieldWidth + 12), 26);
        else canvas.place(floorsToggle, w - 240, 304, 160, 26);
        canvas.place(floorsField, w - 70, 304, 70, 26);
    }
    private void layoutStacked(int w) {
        int dailyX = w - DAILY_WIDTH;
        caption(seedCaption, 12, 0, 0, w, 18);
        canvas.place(seedField, dailyX - FIELD_GAP - SEED_WIDTH, 26, SEED_WIDTH, 36);
        canvas.place(dailyButton, dailyX, 26, DAILY_WIDTH, 36);
        caption(seedHint, 12, 0, 74, w, 18);
        int leftWidth = (w - 20) * 250 / 480, rightX = leftWidth + 20, rightWidth = w - rightX;
        caption(finishCaption, 12, 0, 116, leftWidth, 14);
        caption(geometryCaption, 12, rightX, 116, rightWidth, 14);
        canvas.combo(finishBox, 0, 134, leftWidth, 30);
        canvas.combo(geometryBox, rightX, 134, rightWidth, 30);
        caption(algorithmCaption, 12, 0, 180, leftWidth, 14);
        canvas.combo(algorithmBox, 0, 198, leftWidth, 30);
        canvas.place(fogToggle, rightX, 198, rightWidth, 30);
        caption(dimensionsCaption, 12, 0, 246, leftWidth, 14);
        caption(floorsCaption, 12, w - 70, 246, 70, 14);
        canvas.place(xField, 0, 264, 100, 30);
        canvas.text(timesLabel, 18, 100, 264, 24, 30);
        canvas.place(yField, 124, 264, 100, 30);
        int floorsToggleX = 124 + 100 + 12;
        canvas.place(floorsToggle, floorsToggleX, 264, w - 70 - 12 - floorsToggleX, 30);
        canvas.place(floorsField, w - 70, 264, 70, 30);
        // Roguelike and Horror share one row, each takes half of it. They are independent switches.
        int half = (w - 12) / 2;
        canvas.place(roguelikeToggle, 0, STACK_ROGUE_ROW, half, 30);
        canvas.place(horrorToggle, half + 12, STACK_ROGUE_ROW, w - half - 12, 30);
        caption(summary, 12, 0, STACK_SUMMARY_ROW, w, 16);
    }
    private void layoutFooter(int w) {
        int footer = footerTop();
        canvas.place(advancedToggle, 0, footer + 3, 240, 34);
        caption(summary, 12, w - 240, footer + 44, 240, 16);
    }
    public int footerTop() { return advancedOpen ? FOOTER_ADVANCED : FOOTER_BASIC; }
    public int designWidth() { return columnWidth; }
    public int designHeight() { return roguelikeOption ? STACKED_HEIGHT : footerTop() + FOOTER_HEIGHT; }
    public int startX() { return roguelikeOption ? 0 : designWidth() - 240; }
    public int startY() { return roguelikeOption ? STACK_CREATE_ROW : footerTop(); }
    public int startWidth() { return roguelikeOption ? designWidth() : 240; }
    public static int maxDesignHeight() { return STACKED_HEIGHT; }
    public void setRoguelikeOption(boolean shown) {
        boolean wasShown = roguelikeOption;
        if (shown && !wasShown) savedAdvanced = advancedOpen;
        roguelikeOption = shown;
        columnWidth = shown ? SECTION_WIDTH : WIDTH;
        roguelikeToggle.setVisible(shown);
        horrorToggle.setSelected(Settings.getBoolean(HORROR_KEY, false));
        for (StyledToggleButton preset : presetButtons) preset.setVisible(!shown);
        for (StyledLabel label : presetSizeLabels) label.setVisible(!shown);
        sizeCaption.setVisible(!shown);
        advancedToggle.setVisible(!shown);
        if (shown) advancedOpen = true;
        else if (wasShown) advancedOpen = savedAdvanced;
        applyAdvancedVisibility();
    }
    public boolean roguelikeChoice() { return roguelikeOption && roguelikeToggle.isSelected(); }
    private void applyAdvancedVisibility() {
        boolean floors = advancedOpen && floorsToggle.isSelected();
        advancedToggle.setSelected(advancedOpen);
        dimensionsCaption.setVisible(advancedOpen);
        xField.setVisible(advancedOpen);
        timesLabel.setVisible(advancedOpen);
        yField.setVisible(advancedOpen);
        floorsToggle.setVisible(advancedOpen);
        fogToggle.setVisible(advancedOpen);
        horrorToggle.setVisible(advancedOpen);
        finishCaption.setVisible(advancedOpen);
        finishBox.setVisible(advancedOpen);
        geometryCaption.setVisible(advancedOpen);
        geometryBox.setVisible(advancedOpen);
        algorithmCaption.setVisible(advancedOpen);
        algorithmBox.setVisible(advancedOpen);
        floorsCaption.setVisible(floors);
        floorsField.setVisible(floors);
        revalidate();
        repaint();
    }
    private void setAdvancedOpen(boolean open) {
        advancedOpen = open;
        applyAdvancedVisibility();
        Container host = getParent();
        if (host == null) return;
        host.doLayout();
        host.repaint();
    }
    //endregion

    //region Maze Settings
    public void saveMazeSettings() {
        if (loadingValues) return;
        clampFields();
        SaveData.Data saved = SaveData.load();
        try {
            SaveData.saveMazeSettings(parseOr(xField.getText(), saved.mazeWidth), parseOr(yField.getText(), saved.mazeHeight),
                    finishBox.getSelectedIndex(), geometry(), floorsToggle.isSelected(), parseOr(floorsField.getText(), saved.mazeFloors), fogToggle.isSelected());
        } catch (IOException exception) { exception.printStackTrace(); }
    }
    private void saveAlgorithm() {
        if (loadingValues) return;
        int index = algorithmBox.getSelectedIndex();
        if (index >= 0) Settings.set(MazeAlgorithms.SETTING, MazeAlgorithms.all().get(index).id());
    }
    private void loadSavedMazeSettings() {
        SaveData.Data saved = SaveData.load();
        loadingValues = true;
        try {
            setSavedText(xField, saved.mazeWidth);
            setSavedText(yField, saved.mazeHeight);
            floorsField.setText(String.valueOf(saved.mazeFloors));
            finishBox.setSelectedIndex(saved.mazeFinishMode);
            geometryBox.setSelectedIndex(geometryToCombo(saved.mazeGeometry));
            floorsToggle.setSelected(saved.mazeHasFloors);
            fogToggle.setSelected(saved.mazeFog);
            horrorToggle.setSelected(Settings.getBoolean(HORROR_KEY, false));
            algorithmBox.setSelectedIndex(MazeAlgorithms.all().indexOf(MazeAlgorithms.selected()));
            syncPresetSelection();
        } finally { loadingValues = false; }
        refreshGeometryChoices();
    }
    public int mazeWidth() { return Math.clamp(Integer.parseInt(xField.getText()), MIN_MAZE_SIZE, MAX_MAZE_SIZE_CTRL); }
    public int mazeHeight() { return Math.clamp(Integer.parseInt(yField.getText()), MIN_MAZE_SIZE, MAX_MAZE_SIZE_CTRL); }
    public int finishMode() { return finishBox.getSelectedIndex(); }
    public int geometry() { return comboToGeometry(geometryBox.getSelectedIndex()); }
    public boolean hasFloors() { return floorsToggle.isSelected(); }
    public boolean fogMinimap() { return fogToggle.isSelected(); }
    public MazeAlgorithm algorithm() { return MazeAlgorithms.all().get(Math.max(0, algorithmBox.getSelectedIndex())); }
    public String seed() {
        String seedText = SeedUtil.normalize(seedField.getText());
        return seedText.isEmpty() ? null : seedText;
    }
    public int layers() {
        if (!hasFloors()) return 3;
        String text = floorsField.getText().trim();
        return text.isEmpty() ? 3 : Math.clamp(Integer.parseInt(text), MIN_FLOORS, MAX_FLOORS);
    }
    public void applyMazeSpec(MazeSpec spec) {
        loadingValues = true;
        try {
            xField.setText(String.valueOf(spec.width()));
            yField.setText(String.valueOf(spec.height()));
            floorsField.setText(String.valueOf(spec.floors()));
            seedField.setText(spec.seed() == null ? "" : spec.seed());
            geometryBox.setSelectedIndex(geometryToCombo(spec.geometry()));
            floorsToggle.setSelected(spec.hasFloors());
            fogToggle.setSelected(spec.fog());
            horrorToggle.setSelected(spec.horror());
            refreshGeometryChoices();
            syncPresetSelection();
        } finally { loadingValues = false; }
        applyAdvancedVisibility();
        updateSummary();
    }
    public void setHyperbolicGeometryAllowed(boolean allowed) {
        engineAllowsWrong = allowed;
        refreshGeometryChoices();
    }
    private void refreshGeometryChoices() {
        boolean allowed = engineAllowsWrong && !horrorToggle.isSelected();
        if (allowed != wrongGeometryAllowed) {
            int current = geometry();
            wrongGeometryAllowed = allowed;
            loadingValues = true;
            try {
                if (allowed) geometryBox.addItem(GEOMETRY_WRONG, GEOMETRY_MODES[GEOMETRY_WRONG]);
                else geometryBox.removeItemAt(GEOMETRY_WRONG);
                int target = !allowed && current == GEOMETRY_WRONG ? GEOMETRY_EUCLIDEAN : current;
                geometryBox.setSelectedIndex(geometryToCombo(target));
            } finally { loadingValues = false; }
        }
        horrorToggle.setEnabled(geometry() != GEOMETRY_WRONG);
    }
    private void updateSummary() {
        Integer width = parseInteger(xField.getText()), height = parseInteger(yField.getText());
        if (width == null || height == null) summary.setText("");
        else {
            int shownWidth = Math.clamp(width, MIN_MAZE_SIZE, MAX_MAZE_SIZE_CTRL), shownHeight = Math.clamp(height, MIN_MAZE_SIZE, MAX_MAZE_SIZE_CTRL);
            if (hasFloors()) {
                int shownFloors = Math.clamp(parseOr(floorsField.getText(), MIN_FLOORS), MIN_FLOORS, MAX_FLOORS);
                summary.setLocalizationFormat("mm.size_summary_3d", () -> new Object[]{shownWidth, shownHeight, shownFloors});
            } else summary.setLocalizationFormat("mm.size_summary", () -> new Object[]{shownWidth, shownHeight});
        }
        layoutNow();
    }
    //endregion

    //region Geometry Choice
    private int comboToGeometry(int index) {
        if (wrongGeometryAllowed || index <= GEOMETRY_EUCLIDEAN) return index;
        return GEOMETRY_LOOPED;
    }
    private int geometryToCombo(int geometry) {
        if (wrongGeometryAllowed) return geometry;
        return geometry == GEOMETRY_LOOPED ? GEOMETRY_WRONG : GEOMETRY_EUCLIDEAN;
    }
    //endregion

    //region Presets
    private void choosePreset(int index) {
        String size = String.valueOf(PRESET_SIZES[index]);
        xField.setText(size);
        yField.setText(size);
        syncPresetSelection();
        saveMazeSettings();
    }
    private void syncPresetSelection() {
        int width = parseOr(xField.getText(), -1), height = parseOr(yField.getText(), -1);
        presetGroup.clearSelection();
        for (int i = 0; i < PRESET_SIZES.length; i++)
            if (width == PRESET_SIZES[i] && height == PRESET_SIZES[i]) presetButtons[i].setSelected(true);
    }
    //endregion

    //region Listeners
    private void attachListeners() {
        for (int i = 0; i < presetButtons.length; i++) {
            int index = i;
            presetButtons[i].addActionListener(_ -> choosePreset(index));
        }
        advancedToggle.addActionListener(_ -> setAdvancedOpen(advancedToggle.isSelected()));
        floorsToggle.addActionListener(_ -> {
            applyAdvancedVisibility();
            saveMazeSettings();
            updateSummary();
        });
        fogToggle.addActionListener(_ -> saveMazeSettings());
        horrorToggle.addActionListener(_ -> {
            Settings.set(HORROR_KEY, String.valueOf(horrorToggle.isSelected()));
            refreshGeometryChoices();
        });
        finishBox.addActionListener(_ -> saveMazeSettings());
        geometryBox.addActionListener(_ -> { saveMazeSettings(); refreshGeometryChoices(); });
        algorithmBox.addActionListener(_ -> saveAlgorithm());
        seedField.addActionListener(_ -> startAction.run());
        dailyButton.addActionListener(_ -> startDaily());
        FocusAdapter saveOnLeave = new FocusAdapter() {
            @Override public void focusLost(FocusEvent focusEvent) {
                saveMazeSettings();
                syncPresetSelection();
            }
        };
        xField.addFocusListener(saveOnLeave);
        yField.addFocusListener(saveOnLeave);
        floorsField.addFocusListener(saveOnLeave);
        watchFieldsForLimits();
    }
    private void startDaily() {
        seedField.setText(SeedUtil.daily(LocalDate.now()));
        startAction.run();
    }
    private void watchFieldsForLimits() {
        watchSizeField(xField);
        watchSizeField(yField);
        floorsField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent documentEvent) { changed(); }
            @Override public void removeUpdate(DocumentEvent documentEvent) { changed(); }
            @Override public void changedUpdate(DocumentEvent documentEvent) { changed(); }
            private void changed() { SwingUtilities.invokeLater(() -> { clampField(floorsField, false); updateSummary(); }); }
        });
    }
    private void watchSizeField(StyledTextField field) {
        field.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent mouseEvent) { if (SwingUtilities.isMiddleMouseButton(mouseEvent)) wheelHeld = true; }
            @Override public void mouseReleased(MouseEvent mouseEvent) { if (SwingUtilities.isMiddleMouseButton(mouseEvent)) wheelHeld = false; }
        });
        field.addFocusListener(new FocusAdapter() { @Override public void focusLost(FocusEvent focusEvent) { wheelHeld = false; } });
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent documentEvent) { changed(); }
            @Override public void removeUpdate(DocumentEvent documentEvent) { changed(); }
            @Override public void changedUpdate(DocumentEvent documentEvent) { changed(); }
            private void changed() {
                boolean programmatic = loadingValues;
                SwingUtilities.invokeLater(() -> {
                    if (!programmatic) clampSizeField(field, false);
                    updateSummary();
                });
            }
        });
    }
    //endregion

    //region Field Limits
    private void clampFields() {
        clampSizeField(xField, true);
        clampSizeField(yField, true);
        clampField(floorsField, true);
    }
    private void clampSizeField(StyledTextField field, boolean commit) {
        Integer value = parseInteger(field.getText());
        if (value == null) return;
        int max = commit || wheelHeld ? MAX_MAZE_SIZE_CTRL : MAX_MAZE_SIZE;
        int clamped = value > max ? max : commit && value < MIN_MAZE_SIZE ? MIN_MAZE_SIZE : value;
        if (clamped != value) field.setText(String.valueOf(clamped));
    }
    private void setSavedText(StyledTextField field, int value) { field.setText(String.valueOf(value)); }
    private static void clampField(StyledTextField field, boolean commit) {
        Integer value = parseInteger(field.getText());
        if (value == null) return;
        int clamped = value > MAX_FLOORS ? MAX_FLOORS : commit && value < MIN_FLOORS ? MIN_FLOORS : value;
        if (clamped != value) field.setText(String.valueOf(clamped));
    }
    private static Integer parseInteger(String text) {
        try { return Integer.parseInt(text.trim()); }
        catch (NumberFormatException exception) { return null; }
    }
    private static int parseOr(String text, int fallback) {
        try { return Integer.parseInt(text.trim()); }
        catch (NumberFormatException exception) { return fallback; }
    }
    //endregion

    //region Components
    private StyledLabel label(String keyOrText, Color color) {
        StyledLabel label = new StyledLabel(menu.style(), keyOrText, false, false);
        label.setForeground(color);
        return label;
    }
    //endregion
}