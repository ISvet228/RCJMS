package Project.ThemeCreator;
import Project.CreatorKit.*;

import Project.StyleUI.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

import static Project.CreatorKit.CkChrome.*;
import static Project.Helpers.EditorKit.*;

public final class ThemeCreatorView extends JPanel {
    //region Variables
    //region Panel Sizes
    private static final int W = ThemeScene.W, H = ThemeScene.H;
    private static final int LEFT_W = 278, RIGHT_W = 278, COL_W = 252, NOTE_W = 252;
    //endregion
    //region Gizmo Style
    private static final double ARROW_PX = 64, HANDLE_PX = 8, PICK_PX = 6;
    private static final Color X_AXIS = new Color(230, 80, 80), Y_AXIS = new Color(100, 210, 100);
    private static final Color GIZMO_ROT = new Color(240, 190, 60);
    private static final int[][] RECT_HANDLES = {{-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}};
    //endregion
    //region Editing Limits
    private static final int HISTORY_LIMIT = 80;
    private static final int PASTE_STEP = 24;
    //endregion
    //region Animation and Input
    private final javax.swing.Timer clock;
    private double time;
    private final KeyEventDispatcher keyInput = this::keyEvent;
    private final AWTEventListener inputFeed = this::feedInput;
    private int quiet;
    private long lastEscape;
    private static final long ESCAPE_TWICE_MS = 700;
    //endregion
    //region Components
    private final Style style;
    private final Runnable onExit;
    private final HintBar hint = new HintBar();
    private final Text title = new Text("tc.title", 18f, true, TEXT, 0);
    private final Text dirtyText = new Text(null, 12f, true, WARN, 0);
    private final SceneView scene = new SceneView();
    private final EnumMap<Tool, ToolButton> toolButtons = new EnumMap<>(Tool.class);
    private final NodeCanvas graphs;
    private final List<NodeKind> themeKinds;
    private final CkProject noGraph = new CkProject();
    private CkProject shownGraph = noGraph;
    private final JTree tree;
    private final DefaultTreeModel treeModel;
    private final StyledTextField themeName, objectName, layerField;
    private final StyledToggle visibleToggle;
    private final Column propsBody = new Column();
    private final JPanel savedBox = new JPanel();
    //endregion
    //region Document State
    private ThemeDoc doc;
    private String linked;
    private boolean inFile;
    private Object head = new Object(), savedHead;
    private final Deque<Step> undoStack = new ArrayDeque<>(), redoStack = new ArrayDeque<>();
    private String clipboard;
    //endregion
    //region Selection State
    private final Set<Integer> selection = new LinkedHashSet<>();
    private int selected;
    private ThemeObject inspected;
    private String inspectedKey;
    private final List<Runnable> inspectorSync = new ArrayList<>();
    //endregion
    //region Tree State
    private List<TreeRow> treeRows = List.of();
    //endregion
    //endregion

