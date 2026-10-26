package Project.MainMenu;

import java.awt.Graphics2D;

public interface MenuTheme {
    String id();
    String nameKey();
    default int order() { return 100; }
    default boolean hasStars() { return false; }
    void paintBase(Graphics2D g, int w, int h, double t);
    default void paintOverlay(Graphics2D g, int w, int h) { }
}