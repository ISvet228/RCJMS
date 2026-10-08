import EditorUI.*;
import Helpers.*;
import StyleUI.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Path2D;
import java.io.*;
import java.nio.file.*;
import java.util.*;

import static EditorUI.EditorKit.*;

public class MapEditorView extends JPanel {
    //region Variables
    private final Style currentStyle = loadTheme();

    private static final int MIN_MAP_SIZE = 5, MAX_MAP_SIZE = 201;
    private static final int MIN_TOOL_SIZE = 1, MAX_TOOL_SIZE = 15;
    private static final int MAX_FLOORS = 20;
    private static final int PATH = 0, WALL = 1, FINISH = 2, STAIRS_UP = 3, STAIRS_DOWN = 4, PORTAL = 5;
    private static final int DEFAULT_SIZE = 25;
    private static final int START_X = 1, START_Y = 1;
    private static final int LEFT_WIDTH = 176, RIGHT_WIDTH = 208;
    private static final int[] SIZE_PRESETS = {15, 25, 51, 101};
    private static final int[][] PORTAL_DIRS = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
    private static final double[] PORTAL_ROTATIONS = {-Math.PI / 2, 0, Math.PI / 2, Math.PI};
    private static final int GEOMETRY_EUCLIDEAN = 0, GEOMETRY_WRONG = 1;
    private static final Color PATH_COLOR = new Color(45, 45, 50), WALL_COLOR = new Color(225, 225, 230), FINISH_COLOR = new Color(80, 200, 100),
            STAIRS_UP_COLOR = new Color(70, 140, 255), STAIRS_DOWN_COLOR = new Color(255, 150, 60), PORTAL_COLOR = new Color(190, 90, 230);
    private static final String[] BLOCK_NAME_KEYS = {"me.name_path", "me.name_wall", "me.name_finish", "me.name_up", "me.name_down", "me.name_portal"};

    private int mapWidth = DEFAULT_SIZE, mapHeight = DEFAULT_SIZE;
    private final java.util.List<int[][]> floorMaps = createInitialFloorList();
    private int currentFloorIndex = 0;
    private int[][] map = floorMaps.getFirst();
    private final java.util.List<int[]> portalLinks = new ArrayList<>();
    private int[] pendingPortal = null;
    private int portalDirIndex = 1;
    private Tool currentTool = Tool.BRUSH;
    private int selectedBlock = WALL;
    private int brushSize = 1, eraserSize = 1;
    private boolean dirty = false;
    private SeedMapBuilder.Options lastSeedOptions = null;

    private final Deque<EditorState> undoHistory = new ArrayDeque<>(), redoHistory = new ArrayDeque<>();
    private EditorState strokeBase;
    private boolean strokePushed;
    private boolean restoringHistory = false, updatingControls = false;

    private final MapCanvas canvas = new MapCanvas();
    private final FloorStrip floorStrip = new FloorStrip();
    private final HintBar hintBar = new HintBar();
    private final Text hoverText = new Text(null, 12f, true, TEXT, 2);
    private final Text dirtyText = new Text(null, 12f, true, WARN, 0);
    private final Text sizeValue = new Text(null, 12f, true, TEXT, 2);
    private final Note checkNote = new Note(RIGHT_WIDTH - 32, 12f, MUTED);
    private final javax.swing.Timer checkTimer = new javax.swing.Timer(250, e -> runCheck());

    private final ToolButton undoButton = new ToolButton(currentStyle, ToolButton.Layout.ICON, Glyph.UNDO, null).tip("ed.undo", "Ctrl+Z");
    private final ToolButton redoButton = new ToolButton(currentStyle, ToolButton.Layout.ICON, Glyph.REDO, null).tip("ed.redo", "Ctrl+Y");
    private final ToolButton[] toolButtons = new ToolButton[4];
    private final ToolButton[] blockButtons = new ToolButton[7]; //path, wall, finish, up, down, portal, one-sided portal
    private final ToolButton portalDirButton = new ToolButton(currentStyle, ToolButton.Layout.ROW, Glyph.ARROW, "me.portal_direction_short").badge("R").tip("me.portal_direction_tip", "R");
    private final ToolButton loadButton = new ToolButton(currentStyle, ToolButton.Layout.TILE, Glyph.FOLDER, "me.load_short").tip("me.load_tip", null);
    private final StyledSlider sizeSlider = new StyledSlider(currentStyle, MIN_TOOL_SIZE, MAX_TOOL_SIZE, 1);
    private final StyledTextField widthField = new StyledTextField(currentStyle, String.valueOf(DEFAULT_SIZE));
    private final StyledTextField heightField = new StyledTextField(currentStyle, String.valueOf(DEFAULT_SIZE));
    private final ToolButton[] presetButtons = new ToolButton[SIZE_PRESETS.length];
    private final StyledToggle wrongGeometryToggle = new StyledToggle(currentStyle, "mm.wrong_geometry");
    private final StyledToggle skipValidationToggle = new StyledToggle(currentStyle, "me.test_anyway");
    //endregion

    //region Constructors
    public MapEditorView() {
        setLayout(new BorderLayout(10, 8));
        setBorder(new EmptyBorder(10, 10, 8, 10));
        setBackground(BG);

        add(buildHeader(), BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);
        setupKeyBinds();

        checkTimer.setRepeats(false);
        canvas.setGrid(mapWidth, mapHeight);
        updateAll();
    }
    //endregion

