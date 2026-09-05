import javax.swing.*;
import java.io.IOException;

public class RCJMS extends JFrame {
    public static RCJMS instance;
    public MainMenuView mainMenuView = new MainMenuView();
    public GameView gameView;
    public VictoryView victoryView;
    public TextureEditorView textureEditorView = new TextureEditorView();

    public static final int SCREEN_WIDTH = 960;
    public static final int SCREEN_HEIGHT = 540;

    public RCJMS() throws IOException {
        instance = this;
        setTitle("Main Menu");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(SCREEN_WIDTH, SCREEN_HEIGHT);
        setLocationRelativeTo(null);
        add(mainMenuView);
        setVisible(true);
    }
    static void main(String[] args) {
        SwingUtilities.invokeLater(() -> { try {new RCJMS();}
            catch (IOException e){throw new RuntimeException(e);}});
    }
}