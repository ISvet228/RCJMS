package Project.MainMenu;

import Project.Core.KeyBindings;
import Project.Helpers.NSLocalizedString;
import Project.StyleUI.*;

public final class MainMenuRoguelikeScreen extends MainMenuPanel {
    //region Layout
    private static final int CARD_X = 130, CARD_WIDTH = 700;
    private static final int HEADING_TOP = 8, LINE_TOP = 46, MAZE_TOP = 84, MAZE_MARGIN = 8;
    private static final int BACK_WIDTH = 96, LINE_HEIGHT = 18;
    //endregion
    //region Settings
    //endregion
    //region Widgets
    private final StyledLabel heading, keysLine;
    private final StyledButton startButton, backButton;
    //endregion

    //region Constructors
    public MainMenuRoguelikeScreen(MainMenuView menu) {
        super(menu);
        heading = label("mode.rogue.title", MainMenuCanvas.ACCENT);
        keysLine = label("", MainMenuCanvas.TEXT);
        startButton = button("mode.rogue.start");
        backButton = button("mm.back");

        startButton.addActionListener(_ -> startRun());
        backButton.addActionListener(_ -> menu.showMain());

        add(heading);
        add(keysLine);
        add(startButton);
        add(backButton);
    }
    //endregion

    //region Layout
    @Override public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (!visible) return;
        keysLine.setText(NSLocalizedString.format("mode.rogue.keys", KeyBindings.keyName(KeyBindings.Action.ATTACK), KeyBindings.keyName(KeyBindings.Action.POTION)));
    }
    @Override protected void layoutDesign() {
        int height = MAZE_TOP + mazeHeight() + MAZE_MARGIN;
        int top = (menu.screenHeight() - height) / 2;
        setCard(CARD_X, top, CARD_WIDTH, height);
        canvas.place(backButton, CARD_X + 24, top + HEADING_TOP, BACK_WIDTH, 34);
        canvas.text(heading, 26, CARD_X + 24 + BACK_WIDTH, top + HEADING_TOP, CARD_WIDTH - 2 * (24 + BACK_WIDTH), 34);
        canvas.text(keysLine, 13, CARD_X + 40, top + LINE_TOP, CARD_WIDTH - 80, LINE_HEIGHT);
        int mazeX = CARD_X + (CARD_WIDTH - MazeSettingsPanel.WIDTH) / 2;
        placeMaze(mazeX, top + MAZE_TOP);
        placeMazeStart(startButton, mazeX, top + MAZE_TOP);
    }
    //endregion

    private void startRun() { menu.startGame(true); }
}