    //region Map Utilities
    private static int[][] createMap(int width, int height) {
        int[][] result = new int[height][width];
        for (int y = 0; y < height; y++) Arrays.fill(result[y], PATH);
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
    private boolean isOutside(int x, int y) { return x < 0 || y < 0 || x >= mapWidth || y >= mapHeight; }
    private boolean floorHasContent() {
        for (int[] row : map) for (int v : row) if (v != PATH) return true;
        return false;
    }
    private boolean mapHasContent() {
        if (floorMaps.size() > 1 || !portalLinks.isEmpty()) return true;
        return floorHasContent();
    }
    //endregion

    //region Map Resizing
    private void resizeMap(int newWidth, int newHeight) {
        int wantedWidth = newWidth, wantedHeight = newHeight;
        newWidth = normalizeSize(newWidth);
        newHeight = normalizeSize(newHeight);
        if (newWidth != wantedWidth || newHeight != wantedHeight) hintBar.flash(Tone.INFO, fmt("me.size_odd", newWidth, newHeight));

        if (newWidth == mapWidth && newHeight == mapHeight) { updateSizeControls(); return; }
        saveHistoryState();

        int oldWidth = mapWidth, oldHeight = mapHeight;
        for (int f = 0; f < floorMaps.size(); f++) {
            int[][] oldFloor = floorMaps.get(f);
            int[][] newFloor = createMap(newWidth, newHeight);
            int copyWidth = Math.min(oldWidth, newWidth), copyHeight = Math.min(oldHeight, newHeight);
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
            boolean aOut = link[1] >= mapWidth || link[2] >= mapHeight, bOut = link[5] >= mapWidth || link[6] >= mapHeight;
            if (!aOut && !bOut) continue;
            linkIt.remove();
            if (!aOut) floorMaps.get(link[0])[link[2]][link[1]] = PATH;
            if (!bOut) floorMaps.get(link[11])[link[6]][link[5]] = PATH;
        }
        pendingPortal = null;
        markDirty();
        syncCanvasSize();
        updateAll();
    }
    private void syncCanvasSize() { if (canvas.getCols() != mapWidth || canvas.getRows() != mapHeight) canvas.setGrid(mapWidth, mapHeight); }
    private void updateSizeControls() {
        updatingControls = true;
        widthField.setText(String.valueOf(mapWidth));
        heightField.setText(String.valueOf(mapHeight));
        for (int i = 0; i < presetButtons.length; i++) presetButtons[i].setSelected(mapWidth == SIZE_PRESETS[i] && mapHeight == SIZE_PRESETS[i]);
        updatingControls = false;
    }
    private void applyFields() {
        if (updatingControls) return;
        try { resizeMap(Integer.parseInt(widthField.getText().trim()), Integer.parseInt(heightField.getText().trim())); }
        catch (NumberFormatException ignored) { updateSizeControls(); }
    }
    //endregion

    //region Floors
    private void switchFloor(int index) {
        index = Math.clamp(index, 0, floorMaps.size() - 1);
        if (index == currentFloorIndex) return;
        currentFloorIndex = index;
        map = floorMaps.get(currentFloorIndex);
        updateFloorControls();
        updateHint();
        canvas.repaint();
    }
    private void addFloor() {
        if (floorMaps.size() >= MAX_FLOORS) { hintBar.flash(Tone.WARN, fmt("me.max_floors", MAX_FLOORS)); return; }
        saveHistoryState();
        floorMaps.add(createMap(mapWidth, mapHeight));
        currentFloorIndex = floorMaps.size() - 1;
        map = floorMaps.get(currentFloorIndex);
        markDirty();
        updateAll();
        hintBar.flash(Tone.OK, fmt("me.floor_added", currentFloorIndex + 1));
    }
    private void removeFloor() {
        if (floorMaps.size() <= 1) return;
        if (floorHasContent() && ask(this, currentStyle, "ed.confirm_title", fmt("me.remove_floor_confirm", currentFloorIndex + 1), 1, "me.remove_floor_yes", "cancel") != 0) return;
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
        markDirty();
        updateAll();
        hintBar.flash(Tone.INFO, L("me.floor_removed"));
    }
    private void updateFloorControls() {
        floorStrip.update(floorMaps.size(), currentFloorIndex);
        boolean canGoUp = currentFloorIndex < floorMaps.size() - 1, canGoDown = currentFloorIndex > 0;
        blockButtons[3].setEnabled(canGoUp);
        blockButtons[4].setEnabled(canGoDown);
        if ((selectedBlock == STAIRS_UP && !canGoUp) || (selectedBlock == STAIRS_DOWN && !canGoDown)) selectedBlock = WALL;
        syncSelection();
    }
    //endregion

    //region Portals
    private void rotatePortalDirection() {
        portalDirIndex = (portalDirIndex + 1) % PORTAL_DIRS.length;
        portalDirButton.setIconRotation(PORTAL_ROTATIONS[portalDirIndex]);
        canvas.repaint();
    }
    private boolean portalToolActive() { return currentTool == Tool.PORTAL || currentTool == Tool.PORTAL_ONE; }
    private void handlePortalClick(int x, int y, boolean oneSided) {
        if (isOutside(x, y)) return;
        if (currentFloorIndex == 0 && x == START_X && y == START_Y) { hintBar.flash(Tone.WARN, L("me.portal_not_on_start")); return; }
        if (map[y][x] == PORTAL) { hintBar.flash(Tone.WARN, L("me.portal_already")); return; }
        int[] dir = PORTAL_DIRS[portalDirIndex];

        if (pendingPortal == null) {
            pendingPortal = new int[]{currentFloorIndex, x, y, dir[0], dir[1], oneSided ? 1 : 0};
            updateHint();
            canvas.repaint();
            return;
        }
        if (pendingPortal[0] == currentFloorIndex && pendingPortal[1] == x && pendingPortal[2] == y) { pendingPortal = null; updateHint(); canvas.repaint(); return; }

        saveHistoryState();
        int[] anchor = pendingPortal;
        portalLinks.add(new int[]{anchor[0], anchor[1], anchor[2], anchor[3], anchor[4], x, y, dir[0], dir[1], anchor[5], oneSided ? 1 : 0, currentFloorIndex});
        floorMaps.get(anchor[0])[anchor[2]][anchor[1]] = PORTAL;
        map[y][x] = PORTAL;
        pendingPortal = null;
        markDirty();
        updateHint();
        scheduleCheck();
        hintBar.flash(Tone.OK, L("me.portal_linked"));
        canvas.repaint();
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
            if (link[0] == floor && link[1] == x && link[2] == y) { it.remove(); revertPortalCell(link[11], link[5], link[6], -1); return; }
            if (link[11] == floor && link[5] == x && link[6] == y) { it.remove(); revertPortalCell(link[0], link[1], link[2], -1); return; }
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

    //region UI Building
    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setOpaque(false);

        ToolButton back = new ToolButton(currentStyle, ToolButton.Layout.ROW, Glyph.BACK, "ed.back").tip("ed.back", "Esc").size(104, 36);
        back.onClick(this::exitToMainMenu);
        Text title = new Text("me.map_editor", 18f, true, TEXT, 0);
        title.setPreferredSize(new Dimension(200, 36));
        dirtyText.setPreferredSize(new Dimension(150, 36));
        JPanel left = row(10, back, title, dirtyText);
        left.setPreferredSize(new Dimension(480, 36));

        ToolButton helpButton = new ToolButton(currentStyle, ToolButton.Layout.ICON, Glyph.HELP, null).tip("ed.help", "F1").size(36, 36);
        helpButton.onClick(this::showHelp);
        ToolButton saveButton = new ToolButton(currentStyle, ToolButton.Layout.ROW, Glyph.SAVE, "te.save").tip("me.save_tip", "Ctrl+S").accent(new Color(46, 150, 96)).size(130, 36);
        saveButton.onClick(this::saveMapOnly);
        undoButton.size(36, 36).onClick(this::undo);
        redoButton.size(36, 36).onClick(this::redo);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.setOpaque(false);
        right.add(undoButton);
        right.add(redoButton);
        right.add(helpButton);
        right.add(saveButton);
        header.add(left, BorderLayout.WEST);
        header.add(right, BorderLayout.CENTER);
        return header;
    }

    private JComponent buildBody() {
        JPanel body = new JPanel(new BorderLayout(10, 0));
        body.setOpaque(false);
        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setOpaque(false);
        floorStrip.setPreferredSize(new Dimension(100, 32));
        center.add(floorStrip, BorderLayout.NORTH);
        center.add(canvas, BorderLayout.CENTER);
        canvas.setMinimumSize(new Dimension(120, 120));
        body.add(sidebar(buildLeft(), LEFT_WIDTH), BorderLayout.WEST);
        body.add(center, BorderLayout.CENTER);
        body.add(sidebar(buildRight(), RIGHT_WIDTH), BorderLayout.EAST);
        return body;
    }

    private JComponent sidebar(Column column, int width) {
        ScrollBody holder = new ScrollBody();
        holder.add(column, BorderLayout.NORTH);
        StyledScrollPane scroll = new StyledScrollPane(holder, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER, currentStyle);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setPreferredSize(new Dimension(width, 100));
        return scroll;
    }

    private Column buildLeft() {
        Column column = new Column();
        column.setBorder(new EmptyBorder(0, 0, 0, 6));

        Card tools = new Card("tool");
        JPanel toolRow = new JPanel(new GridLayout(1, 4, 4, 0));
        toolRow.setOpaque(false);
        toolRow.setPreferredSize(new Dimension(100, 38));
        Glyph[] glyphs = {Glyph.BRUSH, Glyph.ERASER, Glyph.BOX, Glyph.FILL};
        String[] tips = {"me.tip_brush", "me.tip_eraser", "me.tip_box", "me.tip_fill"}, keys = {"V", "B", "X", "F"};
        Tool[] order = {Tool.BRUSH, Tool.ERASER, Tool.BOX, Tool.FILL};
        for (int i = 0; i < 4; i++) {
            final Tool t = order[i];
            toolButtons[i] = new ToolButton(currentStyle, ToolButton.Layout.ICON, glyphs[i], null).tip(tips[i], keys[i]);
            toolButtons[i].onClick(() -> selectTool(t));
            toolRow.add(toolButtons[i]);
        }
        tools.put(toolRow, 0);
        sizeSlider.setPreferredSize(new Dimension(100, 22));
        sizeSlider.setOpaque(false);
        sizeSlider.addChangeListener(e -> {
            if (updatingControls) return;
            if (currentTool == Tool.ERASER) eraserSize = sizeSlider.getValue(); else brushSize = sizeSlider.getValue();
            sizeValue.setRaw(String.valueOf(sizeSlider.getValue()));
            canvas.repaint();
        });
        sizeValue.setPreferredSize(new Dimension(22, 22));
        JPanel sizeRow = new JPanel(new BorderLayout(6, 0));
        sizeRow.setOpaque(false);
        sizeRow.add(new Text("ed.size", 12f, true, MUTED, 0), BorderLayout.WEST);
        sizeRow.add(sizeSlider, BorderLayout.CENTER);
        sizeRow.add(sizeValue, BorderLayout.EAST);
        sizeRow.setPreferredSize(new Dimension(100, 24));
        tools.put(sizeRow, 8);
        column.put(tools, 0);

        Card blocks = new Card("me.block");
        Color dark = new Color(20, 20, 26);
        Object[][] defs = {
                {PATH_COLOR, Glyph.PATH, new Color(150, 150, 160), "me.name_path", "1", "me.tip_path"},
                {WALL_COLOR, null, null, "me.name_wall", "2", "me.tip_wall"},
                {FINISH_COLOR, Glyph.FLAG, new Color(15, 60, 30), "me.name_finish", "3", "me.tip_finish"},
                {STAIRS_UP_COLOR, Glyph.UP, Color.WHITE, "me.name_up", "4", "me.tip_up"},
                {STAIRS_DOWN_COLOR, Glyph.DOWN, Color.WHITE, "me.name_down", "5", "me.tip_down"},
                {PORTAL_COLOR, Glyph.PORTAL, Color.WHITE, "me.name_portal", "N", "me.tip_portal"},
                {PORTAL_COLOR, Glyph.PORTAL_ONE, Color.WHITE, "me.name_portal_one", "M", "me.tip_portal_one"}};
        for (int i = 0; i < defs.length; i++) {
            final int index = i;
            Object[] d = defs[i];
            blockButtons[i] = new ToolButton(currentStyle, ToolButton.Layout.ROW, null, (String) d[3]).swatch((Color) d[0], (Glyph) d[1], (Color) d[2]).badge((String) d[4]).tip((String) d[5], (String) d[4]);
            blockButtons[i].size(100, 26);
            blockButtons[i].onClick(() -> chooseBlockRow(index));
            blocks.put(blockButtons[i], i == 0 ? 0 : 2);
        }
        column.put(blocks, 6);

        portalDirButton.size(100, 28);
        portalDirButton.setIconRotation(PORTAL_ROTATIONS[portalDirIndex]);
        portalDirButton.onClick(this::rotatePortalDirection);
        column.put(portalDirButton, 6);
        return column;
    }

    private Column buildRight() {
        Column column = new Column();
        column.setBorder(new EmptyBorder(0, 6, 0, 0));

        Card size = new Card("me.map_size");
        size.put(stepperRow("width", widthField, -2), 0);
        size.put(stepperRow("height", heightField, -2), 3);
        JPanel presets = new JPanel(new GridLayout(1, SIZE_PRESETS.length, 3, 0));
        presets.setOpaque(false);
        presets.setPreferredSize(new Dimension(100, 26));
        for (int i = 0; i < SIZE_PRESETS.length; i++) {
            final int value = SIZE_PRESETS[i];
            presetButtons[i] = new ToolButton(currentStyle, ToolButton.Layout.TEXT, null, null).tip("me.preset_tip", null);
            presetButtons[i].setLabel(String.valueOf(value));
            presetButtons[i].onClick(() -> resizeMap(value, value));
            presets.add(presetButtons[i]);
        }
        size.put(presets, 4);
        column.put(size, 0);

        Card actions = new Card("me.actions");
        JPanel grid = new JPanel(new GridLayout(2, 2, 4, 4));
        grid.setOpaque(false);
        grid.setPreferredSize(new Dimension(100, 74));
        loadButton.onClick(this::loadMapFromSave);
        ToolButton seedButton = new ToolButton(currentStyle, ToolButton.Layout.TILE, Glyph.DICE, "me.seed_short").tip("me.seed_tip", null);
        seedButton.onClick(this::loadMapFromSeed);
        ToolButton clearButton = new ToolButton(currentStyle, ToolButton.Layout.TILE, Glyph.TRASH, "me.clear_short").tip("me.clear_tip", null);
        clearButton.onClick(this::clearMap);
        ToolButton newButton = new ToolButton(currentStyle, ToolButton.Layout.TILE, Glyph.NEW, "me.new_short").tip("me.new_tip", null);
        newButton.onClick(this::resetMap);
        grid.add(loadButton); grid.add(seedButton); grid.add(clearButton); grid.add(newButton);
        actions.put(grid, 0);
        column.put(actions, 4);

        Card test = new Card("me.test");
        test.put(checkNote, 0);
        ToolButton play = new ToolButton(currentStyle, ToolButton.Layout.ROW, Glyph.PLAY, "me.play_map").tip("me.play_tip", null).accent(new Color(46, 150, 96));
        play.size(100, 34);
        play.onClick(this::playMap);
        test.put(play, 6);
        wrongGeometryToggle.setPreferredSize(new Dimension(100, 24));
        skipValidationToggle.setPreferredSize(new Dimension(100, 24));
        test.put(wrongGeometryToggle, 6);
        test.put(skipValidationToggle, 1);
        column.put(test, 4);
        return column;
    }

    private JComponent stepperRow(String labelKey, StyledTextField field, int ignored) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        row.setPreferredSize(new Dimension(100, 24));
        Text label = new Text(labelKey, 12f, true, MUTED, 0);
        label.setPreferredSize(new Dimension(66, 24));
        ToolButton minus = new ToolButton(currentStyle, ToolButton.Layout.ICON, Glyph.MINUS, null).size(24, 24);
        ToolButton plus = new ToolButton(currentStyle, ToolButton.Layout.ICON, Glyph.PLUS, null).size(24, 24);
        boolean isWidth = field == widthField;
        minus.onClick(() -> resizeMap(isWidth ? mapWidth - 2 : mapWidth, isWidth ? mapHeight : mapHeight - 2));
        plus.onClick(() -> resizeMap(isWidth ? mapWidth + 2 : mapWidth, isWidth ? mapHeight : mapHeight + 2));
        field.setHorizontalAlignment(JTextField.CENTER);
        field.limitInput(3, c -> c >= '0' && c <= '9');
        field.addActionListener(e -> applyFields());
        field.addFocusListener(new FocusAdapter() { @Override public void focusLost(FocusEvent e) { applyFields(); } });
        JPanel middle = new JPanel(new BorderLayout(4, 0));
        middle.setOpaque(false);
        middle.add(minus, BorderLayout.WEST);
        middle.add(field, BorderLayout.CENTER);
        middle.add(plus, BorderLayout.EAST);
        row.add(label, BorderLayout.WEST);
        row.add(middle, BorderLayout.CENTER);
        return row;
    }

