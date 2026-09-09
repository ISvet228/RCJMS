package StyleUI;

import java.awt.*;
import java.util.function.Supplier;

public class StyledLabel extends AnimatedComponent {
    private Style style;
    private String text;
    private boolean borderVisible = true;
    private Color textColor;
    private Float textSize;
    private boolean reflectionEnabled = true;
    private boolean themed = true;
    private final Runnable reflectionTask;

    public StyledLabel(Style style, String text) { this(style, text, true, true); }
    public StyledLabel(Style style, String text, boolean borderVisible) { this(style, text, true, borderVisible); }
    public StyledLabel(Style style, String text, int referenceWidth, int referenceHeight) {
        this(style, text, true, true);
        setReferenceSize(referenceWidth, referenceHeight);
    }
    public StyledLabel(Style style, String text, int referenceWidth, int referenceHeight, boolean borderVisible) {
        this(style, text, true, borderVisible);
        setReferenceSize(referenceWidth, referenceHeight);
    }
    public StyledLabel(Style style, String text, int referenceWidth, int referenceHeight, boolean themed, boolean borderVisible) {
        this(style, text, themed, borderVisible);
        setReferenceSize(referenceWidth, referenceHeight);
    }
    public StyledLabel(Style style, String text, boolean themed, boolean borderVisible) {
        super();
        this.style = style;
        this.text = text == null ? "" : text;
        LocalizationBridge.bind(this, this.text, this::applyLocalizedText);
        this.borderVisible = borderVisible;
        this.themed = themed;
        setOpaque(false);

        if (themed) setPreferredSize(new Dimension(180, 36));
        reflectionTask = () -> {
            if (reflectionEnabled && style == Style.GLASS) {
                reflectionPhase = (reflectionPhase + 0.0035) % 1.0;
                repaint();
            }
        };
        if (themed && style == Style.GLASS && isDisplayable()) AnimationManager.register(reflectionTask);
    }
    public void setStyle(Style style) {
        if (style == null || style == this.style) return;
        this.style = style;
        repaint();
    }
    public Style getStyleValue() { return style; }
    public String getText() { return text; }
    public void setText(String text) {
        LocalizationBridge.externalTextChanged(this, text, this::applyLocalizedText);
    }
    public void setLocalizationKey(String key) {
        LocalizationBridge.unbind(this);
        LocalizationBridge.bind(this, key, this::applyLocalizedText);
    }
    public void setLocalizationFormat(String key, Supplier<Object[]> arguments) {
        LocalizationBridge.unbind(this);
        LocalizationBridge.bindFormat(this, key, arguments, this::applyLocalizedText);
    }
    private void applyLocalizedText(String text) {
        this.text = text == null ? "" : text;
        if (!themed) revalidate();
        repaint();
    }
    public boolean isBorderVisible() { return borderVisible; }
    public boolean hasBorder() { return borderVisible; }
    public void setBorderVisible(boolean visible) { if (borderVisible == visible) return; borderVisible = visible; repaint(); }
    public void setBorder(boolean visible) { setBorderVisible(visible); }
    public Color getTextColor() { return textColor; }
    public void setTextColor(Color color) { textColor = color; repaint(); }
    public void resetTextColor() { textColor = null; repaint(); }
    public float getTextSize() { return textSize == null ? 0f : textSize; }
    public void setTextSize(float size) { textSize = size > 0 ? size : null; repaint(); }
    public void resetTextSize() { textSize = null; repaint(); }
    public boolean isReflectionEnabled() { return reflectionEnabled; }
    public void setReflectionEnabled(boolean enabled) { reflectionEnabled = enabled; if (!enabled) reflectionPhase = 0; repaint(); }
    public void resetReflection() { reflectionPhase = 0; repaint(); }
    public boolean isThemed() { return themed; }
    public void setPreferredSize(int width, int height) { super.setPreferredSize(new Dimension(Math.max(1, width), Math.max(1, height))); }
    public Dimension getLabelPreferredSize() { return super.getPreferredSize(); }

    @Override public Dimension getPreferredSize() {
        if (themed || isPreferredSizeSet()) return super.getPreferredSize();
        FontMetrics fm = getFontMetrics(getFont());
        String t = text.isEmpty() ? " " : text;
        return new Dimension(fm.stringWidth(t) + 6, fm.getHeight() + 4);
    }
    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2d = graphics(g);
        int w = getWidth(), h = getHeight();
        float scale = parentScale();

        if (themed) {
            float arc = Math.clamp(Math.min(w, h) * 0.22f, 4, 30);
            g2d.setColor(style == Style.GLASS ? new Color(255, 255, 255, 26) : style.field);
            g2d.fillRoundRect(0, 0, w, h, Math.round(arc), Math.round(arc));
            if (borderVisible) {
                g2d.setColor(style.accent);
                g2d.drawRoundRect(0, 0, w - 1, h - 1, Math.round(arc), Math.round(arc));
            }
            if (style == Style.GLASS && reflectionEnabled) paintGlassSheen(g2d, w, h, arc, reflectionPhase);
        }

        float size = textSize != null ? textSize * scale : themed ? Math.clamp(h * 0.34f, 9 * scale, 40 * scale) : getFont().getSize2D();
        Color color = textColor != null ? textColor : (themed ? style.text : getForeground());
        drawCenteredText(g2d, text, color, size, 0, 0, w, h);
        g2d.dispose();
    }
    @Override public void addNotify() {
        super.addNotify();
        if (themed && style == Style.GLASS) AnimationManager.register(reflectionTask);
    }
    @Override public void removeNotify() {
        AnimationManager.unregister(reflectionTask);
        super.removeNotify();
    }
    protected Style getStyle() { return style; }
}