package StyleUI;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;

public class StyledTextField extends JTextField {
    private Style style;
    private double reflectionPhase;

    private int baseX, baseY, baseWidth, baseHeight;
    private int referenceWidth, referenceHeight;
    private boolean hasReferenceSize;
    private boolean scaling;

    private final Runnable reflectionTask;
    private boolean localizationReady;

    public StyledTextField(Style style, String text) {
        super(text);
        this.style = style;
        hasReferenceSize = false;

        setForeground(style.text);
        setCaretColor(style.text);
        setOpaque(false);
        setHorizontalAlignment(LEFT);
        setBorder(new EmptyBorder(3, 8, 3, 8));

        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                updateFont();
                repaint();
            }
        });

        reflectionTask = () -> {
            if (style == Style.GLASS) {
                reflectionPhase = (reflectionPhase + 0.0035) % 1.0;
                repaint();
            }
        };

        updateFont();
        localizationReady = true;
        LocalizationBridge.bind(this, text, this::applyLocalizedText);
    }

    public StyledTextField(Style style, String text, int referenceWidth, int referenceHeight) {
        this(style, text);
        setReferenceSize(referenceWidth, referenceHeight);
    }


    @Override public void setText(String text) {
        if (localizationReady) LocalizationBridge.externalTextChanged(this, text, value -> StyledTextField.super.setText(value == null ? "" : value));
        else super.setText(text == null ? "" : text);
    }

    private void applyLocalizedText(String text) {
        super.setText(text == null ? "" : text);
        revalidate();
        repaint();
    }

    @Override public void addNotify() {
        super.addNotify();
        if (style == Style.GLASS) AnimationManager.register(reflectionTask);
        if (hasReferenceSize) SwingUtilities.invokeLater(this::updateScale);
    }

    @Override public void removeNotify() {
        AnimationManager.unregister(reflectionTask);
        super.removeNotify();
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
            repaint();
            return;
        }
        Container parent = getParent();
        if (parent == null) return;
        updateScale(parent.getWidth(), parent.getHeight());
    }

    public void updateScale(int parentWidth, int parentHeight) {
        if (!hasReferenceSize) {
            repaint();
            return;
        }
        if (baseWidth <= 0 || baseHeight <= 0 || parentWidth <= 0 || parentHeight <= 0) return;

        double scale = Math.min((double)parentWidth / referenceWidth, (double)parentHeight / referenceHeight);
        int scaledWidth = (int)Math.round(referenceWidth * scale);
        int scaledHeight = (int)Math.round(referenceHeight * scale);
        int offsetX = (parentWidth - scaledWidth) / 2;
        int offsetY = (parentHeight - scaledHeight) / 2;

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

    public void setStyle(Style style) {
        if (style == null || style == this.style) return;
        this.style = style;
        AnimationManager.unregister(reflectionTask);
        if (style == Style.GLASS && isDisplayable()) AnimationManager.register(reflectionTask);
        setForeground(style.text);
        setCaretColor(style.text);
        repaint();
    }

    private void updateFont() {
        setFont(new Font("SansSerif", Font.PLAIN, Math.round(Math.clamp(getHeight() * 0.34f, 9, 40))));
    }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D)g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth(), h = getHeight();
        float arc = Math.clamp(Math.min(w, h) * 0.22f, 4, 30);

        g2d.setColor(style.panel);
        g2d.fillRoundRect(0, 0, w, h, Math.round(arc), Math.round(arc));

        if (style == Style.GLASS) {
            g2d.setColor(new Color(255, 255, 255, 22));
            g2d.fillRoundRect(0, 0, w, h, Math.round(arc), Math.round(arc));
            paintGlassSheen(g2d, w, h, arc, reflectionPhase);
            g2d.setColor(new Color(255, 255, 255, 90));
            g2d.drawRoundRect(0, 0, w - 1, h - 1, Math.round(arc), Math.round(arc));
        } else {
            g2d.setColor(style.field);
            g2d.fillRoundRect(0, 0, w, h, Math.round(arc), Math.round(arc));
            g2d.setColor(style.accent);
            g2d.drawRoundRect(0, 0, w - 1, h - 1, Math.round(arc), Math.round(arc));
        }

        g2d.dispose();
        super.paintComponent(g);
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
}