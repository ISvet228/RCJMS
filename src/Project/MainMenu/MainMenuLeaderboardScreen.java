package Project.MainMenu;

import Project.Progress.Leaderboard;
import Project.Helpers.*;
import Project.StyleUI.*;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Map;

public final class MainMenuLeaderboardScreen extends MainMenuPanel {
    //region Variables
    //region Layout
    private static final int CARD_X = 130, CARD_WIDTH = 700, CARD_HEIGHT = 440;
    private static final int ROW_TOP = 92, ROW_STEP = 36, MAX_ROWS = 8, MAX_TIMES = 5;
    private static final int TEXT_LEFT_PAD = 20, TEXT_RIGHT_PAD = 20;
    private static final int MORE_TOP = 384, FOOTER_TOP = 398;
    //endregion
    //region Keys
    private static final String[] GEOMETRY_KEYS = {"mm.euclidean_geometry", "wrong_geometry", "looped_geometry"};
    //endregion
    //endregion
    //region Widgets
    private final StyledLabel heading;
    private final StyledButton backButton;
    //endregion
    //region State
    private Map<String, List<Leaderboard.Entry>> boards = Map.of();
    private int cardTop;
    //endregion

    //region Constructors
    public MainMenuLeaderboardScreen(MainMenuView menu) {
        super(menu);
        heading = label("mm.leaderboard", MainMenuCanvas.ACCENT);
        backButton = button("mm.back");
        backButton.addActionListener(_ -> menu.showMain());

        add(heading);
        add(backButton);
    }
    //endregion

    //region Layout
    @Override public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible) boards = Leaderboard.all();
    }
    @Override protected void layoutDesign() {
        cardTop = (menu.screenHeight() - CARD_HEIGHT) / 2;
        setCard(CARD_X, cardTop, CARD_WIDTH, CARD_HEIGHT);
        canvas.text(heading, 26, CARD_X, cardTop + 18, CARD_WIDTH, 36);
        canvas.place(backButton, CARD_X + (CARD_WIDTH - 150) / 2, cardTop + FOOTER_TOP, 150, 34);
    }
    //endregion

    //region Painting
    @Override protected void paintDecorations(Graphics graphics) {
        Graphics2D g2d = (Graphics2D) graphics.create();
        try {
            g2d.translate(canvas.x(0), canvas.y(0));
            g2d.scale(canvas.scale(), canvas.scale());
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            if (boards.isEmpty()) paintEmpty(g2d);
            int index = 0;
            for (Map.Entry<String, List<Leaderboard.Entry>> board : boards.entrySet()) {
                if (index == MAX_ROWS) break;
                paintRow(g2d, board.getKey(), board.getValue(), index++);
            }
            if (boards.size() > MAX_ROWS) paintMore(g2d, boards.size() - MAX_ROWS);
        } finally { g2d.dispose(); }
    }
    private void paintEmpty(Graphics2D g2d) {
        g2d.setFont(FontCache.get(Font.SANS_SERIF, Font.PLAIN, 14f));
        g2d.setColor(MainMenuCanvas.MUTED);
        String text = NSLocalizedString.get("mm.lb_empty");
        int width = g2d.getFontMetrics().stringWidth(text);
        g2d.drawString(text, CARD_X + (CARD_WIDTH - width) / 2, cardTop + ROW_TOP + 40);
    }
    private void paintRow(Graphics2D g2d, String key, List<Leaderboard.Entry> entries, int index) {
        int y = cardTop + ROW_TOP + index * ROW_STEP;
        int left = CARD_X + TEXT_LEFT_PAD, right = CARD_X + CARD_WIDTH - TEXT_RIGHT_PAD;
        g2d.setFont(FontCache.get(Font.SANS_SERIF, Font.BOLD, 14f));
        g2d.setColor(MainMenuCanvas.TEXT);
        g2d.drawString(title(key), left, y + 14);
        g2d.setFont(FontCache.get(Font.SANS_SERIF, Font.PLAIN, 11f));
        g2d.setColor(MainMenuCanvas.MUTED);
        g2d.drawString(setup(key), left, y + 29);
        String times = times(entries);
        g2d.setFont(FontCache.get(Font.SANS_SERIF, Font.BOLD, 13f));
        g2d.setColor(MainMenuCanvas.ACCENT);
        g2d.drawString(times, right - g2d.getFontMetrics().stringWidth(times), y + 14);
    }
    private void paintMore(Graphics2D g2d, int hidden) {
        g2d.setFont(FontCache.get(Font.SANS_SERIF, Font.PLAIN, 12f));
        g2d.setColor(MainMenuCanvas.MUTED);
        g2d.drawString(NSLocalizedString.format("mm.more_items", hidden), CARD_X + TEXT_LEFT_PAD, cardTop + MORE_TOP);
    }
    //endregion

    //region Text
    private static String[] parts(String key) { return key.split("\\|", -1); }
    private static String title(String key) {
        String seed = parts(key)[0];
        return seed.isBlank() ? NSLocalizedString.get("mm.lb_random") : seed;
    }
    private static String setup(String key) {
        String[] parts = parts(key);
        String size = parts.length > 1 ? parts[1] : "";
        boolean hasFloors = parts.length > 3 && "floors".equals(parts[3]);
        String floors = parts.length > 2 ? parts[2].replace("f", "") : "";
        String dimension = hasFloors ? NSLocalizedString.format("mm.lb_3d", floors) : NSLocalizedString.get("mm.lb_2d");
        return NSLocalizedString.format("mm.lb_setup", size, dimension, geometry(parts));
    }
    private static String geometry(String[] parts) {
        try {
            int index = Integer.parseInt(parts.length > 4 ? parts[4].replace("g", "") : "0");
            return NSLocalizedString.get(GEOMETRY_KEYS[Math.clamp(index, 0, GEOMETRY_KEYS.length - 1)]);
        } catch (NumberFormatException exception) { return ""; }
    }
    private static String times(List<Leaderboard.Entry> entries) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < Math.min(entries.size(), MAX_TIMES); i++) {
            if (i > 0) text.append("   ");
            text.append(NSLocalizedString.format("mm.lb_seconds", entries.get(i).seconds()));
        }
        return text.toString();
    }
    //endregion
}