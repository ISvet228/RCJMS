package Project.EngineCreator;
import Project.Helpers.EditorKit;

import Project.StyleUI.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

public final class NodeCanvas extends JPanel {
    private static final int PORT_R = 6, PORT_HIT = 9;

    private static final String COMMENT_KEY = "\u0000comment";
    private static final Color COMMENT = new Color(150, 160, 178);
    private static final Color BG = EditorKit.CANVAS_BG, GRID = new Color(40, 43, 52), NODE_BG = EditorKit.CARD, TEXT = EditorKit.TEXT, DIM = EditorKit.MUTED,
            SELECT = EditorKit.HILITE, LINE = EditorKit.CARD_LINE;

    private record ClipNode(String kind, int dx, int dy, Map<String, String> params) {}
    private record ClipLink(int from, String fromPort, int to, String toPort) {}
    private record Clip(List<ClipNode> nodes, List<ClipLink> links, Point origin) {}

    private static Clip clipboard = new Clip(List.of(), List.of(), new Point());

    private record PortRef(int node, String port, boolean output) {}
    private enum Mode { NONE, NODES, LINK, MARQUEE, PAN }

    private EcProject project;
    private int pass;
    private final Runnable beforeChange, changed, arrange;
    private final Consumer<String> status;
    private Style style = Style.FLAT;

    private final LinkedHashSet<Integer> selection = new LinkedHashSet<>();
    private final Set<Integer> errorNodes = new HashSet<>(), warnNodes = new HashSet<>();

    private Mode mode = Mode.NONE;
    private PortRef linkStart;
    private Point linkEnd;
    private final Map<Integer, Point> dragOffsets = new HashMap<>();
    private boolean snapshotTaken;
    private Rectangle marquee;
    private Point panScreen, panView;
    private Point lastMouse = new Point(80, 80);

    private JComponent editor;
    private int editorNode = -1;
    private String editorKey;
    private List<String> editorChoices = List.of();

