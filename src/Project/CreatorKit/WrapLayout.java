package Project.CreatorKit;
import Project.EngineCreator.*;
import Project.ThemeCreator.*;

import java.awt.*;

public final class WrapLayout extends FlowLayout {
    //region Constructors
    public WrapLayout(int align, int hgap, int vgap) { super(align, hgap, vgap); }
    //endregion

    //region Layout
    @Override public Dimension preferredLayoutSize(Container target) { return size(target, true); }
    @Override public Dimension minimumLayoutSize(Container target) { return size(target, false); }
    @Override public void layoutContainer(Container target) {
        synchronized (target.getTreeLock()) {
            Insets insets = target.getInsets();
            int maxWidth = target.getWidth() - insets.left - insets.right;
            java.util.List<Component> row = new java.util.ArrayList<>();
            int rowWidth = 0, rowHeight = 0, y = insets.top + getVgap();
            for (Component component : target.getComponents()) {
                if (!component.isVisible()) continue;
                Dimension dimension = component.getPreferredSize();
                if (!row.isEmpty() && rowWidth + getHgap() + dimension.width > maxWidth) {
                    placeRow(row, insets, y, maxWidth);
                    y += rowHeight + getVgap();
                    row.clear();
                    rowWidth = 0;
                    rowHeight = 0;
                }
                rowWidth += (row.isEmpty() ? 0 : getHgap()) + dimension.width;
                rowHeight = Math.max(rowHeight, dimension.height);
                row.add(component);
            }
            if (!row.isEmpty()) placeRow(row, insets, y, maxWidth);
        }
    }
    private Dimension size(Container target, boolean preferred) {
        synchronized (target.getTreeLock()) {
            int maxWidth = target.getWidth() > 0 ? target.getWidth() - target.getInsets().left - target.getInsets().right : Integer.MAX_VALUE;
            int rowWidth = 0, rowHeight = 0, height = 0, width = 0;
            boolean empty = true;
            for (Component component : target.getComponents()) {
                if (!component.isVisible()) continue;
                Dimension dimension = preferred ? component.getPreferredSize() : component.getMinimumSize();
                if (!empty && rowWidth + getHgap() + dimension.width > maxWidth) {
                    width = Math.max(width, rowWidth);
                    height += rowHeight + getVgap();
                    rowWidth = 0;
                    rowHeight = 0;
                    empty = true;
                }
                rowWidth += (empty ? 0 : getHgap()) + dimension.width;
                rowHeight = Math.max(rowHeight, dimension.height);
                empty = false;
            }
            width = Math.max(width, rowWidth);
            height += rowHeight;
            Insets insets = target.getInsets();
            return new Dimension(width + insets.left + insets.right + getHgap() * 2, height + insets.top + insets.bottom + getVgap() * 2);
        }
    }
    private void placeRow(java.util.List<Component> row, Insets insets, int y, int maxWidth) {
        int total = 0, rowHeight = 0;
        for (Component component : row) {
            Dimension dimension = component.getPreferredSize();
            total += dimension.width;
            rowHeight = Math.max(rowHeight, dimension.height);
        }
        total += getHgap() * (row.size() - 1);
        int x = switch (getAlignment()) {
            case RIGHT, TRAILING -> insets.left + maxWidth - total;
            case CENTER -> insets.left + (maxWidth - total) / 2;
            default -> insets.left;
        };
        for (Component component : row) {
            Dimension dimension = component.getPreferredSize();
            component.setBounds(x, y + (rowHeight - dimension.height) / 2, dimension.width, dimension.height);
            x += dimension.width + getHgap();
        }
    }
    //endregion
}