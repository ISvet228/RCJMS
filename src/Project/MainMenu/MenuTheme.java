package Project.MainMenu;
import Project.EngineCreator.*;
import Project.ThemeCreator.*;
import Project.CreatorKit.*;

import java.awt.Graphics2D;

public interface MenuTheme {
    //region Theme Identity
    String id();
    String nameKey();
    default int order() { return 100; }
    //endregion

    //region Painting
    default boolean hasStars() { return false; }
    void paintBase(Graphics2D g2d, int w, int h, double t);
    default void paintOverlay(Graphics2D g2d, int w, int h) { }
    //endregion
}