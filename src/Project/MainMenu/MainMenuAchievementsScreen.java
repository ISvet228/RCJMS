package Project.MainMenu;

import Project.Progress.Achievements;
import Project.Progress.Achievements.Achievement;
import Project.Helpers.*;
import Project.StyleUI.*;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public final class MainMenuAchievementsScreen extends MainMenuPanel {
    //region Variables
    //region Paging
    public static final int COLUMNS = 4, ROWS = 2;
    public static final int PAGE_SIZE = COLUMNS * ROWS;
    //endregion
    //region Layout
    private static final int CARD_X = 130, CARD_WIDTH = 700, CARD_HEIGHT = 440;
    private static final int GRID_SIDE = 20, CELL_WIDTH = (CARD_WIDTH - 2 * GRID_SIDE) / COLUMNS, CELL_TEXT_PAD = 4;
    private static final int GRID_TOP = 88, CELL_HEIGHT = 136, BADGE = 44, NAME_LINE = 15, DESC_LINE = 13;
    private static final int NAME_LINES = 2, DESC_LINES = 3;
    private static final int COUNT_TOP = 62, PAGE_TOP = 372, FOOTER_TOP = 398;
    private static final int BUTTON_WIDTH = 150, BUTTON_HEIGHT = 34, SIDE_INSET = 40;
    //endregion
    //endregion
    //region Widgets
    private final StyledLabel heading;
    private final StyledButton previousButton;
    private final StyledButton backButton;
    private final StyledButton nextButton;
    //endregion
    //region State
    private List<Achievement> achievements = List.of();
    private int page;
    private int cardTop;
    //endregion

    //region Constructors
    public MainMenuAchievementsScreen(MainMenuView menu) {
        super(menu);
        heading = label("mm.achievements", MainMenuCanvas.ACCENT);
        previousButton = button("mm.ach_prev");
        backButton = button("mm.back");
        nextButton = button("mm.ach_next");
        previousButton.addActionListener(_ -> turn(-1));
        nextButton.addActionListener(_ -> turn(1));
        backButton.addActionListener(_ -> menu.showMain());

        add(heading);
        add(previousButton);
        add(backButton);
        add(nextButton);
    }
    //endregion

    //region Paging
    public static int pageCount(int items) { return Math.max(1, (items + PAGE_SIZE - 1) / PAGE_SIZE); }
    public static int pageStart(int page) { return page * PAGE_SIZE; }
    public static int pageEnd(int items, int page) { return Math.min(items, pageStart(page) + PAGE_SIZE); }
    public static int clampPage(int page, int items) { return Math.clamp(page, 0, pageCount(items) - 1); }
    private void turn(int step) {
        int next = clampPage(page + step, achievements.size());
        if (next == page) return;
        page = next;
        repaint();
    }
    //endregion

    //region Layout
    @Override public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (!visible) return;
        achievements = Achievements.all();
        page = 0;
    }
    @Override protected void layoutDesign() {
        cardTop = (menu.screenHeight() - CARD_HEIGHT) / 2;
        setCard(CARD_X, cardTop, CARD_WIDTH, CARD_HEIGHT);
        canvas.text(heading, 26, CARD_X, cardTop + 18, CARD_WIDTH, 36);
        canvas.place(previousButton, CARD_X + SIDE_INSET, cardTop + FOOTER_TOP, BUTTON_WIDTH, BUTTON_HEIGHT);
        canvas.place(backButton, CARD_X + (CARD_WIDTH - BUTTON_WIDTH) / 2, cardTop + FOOTER_TOP, BUTTON_WIDTH, BUTTON_HEIGHT);
        canvas.place(nextButton, CARD_X + CARD_WIDTH - SIDE_INSET - BUTTON_WIDTH, cardTop + FOOTER_TOP, BUTTON_WIDTH, BUTTON_HEIGHT);
    }
    //endregion

    //region Painting
    @Override protected void paintDecorations(Graphics graphics) {
        Graphics2D g2d = (Graphics2D) graphics.create();
        try {
            g2d.translate(canvas.x(0), canvas.y(0));
            g2d.scale(canvas.scale(), canvas.scale());
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            page = clampPage(page, achievements.size());
            paintCount(g2d);
            int start = pageStart(page), end = pageEnd(achievements.size(), page);
            for (int i = start; i < end; i++) paintCell(g2d, achievements.get(i), i - start);
            paintPage(g2d);
        } finally { g2d.dispose(); }
    }
    private void paintCount(Graphics2D g2d) {
        String text = NSLocalizedString.format("mm.ach_ratio", Achievements.unlockedCount(), achievements.size());
        g2d.setFont(FontCache.get(Font.SANS_SERIF, Font.BOLD, 14f));
        g2d.setColor(MainMenuCanvas.TEXT);
        centered(g2d, text, CARD_X, CARD_WIDTH, cardTop + COUNT_TOP);
    }
    private void paintCell(Graphics2D g2d, Achievement achievement, int slot) {
        int left = CARD_X + GRID_SIDE + (slot % COLUMNS) * CELL_WIDTH;
        int top = cardTop + GRID_TOP + (slot / COLUMNS) * CELL_HEIGHT;
        boolean unlocked = Achievements.isUnlocked(achievement.id());
        Achievements.paintIcon(g2d, achievement, unlocked, left + (CELL_WIDTH - BADGE) / 2, top, BADGE);

        int textWidth = CELL_WIDTH - 2 * CELL_TEXT_PAD;
        int y = top + BADGE + 10;
        g2d.setFont(FontCache.get(Font.SANS_SERIF, Font.BOLD, 12f));
        g2d.setColor(unlocked ? MainMenuCanvas.TEXT : MainMenuCanvas.MUTED);
        for (String line : wrap(g2d.getFontMetrics(), NSLocalizedString.get(achievement.nameKey()), textWidth, NAME_LINES)) {
            centered(g2d, line, left, CELL_WIDTH, y);
            y += NAME_LINE;
        }
        y += 4;
        g2d.setFont(FontCache.get(Font.SANS_SERIF, Font.PLAIN, 11f));
        g2d.setColor(MainMenuCanvas.MUTED);
        for (String line : wrap(g2d.getFontMetrics(), NSLocalizedString.get(achievement.descKey()), textWidth, DESC_LINES)) {
            centered(g2d, line, left, CELL_WIDTH, y);
            y += DESC_LINE;
        }
    }
    private void paintPage(Graphics2D g2d) {
        String text = NSLocalizedString.format("mm.ach_page", page + 1, pageCount(achievements.size()));
        g2d.setFont(FontCache.get(Font.SANS_SERIF, Font.PLAIN, 12f));
        g2d.setColor(MainMenuCanvas.MUTED);
        centered(g2d, text, CARD_X, CARD_WIDTH, cardTop + PAGE_TOP);
    }
    private static void centered(Graphics2D g2d, String text, int left, int width, int baseline) {
        int x = left + (width - g2d.getFontMetrics().stringWidth(text)) / 2;
        g2d.drawString(text, x, baseline);
    }
    //endregion

    //region Text
    static List<String> wrap(FontMetrics metrics, String text, int width, int maxLines) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (line.isEmpty() || metrics.stringWidth(candidate) <= width) {
                line.setLength(0);
                line.append(candidate);
            } else {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            }
        }
        if (!line.isEmpty()) lines.add(line.toString());
        if (lines.size() <= maxLines) return lines;
        List<String> kept = new ArrayList<>(lines.subList(0, maxLines));
        kept.set(maxLines - 1, kept.get(maxLines - 1) + "...");
        return kept;
    }
    //endregion
}