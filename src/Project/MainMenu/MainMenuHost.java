package Project.MainMenu;
import Project.EngineCreator.*;
import Project.ThemeCreator.*;
import Project.CreatorKit.*;

import Project.StyleUI.Style;

public interface MainMenuHost {
    //region Window
    int screenWidth();
    int screenHeight();
    int windowWidth();
    void setWindowSize(int width, int height);
    boolean isFullscreen();
    void applyFullscreen(boolean value);
    //endregion

    //region Navigation
    void showMain();
    void startGame();
    //endregion

    //region Style and Language
    Style style();
    void changeStyle(Style style);
    MenuTheme getMenuTheme();
    void setMenuTheme(MenuTheme theme);
    void changeLanguage(String code);
    //endregion

    //region Render Settings
    double getRenderScale();
    void setRenderScale(double scale);
    boolean isVSyncEnabled();
    void setVSyncEnabled(boolean enabled);
    boolean isOpenClAcceleration();
    void setOpenClAcceleration(boolean enabled);
    int getRenderEngine();
    void setRenderEngine(int engine);
    int getRenderEngineQuality();
    void setRenderEngineQuality(int quality);
    //endregion

    //region Saving
    void saveSettings();
    //endregion
}