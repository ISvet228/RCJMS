package StyleUI;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Hashtable;

public class StyledSlider extends AnimatedComponent {
    private Style style;
    private int minimum = 0, maximum = 100;
    private int value = 55, extent = 0;
    private int majorTickSpacing = 10, minorTickSpacing = 0;
    private boolean paintTicks = false,  paintLabels = false;
    private boolean snapToTicks = false, inverted = false;
    private int orientation = SwingConstants.HORIZONTAL;
    private boolean dragging, enabled = true;
    private boolean valueIsAdjusting;
    private int trackPadding = 8;
    private float trackHeightRatio = 0.16f, thumbRatio = 0.46f;
    private int thumbMinSize = 10;
    private int unitIncrement = 1, blockIncrement = 10;
    private boolean scrollByUnit = true;
    private Color trackColor = new Color(50, 51, 54);
    private Color filledTrackColor, thumbColor, tickColor;
    private Hashtable<Integer, JLabel> labelTable;
    private final java.util.List<ChangeListener> listeners = new ArrayList<>();

    public StyledSlider(Style style, int minimum, int maximum, int value) {
        super();
        this.style = style;
        this.minimum = minimum;
        this.maximum = maximum;
        this.value = value;
        filledTrackColor = style.accent;
        thumbColor = tickColor = style.text;
        setFocusable(true);
        setPreferredSize(new Dimension(180, 44));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (!enabled) return;
                requestFocusInWindow();
                dragging = valueIsAdjusting = true;
                updateValue(e.getX(), e.getY());
                repaint();
            }
            @Override public void mouseReleased(MouseEvent e) {
                if (!enabled) return;
                dragging = valueIsAdjusting = false;
                repaint();
            }
        });
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseDragged(MouseEvent e) {if (!dragging || !enabled) return;updateValue(e.getX(), e.getY()); }});
        addMouseWheelListener(e -> {
            if (!enabled) return;
            int amount = e.getWheelRotation() * unitIncrement;
            setValue(value - amount); });
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (!enabled) return;
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_LEFT, KeyEvent.VK_DOWN -> setValue(value - unitIncrement);
                    case KeyEvent.VK_RIGHT, KeyEvent.VK_UP -> setValue(value + unitIncrement);
                    case KeyEvent.VK_PAGE_DOWN -> setValue(value - blockIncrement);
                    case KeyEvent.VK_PAGE_UP -> setValue(value + blockIncrement);
                    case KeyEvent.VK_HOME -> setValue(inverted ? maximum : minimum);
                    case KeyEvent.VK_END -> setValue(inverted ? minimum : maximum);
                }
            }
        });
    }
    public StyledSlider(Style style, int minimum, int maximum, int value, int referenceWidth, int referenceHeight) {
        this(style, minimum, maximum,  value);
        setReferenceSize(referenceWidth, referenceHeight);
    }
    protected Style getStyle() { return style; }
    public void setStyle(Style style) {
        if (style == null || style == this.style) return;
        this.style = style;
        filledTrackColor = style.accent;
        thumbColor = tickColor = style.text;
        repaint();
    }
    public int getMinimum() { return minimum; }
    public void setMinimum(int minimum) {
        if (minimum > maximum) maximum = minimum;
        this.minimum = minimum;
        value = Math.max(value, minimum);
        extent = Math.clamp(extent, 0, maximum - minimum);
        fireChange();
        repaint();
    }
    public int getMaximum() { return maximum; }
    public void setMaximum(int maximum) {
        if (maximum < minimum) minimum = maximum;
        this.maximum = maximum;
        value = Math.min(value, maximum);
        extent = Math.clamp(extent, 0, maximum - minimum);
        fireChange();
        repaint();
    }
    public int getValue() { return value; }
    public void setValue(int value) { setValueInternal(value, false); }
    private void setValueInternal(int value, boolean fromDrag) {
        int usableMaximum = Math.max(minimum, maximum - extent);
        int newValue = Math.clamp(value, minimum, usableMaximum);
        if (snapToTicks) newValue = snapValue(newValue);
        newValue = Math.clamp(newValue, minimum, usableMaximum);
        if (newValue != this.value) {
            this.value = newValue;
            fireChange();
        }
        repaint();
    }
    public int getExtent() { return extent; }
    public void setExtent(int extent) {
        this.extent = Math.clamp(extent, 0, maximum - minimum);
        value = Math.min(value, maximum - this.extent);
        fireChange();
        repaint();
    }
    public int getMajorTickSpacing() { return majorTickSpacing; }
    public void setMajorTickSpacing(int spacing) {
        majorTickSpacing = Math.max(0, spacing);
        repaint();
    }
    public int getMinorTickSpacing() { return minorTickSpacing; }
    public void setMinorTickSpacing(int spacing) {
        minorTickSpacing = Math.max(0, spacing);
        repaint();
    }
    public boolean getPaintTicks() { return paintTicks; }
    public void setPaintTicks(boolean paintTicks) {
        this.paintTicks = paintTicks;
        repaint();
    }
    public boolean getPaintLabels() { return paintLabels; }
    public void setPaintLabels(boolean paintLabels) {
        this.paintLabels = paintLabels;
        repaint();
    }
    public boolean getSnapToTicks() { return snapToTicks; }
    public void setSnapToTicks(boolean snapToTicks) {
        this.snapToTicks = snapToTicks;
        if (snapToTicks) setValueInternal(value, false);
        repaint();
    }
    public boolean getInverted() { return inverted; }
    public void setInverted(boolean inverted) {
        this.inverted = inverted;
        repaint();
    }
    public int getOrientation() { return orientation; }
    public void setOrientation(int orientation) {
        if (orientation != SwingConstants.HORIZONTAL && orientation != SwingConstants.VERTICAL) throw new IllegalArgumentException("orientation");
        this.orientation = orientation;
        revalidate();
        repaint();
    }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        super.setEnabled(enabled);
        repaint();
    }
    public boolean getValueIsAdjusting() { return valueIsAdjusting; }
    public void setValueIsAdjusting(boolean valueIsAdjusting) {
        if (this.valueIsAdjusting != valueIsAdjusting) {
            this.valueIsAdjusting = valueIsAdjusting;
            fireChange();
        }
    }
    public int getUnitIncrement() { return unitIncrement; }
    public void setUnitIncrement(int unitIncrement) { this.unitIncrement = Math.max(1, unitIncrement); }
    public int getBlockIncrement() { return blockIncrement; }
    public void setBlockIncrement(int blockIncrement) { this.blockIncrement = Math.max(1, blockIncrement); }
    public boolean isScrollByUnit() { return scrollByUnit; }
    public void setScrollByUnit(boolean scrollByUnit) { this.scrollByUnit = scrollByUnit; }
    public int getTrackPadding() { return trackPadding; }
    public void setTrackPadding(int trackPadding) {
        this.trackPadding = Math.max(0, trackPadding);
        repaint();
    }

    public float getTrackHeightRatio() { return trackHeightRatio; }
    public void setTrackHeightRatio(float ratio) {
        trackHeightRatio = Math.clamp(ratio, 0.03f, 0.5f);
        repaint();
    }

    public float getThumbRatio() { return thumbRatio; }
    public void setThumbRatio(float ratio) {
        thumbRatio = Math.clamp(ratio, 0.15f, 1.0f);
        repaint();
    }

    public int getThumbMinSize() { return thumbMinSize; }
    public void setThumbMinSize(int size) {
        thumbMinSize = Math.max(4, size);
        repaint();
    }

    public Color getTrackColor() { return trackColor; }
    public void setTrackColor(Color color) {
        trackColor = color;
        repaint();
    }

    public Color getFilledTrackColor() { return filledTrackColor; }
    public void setFilledTrackColor(Color color) {
        filledTrackColor = color;
        repaint();
    }

    public Color getThumbColor() { return thumbColor; }
    public void setThumbColor(Color color) {
        thumbColor = color;
        repaint();
    }

    public Color getTickColor() { return tickColor; }
    public void setTickColor(Color color) {
        tickColor = color;
        repaint();
    }

    public Hashtable<Integer, JLabel> getLabelTable() { return labelTable; }
    public void setLabelTable(Hashtable<Integer, JLabel> table) {
        labelTable = table;
        repaint();
    }

    public void addChangeListener(ChangeListener listener) { if (listener != null) listeners.add(listener); }
    public void removeChangeListener(ChangeListener listener) { listeners.remove(listener); }

    private void fireChange() {
        ChangeEvent event = new ChangeEvent(this);
        for (ChangeListener listener : listeners) listener.stateChanged(event);
    }
    private int snapValue(int value) {
        int spacing = minorTickSpacing > 0 ? minorTickSpacing : majorTickSpacing;
        if (spacing <= 0) return value;
        int a = ((value - minimum) / spacing) * spacing + minimum;
        int b = Math.min(maximum - extent, a + spacing);
        return Math.abs(value - a) <= Math.abs(value - b) ? a : b;
    }
    private void updateValue(int mouseX, int mouseY) {
        int pad = Math.max(scaled(trackPadding), scaled(4));
        double t = Math.clamp((orientation == SwingConstants.HORIZONTAL ? mouseX - pad : mouseY - pad) /
                Math.max(1, orientation == SwingConstants.HORIZONTAL ? getWidth() : getHeight() - pad * 2.0), 0, 1);
        if (orientation == SwingConstants.VERTICAL) t = 1.0 - t;
        if (inverted) t = 1.0 - t;
        setValueInternal(minimum + (int)Math.round(t * (Math.max(minimum, maximum - extent) - minimum)), true);
    }
    private int valuePosition(int start, int length) {
        int usableMaximum = Math.max(minimum, maximum - extent);
        double t = usableMaximum == minimum ? 0 : (value - minimum) / (double) (usableMaximum - minimum);
        if (inverted) t = 1.0 - t;
        if (orientation == SwingConstants.VERTICAL) t = 1.0 - t;
        return start + (int) Math.round(length * t);
    }
    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2d = graphics(g);
        int w = getWidth(), h = getHeight();
        int pad = Math.max(scaled(trackPadding), Math.round(Math.min(w, h) * 0.18f));
        int trackThickness = Math.max(3, Math.round((orientation == SwingConstants.HORIZONTAL ? h : w) * trackHeightRatio));
        int thumb = Math.max(scaled(thumbMinSize), Math.round((orientation == SwingConstants.HORIZONTAL ? h : w) * thumbRatio));
        Color tr, fr, th, tk;
        if (style == Style.GLASS) {
            tr = new Color(255, 255, 255, enabled ? 45 : 20);
            fr = new Color(255, 255, 255, enabled ? 155 : 70);
            th = new Color(255, 255, 255, enabled ? 225 : 90);
            tk = new Color(255, 255, 255, enabled ? 130 : 60);
        }
        else {
            tr = trackColor;
            fr = filledTrackColor;
            th = thumbColor;
            tk = tickColor;
        }
        if (orientation == SwingConstants.HORIZONTAL) {
            int trackY = h / 2 - trackThickness / 2;
            int trackLength = Math.max(1, w - pad * 2);
            int valuePos = valuePosition(pad, trackLength);
            g2d.setColor(tr);
            g2d.fillRoundRect(pad, trackY, trackLength, trackThickness, trackThickness, trackThickness);
            g2d.setColor(fr);
            g2d.fillRoundRect(pad, trackY, Math.clamp(valuePos - pad, 0, trackLength), trackThickness, trackThickness, trackThickness);
            paintHorizontalTicks(g2d, pad, trackLength, trackY, tk);
            int thumbX = valuePos - thumb / 2;
            int thumbY = h / 2 - thumb / 2;
            g2d.setColor(th);
            g2d.fillOval(thumbX, thumbY, thumb, thumb);
            g2d.setColor(style.accent);
            g2d.drawOval(thumbX, thumbY, thumb - 1, thumb - 1);
            paintHorizontalLabels(g2d, pad, trackLength, h, parentScale());
        }
        else {
            int trackX = w / 2 - trackThickness / 2;
            int trackLength = Math.max(1, h - pad * 2);
            int valuePos = valuePosition(pad, trackLength);
            g2d.setColor(tr);
            g2d.fillRoundRect(trackX, pad, trackThickness, trackLength, trackThickness, trackThickness);
            g2d.setColor(fr);
            g2d.fillRoundRect(trackX, pad, trackThickness, Math.clamp(valuePos - pad, 0, trackLength), trackThickness, trackThickness);
            paintVerticalTicks(g2d, pad, trackLength, trackX, tk);
            int thumbX = w / 2 - thumb / 2;
            int thumbY = valuePos - thumb / 2;
            g2d.setColor(th);
            g2d.fillOval(thumbX, thumbY, thumb, thumb);
            g2d.setColor(style.accent);
            g2d.drawOval(thumbX, thumbY, thumb - 1, thumb - 1);
            paintVerticalLabels(g2d, pad, trackLength, w, parentScale());
        }
        if (style == Style.GLASS) paintGlassSheen(g2d, w, h, Math.clamp(Math.min(w, h) * 0.3f, scaled(4), scaled(14)), reflectionPhase);
        g2d.dispose();
    }
    private void paintHorizontalTicks(Graphics2D g2d, int pad, int length, int y, Color color) {
        if (!paintTicks) return;
        int step = minorTickSpacing > 0 ? minorTickSpacing : majorTickSpacing;
        if (step <= 0) return;
        int range = maximum - minimum;
        g2d.setColor(color);
        for (int v = minimum; v <= maximum; v += step) {
            int x = pad + (int) Math.round(length * (range == 0 ? 0 : (v - minimum) / (double) range));
            g2d.drawLine(x, y + 2, x, y + 2 + (Math.max(3, scaled(step == majorTickSpacing ? 7 : 4))));
        }
    }
    private void paintVerticalTicks(Graphics2D g2d, int pad, int length, int x, Color color) {
        if (!paintTicks) return;
        int step = minorTickSpacing > 0 ? minorTickSpacing : majorTickSpacing;
        if (step <= 0) return;
        int range = maximum - minimum;
        g2d.setColor(color);
        for (int v = minimum; v <= maximum; v += step) {
            int y = pad + (int) Math.round(length * (range == 0 ? 0 : (v - minimum) / (double)range));
            g2d.drawLine(x + 2, y, x + 2 + (Math.max(3, scaled(step == majorTickSpacing ? 7 : 4))), y);
        }
    }
    private void paintHorizontalLabels(Graphics2D g2d, int pad, int length, int h, float scale) {
        if (!paintLabels) return;
        int step = majorTickSpacing > 0 ? majorTickSpacing : minorTickSpacing;
        if (step <= 0) return;
        g2d.setFont(new Font("SansSerif", Font.PLAIN, Math.max(9, Math.round(10 * scale))));
        g2d.setColor(style.text);
        FontMetrics fm = g2d.getFontMetrics();
        for (int v = minimum; v <= maximum; v += step) {
            int x = pad + (int) Math.round(length * (maximum == minimum ? 0 : (v - minimum) / (double)(maximum - minimum)));
            String text = labelText(v);
            g2d.drawString(text, x - fm.stringWidth(text) / 2, h - 2);
        }
    }
    private void paintVerticalLabels(Graphics2D g2d, int pad, int length, int w, float scale) {
        if (!paintLabels) return;
        int step = majorTickSpacing > 0 ? majorTickSpacing : minorTickSpacing;
        if (step <= 0) return;
        g2d.setFont(new Font("SansSerif", Font.PLAIN, Math.max(9, Math.round(10 * scale))));
        g2d.setColor(style.text);
        FontMetrics fm = g2d.getFontMetrics();
        for (int v = minimum; v <= maximum; v += step) {
            int y = pad + (int) Math.round(length * (maximum == minimum ? 0 : (v - minimum) / (double)(maximum - minimum)));
            String text = labelText(v);
            g2d.drawString(text, 2, y + fm.getAscent() / 2);
        }
    }
    private String labelText(int value) {
        if (labelTable != null) {
            JLabel label = labelTable.get(value);
            if (label != null) return label.getText();
        }
        return Integer.toString(value);
    }
}
