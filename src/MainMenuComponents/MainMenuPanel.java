package MainMenuComponents;

import StyleUI.*;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.List;

public abstract class MainMenuPanel extends JPanel {
    //region Variables
    protected final MainMenuHost menu;
    protected final MainMenuCanvas canvas;
    private boolean hasCard;
    private int cardX, cardY, cardWidth, cardHeight;
    //endregion

    //region Constructors
    protected MainMenuPanel(MainMenuHost menu) {
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
    protected void setCard(int designX, int designY, int designWidth, int designHeight) {
        hasCard = true;
        cardX = designX;
        cardY = designY;
        cardWidth = designWidth;
        cardHeight = designHeight;
    }
    //endregion

    //region Factories
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

    //region Painting
    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (!canvas.measure(this)) return;
        if (hasCard) canvas.paintCard(g, menu.style(), cardX, cardY, cardWidth, cardHeight);
        paintDecorations(g);
    }
    protected void paintDecorations(Graphics g) { }
    //endregion

    //region Styling
    public void applyStyle(Style style) { canvas.applyStyle(this, style, unstyledComponents()); }
    protected Collection<? extends Component> unstyledComponents() { return List.of(); }
    //endregion
}