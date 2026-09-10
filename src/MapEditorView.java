import StyleUI.*;
import Helpers.AppPaths;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class MapEditorView extends JPanel {
    //region Variables
    private static final Path TMP_DIR = AppPaths.DATA_DIR;
    private static final Path THEME_FILE = TMP_DIR.resolve("theme.txt");
    private final Style currentStyle = loadTheme();

    private static final int MIN_MAP_SIZE = 5, MAX_MAP_SIZE = 201;
    private static final int MIN_TOOL_SIZE = 1, MAX_TOOL_SIZE = 15;
    private static final int PATH = 0, WALL = 1, FINISH = 2;
    private static final int MAX_HISTORY = 10;
    private static final int DEFAULT_SIZE = 21;
    private static final int START_X = 1, START_Y = 1;

    private enum Tool {SELECT, BRUSH, ERASER}

    private int mapWidth = DEFAULT_SIZE, mapHeight = DEFAULT_SIZE;
    private int[][] map = createMap(mapWidth, mapHeight);
    private Tool currentTool = Tool.SELECT;
    private int selectedBlock = PATH;
    private int selectedX = -1, selectedY = -1;

    private final MapCanvas mapCanvas = new MapCanvas();

    private final StyledButton exitButton = new StyledButton(currentStyle, "Exit");
    private final StyledButton clearMapButton = new StyledButton(currentStyle, "Clear Map");
    private final StyledButton resetButton = new StyledButton(currentStyle, "Reset");
    private final StyledButton playMapButton = new StyledButton(currentStyle, "Play Map");

    private final StyledLabel sizeLabel = new StyledLabel(currentStyle, "", false, false);
    private final StyledLabel selectedBlockLabel = new StyledLabel(currentStyle, "", false);
    private final StyledLabel selectedCellLabel = new StyledLabel(currentStyle,"Selected: none", false);
    private final StyledLabel brushSizeLabel = new StyledLabel(currentStyle, "", false);
    private final StyledLabel eraserSizeLabel = new StyledLabel(currentStyle, "",  false);
    private final StyledLabel instruction = new StyledLabel(currentStyle, "Start: (1, 1)    |    0 = Path    1 = Wall    2 = Finish", false, false);
    private final StyledLabel bottomInstruction = new StyledLabel(currentStyle, "Select a tool and block, then click or drag on the map.", false, false);
    private StyledLabel toolsTitle, blockTitle;

    private StyledScrollPane rightScroll;

    private final StyledSlider widthSlider = new StyledSlider(currentStyle, MIN_MAP_SIZE, MAX_MAP_SIZE, DEFAULT_SIZE);
    private final StyledSlider heightSlider = new StyledSlider(currentStyle, MIN_MAP_SIZE, MAX_MAP_SIZE, DEFAULT_SIZE);
    private final StyledSlider brushSizeSlider = new StyledSlider(currentStyle, MIN_TOOL_SIZE, MAX_TOOL_SIZE, 1);
    private final StyledSlider eraserSizeSlider = new StyledSlider(currentStyle, MIN_TOOL_SIZE, MAX_TOOL_SIZE, 1);

    private final StyledTextField widthField = new StyledTextField(currentStyle, String.valueOf(DEFAULT_SIZE));
    private final StyledTextField heightField = new StyledTextField(currentStyle, String.valueOf(DEFAULT_SIZE));

    private final StyledToggleButton selectToolButton = new StyledToggleButton(currentStyle,"Select");
    private final StyledToggleButton brushToolButton = new StyledToggleButton(currentStyle,"Brush");
    private final StyledToggleButton eraserToolButton = new StyledToggleButton(currentStyle,"Eraser");
    private final StyledToggleButton pathButton = new StyledToggleButton(currentStyle,"0 - Path");
    private final StyledToggleButton wallButton = new StyledToggleButton(currentStyle,"1 - Wall");
    private final StyledToggleButton finishButton = new StyledToggleButton(currentStyle,"2 - Finish");

    private boolean changingSize = false;
    private boolean changingSizeField = false;
    private boolean restoringHistory = false;

    private final Deque<EditorState> undoHistory = new ArrayDeque<>();
    private final Deque<EditorState> redoHistory = new ArrayDeque<>();
    //endregion

    public MapEditorView() {
        setLayout(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setBackground(new Color(28, 28, 32));

        buildTopMenu();
        buildCenter();
        buildRightPanel();
        buildBottomMenu();
        setupUndoRedo();

        updateSizeControls();
        updateToolLabels();
        updateBlockLabels();
    }

    //region Map Utilities
    private static int[][] createMap(int width, int height) {
        int[][] result = new int[height][width];
        for (int y = 0; y < height; y++) Arrays.fill(result[y], PATH);
        result[START_Y][START_X] = PATH;
        return result;
    }

    private static int[][] copyMap(int[][] source) {
        if (source == null) return null;
        int[][] result = new int[source.length][];
        for (int y = 0; y < source.length; y++) result[y] = source[y] == null ? null : source[y].clone();
        return result;
    }

    private int normalizeSize(int value) {
        value = Math.clamp(value, MIN_MAP_SIZE, MAX_MAP_SIZE);
        if ((value & 1) == 0) {
            if (value < MAX_MAP_SIZE) value++;
            else value--;
        }
        return value;
    }

    private boolean isInsideMap(int x, int y) {return x < 0 || y < 0 || x >= mapWidth || y >= mapHeight;}
    //endregion

    //region Map Resizing
    private void resizeMap(int newWidth, int newHeight) {
        newWidth = normalizeSize(newWidth);
        newHeight = normalizeSize(newHeight);

        if (newWidth == mapWidth && newHeight == mapHeight) {
            updateSizeControls();
            return;
        }

        saveHistoryState();

        int oldWidth = mapWidth;
        int oldHeight = mapHeight;
        int[][] oldMap = map;

        int[][] newMap = new int[newHeight][newWidth];
        for (int y = 0; y < newHeight; y++) Arrays.fill(newMap[y], PATH);

        int copyWidth = Math.min(oldWidth, newWidth);
        int copyHeight = Math.min(oldHeight, newHeight);
        for (int y = 0; y < copyHeight; y++) System.arraycopy(oldMap[y], 0, newMap[y], 0, copyWidth);

        mapWidth = newWidth;
        mapHeight = newHeight;
        map = newMap;
        map[START_Y][START_X] = PATH;

        selectedX = -1;
        selectedY = -1;

        updateSizeControls();
        updateSelectedLabel();
        mapCanvas.revalidate();
        mapCanvas.repaint();
    }

    private void updateSizeControls() {
        changingSize = true;
        changingSizeField = true;

        widthSlider.setValue(mapWidth);
        widthField.setText(String.valueOf(mapWidth));
        heightSlider.setValue(mapHeight);
        heightField.setText(String.valueOf(mapHeight));
        sizeLabel.setText("Size: " + mapWidth + " x " + mapHeight);

        changingSizeField = false;
        changingSize = false;
    }

    private void applyWidthField() {
        if (changingSizeField) return;
        try {
            int value = Integer.parseInt(widthField.getText().trim());
            resizeMap(normalizeSize(value), mapHeight);}
        catch (NumberFormatException ignored) {updateSizeControls();}
    }

    private void applyHeightField() {
        if (changingSizeField) return;
        try {
            int value = Integer.parseInt(heightField.getText().trim());
            resizeMap(mapWidth, normalizeSize(value));}
        catch (NumberFormatException ignored) {updateSizeControls();}
    }
    //endregion

    //region UI Building - Top Menu
    private void buildTopMenu() {
        JPanel top = new JPanel(new BorderLayout(10, 5));
        top.setOpaque(false);

        JPanel controls = new JPanel();
        controls.setOpaque(false);
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));

        JPanel widthRow = createSizeRow("Width:", widthSlider, widthField);
        widthSlider.setForeground(Color.WHITE);
        controls.add(widthRow);
        controls.add(Box.createVerticalStrut(4));

        JPanel heightRow = createSizeRow("Height:", heightSlider, heightField);
        heightSlider.setForeground(Color.WHITE);
        controls.add(heightRow);
        controls.add(Box.createVerticalStrut(5));

        sizeLabel.setForeground(Color.WHITE);
        controls.add(sizeLabel);
        controls.add(Box.createVerticalStrut(5));

        instruction.setForeground(Color.WHITE);
        controls.add(instruction);

        exitButton.setFocusable(false);
        exitButton.addActionListener(e -> RCJMS.instance.ChangeView(RCJMS.instance.mainMenuView = new MainMenuView(), "Main Menu"));

        JPanel exitPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        exitPanel.setOpaque(false);
        exitPanel.add(exitButton);

        top.add(controls, BorderLayout.CENTER);
        top.add(exitPanel, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        widthSlider.addChangeListener(e -> {
            if (changingSize) return;
            int value = normalizeSize(widthSlider.getValue());
            if (value != widthSlider.getValue()) {
                changingSize = true;
                widthSlider.setValue(value);
                changingSize = false;
            }
            resizeMap(value, mapHeight);
        });

        widthField.addActionListener(e -> applyWidthField());
        widthField.addFocusListener(new FocusAdapter() {@Override public void focusLost(FocusEvent e) {applyWidthField();}});

        heightSlider.addChangeListener(e -> {
            if (changingSize) return;
            int value = normalizeSize(heightSlider.getValue());
            if (value != heightSlider.getValue()) {
                changingSize = true;
                heightSlider.setValue(value);
                changingSize = false;
            }
            resizeMap(mapWidth, value);
        });

        heightField.addActionListener(e -> applyHeightField());
        heightField.addFocusListener(new FocusAdapter() {@Override public void focusLost(FocusEvent e) {applyHeightField();}});
    }
    private JPanel createSizeRow(String labelText, StyledSlider slider, StyledTextField field) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);

        StyledLabel label = new StyledLabel(currentStyle, labelText);
        label.setForeground(Color.WHITE);

        configureSizeSlider(slider);

        field.setPreferredSize(new Dimension(70, 28));
        field.setHorizontalAlignment(JTextField.CENTER);
        field.setToolTipText("Size: " + MIN_MAP_SIZE + " - " + MAX_MAP_SIZE + ", odd values only");

        row.add(label, BorderLayout.WEST);
        row.add(slider, BorderLayout.CENTER);
        row.add(field, BorderLayout.EAST);

        return row;
    }
    private void configureSizeSlider(StyledSlider slider) {
        slider.setMajorTickSpacing(20);
        slider.setMinorTickSpacing(2);
        slider.setPaintTicks(true);
        slider.setPaintLabels(true);
        slider.setOpaque(false);
    }
    //endregion

    private void buildCenter() {
        JPanel center = new JPanel(new BorderLayout());
        center.setOpaque(false);
        center.add(mapCanvas, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);
    }

    //region UI Building - Right Panel
    private void buildRightPanel() {
        JPanel right = new JPanel();
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.setBorder(new EmptyBorder(20, 10, 10, 10));
        right.setBackground(new Color(25, 25, 25));

        toolsTitle = createTitle("Tool");
        JPanel tools = new JPanel(new GridLayout(3, 1, 4, 4));
        tools.setOpaque(false);

        ButtonGroup toolGroup = new ButtonGroup();
        toolGroup.add(selectToolButton);
        toolGroup.add(brushToolButton);
        toolGroup.add(eraserToolButton);

        selectToolButton.setSelected(true);
        selectToolButton.addActionListener(e -> currentTool = Tool.SELECT);
        brushToolButton.addActionListener(e -> currentTool = Tool.BRUSH);
        eraserToolButton.addActionListener(e -> currentTool = Tool.ERASER);

        tools.add(selectToolButton);
        tools.add(brushToolButton);
        tools.add(eraserToolButton);

        blockTitle = createTitle("Block");
        JPanel blockButtons = new JPanel(new GridLayout(3, 1, 4, 4));
        blockButtons.setOpaque(false);

        ButtonGroup blockGroup = new ButtonGroup();
        blockGroup.add(pathButton);
        blockGroup.add(wallButton);
        blockGroup.add(finishButton);

        pathButton.setSelected(true);
        pathButton.addActionListener(e -> {selectedBlock = PATH;updateBlockLabels();});
        wallButton.addActionListener(e -> {selectedBlock = WALL;updateBlockLabels();});
        finishButton.addActionListener(e -> {selectedBlock = FINISH;updateBlockLabels();});

        blockButtons.add(pathButton);
        blockButtons.add(wallButton);
        blockButtons.add(finishButton);

        selectedBlockLabel.setForeground(Color.WHITE);
        selectedBlockLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel brushRow = createSliderRow(brushSizeLabel, brushSizeSlider);
        JPanel eraserRow = createSliderRow(eraserSizeLabel, eraserSizeSlider);

        brushSizeSlider.addChangeListener(e -> updateToolLabels());
        eraserSizeSlider.addChangeListener(e -> updateToolLabels());

        selectedCellLabel.setForeground(Color.WHITE);
        selectedCellLabel.setAlignmentX(Component.CENTER_ALIGNMENT);


        clearMapButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        clearMapButton.addActionListener(e -> clearMap());

        right.add(toolsTitle);
        right.add(Box.createVerticalStrut(10));
        right.add(tools);
        right.add(Box.createVerticalStrut(18));
        right.add(blockTitle);
        right.add(Box.createVerticalStrut(10));
        right.add(blockButtons);
        right.add(Box.createVerticalStrut(6));
        right.add(selectedBlockLabel);
        right.add(Box.createVerticalStrut(15));
        right.add(brushRow);
        right.add(Box.createVerticalStrut(6));
        right.add(eraserRow);
        right.add(Box.createVerticalStrut(18));

        JSeparator separator = new JSeparator();
        separator.setAlignmentX(Component.CENTER_ALIGNMENT);
        separator.setMaximumSize(new Dimension(Integer.MAX_VALUE, 2));
        right.add(separator);

        right.add(Box.createVerticalStrut(15));
        right.add(selectedCellLabel);
        right.add(Box.createVerticalStrut(10));
        right.add(clearMapButton);
        right.add(Box.createVerticalGlue());

        rightScroll = new StyledScrollPane(right, currentStyle);
        rightScroll.setBorder(BorderFactory.createEmptyBorder());
        rightScroll.setPreferredSize(new Dimension(260, 0));
        rightScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        rightScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        rightScroll.getVerticalScrollBar().setUnitIncrement(16);
        add(rightScroll, BorderLayout.EAST);
    }

    private StyledLabel createTitle(String text) {
        StyledLabel label = new StyledLabel(currentStyle, text, false);
        label.setForeground(Color.WHITE);
        label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        return label;
    }
    private JPanel createSliderRow(StyledLabel label, StyledSlider slider) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(215, 30));

        label.setForeground(Color.WHITE);
        label.setPreferredSize(new Dimension(65, 20));

        slider.setOpaque(false);

        row.add(label, BorderLayout.WEST);
        row.add(slider, BorderLayout.CENTER);

        return row;
    }
    //endregion

    private void buildBottomMenu() {
        JPanel bottom = new JPanel();
        bottom.setOpaque(false);
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));

        bottomInstruction.setForeground(Color.WHITE);
        bottomInstruction.setAlignmentX(Component.RIGHT_ALIGNMENT);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        buttons.setOpaque(false);

        resetButton.addActionListener(e -> resetMap());
        playMapButton.addActionListener(e -> saveMapAndPlayNGG());

        buttons.add(resetButton);
        buttons.add(playMapButton);

        bottom.add(bottomInstruction);
        bottom.add(Box.createVerticalStrut(4));
        bottom.add(buttons);

        add(bottom, BorderLayout.SOUTH);
    }

    //region UI Updates
    private void updateToolLabels() {
        brushSizeLabel.setText("Brush: " + brushSizeSlider.getValue());
        eraserSizeLabel.setText("Eraser: " + eraserSizeSlider.getValue());
    }

    private void updateBlockLabels() {
        switch (selectedBlock) {
            case PATH -> selectedBlockLabel.setText("Selected: Path (0)");
            case WALL -> selectedBlockLabel.setText("Selected: Wall (1)");
            case FINISH -> selectedBlockLabel.setText("Selected: Finish (2)");
            default -> selectedBlockLabel.setText("Selected: ?");
        }
    }

    private void updateSelectedLabel() {
        if (isInsideMap(selectedX, selectedY)) {
            selectedCellLabel.setText("Selected: none");
            return;
        }
        selectedCellLabel.setText("Selected: " + selectedX + ", " + selectedY + " (" + map[selectedY][selectedX] + ")");
    }
    //endregion

    //region Editor Actions
    private void selectCell(int x, int y) {
        if (isInsideMap(x, y)) return;
        selectedX = x;
        selectedY = y;
        updateSelectedLabel();
        mapCanvas.repaint();
    }

    private void paintCells(int centerX, int centerY, int size, int value) {
        if (isInsideMap(centerX, centerY)) return;

        int radius = (size - 1) / 2;

        int minX = Math.max(0, centerX - radius);
        int maxX = Math.min(mapWidth - 1, centerX + radius);
        int minY = Math.max(0, centerY - radius);
        int maxY = Math.min(mapHeight - 1, centerY + radius);

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                if (x == START_X && y == START_Y) continue;
                double dx = x - centerX;
                double dy = y - centerY;
                if (dx * dx + dy * dy <= radius * radius + 0.001) map[y][x] = value;
            }
        }

        map[START_Y][START_X] = PATH;
        mapCanvas.repaint();
    }

    private void clearMap() {
        saveHistoryState();
        for (int y = 0; y < mapHeight; y++) Arrays.fill(map[y], PATH);
        map[START_Y][START_X] = PATH;
        selectedX = -1;
        selectedY = -1;
        updateSelectedLabel();
        mapCanvas.repaint();
    }

    private void resetMap() {
        saveHistoryState();
        mapWidth = DEFAULT_SIZE;
        mapHeight = DEFAULT_SIZE;
        map = createMap(mapWidth, mapHeight);
        selectedX = -1;
        selectedY = -1;
        updateSizeControls();
        updateSelectedLabel();
        mapCanvas.revalidate();
        mapCanvas.repaint();
    }
    //endregion

    //region Map Validation
    private java.util.List<int[]> findExits() {
        java.util.List<int[]> exits = new ArrayList<>();

        for (int y = 0; y < mapHeight; y++) {
            for (int x = 0; x < mapWidth; x++) {
                if (map[y][x] == FINISH) exits.add(new int[]{x, y});
            }
        }
        return exits;
    }

    private int[] findReachableExit() {
        boolean[][] visited = new boolean[mapHeight][mapWidth];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{START_X, START_Y});
        visited[START_Y][START_X] = true;

        int[][] directions = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            int x = current[0];
            int y = current[1];

            if (map[y][x] == FINISH)
                return new int[]{x, y};

            for (int[] direction : directions) {
                int nx = x + direction[0];
                int ny = y + direction[1];

                if (isInsideMap(nx, ny)) continue;
                if (visited[ny][nx]) continue;
                if (map[ny][nx] == WALL) continue;

                visited[ny][nx] = true;
                queue.add(new int[]{nx, ny});
            }
        }
        return null;
    }

    private String validateMap() {
        if (mapWidth < MIN_MAP_SIZE || mapHeight < MIN_MAP_SIZE) return "The map is too small.";
        if ((mapWidth & 1) == 0 || (mapHeight & 1) == 0) return "Map width and height must be odd.";
        if (isInsideMap(START_X, START_Y)) return "The starting position is outside the map.";
        if (map[START_Y][START_X] == WALL) return "The starting position (1, 1) must be a Path (0).";

        boolean[][] visited = new boolean[mapHeight][mapWidth];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{START_X, START_Y});
        visited[START_Y][START_X] = true;

        int[][] directions = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            int x = current[0], y = current[1];

            if (map[y][x] == FINISH) return null;

            for (int[] direction : directions) {
                int nx = x + direction[0];
                int ny = y + direction[1];

                if (isInsideMap(nx, ny)) continue;
                if (visited[ny][nx]) continue;
                if (map[ny][nx] == WALL) continue;

                visited[ny][nx] = true;
                queue.add(new int[]{nx, ny});
            }
        }
        return "There is no path from the starting position (1, 1) to any Finish (2).";
    }
    //endregion

    //region  Map RW-
    private void saveMapAndPlayNGG() {
        String error = validateMap();
        if (error != null) {
            JOptionPane.showMessageDialog(this, error, "Map cannot be saved", JOptionPane.ERROR_MESSAGE);
            return;
        }
        try {
            Path folder = AppPaths.DATA_DIR;
            Files.createDirectories(folder);
            Path file = folder.resolve("map.txt");
            writeMap(file);

            java.util.List<int[]> exits = findExits();
            int[] reachable = findReachableExit();

            if (reachable == null) {
                JOptionPane.showMessageDialog(this, "No reachable finish.", "Save error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            String message = "Map saved successfully.\n\nFile:\n" + file.toAbsolutePath() + "\n\nSize: " + mapWidth + " x " + mapHeight + "\nFinishes: " + exits.size() + "\nReachable finish: (" + reachable[0] + ", " + reachable[1] + ")";
            JOptionPane.showMessageDialog(this, message, "Saved", JOptionPane.INFORMATION_MESSAGE);

            RCJMS.instance.gameView = new GameView(readMap(file));
            RCJMS.instance.ChangeView(RCJMS.instance.gameView, "Raycast Me!");
            RCJMS.instance.gameView.start();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not save map:\n" + e.getMessage(), "Save error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void writeMap(Path file) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            writer.write("# Map width=" + mapWidth + " height=" + mapHeight);
            writer.newLine();
            writer.write("# 0=Path, 1=Wall, 2=Finish");
            writer.newLine();

            for (int y = 0; y < mapHeight; y++) {
                for (int x = 0; x < mapWidth; x++) {
                    if (x > 0) writer.write(",");
                    writer.write(String.valueOf(map[y][x]));
                }
                writer.newLine();
            }
        }
    }

    public static int[][] readMap(Path file) throws IOException {
        if (!Files.exists(file)) return new int[0][0];

        java.util.List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        java.util.List<int[]> rows = new ArrayList<>();
        int expectedWidth = -1;

        for (String original : lines) {
            String line = original.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            String[] values = line.split("[,;\\s]+");
            if (expectedWidth == -1) expectedWidth = values.length;
            if (values.length != expectedWidth) throw new IOException("Invalid map: rows have different widths.");

            int[] row = new int[values.length];

            for (int x = 0; x < values.length; x++) {
                int value;
                try { value = Integer.parseInt(values[x]); }
                catch (NumberFormatException e) { throw new IOException("Invalid map value: " + values[x], e); }

                if (value < PATH || value > FINISH) throw new IOException("Invalid block value: " + value + ". Expected 0, 1 or 2.");
                row[x] = value;
            }
            rows.add(row);
        }
        if (rows.isEmpty()) return new int[0][0];

        int height = rows.size();
        int width = expectedWidth;
        if (height < MIN_MAP_SIZE || height > MAX_MAP_SIZE) throw new IOException("Map height must be between " + MIN_MAP_SIZE + " and " + MAX_MAP_SIZE);
        if (width < MIN_MAP_SIZE || width > MAX_MAP_SIZE) throw new IOException("Map width must be between " + MIN_MAP_SIZE + " and " + MAX_MAP_SIZE);
        if ((height & 1) == 0) throw new IOException("Map height must be odd.");
        if ((width & 1) == 0) throw new IOException("Map width must be odd.");
        return rows.toArray(new int[0][]);
    }
    //endregion

    //region Undo/Redo
    private record EditorState(int[][] map, int width, int height) {
        private EditorState(int[][] map, int width, int height) {
            this.map = copyMap(map);
            this.width = width;
            this.height = height;
        }
    }
    private void saveHistoryState() {
        if (restoringHistory) return;
        undoHistory.push(new EditorState(map, mapWidth, mapHeight));
        while (undoHistory.size() > MAX_HISTORY) undoHistory.removeLast();
        redoHistory.clear();
    }
    private void undo() {
        if (undoHistory.isEmpty()) return;
        redoHistory.push(new EditorState(map, mapWidth, mapHeight));
        while (redoHistory.size() > MAX_HISTORY) redoHistory.removeLast();
        restoreState(undoHistory.pop());
    }
    private void redo() {
        if (redoHistory.isEmpty()) return;
        undoHistory.push(new EditorState(map, mapWidth, mapHeight));
        while (undoHistory.size() > MAX_HISTORY) undoHistory.removeLast();
        restoreState(redoHistory.pop());
    }
    private void restoreState(EditorState state) {
        restoringHistory = true;
        map = copyMap(state.map);
        mapWidth = state.width;
        mapHeight = state.height;
        selectedX = -1;
        selectedY = -1;
        updateSizeControls();
        updateSelectedLabel();
        mapCanvas.revalidate();
        mapCanvas.repaint();
        restoringHistory = false;
    }
    private void setupUndoRedo() {
        InputMap inputMap = getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        ActionMap actionMap = getActionMap();

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK), "mapEditorUndo");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK), "mapEditorRedo");

        actionMap.put("mapEditorUndo", new AbstractAction() {@Override public void actionPerformed(ActionEvent e) {undo();}});
        actionMap.put("mapEditorRedo", new AbstractAction() {@Override public void actionPerformed(ActionEvent e) {redo();}});
    }
    //endregion

    //region Canvas
    private class MapCanvas extends JPanel {
        private int gridX, gridY, gridSize;

        MapCanvas() {
            setOpaque(true);
            setBackground(new Color(18, 18, 21));
            setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY, 2));
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));

            MouseAdapter mouseHandler = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    if (currentTool == Tool.BRUSH || currentTool == Tool.ERASER) saveHistoryState();
                    handlePointer(e.getX(), e.getY());}
                @Override public void mouseDragged(MouseEvent e) {if (currentTool == Tool.BRUSH || currentTool == Tool.ERASER) handlePointer(e.getX(), e.getY());}
            };
            addMouseListener(mouseHandler);
            addMouseMotionListener(mouseHandler);
        }

        private void handlePointer(int px, int py) {
            if (gridSize <= 0) return;
            int x = (px - gridX) / gridSize;
            int y = (py - gridY) / gridSize;
            if (isInsideMap(x, y)) return;

            switch (currentTool) {
                case SELECT -> selectCell(x, y);
                case BRUSH -> paintCells(x, y, brushSizeSlider.getValue(), selectedBlock);
                case ERASER -> paintCells(x, y, eraserSizeSlider.getValue(), PATH);
            }
        }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            int width = getWidth();
            int height = getHeight();
            if (width <= 0 || height <= 0) return;

            int availableWidth = Math.max(1, width - 40);
            int availableHeight = Math.max(1, height - 40);

            gridSize = Math.max(1, Math.min(availableWidth / mapWidth, availableHeight / mapHeight));

            int mapPixelWidth = gridSize * mapWidth;
            int mapPixelHeight = gridSize * mapHeight;

            gridX = (width - mapPixelWidth) / 2;
            gridY = (height - mapPixelHeight) / 2;

            drawMap(g);
            drawGrid(g);
            drawStart(g);
            drawSelectedCell(g);
            drawMapSize(g);
        }
        private void drawMap(Graphics g) {
            for (int y = 0; y < mapHeight; y++) {
                for (int x = 0; x < mapWidth; x++) {
                    Color color = switch (map[y][x]) {
                        case PATH -> new Color(45, 45, 50);
                        case WALL -> new Color(225, 225, 230);
                        case FINISH -> new Color(80, 200, 100);
                        default -> Color.MAGENTA;
                    };
                    g.setColor(color);
                    g.fillRect(gridX + x * gridSize, gridY + y * gridSize, gridSize, gridSize);
                }
            }
        }
        private void drawGrid(Graphics g) {
            if (gridSize < 3) return;
            g.setColor(new Color(15, 15, 18));

            for (int x = 0; x <= mapWidth; x++) {
                int px = gridX + x * gridSize;
                g.drawLine(px, gridY, px, gridY + mapHeight * gridSize);
            }
            for (int y = 0; y <= mapHeight; y++) {
                int py = gridY + y * gridSize;
                g.drawLine(gridX, py, gridX + mapWidth * gridSize, py);
            }
        }
        private void drawStart(Graphics g) {
            g.setColor(new Color(50, 120, 255));
            int padding = Math.max(1, gridSize / 5);
            int size = Math.max(1, gridSize - padding * 2);
            g.fillRect(gridX + START_X * gridSize + padding, gridY + START_Y * gridSize + padding, size, size);
        }
        private void drawSelectedCell(Graphics g) {
            if (isInsideMap(selectedX, selectedY)) return;
            g.setColor(Color.RED);
            int border = gridSize >= 3 ? 2 : 1;

            for (int i = 0; i < border; i++)
                g.drawRect(gridX + selectedX * gridSize + i, gridY + selectedY * gridSize + i, Math.max(1, gridSize - i * 2 - 1), Math.max(1, gridSize - i * 2 - 1));
        }
        private void drawMapSize(Graphics g) {
            g.setColor(Color.WHITE);
            g.setFont(g.getFont().deriveFont(Font.BOLD, 14f));

            String text = mapWidth + " x " + mapHeight;
            FontMetrics fm = g.getFontMetrics();

            int x = (getWidth() - fm.stringWidth(text)) / 2;
            int y = Math.max(18, gridY - 8);

            g.drawString(text, x, y);
        }
    }
    //endregion

    private Style loadTheme() {
        try {
            if (Files.exists(THEME_FILE)) {
                String name = Files.readString(THEME_FILE).trim();
                return Style.valueOf(name);
            }
        } catch (IOException | IllegalArgumentException ex) { ex.printStackTrace(); }
        return Style.FLAT;
    }
}