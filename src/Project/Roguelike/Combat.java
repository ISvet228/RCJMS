package Project.Roguelike;

import java.util.Random;

public final class Combat {
    //region Constants
    public static final int PLAYER_DAMAGE = 30;
    public static final double ATTACK_REACH = 1.0;
    public static final double ATTACK_HALF_ANGLE = Math.toRadians(45);
    public static final double POTION_DROP_CHANCE = 0.3;
    //endregion

    //region Constructors
    private Combat() {}
    //endregion

    //region Rules
    public static boolean inAttackCone(double playerX, double playerY, double angle, double enemyX, double enemyY) {
        double dx = enemyX - playerX, dy = enemyY - playerY;
        double distance = Math.hypot(dx, dy);
        if (distance > ATTACK_REACH) return false;
        if (distance < 1e-9) return true;
        return Math.abs(wrapAngle(Math.atan2(dy, dx) - angle)) <= ATTACK_HALF_ANGLE + 1e-9;
    }

    public static boolean dropsPotion(Random random) { return random.nextDouble() < POTION_DROP_CHANCE; }
    public static double wrapAngle(double angle) { return angle - 2 * Math.PI * Math.floor((angle + Math.PI) / (2 * Math.PI)); }
    //endregion
}