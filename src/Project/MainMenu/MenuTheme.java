package Project.MainMenu;
import java.awt.Graphics2D;

public interface MenuTheme {
    //region Theme Identity
    String id();
    String nameKey();
    default int order() { return 100; }
    //endregion

    //region Painting
    void paintBase(Graphics2D g2d, int w, int h, double t);
    default void paintOverlay(Graphics2D g2d, int w, int h) { }
    //endregion
}