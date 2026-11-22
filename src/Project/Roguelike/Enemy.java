package Project.Roguelike;

import Project.Helpers.PortalData;

import java.util.Random;
import java.util.function.BiPredicate;

public final class Enemy {
    //region Constants
    public static final double NOTICE_RANGE = 6.0;
    public static final double LOSE_RANGE = 9.0;
    public static final double REPATH_SECONDS = 0.5;
    public static final double STRIKE_COOLDOWN = 1.2;
    public static final double REACH = 1.05;
    private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    //endregion

    //region Variables
    private final int variant;
    private final int maxHp;
    private final int damage;
    private final double speed;
    private int hp;
    private int cellX, cellY;
    private int targetX = -1, targetY = -1;
    private double x, y;
    private PortalData portals;
    private boolean chasing;
    private boolean noticed;
    private int[] path = new int[0];
    private double repathTimer;
    private double idleTimer;
    private double strikeCooldown;
    //endregion

    //region Constructors
    public Enemy(int cellX, int cellY, int hp, int damage, double speed, int variant) {
        this.cellX = cellX;
        this.cellY = cellY;
        this.hp = hp;
        this.maxHp = hp;
        this.damage = damage;
        this.speed = speed;
        this.variant = variant;
        this.x = cellX + 0.5;
        this.y = cellY + 0.5;
    }
    public static Enemy random(int cellX, int cellY, Random random) {
        return new Enemy(cellX, cellY, 30 + random.nextInt(21), 6 + random.nextInt(7), 1.3 + random.nextDouble() * 0.7, random.nextInt(4));
    }
    //endregion

    //region Simulation
    public boolean update(int[][] map, double playerX, double playerY, double seconds, Random random, BiPredicate<Integer, Integer> occupied) {
        return update(map, null, playerX, playerY, seconds, random, occupied);
    }
    public boolean update(int[][] map, PortalData portals, double playerX, double playerY, double seconds, Random random, BiPredicate<Integer, Integer> occupied) {
        this.portals = portals;
        int playerCellX = (int) Math.floor(playerX), playerCellY = (int) Math.floor(playerY);
        strikeCooldown = Math.max(0, strikeCooldown - seconds);
        updateAwareness(map, playerX, playerY, playerCellX, playerCellY);
        if (targetX < 0) chooseStep(map, playerCellX, playerCellY, seconds, random, occupied);
        if (targetX >= 0) walk(seconds);
        return strikeIfInReach(playerX, playerY);
    }
    private void updateAwareness(int[][] map, double playerX, double playerY, int playerCellX, int playerCellY) {
        double distance = Math.hypot(playerX - x, playerY - y);
        if (!chasing) {
            if (distance <= NOTICE_RANGE && Pathing.lineOfSight(map, cellX, cellY, playerCellX, playerCellY)) {
                chasing = true;
                noticed = true;
                repathTimer = 0;
            }
        }
        else if (distance > LOSE_RANGE) {
            chasing = false;
            path = new int[0];
        }
    }

    private void chooseStep(int[][] map, int playerCellX, int playerCellY, double seconds, Random random, BiPredicate<Integer, Integer> occupied) {
        int[] next = chasing ? chaseStep(map, playerCellX, playerCellY, seconds) : wanderStep(map, playerCellX, playerCellY, seconds, random);
        if (next == null) return;
        if ((next[0] == playerCellX && next[1] == playerCellY) || occupied.test(next[0], next[1])) return;
        targetX = next[0];
        targetY = next[1];
    }

    private int[] chaseStep(int[][] map, int playerCellX, int playerCellY, double seconds) {
        repathTimer -= seconds;
        int index = indexOfCell(path, cellX, cellY);
        if (repathTimer <= 0 || index < 0) {
            path = Pathing.path(map, portals, cellX, cellY, playerCellX, playerCellY);
            repathTimer = REPATH_SECONDS;
            index = indexOfCell(path, cellX, cellY);
        }
        if (index < 0 || 2 * (index + 1) >= path.length) return null;
        int nx = path[2 * (index + 1)], ny = path[2 * (index + 1) + 1];
        if (Math.abs(nx - cellX) + Math.abs(ny - cellY) > 1) {
            cellX = nx;
            cellY = ny;
            x = nx + 0.5;
            y = ny + 0.5;
            targetX = nx;
            targetY = ny;
            path = new int[0];
            return null;
        }
        return new int[]{nx, ny};
    }

    private int[] wanderStep(int[][] map, int playerCellX, int playerCellY, double seconds, Random random) {
        idleTimer -= seconds;
        if (idleTimer > 0) return null;
        idleTimer = 0.2 + random.nextDouble() * 1.2;
        int first = random.nextInt(DIRECTIONS.length);
        for (int k = 0; k < DIRECTIONS.length; k++) {
            int[] direction = DIRECTIONS[(first + k) % DIRECTIONS.length];
            int nx = cellX + direction[0], ny = cellY + direction[1];
            if (Pathing.walkable(map, nx, ny) && (nx != playerCellX || ny != playerCellY)) return new int[]{nx, ny};
        }
        return null;
    }

    private void walk(double seconds) {
        double goalX = targetX + 0.5, goalY = targetY + 0.5;
        double dx = goalX - x, dy = goalY - y;
        double distance = Math.hypot(dx, dy), step = speed * seconds;
        if (distance <= step) {
            x = goalX;
            y = goalY;
            cellX = targetX;
            cellY = targetY;
            targetX = -1;
            targetY = -1;
        }
        else {
            x += dx / distance * step;
            y += dy / distance * step;
        }
    }

    private boolean strikeIfInReach(double playerX, double playerY) {
        if (strikeCooldown > 0 || Math.hypot(playerX - x, playerY - y) > REACH) return false;
        strikeCooldown = STRIKE_COOLDOWN;
        return true;
    }

    private static int indexOfCell(int[] path, int cx, int cy) {
        for (int i = 0; i + 1 < path.length; i += 2) {
            if (path[i] == cx && path[i + 1] == cy) return i / 2;
        }
        return -1;
    }
    //endregion

    //region Health
    public boolean hurt(int amount) {
        hp = Math.max(0, hp - Math.max(0, amount));
        return hp == 0;
    }
    public boolean dead() { return hp <= 0; }
    public boolean takeNotice() {
        boolean was = noticed;
        noticed = false;
        return was;
    }
    //endregion

    //region Accessors
    public int hp() { return hp; }
    public int maxHp() { return maxHp; }
    public int damage() { return damage; }
    public double speed() { return speed; }
    public int variant() { return variant; }
    public double x() { return x; }
    public double y() { return y; }
    public int cellX() { return cellX; }
    public int cellY() { return cellY; }
    public int targetX() { return targetX; }
    public int targetY() { return targetY; }
    public boolean chasing() { return chasing; }
    //endregion
}