package Project.MainMenu;

import Project.Helpers.NSLocalizedString;
import Project.Net.*;
import Project.StyleUI.*;

import javax.swing.*;
import java.awt.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

public final class MainMenuMultiplayerScreen extends MainMenuPanel {
    //region Variables
    //region Layout
    private static final int CARD_X = 20, CARD_WIDTH = 920, CARD_PAD = 8;
    private static final int TITLE_TOP = 10, TITLE_HEIGHT = 32, BACK_WIDTH = 96, TITLE_X = 140, TITLE_WIDTH = 680;
    private static final int LEFT_X = 40, RIGHT_X = 492, SECTION_WIDTH = 428, DIVIDER_X = 480;
    private static final int SECTION_TOP = 48, CONTENT_TOP = 76;
    private static final int NAME_TOP = 94, PORT_CAPTION_TOP = 136, PORT_TOP = 154, SAVE_TOP = 196, SAVE_WIDTH = 130;
    private static final int HALF_GAP = 12, HALF_WIDTH = (SECTION_WIDTH - HALF_GAP) / 2;
    private static final int DIVIDER_TOP = 250, SEARCH_TOP = 274, SEARCH_HEIGHT = 30;
    private static final int STATUS_TOP = 316, STATUS_HEIGHT = 18;
    private static final int ROW_TOP = 346, ROW_STEP = 36, ROW_HEIGHT = 28, JOIN_WIDTH = 70, ROW_LABEL_WIDTH = 340;
    //endregion
    //region Limits
    private static final int MAX_ROWS = 5, MIN_PORT = 1024, MAX_PORT = 65535, MIN_PLAYERS = 2, MAX_PLAYERS = 8;
    private static final int NAME_LIMIT = 32, PORT_DIGITS = 5;
    private static final Duration SEARCH_TIMEOUT = Duration.ofSeconds(2);
    //endregion
    //endregion
    //region Widgets
    private final StyledLabel heading, nameCaption, portCaption, playersCaption, status;
    private final StyledTextField nameField, portField;
    private final StyledComboBox playersBox;
    private final StyledButton saveButton, searchButton, createButton, backButton;
    private final StyledLabel[] rowLabels = new StyledLabel[MAX_ROWS];
    private final StyledButton[] joinButtons = new StyledButton[MAX_ROWS];
    //endregion
    //region State
    private List<RoomDirectory.Room> rooms = List.of();
    private boolean busy;
    private int cardTop;
    //endregion

    //region Constructors
    public MainMenuMultiplayerScreen(MainMenuView menu) {
        super(menu);
        heading = label("mode.net.title", MainMenuCanvas.ACCENT);
        nameCaption = label("mode.net.name", MainMenuCanvas.TEXT);
        portCaption = label("mode.net.port", MainMenuCanvas.TEXT);
        playersCaption = label("mode.net.max_players", MainMenuCanvas.TEXT);
        status = label("", MainMenuCanvas.MUTED);
        nameField = new StyledTextField(menu.style(), "");
        portField = new StyledTextField(menu.style(), "");
        playersBox = new StyledComboBox(menu.style(), playerChoices());
        nameField.limitInput(NAME_LIMIT, c -> c >= ' ');
        portField.limitInput(PORT_DIGITS, MainMenuMultiplayerScreen::isDigit);
        portField.setHorizontalAlignment(JTextField.CENTER);
        saveButton = button("mode.net.save");
        searchButton = button("mode.net.search");
        createButton = button("mode.net.create");
        backButton = button("mm.back");

        saveButton.addActionListener(_ -> saveSettings());
        searchButton.addActionListener(_ -> search());
        createButton.addActionListener(_ -> create());
        backButton.addActionListener(_ -> menu.showMain());
        for (int i = 0; i < MAX_ROWS; i++) {
            int index = i;
            rowLabels[i] = label("", MainMenuCanvas.TEXT);
            joinButtons[i] = button("mode.net.join");
            joinButtons[i].addActionListener(_ -> join(index));
        }

        add(heading);
        add(nameCaption);
        add(nameField);
        add(portCaption);
        add(portField);
        add(playersCaption);
        add(playersBox);
        add(saveButton);
        add(searchButton);
        add(createButton);
        for (int i = 0; i < MAX_ROWS; i++) {
            add(rowLabels[i]);
            add(joinButtons[i]);
        }
        add(status);
        add(backButton);
        for (StyledButton joinButton : joinButtons) joinButton.setVisible(false);
        for (StyledLabel rowLabel : rowLabels) rowLabel.setVisible(false);
    }
    //endregion

