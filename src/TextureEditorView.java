import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

public class TextureEditorView extends JPanel {
    //region Variables
    public enum TextureMode {WALLS, FLOOR, CEILING}

    private enum Tool {SELECT, BRUSH, FILL, ERASER}

    private static final int MIN_TEXTURE_SIZE = 1;
    private static final int MAX_TEXTURE_SIZE = 32;

    private static final int MIN_TOOL_SIZE = 1;
    private static final int MAX_TOOL_SIZE = 10;

    private static final int ORIGINAL_WALL_COLOR = 0xECD485;
    private static final int ORIGINAL_FLOOR_COLOR = 0xD3AF63;
    private static final int ORIGINAL_CEILING_COLOR = 0x816E1E;

    private static final int EMPTY_COLOR = 0x000000;
    private static final int SAVED_EMPTY_COLOR = 0xFFFFFF;

    private final TextureCanvas textureCanvas = new TextureCanvas();
    private final JPanel colorPreview = new JPanel();

    private final JSlider widthSlider = new JSlider(MIN_TEXTURE_SIZE, MAX_TEXTURE_SIZE, 8);
    private final JSlider heightSlider = new JSlider(MIN_TEXTURE_SIZE, MAX_TEXTURE_SIZE, 8);

    private final JLabel widthLabel = new JLabel();
    private final JLabel heightLabel = new JLabel();

    private final JLabel modeLabel = new JLabel();
    private final JLabel selectedCellLabel = new JLabel("Selected: none");

    private final JSlider redSlider = new JSlider(0, 255, 255);
    private final JSlider greenSlider = new JSlider(0, 255, 255);
    private final JSlider blueSlider = new JSlider(0, 255, 255);

    private final JTextField hexField = new JTextField("FFFFFF");

    private final JToggleButton selectToolButton = new JToggleButton("Select");
    private final JToggleButton brushToolButton = new JToggleButton("Brush");
    private final JToggleButton fillToolButton = new JToggleButton("Fill");
    private final JToggleButton eraserToolButton = new JToggleButton("Eraser");

    private final JSlider brushSizeSlider = new JSlider(MIN_TOOL_SIZE, MAX_TOOL_SIZE, 1);
    private final JSlider eraserSizeSlider = new JSlider(MIN_TOOL_SIZE, MAX_TOOL_SIZE, 1);
    private final JLabel brushSizeLabel = new JLabel();
    private final JLabel eraserSizeLabel = new JLabel();

    private Tool currentTool = Tool.SELECT;

    private TextureMode mode = TextureMode.WALLS;

    private int textureWidth = 8;
    private int textureHeight = 8;

    private int wallWidth = 8;
    private int wallHeight = 8;
    private int floorWidth = 8;
    private int floorHeight = 8;
    private int ceilingWidth = 8;
    private int ceilingHeight = 8;

    private int[][] wallTexture = new int[wallHeight][wallWidth];
    private int[][] floorTexture = new int[floorHeight][floorWidth];
    private int[][] ceilingTexture = new int[ceilingHeight][ceilingWidth];

    private int selectedX = -1;
    private int selectedY = -1;

    private boolean updatingSizeControls = false;

    //endregion

