package Project.CreatorKit;

import Project.Helpers.EditorKit.*;
import Project.StyleUI.*;

import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.function.*;

import static Project.Helpers.EditorKit.*;

public final class NodePalette extends Column {
    //region Variables
    //region Layout
    private static final int INNER_W = 252, CARD_GAP = 6;
    //endregion
    //region Palette State
    private final List<String> categories;
    private final Supplier<List<NodeKind>> kinds;
    private BiConsumer<NodeKind, NodeCard> decorate = (nodeKind, nodeCard) -> {};
    private final Consumer<String> onPick;
    private final Column list = new Column();
    private String category = "all", query = "";
    //endregion
    //endregion

    //region Constructors
    public NodePalette(Style style, List<String> categories, Supplier<List<NodeKind>> kinds, Consumer<String> onPick) {
        this.categories = categories;
        this.kinds = kinds;
        this.onPick = onPick;
        setOpaque(false);

        StyledTextField search = new StyledTextField(style, "");
        search.setPreferredSize(new Dimension(INNER_W, 28));
        search.setMaximumSize(new Dimension(INNER_W, 28));
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent documentEvent) { changed(); }
            @Override public void removeUpdate(DocumentEvent documentEvent) { changed(); }
            @Override public void changedUpdate(DocumentEvent documentEvent) { changed(); }
            private void changed() {
                query = search.getText().trim().toLowerCase(Locale.ROOT);
                refresh();
            }
        });
        put(search, 0);

        JPanel chips = new JPanel(new WrapLayout(FlowLayout.LEFT, 4, 4));
        chips.setOpaque(false);
        chips.addComponentListener(new ComponentAdapter() { @Override public void componentResized(ComponentEvent componentEvent) { chips.revalidate(); } });
        List<String> filters = new ArrayList<>();
        filters.add("all");
        filters.addAll(categories);
        for (String cat : filters) {
            String label = L.t(cat.equals("all") ? "ec.ui.all" : "ec.c." + cat);
            StyledButton chip = new StyledButton(style, label);
            int chipW = chip.getFontMetrics(font(11f, false)).stringWidth(label) + 28;
            chip.setPreferredSize(new Dimension(chipW, 26));
            chip.setMinimumSize(new Dimension(chipW, 26));
            chip.addActionListener(actionEvent -> {
                category = cat;
                refresh();
            });
            chips.add(chip);
        }
        put(chips, 6);
        put(list, 6);
        refresh();
    }
    //endregion

    //region Refresh
    public void setCardDecorator(BiConsumer<NodeKind, NodeCard> decorate) {
        this.decorate = decorate;
        refresh();
    }
    public void refresh() {
        list.removeAll();
        List<NodeKind> all = kinds.get();
        boolean flat = !"all".equals(category) || !query.isEmpty();
        if (!flat) {
            for (String cat : categories) {
                if (all.stream().noneMatch(nodeKind -> nodeKind.categoryId().equals(cat))) continue;
                Fold group = new Fold("ec.c." + cat, false);
                JPanel grid = cardGrid();
                for (NodeKind nodeKind : all) if (nodeKind.categoryId().equals(cat)) grid.add(card(nodeKind));
                group.body.put(grid, 0);
                list.put(group, 8);
            }
        } else {
            int shown = 0;
            JPanel grid = cardGrid();
            for (NodeKind nodeKind : all) {
                if (!"all".equals(category) && !nodeKind.categoryId().equals(category)) continue;
                if (!query.isEmpty() && !nodeKind.title().toLowerCase(Locale.ROOT).contains(query)) continue;
                grid.add(card(nodeKind));
                shown++;
            }
            list.put(grid, 0);
            if (shown == 0) list.put(note(INNER_W, "ec.ui.noNodes"), 0);
        }
        list.revalidate();
        list.repaint();
        Container scroll = SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
        if (scroll != null) scroll.revalidate();
    }
    //endregion

    //region Cards
    private NodeCard card(NodeKind nodeKind) {
        String id = nodeKind.id();
        NodeCard card = new NodeCard(nodeKind, () -> onPick.accept(id));
        decorate.accept(nodeKind, card);
        return card;
    }
    private static JPanel cardGrid() {
        JPanel grid = new JPanel(new GridLayout(0, 2, CARD_GAP, CARD_GAP));
        grid.setOpaque(false);
        return grid;
    }
    private static Note note(int width, String key) {
        Note note = new Note(width, 11f, MUTED);
        note.set(null, MUTED, L.t(key));
        return note;
    }
    //endregion
}