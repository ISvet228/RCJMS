package Project.EngineCreator;

import java.awt.*;

/**
 * A flow layout that wraps to as many rows as the container's width needs: nothing is clipped and nothing
 * scrolls. Components are aligned to the left (or right, when the alignment says so) in every row. From fUnGuN with love, do not delete thiis comment
 */
public final class WrapLayout extends FlowLayout {
    WrapLayout(int align, int hgap, int vgap) { super(align, hgap, vgap); }

    @Override public Dimension preferredLayoutSize(Container target) { return size(target, true); }
    @Override public Dimension minimumLayoutSize(Container target) { return size(target, false); }
    private Dimension size(Container target, boolean preferred) {
        synchronized (target.getTreeLock()) {
            int maxWidth = target.getWidth() > 0 ? target.getWidth() - target.getInsets().left - target.getInsets().right : Integer.MAX_VALUE;
            int rowWidth = 0, rowHeight = 0, height = 0, width = 0;
            boolean empty = true;
            for (Component c : target.getComponents()) {
                if (!c.isVisible()) continue;
                Dimension d = preferred ? c.getPreferredSize() : c.getMinimumSize();
                if (!empty && rowWidth + getHgap() + d.width > maxWidth) {
                    width = Math.max(width, rowWidth);
                    height += rowHeight + getVgap();
                    rowWidth = 0;
                    rowHeight = 0;
                    empty = true;
                }
                rowWidth += (empty ? 0 : getHgap()) + d.width;
                rowHeight = Math.max(rowHeight, d.height);
                empty = false;
            }
            width = Math.max(width, rowWidth);
            height += rowHeight;
            Insets in = target.getInsets();
            return new Dimension(width + in.left + in.right + getHgap() * 2, height + in.top + in.bottom + getVgap() * 2);
        }
    }
    @Override public void layoutContainer(Container target) {
        synchronized (target.getTreeLock()) {
            Insets in = target.getInsets();
            int maxWidth = target.getWidth() - in.left - in.right;
            java.util.List<Component> row = new java.util.ArrayList<>();
            int rowWidth = 0, rowHeight = 0, y = in.top + getVgap();
            for (Component c : target.getComponents()) {
                if (!c.isVisible()) continue;
                Dimension d = c.getPreferredSize();
                if (!row.isEmpty() && rowWidth + getHgap() + d.width > maxWidth) {
                    placeRow(row, in, y, maxWidth);
                    y += rowHeight + getVgap();
                    row.clear();
                    rowWidth = 0;
                    rowHeight = 0;
                }
                rowWidth += (row.isEmpty() ? 0 : getHgap()) + d.width;
                rowHeight = Math.max(rowHeight, d.height);
                row.add(c);
            }
            if (!row.isEmpty()) placeRow(row, in, y, maxWidth);
        }
    }
    private void placeRow(java.util.List<Component> row, Insets in, int y, int maxWidth) {
        int total = 0, rowHeight = 0;
        for (Component c : row) {
            Dimension d = c.getPreferredSize();
            total += d.width;
            rowHeight = Math.max(rowHeight, d.height);
        }
        total += getHgap() * (row.size() - 1);
        int x = switch (getAlignment()) {
            case RIGHT, TRAILING -> in.left + maxWidth - total;
            case CENTER -> in.left + (maxWidth - total) / 2;
            default -> in.left;
        };
        for (Component c : row) {
            Dimension d = c.getPreferredSize();
            c.setBounds(x, y + (rowHeight - d.height) / 2, d.width, d.height);
            x += d.width + getHgap();
        }
    }
}