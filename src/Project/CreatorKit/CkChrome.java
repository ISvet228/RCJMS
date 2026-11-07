package Project.CreatorKit;

import Project.Helpers.EditorKit.*;
import Project.StyleUI.*;
import static Project.Helpers.EditorKit.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.*;
import java.util.List;

public final class CkChrome {
    //region Constructors
    private CkChrome() {}
    //endregion

    //region Icons And Menus
    public static ToolButton icon(Style style, Glyph glyph, String tipKey, String shortcut, Runnable action) {
        ToolButton button = new ToolButton(style, ToolButton.Layout.ICON, glyph, null).tip(tipKey, shortcut).size(36, 36);
        button.onClick(action);
        return button;
    }
    public static JMenuItem menuItem(String text, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        item.setBackground(CARD);
        item.setForeground(TEXT);
        item.setFont(font(12f, false));
        item.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        item.addActionListener(_ -> action.run());
        return item;
    }
    //endregion

    //region Sidebar And Labels
    public static JComponent sidebar(Style style, Column column, int width) {
        ScrollBody holder = new ScrollBody();
        holder.add(column, BorderLayout.NORTH);
        StyledScrollPane scroll = new StyledScrollPane(holder, StyledScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, StyledScrollPane.HORIZONTAL_SCROLLBAR_NEVER, style);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setPreferredSize(new Dimension(width, 100));
        return scroll;
    }
    public static Text fieldLabel(String key) {
        Text text = new Text(null, 11f, false, MUTED, 0);
        text.setRaw(L.t(key) + ":");
        return text;
    }
    public record Labelled(ToolButton button, String text, int fullWidth) { }
    //endregion

    //region Top Bar
    public static final class TopBar extends JPanel {
        private static final int LABELS_MIN_WIDTH = 1100, TITLE_MIN_WIDTH = 880, ICON_WIDTH = 36, BUTTON_HEIGHT = 36;
        private final List<Labelled> labelled;
        private final List<JComponent> titleSlots = new ArrayList<>();
        private final Map<JComponent, JComponent> slotOf = new HashMap<>();
        private final List<JPanel> rows = new ArrayList<>();
        private Boolean labelsShown, titleShown;

        public TopBar(List<JComponent> left, List<JComponent> right, List<Labelled> labelled, List<JComponent> titleParts) {
            super(new BorderLayout());
            this.labelled = labelled;
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
            add(row(left), BorderLayout.WEST);
            add(row(right), BorderLayout.EAST);
            for (JComponent part : titleParts) titleSlots.add(slotOf.get(part));
        }

        @Override public void doLayout() {
            fit(getWidth());
            super.doLayout();
        }
        private JPanel row(List<JComponent> items) {
            JPanel row = new JPanel();
            row.setOpaque(false);
            row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
            rows.add(row);
            for (JComponent item : items) {
                JPanel slot = new JPanel(new BorderLayout());
                slot.setOpaque(false);
                slot.setBorder(new EmptyBorder(0, 4, 0, 4));
                slot.add(item);
                slotOf.put(item, slot);
                row.add(slot);
            }
            return row;
        }
        private void fit(int width) {
            if (width <= 0) return;
            boolean labels = width >= LABELS_MIN_WIDTH, title = width >= TITLE_MIN_WIDTH;
            if (Objects.equals(labels, labelsShown) && Objects.equals(title, titleShown)) return;
            labelsShown = labels;
            titleShown = title;
            for (Labelled entry : labelled) {
                entry.button().setLabel(labels ? entry.text() : "");
                entry.button().setPreferredSize(new Dimension(labels ? entry.fullWidth() : ICON_WIDTH, BUTTON_HEIGHT));
            }
            for (JComponent slot : titleSlots) slot.setVisible(title);
            for (JPanel row : rows) row.invalidate();
            revalidate();
            repaint();
        }
    }
    //endregion
}