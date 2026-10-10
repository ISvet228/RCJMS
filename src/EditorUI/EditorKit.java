package EditorUI;

import StyleUI.Style;
import Helpers.NSLocalizedString;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public final class EditorKit {
    private EditorKit() {}

    //region Colors & Text
    public static final Color BG = new Color(28, 28, 32), CARD = new Color(36, 36, 42), CARD_LINE = new Color(60, 60, 68), CANVAS_BG = new Color(18, 18, 21);
    public static final Color TEXT = new Color(238, 238, 244), MUTED = new Color(164, 164, 178), HILITE = new Color(96, 165, 250);
    public static final Color OK = new Color(88, 204, 128), WARN = new Color(245, 185, 66), BAD = new Color(240, 104, 104);

    static {
        UIManager.put("ToolTip.background", new Color(30, 30, 36));
        UIManager.put("ToolTip.foreground", TEXT);
        UIManager.put("ToolTip.border", BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(CARD_LINE), new EmptyBorder(3, 6, 3, 6)));
    }

    public static String L(String key) { return NSLocalizedString.localized(key); }
    public static String fmt(String key, Object... args) {
        try { return String.format(L(key), args); }
        catch (RuntimeException e) { return L(key); }
    }
    public static Color mix(Color a, Color b, float t) {
        t = Math.clamp(t, 0, 1);
        return new Color(Math.round(a.getRed() + (b.getRed() - a.getRed()) * t), Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t), Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t));
    }
    public static Color alpha(Color c, int a) { return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.clamp(a, 0, 255)); }
    public static Graphics2D smooth(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g2d;
    }
    public static Font font(float size, boolean bold) { return new Font(Font.SANS_SERIF, bold ? Font.BOLD : Font.PLAIN, 12).deriveFont(size); }
    public static String ellipsize(String text, FontMetrics fm, int maxWidth) {
        if (fm.stringWidth(text) <= maxWidth) return text;
        String dots = "…";
        int end = text.length();
        while (end > 0 && fm.stringWidth(text.substring(0, end) + dots) > maxWidth) end--;
        return end <= 0 ? "" : text.substring(0, end).stripTrailing() + dots;
    }
    public static List<String> wrap(String text, FontMetrics fm, int width) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (!line.isEmpty() && fm.stringWidth(candidate) > width) { lines.add(line.toString()); line = new StringBuilder(word); }
                else line = new StringBuilder(candidate);
            }
            lines.add(line.toString());
        }
        return lines;
    }
    public static void line(int x0, int y0, int x1, int y1, BiConsumer<Integer, Integer> action) {
        int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0), sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1, err = dx + dy;
        while (true) {
            action.accept(x0, y0);
            if (x0 == x1 && y0 == y1) return;
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x0 += sx; }
            if (e2 <= dx) { err += dx; y0 += sy; }
        }
    }
    //endregion

    public record Theme(Color normal, Color hover, Color text, Color accent, Color field) {
        public static Theme of(Style style) {
            return switch (style) {
                case NEUMORPHIC -> new Theme(new Color(61, 63, 66), new Color(70, 72, 75), new Color(220, 220, 220), new Color(82, 84, 87), new Color(36, 38, 41));
                case GLASS -> new Theme(new Color(255, 255, 255, 28), new Color(255, 255, 255, 55), Color.WHITE, new Color(255, 255, 255, 90), new Color(255, 255, 255, 22));
                default -> new Theme(new Color(57, 79, 124), new Color(72, 99, 151), new Color(225, 225, 230), new Color(71, 73, 79), new Color(25, 26, 29));
            };
        }
    }
    public enum Glyph {
        BRUSH, ERASER, FILL, PICKER, BOX, PORTAL, PORTAL_ONE, UNDO, REDO, ZOOM_IN, ZOOM_OUT, FIT, BACK, SAVE, PLAY, PLUS, MINUS, TRASH, FOLDER,
        DICE, HELP, FLAG, UP, DOWN, IMPORT, CHECK, WARN, NEW, ARROW, WALL, PATH;

        public void paint(Graphics2D g2d, double x, double y, double size, Color color) {
            Graphics2D g2dCopy = (Graphics2D) g2d.create();
            g2dCopy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2dCopy.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2dCopy.translate(x, y);
            g2dCopy.scale(size, size);
            g2dCopy.setColor(color);
            g2dCopy.setStroke(new BasicStroke(0.09f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            switch (this) {
                case BRUSH -> {
                    g2dCopy.setStroke(new BasicStroke(0.14f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2dCopy.draw(new Line2D.Double(0.82, 0.14, 0.50, 0.46));
                    Path2D tip = new Path2D.Double();
                    tip.moveTo(0.46, 0.42); tip.curveTo(0.30, 0.40, 0.14, 0.52, 0.14, 0.74);
                    tip.curveTo(0.14, 0.84, 0.18, 0.88, 0.28, 0.88); tip.curveTo(0.52, 0.88, 0.62, 0.70, 0.58, 0.54);
                    tip.closePath();
                    g2dCopy.fill(tip);
                }
                case ERASER -> {
                    AffineTransform old = g2dCopy.getTransform();
                    g2dCopy.rotate(-Math.PI / 4, 0.5, 0.5);
                    g2dCopy.draw(new RoundRectangle2D.Double(0.2, 0.34, 0.6, 0.32, 0.08, 0.08));
                    g2dCopy.fill(new Rectangle2D.Double(0.2, 0.34, 0.26, 0.32));
                    g2dCopy.setTransform(old);
                    g2dCopy.draw(new Line2D.Double(0.14, 0.9, 0.86, 0.9));
                }
                case FILL -> {
                    Path2D bucket = new Path2D.Double();
                    bucket.moveTo(0.12, 0.46); bucket.lineTo(0.42, 0.16); bucket.lineTo(0.74, 0.48); bucket.lineTo(0.44, 0.78); bucket.closePath();
                    g2dCopy.draw(bucket);
                    Path2D half = new Path2D.Double();
                    half.moveTo(0.12, 0.46); half.lineTo(0.74, 0.48); half.lineTo(0.44, 0.78); half.closePath();
                    g2dCopy.fill(half);
                    g2dCopy.fill(new Ellipse2D.Double(0.78, 0.64, 0.13, 0.18));
                }
                case PICKER -> {
                    g2dCopy.setStroke(new BasicStroke(0.13f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2dCopy.draw(new Line2D.Double(0.30, 0.70, 0.64, 0.36));
                    g2dCopy.fill(new Ellipse2D.Double(0.58, 0.10, 0.32, 0.32));
                    Path2D tip = new Path2D.Double();
                    tip.moveTo(0.20, 0.68); tip.lineTo(0.32, 0.80); tip.lineTo(0.12, 0.90); tip.closePath();
                    g2dCopy.fill(tip);
                }
                case BOX -> {
                    g2dCopy.setStroke(new BasicStroke(0.08f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{0.12f, 0.09f}, 0f));
                    g2dCopy.draw(new Rectangle2D.Double(0.18, 0.24, 0.64, 0.52));
                    for (double[] c : new double[][]{{0.18, 0.24}, {0.82, 0.24}, {0.18, 0.76}, {0.82, 0.76}}) g2dCopy.fill(new Rectangle2D.Double(c[0] - 0.07, c[1] - 0.07, 0.14, 0.14));
                }
                case PORTAL -> {
                    g2dCopy.draw(new Ellipse2D.Double(0.26, 0.10, 0.48, 0.80));
                    g2dCopy.fill(new Ellipse2D.Double(0.39, 0.30, 0.22, 0.40));
                }
                case PORTAL_ONE -> {
                    g2dCopy.draw(new Ellipse2D.Double(0.10, 0.12, 0.36, 0.76));
                    g2dCopy.draw(new Line2D.Double(0.56, 0.5, 0.86, 0.5));
                    Path2D head = new Path2D.Double();
                    head.moveTo(0.76, 0.36); head.lineTo(0.92, 0.5); head.lineTo(0.76, 0.64); head.closePath();
                    g2dCopy.fill(head);
                }
                case UNDO, REDO -> {
                    if (this == REDO) { g2dCopy.translate(1, 0); g2dCopy.scale(-1, 1); }
                    g2dCopy.draw(new Arc2D.Double(0.2, 0.25, 0.6, 0.6, 15, 165, Arc2D.OPEN));
                    Path2D head = new Path2D.Double();
                    head.moveTo(0.06, 0.50); head.lineTo(0.34, 0.50); head.lineTo(0.20, 0.74); head.closePath();
                    g2dCopy.fill(head);
                }
                case ZOOM_IN, ZOOM_OUT -> {
                    g2dCopy.draw(new Ellipse2D.Double(0.10, 0.10, 0.55, 0.55));
                    g2dCopy.setStroke(new BasicStroke(0.12f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2dCopy.draw(new Line2D.Double(0.58, 0.58, 0.88, 0.88));
                    g2dCopy.setStroke(new BasicStroke(0.08f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2dCopy.draw(new Line2D.Double(0.26, 0.375, 0.49, 0.375));
                    if (this == ZOOM_IN) g2dCopy.draw(new Line2D.Double(0.375, 0.26, 0.375, 0.49));
                }
                case FIT -> {
                    double[][][] corners = {{{0.15, 0.38}, {0.15, 0.15}, {0.38, 0.15}}, {{0.62, 0.15}, {0.85, 0.15}, {0.85, 0.38}},
                            {{0.15, 0.62}, {0.15, 0.85}, {0.38, 0.85}}, {{0.62, 0.85}, {0.85, 0.85}, {0.85, 0.62}}};
                    for (double[][] c : corners) {
                        Path2D p = new Path2D.Double();
                        p.moveTo(c[0][0], c[0][1]); p.lineTo(c[1][0], c[1][1]); p.lineTo(c[2][0], c[2][1]);
                        g2dCopy.draw(p);
                    }
                }
                case BACK -> {
                    g2dCopy.setStroke(new BasicStroke(0.12f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2dCopy.draw(new Line2D.Double(0.84, 0.5, 0.2, 0.5));
                    Path2D head = new Path2D.Double();
                    head.moveTo(0.46, 0.24); head.lineTo(0.2, 0.5); head.lineTo(0.46, 0.76);
                    g2dCopy.draw(head);
                }
                case SAVE -> {
                    g2dCopy.draw(new RoundRectangle2D.Double(0.14, 0.14, 0.72, 0.72, 0.1, 0.1));
                    g2dCopy.draw(new Rectangle2D.Double(0.30, 0.14, 0.36, 0.24));
                    g2dCopy.fill(new RoundRectangle2D.Double(0.28, 0.54, 0.44, 0.32, 0.04, 0.04));
                }
                case PLAY -> {
                    Path2D tri = new Path2D.Double();
                    tri.moveTo(0.28, 0.14); tri.lineTo(0.86, 0.5); tri.lineTo(0.28, 0.86); tri.closePath();
                    g2dCopy.fill(tri);
                }
                case PLUS, MINUS -> {
                    g2dCopy.setStroke(new BasicStroke(0.13f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2dCopy.draw(new Line2D.Double(0.22, 0.5, 0.78, 0.5));
                    if (this == PLUS) g2dCopy.draw(new Line2D.Double(0.5, 0.22, 0.5, 0.78));
                }
                case TRASH -> {
                    g2dCopy.draw(new Line2D.Double(0.16, 0.28, 0.84, 0.28));
                    Path2D handle = new Path2D.Double();
                    handle.moveTo(0.38, 0.28); handle.lineTo(0.38, 0.14); handle.lineTo(0.62, 0.14); handle.lineTo(0.62, 0.28);
                    g2dCopy.draw(handle);
                    Path2D body = new Path2D.Double();
                    body.moveTo(0.26, 0.28); body.lineTo(0.30, 0.88); body.lineTo(0.70, 0.88); body.lineTo(0.74, 0.28);
                    g2dCopy.draw(body);
                    g2dCopy.draw(new Line2D.Double(0.43, 0.44, 0.43, 0.72));
                    g2dCopy.draw(new Line2D.Double(0.57, 0.44, 0.57, 0.72));
                }
                case FOLDER -> {
                    Path2D folder = new Path2D.Double();
                    folder.moveTo(0.10, 0.22); folder.lineTo(0.38, 0.22); folder.lineTo(0.46, 0.33); folder.lineTo(0.90, 0.33);
                    folder.lineTo(0.90, 0.80); folder.lineTo(0.10, 0.80); folder.closePath();
                    g2dCopy.draw(folder);
                }
                case DICE -> {
                    g2dCopy.draw(new RoundRectangle2D.Double(0.14, 0.14, 0.72, 0.72, 0.16, 0.16));
                    for (double[] d : new double[][]{{0.32, 0.32}, {0.68, 0.32}, {0.5, 0.5}, {0.32, 0.68}, {0.68, 0.68}}) g2dCopy.fill(new Ellipse2D.Double(d[0] - 0.06, d[1] - 0.06, 0.12, 0.12));
                }
                case HELP -> {
                    g2dCopy.draw(new Ellipse2D.Double(0.10, 0.10, 0.80, 0.80));
                    Font f = new Font(Font.SANS_SERIF, Font.BOLD, 12).deriveFont(0.58f);
                    g2dCopy.setFont(f);
                    Rectangle2D b = f.getStringBounds("?", g2dCopy.getFontRenderContext());
                    g2dCopy.drawString("?", (float) (0.5 - b.getCenterX()), (float) (0.5 - b.getCenterY()));
                }
                case FLAG -> {
                    g2dCopy.draw(new Line2D.Double(0.26, 0.12, 0.26, 0.9));
                    g2dCopy.draw(new Rectangle2D.Double(0.26, 0.14, 0.56, 0.38));
                    g2dCopy.fill(new Rectangle2D.Double(0.26, 0.14, 0.28, 0.19));
                    g2dCopy.fill(new Rectangle2D.Double(0.54, 0.33, 0.28, 0.19));
                }
                case UP, DOWN -> {
                    Path2D tri = new Path2D.Double();
                    if (this == UP) { tri.moveTo(0.5, 0.16); tri.lineTo(0.86, 0.80); tri.lineTo(0.14, 0.80); }
                    else { tri.moveTo(0.5, 0.84); tri.lineTo(0.86, 0.20); tri.lineTo(0.14, 0.20); }
                    tri.closePath();
                    g2dCopy.fill(tri);
                }
                case IMPORT -> {
                    g2dCopy.draw(new RoundRectangle2D.Double(0.12, 0.18, 0.76, 0.64, 0.08, 0.08));
                    g2dCopy.fill(new Ellipse2D.Double(0.60, 0.28, 0.14, 0.14));
                    Path2D mountains = new Path2D.Double();
                    mountains.moveTo(0.18, 0.76); mountains.lineTo(0.40, 0.46); mountains.lineTo(0.56, 0.64); mountains.lineTo(0.66, 0.54); mountains.lineTo(0.82, 0.76); mountains.closePath();
                    g2dCopy.fill(mountains);
                }
                case CHECK -> {
                    g2dCopy.setStroke(new BasicStroke(0.14f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    Path2D tick = new Path2D.Double();
                    tick.moveTo(0.18, 0.54); tick.lineTo(0.40, 0.74); tick.lineTo(0.84, 0.26);
                    g2dCopy.draw(tick);
                }
                case WARN -> {
                    Path2D tri = new Path2D.Double();
                    tri.moveTo(0.5, 0.12); tri.lineTo(0.92, 0.86); tri.lineTo(0.08, 0.86); tri.closePath();
                    g2dCopy.draw(tri);
                    g2dCopy.draw(new Line2D.Double(0.5, 0.38, 0.5, 0.60));
                    g2dCopy.fill(new Ellipse2D.Double(0.455, 0.67, 0.09, 0.09));
                }
                case NEW -> {
                    Path2D page = new Path2D.Double();
                    page.moveTo(0.22, 0.10); page.lineTo(0.58, 0.10); page.lineTo(0.78, 0.30); page.lineTo(0.78, 0.90); page.lineTo(0.22, 0.90); page.closePath();
                    g2dCopy.draw(page);
                    Path2D fold = new Path2D.Double();
                    fold.moveTo(0.58, 0.10); fold.lineTo(0.58, 0.30); fold.lineTo(0.78, 0.30);
                    g2dCopy.draw(fold);
                }
                case ARROW -> {
                    Path2D arrow = new Path2D.Double();
                    arrow.moveTo(0.88, 0.5); arrow.lineTo(0.40, 0.14); arrow.lineTo(0.40, 0.36); arrow.lineTo(0.12, 0.36);
                    arrow.lineTo(0.12, 0.64); arrow.lineTo(0.40, 0.64); arrow.lineTo(0.40, 0.86); arrow.closePath();
                    g2dCopy.fill(arrow);
                }
                case WALL -> g2dCopy.fill(new Rectangle2D.Double(0.14, 0.14, 0.72, 0.72));
                case PATH -> g2dCopy.draw(new Rectangle2D.Double(0.18, 0.18, 0.64, 0.64));
            }
            g2dCopy.dispose();
        }
    }

    //region Text Components
    public static class Text extends JComponent {
        private String text;
        private final float size;
        private final boolean bold;
        private Color color;
        private final int align;

        public Text(String key, float size, boolean bold, Color color, int align) {
            this.text = key == null ? "" : L(key);
            this.size = size; this.bold = bold; this.color = color; this.align = align;
            setOpaque(false);
        }
        public void setKey(String key) { setRaw(L(key)); }
        public void setRaw(String raw) { if (raw.equals(text)) return; text = raw; revalidate(); repaint(); }
        public void setColor(Color color) { this.color = color; repaint(); }
        @Override public Dimension getPreferredSize() {
            if (isPreferredSizeSet()) return super.getPreferredSize();
            FontMetrics fm = getFontMetrics(font(size, bold));
            return new Dimension(fm.stringWidth(text) + 2, fm.getHeight() + 4);
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2d = smooth(g);
            g2d.setFont(font(size, bold));
            FontMetrics fm = g2d.getFontMetrics();
            String shown = ellipsize(text, fm, getWidth());
            int x = switch (align) { case 1 -> (getWidth() - fm.stringWidth(shown)) / 2; case 2 -> getWidth() - fm.stringWidth(shown); default -> 0; };
            g2d.setColor(isEnabled() ? color : alpha(color, 110));
            g2d.drawString(shown, x, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            g2d.dispose();
        }
    }
    public static class Note extends JComponent {
        private String text = "";
        private Glyph icon;
        private Color color;
        private final int width;
        private final float size;

        public Note(int width, float size, Color color) { this.width = width; this.size = size; this.color = color; setOpaque(false); }
        public void set(Glyph icon, Color color, String text) {
            this.icon = icon; this.color = color; this.text = text == null ? "" : text;
            revalidate(); repaint();
        }
        private int textWidth() { return width - (icon != null ? Math.round(size * 1.6f) : 0); }
        @Override public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(font(size, false));
            return new Dimension(width, Math.max(fm.getHeight() + 4, wrap(text, fm, textWidth()).size() * (fm.getHeight() + 1) + 4));
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2d = smooth(g);
            g2d.setFont(font(size, false));
            FontMetrics fm = g2d.getFontMetrics();
            int x = 0;
            if (icon != null) { icon.paint(g2d, 0, 1, size * 1.25, color); x = Math.round(size * 1.6f); }
            g2d.setColor(color);
            int y = 2 + fm.getAscent();
            for (String line : wrap(text, fm, textWidth())) { g2d.drawString(line, x, y); y += fm.getHeight() + 1; }
            g2d.dispose();
        }
    }
    //endregion

    //region Buttons
    public static class ToolButton extends JComponent {
        public enum Layout { ICON, ROW, TILE, TEXT }

        private final Theme theme;
        private final Layout layout;
        private Glyph icon, glyph;
        private String text = "", badge;
        private Color swatch, glyphColor, accent;
        private boolean selectable, selected, hover, pressed, toggle;
        private double rotation;
        private ToolButton[] group;
        private final List<Runnable> listeners = new ArrayList<>();

        public ToolButton(Style style, Layout layout, Glyph icon, String key) {
            this.theme = Theme.of(style);
            this.layout = layout;
            this.icon = icon;
            this.text = key == null ? "" : L(key);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(switch (layout) { case ICON -> new Dimension(34, 34); case ROW -> new Dimension(150, 30); case TILE -> new Dimension(70, 50); case TEXT -> new Dimension(40, 28); });
            MouseAdapter mouse = new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; pressed = false; repaint(); }
                @Override public void mousePressed(MouseEvent e) { if (isEnabled() && SwingUtilities.isLeftMouseButton(e)) { pressed = true; repaint(); } }
                @Override public void mouseReleased(MouseEvent e) {
                    boolean fire = pressed && isEnabled() && contains(e.getPoint());
                    pressed = false; repaint();
                    if (fire) click();
                }
            };
            addMouseListener(mouse);
        }

        public static void group(ToolButton... buttons) { for (ToolButton b : buttons) { b.selectable = true; b.group = buttons; } }
        public ToolButton tip(String key, String shortcut) { setToolTipText(L(key) + (shortcut == null || shortcut.isEmpty() ? "" : "   [" + shortcut + "]")); return this; }
        public ToolButton badge(String badge) { this.badge = badge; return this; }
        public ToolButton swatch(Color color, Glyph glyph, Color glyphColor) { this.swatch = color; this.glyph = glyph; this.glyphColor = glyphColor; return this; }
        public ToolButton accent(Color color) { this.accent = color; return this; }
        public ToolButton toggle() { this.toggle = true; return this; }
        public ToolButton size(int w, int h) { setPreferredSize(new Dimension(w, h)); return this; }
        public ToolButton onClick(Runnable action) { listeners.add(action); return this; }
        public void setLabel(String raw) { text = raw == null ? "" : raw; repaint(); }
        public void setIconRotation(double radians) { rotation = radians; repaint(); }
        public boolean isSelected() { return selected; }
        public void setSelected(boolean value) { if (selected != value) { selected = value; repaint(); } }
        public void choose() { if (group != null) for (ToolButton b : group) b.setSelected(b == this); else setSelected(true); }
        public void click() {
            if (selectable) choose();
            else if (toggle) setSelected(!selected);
            for (Runnable r : new ArrayList<>(listeners)) r.run();
        }
        @Override public void setEnabled(boolean enabled) { super.setEnabled(enabled); setCursor(Cursor.getPredefinedCursor(enabled ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR)); repaint(); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2d = smooth(g);
            int w = getWidth(), h = getHeight();
            boolean on = isEnabled();
            if (!on) g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.38f));
            int arc = Math.round(Math.min(12, Math.min(w, h) * 0.3f));
            int off = pressed ? 1 : 0;

            Color fill = hover && on ? theme.hover : theme.normal;
            Color border = theme.accent;
            Color textColor = theme.text;
            if (accent != null) { fill = hover && on ? mix(accent, Color.WHITE, 0.14f) : accent; border = mix(accent, Color.WHITE, 0.3f); textColor = Color.WHITE; }
            if (selected) { fill = mix(hover ? theme.hover : theme.normal, HILITE, 0.5f); border = HILITE; textColor = Color.WHITE; }
            g2d.setColor(fill);
            g2d.fillRoundRect(0, off, w - 1, h - 1, arc, arc);
            g2d.setColor(border);
            g2d.setStroke(new BasicStroke(selected ? 2f : 1f));
            g2d.drawRoundRect(selected ? 1 : 0, off + (selected ? 1 : 0), w - 1 - (selected ? 2 : 0), h - 1 - (selected ? 2 : 0), arc, arc);

            g2d.translate(0, off);
            switch (layout) {
                case ICON -> drawGraphic(g2d, (w - h * 0.56) / 2, h * 0.22, h * 0.56, textColor);
                case TEXT -> drawLabel(g2d, 6, 0, w - 12, h, textColor, 1);
                case ROW -> {
                    double s = Math.min(h * 0.58, 24);
                    int pad = Math.max(6, (int) ((h - s) / 2));
                    int x = pad;
                    if (swatch != null || icon != null) { drawGraphic(g2d, x, (h - s) / 2, s, textColor); x += (int) s + 8; }
                    int badgeW = 0;
                    if (badge != null && !badge.isEmpty()) badgeW = drawBadge(g2d, w - pad, h);
                    drawLabel(g2d, x, 0, w - x - pad - (badgeW > 0 ? badgeW + 4 : 0), h, textColor, swatch != null || icon != null ? 0 : 1);
                }
                case TILE -> {
                    double s = Math.min(h * 0.46, 26);
                    drawGraphic(g2d, (w - s) / 2, h * 0.10, s, textColor);
                    drawLabel(g2d, 3, (int) (h * 0.10 + s), w - 6, h - (int) (h * 0.10 + s) - 2, textColor, 1);
                    if (badge != null && !badge.isEmpty()) drawBadge(g2d, w - 4, (int) (h * 0.42));
                }
            }
            g2d.dispose();
        }
        private void drawGraphic(Graphics2D g2d, double x, double y, double s, Color textColor) {
            if (swatch != null) {
                g2d.setColor(swatch);
                g2d.fill(new RoundRectangle2D.Double(x, y, s, s, 5, 5));
                g2d.setColor(new Color(0, 0, 0, 120));
                g2d.draw(new RoundRectangle2D.Double(x + 0.5, y + 0.5, s - 1, s - 1, 5, 5));
                if (glyph != null) glyph.paint(g2d, x + s * 0.17, y + s * 0.17, s * 0.66, glyphColor);
            }
            else if (icon != null) {
                Graphics2D g2dCopy = (Graphics2D) g2d.create();
                if (rotation != 0) g2dCopy.rotate(rotation, x + s / 2, y + s / 2);
                icon.paint(g2dCopy, x, y, s, textColor);
                g2dCopy.dispose();
            }
        }
        private void drawLabel(Graphics2D g2d, int x, int y, int w, int h, Color color, int align) {
            if (text.isEmpty() || w <= 4) return;
            float size = layout == Layout.TILE ? Math.min(12f, h * 0.62f) : Math.clamp(getHeight() * 0.42f, 10f, 13f);
            Font f = font(size, true);
            FontMetrics fm = g2d.getFontMetrics(f);
            while (fm.stringWidth(text) > w && size > 9f) { size -= 0.5f; f = font(size, true); fm = g2d.getFontMetrics(f); }
            String shown = ellipsize(text, fm, w);
            g2d.setFont(f);
            g2d.setColor(color);
            g2d.drawString(shown, align == 1 ? x + (w - fm.stringWidth(shown)) / 2 : x, y + (h - fm.getHeight()) / 2 + fm.getAscent());
        }
        private int drawBadge(Graphics2D g2d, int rightEdge, int h) {
            Font f = font(10f, true);
            FontMetrics fm = g2d.getFontMetrics(f);
            int bw = Math.max(16, fm.stringWidth(badge) + 8), bh = 16;
            int bx = rightEdge - bw, by = layout == Layout.TILE ? 4 : (h - bh) / 2;
            g2d.setColor(new Color(0, 0, 0, 90));
            g2d.fillRoundRect(bx, by, bw, bh, 8, 8);
            g2d.setFont(f);
            g2d.setColor(new Color(255, 255, 255, 190));
            g2d.drawString(badge, bx + (bw - fm.stringWidth(badge)) / 2, by + (bh - fm.getHeight()) / 2 + fm.getAscent());
            return bw;
        }
    }
    //endregion

    //region Panels
    public static class Column extends JPanel {
        public Column() { super(new StackLayout()); setOpaque(false); }
        public <T extends Component> T put(T c, int gapTop) {
            if (c instanceof JComponent jc) jc.putClientProperty("stackGap", gapTop);
            add(c);
            return c;
        }
    }
    static final class StackLayout implements LayoutManager {
        private static int gap(Component c) { return c instanceof JComponent jc && jc.getClientProperty("stackGap") instanceof Integer i ? i : 0; }
        @Override public void addLayoutComponent(String name, Component comp) {}
        @Override public void removeLayoutComponent(Component comp) {}
        @Override public Dimension preferredLayoutSize(Container parent) {
            int w = 0, h = 0;
            for (Component c : parent.getComponents()) {
                if (!c.isVisible()) continue;
                Dimension d = c.getPreferredSize();
                w = Math.max(w, d.width); h += d.height + gap(c);
            }
            Insets in = parent.getInsets();
            return new Dimension(w + in.left + in.right, h + in.top + in.bottom);
        }
        @Override public Dimension minimumLayoutSize(Container parent) { return preferredLayoutSize(parent); }
        @Override public void layoutContainer(Container parent) {
            Insets in = parent.getInsets();
            int y = in.top, w = parent.getWidth() - in.left - in.right;
            for (Component c : parent.getComponents()) {
                if (!c.isVisible()) continue;
                int h = c.getPreferredSize().height;
                y += gap(c);
                c.setBounds(in.left, y, w, h);
                y += h;
            }
        }
    }
    public static class Card extends Column {
        private final String title;
        public Card(String titleKey) {
            this.title = L(titleKey).toUpperCase(java.util.Locale.ROOT);
            setBorder(new EmptyBorder(23, 8, 7, 8));
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2d = smooth(g);
            g2d.setColor(CARD);
            g2d.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            g2d.setColor(CARD_LINE);
            g2d.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            g2d.setFont(font(10.5f, true));
            g2d.setColor(MUTED);
            g2d.drawString(ellipsize(title, g2d.getFontMetrics(), getWidth() - 20), 10, 16);
            g2d.dispose();
        }
    }

    public static JPanel row(int hgap, Component... parts) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, hgap, 0));
        p.setOpaque(false);
        for (Component c : parts) p.add(c);
        return p;
    }
    //endregion

    public static class ScrollBody extends JPanel implements Scrollable {
        public ScrollBody() { super(new BorderLayout()); setOpaque(false); }
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 16; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return 64; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    //region Status Bar
    public enum Tone { INFO, OK, WARN, BAD }
    public static class HintBar extends JComponent {
        private String hint = "", flash = "";
        private Tone flashTone = Tone.INFO;
        private final Timer timer = new Timer(3600, e -> { flash = ""; repaint(); });

        public HintBar() { setOpaque(false); setPreferredSize(new Dimension(300, 38)); timer.setRepeats(false); }
        public void setHint(String raw) { hint = raw == null ? "" : raw; repaint(); }
        public void flash(Tone tone, String raw) { flash = raw == null ? "" : raw; flashTone = tone; timer.restart(); repaint(); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2d = smooth(g);
            boolean flashing = !flash.isEmpty();
            Color color = flashing ? switch (flashTone) { case OK -> OK; case WARN -> WARN; case BAD -> BAD; default -> TEXT; } : MUTED;
            g2d.setFont(font(12f, flashing));
            FontMetrics fm = g2d.getFontMetrics();
            int x = 0;
            if (flashing && flashTone != Tone.INFO) { (flashTone == Tone.OK ? Glyph.CHECK : Glyph.WARN).paint(g2d, 0, (getHeight() - 16) / 2.0, 16, color); x = 22; }
            List<String> lines = wrap(flashing ? flash : hint, fm, getWidth() - x);
            int shown = Math.min(2, lines.size());
            int y = (getHeight() - shown * fm.getHeight()) / 2 + fm.getAscent();
            g2d.setColor(color);
            for (int i = 0; i < shown; i++) {
                String line = lines.get(i);
                if (i == 1 && lines.size() > 2) line = ellipsize(line + " …", fm, getWidth() - x);
                g2d.drawString(line, x, y);
                y += fm.getHeight();
            }
            g2d.dispose();
        }
    }
    //endregion

    //region Dialogs
    public static int ask(Component parent, Style style, String titleKey, String message, int primary, String... buttonKeys) {
        Window owner = SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, L(titleKey), Dialog.ModalityType.APPLICATION_MODAL);
        int[] result = {-1};
        JPanel content = new JPanel(new BorderLayout(0, 16));
        content.setBackground(BG);
        content.setBorder(new EmptyBorder(18, 20, 16, 20));
        Note note = new Note(380, 13f, TEXT);
        note.set(null, TEXT, message);
        content.add(note, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        FontMetrics fm = content.getFontMetrics(font(13f, true));
        for (int i = 0; i < buttonKeys.length; i++) {
            final int index = i;
            ToolButton b = new ToolButton(style, ToolButton.Layout.TEXT, null, buttonKeys[i]);
            b.size(Math.max(96, fm.stringWidth(L(buttonKeys[i])) + 32), 34);
            if (i == primary) b.accent(new Color(52, 120, 246));
            b.onClick(() -> { result[0] = index; dialog.dispose(); });
            buttons.add(b);
        }
        content.add(buttons, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.setResizable(false);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
        return result[0];
    }
    public static void inform(Component parent, Style style, String titleKey, String message) { ask(parent, style, titleKey, message, 0, "ed.ok"); }
    //endregion

    //region Grid Canvas
    public abstract static class GridCanvas extends JPanel {
        private static final int MARGIN = 16, PAN_STEP = 40;
        private static final double MAX_CELL_PIXELS = 64, WHEEL_STEP = 1.18;

        protected int cols = 1, rows = 1;
        protected double originX, originY, cell = 1, baseCell = 1;
        protected int hoverX = -1, hoverY = -1;
        private double zoom = 1, panX, panY;
        private boolean panning, spaceDown;
        private int panLastX, panLastY;
        private final List<Runnable> viewListeners = new ArrayList<>();

        public GridCanvas() {
            setOpaque(true);
            setBackground(CANVAS_BG);
            setBorder(BorderFactory.createLineBorder(CARD_LINE, 1));
            setFocusable(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            MouseAdapter mouse = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    requestFocusInWindow();
                    computeView();
                    if (SwingUtilities.isMiddleMouseButton(e) || (spaceDown && SwingUtilities.isLeftMouseButton(e))) {
                        panning = true; panLastX = e.getX(); panLastY = e.getY();
                        setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                        return;
                    }
                    pointerPressed(cellX(e.getX()), cellY(e.getY()), e);
                }
                @Override public void mouseDragged(MouseEvent e) {
                    computeView();
                    if (panning) {
                        panX = originX + (e.getX() - panLastX); panY = originY + (e.getY() - panLastY);
                        panLastX = e.getX(); panLastY = e.getY();
                        computeView(); fireView(); repaint();
                        return;
                    }
                    updateHover(e);
                    pointerDragged(cellX(e.getX()), cellY(e.getY()), e);
                }
                @Override public void mouseReleased(MouseEvent e) {
                    computeView();
                    if (panning) { panning = false; setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)); return; }
                    pointerReleased(cellX(e.getX()), cellY(e.getY()), e);
                }
                @Override public void mouseMoved(MouseEvent e) { computeView(); updateHover(e); }
                @Override public void mouseEntered(MouseEvent e) { requestFocusInWindow(); }
                @Override public void mouseExited(MouseEvent e) { if (hoverX != -1 || hoverY != -1) { hoverX = hoverY = -1; fireView(); repaint(); } }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
            addMouseWheelListener(e -> { computeView(); zoomBy(Math.pow(WHEEL_STEP, -e.getPreciseWheelRotation()), e.getX(), e.getY()); });
            installKeys();
        }

        protected abstract void paintContent(Graphics2D g2d, int x0, int y0, int x1, int y1);
        protected void paintOverlay(Graphics2D g2d, int x0, int y0, int x1, int y1) {}
        protected abstract void pointerPressed(int x, int y, MouseEvent e);
        protected void pointerDragged(int x, int y, MouseEvent e) {}
        protected void pointerReleased(int x, int y, MouseEvent e) {}

        public int getCols() { return cols; }
        public int getRows() { return rows; }
        public int getHoverX() { return hoverX; }
        public int getHoverY() { return hoverY; }
        public void addViewListener(Runnable r) { viewListeners.add(r); }
        public void setGrid(int cols, int rows) { this.cols = Math.max(1, cols); this.rows = Math.max(1, rows); hoverX = hoverY = -1; resetView(); }
        public void resetView() { zoom = 1; panX = panY = 0; fireView(); repaint(); }
        public double zoomPercent() { return zoom * 100; }
        public void zoomIn() { zoomBy(1.4, getWidth() / 2.0, getHeight() / 2.0); }
        public void zoomOut() { zoomBy(1 / 1.4, getWidth() / 2.0, getHeight() / 2.0); }
        public boolean inGrid(int x, int y) { return x >= 0 && y >= 0 && x < cols && y < rows; }
        public int px(int cellX) { return (int) Math.round(originX + cellX * cell); }
        public int py(int cellY) { return (int) Math.round(originY + cellY * cell); }
        public int cellX(int pixel) { return (int) Math.floor((pixel - originX) / Math.max(0.0001, cell)); }
        public int cellY(int pixel) { return (int) Math.floor((pixel - originY) / Math.max(0.0001, cell)); }

        public void zoomBy(double factor, double anchorX, double anchorY) {
            computeView();
            double maxZoom = Math.max(1, MAX_CELL_PIXELS / baseCell);
            double next = Math.clamp(zoom * factor, 1, maxZoom);
            if (next == zoom) return;
            double cx = (anchorX - originX) / cell, cy = (anchorY - originY) / cell;
            zoom = next;
            panX = anchorX - cx * baseCell * zoom;
            panY = anchorY - cy * baseCell * zoom;
            computeView(); fireView(); repaint();
        }
        private void fireView() { for (Runnable r : new ArrayList<>(viewListeners)) r.run(); }
        private void updateHover(MouseEvent e) {
            int cx = cellX(e.getX()), cy = cellY(e.getY());
            if (!inGrid(cx, cy)) { cx = -1; cy = -1; }
            if (cx != hoverX || cy != hoverY) { hoverX = cx; hoverY = cy; fireView(); repaint(); }
        }
        private static double clampAxis(double desired, double content, int viewport) {
            if (content <= viewport) return (viewport - content) / 2.0;
            return Math.clamp(desired, viewport - content, 0);
        }
        private void computeView() {
            int w = getWidth(), h = getHeight();
            baseCell = Math.max(0.0001, Math.min((w - 2.0 * MARGIN) / cols, (h - 2.0 * MARGIN) / rows));
            cell = baseCell * zoom;
            originX = clampAxis(panX, cell * cols, w);
            originY = clampAxis(panY, cell * rows, h);
            panX = originX; panY = originY;
        }
        private void installKeys() {
            InputMap im = getInputMap(WHEN_FOCUSED);
            ActionMap am = getActionMap();
            String[][] pans = {{"up", "W", "UP"}, {"down", "S", "DOWN"}, {"left", "A", "LEFT"}, {"right", "D", "RIGHT"}};
            int[][] steps = {{0, PAN_STEP}, {0, -PAN_STEP}, {PAN_STEP, 0}, {-PAN_STEP, 0}};
            for (int i = 0; i < pans.length; i++) {
                final int[] step = steps[i];
                String name = "pan" + pans[i][0];
                im.put(KeyStroke.getKeyStroke(pans[i][1]), name);
                im.put(KeyStroke.getKeyStroke(pans[i][2]), name);
                am.put(name, new AbstractAction() { @Override public void actionPerformed(ActionEvent e) {
                    computeView();
                    if (zoom <= 1) return;
                    panX = originX + step[0]; panY = originY + step[1];
                    computeView(); fireView(); repaint();
                }});
            }
            im.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0, false), "spaceDown");
            im.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0, true), "spaceUp");
            am.put("spaceDown", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { spaceDown = true; setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)); }});
            am.put("spaceUp", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { spaceDown = false; setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)); }});
            for (int key : new int[]{KeyEvent.VK_EQUALS, KeyEvent.VK_ADD, KeyEvent.VK_PLUS}) im.put(KeyStroke.getKeyStroke(key, 0), "zoomIn");
            for (int key : new int[]{KeyEvent.VK_MINUS, KeyEvent.VK_SUBTRACT}) im.put(KeyStroke.getKeyStroke(key, 0), "zoomOut");
            for (int key : new int[]{KeyEvent.VK_0, KeyEvent.VK_NUMPAD0}) im.put(KeyStroke.getKeyStroke(key, 0), "zoomFit");
            am.put("zoomIn", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { zoomIn(); }});
            am.put("zoomOut", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { zoomOut(); }});
            am.put("zoomFit", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { resetView(); }});
        }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            computeView();
            Graphics2D g2d = (Graphics2D) g.create();
            int x0 = Math.max(0, (int) Math.floor(-originX / cell)), x1 = Math.min(cols - 1, (int) Math.floor((getWidth() - originX) / cell));
            int y0 = Math.max(0, (int) Math.floor(-originY / cell)), y1 = Math.min(rows - 1, (int) Math.floor((getHeight() - originY) / cell));
            if (x1 >= x0 && y1 >= y0) {
                paintContent(g2d, x0, y0, x1, y1);
                paintOverlay(g2d, x0, y0, x1, y1);
            }
            g2d.dispose();
        }
    }

    public static JPanel zoomBar(Style style, GridCanvas canvas) {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        bar.setOpaque(false);
        ToolButton out = new ToolButton(style, ToolButton.Layout.ICON, Glyph.ZOOM_OUT, null).tip("ed.zoom_out", "-").size(30, 28);
        ToolButton in = new ToolButton(style, ToolButton.Layout.ICON, Glyph.ZOOM_IN, null).tip("ed.zoom_in", "+").size(30, 28);
        ToolButton fit = new ToolButton(style, ToolButton.Layout.ICON, Glyph.FIT, null).tip("ed.zoom_fit", "0").size(30, 28);
        Text percent = new Text(null, 12f, true, TEXT, 1);
        percent.setPreferredSize(new Dimension(48, 28));
        out.onClick(canvas::zoomOut);
        in.onClick(canvas::zoomIn);
        fit.onClick(canvas::resetView);
        canvas.addViewListener(() -> percent.setRaw(Math.round(canvas.zoomPercent()) + "%"));
        percent.setRaw("100%");
        bar.add(out); bar.add(percent); bar.add(in); bar.add(fit);
        return bar;
    }
    //endregion
}
