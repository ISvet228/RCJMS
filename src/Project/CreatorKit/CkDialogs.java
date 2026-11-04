package Project.CreatorKit;

import Project.Helpers.EditorKit;
import Project.Helpers.EditorKit.ToolButton;
import Project.StyleUI.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.*;

public final class CkDialogs {
    //region Constructors
    private CkDialogs() {}
    //endregion

    //region Dialog Forms
    public static String[] form(Component parent, Style style, String title, String[] labels, String[] values, boolean[] multiline) {
        Window owner = SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
        String[] result = new String[labels.length];
        JTextComponent[] inputs = new JTextComponent[labels.length];
        boolean[] submitted = {false};

        JPanel fields = new JPanel();
        fields.setOpaque(false);
        fields.setLayout(new BoxLayout(fields, BoxLayout.Y_AXIS));
        for (int i = 0; i < labels.length; i++) {
            JLabel label = new JLabel(labels[i]);
            label.setForeground(EditorKit.MUTED);
            label.setFont(EditorKit.font(12f, false));
            label.setAlignmentX(Component.LEFT_ALIGNMENT);
            fields.add(label);
            fields.add(Box.createVerticalStrut(4));
            if (multiline[i]) {
                JTextArea area = new JTextArea(values[i], 2, 36);
                area.setLineWrap(true);
                area.setWrapStyleWord(true);
                styleInput(area);
                JScrollPane scroll = new JScrollPane(area);
                scroll.setBorder(BorderFactory.createLineBorder(EditorKit.CARD_LINE));
                scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
                scroll.setPreferredSize(new Dimension(440, 60));
                scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));
                fields.add(scroll);
                inputs[i] = area;
            }
            else {
                StyledTextField field = new StyledTextField(style, values[i]);
                field.setFont(EditorKit.font(16f, false));
                field.setAlignmentX(Component.LEFT_ALIGNMENT);
                field.setPreferredSize(new Dimension(440, 46));
                field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
                field.addActionListener(_ -> submit(dialog, inputs, result, submitted));
                fields.add(field);
                inputs[i] = field;
            }
            fields.add(Box.createVerticalStrut(12));
        }

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        ToolButton cancel = new ToolButton(style, ToolButton.Layout.TEXT, null, "cancel").size(110, 34);
        cancel.onClick(dialog::dispose);
        ToolButton toolButton = new ToolButton(style, ToolButton.Layout.TEXT, null, "common.ok").accent(new Color(52, 120, 246)).size(110, 34);
        toolButton.onClick(() -> submit(dialog, inputs, result, submitted));
        buttons.add(cancel);
        buttons.add(toolButton);

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBackground(EditorKit.BG);
        content.setBorder(new EmptyBorder(18, 20, 16, 20));
        content.add(fields, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        dialog.getRootPane().registerKeyboardAction(_ -> dialog.dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.setResizable(false);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        if (labels.length > 0) SwingUtilities.invokeLater(() -> inputs[0].requestFocusInWindow());
        dialog.setVisible(true);
        return submitted[0] ? result : null;
    }
    private static void submit(JDialog dialog, JTextComponent[] inputs, String[] result, boolean[] submitted) {
        for (int i = 0; i < inputs.length; i++) result[i] = inputs[i].getText();
        submitted[0] = true;
        dialog.dispose();
    }
    private static void styleInput(JTextComponent area) {
        area.setBackground(EditorKit.CARD);
        area.setForeground(EditorKit.TEXT);
        area.setCaretColor(EditorKit.TEXT);
        area.setSelectionColor(EditorKit.alpha(EditorKit.HILITE, 120));
        area.setFont(EditorKit.font(13f, false));
        area.setBorder(new EmptyBorder(6, 8, 6, 8));
    }
    public static Icon swatch(Color color) {
        return new Icon() {
            @Override public void paintIcon(Component comp, Graphics graphics, int x, int y) {
                graphics.setColor(color);
                graphics.fillRoundRect(x, y + 2, 14, 10, 4, 4);
                graphics.setColor(EditorKit.CARD_LINE);
                graphics.drawRoundRect(x, y + 2, 13, 9, 4, 4);
            }
            @Override public int getIconWidth() { return 14; }
            @Override public int getIconHeight() { return 14; }
        };
    }
    //endregion

    //region Text Prompts
    public static String text(Component parent, Style style, String title, String label, String value) {
        String[] r = form(parent, style, title, new String[]{label}, new String[]{value}, new boolean[]{false});
        if (r == null) return null;
        return r[0].isBlank() ? null : r[0].strip();
    }
    public static String[] customTexts(Component parent, Style style, String dialogTitle, String titleValue, String descValue) {
        String[] r = form(parent, style, dialogTitle, new String[]{L.t("ec.g.customPrompt"), L.t("ec.g.descPrompt")},
                new String[]{titleValue, descValue}, new boolean[]{false, true});
        if (r == null || r[0].isBlank()) return null;
        return new String[]{r[0].strip(), r[1].strip()};
    }
    //endregion
}