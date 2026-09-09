package StyleUI;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;

public class Animated3DText extends JComponent {
    public enum AnimationType { NONE, PULSE, ROTATE, PULSE_ROTATE, BOUNCE, FULL }
    private AnimationType animationType = AnimationType.PULSE_ROTATE;
    private double animationSpeed = 1.0, time = 0.0, pulseAmount = 0.06, rotationAmount = 0.05, bounceAmount = 0.04;
    private int depth = 8;

    private String text;
    private String fontName = "Arial";
    private int fontStyle = Font.BOLD;
    private float baseFontSize = 55f, minFontSize = 1f, maxFontSize = 1000f;
    private Color textColor = Color.WHITE, depthColor = new Color(120, 180, 255, 80);

    private boolean autoScale = true;
    private double currentScale = 1.0, currentRotation = 0.0, currentOffsetY = 0.0;
    private double targetScale = 1.0, targetRotation = 0.0, targetOffsetY = 0.0;
    private double interpolation = 0.12;

    private int baseX, baseY, baseWidth, baseHeight;
    private int referenceWidth, referenceHeight;
    private boolean hasReferenceSize;
    private boolean scaling;

    private final Runnable animationTask;
    private final java.util.List<ActionListener> actionListeners = new ArrayList<>();

    public Animated3DText(String text) {
        this.text = text == null ? "" : text;
        hasReferenceSize = false;
        setOpaque(false);
        setBackground(new Color(0, 0, 0, 0));

        animationTask = this::updateAnimation;
        addMouseListener(new MouseAdapter() { @Override public void mouseClicked(MouseEvent e) { if (SwingUtilities.isLeftMouseButton(e)) fireActionPerformed(); }});

        LocalizationBridge.bind(this, this.text, this::applyLocalizedText);
    }
    public Animated3DText(String text, AnimationType animationType) {
        this(text);
        setAnimationType(animationType);
    }
    public Animated3DText(String text, int referenceWidth, int referenceHeight) {
        this(text);
        setReferenceSize(referenceWidth, referenceHeight);
    }
    public Animated3DText(String text, AnimationType animationType, int referenceWidth, int referenceHeight) {
        this(text, animationType);
        setReferenceSize(referenceWidth, referenceHeight);
    }

    private void applyLocalizedText(String localizedText) {
        if (localizedText != null) {
            this.text = localizedText;
            repaint();
        }
    }
    public void setLocalizationKey(String key) {
        LocalizationBridge.unbind(this);
        this.text = key == null ? "" : key;
        LocalizationBridge.bind(this, this.text, this::applyLocalizedText);
        repaint();
    }

    private void updateAnimation() {
        if (animationType == AnimationType.NONE || animationSpeed <= 0) {
            targetScale = 1.0;
            targetRotation = 0.0;
            targetOffsetY = 0.0;
        } else {
            double t = time;
            switch (animationType) {
                case PULSE:
                    targetScale = 1.0 + Math.sin(t) * pulseAmount;
                    targetRotation = 0.0;
                    targetOffsetY = 0.0;
                    break;
                case ROTATE:
                    targetScale = 1.0;
                    targetRotation = Math.sin(t * 0.7) * rotationAmount;
                    targetOffsetY = 0.0;
                    break;
                case PULSE_ROTATE:
                    targetScale = 1.0 + Math.sin(t) * pulseAmount;
                    targetRotation = Math.sin(t * 0.7) * rotationAmount;
                    targetOffsetY = 0.0;
                    break;
                case BOUNCE:
                    targetScale = 1.0;
                    targetRotation = 0.0;
                    targetOffsetY = Math.sin(t * 1.5) * getHeight() * bounceAmount;
                    break;
                case FULL:
                    targetScale = 1.0 + Math.sin(t) * pulseAmount;
                    targetRotation = Math.sin(t * 0.7) * rotationAmount;
                    targetOffsetY = Math.sin(t * 1.5) * getHeight() * bounceAmount;
                    break;
                case NONE: break;
            }
            time += 0.05 * animationSpeed;
        }

        currentScale = lerp(currentScale, targetScale, interpolation);
        currentRotation = lerp(currentRotation, targetRotation, interpolation);
        currentOffsetY = lerp(currentOffsetY, targetOffsetY, interpolation);
        repaint();
    }

    private double lerp(double current, double target, double amount) { return current + (target - current) * amount; }
    @Override public void addNotify() {
        super.addNotify();
        if (animationSpeed > 0) AnimationManager.register(animationTask);
        if (hasReferenceSize) SwingUtilities.invokeLater(this::updateScale);
        revalidate();
        repaint();
    }
    @Override public void removeNotify() {
        AnimationManager.unregister(animationTask);
        LocalizationBridge.unbind(this);
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

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (text == null || text.isEmpty()) return;

        int width = getWidth(), height = getHeight();
        if (width <= 0 || height <= 0) return;

        Graphics2D g2d = (Graphics2D)g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        g2d.setFont(calculateFont(width, height));
        FontMetrics fm = g2d.getFontMetrics();

        AffineTransform old = g2d.getTransform();
        g2d.translate((width / 2.0), (height / 2.0) + currentOffsetY);
        g2d.rotate(currentRotation);
        g2d.scale(currentScale, currentScale);

        int x = -fm.stringWidth(text) / 2, y = (int)(fm.getAscent() * 0.35);

        for (int i = depth; i >= 1; i--) {
            g2d.setColor(depthColor);
            g2d.drawString(text, (float)(x - i * 0.5), y + i);
        }

        g2d.setColor(textColor);
        g2d.drawString(text, x, y);
        g2d.setTransform(old);
        g2d.dispose();
    }

