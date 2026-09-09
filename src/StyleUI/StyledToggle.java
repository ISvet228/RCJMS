package StyleUI;

import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public  class StyledToggle extends AnimatedComponent {
    private Style style;
    private String text;
    private boolean selected;
    private final List<ActionListener> listeners = new ArrayList<>();

    public StyledToggle(Style style, String text) {
        super();
        this.style = style;
        this.text = text == null ? "" : text;
        LocalizationBridge.bind(this, this.text, this::applyLocalizedText);
        setPreferredSize(new Dimension(180, 34));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override public void mouseReleased(MouseEvent e) { if (contains(e.getPoint())) {
                selected = !selected;
                repaint();
                fireActionEvent();
            }}});
    }
    public StyledToggle(Style style, String text, int referenceWidth, int referenceHeight) {
        this(style, text);
        setReferenceSize(referenceWidth, referenceHeight);
    }
    protected Style getStyle() { return style; }
    public void setStyle(Style style) {
        if (style == null || style == this.style) return;
        this.style = style;
        repaint();
    }
    public String getText() { return text; }
    public void setText(String text) { LocalizationBridge.externalTextChanged(this, text, this::applyLocalizedText); }
    public void setLocalizationKey(String key) { LocalizationBridge.unbind(this); LocalizationBridge.bind(this, key, this::applyLocalizedText); }
    public void setLocalizationFormat(String key, Supplier<Object[]> arguments) { LocalizationBridge.unbind(this); LocalizationBridge.bindFormat(this, key, arguments, this::applyLocalizedText); }
    private void applyLocalizedText(String text) { this.text = text == null ? "" : text; revalidate(); repaint(); }
    public boolean isSelected() { return selected; }
    public void setSelected(boolean selected) {
        if (this.selected == selected) return;
        this.selected = selected;
        repaint();
    }
    public void addActionListener(ActionListener listener) { if (listener != null) listeners.add(listener); }
    public void removeActionListener(ActionListener listener) { listeners.remove(listener); }
    private void fireActionEvent() {
        ActionEvent event = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, text);
        for (ActionListener listener : new ArrayList<>(listeners)) listener.actionPerformed(event);
    }
    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2d = graphics(g);
        int h = getHeight();
        int size = Math.max(12, Math.round(h * 0.62f));
        int y = (h - size) / 2;
        Color box = selected ? style.accent : style.field;
        if (style == Style.GLASS) box = new Color(255, 255, 255, selected ? 125 : 35);
        g2d.setColor(box);
        g2d.fillRoundRect(0, y, size * 2, size, size, size);
        g2d.setColor(style.text);
        g2d.drawRoundRect(0, y, size * 2 - 1, size - 1, size, size);
        if (style == Style.GLASS) {
            Graphics2D sheen = (Graphics2D) g2d.create();
            sheen.translate(0, y);
            paintGlassSheen(sheen, size * 2, size, size, reflectionPhase);
            sheen.dispose();
        }
        g2d.setColor(style.text);
        int dot = Math.max(4, size * 2 / 3);
        g2d.fillOval((selected ? size + size / 2 - dot / 2 : size / 2 - dot / 2), y + size / 6, dot, dot);
        g2d.setFont(new Font("SansSerif", Font.BOLD, Math.round(Math.clamp(h * 0.40f, 9, 32))));
        FontMetrics fm = g2d.getFontMetrics();
        g2d.drawString(text, size * 2 + scaled(9), y + (size + fm.getAscent() - fm.getDescent()) / 2);
        g2d.dispose();
    }
}