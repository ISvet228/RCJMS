package Project.ThemeCreator;
import Project.EngineCreator.*;
import Project.CreatorKit.*;

import java.util.*;

public final class ThemeInput {
    //region Variables
    //region Key and Button State
    private static final Set<String> KEYS = new HashSet<>();
    private static final Map<String, Integer> PRESSES = new HashMap<>();
    private static final boolean[] BUTTONS = new boolean[4];
    //endregion
    //region Pointer State
    private static double x, y, dx, dy;
    private static boolean hasPointer;
    //endregion
    //endregion

    //region Constructors
    private ThemeInput() {}
    //endregion

    //region Keyboard
    public static synchronized void keyDown(String key) { if (KEYS.add(name(key))) PRESSES.merge(name(key), 1, Integer::sum); }
    public static synchronized void keyUp(String key) { KEYS.remove(name(key)); }
    public static synchronized boolean down(String key) { return KEYS.contains(name(key)); }
    public static synchronized boolean held(String key) { return down(key); }
    public static synchronized int presses(String key) { return PRESSES.getOrDefault(name(key), 0); }
    private static String name(String key) { return key == null ? "" : key.strip().toUpperCase(Locale.ROOT); }
    //endregion

    //region Mouse
    public static synchronized void button(int which, boolean down) { if (which >= 1 && which <= 3) BUTTONS[which] = down; }
    public static synchronized boolean buttonDown(String name) {
        return switch (name == null ? "" : name.toLowerCase(Locale.ROOT)) {
            case "middle" -> BUTTONS[2];
            case "right" -> BUTTONS[3];
            default -> BUTTONS[1];
        };
    }
    public static synchronized void pointer(double px, double py) {
        if (hasPointer) {
            dx += px - x;
            dy += py - y;
        }
        x = px;
        y = py;
        hasPointer = true;
    }
    public static synchronized double mouseX() { return x; }
    public static synchronized double mouseY() { return y; }
    public static synchronized double deltaX() { return dx; }
    public static synchronized double deltaY() { return dy; }
    public static synchronized double speed() { return Math.hypot(dx, dy); }
    public static java.awt.Point screenPointer() {
        try {
            java.awt.PointerInfo info = java.awt.MouseInfo.getPointerInfo();
            return info == null ? null : info.getLocation();
        } catch (RuntimeException exception) {
            return null;
        }
    }
    //endregion

    //region Frame
    public static synchronized void clear() {
        KEYS.clear();
        Arrays.fill(BUTTONS, false);
        dx = dy = 0;
    }
    public static synchronized void frameDone() {
        dx = 0;
        dy = 0;
    }
    //endregion
}