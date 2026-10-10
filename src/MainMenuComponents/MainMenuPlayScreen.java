package MainMenuComponents;

import Helpers.*;
import StyleUI.*;

import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;

public final class MainMenuPlayScreen extends MainMenuPanel {
    //region Variables
    private static final int[] PRESET_SIZES = {15, 25, 40};
    private static final String[] PRESET_KEYS = {"mm.size_small", "mm.size_medium", "mm.size_large"};
    private static final String[] FINISH_MODES = {"mm.opposite_corner", "mm.center", "mm.random_edge"};
    private static final String[] GEOMETRY_MODES = {"mm.euclidean_geometry", "mm.wrong_geometry", "mm.looped_geometry"};
    private static final int MIN_MAZE_SIZE = 5, MAX_MAZE_SIZE = 200, MIN_FLOORS = 2, MAX_FLOORS = 20;

    private final ButtonGroup presetGroup = new ButtonGroup();
    private final StyledToggleButton[] presetButtons = new StyledToggleButton[PRESET_SIZES.length];
    private final StyledLabel[] presetSizeLabels = new StyledLabel[PRESET_SIZES.length];

    private final StyledLabel heading, hint, sizeCaption, seedCaption, seedHint, summary;
    private final StyledLabel finishCaption, geometryCaption, dimensionsCaption, floorsCaption, timesLabel;
    private final StyledTextField xField, yField, seedField, floorsField;
    private final StyledComboBox finishBox, geometryBox;
    private final StyledToggle mode3DToggle, advancedToggle, fogToggle;
    private final StyledButton backButton, startButton;
    private boolean advancedOpen;
    //endregion

    //region Constructors
    public MainMenuPlayScreen(MainMenuHost menu) {
        super(menu);
        Style style = menu.style();

        heading = label("mm.new_game", MainMenuCanvas.ACCENT);
        hint = label("mm.play_hint", MainMenuCanvas.MUTED);
        sizeCaption = label("mm.size", MainMenuCanvas.TEXT);
        seedCaption = label("mm.custom_seed", MainMenuCanvas.TEXT);
        seedHint = label("mm.seed_hint", MainMenuCanvas.MUTED);
        finishCaption = label("mm.finish_position", MainMenuCanvas.TEXT);
        geometryCaption = label("mm.geometry", MainMenuCanvas.TEXT);
        dimensionsCaption = label("mm.maze_dimensions", MainMenuCanvas.TEXT);
        floorsCaption = label("mm.floors_count", MainMenuCanvas.TEXT);
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
        mode3DToggle = new StyledToggle(style, "mm.3d_mode");
        advancedToggle = new StyledToggle(style, "mm.advanced");
        fogToggle = new StyledToggle(style, "mm.fog_minimap");
        backButton = button("mm.back");
        startButton = button("mm.start");

        for (int i = 0; i < PRESET_SIZES.length; i++) {
            presetButtons[i] = new StyledToggleButton(style, PRESET_KEYS[i]);
            presetButtons[i].setFocusable(false);
            presetSizeLabels[i] = label(PRESET_SIZES[i] + " × " + PRESET_SIZES[i], MainMenuCanvas.MUTED);
            presetGroup.add(presetButtons[i]);
        }

        add(heading);
        add(hint);
        add(sizeCaption);
        for (int i = 0; i < PRESET_SIZES.length; i++) {
            add(presetButtons[i]);
            add(presetSizeLabels[i]);
        }
        add(seedCaption);
        add(seedField);
        add(seedHint);
        add(advancedToggle);
        add(finishCaption);
        add(geometryCaption);
        add(finishBox);
        add(geometryBox);
        add(dimensionsCaption);
        add(floorsCaption);
        add(xField);
        add(timesLabel);
        add(yField);
        add(mode3DToggle);
        add(floorsField);
        add(fogToggle);
        add(backButton);
        add(startButton);
        add(summary);
        setComponentZOrder(backButton, 0);

        loadSavedMazeSettings();
        attachListeners();
        applyAdvancedVisibility();
        updateSummary();
    }
    //endregion

