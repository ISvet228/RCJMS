package Project.Core;

import Project.Helpers.Settings;

import java.awt.event.KeyEvent;

public final class KeyBindings {
    //region Actions
    public enum Action {
        FORWARD(KeyEvent.VK_W), BACK(KeyEvent.VK_S), LEFT(KeyEvent.VK_A), RIGHT(KeyEvent.VK_D),
        SPRINT(KeyEvent.VK_SHIFT), PAUSE(KeyEvent.VK_ESCAPE), RESTART(KeyEvent.VK_R),
        SCREENSHOT(KeyEvent.VK_F12), GHOST(KeyEvent.VK_G), STATS(KeyEvent.VK_I),
        
        ATTACK(KeyEvent.VK_SPACE), POTION(KeyEvent.VK_E);

        private final int defaultKey;

        Action(int defaultKey) { this.defaultKey = defaultKey; }

        public int defaultKey() { return defaultKey; }
        public String labelKey() { return "kb." + name().toLowerCase(java.util.Locale.ROOT); }
    }
    //endregion

    private static final String PREFIX = "key.";
    private KeyBindings() {}

    //region Queries
    public static boolean matches(Action action, KeyEvent keyEvent) { return keyEvent.getKeyCode() == keyOf(action); }
    public static int keyOf(Action action) { return Settings.getInt(PREFIX + action.name().toLowerCase(java.util.Locale.ROOT), action.defaultKey()); }
    public static String keyName(Action action) { return KeyEvent.getKeyText(keyOf(action)); }
    public static Action[] all() { return Action.values(); }
    //endregion

    //region Changes
    public static void rebind(Action action, int keyCode) { Settings.set(PREFIX + action.name().toLowerCase(java.util.Locale.ROOT), String.valueOf(keyCode)); }
    public static void resetDefaults() {
        for (Action action : Action.values()) rebind(action, action.defaultKey());
    }
    //endregion
}