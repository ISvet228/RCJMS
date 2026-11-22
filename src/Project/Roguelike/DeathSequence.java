package Project.Roguelike;

public final class DeathSequence {
    public static final double FLASH_SECONDS = 1.5;
    private double elapsed = -1;

    //region State
    public void start() { if (!active()) elapsed = 0; }
    public void update(double seconds) { if (active()) elapsed += seconds; }
    public boolean active() { return elapsed >= 0; }
    public boolean ready() { return active() && elapsed >= FLASH_SECONDS; }
    public double progress() { return active() ? Math.min(1.0, elapsed / FLASH_SECONDS) : 0; }
    public void reset() { elapsed = -1; }
    //endregion
}
