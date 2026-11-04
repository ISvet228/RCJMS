package Project.TextureEditor;
import Project.EngineCreator.*;
import Project.ThemeCreator.*;
import Project.CreatorKit.*;
import Project.Helpers.EditorKit;

import Project.StyleUI.Style;
import Project.StyleUI.StyledTextField;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

import static Project.Helpers.EditorKit.*;

public final class ColorPicker extends EditorKit.Column {
    //region Variables
    //region Palette
    public static final int[] PALETTE = {
            0x000000, 0x4A4A4A, 0x8A8A8A, 0xC8C8C8, 0xFFFFFF, 0x5C3A21, 0x8B5A2B, 0xC68E5B,
            0xECD485, 0xD3AF63, 0x816E1E, 0x33FF66, 0x1E8A3C, 0x0F4D2A, 0x3FB6A8, 0x7FD4FF,
            0x3A86FF, 0x1B3A8A, 0x7A4DFF, 0xFF4D9D, 0xE63946, 0xFF7A1A, 0xFFD23F, 0xB5E24B};
    private static final int RECENT_COUNT = 8;
    private static final List<Integer> RECENT = new ArrayList<>();
    //endregion
    //region Color State
    private float hue = 0f, saturation = 0f, brightness = 1f;
    private boolean updating;
    private final List<IntConsumer> listeners = new ArrayList<>();
    //endregion
    //region Child Widgets
    private final int width;
    private final SvBox svBox;
    private final HueBar hueBar;
    private final Preview preview;
    private final StyledTextField hexField;
    private final Swatches paletteGrid, recentGrid;
    //endregion
    //endregion

    //region Constructors
    public ColorPicker(Style style, int width, int svHeight) {
        this.width = width;
        svBox = new SvBox(svHeight);
        hueBar = new HueBar();
        preview = new Preview();
        hexField = new StyledTextField(style, "FFFFFF");
        hexField.setHorizontalAlignment(JTextField.CENTER);
        hexField.limitInput(7, c -> c == '#' || Character.digit(c, 16) >= 0);
        hexField.addActionListener(actionEvent -> applyHex());
        hexField.addFocusListener(new FocusAdapter() { @Override public void focusLost(FocusEvent focusEvent) { applyHex(); } });

        JPanel hexRow = new JPanel(new BorderLayout(8, 0));
        hexRow.setOpaque(false);
        hexRow.add(preview, BorderLayout.WEST);
        hexRow.add(hexField, BorderLayout.CENTER);
        hexRow.setPreferredSize(new Dimension(width, 30));

        paletteGrid = new Swatches(width, 8, PALETTE.length / 8, false);
        recentGrid = new Swatches(width, RECENT_COUNT, 1, true);

        put(svBox, 0);
        put(hueBar, 6);
        put(hexRow, 8);
        put(new Text("te.palette", 10.5f, true, MUTED, 0), 8);
        put(paletteGrid, 2);
        put(new Text("te.recent", 10.5f, true, MUTED, 0), 6);
        put(recentGrid, 2);
        setColor(0xFFFFFF, false);
    }
    //endregion

    //region Color Value
    public int getColor() { return Color.HSBtoRGB(hue, saturation, brightness) & 0xFFFFFF; }
    public void setColor(int rgb, boolean fire) {
        rgb &= 0xFFFFFF;
        float[] hsb = Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
        if (hsb[1] > 0.001f && hsb[2] > 0.001f) hue = hsb[0];
        saturation = hsb[1];
        brightness = hsb[2];
        refresh(fire);
    }
    public void addColorListener(IntConsumer listener) { listeners.add(listener); }
    private void refresh(boolean fire) {
        updating = true;
        hexField.setText(String.format("#%06X", getColor()));
        updating = false;
        svBox.invalidateImage();
        repaintAll();
        if (fire) for (IntConsumer intConsumer : new ArrayList<>(listeners)) intConsumer.accept(getColor());
    }
    private void repaintAll() { svBox.repaint(); hueBar.repaint(); preview.repaint(); paletteGrid.repaint(); recentGrid.repaint(); }
    private void applyHex() {
        if (updating) return;
        String value = hexField.getText().trim().replace("#", "");
        if (value.length() == 3) value = "" + value.charAt(0) + value.charAt(0) + value.charAt(1) + value.charAt(1) + value.charAt(2) + value.charAt(2);
        if (value.length() == 6) {
            try { setColor(Integer.parseInt(value, 16), true); return; }
            catch (NumberFormatException ignored) { }
        }
        refresh(false);
    }
    //endregion

    //region Recent Colors
    public void remember() {
        int rgb = getColor();
        RECENT.remove(Integer.valueOf(rgb));
        RECENT.addFirst(rgb);
        while (RECENT.size() > RECENT_COUNT) RECENT.removeLast();
        recentGrid.repaint();
    }
    //endregion

    //region Inner Widgets
    //region Saturation and Brightness
    private final class SvBox extends JComponent {
        private BufferedImage image;
        private float imageHue = -1;

        public SvBox(int height) {
            setPreferredSize(new Dimension(width, height));
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            MouseAdapter mouse = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent mouseEvent) { pick(mouseEvent); }
                @Override public void mouseDragged(MouseEvent mouseEvent) { pick(mouseEvent); }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }

