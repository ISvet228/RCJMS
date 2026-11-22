package Project.Game.Pause;

import Project.Core.KeyBindings;
import Project.Helpers.*;
import Project.Audio.Music;
import Project.Game.Fov;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.List;

public final class PauseMenuView {
    public interface Listener { void onItem(PauseMenuState.Item item); }

    //region Variables
    private static final String MUSIC_KEY = "music.volume", EFFECTS_KEY = "sound.volume";
    private final PauseMenuState state;
    private final Listener listener;
    private boolean hudOn = true;
    private String filterName = "";
    private int dragRow = -1;
    private volatile Rectangle[] rowRects = new Rectangle[0];
    private volatile Rectangle[] trackRects = new Rectangle[0];
    //endregion

    //region Constructors
    public PauseMenuView(PauseMenuState state, Listener listener) {
        this.state = state;
        this.listener = listener;
    }
    //endregion

    //region Opening
    public void open(boolean hudVisible, String currentFilterName) {
        state.reset();
        state.setVolumes(Settings.getInt(MUSIC_KEY, PauseMenuState.DEFAULT_MUSIC_VOLUME), Settings.getInt(EFFECTS_KEY, PauseMenuState.DEFAULT_EFFECTS_VOLUME));
        state.setFov(Fov.degrees());
        setStatus(hudVisible, currentFilterName);
    }
    public void setStatus(boolean hudVisible, String currentFilterName) {
        hudOn = hudVisible;
        filterName = currentFilterName == null ? "" : currentFilterName;
    }
    //endregion

    //region Keys
    public boolean key(KeyEvent keyEvent) {
        int code = keyEvent.getKeyCode();
        if (code == KeyEvent.VK_UP || KeyBindings.matches(KeyBindings.Action.FORWARD, keyEvent)) state.move(-1);
        else if (code == KeyEvent.VK_DOWN || KeyBindings.matches(KeyBindings.Action.BACK, keyEvent)) state.move(1);
        else if (code == KeyEvent.VK_LEFT || KeyBindings.matches(KeyBindings.Action.LEFT, keyEvent)) changeSelectedVolume(-1);
        else if (code == KeyEvent.VK_RIGHT || KeyBindings.matches(KeyBindings.Action.RIGHT, keyEvent)) changeSelectedVolume(1);
        else if (code == KeyEvent.VK_ENTER || code == KeyEvent.VK_SPACE) activate();
        else if (code == KeyEvent.VK_ESCAPE || KeyBindings.matches(KeyBindings.Action.PAUSE, keyEvent)) return state.back();
        else return false;
        return true;
    }
    static String pauseKeyName() { return KeyBindings.keyName(KeyBindings.Action.PAUSE); }
    private void changeSelectedVolume(int steps) {
        if (state.adjust(steps)) saveVolumes();
    }
    private void activate() {
        PauseMenuState.Item item = state.activate();
        if (item != null) listener.onItem(item);
    }
    //endregion

    //region Mouse
    public void mouseMoved(int x, int y) {
        int row = rowAt(x, y);
        if (row >= 0) state.select(row);
    }
    public void mousePressed(int x, int y) {
        int row = rowAt(x, y);
        if (row < 0) return;
        state.select(row);
        if (isVolumeRow(row)) {
            dragRow = row;
            setVolumeFromX(row, x);
        }
        else activate();
    }
    public void mouseDragged(int x, int y) {
        if (dragRow >= 0) setVolumeFromX(dragRow, x);
    }
    public void mouseReleased() {
        if (dragRow >= 0) saveVolumes();
        dragRow = -1;
    }
    private int rowAt(int x, int y) {
        Rectangle[] rects = rowRects;
        for (int i = 0; i < rects.length && i < state.rowCount(); i++)
            if (rects[i].contains(x, y)) return i;
        return -1;
    }
    private boolean isVolumeRow(int row) { return state.page() == PauseMenuState.Page.SETTINGS && row < PauseMenuState.Row.BACK.ordinal(); }
    private void setVolumeFromX(int row, int x) {
        Rectangle track = trackRects[row];
        if (track == null || track.width <= 0) return;
        double fraction = (x - track.x) / (double) track.width;
        PauseMenuState.Row volumeRow = PauseMenuState.Row.values()[row];
        int min = PauseMenuState.minOf(volumeRow), max = PauseMenuState.maxOf(volumeRow);
        int value = (int) Math.round(min + fraction * (max - min));
        state.setVolume(volumeRow, value);
    }
    //endregion

    //region Saving
    private void saveVolumes() {
        Settings.set(MUSIC_KEY, String.valueOf(state.musicVolume()));
        Settings.set(EFFECTS_KEY, String.valueOf(state.effectsVolume()));
        Fov.set(state.fov());
        Music.volumeChanged();
    }
    //endregion

    //region Painting
    public void paint(Graphics2D g2d, int width, int height) {
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setColor(new Color(0, 0, 0, 180));
        g2d.fillRect(0, 0, width, height);
        Metrics metrics = new Metrics(width, height);
        switch (state.page()) {
            case MAIN -> paintMain(g2d, metrics);
            case SETTINGS -> paintSettings(g2d, metrics);
            case CONTROLS -> paintControls(g2d, metrics);
        }
    }