    private Font calculateFont(int width, int height) {
        float requestedSize = Math.max(1f, baseFontSize);
        Font referenceFont = new Font(fontName, fontStyle, Math.max(1, Math.round(requestedSize)));

        FontMetrics fm = getFontMetrics(referenceFont);
        int textWidth = Math.max(1, fm.stringWidth(text));
        int textHeight = Math.max(1, fm.getHeight());

        if (!autoScale) return referenceFont;

        double maxPulse = 1.0 + Math.abs(pulseAmount);
        double sinRotation = Math.abs(Math.sin(Math.abs(rotationAmount)));
        double cosRotation = Math.abs(Math.cos(Math.abs(rotationAmount)));

        double rotatedWidth = textWidth * cosRotation + textHeight * sinRotation * maxPulse;
        double rotatedHeight = textWidth * sinRotation + textHeight * cosRotation * maxPulse;

        double scale = Math.min(Math.max(1, width - depth * 2 + 12) / Math.max(1.0, rotatedWidth),
                Math.max(1, height - depth * 2 + 12 - Math.abs(getHeight() * bounceAmount) * 2.0) / Math.max(1.0, rotatedHeight));

        float finalSize = (float)(requestedSize * scale);
        finalSize = Math.min(maxFontSize, Math.max(minFontSize, finalSize));

        return new Font(fontName, fontStyle, Math.max(1, Math.round(finalSize)));
    }

    @Override public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(new Font(fontName, fontStyle, Math.max(1, Math.round(baseFontSize))));
        return new Dimension(Math.max(32, fm.stringWidth(text) + depth * 2 + 20), Math.max(32, fm.getHeight() + depth * 2 + 20));
    }
    @Override public Dimension getMinimumSize() { return new Dimension(1, 1); }

    public String getText() { return text; }
    public void setText(String text) {
        this.text = text == null ? "" : text;
        if (!LocalizationBridge.isAvailable()) {
            revalidate();
            repaint();
        }
    }

    public AnimationType getAnimationType() { return animationType; }
    public void setAnimationType(AnimationType animationType) {
        this.animationType = animationType == null ? AnimationType.NONE : animationType;
        repaint();
    }

    public double getAnimationSpeed() { return animationSpeed; }
    public void setAnimationSpeed(double animationSpeed) { this.animationSpeed = Math.max(0.0, animationSpeed); }

    public float getBaseFontSize() { return baseFontSize; }
    public void setBaseFontSize(float baseFontSize) {
        this.baseFontSize = Math.max(1f, baseFontSize);
        revalidate();
        repaint();
    }

    public String getFontName() { return fontName; }
    public void setFontName(String fontName) {
        if (fontName == null || fontName.isEmpty()) return;
        this.fontName = fontName;
        revalidate();
        repaint();
    }

    public int getFontStyle() { return fontStyle; }
    public void setFontStyle(int fontStyle) {
        this.fontStyle = fontStyle;
        revalidate();
        repaint();
    }

    public Font getBaseFont() { return new Font(fontName, fontStyle, Math.max(1, Math.round(baseFontSize))); }
    public void setBaseFont(Font font) {
        if (font == null) return;
        this.fontName = font.getName();
        this.fontStyle = font.getStyle();
        this.baseFontSize = font.getSize2D();
        revalidate();
        repaint();
    }

    @Override public Font getFont() { return getBaseFont(); }
    @Override public void setFont(Font font) { setBaseFont(font); }

    public Color getTextColor() { return textColor; }
    public void setTextColor(Color color) {
        if (color == null) return;
        textColor = color;
        repaint();
    }

    public Color getDepthColor() { return depthColor; }
    public void setDepthColor(Color color) {
        if (color == null) return;
        depthColor = color;
        repaint();
    }

    public int getDepth() { return depth; }
    public void setDepth(int depth) {
        this.depth = Math.max(0, depth);
        revalidate();
        repaint();
    }

    public double getPulseAmount() { return pulseAmount; }
    public void setPulseAmount(double pulseAmount) {
        this.pulseAmount = Math.max(0.0, pulseAmount);
        repaint();
    }

    public double getRotationAmount() { return rotationAmount; }
    public void setRotationAmount(double rotationAmount) {
        this.rotationAmount = Math.max(0.0, rotationAmount);
        repaint();
    }

    public double getBounceAmount() { return bounceAmount; }
    public void setBounceAmount(double bounceAmount) {
        this.bounceAmount = Math.max(0.0, bounceAmount);
        repaint();
    }

    public boolean isAutoScale() { return autoScale; }
    public void setAutoScale(boolean autoScale) {
        this.autoScale = autoScale;
        revalidate();
        repaint();
    }

    public double getInterpolation() { return interpolation; }
    public void setInterpolation(double interpolation) { this.interpolation = Math.clamp(interpolation, 0.001, 1.0); }

    public float getMinFontSize() { return minFontSize; }
    public void setMinFontSize(float minFontSize) {
        this.minFontSize = Math.max(1f, minFontSize);
        revalidate();
        repaint();
    }

    public float getMaxFontSize() { return maxFontSize; }
    public void setMaxFontSize(float maxFontSize) {
        this.maxFontSize = maxFontSize <= 0 ? 1000f : maxFontSize;
        revalidate();
        repaint();
    }

    public void addActionListener(ActionListener listener) { if (listener != null && !actionListeners.contains(listener)) actionListeners.add(listener); }
    public void removeActionListener(ActionListener listener) { actionListeners.remove(listener); }

    private void fireActionPerformed() {
        ActionEvent event = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, text);
        for (ActionListener listener : new ArrayList<>(actionListeners)) listener.actionPerformed(event);
    }
}