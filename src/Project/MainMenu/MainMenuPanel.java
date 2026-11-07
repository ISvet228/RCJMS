package Project.MainMenu;
import Project.StyleUI.*;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.List;

public abstract class MainMenuPanel extends JPanel {
    //region Variables
    //region Child Components
    protected final MainMenuView menu;
    protected final MainMenuCanvas canvas;
    //endregion
    //region Card Overlay
    private boolean hasCard;
    private int cardX, cardY, cardWidth, cardHeight;
    //endregion
    //endregion

    //region Constructors
    protected MainMenuPanel(MainMenuView menu) {
        this.menu = menu;
        this.canvas = new MainMenuCanvas(menu.screenWidth(), menu.screenHeight());
        setOpaque(false);
        setLayout(null);
    }
    //endregion

    //region Layout
    @Override public void doLayout() {
        if (!canvas.measure(this)) return;
        if (canvas.sizeChanged(this)) canvas.resetStyleUi(this);
        layoutDesign();
    }
    protected abstract void layoutDesign();
    //endregion

    //region Card
    protected void setCard(int designX, int designY, int designWidth, int designHeight) {
        hasCard = true;
        cardX = designX;
        cardY = designY;
        cardWidth = designWidth;
        cardHeight = designHeight;
    }
    protected void paintDecorations(Graphics graphics) { }
    //endregion

    //region Components
    protected StyledLabel label(String keyOrText, Color color) {
        StyledLabel label = new StyledLabel(menu.style(), keyOrText, false, false);
        label.setForeground(color);
        return label;
    }
    protected StyledButton button(String key) {
        StyledButton button = new StyledButton(menu.style(), key);
        button.setFocusable(false);
        return button;
    }
    //endregion

    //region Styling
    public void applyStyle(Style style) { canvas.applyStyle(this, style, unstyledComponents()); }
    protected Collection<? extends Component> unstyledComponents() { return List.of(); }
    //endregion

    //region Painting
    @Override public boolean isOptimizedDrawingEnabled() { return false; }
    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        if (!canvas.measure(this)) return;
        if (hasCard) canvas.paintCard(graphics, menu.style(), cardX, cardY, cardWidth, cardHeight);
        paintDecorations(graphics);
    }
    //endregion
}