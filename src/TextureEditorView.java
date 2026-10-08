import EditorUI.*;
import Helpers.AppPaths;
import Helpers.SaveData;
import StyleUI.*;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.*;
import java.nio.file.*;
import java.util.*;

import static EditorUI.EditorKit.*;

public class TextureEditorView extends JPanel {
    //region Variables
    private final Style currentStyle = loadTheme();

    public static final int MIN_TEXTURE_SIZE = 1, MAX_TEXTURE_SIZE = 512, MAX_FINISH_HEIGHT = 256;
    private static final int ABSOLUTE_MAX_TEXTURE_DIMENSION = 4096;
    private static final int MIN_TOOL_SIZE = 1, MAX_TOOL_SIZE = 16, MAX_HISTORY = 40;
    private static final int LEFT_WIDTH = 168, RIGHT_WIDTH = 216;

    private static final int ORIGINAL_WALL_COLOR = 0xECD485;
    private static final int ORIGINAL_FLOOR_COLOR = 0xD3AF63;
    private static final int ORIGINAL_CEILING_COLOR = 0x816E1E;
    private static final int ORIGINAL_FINISH_COLOR = 0x33FF66;

    private static final int EMPTY_COLOR = -1;
    private static final int SAVED_EMPTY_COLOR = 0xFFFFFF;
    private static final int TEXTURE_FILE_MAGIC = 0x52435458; //RCTX Extension Token
    private static final int TEXTURE_FORMAT_VERSION = 2;
    private static final int RCTX_PIXEL_RGB888 = 0, RCTX_PIXEL_PALETTE8 = 1, RCTX_FLAG_DEFLATE = 1;
    private static final String[] MODE_KEYS = {"te.walls", "te.floor", "te.ceiling", "te.finish"};

    private final int[][][] textures = {{{ORIGINAL_WALL_COLOR}}, {{ORIGINAL_FLOOR_COLOR}}, {{ORIGINAL_CEILING_COLOR}}, {{ORIGINAL_FINISH_COLOR}}};
    private final BufferedImage[] images = new BufferedImage[4];
    private final boolean[] imageDirty = {true, true, true, true};
    private TextureMode mode = TextureMode.WALLS;
    private Tool currentTool = Tool.BRUSH;
    private int brushSize = 1, eraserSize = 1;
    private boolean dirty = false;

    private final Deque<Snapshot> undoHistory = new ArrayDeque<>(), redoHistory = new ArrayDeque<>();
    private Snapshot strokeSnapshot;
    private boolean strokePushed;
    private Tool strokeTool;
    private int strokeLastX, strokeLastY;
    private boolean updatingControls;

    private final TextureCanvas canvas = new TextureCanvas();
    private final ColorPicker colorPicker = new ColorPicker(currentStyle, RIGHT_WIDTH - 32, 96);
    private final HintBar hintBar = new HintBar();
    private final Text hoverText = new Text(null, 12f, true, TEXT, 2);
    private final Text dirtyText = new Text(null, 12f, true, WARN, 0);
    private final Text sizeValue = new Text(null, 12f, true, TEXT, 2);
    private final Text maxText = new Text(null, 10.5f, false, MUTED, 0);
    private final ModeTab[] tabs = new ModeTab[4];
    private final ToolButton[] toolButtons = new ToolButton[4];
    private final ToolButton undoButton = new ToolButton(currentStyle, ToolButton.Layout.ICON, Glyph.UNDO, null).tip("ed.undo", "Ctrl+Z");
    private final ToolButton redoButton = new ToolButton(currentStyle, ToolButton.Layout.ICON, Glyph.REDO, null).tip("ed.redo", "Ctrl+Y");
    private final ToolButton[] presetButtons = new ToolButton[4];
    private final StyledSlider sizeSlider = new StyledSlider(currentStyle, MIN_TOOL_SIZE, MAX_TOOL_SIZE, 1);
    private final StyledTextField widthField = new StyledTextField(currentStyle, "1");
    private final StyledTextField heightField = new StyledTextField(currentStyle, "1");
    private final TilePreview preview = new TilePreview();
    private static final int[] PRESETS = {8, 16, 32, 64};
    //endregion

    //region Constructors
    public TextureEditorView() throws IOException {
        setLayout(new BorderLayout(10, 8));
        setBorder(new EmptyBorder(10, 10, 8, 10));
        setBackground(BG);

        add(buildHeader(), BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);
        setupKeyBinds();

        loadTexturesFromTmp();
        colorPicker.setColor(ORIGINAL_WALL_COLOR, true);
        selectTool(Tool.BRUSH);
        showMode(TextureMode.WALLS);
    }
    //endregion

    //region Public API
    /** Puts all four textures back to the game's original flat colors. */
    public void resetTextures() {
        textures[0] = new int[][]{{ORIGINAL_WALL_COLOR}};
        textures[1] = new int[][]{{ORIGINAL_FLOOR_COLOR}};
        textures[2] = new int[][]{{ORIGINAL_CEILING_COLOR}};
        textures[3] = new int[][]{{ORIGINAL_FINISH_COLOR}};
        Arrays.fill(imageDirty, true);
        undoHistory.clear();
        redoHistory.clear();
        showMode(mode);
    }
    //endregion

    //region Builders
    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setOpaque(false);

        ToolButton back = new ToolButton(currentStyle, ToolButton.Layout.ROW, Glyph.BACK, "ed.back").tip("ed.back", "Esc").size(104, 36);
        back.onClick(this::exitToMainMenu);
        Text title = new Text("te.texture_editor", 18f, true, TEXT, 0);
        title.setPreferredSize(new Dimension(190, 36));
        dirtyText.setPreferredSize(new Dimension(150, 36));
        JPanel left = row(10, back, title, dirtyText);
        left.setPreferredSize(new Dimension(480, 36));