    //region Constructors
    public ThemeCreatorView(Style style, Runnable onExit) {
        super(new BorderLayout(8, 6));
        this.style = style;
        this.onExit = onExit;
        ThemeNodes.install();
        themeKinds = new ArrayList<>();
        for (String id : ThemeNodes.ids()) {
            NodeKind nodeKind = Nodes.find(id);
            if (nodeKind != null) themeKinds.add(nodeKind);
        }

        setBackground(BG);
        setOpaque(true);
        setBorder(new EmptyBorder(6, 8, 0, 8));

        graphs = new NodeCanvas(new CkProject(), this::snapshot, this::onGraphChanged, msg -> hint.flash(Tone.INFO, msg), this::arrangeGraph);
        graphs.setLiveText(id -> ThemeGraph.liveText(shownGraph, id));
        graphs.setStyle(style);
        graphs.setGroupsEnabled(false);
        graphs.setEmptyHint("tc.canvasEmpty", 0);
        graphs.setAddableKinds(() -> themeKinds);

        themeName = new StyledTextField(style, "");
        objectName = new StyledTextField(style, "");
        layerField = new StyledTextField(style, "0");
        visibleToggle = new StyledToggle(style, "tc.visible");

        tree = new JTree(new DefaultMutableTreeNode(""));
        treeModel = (DefaultTreeModel) tree.getModel();
        tree.setRootVisible(true);
        tree.setDragEnabled(!GraphicsEnvironment.isHeadless());
        tree.setDropMode(DropMode.ON_OR_INSERT);
        tree.setTransferHandler(new TreeMove());
        tree.setBackground(CARD);
        tree.setRowHeight(24);
        tree.setLargeModel(true);
        tree.setCellRenderer(new TreeCells());
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
        tree.addTreeSelectionListener(this::treeSelected);
        tree.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent mouseEvent) { if (mouseEvent.isPopupTrigger()) treeMenu(mouseEvent); }
            @Override public void mouseReleased(MouseEvent mouseEvent) { if (mouseEvent.isPopupTrigger()) treeMenu(mouseEvent); }
            @Override public void mouseClicked(MouseEvent mouseEvent) {
                if (mouseEvent.getClickCount() != 2 || SwingUtilities.isRightMouseButton(mouseEvent)) return;
                ThemeObject themeObject = objectAt(mouseEvent.getX(), mouseEvent.getY());
                if (themeObject != null) renameInDialog(themeObject);
            }
        });

        add(header(), BorderLayout.NORTH);
        add(CkChrome.sidebar(style, buildLeft(), LEFT_W), BorderLayout.WEST);
        add(buildCentre(), BorderLayout.CENTER);
        add(CkChrome.sidebar(style, buildRight(), RIGHT_W), BorderLayout.EAST);
        add(hint, BorderLayout.SOUTH);

        bindInspector();
        clock = new javax.swing.Timer(33, _ -> tick());
        clock.start();
        open(blankScene(), null, false);
        hint.setHint(L.t("tc.ready"));
    }
    //endregion

    //region Lifecycle
    @Override public void addNotify() {
        super.addNotify();
        if (!clock.isRunning()) clock.start();
        ThemeGpu.warmUp();
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keyInput);
        Toolkit.getDefaultToolkit().addAWTEventListener(inputFeed, AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
        SwingUtilities.invokeLater(scene::requestFocusInWindow);
    }
    @Override public void removeNotify() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(keyInput);
        Toolkit.getDefaultToolkit().removeAWTEventListener(inputFeed);
        ThemeInput.clear();
        clock.stop();
        super.removeNotify();
    }
    private void tick() {
        time += 1 / 30.0;
        trackPointer();
        ThemeInput.frameDone();
        scene.repaint();
        if (inspected != null) quiet(() -> {
            for (Runnable sync : inspectorSync) sync.run();
            ThemeObject shown = selectedObject();
            if (shown != null) visibleToggle.setSelected(liveValue(shown, "visible") instanceof Boolean b ? b : shown.visible);
        });
    }
    private boolean canLeave() {
        if (doc == null || !dirty()) return true;
        int answer = ask(this, style, "common.unsaved_title", L.t("tc.unsaved_msg"), 0, "common.save_and_leave", "common.leave_without_saving", "common.keep_editing");
        if (answer == 0) return save();
        return answer == 1;
    }
    private void exit() {
        if (!canLeave()) return;
        clock.stop();
        onExit.run();
    }
    //endregion

    //region Refresh
    private void refreshAll() {
        quiet(() -> {
            refreshTree();
            themeName.setText(doc.name);
            ThemeObject themeObject = selectedObject();
            CkProject ckProject = themeObject == null ? noGraph : themeObject.graph;
            if (ckProject != shownGraph) {
                shownGraph = ckProject;
                graphs.setProject(ckProject);
            }
        });
        refreshInspector();
        refreshDirty();
        scene.repaint();
    }
    private void quiet(Runnable r) {
        quiet++;
        try { r.run(); }
        finally { quiet--; }
    }
    private void refreshDirty() { dirtyText.setRaw(doc != null && dirty() ? L.t("tc.unsaved") : ""); }
    private void refreshSaved() {
        savedBox.removeAll();
        for (String id : ThemeStore.ids()) {
            JComponent component = listButton(id, () -> openSaved(id));
            component.addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent mouseEvent) {
                    if (SwingUtilities.isRightMouseButton(mouseEvent)) savedMenu(id).show(component, mouseEvent.getX(), mouseEvent.getY());
                }
            });
            savedBox.add(component);
            savedBox.add(Box.createVerticalStrut(3));
        }
        savedBox.revalidate();
        savedBox.repaint();
    }
    //endregion

    //region Panels
    private JComponent header() {
        ToolButton back = new ToolButton(style, ToolButton.Layout.ROW, Glyph.BACK, "back").tip("back", null).size(104, 36);
        back.onClick(this::exit);
        title.setPreferredSize(new Dimension(220, 36));
        dirtyText.setPreferredSize(new Dimension(140, 36));
        ToolButton created = CkChrome.icon(style, Glyph.NEW, "tc.newSceneTip", null, this::newScene);
        ToolButton undo = CkChrome.icon(style, Glyph.UNDO, "tc.undoTip", "Ctrl+Z", this::undo);
        ToolButton redo = CkChrome.icon(style, Glyph.REDO, "tc.redoTip", "Ctrl+Y", this::redo);
        ToolButton arrange = CkChrome.icon(style, Glyph.FIT, "ec.ui.arrangeTip", null, this::arrangeGraph);
        ToolButton delete = CkChrome.icon(style, Glyph.TRASH, "tc.deleteTip", "Del", this::deleteSelected);
        ToolButton save = new ToolButton(style, ToolButton.Layout.ROW, Glyph.SAVE, "tc.save").tip("tc.saveTip", "Ctrl+S")
                .accent(new Color(46, 150, 96)).size(120, 36);
        save.onClick(this::save);

        return new CkChrome.TopBar(
                List.of(back, title, dirtyText),
                List.of(undo, redo, arrange, created, delete, save),
                List.of(new CkChrome.Labelled(save, L.t("tc.save"), 120)),
                List.of(title, dirtyText));
    }
    private JComponent toolStrip() {
        Tool[] tools = Tool.values();
        Glyph[] glyphs = {Glyph.HAND_TOOL, Glyph.MOVE_TOOL, Glyph.ROTATE_TOOL, Glyph.SCALE_TOOL, Glyph.RECT_TOOL};
        String[] keys = {"Q", "W", "E", "R", "T"};
        Box strip = Box.createVerticalBox();
        strip.setAlignmentY(Component.TOP_ALIGNMENT);
        ToolButton[] buttons = new ToolButton[tools.length];
        for (int i = 0; i < tools.length; i++) {
            Tool tool = tools[i];
            ToolButton button = new ToolButton(style, ToolButton.Layout.ICON, glyphs[i], null)
                    .tip("tc.tool." + tool.name().toLowerCase(Locale.ROOT), keys[i]).size(34, 34);
            button.setMaximumSize(button.getPreferredSize());
            button.setAlignmentX(Component.LEFT_ALIGNMENT);
            button.onClick(() -> chooseTool(tool));
            buttons[i] = button;
            toolButtons.put(tool, button);
            strip.add(button);
            if (i < tools.length - 1) strip.add(Box.createVerticalStrut(4));
        }
        ToolButton.group(buttons);
        chooseTool(Tool.MOVE);
        JPanel holder = new JPanel(new BorderLayout());
        holder.setOpaque(false);
        holder.add(strip, BorderLayout.NORTH);
        return holder;
    }
    private JComponent listButton(String text, Runnable action) {
        ToolButton toolButton = new ToolButton(style, ToolButton.Layout.ROW, Glyph.NEW, null).size(COL_W, 30);
        toolButton.setLabel(text);
        toolButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        toolButton.onClick(action);
        return toolButton;
    }
    private static Note note(int width, float size, String text) {
        Note note = new Note(width, size, MUTED);
        note.set(null, MUTED, text);
        return note;
    }
    private Column buildLeft() {
        Column col = new Column();
        Fold templates = new Fold("tc.cardTemplates", true, false);
        for (ThemeDoc themeDoc : ThemeOriginals.templates()) {
            String id = themeDoc.id;
            templates.body.put(listButton(themeDoc.name, () -> openTemplate(id)), 3);
        }
        col.put(templates, 0);

        Fold originals = new Fold("tc.cardOriginals", true, false);
        for (ThemeDoc themeDoc : ThemeOriginals.originals()) {
            String id = themeDoc.id;
            originals.body.put(listButton(themeDoc.name, () -> openOriginal(id)), 3);
        }
        col.put(originals, 12);

        Fold saved = new Fold("tc.cardSaved", true, true);
        savedBox.setOpaque(false);
        savedBox.setLayout(new BoxLayout(savedBox, BoxLayout.Y_AXIS));
        savedBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        saved.body.put(savedBox, 0);
        col.put(saved, 12);

        Fold nodes = new Fold("tc.cardNodes", true, true);
        nodes.body.put(note(COL_W, 11f, L.t("tc.nodesHint")), 0);
        nodes.body.put(new NodePalette(style, ThemeNodes.categories(), () -> themeKinds, this::addNode), 6);
        col.put(nodes, 12);
        refreshSaved();
        return col;
    }
    private Column buildRight() {
        Column col = new Column();
        Card theme = new Card("tc.cardTheme");
        theme.put(fieldLabel("tc.themeName"), 0);
        themeName.setPreferredSize(new Dimension(COL_W, 30));
        themeName.setMaximumSize(new Dimension(COL_W, 30));
        theme.put(themeName, 2);
        col.put(theme, 0);

        Card treeCard = new Card("tc.cardTree");
        StyledScrollPane treeScroll = new StyledScrollPane(tree, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER, style);
        treeScroll.setPreferredSize(new Dimension(COL_W, 240));
        treeScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 240));
        treeScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        treeCard.put(treeScroll, 0);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        buttons.setOpaque(false);
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        ToolButton add = new ToolButton(style, ToolButton.Layout.ROW, Glyph.PLUS, "tc.addObject").size(116, 30);
        add.onClick(() -> {
            JPopupMenu menu = popup();
            addTypeItems(menu, type -> addObject(type, 0, null));
            menu.show(add, 0, add.getHeight());
        });
        ToolButton remove = new ToolButton(style, ToolButton.Layout.ROW, Glyph.TRASH, "tc.deleteObject").size(116, 30);
        remove.onClick(this::deleteSelected);
        buttons.add(add);
        buttons.add(remove);
        treeCard.put(buttons, 6);
        treeCard.put(note(NOTE_W, 11f, L.t("tc.treeTip")), 4);
        col.put(treeCard, 12);

        Card object = new Card("tc.cardObject");
        object.put(fieldLabel("tc.objectName"), 0);
        objectName.setPreferredSize(new Dimension(COL_W, 30));
        objectName.setMaximumSize(new Dimension(COL_W, 30));
        object.put(objectName, 2);
        object.put(fieldLabel("tc.layer"), 8);
        layerField.setPreferredSize(new Dimension(COL_W, 30));
        layerField.setMaximumSize(new Dimension(COL_W, 30));
        object.put(layerField, 2);
        object.put(note(NOTE_W, 11f, L.t("tc.layerTip")), 4);
        visibleToggle.setPreferredSize(new Dimension(COL_W, 32));
        object.put(visibleToggle, 8);
        propsBody.setAlignmentX(Component.LEFT_ALIGNMENT);
        object.put(propsBody, 10);
        col.put(object, 12);
        return col;
    }
    private JComponent buildCentre() {
        JPanel sceneBox = new JPanel(new BorderLayout(6, 0));
        sceneBox.setOpaque(false);
        sceneBox.add(toolStrip(), BorderLayout.WEST);
        sceneBox.add(scene, BorderLayout.CENTER);

        StyledScrollPane graphScroll = new StyledScrollPane(graphs, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED, style);
        graphScroll.setBorder(BorderFactory.createLineBorder(CARD_LINE));
        JPanel graphBox = new JPanel(new BorderLayout(0, 4));
        graphBox.setOpaque(false);
        graphBox.add(new Text("tc.graphTitle", 12f, true, TEXT, 0), BorderLayout.NORTH);
        graphBox.add(graphScroll, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, sceneBox, graphBox);
        split.setResizeWeight(0.5);
        split.setDividerSize(8);
        split.setBorder(null);
        split.setOpaque(false);
        split.setContinuousLayout(true);
        return split;
    }
    //endregion

    //region Menus
    private static JPopupMenu popup() {
        JPopupMenu popupMenu = new JPopupMenu();
        popupMenu.setBackground(CARD);
        popupMenu.setBorder(BorderFactory.createLineBorder(CARD_LINE));
        return popupMenu;
    }
    private static JMenu typeMenu(String text, Consumer<String> action) {
        JMenu sub = new JMenu(text);
        sub.setBackground(CARD);
        sub.setForeground(TEXT);
        sub.setFont(font(12f, false));
        sub.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        sub.getPopupMenu().setBackground(CARD);
        sub.getPopupMenu().setBorder(BorderFactory.createLineBorder(CARD_LINE));
        addTypeItems(sub, action);
        return sub;
    }
    private static void addTypeItems(Container menu, Consumer<String> action) {
        for (String type : ThemeTypes.TYPES) menu.add(menuItem(L.t("tc.o." + type), () -> action.accept(type)));
    }
    private JPopupMenu savedMenu(String id) {
        JPopupMenu menu = popup();
        menu.add(menuItem("tc.menuOpen", () -> openSaved(id)));
        menu.add(menuItem("tc.menuDelete", () -> deleteSaved(id)));
        return menu;
    }
    private JPopupMenu contextMenu(Point2D point2D, ThemeObject target, boolean inTree) {
        JPopupMenu popupMenu = popup();
        if (target != null) {
            if (inTree) popupMenu.add(menuItem("tc.menuRename", () -> renameInDialog(target)));
            popupMenu.add(menuItem("tc.menuDeleteObject", this::deleteSelected));
            popupMenu.addSeparator();
        }
        popupMenu.add(typeMenu("tc.menuNewObject", type -> addObject(type, 0, point2D)));
        if (target != null) popupMenu.add(typeMenu("tc.menuNewChild", type -> addObject(type, target.id, point2D)));
        popupMenu.addSeparator();
        if (target != null) popupMenu.add(menuItem("tc.menuCopy", this::copySelection));
        popupMenu.add(menuItem("tc.menuPaste", () -> pasteInto(target == null ? 0 : target.id, point2D)));
        return popupMenu;
    }
    private void treeMenu(MouseEvent mouseEvent) {
        ThemeObject themeObject = objectAt(mouseEvent.getX(), mouseEvent.getY());
        if (themeObject != null && !selection.contains(themeObject.id)) selectObject(themeObject.id);
        contextMenu(null, themeObject, true).show(tree, mouseEvent.getX(), mouseEvent.getY());
    }
    //endregion

    //region Keyboard
    private boolean keyEvent(KeyEvent e) {
        Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (owner == null || !SwingUtilities.isDescendingFrom(owner, this)) return false;
        boolean typing = owner instanceof javax.swing.text.JTextComponent;
        if (e.getID() == KeyEvent.KEY_PRESSED) {
            if (shortcut(e, owner, typing)) {
                e.consume();
                return true;
            }
            if (!typing) ThemeInput.keyDown(KeyEvent.getKeyText(e.getKeyCode()));
        }
        else if (e.getID() == KeyEvent.KEY_RELEASED && !typing) ThemeInput.keyUp(KeyEvent.getKeyText(e.getKeyCode()));
        return false;
    }
    private boolean shortcut(KeyEvent keyEvent, Component owner, boolean typing) {
        boolean ctrl = keyEvent.isControlDown() && !keyEvent.isAltDown();
        int k = keyEvent.getKeyCode();
        if (ctrl && k == KeyEvent.VK_S) { save(); return true; }
        if (k == KeyEvent.VK_ESCAPE) {
            if (graphs.editing() || owner instanceof StyledComboBox box && box.isPopupVisible()) return false;
            long now = System.currentTimeMillis();
            if (now - lastEscape < ESCAPE_TWICE_MS) {
                lastEscape = 0;
                exit();
            }
            else {
                lastEscape = now;
                escape(owner);
                hint.flash(Tone.INFO, L.t("tc.escAgain"));
            }
            return true;
        }
        if (typing) return false;
        Tool tool = switch (k) {
            case KeyEvent.VK_Q -> Tool.HAND;
            case KeyEvent.VK_W -> Tool.MOVE;
            case KeyEvent.VK_E -> Tool.ROTATE;
            case KeyEvent.VK_R -> Tool.SCALE;
            case KeyEvent.VK_T -> Tool.RECT;
            default -> null;
        };
        if (tool != null && !ctrl && !keyEvent.isAltDown()) chooseTool(tool);
        if (ctrl && k == KeyEvent.VK_Z && !keyEvent.isShiftDown()) { undo(); return true; }
        if (ctrl && (k == KeyEvent.VK_Y || k == KeyEvent.VK_Z)) { redo(); return true; }
        if (ctrl && k == KeyEvent.VK_C) { copySelection(); return true; }
        if (ctrl && k == KeyEvent.VK_V) { pasteInto(selected, null); return true; }
        if (k == KeyEvent.VK_DELETE) {
            if (owner == graphs) graphs.deleteSelected(); else deleteSelected();
            return true;
        }
        return false;
    }
    private void escape(Component owner) {
        if (owner == graphs && !graphs.selectedNodes().isEmpty()) graphs.selectNode(null);
        else selectObject(0);
    }
    private void feedInput(AWTEvent awtEvent) {
        if (!(awtEvent instanceof MouseEvent mouseEvent)) return;
        Component src = mouseEvent.getComponent();
        if (src == null || !SwingUtilities.isDescendingFrom(src, this)) return;
        Point point = SwingUtilities.convertPoint(src, mouseEvent.getPoint(), scene);
        Point2D point2D = scene.toDoc(point);
        ThemeInput.pointer(point2D.getX(), point2D.getY());
        if (mouseEvent.getID() == MouseEvent.MOUSE_PRESSED) ThemeInput.button(mouseEvent.getButton(), true);
        else if (mouseEvent.getID() == MouseEvent.MOUSE_RELEASED) ThemeInput.button(mouseEvent.getButton(), false);
    }
    //endregion

    //region Tools
    private void chooseTool(Tool next) {
        scene.setTool(next);
        ToolButton button = toolButtons.get(next);
        if (button != null) button.choose();
        scene.repaint();
    }
    private void trackPointer() {
        java.awt.Point screen = ThemeInput.screenPointer();
        if (screen == null || !scene.isShowing()) return;
        try {
            Point origin = scene.getLocationOnScreen();
            Point2D point2D = scene.toDoc(new Point(screen.x - origin.x, screen.y - origin.y));
            ThemeInput.pointer(point2D.getX(), point2D.getY());
        } catch (java.awt.IllegalComponentStateException _) { }
    }
    private enum Tool { HAND, MOVE, ROTATE, SCALE, RECT }
    //endregion

    //region Scene View
    private final class SceneView extends JComponent {
        private double scale = 1;
        private int ox, oy;
        private double panX, panY;
        private Tool tool = Tool.MOVE;
        private ThemeRenderer.Frame frame;

        private char drag;
        private final List<Drag> drags = new ArrayList<>();
        private boolean dragChanged;
        private int pendingSingle;
        private Gesture gesture;
        private Point panAt;

        SceneView() {
            setFocusable(true);
            setPreferredSize(new Dimension(640, 360));
            setMinimumSize(new Dimension(240, 135));
            MouseAdapter mouse = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent mouseEvent) {
                    requestFocusInWindow();
                    ThemeInput.button(mouseEvent.getButton(), true);
                    Point2D point2D = toDoc(mouseEvent.getPoint());
                    ThemeInput.pointer(point2D.getX(), point2D.getY());
                    ThemeRenderer.Frame f = ThemeRenderer.frame(doc, time);
                    frame = f;
                    if (mouseEvent.isPopupTrigger() || SwingUtilities.isRightMouseButton(mouseEvent)) {
                        ThemeObject hit = ThemeScene.pick(doc, f, point2D, PICK_PX / scale);
                        if (hit != null && !selection.contains(hit.id)) selectObject(hit.id);
                        contextMenu(point2D, hit, false).show(SceneView.this, mouseEvent.getX(), mouseEvent.getY());
                        return;
                    }
                    if (mouseEvent.getButton() != MouseEvent.BUTTON1) return;
                    if (tool == Tool.HAND) {
                        if (mouseEvent.getClickCount() == 2) {
                            panX = 0;
                            panY = 0;
                            repaint();
                        } else {
                            panAt = mouseEvent.getPoint();
                        }
                        return;
                    }
                    ThemeObject sel = selectedObject();
                    if (sel != null && f.world.get(sel.id) != null) {
                        if (tool == Tool.MOVE) {
                            AffineTransform world = f.world.get(sel.id);
                            Point2D origin = origin(world);
                            Point2D[] handle = ThemeScene.handle(ThemeScene.parentWorld(doc, f, sel), origin, ARROW_PX / scale);
                            char h = ThemeScene.handleAt(handle, point2D, HANDLE_PX / scale);
                            if (h != 0) {
                                startDrag(h, f, point2D);
                                return;
                            }
                        }
                        else if (startGesture(sel, f, point2D)) return;

                    }
                    ThemeObject hit = ThemeScene.pick(doc, f, point2D, PICK_PX / scale);
                    boolean toggle = mouseEvent.isControlDown() || mouseEvent.isMetaDown();
                    if (hit == null) { if (!toggle && !mouseEvent.isShiftDown()) selectObject(0); }
                    else if (toggle) { toggleSelection(hit.id); }
                    else if (mouseEvent.isShiftDown()) { addToSelection(hit.id); }
                    else if (selection.contains(hit.id) && selection.size() > 1) { pendingSingle = hit.id; }
                    else { selectObject(hit.id); }
                }
                @Override public void mouseMoved(MouseEvent mouseEvent) {
                    Point2D point2D = toDoc(mouseEvent.getPoint());
                    ThemeInput.pointer(point2D.getX(), point2D.getY());
                }
                @Override public void mouseDragged(MouseEvent mouseEvent) {
                    Point2D point2D = toDoc(mouseEvent.getPoint());
                    ThemeInput.pointer(point2D.getX(), point2D.getY());
                    if (panAt != null) {
                        panX += mouseEvent.getX() - panAt.x;
                        panY += mouseEvent.getY() - panAt.y;
                        panAt = mouseEvent.getPoint();
                        repaint();
                        return;
                    }
                    if (gesture != null && frame != null) {
                        if (!dragChanged) {
                            snapshot();
                            dragChanged = true;
                        }
                        applyGesture(point2D, mouseEvent.isShiftDown());
                        refreshInspector();
                        repaint();
                        return;
                    }
                    if (drag == 0 || drags.isEmpty() || frame == null) return;
                    if (!dragChanged) {
                        snapshot();
                        dragChanged = true;
                    }
                    for (Drag d : drags) {
                        Point2D local = ThemeScene.toParentSpace(doc, frame, d.o(), point2D);
                        if (drag != 'y') d.o().props.put("x", round(d.x0() + local.getX() - d.startLocal().getX()));
                        if (drag != 'x') d.o().props.put("y", round(d.y0() + local.getY() - d.startLocal().getY()));
                    }
                    refreshInspector();
                    repaint();
                }
                @Override public void mouseReleased(MouseEvent mouseEvent) {
                    ThemeInput.button(mouseEvent.getButton(), false);
                    boolean changed = dragChanged;
                    int single = pendingSingle;
                    drag = 0;
                    drags.clear();
                    gesture = null;
                    panAt = null;
                    dragChanged = false;
                    pendingSingle = 0;
                    if (changed) refreshAll();
                    else if (single != 0) selectObject(single);
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
            addMouseWheelListener(wheel -> {
                if (drag == 0 && gesture == null) return;
                JScrollPane pane = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, tree);
                if (pane == null) return;
                JScrollBar bar = pane.getVerticalScrollBar();
                bar.setValue(bar.getValue() + wheel.getUnitsToScroll() * bar.getUnitIncrement());
            });
        }
        void setTool(Tool next) {
            tool = next;
            gesture = null;
            panAt = null;
            setCursor(Cursor.getPredefinedCursor(next == Tool.HAND ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            repaint();
        }
        private void startDrag(char h, ThemeRenderer.Frame frame, Point2D point2D) {
            drags.clear();
            for (ThemeObject themeObject : selectedObjects()) {
                if (frame.world.get(themeObject.id) == null) continue;
                drags.add(new Drag(themeObject, ThemeScene.toParentSpace(doc, frame, themeObject, point2D), prop(frame.props.get(themeObject.id), "x"), prop(frame.props.get(themeObject.id), "y")));
            }
            drag = h;
            dragChanged = false;
        }
        private boolean startGesture(ThemeObject sel, ThemeRenderer.Frame f, Point2D point2D) {
            AffineTransform world = f.world.get(sel.id);
            Map<String, Object> p = f.props.get(sel.id);
            double tol = HANDLE_PX / scale;
            Part part = null;
            int ex = 0, ey = 0;
            char axis = 0;
            if (tool == Tool.ROTATE) {
                if (Math.abs(origin(world).distance(point2D) - ARROW_PX / scale) <= tol) part = Part.ROTATE;
            } else if (tool == Tool.RECT && sized(sel.type)) {
                Point2D[] corners = rectHandles(world, p);
                for (int i = 0; i < corners.length; i++) {
                    if (corners[i].distance(point2D) <= tol) {
                        part = Part.RECT;
                        ex = RECT_HANDLES[i][0];
                        ey = RECT_HANDLES[i][1];
                        break;
                    }
                }
            } else if (tool == Tool.SCALE || tool == Tool.RECT) {
                char h = ThemeScene.handleAt(scaleHandles(world), point2D, tol);
                if (h == 'x' || h == 'y' || h == 'c') {
                    part = Part.SCALE;
                    axis = h;
                }
            }
            if (part == null) return false;
            gesture = new Gesture(part, axis, ex, ey, sel, new HashMap<>(sel.props), world, point2D, ThemeScene.toParentSpace(doc, f, sel, point2D));
            drags.clear();
            drag = 0;
            dragChanged = false;
            return true;
        }
        private void applyGesture(Point2D p, boolean snap) {
            Gesture g = gesture;
            Map<String, Object> s = g.start();
            ThemeObject o = g.o();
            switch (g.part()) {
                case ROTATE -> {
                    Point2D local = ThemeScene.toParentSpace(doc, frame, o, p);
                    double px = ThemeRenderer.d(s, "x", 0), py = ThemeRenderer.d(s, "y", 0);
                    double turn = Math.atan2(local.getY() - py, local.getX() - px) - Math.atan2(g.startLocal().getY() - py, g.startLocal().getX() - px);
                    double rot = ThemeRenderer.d(s, "rot", 0) + Math.toDegrees(turn);
                    if (snap) rot = Math.round(rot / 15) * 15;
                    o.props.put("rot", round(rot));
                }
                case SCALE -> {
                    double sx0 = ThemeRenderer.d(s, "scale", 1), sy0 = ThemeRenderer.d(s, "scaleY", sx0);
                    Point2D origin = origin(g.world());
                    double sx = sx0, sy = sy0;
                    if (g.axis() == 'c') {
                        double before = origin.distance(g.startDoc());
                        if (before < 1e-6) return;
                        double k = origin.distance(p) / before;
                        sx = sx0 * k;
                        sy = sy0 * k;
                    } else {
                        Point2D dir = unit(g.world().deltaTransform(g.axis() == 'x' ? new Point2D.Double(1, 0) : new Point2D.Double(0, 1), null));
                        double before = along(g.startDoc(), origin, dir);
                        if (Math.abs(before) < 1e-6) return;
                        double k = along(p, origin, dir) / before;
                        if (g.axis() == 'x') sx = sx0 * k;
                        else sy = sy0 * k;
                    }
                    o.props.put("scale", fine(sx));
                    o.props.put("scaleY", fine(sy));
                }
                case RECT -> {
                    double w0 = ThemeRenderer.d(s, "w", 100), h0 = ThemeRenderer.d(s, "h", 100);
                    Point2D lp;
                    try { lp = g.world().createInverse().transform(p, null); }
                    catch (NoninvertibleTransformException e) { return; }
                    double shiftX = 0, shiftY = 0, nw = w0, nh = h0;
                    if (g.ex() > 0) nw = Math.max(1, lp.getX());
                    else if (g.ex() < 0) {
                        shiftX = Math.min(lp.getX(), w0 - 1);
                        nw = w0 - shiftX;
                    }
                    if (g.ey() > 0) nh = Math.max(1, lp.getY());
                    else if (g.ey() < 0) {
                        shiftY = Math.min(lp.getY(), h0 - 1);
                        nh = h0 - shiftY;
                    }
                    if (g.ex() != 0) o.props.put("w", round(nw));
                    if (g.ey() != 0) o.props.put("h", round(nh));
                    if (shiftX != 0 || shiftY != 0) {
                        double sx = ThemeRenderer.d(s, "scale", 1);
                        AffineTransform m = new AffineTransform();
                        m.translate(ThemeRenderer.d(s, "x", 0), ThemeRenderer.d(s, "y", 0));
                        m.rotate(Math.toRadians(ThemeRenderer.d(s, "rot", 0)));
                        m.scale(sx, ThemeRenderer.d(s, "scaleY", sx));
                        Point2D at = m.transform(new Point2D.Double(shiftX, shiftY), null);
                        o.props.put("x", round(at.getX()));
                        o.props.put("y", round(at.getY()));
                    }
                }
            }
        }
        private enum Part { ROTATE, SCALE, RECT }
        private record Drag(ThemeObject o, Point2D startLocal, double x0, double y0) { }
        private record Gesture(Part part, char axis, int ex, int ey, ThemeObject o, Map<String, Object> start, AffineTransform world, Point2D startDoc, Point2D startLocal) { }

        private void fit() {
            scale = Math.max(0.05, Math.min((double) getWidth() / W, (double) getHeight() / H));
            ox = (int) Math.round((getWidth() - W * scale) / 2 + panX);
            oy = (int) Math.round((getHeight() - H * scale) / 2 + panY);
        }
        private Point2D toDoc(Point point) { return new Point2D.Double((point.x - ox) / scale, (point.y - oy) / scale); }
        private static Point2D origin(AffineTransform world) { return world.transform(new Point2D.Double(0, 0), null); }
        private static Point2D unit(Point2D v) {
            double n = Math.hypot(v.getX(), v.getY());
            return n < 1e-9 ? new Point2D.Double(0, 0) : new Point2D.Double(v.getX() / n, v.getY() / n);
        }
        private static Point2D at(Point2D from, Point2D dir, double len) { return new Point2D.Double(from.getX() + dir.getX() * len, from.getY() + dir.getY() * len); }

        private Point2D[] scaleHandles(AffineTransform world) {
            Point2D o = origin(world);
            Point2D ux = unit(world.deltaTransform(new Point2D.Double(1, 0), null));
            Point2D uy = unit(world.deltaTransform(new Point2D.Double(0, 1), null));
            double len = ARROW_PX / scale;
            return new Point2D[]{o, at(o, ux, len), at(o, uy, len)};
        }
        private Point2D[] rectHandles(AffineTransform world, Map<String, Object> p) {
            double w = ThemeRenderer.d(p, "w", 100), h = ThemeRenderer.d(p, "h", 100);
            Point2D[] out = new Point2D[RECT_HANDLES.length];
            for (int i = 0; i < RECT_HANDLES.length; i++) {
                out[i] = world.transform(new Point2D.Double((RECT_HANDLES[i][0] + 1) / 2.0 * w, (RECT_HANDLES[i][1] + 1) / 2.0 * h), null);
            }
            return out;
        }
        private static double along(Point2D p, Point2D from, Point2D dir) { return (p.getX() - from.getX()) * dir.getX() + (p.getY() - from.getY()) * dir.getY(); }
        private static Rectangle2D square(Point2D c, double half) { return new Rectangle2D.Double(c.getX() - half, c.getY() - half, 2 * half, 2 * half); }

        @Override protected void paintComponent(Graphics graphics) {
            fit();
            Graphics2D g2d = (Graphics2D) graphics.create();
            try {
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(CANVAS_BG);
                g2d.fillRect(0, 0, getWidth(), getHeight());
                g2d.translate(ox, oy);
                g2d.scale(scale, scale);
                g2d.clipRect(0, 0, W, H);
                frame = ThemeRenderer.frame(doc, time);
                ThemeRenderer.paint(g2d, frame, W, H);
                g2d.setStroke(new BasicStroke((float) (1 / scale)));
                g2d.setColor(CARD_LINE);
                g2d.drawRect(0, 0, W, H);
                drawSelection(g2d);
            } finally { g2d.dispose(); }
        }
        private void drawSelection(Graphics2D g2d) {
            if (frame == null) return;
            for (ThemeObject themeObject : selectedObjects()) {
                AffineTransform world = frame.world.get(themeObject.id);
                if (world == null) continue;
                Shape outline = ThemeRenderer.outline(themeObject.type, frame.props.get(themeObject.id));
                if (outline == null) continue;
                g2d.setStroke(new BasicStroke((float) (1.5 / scale)));
                g2d.setColor(themeObject.id == selected ? HILITE : alpha(HILITE, 120));
                g2d.draw(world.createTransformedShape(outline));
            }
            ThemeObject themeObject = selectedObject();
            AffineTransform world = themeObject == null ? null : frame.world.get(themeObject.id);
            if (world == null) return;
            switch (tool) {
                case MOVE -> drawMove(g2d, themeObject, world);
                case ROTATE -> drawRotate(g2d, world);
                case SCALE -> drawScale(g2d, world);
                case RECT -> {
                    if (sized(themeObject.type)) drawRect(g2d, frame.props.get(themeObject.id), world);
                    else drawScale(g2d, world);
                }
                case HAND -> { }
            }
        }
        private void drawMove(Graphics2D g2d, ThemeObject themeObject, AffineTransform world) {
            Point2D origin = origin(world);
            Point2D[] handle = ThemeScene.handle(ThemeScene.parentWorld(doc, frame, themeObject), origin, ARROW_PX / scale);
            g2d.setStroke(new BasicStroke((float) (2 / scale), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            arrow(g2d, handle[0], handle[1], X_AXIS, 10 / scale);
            arrow(g2d, handle[0], handle[2], Y_AXIS, 10 / scale);
            g2d.setColor(Color.WHITE);
            g2d.fill(square(origin, 4 / scale));
        }
        private void drawRotate(Graphics2D g2d, AffineTransform world) {
            Point2D origin = origin(world);
            double r = ARROW_PX / scale;
            Point2D axis = unit(world.deltaTransform(new Point2D.Double(1, 0), null));
            g2d.setStroke(new BasicStroke((float) (2 / scale), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2d.setColor(GIZMO_ROT);
            g2d.draw(new Ellipse2D.Double(origin.getX() - r, origin.getY() - r, 2 * r, 2 * r));
            g2d.draw(new Line2D.Double(origin, at(origin, axis, r)));
            g2d.fill(square(at(origin, axis, r), 4 / scale));
        }
        private void drawScale(Graphics2D g2d, AffineTransform world) {
            Point2D[] handles = scaleHandles(world);
            g2d.setStroke(new BasicStroke((float) (2 / scale), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            double sq = 4 / scale;
            g2d.setColor(X_AXIS);
            g2d.draw(new Line2D.Double(handles[0], handles[1]));
            g2d.fill(square(handles[1], sq));
            g2d.setColor(Y_AXIS);
            g2d.draw(new Line2D.Double(handles[0], handles[2]));
            g2d.fill(square(handles[2], sq));
            g2d.setColor(Color.WHITE);
            g2d.fill(square(handles[0], sq));
        }
        private void drawRect(Graphics2D g2d, Map<String, Object> p, AffineTransform world) {
            double sq = 4 / scale;
            g2d.setStroke(new BasicStroke((float) (1 / scale)));
            for (Point2D corner : rectHandles(world, p)) {
                g2d.setColor(Color.WHITE);
                g2d.fill(square(corner, sq));
                g2d.setColor(HILITE);
                g2d.draw(square(corner, sq));
            }
        }
        private void arrow(Graphics2D g2d, Point2D from, Point2D point2D, Color color, double head) {
            g2d.setColor(color);
            g2d.draw(new Line2D.Double(from, point2D));
            double angle = Math.atan2(point2D.getY() - from.getY(), point2D.getX() - from.getX());
            Path2D.Double tip = new Path2D.Double();
            tip.moveTo(point2D.getX(), point2D.getY());
            tip.lineTo(point2D.getX() - head * Math.cos(angle - 0.4), point2D.getY() - head * Math.sin(angle - 0.4));
            tip.lineTo(point2D.getX() - head * Math.cos(angle + 0.4), point2D.getY() - head * Math.sin(angle + 0.4));
            tip.closePath();
            g2d.fill(tip);
        }
    }
    //endregion

    //region Scene Editing
    private void addObject(String type, int parent, Point2D point2D) {
        snapshot();
        ThemeObject themeObject = doc.add(type, parent);
        ThemeRenderer.Frame frame = ThemeRenderer.frame(doc, time);
        double[] xy = point2D == null ? ThemeScene.centred(doc, frame, themeObject) : ThemeScene.placedAt(doc, frame, themeObject, point2D);
        themeObject.props.put("x", round(xy[0]));
        themeObject.props.put("y", round(xy[1]));
        setSelection(List.of(themeObject.id), themeObject.id);
        refreshAll();
        hint.flash(Tone.OK, L.f("tc.added", L.t("tc.o." + type)));
    }
    private void addNode(String kind) {
        if (selectedObject() == null) { hint.flash(Tone.WARN, L.t("tc.needObject")); return; }
        graphs.addNodeAt(kind, graphs.viewCentre());
    }
    private void arrangeGraph() {
        ThemeObject themeObject = selectedObject();
        if (themeObject == null) { hint.flash(Tone.WARN, L.t("tc.needObject")); return; }
        snapshot();
        ThemeLayout.arrange(themeObject.graph);
        onGraphChanged();
        hint.flash(Tone.OK, L.t("ec.s.arranged"));
    }
    private void deleteSelected() {
        if (selection.isEmpty()) return;
        snapshot();
        int count = selection.size();
        String name = selectedObject() == null ? "" : selectedObject().name;
        for (int id : new ArrayList<>(selection)) doc.remove(id);
        selection.clear();
        selected = 0;
        refreshAll();
        hint.flash(Tone.INFO, count == 1 ? L.f("tc.deleted", name) : L.f("tc.deletedMany", count));
    }
    private void copySelection() {
        List<ThemeObject> roots = new ArrayList<>();
        for (ThemeObject themeObject : selectedObjects()) if (!hasSelectedAncestor(themeObject)) roots.add(themeObject);
        if (roots.isEmpty()) { hint.flash(Tone.WARN, L.t("tc.needObject")); return; }
        Set<Integer> keep = new HashSet<>();
        for (ThemeObject themeObject : roots) collectSubtree(themeObject.id, keep);
        ThemeDoc copy = new ThemeDoc();
        for (ThemeObject themeObject : doc.objects) if (keep.contains(themeObject.id)) copy.objects.add(themeObject);
        clipboard = ThemeIO.toJson(copy);
        hint.flash(Tone.OK, L.f("tc.copied", keep.size()));
    }
    private void pasteInto(int parent, Point2D point2D) {
        if (clipboard == null) { hint.flash(Tone.WARN, L.t("tc.nothingToPaste")); return; }
        ThemeDoc src = ThemeIO.fromJson(clipboard);
        if (parent != 0 && doc.find(parent) == null) parent = 0;
        snapshot();
        Map<Integer, Integer> ids = new HashMap<>();
        for (ThemeObject themeObject : src.objects) ids.put(themeObject.id, doc.nextId++);
        List<ThemeObject> roots = new ArrayList<>();
        for (ThemeObject themeObject : src.objects) {
            ThemeObject c = new ThemeObject(ids.get(themeObject.id), themeObject.type, themeObject.name);
            boolean top = !ids.containsKey(themeObject.parent);
            c.parent = top ? parent : ids.get(themeObject.parent);
            c.layer = themeObject.layer;
            c.visible = themeObject.visible;
            c.props.clear();
            c.props.putAll(themeObject.props);
            c.graph = themeObject.graph;
            doc.objects.add(c);
            if (top) roots.add(c);
        }
        ThemeRenderer.Frame frame = ThemeRenderer.frame(doc, time);
        for (int i = 0; i < roots.size(); i++) {
            ThemeObject themeObject = roots.get(i);
            double[] xy = point2D == null ? ThemeScene.centred(doc, frame, themeObject) : ThemeScene.placedAt(doc, frame, themeObject, point2D);
            themeObject.props.put("x", round(xy[0] + PASTE_STEP * i));
            themeObject.props.put("y", round(xy[1] + PASTE_STEP * i));
        }
        List<Integer> pasted = new ArrayList<>(ids.values());
        setSelection(pasted, roots.isEmpty() ? 0 : roots.getFirst().id);
        refreshAll();
        hint.flash(Tone.OK, L.f("tc.pasted", pasted.size()));
    }
    private boolean hasSelectedAncestor(ThemeObject themeObject) {
        for (int p = themeObject.parent; p != 0; ) {
            if (selection.contains(p)) return true;
            ThemeObject up = doc.find(p);
            if (up == null) return false;
            p = up.parent;
        }
        return false;
    }
    private void collectSubtree(int id, Set<Integer> out) {
        if (!out.add(id)) return;
        for (ThemeObject themeObject : doc.children(id)) collectSubtree(themeObject.id, out);
    }
    private void onGraphChanged() {
        ThemeGraph.changed(shownGraph);
        refreshAll();
        graphs.refresh();
    }
    //endregion

    //region Selection
    private ThemeObject selectedObject() { return doc.find(selected); }
    private List<ThemeObject> selectedObjects() {
        List<ThemeObject> out = new ArrayList<>();
        for (int id : selection) {
            ThemeObject themeObject = doc.find(id);
            if (themeObject != null) out.add(themeObject);
        }
        return out;
    }
    private void setSelection(Collection<Integer> ids, int primary) {
        selection.clear();
        for (int id : ids) if (id != 0 && doc.find(id) != null) selection.add(id);
        selected = selection.contains(primary) ? primary : (selection.isEmpty() ? 0 : selection.iterator().next());
    }
    private void pruneSelection() {
        selection.removeIf(id -> doc.find(id) == null);
        selected = selection.contains(selected) ? selected : (selection.isEmpty() ? 0 : selection.iterator().next());
    }
    private void selectObject(int id) {
        if (selection.size() == 1 && selected == id) return;
        setSelection(id == 0 ? List.of() : List.of(id), id);
        refreshAll();
    }
    private void toggleSelection(int id) {
        List<Integer> ids = new ArrayList<>(selection);
        if (ids.remove((Integer) id)) setSelection(ids, ids.isEmpty() ? 0 : ids.getLast());
        else {
            ids.add(id);
            setSelection(ids, id);
        }
        refreshAll();
    }
    private void addToSelection(int id) {
        List<Integer> ids = new ArrayList<>(selection);
        ids.add(id);
        setSelection(ids, id);
        refreshAll();
    }
    private ThemeObject objectAt(int x, int y) {
        TreePath path = tree.getPathForLocation(x, y);
        return path != null && path.getLastPathComponent() instanceof DefaultMutableTreeNode defaultMutableTreeNode
                && defaultMutableTreeNode.getUserObject() instanceof ThemeObject themeObject ? themeObject : null;
    }
    //endregion

    //region Inspector
    private void refreshInspector() {
        quiet(() -> {
            ThemeObject themeObject = selectedObject();
            boolean on = themeObject != null;
            objectName.setEnabled(on);
            layerField.setEnabled(on);
            visibleToggle.setEnabled(on);
            if (!on) {
                objectName.setText("");
                layerField.setText("");
                if (inspected != null || inspectedKey == null || !inspectedKey.equals("-")) rebuildInspector(null, "-", Set.of());
                return;
            }
            objectName.setText(themeObject.name);
            layerField.setText(String.valueOf(themeObject.layer));
            Set<String> driven = drivenKeys(themeObject);
            visibleToggle.setSelected(liveValue(themeObject, "visible") instanceof Boolean b ? b : themeObject.visible);
            visibleToggle.setEnabled(!driven.contains("visible"));
            String key = (selection.size() > 1 ? "multi" : "one") + "|" + themeObject.type + "|" + new TreeSet<>(driven)
                    + "|" + ThemeInspectorLayout.signature(ThemeInspectorLayout.rows(themeObject.type, themeObject.props));
            if (themeObject == inspected && key.equals(inspectedKey)) {
                for (Runnable sync : inspectorSync) sync.run();
                return;
            }
            rebuildInspector(themeObject, key, driven);
        });
    }
    private void rebuildInspector(ThemeObject themeObject, String key, Set<String> driven) {
        propsBody.removeAll();
        inspectorSync.clear();
        if (themeObject == null) {
            propsBody.put(note(NOTE_W, 12f, L.t("tc.noSelection")), 0);
        } else {
            if (selection.size() > 1) propsBody.put(note(NOTE_W, 11f, L.f("tc.multiNote", selection.size())), 0);
            propsBody.put(new Text("tc.props", 12f, true, TEXT, 0), 8);
            for (ThemeInspectorLayout.Row row : ThemeInspectorLayout.rows(themeObject.type, themeObject.props))
                propsBody.put(inspectorRow(themeObject, row, driven), 8);
        }
        inspected = themeObject;
        inspectedKey = key;
        propsBody.revalidate();
        propsBody.repaint();
    }
    private static Set<String> drivenKeys(ThemeObject themeObject) {
        Set<String> keys = new HashSet<>();
        for (NodeInst nodeInst : themeObject.graph.nodes) {
            String key = ThemeNodes.settableKey(nodeInst.kind);
            if (key != null) keys.add(key);
            else if (!ThemeNodes.sharedKeys(nodeInst.kind).isEmpty()) keys.addAll(ThemeNodes.sharedKeys(nodeInst.kind));
            else if (nodeInst.kind.equals("th.set.points") || nodeInst.kind.equals("th.set.pointX") || nodeInst.kind.equals("th.set.pointY")) keys.add("points");
            else if (nodeInst.kind.equals("th.set.visible")) keys.add("visible");
        }
        return keys;
    }
    private JComponent inspectorRow(ThemeObject themeObject, ThemeInspectorLayout.Row layoutRow, Set<String> driven) {
        JPanel block = new JPanel();
        block.setOpaque(false);
        block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));
        block.setAlignmentX(Component.LEFT_ALIGNMENT);
        boolean rowDriven = layoutRow.cells().stream().anyMatch(cell -> driven.contains(cell.key()));
        Text title = new Text(null, 11f, false, rowDriven ? WARN : MUTED, 0);
        title.setRaw(L.t(layoutRow.titleKey()) + (rowDriven ? "  " + L.t("tc.driven") : ""));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        block.add(title);
        block.add(Box.createVerticalStrut(3));
        JPanel line = new JPanel(new GridLayout(1, layoutRow.cells().size(), 6, 0));
        line.setOpaque(false);
        line.setAlignmentX(Component.LEFT_ALIGNMENT);
        line.setMaximumSize(new Dimension(COL_W, 30));
        for (ThemeInspectorLayout.Cell cell : layoutRow.cells())
            line.add(cellView(themeObject, cell, driven, L.t(layoutRow.titleKey())));
        block.add(line);
        return block;
    }
    private void bindInspector() {
        objectName.addActionListener(_ -> renameSelected());
        objectName.addFocusListener(new FocusAdapter() { @Override public void focusLost(FocusEvent focusEvent) { renameSelected(); } });
        layerField.addActionListener(_ -> applyLayer());
        layerField.addFocusListener(new FocusAdapter() { @Override public void focusLost(FocusEvent focusEvent) { applyLayer(); } });
        visibleToggle.addActionListener(_ -> {
            if (quiet > 0 || selectedObject() == null) return;
            boolean on = visibleToggle.isSelected();
            if (selectedObjects().stream().allMatch(themeObject -> themeObject.visible == on)) return;
            snapshot();
            for (ThemeObject themeObject : selectedObjects()) themeObject.visible = on;
            refreshAll();
        });
        themeName.addActionListener(_ -> { applyName(); refreshAll(); });
        themeName.addFocusListener(new FocusAdapter() { @Override public void focusLost(FocusEvent focusEvent) { if (quiet == 0) { applyName(); refreshAll(); } } });
    }
    private static double prop(Map<String, Object> p, String key) { return p != null && p.get(key) instanceof Number n ? n.doubleValue() : 0; }
    private void setProp(ThemeObject themeObject, String key, Object value) {
        if (Objects.equals(themeObject.props.get(key), value)) return;
        snapshot();
        if (value == null) themeObject.props.remove(key); else themeObject.props.put(key, value);
        refreshAll();
    }
    private void setProps(ThemeObject themeObject, Map<String, Object> changes) { setProps(themeObject, changes, true); }
    private void setProps(ThemeObject themeObject, Map<String, Object> changes, boolean undoStep) {
        boolean changed = false;
        for (Map.Entry<String, Object> change : changes.entrySet()) {
            if (!Objects.equals(themeObject.props.get(change.getKey()), change.getValue())) changed = true;
        }
        if (!changed) return;
        if (undoStep) snapshot();
        for (Map.Entry<String, Object> change : changes.entrySet()) {
            if (change.getValue() == null) themeObject.props.remove(change.getKey());
            else themeObject.props.put(change.getKey(), change.getValue());
        }
        refreshAll();
    }
    private static Object liveValue(ThemeObject themeObject, String key) {
        return ThemeGraph.liveOverrides(themeObject.graph).getOrDefault(key, themeObject.props.get(key));
    }
    private static String valueOf(ThemeObject themeObject, ThemeTypes.Spec spec) {
        Object v = liveValue(themeObject, spec.key());
        if (v == null) return "";
        return v instanceof Number n ? numText(n.doubleValue()) : v.toString();
    }
    //endregion

    //region Property Cells
    private JComponent cellView(ThemeObject themeObject, ThemeInspectorLayout.Cell cell, Set<String> driven, String title) {
        return switch (cell.kind()) {
            case COLOR -> colorCell(themeObject, cell, driven.contains(cell.key()));
            case CHOICE -> choiceCell(themeObject, cell);
            case NUMBER, TEXT -> textCell(themeObject, cell, driven.contains(cell.key()), title);
        };
    }
    private JComponent textCell(ThemeObject themeObject, ThemeInspectorLayout.Cell cell, boolean driven, String title) {
        ThemeTypes.Spec spec = specOf(themeObject, cell.key());
        StyledTextField field = new StyledTextField(style, valueOf(themeObject, spec));
        field.setEnabled(!driven);
        field.setPreferredSize(new Dimension(40, 30));
        field.setMinimumSize(new Dimension(40, 30));
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        Runnable apply = () -> {
            if (quiet > 0) return;
            if (!field.getText().equals(valueOf(themeObject, spec))) applyText(themeObject, spec, field.getText());
        };
        field.addActionListener(_ -> apply.run());
        field.addFocusListener(new FocusAdapter() { @Override public void focusLost(FocusEvent focusEvent) { apply.run(); } });
        inspectorSync.add(() -> {
            String now = valueOf(themeObject, spec);
            if (!field.isFocusOwner() && !field.getText().equals(now)) field.setText(now);
        });
        String name = cell.label() == null ? title : title + " " + cell.label();
        return labelled(style, cell.label(), field, driven || spec.kind() != ThemeTypes.Kind.NUM ? null : scrubber(themeObject, spec), driven ? name + "  " + L.t("tc.driven") : name);
    }
    private JComponent choiceCell(ThemeObject themeObject, ThemeInspectorLayout.Cell cell) {
        ThemeTypes.Spec spec = specOf(themeObject, cell.key());
        String[] labels = spec.choices().stream().map(c -> L.t("ec.o." + c)).toArray(String[]::new);
        StyledComboBox box = new StyledComboBox(style, labels);
        box.setPreferredSize(new Dimension(40, 30));
        box.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        box.setAlignmentX(Component.LEFT_ALIGNMENT);
        Object current = themeObject.props.containsKey(spec.key()) ? themeObject.props.get(spec.key()) : spec.def();
        box.setSelectedIndex(Math.max(0, spec.choices().indexOf(String.valueOf(current))));
        box.addActionListener(_ -> {
            if (quiet > 0 || box.getSelectedIndex() < 0) return;
            setProp(themeObject, spec.key(), spec.choices().get(box.getSelectedIndex()));
        });
        inspectorSync.add(() -> {
            Object now = themeObject.props.containsKey(spec.key()) ? themeObject.props.get(spec.key()) : spec.def();
            int i = Math.max(0, spec.choices().indexOf(String.valueOf(now)));
            if (box.getSelectedIndex() != i) box.setSelectedIndex(i);
        });
        return box;
    }
    private JComponent colorCell(ThemeObject themeObject, ThemeInspectorLayout.Cell cell, boolean driven) {
        String colorKey = cell.key(), alphaKey = cell.alphaKey();
        JComponent swatch = new JComponent() {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g2d = (Graphics2D) graphics.create();
                for (int y = 0; y < getHeight(); y += 6) {
                    for (int x = 0; x < getWidth(); x += 6) {
                        g2d.setColor(((x + y) / 6) % 2 == 0 ? new Color(200, 200, 205) : new Color(140, 140, 146));
                        g2d.fillRect(x, y, 6, 6);
                    }
                }
                Color c = colorOf(themeObject, colorKey, alphaKey);
                if (c != null) {
                    g2d.setColor(c);
                    g2d.fillRect(0, 0, getWidth(), getHeight());
                }
                g2d.setColor(MUTED);
                g2d.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
                g2d.dispose();
            }
        };
        swatch.setPreferredSize(new Dimension(44, 30));
        swatch.setMinimumSize(new Dimension(44, 30));
        swatch.setCursor(Cursor.getPredefinedCursor(driven ? Cursor.DEFAULT_CURSOR : Cursor.HAND_CURSOR));
        StyledTextField hexField = new StyledTextField(style, rgbaText(themeObject, colorKey, alphaKey));
        hexField.setPreferredSize(new Dimension(40, 30));
        hexField.setMinimumSize(new Dimension(40, 30));
        hexField.setEnabled(!driven);
        Runnable apply = () -> {
            if (quiet > 0) return;
            if (!hexField.getText().equals(rgbaText(themeObject, colorKey, alphaKey))) applyRgba(themeObject, colorKey, alphaKey, hexField.getText());
        };
        hexField.addActionListener(_ -> apply.run());
        hexField.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent focusEvent) { apply.run(); }
        });
        inspectorSync.add(() -> {
            swatch.repaint();
            String now = rgbaText(themeObject, colorKey, alphaKey);
            if (!hexField.isFocusOwner() && !hexField.getText().equals(now)) hexField.setText(now);
        });
        swatch.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent mouseEvent) {
                if (driven || quiet > 0 || colorOf(themeObject, colorKey, alphaKey) == null) return;
                openColorMenu(themeObject, colorKey, alphaKey, swatch);
            }
        });
        JPanel panel = new JPanel(new BorderLayout(6, 0));
        panel.setOpaque(false);
        panel.add(swatch, BorderLayout.WEST);
        panel.add(hexField, BorderLayout.CENTER);
        return panel;
    }
    private void openColorMenu(ThemeObject themeObject, String colorKey, String alphaKey, JComponent anchor) {
        Color c = colorOf(themeObject, colorKey, alphaKey);
        boolean[] undoStepTaken = {false};
        assert c != null;
        RgbaPopup popup = new RgbaPopup(style, c.getRGB() & 0xFFFFFF, alphaOf(themeObject, alphaKey), (rgb, alpha) -> {
            if (!undoStepTaken[0]) {
                snapshot();
                undoStepTaken[0] = true;
            }
            Map<String, Object> changes = new LinkedHashMap<>();
            changes.put(colorKey, String.format(Locale.ROOT, "#%06X", rgb));
            if (alphaKey != null) changes.put(alphaKey, alpha);
            setProps(themeObject, changes, false);
        });
        popup.show(anchor, 0, anchor.getHeight() + 4);
    }
    private static JComponent labelled(Style style, String label, JComponent field, MouseAdapter scrub, String tip) {
        JPanel panel = new JPanel(new BorderLayout(4, 0));
        panel.setOpaque(false);
        if (label != null) {
            String text = label.startsWith("tc.") ? L.t(label) : label;
            StyledLabel tag = new StyledLabel(style, text, false, false);
            tag.setTextColor(switch (text) {
                case "X" -> X_AXIS;
                case "Y" -> Y_AXIS;
                case "Z" -> new Color(100, 150, 245);
                default -> MUTED;
            });
            tag.setFont(font(11f, true));
            tag.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
            if (tip != null) tag.setToolTipText(tip);
            if (scrub != null) {
                tag.setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
                tag.addMouseListener(scrub);
                tag.addMouseMotionListener(scrub);
            }
            panel.add(tag, BorderLayout.WEST);
        }
        panel.add(field, BorderLayout.CENTER);
        return panel;
    }
    private static double scrubStep(String key) {
        if (key.startsWith("rot")) return 0.5;
        if (key.startsWith("scale") || key.startsWith("alpha") || key.equals("midAt")) return 0.01;
        return 1;
    }
    private MouseAdapter scrubber(ThemeObject themeObject, ThemeTypes.Spec spec) {
        return new MouseAdapter() {
            private int startX;
            private double startValue;
            private boolean moved;
            @Override public void mousePressed(MouseEvent mouseEvent) {
                startX = mouseEvent.getXOnScreen();
                startValue = liveValue(themeObject, spec.key()) instanceof Number n ? n.doubleValue() : 0;
                moved = false;
            }
            @Override public void mouseDragged(MouseEvent mouseEvent) {
                if (!moved) { snapshot(); moved = true; }
                double step = scrubStep(spec.key()) * (mouseEvent.isShiftDown() ? 0.1 : 1);
                double v = Math.round((startValue + (mouseEvent.getXOnScreen() - startX) * step) * 1000) / 1000.0;
                themeObject.props.put(spec.key(), v);
                refreshAll();
            }
        };
    }
    //endregion

    //region Value Editing
    private void applyText(ThemeObject themeObject, ThemeTypes.Spec spec, String text) {
        String value = text.strip();
        if (value.isEmpty()) {
            if (spec.def() == null) setProp(themeObject, spec.key(), null);
            else refreshInspector();
            return;
        }
        switch (spec.kind()) {
            case NUM -> {
                try { setProp(themeObject, spec.key(), Double.parseDouble(value)); }
                catch (NumberFormatException exception) {
                    hint.flash(Tone.BAD, L.f("tc.badNumber", value));
                    refreshInspector();
                }
            }
            case COLOR -> {
                String hex = Param.normalizeHex(value);
                if (hex == null) {
                    hint.flash(Tone.BAD, L.f("tc.badColor", value));
                    refreshInspector();
                    return;
                }
                setProp(themeObject, spec.key(), hex);
            }
            default -> setProp(themeObject, spec.key(), value);
        }
    }
    private void applyName() {
        String name = themeName.getText().strip();
        if (name.isEmpty() || name.equals(doc.name)) return;
        snapshot();
        doc.name = name;
        doc.nameKey = null;
    }
    private void applyLayer() {
        ThemeObject themeObject = selectedObject();
        if (quiet > 0 || themeObject == null) return;
        try {
            int layer = ThemeDoc.clampLayer(Integer.parseInt(layerField.getText().strip()));
            if (layer == themeObject.layer) { layerField.setText(String.valueOf(layer)); return; }
            snapshot();
            themeObject.layer = layer;
            refreshAll();
        } catch (NumberFormatException exception) {
            hint.flash(Tone.BAD, L.f("tc.badNumber", layerField.getText()));
            refreshInspector();
        }
    }
    private void applyRgba(ThemeObject themeObject, String colorKey, String alphaKey, String text) {
        String value = text.strip();
        if (value.isEmpty()) {
            if (ThemeTypes.defaultOf(themeObject.type, colorKey) == null) {
                Map<String, Object> cleared = new LinkedHashMap<>();
                cleared.put(colorKey, null);
                if (alphaKey != null) cleared.put(alphaKey, null);
                setProps(themeObject, cleared);
            } else refreshInspector();
            return;
        }
        String body = value.startsWith("#") ? value.substring(1) : value;
        if (!body.matches("[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?")) {
            hint.flash(Tone.BAD, L.f("tc.badColor", value));
            refreshInspector();
            return;
        }
        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put(colorKey, Param.normalizeHex(body.substring(0, 6)));
        if (body.length() == 8 && alphaKey != null) changes.put(alphaKey, Integer.parseInt(body.substring(6), 16) / 255.0);
        setProps(themeObject, changes);
    }
    private void renameSelected() {
        ThemeObject themeObject = selectedObject();
        if (quiet > 0 || themeObject == null) return;
        String name = objectName.getText().strip();
        if (name.isEmpty() || name.equals(themeObject.name)) return;
        snapshot();
        themeObject.name = name;
        refreshAll();
    }
    private void renameInDialog(ThemeObject themeObject) {
        String name = CkDialogs.text(this, style, "tc.renameTitle", "tc.renamePrompt", themeObject.name);
        if (name == null || name.equals(themeObject.name)) return;
        snapshot();
        themeObject.name = name;
        refreshAll();
    }
    //endregion

    //region Value Formatting
    public static String numText(double v) {
        return v == Math.rint(v) && Math.abs(v) < 1e12 ? String.valueOf((long) v) : String.valueOf(v);
    }
    private static double round(double v) { return Math.round(v * 10) / 10.0; }
    private static double fine(double v) { return Math.round(v * 1000) / 1000.0; }
    private static boolean sized(String type) {
        return switch (type) {
            case "rect", "ellipse", "ring", "arc" -> true;
            default -> false;
        };
    }
    private static String rgbaText(ThemeObject themeObject, String colorKey, String alphaKey) {
        Color c = colorOf(themeObject, colorKey, alphaKey);
        if (c == null) return "";
        return String.format(Locale.ROOT, "#%02X%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha());
    }
    private static ThemeTypes.Spec specOf(ThemeObject themeObject, String key) {
        for (ThemeTypes.Spec spec : ThemeTypes.specs(themeObject.type)) if (spec.key().equals(key)) return spec;
        throw new IllegalArgumentException("no property " + key + " for " + themeObject.type);
    }
    private static double alphaOf(ThemeObject themeObject, String alphaKey) {
        Object value = alphaKey == null ? null : themeObject.props.get(alphaKey);
        return value instanceof Number n ? Math.clamp(n.doubleValue(), 0, 1) : 1;
    }
    private static Color colorOf(ThemeObject themeObject, String colorKey, String alphaKey) {
        Object value = liveValue(themeObject, colorKey);
        Color base;
        if (value instanceof Color c) base = c;
        else {
            String hex = Param.normalizeHex(value instanceof String text ? text : null);
            if (hex == null) return null;
            int rgb = Integer.parseInt(hex.substring(1), 16);
            base = new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
        }
        double alpha = alphaKey != null && liveValue(themeObject, alphaKey) instanceof Number n ? n.doubleValue() : alphaOf(themeObject, alphaKey);
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), (int) Math.round(alpha * 255));
    }
    //endregion

    //region Object Tree
    private void refreshTree() {
        List<TreeRow> rows = new ArrayList<>(doc.objects.size());
        for (ThemeObject themeObject : doc.objects) rows.add(new TreeRow(themeObject, themeObject.parent, themeObject.name, themeObject.layer, themeObject.visible));
        if (rows.equals(treeRows)) {
            selectInTree();
            return;
        }
        treeRows = rows;
        rebuildTree();
    }
    private void rebuildTree() {
        Set<Integer> open = new HashSet<>();
        if (treeModel.getRoot() instanceof DefaultMutableTreeNode oldRoot) collectOpen(oldRoot, open);
        DefaultMutableTreeNode root = new DefaultMutableTreeNode(L.t("tc.scene"));
        addChildren(root, 0);
        treeModel.setRoot(root);
        tree.expandPath(new TreePath(root));
        applyOpen(root, open);
        for (int id : selection) {
            DefaultMutableTreeNode node = findNode(root, id);
            for (TreeNode parent = node == null ? null : node.getParent(); parent != null; parent = parent.getParent())
                tree.expandPath(new TreePath(((DefaultMutableTreeNode) parent).getPath()));
        }
        selectInTree();
    }
    private void collectOpen(DefaultMutableTreeNode node, Set<Integer> open) {
        if (node.getUserObject() instanceof ThemeObject themeObject && tree.isExpanded(new TreePath(node.getPath()))) open.add(themeObject.id);
        for (int i = 0; i < node.getChildCount(); i++) collectOpen((DefaultMutableTreeNode) node.getChildAt(i), open);
    }
    private void applyOpen(DefaultMutableTreeNode node, Set<Integer> open) {
        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
            if (child.getUserObject() instanceof ThemeObject themeObject && open.contains(themeObject.id)) {
                tree.expandPath(new TreePath(child.getPath()));
            }
            applyOpen(child, open);
        }
    }
    private DefaultMutableTreeNode findNode(DefaultMutableTreeNode node, int id) {
        if (node.getUserObject() instanceof ThemeObject themeObject && themeObject.id == id) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode found = findNode((DefaultMutableTreeNode) node.getChildAt(i), id);
            if (found != null) return found;
        }
        return null;
    }
    private Map<String, Object> keptLocal(int id, int newParent) {
        ThemeRenderer.Frame f = ThemeRenderer.frame(doc, time);
        AffineTransform current = f.world.get(id);
        AffineTransform parentWorld = newParent == 0 ? new AffineTransform() : f.world.get(newParent);
        if (current == null || parentWorld == null) return Map.of();
        try {
            AffineTransform local = parentWorld.createInverse();
            local.concatenate(current);
            double sx = Math.hypot(local.getScaleX(), local.getShearY());
            if (sx < 1e-9) return Map.of();
            double cos = local.getScaleX() / sx, sin = local.getShearY() / sx;
            double sy = -sin * local.getShearX() + cos * local.getScaleY();
            double[] tilt = ThemeRenderer.tilt(f.props.get(id));
            Map<String, Object> out = new HashMap<>();
            out.put("x", fine(local.getTranslateX()));
            out.put("y", fine(local.getTranslateY()));
            out.put("rot", fine(Math.toDegrees(Math.atan2(sin, cos))));
            out.put("scale", fine(sx / tilt[1]));
            out.put("scaleY", fine(sy / tilt[0]));
            return out;
        } catch (NoninvertibleTransformException exception) { return Map.of(); }
    }
    private void addChildren(DefaultMutableTreeNode into, int parent) {
        for (ThemeObject themeObject : doc.children(parent)) {
            DefaultMutableTreeNode node = new DefaultMutableTreeNode(themeObject);
            into.add(node);
            addChildren(node, themeObject.id);
        }
    }
    private void selectInTree() {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
        List<TreePath> paths = new ArrayList<>();
        TreePath primary = null;
        Enumeration<?> all = root.depthFirstEnumeration();
        while (all.hasMoreElements()) {
            DefaultMutableTreeNode defaultMutableTreeNode = (DefaultMutableTreeNode) all.nextElement();
            if (!(defaultMutableTreeNode.getUserObject() instanceof ThemeObject themeObject) || !selection.contains(themeObject.id)) continue;
            TreePath path = new TreePath(defaultMutableTreeNode.getPath());
            if (themeObject.id == selected) primary = path; else paths.add(path);
        }
        if (primary != null) paths.add(primary);
        if (paths.isEmpty()) {
            tree.clearSelection();
            return;
        }
        tree.setSelectionPaths(paths.toArray(TreePath[]::new));
        tree.scrollPathToVisible(paths.getLast());
    }
    private void treeSelected(TreeSelectionEvent treeSelectionEvent) {
        if (quiet > 0) return;
        List<Integer> ids = new ArrayList<>();
        int primary = 0;
        TreePath lead = tree.getSelectionPath();
        for (TreePath treePath : Objects.requireNonNull(tree.getSelectionPaths())) {
            if (!(treePath.getLastPathComponent() instanceof DefaultMutableTreeNode defaultMutableTreeNode) || !(defaultMutableTreeNode.getUserObject() instanceof ThemeObject themeObject)) continue;
            ids.add(themeObject.id);
            if (treePath.equals(lead)) primary = themeObject.id;
        }
        setSelection(ids, primary != 0 ? primary : (ids.isEmpty() ? 0 : ids.getLast()));
        refreshAll();
    }
    private record TreeRow(ThemeObject o, int parent, String name, int layer, boolean visible) { }
    private static final class TreeCells extends DefaultTreeCellRenderer {
        TreeCells() {
            setLeafIcon(null);
            setOpenIcon(null);
            setClosedIcon(null);
        }
        @Override public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean focus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, focus);
            Object u = value instanceof DefaultMutableTreeNode defaultMutableTreeNode ? defaultMutableTreeNode.getUserObject() : null;
            if (u instanceof ThemeObject themeObject) {
                setText(themeObject.name + "   [" + themeObject.layer + "]" + (themeObject.visible ? "" : "  " + L.t("tc.hidden")));
            } else { setText(L.t("tc.scene")); }
            setFont(font(12f, false));
            setForeground(TEXT);
            setBackgroundNonSelectionColor(CARD);
            setBackgroundSelectionColor(alpha(HILITE, 110));
            setTextSelectionColor(TEXT);
            setTextNonSelectionColor(TEXT);
            return this;
        }
    }
    private final class TreeMove extends TransferHandler {
        @Override public int getSourceActions(JComponent component) { return MOVE; }
        @Override protected Transferable createTransferable(JComponent component) {
            Set<Integer> chosen = new HashSet<>();
            for (TreePath path : Objects.requireNonNull(tree.getSelectionPaths())) {
                if (path.getLastPathComponent() instanceof DefaultMutableTreeNode node && node.getUserObject() instanceof ThemeObject themeObject) chosen.add(themeObject.id);
            }
            if (chosen.isEmpty()) return null;
            StringJoiner ids = new StringJoiner(",");
            for (ThemeObject themeObject : doc.treeOrder()) if (chosen.contains(themeObject.id)) ids.add(String.valueOf(themeObject.id));
            return new StringSelection(ids.toString());
        }
        @Override public boolean canImport(TransferSupport transferSupport) {
            return transferSupport.isDrop() && transferSupport.isDataFlavorSupported(DataFlavor.stringFlavor);
        }
        @Override public boolean importData(TransferSupport transferSupport) {
            if (!canImport(transferSupport)) return false;
            List<Integer> dragged = new ArrayList<>();
            try {
                for (String part : ((String) transferSupport.getTransferable().getTransferData(DataFlavor.stringFlavor)).split(","))
                    if (!part.isBlank()) dragged.add(Integer.parseInt(part.trim()));
            } catch (Exception exception) { return false; }
            JTree.DropLocation where = (JTree.DropLocation) transferSupport.getDropLocation();
            int newParent = where.getPath() == null ? 0 : idOf(where.getPath());
            List<Integer> moving = new ArrayList<>();
            boolean refused = false;
            for (int id : dragged) {
                if (doc.isWithin(id, newParent)) refused = true;
                else moving.add(id);
            }
            if (refused) hint.flash(Tone.BAD, L.t("tc.moveRefused"));
            if (moving.isEmpty()) return false;
            int before = 0;
            if (where.getChildIndex() >= 0) {
                List<ThemeObject> siblings = new ArrayList<>();
                for (ThemeObject sibling : doc.children(newParent)) if (!moving.contains(sibling.id)) siblings.add(sibling);
                if (where.getChildIndex() < siblings.size()) before = siblings.get(where.getChildIndex()).id;
            }
            Map<Integer, Map<String, Object>> kept = new LinkedHashMap<>();
            for (int id : moving) kept.put(id, keptLocal(id, newParent));
            snapshot();
            List<Integer> done = new ArrayList<>();
            for (int id : moving)
                if (doc.move(id, newParent, before)) done.add(id);
            if (done.isEmpty()) {
                undoStack.pop();
                refreshDirty();
                return false;
            }
            for (int id : done) {
                ThemeObject moved = doc.find(id);
                if (moved != null) moved.props.putAll(kept.get(id));
            }
            setSelection(done, done.getLast());
            refreshAll();
            return true;
        }
        private int idOf(TreePath path) {
            Object u = path.getLastPathComponent() instanceof DefaultMutableTreeNode defaultMutableTreeNode ? defaultMutableTreeNode.getUserObject() : null;
            return u instanceof ThemeObject themeObject ? themeObject.id : 0;
        }
    }
    //endregion

    //region Theme Files
    private static ThemeDoc blankScene() {
        ThemeDoc themeDoc = new ThemeDoc();
        themeDoc.name = L.t("tc.newScene");
        themeDoc.id = "new-scene";
        return themeDoc;
    }
    private void newScene() { open(blankScene(), null, false); }
    private void openOriginal(String id) {
        ThemeDoc themeDoc = pick(ThemeOriginals.originals(), id);
        themeDoc.name = themeDoc.name + " (copy)";
        themeDoc.nameKey = null;
        themeDoc.id = ThemeStore.slug(themeDoc.name);
        open(themeDoc, null, false);
    }
    private void openTemplate(String id) { open(pick(ThemeOriginals.templates(), id), null, false); }
    private void openSaved(String id) {
        try { open(ThemeStore.load(id), id, true); }
        catch (IOException | RuntimeException exception) { hint.flash(Tone.BAD, L.f("tc.loadFailed", exception.getMessage())); }
    }
    private static ThemeDoc pick(List<ThemeDoc> all, String id) {
        for (ThemeDoc themeDoc : all) if (themeDoc.id.equals(id)) return themeDoc;
        return all.getFirst();
    }
    private void open(ThemeDoc themeDoc, String fileId, boolean saved) {
        if (!canLeave()) return;
        doc = themeDoc;
        linked = fileId;
        undoStack.clear();
        redoStack.clear();
        selection.clear();
        selected = 0;
        shownGraph = noGraph;
        head = new Object();
        savedHead = head;
        inFile = saved;
        treeRows = List.of();
        treeModel.setRoot(new DefaultMutableTreeNode(""));
        refreshAll();
        hint.flash(Tone.INFO, L.f("tc.opened", doc.name));
    }
    private void deleteSaved(String id) {
        int r = ask(this, style, "tc.deleteTitle", L.f("tc.deleteMsg", id), 1, "tc.delete", "tc.cancel");
        if (r != 0) return;
        try {
            ThemeStore.delete(id);
            Project.MainMenu.MenuThemeRegistry.reload();
            if (id.equals(linked)) {
                linked = null;
                inFile = false;
            }
            refreshSaved();
            refreshDirty();
            hint.flash(Tone.OK, L.f("tc.deletedFile", id));
        } catch (IOException exception) { hint.flash(Tone.BAD, L.f("tc.saveFailed", exception.getMessage())); }
    }
    private boolean save() {
        applyName();
        String id = ThemeStore.slug(doc.name);
        if (!id.equals(linked) && ThemeStore.ids().contains(id)) {
            int answer = ask(this, style, "ec.d.overwriteTitle", L.f("ec.d.overwrite", doc.name, id), 0, "ec.btn.replace", "cancel");
            if (answer != 0) return false;
        }
        doc.id = id;
        try {
            ThemeStore.save(doc);
            Project.MainMenu.MenuThemeRegistry.reload();
            inFile = true;
            savedHead = head;
            linked = id;
            refreshSaved();
            refreshDirty();
            hint.flash(Tone.OK, L.f("tc.saved", id));
            return true;
        } catch (IOException exception) {
            hint.flash(Tone.BAD, L.f("tc.saveFailed", exception.getMessage()));
            return false;
        }
    }
    //endregion

    //region Undo History
    private void snapshot() {
        undoStack.push(new Step(ThemeIO.copy(doc), head));
        while (undoStack.size() > HISTORY_LIMIT) undoStack.removeLast();
        redoStack.clear();
        head = new Object();
        refreshDirty();
    }
    private void restore(Step step) {
        doc = step.doc();
        head = step.token();
        shownGraph = noGraph;
        pruneSelection();
        treeRows = List.of();
        treeModel.setRoot(new DefaultMutableTreeNode(""));
        refreshAll();
    }
    private void undo() {
        if (undoStack.isEmpty()) { hint.flash(Tone.WARN, L.t("tc.nothingToUndo")); return; }
        redoStack.push(new Step(ThemeIO.copy(doc), head));
        restore(undoStack.pop());
        hint.flash(Tone.INFO, L.t("tc.undone"));
    }
    private void redo() {
        if (redoStack.isEmpty()) { hint.flash(Tone.WARN, L.t("tc.nothingToRedo")); return; }
        undoStack.push(new Step(ThemeIO.copy(doc), head));
        restore(redoStack.pop());
        hint.flash(Tone.INFO, L.t("tc.redone"));
    }
    private boolean dirty() {
        if (doc.objects.isEmpty()) return false;
        return !inFile || head != savedHead;
    }
    private record Step(ThemeDoc doc, Object token) { }
    //endregion
}