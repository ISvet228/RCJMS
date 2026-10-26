package Project.EngineCreator;

import java.awt.*;
import java.awt.image.BufferedImage;

public final class NodeGeometry {
    static final int PAD = 10, HEADER = 26, ROW = 18, PARAM = 15;
    static final int GAP = 6, PORT_SPACE = 14, LABEL_GAP = 12, MIN_W = 150;

    private static final Graphics2D MEASURE = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
    private static final Font TITLE_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 12);
    private static final Font BODY_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 11);

    static final int COMMENT_MAX = 60;
    static final int COMMENT_LINES = 3;
    static final int COMMENT_LINE = 14;
    private static final Font COMMENT_FONT = new Font(Font.SANS_SERIF, Font.ITALIC, 11);

    private NodeGeometry() {}

    static int titleWidth(String text) {
        MEASURE.setFont(TITLE_FONT);
        return MEASURE.getFontMetrics().stringWidth(text);
    }
    static int bodyWidth(String text) {
        MEASURE.setFont(BODY_FONT);
        return MEASURE.getFontMetrics().stringWidth(text);
    }
    static int rows(NodeKind k) {
        return Math.max(1, Math.max(k.inputs().size(), k.outputs().size()));
    }
    static String paramText(NodeInst n, Param prm) {
        String value = n.params.getOrDefault(prm.key(), "");
        if (prm.kind() == Param.Kind.CHOICE) value = prm.choiceLabel(value);
        return prm.label() + ": " + value;
    }
    static int width(NodeInst n) {
        NodeKind k = Nodes.find(n.kind);
        if (k == null) return MIN_W;
        int left = 0, right = 0;
        for (Port p : k.inputs()) left = Math.max(left, bodyWidth(p.name()));
        for (Port p : k.outputs()) right = Math.max(right, bodyWidth(p.name()));
        int ports = PORT_SPACE + left + LABEL_GAP + right + PORT_SPACE;
        int title = titleWidth(k.title()) + 2 * PAD;
        int params = 0;
        for (Param prm : k.params()) params = Math.max(params, bodyWidth(paramText(n, prm)) + 2 * PAD);
        return Math.max(MIN_W, Math.max(ports, Math.max(title, params)));
    }
    static int height(NodeInst n) {
        NodeKind k = Nodes.find(n.kind);
        if (k == null) return HEADER + GAP + ROW + GAP;
        int h = paramTop(n) - n.y + GAP;
        h += k.params().size() * PARAM;
        int lines = commentLines(n).size();
        if (lines > 0) h += lines * COMMENT_LINE + GAP;
        return h;
    }
    static java.util.List<String> commentLines(NodeInst n) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (n.comment == null || n.comment.isEmpty()) return out;
        MEASURE.setFont(COMMENT_FONT);
        FontMetrics fm = MEASURE.getFontMetrics();
        int maxW = Math.max(40, width(n) - 2 * PAD);
        StringBuilder line = new StringBuilder();
        for (String word : n.comment.trim().split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (fm.stringWidth(candidate) <= maxW) { line.setLength(0); line.append(candidate); continue; }
            if (!line.isEmpty()) out.add(line.toString());
            line.setLength(0);
            line.append(word);
            while (fm.stringWidth(line.toString()) > maxW && line.length() > 1) {
                String head = fitPrefix(fm, line.toString(), maxW);
                out.add(head);
                line.delete(0, head.length());
            }
        }
        if (!line.isEmpty()) out.add(line.toString());
        if (out.size() > COMMENT_LINES) {
            String last = String.join(" ", out.subList(COMMENT_LINES - 1, out.size()));
            while (out.size() > COMMENT_LINES) out.removeLast();
            out.set(COMMENT_LINES - 1, fitPrefix(fm, last, maxW - fm.stringWidth("…")) + "…");
        }
        return out;
    }
    private static String fitPrefix(FontMetrics fm, String text, int maxW) {
        int end = text.length();
        while (end > 1 && fm.stringWidth(text.substring(0, end)) > maxW) end--;
        return text.substring(0, end);
    }
    static int commentTop(NodeInst n) {
        NodeKind k = Nodes.find(n.kind);
        int params = k == null ? 0 : k.params().size();
        return paramTop(n) + GAP + params * PARAM;
    }
    static Rectangle commentRect(NodeInst n) { return new Rectangle(n.x + PAD, commentTop(n), width(n) - 2 * PAD, COMMENT_LINE); }
    static int portTop(NodeInst n) { return n.y + HEADER + GAP; }
    static int rowY(NodeInst n, int i) { return portTop(n) + i * ROW + ROW / 2; }
    static int paramTop(NodeInst n) {
        NodeKind k = Nodes.find(n.kind);
        int rows = k == null ? 1 : rows(k);
        return portTop(n) + rows * ROW + GAP;
    }
    static Rectangle paramRect(NodeInst n, int i) { return new Rectangle(n.x + 4, paramTop(n) + i * PARAM, width(n) - 8, PARAM); }
    static Rectangle bounds(NodeInst n) {
        return new Rectangle(n.x, n.y, width(n), height(n));
    }
}