    //region Public API
    public void refresh() { syncPresetSelection(); }
    public void saveMazeSettings() {
        clampFields(true);
        SaveData.Data saved = SaveData.load();
        try {
            SaveData.saveMazeSettings(parseOr(xField.getText(), saved.mazeWidth), parseOr(yField.getText(), saved.mazeHeight),
                    finishBox.getSelectedIndex(), geometryBox.getSelectedIndex(), mode3DToggle.isSelected(), parseOr(floorsField.getText(), saved.mazeFloors),
                    fogToggle.isSelected());
        } catch (IOException ex) { ex.printStackTrace(); }
    }
    public int mazeWidth() { return Math.clamp(Integer.parseInt(xField.getText()), MIN_MAZE_SIZE, MAX_MAZE_SIZE); }
    public int mazeHeight() { return Math.clamp(Integer.parseInt(yField.getText()), MIN_MAZE_SIZE, MAX_MAZE_SIZE); }
    public int finishMode() { return finishBox.getSelectedIndex(); }
    public int geometry() { return geometryBox.getSelectedIndex(); }
    public boolean is3D() { return mode3DToggle.isSelected(); }
    public String seed() {
        String seedText = SeedUtil.normalize(seedField.getText());
        return seedText.isEmpty() ? null : seedText;
    }
    public boolean fogMinimap() { return fogToggle.isSelected(); }
    public int layers() {
        if (!is3D()) return 3;
        String text = floorsField.getText().trim();
        return text.isEmpty() ? 3 : Math.clamp(Integer.parseInt(text), MIN_FLOORS, MAX_FLOORS);
    }
    //endregion

    //region Layout
    @Override protected void layoutDesign() {
        int height = advancedOpen ? 512 : 358;
        int top = (menu.screenHeight() - height) / 2;
        int footerY = top + (advancedOpen ? 452 : 296);
        setCard(200, top, 560, height);

        canvas.place(backButton, 230, top + 20, 96, 36);
        canvas.text(heading, 26, 200, top + 20, 560, 36);
        canvas.text(hint, 13, 230, top + 62, 500, 18);

        canvas.text(sizeCaption, 12, 230, top + 92, 500, 18);
        for (int i = 0; i < PRESET_SIZES.length; i++) {
            int x = 230 + i * 172;
            canvas.place(presetButtons[i], x, top + 114, 156, 46);
            canvas.text(presetSizeLabels[i], 12, x, top + 162, 156, 18);
        }

        canvas.text(seedCaption, 12, 230, top + 194, 500, 18);
        canvas.place(seedField, 390, top + 216, 180, 36);
        canvas.text(seedHint, 12, 230, top + 256, 500, 18);

        canvas.text(finishCaption, 12, 230, top + 284, 250, 18);
        canvas.text(geometryCaption, 12, 500, top + 284, 230, 18);
        canvas.combo(finishBox, 230, top + 304, 250, 34);
        canvas.combo(geometryBox, 500, top + 304, 230, 34);
        canvas.text(dimensionsCaption, 12, 230, top + 350, 240, 18);
        canvas.text(floorsCaption, 12, 660, top + 350, 70, 18);
        canvas.place(xField, 230, top + 372, 110, 34);
        canvas.text(timesLabel, 18, 340, top + 372, 30, 34);
        canvas.place(yField, 370, top + 372, 110, 34);
        canvas.place(mode3DToggle, 490, top + 376, 160, 28);
        canvas.place(floorsField, 660, top + 372, 70, 34);
        canvas.place(fogToggle, 230, top + 414, 500, 28);

        canvas.place(advancedToggle, 230, footerY + 3, 240, 34);
        canvas.place(startButton, 490, footerY, 240, 40);
        canvas.text(summary, 12, 490, footerY + 44, 240, 16);
    }
    //endregion

