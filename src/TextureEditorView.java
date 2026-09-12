import StyleUI.*;
import Helpers.AppPaths;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class TextureEditorView extends JPanel {
    //region Variables
    private final Style currentStyle = loadTheme();

    public enum TextureMode {WALLS, FLOOR, CEILING, FINISH}
    private enum Tool {SELECT, BRUSH, FILL, ERASER}

    public static final int MIN_TEXTURE_SIZE = 1, MAX_TEXTURE_SIZE = 512;
    public static final int MAX_FINISH_HEIGHT = 256;
    private static final int ABSOLUTE_MAX_TEXTURE_DIMENSION = 4096;
    private static final int MIN_TOOL_SIZE = 1, MAX_TOOL_SIZE = 10;

    private static final int ORIGINAL_WALL_COLOR = 0xECD485;
    private static final int ORIGINAL_FLOOR_COLOR = 0xD3AF63;
    private static final int ORIGINAL_CEILING_COLOR = 0x816E1E;
    private static final int ORIGINAL_FINISH_COLOR = 0x33FF66;
    private static final int EMPTY_COLOR = -1;
    private static final int SAVED_EMPTY_COLOR = 0xFFFFFF;
    private static final int TEXTURE_FILE_MAGIC = 0x52435458; //RCTX Extension Token
    private static final int TEXTURE_FORMAT_VERSION = 1;

    private final TextureCanvas textureCanvas = new TextureCanvas();
    private final JPanel colorPreview = new JPanel();

    StyledButton wallsButton = new StyledButton(currentStyle, "te.walls");
    StyledButton floorButton = new StyledButton(currentStyle, "te.floor");
    StyledButton ceilingButton = new StyledButton(currentStyle, "te.ceiling");
    StyledButton finishButton = new StyledButton(currentStyle, "te.finish");
    StyledButton exitButton = new StyledButton(currentStyle, "exit");
    StyledButton applyButton = new StyledButton(currentStyle,"te.apply_to_cell");
    StyledButton fillButton = new StyledButton(currentStyle, "te.fill_texture");
    StyledButton resetButton = new StyledButton(currentStyle, "reset");
    StyledButton saveButton = new StyledButton(currentStyle, "te.save");
    StyledButton resetAllButton = new StyledButton(currentStyle, "reset");
    StyledButton importPngButton = new StyledButton(currentStyle, "te.import_png");

    private final StyledLabel widthLabel = new StyledLabel(currentStyle, "");
    private final StyledLabel heightLabel = new StyledLabel(currentStyle, "");
    private final StyledLabel modeLabel = new StyledLabel(currentStyle, "");
    private final StyledLabel selectedCellLabel = new StyledLabel(currentStyle, "selected_none", false, false);
    private final StyledLabel brushSizeLabel = new StyledLabel(currentStyle, "");
    private final StyledLabel eraserSizeLabel = new StyledLabel(currentStyle, "");
    private final StyledLabel textureLabel = new StyledLabel(currentStyle, "te.texture", false, false);
    private final StyledLabel toolsTitle = new StyledLabel(currentStyle, "tool", false, false);
    private final StyledLabel colorTitle = new StyledLabel(currentStyle, "te.color", false, false);
    private final StyledLabel hint = new StyledLabel(currentStyle, "te.color_square", false, false);
    private final StyledLabel info = new StyledLabel(currentStyle, "te.pick_a_tool", false, false);

    private final StyledSlider widthSlider = new StyledSlider(currentStyle, MIN_TEXTURE_SIZE, MAX_TEXTURE_SIZE, 8);
    private final StyledSlider heightSlider = new StyledSlider(currentStyle, MIN_TEXTURE_SIZE, MAX_TEXTURE_SIZE, 8);
    private final StyledSlider redSlider = new StyledSlider(currentStyle, 0, 255, 255);
    private final StyledSlider greenSlider = new StyledSlider(currentStyle, 0, 255, 255);
    private final StyledSlider blueSlider = new StyledSlider(currentStyle, 0, 255, 255);
    private final StyledSlider brushSizeSlider = new StyledSlider(currentStyle, MIN_TOOL_SIZE, MAX_TOOL_SIZE, 1);
    private final StyledSlider eraserSizeSlider = new StyledSlider(currentStyle, MIN_TOOL_SIZE, MAX_TOOL_SIZE, 1);

    private final StyledTextField hexField = new StyledTextField(currentStyle, "FFFFFF");

    private final StyledToggleButton selectToolButton = new StyledToggleButton(currentStyle, "select");
    private final StyledToggleButton brushToolButton = new StyledToggleButton(currentStyle, "brush");
    private final StyledToggleButton fillToolButton = new StyledToggleButton(currentStyle, "te.fill");
    private final StyledToggleButton eraserToolButton = new StyledToggleButton(currentStyle, "eraser");

    private Tool currentTool = Tool.SELECT;
    private TextureMode mode = TextureMode.WALLS;

    private int textureWidth = 8, textureHeight = 8;
    private int wallWidth = 8, wallHeight = 8;
    private int floorWidth = 8, floorHeight = 8;
    private int ceilingWidth = 8, ceilingHeight = 8;
    private int finishWidth = 8, finishHeight = 8;

    private int[][] wallTexture = createEmptyTexture(wallWidth, wallHeight);
    private int[][] floorTexture = createEmptyTexture(floorWidth, floorHeight);
    private int[][] ceilingTexture = createEmptyTexture(ceilingWidth, ceilingHeight);
    private int[][] finishTexture = createEmptyTexture(finishWidth, finishHeight);

    private int selectedX = -1, selectedY = -1;
    private boolean updatingSizeControls = false;
    private static final int MAX_HISTORY = 10;
    private final Deque<EditorState> undoHistory = new ArrayDeque<>();
    private final Deque<EditorState> redoHistory = new ArrayDeque<>();
    private boolean historyRestoring = false;
    private boolean mouseHistoryStarted = false;
    //endregion

    public TextureEditorView() throws IOException {
        setLayout(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setBackground(RCJMS.MY_FAV_GRAY);

        buildTopMenu();
        buildRightPanel();
        buildCenter();
        buildBottomMenu();
        setupUndoRedo();

        resetTextures();
        updateModeLabel();
        updateSizeLabels();
        updateToolSizeLabels();
        loadTexturesFromTmp();
        textureCanvas.rebuildGrid();

        undoHistory.clear();
        redoHistory.clear();
    }
    private static int[][] createEmptyTexture(int width, int height) {
        int[][] texture = new int[height][width];
        for (int[] row : texture) Arrays.fill(row, EMPTY_COLOR);
        return texture;
    }
    private int[] sizeOf(int[][] texture) {return sizeOf(texture, MAX_TEXTURE_SIZE, MAX_TEXTURE_SIZE);}
    private int[] sizeOf(int[][] texture, int maxWidth, int maxHeight) {
        if (isValidTexture(texture)) return new int[]{ Math.clamp(texture[0].length, MIN_TEXTURE_SIZE, maxWidth), Math.clamp(texture.length, MIN_TEXTURE_SIZE, maxHeight)};
        return new int[]{8, 8};
    }
    private static int maxWidthForMode(TextureMode m) {return MAX_TEXTURE_SIZE;}
    private static int maxHeightForMode(TextureMode m) {return m == TextureMode.FINISH ? MAX_FINISH_HEIGHT : MAX_TEXTURE_SIZE;}

    //region Builders
    private void buildTopMenu() {
        JPanel top = new JPanel(new BorderLayout(10, 5));
        top.setOpaque(false);

        JPanel stack = new JPanel();
        stack.setOpaque(false);
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));

        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        modePanel.setOpaque(false);
        modePanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        modeLabel.setForeground(Color.WHITE);
        modeLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));

        wallsButton.addActionListener(e -> setMode(TextureMode.WALLS));
        floorButton.addActionListener(e -> setMode(TextureMode.FLOOR));
        ceilingButton.addActionListener(e -> setMode(TextureMode.CEILING));
        finishButton.addActionListener(e -> setMode(TextureMode.FINISH));

        textureLabel.setForeground(Color.WHITE);
        modePanel.add(textureLabel);
        wallsButton.setPreferredSize(new Dimension(150, 50));
        modePanel.add(wallsButton);
        floorButton.setPreferredSize(new Dimension(150, 50));
        modePanel.add(floorButton);
        ceilingButton.setPreferredSize(new Dimension(150, 50));
        modePanel.add(ceilingButton);
        finishButton.setPreferredSize(new Dimension(150, 50));
        modePanel.add(finishButton);
        modePanel.add(Box.createHorizontalStrut(15));
        modeLabel.setPreferredSize(new Dimension(150, 50));
        modePanel.add(modeLabel);

        widthLabel.setForeground(Color.WHITE);
        heightLabel.setForeground(Color.WHITE);
        widthLabel.setPreferredSize(50, 22);
        heightLabel.setPreferredSize(50, 22);

        configureSizeSlider(widthSlider);
        configureSizeSlider(heightSlider);

        JPanel widthRow = buildLabeledSliderRow("width", widthSlider, widthLabel);
        JPanel heightRow = buildLabeledSliderRow("height", heightSlider, heightLabel);

        widthSlider.addChangeListener(e -> {
            if (updatingSizeControls) return;
            setTextureSize(widthSlider.getValue(), textureHeight);});

        heightSlider.addChangeListener(e -> {
            if (updatingSizeControls) return;
            setTextureSize(textureWidth, heightSlider.getValue());});

        stack.add(modePanel);
        stack.add(Box.createVerticalStrut(4));
        stack.add(widthRow);
        stack.add(Box.createVerticalStrut(2));
        stack.add(heightRow);

        exitButton.setFocusable(false);

        exitButton.addActionListener(e -> {RCJMS.instance.ChangeView(RCJMS.instance.mainMenuView = new MainMenuView(), "main_menu");});

        JPanel exitPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        exitPanel.setOpaque(false);
        exitPanel.add(exitButton);

        top.add(stack, BorderLayout.CENTER);
        top.add(exitPanel, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);
    }

    private JPanel buildLabeledSliderRow(String labelText, StyledSlider slider, StyledLabel valueLabel) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        StyledLabel nameLabel = new StyledLabel(currentStyle, labelText, false, false);
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setPreferredSize(60, 22);

        row.add(nameLabel, BorderLayout.WEST);
        row.add(slider, BorderLayout.CENTER);
        row.add(valueLabel, BorderLayout.EAST);
        return row;
    }

    private void configureSizeSlider(StyledSlider slider) {
        slider.setMajorTickSpacing(64);
        slider.setMinorTickSpacing(8);
        slider.setForeground(Color.WHITE);
        slider.setPaintTicks(true);
        slider.setPaintLabels(true);
        slider.setOpaque(false);
        slider.setPreferredSize(new Dimension(140, 32)); // фикс — не даём дефолтные 180x44 распирать строку
    }

    private void configureToolSizeSlider(StyledSlider slider) {
        slider.setMajorTickSpacing(MAX_TOOL_SIZE - MIN_TOOL_SIZE);
        slider.setMinorTickSpacing(1);
        slider.setPaintTicks(false);
        slider.setPaintLabels(false);
        slider.setOpaque(false);
        slider.setPreferredSize(new Dimension(130, 22)); // фикс п.4: строка рассчитана на 26px высоты
    }

    private void buildCenter() {
        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);

        textureCanvas.setPreferredSize(new Dimension(650, 650));
        textureCanvas.setMinimumSize(new Dimension(120, 120));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1;
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.BOTH;
        center.add(textureCanvas, gbc);
        add(center, BorderLayout.CENTER);
    }

    private void buildRightPanel() {
        JPanel right = new JPanel();
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.setBorder(new EmptyBorder(20, 10, 10, 10));
        right.setBackground(new Color(25, 25, 25));

        right.setPreferredSize(new Dimension(220, 700));
        right.setMinimumSize(new Dimension(220, 0));
        right.setMaximumSize(new Dimension(220, Integer.MAX_VALUE));

        toolsTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        toolsTitle.setForeground(Color.WHITE);
        toolsTitle.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));

        JPanel toolButtons = new JPanel(new GridLayout(2, 2, 4, 4));
        toolButtons.setOpaque(false);
        toolButtons.setMaximumSize(new Dimension(200, 64));
        toolButtons.setAlignmentX(Component.CENTER_ALIGNMENT);

        ButtonGroup toolGroup = new ButtonGroup();
        toolGroup.add(selectToolButton);
        toolGroup.add(brushToolButton);
        toolGroup.add(fillToolButton);
        toolGroup.add(eraserToolButton);
        selectToolButton.setSelected(true);

        selectToolButton.addActionListener(e -> currentTool = Tool.SELECT);
        brushToolButton.addActionListener(e -> currentTool = Tool.BRUSH);
        fillToolButton.addActionListener(e -> currentTool = Tool.FILL);
        eraserToolButton.addActionListener(e -> currentTool = Tool.ERASER);

        toolButtons.add(selectToolButton);
        toolButtons.add(brushToolButton);
        toolButtons.add(fillToolButton);
        toolButtons.add(eraserToolButton);

        JPanel brushSizeRow = new JPanel(new BorderLayout(6, 0));
        brushSizeRow.setOpaque(false);
        brushSizeRow.setMaximumSize(new Dimension(210, 26));
        brushSizeRow.setAlignmentX(Component.CENTER_ALIGNMENT);
        brushSizeLabel.setForeground(Color.WHITE);
        brushSizeLabel.setPreferredSize(new Dimension(60, 16));
        configureToolSizeSlider(brushSizeSlider);
        brushSizeRow.add(brushSizeLabel, BorderLayout.WEST);
        brushSizeRow.add(brushSizeSlider, BorderLayout.CENTER);

        JPanel eraserSizeRow = new JPanel(new BorderLayout(6, 0));
        eraserSizeRow.setOpaque(false);
        eraserSizeRow.setMaximumSize(new Dimension(210, 26));
        eraserSizeRow.setAlignmentX(Component.CENTER_ALIGNMENT);
        eraserSizeLabel.setForeground(Color.WHITE);
        eraserSizeLabel.setPreferredSize(new Dimension(60, 16));
        configureToolSizeSlider(eraserSizeSlider);
        eraserSizeRow.add(eraserSizeLabel, BorderLayout.WEST);
        eraserSizeRow.add(eraserSizeSlider, BorderLayout.CENTER);

        brushSizeSlider.addChangeListener(e -> updateToolSizeLabels());
        eraserSizeSlider.addChangeListener(e -> updateToolSizeLabels());

        JSeparator separator = new JSeparator();
        separator.setMaximumSize(new Dimension(210, 2));
        separator.setAlignmentX(Component.CENTER_ALIGNMENT); // фикс п.5

        colorTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        colorTitle.setForeground(Color.WHITE);
        colorTitle.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));

        colorPreview.setPreferredSize(new Dimension(150, 150));
        colorPreview.setMinimumSize(new Dimension(150, 150));
        colorPreview.setMaximumSize(new Dimension(150, 150));
        colorPreview.setBackground(Color.WHITE);
        colorPreview.setBorder(BorderFactory.createLineBorder(Color.BLACK, 2));
        colorPreview.setAlignmentX(Component.CENTER_ALIGNMENT);
        colorPreview.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        colorPreview.addMouseListener(new MouseAdapter() {@Override public void mouseClicked(MouseEvent e) {openColorPicker();}});

        hint.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));//Different Font Makes Me Proud Of Myself
        hint.setForeground(Color.LIGHT_GRAY);
        hint.setAlignmentX(Component.CENTER_ALIGNMENT);

        selectedCellLabel.setForeground(Color.WHITE);
        selectedCellLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        applyButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        applyButton.addActionListener(e -> applyColorToSelectedCell());

        fillButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        fillButton.addActionListener(e -> fillCurrentTexture());

        resetButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        resetButton.addActionListener(e -> resetTextures());

        right.add(toolsTitle);
        right.add(Box.createVerticalStrut(10));
        right.add(toolButtons);
        right.add(Box.createVerticalStrut(10));
        right.add(brushSizeRow);
        right.add(Box.createVerticalStrut(6));
        right.add(eraserSizeRow);
        right.add(Box.createVerticalStrut(16));
        right.add(separator);
        right.add(Box.createVerticalStrut(16));

        right.add(colorTitle);
        right.add(Box.createVerticalStrut(12));
        right.add(colorPreview);
        right.add(Box.createVerticalStrut(8));
        right.add(hint);
        right.add(Box.createVerticalStrut(20));
        right.add(selectedCellLabel);
        right.add(Box.createVerticalStrut(8));
        right.add(applyButton);
        right.add(Box.createVerticalStrut(6));
        right.add(fillButton);
        right.add(Box.createVerticalStrut(25));
        right.add(resetButton);

        right.add(Box.createVerticalGlue());

        StyledScrollPane rightScroll = new StyledScrollPane(right, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER, currentStyle);

        rightScroll.setBorder(BorderFactory.createEmptyBorder());
        rightScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        rightScroll.getVerticalScrollBar().setUnitIncrement(16);
        rightScroll.setPreferredSize(new Dimension(240, 0));
        rightScroll.setMinimumSize(new Dimension(160, 80));

        add(rightScroll, BorderLayout.EAST);
    }

    private void buildBottomMenu() {
        JPanel bottom = new JPanel();
        bottom.setOpaque(false);
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));

        info.setForeground(Color.LIGHT_GRAY);
        info.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        buttons.setOpaque(false);
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);

        saveButton.addActionListener(e -> {
            try {
                Path folder = saveTexturesToTmp();
                JOptionPane.showMessageDialog(this, "te.saved_to\n" + folder.toAbsolutePath(), "saved", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "te.could_not_save\n" + ex.getMessage(), "save_error", JOptionPane.ERROR_MESSAGE);}
        });
        resetAllButton.addActionListener(e -> resetTextures());
        importPngButton.addActionListener(e -> importPng());

        buttons.add(resetAllButton);
        buttons.add(importPngButton);
        buttons.add(saveButton);

        bottom.add(info);
        bottom.add(Box.createVerticalStrut(4));
        bottom.add(buttons);
        add(bottom, BorderLayout.SOUTH);
    }
    //endregion

    //region Mode Management
    private void setMode(TextureMode newMode) {
        mode = newMode;
        selectedX = -1;
        selectedY = -1;

        syncSizeControlsToMode();

        updateModeLabel();
        updateSelectedLabel();
        textureCanvas.rebuildGrid();
    }
    private void syncSizeControlsToMode() {
        int[] size = getSizeForMode(mode);
        textureWidth = size[0];
        textureHeight = size[1];

        updatingSizeControls = true;
        widthSlider.setMaximum(maxWidthForMode(mode));
        heightSlider.setMaximum(maxHeightForMode(mode));
        widthSlider.setValue(textureWidth);
        heightSlider.setValue(textureHeight);
        updatingSizeControls = false;

        updateSizeLabels();
    }
    private int[] getSizeForMode(TextureMode m) {
        return switch (m) {
            case WALLS -> new int[]{wallWidth, wallHeight};
            case FLOOR -> new int[]{floorWidth, floorHeight};
            case CEILING -> new int[]{ceilingWidth, ceilingHeight};
            case FINISH -> new int[]{finishWidth, finishHeight};
        };
    }
    private void setSizeForMode(TextureMode m, int w, int h) {
        switch (m) {
            case WALLS -> {wallWidth = w;wallHeight = h;}
            case FLOOR -> {floorWidth = w;floorHeight = h;}
            case CEILING -> {ceilingWidth = w;ceilingHeight = h;}
            case FINISH -> {finishWidth = w;finishHeight = h;}
        }
    }
    private void updateModeLabel() {
        String text = switch (mode) {
            case WALLS -> "te.walls";
            case FLOOR -> "te.floor";
            case CEILING -> "te.ceiling";
            case FINISH -> "te.finish";
        };
        modeLabel.setText("te.editing" + text);
    }
    //endregion

    private void setTextureSize(int newWidth, int newHeight) {
        newWidth = Math.clamp(newWidth, MIN_TEXTURE_SIZE, maxWidthForMode(mode));
        newHeight = Math.clamp(newHeight, MIN_TEXTURE_SIZE, maxHeightForMode(mode));

        if (newWidth == textureWidth && newHeight == textureHeight) {
            updateSizeLabels();
            return;
        }
        saveHistoryState();

        int[][] resized = resizePreservingData(getCurrentTexture(), newWidth, newHeight);
        setCurrentTexture(resized);

        textureWidth = newWidth;
        textureHeight = newHeight;
        setSizeForMode(mode, newWidth, newHeight);

        selectedX = selectedY = -1;

        updatingSizeControls = true;
        if (widthSlider.getValue() != newWidth) widthSlider.setValue(newWidth);
        if (heightSlider.getValue() != newHeight) heightSlider.setValue(newHeight);
        updatingSizeControls = false;

        updateSizeLabels();
        updateSelectedLabel();
        textureCanvas.rebuildGrid();
    }
    private void updateSizeLabels() {
        widthLabel.setText("te.w" + textureWidth);
        heightLabel.setText("te.h" + textureHeight);
    }
    private void updateToolSizeLabels() {
        brushSizeLabel.setText("brush_size" + brushSizeSlider.getValue());
        eraserSizeLabel.setText("eraser_size" + eraserSizeSlider.getValue());
    }
    private int[][] resizePreservingData(int[][] source, int newWidth, int newHeight) {
        int[][] result = createEmptyTexture(newWidth, newHeight);
        if (source == null) return result;
        int copyHeight = Math.min(source.length, newHeight);
        for (int y = 0; y < copyHeight; y++) {
            if (source[y] == null) continue;

            int copyWidth = Math.min(source[y].length, newWidth);
            System.arraycopy(source[y], 0, result[y], 0, copyWidth);
        }
        return result;
    }

    //region Coloring
    private void selectCell(int x, int y) {
        if (x < 0 || y < 0 || x >= textureWidth || y >= textureHeight) return;
        selectedX = x;
        selectedY = y;
        int color = getCurrentTexture()[y][x];
        setColorControls(color == EMPTY_COLOR ? SAVED_EMPTY_COLOR : color);
        updateSelectedLabel();
        textureCanvas.repaint();
    }
    private void selectFirstCell() {if (textureWidth > 0 && textureHeight > 0) selectCell(0, 0);}
    private void updateSelectedLabel() {
        if (selectedX < 0 || selectedY < 0) selectedCellLabel.setText("selected_none");
        else selectedCellLabel.setText("selected" + selectedX + ", " + selectedY);
    }
    private int[][] getCurrentTexture() {
        return switch (mode) {
            case WALLS -> wallTexture;
            case FLOOR -> floorTexture;
            case CEILING -> ceilingTexture;
            case FINISH -> finishTexture;
        };
    }
    private void setCurrentTexture(int[][] texture) {
        switch (mode) {
            case WALLS -> wallTexture = texture;
            case FLOOR -> floorTexture = texture;
            case CEILING -> ceilingTexture = texture;
            case FINISH -> finishTexture = texture;
        }
    }
    private void applyColorToSelectedCell() {
        if (selectedX < 0 || selectedY < 0) return;
        saveHistoryState();
        int color = getColorFromControls();
        getCurrentTexture()[selectedY][selectedX] = color;
        textureCanvas.repaint();
    }
    private void fillCurrentTexture() {
        int color = getColorFromControls();
        int[][] texture = getCurrentTexture();
        saveHistoryState();
        for (int[] ints : texture) Arrays.fill(ints, color);
        textureCanvas.repaint();
    }
    private void paintCells(int centerX, int centerY, int size, int color) {
        int[][] texture = getCurrentTexture();
        double radius = (size - 1) / 2.0;

        int minX = Math.max(0, (int) Math.floor(centerX - radius));
        int maxX = Math.min(textureWidth - 1, (int) Math.ceil(centerX + radius));
        int minY = Math.max(0, (int) Math.floor(centerY - radius));
        int maxY = Math.min(textureHeight - 1, (int) Math.ceil(centerY + radius));

        double radiusSq = radius * radius + 0.0001;

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                double dx = x - centerX;
                double dy = y - centerY;
                if (dx * dx + dy * dy <= radiusSq) texture[y][x] = color;
            }
        }
        textureCanvas.repaint();
    }
    private void floodFill(int startX, int startY, int newColor) {
        if (startX < 0 || startY < 0 || startX >= textureWidth || startY >= textureHeight) return;

        int[][] texture = getCurrentTexture();
        int targetColor = texture[startY][startX];

        if (targetColor == newColor) return;

        Deque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{startX, startY});

        while (!stack.isEmpty()) {
            int[] p = stack.pop();
            int x = p[0];
            int y = p[1];

            if (x < 0 || y < 0 || x >= textureWidth || y >= textureHeight) continue;
            if (texture[y][x] != targetColor) continue;
            texture[y][x] = newColor;

            stack.push(new int[]{x + 1, y});
            stack.push(new int[]{x - 1, y});
            stack.push(new int[]{x, y + 1});
            stack.push(new int[]{x, y - 1});
        }
        textureCanvas.repaint();
    }
    //endregion

    //region Colores
    private void openColorPicker() {
        final JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), "cp.choose_color", Dialog.ModalityType.APPLICATION_MODAL);

        JPanel panel = new JPanel();
        panel.setBackground(RCJMS.MY_FAV_GRAY);
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JPanel preview = new JPanel();
        preview.setPreferredSize(new Dimension(220, 80));
        preview.setMinimumSize(new Dimension(220, 80));
        preview.setMaximumSize(new Dimension(220, 80));
        preview.setBackground(colorPreview.getBackground());
        preview.setBorder(BorderFactory.createLineBorder(Color.WHITE, 2));
        preview.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel sliders = new JPanel(new GridLayout(3, 2, 8, 8));
        sliders.setBackground(RCJMS.MY_FAV_GRAY);
        sliders.setMaximumSize(new Dimension(350, 100));

        StyledLabel redLabel = new StyledLabel(currentStyle, "cp.red");
        redLabel.setForeground(Color.WHITE);
        StyledLabel greenLabel = new StyledLabel(currentStyle, "cp.green");
        greenLabel.setForeground(Color.WHITE);
        StyledLabel blueLabel = new StyledLabel(currentStyle, "cp.blue");
        blueLabel.setForeground(Color.WHITE);

        StyledSlider r = new StyledSlider(currentStyle, 0, 255, redSlider.getValue());
        r.setBackground(RCJMS.MY_FAV_GRAY);
        r.setOpaque(false);
        StyledSlider g = new StyledSlider(currentStyle, 0, 255, greenSlider.getValue());
        g.setBackground(RCJMS.MY_FAV_GRAY);
        g.setOpaque(false);
        StyledSlider b = new StyledSlider(currentStyle, 0, 255, blueSlider.getValue());
        b.setBackground(RCJMS.MY_FAV_GRAY);
        b.setOpaque(false);

        StyledLabel rValue = new StyledLabel(currentStyle, String.valueOf(r.getValue()), false, false);
        StyledLabel gValue = new StyledLabel(currentStyle, String.valueOf(g.getValue()), false, false);
        StyledLabel bValue = new StyledLabel(currentStyle, String.valueOf(b.getValue()), false, false);

        sliders.add(redLabel);
        sliders.add(createSliderRow(r, rValue));
        sliders.add(greenLabel);
        sliders.add(createSliderRow(g, gValue));
        sliders.add(blueLabel);
        sliders.add(createSliderRow(b, bValue));

        StyledLabel hexLabel = new StyledLabel(currentStyle, "cp.color_code", false, false);
        hexLabel.setForeground(Color.WHITE);
        StyledTextField pickerHex = new StyledTextField(currentStyle, hexField.getText());
        pickerHex.setBackground(RCJMS.MY_FAV_GRAY);
        pickerHex.setForeground(Color.WHITE);
        pickerHex.setMaximumSize(new Dimension(350, 28));

        Runnable updatePreview = () -> {
            int color = (r.getValue() << 16) | (g.getValue() << 8) | b.getValue();
            preview.setBackground(new Color(color));
            rValue.setText(String.valueOf(r.getValue()));
            gValue.setText(String.valueOf(g.getValue()));
            bValue.setText(String.valueOf(b.getValue()));
            pickerHex.setText(String.format("%06X", color));
        };

        r.addChangeListener(e -> updatePreview.run());
        g.addChangeListener(e -> updatePreview.run());
        b.addChangeListener(e -> updatePreview.run());

        StyledButton apply = new StyledButton(currentStyle, "cp.apply");
        StyledButton cancel = new StyledButton(currentStyle, "cp.cancel");

        pickerHex.addActionListener(e -> {
            Integer color = parseColor(pickerHex.getText());
            if (color != null) {
                r.setValue((color >> 16) & 0xFF);
                g.setValue((color >> 8) & 0xFF);
                b.setValue(color & 0xFF);
                updatePreview.run();
            }});

        apply.addActionListener(e -> {
            int color = (r.getValue() << 16) | (g.getValue() << 8) | b.getValue();
            redSlider.setValue(r.getValue());
            greenSlider.setValue(g.getValue());
            blueSlider.setValue(b.getValue());
            hexField.setText(String.format("%06X", color));
            colorPreview.setBackground(new Color(color));
            applyColorToSelectedCell();
            dialog.dispose();});

        cancel.addActionListener(e -> dialog.dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.setBackground(RCJMS.MY_FAV_GRAY);
        buttons.add(cancel);
        buttons.add(apply);

        panel.add(preview);
        panel.add(Box.createVerticalStrut(15));
        panel.add(sliders);
        panel.add(Box.createVerticalStrut(12));
        panel.add(hexLabel);
        panel.add(pickerHex);
        panel.add(Box.createVerticalStrut(10));
        panel.add(buttons);

        dialog.setContentPane(panel);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        updatePreview.run();
        dialog.setVisible(true);
    }
    private JPanel createSliderRow(StyledSlider slider, StyledLabel valueLabel) {
        JPanel panel = new JPanel(new BorderLayout(5, 0));
        panel.setOpaque(false);
        valueLabel.setForeground(Color.WHITE);
        panel.add(slider, BorderLayout.CENTER);
        panel.add(valueLabel, BorderLayout.EAST);
        return panel;
    }
    private void setColorControls(int color) {
        color &= 0xFFFFFF;
        redSlider.setValue((color >> 16) & 0xFF);
        greenSlider.setValue((color >> 8) & 0xFF);
        blueSlider.setValue(color & 0xFF);
        hexField.setText(String.format("%06X", color));
        colorPreview.setBackground(new Color(color));
    }
    private int getColorFromControls() {return ((redSlider.getValue() & 0xFF) << 16) | ((greenSlider.getValue() & 0xFF) << 8) | (blueSlider.getValue() & 0xFF);}
    private Integer parseColor(String text) {
        if (text == null) return null;
        String value = text.trim().replace("#", "").replace("0x", "").replace("0X", "");
        if (value.length() != 6) return null;
        try {return Integer.parseInt(value, 16) & 0xFFFFFF;}
        catch (NumberFormatException ignored) {return null;}
    }
    //endregion

    public void resetTextures() {
        if (!historyRestoring) saveHistoryState();
        wallTexture = new int[][]{{ORIGINAL_WALL_COLOR}};
        floorTexture = new int[][]{{ORIGINAL_FLOOR_COLOR}};
        ceilingTexture = new int[][]{{ORIGINAL_CEILING_COLOR}};
        finishTexture = new int[][]{{ORIGINAL_FINISH_COLOR}};

        wallWidth = 1;
        wallHeight = 1;
        floorWidth = 1;
        floorHeight = 1;
        ceilingWidth = 1;
        ceilingHeight = 1;
        finishWidth = 1;
        finishHeight = 1;

        textureWidth = 1;
        textureHeight = 1;

        updatingSizeControls = true;
        widthSlider.setMaximum(maxWidthForMode(mode));
        heightSlider.setMaximum(maxHeightForMode(mode));
        widthSlider.setValue(1);
        heightSlider.setValue(1);
        updatingSizeControls = false;

        selectedX = 0;
        selectedY = 0;

        updateSizeLabels();
        updateSelectedLabel();
        setColorControls(getOriginalColorForMode(mode));
        textureCanvas.rebuildGrid();
    }
    private int getOriginalColorForMode(TextureMode m) {
        return switch (m) {
            case WALLS -> ORIGINAL_WALL_COLOR;
            case FLOOR -> ORIGINAL_FLOOR_COLOR;
            case CEILING -> ORIGINAL_CEILING_COLOR;
            case FINISH -> ORIGINAL_FINISH_COLOR;
        };
    }

    //region Texture RW-
    public Path saveTexturesToTmp() throws IOException {
        Files.createDirectories(AppPaths.DATA_DIR);

        writeTexture(AppPaths.WALL_TEXTURE_FILE, wallTexture);
        writeTexture(AppPaths.FLOOR_TEXTURE_FILE, floorTexture);
        writeTexture(AppPaths.CEILING_TEXTURE_FILE, ceilingTexture);
        writeTexture(AppPaths.FINISH_TEXTURE_FILE, finishTexture);
        return AppPaths.DATA_DIR;
    }

    public static int[][] readTexture(Path file) throws IOException {return readTexture(file, MAX_TEXTURE_SIZE, MAX_TEXTURE_SIZE);}
    public static int[][] readFinishTexture(Path file) throws IOException {return readTexture(file, MAX_TEXTURE_SIZE, MAX_FINISH_HEIGHT);}
    public static int[][] readTexture(Path file, int maxWidth, int maxHeight) throws IOException {
        if (!Files.exists(file)) return new int[0][0];

        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            int magic = in.readInt();
            if (magic != TEXTURE_FILE_MAGIC) throw new IOException("Invalid texture file (bad signature): " + file);

            in.readUnsignedByte();
            int width = in.readUnsignedShort(), height = in.readUnsignedShort();

            if (width <= 0 || height <= 0) return new int[0][0];
            if (width > maxWidth || height > maxHeight) throw new IOException("Texture is larger than " + maxWidth + "x" + maxHeight + ": " + file);

            int[][] texture = new int[height][width];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int r = in.readUnsignedByte();
                    int g = in.readUnsignedByte();
                    int b = in.readUnsignedByte();
                    texture[y][x] = (r << 16) | (g << 8) | b;
                }
            }
            return texture;
        } catch (EOFException ex) {
            throw new IOException("Texture file is truncated or corrupted: " + file, ex);
        }
    }
    private static void writeTexture(Path file, int[][] texture) throws IOException {
        int width = isValidTexture(texture) ? texture[0].length : 0;
        int height = isValidTexture(texture) ? texture.length : 0;

        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(file, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)))) {
            out.writeInt(TEXTURE_FILE_MAGIC);
            out.writeByte(TEXTURE_FORMAT_VERSION);
            out.writeShort(width);
            out.writeShort(height);

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int color = texture[y][x];
                    if (color == EMPTY_COLOR) color = SAVED_EMPTY_COLOR;
                    color &= 0xFFFFFF;

                    out.writeByte((color >> 16) & 0xFF);
                    out.writeByte((color >> 8) & 0xFF);
                    out.writeByte(color & 0xFF);
                }
            }
        }
    }
    public void loadTexturesFromTmp() throws IOException {
        int[][] walls = readTexture(AppPaths.WALL_TEXTURE_FILE, MAX_TEXTURE_SIZE, MAX_TEXTURE_SIZE);
        int[][] floor = readTexture(AppPaths.FLOOR_TEXTURE_FILE, MAX_TEXTURE_SIZE, MAX_TEXTURE_SIZE);
        int[][] ceiling = readTexture(AppPaths.CEILING_TEXTURE_FILE, MAX_TEXTURE_SIZE, MAX_TEXTURE_SIZE);
        int[][] finish = readTexture(AppPaths.FINISH_TEXTURE_FILE, MAX_TEXTURE_SIZE, MAX_FINISH_HEIGHT);

        int[] wallSize = sizeOf(walls, MAX_TEXTURE_SIZE, MAX_TEXTURE_SIZE);
        int[] floorSize = sizeOf(floor, MAX_TEXTURE_SIZE, MAX_TEXTURE_SIZE);
        int[] ceilingSize = sizeOf(ceiling, MAX_TEXTURE_SIZE, MAX_TEXTURE_SIZE);
        int[] finishSize = sizeOf(finish, MAX_TEXTURE_SIZE, MAX_FINISH_HEIGHT);

        wallWidth = wallSize[0];
        wallHeight = wallSize[1];
        floorWidth = floorSize[0];
        floorHeight = floorSize[1];
        ceilingWidth = ceilingSize[0];
        ceilingHeight = ceilingSize[1];
        finishWidth = finishSize[0];
        finishHeight = finishSize[1];

        wallTexture = normalizeTexture(walls, wallWidth, wallHeight);
        floorTexture = normalizeTexture(floor, floorWidth, floorHeight);
        ceilingTexture = normalizeTexture(ceiling, ceilingWidth, ceilingHeight);
        finishTexture = normalizeTexture(finish, finishWidth, finishHeight);

        syncSizeControlsToMode();
        selectFirstCell();
        textureCanvas.rebuildGrid();
    }
    //endregion

    //region PNG Import
    private void importPng() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("PNG Image", "png"));

        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        try {
            BufferedImage source = ImageIO.read(chooser.getSelectedFile());
            if (source == null) throw new IOException("Unsupported or corrupted PNG file.");

            BufferedImage fitted = fitImageToBounds(source, maxWidthForMode(mode), maxHeightForMode(mode));
            int[][] imported = imageToTexture(fitted);

            saveHistoryState();
            setCurrentTexture(imported);
            setSizeForMode(mode, fitted.getWidth(), fitted.getHeight());

            textureWidth = fitted.getWidth();
            textureHeight = fitted.getHeight();

            updatingSizeControls = true;
            widthSlider.setMaximum(maxWidthForMode(mode));
            heightSlider.setMaximum(maxHeightForMode(mode));
            widthSlider.setValue(textureWidth);
            heightSlider.setValue(textureHeight);
            updatingSizeControls = false;

            selectedX = -1;
            selectedY = -1;

            updateSizeLabels();
            updateSelectedLabel();
            selectFirstCell();
            textureCanvas.rebuildGrid();
        }
        catch (IOException ex) { JOptionPane.showMessageDialog(this, "te.could_not_import\n" + ex.getMessage(), "te.import_error", JOptionPane.ERROR_MESSAGE); }
    }
    private BufferedImage fitImageToBounds(BufferedImage source, int maxWidth, int maxHeight) {
        int width = source.getWidth(), height = source.getHeight();
        if (width <= maxWidth && height <= maxHeight) return source;

        double scale = Math.min((double) maxWidth / width, (double) maxHeight / height);
        int newWidth = Math.max(1, (int) Math.floor(width * scale));
        int newHeight = Math.max(1, (int) Math.floor(height * scale));

        BufferedImage scaled = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = scaled.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.drawImage(source, 0, 0, newWidth, newHeight, null);
        g2d.dispose();
        return scaled;
    }
    private int[][] imageToTexture(BufferedImage image) {
        int width = image.getWidth(), height = image.getHeight();
        int[][] texture = new int[height][width];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) texture[y][x] = image.getRGB(x, y) & 0xFFFFFF;
        return texture;
    }
    //endregion

    private static int[][] normalizeTexture(int[][] source, int width, int height) {
        int[][] result = createEmptyTexture(width, height);
        if (!isValidTexture(source)) return result;
        int copyHeight = Math.min(height, source.length);
        for (int y = 0; y < copyHeight; y++) {
            int copyWidth = Math.min(width, source[y].length);
            for (int x = 0; x < copyWidth; x++) result[y][x] = source[y][x] & 0xFFFFFF;
        }
        return result;
    }
    private static boolean isValidTexture(int[][] texture) {
        if (texture == null || texture.length == 0 || texture.length > ABSOLUTE_MAX_TEXTURE_DIMENSION) return false;
        if (texture[0] == null || texture[0].length == 0 || texture[0].length > ABSOLUTE_MAX_TEXTURE_DIMENSION) return false;

        int width = texture[0].length;

        for (int[] row : texture) if (row == null || row.length != width) return false;
        return true;
    }
    private static class EditorState {
        private final int[][] wallTexture, floorTexture, ceilingTexture, finishTexture;
        private final int wallWidth, wallHeight;
        private final int floorWidth, floorHeight;
        private final int ceilingWidth, ceilingHeight;
        private final int finishWidth, finishHeight;

        EditorState(int[][] wallTexture, int[][] floorTexture, int[][] ceilingTexture, int[][] finishTexture,
                    int wallWidth, int wallHeight, int floorWidth, int floorHeight,
                    int ceilingWidth, int ceilingHeight, int finishWidth, int finishHeight) {
            this.wallTexture = copyTexture(wallTexture);
            this.floorTexture = copyTexture(floorTexture);
            this.ceilingTexture = copyTexture(ceilingTexture);
            this.finishTexture = copyTexture(finishTexture);
            this.wallWidth = wallWidth;
            this.wallHeight = wallHeight;
            this.floorWidth = floorWidth;
            this.floorHeight = floorHeight;
            this.ceilingWidth = ceilingWidth;
            this.ceilingHeight = ceilingHeight;
            this.finishWidth = finishWidth;
            this.finishHeight = finishHeight;
        }
    }
    private static int[][] copyTexture(int[][] texture) {
        if (texture == null) return null;
        int[][] copy = new int[texture.length][];
        for (int y = 0; y < texture.length; y++) copy[y] = texture[y] == null ? null : texture[y].clone();
        return copy;
    }
    private EditorState createEditorState() {
        return new EditorState(wallTexture, floorTexture, ceilingTexture, finishTexture,
                wallWidth, wallHeight, floorWidth, floorHeight, ceilingWidth, ceilingHeight, finishWidth, finishHeight);
    }
    private void saveHistoryState() {
        if (historyRestoring) return;
        undoHistory.push(createEditorState());
        while (undoHistory.size() > MAX_HISTORY) undoHistory.removeLast();
        redoHistory.clear();
    }
    private void undo() {
        if (undoHistory.isEmpty()) return;
        redoHistory.push(createEditorState());
        while (redoHistory.size() > MAX_HISTORY) redoHistory.removeLast();
        restoreEditorState(undoHistory.pop());
    }
    private void redo() {
        if (redoHistory.isEmpty()) return;
        undoHistory.push(createEditorState());
        while (undoHistory.size() > MAX_HISTORY) undoHistory.removeLast();
        restoreEditorState(redoHistory.pop());
    }
    private void restoreEditorState(EditorState state) {
        historyRestoring = true;

        wallTexture = copyTexture(state.wallTexture);
        floorTexture = copyTexture(state.floorTexture);
        ceilingTexture = copyTexture(state.ceilingTexture);
        finishTexture = copyTexture(state.finishTexture);

        wallWidth = state.wallWidth;
        wallHeight = state.wallHeight;
        floorWidth = state.floorWidth;
        floorHeight = state.floorHeight;
        ceilingWidth = state.ceilingWidth;
        ceilingHeight = state.ceilingHeight;
        finishWidth = state.finishWidth;
        finishHeight = state.finishHeight;

        syncSizeControlsToMode();
        selectedX = -1;
        selectedY = -1;
        updateSelectedLabel();
        selectFirstCell();
        textureCanvas.refreshView();
        historyRestoring = false;
    }
    private void setupUndoRedo() {
        InputMap inputMap = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = getActionMap();
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK), "undo");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK), "redo");
        actionMap.put("undo", new AbstractAction() {@Override public void actionPerformed(ActionEvent e) {undo();}});
        actionMap.put("redo", new AbstractAction() {@Override public void actionPerformed(ActionEvent e) {redo();}});
    }
    private class TextureCanvas extends JPanel {
        private static final int ZOOM_ENABLE_SIZE = 64;
        private static final double MIN_ZOOM = 1.0;
        private static final double MAX_ZOOM = 24.0;
        private static final double ZOOM_STEP_BASE = 1.15;
        private static final int GRID_LINE_MIN_PIXELS = 4;
        private static final int PAN_STEP_PIXELS = 40;

        private double gridX, gridY, gridSize, baseGridSize = 1;

        private double zoom = MIN_ZOOM;
        private double panGridX = 0;
        private double panGridY = 0;

        TextureCanvas() {
            setBackground(new Color(18, 18, 21));
            setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY, 2));
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            setFocusable(true);

            MouseAdapter handler = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    requestFocusInWindow();
                    mouseHistoryStarted = false;
                    if (currentTool == Tool.BRUSH || currentTool == Tool.ERASER) {
                        saveHistoryState();
                        mouseHistoryStarted = true;
                    }
                    handlePointerEvent(e.getX(), e.getY());
                }
                @Override public void mouseReleased(MouseEvent e) {mouseHistoryStarted = false;}
                @Override public void mouseDragged(MouseEvent e) {if (currentTool == Tool.BRUSH || currentTool == Tool.ERASER) handlePointerEvent(e.getX(), e.getY());}
            };
            addMouseListener(handler);
            addMouseMotionListener(handler);
            addMouseListener(new MouseAdapter() { @Override public void mouseEntered(MouseEvent e) { requestFocusInWindow(); }});

            addMouseWheelListener(this::handleMouseWheel);
            setupPanKeyBindings();
        }
        private boolean zoomAllowed() { return textureWidth > ZOOM_ENABLE_SIZE || textureHeight > ZOOM_ENABLE_SIZE; }
        private void handleMouseWheel(MouseWheelEvent e) {
            if (!e.isControlDown()) return;
            requestFocusInWindow();
            if (!zoomAllowed() && zoom <= MIN_ZOOM) return;

            double newZoom = Math.clamp(zoom * (Math.pow(ZOOM_STEP_BASE, -e.getPreciseWheelRotation())), MIN_ZOOM, zoomAllowed() ? MAX_ZOOM : MIN_ZOOM);
            if (newZoom == zoom) return;

            double safeGridSize = Math.max(0.0001, gridSize);
            double newGridSize = baseGridSize * newZoom;

            panGridX = e.getX() - ((e.getX() - gridX) / safeGridSize) * newGridSize;
            panGridY = e.getY() - ((e.getY() - gridY) / safeGridSize) * newGridSize;
            zoom = newZoom;
            repaint();
        }
        private void setupPanKeyBindings() {
            InputMap im = getInputMap(WHEN_FOCUSED);
            ActionMap am = getActionMap();

            im.put(KeyStroke.getKeyStroke(KeyEvent.VK_W, 0), "panUp");
            im.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "panUp");
            im.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, 0), "panDown");
            im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "panDown");
            im.put(KeyStroke.getKeyStroke(KeyEvent.VK_A, 0), "panLeft");
            im.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "panLeft");
            im.put(KeyStroke.getKeyStroke(KeyEvent.VK_D, 0), "panRight");
            im.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "panRight");

            am.put("panUp", panAction(0, PAN_STEP_PIXELS));
            am.put("panDown", panAction(0, -PAN_STEP_PIXELS));
            am.put("panLeft", panAction(PAN_STEP_PIXELS, 0));
            am.put("panRight", panAction(-PAN_STEP_PIXELS, 0));
        }
        private Action panAction(int dx, int dy) {
            return new AbstractAction() {
                @Override public void actionPerformed(ActionEvent e) {
                    if (zoom <= MIN_ZOOM) return;
                    panGridX += dx;
                    panGridY += dy;
                    repaint();
                }
            };
        }
        private void handlePointerEvent(int px, int py) {
            int x = (int) Math.floor((px - gridX) / Math.max(0.0001, gridSize));
            int y = (int) Math.floor((py - gridY) / Math.max(0.0001, gridSize));

            if (x < 0 || y < 0 || x >= textureWidth || y >= textureHeight) return;
            switch (currentTool) {
                case SELECT -> selectCell(x, y);
                case BRUSH -> paintCells(x, y, brushSizeSlider.getValue(), getColorFromControls());
                case ERASER -> paintCells(x, y, eraserSizeSlider.getValue(), EMPTY_COLOR);
                case FILL -> floodFill(x, y, getColorFromControls());
            }
        }
        void resetView() { zoom = MIN_ZOOM; panGridX = 0; panGridY = 0; }
        void refreshView() { revalidate(); repaint(); }
        void rebuildGrid() { resetView(); revalidate(); repaint(); }
        private double clampAxis(double desired, double contentSize, int viewportSize) {
            if (contentSize <= viewportSize) return (viewportSize - contentSize) / 2.0;
            double min = viewportSize - contentSize;
            return Math.clamp(desired, min, 0);
        }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;

            int viewportW = getWidth(), viewportH = getHeight();
            int outerSize = Math.min(viewportW - 40, viewportH - 40);
            outerSize = Math.max(40, outerSize);

            baseGridSize = Math.max(0.0001, Math.min((double) outerSize / textureWidth, (double) outerSize / textureHeight));

            if (!zoomAllowed()) zoom = MIN_ZOOM;
            gridSize = baseGridSize * zoom;

            double actualWidth = gridSize * textureWidth, actualHeight = gridSize * textureHeight;

            gridX = clampAxis(panGridX, actualWidth, viewportW);
            gridY = clampAxis(panGridY, actualHeight, viewportH);
            panGridX = gridX;
            panGridY = gridY;

            for (int y = 0; y < textureHeight; y++) {
                for (int x = 0; x < textureWidth; x++) {
                    int color = getCurrentTexture()[y][x];
                    if (color == EMPTY_COLOR) color = 0xFFFFFF;
                    g2d.setColor(new Color(color & 0xFFFFFF));
                    g2d.fill(new Rectangle2D.Double(gridX + x * gridSize, gridY + y * gridSize, gridSize, gridSize));
                }
            }
            boolean showGrid = gridSize >= GRID_LINE_MIN_PIXELS;
            if (showGrid) {
                g2d.setColor(Color.BLACK);
                for (int x = 0; x <= textureWidth; x++) {
                    double px = gridX + x * gridSize;
                    g2d.draw(new Line2D.Double(px, gridY, px, gridY + actualHeight));
                }
                for (int y = 0; y <= textureHeight; y++) {
                    double py = gridY + y * gridSize;
                    g2d.draw(new Line2D.Double(gridX, py, gridX + actualWidth, py));
                }
            }
            if (selectedX >= 0 && selectedY >= 0 && selectedX < textureWidth && selectedY < textureHeight) {
                g2d.setColor(Color.RED);
                double rs = Math.max(1, gridSize - 2);
                g2d.draw(new Rectangle2D.Double(gridX + selectedX * gridSize + 1, gridY + selectedY * gridSize + 1, rs, rs));
            }
            g2d.setColor(Color.WHITE);
            g2d.setFont(g2d.getFont().deriveFont(Font.BOLD, 14f));
            String text = textureWidth + " x " + textureHeight;
            if (zoom > MIN_ZOOM + 0.001) text += String.format(" (%.0f%%)", zoom * 100);
            FontMetrics fm = g2d.getFontMetrics();
            g2d.drawString(text, (viewportW - fm.stringWidth(text)) / 2, Math.max(18, (int) gridY - 8));
        }
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
}