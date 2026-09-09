package StyleUI;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.*;

public abstract class AnimatedComponent extends JComponent {
    protected double animation, reflectionPhase;
    protected boolean mouseOver, mousePressed;
    private Dimension baseParentSize;
    private final Runnable animationTask;

    private int baseX, baseY, baseWidth, baseHeight;
    private int referenceWidth, referenceHeight;
    private boolean hasReferenceSize, scaling;

    AnimatedComponent() {
        hasReferenceSize = false;
        setOpaque(false);
        enableEvents(AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
        animationTask = () -> {
            double target = mouseOver || mousePressed ? 1.0 : 0.0;
            double old = animation;
            animation += (target - animation) * 0.22;
            if (Math.abs(animation - target) < 0.003) animation = target;
            boolean glass = getStyle() == Style.GLASS;
            if (glass) reflectionPhase = (reflectionPhase + 0.0035) % 1.0;
            if (old != animation || glass) repaint();
        };
    }

    AnimatedComponent(int referenceWidth, int referenceHeight) {
        this();
        this.referenceWidth = Math.max(1, referenceWidth);
        this.referenceHeight = Math.max(1, referenceHeight);
        hasReferenceSize = true;
    }
    @Override public void addNotify() {
        super.addNotify();
        AnimationManager.register(animationTask);
        if (hasReferenceSize) SwingUtilities.invokeLater(this::updateScale);
    }
    @Override public void removeNotify() {
        AnimationManager.unregister(animationTask);
        super.removeNotify();
    }
    protected abstract Style getStyle();
    protected void processMouseEvent(MouseEvent e) {
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
    protected void processMouseMotionEvent(MouseEvent e) {
        super.processMouseMotionEvent(e);
        repaint();
    }
    protected Graphics2D graphics(Graphics g) {
        Graphics2D g2d = (Graphics2D)g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g2d;
    }
    protected float parentScale() {
        if (hasReferenceSize) return 1f;
        Container parent = getParent();
        if (parent == null || parent.getWidth() <= 0 || parent.getHeight() <= 0) return 1f;
        if (baseParentSize == null || baseParentSize.width <= 0 || baseParentSize.height <= 0) baseParentSize = new Dimension(parent.getWidth(), parent.getHeight());
        float sx = parent.getWidth() / (float)baseParentSize.width;
        float sy = parent.getHeight() / (float)baseParentSize.height;
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
    static void paintGlassSheen(Graphics2D g2d, int w, int h, float arc, double phase) {
        if (w <= 0 || h <= 0) return;
        Graphics2D gc = (Graphics2D)g2d.create();
        gc.clip(new RoundRectangle2D.Float(0, 0, w, h, arc, arc));
        float band = Math.max(w, h) * 0.5f;
        float span = w + h + band * 2;
        float start = (float)(phase * span) - band - h;
        Point2D p1 = new Point2D.Float(start, 0);
        Point2D p2 = new Point2D.Float(start + band, h);
        float[] fractions = {0f, 0.5f, 1f};
        Color[] colors = {
                new Color(255, 255, 255, 0),
                new Color(255, 255, 255, Math.round(255 * 0.45f)),
                new Color(255, 255, 255, 0)};
        gc.setPaint(new LinearGradientPaint(p1, p2, fractions, colors, MultipleGradientPaint.CycleMethod.NO_CYCLE));
        gc.fillRoundRect(0, 0, w, h, Math.round(arc), Math.round(arc));
        gc.dispose();
    }
    static void drawCenteredText(Graphics2D g2d, String text, Color color, float size, int x, int y, int w, int h) {
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
        if (!hasReferenceSize) {
            super.setBounds(x, y, width, height);
            return;
        }
        if (!scaling) {
            baseX = x;
            baseY = y;
            baseWidth = width;
            baseHeight = height;
            updateScale();
            return;
        }
        super.setBounds(x, y, width, height);
    }

    @Override public void setBounds(Rectangle r) {
        if (r == null) return;
        setBounds(r.x, r.y, r.width, r.height);
    }

    public void updateScale() {
        if (!hasReferenceSize) {
            resetParentScaleReference();
            revalidate();
            repaint();
            return;
        }
        Container parent = getParent();
        if (parent == null) return;
        updateScale(parent.getWidth(), parent.getHeight());
    }

    public void updateScale(int parentWidth, int parentHeight) {
        if (!hasReferenceSize) {
            if (parentWidth > 0 && parentHeight > 0) {
                baseParentSize = new Dimension(parentWidth, parentHeight);
                revalidate();
                repaint();
            }
            return;
        }
        if (baseWidth <= 0 || baseHeight <= 0 || parentWidth <= 0 || parentHeight <= 0) return;

        double scale = Math.min((double)parentWidth / referenceWidth, (double)parentHeight / referenceHeight);
        int scaledReferenceWidth = (int)Math.round(referenceWidth * scale);
        int scaledReferenceHeight = (int)Math.round(referenceHeight * scale);
        int offsetX = (parentWidth - scaledReferenceWidth) / 2;
        int offsetY = (parentHeight - scaledReferenceHeight) / 2;

        int x = offsetX + (int)Math.round(baseX * scale);
        int y = offsetY + (int)Math.round(baseY * scale);
        int width = Math.max(1, (int)Math.round(baseWidth * scale));
        int height = Math.max(1, (int)Math.round(baseHeight * scale));

        scaling = true;
        super.setBounds(x, y, width, height);
        scaling = false;
    }

    public void setReferenceSize(int width, int height) {
        referenceWidth = Math.max(1, width);
        referenceHeight = Math.max(1, height);
        hasReferenceSize = true;
        updateScale();
    }

    public boolean hasReferenceSize() { return hasReferenceSize; }
    public int getReferenceWidth() { return referenceWidth; }
    public int getReferenceHeight() { return referenceHeight; }
    public int getBaseX() { return baseX; }
    public int getBaseY() { return baseY; }
    public int getBaseWidth() { return baseWidth; }
    public int getBaseHeight() { return baseHeight; }
    public Rectangle getBaseBounds() { return new Rectangle(baseX, baseY, baseWidth, baseHeight); }
}