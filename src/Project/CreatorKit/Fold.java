package Project.CreatorKit;
import Project.ThemeCreator.*;
import Project.StyleUI.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import java.util.*;
import static Project.Helpers.EditorKit.*;

public class Fold extends Column {
    //region Variables
    public final Column body = new Column();
    private final String title;
    private final boolean card;
    private boolean open;
    //endregion

    //region Constructors
    public Fold(String key, boolean card) { this(key, card, false); }
    public Fold(String key, boolean card, boolean startOpen) {
        this.card = card;
        this.title = card ? L(key).toUpperCase(Locale.ROOT) : L(key);
        put(body, 0);
        addMouseListener(new MouseAdapter() { @Override public void mouseClicked(MouseEvent mouseEvent) { if (!open || mouseEvent.getY() < headHeight()) setOpen(!open); } });
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent mouseEvent) {
                setCursor(Cursor.getPredefinedCursor(mouseEvent.getY() < headHeight() ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            }
        });
        setOpen(startOpen);
    }
    //endregion

    //region Expand And Collapse
    public void setOpen(boolean value) {
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
    private int headHeight() { return card ? 23 : 20; }
    //endregion

    //region Rendering
    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g2d = smooth(graphics);
        int w = getWidth(), h = getHeight();
        if (card) {
            g2d.setColor(CARD);
            g2d.fillRoundRect(0, 0, w - 1, h - 1, 12, 12);
            g2d.setColor(CARD_LINE);
            g2d.drawRoundRect(0, 0, w - 1, h - 1, 12, 12);
        }
        g2d.setFont(font(10.5f, true));
        g2d.setColor(MUTED);
        int pad = card ? 10 : 2;
        int arrowX = w - (card ? 22 : 18);
        g2d.drawString(ellipsize(title, g2d.getFontMetrics(), arrowX - pad - 6), pad, card ? 16 : 14);
        (open ? Glyph.UP : Glyph.DOWN).paint(g2d, arrowX, card ? 4 : 2, 14, MUTED);
        g2d.dispose();
    }
    //endregion
}