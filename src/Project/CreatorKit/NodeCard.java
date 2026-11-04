package Project.CreatorKit;
import Project.ThemeCreator.*;
import Project.StyleUI.*;

import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import static Project.Helpers.EditorKit.*;

public class NodeCard extends JComponent {
    //region Variables
    //region Layout
    /** Card size: two per row in the palette (the palette column is 252px wide). */
    static final int CARD_H = 92, CARD_GAP = 6, CARD_W = (252 - CARD_GAP) / 2;
    //endregion
    //region Card State
    private final NodeKind kind;
    private boolean hover;
    private java.util.function.Consumer<Point> onRightClick;
    //endregion
    //endregion

    //region Constructors
    public NodeCard(NodeKind kind, Runnable onClick) {
        this.kind = kind;
        setPreferredSize(new Dimension(CARD_W, CARD_H));
        setMinimumSize(new Dimension(CARD_W, CARD_H));
        setMaximumSize(new Dimension(CARD_W, CARD_H));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setToolTipText(kind.help());
        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent mouseEvent) { hover = true; repaint(); }
            @Override public void mouseExited(MouseEvent mouseEvent) { hover = false; repaint(); }
            @Override public void mouseClicked(MouseEvent mouseEvent) {
                if (!SwingUtilities.isRightMouseButton(mouseEvent)) onClick.run();
                else if (onRightClick != null) onRightClick.accept(mouseEvent.getPoint());
                else NodeHelp.show(NodeCard.this, kind);
            }
        });
    }
    //endregion

    //region Interaction
    public void setOnRightClick(java.util.function.Consumer<Point> action) { onRightClick = action; }
    //endregion

    //region Drawing
    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g2d = smooth(graphics);
        int w = getWidth(), h = getHeight();
        g2d.setColor(hover ? CARD_LINE : CARD);
        g2d.fillRoundRect(0, 0, w - 1, h - 1, 10, 10);
        g2d.setColor(NodeCanvas.categoryColor(kind.categoryId()));
        g2d.fillRoundRect(0, 0, w - 1, 6, 4, 4);
        g2d.fillRect(0, 3, w - 1, 3);
        int pad = 8, textW = w - 2 * pad;
        float titleSize = 11f;
        g2d.setFont(font(titleSize, true));
        while (titleSize > 7f && g2d.getFontMetrics().stringWidth(kind.title()) > textW) {
            titleSize -= 0.5f;
            g2d.setFont(font(titleSize, true));
        }
        g2d.setColor(TEXT);
        FontMetrics fontMetrics = g2d.getFontMetrics();
        g2d.drawString(ellipsize(kind.title(), fontMetrics, textW), pad, 24);
        g2d.setFont(font(10f, false));
        g2d.setColor(MUTED);
        fontMetrics = g2d.getFontMetrics();
        int y = 40;
        List<String> lines = wrap(summary(), fontMetrics, textW);
        for (String line : lines) { g2d.drawString(line, pad, y); y += 13; }
        g2d.dispose();
    }
    private String summary() {
        String h = kind.help().trim();
        int dot = h.indexOf('.');
        if (dot > 0) h = h.substring(0, dot + 1);
        return h.length() > 70 ? h.substring(0, 69) + "…" : h;
    }
    private static List<String> wrap(String text, FontMetrics fontMetrics, int maxW) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (fontMetrics.stringWidth(next) <= maxW) { line.setLength(0); line.append(next); continue; }
            if (!line.isEmpty()) out.add(line.toString());
            line.setLength(0);
            line.append(word);
        }
        if (!line.isEmpty()) out.add(line.toString());
        if (out.size() > 3) out = new ArrayList<>(out.subList(0, 3));
        if (!out.isEmpty()) {
            int last = out.size() - 1;
            out.set(last, ellipsize(out.get(last), fontMetrics, maxW));
        }
        return out;
    }
    //endregion
}