    private JComponent buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout(10, 0));
        bar.setOpaque(false);
        hoverText.setPreferredSize(new Dimension(190, 28));
        JPanel east = new JPanel(new BorderLayout(6, 0));
        east.setOpaque(false);
        east.add(hoverText, BorderLayout.WEST);
        east.add(zoomBar(currentStyle, canvas), BorderLayout.CENTER);
        bar.add(hintBar, BorderLayout.CENTER);
        bar.add(east, BorderLayout.EAST);
        canvas.addViewListener(this::updateHover);
        return bar;
    }
    //endregion

    //region Tools & Selection
    private void selectTool(Tool tool) {
        currentTool = tool;
        if (tool != Tool.PORTAL && tool != Tool.PORTAL_ONE) pendingPortal = null;
        syncSelection();
        updateHint();
        canvas.repaint();
    }
    private void chooseBlockRow(int index) {
        if (index == 5) selectTool(Tool.PORTAL);
        else if (index == 6) selectTool(Tool.PORTAL_ONE);
        else {
            selectedBlock = index;
            if (currentTool == Tool.ERASER || portalToolActive()) { currentTool = Tool.BRUSH; pendingPortal = null; }
            syncSelection();
            updateHint();
            canvas.repaint();
        }
    }
    /** One place decides which tool button and which block row look pressed. */
    private void syncSelection() {
        Tool[] order = {Tool.BRUSH, Tool.ERASER, Tool.BOX, Tool.FILL};
        for (int i = 0; i < toolButtons.length; i++) toolButtons[i].setSelected(currentTool == order[i]);
        boolean paints = currentTool == Tool.BRUSH || currentTool == Tool.BOX || currentTool == Tool.FILL;
        for (int i = 0; i < 5; i++) blockButtons[i].setSelected(paints && selectedBlock == i);
        blockButtons[5].setSelected(currentTool == Tool.PORTAL);
        blockButtons[6].setSelected(currentTool == Tool.PORTAL_ONE);
        portalDirButton.setEnabled(portalToolActive());
        boolean sized = currentTool == Tool.BRUSH || currentTool == Tool.ERASER;
        updatingControls = true;
        sizeSlider.setEnabled(sized);
        sizeSlider.setValue(currentTool == Tool.ERASER ? eraserSize : brushSize);
        sizeValue.setRaw(String.valueOf(sizeSlider.getValue()));
        sizeValue.setEnabled(sized);
        updatingControls = false;
    }
    private void updateHint() {
        if (pendingPortal != null) { hintBar.setHint(L("me.hint_pending")); return; }
        hintBar.setHint(L(switch (currentTool) {
            case BRUSH -> "me.hint_brush"; case ERASER -> "me.hint_eraser"; case BOX -> "me.hint_box"; case FILL -> "me.hint_fill";
            case PORTAL -> "me.hint_portal"; case PORTAL_ONE -> "me.hint_portal_one";
        }));
    }
    private void updateHover() {
        int x = canvas.getHoverX(), y = canvas.getHoverY();
        if (isOutside(x, y)) { hoverText.setRaw(""); return; }
        boolean start = currentFloorIndex == 0 && x == START_X && y == START_Y;
        hoverText.setRaw(x + ", " + y + "   " + L(start ? "me.name_start" : BLOCK_NAME_KEYS[Math.clamp(map[y][x], 0, 5)]));
    }
    private void updateAll() {
        updateSizeControls();
        updateFloorControls();
        updateHistoryButtons();
        updateDirtyText();
        updateHint();
        updateHover();
        updateLoadButton();
        scheduleCheck();
        canvas.repaint();
    }
    private void updateHistoryButtons() { undoButton.setEnabled(!undoHistory.isEmpty()); redoButton.setEnabled(!redoHistory.isEmpty()); }
    private void updateDirtyText() { dirtyText.setRaw(dirty ? "● " + L("ed.unsaved") : ""); }
    private void markDirty() { dirty = true; updateDirtyText(); updateHistoryButtons(); }
    //endregion

    //region Editing
    private void beginStroke() { strokeBase = snapshot(); strokePushed = false; }
    /** The first real change of a stroke puts the picture from before on the undo stack, so empty clicks never make undo steps. */
    private void touch() {
        if (!strokePushed && strokeBase != null) { pushUndo(strokeBase); strokePushed = true; }
        markDirty();
        scheduleCheck();
        canvas.repaint();
    }
    private boolean setCell(int x, int y, int value) {
        if (isOutside(x, y)) return false;
        if (currentFloorIndex == 0 && x == START_X && y == START_Y) return false;
        int old = map[y][x];
        if (old == value) return false;
        if (old == PORTAL && value != PORTAL) clearPortalAt(currentFloorIndex, x, y);
        map[y][x] = value;
        return true;
    }
    private static boolean inFootprint(int dx, int dy, int size) {
        int radius = (size - 1) / 2;
        return dx * dx + dy * dy <= radius * radius + 0.001;
    }
    private boolean stamp(int cx, int cy, int size, int value) {
        int radius = (size - 1) / 2;
        boolean changed = false;
        for (int y = cy - radius; y <= cy + radius; y++)
            for (int x = cx - radius; x <= cx + radius; x++)
                if (inFootprint(x - cx, y - cy, size)) changed |= setCell(x, y, value);
        return changed;
    }
    private boolean fillRect(int x0, int y0, int x1, int y1, int value) {
        boolean changed = false;
        for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) changed |= setCell(x, y, value);
        return changed;
    }
    private boolean floodFill(int startX, int startY, int value) {
        int target = map[startY][startX];
        if (target == value || target == PORTAL) return false;
        boolean[][] seen = new boolean[mapHeight][mapWidth];
        Deque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{startX, startY});
        boolean changed = false;
        while (!stack.isEmpty()) {
            int[] p = stack.pop();
            int x = p[0], y = p[1];
            if (isOutside(x, y) || seen[y][x] || map[y][x] != target) continue;
            seen[y][x] = true;
            changed |= setCell(x, y, value); //the start cell stays as it is but the fill still flows through it
            stack.push(new int[]{x + 1, y}); stack.push(new int[]{x - 1, y}); stack.push(new int[]{x, y + 1}); stack.push(new int[]{x, y - 1});
        }
        return changed;
    }
    private void clearMap() {
        if (!floorHasContent() && portalLinks.isEmpty()) { hintBar.flash(Tone.INFO, L("me.floor_already_empty")); return; }
        if (ask(this, currentStyle, "ed.confirm_title", fmt("me.clear_confirm", currentFloorIndex + 1), 1, "me.clear_yes", "cancel") != 0) return;
        saveHistoryState();
        for (int y = 0; y < mapHeight; y++) for (int x = 0; x < mapWidth; x++) if (map[y][x] == PORTAL) clearPortalAt(currentFloorIndex, x, y);
        for (int y = 0; y < mapHeight; y++) Arrays.fill(map[y], PATH);
        pendingPortal = null;
        markDirty();
        updateAll();
        hintBar.flash(Tone.INFO, L("me.floor_cleared"));
    }
    private void resetMap() {
        if (mapHasContent() && ask(this, currentStyle, "ed.confirm_title", L("me.new_confirm"), 1, "me.new_yes", "cancel") != 0) return;
        saveHistoryState();
        mapWidth = DEFAULT_SIZE;
        mapHeight = DEFAULT_SIZE;
        floorMaps.clear();
        floorMaps.add(createMap(mapWidth, mapHeight));
        currentFloorIndex = 0;
        map = floorMaps.getFirst();
        portalLinks.clear();
        pendingPortal = null;
        markDirty();
        syncCanvasSize();
        updateAll();
    }
    private void pickBlockFromCell(int x, int y) {
        if (isOutside(x, y)) return;
        int value = map[y][x];
        if (value == PORTAL) return;
        selectedBlock = value;
        if (currentTool == Tool.ERASER || portalToolActive()) currentTool = Tool.BRUSH;
        syncSelection();
        updateHint();
    }
    //endregion

    //region Map Validation
    private java.util.List<int[]> findExits() {
        java.util.List<int[]> exits = new ArrayList<>();
        for (int f = 0; f < floorMaps.size(); f++) {
            int[][] floorMap = floorMaps.get(f);
            for (int y = 0; y < mapHeight; y++) for (int x = 0; x < mapWidth; x++) if (floorMap[y][x] == FINISH) exits.add(new int[]{f, x, y});
        }
        return exits;
    }
    private int[] findReachableExit() { return MazeSolver.findReachableFinish(floorMaps.toArray(new int[0][][]), buildPortalDataPerFloor(), 0, START_X, START_Y); }
    /** Returns a readable problem description, or null when the map is playable. */
    private String validateMap() {
        if (findExits().isEmpty()) return L("me.check_no_finish");
        int[] blocked = MazeSolver.findBlockedPortalExit(floorMaps.toArray(new int[0][][]), buildPortalDataPerFloor());
        if (blocked != null) return L("me.portal_exit_blocked") + "\n(" + L("floor") + (blocked[0] + 1) + ": " + blocked[1] + ", " + blocked[2] + ")";
        return findReachableExit() == null ? L("me.check_unreachable") : null;
    }
    private void scheduleCheck() { checkTimer.restart(); }
    /** Live feedback next to the Play button, so nobody has to press it just to learn the map is broken. */
    private void runCheck() {
        String problem = validateMap();
        if (problem == null) checkNote.set(Glyph.CHECK, OK, L("me.check_ok"));
        else checkNote.set(Glyph.WARN, WARN, problem);
        checkNote.getParent().revalidate();
    }
    //endregion

    //region Map Saving And Loading
    private void saveMapOnly() {
        try {
            writeMap(AppPaths.SAVE_FILE, wrongGeometryToggle.isSelected() ? GEOMETRY_WRONG : GEOMETRY_EUCLIDEAN);
            dirty = false;
            updateDirtyText();
            updateLoadButton();
            hintBar.flash(Tone.OK, L("ed.saved_ok"));
        } catch (IOException e) {
            inform(this, currentStyle, "save_error", L("me.could_not_save_map") + "\n" + e.getMessage());
        }
    }
    private void playMap() {
        if (!skipValidationToggle.isSelected()) {
            String error = validateMap();
            if (error != null) { inform(this, currentStyle, "me.map_cannot_be_played", error + "\n\n" + L("me.play_anyway_hint")); return; }
        }
        try {
            int geometryIndex = wrongGeometryToggle.isSelected() ? GEOMETRY_WRONG : GEOMETRY_EUCLIDEAN;
            Files.createDirectories(AppPaths.DATA_DIR);
            writeMap(AppPaths.SAVE_FILE, geometryIndex);
            dirty = false;
            int[][][] floorsArray = floorMaps.toArray(new int[0][][]);
            PortalData[] portalsPerFloor = buildPortalDataPerFloor();
            RCJMS.instance.gameView = new GameView(floorsArray, portalsPerFloor, geometryIndex);
            RCJMS.instance.changeView(RCJMS.instance.gameView, "Raycast Me!");
            RCJMS.instance.gameView.start();
        } catch (IOException e) {
            inform(this, currentStyle, "save_error", L("me.could_not_save_map") + "\n" + e.getMessage());
        }
    }
    private static boolean hasSavedMap() {
        try { return !readMap(AppPaths.SAVE_FILE).isEmpty(); }
        catch (IOException e) { return false; }
    }
    private void updateLoadButton() { loadButton.setEnabled(hasSavedMap()); }
    private void loadMapFromSave() {
        if (dirty && mapHasContent() && ask(this, currentStyle, "ed.confirm_title", L("me.load_confirm"), 1, "me.load_yes", "cancel") != 0) return;
        java.util.List<int[][]> floors;
        SaveData.Data data;
        try {
            floors = readMap(AppPaths.SAVE_FILE);
            data = SaveData.load();
        } catch (IOException e) {
            inform(this, currentStyle, "load_error", L("me.could_not_load_map") + "\n" + e.getMessage());
            updateLoadButton();
            return;
        }
        if (floors.isEmpty() || floors.size() > MAX_FLOORS) { updateLoadButton(); return; }

        saveHistoryState();
        int newHeight = floors.getFirst().length, newWidth = floors.getFirst()[0].length;
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
                if (isOutside(normalized[1], normalized[2]) || isOutside(normalized[5], normalized[6])) continue;
                portalLinks.add(normalized);
            }
        }
        pendingPortal = null;
        wrongGeometryToggle.setSelected(data.mapGeometryMode == GEOMETRY_WRONG);
        dirty = false;
        syncCanvasSize();
        updateAll();
        hintBar.flash(Tone.OK, L("me.map_loaded"));
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
    private void exitToMainMenu() {
        if (dirty && mapHasContent()) {
            int answer = ask(this, currentStyle, "ed.unsaved_title", L("me.unsaved_msg"), 0, "ed.save_and_leave", "ed.leave_without_saving", "ed.keep_editing");
            if (answer == 0) { saveMapOnly(); if (dirty) return; }
            else if (answer != 1) return;
        }
        RCJMS.instance.changeView(RCJMS.instance.mainMenuView = new MainMenuView(), "main_menu");
    }
    private void showHelp() { inform(this, currentStyle, "ed.help", L("me.help_text")); }
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
        markDirty();
        syncCanvasSize();
        updateAll();
        hintBar.flash(Tone.OK, L("me.seed_map_loaded"));
    }
    //endregion

    //region Undo/Redo
    private EditorState snapshot() { return new EditorState(floorMaps, currentFloorIndex, portalLinks, mapWidth, mapHeight); }
    private int historyLimit() { return (long) mapWidth * mapHeight * floorMaps.size() > 150_000 ? 10 : 50; }
    private void pushUndo(EditorState state) {
        if (restoringHistory) return;
        undoHistory.push(state);
        while (undoHistory.size() > historyLimit()) undoHistory.removeLast();
        redoHistory.clear();
    }
    private void saveHistoryState() { pushUndo(snapshot()); }
    private void undo() {
        if (undoHistory.isEmpty()) return;
        redoHistory.push(snapshot());
        while (redoHistory.size() > historyLimit()) redoHistory.removeLast();
        restoreState(undoHistory.pop());
    }
    private void redo() {
        if (redoHistory.isEmpty()) return;
        undoHistory.push(snapshot());
        while (undoHistory.size() > historyLimit()) undoHistory.removeLast();
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
        dirty = true;
        syncCanvasSize();
        updateAll();
        restoringHistory = false;
    }
    //endregion

    //region Key Binds
    private void setupKeyBinds() {
        InputMap windowMap = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = getActionMap();
        bindKey(windowMap, actionMap, KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK), "undo", this::undo);
        bindKey(windowMap, actionMap, KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK), "redo", this::redo);
        bindKey(windowMap, actionMap, KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), "redo2", this::redo);
        bindKey(windowMap, actionMap, KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK), "save", this::saveMapOnly);
        bindKey(windowMap, actionMap, KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0), "help", this::showHelp);
        bindKey(windowMap, actionMap, KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, 0), "floorUp", () -> switchFloor(currentFloorIndex + 1));
        bindKey(windowMap, actionMap, KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, 0), "floorDown", () -> switchFloor(currentFloorIndex - 1));
        bindKey(windowMap, actionMap, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel", () -> {
            if (pendingPortal != null) { pendingPortal = null; updateHint(); canvas.repaint(); }
            else exitToMainMenu();
        });
        bindHotkey(windowMap, actionMap, KeyEvent.VK_V, () -> selectTool(Tool.BRUSH));
        bindHotkey(windowMap, actionMap, KeyEvent.VK_B, () -> selectTool(Tool.ERASER));
        bindHotkey(windowMap, actionMap, KeyEvent.VK_X, () -> selectTool(Tool.BOX));
        bindHotkey(windowMap, actionMap, KeyEvent.VK_F, () -> selectTool(Tool.FILL));
        bindHotkey(windowMap, actionMap, KeyEvent.VK_N, () -> chooseBlockRow(5));
        bindHotkey(windowMap, actionMap, KeyEvent.VK_M, () -> chooseBlockRow(6));
        bindHotkey(windowMap, actionMap, KeyEvent.VK_R, this::rotatePortalDirection);
        bindHotkey(windowMap, actionMap, KeyEvent.VK_G, () -> wrongGeometryToggle.setSelected(!wrongGeometryToggle.isSelected()));
        for (int i = 0; i < 5; i++) { final int index = i; bindHotkey(windowMap, actionMap, KeyEvent.VK_1 + i, () -> { if (blockButtons[index].isEnabled()) chooseBlockRow(index); }); }
        bindHotkey(windowMap, actionMap, KeyEvent.VK_OPEN_BRACKET, () -> sizeSlider.setValue(sizeSlider.getValue() - 1));
        bindHotkey(windowMap, actionMap, KeyEvent.VK_CLOSE_BRACKET, () -> sizeSlider.setValue(sizeSlider.getValue() + 1));
    }
    private void bindKey(InputMap inputMap, ActionMap actionMap, KeyStroke key, String name, Runnable action) {
        String id = "mapEditor" + name;
        inputMap.put(key, id);
        actionMap.put(id, new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { action.run(); } });
    }
    private void bindHotkey(InputMap inputMap, ActionMap actionMap, int keyCode, Runnable action) {
        bindKey(inputMap, actionMap, KeyStroke.getKeyStroke(keyCode, 0), "hotkey" + keyCode, () -> {
            if (KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner() instanceof javax.swing.text.JTextComponent) return;
            action.run();
        });
    }
    //endregion

    //region Helpers
    private Style loadTheme() { return SaveData.load().theme; }
    //endregion

    //region Nested Types
    private enum Tool {BRUSH, ERASER, BOX, FILL, PORTAL, PORTAL_ONE}

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

    /** Row of floor buttons above the map: pick a floor, add one, or remove the current one. */
    private final class FloorStrip extends JComponent {
        private static final int ADD = -2, REMOVE = -3, NONE = -4;
        private int count = 1, current = 0, hover = NONE;

        FloorStrip() {
            setToolTipText("");
            MouseAdapter mouse = new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent e) { int h = hit(e.getX(), e.getY()); if (h != hover) { hover = h; repaint(); } }
                @Override public void mouseExited(MouseEvent e) { hover = NONE; repaint(); }
                @Override public void mouseReleased(MouseEvent e) {
                    int h = hit(e.getX(), e.getY());
                    if (h == ADD) addFloor(); else if (h == REMOVE) removeFloor(); else if (h >= 0) switchFloor(h);
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }
        void update(int count, int current) { this.count = count; this.current = current; repaint(); }
        private int labelWidth() { return 64; }
        private Rectangle addRect() { return new Rectangle(getWidth() - 62, 3, 26, 26); }
        private Rectangle removeRect() { return new Rectangle(getWidth() - 30, 3, 26, 26); }
        private int pillWidth() { return Math.clamp((getWidth() - labelWidth() - 62 - 8) / Math.max(1, count) - 3, 20, 40); }
        private Rectangle pillRect(int i) { int w = pillWidth(); return new Rectangle(labelWidth() + i * (w + 3), 3, w, 26); }
        private int hit(int x, int y) {
            if (addRect().contains(x, y)) return ADD;
            if (removeRect().contains(x, y)) return REMOVE;
            for (int i = 0; i < count; i++) if (pillRect(i).contains(x, y)) return i;
            return NONE;
        }
        @Override public String getToolTipText(MouseEvent e) {
            int h = hit(e.getX(), e.getY());
            return h == ADD ? L("me.add_floor") : h == REMOVE ? L("me.remove_floor") : h >= 0 ? fmt("me.go_floor", h + 1) : null;
        }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = smooth(g0);
            Theme theme = Theme.of(currentStyle);
            g.setFont(font(12f, true));
            g.setColor(MUTED);
            g.drawString(ellipsize(L("me.floor_strip"), g.getFontMetrics(), labelWidth() - 6), 2, (getHeight() - g.getFontMetrics().getHeight()) / 2 + g.getFontMetrics().getAscent());
            for (int i = 0; i < count; i++) {
                Rectangle r = pillRect(i);
                boolean on = i == current;
                g.setColor(on ? mix(theme.normal(), HILITE, 0.55f) : hover == i ? theme.hover() : theme.normal());
                g.fillRoundRect(r.x, r.y, r.width, r.height, 10, 10);
                g.setColor(on ? HILITE : theme.accent());
                g.setStroke(new BasicStroke(on ? 2f : 1f));
                g.drawRoundRect(r.x, r.y, r.width - 1, r.height - 1, 10, 10);
                String label = String.valueOf(i + 1);
                g.setFont(font(12f, true));
                FontMetrics fm = g.getFontMetrics();
                g.setColor(Color.WHITE);
                g.drawString(label, r.x + (r.width - fm.stringWidth(label)) / 2, r.y + (r.height - fm.getHeight()) / 2 + fm.getAscent());
            }
            drawRoundButton(g, theme, addRect(), Glyph.PLUS, count < MAX_FLOORS, hover == ADD);
            drawRoundButton(g, theme, removeRect(), Glyph.TRASH, count > 1, hover == REMOVE);
            g.dispose();
        }
        private void drawRoundButton(Graphics2D g, Theme theme, Rectangle r, Glyph glyph, boolean enabled, boolean hovered) {
            Composite old = g.getComposite();
            if (!enabled) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
            g.setColor(hovered && enabled ? theme.hover() : theme.normal());
            g.fillRoundRect(r.x, r.y, r.width, r.height, 10, 10);
            g.setColor(theme.accent());
            g.setStroke(new BasicStroke(1f));
            g.drawRoundRect(r.x, r.y, r.width - 1, r.height - 1, 10, 10);
            glyph.paint(g, r.x + 5, r.y + 5, r.width - 10, theme.text());
            g.setComposite(old);
        }
    }

    private final class MapCanvas extends GridCanvas {
        private Tool strokeTool;
        private int strokeSize, strokeValue, lastX, lastY;
        private boolean boxing, boxErase;
        private int boxX0, boxY0, boxX1, boxY1;

        private Color colorOf(int value) {
            return switch (value) { case WALL -> WALL_COLOR; case FINISH -> FINISH_COLOR; case STAIRS_UP -> STAIRS_UP_COLOR; case STAIRS_DOWN -> STAIRS_DOWN_COLOR; case PORTAL -> PORTAL_COLOR; default -> PATH_COLOR; };
        }
        @Override protected void paintContent(Graphics2D g, int x0, int y0, int x1, int y1) {
            for (int y = y0; y <= y1; y++) {
                int ry = py(y), rh = Math.max(1, py(y + 1) - ry);
                for (int x = x0; x <= x1; x++) {
                    int rx = px(x), rw = Math.max(1, px(x + 1) - rx);
                    g.setColor(colorOf(map[y][x]));
                    g.fillRect(rx, ry, rw, rh);
                }
            }
            if (cell >= 14) {
                Graphics2D a = smooth(g);
                for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) {
                    Glyph glyph = switch (map[y][x]) { case FINISH -> Glyph.FLAG; case STAIRS_UP -> Glyph.UP; case STAIRS_DOWN -> Glyph.DOWN; default -> null; };
                    if (glyph == null) continue;
                    glyph.paint(a, px(x) + cell * 0.18, py(y) + cell * 0.18, cell * 0.64, map[y][x] == FINISH ? new Color(15, 60, 30) : Color.WHITE);
                }
                a.dispose();
            }
            if (cell >= 4) {
                g.setColor(new Color(15, 15, 18, 150));
                for (int x = x0; x <= x1 + 1; x++) g.drawLine(px(x), py(y0), px(x), py(y1 + 1));
                for (int y = y0; y <= y1 + 1; y++) g.drawLine(px(x0), py(y), px(x1 + 1), py(y));
            }
            if (currentFloorIndex == 0) drawStart(g);
            drawPortalArrows(g);
            g.setColor(new Color(0, 0, 0, 200));
            g.drawRect(px(0) - 1, py(0) - 1, px(cols) - px(0) + 1, py(rows) - py(0) + 1);
        }
        private void drawStart(Graphics2D g) {
            int rx = px(START_X), ry = py(START_Y), size = Math.max(1, px(START_X + 1) - rx);
            int inset = Math.max(1, size / 6);
            g.setColor(new Color(40, 110, 255));
            g.fillRect(rx + inset, ry + inset, Math.max(1, size - inset * 2), Math.max(1, size - inset * 2));
            if (cell >= 14) {
                Graphics2D a = smooth(g);
                a.setFont(font((float) Math.max(9, cell * 0.5), true));
                FontMetrics fm = a.getFontMetrics();
                a.setColor(Color.WHITE);
                a.drawString("S", rx + (size - fm.stringWidth("S")) / 2, ry + (size - fm.getHeight()) / 2 + fm.getAscent());
                a.dispose();
            }
        }
        @Override protected void paintOverlay(Graphics2D g, int x0, int y0, int x1, int y1) {
            if (pendingPortal != null && pendingPortal[0] == currentFloorIndex) outline(g, pendingPortal[1], pendingPortal[2], new Color(230, 220, 60), 2);
            if (boxing) {
                int ax = Math.min(boxX0, boxX1), ay = Math.min(boxY0, boxY1), bx = Math.max(boxX0, boxX1), by = Math.max(boxY0, boxY1);
                Color c = boxErase ? PATH_COLOR : colorOf(selectedBlock);
                g.setColor(alpha(c, 170));
                g.fillRect(px(ax), py(ay), px(bx + 1) - px(ax), py(by + 1) - py(ay));
                g.setColor(Color.WHITE);
                g.drawRect(px(ax), py(ay), px(bx + 1) - px(ax) - 1, py(by + 1) - py(ay) - 1);
                return;
            }
            if (hoverX < 0) return;
            switch (currentTool) {
                case BRUSH, ERASER -> {
                    int size = currentTool == Tool.BRUSH ? brushSize : eraserSize, radius = (size - 1) / 2;
                    Color fill = alpha(currentTool == Tool.BRUSH ? colorOf(selectedBlock) : new Color(255, 90, 90), 140);
                    for (int y = hoverY - radius; y <= hoverY + radius; y++) for (int x = hoverX - radius; x <= hoverX + radius; x++) {
                        if (!inGrid(x, y) || !inFootprint(x - hoverX, y - hoverY, size)) continue;
                        g.setColor(fill);
                        g.fillRect(px(x), py(y), Math.max(1, px(x + 1) - px(x)), Math.max(1, py(y + 1) - py(y)));
                    }
                    outline(g, hoverX, hoverY, Color.WHITE, 1);
                }
                case PORTAL, PORTAL_ONE -> {
                    outline(g, hoverX, hoverY, PORTAL_COLOR.brighter(), 2);
                    if (cell >= 5) {
                        Graphics2D a = smooth(g);
                        int[] d = PORTAL_DIRS[portalDirIndex];
                        drawArrow(a, hoverX, hoverY, d[0], d[1], new Color(255, 255, 255, 190));
                        a.dispose();
                    }
                }
                default -> outline(g, hoverX, hoverY, Color.WHITE, 1);
            }
        }
        private void outline(Graphics2D g, int x, int y, Color color, int thickness) {
            g.setColor(color);
            int w = Math.max(2, px(x + 1) - px(x)), h = Math.max(2, py(y + 1) - py(y));
            for (int i = 0; i < thickness; i++) g.drawRect(px(x) + i, py(y) + i, w - 1 - i * 2, h - 1 - i * 2);
        }
        private void drawPortalArrows(Graphics2D base) {
            if (cell < 5) return;
            Graphics2D g2 = smooth(base);
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
            if (cell < 12) return;
            g2.setFont(g2.getFont().deriveFont(Font.BOLD, (float) Math.max(9f, cell * 0.38f)));
            String text = String.valueOf(otherFloor + 1);
            g2.setColor(new Color(15, 15, 18));
            g2.drawString(text, (float) (originX + x * cell + 3), (float) (originY + y * cell + g2.getFontMetrics().getAscent() + 1));
            g2.setColor(new Color(255, 235, 120));
            g2.drawString(text, (float) (originX + x * cell + 2), (float) (originY + y * cell + g2.getFontMetrics().getAscent()));
        }
        private void drawOneSidedFace(Graphics2D g2, int x, int y, int dx, int dy) {
            double cx = originX + x * cell + cell / 2.0, cy = originY + y * cell + cell / 2.0, half = cell / 2.0;
            double ex = cx - dx * half, ey = cy - dy * half, ox = -dy * half, oy = dx * half;
            g2.setColor(new Color(80, 220, 230));
            g2.setStroke(new BasicStroke((float) Math.max(2f, cell / 8f)));
            g2.draw(new java.awt.geom.Line2D.Double(ex - ox, ey - oy, ex + ox, ey + oy));
            g2.setStroke(new BasicStroke(1f));
        }
        private void drawArrow(Graphics2D g2, int x, int y, int dx, int dy, Color color) {
            double cx = originX + x * cell + cell / 2.0, cy = originY + y * cell + cell / 2.0, s = cell * 0.32;
            Path2D.Double arrow = new Path2D.Double();
            arrow.moveTo(cx + dx * s, cy + dy * s);
            arrow.lineTo(cx - dx * s * 0.7 + (-dy) * s * 0.8, cy - dy * s * 0.7 + (double) dx * s * 0.8);
            arrow.lineTo(cx - dx * s * 0.7 - (-dy) * s * 0.8, cy - dy * s * 0.7 - (double) dx * s * 0.8);
            arrow.closePath();
            g2.setColor(color);
            g2.fill(arrow);
            g2.setColor(new Color(15, 15, 18));
            g2.draw(arrow);
        }

        @Override protected void pointerPressed(int x, int y, MouseEvent e) {
            if (!inGrid(x, y)) return;
            boolean right = SwingUtilities.isRightMouseButton(e);
            if (e.isAltDown() && !right) { pickBlockFromCell(x, y); return; }
            if (!right && (currentTool == Tool.PORTAL || currentTool == Tool.PORTAL_ONE)) { handlePortalClick(x, y, currentTool == Tool.PORTAL_ONE); return; }
            Tool tool = right ? Tool.ERASER : currentTool;
            if (tool == Tool.PORTAL || tool == Tool.PORTAL_ONE) tool = Tool.ERASER;
            int value = tool == Tool.ERASER ? PATH : selectedBlock;
            switch (tool) {
                case BRUSH, ERASER -> {
                    beginStroke();
                    strokeTool = tool; strokeValue = value; lastX = x; lastY = y;
                    strokeSize = tool == Tool.BRUSH ? brushSize : (right && portalToolActive() ? 1 : eraserSize);
                    if (stamp(x, y, strokeSize, value)) touch();
                }
                case BOX -> { boxing = true; boxErase = right; boxX0 = boxX1 = x; boxY0 = boxY1 = y; repaint(); }
                case FILL -> { beginStroke(); if (floodFill(x, y, value)) touch(); }
                default -> { }
            }
        }
        @Override protected void pointerDragged(int x, int y, MouseEvent e) {
            if (boxing) { boxX1 = Math.clamp(x, 0, cols - 1); boxY1 = Math.clamp(y, 0, rows - 1); repaint(); return; }
            if (strokeTool == null || !inGrid(x, y)) return;
            boolean[] any = {false};
            line(lastX, lastY, x, y, (cx, cy) -> any[0] |= stamp(cx, cy, strokeSize, strokeValue)); //no gaps when the mouse moves fast
            lastX = x; lastY = y;
            if (any[0]) touch();
        }
        @Override protected void pointerReleased(int x, int y, MouseEvent e) {
            if (boxing) {
                boxing = false;
                beginStroke();
                if (fillRect(boxX0, boxY0, boxX1, boxY1, boxErase ? PATH : selectedBlock)) touch();
                repaint();
            }
            strokeTool = null;
        }
    }
    //endregion
}
