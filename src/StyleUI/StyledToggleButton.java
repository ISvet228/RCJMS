package StyleUI;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.*;
import java.util.function.Supplier;

public class StyledToggleButton extends JToggleButton {
    private Style style;
    private double animation, reflectionPhase;
    private boolean mouseOver, mousePressed;
    private Dimension baseParentSize;
    private Runnable animationTask;

    private int baseX, baseY;
    private int baseWidth, baseHeight;
    private int referenceWidth, referenceHeight;
    private boolean scaling;
    private boolean referenceScaling;

    public StyledToggleButton(Style style, String text) {
        super(text);
        this.style = style;
        LocalizationBridge.bind(this, text, this::applyLocalizedText);
        this.referenceScaling = false;
        initialize();
    }
    public StyledToggleButton(Style style, String text, int referenceWidth, int referenceHeight) {
        super(text);
        this.style = style;
        LocalizationBridge.bind(this, text, this::applyLocalizedText);
        this.referenceWidth = Math.max(1, referenceWidth);
        this.referenceHeight = Math.max(1, referenceHeight);
        this.referenceScaling = true;
        initialize();
    }

    @Override public void setText(String text) {
        LocalizationBridge.externalTextChanged(this, text, value -> StyledToggleButton.super.setText(value == null ? "" : value));
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
        super.setText(text == null ? "" : text);
        revalidate();
        repaint();
    }

    private void initialize() {
        setOpaque(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setRolloverEnabled(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setPreferredSize(new Dimension(180, 42));

        enableEvents(AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);

        animationTask = () -> {
            double target = mouseOver || mousePressed || isSelected() ? 1.0 : 0.0;
            double old = animation;
            animation += (target - animation) * 0.22;
            if (Math.abs(animation - target) < 0.003) animation = target;
            boolean glass = style == Style.GLASS;
            if (glass) reflectionPhase = (reflectionPhase + 0.0035) % 1.0;
            if (old != animation || glass) repaint();
        };
    }

    @Override public void addNotify() {
        super.addNotify();
        AnimationManager.register(animationTask);
        SwingUtilities.invokeLater(this::updateScale);
    }

    @Override public void removeNotify() {
        AnimationManager.unregister(animationTask);
        super.removeNotify();
    }

    protected Style getStyle() { return style; }

    public void setStyle(Style style) {
        if (style == null || style == this.style) return;
        this.style = style;
        repaint();
    }

    @Override protected void processMouseEvent(MouseEvent e) {
        switch (e.getID()) {
            case MouseEvent.MOUSE_ENTERED -> mouseOver = true;
            case MouseEvent.MOUSE_EXITED -> {
                mouseOver = false;
                mousePressed = false;
            }
            case MouseEvent.MOUSE_PRESSED -> mousePressed = true;
            case MouseEvent.MOUSE_RELEASED -> mousePressed = false;
        }
        super.processMouseEvent(e);
        repaint();
    }

    @Override protected void processMouseMotionEvent(MouseEvent e) {
        super.processMouseMotionEvent(e);
        repaint();
    }

    protected Graphics2D graphics(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g2d;
    }

    protected float parentScale() {
        Container parent = getParent();
        if (parent == null || parent.getWidth() <= 0 || parent.getHeight() <= 0) return 1f;
        if (baseParentSize == null || baseParentSize.width <= 0 || baseParentSize.height <= 0) baseParentSize = new Dimension(parent.getWidth(), parent.getHeight());
        float sx = parent.getWidth() / (float) baseParentSize.width;
        float sy = parent.getHeight() / (float) baseParentSize.height;
        return Math.max(0.1f, Math.min(sx, sy));
    }

    protected int scaled(int value) { return Math.max(1, Math.round(value * parentScale())); }
    protected float scaled(float value) { return value * parentScale(); }

    protected void resetParentScaleReference() {
        Container parent = getParent();
        if (parent == null) {
            baseParentSize = null;
            return;
        }
        baseParentSize = new Dimension(Math.max(1, parent.getWidth()), Math.max(1, parent.getHeight()));
    }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2d = graphics(g);
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) {
            g2d.dispose();
            return;
        }

        int inset = Math.max(1, Math.round(Math.min(w, h) * 0.025f));
        int x = inset, y = inset;
        int bw = Math.max(1, w - inset * 2);
        int bh = Math.max(1, h - inset * 2);

        if (mousePressed) {
            int press = scaled(2);
            x += press;
            y += press;
        }

        Color fill = mix(style.normal, style.hover, (float) animation);
        if (style == Style.GLASS) fill = new Color(255, 255, 255, Math.round(22 + 35 * (float) animation));

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

        drawCenteredText(g2d, getText(), style.text, Math.max(9f, h * 0.34f), x, y, bw, bh);
        g2d.dispose();
    }

