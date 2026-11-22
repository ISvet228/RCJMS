package Project.Update;

import Project.Helpers.*;
import Project.RCJMS;
import Project.StyleUI.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

final class UpdatePrompt extends JDialog {
    //region Variables
    private boolean accepted = false;
    private Point dragOffset;
    //endregion

    //region Constructors
    private UpdatePrompt(Window owner, Style style) {
        super(owner, ModalityType.APPLICATION_MODAL);
        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setAlwaysOnTop(true);
        setResizable(false);

        Color panel = panelColor(style);
        Color accent = accentColor(style);

        JPanel root = new JPanel(new BorderLayout(0, 14)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(panel);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
                g2.setColor(accent);
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 18, 18);
                g2.dispose();
            }
        };
        root.setOpaque(false);
        root.setBorder(BorderFactory.createEmptyBorder(22, 26, 22, 26));

        StyledLabel title = new StyledLabel(style, NSLocalizedString.get("update.title"), true, false);
        title.setPreferredSize(new Dimension(380, 40));
        StyledLabel message = new StyledLabel(style, NSLocalizedString.get("update.message"), true, true);
        message.setPreferredSize(new Dimension(380, 60));
        root.add(title, BorderLayout.NORTH);
        root.add(message, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 0));
        buttons.setOpaque(false);
        StyledButton later = new StyledButton(style, NSLocalizedString.get("update.later"));
        StyledButton download = new StyledButton(style, NSLocalizedString.get("update.download"));
        later.setPreferredSize(new Dimension(160, 44));
        download.setPreferredSize(new Dimension(160, 44));
        later.addActionListener(_ -> close(false));
        download.addActionListener(_ -> close(true));
        buttons.add(later);
        buttons.add(download);
        root.add(buttons, BorderLayout.SOUTH);

        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "later");
        root.getActionMap().put("later", new AbstractAction() { @Override public void actionPerformed(java.awt.event.ActionEvent event) { close(false); } });
        MouseAdapter drag = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) { dragOffset = event.getPoint(); }
            @Override public void mouseDragged(MouseEvent event) {
                Point screen = event.getLocationOnScreen();
                setLocation(screen.x - dragOffset.x, screen.y - dragOffset.y);
            }
        };
        root.addMouseListener(drag);
        root.addMouseMotionListener(drag);

        setContentPane(root);
        pack();
        setLocationRelativeTo(owner);
    }
    //endregion

    //region Asking
    static boolean ask() {
        if (SwingUtilities.isEventDispatchThread()) return askNow();
        boolean[] answer = {false};
        try { SwingUtilities.invokeAndWait(() -> answer[0] = askNow()); }
        catch (Exception exception) { System.err.println("RCJMS update prompt: " + exception.getMessage()); }
        return answer[0];
    }
    private static boolean askNow() {
        UpdatePrompt prompt = new UpdatePrompt(RCJMS.instance, loadStyle());
        prompt.setVisible(true);
        return prompt.accepted;
    }
    private void close(boolean download) {
        accepted = download;
        dispose();
    }
    private static Style loadStyle() {
        try {
            Style theme = SaveData.load().theme;
            return theme == null ? Style.FLAT : theme;
        } catch (Exception exception) { return Style.FLAT; }
    }
    private static Color accentColor(Style style) {
        return switch (style) {
            case NEUMORPHIC -> new Color(82, 84, 87);
            case GLASS -> new Color(255, 255, 255, 90);
            default -> new Color(71, 73, 79);
        };
    }
    private static Color panelColor(Style style) {
        return switch (style) {
            case NEUMORPHIC -> new Color(45, 47, 50);
            case GLASS -> new Color(35, 45, 55);
            default -> new Color(31, 33, 37);
        };
    }
    //endregion
}