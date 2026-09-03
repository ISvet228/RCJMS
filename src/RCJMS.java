import javax.swing.*;

public class RCJMS extends JFrame {
    public static RCJMS instance;
    public MainMenuView mainMenuView = new MainMenuView();
    public GameView gameView;
    public VictoryView victoryPanel;

    public static final int SCREEN_WIDTH = 960;
    public static final int SCREEN_HEIGHT = 540;

    public RCJMS() {
        instance = this;
        setTitle("Main Menu");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(SCREEN_WIDTH, SCREEN_HEIGHT);
        setLocationRelativeTo(null);
        add(mainMenuView);
        setResizable(false);
        setVisible(true);
    }
    static void main(String[] args) {
        SwingUtilities.invokeLater(RCJMS::new);
    }
}