    private void paintMain(Graphics2D g2d, Metrics m) {
        paintTitle(g2d, m, NSLocalizedString.get("gv.paused"));
        List<PauseMenuState.Item> items = state.items();
        Rectangle[] rects = new Rectangle[items.size()];
        for (int i = 0; i < rects.length; i++) rects[i] = m.row(i);
        publishRects(rects, new Rectangle[rects.length]);
        for (int i = 0; i < rects.length; i++)
            drawRow(g2d, m, rects[i], i == state.selected(), itemLabel(items.get(i)));
        paintHint(g2d, m, NSLocalizedString.format("pause.hint_main", pauseKeyName()));
    }

    private void paintSettings(Graphics2D g2d, Metrics m) {
        paintTitle(g2d, m, NSLocalizedString.get("pause.settings"));
        Rectangle[] rects = new Rectangle[PauseMenuState.Row.values().length];
        Rectangle[] tracks = new Rectangle[rects.length];
        for (int i = 0; i < rects.length; i++) rects[i] = m.settingsRow(i);
        for (int i = 0; i < rects.length - 1; i++) tracks[i] = m.track(rects[i]);
        publishRects(rects, tracks);
        paintSectionDivider(g2d, m, rects[PauseMenuState.Row.EFFECTS.ordinal()], rects[PauseMenuState.Row.FOV.ordinal()]);
        for (int i = 0; i < rects.length; i++) {
            boolean selected = i == state.selected();
            if (i == PauseMenuState.Row.BACK.ordinal()) {
                drawRow(g2d, m, rects[i], selected, NSLocalizedString.get("pause.back"));
                continue;
            }
            PauseMenuState.Row row = PauseMenuState.Row.values()[i];
            String key = switch (row) {
                case MUSIC -> "pause.music_volume";
                case EFFECTS -> "pause.effects_volume";
                default -> "pause.fov";
            };
            int value = state.volumeOf(row);
            drawRow(g2d, m, rects[i], selected, NSLocalizedString.format(key, value));
            drawSlider(g2d, tracks[i], value, PauseMenuState.minOf(row), PauseMenuState.maxOf(row));
        }
        paintHint(g2d, m, NSLocalizedString.format("pause.hint_settings", pauseKeyName()));
    }
    private void paintSectionDivider(Graphics2D g2d, Metrics m, Rectangle above, Rectangle below) {
        int top = above.y + above.height, bottom = below.y;
        int middle = (top + bottom) / 2;
        g2d.setColor(new Color(120, 120, 128));
        g2d.drawLine(above.x, middle, above.x + above.width, middle);
        String heading = NSLocalizedString.get("pause.section_view");
        g2d.setFont(m.hintFont);
        FontMetrics fm = g2d.getFontMetrics();
        int labelX = above.x + m.pad;
        int baseline = middle - fm.getDescent() - 2;
        g2d.setColor(new Color(170, 170, 178));
        g2d.drawString(heading, labelX, baseline);
    }
    private void paintControls(Graphics2D g2d, Metrics m) {
        paintTitle(g2d, m, NSLocalizedString.get("pause.controls"));
        KeyBindings.Action[] actions = KeyBindings.all();
        int perColumn = (actions.length + 1) / 2;
        g2d.setFont(m.rowFont);
        FontMetrics fm = g2d.getFontMetrics();
        for (int i = 0; i < actions.length; i++) {
            int column = i / perColumn, line = i % perColumn;
            int x = m.controlsX + column * (m.controlsWidth / 2 + m.gap);
            int baseline = m.controlsY + line * m.controlsLine + fm.getAscent();
            int colWidth = m.controlsWidth / 2 - m.gap;
            g2d.setColor(new Color(220, 220, 225));
            g2d.drawString(NSLocalizedString.get(actions[i].labelKey()), x, baseline);
            String key = KeyBindings.keyName(actions[i]);
            g2d.setColor(Color.WHITE);
            g2d.drawString(key, x + colWidth - fm.stringWidth(key), baseline);
        }
        Rectangle back = m.controlsBackRow(perColumn);
        publishRects(new Rectangle[] { back }, new Rectangle[1]);
        drawRow(g2d, m, back, true, NSLocalizedString.get("pause.back"));
        paintHint(g2d, m, NSLocalizedString.format("pause.hint_controls", pauseKeyName()));
    }

    private void publishRects(Rectangle[] rects, Rectangle[] tracks) {
        trackRects = tracks;
        rowRects = rects;
    }
    private String itemLabel(PauseMenuState.Item item) {
        return switch (item) {
            case RESUME -> NSLocalizedString.get("pause.resume");
            case RESTART -> NSLocalizedString.get("pause.restart");
            case SETTINGS -> NSLocalizedString.get("pause.settings");
            case CONTROLS -> NSLocalizedString.get("pause.controls");
            case TOGGLE_HUD -> NSLocalizedString.format("pause.hud", NSLocalizedString.get(hudOn ? "pause.hud_on" : "pause.hud_off"));
            case CYCLE_FILTER -> NSLocalizedString.format("pause.filter", filterName);
            case MAIN_MENU -> NSLocalizedString.get("pause.main_menu");
        };
    }

