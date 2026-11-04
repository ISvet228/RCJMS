package Project.EngineCreator;
import Project.CreatorKit.*;
import Project.Helpers.EditorKit;

import Project.Game.RenderEngines;
import Project.StyleUI.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;

import static Project.CreatorKit.CkChrome.fieldLabel;
import static Project.CreatorKit.CkChrome.menuItem;
import static Project.Helpers.EditorKit.*;

public final class RenderEngineCreatorView extends JPanel {
    //region Variables
    //region Layout Sizes
    private static final int LEFT_W = 278, RIGHT_W = 248, INNER_W = 252;
    private static final int NOTE_W = RIGHT_W - 32;
    private static final int COL_W = RIGHT_W - 32;
    private static final int CARD_H = 92, CARD_GAP = 6, CARD_W = (INNER_W - CARD_GAP) / 2;
    //endregion
    //region Node Catalog
    private static final String[] CATEGORIES = {"in", "const", "math", "logic", "conv", "color", "map", "tex", "ray", "geo", "fx", "out", CustomNodes.CATEGORY};
    private static final String[] GEOMETRY_KEYS = {CkProject.BOTH, CkProject.EUCLID, CkProject.HYPER};
    //endregion
    //region Project State
    private CkProject project = Presets.newProject();
    private int pass = 0;
    private boolean dirty = false;
    private boolean syncing = false;
    private static final int HISTORY_LIMIT = 100;
    //endregion
    //region Undo History
    private final Deque<String> history = new ArrayDeque<>();
    private final Deque<String> redoHistory = new ArrayDeque<>();
    //endregion
    //region Inside Editing State
    private final Deque<Frame> insideStack = new ArrayDeque<>();
    private JPanel insideBar;
    private StyledLabel insideLabel;
    //endregion
    //region Canvas and Preview
    private final NodeCanvas canvas;
    private NodePalette nodePalette;
    private final HintBar hint = new HintBar();
    private final PreviewPanel preview;
    private JComponent previewSlot;
    private boolean previewShown = false;
    //endregion
    //region Controls
    private final StyledComboBox passBox, passGeometry;
    private final StyledTextField nameField, titleField, curvatureField;
    private final StyledToggle openClToggle, backupToggle;
    private final Text dirtyText = new Text(null, 12f, true, WARN, 0);
    //endregion
    //region Properties Panel
    private final Text propsHead = new Text("ec.ui.cardProps", 13f, true, TEXT, 0);
    private final Note propsHelp = new Note(NOTE_W, 11f, MUTED);
    private final Column propsBody = new Column();
    private final Map<String, JComponent> editors = new LinkedHashMap<>();
    //endregion
    //region Issues and Engines Lists
    private final DefaultListModel<Issue> issueModel = new DefaultListModel<>();
    private final JList<Issue> issueList = new JList<>(issueModel);
    private final Column enginesList = new Column();
    //endregion
    //region Window
    private final Style style;
    private final Runnable onExit;
    //endregion
    //endregion

    //region Constructors
    public RenderEngineCreatorView(Style style, Runnable onExit) {
        super(new BorderLayout(10, 8));
        this.style = style;
        this.onExit = onExit;
        String customError = CustomNodes.load();
        setBackground(BG);
        setOpaque(true);
        setBorder(new EmptyBorder(6, 8, 0, 8));

        canvas = new NodeCanvas(project, this::snapshot, this::onCanvasChanged, msg -> hint.flash(Tone.INFO, msg), this::arrange);
        canvas.setStyle(style);
        canvas.setInsideOpener(this::enterInside);
        passBox = new StyledComboBox(style, new Object[]{"1"});
        passGeometry = new StyledComboBox(style, new Object[]{L.t("ec.o.both"), L.t("ec.o.euclid"), L.t("ec.o.hyperbolic")});
        nameField = new StyledTextField(style, project.name);
        titleField = new StyledTextField(style, project.title);
        curvatureField = new StyledTextField(style, String.valueOf(project.curvature));
        openClToggle = new StyledToggle(style, "ec.ui.openCl");
        backupToggle = new StyledToggle(style, "ec.ui.backupCustom");
        preview = new PreviewPanel(style, () -> project, msg -> hint.flash(Tone.BAD, msg));

        add(header(), BorderLayout.NORTH);
        add(sidebar(buildLeft(), LEFT_W), BorderLayout.WEST);
        add(buildCenter(), BorderLayout.CENTER);
        add(sidebar(buildRight(), RIGHT_W), BorderLayout.EAST);
        add(hint, BorderLayout.SOUTH);

        bindFields();
        bindKeys();
        syncFieldsFromProject();
        refreshPassBox();
        refreshEngines();
        refreshAll();
        hint.setHint(L.t("ec.s.ready"));
        if (customError != null) hint.flash(Tone.BAD, customError);
    }
    //endregion

