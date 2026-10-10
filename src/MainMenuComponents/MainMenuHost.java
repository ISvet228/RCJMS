package MainMenuComponents;

import StyleUI.Style;

public interface MainMenuHost {
    int screenWidth();
    int screenHeight();
    Style style();
    int windowWidth();
    boolean isFullscreen();
    void showMain();
    void startGame();
    void changeStyle(Style style);
    void changeLanguage(String code);
    void applyFullscreen(boolean value);
    void setWindowSize(int width, int height);
    void saveSettings();
    double getRenderScale();
    void setRenderScale(double scale);
    boolean isVSyncEnabled();
    void setVSyncEnabled(boolean enabled);
    boolean isRayTracingEnabled();
    void setRayTracingEnabled(boolean enabled);
    int getRayTracingQuality();
    void setRayTracingQuality(int quality);
}