    //region Helpers
    private void attachListeners() {
        for (int i = 0; i < presetButtons.length; i++) {
            int index = i;
            presetButtons[i].addActionListener(e -> choosePreset(index));
        }
        advancedToggle.addActionListener(e -> setAdvancedOpen(advancedToggle.isSelected()));
        mode3DToggle.addActionListener(e -> {
            applyAdvancedVisibility();
            saveMazeSettings();
            updateSummary();
        });
        fogToggle.addActionListener(e -> saveMazeSettings());
        finishBox.addActionListener(e -> saveMazeSettings());
        geometryBox.addActionListener(e -> saveMazeSettings());
        seedField.addActionListener(e -> menu.startGame());
        backButton.addActionListener(e -> menu.showMain());
        startButton.addActionListener(e -> menu.startGame());
        FocusAdapter saveOnLeave = new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) {
                saveMazeSettings();
                syncPresetSelection();
            }
        };
        xField.addFocusListener(saveOnLeave);
        yField.addFocusListener(saveOnLeave);
        floorsField.addFocusListener(saveOnLeave);
        watchFieldsForLimits();
    }
    private void watchFieldsForLimits() {
        DocumentListener listener = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { changed(); }
            @Override public void removeUpdate(DocumentEvent e) { changed(); }
            @Override public void changedUpdate(DocumentEvent e) { changed(); }
            private void changed() { SwingUtilities.invokeLater(() -> { clampFields(false); updateSummary(); }); }
        };
        xField.getDocument().addDocumentListener(listener);
        yField.getDocument().addDocumentListener(listener);
        floorsField.getDocument().addDocumentListener(listener);
    }
    private void clampFields(boolean commit) {
        clampField(xField, MIN_MAZE_SIZE, MAX_MAZE_SIZE, commit);
        clampField(yField, MIN_MAZE_SIZE, MAX_MAZE_SIZE, commit);
        clampField(floorsField, MIN_FLOORS, MAX_FLOORS, commit);
    }
    private static void clampField(StyledTextField field, int min, int max, boolean commit) {
        Integer value = parseInteger(field.getText());
        if (value == null) return;
        int clamped = value > max ? max : commit && value < min ? min : value;
        if (clamped != value) field.setText(String.valueOf(clamped));
    }
    private void updateSummary() {
        Integer width = parseInteger(xField.getText()), height = parseInteger(yField.getText());
        if (width == null || height == null) { summary.setText(""); return; }
        int shownWidth = Math.clamp(width, MIN_MAZE_SIZE, MAX_MAZE_SIZE), shownHeight = Math.clamp(height, MIN_MAZE_SIZE, MAX_MAZE_SIZE);
        if (is3D()) {
            int shownFloors = Math.clamp(parseOr(floorsField.getText(), MIN_FLOORS), MIN_FLOORS, MAX_FLOORS);
            summary.setLocalizationFormat("mm.size_summary_3d", () -> new Object[]{shownWidth, shownHeight, shownFloors});
        } else summary.setLocalizationFormat("mm.size_summary", () -> new Object[]{shownWidth, shownHeight});
    }
    private void loadSavedMazeSettings() {
        SaveData.Data saved = SaveData.load();
        xField.setText(String.valueOf(saved.mazeWidth));
        yField.setText(String.valueOf(saved.mazeHeight));
        floorsField.setText(String.valueOf(saved.mazeFloors));
        finishBox.setSelectedIndex(saved.mazeFinishMode);
        geometryBox.setSelectedIndex(saved.mazeGeometry);
        mode3DToggle.setSelected(saved.maze3D);
        fogToggle.setSelected(saved.mazeFog);
        syncPresetSelection();
    }
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
    private void setAdvancedOpen(boolean open) {
        advancedOpen = open;
        advancedToggle.setSelected(open);
        applyAdvancedVisibility();
        revalidate();
        repaint();
    }
    private void applyAdvancedVisibility() {
        boolean floors = advancedOpen && mode3DToggle.isSelected();
        finishCaption.setVisible(advancedOpen);
        geometryCaption.setVisible(advancedOpen);
        finishBox.setVisible(advancedOpen);
        geometryBox.setVisible(advancedOpen);
        dimensionsCaption.setVisible(advancedOpen);
        xField.setVisible(advancedOpen);
        timesLabel.setVisible(advancedOpen);
        yField.setVisible(advancedOpen);
        mode3DToggle.setVisible(advancedOpen);
        floorsCaption.setVisible(floors);
        floorsField.setVisible(floors);
        fogToggle.setVisible(advancedOpen);
    }
    private static Integer parseInteger(String text) {
        try { return Integer.parseInt(text.trim()); }
        catch (NumberFormatException ex) { return null; }
    }
    private static int parseOr(String text, int fallback) {
        try { return Integer.parseInt(text.trim()); }
        catch (NumberFormatException ex) { return fallback; }
    }
    //endregion
}