package Project.CreatorKit;

import Project.Helpers.EditorKit;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.Locale;

public final class RgbaPopup extends JPopupMenu {
    //region Variables
    //region Layout Sizes
    private static final int POPUP_WIDTH = 236, SQUARE_HEIGHT = 160, BAR_HEIGHT = 14;
    private static final Color BG = new Color(43, 45, 50), BORDER = new Color(20, 21, 24);
    //endregion
    //region Color State
    private float hue, saturation, brightness;
    private double alpha;
    private final Listener listener;
    private boolean quiet;
    //endregion
    //region Components
    private final SquareBox square = new SquareBox();
    private final Bar hueBar = new Bar(false), alphaBar = new Bar(true);
    private final Preview preview = new Preview();
    private final JTextField hex = new JTextField(9);
    //endregion
    //endregion

    //region Constructors
    public RgbaPopup(int rgb, double alpha, Listener listener) {
        this.listener = listener;
        float[] hsb = Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
        hue = hsb[0];
        saturation = hsb[1];
        brightness = hsb[2];
        this.alpha = alpha;

        JPanel body = new JPanel();
        body.setBackground(BG);
        body.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER), BorderFactory.createEmptyBorder(8, 8, 8, 8)));
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        square.setAlignmentX(LEFT_ALIGNMENT);
        body.add(square);
        body.add(Box.createVerticalStrut(8));
        hueBar.setAlignmentX(LEFT_ALIGNMENT);
        body.add(hueBar);
        body.add(Box.createVerticalStrut(6));
        alphaBar.setAlignmentX(LEFT_ALIGNMENT);
        body.add(alphaBar);
        body.add(Box.createVerticalStrut(8));

        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.add(preview, BorderLayout.WEST);
        hex.setBackground(new Color(30, 31, 35));
        hex.setForeground(EditorKit.TEXT);
        hex.setCaretColor(EditorKit.TEXT);
        hex.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
        hex.addActionListener(actionEvent -> readHex());
        hex.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusLost(java.awt.event.FocusEvent focusEvent) { readHex(); }
        });
        row.add(hex, BorderLayout.CENTER);
        body.add(row);

        add(body);
        setBorder(BorderFactory.createEmptyBorder());
        updateAll();
    }
    //endregion

    //region Color Model
    private int rgb() { return Color.HSBtoRGB(hue, saturation, brightness) & 0xFFFFFF; }
    private void updateAll() {
        quiet = true;
        try {
            square.repaint();
            hueBar.repaint();
            alphaBar.repaint();
            preview.repaint();
            int a = (int) Math.round(alpha * 255);
            hex.setText(String.format(Locale.ROOT, "#%06X%02X", rgb(), a));
        } finally {
            quiet = false;
        }
    }
    private void readHex() {
        if (quiet) return;
        String text = hex.getText().strip();
        if (text.startsWith("#")) text = text.substring(1);
        if (!text.matches("[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?")) {
            updateAll();
            return;
        }
        int value = Integer.parseInt(text.substring(0, 6), 16);
        float[] hsb = Color.RGBtoHSB((value >> 16) & 0xFF, (value >> 8) & 0xFF, value & 0xFF, null);
        hue = hsb[0];
        saturation = hsb[1];
        brightness = hsb[2];
        if (text.length() == 8) alpha = Integer.parseInt(text.substring(6), 16) / 255.0;
        fire();
    }
    //endregion

    //region Change Notification
    public interface Listener { void changed(int rgb, double alpha); }
    private void fire() {
        if (quiet) return;
        updateAll();
        listener.changed(rgb(), alpha);
    }
    //endregion

    //region Color Controls
    private final class SquareBox extends JComponent {
        private BufferedImage image;
        private float imageHue = -1;
        SquareBox() {
            setPreferredSize(new Dimension(POPUP_WIDTH - 16, SQUARE_HEIGHT));
            setMaximumSize(new Dimension(POPUP_WIDTH - 16, SQUARE_HEIGHT));
            setMinimumSize(new Dimension(POPUP_WIDTH - 16, SQUARE_HEIGHT));
            MouseAdapter drag = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent mouseEvent) { pick(mouseEvent); }
                @Override public void mouseDragged(MouseEvent mouseEvent) { pick(mouseEvent); }
            };
            addMouseListener(drag);
            addMouseMotionListener(drag);
        }
        @Override protected void paintComponent(Graphics graphics) {
            int w = getWidth(), h = getHeight();
            if (image == null || image.getWidth() != w || image.getHeight() != h || imageHue != hue) {
                image = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_RGB);
                for (int y = 0; y < image.getHeight(); y++) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        image.setRGB(x, y, Color.HSBtoRGB(hue, x / (float) Math.max(1, w - 1), 1f - y / (float) Math.max(1, h - 1)));
                    }
                }
                imageHue = hue;
            }
            Graphics2D g2d = (Graphics2D) graphics.create();
            g2d.drawImage(image, 0, 0, null);
            int cx = Math.round(saturation * (w - 1)), cy = Math.round((1 - brightness) * (h - 1));
            g2d.setColor(Color.WHITE);
            g2d.setStroke(new BasicStroke(2f));
            g2d.drawOval(cx - 5, cy - 5, 10, 10);
            g2d.setColor(Color.BLACK);
            g2d.setStroke(new BasicStroke(1f));
            g2d.drawOval(cx - 6, cy - 6, 12, 12);
            g2d.dispose();
        }
        private void pick(MouseEvent mouseEvent) {
            saturation = (float) Math.clamp(mouseEvent.getX() / (double) Math.max(1, getWidth() - 1), 0, 1);
            brightness = (float) (1 - Math.clamp(mouseEvent.getY() / (double) Math.max(1, getHeight() - 1), 0, 1));
            fire();
        }
    }
    private final class Bar extends JComponent {
        private final boolean alphaMode;
        Bar(boolean alphaMode) {
            this.alphaMode = alphaMode;
            setPreferredSize(new Dimension(POPUP_WIDTH - 16, BAR_HEIGHT));
            setMaximumSize(new Dimension(POPUP_WIDTH - 16, BAR_HEIGHT));
            setMinimumSize(new Dimension(POPUP_WIDTH - 16, BAR_HEIGHT));
            MouseAdapter drag = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent mouseEvent) { pick(mouseEvent); }
                @Override public void mouseDragged(MouseEvent mouseEvent) { pick(mouseEvent); }
            };
            addMouseListener(drag);
            addMouseMotionListener(drag);
        }
        @Override protected void paintComponent(Graphics graphics) {
            int w = getWidth(), h = getHeight();
            Graphics2D g2d = (Graphics2D) graphics.create();
            for (int x = 0; x < w; x++) {
                if (alphaMode) {
                    g2d.setColor((x / 6) % 2 == 0 ? new Color(200, 200, 205) : new Color(140, 140, 146));
                    g2d.fillRect(x, 0, 1, h);
                    int a = (int) Math.round(255 * (x / (double) Math.max(1, w - 1)));
                    int c = rgb();
                    g2d.setColor(new Color((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, a));
                    g2d.fillRect(x, 0, 1, h);
                } else {
                    g2d.setColor(new Color(Color.HSBtoRGB(x / (float) Math.max(1, w - 1), 1f, 1f)));
                    g2d.fillRect(x, 0, 1, h);
                }
            }
            int marker = Math.round((float) (alphaMode ? alpha : hue) * (w - 1));
            g2d.setColor(Color.WHITE);
            g2d.fillRect(marker - 2, 0, 4, h);
            g2d.setColor(Color.BLACK);
            g2d.drawRect(marker - 2, 0, 4, h - 1);
            g2d.dispose();
        }
        private void pick(MouseEvent mouseEvent) {
            double t = Math.clamp(mouseEvent.getX() / (double) Math.max(1, getWidth() - 1), 0, 1);
            if (alphaMode) alpha = t; else hue = (float) Math.min(t, 0.999);
            fire();
        }
    }
    private final class Preview extends JComponent {
        Preview() {
            setPreferredSize(new Dimension(34, 26));
            setMinimumSize(new Dimension(34, 26));
        }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g2d = (Graphics2D) graphics.create();
            for (int y = 0; y < getHeight(); y += 6) {
                for (int x = 0; x < getWidth(); x += 6) {
                    g2d.setColor(((x + y) / 6) % 2 == 0 ? new Color(200, 200, 205) : new Color(140, 140, 146));
                    g2d.fillRect(x, y, 6, 6);
                }
            }
            int r = (rgb() >> 16) & 0xFF, g = (rgb() >> 8) & 0xFF, b = rgb() & 0xFF;
            g2d.setColor(new Color(r, g, b, (int) Math.round(alpha * 255)));
            g2d.fillRect(0, 0, getWidth(), getHeight());
            g2d.setColor(BORDER);
            g2d.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
            g2d.dispose();
        }
    }
    //endregion
}
