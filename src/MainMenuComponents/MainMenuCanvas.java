package MainMenuComponents;

import StyleUI.*;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.List;

public final class MainMenuCanvas {
    //region Variables
    static final Color ACCENT = new Color(255, 127, 0);
    static final Color TEXT = new Color(235, 235, 240);
    static final Color MUTED = new Color(165, 165, 180);

    private final int screenWidth, screenHeight;
    private double scale = 1;
    private int originX, originY;
    private int lastWidth = -1, lastHeight = -1;
    //endregion

    //region Constructors
    public MainMenuCanvas(int screenWidth, int screenHeight) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
    }
    //endregion

    //region Measuring
    public boolean measure(Container host) {
        int width = host.getWidth(), height = host.getHeight();
        if (width <= 0 || height <= 0) return false;
        scale = Math.min((double) width / screenWidth, (double) height / screenHeight);
        originX = (width - (int) Math.round(screenWidth * scale)) / 2;
        originY = (height - (int) Math.round(screenHeight * scale)) / 2;
        return true;
    }
    public boolean sizeChanged(Container host) {
        boolean changed = host.getWidth() != lastWidth || host.getHeight() != lastHeight;
        lastWidth = host.getWidth();
        lastHeight = host.getHeight();
        return changed;
    }
    public double scale() { return scale; }
    public int x(int designX) { return originX + (int) Math.round(designX * scale); }
    public int y(int designY) { return originY + (int) Math.round(designY * scale); }
    public Font font(float designSize, int style) { return new Font(Font.SANS_SERIF, style, Math.max(1, (int) Math.round(designSize * scale))); }
    private int size(int designSize) { return Math.max(1, (int) Math.round(designSize * scale)); }
    //endregion

    //region Layout
    public void place(Component component, int designX, int designY, int designWidth, int designHeight) {
        if (component instanceof StyledToggleButton toggle) {
            toggle.setReferenceSize(screenWidth, screenHeight);
            toggle.setBounds(designX, designY, designWidth, designHeight);
            toggle.updateScale();
            return;
        }
        component.setBounds(x(designX), y(designY), size(designWidth), size(designHeight));
    }
    public void placeCentered(Component component, double centerX, double centerY, double designWidth, double designHeight) {
        int x = originX + (int) Math.round((centerX - designWidth / 2) * scale);
        int y = originY + (int) Math.round((centerY - designHeight / 2) * scale);
        component.setBounds(x, y, Math.max(1, (int) Math.round(designWidth * scale)), Math.max(1, (int) Math.round(designHeight * scale)));
    }
    public void text(StyledLabel label, float designSize, int designX, int designY, int designWidth, int designHeight) {
        label.setFont(font(designSize, Font.BOLD));
        place(label, designX, designY, designWidth, designHeight);
    }
    void combo(StyledComboBox box, int designX, int designY, int designWidth, int designHeight) {
        box.setFontValue(font(13, Font.BOLD));
        box.setPopupFont(font(13, Font.BOLD));
        place(box, designX, designY, designWidth, designHeight);
    }
    public void resetStyleUi(Container host) {
        for (Component component : host.getComponents())
            if (component instanceof AnimatedComponent animated) animated.updateScale();
    }
    //endregion

    //region Style
    public void applyStyle(Container host, Style style) { applyStyle(host, style, List.of()); }
    public void applyStyle(Container host, Style style, Collection<? extends Component> unstyled) {
        for (Component component : host.getComponents()) {
            if (unstyled.contains(component)) continue;
            if (component instanceof StyledButton button) button.setStyle(style);
            else if (component instanceof StyledToggle toggle) toggle.setStyle(style);
            else if (component instanceof StyledToggleButton toggleButton) toggleButton.setStyle(style);
            else if (component instanceof StyledComboBox box) box.setStyle(style);
            else if (component instanceof StyledTextField field) field.setStyle(style);
            else if (component instanceof StyledLabel label) label.setStyle(style);
        }
        host.repaint();
    }
    void paintCard(Graphics g, Style style, int designX, int designY, int designWidth, int designHeight) {
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int x = x(designX), y = y(designY), width = size(designWidth), height = size(designHeight), arc = size(18);
        g2d.setColor(panelColor(style));
        g2d.fillRoundRect(x, y, width, height, arc, arc);
        g2d.setColor(borderColor(style));
        g2d.drawRoundRect(x, y, width - 1, height - 1, arc, arc);
        g2d.dispose();
    }
    void paintDivider(Graphics g, Style style, int designX, int designFromY, int designToY) {
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setColor(borderColor(style));
        int top = y(designFromY);
        g2d.fillRect(x(designX), top, size(1), Math.max(1, y(designToY) - top));
        g2d.dispose();
    }
    private static Color panelColor(Style style) {
        return switch (style) {
            case NEUMORPHIC -> new Color(45, 47, 50, 240);
            case GLASS -> new Color(35, 45, 55, 215);
            default -> new Color(31, 33, 37, 240);
        };
    }
    private static Color borderColor(Style style) {
        return switch (style) {
            case NEUMORPHIC -> new Color(82, 84, 87);
            case GLASS -> new Color(255, 255, 255, 90);
            default -> new Color(71, 73, 79);
        };
    }
    //endregion
}