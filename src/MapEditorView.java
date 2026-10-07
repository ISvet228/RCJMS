import StyleUI.*;
import Helpers.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class MapEditorView extends JPanel {
    //region Variables
    private final Style currentStyle = loadTheme();

    private static final int MIN_MAP_SIZE = 5, MAX_MAP_SIZE = 201;
    private static final int MIN_TOOL_SIZE = 1, MAX_TOOL_SIZE = 15;
    private static final int MAX_FLOORS = 20;
    private static final int PATH = 0, WALL = 1, FINISH = 2, STAIRS_UP = 3, STAIRS_DOWN = 4, PORTAL = 5;
    private static final int MAX_HISTORY = 10;
    private static final int DEFAULT_SIZE = 25;
    private static final int START_X = 1, START_Y = 1;

    private int mapWidth = DEFAULT_SIZE, mapHeight = DEFAULT_SIZE;
    private final java.util.List<int[][]> floorMaps = createInitialFloorList();
    private int currentFloorIndex = 0;
    private int[][] map = floorMaps.getFirst();
    private final java.util.List<int[]> portalLinks = new ArrayList<>();
    private int[] pendingPortal = null;
    private static final int[][] PORTAL_DIRS = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
    private static final String[] PORTAL_DIR_ARROWS = {"↑", "→", "↓", "←"};
    private int portalDirIndex = 0;
    private Tool currentTool = Tool.BRUSH;
    private int selectedBlock = PATH;

    private final MapCanvas mapCanvas = new MapCanvas();

    private final StyledButton exitButton = new StyledButton(currentStyle, "exit");
    private final StyledButton clearMapButton = new StyledButton(currentStyle, "me.clear_map");
    private final StyledButton resetButton = new StyledButton(currentStyle, "reset");
    private final StyledButton loadMapButton = new StyledButton(currentStyle, "me.load_map");
    private final StyledButton loadSeedButton = new StyledButton(currentStyle, "me.load_from_seed");
    private final StyledButton portalDirButton = new StyledButton(currentStyle, "me.portal_direction");
    private final StyledButton playMapButton = new StyledButton(currentStyle, "me.play_map");
    private final StyledButton addFloorButton = new StyledButton(currentStyle, "me.add_floor");
    private final StyledButton removeFloorButton = new StyledButton(currentStyle, "me.remove_floor");
    private final StyledButton prevFloorButton = new StyledButton(currentStyle, "<");
    private final StyledButton nextFloorButton = new StyledButton(currentStyle, ">");

    private final StyledLabel sizeLabel = new StyledLabel(currentStyle, "", false, false);
    private final StyledLabel selectedBlockLabel = new StyledLabel(currentStyle, "", false);
    private final StyledLabel brushSizeLabel = new StyledLabel(currentStyle, "", false);
    private final StyledLabel eraserSizeLabel = new StyledLabel(currentStyle, "",  false);
    private final StyledLabel instruction = new StyledLabel(currentStyle, "me.blocks_info", false, false);
    private final StyledLabel bottomInstruction = new StyledLabel(currentStyle, "me.select_a_tool", false, false);
    private final StyledLabel floorLabel = new StyledLabel(currentStyle, "", false, false);
    private StyledLabel toolsTitle, blockTitle;

    private StyledScrollPane rightScroll;

    private final StyledSlider widthSlider = new StyledSlider(currentStyle, MIN_MAP_SIZE, MAX_MAP_SIZE, DEFAULT_SIZE);
    private final StyledSlider heightSlider = new StyledSlider(currentStyle, MIN_MAP_SIZE, MAX_MAP_SIZE, DEFAULT_SIZE);
    private final StyledSlider brushSizeSlider = new StyledSlider(currentStyle, MIN_TOOL_SIZE, MAX_TOOL_SIZE, 1);
    private final StyledSlider eraserSizeSlider = new StyledSlider(currentStyle, MIN_TOOL_SIZE, MAX_TOOL_SIZE, 1);

    private final StyledTextField widthField = new StyledTextField(currentStyle, String.valueOf(DEFAULT_SIZE));
    private final StyledTextField heightField = new StyledTextField(currentStyle, String.valueOf(DEFAULT_SIZE));

    private static final int GEOMETRY_EUCLIDEAN = 0, GEOMETRY_WRONG = 1;
    private final StyledToggle wrongGeometryToggle = new StyledToggle(currentStyle, "mm.wrong_geometry");
    private final StyledToggle skipValidationToggle = new StyledToggle(currentStyle, "me.test_anyway");

    private final StyledToggleButton brushToolButton = new StyledToggleButton(currentStyle,"brush");
    private final StyledToggleButton eraserToolButton = new StyledToggleButton(currentStyle,"eraser");
    private final StyledToggleButton portalToolButton = new StyledToggleButton(currentStyle,"me.portal_tool");
    private final StyledToggleButton portalOneSidedToolButton = new StyledToggleButton(currentStyle,"me.portal_one_sided_tool");
    private final StyledToggleButton pathButton = new StyledToggleButton(currentStyle,"me.path");
    private final StyledToggleButton wallButton = new StyledToggleButton(currentStyle,"me.wall");
    private final StyledToggleButton finishButton = new StyledToggleButton(currentStyle,"me.finish");
    private final StyledToggleButton stairsUpButton = new StyledToggleButton(currentStyle,"me.stairs_up");
    private final StyledToggleButton stairsDownButton = new StyledToggleButton(currentStyle,"me.stairs_down");

    private boolean changingSize = false;
    private boolean changingSizeField = false;
    private boolean restoringHistory = false;

    private SeedMapBuilder.Options lastSeedOptions = null;

    private final Deque<EditorState> undoHistory = new ArrayDeque<>();
    private final Deque<EditorState> redoHistory = new ArrayDeque<>();
    //endregion

    //region Constructors
    public MapEditorView() {
        setLayout(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setBackground(RCJMS.MY_FAV_GRAY);

        buildTopMenu();
        buildCenter();
        buildRightPanel();
        buildBottomMenu();
        setupKeyBinds();

        updateSizeControls();
        updateToolLabels();
        updateBlockLabels();
        updateFloorControls();
        updateLoadButton();
        updatePortalDirButton();
    }
    //endregion

    //region Map Utilities
    private static int[][] createMap(int width, int height) {
        int[][] result = new int[height][width];
        for (int y = 0; y < height; y++) Arrays.fill(result[y], PATH);
        result[START_Y][START_X] = PATH;
        return result;
    }
    private static java.util.List<int[][]> createInitialFloorList() {
        java.util.List<int[][]> list = new ArrayList<>();
        list.add(createMap(DEFAULT_SIZE, DEFAULT_SIZE));
        return list;
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

        for (int f = 0; f < floorMaps.size(); f++) {
            int[][] oldFloor = floorMaps.get(f);
            int[][] newFloor = new int[newHeight][newWidth];
            for (int y = 0; y < newHeight; y++) Arrays.fill(newFloor[y], PATH);

            int copyWidth = Math.min(oldWidth, newWidth);
            int copyHeight = Math.min(oldHeight, newHeight);
            for (int y = 0; y < copyHeight; y++) System.arraycopy(oldFloor[y], 0, newFloor[y], 0, copyWidth);

            if (f == 0) newFloor[START_Y][START_X] = PATH;
            floorMaps.set(f, newFloor);
        }

        mapWidth = newWidth;
        mapHeight = newHeight;
        map = floorMaps.get(currentFloorIndex);

        Iterator<int[]> linkIt = portalLinks.iterator();
        while (linkIt.hasNext()) {
            int[] link = linkIt.next();
            boolean aOut = link[1] >= mapWidth || link[2] >= mapHeight;
            boolean bOut = link[5] >= mapWidth || link[6] >= mapHeight;
            if (!aOut && !bOut) continue;
            linkIt.remove();
            if (!aOut) floorMaps.get(link[0])[link[2]][link[1]] = PATH;
            if (!bOut) floorMaps.get(link[11])[link[6]][link[5]] = PATH;
        }
        pendingPortal = null;


        updateSizeControls();
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

    //region Floors
    private void switchFloor(int index) {
        index = Math.clamp(index, 0, floorMaps.size() - 1);
        if (index == currentFloorIndex) return;
        currentFloorIndex = index;
        map = floorMaps.get(currentFloorIndex);
        updateFloorControls();
        mapCanvas.repaint();
    }
    private void addFloor() {
        if (floorMaps.size() >= MAX_FLOORS) return;
        saveHistoryState();
        floorMaps.add(createMap(mapWidth, mapHeight));
        currentFloorIndex = floorMaps.size() - 1;
        map = floorMaps.get(currentFloorIndex);
        updateFloorControls();
        mapCanvas.repaint();
    }
    private void removeFloor() {
        if (floorMaps.size() <= 1) return;
        saveHistoryState();
        int removed = currentFloorIndex;
        floorMaps.remove(removed);

        Iterator<int[]> it = portalLinks.iterator();
        while (it.hasNext()) {
            int[] link = it.next();
            if (link[0] == removed || link[11] == removed) {
                it.remove();
                if (link[0] != removed) revertPortalCell(link[0], link[1], link[2], removed);
                if (link[11] != removed) revertPortalCell(link[11], link[5], link[6], removed);
            }
        }
        for (int[] link : portalLinks) {
            if (link[0] > removed) link[0]--;
            if (link[11] > removed) link[11]--;
        }
        if (pendingPortal != null) {
            if (pendingPortal[0] == removed) pendingPortal = null;
            else if (pendingPortal[0] > removed) pendingPortal[0]--;
        }

        currentFloorIndex = Math.min(removed, floorMaps.size() - 1);
        map = floorMaps.get(currentFloorIndex);
        updateFloorControls();
        mapCanvas.repaint();
    }
    private void updateFloorControls() {
        floorLabel.setText("me.floor" + (currentFloorIndex + 1) + " / " + floorMaps.size());
        prevFloorButton.setEnabled(currentFloorIndex > 0);
        nextFloorButton.setEnabled(currentFloorIndex < floorMaps.size() - 1);
        removeFloorButton.setEnabled(floorMaps.size() > 1);
        addFloorButton.setEnabled(floorMaps.size() < MAX_FLOORS);

        boolean canGoUp = currentFloorIndex < floorMaps.size() - 1;
        boolean canGoDown = currentFloorIndex > 0;
        stairsUpButton.setEnabled(canGoUp);
        stairsDownButton.setEnabled(canGoDown);

        if ((selectedBlock == STAIRS_UP && !canGoUp) || (selectedBlock == STAIRS_DOWN && !canGoDown)) {
            selectedBlock = PATH;
            pathButton.setSelected(true);
            updateBlockLabels();
        }
    }
    //endregion

    //region Portals
    private void rotatePortalDirection() {
        portalDirIndex = (portalDirIndex + 1) % PORTAL_DIRS.length;
        updatePortalDirButton();
    }
    private void updatePortalDirButton() {
        portalDirButton.setText("me.portal_direction" + PORTAL_DIR_ARROWS[portalDirIndex]);
    }
    private void handlePortalClick(int x, int y, boolean oneSided) {
        if (isInsideMap(x, y)) return;

        if (currentFloorIndex == 0 && x == START_X && y == START_Y) { bottomInstruction.setText("me.portal_not_on_start"); return; }
        if (map[y][x] == PORTAL) { bottomInstruction.setText("me.portal_already"); return; }
        int[] dir = PORTAL_DIRS[portalDirIndex];

        if (pendingPortal == null) {
            pendingPortal = new int[]{currentFloorIndex, x, y, dir[0], dir[1], oneSided ? 1 : 0};
            bottomInstruction.setText("me.portal_pick_second");
            mapCanvas.repaint();
            return;
        }
        if (pendingPortal[0] == currentFloorIndex && pendingPortal[1] == x && pendingPortal[2] == y) {
            pendingPortal = null;
            bottomInstruction.setText(oneSided ? "me.portal_one_sided_hint" : "me.portal_hint");
            mapCanvas.repaint();
            return;
        }

        saveHistoryState();
        int[] anchor = pendingPortal;
        portalLinks.add(new int[]{anchor[0], anchor[1], anchor[2], anchor[3], anchor[4], x, y, dir[0], dir[1], anchor[5], oneSided ? 1 : 0, currentFloorIndex});
        floorMaps.get(anchor[0])[anchor[2]][anchor[1]] = PORTAL;
        map[y][x] = PORTAL;
        pendingPortal = null;
        bottomInstruction.setText(oneSided ? "me.portal_one_sided_hint" : "me.portal_hint");
        mapCanvas.repaint();
    }
    private void revertPortalCell(int floor, int x, int y, int removedFloor) {
        if (floor == removedFloor || floor < 0 || floor >= floorMaps.size()) return;
        int[][] floorMap = floorMaps.get(floor);
        if (floorMap[y][x] == PORTAL) floorMap[y][x] = PATH;
    }
    private void clearPortalAt(int floor, int x, int y) {
        Iterator<int[]> it = portalLinks.iterator();
        while (it.hasNext()) {
            int[] link = it.next();
            if (link[0] == floor && link[1] == x && link[2] == y) {
                it.remove();
                revertPortalCell(link[11], link[5], link[6], -1);
                return;
            }
            if (link[11] == floor && link[5] == x && link[6] == y) {
                it.remove();
                revertPortalCell(link[0], link[1], link[2], -1);
                return;
            }
        }
    }
    private PortalData[] buildPortalDataPerFloor() {
        PortalData[] result = new PortalData[floorMaps.size()];
        for (int i = 0; i < result.length; i++) result[i] = new PortalData();
        for (int[] link : portalLinks) {
            int floorA = link[0], floorB = link[11];
            if (floorA < 0 || floorA >= result.length || floorB < 0 || floorB >= result.length) continue;
            PortalData.linkFloors(result[floorA], floorA, link[1], link[2], link[3], link[4], link[9] != 0,
                    result[floorB], floorB, link[5], link[6], link[7], link[8], link[10] != 0);
        }
        return result;
    }
    //endregion

    //region UI Building - Top Menu
    private void buildTopMenu() {
        JPanel top = new JPanel(new BorderLayout(10, 5));
        top.setOpaque(false);

        JPanel controls = new JPanel();
        controls.setOpaque(false);
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));

        JPanel widthRow = createSizeRow("width", widthSlider, widthField);
        widthSlider.setForeground(Color.WHITE);
        controls.add(widthRow);
        controls.add(Box.createVerticalStrut(4));

        JPanel heightRow = createSizeRow("height", heightSlider, heightField);
        heightSlider.setForeground(Color.WHITE);
        controls.add(heightRow);
        controls.add(Box.createVerticalStrut(5));

        sizeLabel.setForeground(Color.WHITE);
        controls.add(sizeLabel);
        controls.add(Box.createVerticalStrut(5));

        instruction.setForeground(Color.WHITE);
        controls.add(instruction);

        exitButton.setFocusable(false);
        exitButton.addActionListener(e -> exitToMainMenu());

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
    private void exitToMainMenu() { RCJMS.instance.changeView(RCJMS.instance.mainMenuView = new MainMenuView(), "main_menu"); }
    private JPanel createSizeRow(String labelText, StyledSlider slider, StyledTextField field) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);

        StyledLabel label = new StyledLabel(currentStyle, labelText);
        label.setForeground(Color.WHITE);

        configureSizeSlider(slider);

        field.setPreferredSize(new Dimension(70, 28));
        field.setHorizontalAlignment(JTextField.CENTER);

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

    //region UI Building - Right Panel
    private void buildRightPanel() {
        JPanel right = new JPanel();
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.setBorder(new EmptyBorder(20, 10, 10, 10));
        right.setBackground(new Color(25, 25, 25));

        toolsTitle = createTitle("tool");
        JPanel tools = new JPanel(new GridLayout(4, 1, 4, 4));
        tools.setOpaque(false);

        ButtonGroup toolGroup = new ButtonGroup();
        toolGroup.add(brushToolButton);
        toolGroup.add(eraserToolButton);
        toolGroup.add(portalToolButton);
        toolGroup.add(portalOneSidedToolButton);

        brushToolButton.setSelected(true);
        brushToolButton.addActionListener(e -> {currentTool = Tool.BRUSH; pendingPortal = null; bottomInstruction.setText("me.select_a_tool");});
        eraserToolButton.addActionListener(e -> {currentTool = Tool.ERASER; pendingPortal = null; bottomInstruction.setText("me.select_a_tool");});
        portalToolButton.addActionListener(e -> {currentTool = Tool.PORTAL; bottomInstruction.setText("me.portal_hint"); mapCanvas.repaint();});
        portalOneSidedToolButton.addActionListener(e -> {currentTool = Tool.PORTAL_ONE_SIDED; bottomInstruction.setText("me.portal_one_sided_hint"); mapCanvas.repaint();});

        tools.add(brushToolButton);
        tools.add(eraserToolButton);
        tools.add(portalToolButton);
        tools.add(portalOneSidedToolButton);

        portalDirButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        portalDirButton.setMaximumSize(new Dimension(260, 34));
        portalDirButton.addActionListener(e -> rotatePortalDirection());

        blockTitle = createTitle("me.block");
        JPanel blockButtons = new JPanel(new GridLayout(5, 1, 4, 4));
        blockButtons.setOpaque(false);

        ButtonGroup blockGroup = new ButtonGroup();
        blockGroup.add(pathButton);
        blockGroup.add(wallButton);
        blockGroup.add(finishButton);
        blockGroup.add(stairsUpButton);
        blockGroup.add(stairsDownButton);

        pathButton.setSelected(true);
        pathButton.addActionListener(e -> {selectedBlock = PATH;updateBlockLabels();brushToolButton.doClick();});
        wallButton.addActionListener(e -> {selectedBlock = WALL;updateBlockLabels();brushToolButton.doClick();});
        finishButton.addActionListener(e -> {selectedBlock = FINISH;updateBlockLabels();brushToolButton.doClick();});
        stairsUpButton.addActionListener(e -> {selectedBlock = STAIRS_UP;updateBlockLabels();brushToolButton.doClick();});
        stairsDownButton.addActionListener(e -> {selectedBlock = STAIRS_DOWN;updateBlockLabels();brushToolButton.doClick();});

        blockButtons.add(pathButton);
        blockButtons.add(wallButton);
        blockButtons.add(finishButton);
        blockButtons.add(stairsUpButton);
        blockButtons.add(stairsDownButton);

        selectedBlockLabel.setForeground(Color.WHITE);
        selectedBlockLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel brushRow = createSliderRow(brushSizeLabel, brushSizeSlider);
        JPanel eraserRow = createSliderRow(eraserSizeLabel, eraserSizeSlider);

        brushSizeSlider.addChangeListener(e -> updateToolLabels());
        eraserSizeSlider.addChangeListener(e -> updateToolLabels());


        clearMapButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        clearMapButton.addActionListener(e -> clearMap());

        JPanel floorNav = new JPanel(new FlowLayout(FlowLayout.CENTER, 2, 2));
        floorNav.setOpaque(false);
        floorNav.setAlignmentX(Component.CENTER_ALIGNMENT);
        floorNav.setMaximumSize(new Dimension(260, 34));
        prevFloorButton.setPreferredSize(new Dimension(28, 26));
        nextFloorButton.setPreferredSize(new Dimension(28, 26));
        floorLabel.setPreferredSize(new Dimension(110, 26));
        prevFloorButton.addActionListener(e -> switchFloor(currentFloorIndex - 1));
        nextFloorButton.addActionListener(e -> switchFloor(currentFloorIndex + 1));
        floorNav.add(prevFloorButton);
        floorNav.add(floorLabel);
        floorNav.add(nextFloorButton);

        JPanel floorEdit = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 2));
        floorEdit.setOpaque(false);
        floorEdit.setAlignmentX(Component.CENTER_ALIGNMENT);
        floorEdit.setMaximumSize(new Dimension(260, 38));
        addFloorButton.setPreferredSize(new Dimension(90, 30));
        removeFloorButton.setPreferredSize(new Dimension(90, 30));
        addFloorButton.addActionListener(e -> addFloor());
        removeFloorButton.addActionListener(e -> removeFloor());
        floorEdit.add(addFloorButton);
        floorEdit.add(removeFloorButton);

        floorLabel.setForeground(Color.WHITE);

        right.add(toolsTitle);
        right.add(Box.createVerticalStrut(10));
        right.add(tools);
        right.add(Box.createVerticalStrut(6));
        right.add(portalDirButton);
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

        JSeparator floorSeparator = new JSeparator();
        floorSeparator.setAlignmentX(Component.CENTER_ALIGNMENT);
        floorSeparator.setMaximumSize(new Dimension(Integer.MAX_VALUE, 2));
        right.add(floorSeparator);
        right.add(Box.createVerticalStrut(10));
        right.add(floorNav);
        right.add(Box.createVerticalStrut(6));
        right.add(floorEdit);
        right.add(Box.createVerticalStrut(15));

        JSeparator separator = new JSeparator();
        separator.setAlignmentX(Component.CENTER_ALIGNMENT);
        separator.setMaximumSize(new Dimension(Integer.MAX_VALUE, 2));
        right.add(separator);

        right.add(Box.createVerticalStrut(15));
        right.add(clearMapButton);
        right.add(Box.createVerticalGlue());

        rightScroll = new StyledScrollPane(right, currentStyle);
        rightScroll.setBorder(BorderFactory.createEmptyBorder());
        rightScroll.setPreferredSize(new Dimension(300, 0));
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

    //region UI Updates
    private void updateToolLabels() {
        brushSizeLabel.setText("brush_size" + brushSizeSlider.getValue());
        eraserSizeLabel.setText("eraser_size" + eraserSizeSlider.getValue());
    }

    private void updateBlockLabels() {
        switch (selectedBlock) {
            case PATH -> selectedBlockLabel.setText("me.selected_path");
            case WALL -> selectedBlockLabel.setText("me.selected_wall");
            case FINISH -> selectedBlockLabel.setText("me.selected_finish");
            case STAIRS_UP -> selectedBlockLabel.setText("me.selected_stairs_up");
            case STAIRS_DOWN -> selectedBlockLabel.setText("me.selected_stairs_down");
            default -> selectedBlockLabel.setText("me.selected_?");
        }
    }
    //endregion

    //region Editor Actions
    private void paintCells(int centerX, int centerY, int size, int value) {
        if (isInsideMap(centerX, centerY)) return;

        int radius = (size - 1) / 2;

        int minX = Math.max(0, centerX - radius);
        int maxX = Math.min(mapWidth - 1, centerX + radius);
        int minY = Math.max(0, centerY - radius);
        int maxY = Math.min(mapHeight - 1, centerY + radius);

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                if (currentFloorIndex == 0 && x == START_X && y == START_Y) continue;
                double dx = x - centerX;
                double dy = y - centerY;
                if (dx * dx + dy * dy <= radius * radius + 0.001) {
                    if (map[y][x] == PORTAL && value != PORTAL) clearPortalAt(currentFloorIndex, x, y);
                    map[y][x] = value;
                }
            }
        }

        if (currentFloorIndex == 0) map[START_Y][START_X] = PATH;
        mapCanvas.repaint();
    }

    private void clearMap() {
        saveHistoryState();
        for (int y = 0; y < mapHeight; y++)
            for (int x = 0; x < mapWidth; x++)
                if (map[y][x] == PORTAL) clearPortalAt(currentFloorIndex, x, y);
        for (int y = 0; y < mapHeight; y++) Arrays.fill(map[y], PATH);
        if (currentFloorIndex == 0) map[START_Y][START_X] = PATH;
        pendingPortal = null;
        mapCanvas.repaint();
    }

    private void resetMap() {
        saveHistoryState();
        mapWidth = DEFAULT_SIZE;
        mapHeight = DEFAULT_SIZE;
        floorMaps.clear();
        floorMaps.add(createMap(mapWidth, mapHeight));
        currentFloorIndex = 0;
        map = floorMaps.getFirst();
        portalLinks.clear();
        pendingPortal = null;
        updateSizeControls();
        updateFloorControls();
        mapCanvas.revalidate();
        mapCanvas.repaint();
    }
    //endregion

    //region Map Validation
    private java.util.List<int[]> findExits() {
        java.util.List<int[]> exits = new ArrayList<>();

        for (int f = 0; f < floorMaps.size(); f++) {
            int[][] floorMap = floorMaps.get(f);
            for (int y = 0; y < mapHeight; y++)
                for (int x = 0; x < mapWidth; x++)
                    if (floorMap[y][x] == FINISH) exits.add(new int[]{f, x, y});
        }
        return exits;
    }

    private int[] findReachableExit() { return MazeSolver.findReachableFinish(floorMaps.toArray(new int[0][][]), buildPortalDataPerFloor(), 0, START_X, START_Y); }
    private String validateMap() {
        int[] blocked = MazeSolver.findBlockedPortalExit(floorMaps.toArray(new int[0][][]), buildPortalDataPerFloor());
        if (blocked != null) return "me.portal_exit_blocked\n(me.floor" + (blocked[0] + 1) + ": " + blocked[1] + ", " + blocked[2] + ")";
        return findReachableExit() == null ? "me.no_path" : null;
    }
    //endregion

    //region Map Saving And Loading
    private void saveMapAndPlayNGG() {
        boolean skipValidation = skipValidationToggle.isSelected();
        if (!skipValidation) {
            String error = validateMap();
            if (error != null) {
                JOptionPane.showMessageDialog(this, error, "me.map_cannot_be_saved", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }
        try {
            Files.createDirectories(AppPaths.DATA_DIR);
            Path file = AppPaths.SAVE_FILE;
            int geometryIndex = wrongGeometryToggle.isSelected() ? GEOMETRY_WRONG : GEOMETRY_EUCLIDEAN;
            writeMap(file, geometryIndex);

            java.util.List<int[]> exits = findExits();
            int[] reachable = findReachableExit();

            String reachableText = reachable == null ? "-" : "(floor " + reachable[0] + ") " + reachable[1] + ", " + reachable[2] + ")";
            String message = "me.map_saved_successfully" + "\n\nme.file\n" + file.toAbsolutePath() + "\n\nme.size" +
                    mapWidth + " x " + mapHeight + "\nme.floors" + floorMaps.size() + "\nme.portals" + portalLinks.size() +
                    "\nme.finishes" + exits.size() + "\nme.reachable_finish" + reachableText;
            JOptionPane.showMessageDialog(this, message, "saved", JOptionPane.INFORMATION_MESSAGE);

            int[][][] floorsArray = floorMaps.toArray(new int[0][][]);
            PortalData[] portalsPerFloor = buildPortalDataPerFloor();
            RCJMS.instance.gameView = new GameView(floorsArray, portalsPerFloor, geometryIndex);
            RCJMS.instance.changeView(RCJMS.instance.gameView, "Raycast Me!");
            RCJMS.instance.gameView.start();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "me.could_not_save_map\n" + e.getMessage(), "save_error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static boolean hasSavedMap() {
        try { return !readMap(AppPaths.SAVE_FILE).isEmpty(); }
        catch (IOException e) { return false; }
    }
    private void updateLoadButton() {
        loadMapButton.setEnabled(hasSavedMap());
    }
    private void loadMapFromSave() {
        java.util.List<int[][]> floors;
        SaveData.Data data;
        try {
            floors = readMap(AppPaths.SAVE_FILE);
            data = SaveData.load();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "me.could_not_load_map\n" + e.getMessage(), "load_error", JOptionPane.ERROR_MESSAGE);
            updateLoadButton();
            return;
        }
        if (floors.isEmpty() || floors.size() > MAX_FLOORS) { updateLoadButton(); return; }

        saveHistoryState();

        int newHeight = floors.getFirst().length;
        int newWidth = floors.getFirst()[0].length;
        floorMaps.clear();
        for (int[][] floor : floors) floorMaps.add(copyMap(floor));
        floorMaps.getFirst()[START_Y][START_X] = PATH;
        mapWidth = newWidth;
        mapHeight = newHeight;
        currentFloorIndex = 0;
        map = floorMaps.getFirst();

        portalLinks.clear();
        if (data.mapPortalLinks != null) {
            for (int[] link : data.mapPortalLinks) {
                if (link.length != 9 && link.length != 11 && link.length != 12) continue;
                int[] normalized = Arrays.copyOf(link, 12);
                if (link.length < 12) normalized[11] = link[0]; // older saves: both ends on the same floor
                if (normalized[0] < 0 || normalized[0] >= floorMaps.size() || normalized[11] < 0 || normalized[11] >= floorMaps.size()) continue;
                if (isInsideMap(normalized[1], normalized[2]) || isInsideMap(normalized[5], normalized[6])) continue;
                portalLinks.add(normalized);
            }
        }
        pendingPortal = null;
        wrongGeometryToggle.setSelected(data.mapGeometryMode == GEOMETRY_WRONG);

        updateSizeControls();
        updateFloorControls();
        bottomInstruction.setText("me.select_a_tool");
        mapCanvas.revalidate();
        mapCanvas.repaint();
    }

    private void writeMap(Path file, int geometryIndex) throws IOException {
        SaveData.saveMap(floorMaps.toArray(new int[0][][]), portalLinks, geometryIndex, NSLocalizedString.getLanguage(), currentStyle, GameView.getRenderScale());
    }

    public static java.util.List<int[][]> readMap(Path file) throws IOException {
        SaveData.Data data = SaveData.load();
        int[][][] floors = data.mapFloors;
        if (floors == null || floors.length == 0) return new ArrayList<>();

        int height = floors[0].length;
        int width = height == 0 ? 0 : floors[0][0].length;
        if (height < MIN_MAP_SIZE || height > MAX_MAP_SIZE) throw new IOException("Map height must be between " + MIN_MAP_SIZE + " and " + MAX_MAP_SIZE);
        if (width < MIN_MAP_SIZE || width > MAX_MAP_SIZE) throw new IOException("Map width must be between " + MIN_MAP_SIZE + " and " + MAX_MAP_SIZE);
        if ((height & 1) == 0) throw new IOException("Map height must be odd.");
        if ((width & 1) == 0) throw new IOException("Map width must be odd.");

        java.util.List<int[][]> result = new ArrayList<>();
        for (int[][] floor : floors) {
            if (floor == null || floor.length != height) throw new IOException("Invalid map: floors have different heights.");
            for (int[] row : floor) {
                if (row.length != width) throw new IOException("Invalid map: rows have different widths.");
                for (int value : row) if (value < PATH || value > PORTAL) throw new IOException("Invalid block value: " + value + ". Expected 0-5.");
            }
            result.add(floor);
        }
        return result;
    }
    //endregion

    //region Seed Maps
    private void loadMapFromSeed() {
        SeedMapBuilder.Options initial = lastSeedOptions != null ? lastSeedOptions
                : new SeedMapBuilder.Options(mapWidth, mapHeight, floorMaps.size(), false, 0, SeedUtil.randomSeed());
        SeedMapBuilder.Options chosen = new SeedMapDialog(this, currentStyle, initial, this::normalizeSize, MAX_FLOORS).showDialog();
        if (chosen == null) return;

        SeedMapBuilder.Result generated = SeedMapBuilder.build(chosen);
        lastSeedOptions = new SeedMapBuilder.Options(chosen.width(), chosen.height(), chosen.floors(), chosen.looped(), chosen.finishMode(), generated.seed());

        saveHistoryState();
        floorMaps.clear();
        for (int[][] floor : generated.floors()) floorMaps.add(copyMap(floor));
        floorMaps.getFirst()[START_Y][START_X] = PATH;
        mapHeight = floorMaps.getFirst().length;
        mapWidth = floorMaps.getFirst()[0].length;
        currentFloorIndex = 0;
        map = floorMaps.getFirst();

        portalLinks.clear();
        for (int[] link : generated.portalLinks()) portalLinks.add(link.clone());
        pendingPortal = null;

        updateSizeControls();
        updateFloorControls();
        bottomInstruction.setText("me.seed_map_loaded");
        mapCanvas.revalidate();
        mapCanvas.repaint();
    }
    //endregion

    //region Undo/Redo
    private void saveHistoryState() {
        if (restoringHistory) return;
        undoHistory.push(new EditorState(floorMaps, currentFloorIndex, portalLinks, mapWidth, mapHeight));
        while (undoHistory.size() > MAX_HISTORY) undoHistory.removeLast();
        redoHistory.clear();
    }
    private void undo() {
        if (undoHistory.isEmpty()) return;
        redoHistory.push(new EditorState(floorMaps, currentFloorIndex, portalLinks, mapWidth, mapHeight));
        while (redoHistory.size() > MAX_HISTORY) redoHistory.removeLast();
        restoreState(undoHistory.pop());
    }
    private void redo() {
        if (redoHistory.isEmpty()) return;
        undoHistory.push(new EditorState(floorMaps, currentFloorIndex, portalLinks, mapWidth, mapHeight));
        while (undoHistory.size() > MAX_HISTORY) undoHistory.removeLast();
        restoreState(redoHistory.pop());
    }
    private void restoreState(EditorState state) {
        restoringHistory = true;
        floorMaps.clear();
        for (int[][] floor : state.floors()) floorMaps.add(copyMap(floor));
        currentFloorIndex = Math.clamp(state.currentFloor(), 0, floorMaps.size() - 1);
        map = floorMaps.get(currentFloorIndex);
        portalLinks.clear();
        for (int[] link : state.portals()) portalLinks.add(link.clone());
        pendingPortal = null;
        mapWidth = state.width();
        mapHeight = state.height();
        updateSizeControls();
        updateFloorControls();
        mapCanvas.revalidate();
        mapCanvas.repaint();
        restoringHistory = false;
    }
    private void setupKeyBinds() {
        InputMap inputMap = getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        ActionMap actionMap = getActionMap();

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK), "mapEditorUndo");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK), "mapEditorRedo");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, 0), "mapEditorFloorUp");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, 0), "mapEditorFloorDown");
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "mapEditorCancel");
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_R, 0), "mapEditorRotatePortal");
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK), "mapEditorSave");
        actionMap.put("mapEditorSave", new AbstractAction() {@Override public void actionPerformed(ActionEvent e) {saveMapAndPlayNGG();}});

        InputMap windowMap = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        bindHotkey(windowMap, actionMap, KeyEvent.VK_V, brushToolButton::doClick);
        bindHotkey(windowMap, actionMap, KeyEvent.VK_B, eraserToolButton::doClick);
        bindHotkey(windowMap, actionMap, KeyEvent.VK_N, portalToolButton::doClick);
        bindHotkey(windowMap, actionMap, KeyEvent.VK_M, portalOneSidedToolButton::doClick);
        bindHotkey(windowMap, actionMap, KeyEvent.VK_1, pathButton::doClick);
        bindHotkey(windowMap, actionMap, KeyEvent.VK_2, wallButton::doClick);
        bindHotkey(windowMap, actionMap, KeyEvent.VK_3, finishButton::doClick);
        bindHotkey(windowMap, actionMap, KeyEvent.VK_4, stairsUpButton::doClick);
        bindHotkey(windowMap, actionMap, KeyEvent.VK_5, stairsDownButton::doClick);
        bindHotkey(windowMap, actionMap, KeyEvent.VK_G, () -> wrongGeometryToggle.setSelected(!wrongGeometryToggle.isSelected()));

        actionMap.put("mapEditorUndo", new AbstractAction() {@Override public void actionPerformed(ActionEvent e) {undo();}});
        actionMap.put("mapEditorRedo", new AbstractAction() {@Override public void actionPerformed(ActionEvent e) {redo();}});
        actionMap.put("mapEditorFloorUp", new AbstractAction() {@Override public void actionPerformed(ActionEvent e) {switchFloor(currentFloorIndex + 1);}});
        actionMap.put("mapEditorFloorDown", new AbstractAction() {@Override public void actionPerformed(ActionEvent e) {switchFloor(currentFloorIndex - 1);}});
        actionMap.put("mapEditorRotatePortal", new AbstractAction() {@Override public void actionPerformed(ActionEvent e) {
            if (KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner() instanceof javax.swing.text.JTextComponent) return;
            rotatePortalDirection();
        }});
        actionMap.put("mapEditorCancel", new AbstractAction() {@Override public void actionPerformed(ActionEvent e) {
            if (pendingPortal != null) { pendingPortal = null; bottomInstruction.setText("me.select_a_tool"); mapCanvas.repaint(); }
            else exitToMainMenu();
        }});
    }
    private void bindHotkey(InputMap inputMap, ActionMap actionMap, int keyCode, Runnable action) {
        String name = "mapEditorHotkey" + keyCode;
        inputMap.put(KeyStroke.getKeyStroke(keyCode, 0), name);
        actionMap.put(name, new AbstractAction() {@Override public void actionPerformed(ActionEvent e) {
            if (KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner() instanceof javax.swing.text.JTextComponent) return;
            action.run();
        }});
    }
    private void pickBlockFromCell(int x, int y) {
        if (isInsideMap(x, y)) return;
        AbstractButton button = switch (map[y][x]) {
            case PATH -> pathButton;
            case WALL -> wallButton;
            case FINISH -> finishButton;
            case STAIRS_UP -> stairsUpButton;
            case STAIRS_DOWN -> stairsDownButton;
            default -> null;
        };
        if (button != null) button.doClick();
    }
    //endregion

    //region Helpers
    private void buildCenter() {
        JPanel center = new JPanel(new BorderLayout());
        center.setOpaque(false);
        center.add(mapCanvas, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);
    }

    private void buildBottomMenu() {
        JPanel bottom = new JPanel();
        bottom.setOpaque(false);
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));

        bottomInstruction.setForeground(Color.WHITE);
        bottomInstruction.setAlignmentX(Component.RIGHT_ALIGNMENT);

        JPanel testRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 2));
        testRow.setOpaque(false);
        wrongGeometryToggle.setPreferredSize(new Dimension(220, 32));
        skipValidationToggle.setPreferredSize(new Dimension(190, 32));
        testRow.add(wrongGeometryToggle);
        testRow.add(skipValidationToggle);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        buttons.setOpaque(false);

        resetButton.addActionListener(e -> resetMap());
        loadMapButton.addActionListener(e -> loadMapFromSave());
        loadSeedButton.addActionListener(e -> loadMapFromSeed());
        playMapButton.addActionListener(e -> saveMapAndPlayNGG());

        buttons.add(loadMapButton);
        buttons.add(loadSeedButton);
        buttons.add(resetButton);
        buttons.add(playMapButton);

        bottom.add(bottomInstruction);
        bottom.add(Box.createVerticalStrut(4));
        bottom.add(testRow);
        bottom.add(Box.createVerticalStrut(4));
        bottom.add(buttons);

        add(bottom, BorderLayout.SOUTH);
    }

    private Style loadTheme() { return SaveData.load().theme; }
    //endregion

    //region Nested Types
    private enum Tool {BRUSH, ERASER, PORTAL, PORTAL_ONE_SIDED}

    private record EditorState(java.util.List<int[][]> floors, int currentFloor, java.util.List<int[]> portals, int width, int height) {
        private EditorState(java.util.List<int[][]> floors, int currentFloor, java.util.List<int[]> portals, int width, int height) {
            java.util.List<int[][]> copiedFloors = new ArrayList<>();
            for (int[][] floor : floors) copiedFloors.add(copyMap(floor));
            this.floors = copiedFloors;
            this.currentFloor = currentFloor;
            java.util.List<int[]> copiedPortals = new ArrayList<>();
            for (int[] link : portals) copiedPortals.add(link.clone());
            this.portals = copiedPortals;
            this.width = width;
            this.height = height;
        }
    }

    private class MapCanvas extends JPanel {
        private static final double MIN_ZOOM = 1.0, MAX_ZOOM = 32.0, ZOOM_STEP_BASE = 1.15;
        private static final int PAN_STEP_PIXELS = 40;
        private static final Color PATH_COLOR = new Color(45, 45, 50), WALL_COLOR = new Color(225, 225, 230), FINISH_COLOR = new Color(80, 200, 100),
                STAIRS_UP_COLOR = new Color(70, 140, 255), STAIRS_DOWN_COLOR = new Color(255, 150, 60), PORTAL_COLOR = new Color(190, 90, 230);

        private boolean pipetteDrag = false, panDrag = false;
        private double gridX, gridY, gridSize = 1, baseGridSize = 1;
        private double zoom = MIN_ZOOM, panGridX = 0, panGridY = 0;
        private int panLastX, panLastY, lastMapWidth = -1, lastMapHeight = -1;

        MapCanvas() {
            setOpaque(true);
            setBackground(new Color(18, 18, 21));
            setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY, 2));
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            setFocusable(true);

            MouseAdapter mouseHandler = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    requestFocusInWindow();
                    if (SwingUtilities.isMiddleMouseButton(e)) { panDrag = true; panLastX = e.getX(); panLastY = e.getY(); return; }
                    pipetteDrag = SwingUtilities.isRightMouseButton(e) && e.isAltDown();
                    if (pipetteDrag) {
                        if (gridSize > 0) pickBlockFromCell(cellAt(e.getX(), gridX), cellAt(e.getY(), gridY));
                        return;
                    }
                    if (currentTool == Tool.BRUSH || currentTool == Tool.ERASER) saveHistoryState();
                    handlePointer(e.getX(), e.getY());}
                @Override public void mouseReleased(MouseEvent e) {pipetteDrag = false; panDrag = false;}
                @Override public void mouseDragged(MouseEvent e) {
                    if (panDrag) {
                        panGridX = gridX + (e.getX() - panLastX);
                        panGridY = gridY + (e.getY() - panLastY);
                        panLastX = e.getX(); panLastY = e.getY();
                        repaint();
                        return;
                    }
                    if (!pipetteDrag && (currentTool == Tool.BRUSH || currentTool == Tool.ERASER)) handlePointer(e.getX(), e.getY());
                }
                @Override public void mouseEntered(MouseEvent e) { requestFocusInWindow(); }
            };
            addMouseListener(mouseHandler);
            addMouseMotionListener(mouseHandler);
            addMouseWheelListener(this::handleMouseWheel);
            setupPanKeyBindings();
        }

        private int cellAt(int pixel, double origin) { return (int) Math.floor((pixel - origin) / Math.max(0.0001, gridSize)); }
        private int cellStart(double origin, int cell) { return (int) Math.round(origin + cell * gridSize); }
        private void handleMouseWheel(MouseWheelEvent e) {
            if (!e.isControlDown()) return;
            requestFocusInWindow();
            double newZoom = Math.clamp(zoom * Math.pow(ZOOM_STEP_BASE, -e.getPreciseWheelRotation()), MIN_ZOOM, MAX_ZOOM);
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
                    panGridX = gridX + dx;
                    panGridY = gridY + dy;
                    repaint();
                }
            };
        }
        private double clampAxis(double desired, double contentSize, int viewportSize) {
            if (contentSize <= viewportSize) return (viewportSize - contentSize) / 2.0;
            return Math.clamp(desired, viewportSize - contentSize, 0);
        }

        private void handlePointer(int px, int py) {
            if (gridSize <= 0) return;
            int x = cellAt(px, gridX);
            int y = cellAt(py, gridY);
            if (isInsideMap(x, y)) return;

            switch (currentTool) {
                case BRUSH -> paintCells(x, y, brushSizeSlider.getValue(), selectedBlock);
                case ERASER -> paintCells(x, y, eraserSizeSlider.getValue(), PATH);
                case PORTAL -> handlePortalClick(x, y, false);
                case PORTAL_ONE_SIDED -> handlePortalClick(x, y, true);
            }
        }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            int width = getWidth();
            int height = getHeight();
            if (width <= 0 || height <= 0) return;

            if (mapWidth != lastMapWidth || mapHeight != lastMapHeight) {
                lastMapWidth = mapWidth; lastMapHeight = mapHeight;
                zoom = MIN_ZOOM; panGridX = panGridY = 0;
            }

            int availableWidth = Math.max(1, width - 40);
            int availableHeight = Math.max(1, height - 40);

            baseGridSize = Math.max(0.0001, Math.min((double) availableWidth / mapWidth, (double) availableHeight / mapHeight));
            gridSize = baseGridSize * zoom;

            gridX = clampAxis(panGridX, gridSize * mapWidth, width);
            gridY = clampAxis(panGridY, gridSize * mapHeight, height);
            panGridX = gridX;
            panGridY = gridY;

            drawMap(g);
            drawGrid(g);
            drawStart(g);
            drawPendingPortal(g);
            drawPortalArrows(g);
            drawMapSize(g);
        }
        private int firstVisible(double origin) { return Math.max(0, (int) Math.floor((0 - origin) / gridSize)); }
        private int lastVisible(double origin, int viewport, int count) { return Math.min(count - 1, (int) Math.floor((viewport - origin) / gridSize)); }
        private void drawMap(Graphics g) {
            int x0 = firstVisible(gridX), x1 = lastVisible(gridX, getWidth(), mapWidth);
            int y0 = firstVisible(gridY), y1 = lastVisible(gridY, getHeight(), mapHeight);
            for (int y = y0; y <= y1; y++) {
                int top = cellStart(gridY, y), bottom = cellStart(gridY, y + 1);
                for (int x = x0; x <= x1; x++) {
                    Color color = switch (map[y][x]) {
                        case PATH -> PATH_COLOR;
                        case WALL -> WALL_COLOR;
                        case FINISH -> FINISH_COLOR;
                        case STAIRS_UP -> STAIRS_UP_COLOR;
                        case STAIRS_DOWN -> STAIRS_DOWN_COLOR;
                        case PORTAL -> PORTAL_COLOR;
                        default -> Color.MAGENTA;
                    };
                    g.setColor(color);
                    int left = cellStart(gridX, x);
                    g.fillRect(left, top, Math.max(1, cellStart(gridX, x + 1) - left), Math.max(1, bottom - top));
                }
            }
        }
        private void drawGrid(Graphics g) {
            if (gridSize < 3) return;
            g.setColor(new Color(15, 15, 18));
            int x0 = firstVisible(gridX), x1 = lastVisible(gridX, getWidth(), mapWidth) + 1;
            int y0 = firstVisible(gridY), y1 = lastVisible(gridY, getHeight(), mapHeight) + 1;
            int top = cellStart(gridY, y0), bottom = cellStart(gridY, y1), left = cellStart(gridX, x0), right = cellStart(gridX, x1);
            for (int x = x0; x <= x1; x++) { int px = cellStart(gridX, x); g.drawLine(px, top, px, bottom); }
            for (int y = y0; y <= y1; y++) { int py = cellStart(gridY, y); g.drawLine(left, py, right, py); }
        }
        private void drawStart(Graphics g) {
            if (currentFloorIndex != 0) return;
            g.setColor(new Color(50, 120, 255));
            int padding = Math.max(1, (int) (gridSize / 5));
            int size = Math.max(1, (int) gridSize - padding * 2);
            g.fillRect(cellStart(gridX, START_X) + padding, cellStart(gridY, START_Y) + padding, size, size);
        }
        private void drawPendingPortal(Graphics g) {
            if (pendingPortal == null || pendingPortal[0] != currentFloorIndex) return;
            g.setColor(new Color(230, 220, 60));
            int px = pendingPortal[1], py = pendingPortal[2];
            int border = gridSize >= 3 ? 2 : 1;
            for (int i = 0; i < border; i++)
                g.drawRect(cellStart(gridX, px) + i, cellStart(gridY, py) + i, Math.max(1, (int) gridSize - i * 2 - 1), Math.max(1, (int) gridSize - i * 2 - 1));
        }
        private void drawPortalArrows(Graphics g) {
            if (gridSize < 5) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            for (int[] link : portalLinks) {
                if (link[0] == currentFloorIndex) {
                    drawArrow(g2, link[1], link[2], link[3], link[4], Color.WHITE);
                    if (link[9] != 0) drawOneSidedFace(g2, link[1], link[2], link[3], link[4]);
                    if (link[11] != link[0]) drawFloorTag(g2, link[1], link[2], link[11]);
                }
                if (link[11] == currentFloorIndex) {
                    drawArrow(g2, link[5], link[6], link[7], link[8], Color.WHITE);
                    if (link[10] != 0) drawOneSidedFace(g2, link[5], link[6], link[7], link[8]);
                    if (link[0] != link[11]) drawFloorTag(g2, link[5], link[6], link[0]);
                }
            }
            if (pendingPortal != null && pendingPortal[0] == currentFloorIndex) {
                drawArrow(g2, pendingPortal[1], pendingPortal[2], pendingPortal[3], pendingPortal[4], new Color(230, 220, 60));
                if (pendingPortal[5] != 0) drawOneSidedFace(g2, pendingPortal[1], pendingPortal[2], pendingPortal[3], pendingPortal[4]);
            }
            g2.dispose();
        }
        private void drawFloorTag(Graphics2D g2, int x, int y, int otherFloor) {
            if (gridSize < 12) return;
            g2.setFont(g2.getFont().deriveFont(Font.BOLD, (float) Math.max(9f, gridSize * 0.38f)));
            String text = String.valueOf(otherFloor + 1);
            g2.setColor(new Color(15, 15, 18));
            g2.drawString(text, (float) (gridX + x * gridSize + 2 + 1), (float) (gridY + y * gridSize + g2.getFontMetrics().getAscent() + 1));
            g2.setColor(new Color(255, 235, 120));
            g2.drawString(text, (float) (gridX + x * gridSize + 2), (float) (gridY + y * gridSize + g2.getFontMetrics().getAscent()));
        }
        private void drawOneSidedFace(Graphics2D g2, int x, int y, int dx, int dy) {
            double cx = gridX + x * gridSize + gridSize / 2.0, cy = gridY + y * gridSize + gridSize / 2.0;
            double half = gridSize / 2.0;
            double ex = cx - dx * half, ey = cy - dy * half;
            double px = -dy * half, py = dx * half;
            g2.setColor(new Color(80, 220, 230));
            g2.setStroke(new BasicStroke((float) Math.max(2f, gridSize / 8f)));
            g2.draw(new java.awt.geom.Line2D.Double(ex - px, ey - py, ex + px, ey + py));
            g2.setStroke(new BasicStroke(1f));
        }
        private void drawArrow(Graphics2D g2, int x, int y, int dx, int dy, Color color) {
            double cx = gridX + x * gridSize + gridSize / 2.0, cy = gridY + y * gridSize + gridSize / 2.0;
            double s = gridSize * 0.32;
            java.awt.geom.Path2D.Double arrow = new java.awt.geom.Path2D.Double();
            arrow.moveTo(cx + dx * s, cy + dy * s);
            arrow.lineTo(cx - dx * s * 0.7 + (-dy) * s * 0.8, cy - dy * s * 0.7 + (double) dx * s * 0.8);
            arrow.lineTo(cx - dx * s * 0.7 - (-dy) * s * 0.8, cy - dy * s * 0.7 - (double) dx * s * 0.8);
            arrow.closePath();
            g2.setColor(color);
            g2.fill(arrow);
            g2.setColor(new Color(15, 15, 18));
            g2.draw(arrow);
        }
        private void drawMapSize(Graphics g) {
            g.setColor(Color.WHITE);
            g.setFont(g.getFont().deriveFont(Font.BOLD, 14f));

            String text = mapWidth + " x " + mapHeight;
            if (zoom > MIN_ZOOM + 0.001) text += String.format(" (%.0f%%)", zoom * 100);
            FontMetrics fm = g.getFontMetrics();

            int x = (getWidth() - fm.stringWidth(text)) / 2;
            int y = Math.max(18, (int) gridY - 8);

            g.drawString(text, x, y);
        }
    }
    //endregion
}