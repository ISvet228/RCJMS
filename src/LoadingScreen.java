import Helpers.*;
import StyleUI.*;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;

public class LoadingScreen extends JPanel {
    //region Constants
    private static final Color BACKGROUND = new Color(8, 7, 11);
    private static final Color WALL = new Color(236, 212, 133);
    private static final Color BAR_DARK = new Color(170, 136, 60);
    private static final int SECTORS = 8, DEPTH = 24;
    private static final double RINGS_PER_SECOND = 0.35, SECTOR_GAP = 0.035;
    private static final int TIMER_MS = 33;
    private static final double NEAR_FADE_DEPTH = 0.35;
    private static final double GLINT_SPEED_PER_BAR = 0.35;
    //endregion

    //region Variables
    private final Progress progress;
    private final long startNanos = System.nanoTime();
    private final Timer timer = new Timer(TIMER_MS, e -> repaint());
    //endregion

    //region Constructors
    public LoadingScreen(Progress progress) {
        this.progress = progress;
        setOpaque(true);
        setBackground(BACKGROUND);
    }
    //endregion

    //region Other
    @Override public void addNotify() { super.addNotify(); timer.start(); }
    @Override public void removeNotify() { timer.stop(); super.removeNotify(); }
    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        paint(g2, getWidth(), getHeight(), progress.fraction(), statusText(progress.stage()), seconds());
        g2.dispose();
    }
    //endregion

    //region Painting
    public static void paint(Graphics2D g2d, int w, int h, double fraction, String status, double seconds) {
        Graphics2D g2g = (Graphics2D) g2d.create();
        g2g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2g.setColor(BACKGROUND);
        g2g.fillRect(0, 0, w, h);
        drawTunnel(g2g, w, h, seconds);
        g2g.setPaint(new RadialGradientPaint(w / 2f, h * 0.42f, Math.max(w, h) * 0.75f,
                new float[]{0.45f, 1f}, new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 215)}));
        g2g.fillRect(0, 0, w, h);
        g2g.setPaint(new GradientPaint(0, h * 0.62f, new Color(0, 0, 0, 0), 0, h, new Color(0, 0, 0, 200)));
        g2g.fillRect(0, (int) (h * 0.62), w, h);
        drawBar(g2g, w, h, fraction, status, seconds);
        g2g.dispose();
    }

    private static void drawTunnel(Graphics2D g, int w, int h, double t) {
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
                g.setColor(fill);
                g.fill(p);
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

    private static void drawBar(Graphics2D g, int w, int h, double fraction, String status, double seconds) {
        int barW = (int) Math.round(w * 0.46), barH = Math.max(10, h / 34);
        int bx = (w - barW) / 2, by = (int) Math.round(h * 0.80);
        int fontSize = Math.max(12, h / 28);
        g.setFont(new Font("SansSerif", Font.BOLD, fontSize));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(new Color(236, 230, 214, 230));
        g.drawString(status, (w - fm.stringWidth(status)) / 2, by - barH);

        RoundRectangle2D track = new RoundRectangle2D.Double(bx, by, barW, barH, barH, barH);
        g.setColor(new Color(255, 255, 255, 28));
        g.fill(track);
        g.setColor(new Color(255, 255, 255, 70));
        g.draw(track);

        double fillW = Math.max(barH, barW * fraction);
        if (fraction > 0) {
            Shape clip = g.getClip();
            RoundRectangle2D fill = new RoundRectangle2D.Double(bx, by, fillW, barH, barH, barH);
            g.setPaint(new GradientPaint(bx, by, BAR_DARK, bx + (float) fillW, by, WALL));
            g.fill(fill);
            g.setClip(fill);
            double glintW = Math.max(40, barH * 3);
            double cycle = barW + 2 * glintW;
            double glintCenter = bx + (seconds * barW * GLINT_SPEED_PER_BAR) % cycle - glintW;
            float x0 = (float) (glintCenter - glintW / 2), x1 = (float) (glintCenter + glintW / 2);
            g.setPaint(new LinearGradientPaint(x0, by, x1, by, new float[]{0f, 0.5f, 1f},
                    new Color[]{new Color(255, 255, 255, 0), new Color(255, 255, 255, 110), new Color(255, 255, 255, 0)}));
            g.fillRect((int) Math.floor(x0), by, (int) Math.ceil(x1 - x0) + 1, barH);
            g.setClip(clip);
        }
        String percent = Math.round(fraction * 100) + "%";
        g.setColor(new Color(236, 230, 214, 230));
        g.drawString(percent, (w - fm.stringWidth(percent)) / 2, by + barH + fontSize + 4);
    }

    private static String statusText(String key) {
        if (key == null || key.isEmpty()) return NSLocalizedString.get("load.title");
        return NSLocalizedString.get(key);
    }
    private double seconds() { return (System.nanoTime() - startNanos) / 1e9; }
    //endregion
}