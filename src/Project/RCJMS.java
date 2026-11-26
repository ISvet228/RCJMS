package Project;

import Project.Helpers.*;
import Project.Credits.CreditsView;
import Project.Audio.*;
import Project.Net.NetSession;
import Project.Game.GameView;
import Project.MainMenu.MainMenuView;
import Project.MapEditor.MapEditorView;
import Project.EngineCreator.RenderEngineCreatorView;
import Project.ThemeCreator.ThemeCreatorView;
import Project.TextureEditor.TextureEditorView;
import Project.Victory.VictoryView;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.IOException;

public class RCJMS extends JFrame {
    //region Variables
    //region Singleton
    public static RCJMS instance;
    //endregion
    //region View References
    public MainMenuView mainMenuView = new MainMenuView();
    public GameView gameView;
    public VictoryView victoryView;
    public TextureEditorView textureEditorView;
    public CreditsView creditsView;
    public MapEditorView mapEditorView;
    public RenderEngineCreatorView renderEngineCreatorView;
    public ThemeCreatorView themeCreatorView;
    private JPanel currentView;
    //endregion
    //region Window Metrics
    public static final int SCREEN_WIDTH = 960, SCREEN_HEIGHT = 540;
    public static int GAME_WIDTH = SCREEN_WIDTH, GAME_HEIGHT = SCREEN_HEIGHT;
    public static final Color MY_FAV_GRAY = new Color(28, 28, 32);
    //endregion
    //region Fullscreen State
    private boolean fullscreen = false, f11Down = false, f8Down = false;
    //endregion
    //endregion

    //region Constructors
    public RCJMS() throws IOException {
        instance = this;
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        SaveData.Data savedData = SaveData.load();
        GAME_WIDTH = savedData.windowWidth;
        GAME_HEIGHT = savedData.windowHeight;
        fullscreen = savedData.fullscreen;
        installWindowHotkeys();
        if (savedData.fullscreen) {
            setUndecorated(true);
            setExtendedState(JFrame.MAXIMIZED_BOTH);
        }
        else setSize(savedData.windowWidth, savedData.windowHeight);

        setLocationRelativeTo(null);
        changeView(mainMenuView, "main_menu");
        setVisible(true);
    }
    //endregion

    //region Startup
    public static void main(String[] args) {
        Project.Update.GameUpdater.start();
        SwingUtilities.invokeLater(() -> { try {new RCJMS();} catch (IOException exception){throw new RuntimeException(exception);}}); }
    //endregion

    //region Shutdown
    public static void quit() {
        NetSession session = NetSession.current();
        if (session != null) session.leaveGracefully();
        System.exit(0);
    }
    //endregion

    //region Window Hotkeys
    private void installWindowHotkeys() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keyEvent -> {
            int code = keyEvent.getKeyCode();
            if (code != KeyEvent.VK_F11 && code != KeyEvent.VK_F8) return false;
            Component source = keyEvent.getSource() instanceof Component component ? component : null;
            Window window = source instanceof Window w ? w : source == null ? null : SwingUtilities.getWindowAncestor(source);
            if (window != this) return false;
            boolean pressed = keyEvent.getID() == KeyEvent.KEY_PRESSED;
            if (keyEvent.getID() != KeyEvent.KEY_PRESSED && keyEvent.getID() != KeyEvent.KEY_RELEASED) return false;
            boolean down = code == KeyEvent.VK_F11 ? f11Down : f8Down;
            if (pressed && !down)
                if (code == KeyEvent.VK_F11) setFullscreen(!fullscreen, true); else fixAspectRatio();
            if (code == KeyEvent.VK_F11) f11Down = pressed; else f8Down = pressed;
            return true;
        });
    }
    //endregion

    //region Fullscreen
    public void setFullscreen(boolean value, boolean save) {
        fullscreen = value;
        dispose();
        setUndecorated(value);
        setExtendedState(value ? JFrame.MAXIMIZED_BOTH : JFrame.NORMAL);
        if (!value){
            setExtendedState(JFrame.NORMAL);
            setSize(GAME_WIDTH, GAME_HEIGHT);
            setLocationRelativeTo(null);
        }
        setVisible(true);
        revalidate();
        repaint();
        if (currentView != null) currentView.requestFocusInWindow();
        if (currentView instanceof MainMenuView menu) menu.syncFullscreen(value);
        if (save) saveFullscreenSetting();
    }
    public void fixAspectRatio() {
        if (fullscreen || (getExtendedState() & JFrame.MAXIMIZED_BOTH) != 0) return;
        int contentWidth = getContentPane().getWidth(), contentHeight = getContentPane().getHeight();
        if (contentWidth <= 0 || contentHeight <= 0) return;
        int extraWidth = getWidth() - contentWidth, extraHeight = getHeight() - contentHeight;
        int newContentHeight = Math.round(contentWidth * (float) SCREEN_HEIGHT / SCREEN_WIDTH);

        Insets screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(getGraphicsConfiguration());
        int maxHeight = getGraphicsConfiguration().getBounds().height - screenInsets.top - screenInsets.bottom;
        if (newContentHeight + extraHeight > maxHeight) {
            newContentHeight = maxHeight - extraHeight;
            contentWidth = Math.round(newContentHeight * (float) SCREEN_WIDTH / SCREEN_HEIGHT);
        }
        Point location = getLocation();
        setSize(contentWidth + extraWidth, newContentHeight + extraHeight);
        setLocation(location);
        revalidate();
        repaint();
    }
    private void saveFullscreenSetting() {
        try {
            SaveData.Data data = SaveData.load();
            SaveData.saveSettings(data.language, data.theme, data.renderScale, data.vsync, data.renderEngine, data.renderEngineQuality, GAME_WIDTH, GAME_HEIGHT, fullscreen, data.openCl);
        } catch (IOException exception) { Log.error(exception); }
    }
    //endregion

    //region View Switching
    public void changeView(JPanel nextView, String nextTitle) {
        if (currentView != null) remove(currentView);
        add(nextView);
        currentView = nextView;
        setTitle(nextTitle);
        revalidate();
        repaint();
        chooseSceneMusic(nextTitle);
    }

    private static void chooseSceneMusic(String viewName) {
        if (viewName == null) return;
        boolean game = "Raycast Me!".equals(viewName) || "RayCast Me!".equals(viewName);
        Sound.setRunActive(game);
        if (!game) {
            NetSession session = NetSession.current();
            if (session != null) session.close();
        }
        switch (viewName) {
            case "Raycast Me!", "RayCast Me!" -> Music.stop();
            case "cv.credits" -> Music.showScene(Music.Style.CREDITS);
            default -> Music.showScene(Music.Style.CALM);
        }
    }
    //endregion
}