package StyleUI;

import javax.swing.*;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;

public class StyledScrollPane extends JScrollPane {
    private Style style;

    private Color panelColor, borderColor, trackColor, thumbColor, thumbHoverColor;

    private int arc = 16;
    private int thumbArc = 8;
    private int thumbThickness = 10;
    private int thumbInset = 3;

    private boolean glass;
    private float glassOpacity = 0.45f;

    private double reflectionPhase;
    private final Runnable reflectionTask;

    public StyledScrollPane(Component view, int vsbPolicy, int hsbPolicy, Style style) {
        super(view, vsbPolicy, hsbPolicy);
        this.style = style;

        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        setViewportBorder(BorderFactory.createEmptyBorder());

        getViewport().setOpaque(false);
        if (view instanceof JComponent jc) jc.setOpaque(false);

        applyStyleColors(style);

        installScrollBar(getVerticalScrollBar(), true);
        installScrollBar(getHorizontalScrollBar(), false);

        getVerticalScrollBar().setUnitIncrement(16);
        getHorizontalScrollBar().setUnitIncrement(16);

        reflectionTask = () -> {
            if (this.style != Style.GLASS) return;
            reflectionPhase = (reflectionPhase + 0.0035) % 1.0;
            repaint();
        };
    }

    public StyledScrollPane(Component view, Style style) { this(view, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_AS_NEEDED, style); }
    public StyledScrollPane(Component view) { this(view, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_AS_NEEDED, Style.FLAT); }

    @Override public void addNotify() {
        super.addNotify();
        if (glass) AnimationManager.register(reflectionTask);
        startScrollBarAnimations();
    }
    @Override public void removeNotify() {
        AnimationManager.unregister(reflectionTask);
        stopScrollBarAnimations();
        super.removeNotify();
    }
    private void startScrollBarAnimations() {
        startScrollBarAnimation(getVerticalScrollBar());
        startScrollBarAnimation(getHorizontalScrollBar());
    }
    private void stopScrollBarAnimations() {
        stopScrollBarAnimation(getVerticalScrollBar());
        stopScrollBarAnimation(getHorizontalScrollBar());
    }
    private void startScrollBarAnimation(JScrollBar bar) {
        if (bar != null && bar.getUI() instanceof StyledScrollBarUI ui) ui.startAnimation();
    }
    private void stopScrollBarAnimation(JScrollBar bar) {
        if (bar != null && bar.getUI() instanceof StyledScrollBarUI ui) ui.stopAnimation();
    }

    private void installScrollBar(JScrollBar bar, boolean vertical) {
        bar.setUI(new StyledScrollBarUI(this));
        bar.setOpaque(false);
        bar.setUnitIncrement(16);
        bar.setPreferredSize(vertical ? new Dimension(thumbThickness + thumbInset * 2, 0) : new Dimension(0, thumbThickness + thumbInset * 2));
    }
    private void applyStyleColors(Style style) {
        if (style == Style.GLASS) {
            glass = true;
            panelColor = new Color(255, 255, 255, 22);
            borderColor = new Color(255, 255, 255, 60);
            trackColor = new Color(255, 255, 255, 18);
            thumbColor = new Color(255, 255, 255, 110);
            thumbHoverColor = new Color(255, 255, 255, 190);
        }
        else {
            glass = false;
            panelColor = style.panel;
            borderColor = style.accent;
            trackColor = style.field;
            thumbColor = style.accent;
            thumbHoverColor = style.hover;
        }
    }

    public Style getStyleValue() { return style; }
    public void setStyle(Style style) {
        if (style == null || style == this.style) return;
        this.style = style;
        applyStyleColors(style);
        AnimationManager.unregister(reflectionTask);
        if (glass && isDisplayable()) AnimationManager.register(reflectionTask);
        getVerticalScrollBar().repaint();
        getHorizontalScrollBar().repaint();
        repaint();
    }

    public boolean isGlass() { return glass; }

    public int getArc() { return arc; }
    public void setArc(int arc) { this.arc = Math.max(0, arc); repaint(); }

    public int getThumbArc() { return thumbArc; }
    public void setThumbArc(int arc) { thumbArc = Math.max(0, arc); repaint(); }

    public int getThumbThickness() { return thumbThickness; }
    public void setThumbThickness(int thickness) {
        thumbThickness = Math.max(2, thickness);
        getVerticalScrollBar().setPreferredSize(new Dimension(thumbThickness + thumbInset * 2, 0));
        getHorizontalScrollBar().setPreferredSize(new Dimension(0, thumbThickness + thumbInset * 2));
        revalidate();
        repaint();
    }

    public Color getPanelColor() { return panelColor; }
    public void setPanelColor(Color color) { if (color != null) { panelColor = color; repaint(); } }

    public Color getBorderColor() { return borderColor; }
    public void setBorderColor(Color color) { if (color != null) { borderColor = color; repaint(); } }

    public Color getTrackColor() { return trackColor; }
    public void setTrackColor(Color color) { if (color != null) { trackColor = color; getVerticalScrollBar().repaint(); getHorizontalScrollBar().repaint(); } }