    private void paintTitle(Graphics2D g2d, Metrics m, String title) {
        g2d.setColor(Color.WHITE);
        g2d.setFont(m.titleFont);
        FontMetrics fm = g2d.getFontMetrics();
        g2d.drawString(title, (m.width - fm.stringWidth(title)) / 2, m.titleBaseline);
    }

    private void paintHint(Graphics2D g2d, Metrics m, String hint) {
        g2d.setColor(new Color(170, 170, 178));
        g2d.setFont(m.hintFont);
        FontMetrics fm = g2d.getFontMetrics();
        g2d.drawString(hint, (m.width - fm.stringWidth(hint)) / 2, m.height - m.hintBottom);
    }

    private void drawRow(Graphics2D g2d, Metrics m, Rectangle rect, boolean selected, String label) {
        if (selected) {
            g2d.setColor(new Color(255, 255, 255, 50));
            g2d.fillRoundRect(rect.x, rect.y, rect.width, rect.height, 12, 12);
            g2d.setColor(Color.WHITE);
            g2d.drawRoundRect(rect.x, rect.y, rect.width, rect.height, 12, 12);
        }
        g2d.setFont(m.rowFont);
        FontMetrics fm = g2d.getFontMetrics();
        g2d.setColor(selected ? Color.WHITE : new Color(200, 200, 206));
        g2d.drawString(label, rect.x + m.pad, rect.y + (rect.height - fm.getHeight()) / 2 + fm.getAscent());
    }

    private void drawSlider(Graphics2D g2d, Rectangle track, int value, int min, int max) {
        int barHeight = Math.max(4, track.height / 5);
        int barY = track.y + (track.height - barHeight) / 2;
        g2d.setColor(new Color(90, 90, 98));
        g2d.fillRoundRect(track.x, barY, track.width, barHeight, barHeight, barHeight);
        int filled = (int) Math.round(track.width * (value - min) / (double) (max - min));
        g2d.setColor(Color.WHITE);
        g2d.fillRoundRect(track.x, barY, Math.max(0, filled), barHeight, barHeight, barHeight);
        int knob = Math.max(8, track.height / 3);
        g2d.fillOval(track.x + filled - knob / 2, track.y + (track.height - knob) / 2, knob, knob);
    }
    //endregion

    //region Layout
    private static final class Metrics {
        final int width, height, rowWidth, rowX, rowTop, rowStep, rowHeight, pad, titleBaseline, hintBottom, gap;
        final int controlsX, controlsY, controlsWidth, controlsLine;
        final Font titleFont, rowFont, hintFont;

        Metrics(int width, int height) {
            this.width = width;
            this.height = height;
            float rowSize = Math.max(12f, height * 0.04f);
            titleFont = FontCache.get(Font.SANS_SERIF, Font.BOLD, Math.max(20f, height * 0.13f));
            rowFont = FontCache.get(Font.SANS_SERIF, Font.PLAIN, rowSize);
            hintFont = FontCache.get(Font.SANS_SERIF, Font.PLAIN, Math.max(10f, height * 0.028f));
            rowStep = Math.max((int) (rowSize * 1.5), (int) (height * 0.075));
            rowHeight = (int) (rowStep * 0.8);
            rowWidth = Math.clamp((int) (width * 0.45), 260, width - 32);
            rowX = (width - rowWidth) / 2;
            rowTop = (int) (height * 0.25);
            pad = Math.max(10, rowWidth / 25);
            gap = Math.max(8, width / 60);
            titleBaseline = (int) (height * 0.2);
            hintBottom = Math.max(12, height / 22);
            controlsWidth = Math.clamp((int) (width * 0.7), 360, width - 32);
            controlsX = (width - controlsWidth) / 2;
            controlsY = rowTop;
            controlsLine = (int) Math.max(12, Math.min(rowSize * 1.3, height * 0.5 / 9));
        }

        Rectangle row(int index) { return new Rectangle(rowX, rowTop + index * rowStep, rowWidth, rowHeight); }
        Rectangle settingsRow(int index) {
            int extra = index >= PauseMenuState.Row.FOV.ordinal() ? rowStep : 0;
            return new Rectangle(rowX, rowTop + index * rowStep + extra, rowWidth, rowHeight);
        }
        Rectangle track(Rectangle row) {
            int left = row.x + (int) (row.width * 0.55);
            int right = row.x + row.width - pad;
            return new Rectangle(left, row.y, Math.max(1, right - left), row.height);
        }

        Rectangle controlsBackRow(int perColumn) {
            int y = controlsY + perColumn * controlsLine + rowStep / 2;
            int x = (width - rowWidth) / 2;
            return new Rectangle(x, y, rowWidth, rowHeight);
        }
    }
    //endregion
}