package Project.EngineCreator;
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

import static Project.Helpers.EditorKit.*;

public final class RenderEngineCreatorView extends JPanel {
    private static final int LEFT_W = 278, RIGHT_W = 248, INNER_W = 252;
    /** Node cards: two per row in the palette. */
    private static final int CARD_H = 92, CARD_GAP = 6, CARD_W = (INNER_W - CARD_GAP) / 2;
    private static final String[] CATEGORIES = {"in", "const", "math", "logic", "conv", "color", "map", "tex", "ray", "geo", "fx", "out"};
    private static final String[] GEOMETRY_KEYS = {EcProject.BOTH, EcProject.EUCLID, EcProject.HYPER};
    private static final int HISTORY_LIMIT = 100;

    private final Style style;
    private final Runnable onExit;

    private EcProject project = Presets.newProject();
    private int pass = 0;
    private final Deque<String> history = new ArrayDeque<>();
    private final Deque<String> redoHistory = new ArrayDeque<>();
    private boolean dirty = false;
    private boolean syncing = false;

    private final NodeCanvas canvas;
    private final HintBar hint = new HintBar();
    private final Text dirtyText = new Text(null, 12f, true, WARN, 0);
    private final StyledComboBox passBox, passGeometry;
    private final StyledTextField nameField, titleField, curvatureField;
    private final StyledToggle openClToggle;
    private final Text propsHead = new Text("ec.ui.cardProps", 13f, true, TEXT, 0);
    private final Note propsHelp = new Note(INNER_W, 11f, MUTED);
    private final Column propsBody = new Column();
    private final Map<String, JComponent> editors = new LinkedHashMap<>();
    private final DefaultListModel<Issue> issueModel = new DefaultListModel<>();
    private final JList<Issue> issueList = new JList<>(issueModel);
    private final Column enginesList = new Column();
    private Column nodeList;
    private String nodeCategory = "all", nodeQuery = "";
    private final PreviewPanel preview;
    private JComponent previewSlot;
    private boolean previewShown = false;

    public RenderEngineCreatorView(Style style, Runnable onExit) {
        super(new BorderLayout(10, 8));
        this.style = style;
        this.onExit = onExit;
        setBackground(BG);
        setOpaque(true);
        setBorder(new EmptyBorder(6, 8, 0, 8));

        canvas = new NodeCanvas(project, this::snapshot, this::onCanvasChanged, msg -> hint.flash(Tone.INFO, msg), this::arrange);
        canvas.setStyle(style);
        passBox = new StyledComboBox(style, new Object[]{"1"});
        passGeometry = new StyledComboBox(style, new Object[]{L.t("ec.o.both"), L.t("ec.o.euclid"), L.t("ec.o.hyperbolic")});
        nameField = new StyledTextField(style, project.name);
        titleField = new StyledTextField(style, project.title);
        curvatureField = new StyledTextField(style, String.valueOf(project.curvature));
        openClToggle = new StyledToggle(style, "ec.ui.openCl");
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
    }

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

        List<JComponent> items = new ArrayList<>(List.of(back, title, dirtyText, undo, redo, arrange, delete, previewButton, check, help, save));
        List<ToolButton> labelled = List.of(previewButton, check, save);