    public Color getThumbColor() { return thumbColor; }
    public void setThumbColor(Color color) { if (color != null) { thumbColor = color; getVerticalScrollBar().repaint(); getHorizontalScrollBar().repaint(); } }

    public Color getThumbHoverColor() { return thumbHoverColor; }
    public void setThumbHoverColor(Color color) { if (color != null) { thumbHoverColor = color; getVerticalScrollBar().repaint(); getHorizontalScrollBar().repaint(); } }

    double getReflectionPhase() { return reflectionPhase; }

    static Color mix(Color a, Color b, float t) {
        t = Math.clamp(t, 0, 1);
        int r = Math.round(a.getRed() + (b.getRed() - a.getRed()) * t);
        int g = Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t);
        int bl = Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t);
        int al = Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t);
        return new Color(r, g, bl, al);
    }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        if (w > 0 && h > 0) {
            float currentArc = Math.min(arc, Math.min(w, h) * 0.3f);
            g2d.setColor(panelColor);
            g2d.fillRoundRect(0, 0, w, h, Math.round(currentArc), Math.round(currentArc));
            if (glass) {
                Graphics2D sheen = (Graphics2D) g2d.create();
                sheen.clip(new RoundRectangle2D.Float(0, 0, w, h, currentArc, currentArc));
                AnimatedComponent.paintGlassSheen(sheen, w, h, currentArc, reflectionPhase);
                sheen.dispose();
            }
            g2d.setColor(borderColor);
            g2d.drawRoundRect(0, 0, w - 1, h - 1, Math.round(currentArc), Math.round(currentArc));
        }
        g2d.dispose();
        super.paintComponent(g);
    }
    private static class StyledScrollBarUI extends BasicScrollBarUI {
        private final StyledScrollPane pane;
        private boolean thumbHover, thumbPressed;
        private double animation;
        private Runnable animationTask;

        StyledScrollBarUI(StyledScrollPane pane) { this.pane = pane; }

        @Override protected void configureScrollBarColors() { }
        @Override protected void installDefaults() {
            super.installDefaults();
            scrollbar.setOpaque(false);
        }
        @Override protected void installListeners() {
            super.installListeners();
            animationTask = () -> {
                double target = thumbHover || thumbPressed ? 1.0 : 0.0;
                double old = animation;
                animation += (target - animation) * 0.25;
                if (Math.abs(animation - target) < 0.01) animation = target;
                boolean glassAnim = pane.isGlass();
                if (old != animation || glassAnim) scrollbar.repaint();
            };
            if (scrollbar.isDisplayable()) AnimationManager.register(animationTask);
            scrollbar.addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { thumbHover = true; }
                @Override public void mouseExited(MouseEvent e) { thumbHover = false; thumbPressed = false; }
                @Override public void mousePressed(MouseEvent e) { thumbPressed = true; }
                @Override public void mouseReleased(MouseEvent e) { thumbPressed = false; }
            });
        }
        void startAnimation() {
            if (animationTask != null) AnimationManager.register(animationTask);
        }
        void stopAnimation() {
            if (animationTask != null) AnimationManager.unregister(animationTask);
        }
        @Override protected void uninstallListeners() {
            stopAnimation();
            super.uninstallListeners();
        }
        @Override protected JButton createDecreaseButton(int orientation) { return zeroButton(); }
        @Override protected JButton createIncreaseButton(int orientation) { return zeroButton(); }
        private JButton zeroButton() {
            JButton button = new JButton();
            button.setPreferredSize(new Dimension(0, 0));
            button.setMinimumSize(new Dimension(0, 0));
            button.setMaximumSize(new Dimension(0, 0));
            button.setFocusable(false);
            button.setOpaque(false);
            button.setBorder(null);
            button.setContentAreaFilled(false);
            return button;
        }
        @Override protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
            if (trackBounds.isEmpty()) return;
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int arc = Math.min(trackBounds.width, trackBounds.height);
            g2d.setColor(pane.getTrackColor());
            g2d.fillRoundRect(trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height, arc, arc);
            g2d.dispose();
        }
        @Override protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
            if (thumbBounds.isEmpty() || !scrollbar.isEnabled()) return;
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int pad = 2;
            int x = thumbBounds.x + pad, y = thumbBounds.y + pad;
            int w = Math.max(1, thumbBounds.width - pad * 2);
            int h = Math.max(1, thumbBounds.height - pad * 2);
            int arc = Math.max(pane.getThumbArc(), Math.min(w, h));

            g2d.setColor(mix(pane.getThumbColor(), pane.getThumbHoverColor(), (float) animation));
            g2d.fillRoundRect(x, y, w, h, arc, arc);

            if (pane.isGlass()) {
                Graphics2D sheen = (Graphics2D) g2d.create();
                sheen.clip(new RoundRectangle2D.Float(x, y, w, h, arc, arc));
                sheen.translate(x, y);
                AnimatedComponent.paintGlassSheen(sheen, w, h, arc, pane.getReflectionPhase());
                sheen.dispose();
            }
            g2d.dispose();
        }
        @Override protected Dimension getMinimumThumbSize() {return scrollbar.getOrientation() == JScrollBar.VERTICAL ? new Dimension(pane.getThumbThickness(), 24) : new Dimension(24, pane.getThumbThickness());}
    }
}