    NodeCanvas(EcProject project, Runnable beforeChange, Runnable changed, Consumer<String> status, Runnable arrange) {
        this.project = project;
        this.beforeChange = beforeChange;
        this.changed = changed;
        this.status = status;
        this.arrange = arrange;
        setLayout(null);
        setBackground(BG);
        setFocusable(true);
        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { screenPoint = e.getPoint(); press(logical(e)); }
            @Override public void mouseReleased(MouseEvent e) { screenPoint = e.getPoint(); release(logical(e)); }
        });
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent e) { lastMouse = logical(e).getPoint(); }
            @Override public void mouseDragged(MouseEvent e) { drag(logical(e)); }
        });
        addMouseWheelListener(e -> {
            e.consume();
            if (!e.isControlDown()) {
                JScrollPane pane = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
                if (pane == null) return;
                JScrollBar bar = e.isShiftDown() ? pane.getHorizontalScrollBar() : pane.getVerticalScrollBar();
                if (!bar.isVisible()) return;
                int step = e.getUnitsToScroll() * bar.getUnitIncrement();
                bar.setValue(bar.getValue() + step);
                return;
            }
            double old = zoom;
            double next = Math.clamp(old * (e.getWheelRotation() < 0 ? 1.1 : 1 / 1.1), ZOOM_MIN, ZOOM_MAX);
            if (next == old) return;
            JScrollPane sp = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
            Point inView = sp == null ? e.getPoint() : SwingUtilities.convertPoint(this, e.getPoint(), sp.getViewport());
            double lx = e.getX() / old, ly = e.getY() / old;
            zoom = next;
            if (sp == null) { revalidate(); repaint(); return; }
            sp.validate();
            JViewport vp = sp.getViewport();
            int x = (int) Math.round(lx * zoom) - inView.x;
            int y = (int) Math.round(ly * zoom) - inView.y;
            Dimension view = vp.getViewSize(), ext = vp.getExtentSize();
            x = Math.clamp(x, 0, view.width - ext.width);
            y = Math.clamp(y, 0, view.height - ext.height);
            vp.setViewPosition(new Point(x, y));
            repaint();
        });
    }

    static final double ZOOM_MIN = 0.4, ZOOM_MAX = 2.5;
    private double zoom = 1.0;
    private Point screenPoint = new Point();

    private MouseEvent logical(MouseEvent e) {
        if (zoom == 1.0) return e;
        return new MouseEvent(e.getComponent(), e.getID(), e.getWhen(), e.getModifiersEx(), (int) Math.round(e.getX() / zoom), (int) Math.round(e.getY() / zoom), e.getClickCount(),
                e.isPopupTrigger(), e.getButton());
    }
    private void placeEditor(JComponent ed, int x, int y, int w, int h) {
        ed.setBounds((int) Math.round(x * zoom), (int) Math.round(y * zoom), (int) Math.round(w * zoom), (int) Math.round(h * zoom));
    }
    private void refreshLayout() {
        revalidate();
        Container sp = SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
        if (sp != null) sp.revalidate();
        repaint();
    }
    void setStyle(Style style) { this.style = style; }
    @Override public Dimension getPreferredSize() {
        int w = 1600, h = 1000;
        for (NodeInst n : project.nodes) {
            if (n.pass != pass) continue;
            w = Math.max(w, n.x + NodeGeometry.width(n) + 200);
            h = Math.max(h, n.y + NodeGeometry.height(n) + 200);
        }
        return new Dimension((int) Math.round(w * zoom), (int) Math.round(h * zoom));
    }
    void setProject(EcProject p) {
        project = p;
        selection.clear();
        refreshLayout();
    }
    void setPass(int value) {
        pass = value;
        selection.clear();
        refreshLayout();
    }
    int pass() { return pass; }

    Integer selectedNode() { return selection.size() == 1 ? selection.getFirst() : null; }

    List<Integer> selectedNodes() { return List.copyOf(selection); }
    void selectNode(Integer id) {
        selection.clear();
        if (id != null) selection.add(id);
        repaint();
        changed.run();
    }

    void setHighlights(Collection<Integer> errors, Collection<Integer> warnings) {
        errorNodes.clear();
        errorNodes.addAll(errors);
        warnNodes.clear();
        warnNodes.addAll(warnings);
        repaint();
    }
    void selectAll() {
        selection.clear();
        for (NodeInst n : project.nodes) if (n.pass == pass) selection.add(n.id);
        status.accept(L.f("ec.s.selectedCount", selection.size()));
        changed.run();
    }

    void deleteSelected() {
        if (selection.isEmpty()) return;
        beforeChange.run();
        for (int id : selection) project.removeNode(id);
        int count = selection.size();
        selection.clear();
        status.accept(L.f("ec.s.deleted", count));
        changed.run();
    }

    void copySelection() {
        if (selection.isEmpty()) { status.accept(L.t("ec.s.nothingToCopy")); return; }
        clipboard = buildClip();
        status.accept(L.f("ec.s.copied", clipboard.nodes().size()));
    }

    void pasteAtCursor() { pasteClip(clipboard, lastMouse); }
    void duplicateSelection() {
        if (selection.isEmpty()) { status.accept(L.t("ec.s.nothingToCopy")); return; }
        Clip copy = buildClip();
        pasteClip(copy, new Point(copy.origin().x + 30, copy.origin().y + 30));
    }
    private Clip buildClip() {
        List<NodeInst> sel = new ArrayList<>();
        for (NodeInst n : project.nodes) if (n.pass == pass && selection.contains(n.id)) sel.add(n);
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        for (NodeInst n : sel) { minX = Math.min(minX, n.x); minY = Math.min(minY, n.y); }
        List<ClipNode> nodes = new ArrayList<>();
        Map<Integer, Integer> index = new HashMap<>();
        for (NodeInst n : sel) {
            index.put(n.id, nodes.size());
            nodes.add(new ClipNode(n.kind, n.x - minX, n.y - minY, new LinkedHashMap<>(n.params)));
        }
        List<ClipLink> links = new ArrayList<>();
        for (Link l : project.links) if (index.containsKey(l.from()) && index.containsKey(l.to()))
                links.add(new ClipLink(index.get(l.from()), l.fromPort(), index.get(l.to()), l.toPort()));
        return new Clip(nodes, links, new Point(minX, minY));
    }

    private void pasteClip(Clip clip, Point at) {
        if (clip.nodes().isEmpty()) { status.accept(L.t("ec.s.nothingToPaste")); return; }
        beforeChange.run();
        List<Integer> ids = new ArrayList<>();
        for (ClipNode c : clip.nodes()) {
            NodeInst n = project.add(c.kind(), at.x + c.dx(), at.y + c.dy(), pass);
            n.params.putAll(c.params());
            ids.add(n.id);
        }
        for (ClipLink l : clip.links()) project.setLink(new Link(ids.get(l.from()), l.fromPort(), ids.get(l.to()), l.toPort()));
        selection.clear();
        selection.addAll(ids);
        status.accept(L.f("ec.s.pasted", ids.size()));
        changed.run();
    }

    private void addNodeAt(String kind, Point p) {
        beforeChange.run();
        NodeInst n = project.add(kind, p.x, p.y, pass);
        selection.clear();
        selection.add(n.id);
        status.accept(L.f("ec.s.nodeAdded", Nodes.get(kind).title()));
        changed.run();
    }

    private PortRef hitPort(Point p) {
        List<NodeInst> nodes = nodesInPass();
        for (int idx = nodes.size() - 1; idx >= 0; idx--) {
            NodeInst n = nodes.get(idx);
            NodeKind k = Nodes.find(n.kind);
            if (k == null) continue;
            for (int i = 0; i < k.inputs().size(); i++)
                if (new Point(n.x, NodeGeometry.rowY(n, i)).distance(p) <= PORT_HIT) return new PortRef(n.id, k.inputs().get(i).name(), false);
            for (int i = 0; i < k.outputs().size(); i++)
                if (new Point(n.x + NodeGeometry.width(n), NodeGeometry.rowY(n, i)).distance(p) <= PORT_HIT) return new PortRef(n.id, k.outputs().get(i).name(), true);
        }
        return null;
    }

    private NodeInst hitNode(Point p) {
        List<NodeInst> nodes = nodesInPass();
        for (int idx = nodes.size() - 1; idx >= 0; idx--) {
            NodeInst n = nodes.get(idx);
            if (NodeGeometry.bounds(n).contains(p)) return n;
        }
        return null;
    }
    private int paramRowAt(NodeInst n, Point p) {
        NodeKind k = Nodes.find(n.kind);
        if (k == null) return -1;
        for (int i = 0; i < k.params().size(); i++) if (NodeGeometry.paramRect(n, i).contains(p)) return i;
        return -1;
    }
    private Link hitLink(Point p) {
        for (Link l : project.links) {
            Point a = portPoint(l.from(), l.fromPort(), true);
            Point b = portPoint(l.to(), l.toPort(), false);
            if (a == null || b == null) continue;
            CubicCurve2D.Double c = curve(a, b);
            for (double t = 0; t <= 1.0001; t += 0.05) if (bezierPoint(c, t).distance(p) < 6) return l;
        }
        return null;
    }

    private List<NodeInst> nodesInPass() {
        List<NodeInst> list = new ArrayList<>();
        for (NodeInst n : project.nodes) if (n.pass == pass) list.add(n);
        return list;
    }
    private Point portPoint(int node, String port, boolean output) {
        NodeInst n = project.node(node);
        if (n == null || n.pass != pass) return null;
        NodeKind k = Nodes.find(n.kind);
        if (k == null) return null;
        if (output) {
            int i = k.outputIndex(port);
            return i < 0 ? null : new Point(n.x + NodeGeometry.width(n), NodeGeometry.rowY(n, i));
        }
        int i = k.inputIndex(port);
        return i < 0 ? null : new Point(n.x, NodeGeometry.rowY(n, i));
    }
    private static CubicCurve2D.Double curve(Point a, Point b) {
        double dx = Math.max(40, Math.abs(b.x - a.x) / 2.0);
        return new CubicCurve2D.Double(a.x, a.y, a.x + dx, a.y, b.x - dx, b.y, b.x, b.y);
    }
    private static Point bezierPoint(CubicCurve2D.Double c, double t) {
        double u = 1 - t;
        double x = u * u * u * c.x1 + 3 * u * u * t * c.ctrlx1 + 3 * u * t * t * c.ctrlx2 + t * t * t * c.x2;
        double y = u * u * u * c.y1 + 3 * u * u * t * c.ctrly1 + 3 * u * t * t * c.ctrly2 + t * t * t * c.y2;
        return new Point((int) Math.round(x), (int) Math.round(y));
    }

    private void press(MouseEvent e) {
        requestFocusInWindow();
        Point p = e.getPoint();
        lastMouse = p;
        finishEditor(true);

        if (SwingUtilities.isMiddleMouseButton(e)) { startPan(e); return; }
        if (SwingUtilities.isRightMouseButton(e)) { showMenu(e); return; }
        if (!SwingUtilities.isLeftMouseButton(e)) return;
        boolean additive = e.isControlDown() || e.isMetaDown();

        PortRef port = hitPort(p);
        if (port != null) {
            if (port.output()) {
                linkStart = port;
            } else {
                Link existing = project.incoming(port.node(), port.port());
                if (existing != null) {
                    beforeChange.run();
                    project.removeLink(existing);
                    linkStart = new PortRef(existing.from(), existing.fromPort(), true);
                    status.accept(L.t("ec.s.detached"));
                } else {
                    linkStart = port;
                }
            }
            linkEnd = p;
            mode = Mode.LINK;
            repaint();
            return;
        }

        NodeInst n = hitNode(p);
        if (n != null) {
            int row = paramRowAt(n, p);
            if (e.getClickCount() == 2 && row >= 0) { openEditor(n, row); return; }
            if (additive && selection.contains(n.id)) {
                selection.remove(n.id);
                changed.run();
                repaint();
                return;
            }
            if (additive) selection.add(n.id);
            else if (!selection.contains(n.id)) { selection.clear(); selection.add(n.id); }
            dragOffsets.clear();
            for (int id : selection) {
                NodeInst s = project.node(id);
                if (s != null) dragOffsets.put(id, new Point(p.x - s.x, p.y - s.y));
            }
            snapshotTaken = false;
            mode = Mode.NODES;
            changed.run();
            repaint();
            return;
        }

        if (!additive) selection.clear();
        marquee = new Rectangle(p.x, p.y, 0, 0);
        mode = Mode.MARQUEE;
        changed.run();
        repaint();
    }

    private void startPan(MouseEvent e) {
        JViewport vp = viewport();
        if (vp == null) return;
        panScreen = e.getLocationOnScreen();
        panView = vp.getViewPosition();
        mode = Mode.PAN;
        setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
    }

    private JViewport viewport() {
        return getParent() instanceof JViewport vp ? vp : null;
    }

    private void drag(MouseEvent e) {
        switch (mode) {
            case PAN -> {
                JViewport vp = viewport();
                if (vp == null) return;
                Point now = e.getLocationOnScreen();
                int x = panView.x - (now.x - panScreen.x);
                int y = panView.y - (now.y - panScreen.y);
                Dimension view = getSize(), extent = vp.getExtentSize();
                x = Math.clamp(x, 0, view.width - extent.width);
                y = Math.clamp(y, 0, view.height - extent.height);
                vp.setViewPosition(new Point(x, y));
            }
            case NODES -> {
                if (!snapshotTaken) { beforeChange.run(); snapshotTaken = true; }
                for (Map.Entry<Integer, Point> off : dragOffsets.entrySet()) {
                    NodeInst n = project.node(off.getKey());
                    if (n == null) continue;
                    n.x = Math.max(0, e.getX() - off.getValue().x);
                    n.y = Math.max(0, e.getY() - off.getValue().y);
                }
                revalidate();
                repaint();
            }
            case LINK -> { linkEnd = e.getPoint(); repaint(); }
            case MARQUEE -> {
                Point s = new Point(marquee.x, marquee.y), p = e.getPoint();
                marquee = new Rectangle(Math.min(s.x, p.x), Math.min(s.y, p.y), Math.abs(p.x - s.x), Math.abs(p.y - s.y));
                repaint();
            }
            default -> { }
        }
        lastMouse = e.getPoint();
    }

    private void release(MouseEvent e) {
        switch (mode) {
            case LINK -> {
                PortRef target = hitPort(e.getPoint());
                if (target != null) tryConnect(linkStart, target);
                else status.accept(L.t("ec.s.linkNone"));
                linkStart = null;
                linkEnd = null;
                changed.run();
            }
            case MARQUEE -> {
                for (NodeInst n : nodesInPass()) {
                    if (marquee != null && marquee.intersects(NodeGeometry.bounds(n))) selection.add(n.id);
                }
                marquee = null;
                status.accept(L.f("ec.s.selectedCount", selection.size()));
                changed.run();
            }
            case PAN -> setCursor(Cursor.getDefaultCursor());
            default -> { }
        }
        mode = Mode.NONE;
        dragOffsets.clear();
        repaint();
    }

    private void tryConnect(PortRef a, PortRef b) {
        PortRef from, to;
        if (a.output() && !b.output()) { from = a; to = b; }
        else if (!a.output() && b.output()) { from = b; to = a; }
        else { status.accept(L.t("ec.s.linkDirection")); return; }
        if (from.node() == to.node()) { status.accept(L.t("ec.s.linkSelf")); return; }

        NodeKind tk = Nodes.get(Objects.requireNonNull(project.node(to.node())).kind);
        DataType want = tk.inputs().get(tk.inputIndex(to.port())).type();
        DataType have = Graph.outType(project, from.node(), from.port(), 0);
        if (have != null && Graph.incompatible(have, want)) {
            status.accept(L.f("ec.s.linkType", want.label(), have.label()));
            return;
        }
        if (Graph.upstream(project, from.node()).contains(to.node())) {
            status.accept(L.t("ec.s.linkCycle"));
            return;
        }
        beforeChange.run();
        project.setLink(new Link(from.node(), from.port(), to.node(), to.port()));
        status.accept(L.t("ec.s.linkDone"));
    }

    private void openEditor(NodeInst n, int row) {
        finishEditor(true);
        NodeKind k = Nodes.find(n.kind);
        if (k == null || row < 0 || row >= k.params().size()) return;
        Param prm = k.params().get(row);
        Rectangle r = NodeGeometry.paramRect(n, row);
        String current = n.params.getOrDefault(prm.key(), prm.def());
        JComponent ed;
        if (prm.kind() == Param.Kind.CHOICE) {
            String[] labels = prm.choices().stream().map(prm::choiceLabel).toArray(String[]::new);
            StyledComboBox box = new StyledComboBox(style, labels);
            box.setSelectedIndex(Math.max(0, prm.choices().indexOf(current)));
            box.addActionListener(e -> finishEditor(true));
            bindEscape(box);
            ed = box;
        } else {
            StyledTextField field = new StyledTextField(style, current);
            field.addActionListener(e -> finishEditor(true));
            field.addFocusListener(new FocusAdapter() { @Override public void focusLost(FocusEvent e) { finishEditor(true); } });
            bindEscape(field);
            ed = field;
        }
        placeEditor(ed, r.x, r.y - 2, r.width, r.height + 4);
        add(ed);
        editor = ed;
        editorNode = n.id;
        editorKey = prm.key();
        editorChoices = prm.choices();
        setComponentZOrder(ed, 0);
        ed.requestFocusInWindow();
        if (ed instanceof StyledTextField f) f.selectAll();
        repaint();
    }
    private void openCommentEditor(NodeInst n) {
        finishEditor(true);
        StyledTextField field = new StyledTextField(style, n.comment);
        ((javax.swing.text.AbstractDocument) field.getDocument()).setDocumentFilter(new javax.swing.text.DocumentFilter() {
            @Override public void insertString(FilterBypass fb, int off, String str, javax.swing.text.AttributeSet a) throws javax.swing.text.BadLocationException {
                int room = NodeGeometry.COMMENT_MAX - fb.getDocument().getLength();
                if (room > 0) super.insertString(fb, off, str.length() > room ? str.substring(0, room) : str, a);
            }
            @Override public void replace(FilterBypass fb, int off, int len, String str, javax.swing.text.AttributeSet a) throws javax.swing.text.BadLocationException {
                int room = NodeGeometry.COMMENT_MAX - (fb.getDocument().getLength() - len);
                if (room >= 0) super.replace(fb, off, len, str.length() > room ? str.substring(0, room) : str, a);
            }
        });
        field.addActionListener(e -> finishEditor(true));
        field.addFocusListener(new FocusAdapter() { @Override public void focusLost(FocusEvent e) { finishEditor(true); } });
        bindEscape(field);
        Rectangle r = NodeGeometry.commentRect(n);
        placeEditor(field, r.x, r.y - 2, r.width, r.height + 4);
        add(field);
        editor = field;
        editorNode = n.id;
        editorKey = COMMENT_KEY;
        editorChoices = List.of();
        setComponentZOrder(field, 0);
        field.requestFocusInWindow();
        field.selectAll();
        repaint();
    }

    private void bindEscape(JComponent c) {
        c.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancelEdit");
        c.getActionMap().put("cancelEdit", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { finishEditor(false); }
        });
    }
    private void finishEditor(boolean apply) {
        if (editor == null) return;
        JComponent ed = editor;
        String value;
        if (ed instanceof StyledComboBox box) {
            int i = box.getSelectedIndex();
            value = i >= 0 && i < editorChoices.size() ? editorChoices.get(i) : null;
        } else value = ((StyledTextField) ed).getText().trim();
        int nodeId = editorNode;
        String key = editorKey;
        editor = null;
        editorNode = -1;
        editorKey = null;
        remove(ed);
        repaint();
        NodeInst n = project.node(nodeId);
        if (COMMENT_KEY.equals(key)) {
            if (!apply || n == null || value == null || value.equals(n.comment)) return;
            beforeChange.run();
            n.comment = value;
            status.accept(L.t("ec.s.valueSet"));
            changed.run();
            return;
        }
        if (!apply || n == null || value == null || value.equals(n.params.get(key))) return;
        beforeChange.run();
        n.params.put(key, value);
        status.accept(L.t("ec.s.valueSet"));
        changed.run();
    }

    private void showMenu(MouseEvent e) {
        Point p = e.getPoint();
        JPopupMenu menu = styledMenu();
        Link link = hitLink(p);
        NodeInst n = hitNode(p);
        if (link != null) {
            menu.add(item(L.t("ec.menu.deleteLink"), null, () -> {
                beforeChange.run();
                project.removeLink(link);
                status.accept(L.t("ec.s.linkRemoved"));
                changed.run();
            }));
        } else if (n != null) {
            if (!selection.contains(n.id)) { selection.clear(); selection.add(n.id); changed.run(); }
            NodeKind k = Nodes.get(n.kind);
            if (!k.params().isEmpty()) menu.add(item(L.t("ec.menu.edit"), null, () -> openEditor(n, 0)));
            menu.add(item(L.t("ec.menu.comment"), null, () -> openCommentEditor(n)));
            menu.add(item(L.t("ec.menu.copy"), "Ctrl+C", this::copySelection));
            menu.add(item(L.t("ec.menu.duplicate"), "Ctrl+D", this::duplicateSelection));
            menu.addSeparator();
            menu.add(item(L.t("ec.menu.delete"), "Del", this::deleteSelected));
        }
        else {
            if (!clipboard.nodes().isEmpty()) menu.add(item(L.t("ec.menu.pasteHere"), "Ctrl+V", () -> pasteClip(clipboard, p)));
            menu.add(item(L.t("ec.menu.selectAll"), "Ctrl+A", this::selectAll));
            menu.add(item(L.t("ec.menu.arrange"), null, arrange));
            menu.addSeparator();
            menu.add(addNodeMenu(p));
        }
        menu.show(this, screenPoint.x, screenPoint.y);
    }

    private JMenu addNodeMenu(Point at) {
        JMenu root = styledMenu(new JMenu(L.t("ec.menu.addNode")));
        Map<String, JMenu> categories = new LinkedHashMap<>();
        for (NodeKind k : Nodes.ALL) {
            JMenu cat = categories.computeIfAbsent(k.categoryId(), id -> styledMenu(new JMenu(
                    Nodes.ALL.stream().filter(x -> x.categoryId().equals(id)).findFirst().orElseThrow().category())));
            cat.add(styledItem(new JMenuItem(k.title()), () -> addNodeAt(k.id(), at)));
        }
        for (JMenu cat : categories.values()) root.add(cat);
        return root;
    }

    private JPopupMenu styledMenu() {
        JPopupMenu m = new JPopupMenu();
        m.setBackground(NODE_BG);
        m.setBorder(BorderFactory.createLineBorder(LINE));
        return m;
    }
    private static JMenu styledMenu(JMenu m) {
        styleMenuItem(m);
        JPopupMenu popup = m.getPopupMenu();
        popup.setBackground(NODE_BG);
        popup.setBorder(BorderFactory.createLineBorder(LINE));
        return m;
    }
    private JMenuItem item(String text, String shortcut, Runnable action) { return styledItem(new JMenuItem(shortcut == null ? text : text + "    " + shortcut), action); }
    private static JMenuItem styledItem(JMenuItem it, Runnable action) {
        styleMenuItem(it);
        it.addActionListener(e -> action.run());
        return it;
    }
    private static void styleMenuItem(JMenuItem it) {
        it.setBackground(NODE_BG);
        it.setForeground(TEXT);
        it.setFont(EditorKit.font(12f, false));
        it.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
    }
    @Override protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g.scale(zoom, zoom);
        int lw = (int) (getWidth() / zoom) + 1, lh = (int) (getHeight() / zoom) + 1;
        g.setColor(GRID);
        for (int x = 0; x < lw; x += 24) for (int y = 0; y < lh; y += 24) g.fillRect(x, y, 1, 1);

        for (Link l : project.links) {
            Point a = portPoint(l.from(), l.fromPort(), true);
            Point b = portPoint(l.to(), l.toPort(), false);
            if (a == null || b == null) continue;
            g.setColor(typeColor(Graph.outType(project, l.from(), l.fromPort(), 0)));
            g.setStroke(new BasicStroke(2f));
            g.draw(curve(a, b));
        }
        if (linkStart != null && linkEnd != null && mode == Mode.LINK) {
            Point a = portPoint(linkStart.node(), linkStart.port(), linkStart.output());
            if (a != null) {
                g.setColor(new Color(255, 255, 255, 170));
                g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{6, 4}, 0));
                g.draw(linkStart.output() ? curve(a, linkEnd) : curve(linkEnd, a));
            }
        }
        if (marquee != null) {
            g.setColor(new Color(SELECT.getRed(), SELECT.getGreen(), SELECT.getBlue(), 40));
            g.fill(marquee);
            g.setColor(SELECT);
            g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{4, 3}, 0));
            g.draw(marquee);
        }
        for (NodeInst n : nodesInPass()) paintNode(g, n);
        if (nodesInPass().size() <= 1) paintEmptyHint(g);
        g.dispose();
    }
    private void paintEmptyHint(Graphics2D g) {
        String[] lines = L.t("ec.ui.emptyHint").split("\\n");
        g.setFont(EditorKit.font(13f, false));
        FontMetrics fm = g.getFontMetrics();
        Rectangle vis = getVisibleRect();
        int y = vis.y + vis.height / 2 - lines.length * (fm.getHeight() / 2);
        int cx = vis.x + vis.width / 2;
        g.setColor(TEXT);
        for (String line : lines) {
            g.drawString(line, cx - fm.stringWidth(line) / 2, y);
            y += fm.getHeight() + 4;
        }
    }
    private void paintNode(Graphics2D g, NodeInst n) {
        NodeKind k = Nodes.find(n.kind);
        if (k == null) return;
        int w = NodeGeometry.width(n), h = NodeGeometry.height(n);
        RoundRectangle2D.Float box = new RoundRectangle2D.Float(n.x, n.y, w, h, 10, 10);
        g.setColor(NODE_BG);
        g.fill(box);
        g.setColor(categoryColor(k.categoryId()));
        g.fillRoundRect(n.x, n.y, w, NodeGeometry.HEADER, 10, 10);
        g.fillRect(n.x, n.y + NodeGeometry.HEADER - 8, w, 8);

        boolean chosen = selection.contains(n.id);
        Color border = new Color(70, 76, 90);
        float width = 1f;
        if (errorNodes.contains(n.id)) { border = new Color(230, 70, 70); width = 2.5f; }
        else if (warnNodes.contains(n.id)) { border = new Color(230, 170, 50); width = 2f; }
        if (chosen) { border = SELECT; width = 2.5f; }
        g.setStroke(new BasicStroke(width));
        g.setColor(border);
        g.draw(box);
        g.setStroke(new BasicStroke(1f));

        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        g.setColor(Color.WHITE);
        g.drawString(k.title(), n.x + NodeGeometry.PAD, n.y + 17);

        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        for (int i = 0; i < k.inputs().size(); i++) {
            Port port = k.inputs().get(i);
            int cy = NodeGeometry.rowY(n, i);
            drawPort(g, new Point(n.x, cy), port.type(), project.incoming(n.id, port.name()) != null);
            g.setColor(TEXT);
            g.drawString(port.name(), n.x + NodeGeometry.PORT_SPACE, cy + 4);
        }
        for (int i = 0; i < k.outputs().size(); i++) {
            Port port = k.outputs().get(i);
            int cy = NodeGeometry.rowY(n, i);
            DataType resolved = Graph.outType(project, n.id, port.name(), 0);
            drawPort(g, new Point(n.x + w, cy), resolved == null ? port.type() : resolved, true);
            g.setColor(TEXT);
            g.drawString(port.name(), n.x + w - NodeGeometry.PORT_SPACE - g.getFontMetrics().stringWidth(port.name()), cy + 4);
        }

        for (int i = 0; i < k.params().size(); i++) {
            Rectangle r = NodeGeometry.paramRect(n, i);
            boolean editing = editor != null && editorNode == n.id && k.params().get(i).key().equals(editorKey);
            if (editing) continue;
            g.setColor(DIM);
            g.drawString(NodeGeometry.paramText(n, k.params().get(i)), r.x + 6, r.y + r.height - 4);
        }
        if (!(editor != null && editorNode == n.id && COMMENT_KEY.equals(editorKey))) {
            java.util.List<String> lines = NodeGeometry.commentLines(n);
            if (!lines.isEmpty()) {
                g.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 11));
                g.setColor(COMMENT);
                int top = NodeGeometry.commentTop(n);
                for (int i = 0; i < lines.size(); i++)
                    g.drawString(lines.get(i), n.x + NodeGeometry.PAD, top + i * NodeGeometry.COMMENT_LINE + 11);
            }
        }
    }

    private void drawPort(Graphics2D g, Point p, DataType t, boolean filled) {
        Color c = typeColor(t);
        if (filled) {
            g.setColor(c);
            g.fillOval(p.x - PORT_R, p.y - PORT_R, PORT_R * 2, PORT_R * 2);
        } else {
            g.setColor(BG);
            g.fillOval(p.x - PORT_R, p.y - PORT_R, PORT_R * 2, PORT_R * 2);
            g.setColor(c);
            g.setStroke(new BasicStroke(2f));
            g.drawOval(p.x - PORT_R, p.y - PORT_R, PORT_R * 2, PORT_R * 2);
            g.setStroke(new BasicStroke(1f));
        }
    }
    static Color typeColor(DataType t) {
        if (t == null) return new Color(150, 150, 150);
        return switch (t) {
            case FLOAT -> new Color(0x4FC3F7);
            case INT -> new Color(0x81C784);
            case BOOL -> new Color(0xFFB74D);
            case COLOR -> new Color(0xF06292);
            case ANY -> new Color(0xBDBDBD);
        };
    }
    static Color categoryColor(String categoryId) {
        float hue = (Math.abs(categoryId.hashCode()) % 360) / 360f;
        return Color.getHSBColor(hue, 0.45f, 0.42f);
    }
}