package Project.CreatorKit;

import Project.Helpers.*;
import Project.EngineCreator.*;
import Project.StyleUI.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

public final class NodeCanvas extends JPanel {
    //region Variables
    //region Canvas Colors
    private static final int PORT_R = 6, PORT_HIT = 9;
    private static final String COMMENT_KEY = "\u0000comment";
    private static final Color COMMENT = new Color(150, 160, 178);
    private static final Color BG = EditorKit.CANVAS_BG, GRID = new Color(40, 43, 52), NODE_BG = EditorKit.CARD, TEXT = EditorKit.TEXT, DIM = EditorKit.MUTED,
            SELECT = EditorKit.HILITE, LINE = EditorKit.CARD_LINE;
    private static final Color LIVE = new Color(0xA5D6A7);
    //endregion
    //region Project State
    private CkProject project;
    private int pass;
    private Style style = Style.FLAT;
    private boolean groupsEnabled = true;
    private String emptyKey = "ec.ui.emptyHint";
    private int emptyBelow = 1;
    //endregion
    //region Callbacks
    private final Runnable beforeChange, changed, arrange;
    private final Consumer<String> status;
    private java.util.function.Consumer<String> insideOpener;
    private java.util.function.Supplier<List<NodeKind>> addableKinds = Nodes::engineKinds;
    private java.util.function.IntFunction<String> liveText;
    private final javax.swing.Timer liveClock = new javax.swing.Timer(100, _ -> { if (isShowing()) repaint(); });
    //endregion
    //region Selection And Clipboard
    private final LinkedHashSet<Integer> selection = new LinkedHashSet<>();
    private final Set<Integer> errorNodes = new HashSet<>(), warnNodes = new HashSet<>();
    private static Clip clipboard = new Clip(List.of(), List.of(), new Point());
    //endregion
    //region Pointer State
    private Mode mode = Mode.NONE;
    private PortRef linkStart;
    private Point linkEnd;
    private final Map<Integer, Point> dragOffsets = new HashMap<>();
    private boolean snapshotTaken;
    private Rectangle marquee;
    private Point panScreen, panView;
    private Point lastMouse = new Point(80, 80);
    //endregion
    //region View State
    public static final double ZOOM_MIN = 0.4, ZOOM_MAX = 2.5;
    private double zoom = 1.0;
    private Point screenPoint = new Point();
    //endregion
    //region Inline Editor
    private JComponent editor;
    private int editorNode = -1;
    private String editorKey;
    private List<String> editorChoices = List.of();
    //endregion
    //endregion