        ToolButton importButton = new ToolButton(currentStyle, ToolButton.Layout.ROW, Glyph.IMPORT, "te.import_short").tip("te.import_hint", null).size(140, 36);
        importButton.onClick(this::importImage);
        ToolButton saveButton = new ToolButton(currentStyle, ToolButton.Layout.ROW, Glyph.SAVE, "te.save").tip("te.save_hint", "Ctrl+S").accent(new Color(46, 150, 96)).size(120, 36);
        saveButton.onClick(this::saveWithDialog);
        ToolButton helpButton = new ToolButton(currentStyle, ToolButton.Layout.ICON, Glyph.HELP, null).tip("ed.help", "F1").size(36, 36);
        helpButton.onClick(this::showHelp);
        undoButton.size(36, 36).onClick(this::undo);
        redoButton.size(36, 36).onClick(this::redo);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.setOpaque(false);
        right.add(undoButton);
        right.add(redoButton);
        right.add(importButton);
        right.add(helpButton);
        right.add(saveButton);

        header.add(left, BorderLayout.WEST);
        header.add(right, BorderLayout.CENTER);
        return header;
    }

    private JComponent buildBody() {
        JPanel body = new JPanel(new BorderLayout(10, 0));
        body.setOpaque(false);

        JPanel tabRow = new JPanel(new GridLayout(1, 4, 6, 0));
        tabRow.setOpaque(false);
        tabRow.setPreferredSize(new Dimension(100, 54));
        for (TextureMode m : TextureMode.values()) {
            tabs[m.ordinal()] = new ModeTab(m);
            tabRow.add(tabs[m.ordinal()]);
        }
        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setOpaque(false);
        center.add(tabRow, BorderLayout.NORTH);
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
        JPanel grid = new JPanel(new GridLayout(2, 2, 4, 4));
        grid.setOpaque(false);
        grid.setPreferredSize(new Dimension(100, 88));
        Glyph[] glyphs = {Glyph.BRUSH, Glyph.ERASER, Glyph.FILL, Glyph.PICKER};
        String[] keys = {"brush", "eraser", "te.fill", "te.pick"}, shortcuts = {"V", "N", "B", "I"};
        String[] tips = {"te.hint_brush", "te.hint_eraser", "te.hint_fill", "te.hint_pick"};
        Tool[] tools4 = Tool.values();
        for (int i = 0; i < 4; i++) {
            final Tool t = tools4[i];
            toolButtons[i] = new ToolButton(currentStyle, ToolButton.Layout.TILE, glyphs[i], keys[i]).badge(shortcuts[i]).tip(tips[i], shortcuts[i]);
            toolButtons[i].onClick(() -> selectTool(t));
            grid.add(toolButtons[i]);
        }
        ToolButton.group(toolButtons);
        tools.put(grid, 0);
        sizeSlider.setPreferredSize(new Dimension(100, 22));
        sizeSlider.setOpaque(false);
        sizeSlider.addChangeListener(e -> {
            if (updatingControls) return;
            if (currentTool == Tool.ERASER) eraserSize = sizeSlider.getValue(); else brushSize = sizeSlider.getValue();
            sizeValue.setRaw(String.valueOf(sizeSlider.getValue()));
            canvas.repaint();
        });
        sizeValue.setPreferredSize(new Dimension(24, 22));
        JPanel sizeRow = new JPanel(new BorderLayout(6, 0));
        sizeRow.setOpaque(false);
        sizeRow.add(new Text("ed.size", 12f, true, MUTED, 0), BorderLayout.WEST);
        sizeRow.add(sizeSlider, BorderLayout.CENTER);
        sizeRow.add(sizeValue, BorderLayout.EAST);
        sizeRow.setPreferredSize(new Dimension(100, 24));
        tools.put(sizeRow, 8);
        column.put(tools, 0);

        Card size = new Card("te.size_title");
        JPanel presets = new JPanel(new GridLayout(1, 5, 3, 0));
        presets.setOpaque(false);
        presets.setPreferredSize(new Dimension(100, 28));
        for (int i = 0; i < PRESETS.length; i++) {
            final int value = PRESETS[i];
            presetButtons[i] = new ToolButton(currentStyle, ToolButton.Layout.TEXT, null, null).tip("te.preset_tip", null);
            presetButtons[i].setLabel(String.valueOf(value));
            presetButtons[i].onClick(() -> setTextureSize(value, value));
            presets.add(presetButtons[i]);
        }
        ToolButton reset = new ToolButton(currentStyle, ToolButton.Layout.ICON, Glyph.TRASH, null).tip("te.reset_this", null);
        reset.onClick(this::resetCurrent);
        presets.add(reset);
        size.put(presets, 0);

        widthField.setHorizontalAlignment(JTextField.CENTER);
        heightField.setHorizontalAlignment(JTextField.CENTER);
        for (StyledTextField f : new StyledTextField[]{widthField, heightField}) f.limitInput(3, c -> c >= '0' && c <= '9');
        widthField.addActionListener(e -> applyFields());
        heightField.addActionListener(e -> applyFields());
        FocusAdapter blur = new FocusAdapter() { @Override public void focusLost(FocusEvent e) { applyFields(); } };
        widthField.addFocusListener(blur);
        heightField.addFocusListener(blur);
        JPanel dims = new JPanel(new GridLayout(1, 2, 6, 0));
        dims.setOpaque(false);
        dims.setPreferredSize(new Dimension(100, 28));
        dims.add(labeled("te.w_short", widthField));
        dims.add(labeled("te.h_short", heightField));
        size.put(dims, 6);
        size.put(maxText, 4);
        column.put(size, 8);

        Card previewCard = new Card("te.preview");
        previewCard.put(preview, 0);
        column.put(previewCard, 8);
        return column;
    }

    private JComponent labeled(String key, JComponent field) {
        JPanel p = new JPanel(new BorderLayout(4, 0));
        p.setOpaque(false);
        Text label = new Text(key, 12f, true, MUTED, 0);
        label.setPreferredSize(new Dimension(18, 26));
        p.add(label, BorderLayout.WEST);
        p.add(field, BorderLayout.CENTER);
        return p;
    }

    private Column buildRight() {
        Column column = new Column();
        column.setBorder(new EmptyBorder(0, 6, 0, 0));
        Card color = new Card("te.color");
        color.put(colorPicker, 0);
        column.put(color, 0);
        colorPicker.addColorListener(rgb -> canvas.repaint());
        return column;
    }

    private JComponent buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout(10, 0));
        bar.setOpaque(false);
        hoverText.setPreferredSize(new Dimension(170, 28));
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

    //region Tools & Modes
    private void selectTool(Tool tool) {
        currentTool = tool;
        toolButtons[tool.ordinal()].choose();
        updatingControls = true;
        sizeSlider.setEnabled(tool == Tool.BRUSH || tool == Tool.ERASER);
        sizeSlider.setValue(tool == Tool.ERASER ? eraserSize : brushSize);
        sizeValue.setRaw(String.valueOf(sizeSlider.getValue()));
        updatingControls = false;
        sizeValue.setEnabled(sizeSlider.isEnabled());
        updateHint();
        canvas.repaint();
    }
    private void updateHint() {
        int[][] t = textures[mode.ordinal()];
        if (t.length * t[0].length == 1) hintBar.setHint(L("te.hint_flat"));
        else hintBar.setHint(L(switch (currentTool) { case BRUSH -> "te.hint_brush"; case ERASER -> "te.hint_eraser"; case FILL -> "te.hint_fill"; case PICK -> "te.hint_pick"; }));
    }
    private void setMode(TextureMode newMode) { if (newMode != mode) showMode(newMode); }
    /** Switches the visible texture and refreshes every control that depends on it. */
    private void showMode(TextureMode newMode) {
        mode = newMode;
        int[][] t = textures[mode.ordinal()];
        canvas.setGrid(t[0].length, t.length);
        refreshAll();
    }
    private void refreshAll() {
        int[][] t = textures[mode.ordinal()];
        updatingControls = true;
        widthField.setText(String.valueOf(t[0].length));
        heightField.setText(String.valueOf(t.length));
        updatingControls = false;
        maxText.setRaw(fmt("te.max_size", MAX_TEXTURE_SIZE, maxHeightForMode(mode)));
        for (int i = 0; i < tabs.length; i++) tabs[i].setSelected(i == mode.ordinal());
        for (int i = 0; i < presetButtons.length; i++) presetButtons[i].setSelected(t[0].length == PRESETS[i] && t.length == PRESETS[i]);
        undoButton.setEnabled(!undoHistory.isEmpty());
        redoButton.setEnabled(!redoHistory.isEmpty());
        dirtyText.setRaw(dirty ? "● " + L("ed.unsaved") : "");
        updateHint();
        updateHover();
        for (ModeTab tab : tabs) tab.repaint();
        preview.repaint();
        canvas.repaint();
    }
    private void updateHover() {
        int x = canvas.getHoverX(), y = canvas.getHoverY();
        int[][] t = textures[mode.ordinal()];
        if (x < 0 || y < 0 || x >= t[0].length || y >= t.length) { hoverText.setRaw(""); return; }
        int c = t[y][x] == EMPTY_COLOR ? SAVED_EMPTY_COLOR : t[y][x] & 0xFFFFFF;
        hoverText.setRaw(String.format("x %d  y %d   #%06X", x, y, c));
    }
    private static int maxHeightForMode(TextureMode m) { return m == TextureMode.FINISH ? MAX_FINISH_HEIGHT : MAX_TEXTURE_SIZE; }
    //endregion

    //region Editing
    private void markChanged() {
        imageDirty[mode.ordinal()] = true;
        dirty = true;
        refreshAfterEdit();
    }
    private void refreshAfterEdit() {
        undoButton.setEnabled(!undoHistory.isEmpty());
        redoButton.setEnabled(!redoHistory.isEmpty());
        dirtyText.setRaw("● " + L("ed.unsaved"));
        updateHint();
        updateHover();
        for (ModeTab tab : tabs) tab.repaint();
        preview.repaint();
        canvas.repaint();
    }
    private void beginStroke() {
        strokeSnapshot = new Snapshot(mode.ordinal(), copyTexture(textures[mode.ordinal()]));
        strokePushed = false;
    }
    /** The first real change of a stroke puts the "before" picture on the undo stack, so empty clicks never create undo steps. */
    private void touch() {
        if (!strokePushed && strokeSnapshot != null) { pushUndo(strokeSnapshot); strokePushed = true; }
        markChanged();
    }
    private void pushUndo(Snapshot snapshot) {
        undoHistory.push(snapshot);
        while (undoHistory.size() > MAX_HISTORY) undoHistory.removeLast();
        redoHistory.clear();
    }
    private boolean stamp(int cx, int cy, int size, int color) {
        int[][] texture = textures[mode.ordinal()];
        int w = texture[0].length, h = texture.length;
        double radius = (size - 1) / 2.0, radiusSq = radius * radius + 0.0001;
        boolean changed = false;
        for (int y = Math.max(0, (int) Math.floor(cy - radius)); y <= Math.min(h - 1, (int) Math.ceil(cy + radius)); y++)
            for (int x = Math.max(0, (int) Math.floor(cx - radius)); x <= Math.min(w - 1, (int) Math.ceil(cx + radius)); x++) {
                double dx = x - cx, dy = y - cy;
                if (dx * dx + dy * dy <= radiusSq && texture[y][x] != color) { texture[y][x] = color; changed = true; }
            }
        return changed;
    }
    private boolean floodFill(int startX, int startY, int newColor) {
        int[][] texture = textures[mode.ordinal()];
        int w = texture[0].length, h = texture.length;
        int target = texture[startY][startX];
        if (target == newColor) return false;
        Deque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{startX, startY});
        while (!stack.isEmpty()) {
            int[] p = stack.pop();
            int x = p[0], y = p[1];
            if (x < 0 || y < 0 || x >= w || y >= h || texture[y][x] != target) continue;
            texture[y][x] = newColor;
            stack.push(new int[]{x + 1, y}); stack.push(new int[]{x - 1, y}); stack.push(new int[]{x, y + 1}); stack.push(new int[]{x, y - 1});
        }
        return true;
    }
    private void pickColor(int x, int y) {
        int[][] t = textures[mode.ordinal()];
        if (x < 0 || y < 0 || x >= t[0].length || y >= t.length) return;
        colorPicker.setColor(t[y][x] == EMPTY_COLOR ? SAVED_EMPTY_COLOR : t[y][x], true);
    }
    private void setTextureSize(int newWidth, int newHeight) {
        int m = mode.ordinal();
        newWidth = Math.clamp(newWidth, MIN_TEXTURE_SIZE, MAX_TEXTURE_SIZE);
        newHeight = Math.clamp(newHeight, MIN_TEXTURE_SIZE, maxHeightForMode(mode));
        int[][] old = textures[m];
        if (newWidth == old[0].length && newHeight == old.length) { refreshAll(); return; }
        pushUndo(new Snapshot(m, copyTexture(old)));
        textures[m] = resizePreservingData(old, newWidth, newHeight);
        imageDirty[m] = true;
        dirty = true;
        canvas.setGrid(newWidth, newHeight);
        refreshAll();
    }
    private void applyFields() {
        if (updatingControls) return;
        int[][] t = textures[mode.ordinal()];
        try { setTextureSize(Integer.parseInt(widthField.getText().trim()), Integer.parseInt(heightField.getText().trim())); }
        catch (NumberFormatException ignored) { refreshAll(); }
        if (t != textures[mode.ordinal()]) hintBar.flash(Tone.OK, fmt("te.size_changed", textures[mode.ordinal()][0].length, textures[mode.ordinal()].length));
    }
    private void resetCurrent() {
        int answer = ask(this, currentStyle, "ed.confirm_title", fmt("te.reset_confirm", L(MODE_KEYS[mode.ordinal()])), 1, "te.reset_yes", "cancel");
        if (answer != 0) return;
        int m = mode.ordinal();
        pushUndo(new Snapshot(m, copyTexture(textures[m])));
        int original = getOriginalColorForMode(mode);
        textures[m] = new int[][]{{original}};
        imageDirty[m] = true;
        dirty = true;
        colorPicker.setColor(original, true);
        canvas.setGrid(1, 1);
        refreshAll();
    }
    private void undo() {
        if (undoHistory.isEmpty()) return;
        Snapshot s = undoHistory.pop();
        redoHistory.push(new Snapshot(s.mode, copyTexture(textures[s.mode])));
        restore(s);
    }
    private void redo() {
        if (redoHistory.isEmpty()) return;
        Snapshot s = redoHistory.pop();
        undoHistory.push(new Snapshot(s.mode, copyTexture(textures[s.mode])));
        restore(s);
    }
    private void restore(Snapshot s) {
        textures[s.mode] = copyTexture(s.data);
        imageDirty[s.mode] = true;
        dirty = true;
        mode = TextureMode.values()[s.mode]; //jump to the texture that was changed so the user sees what happened
        canvas.setGrid(textures[s.mode][0].length, textures[s.mode].length);
        refreshAll();
    }
    //endregion

    //region Images
    private BufferedImage image(int m) {
        int[][] t = textures[m];
        int w = t[0].length, h = t.length;
        BufferedImage img = images[m];
        if (img == null || img.getWidth() != w || img.getHeight() != h) { img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB); images[m] = img; imageDirty[m] = true; }
        if (imageDirty[m]) {
            int[] data = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) data[y * w + x] = t[y][x] == EMPTY_COLOR ? SAVED_EMPTY_COLOR : t[y][x] & 0xFFFFFF;
            imageDirty[m] = false;
        }
        return img;
    }
    /** Draws an image scaled to fit a box, keeping its proportions. Pixels stay sharp when enlarged. */
    private static Rectangle drawFitted(Graphics2D g, BufferedImage img, int x, int y, int boxW, int boxH) {
        double scale = Math.min((double) boxW / img.getWidth(), (double) boxH / img.getHeight());
        int w = Math.max(1, (int) Math.round(img.getWidth() * scale)), h = Math.max(1, (int) Math.round(img.getHeight() * scale));
        int dx = x + (boxW - w) / 2, dy = y + (boxH - h) / 2;
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, scale >= 1 ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR : RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, dx, dy, w, h, null);
        return new Rectangle(dx, dy, w, h);
    }
    private void importImage() {
        JFileChooser chooser = new JFileChooser();
        java.util.List<String> extensions = new ArrayList<>();
        for (String ext : ImageIO.getReaderFileSuffixes()) if (ext != null && !ext.isBlank() && !extensions.contains(ext.toLowerCase(Locale.ROOT))) extensions.add(ext.toLowerCase(Locale.ROOT));
        if (extensions.isEmpty()) extensions.add("png");
        chooser.setFileFilter(new FileNameExtensionFilter(L("te.import_image") + " (" + String.join(", ", extensions) + ")", extensions.toArray(new String[0])));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            BufferedImage source = ImageIO.read(chooser.getSelectedFile());
            if (source == null) throw new IOException(L("te.bad_image"));
            BufferedImage fitted = fitImageToBounds(source, MAX_TEXTURE_SIZE, maxHeightForMode(mode));
            int m = mode.ordinal();
            pushUndo(new Snapshot(m, copyTexture(textures[m])));
            textures[m] = imageToTexture(fitted);
            imageDirty[m] = true;
            dirty = true;
            canvas.setGrid(fitted.getWidth(), fitted.getHeight());
            refreshAll();
            hintBar.flash(Tone.OK, fmt("te.imported", fitted.getWidth(), fitted.getHeight()));
        }
        catch (IOException ex) { inform(this, currentStyle, "te.import_error", L("te.could_not_import") + "\n" + ex.getMessage()); }
    }
    private BufferedImage fitImageToBounds(BufferedImage source, int maxWidth, int maxHeight) {
        int width = source.getWidth(), height = source.getHeight();
        if (width <= maxWidth && height <= maxHeight) return source;
        double scale = Math.min((double) maxWidth / width, (double) maxHeight / height);
        int newWidth = Math.max(1, (int) Math.floor(width * scale)), newHeight = Math.max(1, (int) Math.floor(height * scale));
        BufferedImage scaled = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = scaled.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
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

    //region Saving
    private boolean saveWithDialog() {
        try {
            saveTexturesToTmp();
            dirty = false;
            dirtyText.setRaw("");
            hintBar.flash(Tone.OK, L("ed.saved_ok"));
            return true;
        } catch (IOException ex) {
            inform(this, currentStyle, "save_error", L("te.could_not_save") + "\n" + ex.getMessage());
            return false;
        }
    }
    private void exitToMainMenu() {
        if (dirty) {
            int answer = ask(this, currentStyle, "ed.unsaved_title", L("ed.unsaved_msg"), 0, "ed.save_and_leave", "ed.leave_without_saving", "ed.keep_editing");
            if (answer == 0) { if (!saveWithDialog()) return; }
            else if (answer != 1) return;
        }
        RCJMS.instance.changeView(RCJMS.instance.mainMenuView = new MainMenuView(), "main_menu");
    }
    private void showHelp() { inform(this, currentStyle, "ed.help", L("te.help_text")); }
    //endregion

    //region Key Binds
    private void setupKeyBinds() {
        InputMap inputMap = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = getActionMap();
        bindKey(inputMap, actionMap, KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK), "undo", this::undo);
        bindKey(inputMap, actionMap, KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK), "redo", this::redo);
        bindKey(inputMap, actionMap, KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), "redo2", this::redo);
        bindKey(inputMap, actionMap, KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK), "save", this::saveWithDialog);
        bindKey(inputMap, actionMap, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "exitEditor", this::exitToMainMenu);
        bindKey(inputMap, actionMap, KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0), "help", this::showHelp);
        bindHotkey(inputMap, actionMap, KeyEvent.VK_1, () -> setMode(TextureMode.WALLS));
        bindHotkey(inputMap, actionMap, KeyEvent.VK_2, () -> setMode(TextureMode.FLOOR));
        bindHotkey(inputMap, actionMap, KeyEvent.VK_3, () -> setMode(TextureMode.CEILING));
        bindHotkey(inputMap, actionMap, KeyEvent.VK_4, () -> setMode(TextureMode.FINISH));
        bindHotkey(inputMap, actionMap, KeyEvent.VK_V, () -> selectTool(Tool.BRUSH));
        bindHotkey(inputMap, actionMap, KeyEvent.VK_N, () -> selectTool(Tool.ERASER));
        bindHotkey(inputMap, actionMap, KeyEvent.VK_B, () -> selectTool(Tool.FILL));
        bindHotkey(inputMap, actionMap, KeyEvent.VK_I, () -> selectTool(Tool.PICK));
        bindHotkey(inputMap, actionMap, KeyEvent.VK_OPEN_BRACKET, () -> sizeSlider.setValue(sizeSlider.getValue() - 1));
        bindHotkey(inputMap, actionMap, KeyEvent.VK_CLOSE_BRACKET, () -> sizeSlider.setValue(sizeSlider.getValue() + 1));
    }
    private void bindKey(InputMap inputMap, ActionMap actionMap, KeyStroke key, String name, Runnable action) {
        inputMap.put(key, name);
        actionMap.put(name, new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { action.run(); } });
    }
    private void bindHotkey(InputMap inputMap, ActionMap actionMap, int keyCode, Runnable action) {
        bindKey(inputMap, actionMap, KeyStroke.getKeyStroke(keyCode, 0), "hotkey" + keyCode, () -> {
            if (KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner() instanceof javax.swing.text.JTextComponent) return;
            action.run();
        });
    }
    //endregion

    //region Texture Saving And Loading
    public Path saveTexturesToTmp() throws IOException {
        Files.createDirectories(AppPaths.DATA_DIR);

        writeTexture(AppPaths.WALL_TEXTURE_FILE, textures[0]);
        writeTexture(AppPaths.FLOOR_TEXTURE_FILE, textures[1]);
        writeTexture(AppPaths.CEILING_TEXTURE_FILE, textures[2]);
        writeTexture(AppPaths.FINISH_TEXTURE_FILE, textures[3]);
        return AppPaths.DATA_DIR;
    }
    public static int[][] readTexture(Path file, int maxWidth, int maxHeight) throws IOException {
        RuntimeTexture texture = readRuntimeTexture(file, maxWidth, maxHeight);
        return texture.toMatrix();
    }

    public static RuntimeTexture readRuntimeTexture(Path file, int maxWidth, int maxHeight) throws IOException {
        if (!Files.exists(file)) return RuntimeTexture.empty();

        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            int magic = in.readInt();
            if (magic != TEXTURE_FILE_MAGIC) throw new IOException("Invalid RCTX file (bad signature): " + file);

            int version = in.readUnsignedByte();
            if (version != TEXTURE_FORMAT_VERSION) throw new IOException("Unsupported RCTX version " + version + ": " + file);

            int width = in.readUnsignedShort();
            int height = in.readUnsignedShort();
            int pixelFormat = in.readUnsignedByte();
            int flags = in.readUnsignedByte();
            int paletteSize = in.readUnsignedShort();
            int payloadLength = in.readInt();
            int rawLength = in.readInt();

            if (width <= 0 || height <= 0 || width > maxWidth || height > maxHeight) throw new IOException("Invalid RCTX dimensions: " + width + "x" + height + " in " + file);
            if (payloadLength < 0 || rawLength < 0 || rawLength > maxWidth * (long) maxHeight * 3L + 4096L) throw new IOException("Invalid RCTX payload size: " + file);
            if (pixelFormat != RCTX_PIXEL_RGB888 && pixelFormat != RCTX_PIXEL_PALETTE8) throw new IOException("Unsupported RCTX pixel format " + pixelFormat + ": " + file);
            if (pixelFormat == RCTX_PIXEL_PALETTE8 && (paletteSize < 1 || paletteSize > 256)) throw new IOException("Invalid RCTX palette size: " + paletteSize + ": " + file);
            if (pixelFormat == RCTX_PIXEL_RGB888 && paletteSize != 0) throw new IOException("Unexpected RCTX palette data: " + file);

            int expectedRawLength = pixelFormat == RCTX_PIXEL_PALETTE8 ? width * height : width * height * 3;
            if (rawLength != expectedRawLength) throw new IOException("Invalid RCTX raw size: " + file);

            int[] palette = null;
            if (pixelFormat == RCTX_PIXEL_PALETTE8) {
                palette = new int[paletteSize];
                for (int i = 0; i < paletteSize; i++) palette[i] = (in.readUnsignedByte() << 16) | (in.readUnsignedByte() << 8) | in.readUnsignedByte();
            }

            byte[] payload = in.readNBytes(payloadLength);
            if (payload.length != payloadLength) throw new IOException("RCTX file is truncated: " + file);

            byte[] raw = (flags & RCTX_FLAG_DEFLATE) != 0 ? inflate(payload, rawLength) : payload;
            if (raw.length != rawLength) throw new IOException("Invalid RCTX decompressed size: " + file);

            int[] pixels = new int[width * height];
            if (pixelFormat == RCTX_PIXEL_PALETTE8) {
                for (int i = 0; i < pixels.length; i++) {
                    int index = raw[i] & 0xFF;
                    if (index >= palette.length) throw new IOException("Invalid RCTX palette index: " + file);
                    pixels[i] = palette[index];
                }
            }
            else for (int i = 0, p = 0; i < pixels.length; i++) pixels[i] = ((raw[p++] & 0xFF) << 16) | ((raw[p++] & 0xFF) << 8) | (raw[p++] & 0xFF);
            return new RuntimeTexture(width, height, pixels);
        }
        catch (EOFException ex) { throw new IOException("RCTX file is truncated or corrupted: " + file, ex); }
    }
    private static byte[] inflate(byte[] compressed, int expectedLength) throws IOException {
        try (java.util.zip.InflaterInputStream inflater = new java.util.zip.InflaterInputStream(new ByteArrayInputStream(compressed));
             ByteArrayOutputStream out = new ByteArrayOutputStream(expectedLength)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inflater.read(buffer)) != -1) out.write(buffer, 0, read);
            return out.toByteArray();
        }
    }
    private static void writeTexture(Path file, int[][] texture) throws IOException {
        int width = isValidTexture(texture) ? texture[0].length : 0,  height = isValidTexture(texture) ? texture.length : 0;
        if (width <= 0 || height <= 0) throw new IOException("Cannot save an empty texture: " + file);

        int[] flat = new int[width * height];
        java.util.HashMap<Integer, Integer> paletteMap = new java.util.HashMap<>();
        int[] palette = new int[256];
        int paletteSize = 0;
        boolean palettePossible = true;

        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int color = texture[y][x] == EMPTY_COLOR ? SAVED_EMPTY_COLOR : texture[y][x] & 0xFFFFFF;
            flat[y * width + x] = color;
            if (palettePossible && !paletteMap.containsKey(color)) {
                if (paletteSize == 256) palettePossible = false;
                else { paletteMap.put(color, paletteSize); palette[paletteSize++] = color; }
            }
        }

        int pixelFormat = palettePossible ? RCTX_PIXEL_PALETTE8 : RCTX_PIXEL_RGB888;
        byte[] raw;
        if (pixelFormat == RCTX_PIXEL_PALETTE8) {
            raw = new byte[flat.length];
            for (int i = 0; i < flat.length; i++) raw[i] = (byte)(int)paletteMap.get(flat[i]);
        } else {
            raw = new byte[flat.length * 3];
            for (int i = 0, p = 0; i < flat.length; i++) {
                int color = flat[i];
                raw[p++] = (byte)(color >> 16);
                raw[p++] = (byte)(color >> 8);
                raw[p++] = (byte)color;
            }
        }

        byte[] compressed = deflate(raw);
        boolean useCompression = compressed.length < raw.length;
        byte[] payload = useCompression ? compressed : raw;
        int flags = useCompression ? RCTX_FLAG_DEFLATE : 0;

        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(file, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)))) {
            out.writeInt(TEXTURE_FILE_MAGIC);
            out.writeByte(TEXTURE_FORMAT_VERSION);
            out.writeShort(width);
            out.writeShort(height);
            out.writeByte(pixelFormat);
            out.writeByte(flags);
            out.writeShort(pixelFormat == RCTX_PIXEL_PALETTE8 ? paletteSize : 0);
            out.writeInt(payload.length);
            out.writeInt(raw.length);
            if (pixelFormat == RCTX_PIXEL_PALETTE8) for (int i = 0; i < paletteSize; i++) {
                int color = palette[i];
                out.writeByte(color >> 16); out.writeByte(color >> 8); out.writeByte(color);
            }
            out.write(payload);
        }
    }
    private static byte[] deflate(byte[] raw) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(raw.length);
        try (java.util.zip.DeflaterOutputStream deflater = new java.util.zip.DeflaterOutputStream(out)) { deflater.write(raw); }
        return out.toByteArray();
    }
    public void loadTexturesFromTmp() throws IOException {
        Path[] files = {AppPaths.WALL_TEXTURE_FILE, AppPaths.FLOOR_TEXTURE_FILE, AppPaths.CEILING_TEXTURE_FILE, AppPaths.FINISH_TEXTURE_FILE};
        for (int i = 0; i < files.length; i++) {
            int maxHeight = i == TextureMode.FINISH.ordinal() ? MAX_FINISH_HEIGHT : MAX_TEXTURE_SIZE;
            int[][] loaded = readTexture(files[i], MAX_TEXTURE_SIZE, maxHeight);
            if (!isValidTexture(loaded)) continue; //no saved file yet: keep the game's flat default color
            int[] size = sizeOf(loaded, MAX_TEXTURE_SIZE, maxHeight);
            textures[i] = normalizeTexture(loaded, size[0], size[1]);
        }
        Arrays.fill(imageDirty, true);
        undoHistory.clear();
        redoHistory.clear();
        dirty = false;
        showMode(mode);
    }
    //endregion

    //region Helpers
    private static int[][] createEmptyTexture(int width, int height) {
        int[][] texture = new int[height][width];
        for (int[] row : texture) Arrays.fill(row, EMPTY_COLOR);
        return texture;
    }
    private int[] sizeOf(int[][] texture, int maxWidth, int maxHeight) {
        if (isValidTexture(texture)) return new int[]{Math.clamp(texture[0].length, MIN_TEXTURE_SIZE, maxWidth), Math.clamp(texture.length, MIN_TEXTURE_SIZE, maxHeight)};
        return new int[]{8, 8};
    }
    private int[][] resizePreservingData(int[][] source, int newWidth, int newHeight) {
        int[][] result = createEmptyTexture(newWidth, newHeight);
        if (source == null) return result;
        int flat = flatColor(source);
        if (flat != EMPTY_COLOR) for (int[] row : result) Arrays.fill(row, flat); //a plain one-color texture keeps its color when it grows
        int copyHeight = Math.min(source.length, newHeight);
        for (int y = 0; y < copyHeight; y++) {
            if (source[y] == null) continue;
            System.arraycopy(source[y], 0, result[y], 0, Math.min(source[y].length, newWidth));
        }
        return result;
    }
    /** The color of a texture that is made of one single color, or EMPTY_COLOR when it has details. */
    private static int flatColor(int[][] texture) {
        int first = texture[0][0];
        if (first == EMPTY_COLOR) return EMPTY_COLOR;
        for (int[] row : texture) for (int c : row) if (c != first) return EMPTY_COLOR;
        return first;
    }
    private int getOriginalColorForMode(TextureMode m) {
        return switch (m) { case WALLS -> ORIGINAL_WALL_COLOR; case FLOOR -> ORIGINAL_FLOOR_COLOR; case CEILING -> ORIGINAL_CEILING_COLOR; case FINISH -> ORIGINAL_FINISH_COLOR; };
    }
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
    private static int[][] copyTexture(int[][] texture) {
        if (texture == null) return null;
        int[][] copy = new int[texture.length][];
        for (int y = 0; y < texture.length; y++) copy[y] = texture[y] == null ? null : texture[y].clone();
        return copy;
    }
    private Style loadTheme() { return SaveData.load().theme; }
    //endregion

    //region Nested Types
    public enum TextureMode {WALLS, FLOOR, CEILING, FINISH}
    private enum Tool {BRUSH, ERASER, FILL, PICK}
    private record Snapshot(int mode, int[][] data) {}

    public static final class RuntimeTexture {
        public final int width, height;
        public final int[] pixels;

        private RuntimeTexture(int width, int height, int[] pixels) { this.width = width; this.height = height; this.pixels = pixels; }
        static RuntimeTexture empty() { return new RuntimeTexture(0, 0, new int[0]); }
        int[][] toMatrix() {
            if (width == 0 || height == 0) return new int[0][0];
            int[][] result = new int[height][width];
            for (int y = 0; y < height; y++) System.arraycopy(pixels, y * width, result[y], 0, width);
            return result;
        }
    }

    /** One of the four big buttons above the picture: a live thumbnail, the name and the current size of a texture. */
    private final class ModeTab extends JComponent {
        private final TextureMode tabMode;
        private boolean selected, hover;

        ModeTab(TextureMode tabMode) {
            this.tabMode = tabMode;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(L(MODE_KEYS[tabMode.ordinal()]) + "   [" + (tabMode.ordinal() + 1) + "]");
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                @Override public void mouseReleased(MouseEvent e) { if (contains(e.getPoint())) setMode(tabMode); }
            });
        }
        void setSelected(boolean value) { if (selected != value) { selected = value; repaint(); } }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = smooth(g0);
            int w = getWidth(), h = getHeight();
            Theme theme = Theme.of(currentStyle);
            g.setColor(selected ? mix(theme.normal(), HILITE, 0.5f) : hover ? theme.hover() : theme.normal());
            g.fillRoundRect(0, 0, w - 1, h - 1, 12, 12);
            g.setColor(selected ? HILITE : theme.accent());
            g.setStroke(new BasicStroke(selected ? 2f : 1f));
            g.drawRoundRect(0, 0, w - 1, h - 1, 12, 12);
            int box = h - 14;
            Graphics2D clipped = (Graphics2D) g.create();
            clipped.setClip(new RoundRectangle2D.Double(7, 7, box, box, 6, 6));
            drawFitted(clipped, image(tabMode.ordinal()), 7, 7, box, box);
            clipped.dispose();
            g.setColor(new Color(0, 0, 0, 120));
            g.setStroke(new BasicStroke(1f));
            g.drawRoundRect(7, 7, box, box, 6, 6);
            int tx = 7 + box + 8, tw = w - tx - 6;
            int[][] t = textures[tabMode.ordinal()];
            g.setFont(font(13f, true));
            FontMetrics fm = g.getFontMetrics();
            g.setColor(Color.WHITE);
            g.drawString(ellipsize(L(MODE_KEYS[tabMode.ordinal()]), fm, tw), tx, h / 2 - 1);
            g.setFont(font(11f, false));
            g.setColor(selected ? new Color(225, 235, 255) : MUTED);
            g.drawString(t[0].length + " × " + t.length, tx, h / 2 + 14);
            g.dispose();
        }
    }

    /** Shows the current texture repeated 3 x 3 so it is easy to spot ugly seams. */
    private final class TilePreview extends JComponent {
        TilePreview() { setPreferredSize(new Dimension(100, 68)); }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = smooth(g0);
            BufferedImage img = image(mode.ordinal());
            int box = Math.min(getWidth(), getHeight());
            double scale = Math.min(box / (3.0 * img.getWidth()), box / (3.0 * img.getHeight()));
            int tw = Math.max(1, (int) Math.round(img.getWidth() * scale)), th = Math.max(1, (int) Math.round(img.getHeight() * scale));
            int x0 = (getWidth() - tw * 3) / 2, y0 = (getHeight() - th * 3) / 2;
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, scale >= 1 ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR : RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            for (int ty = 0; ty < 3; ty++) for (int tx = 0; tx < 3; tx++) g.drawImage(img, x0 + tx * tw, y0 + ty * th, tw, th, null);
            g.setColor(new Color(0, 0, 0, 140));
            g.drawRect(x0, y0, tw * 3, th * 3);
            g.dispose();
        }
    }

    private final class TextureCanvas extends GridCanvas {
        @Override protected void paintContent(Graphics2D g, int x0, int y0, int x1, int y1) {
            BufferedImage img = image(mode.ordinal());
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(img, px(x0), py(y0), px(x1 + 1), py(y1 + 1), x0, y0, x1 + 1, y1 + 1, null);
            if (cell >= 6) {
                g.setColor(new Color(0, 0, 0, 80));
                for (int x = x0; x <= x1 + 1; x++) g.drawLine(px(x), py(y0), px(x), py(y1 + 1));
                for (int y = y0; y <= y1 + 1; y++) g.drawLine(px(x0), py(y), px(x1 + 1), py(y));
            }
            g.setColor(new Color(0, 0, 0, 170));
            g.drawRect(px(0) - 1, py(0) - 1, px(cols) - px(0) + 1, py(rows) - py(0) + 1);
        }
        @Override protected void paintOverlay(Graphics2D g, int x0, int y0, int x1, int y1) {
            if (hoverX < 0) return;
            int size = currentTool == Tool.BRUSH ? brushSize : currentTool == Tool.ERASER ? eraserSize : 1;
            Color fill = currentTool == Tool.ERASER ? new Color(255, 255, 255, 150) : alpha(new Color(colorPicker.getColor()), 190);
            double radius = (size - 1) / 2.0, radiusSq = radius * radius + 0.0001;
            for (int y = Math.max(0, (int) Math.floor(hoverY - radius)); y <= Math.min(rows - 1, (int) Math.ceil(hoverY + radius)); y++)
                for (int x = Math.max(0, (int) Math.floor(hoverX - radius)); x <= Math.min(cols - 1, (int) Math.ceil(hoverX + radius)); x++) {
                    double dx = x - hoverX, dy = y - hoverY;
                    if (dx * dx + dy * dy > radiusSq) continue;
                    int rx = px(x), ry = py(y), rw = Math.max(1, px(x + 1) - rx), rh = Math.max(1, py(y + 1) - ry);
                    if (currentTool == Tool.BRUSH || currentTool == Tool.ERASER) { g.setColor(fill); g.fillRect(rx, ry, rw, rh); }
                    g.setColor(new Color(255, 255, 255, 230));
                    g.drawRect(rx, ry, rw - 1, rh - 1);
                    g.setColor(new Color(0, 0, 0, 160));
                    if (rw > 4) g.drawRect(rx + 1, ry + 1, rw - 3, rh - 3);
                }
        }
        @Override protected void pointerPressed(int x, int y, MouseEvent e) {
            if (!inGrid(x, y)) return;
            boolean right = SwingUtilities.isRightMouseButton(e);
            if (e.isAltDown() && !right) { pickColor(x, y); return; }
            Tool tool = right ? Tool.ERASER : currentTool;
            switch (tool) {
                case BRUSH, ERASER -> {
                    beginStroke();
                    strokeTool = tool;
                    strokeLastX = x; strokeLastY = y;
                    if (tool == Tool.BRUSH) colorPicker.remember();
                    if (stamp(x, y, tool == Tool.BRUSH ? brushSize : eraserSize, tool == Tool.BRUSH ? colorPicker.getColor() : EMPTY_COLOR)) touch();
                }
                case FILL -> {
                    beginStroke();
                    colorPicker.remember();
                    if (floodFill(x, y, colorPicker.getColor())) touch();
                }
                case PICK -> { pickColor(x, y); selectTool(Tool.BRUSH); }
            }
        }
        @Override protected void pointerDragged(int x, int y, MouseEvent e) {
            if (strokeTool == null || !inGrid(x, y)) return;
            int size = strokeTool == Tool.BRUSH ? brushSize : eraserSize, color = strokeTool == Tool.BRUSH ? colorPicker.getColor() : EMPTY_COLOR;
            boolean[] any = {false};
            line(strokeLastX, strokeLastY, x, y, (px, py) -> any[0] |= stamp(px, py, size, color)); //fill the gaps when the mouse moves fast
            strokeLastX = x; strokeLastY = y;
            if (any[0]) touch();
        }
        @Override protected void pointerReleased(int x, int y, MouseEvent e) { strokeTool = null; }
    }
    //endregion
}