        public void invalidateImage() { imageHue = -1; }
        @Override protected void paintComponent(Graphics graphics) {
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            if (image == null || image.getWidth() != w || image.getHeight() != h || imageHue != hue) {
                image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                for (int y = 0; y < h; y++) for (int x = 0; x < w; x++)
                    image.setRGB(x, y, Color.HSBtoRGB(hue, x / (float) Math.max(1, w - 1), 1f - y / (float) Math.max(1, h - 1)));
                imageHue = hue;
            }
            Graphics2D g2d = smooth(graphics);
            g2d.setClip(new RoundRectangle2D.Double(0, 0, w, h, 8, 8));
            g2d.drawImage(image, 0, 0, null);
            g2d.setClip(null);
            g2d.setColor(CARD_LINE);
            g2d.drawRoundRect(0, 0, w - 1, h - 1, 8, 8);
            double hx = saturation * (w - 1), hy = (1 - brightness) * (h - 1);
            g2d.setStroke(new BasicStroke(2f));
            g2d.setColor(Color.BLACK);
            g2d.draw(new Ellipse2D.Double(hx - 6, hy - 6, 12, 12));
            g2d.setColor(Color.WHITE);
            g2d.draw(new Ellipse2D.Double(hx - 5, hy - 5, 10, 10));
            g2d.dispose();
        }

        private void pick(MouseEvent mouseEvent) {
            saturation = Math.clamp(mouseEvent.getX() / (float) Math.max(1, getWidth() - 1), 0f, 1f);
            brightness = 1f - Math.clamp(mouseEvent.getY() / (float) Math.max(1, getHeight() - 1), 0f, 1f);
            refresh(true);
        }
    }
    //endregion
    //region Hue
    private final class HueBar extends JComponent {
        public HueBar() {
            setPreferredSize(new Dimension(width, 16));
            setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
            MouseAdapter mouse = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent mouseEvent) { pick(mouseEvent); }
                @Override public void mouseDragged(MouseEvent mouseEvent) { pick(mouseEvent); }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }

        @Override protected void paintComponent(Graphics graphics) {
            int w = getWidth(), h = getHeight();
            Graphics2D g2d = smooth(graphics);
            g2d.setClip(new RoundRectangle2D.Double(0, 0, w, h, 8, 8));
            for (int x = 0; x < w; x++) { g2d.setColor(new Color(Color.HSBtoRGB(x / (float) Math.max(1, w - 1), 1f, 1f))); g2d.drawLine(x, 0, x, h); }
            g2d.setClip(null);
            g2d.setColor(CARD_LINE);
            g2d.drawRoundRect(0, 0, w - 1, h - 1, 8, 8);
            int hx = Math.round(hue * (w - 1));
            g2d.setColor(Color.BLACK);
            g2d.fillRoundRect(hx - 3, -1, 7, h + 2, 4, 4);
            g2d.setColor(Color.WHITE);
            g2d.fillRoundRect(hx - 2, 0, 5, h, 3, 3);
            g2d.dispose();
        }

        private void pick(MouseEvent mouseEvent) {
            hue = Math.clamp(mouseEvent.getX() / (float) Math.max(1, getWidth() - 1), 0f, 0.999f);
            if (saturation < 0.001f) saturation = 1f;
            if (brightness < 0.001f) brightness = 1f;
            refresh(true);
        }
    }
    //endregion
    //region Preview
    private final class Preview extends JComponent {
        public Preview() { setPreferredSize(new Dimension(40, 28)); }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g2d = smooth(graphics);
            g2d.setColor(new Color(getColor()));
            g2d.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
            g2d.setColor(Color.WHITE);
            g2d.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
            g2d.dispose();
        }
    }
    //endregion
    //region Swatches
    private final class Swatches extends JComponent {
        private final int columns, rowCount, size;
        private static final int GAP = 3;
        private final boolean recent;

        public Swatches(int totalWidth, int columns, int rowCount, boolean recent) {
            this.columns = columns; this.rowCount = rowCount; this.recent = recent;
            this.size = (totalWidth - GAP * (columns - 1)) / columns;
            setPreferredSize(new Dimension(totalWidth, rowCount * size + (rowCount - 1) * GAP));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent mouseEvent) {
                    int index = indexAt(mouseEvent.getX(), mouseEvent.getY());
                    Integer color = colorAt(index);
                    if (color != null) setColor(color, true);
                }
            });
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g2d = smooth(graphics);
            int current = getColor();
            for (int i = 0; i < columns * rowCount; i++) {
                int x = (i % columns) * (size + GAP), y = (i / columns) * (size + GAP);
                Integer color = colorAt(i);
                if (color == null) {
                    g2d.setColor(CARD_LINE);
                    g2d.drawRoundRect(x, y, size - 1, size - 1, 6, 6);
                    continue;
                }
                g2d.setColor(new Color(color));
                g2d.fillRoundRect(x, y, size, size, 6, 6);
                boolean chosen = color == current;
                g2d.setColor(chosen ? Color.WHITE : new Color(0, 0, 0, 110));
                g2d.setStroke(new BasicStroke(chosen ? 2f : 1f));
                g2d.drawRoundRect(x + (chosen ? 1 : 0), y + (chosen ? 1 : 0), size - 1 - (chosen ? 2 : 0), size - 1 - (chosen ? 2 : 0), 6, 6);
            }
            g2d.dispose();
        }

        private int indexAt(int x, int y) {
            int cx = x / (size + GAP), cy = y / (size + GAP);
            if (cx >= columns || cy >= rowCount || x % (size + GAP) >= size || y % (size + GAP) >= size) return -1;
            return cy * columns + cx;
        }
        private Integer colorAt(int index) {
            if (index < 0) return null;
            if (!recent) return index < PALETTE.length ? PALETTE[index] : null;
            return index < RECENT.size() ? RECENT.get(index) : null;
        }
    }
    //endregion
    //endregion
}