import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class CreditsView extends JPanel {
    //region Variables
    private final String[] creditsText = {
            "PROJECT RCJMS",
            "",
            "Main Devloper",
            "ME",
            "",
            "Main Designer",
            "ME",
            "",
            "Scenario By",
            "ME",
            "",
            "Special Thanks",
            "",
            "Ljubo For Collision Fix And Walk Enchantments|https://github.com/lobujo552",
            "",
            "Useful Resources",
            "",
            "DDA|https://aaaa.sh/creatures/dda-algorithm-interactive/",
            "",
            "Thanks For Watching Credits",
            "",
            "I Also Have Another Project",
            "",
            "MondLocalized|https://github.com/ISvet228/mondLocalized",
            "",
            "",
            ""
    };

    private final List<CreditLine> lines = new ArrayList<>();
    private final Timer scrollTimer;

    private final double scrollSpeed = 1.0;
    private double scrollY;
    private boolean scrollInitialized = false;
    private double endY;
    private boolean finished = false;
    //endregion

    public CreditsView() {
        setPreferredSize(new Dimension(RCJMS.SCREEN_WIDTH, RCJMS.SCREEN_HEIGHT));
        setBackground(Color.BLACK);
        setFocusable(true);
        parseCredits();

        addKeyListener(new KeyAdapter() {@Override public void keyPressed(KeyEvent e) {if (e.getKeyCode() == KeyEvent.VK_ESCAPE) returnToMenu();}});
        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                CreditLine clickedLine = getClickedLine(e.getX(), e.getY());
                if (clickedLine != null && clickedLine.url != null) openURL(clickedLine.url);
            }});
        scrollTimer = new Timer(16, e -> {updateCredits();repaint();});
        scrollTimer.start();
        SwingUtilities.invokeLater(this::requestFocusInWindow);
    }

    //region Credits
    private void parseCredits() {
        lines.clear();
        for (String text : creditsText) {
            if (text == null) {
                lines.add(new CreditLine(""));
                continue;
            }
            int separator = text.lastIndexOf('|');
            if (separator > 0 && separator < text.length() - 1) {
                String visibleText = text.substring(0, separator);
                String url = text.substring(separator + 1);
                lines.add(new CreditLine(visibleText, url));
            } else lines.add(new CreditLine(text));
        }
    }

    private void updateCredits() {
        if (finished) return;
        scrollY -= scrollSpeed;
        if (scrollY <= endY) {
            scrollY = endY;
            finished = true;
            Timer returnTimer = new Timer(1200, e -> {((Timer) e.getSource()).stop();returnToMenu();});
            returnTimer.setRepeats(false);
            returnTimer.start();
        }
    }
    //endregion

    public void returnToMenu() {
        if (finished && scrollTimer == null) return;
        finished = true;
        if (scrollTimer != null) scrollTimer.stop();

        try {RCJMS.instance.ChangeView(RCJMS.instance.mainMenuView = new MainMenuView(), "RayCast Me!");}
        catch (Exception ex) {ex.printStackTrace();}
    }

    //region Layout
    private double getScale() {
        int width = getWidth();
        int height = getHeight();

        if (width <= 0 || height <= 0) return 1.0;
        return Math.min((double) width / RCJMS.SCREEN_WIDTH, (double) height / RCJMS.SCREEN_HEIGHT);
    }
    private int getOffsetX(double scale) {return (getWidth() - (int) (RCJMS.SCREEN_WIDTH * scale)) / 2;}
    private int getOffsetY(double scale) {return (getHeight() - (int) (RCJMS.SCREEN_HEIGHT * scale)) / 2;}
    //endregion

    //region Rendering
    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        int width = getWidth();
        int height = getHeight();

        if (width <= 0 || height <= 0) return;
        double scale = getScale();
        int offsetX = getOffsetX(scale);
        int offsetY = getOffsetY(scale);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(Color.BLACK);
        g2.fillRect(0, 0, width, height);
        g2.translate(offsetX, offsetY);
        g2.scale(scale, scale);
        g2.setColor(Color.BLACK);
        g2.fillRect(0, 0, RCJMS.SCREEN_WIDTH, RCJMS.SCREEN_HEIGHT);

        Shape oldClip = g2.getClip();

        g2.clipRect(0, 0, RCJMS.SCREEN_WIDTH, RCJMS.SCREEN_HEIGHT);
        drawCredits(g2);
        g2.setClip(oldClip);
        g2.dispose();
    }
    private void drawCredits(Graphics2D g2) {
        int baseFontSize = 28;
        int lineHeight = 45;

        Font normalFont = new Font("Arial", Font.PLAIN, baseFontSize);
        Font linkFont = new Font("Arial", Font.PLAIN, baseFontSize);
        Font titleFont = new Font("Arial", Font.BOLD, 40);

        double startY = RCJMS.SCREEN_HEIGHT + 100;

        int lastVisibleLine = -1;

        for (int i = lines.size() - 1; i >= 0; i--) {
            if (!lines.get(i).text.isEmpty()) {
                lastVisibleLine = i;
                break;
            }
        }

        if (lastVisibleLine >= 0) endY = -100 - lastVisibleLine * lineHeight;
        else endY = -100;

        if (!scrollInitialized) {
            scrollY = startY;
            scrollInitialized = true;
        }

        for (int i = 0; i < lines.size(); i++) {
            CreditLine line = lines.get(i);
            double y = scrollY + i * lineHeight;

            if (y < -lineHeight || y > RCJMS.SCREEN_HEIGHT + lineHeight) continue;

            Font font = line.url != null ? linkFont : normalFont;

            if (i == 0 && !line.text.isEmpty()) font = titleFont;
            g2.setFont(font);
            FontMetrics fm = g2.getFontMetrics();

            int textWidth = fm.stringWidth(line.text);
            int x = (RCJMS.SCREEN_WIDTH - textWidth) / 2;
            int baseline = (int) y;

            g2.setColor(Color.WHITE);
            g2.drawString(line.text, x, baseline);

            if (line.url != null) {
                int underlineY = baseline + 2;
                g2.drawLine(x, underlineY, x + textWidth, underlineY);
            }
        }
    }
    //endregion

    //region Muuse
    private CreditLine getClickedLine(int mouseX, int mouseY) {
        double scale = getScale();
        int offsetX = getOffsetX(scale);
        int offsetY = getOffsetY(scale);
        double logicalX = (mouseX - offsetX) / scale;
        double logicalY = (mouseY - offsetY) / scale;
        int lineHeight = 45;

        for (int i = 0; i < lines.size(); i++) {
            CreditLine line = lines.get(i);
            if (line.url == null || line.text.isEmpty()) continue;
            double y = scrollY + i * lineHeight;

            Font font = new Font("Arial", Font.PLAIN, 28);
            FontMetrics fm = getFontMetrics(font);

            int textWidth = fm.stringWidth(line.text);
            int x = (RCJMS.SCREEN_WIDTH - textWidth) / 2;
            int top = (int) y - fm.getAscent();
            int bottom = (int) y + fm.getDescent();

            if (logicalX >= x && logicalX <= x + textWidth && logicalY >= top && logicalY <= bottom) return line;
        }
        return null;
    }
    private void openURL(String url) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                if (desktop.isSupported(Desktop.Action.BROWSE)) desktop.browse(new URI(url));
            }
        } catch (Exception ex) {ex.printStackTrace();}
    }
    //endregion

    private static class CreditLine {
        String text;
        String url;
        CreditLine(String text) {
            this.text = text;
            this.url = null;
        }
        CreditLine(String text, String url) {
            this.text = text;
            this.url = url;
        }
    }
}