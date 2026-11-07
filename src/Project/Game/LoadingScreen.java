package Project.Game;

import Project.Helpers.FontCache;
import Project.Helpers.*;
import Project.StyleUI.*;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;

public class LoadingScreen extends JPanel {
    //region Variables
    //region Tunnel Look
    private static final Color BACKGROUND = new Color(8, 7, 11);
    private static final Color WALL = new Color(236, 212, 133);
    private static final Color BAR_DARK = new Color(170, 136, 60);
    private static final int SECTORS = 8, DEPTH = 24;
    private static final double RINGS_PER_SECOND = 0.35, SECTOR_GAP = 0.035;
    private static final double NEAR_FADE_DEPTH = 0.35;
    private static final double GLINT_SPEED_PER_BAR = 0.35;
    //endregion
    //region Animation Timer
    private static final int TIMER_MS = 33;
    private final long startNanos = System.nanoTime();
    private final Timer timer = new Timer(TIMER_MS, actionEvent -> repaint());
    //endregion
    //region Loading Progress
    private final Progress progress;
    //endregion
    //endregion

    //region Constructors
    public LoadingScreen(Progress progress) {
        this.progress = progress;
        setOpaque(true);
        setBackground(BACKGROUND);
    }
    //endregion

    //region Lifecycle
    @Override public void addNotify() { super.addNotify(); timer.start(); }
    @Override public void removeNotify() { timer.stop(); super.removeNotify(); }
    //endregion

    //region Painting
    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g2d = (Graphics2D) graphics.create();
        paint(g2d, getWidth(), getHeight(), progress.fraction(), statusText(progress.stage()), seconds());
        g2d.dispose();
    }
    public static void paint(Graphics2D g2d, int w, int h, double fraction, String status, double seconds) {
        Graphics2D copyG2d = (Graphics2D) g2d.create();
        copyG2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        copyG2d.setColor(BACKGROUND);
        copyG2d.fillRect(0, 0, w, h);
        drawTunnel(copyG2d, w, h, seconds);
        copyG2d.setPaint(new RadialGradientPaint(w / 2f, h * 0.42f, Math.max(w, h) * 0.75f,
                new float[]{0.45f, 1f}, new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 215)}));
        copyG2d.fillRect(0, 0, w, h);
        copyG2d.setPaint(new GradientPaint(0, h * 0.62f, new Color(0, 0, 0, 0), 0, h, new Color(0, 0, 0, 200)));
        copyG2d.fillRect(0, (int) (h * 0.62), w, h);
        drawBar(copyG2d, w, h, fraction, status, seconds);
        copyG2d.dispose();
    }
    private static void drawTunnel(Graphics2D g2d, int w, int h, double t) {
        double cx = w * 0.5, cy = h * 0.42;
        double scale = Math.min(w, h) * 0.9;
        double maxR = Math.max(w, h) * 3.0;
        double phase = t * RINGS_PER_SECOND;
        long base = (long) Math.floor(phase);
        double frac = phase - base;
        double sector = 2 * Math.PI / SECTORS;
        double spin = t * 0.06;
        for (int i = DEPTH; i >= 1; i--) {
            double depth = i - frac;
            if (depth <= 0) continue;
            double rIn = scale / (depth + 1.0);
            if (rIn > maxR) continue;
            double rOut = Math.min(scale / depth, maxR);
            double nearFade = Math.min(1.0, depth / NEAR_FADE_DEPTH);
            long ring = base + i;
            double brightness = Math.clamp(1.0 - depth / (DEPTH * 0.85), 0.06, 1.0) * 0.5 * nearFade;
            Color fill = new Color((int) (WALL.getRed() * brightness), (int) (WALL.getGreen() * brightness),
                    (int) (WALL.getBlue() * brightness));
            for (int s = 0; s < SECTORS; s++) {
                if (!hasWall(ring, s)) continue;
                double a0 = spin + s * sector + SECTOR_GAP, a1 = spin + (s + 1) * sector - SECTOR_GAP;
                Path2D.Double p = new Path2D.Double();
                p.moveTo(cx + rIn * Math.cos(a0), cy + rIn * Math.sin(a0));
                p.lineTo(cx + rOut * Math.cos(a0), cy + rOut * Math.sin(a0));
                p.lineTo(cx + rOut * Math.cos(a1), cy + rOut * Math.sin(a1));
                p.lineTo(cx + rIn * Math.cos(a1), cy + rIn * Math.sin(a1));
                p.closePath();
                g2d.setColor(fill);
                g2d.fill(p);
            }
        }
    }
    private static boolean hasWall(long ring, int sector) {
        long hsh = ring * 0x9E3779B97F4A7C15L + sector * 0xC2B2AE3D27D4EB4FL;
        hsh ^= hsh >>> 31;
        hsh *= 0xBF58476D1CE4E5B9L;
        hsh ^= hsh >>> 27;
        return (hsh & 3) != 0;
    }
    private static void drawBar(Graphics2D g2d, int w, int h, double fraction, String status, double seconds) {
        int barW = (int) Math.round(w * 0.46), barH = Math.max(10, h / 34);
        int bx = (w - barW) / 2, by = (int) Math.round(h * 0.80);
        int fontSize = Math.max(12, h / 28);
        g2d.setFont(FontCache.get("SansSerif", Font.BOLD, fontSize));
        FontMetrics fontMetrics = g2d.getFontMetrics();
        g2d.setColor(new Color(236, 230, 214, 230));
        g2d.drawString(status, (w - fontMetrics.stringWidth(status)) / 2, by - barH);

        RoundRectangle2D track = new RoundRectangle2D.Double(bx, by, barW, barH, barH, barH);
        g2d.setColor(new Color(255, 255, 255, 28));
        g2d.fill(track);
        g2d.setColor(new Color(255, 255, 255, 70));
        g2d.draw(track);

        double fillW = Math.max(barH, barW * fraction);
        if (fraction > 0) {
            Shape clip = g2d.getClip();
            RoundRectangle2D fill = new RoundRectangle2D.Double(bx, by, fillW, barH, barH, barH);
            g2d.setPaint(new GradientPaint(bx, by, BAR_DARK, bx + (float) fillW, by, WALL));
            g2d.fill(fill);
            g2d.setClip(fill);
            double glintW = Math.max(40, barH * 3);
            double cycle = barW + 2 * glintW;
            double glintCenter = bx + (seconds * barW * GLINT_SPEED_PER_BAR) % cycle - glintW;
            float x0 = (float) (glintCenter - glintW / 2), x1 = (float) (glintCenter + glintW / 2);
            g2d.setPaint(new LinearGradientPaint(x0, by, x1, by, new float[]{0f, 0.5f, 1f},
                    new Color[]{new Color(255, 255, 255, 0), new Color(255, 255, 255, 110), new Color(255, 255, 255, 0)}));
            g2d.fillRect((int) Math.floor(x0), by, (int) Math.ceil(x1 - x0) + 1, barH);
            g2d.setClip(clip);
        }
        String percent = Math.round(fraction * 100) + "%";
        g2d.setColor(new Color(236, 230, 214, 230));
        g2d.drawString(percent, (w - fontMetrics.stringWidth(percent)) / 2, by + barH + fontSize + 4);
    }
    //endregion

    //region Status Text
    private static String statusText(String key) {
        if (key == null || key.isEmpty()) return NSLocalizedString.get("ld.title");
        return NSLocalizedString.get(key);
    }
    private double seconds() { return (System.nanoTime() - startNanos) / 1e9; }
    //endregion
}