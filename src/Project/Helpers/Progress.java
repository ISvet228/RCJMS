package Project.Helpers;

public final class Progress {
    private static final ThreadLocal<Progress> CURRENT = new ThreadLocal<>();

    private volatile String stage = "";
    private volatile double fraction;
    public static void bind(Progress progress) { CURRENT.set(progress); }
    public static void unbind() { CURRENT.remove(); }
    public static void report(String stage, double fraction) {
        Progress p = CURRENT.get();
        if (p != null) p.set(stage, fraction);
    }
    public void set(String stage, double fraction) {
        this.stage = stage;
        this.fraction = Math.clamp(fraction, 0.0, 1.0);
    }
    public String stage() { return stage; }
    public double fraction() { return fraction; }
}