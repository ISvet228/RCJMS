package Project.MainMenu;

import Project.Core.KeyBindings;
import Project.Core.KeyBindings.Action;
import Project.Helpers.NSLocalizedString;
import Project.StyleUI.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public final class MainMenuControlsScreen extends MainMenuPanel {
    //region Variables
    //region Content
    private static Action[] visibleActions() { return KeyBindings.all(); }
    private static final Action[] ACTIONS = visibleActions();
    private static final String[] NOTE_KEYS = {"mm.ctrl_mouse", "mm.ctrl_fullscreen", "mm.ctrl_aspect"};
    private static final int GROUP_MOVE = 0, GROUP_ACT = 1, GROUP_TOOLS = 2, GROUP_NOTES = 3;
    private static final String[] GROUP_KEYS = {"kb.group.move", "kb.group.act", "kb.group.tools", "kb.group.notes"};
    //endregion
    //region Layout
    private static final int CARD_X = 100, CARD_WIDTH = 760, CARD_HEIGHT = 440;
    private static final int LEFT_X = 130, RIGHT_X = 500, ROW_WIDTH = 330, ROW_HEIGHT = 24, ROW_STEP = 26;
    private static final int GROUP_TOP = 88, GROUP_HEADER = 22, GROUP_GAP = 14, NOTE_STEP = 18;
    private static final int FOOTER_TOP = 396;
    //endregion
    //endregion
    //region Widgets
    private final StyledLabel heading, hint;
    private final StyledLabel[] groupLabels = new StyledLabel[GROUP_KEYS.length];
    private final StyledButton[] rows = new StyledButton[ACTIONS.length];
    private final StyledLabel[] notes = new StyledLabel[NOTE_KEYS.length];
    private final StyledButton resetButton, backButton;
    //endregion
    //region State
    private Action waiting;
    //endregion

    //region Constructors
    public MainMenuControlsScreen(MainMenuView menu) {
        super(menu);
        heading = label("controls", MainMenuCanvas.ACCENT);
        hint = label("kb.hint", MainMenuCanvas.MUTED);
        for (int g = 0; g < GROUP_KEYS.length; g++) groupLabels[g] = label(GROUP_KEYS[g], MainMenuCanvas.ACCENT);
        for (int i = 0; i < ACTIONS.length; i++) {
            Action action = ACTIONS[i];
            rows[i] = button("");
            rows[i].addActionListener(_ -> startListening(action));
        }
        for (int i = 0; i < NOTE_KEYS.length; i++) notes[i] = label(NOTE_KEYS[i], MainMenuCanvas.MUTED);
        resetButton = button("kb.reset");
        resetButton.addActionListener(_ -> resetAll());
        backButton = button("mm.back");
        backButton.addActionListener(_ -> menu.showMain());

        setFocusable(true);
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent keyEvent) {
                if (waiting == null) return;
                keyEvent.consume();
                finishListening(keyEvent.getKeyCode());
            }
        });

        add(heading);
        add(hint);
        for (StyledLabel groupLabel : groupLabels) add(groupLabel);
        for (StyledButton row : rows) add(row);
        for (StyledLabel note : notes) add(note);
        add(resetButton);
        add(backButton);
        refreshRows();
    }
    //endregion

    //region Layout
    @Override protected void layoutDesign() {
        int top = (menu.screenHeight() - CARD_HEIGHT) / 2;
        setCard(CARD_X, top, CARD_WIDTH, CARD_HEIGHT);
        canvas.text(heading, 26, CARD_X, top + 18, CARD_WIDTH, 36);
        canvas.text(hint, 13, CARD_X, top + 56, CARD_WIDTH, 18);

        int leftBottom = placeGroup(GROUP_MOVE, LEFT_X, top + GROUP_TOP);
        int rightBottom = placeGroup(GROUP_ACT, RIGHT_X, top + GROUP_TOP);
        rightBottom = placeGroup(GROUP_TOOLS, RIGHT_X, rightBottom + GROUP_GAP);

        int y = leftBottom + GROUP_GAP;
        canvas.text(groupLabels[GROUP_NOTES], 12, LEFT_X, y, ROW_WIDTH, GROUP_HEADER);
        y += GROUP_HEADER;
        for (StyledLabel note : notes) {
            canvas.text(note, 12, LEFT_X, y, ROW_WIDTH, NOTE_STEP);
            y += NOTE_STEP;
        }

        canvas.place(resetButton, 520, top + FOOTER_TOP, 150, 30);
        canvas.place(backButton, 690, top + FOOTER_TOP, 150, 30);
    }
    private int placeGroup(int group, int x, int y) {
        canvas.text(groupLabels[group], 12, x, y, ROW_WIDTH, GROUP_HEADER);
        y += GROUP_HEADER;
        for (int i = 0; i < ACTIONS.length; i++) {
            if (groupOf(ACTIONS[i]) != group) continue;
            canvas.place(rows[i], x, y, ROW_WIDTH, ROW_HEIGHT);
            y += ROW_STEP;
        }
        return y;
    }
    private static int groupOf(Action action) {
        return switch (action) {
            case FORWARD, BACK, LEFT, RIGHT, SPRINT -> GROUP_MOVE;
            case ATTACK, POTION, PAUSE, RESTART -> GROUP_ACT;
            default -> GROUP_TOOLS;
        };
    }
    //endregion

    //region Listening
    @Override public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible) refreshRows();
        else cancelListening();
    }
    private void startListening(Action action) {
        waiting = action;
        hint.setText(NSLocalizedString.format("kb.press_key", NSLocalizedString.get(action.labelKey())));
        requestFocusInWindow();
        refreshRows();
    }
    private void finishListening(int keyCode) {
        Action action = waiting;
        waiting = null;
        if (keyCode != KeyEvent.VK_ESCAPE && keyCode != KeyEvent.VK_UNDEFINED) assign(action, keyCode);
        cancelListening();
    }
    private void cancelListening() {
        waiting = null;
        hint.setLocalizationKey("kb.hint");
        refreshRows();
    }
    private static void assign(Action action, int keyCode) {
        int previous = KeyBindings.keyOf(action);
        for (Action other : ACTIONS)
            if (other != action && KeyBindings.keyOf(other) == keyCode) KeyBindings.rebind(other, previous);
        KeyBindings.rebind(action, keyCode);
    }
    private void resetAll() {
        cancelListening();
        KeyBindings.resetDefaults();
        refreshRows();
    }
    private void refreshRows() {
        if (rows[0] == null) return;
        for (int i = 0; i < ACTIONS.length; i++) rows[i].setText(rowText(ACTIONS[i]));
    }
    private String rowText(Action action) {
        String key = action == waiting ? NSLocalizedString.get("kb.waiting") : KeyBindings.keyName(action);
        return NSLocalizedString.get(action.labelKey()) + "  [" + key + "]";
    }
    //endregion
}