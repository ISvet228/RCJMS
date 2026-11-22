package Project.Helpers;

import Project.Helpers.SaveData;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The settings of the game modules, stored in settings.prt with every other setting. Keys are written as
 * {@code module.setting}. A missing key gives the fallback the caller passes; nothing here throws.
 */
public final class Settings {
    //region Variables
    private static final Map<String, String> VALUES = new LinkedHashMap<>();
    private static boolean loaded;
    //endregion

    //region Constructors
    private Settings() {}
    //endregion

    //region Reading
    public static synchronized boolean getBoolean(String key, boolean fallback) {
        String value = getString(key, null);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }
    public static synchronized int getInt(String key, int fallback) {
        String value = getString(key, null);
        try { return value == null ? fallback : Integer.parseInt(value.trim()); }
        catch (NumberFormatException exception) { return fallback; }
    }
    public static synchronized String getString(String key, String fallback) {
        ensureLoaded();
        return VALUES.getOrDefault(key, fallback);
    }
    //endregion

    //region Writing
    /** Stores a value and writes settings.prt at once. */
    public static synchronized void set(String key, String value) {
        ensureLoaded();
        VALUES.put(key, String.valueOf(value));
        try { SaveData.saveModules(VALUES); }
        catch (java.io.IOException ignored) { }
    }
    //endregion

    //region Loading
    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        VALUES.putAll(SaveData.load().modules);
    }
    //endregion
}
