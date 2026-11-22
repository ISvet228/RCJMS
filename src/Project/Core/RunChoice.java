package Project.Core;

public final class RunChoice {
    private static volatile boolean roguelike;
    private static volatile Boolean horror;
    private RunChoice() {}

    public static boolean roguelike() { return roguelike; }
    public static void setRoguelike(boolean on) { roguelike = on; }
    public static Boolean horror() { return horror; }
    public static void setHorror(Boolean on) { horror = on; }
}