        JPanel toolbar = new JPanel(new BorderLayout(8, 0)) {
            @Override public void doLayout() {
                fitToolbar(this, items, labelled);
                super.doLayout();
            }
        };
        toolbar.setOpaque(false);
        JPanel leftGroup = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        leftGroup.setOpaque(false);
        JPanel rightGroup = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 6));
        rightGroup.setOpaque(false);
        for (JComponent c : List.of(back, title, dirtyText)) leftGroup.add(c);
        for (JComponent c : List.of(undo, redo, arrange, delete, previewButton, check, help, save)) rightGroup.add(c);
        toolbar.add(leftGroup, BorderLayout.WEST);
        toolbar.add(rightGroup, BorderLayout.EAST);
        return toolbar;
    }

    private Boolean appliedNoLabels, appliedNoTitle;

    private void fitToolbar(JPanel bar, List<JComponent> items, List<ToolButton> labelled) {
        int w = bar.getWidth();
        if (w <= 0) return;
        boolean noLabels = w < 1100;
        boolean noTitle = w < 880;
        if (appliedNoLabels != null && appliedNoLabels == noLabels && appliedNoTitle == noTitle) return;
        appliedNoLabels = noLabels;
        appliedNoTitle = noTitle;

        String[] keys = {"ec.ui.preview", "ec.ui.check", "ec.ui.save"};
        int[] fullWidth = {112, 104, 140};
        for (int i = 0; i < labelled.size(); i++) {
            ToolButton b = labelled.get(i);
            b.setLabel(noLabels ? "" : L(keys[i]));
            b.setPreferredSize(new Dimension(noLabels ? 36 : fullWidth[i], 36));
        }
        items.get(1).setVisible(!noTitle);
        items.get(2).setVisible(!noTitle);
        bar.revalidate();
        bar.repaint();
    }

    private ToolButton icon(Glyph glyph, String tipKey, String shortcut, Runnable action) {
        ToolButton b = new ToolButton(style, ToolButton.Layout.ICON, glyph, null).tip(tipKey, shortcut).size(36, 36);
        b.onClick(action);
        return b;
    }

    private JComponent sidebar(Column column, int width) {
        ScrollBody holder = new ScrollBody();
        holder.add(column, BorderLayout.NORTH);
        StyledScrollPane scroll = new StyledScrollPane(holder, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER, style);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setPreferredSize(new Dimension(width, 100));
        return scroll;
    }
    private Column buildLeft() {
        Column col = new Column();

        Fold templates = new Fold("ec.ui.cardTemplates", true);
        templates.body.put(note("ec.ui.templatesHint", MUTED), 0);
        for (Presets.Preset pr : Presets.ALL) {
            ToolButton b = new ToolButton(style, ToolButton.Layout.ROW, Glyph.NEW, "ec.pr." + pr.id()).tip("ec.pr." + pr.id() + ".help", null).size(INNER_W, 30);
            b.onClick(() -> applyPreset(pr.id()));
            templates.body.put(b, 6);
        }
        col.put(templates, 0);

        Fold originals = new Fold("ec.ui.cardOriginals", true);
        originals.body.put(note("ec.ui.originalsHint", MUTED), 0);
        for (OriginalEngines.Original o : OriginalEngines.ALL) {
            ToolButton b = new ToolButton(style, ToolButton.Layout.ROW, Glyph.PLAY, "ec.og." + o.id()).tip("ec.og." + o.id() + ".help", null).size(INNER_W, 30);
            b.onClick(() -> loadOriginal(o.id()));
            originals.body.put(b, 6);
        }
        col.put(originals, 12);

        Fold engines = new Fold("ec.ui.cardEngines", true, true);
        engines.body.put(note("ec.ui.enginesHint", MUTED), 0);
        StyledButton refresh = new StyledButton(style, "ec.ui.refresh");
        refresh.setPreferredSize(new Dimension(INNER_W, 30));
        refresh.addActionListener(e -> refreshEngines());
        engines.body.put(refresh, 6);
        engines.body.put(enginesList, 6);
        col.put(engines, 12);

        Fold nodes = new Fold("ec.ui.cardNodes", true, true);
        nodes.body.put(note("ec.ui.nodesHint", MUTED), 0);
        // search, then category filters; "All" keeps the categories as folds, a filter gives one short list
        StyledTextField search = new StyledTextField(style, "");
        search.setPreferredSize(new Dimension(INNER_W, 28));
        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { changed(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { changed(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { changed(); }
            private void changed() {
                nodeQuery = search.getText().trim().toLowerCase(Locale.ROOT);
                refreshNodeList();
            }
        });
        nodes.body.put(search, 6);
        JPanel chips = new JPanel(new WrapLayout(FlowLayout.LEFT, 4, 4));
        chips.setOpaque(false);
        chips.addComponentListener(new ComponentAdapter() { @Override public void componentResized(ComponentEvent e) { chips.revalidate(); } });
        List<String> filters = new ArrayList<>();
        filters.add("all");
        filters.addAll(List.of(CATEGORIES));
        for (String cat : filters) {
            String label = L(cat.equals("all") ? "ec.ui.all" : "ec.c." + cat);
            StyledButton chip = new StyledButton(style, label);
            int chipW = chip.getFontMetrics(font(11f, false)).stringWidth(label) + 28;
            chip.setPreferredSize(new Dimension(chipW, 26));
            chip.setMinimumSize(new Dimension(chipW, 26));
            chip.addActionListener(e -> {
                nodeCategory = cat;
                refreshNodeList();
            });
            chips.add(chip);
        }
        nodes.body.put(chips, 6);
        nodeList = new Column();
        nodes.body.put(nodeList, 6);
        refreshNodeList();
        col.put(nodes, 12);
        return col;
    }
    private void refreshNodeList() {
        nodeList.removeAll();
        boolean flat = !"all".equals(nodeCategory) || !nodeQuery.isEmpty();
        if (!flat) {
            for (String cat : CATEGORIES) {
                Fold group = new Fold("ec.c." + cat, false);
                JPanel grid = cardGrid();
                for (NodeKind k : Nodes.ALL) if (k.categoryId().equals(cat)) grid.add(nodeCard(k));
                group.body.put(grid, 0);
                nodeList.put(group, 8);
            }
        } else {
            int shown = 0;
            JPanel grid = cardGrid();
            for (NodeKind k : Nodes.ALL) {
                if (!"all".equals(nodeCategory) && !k.categoryId().equals(nodeCategory)) continue;
                if (!nodeQuery.isEmpty() && !k.title().toLowerCase(Locale.ROOT).contains(nodeQuery)) continue;
                grid.add(nodeCard(k));
                shown++;
            }
            nodeList.put(grid, 0);
            if (shown == 0) nodeList.put(note("ec.ui.noNodes", MUTED), 0);
        }
        nodeList.revalidate();
        nodeList.repaint();
        Container scroll = SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
        if (scroll != null) scroll.revalidate();
    }
    private static JPanel cardGrid() {
        JPanel grid = new JPanel(new GridLayout(0, 2, CARD_GAP, CARD_GAP));
        grid.setOpaque(false);
        return grid;
    }
    private NodeCard nodeCard(NodeKind k) {
        String id = k.id();
        return new NodeCard(k, () -> addNode(id));
    }
    private static final class NodeCard extends JComponent {
        private final NodeKind kind;
        private boolean hover;

        NodeCard(NodeKind kind, Runnable onClick) {
            this.kind = kind;
            setPreferredSize(new Dimension(CARD_W, CARD_H));
            setMinimumSize(new Dimension(CARD_W, CARD_H));
            setMaximumSize(new Dimension(CARD_W, CARD_H));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(kind.help());
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                @Override public void mouseClicked(MouseEvent e) { onClick.run(); }
            });
        }
        private String summary() {
            String h = kind.help().trim();
            int dot = h.indexOf('.');
            if (dot > 0) h = h.substring(0, dot + 1);
            return h.length() > 70 ? h.substring(0, 69) + "…" : h;
        }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = smooth(g0);
            int w = getWidth(), h = getHeight();
            g.setColor(hover ? CARD_LINE : CARD);
            g.fillRoundRect(0, 0, w - 1, h - 1, 10, 10);
            g.setColor(NodeCanvas.categoryColor(kind.categoryId()));
            g.fillRoundRect(0, 0, w - 1, 6, 4, 4);
            g.fillRect(0, 3, w - 1, 3);
            int pad = 8, textW = w - 2 * pad;
            g.setFont(font(11f, true));
            g.setColor(TEXT);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(ellipsize(kind.title(), fm, textW), pad, 24);
            g.setFont(font(10f, false));
            g.setColor(MUTED);
            fm = g.getFontMetrics();
            int y = 40;
            List<String> lines = wrap(summary(), fm, textW);
            for (String line : lines) { g.drawString(line, pad, y); y += 13; }
            g.dispose();
        }
        private static List<String> wrap(String text, FontMetrics fm, int maxW) {
            List<String> out = new ArrayList<>();
            StringBuilder line = new StringBuilder();
            for (String word : text.split(" ")) {
                String next = line.isEmpty() ? word : line + " " + word;
                if (fm.stringWidth(next) <= maxW) { line.setLength(0); line.append(next); continue; }
                if (!line.isEmpty()) out.add(line.toString());
                line.setLength(0);
                line.append(word);
            }
            if (!line.isEmpty()) out.add(line.toString());
            if (out.size() > 3) out = new ArrayList<>(out.subList(0, 3));
            if (!out.isEmpty()) {
                int last = out.size() - 1;
                out.set(last, ellipsize(out.get(last), fm, maxW));
            }
            return out;
        }
    }

    private Column buildRight() {
        Column col = new Column();

        Card proj = new Card("ec.ui.cardProject");
        proj.put(fieldLabel("ec.ui.name"), 0);
        nameField.setPreferredSize(new Dimension(INNER_W, 30));
        proj.put(nameField, 2);
        proj.put(note("ec.ui.nameHint", MUTED), 2);
        proj.put(fieldLabel("ec.ui.displayTitle"), 8);
        titleField.setPreferredSize(new Dimension(INNER_W, 30));
        proj.put(titleField, 2);
        proj.put(fieldLabel("ec.ui.curvature"), 8);
        curvatureField.setPreferredSize(new Dimension(INNER_W, 30));
        proj.put(curvatureField, 2);
        proj.put(note("ec.ui.curvatureHint", MUTED), 2);
        openClToggle.setPreferredSize(new Dimension(INNER_W, 32));
        proj.put(openClToggle, 8);
        proj.put(note("ec.ui.openClHint", MUTED), 2);
        col.put(proj, 0);

        Card props = new Card("ec.ui.cardProps");
        props.put(propsHead, 0);
        props.put(propsHelp, 4);
        props.put(propsBody, 8);
        col.put(props, 12);

        Card legend = new Card("ec.ui.cardLegend");
        for (DataType t : DataType.values())
            legend.put(new Text("ec.type." + t.name().toLowerCase(Locale.ROOT), 11.5f, true, NodeCanvas.typeColor(t), 0), 3);
        col.put(legend, 12);

        Card issues = new Card("ec.ui.cardIssues");
        issues.put(note("ec.ui.issuesTip", MUTED), 0);
        issueList.setBackground(CARD);
        issueList.setVisibleRowCount(6);
        issueList.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                Issue is = (Issue) value;
                setText("<html><body style='width:" + (INNER_W - 16) + "px'>" + escape(is.text()) + "</body></html>");
                setForeground(is.error() ? BAD : WARN);
                setBackground(selected ? CARD_LINE : CARD);
                setBorder(new EmptyBorder(4, 4, 4, 4));
                return this;
            }
        });
        issueList.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2) return;
                Issue is = issueList.getSelectedValue();
                if (is == null || is.node() <= 0) return;
                NodeInst n = project.node(is.node());
                if (n == null) return;
                if (n.pass != pass) selectPass(n.pass);
                canvas.selectNode(is.node());
            }
        });
        StyledScrollPane issueScroll = new StyledScrollPane(issueList, style);
        issueScroll.setPreferredSize(new Dimension(INNER_W, 170));
        issues.put(issueScroll, 6);
        col.put(issues, 12);
        return col;
    }

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
        addPass.addActionListener(e -> addPass());
        StyledButton removePass = new StyledButton(style, "ec.ui.removePass");
        removePass.setPreferredSize(new Dimension(92, 32));
        removePass.addActionListener(e -> removePass());
        bar.add(passBox);
        bar.add(passGeometry);
        bar.add(addPass);
        bar.add(removePass);

        StyledScrollPane sp = new StyledScrollPane(canvas, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED, style);
        sp.getViewport().setBackground(EditorKit.CANVAS_BG);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        sp.getHorizontalScrollBar().setUnitIncrement(16);
        previewSlot = new JPanel(new BorderLayout());
        previewSlot.setOpaque(false);
        previewSlot.add(preview, BorderLayout.CENTER);
        previewSlot.setVisible(false);
        center.add(bar, BorderLayout.NORTH);
        center.add(sp, BorderLayout.CENTER);
        center.add(previewSlot, BorderLayout.SOUTH);
        return center;
    }

    private Text fieldLabel(String key) { Text t = new Text(null, 11f, false, MUTED, 0); t.setRaw(L.t(key) + ":"); return t; }
    private Note note(String key, Color color) {
        Note n = new Note(INNER_W, 11f, color);
        n.set(null, color, L.t(key));
        return n;
    }
    private static String escape(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }

    private void bindFields() {
        bindField(nameField, v -> project.name = v.trim());
        bindField(titleField, v -> project.title = v.trim());
        bindField(curvatureField, v -> {
            try { project.curvature = Double.parseDouble(v.trim()); }
            catch (NumberFormatException ignored) { project.curvature = Double.NaN; }
        });
        openClToggle.addActionListener(e -> { if (!syncing) applyProjectToggles(); });
        passGeometry.addActionListener(e -> {
            if (syncing) return;
            snapshot();
            project.setGeometry(pass, GEOMETRY_KEYS[passGeometry.getSelectedIndex()]);
            refreshPassBox();
            refreshAll();
        });
        passBox.addActionListener(e -> { if (!syncing && passBox.getSelectedIndex() >= 0) selectPass(passBox.getSelectedIndex()); });
    }

    private void bindField(JTextField field, java.util.function.Consumer<String> apply) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            void changed() {
                if (syncing) return;
                apply.accept(field.getText());
                canvas.repaint();
                refreshIssues();
            }
            @Override public void insertUpdate(DocumentEvent e) { changed(); }
            @Override public void removeUpdate(DocumentEvent e) { changed(); }
            @Override public void changedUpdate(DocumentEvent e) { changed(); }
        });
    }

    private void bindKeys() {
        InputMap in = getInputMap(WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getActionMap();
        bindKey(in, am, "escape", KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), this::exit);
        bindKey(in, am, "undo", KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK), this::undo);
        bindKey(in, am, "redo", KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK), this::redo);
        bindKey(in, am, "redo2", KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), this::redo);
        bindKey(in, am, "save", KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK), this::save);
        bindKey(in, am, "check", KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), this::check);
        bindKey(in, am, "preview", KeyStroke.getKeyStroke(KeyEvent.VK_F6, 0), this::togglePreview);
        bindKey(in, am, "help", KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0), this::showHelp);
        bindKey(in, am, "delete", KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), canvas::deleteSelected);
        bindKey(in, am, "copy", KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK), canvas::copySelection);
        bindKey(in, am, "paste", KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK), canvas::pasteAtCursor);
        bindKey(in, am, "duplicate", KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK), canvas::duplicateSelection);
        bindKey(in, am, "selectAll", KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK), canvas::selectAll);
    }
    private static void bindKey(InputMap in, ActionMap am, String name, KeyStroke key, Runnable action) {
        in.put(key, name);
        am.put(name, new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { action.run(); } });
    }

    private void addNode(String kind) {
        snapshot();
        int count = (int) project.nodes.stream().filter(x -> x.pass == pass).count();
        NodeInst added = project.add(kind, 60 + (count % 4) * 200, 60 + (count / 4) * 110, pass);
        canvas.selectNode(added.id);
        hint.flash(Tone.OK, L.f("ec.s.nodeAdded", Nodes.get(kind).title()));
        refreshAll();
    }

    private void applyPreset(String id) {
        snapshot();
        try {
            int used = Presets.apply(project, id, pass);
            Layout.arrange(project, used);
            refreshPassBox();
            if (used != pass) selectPass(used);
            hint.flash(Tone.OK, L.f("ec.s.toolAdded", Presets.ALL.stream().filter(x -> x.id().equals(id)).findFirst().orElseThrow().title()));
        } catch (IllegalStateException ex) {
            if (!history.isEmpty()) history.pop();
            hint.flash(Tone.BAD, ex.getMessage());
        }
        refreshAll();
    }

    private void applyProjectToggles() {
        snapshot();
        project.openCl = openClToggle.isSelected();
        refreshAll();
    }

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

    private void arrange() {
        snapshot();
        Layout.arrange(project, pass);
        canvas.repaint();
        hint.flash(Tone.OK, L.t("ec.s.arranged"));
    }
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
        } catch (IllegalArgumentException ex) { hint.flash(Tone.BAD, L.f("ec.s.undoFailed", ex.getMessage())); }
    }

    private void check() {
        EngineBuilder.Result r = EngineBuilder.build(project, false, t -> false);
        showIssues(r.issues());
        if (r.hasErrors()) hint.flash(Tone.BAD, L.t("ec.s.checkBad"));
        else hint.flash(Tone.OK, L.t("ec.s.checkOk"));
    }
    private void save() {
        EngineBuilder.Result r = EngineBuilder.build(project, true, target ->
                ask(this, style, "ec.d.overwriteTitle", L.f("ec.d.overwrite", project.name, target.toString()), 0, "ec.btn.replace", "cancel") == 0);
        showIssues(r.issues());
        if (r.saved()) {
            setDirty(false);
            refreshEngines();
            hint.flash(Tone.OK, L.f("ec.s.saved", r.file().getFileName().toString()));
            inform(this, style, "ec.d.savedTitle", L.f("ec.d.savedText", project.title));
        } else if (r.hasErrors()) {
            Issue first = r.issues().stream().filter(Issue::error).findFirst().orElseThrow();
            hint.flash(Tone.BAD, L.t("ec.s.saveFailed") + " " + first.text());
        } else if (!r.messages().isEmpty()) hint.flash(Tone.WARN, r.messages().getLast());
    }

    private void togglePreview() {
        previewShown = !previewShown;
        previewSlot.setVisible(previewShown);
        if (previewShown) preview.build();
        revalidate();
        repaint();
    }

    private Path projectsFolder() { return EngineBuilder.folder().resolve(EngineBuilder.PROJECTS); }
    private void refreshEngines() {
        enginesList.removeAll();
        Path dir = projectsFolder();
        List<Path> files = new ArrayList<>();
        if (Files.isDirectory(dir)) {
            try (var stream = Files.list(dir)) { stream.filter(p -> p.getFileName().toString().endsWith(".engine.json")).sorted().forEach(files::add); }
            catch (Exception ignored) { }
        }
        if (files.isEmpty()) enginesList.put(note("ec.ui.noEngines", MUTED), 0);
        for (Path file : files) {
            try {
                EcProject p = ProjectIO.fromJson(Files.readString(file, StandardCharsets.UTF_8));
                boolean built = Files.exists(EngineBuilder.folder().resolve(CodeGen.className(p) + ".java"));
                String text = p.name + (built ? "  ●" : "");
                // the label is raw text: ToolButton treats an unknown key as its own text
                ToolButton b = new ToolButton(style, ToolButton.Layout.ROW, Glyph.FOLDER, text).size(INNER_W, 28);
                b.setToolTipText(L.f("ec.ui.engineTip", p.title, p.passCount()));
                b.onClick(() -> loadProject(file));
                b.addMouseListener(new MouseAdapter() {
                    @Override public void mouseReleased(MouseEvent e) {
                        if (SwingUtilities.isRightMouseButton(e)) showEngineMenu(b, e, file, p);
                    }
                });
                enginesList.put(b, 4);
            } catch (Exception ex) { enginesList.put(note("ec.ui.engineBroken", BAD), 4); }
        }
        enginesList.revalidate();
        enginesList.repaint();
    }
    private void showEngineMenu(Component at, MouseEvent e, Path file, EcProject p) {
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(CARD);
        menu.setBorder(BorderFactory.createLineBorder(CARD_LINE));
        menu.add(menuItem(L.t("ec.ui.engMenu.open"), () -> loadProject(file)));
        menu.add(menuItem(L.t("ec.menu.delete"), () -> deleteEngine(file, p)));
        menu.show(at, e.getX(), e.getY());
    }
    private static JMenuItem menuItem(String text, Runnable action) {
        JMenuItem it = new JMenuItem(text);
        it.setBackground(CARD);
        it.setForeground(TEXT);
        it.setFont(font(12f, false));
        it.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        it.addActionListener(ev -> action.run());
        return it;
    }
    private void deleteEngine(Path file, EcProject p) {
        int answer = ask(this, style, "ec.d.deleteTitle", L.f("ec.d.deleteText", p.name), 1, "ec.btn.delete", "cancel");
        if (answer != 0) return;
        String cls = CodeGen.className(p);
        try {
            Path dir = EngineBuilder.folder();
            Files.deleteIfExists(file);
            Files.deleteIfExists(dir.resolve(cls + ".java"));
            Files.deleteIfExists(dir.resolve(cls + ".cl"));
            RenderEngines.reload();
            refreshEngines();
            hint.flash(Tone.OK, L.f("ec.s.engineDeleted", p.name));
        } catch (IOException ex) { hint.flash(Tone.BAD, L.f("ec.s.deleteFailed", ex.getMessage())); }
    }
    private void loadOriginal(String id) {
        if (dirty && project.nodes.size() > 1) {
            int answer = ask(this, style, "unsaved_title", L.t("unsaved_msg"), 0, "save_and_leave", "leave_without_saving", "keep_editing");
            if (answer == 0) { save(); if (dirty) return; }
            else if (answer != 1) return;
        }
        try {
            EcProject built = OriginalEngines.build(id);
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
        } catch (IllegalArgumentException ex) { hint.flash(Tone.BAD, ex.getMessage()); }
    }

    private void loadProject(Path file) {
        if (dirty && project.nodes.size() > 1) {
            int answer = ask(this, style, "unsaved_title", L.t("unsaved_msg"), 0, "save_and_leave", "leave_without_saving", "keep_editing");
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
        } catch (Exception ex) { hint.flash(Tone.BAD, L.f("ec.d.openFailed", ex.getMessage())); }
    }
    private void exit() {
        if (dirty && project.nodes.size() > 1) {
            int answer = ask(this, style, "unsaved_title", L.t("unsaved_msg"), 0, "save_and_leave", "leave_without_saving", "keep_editing");
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

    private void setDirty(boolean value) {
        dirty = value;
        if (value) dirtyText.setKey("unsaved");
        else dirtyText.setRaw("");
    }
    private void onCanvasChanged() {
        refreshProperties();
        refreshIssues();
    }
    private void refreshAll() {
        canvas.repaint();
        refreshProperties();
        refreshIssues();
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
    private void syncFieldsFromProject() {
        syncing = true;
        try {
            nameField.setText(project.name);
            titleField.setText(project.title);
            curvatureField.setText(Double.isFinite(project.curvature) ? String.valueOf(project.curvature) : "");
            openClToggle.setSelected(project.openCl);
        } finally { syncing = false; }
    }
    private void refreshIssues() { showIssues(Validator.validate(project)); }

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

    private void refreshProperties() {
        Integer id = canvas.selectedNode();
        NodeInst n = id == null ? null : project.node(id);
        NodeKind k = n == null ? null : Nodes.find(n.kind);
        propsBody.removeAll();
        editors.clear();
        if (canvas.selectedNodes().size() > 1) {
            propsHead.setRaw(L.f("ec.s.selectedCount", canvas.selectedNodes().size()));
            propsHelp.set(null, MUTED, L.t("ec.ui.multiHelp"));
        }
        else if (k == null) {
            propsHead.setKey("ec.ui.cardProps");
            propsHelp.set(null, MUTED, L.t("ec.ui.noSelection"));
        }
        else {
            propsHead.setRaw(k.title());
            propsHelp.set(null, MUTED, k.help());
            for (Param prm : k.params()) {
                propsBody.put(new Text("ec.p." + prm.key(), 11f, false, MUTED, 0), 6);
                String current = n.params.getOrDefault(prm.key(), prm.def());
                JComponent editor;
                if (prm.kind() == Param.Kind.CHOICE) {
                    String[] labels = prm.choices().stream().map(prm::choiceLabel).toArray(String[]::new);
                    StyledComboBox box = new StyledComboBox(style, labels);
                    box.setPreferredSize(new Dimension(INNER_W, 32));
                    box.setSelectedIndex(Math.max(0, prm.choices().indexOf(current)));
                    editor = box;
                } else {
                    StyledTextField field = new StyledTextField(style, current);
                    field.setPreferredSize(new Dimension(INNER_W, 30));
                    editor = field;
                }
                propsBody.put(editor, 2);
                editors.put(prm.key(), editor);
            }
            if (k.params().isEmpty()) propsBody.put(note("ec.ui.paramsEmpty", MUTED), 0);
            else {
                StyledButton apply = new StyledButton(style, "ec.ui.applyProps");
                int nodeId = id;
                apply.addActionListener(e -> applyProperties(nodeId));
                propsBody.put(apply, 10);
            }
        }
        propsBody.revalidate();
        propsBody.repaint();
    }
    private void applyProperties(int nodeId) {
        NodeInst n = project.node(nodeId);
        NodeKind k = n == null ? null : Nodes.find(n.kind);
        if (k == null) return;
        snapshot();
        for (Param prm : k.params()) {
            JComponent c = editors.get(prm.key());
            if (c instanceof StyledComboBox box) {
                int i = box.getSelectedIndex();
                if (i >= 0) n.params.put(prm.key(), prm.choices().get(i));
            }
            else if (c instanceof StyledTextField f) n.params.put(prm.key(), f.getText().trim());
        }
        refreshIssues();
        canvas.repaint();
        hint.flash(Tone.OK, L.t("ec.s.propsApplied"));
    }
    private static final class Fold extends Column {
        final Column body = new Column();
        private final String title;
        private final boolean card;
        private boolean open;

        Fold(String key, boolean card) { this(key, card, false); }
        Fold(String key, boolean card, boolean startOpen) {
            this.card = card;
            this.title = card ? L(key).toUpperCase(Locale.ROOT) : L(key);
            put(body, 0);
            addMouseListener(new MouseAdapter() { @Override public void mouseClicked(MouseEvent e) { if (!open || e.getY() < headHeight()) setOpen(!open); } });
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override public void mouseMoved(MouseEvent e) {
                    setCursor(Cursor.getPredefinedCursor(e.getY() < headHeight() ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                }
            });
            setOpen(startOpen);
        }
        private int headHeight() { return card ? 23 : 20; }
        void setOpen(boolean value) {
            open = value;
            body.setVisible(value);
            setBorder(card ? new EmptyBorder(headHeight(), 8, 7, 8) : new EmptyBorder(headHeight(), 2, 0, 2));
            revalidate();
            repaint();
            Container scroll = SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
            if (scroll != null) {
                scroll.revalidate();
                scroll.repaint();
            }
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            int w = getWidth(), h = getHeight();
            if (card) {
                g2.setColor(CARD);
                g2.fillRoundRect(0, 0, w - 1, h - 1, 12, 12);
                g2.setColor(CARD_LINE);
                g2.drawRoundRect(0, 0, w - 1, h - 1, 12, 12);
            }
            g2.setFont(font(10.5f, true));
            g2.setColor(MUTED);
            int pad = card ? 10 : 2;
            int arrowX = w - (card ? 22 : 18);
            g2.drawString(ellipsize(title, g2.getFontMetrics(), arrowX - pad - 6), pad, card ? 16 : 14);
            (open ? Glyph.UP : Glyph.DOWN).paint(g2, arrowX, card ? 4 : 2, 14, MUTED);
            g2.dispose();
        }
    }
}