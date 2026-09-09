package StyleUI;

import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class StyledButton extends AnimatedComponent {
    private Style style;
    private String text;
    private final List<ActionListener> listeners = new ArrayList<>();
    public StyledButton(Style style, String text) {
        super();
        this.style = style;
        this.text = text == null ? "" : text;
        LocalizationBridge.bind(this, this.text, this::applyLocalizedText);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setPreferredSize(new Dimension(180, 42));
        addMouseListener(new MouseAdapter() {
            @Override public void mouseReleased(MouseEvent e) {
                if (!contains(e.getPoint())) return;
                ActionEvent event = new ActionEvent(StyledButton.this, ActionEvent.ACTION_PERFORMED, text);
                for (ActionListener listener : listeners) listener.actionPerformed(event); }});
    }
    public void addActionListener(ActionListener listener) { if (listener != null) listeners.add(listener); }
    public String getText() { return text; }
    public void setText(String text) { LocalizationBridge.externalTextChanged(this, text, this::applyLocalizedText); }
    public void setLocalizationKey(String key) { LocalizationBridge.unbind(this); LocalizationBridge.bind(this, key, this::applyLocalizedText); }
    public void setLocalizationFormat(String key, Supplier<Object[]> arguments) { LocalizationBridge.unbind(this); LocalizationBridge.bindFormat(this, key, arguments, this::applyLocalizedText); }
    private void applyLocalizedText(String text) { this.text = text == null ? "" : text; revalidate(); repaint(); }
    public StyledButton(Style style, String text, int referenceWidth, int referenceHeight) {
        this(style, text);
        setReferenceSize(referenceWidth, referenceHeight);
    }
    protected Style getStyle() { return style; }
    public void setStyle(Style style) {
        if (style == null || style == this.style) return;
        this.style = style;
        repaint();
    }
    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2d = graphics(g);
        int w = getWidth(), h = getHeight();
        int inset = Math.max(1, Math.round(Math.min(w, h) * 0.025f));
        int x = inset, y = inset;
        int bw = Math.max(1, w - inset * 2);
        int bh = Math.max(1, h - inset * 2);
        if (mousePressed) {
            int press = scaled(2);
            x += press;
            y += press;
        }
        Color fill = mix(style.normal, style.hover, (float)animation);
        if (style == Style.GLASS) fill = new Color(255, 255, 255, Math.round(22 + 35 * (float)animation));
        float arc = Math.clamp(Math.min(bw, bh) * 0.22f, scaled(4), scaled(24));
        g2d.setColor(fill);
        g2d.fillRoundRect(x, y, bw, bh, Math.round(arc), Math.round(arc));
        g2d.setColor(style.accent);
        g2d.drawRoundRect(x, y, bw - 1, bh - 1, Math.round(arc), Math.round(arc));
        if (style == Style.GLASS) {
            Graphics2D sheen = (Graphics2D) g2d.create();
            sheen.translate(x, y);
            paintGlassSheen(sheen, bw, bh, arc, reflectionPhase);
            sheen.dispose();
        }
        drawCenteredText(g2d, text, style.text, Math.max(9f, h * 0.34f), x, y, bw, bh);
        g2d.dispose();
    }
}