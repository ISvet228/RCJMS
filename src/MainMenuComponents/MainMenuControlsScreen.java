package MainMenuComponents;

import StyleUI.*;

import javax.swing.*;
import java.awt.*;

public final class MainMenuControlsScreen extends MainMenuPanel {
    //region Variables
    private static final String[] LINE_KEYS = {"if.move", "run", "mm.ctrl_mouse", "mm.ctrl_pause", "gv.restart", "mm.ctrl_fullscreen", "mm.ctrl_aspect"};

    private final StyledLabel heading;
    private final StyledLabel[] lines = new StyledLabel[LINE_KEYS.length];
    private final StyledButton backButton;
    //endregion

    //region Constructors
    public MainMenuControlsScreen(MainMenuHost menu) {
        super(menu);
        heading = label("mm.controls", MainMenuCanvas.ACCENT);
        for (int i = 0; i < LINE_KEYS.length; i++) lines[i] = label(LINE_KEYS[i], MainMenuCanvas.TEXT);
        backButton = button("mm.back");
        backButton.addActionListener(e -> menu.showMain());

        add(heading);
        for (StyledLabel line : lines) add(line);
        add(backButton);
    }
    //endregion

    //region Layout
    @Override protected void layoutDesign() {
        int height = 306;
        int top = (menu.screenHeight() - height) / 2;
        setCard(280, top, 400, height);
        canvas.text(heading, 26, 280, top + 22, 400, 40);
        for (int i = 0; i < lines.length; i++) canvas.text(lines[i], 16, 280, top + 74 + i * 24, 400, 22);
        canvas.place(backButton, 430, top + 254, 100, 36);
    }
    //endregion
}