    static void paintGlassSheen(Graphics2D g2d, int w, int h, float arc, double phase) {
        if (w <= 0 || h <= 0) return;

        Graphics2D g2dc = (Graphics2D) g2d.create();
        g2dc.clip(new RoundRectangle2D.Float(0, 0, w, h, arc, arc));

        float band = Math.max(w, h) * 0.5f;
        float start = (float) (phase * w + h + band * 2) - band - h;

        float[] fractions = {0f, 0.5f, 1f};
        Color[] colors = {
                new Color(255, 255, 255, 0),
                new Color(255, 255, 255, Math.round(255 * 0.45f)),
                new Color(255, 255, 255, 0)
        };

        g2dc.setPaint(new LinearGradientPaint((new Point2D.Float(start, 0)), (new Point2D.Float(start + band, h)), fractions, colors, MultipleGradientPaint.CycleMethod.NO_CYCLE));
        g2dc.fillRoundRect(0, 0, w, h, Math.round(arc), Math.round(arc));
        g2dc.dispose();
    }

    static void drawCenteredText(Graphics2D g2d, String text, Color color, float size, int x, int y, int w, int h) {
        if (text == null || text.isEmpty()) return;

        Font font = new Font("SansSerif", Font.BOLD, Math.max(1, Math.round(size)));
        g2d.setFont(font);
        g2d.setColor(color);

        FontMetrics fm = g2d.getFontMetrics(font);
        int ty = y + (h - fm.getHeight()) / 2 + fm.getAscent();
        ty = Math.max(y + fm.getAscent(), Math.min(y + h - Math.max(1, fm.getDescent()), ty));

        g2d.drawString(text, x + (w - fm.stringWidth(text)) / 2, ty);
    }

    static Color mix(Color a, Color b, float t) {
        t = Math.clamp(t, 0, 1);
        int r = Math.round(a.getRed() + (b.getRed() - a.getRed()) * t);
        int g = Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t);
        int bl = Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t);
        int al = Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t);
        return new Color(r, g, bl, al);
    }

    @Override public void setBounds(int x, int y, int width, int height) {
        if (!scaling) {
            baseX = x;
            baseY = y;
            baseWidth = width;
            baseHeight = height;
        }
        super.setBounds(x, y, width, height);
    }

    @Override public void setBounds(Rectangle r) {
        if (r == null) return;
        setBounds(r.x, r.y, r.width, r.height);
    }

    public void updateScale() {
        Container parent = getParent();
        if (parent == null) return;
        updateScale(parent.getWidth(), parent.getHeight());
    }

    public void updateScale(int parentWidth, int parentHeight) {
        if (baseWidth <= 0 || baseHeight <= 0 || parentWidth <= 0 || parentHeight <= 0) return;

        double scale;
        int offsetX = 0;
        int offsetY = 0;

        if (referenceScaling) {
            scale = Math.min((double) parentWidth / referenceWidth, (double) parentHeight / referenceHeight);
            offsetX = (parentWidth - (int) Math.round(referenceWidth * scale)) / 2;
            offsetY = (parentHeight - (int) Math.round(referenceHeight * scale)) / 2;
        } else {
            if (baseParentSize == null || baseParentSize.width <= 0 || baseParentSize.height <= 0) baseParentSize = new Dimension(parentWidth, parentHeight);
            scale = Math.min((double) parentWidth / baseParentSize.width, (double) parentHeight / baseParentSize.height);
        }

        scaling = true;
        super.setBounds(offsetX + (int) Math.round(baseX * scale), offsetY + (int) Math.round(baseY * scale),
                Math.max(1, (int) Math.round(baseWidth * scale)), Math.max(1, (int) Math.round(baseHeight * scale)));
        scaling = false;
    }

    public void setReferenceSize(int width, int height) {
        referenceWidth = Math.max(1, width);
        referenceHeight = Math.max(1, height);
        referenceScaling = true;
        updateScale();
    }

    public void clearReferenceSize() {
        referenceScaling = false;
        resetParentScaleReference();
        updateScale();
    }

    public boolean hasReferenceSize() { return referenceScaling; }

    public int getReferenceWidth() { return referenceWidth; }
    public int getReferenceHeight() { return referenceHeight; }
    public int getBaseX() { return baseX; }
    public int getBaseY() { return baseY; }
    public int getBaseWidth() { return baseWidth; }
    public int getBaseHeight() { return baseHeight; }
    public Rectangle getBaseBounds() { return new Rectangle(baseX, baseY, baseWidth, baseHeight); }
}