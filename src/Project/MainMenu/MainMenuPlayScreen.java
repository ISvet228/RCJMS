package Project.MainMenu;

import Project.StyleUI.*;

public final class MainMenuPlayScreen extends MainMenuPanel {
    //region Layout
    private static final int CARD_X = 200, CARD_WIDTH = 560;
    private static final int HEADING_TOP = 20, BACK_TOP = 20, HINT_TOP = 62, MAZE_TOP = 92, MAZE_MARGIN = 2;
    private static final int CONTENT_X = 230, CONTENT_WIDTH = 500, BACK_WIDTH = 96;
    //endregion

    //region Widgets
    private final StyledLabel heading, hint;
    private final StyledButton backButton, startButton;
    //endregion

    //region Constructors
    public MainMenuPlayScreen(MainMenuView menu) {
        super(menu);
        heading = label("mm.new_game", MainMenuCanvas.ACCENT);
        hint = label("mm.play_hint", MainMenuCanvas.MUTED);
        backButton = button("mm.back");
        startButton = button("mm.start");

        backButton.addActionListener(_ -> menu.showMain());
        startButton.addActionListener(_ -> menu.startGame());

        add(heading);
        add(hint);
        add(backButton);
        add(startButton);
        setComponentZOrder(backButton, 0);
    }
    //endregion

    //region Layout
    @Override protected void layoutDesign() {
        int height = MAZE_TOP + mazeHeight() + MAZE_MARGIN;
        int top = (menu.screenHeight() - height) / 2;
        setCard(CARD_X, top, CARD_WIDTH, height);
        canvas.place(backButton, CONTENT_X, top + BACK_TOP, BACK_WIDTH, 36);
        canvas.text(heading, 26, CARD_X, top + HEADING_TOP, CARD_WIDTH, 36);
        canvas.text(hint, 13, CONTENT_X, top + HINT_TOP, CONTENT_WIDTH, 18);
        placeMaze(CONTENT_X, top + MAZE_TOP);
        placeMazeStart(startButton, CONTENT_X, top + MAZE_TOP);
    }
    //endregion
}