    //region Layout
    @Override public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible) loadSettings();
    }
    @Override protected Runnable mazeAction() { return this::create; }
    @Override protected boolean mazeRoguelikeOption() { return true; }
    @Override protected void layoutDesign() {
        cardTop = (menu.screenHeight() - fullHeight()) / 2;
        setCard(CARD_X, cardTop, CARD_WIDTH, fullHeight());
        canvas.text(heading, 26, TITLE_X, cardTop + TITLE_TOP, TITLE_WIDTH, TITLE_HEIGHT);
        canvas.place(backButton, LEFT_X, cardTop + TITLE_TOP, BACK_WIDTH, TITLE_HEIGHT);
        placeMaze(LEFT_X, cardTop + CONTENT_TOP);
        placeMazeStart(createButton, LEFT_X, cardTop + CONTENT_TOP);
        layoutNetwork();
    }
    private static int fullHeight() {
        int rowsBottom = ROW_TOP + (MAX_ROWS - 1) * ROW_STEP + ROW_HEIGHT;
        return Math.max(CONTENT_TOP + MazeSettingsPanel.maxDesignHeight(), rowsBottom) + CARD_PAD;
    }
    private void layoutNetwork() {
        canvas.textLeft(nameCaption, 12, RIGHT_X, cardTop + SECTION_TOP + 28, SECTION_WIDTH, 16);
        canvas.place(nameField, RIGHT_X, cardTop + NAME_TOP, SECTION_WIDTH, 30);
        canvas.text(portCaption, 12, RIGHT_X, cardTop + PORT_CAPTION_TOP, HALF_WIDTH, 16);
        canvas.place(portField, RIGHT_X, cardTop + PORT_TOP, HALF_WIDTH, 30);
        int playersX = RIGHT_X + HALF_WIDTH + HALF_GAP;
        canvas.text(playersCaption, 12, playersX, cardTop + PORT_CAPTION_TOP, HALF_WIDTH, 16);
        canvas.combo(playersBox, playersX, cardTop + PORT_TOP, HALF_WIDTH, 30);
        canvas.place(saveButton, RIGHT_X, cardTop + SAVE_TOP, SAVE_WIDTH, 30);
        canvas.place(searchButton, RIGHT_X, cardTop + SEARCH_TOP, SECTION_WIDTH, SEARCH_HEIGHT);
        canvas.text(status, 12, RIGHT_X, cardTop + STATUS_TOP, SECTION_WIDTH, STATUS_HEIGHT);
        for (int i = 0; i < MAX_ROWS; i++) {
            int y = cardTop + ROW_TOP + i * ROW_STEP;
            canvas.textLeft(rowLabels[i], 12, RIGHT_X, y + 3, ROW_LABEL_WIDTH, 22);
            canvas.place(joinButtons[i], RIGHT_X + SECTION_WIDTH - JOIN_WIDTH, y, JOIN_WIDTH, ROW_HEIGHT);
        }
    }
    @Override protected void paintDecorations(Graphics graphics) {
        Graphics2D g2d = (Graphics2D) graphics.create();
        try {
            g2d.setColor(new Color(71, 73, 79));
            double scale = canvas.scale();
            int thickness = Math.max(1, (int) Math.round(scale));
            int sectionHeight = fullHeight() - CARD_PAD - SECTION_TOP;
            g2d.fillRect(canvas.x(DIVIDER_X), canvas.y(cardTop + SECTION_TOP), thickness, (int) Math.round(sectionHeight * scale));
            g2d.fillRect(canvas.x(RIGHT_X), canvas.y(cardTop + DIVIDER_TOP), (int) Math.round(SECTION_WIDTH * scale), thickness);
        } finally { g2d.dispose(); }
    }
    private static String[] playerChoices() {
        String[] choices = new String[MAX_PLAYERS - MIN_PLAYERS + 1];
        for (int i = 0; i < choices.length; i++) choices[i] = String.valueOf(MIN_PLAYERS + i);
        return choices;
    }
    //endregion

    //region Settings
    private void loadSettings() {
        nameField.setText(NetSettings.playerName());
        portField.setText(String.valueOf(NetSettings.port()));
        playersBox.setSelectedIndex(NetSettings.maxPlayers() - MIN_PLAYERS);
    }
    private boolean saveSettings() {
        Integer port = parseInteger(portField.getText());
        if (port == null || port < MIN_PORT || port > MAX_PORT) {
            setStatus(NSLocalizedString.get("mode.net.status.bad_port"));
            return false;
        }
        NetSettings.setPlayerName(nameField.getText());
        NetSettings.setPort(port);
        NetSettings.setMaxPlayers(playersBox.getSelectedIndex() + MIN_PLAYERS);
        loadSettings();
        setStatus(NSLocalizedString.get("mode.net.status.saved"));
        return true;
    }
    //endregion

    //region Rooms
    private void search() {
        runAsync(NSLocalizedString.get("mode.net.status.searching"), () -> {
            List<RoomDirectory.Room> found = new ArrayList<>(RoomDirectory.search(SEARCH_TIMEOUT));
            return () -> showRooms(found);
        });
    }
    private void showRooms(List<RoomDirectory.Room> found) {
        rooms = List.copyOf(found);
        for (int i = 0; i < MAX_ROWS; i++) {
            boolean shown = i < rooms.size();
            rowLabels[i].setVisible(shown);
            joinButtons[i].setVisible(shown);
            if (!shown) continue;
            RoomDirectory.Room room = rooms.get(i);
            rowLabels[i].setText(NSLocalizedString.format("mode.net.row", room.name(), room.players(), room.maxPlayers(),
                    room.spec().seed(), room.spec().width(), room.spec().height()));
            joinButtons[i].setEnabled(room.players() < room.maxPlayers());
        }
        setStatus(rooms.isEmpty() ? NSLocalizedString.get("mode.net.status.none") : NSLocalizedString.format("mode.net.status.found", rooms.size()));
    }
    private void join(int index) {
        if (index >= rooms.size()) return;
        RoomDirectory.Room room = rooms.get(index);
        if (room.players() >= room.maxPlayers()) {
            setStatus(NSLocalizedString.get("mode.net.status.full"));
            return;
        }
        runAsync(NSLocalizedString.format("mode.net.status.joining", room.host(), room.port()), () -> {
            NetSession.Joined joined = NetSession.join(room.host(), room.port());
            return () -> startWith(joined);
        });
    }
    private void create() {
        if (!saveSettings()) return;
        MazeSpec spec = currentSpec();
        if (spec == null) return;
        int port = NetSettings.port();
        menu.maze().applyMazeSpec(spec);
        menu.maze().saveMazeSettings();
        runAsync(NSLocalizedString.format("mode.net.status.hosting", port), () -> {
            NetSession previous = NetSession.current();
            if (previous != null) previous.close();
            Project.Helpers.MazeData maze = NetSession.buildMaze(spec);
            NetSession.host(port, spec, maze);
            return () -> menu.startRace(spec, maze);
        });
    }
    private MazeSpec currentSpec() {
        try { return MainMenuMultiplayerSpec.fromMaze(menu.maze()); }
        catch (NumberFormatException exception) {
            setStatus(NSLocalizedString.get("mode.net.status.maze_size"));
            return null;
        }
    }
    private void startWith(NetSession.Joined joined) { menu.startRace(joined.spec(), joined.maze()); }
    //endregion

    //region Background
    private void runAsync(String waitingText, Callable<Runnable> work) {
        if (busy) {
            setStatus(NSLocalizedString.get("mode.net.status.busy"));
            return;
        }
        busy = true;
        setStatus(waitingText);
        Thread worker = new Thread(() -> {
            Runnable done = attempt(work);
            SwingUtilities.invokeLater(() -> {
                busy = false;
                done.run();
            });
        }, "multiplayer-net");
        worker.setDaemon(true);
        worker.start();
    }
    private Runnable attempt(Callable<Runnable> work) {
        try { return work.call(); }
        catch (Exception exception) {
            String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            return () -> setStatus(NSLocalizedString.format("mode.net.status.error", message));
        }
    }
    //endregion

    //region Text
    private void setStatus(String text) {
        status.setText(text);
        doLayout();
    }
    private static boolean isDigit(int codePoint) { return codePoint >= '0' && codePoint <= '9'; }
    private static Integer parseInteger(String text) {
        try { return Integer.parseInt(text.trim()); }
        catch (NumberFormatException exception) { return null; }
    }
    //endregion
}