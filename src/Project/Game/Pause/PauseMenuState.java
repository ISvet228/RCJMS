package Project.Game.Pause;

import java.util.List;

public final class PauseMenuState {
    //region Types
    public enum Page { MAIN, SETTINGS, CONTROLS }
    public enum Item { RESUME, RESTART, SETTINGS, CONTROLS, TOGGLE_HUD, CYCLE_FILTER, MAIN_MENU }
    public enum Row { MUSIC, EFFECTS, BACK }
    //endregion

    //region Variables
    public static final int VOLUME_MIN = 0, VOLUME_MAX = 100, VOLUME_STEP = 5;
    public static final int DEFAULT_MUSIC_VOLUME = 35, DEFAULT_EFFECTS_VOLUME = 100;
    private final List<Item> items;
    private Page page = Page.MAIN;
    private int selected;
    private int homeIndex;
    private int musicVolume = DEFAULT_MUSIC_VOLUME, effectsVolume = DEFAULT_EFFECTS_VOLUME;
    //endregion

    //region Constructors
    public PauseMenuState() {
        items = List.of(Item.RESUME, Item.RESTART, Item.SETTINGS, Item.CONTROLS, Item.TOGGLE_HUD, Item.CYCLE_FILTER, Item.MAIN_MENU);
    }
    //endregion

    //region Queries
    public List<Item> items() { return items; }
    public synchronized Page page() { return page; }
    public synchronized int selected() { return selected; }
    public synchronized int rowCount() {
        return switch (page) {
            case MAIN -> items.size();
            case SETTINGS -> Row.values().length;
            case CONTROLS -> 1;
        };
    }
    public synchronized Item selectedItem() { return page == Page.MAIN ? items.get(selected) : null; }
    public synchronized int musicVolume() { return musicVolume; }
    public synchronized int effectsVolume() { return effectsVolume; }
    public synchronized int volumeOf(Row row) { return row == Row.MUSIC ? musicVolume : row == Row.EFFECTS ? effectsVolume : -1; }
    public static int clamp(int value) { return Math.clamp(value, VOLUME_MIN, VOLUME_MAX); }
    //endregion

    //region Changes
    public synchronized void reset() {
        page = Page.MAIN;
        selected = 0;
        homeIndex = 0;
    }
    public synchronized void move(int delta) {
        int count = rowCount();
        selected = Math.floorMod(selected + delta, count);
    }
    public synchronized void select(int index) { if (index >= 0 && index < rowCount()) selected = index; }
    public synchronized Item activate() {
        if (page == Page.MAIN) {
            Item item = items.get(selected);
            if (item == Item.SETTINGS) open(Page.SETTINGS, item);
            else if (item == Item.CONTROLS) open(Page.CONTROLS, item);
            else return item;
            return null;
        }
        if (isBackRow()) back();
        return null;
    }
    public synchronized boolean back() {
        if (page == Page.MAIN) return false;
        page = Page.MAIN;
        selected = homeIndex;
        return true;
    }
    public synchronized boolean adjust(int steps) {
        Row row = selectedRow();
        if (row == null) return false;
        return setVolume(row, volumeOf(row) + steps * VOLUME_STEP);
    }
    public synchronized boolean setVolume(Row row, int value) {
        int clamped = clamp(value);
        if (row == Row.MUSIC) {
            boolean changed = clamped != musicVolume;
            musicVolume = clamped;
            return changed;
        }
        if (row == Row.EFFECTS) {
            boolean changed = clamped != effectsVolume;
            effectsVolume = clamped;
            return changed;
        }
        return false;
    }
    public synchronized void setVolumes(int music, int effects) {
        musicVolume = clamp(music);
        effectsVolume = clamp(effects);
    }
    //endregion

    //region Helpers
    private void open(Page next, Item from) {
        homeIndex = items.indexOf(from);
        page = next;
        selected = 0;
    }
    private Row selectedRow() {
        if (page != Page.SETTINGS || selected >= Row.BACK.ordinal()) return null;
        return Row.values()[selected];
    }
    private boolean isBackRow() { return selected == rowCount() - 1; }
    //endregion
}