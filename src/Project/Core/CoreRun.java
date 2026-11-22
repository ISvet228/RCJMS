package Project.Core;

import java.util.Locale;

public final class CoreRun {
    //region Variables
    private boolean running;
    private double seconds;
    private int steps;
    private int cellX, cellY, cellFloor;
    //endregion

    //region Changes
    public void begin(GameContext context) {
        running = true;
        seconds = 0;
        steps = 0;
        remember(context);
    }
    public void end() { running = false; }
    public int advance(GameContext context, double dt) {
        if (!running) return 0;
        seconds += Math.max(0, dt);
        int cx = cellOf(context.playerX()), cy = cellOf(context.playerY()), floor = context.currentFloor();
        boolean entered = cx != cellX || cy != cellY || floor != cellFloor;
        remember(context);
        if (!entered) return 0;
        steps++;
        return 1;
    }
    //endregion

    //region Queries
    public boolean isRunning() { return running; }
    public double seconds() { return seconds; }
    public int steps() { return steps; }
    public static String clock(double totalSeconds) {
        long tenths = Math.round(Math.max(0, totalSeconds) * 10);
        long hours = tenths / 36000, minutes = (tenths / 600) % 60, secs = (tenths / 10) % 60, part = tenths % 10;
        if (hours > 0) return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, secs);
        return String.format(Locale.ROOT, "%d:%02d.%d", minutes, secs, part);
    }
    //endregion

    //region Helpers
    private void remember(GameContext context) {
        cellX = cellOf(context.playerX());
        cellY = cellOf(context.playerY());
        cellFloor = context.currentFloor();
    }
    private static int cellOf(double coordinate) { return (int) Math.floor(coordinate); }
    //endregion
}