package Project.CreatorKit;

import Project.Helpers.EditorKit;

import javax.swing.*;
import java.awt.*;
import java.util.Locale;

public final class NodeHelp {
    //region Variables
    private static final Color DARK = new Color(14, 14, 17), BAR = new Color(22, 22, 26), HOVER_CLOSE = new Color(170, 60, 60);
    //endregion

    //region Constructors
    private NodeHelp() {}
    //endregion

    //region Help Dialog
    public static void show(Component component, NodeKind nodeKind) {
        if (nodeKind == null) return;
        Window owner = SwingUtilities.getWindowAncestor(component);
        JDialog dialog = new JDialog(owner, nodeKind.title(), Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setUndecorated(true);

        javax.swing.text.html.HTMLEditorKit kit = new javax.swing.text.html.HTMLEditorKit();
        String light = hex(EditorKit.TEXT);
        kit.getStyleSheet().addRule("body, p, li, ul, h1, h2, h3, h4, b, span { color: " + light + "; }");
        JEditorPane pane = new JEditorPane();
        pane.setEditorKit(kit);
        pane.setContentType("text/html");
        pane.setText(html(nodeKind));
        pane.setEditable(false);
        pane.setBackground(DARK);
        pane.setCaretPosition(0);
        JScrollPane scroll = new JScrollPane(pane);
        scroll.setBorder(BorderFactory.createEmptyBorder(6, 10, 10, 10));
        scroll.getViewport().setBackground(DARK);
        scroll.setBackground(DARK);
        scroll.getVerticalScrollBar().setUI(new DarkScrollBarUI());
        scroll.getHorizontalScrollBar().setUI(new DarkScrollBarUI());

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(DARK);
        root.setBorder(BorderFactory.createLineBorder(new Color(48, 48, 56)));
        root.add(titleBar(dialog, nodeKind.title()), BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        dialog.setContentPane(root);
        dialog.getRootPane().registerKeyboardAction(_ -> dialog.dispose(), KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        dialog.setSize(440, 480);
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }
    private static JPanel titleBar(JDialog dialog, String title) {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(BAR);
        bar.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 6));
        JLabel label = new JLabel(title);
        label.setForeground(EditorKit.TEXT);
        label.setFont(EditorKit.font(13f, true));
        JButton close = new JButton("\u00d7");
        close.setFont(EditorKit.font(16f, false));
        close.setForeground(EditorKit.TEXT);
        close.setBackground(BAR);
        close.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        close.setFocusPainted(false);
        close.setContentAreaFilled(false);
        close.setOpaque(true);
        close.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        close.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseEntered(java.awt.event.MouseEvent mouseEvent) { close.setBackground(HOVER_CLOSE); }
            @Override public void mouseExited(java.awt.event.MouseEvent mouseEvent) { close.setBackground(BAR); }
        });
        close.addActionListener(actionEvent -> dialog.dispose());
        bar.add(label, BorderLayout.CENTER);
        bar.add(close, BorderLayout.EAST);

