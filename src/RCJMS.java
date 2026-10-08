import Helpers.SaveData;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.IOException;

public class RCJMS extends JFrame {
    //region Variables
    public static RCJMS instance;
    public MainMenuView mainMenuView = new MainMenuView();
    public GameView gameView;
    public VictoryView victoryView;
    public TextureEditorView textureEditorView;
    public CreditsView creditsView;
    public MapEditorView mapEditorView;
    private JPanel currentView;
    private boolean fullscreen = false, f11Down = false, f8Down = false;

    public static final int SCREEN_WIDTH = 960;//DO NOT CHANGE UI WILL BE BROKEN
    public static final int SCREEN_HEIGHT = 540;//DO NOT CHANGE UI WILL BE BROKEN
    public static int GAME_WIDTH = SCREEN_WIDTH;
    public static int GAME_HEIGHT = SCREEN_HEIGHT;
    public static final Color MY_FAV_GRAY = new Color(28, 28, 32);
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

    //region Public API
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
    public void changeView(JPanel nextView, String nextTitle) {
        if (currentView != null) remove(currentView);
        add(nextView);
        currentView = nextView;
        setTitle(nextTitle);
        revalidate();
        repaint();
    }
    public static void main(String[] args) { SwingUtilities.invokeLater(() -> { try {new RCJMS();} catch (IOException e){throw new RuntimeException(e);}}); }
    //endregion

    //region Helpers
    private void installWindowHotkeys() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
            int code = e.getKeyCode();
            if (code != KeyEvent.VK_F11 && code != KeyEvent.VK_F8) return false;
            Component source = e.getSource() instanceof Component c ? c : null;
            Window window = source instanceof Window w ? w : source == null ? null : SwingUtilities.getWindowAncestor(source);
            if (window != this) return false;
            boolean pressed = e.getID() == KeyEvent.KEY_PRESSED;
            if (e.getID() != KeyEvent.KEY_PRESSED && e.getID() != KeyEvent.KEY_RELEASED) return false;
            boolean down = code == KeyEvent.VK_F11 ? f11Down : f8Down;
            if (pressed && !down)
                if (code == KeyEvent.VK_F11) setFullscreen(!fullscreen, true); else fixAspectRatio();
            if (code == KeyEvent.VK_F11) f11Down = pressed; else f8Down = pressed;
            return true;
        });
    }
    private void saveFullscreenSetting() {
        try {
            SaveData.Data data = SaveData.load();
            SaveData.saveSettings(data.language, data.theme, data.renderScale, data.vsync, data.rayTracing, data.rayTracingQuality, GAME_WIDTH, GAME_HEIGHT, fullscreen);
        } catch (IOException ex) { ex.printStackTrace(); }
    }
    //endregion
}