package Helpers;

public final class AutoRenderController {
    public static final double MIN_SCALE = 0.05, MAX_SCALE = 1.0;
    public static final double DROP_BELOW_FPS = 45, FAST_DROP_BELOW_FPS = 30, RAISE_ABOVE_FPS = 150;
    private static final double WARMUP_SECONDS = 1.5;
    private static final double SMOOTHING_SECONDS = 0.5;
    private static final double DROP_RATE = 0.25, RAISE_RATE = 0.10;
    private static final double SCALE_STEP = 0.01;
    private static final double MAX_FRAME_SECONDS = 0.5;

    private double level = 1.0;
    private double frameSeconds = 1.0 / 60.0;
    private double elapsed = 0;
    private boolean primed = false;

    public double level() { return level; }
    private double fps() { return 1.0 / Math.max(frameSeconds, 1e-4); }
    public void update(double dt) {
        dt = Math.clamp(dt, 0.0, MAX_FRAME_SECONDS);
        elapsed += dt;
        if (!primed) { frameSeconds = dt; primed = true; }
        else frameSeconds += (dt - frameSeconds) * (1.0 - Math.exp(-dt / SMOOTHING_SECONDS));
        if (elapsed < WARMUP_SECONDS) return;

        double fps = fps();
        if (fps < DROP_BELOW_FPS) {
            double urgency = Math.clamp((DROP_BELOW_FPS - fps) / (DROP_BELOW_FPS - FAST_DROP_BELOW_FPS), 0.2, 1.0);
            level = Math.max(0.0, level - DROP_RATE * urgency * dt);
        } else if (fps > RAISE_ABOVE_FPS) {
            double urgency = Math.clamp((fps - RAISE_ABOVE_FPS) / 75.0, 0.2, 1.0);
            level = Math.min(1.0, level + RAISE_RATE * urgency * dt);
        }
    }
    public static int tierFor(double level) { return level >= 2.0 / 3.0 ? 2 : level >= 1.0 / 3.0 ? 1 : 0; }
    public static double scaleFor(double level) {
        double scale = MIN_SCALE + (MAX_SCALE - MIN_SCALE) * level;
        return Math.clamp(Math.round(scale / SCALE_STEP) * SCALE_STEP, MIN_SCALE, MAX_SCALE);
    }
}