    public TextureEditorView() throws IOException {
        setLayout(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setBackground(new Color(28, 28, 32));

        buildTopMenu();
        buildRightPanel();
        buildCenter();
        buildBottomMenu();

        resetTextures();
        updateModeLabel();
        updateSizeLabels();
        updateToolSizeLabels();
        loadTexturesFromTmp();
        textureCanvas.rebuildGrid();
    }
    private int[] sizeOf(int[][] texture) {
        if (isValidTexture(texture)) {
            return new int[]{
                    Math.clamp(texture[0].length, MIN_TEXTURE_SIZE, MAX_TEXTURE_SIZE),
                    Math.clamp(texture.length, MIN_TEXTURE_SIZE, MAX_TEXTURE_SIZE)};
        }
        return new int[]{8, 8};
    }

    //region builders
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
        modeLabel.setFont(modeLabel.getFont().deriveFont(Font.BOLD, 16f));

        JButton wallsButton = new JButton("Walls");
        JButton floorButton = new JButton("Floor");
        JButton ceilingButton = new JButton("Ceiling");

        wallsButton.addActionListener(e -> setMode(TextureMode.WALLS));
        floorButton.addActionListener(e -> setMode(TextureMode.FLOOR));
        ceilingButton.addActionListener(e -> setMode(TextureMode.CEILING));

        modePanel.add(new JLabel("Texture:"));
        modePanel.add(wallsButton);
        modePanel.add(floorButton);
        modePanel.add(ceilingButton);
        modePanel.add(Box.createHorizontalStrut(15));
        modePanel.add(modeLabel);

        widthLabel.setForeground(Color.WHITE);
        heightLabel.setForeground(Color.WHITE);

        configureSizeSlider(widthSlider);
        configureSizeSlider(heightSlider);

        JPanel widthRow = buildLabeledSliderRow("Width:", widthSlider, widthLabel);
        JPanel heightRow = buildLabeledSliderRow("Height:", heightSlider, heightLabel);

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

        JButton exitButton = new JButton("Exit");
        exitButton.setFocusable(false);

        exitButton.addActionListener(e -> {
            Window window = SwingUtilities.getWindowAncestor(this);
            if (window != null) {
                window.dispose();
            }
        });

        JPanel exitPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        exitPanel.setOpaque(false);
        exitPanel.add(exitButton);

        top.add(stack, BorderLayout.CENTER);
        top.add(exitPanel, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);
    }
    private JPanel buildLabeledSliderRow(String labelText, JSlider slider, JLabel valueLabel) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));

        JLabel nameLabel = new JLabel(labelText);
        nameLabel.setForeground(Color.WHITE);

        row.add(nameLabel, BorderLayout.WEST);
        row.add(slider, BorderLayout.CENTER);
        row.add(valueLabel, BorderLayout.EAST);
        return row;
    }
    private void configureSizeSlider(JSlider slider) {
        slider.setMajorTickSpacing(8);
        slider.setMinorTickSpacing(1);
        slider.setPaintTicks(true);
        slider.setPaintLabels(true);
        slider.setOpaque(false);
    }
    private void configureToolSizeSlider(JSlider slider) {
        slider.setMajorTickSpacing(MAX_TOOL_SIZE - MIN_TOOL_SIZE);
        slider.setMinorTickSpacing(1);
        slider.setPaintTicks(false);
        slider.setPaintLabels(false);
        slider.setOpaque(false);
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
        right.setBackground(new Color(38, 38, 44));

        right.setPreferredSize(new Dimension(220, 700));
        right.setMinimumSize(new Dimension(220, 0));
        right.setMaximumSize(new Dimension(220, Integer.MAX_VALUE));

        JLabel toolsTitle = new JLabel("Tool");
        toolsTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        toolsTitle.setForeground(Color.WHITE);
        toolsTitle.setFont(toolsTitle.getFont().deriveFont(Font.BOLD, 18f));

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

        JLabel colorTitle = new JLabel("Color");
        colorTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        colorTitle.setForeground(Color.WHITE);
        colorTitle.setFont(colorTitle.getFont().deriveFont(Font.BOLD, 18f));

        colorPreview.setPreferredSize(new Dimension(150, 150));
        colorPreview.setMinimumSize(new Dimension(150, 150));
        colorPreview.setMaximumSize(new Dimension(150, 150));
        colorPreview.setBackground(Color.WHITE);
        colorPreview.setBorder(BorderFactory.createLineBorder(Color.BLACK, 2));
        colorPreview.setAlignmentX(Component.CENTER_ALIGNMENT);
        colorPreview.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        colorPreview.addMouseListener(new MouseAdapter() {@Override public void mouseClicked(MouseEvent e) {openColorPicker();}});

        JLabel hint = new JLabel("<html><center>Click the square<br>to choose a color</center></html>");
        hint.setForeground(Color.LIGHT_GRAY);
        hint.setAlignmentX(Component.CENTER_ALIGNMENT);

        selectedCellLabel.setForeground(Color.WHITE);
        selectedCellLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton applyButton = new JButton("Apply to selected cell");
        applyButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        applyButton.addActionListener(e -> applyColorToSelectedCell());

        JButton fillButton = new JButton("Fill texture");
        fillButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        fillButton.addActionListener(e -> fillCurrentTexture());

        JButton resetButton = new JButton("Reset");
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

        JPanel centerWrapper = new JPanel(new GridBagLayout());
        centerWrapper.setOpaque(false);
        centerWrapper.add(right);

        JScrollPane rightScroll = new JScrollPane(centerWrapper, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
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

        JLabel info = new JLabel("Pick a tool, pick a color, then click or drag on the grid.");
        info.setForeground(Color.LIGHT_GRAY);
        info.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        buttons.setOpaque(false);
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton saveButton = new JButton("Save");
        JButton resetButton = new JButton("Reset");

        saveButton.addActionListener(e -> {
            try {
                Path folder = saveTexturesToTmp();
                JOptionPane.showMessageDialog(this, "Textures saved to:\n" + folder.toAbsolutePath(), "Saved", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Could not save textures:\n" + ex.getMessage(), "Save error", JOptionPane.ERROR_MESSAGE);}
        });
        resetButton.addActionListener(e -> resetTextures());

        buttons.add(resetButton);
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
        };
    }
    private void setSizeForMode(TextureMode m, int w, int h) {
        switch (m) {
            case WALLS -> {wallWidth = w;wallHeight = h;}
            case FLOOR -> {floorWidth = w;floorHeight = h;}
            case CEILING -> {ceilingWidth = w;ceilingHeight = h;}
        }
    }
    private void updateModeLabel() {
        String text = switch (mode) {
            case WALLS -> "Walls";
            case FLOOR -> "Floor";
            case CEILING -> "Ceiling";
        };
        modeLabel.setText("Editing: " + text);
    }
    //endregion

    private void setTextureSize(int newWidth, int newHeight) {
        newWidth = Math.clamp(newWidth, MIN_TEXTURE_SIZE, MAX_TEXTURE_SIZE);
        newHeight = Math.clamp(newHeight, MIN_TEXTURE_SIZE, MAX_TEXTURE_SIZE);

        if (newWidth == textureWidth && newHeight == textureHeight) {
            updateSizeLabels();
            return;
        }

        int[][] resized = resizePreservingData(getCurrentTexture(), newWidth, newHeight);
        setCurrentTexture(resized);

        textureWidth = newWidth;
        textureHeight = newHeight;
        setSizeForMode(mode, newWidth, newHeight);

        selectedX = -1;
        selectedY = -1;

        updatingSizeControls = true;
        if (widthSlider.getValue() != newWidth) widthSlider.setValue(newWidth);
        if (heightSlider.getValue() != newHeight) heightSlider.setValue(newHeight);
        updatingSizeControls = false;

        updateSizeLabels();
        updateSelectedLabel();
        textureCanvas.rebuildGrid();
    }
    private void updateSizeLabels() {
        widthLabel.setText("W: " + textureWidth);
        heightLabel.setText("H: " + textureHeight);
    }
    private void updateToolSizeLabels() {
        brushSizeLabel.setText("Brush: " + brushSizeSlider.getValue());
        eraserSizeLabel.setText("Eraser: " + eraserSizeSlider.getValue());
    }
    private int[][] resizePreservingData(int[][] source, int newWidth, int newHeight) {
        int[][] result = new int[newHeight][newWidth];
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
        if (selectedX < 0 || selectedY < 0) selectedCellLabel.setText("Selected: none");
        else selectedCellLabel.setText("Selected: " + selectedX + ", " + selectedY);
    }
    private int[][] getCurrentTexture() {
        return switch (mode) {
            case WALLS -> wallTexture;
            case FLOOR -> floorTexture;
            case CEILING -> ceilingTexture;
        };
    }
    private void setCurrentTexture(int[][] texture) {
        switch (mode) {
            case WALLS -> wallTexture = texture;
            case FLOOR -> floorTexture = texture;
            case CEILING -> ceilingTexture = texture;
        }
    }
    private void applyColorToSelectedCell() {
        if (selectedX < 0 || selectedY < 0) return;
        int color = getColorFromControls();
        getCurrentTexture()[selectedY][selectedX] = color;
        textureCanvas.repaint();
    }
    private void fillCurrentTexture() {
        int color = getColorFromControls();
        int[][] texture = getCurrentTexture();
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
        final JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), "Choose Color", Dialog.ModalityType.APPLICATION_MODAL);

        JPanel panel = new JPanel();
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JPanel preview = new JPanel();
        preview.setPreferredSize(new Dimension(220, 80));
        preview.setMinimumSize(new Dimension(220, 80));
        preview.setMaximumSize(new Dimension(220, 80));
        preview.setBackground(colorPreview.getBackground());
        preview.setBorder(BorderFactory.createLineBorder(Color.BLACK, 2));
        preview.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel sliders = new JPanel(new GridLayout(3, 2, 8, 8));
        sliders.setMaximumSize(new Dimension(350, 100));

        JLabel redLabel = new JLabel("Red");
        JLabel greenLabel = new JLabel("Green");
        JLabel blueLabel = new JLabel("Blue");

        JSlider r = new JSlider(0, 255, redSlider.getValue());
        JSlider g = new JSlider(0, 255, greenSlider.getValue());
        JSlider b = new JSlider(0, 255, blueSlider.getValue());

        JLabel rValue = new JLabel(String.valueOf(r.getValue()));
        JLabel gValue = new JLabel(String.valueOf(g.getValue()));
        JLabel bValue = new JLabel(String.valueOf(b.getValue()));

        sliders.add(redLabel);
        sliders.add(createSliderRow(r, rValue));
        sliders.add(greenLabel);
        sliders.add(createSliderRow(g, gValue));
        sliders.add(blueLabel);
        sliders.add(createSliderRow(b, bValue));

        JLabel hexLabel = new JLabel("Color code (RRGGBB):");
        JTextField pickerHex = new JTextField(hexField.getText());
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

        pickerHex.addActionListener(e -> {
            Integer color = parseColor(pickerHex.getText());
            if (color != null) {
                r.setValue((color >> 16) & 0xFF);
                g.setValue((color >> 8) & 0xFF);
                b.setValue(color & 0xFF);
                updatePreview.run();
            }});

        JButton apply = new JButton("Apply");
        JButton cancel = new JButton("Cancel");

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
    private JPanel createSliderRow(JSlider slider, JLabel valueLabel) {
        JPanel panel = new JPanel(new BorderLayout(5, 0));
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
        wallTexture = new int[][]{{ORIGINAL_WALL_COLOR}};
        floorTexture = new int[][]{{ORIGINAL_FLOOR_COLOR}};
        ceilingTexture = new int[][]{{ORIGINAL_CEILING_COLOR}};

        wallWidth = 1;
        wallHeight = 1;
        floorWidth = 1;
        floorHeight = 1;
        ceilingWidth = 1;
        ceilingHeight = 1;

        textureWidth = 1;
        textureHeight = 1;

        updatingSizeControls = true;
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
        };
    }

    //region Texture RW-
    public Path saveTexturesToTmp() throws IOException {
        Path tmp = Paths.get("tmp");
        Files.createDirectories(tmp);

        writeTexture(tmp.resolve("walltexture.txt"), wallTexture);
        writeTexture(tmp.resolve("floortexture.txt"), floorTexture);
        writeTexture(tmp.resolve("ceilingtexture.txt"), ceilingTexture);
        return tmp;
    }
    public static int[][] readTexture(Path file) throws IOException {
        if (!Files.exists(file)) return new int[0][0];

        java.util.List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        java.util.List<int[]> rows = new java.util.ArrayList<>();

        int expectedWidth = -1;

        for (String originalLine : lines) {
            String line = originalLine.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] values = line.split("[,;\\s]+");
            if (expectedWidth == -1) expectedWidth = values.length;
            if (values.length != expectedWidth) throw new IOException("Invalid texture: rows have different widths in " + file);
            int[] row = new int[values.length];

            for (int x = 0; x < values.length; x++) {
                String value = values[x].trim();

                if (value.startsWith("0x") || value.startsWith("0X")) value = value.substring(2);
                if (value.startsWith("#")) value = value.substring(1);
                if (value.length() != 6) throw new IOException("Invalid color '" + values[x] + "' in " + file);

                try {row[x] = Integer.parseInt(value, 16) & 0xFFFFFF;}
                catch (NumberFormatException ex) {throw new IOException("Invalid color '" + values[x] + "' in " + file, ex);}
            }
            rows.add(row);
        }

        if (rows.isEmpty() || expectedWidth <= 0) return new int[0][0];
        if (rows.size() > MAX_TEXTURE_SIZE || expectedWidth > MAX_TEXTURE_SIZE) throw new IOException("Texture is larger than 32x32: " + file);
        return rows.toArray(new int[0][]);
    }
    private static void writeTexture(Path file, int[][] texture) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            int width = isValidTexture(texture) ? texture[0].length : 0;
            int height = isValidTexture(texture) ? texture.length : 0;

            writer.write("# Texture width=" + width + " height=" + height);
            writer.newLine();

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (x > 0) writer.write(",");

                    int color = texture[y][x];

                    if (color == EMPTY_COLOR) color = SAVED_EMPTY_COLOR;
                    writer.write(String.format("%06X", color & 0xFFFFFF));
                }
                writer.newLine();
            }
        }
    }
    public void loadTexturesFromTmp() throws IOException {
        Path tmp = Paths.get("tmp");
        int[][][] textures = {
                readTexture(tmp.resolve("walltexture.txt")),
                readTexture(tmp.resolve("floortexture.txt")),
                readTexture(tmp.resolve("ceilingtexture.txt"))
        };

        int[][] walls = textures[0];
        int[][] floor = textures[1];
        int[][] ceiling = textures[2];

        int[] wallSize = sizeOf(walls);
        int[] floorSize = sizeOf(floor);
        int[] ceilingSize = sizeOf(ceiling);

        wallWidth = wallSize[0];
        wallHeight = wallSize[1];
        floorWidth = floorSize[0];
        floorHeight = floorSize[1];
        ceilingWidth = ceilingSize[0];
        ceilingHeight = ceilingSize[1];

        wallTexture = normalizeTexture(walls, wallWidth, wallHeight);
        floorTexture = normalizeTexture(floor, floorWidth, floorHeight);
        ceilingTexture = normalizeTexture(ceiling, ceilingWidth, ceilingHeight);

        syncSizeControlsToMode();
        selectFirstCell();
        textureCanvas.rebuildGrid();
    }
    //endregion

    private static int[][] normalizeTexture(int[][] source, int width, int height) {
        int[][] result = new int[height][width];
        if (!isValidTexture(source)) return result;
        int copyHeight = Math.min(height, source.length);
        for (int y = 0; y < copyHeight; y++) {
            int copyWidth = Math.min(width, source[y].length);
            for (int x = 0; x < copyWidth; x++) result[y][x] = source[y][x] & 0xFFFFFF;
        }
        return result;
    }
    private static boolean isValidTexture(int[][] texture) {
        if (texture == null || texture.length == 0 || texture.length > MAX_TEXTURE_SIZE) return false;
        if (texture[0] == null || texture[0].length == 0 || texture[0].length > MAX_TEXTURE_SIZE) return false;

        int width = texture[0].length;

        for (int[] row : texture) if (row == null || row.length != width) return false;
        return true;
    }

    private class TextureCanvas extends JPanel {
        private int gridX;
        private int gridY;
        private int gridSize;

        TextureCanvas() {
            setBackground(new Color(18, 18, 21));
            setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY, 2));
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));

            MouseAdapter handler = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {handlePointerEvent(e.getX(), e.getY());}
                @Override public void mouseDragged(MouseEvent e) {if (currentTool == Tool.BRUSH || currentTool == Tool.ERASER) handlePointerEvent(e.getX(), e.getY());}
            };

            addMouseListener(handler);
            addMouseMotionListener(handler);
        }
        private void handlePointerEvent(int px, int py) {
            int x = (px - gridX) / Math.max(1, gridSize);
            int y = (py - gridY) / Math.max(1, gridSize);

            if (x < 0 || y < 0 || x >= textureWidth || y >= textureHeight) return;
            switch (currentTool) {
                case SELECT -> selectCell(x, y);
                case BRUSH -> paintCells(x, y, brushSizeSlider.getValue(), getColorFromControls());
                case ERASER -> paintCells(x, y, eraserSizeSlider.getValue(), EMPTY_COLOR);
                case FILL -> floodFill(x, y, getColorFromControls());
            }
        }
        void rebuildGrid() {
            revalidate();
            repaint();
        }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            int outerSize = Math.min(getWidth() - 40, getHeight() - 40);
            outerSize = Math.max(40, outerSize);

            gridSize = Math.max(1, Math.min(outerSize / textureWidth, outerSize / textureHeight));

            int actualWidth = gridSize * textureWidth;
            int actualHeight = gridSize * textureHeight;

            gridX = (getWidth() - actualWidth) / 2;
            gridY = (getHeight() - actualHeight) / 2;

            int[][] texture = getCurrentTexture();

            for (int y = 0; y < textureHeight; y++) {
                for (int x = 0; x < textureWidth; x++) {
                    int color = texture[y][x];

                    if (color == EMPTY_COLOR) color = 0xFFFFFF;

                    g.setColor(new Color(color & 0xFFFFFF));
                    g.fillRect(gridX + x * gridSize, gridY + y * gridSize, gridSize, gridSize);
                }
            }
            g.setColor(Color.BLACK);

            for (int x = 0; x <= textureWidth; x++) {
                int px = gridX + x * gridSize;
                g.drawLine(px, gridY, px, gridY + actualHeight);
            }

            for (int y = 0; y <= textureHeight; y++) {
                int py = gridY + y * gridSize;
                g.drawLine(gridX, py, gridX + actualWidth, py);
            }

            if (selectedX >= 0 && selectedY >= 0 && selectedX < textureWidth && selectedY < textureHeight) {
                g.setColor(Color.RED);
                g.drawRect(gridX + selectedX * gridSize + 1, gridY + selectedY * gridSize + 1, Math.max(1, gridSize - 2), Math.max(1, gridSize - 2));
            }
            g.setColor(Color.WHITE);
            g.setFont(g.getFont().deriveFont(Font.BOLD, 14f));
            String text = textureWidth + " x " + textureHeight;
            FontMetrics fm = g.getFontMetrics();
            g.drawString(text, (getWidth() - fm.stringWidth(text)) / 2, Math.max(18, gridY - 8));
        }
    }
}