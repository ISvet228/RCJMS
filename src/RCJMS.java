import Helpers.SaveData;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

public class RCJMS extends JFrame {
    public static RCJMS instance;
    public MainMenuView mainMenuView = new MainMenuView();
    public GameView gameView;
    public VictoryView victoryView;
    public TextureEditorView textureEditorView;
    public CreditsView creditsView;
    public MapEditorView mapEditorView;
    private JPanel currentView;

    public static final int SCREEN_WIDTH = 960;//DO NOT CHANGE UI WILL BE BROKEN
    public static final int SCREEN_HEIGHT = 540;//DO NOT CHANGE UI WILL BE BROKEN
    public static int GAME_WIDTH = SCREEN_WIDTH;
    public static int GAME_HEIGHT = SCREEN_HEIGHT;
    public static final Color MY_FAV_GRAY = new Color(28, 28, 32);

    public RCJMS() throws IOException {
        instance = this;
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        SaveData.Data savedData = SaveData.load();
        GAME_WIDTH = savedData.windowWidth;
        GAME_HEIGHT = savedData.windowHeight;
        if (savedData.fullscreen) {
            setUndecorated(true);
            setExtendedState(JFrame.MAXIMIZED_BOTH);
        }
        else setSize(savedData.windowWidth, savedData.windowHeight);

        setLocationRelativeTo(null);
        ChangeView(mainMenuView, "main_menu");
        setVisible(true);
    }
    public void ChangeView(JPanel nextView, String nextTitle) {
        if (currentView != null) remove(currentView);
        add(nextView);
        currentView = nextView;
        setTitle(nextTitle);
        revalidate();
        repaint();
    }
    public static void main(String[] args) { SwingUtilities.invokeLater(() -> { try {new RCJMS();} catch (IOException e){throw new RuntimeException(e);}}); }
}