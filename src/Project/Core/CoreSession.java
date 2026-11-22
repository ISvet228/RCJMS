package Project.Core;

public final class CoreSession {
    //region Variables
    private static final long STARTED_NANOS = System.nanoTime();
    private static int runs, finishes, steps;
    private static double bestSeconds = Double.NaN;
    //endregion

    //region Constructors
    private CoreSession() {}
    //endregion

    //region Changes
    public static synchronized void runStarted() { runs++; }
    public static synchronized void runFinished(double seconds) {
        finishes++;
        if (Double.isNaN(bestSeconds) || seconds < bestSeconds) bestSeconds = seconds;
    }
    public static synchronized void addSteps(int count) { steps += count; }
    //endregion

    //region Queries
    public static synchronized int runs() { return runs; }
    public static synchronized int finishes() { return finishes; }
    public static synchronized int steps() { return steps; }
    public static synchronized double bestSeconds() { return bestSeconds; }
    public static double sessionSeconds() { return (System.nanoTime() - STARTED_NANOS) / 1e9; }
    //endregion
}