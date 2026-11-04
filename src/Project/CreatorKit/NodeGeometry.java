package Project.CreatorKit;

import Project.EngineCreator.NodeGroup;

import java.awt.*;
import java.awt.image.BufferedImage;

public final class NodeGeometry {
    //region Variables
    //region Layout Constants
    public static final int PAD = 10, HEADER = 26, ROW = 18, PARAM = 15;
    public static final int GAP = 6, PORT_SPACE = 14, LABEL_GAP = 12, MIN_W = 150;
    public static final int GROUP_PAD = 14, GROUP_HEAD = 26;
    //endregion
    //region Comment Constants
    public static final int COMMENT_MAX = 60;
    public static final int COMMENT_LINES = 3;
    public static final int COMMENT_LINE = 14;
    private static final Font COMMENT_FONT = new Font(Font.SANS_SERIF, Font.ITALIC, 11);
    //endregion
    //region Text Measuring
    private static final Graphics2D g2d = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
    private static final Font TITLE_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 12);
    private static final Font BODY_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
    //endregion
    //endregion

    //region Constructors
    private NodeGeometry() {}
    //endregion

    //region Node Size
    public static int titleWidth(String text) {
        g2d.setFont(TITLE_FONT);
        return g2d.getFontMetrics().stringWidth(text);
    }
    public static int bodyWidth(String text) {
        g2d.setFont(BODY_FONT);
        return g2d.getFontMetrics().stringWidth(text);
    }
    public static int rows(NodeKind nodeKind) {
        return Math.max(1, Math.max(nodeKind.inputs().size(), nodeKind.outputs().size()));
    }
    public static String paramText(NodeInst nodeInst, Param prm) {
        String value = nodeInst.params.getOrDefault(prm.key(), "");
        if (prm.kind() == Param.Kind.CHOICE) value = prm.choiceLabel(value);
        return prm.label() + ": " + value;
    }
    public static int width(NodeInst nodeInst) {
        NodeKind nodeKind = Nodes.find(nodeInst.kind);
        if (nodeKind == null) return MIN_W;
        int left = 0, right = 0;
        for (Port port : nodeKind.inputs()) left = Math.max(left, bodyWidth(port.name()));
        for (Port port : nodeKind.outputs()) right = Math.max(right, bodyWidth(port.name()));
        int ports = PORT_SPACE + left + LABEL_GAP + right + PORT_SPACE;
        int title = titleWidth(nodeKind.title()) + 2 * PAD;
        int params = 0;
        for (Param prm : nodeKind.params()) params = Math.max(params, bodyWidth(paramText(nodeInst, prm)) + 2 * PAD);
        return Math.max(MIN_W, Math.max(ports, Math.max(title, params)));
    }
    public static int height(NodeInst nodeInst) {
        NodeKind nodeKind = Nodes.find(nodeInst.kind);
        if (nodeKind == null) return HEADER + GAP + ROW + GAP;
        int h = paramTop(nodeInst) - nodeInst.y + GAP;
        h += nodeKind.params().size() * PARAM;
        int lines = commentLines(nodeInst).size();
        if (lines > 0) h += lines * COMMENT_LINE + GAP;
        return h;
    }
    //endregion

    //region Ports And Params
    public static int portTop(NodeInst nodeInst) { return nodeInst.y + HEADER + GAP; }
    public static int rowY(NodeInst nodeInst, int i) { return portTop(nodeInst) + i * ROW + ROW / 2; }
    public static int paramTop(NodeInst nodeInst) {
        NodeKind nodeKind = Nodes.find(nodeInst.kind);
        int rows = nodeKind == null ? 1 : rows(nodeKind);
        return portTop(nodeInst) + rows * ROW + GAP;
    }
    public static Rectangle paramRect(NodeInst nodeInst, int i) { return new Rectangle(nodeInst.x + 4, paramTop(nodeInst) + i * PARAM, width(nodeInst) - 8, PARAM); }
    //endregion

    //region Comments
    public static java.util.List<String> commentLines(NodeInst nodeInst) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (nodeInst.comment == null || nodeInst.comment.isEmpty()) return out;
        g2d.setFont(COMMENT_FONT);
        FontMetrics fontMetrics = g2d.getFontMetrics();
        int maxW = Math.max(40, width(nodeInst) - 2 * PAD);
        StringBuilder line = new StringBuilder();
        for (String word : nodeInst.comment.trim().split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (fontMetrics.stringWidth(candidate) <= maxW) { line.setLength(0); line.append(candidate); continue; }
            if (!line.isEmpty()) out.add(line.toString());
            line.setLength(0);
            line.append(word);
            while (fontMetrics.stringWidth(line.toString()) > maxW && line.length() > 1) {
                String head = fitPrefix(fontMetrics, line.toString(), maxW);
                out.add(head);
                line.delete(0, head.length());
            }
        }
        if (!line.isEmpty()) out.add(line.toString());
        if (out.size() > COMMENT_LINES) {
            String last = String.join(" ", out.subList(COMMENT_LINES - 1, out.size()));
            while (out.size() > COMMENT_LINES) out.removeLast();
            out.set(COMMENT_LINES - 1, fitPrefix(fontMetrics, last, maxW - fontMetrics.stringWidth("…")) + "…");
        }
        return out;
    }
    public static int commentTop(NodeInst nodeInst) {
        NodeKind nodeKind = Nodes.find(nodeInst.kind);
        int params = nodeKind == null ? 0 : nodeKind.params().size();
        return paramTop(nodeInst) + GAP + params * PARAM;
    }
    public static Rectangle commentRect(NodeInst nodeInst) { return new Rectangle(nodeInst.x + PAD, commentTop(nodeInst), width(nodeInst) - 2 * PAD, COMMENT_LINE); }
    private static String fitPrefix(FontMetrics fontMetrics, String text, int maxW) {
        int end = text.length();
        while (end > 1 && fontMetrics.stringWidth(text.substring(0, end)) > maxW) end--;
        return text.substring(0, end);
    }
    //endregion

    //region Bounds
    public static Rectangle bounds(NodeInst nodeInst) { return new Rectangle(nodeInst.x, nodeInst.y, width(nodeInst), height(nodeInst)); }
    public static Rectangle groupBounds(CkProject ckProject, NodeGroup nodeGroup, int pass) {
        Rectangle rectangle = null;
        for (int id : nodeGroup.nodes) {
            NodeInst nodeInst = ckProject.node(id);
            if (nodeInst == null || nodeInst.pass != pass) continue;
            Rectangle b = bounds(nodeInst);
            rectangle = rectangle == null ? b : rectangle.union(b);
        }
        if (rectangle == null) return null;
        return new Rectangle(rectangle.x - GROUP_PAD, rectangle.y - GROUP_PAD - GROUP_HEAD, rectangle.width + 2 * GROUP_PAD, rectangle.height + 2 * GROUP_PAD + GROUP_HEAD);
    }
    //endregion
}