        final Point[] grab = {null};
        bar.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mousePressed(java.awt.event.MouseEvent mouseEvent) { grab[0] = mouseEvent.getPoint(); }
        });
        bar.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override public void mouseDragged(java.awt.event.MouseEvent mouseEvent) {
                Point screen = mouseEvent.getLocationOnScreen();
                if (grab[0] != null) dialog.setLocation(screen.x - grab[0].x, screen.y - grab[0].y);
            }
        });
        return bar;
    }
    //endregion

    //region Help Content
    static String html(NodeKind nodeKind) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("<html><body style='font-family:SansSerif;font-size:12px;color:").append(hex(EditorKit.TEXT)).append(";'>");
        stringBuilder.append("<h2 style='margin:0 0 2px 0;'>").append(esc(nodeKind.title())).append("</h2>");
        stringBuilder.append("<p style='color:").append(hex(EditorKit.MUTED)).append(";margin-top:0;'>").append(esc(nodeKind.category())).append("</p>");
        stringBuilder.append("<p>").append(esc(nodeKind.help())).append("</p>");
        stringBuilder.append("<p style='color:").append(hex(EditorKit.MUTED)).append(";'>").append(esc(L.t("ec.help.howto"))).append("</p>");

        String use = L.t("ec.n." + nodeKind.id() + ".use");
        if (!use.equals("ec.n." + nodeKind.id() + ".use")) {
            stringBuilder.append("<h3>").append(esc(L.t("ec.help.usage"))).append("</h3>");
            stringBuilder.append("<p>").append(esc(use)).append("</p>");
        }
        stringBuilder.append("<h3>").append(esc(L.t("ec.help.inputs"))).append("</h3>");
        if (nodeKind.inputs().isEmpty()) stringBuilder.append("<p>").append(esc(L.t("ec.help.none"))).append("</p>");
        else {
            stringBuilder.append("<ul>");
            for (Port port : nodeKind.inputs()) stringBuilder.append("<li><b>").append(esc(port.name())).append("</b> — ").append(esc(port.type().label())).append("</li>");
            stringBuilder.append("</ul>");
        }

        stringBuilder.append("<h3>").append(esc(L.t("ec.help.outputs"))).append("</h3>");
        if (nodeKind.outputs().isEmpty()) stringBuilder.append("<p>").append(esc(L.t("ec.help.none"))).append("</p>");
        else {
            stringBuilder.append("<ul>");
            for (Port port : nodeKind.outputs()) stringBuilder.append("<li><b>").append(esc(port.name())).append("</b> — ").append(esc(port.type().label())).append("</li>");
            stringBuilder.append("</ul>");
        }

        stringBuilder.append("<h3>").append(esc(L.t("ec.help.params"))).append("</h3>");
        if (nodeKind.params().isEmpty()) stringBuilder.append("<p>").append(esc(L.t("ec.help.none"))).append("</p>");
        else {
            stringBuilder.append("<ul>");
            for (Param param : nodeKind.params()) stringBuilder.append("<li><b>").append(esc(param.label())).append("</b>: ").append(esc(describe(param))).append("</li>");
            stringBuilder.append("</ul>");
        }
        stringBuilder.append("</body></html>");
        return stringBuilder.toString();
    }
    static String describe(Param param) {
        return switch (param.kind()) {
            case FLOAT, INT -> param.min() > -1e5 && param.max() < 1e5 ? L.f("ec.help.range", num(param.min()), num(param.max()), param.def()) : L.f("ec.help.any", param.def());
            case CHOICE -> {
                StringBuilder names = new StringBuilder();
                for (String c : param.choices()) {
                    if (!names.isEmpty()) names.append(", ");
                    names.append(param.choiceLabel(c));
                }
                yield L.f("ec.help.choice", names.toString(), param.choiceLabel(param.def()));
            }
            case COLOR -> L.f("ec.help.color", param.def());
            case TEXT -> L.f("ec.help.text", param.def());
        };
    }
    //endregion

    //region Text Formatting
    private static String num(double v) { return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.3g", v); }
    private static String hex(Color color) { return String.format(Locale.ROOT, "#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue()); }
    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
    //endregion

    //region Dark Scroll Bar
    private static final class DarkScrollBarUI extends javax.swing.plaf.basic.BasicScrollBarUI {
        @Override protected void configureScrollBarColors() {
            thumbColor = new Color(70, 70, 80);
            trackColor = DARK;
        }
        @Override protected JButton createDecreaseButton(int orientation) { return zeroButton(); }
        @Override protected JButton createIncreaseButton(int orientation) { return zeroButton(); }
        private static JButton zeroButton() {
            JButton button = new JButton();
            button.setPreferredSize(new Dimension(0, 0));
            return button;
        }
    }
    //endregion
}