    //region Constructors
    public NodeCanvas(CkProject project, Runnable beforeChange, Runnable changed, Consumer<String> status, Runnable arrange) {
        this.project = project;
        this.beforeChange = beforeChange;
        this.changed = changed;
        this.status = status;
        this.arrange = arrange;
        setLayout(null);
        setBackground(BG);
        setFocusable(true);
        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent mouseEvent) { screenPoint = mouseEvent.getPoint(); press(logical(mouseEvent)); }
            @Override public void mouseReleased(MouseEvent mouseEvent) { screenPoint = mouseEvent.getPoint(); release(logical(mouseEvent)); }
        });
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent mouseEvent) { lastMouse = logical(mouseEvent).getPoint(); }
            @Override public void mouseDragged(MouseEvent mouseEvent) { drag(logical(mouseEvent)); }
        });
        addMouseWheelListener(mouseWheelEvent -> {
            mouseWheelEvent.consume();
            if (!mouseWheelEvent.isControlDown()) {
                JScrollPane pane = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
                if (pane == null) return;
                JScrollBar bar = mouseWheelEvent.isShiftDown() ? pane.getHorizontalScrollBar() : pane.getVerticalScrollBar();
                if (!bar.isVisible()) return;
                int step = mouseWheelEvent.getUnitsToScroll() * bar.getUnitIncrement();
                bar.setValue(bar.getValue() + step);
                return;
            }
            double old = zoom;
            double next = Math.clamp(old * (mouseWheelEvent.getWheelRotation() < 0 ? 1.1 : 1 / 1.1), ZOOM_MIN, ZOOM_MAX);
            if (next == old) return;
            JScrollPane scrollPane = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
            Point inView = scrollPane == null ? mouseWheelEvent.getPoint() : SwingUtilities.convertPoint(this, mouseWheelEvent.getPoint(), scrollPane.getViewport());
            double lx = mouseWheelEvent.getX() / old, ly = mouseWheelEvent.getY() / old;
            zoom = next;
            if (scrollPane == null) { revalidate(); repaint(); return; }
            scrollPane.validate();
            JViewport viewport = scrollPane.getViewport();
            int x = (int) Math.round(lx * zoom) - inView.x;
            int y = (int) Math.round(ly * zoom) - inView.y;
            Dimension view = viewport.getViewSize(), ext = viewport.getExtentSize();
            x = Math.clamp(x, 0, view.width - ext.width);
            y = Math.clamp(y, 0, view.height - ext.height);
            viewport.setViewPosition(new Point(x, y));
            repaint();
        });
    }
    //endregion

    //region Canvas Setup
    public void setEmptyHint(String key, int below) { emptyKey = key; emptyBelow = below; repaint(); }
    public void setAddableKinds(java.util.function.Supplier<List<NodeKind>> kinds) { addableKinds = kinds; }
    public void setLiveText(java.util.function.IntFunction<String> f) {
        liveText = f;
        if (f == null) liveClock.stop();
        else if (!liveClock.isRunning()) liveClock.start();
        repaint();
    }
    public void setStyle(Style style) { this.style = style; }
    public void setProject(CkProject ckProject) {
        project = ckProject;
        selection.clear();
        refreshLayout();
    }
    public void setPass(int value) {
        pass = value;
        selection.clear();
        refreshLayout();
    }
    public int pass() { return pass; }
    private List<NodeInst> nodesInPass() {
        List<NodeInst> list = new ArrayList<>();
        for (NodeInst nodeInst : project.nodes) if (nodeInst.pass == pass) list.add(nodeInst);
        return list;
    }
    public void setHighlights(Collection<Integer> errors, Collection<Integer> warnings) {
        errorNodes.clear();
        errorNodes.addAll(errors);
        warnNodes.clear();
        warnNodes.addAll(warnings);
        repaint();
    }
    public void setInsideOpener(java.util.function.Consumer<String> opener) { insideOpener = opener; }
    public void setGroupsEnabled(boolean on) { groupsEnabled = on; }
    public void refresh() { refreshLayout(); }
    private void refreshLayout() {
        revalidate();
        Container container = SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
        if (container != null) container.revalidate();
        repaint();
    }
    @Override public Dimension getPreferredSize() {
        int w = 1600, h = 1000;
        for (NodeInst nodeInst : project.nodes) {
            if (nodeInst.pass != pass) continue;
            w = Math.max(w, nodeInst.x + NodeGeometry.width(nodeInst) + 200);
            h = Math.max(h, nodeInst.y + NodeGeometry.height(nodeInst) + 200);
        }
        return new Dimension((int) Math.round(w * zoom), (int) Math.round(h * zoom));
    }
    //endregion

    //region Selection
    public Integer selectedNode() { return selection.size() == 1 ? selection.getFirst() : null; }
    public List<Integer> selectedNodes() { return List.copyOf(selection); }
    public void selectNode(Integer id) {
        selection.clear();
        if (id != null) selection.add(id);
        repaint();
        changed.run();
    }
    public void selectAll() {
        selection.clear();
        for (NodeInst nodeInst : project.nodes) if (nodeInst.pass == pass) selection.add(nodeInst.id);
        status.accept(L.f("ec.s.selectedCount", selection.size()));
        changed.run();
    }
    public void deleteSelected() {
        if (selection.isEmpty()) return;
        beforeChange.run();
        for (int id : selection) project.removeNode(id);
        int count = selection.size();
        selection.clear();
        status.accept(L.f("ec.s.deleted", count));
        changed.run();
    }
    public void duplicateSelection() {
        if (selection.isEmpty()) { status.accept(L.t("ec.s.nothingToCopy")); return; }
        Clip copy = buildClip();
        pasteClip(copy, new Point(copy.origin().x + 30, copy.origin().y + 30));
    }
    //endregion

    //region Clipboard
    public void copySelection() {
        if (selection.isEmpty()) { status.accept(L.t("ec.s.nothingToCopy")); return; }
        clipboard = buildClip();
        status.accept(L.f("ec.s.copied", clipboard.nodes().size()));
    }
    public void pasteAtCursor() { pasteClip(clipboard, visibleLogicalRect().contains(lastMouse) ? lastMouse : viewCentre()); }
    private Clip buildClip() {
        List<NodeInst> sel = new ArrayList<>();
        for (NodeInst nodeInst : project.nodes) if (nodeInst.pass == pass && selection.contains(nodeInst.id)) sel.add(nodeInst);
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        for (NodeInst nodeInst : sel) { minX = Math.min(minX, nodeInst.x); minY = Math.min(minY, nodeInst.y); }
        List<ClipNode> nodes = new ArrayList<>();
        Map<Integer, Integer> index = new HashMap<>();
        for (NodeInst nodeInst : sel) {
            index.put(nodeInst.id, nodes.size());
            nodes.add(new ClipNode(nodeInst.kind, nodeInst.x - minX, nodeInst.y - minY, new LinkedHashMap<>(nodeInst.params)));
        }
        List<ClipLink> links = new ArrayList<>();
        for (Link link : project.links) if (index.containsKey(link.from()) && index.containsKey(link.to()))
                links.add(new ClipLink(index.get(link.from()), link.fromPort(), index.get(link.to()), link.toPort()));
        return new Clip(nodes, links, new Point(minX, minY));
    }
    private void pasteClip(Clip clip, Point point) {
        if (clip.nodes().isEmpty()) { status.accept(L.t("ec.s.nothingToPaste")); return; }
        beforeChange.run();
        List<Integer> ids = new ArrayList<>();
        for (ClipNode clipNode : clip.nodes()) {
            NodeInst nodeInst = project.add(clipNode.kind(), point.x + clipNode.dx(), point.y + clipNode.dy(), pass);
            nodeInst.params.putAll(clipNode.params());
            ids.add(nodeInst.id);
        }
        for (ClipLink clipLink : clip.links()) project.setLink(new Link(ids.get(clipLink.from()), clipLink.fromPort(), ids.get(clipLink.to()), clipLink.toPort()));
        selection.clear();
        selection.addAll(ids);
        status.accept(L.f("ec.s.pasted", ids.size()));
        changed.run();
        if (!ids.isEmpty()) revealNode(project.node(ids.getFirst()));
    }
    private record ClipNode(String kind, int dx, int dy, Map<String, String> params) { }
    private record ClipLink(int from, String fromPort, int to, String toPort) { }
    private record Clip(List<ClipNode> nodes, List<ClipLink> links, Point origin) { }
    //endregion

    //region Node Editing
    public void addNodeAt(String kind, Point point) {
        beforeChange.run();
        Point at = new Point(point);
        for (int tries = 0; tries < 50 && occupied(at); tries++) at.translate(40, 40);
        NodeInst nodeInst = project.add(kind, at.x, at.y, pass);
        selection.clear();
        selection.add(nodeInst.id);
        status.accept(L.f("ec.s.nodeAdded", Nodes.get(kind).title()));
        changed.run();
    }
    private boolean occupied(Point point) {
        for (NodeInst nodeInst : project.nodes) {
            if (nodeInst.pass != pass) continue;
            if (Math.abs(nodeInst.x - point.x) < 60 && Math.abs(nodeInst.y - point.y) < 40) return true;
        }
        return false;
    }
    private void beginNodeDrag(Point point) {
        dragOffsets.clear();
        for (int id : selection) {
            NodeInst nodeInst = project.node(id);
            if (nodeInst != null) dragOffsets.put(id, new Point(point.x - nodeInst.x, point.y - nodeInst.y));
        }
        snapshotTaken = false;
        mode = Mode.NODES;
        changed.run();
        repaint();
    }
    private NodeInst hitNode(Point point) {
        List<NodeInst> nodes = nodesInPass();
        for (int idx = nodes.size() - 1; idx >= 0; idx--) {
            NodeInst nodeInst = nodes.get(idx);
            if (NodeGeometry.bounds(nodeInst).contains(point)) return nodeInst;
        }
        return null;
    }
    private int paramRowAt(NodeInst nodeInst, Point point) {
        NodeKind nodeKind = Nodes.find(nodeInst.kind);
        if (nodeKind == null) return -1;
        for (int i = 0; i < nodeKind.params().size(); i++) if (NodeGeometry.paramRect(nodeInst, i).contains(point)) return i;
        return -1;
    }
    private PortRef hitPort(Point point) {
        List<NodeInst> nodes = nodesInPass();
        for (int idx = nodes.size() - 1; idx >= 0; idx--) {
            NodeInst nodeInst = nodes.get(idx);
            NodeKind nodeKind = Nodes.find(nodeInst.kind);
            if (nodeKind == null) continue;
            for (int i = 0; i < nodeKind.inputs().size(); i++)
                if (new Point(nodeInst.x, NodeGeometry.rowY(nodeInst, i)).distance(point) <= PORT_HIT) return new PortRef(nodeInst.id, nodeKind.inputs().get(i).name(), false);
            for (int i = 0; i < nodeKind.outputs().size(); i++)
                if (new Point(nodeInst.x + NodeGeometry.width(nodeInst), NodeGeometry.rowY(nodeInst, i)).distance(point) <= PORT_HIT)
                    return new PortRef(nodeInst.id, nodeKind.outputs().get(i).name(), true);
        }
        return null;
    }
    //endregion

    //region Groups And Custom Nodes
    public void groupSelection() {
        if (selection.isEmpty()) { status.accept(L.t("ec.g.nothingSelected")); return; }
        beforeChange.run();
        NodeGroup nodeGroup = project.createGroup(selection);
        status.accept(L.f("ec.g.created", nodeGroup.name));
        changed.run();
        repaint();
    }
    public void makeCustomNode() {
        try { CustomNodes.check(project, selection); }
        catch (IllegalArgumentException exception) { status.accept(exception.getMessage()); return; }
        String[] texts = CkDialogs.customTexts(this, style, "ec.g.createTitle", L.t("ec.g.customDefault"), "");
        if (texts == null) return;
        beforeChange.run();
        try {
            NodeInst node = CustomNodes.create(project, selection, limit(texts[0]), texts[1]);
            selection.clear();
            selection.add(node.id);
            status.accept(L.f("ec.g.customCreated", CustomNodes.titleOf(node.kind)));
            changed.run();
            refreshLayout();
            revealNode(node);
        } catch (IOException exception) {
            status.accept(L.f("ec.g.customSaveFailed", exception.getMessage()));
            changed.run();
        }
        repaint();
    }
    // ---------- groups and custom nodes ----------
    private NodeGroup groupHeaderAt(Point point) {
        for (NodeGroup nodeGroup : project.groups) {
            Rectangle rectangle = NodeGeometry.groupBounds(project, nodeGroup, pass);
            if (rectangle != null && new Rectangle(rectangle.x, rectangle.y, rectangle.width, NodeGeometry.GROUP_HEAD).contains(point)) return nodeGroup;
        }
        return null;
    }
    private void paintGroups(Graphics2D g2d) {
        for (NodeGroup grp : project.groups) {
            Rectangle rectangle = NodeGeometry.groupBounds(project, grp, pass);
            if (rectangle == null) continue;
            Color color = Color.decode(grp.color);
            boolean picked = !grp.nodes.isEmpty() && selection.containsAll(grp.nodes);
            g2d.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 36));
            g2d.fillRect(rectangle.x, rectangle.y, rectangle.width, rectangle.height);
            g2d.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 70));
            g2d.fillRect(rectangle.x, rectangle.y, rectangle.width, NodeGeometry.GROUP_HEAD);
            g2d.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), picked ? 230 : 150));
            g2d.setStroke(new BasicStroke(picked ? 2f : 1.2f));
            g2d.drawRect(rectangle.x, rectangle.y, rectangle.width, rectangle.height);
            g2d.setStroke(new BasicStroke(1f));
            g2d.setFont(FontCache.get(Font.SANS_SERIF, Font.BOLD, 12));
            g2d.setColor(color);
            g2d.drawString(grp.name, rectangle.x + 8, rectangle.y + NodeGeometry.GROUP_HEAD - 8);
        }
    }
    private void renameGroup(NodeGroup nodeGroup) {
        String name = CkDialogs.text(this, style, "ec.menu.renameGroup", "ec.g.renamePrompt", nodeGroup.name);
        if (name == null) return;
        beforeChange.run();
        nodeGroup.name = limit(name);
        changed.run();
        repaint();
    }
    private void addColorItems(JMenu menu, NodeGroup nodeGroup) {
        for (String hex : NodeGroup.PALETTE) {
            JMenuItem menuItem = styledItem(new JMenuItem(hex + (hex.equals(nodeGroup.color) ? "  ✓" : "")), () -> setGroupColor(nodeGroup, hex));
            menuItem.setIcon(CkDialogs.swatch(Color.decode(hex)));
            menu.add(menuItem);
        }
    }
    private void setGroupColor(NodeGroup nodeGroup, String hex) {
        beforeChange.run();
        nodeGroup.color = hex;
        changed.run();
        repaint();
    }
    private void openInside(String kind) { if (insideOpener != null) insideOpener.accept(kind); }
    private void editCustomTexts(String kind) {
        CustomNodes.Def def = CustomNodes.def(kind);
        if (def == null) return;
        String[] t = CkDialogs.customTexts(this, style, "ec.g.editTitle", def.title(), def.description());
        if (t == null) return;
        try {
            CustomNodes.updateTexts(kind, limit(t[0]), t[1]);
            status.accept(L.f("ec.g.textsSaved", t[0]));
        }
        catch (IOException exception) { status.accept(L.f("ec.g.customSaveFailed", exception.getMessage())); }
        changed.run();
        repaint();
    }
    private void disbandGroup(NodeGroup nodeGroup) {
        beforeChange.run();
        project.groups.remove(nodeGroup);
        status.accept(L.f("ec.g.disbanded", nodeGroup.name));
        changed.run();
        repaint();
    }
    private static String limit(String text) {
        String t = text.strip();
        return t.length() > NodeGroup.NAME_MAX ? t.substring(0, NodeGroup.NAME_MAX) : t;
    }
    private void addGroupItems(JPopupMenu menu) {
        if (!groupsEnabled || selection.isEmpty()) return;
        menu.add(item(L.t("ec.menu.group"), "Ctrl+G", this::groupSelection));
        menu.add(item(L.t("ec.menu.customNode"), "Ctrl+K", this::makeCustomNode));
        boolean grouped = false;
        for (int id : selection)
            if (project.groupOf(id) != null) {
                grouped = true;
                break;
            }
        if (grouped) menu.add(item("ec.menu.ungroup", null, () -> {
            beforeChange.run();
            project.ungroup(selection);
            changed.run();
            repaint();
        }));
    }
    //endregion

    //region Wires
    private Point portPoint(int node, String port, boolean output) {
        NodeInst nodeInst = project.node(node);
        if (nodeInst == null || nodeInst.pass != pass) return null;
        NodeKind nodeKind = Nodes.find(nodeInst.kind);
        if (nodeKind == null) return null;
        if (output) {
            int i = nodeKind.outputIndex(port);
            return i < 0 ? null : new Point(nodeInst.x + NodeGeometry.width(nodeInst), NodeGeometry.rowY(nodeInst, i));
        }
        int i = nodeKind.inputIndex(port);
        return i < 0 ? null : new Point(nodeInst.x, NodeGeometry.rowY(nodeInst, i));
    }
    private static CubicCurve2D.Double curve(Point point, Point b) {
        double dx = Math.max(40, Math.abs(b.x - point.x) / 2.0);
        return new CubicCurve2D.Double(point.x, point.y, point.x + dx, point.y, b.x - dx, b.y, b.x, b.y);
    }
    private static Point bezierPoint(CubicCurve2D.Double c, double t) {
        double u = 1 - t;
        double x = u * u * u * c.x1 + 3 * u * u * t * c.ctrlx1 + 3 * u * t * t * c.ctrlx2 + t * t * t * c.x2;
        double y = u * u * u * c.y1 + 3 * u * u * t * c.ctrly1 + 3 * u * t * t * c.ctrly2 + t * t * t * c.y2;
        return new Point((int) Math.round(x), (int) Math.round(y));
    }
    private Link hitLink(Point point) {
        for (Link link : project.links) {
            Point a = portPoint(link.from(), link.fromPort(), true);
            Point b = portPoint(link.to(), link.toPort(), false);
            if (a == null || b == null) continue;
            CubicCurve2D.Double c = curve(a, b);
            for (double t = 0; t <= 1.0001; t += 0.05) if (bezierPoint(c, t).distance(point) < 6) return link;
        }
        return null;
    }
    private void tryConnect(PortRef portRef, PortRef b) {
        PortRef from, to;
        if (portRef.output() && !b.output()) { from = portRef; to = b; }
        else if (!portRef.output() && b.output()) { from = b; to = portRef; }
        else { status.accept(L.t("ec.s.linkDirection")); return; }
        if (from.node() == to.node()) { status.accept(L.t("ec.s.linkSelf")); return; }

        NodeKind nodeKind = Nodes.get(Objects.requireNonNull(project.node(to.node())).kind);
        DataType want = nodeKind.inputs().get(nodeKind.inputIndex(to.port())).type();
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
    private record PortRef(int node, String port, boolean output) { }
    //endregion

    //region Navigation
    public Rectangle visibleLogicalRect() {
        JScrollPane scrollPane = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
        Rectangle rectangle = scrollPane == null ? new Rectangle(0, 0, getWidth(), getHeight()) : scrollPane.getViewport().getViewRect();
        return new Rectangle((int) (rectangle.x / zoom), (int) (rectangle.y / zoom), (int) (rectangle.width / zoom), (int) (rectangle.height / zoom));
    }
    public Point viewCentre() {
        Rectangle rectangle = visibleLogicalRect();
        return new Point(rectangle.x + rectangle.width / 2, rectangle.y + rectangle.height / 2);
    }
    public void revealNode(NodeInst nodeInst) {
        if (nodeInst == null) return;
        JScrollPane scrollPane = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
        if (scrollPane != null) scrollPane.validate();
        int m = 40;
        int w = NodeGeometry.width(nodeInst), h = NodeGeometry.height(nodeInst);
        scrollRectToVisible(new Rectangle((int) Math.round((nodeInst.x - m) * zoom), (int) Math.round((nodeInst.y - m) * zoom),
                (int) Math.round((w + 2 * m) * zoom), (int) Math.round((h + 2 * m) * zoom)));
    }
    private MouseEvent logical(MouseEvent mouseEvent) {
        if (zoom == 1.0) return mouseEvent;
        return new MouseEvent(mouseEvent.getComponent(), mouseEvent.getID(), mouseEvent.getWhen(), mouseEvent.getModifiersEx(),
                (int) Math.round(mouseEvent.getX() / zoom), (int) Math.round(mouseEvent.getY() / zoom), mouseEvent.getClickCount(),
                mouseEvent.isPopupTrigger(), mouseEvent.getButton());
    }
    private void startPan(MouseEvent mouseEvent) {
        JViewport vp = viewport();
        if (vp == null) return;
        panScreen = mouseEvent.getLocationOnScreen();
        panView = vp.getViewPosition();
        mode = Mode.PAN;
        setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
    }
    private JViewport viewport() { return getParent() instanceof JViewport viewport ? viewport : null; }
    //endregion

    //region Mouse Input
    private void press(MouseEvent mouseEvent) {
        requestFocusInWindow();
        Point point = mouseEvent.getPoint();
        lastMouse = point;
        finishEditor(true);

        if (SwingUtilities.isMiddleMouseButton(mouseEvent)) { startPan(mouseEvent); return; }
        if (SwingUtilities.isRightMouseButton(mouseEvent)) { showMenu(mouseEvent); return; }
        if (!SwingUtilities.isLeftMouseButton(mouseEvent)) return;
        boolean additive = mouseEvent.isControlDown() || mouseEvent.isMetaDown();

        PortRef port = hitPort(point);
        if (port != null) {
            if (port.output()) linkStart = port;
            else {
                Link existing = project.incoming(port.node(), port.port());
                if (existing != null) {
                    beforeChange.run();
                    project.removeLink(existing);
                    linkStart = new PortRef(existing.from(), existing.fromPort(), true);
                    status.accept(L.t("ec.s.detached"));
                }
                else linkStart = port;
            }
            linkEnd = point;
            mode = Mode.LINK;
            repaint();
            return;
        }

        NodeInst nodeInst = hitNode(point);
        if (nodeInst != null) {
            int row = paramRowAt(nodeInst, point);
            if (mouseEvent.getClickCount() == 2 && CustomNodes.isCustom(nodeInst.kind)) {
                mode = Mode.NONE;
                String kind = nodeInst.kind;
                SwingUtilities.invokeLater(() -> openInside(kind));
                return;
            }
            if (mouseEvent.getClickCount() == 2 && row >= 0) { openEditor(nodeInst, row); return; }
            if (additive && selection.contains(nodeInst.id)) {
                selection.remove(nodeInst.id);
                changed.run();
                repaint();
                return;
            }
            if (additive) selection.add(nodeInst.id);
            else if (!selection.contains(nodeInst.id)) { selection.clear(); selection.add(nodeInst.id); }
            beginNodeDrag(point);
            return;
        }

        NodeGroup group = groupHeaderAt(point);
        if (group != null) {
            if (mouseEvent.getClickCount() == 2) {
                mode = Mode.NONE;
                SwingUtilities.invokeLater(() -> renameGroup(group));
                return;
            }
            if (!additive) selection.clear();
            selection.addAll(group.nodes);
            beginNodeDrag(point);
            return;
        }

        if (!additive) selection.clear();
        marquee = new Rectangle(point.x, point.y, 0, 0);
        mode = Mode.MARQUEE;
        changed.run();
        repaint();
    }
    private void drag(MouseEvent mouseEvent) {
        switch (mode) {
            case PAN -> {
                JViewport vp = viewport();
                if (vp == null) return;
                Point now = mouseEvent.getLocationOnScreen();
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
                    NodeInst nodeInst = project.node(off.getKey());
                    if (nodeInst == null) continue;
                    boolean grouped = project.groupOf(nodeInst.id) != null;
                    nodeInst.x = Math.max(grouped ? NodeGeometry.GROUP_PAD : 0, mouseEvent.getX() - off.getValue().x);
                    nodeInst.y = Math.max(grouped ? NodeGeometry.GROUP_PAD + NodeGeometry.GROUP_HEAD : 0, mouseEvent.getY() - off.getValue().y);
                }
                revalidate();
                repaint();
            }
            case LINK -> { linkEnd = mouseEvent.getPoint(); repaint(); }
            case MARQUEE -> {
                Point point = new Point(marquee.x, marquee.y), p = mouseEvent.getPoint();
                marquee = new Rectangle(Math.min(point.x, p.x), Math.min(point.y, p.y), Math.abs(p.x - point.x), Math.abs(p.y - point.y));
                repaint();
            }
            default -> { }
        }
        lastMouse = mouseEvent.getPoint();
    }
    private void release(MouseEvent mouseEvent) {
        switch (mode) {
            case LINK -> {
                PortRef target = hitPort(mouseEvent.getPoint());
                if (target != null) tryConnect(linkStart, target);
                else status.accept(L.t("ec.s.linkNone"));
                linkStart = null;
                linkEnd = null;
                changed.run();
            }
            case MARQUEE -> {
                for (NodeInst nodeInst : nodesInPass()) if (marquee != null && marquee.intersects(NodeGeometry.bounds(nodeInst))) selection.add(nodeInst.id);
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
    private enum Mode { NONE, NODES, LINK, MARQUEE, PAN }
    //endregion

    //region Inline Editing
    public boolean editing() { return editor != null; }
    private void openEditor(NodeInst nodeInst, int row) {
        finishEditor(true);
        NodeKind nodeKind = Nodes.find(nodeInst.kind);
        if (nodeKind == null || row < 0 || row >= nodeKind.params().size()) return;
        Param prm = nodeKind.params().get(row);
        Rectangle rectangle = NodeGeometry.paramRect(nodeInst, row);
        String current = nodeInst.params.getOrDefault(prm.key(), prm.def());
        JComponent component;
        if (prm.kind() == Param.Kind.CHOICE) {
            String[] labels = prm.choices().stream().map(prm::choiceLabel).toArray(String[]::new);
            StyledComboBox box = new StyledComboBox(style, labels);
            box.setSelectedIndex(Math.max(0, prm.choices().indexOf(current)));
            box.addActionListener(_ -> finishEditor(true));
            bindEscape(box);
            component = box;
        } else {
            StyledTextField field = new StyledTextField(style, current);
            field.addActionListener(_ -> finishEditor(true));
            field.addFocusListener(new FocusAdapter() { @Override public void focusLost(FocusEvent focusEvent) { finishEditor(true); } });
            bindEscape(field);
            component = field;
        }
        placeEditor(component, rectangle.x, rectangle.y - 2, rectangle.width, rectangle.height + 4);
        add(component);
        editor = component;
        editorNode = nodeInst.id;
        editorKey = prm.key();
        editorChoices = prm.choices();
        setComponentZOrder(component, 0);
        component.requestFocusInWindow();
        if (component instanceof StyledTextField styledTextField) styledTextField.selectAll();
        repaint();
    }
    private void openCommentEditor(NodeInst nodeInst) {
        finishEditor(true);
        StyledTextField field = new StyledTextField(style, nodeInst.comment);
        ((javax.swing.text.AbstractDocument) field.getDocument()).setDocumentFilter(new javax.swing.text.DocumentFilter() {
            @Override public void insertString(FilterBypass filterBypass, int off, String str, javax.swing.text.AttributeSet attributeSet) throws javax.swing.text.BadLocationException {
                int room = NodeGeometry.COMMENT_MAX - filterBypass.getDocument().getLength();
                if (room > 0) super.insertString(filterBypass, off, str.length() > room ? str.substring(0, room) : str, attributeSet);
            }
            @Override public void replace(FilterBypass filterBypass, int off, int len, String str, javax.swing.text.AttributeSet attributeSet) throws javax.swing.text.BadLocationException {
                int room = NodeGeometry.COMMENT_MAX - (filterBypass.getDocument().getLength() - len);
                if (room >= 0) super.replace(filterBypass, off, len, str.length() > room ? str.substring(0, room) : str, attributeSet);
            }
        });
        field.addActionListener(_ -> finishEditor(true));
        field.addFocusListener(new FocusAdapter() { @Override public void focusLost(FocusEvent focusEvent) { finishEditor(true); } });
        bindEscape(field);
        Rectangle rectangle = NodeGeometry.commentRect(nodeInst);
        placeEditor(field, rectangle.x, rectangle.y - 2, rectangle.width, rectangle.height + 4);
        add(field);
        editor = field;
        editorNode = nodeInst.id;
        editorKey = COMMENT_KEY;
        editorChoices = List.of();
        setComponentZOrder(field, 0);
        field.requestFocusInWindow();
        field.selectAll();
        repaint();
    }
    private void bindEscape(JComponent component) {
        component.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancelEdit");
        component.getActionMap().put("cancelEdit", new AbstractAction() { @Override public void actionPerformed(ActionEvent actionEvent) { finishEditor(false); } });
    }
    private void finishEditor(boolean apply) {
        if (editor == null) return;
        JComponent component = editor;
        String value;
        if (component instanceof StyledComboBox box) {
            int i = box.getSelectedIndex();
            value = i >= 0 && i < editorChoices.size() ? editorChoices.get(i) : null;
        }
        else value = ((StyledTextField) component).getText().trim();
        int nodeId = editorNode;
        String key = editorKey;
        editor = null;
        editorNode = -1;
        editorKey = null;
        remove(component);
        repaint();
        NodeInst nodeInst = project.node(nodeId);
        if (COMMENT_KEY.equals(key)) {
            if (!apply || nodeInst == null || value == null || value.equals(nodeInst.comment)) return;
            beforeChange.run();
            nodeInst.comment = value;
            status.accept(L.t("ec.s.valueSet"));
            changed.run();
            return;
        }
        if (!apply || nodeInst == null || value == null) return;
        Param prm = Nodes.get(nodeInst.kind).param(key);
        if (prm != null && prm.kind() == Param.Kind.COLOR) {
            String hex = Param.normalizeHex(value);
            if (hex == null) { status.accept(L.f("ec.v.param.color", Nodes.get(nodeInst.kind).title(), prm.label())); return; }
            value = hex;
        }
        if (value.equals(nodeInst.params.get(key))) return;
        beforeChange.run();
        nodeInst.params.put(key, value);
        status.accept(L.t("ec.s.valueSet"));
        changed.run();
    }
    private void placeEditor(JComponent component, int x, int y, int w, int h) {
        component.setBounds((int) Math.round(x * zoom), (int) Math.round(y * zoom), (int) Math.round(w * zoom), (int) Math.round(h * zoom));
    }
    //endregion

    //region Menus
    private void showMenu(MouseEvent mouseEvent) {
        Point point = mouseEvent.getPoint();
        JPopupMenu menu = styledMenu();
        Link link = hitLink(point);
        NodeInst nodeInst = hitNode(point);
        NodeGroup group = nodeInst == null ? groupHeaderAt(point) : null;
        if (link != null) {
            menu.add(item("ec.menu.deleteLink", null, () -> {
                beforeChange.run();
                project.removeLink(link);
                status.accept(L.t("ec.s.linkRemoved"));
                changed.run();
            }));
        }
        else if (nodeInst != null) {
            if (!selection.contains(nodeInst.id)) { selection.clear(); selection.add(nodeInst.id); changed.run(); }
            NodeKind nodeKind = Nodes.get(nodeInst.kind);
            if (!nodeKind.params().isEmpty()) menu.add(item("ec.menu.edit", null, () -> openEditor(nodeInst, 0)));
            if (CustomNodes.isCustom(nodeInst.kind)) {
                String kind = nodeInst.kind;
                menu.add(item("ec.menu.openInside", null, () -> openInside(kind)));
                menu.add(item("ec.menu.customText", null, () -> editCustomTexts(kind)));
            }
            menu.add(item("ec.menu.comment", null, () -> openCommentEditor(nodeInst)));
            menu.add(item(L.t("ec.menu.copy"), "Ctrl+C", this::copySelection));
            menu.add(item(L.t("ec.menu.duplicate"), "Ctrl+D", this::duplicateSelection));
            menu.addSeparator();
            addGroupItems(menu);
            menu.add(item(L.t("ec.menu.delete"), "Del", this::deleteSelected));
        }
        else if (group != null) {
            menu.add(item("ec.menu.renameGroup", null, () -> renameGroup(group)));
            JMenu colors = styledMenu(new JMenu("ec.menu.groupColor"));
            addColorItems(colors, group);
            menu.add(colors);
            menu.add(item("ec.menu.disbandGroup", null, () -> disbandGroup(group)));
        }
        else {
            if (!clipboard.nodes().isEmpty()) menu.add(item(L.t("ec.menu.pasteHere"), "Ctrl+V", () -> pasteClip(clipboard, point)));
            menu.add(item(L.t("ec.menu.selectAll"), "Ctrl+A", this::selectAll));
            menu.add(item("ec.menu.arrange", null, arrange));
            menu.addSeparator();
            addGroupItems(menu);
            if (!selection.isEmpty()) menu.addSeparator();
            menu.add(addNodeMenu(point));
        }
        menu.show(this, screenPoint.x, screenPoint.y);
    }
    private JMenu addNodeMenu(Point point) {
        JMenu root = styledMenu(new JMenu("ec.menu.addNode"));
        Map<String, JMenu> categories = new LinkedHashMap<>();
        for (NodeKind nodeKind : addableKinds.get()) {
            JMenu cat = categories.computeIfAbsent(nodeKind.categoryId(), _ -> styledMenu(new JMenu(nodeKind.category())));
            cat.add(styledItem(new JMenuItem(nodeKind.title()), () -> addNodeAt(nodeKind.id(), point)));
        }
        for (JMenu cat : categories.values()) root.add(cat);
        return root;
    }
    private JPopupMenu styledMenu() {
        JPopupMenu popupMenu = new JPopupMenu();
        popupMenu.setBackground(NODE_BG);
        popupMenu.setBorder(BorderFactory.createLineBorder(LINE));
        return popupMenu;
    }
    private static JMenu styledMenu(JMenu menu) {
        styleMenuItem(menu);
        JPopupMenu popup = menu.getPopupMenu();
        popup.setBackground(NODE_BG);
        popup.setBorder(BorderFactory.createLineBorder(LINE));
        return menu;
    }
    private JMenuItem item(String text, String shortcut, Runnable action) { return styledItem(new JMenuItem(shortcut == null ? text : text + "    " + shortcut), action); }
    private static JMenuItem styledItem(JMenuItem menuItem, Runnable action) {
        styleMenuItem(menuItem);
        menuItem.addActionListener(_ -> action.run());
        return menuItem;
    }
    private static void styleMenuItem(JMenuItem menuItem) {
        menuItem.setBackground(NODE_BG);
        menuItem.setForeground(TEXT);
        menuItem.setFont(EditorKit.font(12f, false));
        menuItem.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
    }
    //endregion

    //region Painting
    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g2d = (Graphics2D) graphics.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g2d.scale(zoom, zoom);
        int lw = (int) (getWidth() / zoom) + 1, lh = (int) (getHeight() / zoom) + 1;
        g2d.setColor(GRID);
        for (int x = 0; x < lw; x += 24) for (int y = 0; y < lh; y += 24) g2d.fillRect(x, y, 1, 1);

        paintGroups(g2d);
        for (Link link : project.links) {
            Point point = portPoint(link.from(), link.fromPort(), true);
            Point b = portPoint(link.to(), link.toPort(), false);
            if (point == null || b == null) continue;
            g2d.setColor(typeColor(Graph.outType(project, link.from(), link.fromPort(), 0)));
            g2d.setStroke(new BasicStroke(2f));
            g2d.draw(curve(point, b));
        }
        if (linkStart != null && linkEnd != null && mode == Mode.LINK) {
            Point point = portPoint(linkStart.node(), linkStart.port(), linkStart.output());
            if (point != null) {
                g2d.setColor(new Color(255, 255, 255, 170));
                g2d.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{6, 4}, 0));
                g2d.draw(linkStart.output() ? curve(point, linkEnd) : curve(linkEnd, point));
            }
        }
        if (marquee != null) {
            g2d.setColor(new Color(SELECT.getRed(), SELECT.getGreen(), SELECT.getBlue(), 40));
            g2d.fill(marquee);
            g2d.setColor(SELECT);
            g2d.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{4, 3}, 0));
            g2d.draw(marquee);
        }
        for (NodeInst nodeInst : nodesInPass()) paintNode(g2d, nodeInst);
        if (nodesInPass().size() <= emptyBelow) paintEmptyHint(g2d);
        g2d.dispose();
    }
    private void paintEmptyHint(Graphics2D g2d) {
        String[] lines = L.t(emptyKey).split("\\n");
        g2d.setFont(EditorKit.font(13f, false));
        FontMetrics fontMetrics = g2d.getFontMetrics();
        Rectangle vis = getVisibleRect();
        int y = vis.y + vis.height / 2 - lines.length * (fontMetrics.getHeight() / 2);
        int cx = vis.x + vis.width / 2;
        g2d.setColor(TEXT);
        for (String line : lines) {
            g2d.drawString(line, cx - fontMetrics.stringWidth(line) / 2, y);
            y += fontMetrics.getHeight() + 4;
        }
    }
    private void paintNode(Graphics2D g2d, NodeInst nodeInst) {
        NodeKind nodeKind = Nodes.find(nodeInst.kind);
        if (nodeKind == null) return;
        int w = NodeGeometry.width(nodeInst), h = NodeGeometry.height(nodeInst);
        RoundRectangle2D.Float box = new RoundRectangle2D.Float(nodeInst.x, nodeInst.y, w, h, 10, 10);
        g2d.setColor(NODE_BG);
        g2d.fill(box);
        g2d.setColor(categoryColor(nodeKind.categoryId()));
        g2d.fillRoundRect(nodeInst.x, nodeInst.y, w, NodeGeometry.HEADER, 10, 10);
        g2d.fillRect(nodeInst.x, nodeInst.y + NodeGeometry.HEADER - 8, w, 8);

        boolean chosen = selection.contains(nodeInst.id);
        Color border = new Color(70, 76, 90);
        float width = 1f;
        if (errorNodes.contains(nodeInst.id)) { border = new Color(230, 70, 70); width = 2.5f; }
        else if (warnNodes.contains(nodeInst.id)) { border = new Color(230, 170, 50); width = 2f; }
        if (chosen) { border = SELECT; width = 2.5f; }
        g2d.setStroke(new BasicStroke(width));
        g2d.setColor(border);
        g2d.draw(box);
        g2d.setStroke(new BasicStroke(1f));

        g2d.setFont(FontCache.get(Font.SANS_SERIF, Font.BOLD, 12));
        g2d.setColor(Color.WHITE);
        g2d.drawString(nodeKind.title(), nodeInst.x + NodeGeometry.PAD, nodeInst.y + 17);

        g2d.setFont(FontCache.get(Font.SANS_SERIF, Font.PLAIN, 11));
        for (int i = 0; i < nodeKind.inputs().size(); i++) {
            Port port = nodeKind.inputs().get(i);
            int cy = NodeGeometry.rowY(nodeInst, i);
            drawPort(g2d, new Point(nodeInst.x, cy), port.type(), project.incoming(nodeInst.id, port.name()) != null);
            g2d.setColor(TEXT);
            g2d.drawString(port.name(), nodeInst.x + NodeGeometry.PORT_SPACE, cy + 4);
            if (liveText != null) {
                Link link = project.incoming(nodeInst.id, port.name());
                String v = link == null ? null : liveText.apply(link.from());
                if (v != null) {
                    g2d.setColor(LIVE);
                    g2d.drawString(v, nodeInst.x + NodeGeometry.PORT_SPACE + g2d.getFontMetrics().stringWidth(port.name()) + 6, cy + 4);
                }
            }
        }
        for (int i = 0; i < nodeKind.outputs().size(); i++) {
            Port port = nodeKind.outputs().get(i);
            int cy = NodeGeometry.rowY(nodeInst, i);
            DataType resolved = Graph.outType(project, nodeInst.id, port.name(), 0);
            drawPort(g2d, new Point(nodeInst.x + w, cy), resolved == null ? port.type() : resolved, true);
            g2d.setColor(TEXT);
            int nameW = g2d.getFontMetrics().stringWidth(port.name());
            g2d.drawString(port.name(), nodeInst.x + w - NodeGeometry.PORT_SPACE - nameW, cy + 4);
            if (liveText != null) {
                String v = liveText.apply(nodeInst.id);
                if (v != null) {
                    g2d.setColor(LIVE);
                    g2d.drawString(v, nodeInst.x + w - NodeGeometry.PORT_SPACE - nameW - 6 - g2d.getFontMetrics().stringWidth(v), cy + 4);
                }
            }
        }

        for (int i = 0; i < nodeKind.params().size(); i++) {
            Rectangle rectangle = NodeGeometry.paramRect(nodeInst, i);
            boolean editing = editor != null && editorNode == nodeInst.id && nodeKind.params().get(i).key().equals(editorKey);
            if (editing) continue;
            g2d.setColor(DIM);
            g2d.drawString(NodeGeometry.paramText(nodeInst, nodeKind.params().get(i)), rectangle.x + 6, rectangle.y + rectangle.height - 4);
        }
        if (!(editor != null && editorNode == nodeInst.id && COMMENT_KEY.equals(editorKey))) {
            java.util.List<String> lines = NodeGeometry.commentLines(nodeInst);
            if (!lines.isEmpty()) {
                g2d.setFont(FontCache.get(Font.SANS_SERIF, Font.ITALIC, 11));
                g2d.setColor(COMMENT);
                int top = NodeGeometry.commentTop(nodeInst);
                for (int i = 0; i < lines.size(); i++)
                    g2d.drawString(lines.get(i), nodeInst.x + NodeGeometry.PAD, top + i * NodeGeometry.COMMENT_LINE + 11);
            }
        }
    }
    private void drawPort(Graphics2D g2d, Point point, DataType dataType, boolean filled) {
        Color color = typeColor(dataType);
        if (filled) {
            g2d.setColor(color);
            g2d.fillOval(point.x - PORT_R, point.y - PORT_R, PORT_R * 2, PORT_R * 2);
        }
        else {
            g2d.setColor(BG);
            g2d.fillOval(point.x - PORT_R, point.y - PORT_R, PORT_R * 2, PORT_R * 2);
            g2d.setColor(color);
            g2d.setStroke(new BasicStroke(2f));
            g2d.drawOval(point.x - PORT_R, point.y - PORT_R, PORT_R * 2, PORT_R * 2);
            g2d.setStroke(new BasicStroke(1f));
        }
    }
    public static Color typeColor(DataType dataType) {
        if (dataType == null) return new Color(150, 150, 150);
        return switch (dataType) {
            case FLOAT -> new Color(0x4FC3F7);
            case INT -> new Color(0x81C784);
            case BOOL -> new Color(0xFFB74D);
            case COLOR -> new Color(0xF06292);
            case ANY -> new Color(0xBDBDBD);
        };
    }
    public static Color categoryColor(String categoryId) {
        float hue = (Math.abs(categoryId.hashCode()) % 360) / 360f;
        return Color.getHSBColor(hue, 0.45f, 0.42f);
    }
    //endregion
}