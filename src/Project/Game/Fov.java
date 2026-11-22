package Project.Game;

import Project.Helpers.Settings;

public final class Fov {
    //region Variables
    public static final int MIN = 40, MAX = 110, DEFAULT = 60;
    private static final String KEY = "fov.degrees";
    private static volatile int degrees = Math.clamp(Settings.getInt(KEY, DEFAULT), MIN, MAX);
    //endregion
    private Fov() { }
    public static int degrees() { return degrees; }
    public static double halfRadians() { return Math.toRadians(degrees) / 2.0; }
    public static double tanHalf() { return Math.tan(halfRadians()); }
    public static void set(int value) {
        degrees = Math.clamp(value, MIN, MAX);
        Settings.set(KEY, String.valueOf(degrees));
    }
}