    //region Layout
    //region Frame Setup
    @Override public void addNotify() {
        super.addNotify();
        SwingUtilities.invokeLater(canvas::requestFocusInWindow);
    }
    private JComponent header() {
        ToolButton back = new ToolButton(style, ToolButton.Layout.ROW, Glyph.BACK, "back").tip("back", "Esc").size(104, 36);
        back.onClick(this::exit);
        Text title = new Text("ec.ui.shortTitle", 18f, true, TEXT, 0);
        title.setPreferredSize(new Dimension(232, 36));
        dirtyText.setPreferredSize(new Dimension(140, 36));

        ToolButton undo = icon(Glyph.UNDO, "ec.ui.undoTip", "Ctrl+Z", this::undo);
        ToolButton redo = icon(Glyph.REDO, "ec.ui.redoTip", "Ctrl+Y", this::redo);
        ToolButton arrange = icon(Glyph.FIT, "ec.ui.arrangeTip", null, this::arrange);
        ToolButton delete = icon(Glyph.TRASH, "ec.ui.deleteTip", "Del", canvas::deleteSelected);
        ToolButton previewButton = new ToolButton(style, ToolButton.Layout.ROW, Glyph.PLAY, "ec.ui.preview").tip("ec.ui.previewTip", "F6").size(112, 36);
        previewButton.onClick(this::togglePreview);
        ToolButton check = new ToolButton(style, ToolButton.Layout.ROW, Glyph.CHECK, "ec.ui.check").tip("ec.ui.checkTip", "F5").size(104, 36);
        check.onClick(this::check);
        ToolButton save = new ToolButton(style, ToolButton.Layout.ROW, Glyph.SAVE, "ec.ui.save").tip("ec.ui.saveTip", "Ctrl+S").accent(new Color(46, 150, 96)).size(140, 36);
        save.onClick(this::save);
        ToolButton help = icon(Glyph.HELP, "ec.ui.helpTip", "F1", this::showHelp);

        return new CkChrome.TopBar(
                List.of(back, title, dirtyText),
                List.of(undo, redo, arrange, delete, previewButton, check, help, save),
                List.of(new CkChrome.Labelled(previewButton, L.t("ec.ui.preview"), 112),
                        new CkChrome.Labelled(check, L.t("ec.ui.check"), 104),
                        new CkChrome.Labelled(save, L.t("ec.ui.save"), 140)),
                List.of(title, dirtyText));
    }
    private ToolButton icon(Glyph glyph, String tipKey, String shortcut, Runnable action) {
        return CkChrome.icon(style, glyph, tipKey, shortcut, action);
    }
    private JComponent sidebar(Column column, int width) {
        return CkChrome.sidebar(style, column, width);
    }
    private Note note(String key, Color color) {
        Note note = new Note(NOTE_W, 11f, color);
        note.set(null, color, L.t(key));
        return note;
    }
    private static String escape(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }
    //endregion
    //region Left Column
    private Column buildLeft() {
        Column col = new Column();

        Fold templates = new Fold("ec.ui.cardTemplates", true);
        templates.body.put(note("ec.ui.templatesHint", MUTED), 0);
        for (Presets.Preset preset : Presets.ALL) {
            ToolButton toolButton = new ToolButton(style, ToolButton.Layout.ROW, Glyph.NEW, "ec.pr." + preset.id()).tip("ec.pr." + preset.id() + ".help", null).size(INNER_W, 30);
            toolButton.onClick(() -> applyPreset(preset.id()));
            templates.body.put(toolButton, 6);
        }
        col.put(templates, 0);

        Fold originals = new Fold("ec.ui.cardOriginals", true);
        originals.body.put(note("ec.ui.originalsHint", MUTED), 0);
        for (OriginalEngines.Original original : OriginalEngines.ALL) {
            ToolButton toolButton = new ToolButton(style, ToolButton.Layout.ROW, Glyph.PLAY, "ec.og." + original.id()).tip("ec.og." + original.id() + ".help", null).size(INNER_W, 30);
            toolButton.onClick(() -> loadOriginal(original.id()));
            originals.body.put(toolButton, 6);
        }
        col.put(originals, 12);

        Fold engines = new Fold("ec.ui.cardEngines", true, true);
        engines.body.put(note("ec.ui.enginesHint", MUTED), 0);
        StyledButton refresh = new StyledButton(style, "ec.ui.refresh");
        refresh.setPreferredSize(new Dimension(INNER_W, 30));
        refresh.addActionListener(actionEvent -> refreshEngines());
        engines.body.put(refresh, 6);
        engines.body.put(enginesList, 6);
        col.put(engines, 12);

        Fold nodes = new Fold("ec.ui.cardNodes", true, true);
        nodes.body.put(note("ec.ui.nodesHint", MUTED), 0);
        nodePalette = new NodePalette(style, List.of(CATEGORIES), Nodes::engineKinds, this::addNode);
        nodePalette.setCardDecorator((nodeKind, card) -> {
            if (CustomNodes.isCustom(nodeKind.id())) card.setOnRightClick(point -> showCustomCardMenu(card, nodeKind.id(), point));
        });
        nodes.body.put(nodePalette, 6);
        col.put(nodes, 12);
        return col;
    }
    private void showCustomCardMenu(Component component, String kind, Point point) {
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(EditorKit.CARD);
        menu.setBorder(BorderFactory.createLineBorder(EditorKit.CARD_LINE));
        JMenuItem help = new JMenuItem(L.t("ec.menu.help"));
        JMenuItem edit = new JMenuItem(L.t("ec.menu.customText"));
        JMenuItem delete = new JMenuItem(L.t("ec.menu.deleteCustom"));
        for (JMenuItem menuItem : List.of(help, edit, delete)) {
            menuItem.setBackground(EditorKit.CARD);
            menuItem.setForeground(EditorKit.TEXT);
            menuItem.setFont(EditorKit.font(12f, false));
        }
        help.addActionListener(actionEvent -> NodeHelp.show(component, Nodes.find(kind)));
        edit.addActionListener(actionEvent -> editCustomTexts(kind));
        delete.addActionListener(actionEvent -> deleteCustomNode(kind));
        menu.add(edit);
        menu.add(delete);
        menu.show(component, point.x, point.y);
    }
    private void editCustomTexts(String kind) {
        CustomNodes.Def def = CustomNodes.def(kind);
        if (def == null) return;
        String[] t = CkDialogs.customTexts(this, style, L.t("ec.g.editTitle"), def.title(), def.description());
        if (t == null) return;
        try {
            CustomNodes.updateTexts(kind, t[0], t[1]);
            hint.flash(Tone.OK, L.f("ec.g.textsSaved", t[0]));
        } catch (IOException exception) {
            hint.flash(Tone.BAD, L.f("ec.g.customSaveFailed", exception.getMessage()));
        }
        nodePalette.refresh();
        canvas.repaint();
    }
    private void deleteCustomNode(String kind) {
        String title = CustomNodes.titleOf(kind);
        if (ask(this, style, "ec.g.deleteTitle", L.f("ec.g.deleteConfirm", title), 0, "ec.g.deleteBtn", "cancel") != 0) return;
        try {
            CustomNodes.delete(kind);
            hint.flash(Tone.INFO, L.f("ec.g.deleted", title));
        } catch (IOException exception) {
            hint.flash(Tone.BAD, L.f("ec.g.customSaveFailed", exception.getMessage()));
        }
        nodePalette.refresh();
        canvas.repaint();
    }
    //endregion
    //region Right Column
    private Column buildRight() {
        Column col = new Column();

        Card proj = new Card("ec.ui.cardProject");
        proj.put(fieldLabel("ec.ui.name"), 0);
        nameField.setPreferredSize(new Dimension(COL_W, 30));
        proj.put(nameField, 2);
        proj.put(note("ec.ui.nameHint", MUTED), 2);
        proj.put(fieldLabel("ec.ui.displayTitle"), 8);
        titleField.setPreferredSize(new Dimension(COL_W, 30));
        proj.put(titleField, 2);
        proj.put(fieldLabel("ec.ui.curvature"), 8);
        curvatureField.setPreferredSize(new Dimension(COL_W, 30));
        proj.put(curvatureField, 2);
        proj.put(note("ec.ui.curvatureHint", MUTED), 2);
        openClToggle.setPreferredSize(new Dimension(COL_W, 32));
        proj.put(openClToggle, 8);
        proj.put(note("ec.ui.openClHint", MUTED), 2);
        backupToggle.setPreferredSize(new Dimension(COL_W, 32));
        proj.put(backupToggle, 8);
        proj.put(note("ec.ui.backupHint", MUTED), 2);
        col.put(proj, 0);

        Card props = new Card("ec.ui.cardProps");
        props.put(propsHead, 0);
        props.put(propsHelp, 4);
        props.put(propsBody, 8);
        col.put(props, 12);

        Card legend = new Card("ec.ui.cardLegend");
        for (DataType dataType : DataType.values())
            legend.put(new Text("ec.type." + dataType.name().toLowerCase(Locale.ROOT), 11.5f, true, NodeCanvas.typeColor(dataType), 0), 3);
        col.put(legend, 12);

        Card issues = new Card("ec.ui.cardIssues");
        issues.put(note("ec.ui.issuesTip", MUTED), 0);
        issueList.setBackground(CARD);
        issueList.setVisibleRowCount(6);
        issueList.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                Issue issue = (Issue) value;
                setText("<html><body style='width:" + (NOTE_W - 8) + "px'>" + escape(issue.text()) + "</body></html>");
                setForeground(issue.error() ? BAD : WARN);
                setBackground(selected ? CARD_LINE : CARD);
                setBorder(new EmptyBorder(4, 4, 4, 4));
                return this;
            }
        });
        issueList.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent mouseEvent) {
                if (mouseEvent.getClickCount() != 2) return;
                Issue issue = issueList.getSelectedValue();
                if (issue == null || issue.node() <= 0) return;
                NodeInst nodeInst = project.node(issue.node());
                if (nodeInst == null) return;
                if (nodeInst.pass != pass) selectPass(nodeInst.pass);
                canvas.selectNode(issue.node());
            }
        });
        StyledScrollPane issueScroll = new StyledScrollPane(issueList, style);
        issueScroll.setPreferredSize(new Dimension(COL_W, 170));
        issues.put(issueScroll, 6);
        col.put(issues, 12);
        return col;
    }
    //endregion
    //region Center
    private JComponent buildCenter() {
        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setOpaque(false);

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        bar.setOpaque(false);
        passBox.setToolTipText(L.t("ec.ui.passHint"));
        passBox.setPreferredSize(new Dimension(116, 32));
        passGeometry.setToolTipText(L.t("ec.ui.passGeo"));
        passGeometry.setPreferredSize(new Dimension(150, 32));
        StyledButton addPass = new StyledButton(style, "ec.ui.addPass");
        addPass.setPreferredSize(new Dimension(92, 32));
        addPass.addActionListener(actionEvent -> addPass());
        StyledButton removePass = new StyledButton(style, "ec.ui.removePass");
        removePass.setPreferredSize(new Dimension(92, 32));
        removePass.addActionListener(actionEvent -> removePass());
        bar.add(passBox);
        bar.add(passGeometry);
        bar.add(addPass);
        bar.add(removePass);

        insideLabel = new StyledLabel(style, "");
        insideLabel.setFont(EditorKit.font(12f, true));
        insideLabel.setForeground(EditorKit.TEXT);
        StyledButton saveInside = new StyledButton(style, "ec.g.saveInside");
        saveInside.setPreferredSize(new Dimension(150, 32));
        saveInside.addActionListener(actionEvent -> saveInside());
        StyledButton cancelInside = new StyledButton(style, "ec.g.cancelInside");
        cancelInside.setPreferredSize(new Dimension(150, 32));
        cancelInside.addActionListener(actionEvent -> cancelInside());
        insideBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        insideBar.setOpaque(false);
        insideBar.add(insideLabel);
        insideBar.add(saveInside);
        insideBar.add(cancelInside);
        insideBar.setVisible(false);
        bar.add(insideBar);

        StyledScrollPane styledScrollPane = new StyledScrollPane(canvas, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED, style);
        styledScrollPane.getViewport().setBackground(EditorKit.CANVAS_BG);
        styledScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        styledScrollPane.getHorizontalScrollBar().setUnitIncrement(16);
        previewSlot = new JPanel(new BorderLayout());
        previewSlot.setOpaque(false);
        previewSlot.add(preview, BorderLayout.CENTER);
        previewSlot.setVisible(false);
        center.add(bar, BorderLayout.NORTH);
        center.add(styledScrollPane, BorderLayout.CENTER);
        center.add(previewSlot, BorderLayout.SOUTH);
        return center;
    }
    //endregion
    //endregion

    //region Bindings
    private void bindFields() {
        bindField(nameField, v -> project.name = v.trim());
        bindField(titleField, v -> project.title = v.trim());
        bindField(curvatureField, v -> {
            try { project.curvature = Double.parseDouble(v.trim()); }
            catch (NumberFormatException ignored) { project.curvature = Double.NaN; }
        });
        openClToggle.addActionListener(actionEvent -> { if (!syncing) applyProjectToggles(); });
        backupToggle.addActionListener(actionEvent -> { if (!syncing) applyBackupToggle(); });
        passGeometry.addActionListener(actionEvent -> {
            if (syncing) return;
            snapshot();
            project.setGeometry(pass, GEOMETRY_KEYS[passGeometry.getSelectedIndex()]);
            refreshPassBox();
            refreshAll();
        });
        passBox.addActionListener(actionEvent -> { if (!syncing && passBox.getSelectedIndex() >= 0) selectPass(passBox.getSelectedIndex()); });
    }
    private void bindField(JTextField field, java.util.function.Consumer<String> apply) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            void changed() {
                if (syncing) return;
                apply.accept(field.getText());
                canvas.repaint();
                refreshIssues();
            }
            @Override public void insertUpdate(DocumentEvent documentEvent) { changed(); }
            @Override public void removeUpdate(DocumentEvent documentEvent) { changed(); }
            @Override public void changedUpdate(DocumentEvent documentEvent) { changed(); }
        });
    }
    private void bindKeys() {
        InputMap inputMap = getInputMap(WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = getActionMap();
        bindKey(inputMap, actionMap, "escape", KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), this::exit);
        bindKey(inputMap, actionMap, "undo", KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK), this::undo);
        bindKey(inputMap, actionMap, "redo", KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK), this::redo);
        bindKey(inputMap, actionMap, "redo2", KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), this::redo);
        bindKey(inputMap, actionMap, "save", KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK), this::save);
        bindKey(inputMap, actionMap, "check", KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), this::check);
        bindKey(inputMap, actionMap, "preview", KeyStroke.getKeyStroke(KeyEvent.VK_F6, 0), this::togglePreview);
        bindKey(inputMap, actionMap, "help", KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0), this::showHelp);
        bindKey(inputMap, actionMap, "delete", KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), canvas::deleteSelected);
        bindKey(inputMap, actionMap, "copy", KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK), canvas::copySelection);
        bindKey(inputMap, actionMap, "paste", KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK), canvas::pasteAtCursor);
        bindKey(inputMap, actionMap, "duplicate", KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK), canvas::duplicateSelection);
        bindKey(inputMap, actionMap, "selectAll", KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK), canvas::selectAll);
        bindKey(inputMap, actionMap, "group", KeyStroke.getKeyStroke(KeyEvent.VK_G, InputEvent.CTRL_DOWN_MASK), canvas::groupSelection);
        bindKey(inputMap, actionMap, "customNode", KeyStroke.getKeyStroke(KeyEvent.VK_K, InputEvent.CTRL_DOWN_MASK), canvas::makeCustomNode);
    }
    private static void bindKey(InputMap inputMap, ActionMap actionMap, String name, KeyStroke key, Runnable action) {
        inputMap.put(key, name);
        actionMap.put(name, new AbstractAction() { @Override public void actionPerformed(ActionEvent actionEvent) { action.run(); } });
    }
    //endregion

    //region Project Editing
    //region Nodes
    private void addNode(String kind) {
        snapshot();
        Point point = canvas.viewCentre();
        int k = (int) project.nodes.stream().filter(nodeInst -> nodeInst.pass == pass).count() % 5;
        NodeInst added = project.add(kind, Math.max(0, point.x - 40 + k * 24), Math.max(0, point.y - 20 + k * 24), pass);
        canvas.selectNode(added.id);
        hint.flash(Tone.OK, L.f("ec.s.nodeAdded", Nodes.get(kind).title()));
        refreshAll();
        canvas.revealNode(added);
    }
    private void applyPreset(String id) {
        snapshot();
        try {
            int used = Presets.apply(project, id, pass);
            Layout.arrange(project, used);
            refreshPassBox();
            if (used != pass) selectPass(used);
            hint.flash(Tone.OK, L.f("ec.s.toolAdded", Presets.ALL.stream().filter(preset -> preset.id().equals(id)).findFirst().orElseThrow().title()));
        } catch (IllegalStateException exception) {
            if (!history.isEmpty()) history.pop();
            hint.flash(Tone.BAD, exception.getMessage());
        }
        refreshAll();
    }
    private void arrange() {
        snapshot();
        Layout.arrange(project, pass);
        canvas.repaint();
        hint.flash(Tone.OK, L.t("ec.s.arranged"));
    }
    private void applyProjectToggles() {
        snapshot();
        project.openCl = openClToggle.isSelected();
        refreshAll();
    }
    /** Backup of custom nodes: when off, the project forgets the parts of its custom nodes. */
    private void applyBackupToggle() {
        snapshot();
        project.backupCustom = backupToggle.isSelected();
        if (!project.backupCustom) project.backup.clear();
        hint.flash(Tone.INFO, L.t(project.backupCustom ? "ec.s.backupOn" : "ec.s.backupOff"));
        refreshAll();
    }
    //endregion
    //region Passes
    private void addPass() {
        snapshot();
        pass = project.passCount();
        project.add("output", 900, 120, pass);
        refreshPassBox();
        refreshAll();
        hint.flash(Tone.OK, L.f("ec.s.passAdded", pass + 1));
    }
    private void removePass() {
        if (project.passCount() <= 1) { hint.flash(Tone.WARN, L.t("ec.s.passKeepOne")); return; }
        int last = project.passCount() - 1;
        if (pass != last) { hint.flash(Tone.WARN, L.t("ec.s.passOnlyLast")); return; }
        snapshot();
        project.removePass(last);
        pass = last - 1;
        refreshPassBox();
        refreshAll();
        hint.flash(Tone.OK, L.f("ec.s.passRemoved", last + 1));
    }
    private void selectPass(int index) {
        pass = index;
        refreshPassBox();
        refreshAll();
    }
    private void refreshPassBox() {
        syncing = true;
        try {
            passBox.removeAllItems();
            for (int k = 0; k < project.passCount(); k++) passBox.addItem(L.f("ec.ui.passItem", k + 1));
            passBox.setSelectedIndex(pass);
            int g = Arrays.asList(GEOMETRY_KEYS).indexOf(project.geometry(pass));
            passGeometry.setSelectedIndex(Math.max(0, g));
        } finally { syncing = false; }
        canvas.setPass(pass);
    }
    //endregion
    //endregion

    //region Undo and Redo
    private void snapshot() {
        history.push(ProjectIO.toJson(project));
        while (history.size() > HISTORY_LIMIT) history.removeLast();
        redoHistory.clear();
        setDirty(true);
    }
    private void undo() {
        if (history.isEmpty()) { hint.flash(Tone.INFO, L.t("ec.s.undoNone")); return; }
        redoHistory.push(ProjectIO.toJson(project));
        restore(history.pop());
        hint.flash(Tone.INFO, L.t("ec.s.undone"));
    }
    private void redo() {
        if (redoHistory.isEmpty()) { hint.flash(Tone.INFO, L.t("ec.s.redoNone")); return; }
        history.push(ProjectIO.toJson(project));
        restore(redoHistory.pop());
        hint.flash(Tone.INFO, L.t("ec.s.redone"));
    }
    private void restore(String json) {
        try {
            project = ProjectIO.fromJson(json);
            canvas.setProject(project);
            pass = Math.min(pass, project.passCount() - 1);
            syncFieldsFromProject();
            refreshPassBox();
            refreshAll();
        } catch (IllegalArgumentException exception) { hint.flash(Tone.BAD, L.f("ec.s.undoFailed", exception.getMessage())); }
    }
    //endregion

    //region Inside Editing
    // ---------- editing a custom node from inside ----------

    private boolean inside() { return !insideStack.isEmpty(); }
    private void enterInside(String kind) {
        CustomNodes.Def def = CustomNodes.def(kind);
        if (def == null) { hint.flash(Tone.BAD, L.f("ec.g.notDefined", kind)); return; }
        CkProject inner;
        try { inner = CustomNodes.openInner(def); }
        catch (IllegalArgumentException exception) { hint.flash(Tone.BAD, exception.getMessage()); return; }
        insideStack.push(new Frame(project, List.copyOf(history), List.copyOf(redoHistory), dirty, kind, def));
        history.clear();
        redoHistory.clear();
        switchProject(inner);
        setDirty(false);
        hint.flash(Tone.INFO, L.f("ec.g.entered", def.title()));
    }private void saveInside() {
        Frame frame = insideStack.peek();
        if (frame == null) return;
        for (NodeInst nodeInst : project.nodes)
            if (Nodes.find(nodeInst.kind) != null && Nodes.get(nodeInst.kind).isOutput()) { hint.flash(Tone.BAD, L.t("ec.g.noOutput")); return; }
        CustomNodes.Rebuilt rebuilt;
        try { rebuilt = CustomNodes.rebuild(project, frame.old(), frame.old().title(), frame.old().description()); }
        catch (IllegalArgumentException exception) { hint.flash(Tone.BAD, exception.getMessage()); return; }
        try { CustomNodes.update(rebuilt.def()); }
        catch (IOException exception) { hint.flash(Tone.BAD, L.f("ec.g.customSaveFailed", exception.getMessage())); }

        insideStack.pop();
        restoreFrame(frame);
        snapshot();
        List<CkProject> followers = new ArrayList<>();
        followers.add(frame.parent());
        for (Frame g : insideStack) followers.add(g.parent());
        for (CkProject ckProject : followers) CustomNodes.migrate(ckProject, frame.old(), rebuilt.def(), rebuilt.oldToNew());
        setDirty(true);
        canvas.repaint();
        refreshAll();
        hint.flash(Tone.OK, L.f("ec.g.insideSaved", rebuilt.def().title()));
    }
    private void cancelInside() {
        Frame frame = insideStack.peek();
        if (frame == null) return;
        insideStack.pop();
        restoreFrame(frame);
        hint.flash(Tone.INFO, L.f("ec.g.cancelled", frame.old().title()));
    }
    private void restoreFrame(Frame frame) {
        history.clear();
        for (String s : frame.history()) history.addLast(s);
        redoHistory.clear();
        for (String s : frame.redo()) redoHistory.addLast(s);
        switchProject(frame.parent());
        setDirty(frame.dirty());
    }
    private record Frame(CkProject parent, List<String> history, List<String> redo, boolean dirty, String kind, CustomNodes.Def old) {

    }
    //endregion

    //region Project Files
    private void switchProject(CkProject ckProject) {
        project = ckProject;
        canvas.setProject(ckProject);
        canvas.setPass(0);
        syncFieldsFromProject();
        refreshPassBox();
        if (inside()) insideLabel.setText(L.f("ec.g.insideLabel", insideStack.peek().old().title()));
        insideBar.setVisible(inside());
        refreshAll();
    }
    private void save() {
        if (inside()) { hint.flash(Tone.WARN, L.t("ec.g.insideBlocked")); return; }
        EngineBuilder.Result result = EngineBuilder.build(project, true, target ->
                ask(this, style, "ec.d.overwriteTitle", L.f("ec.d.overwrite", project.name, target.toString()), 0, "ec.btn.replace", "cancel") == 0);
        showIssues(result.issues());
        if (result.saved()) {
            setDirty(false);
            refreshEngines();
            hint.flash(Tone.OK, L.f("ec.s.saved", result.file().getFileName().toString()));
            inform(this, style, "ec.d.savedTitle", L.f("ec.d.savedText", project.title));
        } else if (result.hasErrors()) {
            Issue first = result.issues().stream().filter(Issue::error).findFirst().orElseThrow();
            hint.flash(Tone.BAD, L.t("ec.s.saveFailed") + " " + first.text());
        } else if (!result.messages().isEmpty()) hint.flash(Tone.WARN, result.messages().getLast());
    }
    private void loadProject(Path file) {
        if (inside()) { hint.flash(Tone.WARN, L.t("ec.g.insideBlocked")); return; }
        if (dirty && project.nodes.size() > 1) {
            int answer = ask(this, style, "common.unsaved_title", L.t("ec.unsaved_msg"), 0, "common.save_and_leave", "common.leave_without_saving", "common.keep_editing");
            if (answer == 0) { save(); if (dirty) return; }
            else if (answer != 1) return;
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            snapshot();
            project = ProjectIO.fromJson(json);
            canvas.setProject(project);
            pass = 0;
            syncFieldsFromProject();
            refreshPassBox();
            preview.reset();
            setDirty(false);
            refreshAll();
            hint.flash(Tone.OK, L.f("ec.s.loaded", project.name));
        } catch (Exception exception) { hint.flash(Tone.BAD, L.f("ec.d.openFailed", exception.getMessage())); }
    }
    private void loadOriginal(String id) {
        if (inside()) { hint.flash(Tone.WARN, L.t("ec.g.insideBlocked")); return; }
        if (dirty && project.nodes.size() > 1) {
            int answer = ask(this, style, "common.unsaved_title", L.t("ec.unsaved_msg"), 0, "common.save_and_leave", "common.leave_without_saving", "common.keep_editing");
            if (answer == 0) { save(); if (dirty) return; }
            else if (answer != 1) return;
        }
        try {
            CkProject built = OriginalEngines.build(id);
            snapshot();
            project = built;
            canvas.setProject(project);
            pass = 0;
            syncFieldsFromProject();
            refreshPassBox();
            preview.reset();
            setDirty(true);
            refreshAll();
            hint.flash(Tone.OK, L.f("ec.s.loaded", project.name));
        } catch (IllegalArgumentException exception) { hint.flash(Tone.BAD, exception.getMessage()); }
    }
    private Path projectsFolder() { return EngineBuilder.folder().resolve(EngineBuilder.PROJECTS); }
    private void setDirty(boolean value) {
        dirty = value;
        if (value) dirtyText.setKey("common.unsaved");
        else dirtyText.setRaw("");
    }
    //endregion

    //region Validation
    private void check() {
        if (inside()) { hint.flash(Tone.WARN, L.t("ec.g.insideBlocked")); return; }
        EngineBuilder.Result result = EngineBuilder.build(project, false, path -> false);
        showIssues(result.issues());
        if (result.hasErrors()) hint.flash(Tone.BAD, L.t("ec.s.checkBad"));
        else hint.flash(Tone.OK, L.t("ec.s.checkOk"));
    }
    private void refreshIssues() {
        if (inside()) { showIssues(List.of()); return; }
        showIssues(Validator.validate(project));
    }
    private void showIssues(List<Issue> issues) {
        Set<Integer> errs = new HashSet<>(), warns = new HashSet<>();
        for (Issue i : issues) {
            if (i.node() < 0) continue;
            if (i.error()) errs.add(i.node()); else warns.add(i.node());
        }
        canvas.setHighlights(errs, warns);
        issueModel.clear();
        for (Issue i : issues) issueModel.addElement(i);
        long e = issues.stream().filter(Issue::error).count();
        long w = issues.size() - e;
        hint.setHint(issues.isEmpty() ? L.t("ec.s.correct") : L.f("ec.s.counts", e, w));
    }
    //endregion

    //region Properties
    private void refreshProperties() {
        Integer id = canvas.selectedNode();
        NodeInst nodeInst = id == null ? null : project.node(id);
        NodeKind nodeKind = nodeInst == null ? null : Nodes.find(nodeInst.kind);
        propsBody.removeAll();
        editors.clear();
        if (canvas.selectedNodes().size() > 1) {
            propsHead.setRaw(L.f("ec.s.selectedCount", canvas.selectedNodes().size()));
            propsHelp.set(null, MUTED, L.t("ec.ui.multiHelp"));
        }
        else if (nodeKind == null) {
            propsHead.setKey("ec.ui.cardProps");
            propsHelp.set(null, MUTED, L.t("ec.ui.noSelection"));
        }
        else {
            propsHead.setRaw(nodeKind.title());
            propsHelp.set(null, MUTED, nodeKind.help());
            for (Param prm : nodeKind.params()) {
                propsBody.put(new Text("ec.p." + prm.key(), 11f, false, MUTED, 0), 6);
                String current = nodeInst.params.getOrDefault(prm.key(), prm.def());
                JComponent editor;
                if (prm.kind() == Param.Kind.CHOICE) {
                    String[] labels = prm.choices().stream().map(prm::choiceLabel).toArray(String[]::new);
                    StyledComboBox box = new StyledComboBox(style, labels);
                    box.setPreferredSize(new Dimension(COL_W, 32));
                    box.setSelectedIndex(Math.max(0, prm.choices().indexOf(current)));
                    editor = box;
                } else {
                    StyledTextField field = new StyledTextField(style, current);
                    field.setPreferredSize(new Dimension(COL_W, 30));
                    editor = field;
                }
                propsBody.put(editor, 2);
                editors.put(prm.key(), editor);
            }
            if (nodeKind.params().isEmpty()) propsBody.put(note("ec.ui.paramsEmpty", MUTED), 0);
            else {
                StyledButton apply = new StyledButton(style, "ec.ui.applyProps");
                int nodeId = id;
                apply.addActionListener(actionEvent -> applyProperties(nodeId));
                propsBody.put(apply, 10);
            }
        }
        propsBody.revalidate();
        propsBody.repaint();
    }
    private void applyProperties(int nodeId) {
        NodeInst nodeInst = project.node(nodeId);
        NodeKind nodeKind = nodeInst == null ? null : Nodes.find(nodeInst.kind);
        if (nodeKind == null) return;
        snapshot();
        for (Param prm : nodeKind.params()) {
            JComponent component = editors.get(prm.key());
            if (component instanceof StyledComboBox box) {
                int i = box.getSelectedIndex();
                if (i >= 0) nodeInst.params.put(prm.key(), prm.choices().get(i));
            }
            else if (component instanceof StyledTextField styledTextField) nodeInst.params.put(prm.key(), styledTextField.getText().trim());
        }
        refreshIssues();
        canvas.repaint();
        hint.flash(Tone.OK, L.t("ec.s.propsApplied"));
    }
    //endregion

    //region Sync
    private void onCanvasChanged() {
        refreshProperties();
        refreshIssues();
    }
    private void refreshAll() {
        canvas.repaint();
        refreshProperties();
        refreshIssues();
    }
    private void syncFieldsFromProject() {
        syncing = true;
        try {
            nameField.setText(project.name);
            titleField.setText(project.title);
            curvatureField.setText(Double.isFinite(project.curvature) ? String.valueOf(project.curvature) : "");
            openClToggle.setSelected(project.openCl);
            backupToggle.setSelected(project.backupCustom);
        } finally { syncing = false; }
    }
    //endregion

    //region Engines
    private void refreshEngines() {
        enginesList.removeAll();
        Path dir = projectsFolder();
        List<Path> files = new ArrayList<>();
        if (Files.isDirectory(dir)) {
            try (var stream = Files.list(dir)) { stream.filter(path -> path.getFileName().toString().endsWith(".engine.json")).sorted().forEach(files::add); }
            catch (Exception ignored) { }
        }
        if (files.isEmpty()) enginesList.put(note("ec.ui.noEngines", MUTED), 0);
        for (Path file : files) {
            try {
                CkProject ckProject = ProjectIO.fromJson(Files.readString(file, StandardCharsets.UTF_8));
                boolean built = Files.exists(EngineBuilder.folder().resolve(CodeGen.className(ckProject) + ".java"));
                String text = ckProject.name + (built ? "  ●" : "");
                ToolButton toolButton = new ToolButton(style, ToolButton.Layout.ROW, Glyph.FOLDER, text).size(INNER_W, 28);
                toolButton.setToolTipText(L.f("ec.ui.engineTip", ckProject.title, ckProject.passCount()));
                toolButton.onClick(() -> loadProject(file));
                toolButton.addMouseListener(new MouseAdapter() {
                    @Override public void mouseReleased(MouseEvent mouseEvent) {
                        if (SwingUtilities.isRightMouseButton(mouseEvent)) showEngineMenu(toolButton, mouseEvent, file, ckProject);
                    }
                });
                enginesList.put(toolButton, 4);
            } catch (Exception exception) { enginesList.put(note("ec.ui.engineBroken", BAD), 4); }
        }
        enginesList.revalidate();
        enginesList.repaint();
    }
    private void showEngineMenu(Component component, MouseEvent mouseEvent, Path file, CkProject ckProject) {
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(CARD);
        menu.setBorder(BorderFactory.createLineBorder(CARD_LINE));
        menu.add(menuItem(L.t("ec.ui.engMenu.open"), () -> loadProject(file)));
        menu.add(menuItem(L.t("ec.menu.delete"), () -> deleteEngine(file, ckProject)));
        menu.show(component, mouseEvent.getX(), mouseEvent.getY());
    }
    private void deleteEngine(Path file, CkProject ckProject) {
        int answer = ask(this, style, "ec.d.deleteTitle", L.f("ec.d.deleteText", ckProject.name), 1, "ec.btn.delete", "cancel");
        if (answer != 0) return;
        String cls = CodeGen.className(ckProject);
        try {
            Path dir = EngineBuilder.folder();
            Files.deleteIfExists(file);
            Files.deleteIfExists(dir.resolve(cls + ".java"));
            Files.deleteIfExists(dir.resolve(cls + ".cl"));
            RenderEngines.reload();
            refreshEngines();
            hint.flash(Tone.OK, L.f("ec.s.engineDeleted", ckProject.name));
        } catch (IOException exception) { hint.flash(Tone.BAD, L.f("ec.s.deleteFailed", exception.getMessage())); }
    }
    //endregion

    //region Window Actions
    private void togglePreview() {
        previewShown = !previewShown;
        previewSlot.setVisible(previewShown);
        if (previewShown) preview.build();
        revalidate();
        repaint();
    }
    private void exit() {
        if (inside()) { cancelInside(); return; }
        if (dirty && project.nodes.size() > 1) {
            int answer = ask(this, style, "common.unsaved_title", L.t("ec.unsaved_msg"), 0, "common.save_and_leave", "common.leave_without_saving", "common.keep_editing");
            if (answer == 0) { save(); if (dirty) return; }
            else if (answer != 1) return;
        }
        onExit.run();
    }
    private void showHelp() {
        String text = String.join("\n\n", L.t("ec.d.help1") + ":", L.t("ec.d.help2"), L.t("ec.d.help3"),
                L.t("ec.d.help4"), L.t("ec.d.help5"), L.t("ec.d.help6"), L.t("ec.d.help7"), L.t("ec.d.help8"), L.t("ec.d.help9"));
        inform(this, style, "ec.ui.help", text);
    }
    //endregion
}