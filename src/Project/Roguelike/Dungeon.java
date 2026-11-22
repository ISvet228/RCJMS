package Project.Roguelike;

import Project.Helpers.MazeGenerator;

import java.util.*;

public final class Dungeon {
    //region Constants
    public static final int MIN_ENEMIES = 6;
    public static final int MAX_ENEMIES = 12;
    public static final int POTIONS_PER_FLOOR = 2;
    private static final double SPAWN_DISTANCE = 8.0;
    private static final int EXIT_DISTANCE = 4;
    //endregion

    //region Variables
    private final int enemyTotal;
    private final int floorCount;
    private final Random random;
    private final Map<Integer, FloorState> floors = new HashMap<>();
    //endregion

    //region Constructors
    public Dungeon(int width, int height, int floorCount, long seed) {
        this.floorCount = Math.max(1, floorCount);
        this.enemyTotal = enemyTotal(width, height, this.floorCount);
        this.random = new Random(seed);
    }
    public static int enemyTotal(int width, int height, int floors) {
        long cells = (long) width * height * Math.max(1, floors);
        return Math.clamp(MIN_ENEMIES + cells / 400, MIN_ENEMIES, MAX_ENEMIES);
    }
    public static int quota(int total, int floors, int floor) { return total / floors + (floor < total % floors ? 1 : 0); }
    private static final class FloorState {
        final List<Enemy> enemies = new ArrayList<>();
        final List<int[]> potions = new ArrayList<>();
    }
    //endregion

    //region Floors
    public boolean hasFloor(int floor) { return floors.containsKey(floor); }
    public void ensureFloor(int floor, int[][] map, int playerCellX, int playerCellY) {
        if (floors.containsKey(floor)) return;
        FloorState state = new FloorState();
        floors.put(floor, state);
        List<int[]> cells = freeCells(map, playerCellX, playerCellY);
        int enemyQuota = quota(enemyTotal, floorCount, floor);
        int index = 0;
        for (; index < enemyQuota && index < cells.size(); index++) {
            int[] cell = cells.get(index);
            state.enemies.add(Enemy.random(cell[0], cell[1], random));
        }
        for (int placed = 0; placed < POTIONS_PER_FLOOR && index < cells.size(); placed++, index++) { state.potions.add(cells.get(index)); }
    }

    private List<int[]> freeCells(int[][] map, int playerCellX, int playerCellY) {
        List<int[]> exits = new ArrayList<>();
        List<int[]> far = new ArrayList<>(), near = new ArrayList<>();
        for (int y = 0; y < map.length; y++) {
            for (int x = 0; x < map[y].length; x++)
                if (map[y][x] == MazeGenerator.PORTAL || map[y][x] == MazeGenerator.FINISH) exits.add(new int[]{x, y});
        }
        for (int y = 0; y < map.length; y++) {
            for (int x = 0; x < map[y].length; x++) {
                if (map[y][x] != MazeGenerator.OPEN || nearExit(exits, x, y)) continue;
                boolean isFar = Math.hypot(x - playerCellX, y - playerCellY) >= SPAWN_DISTANCE;
                (isFar ? far : near).add(new int[]{x, y});
            }
        }
        Collections.shuffle(far, random);
        Collections.shuffle(near, random);
        near.sort(Comparator.comparingDouble((int[] cell) -> Math.hypot(cell[0] - playerCellX, cell[1] - playerCellY)).reversed());
        far.addAll(near);
        return far;
    }

    private static boolean nearExit(List<int[]> exits, int x, int y) {
        for (int[] exit : exits)
            if (Math.max(Math.abs(exit[0] - x), Math.abs(exit[1] - y)) <= EXIT_DISTANCE) return true;
        return false;
    }
    //endregion

    //region Enemies
    public List<Enemy> enemies(int floor) {
        FloorState state = floors.get(floor);
        return state == null ? List.of() : state.enemies;
    }
    public boolean occupied(int floor, int x, int y, Enemy self) {
        for (Enemy enemy : enemies(floor)) {
            if (enemy == self) continue;
            if ((enemy.cellX() == x && enemy.cellY() == y) || (enemy.targetX() == x && enemy.targetY() == y)) return true;
        }
        return false;
    }

    public void removeEnemy(int floor, Enemy enemy) {
        FloorState state = floors.get(floor);
        if (state != null) state.enemies.remove(enemy);
    }
    //endregion

    //region Potions
    public List<int[]> potions(int floor) {
        FloorState state = floors.get(floor);
        return state == null ? List.of() : state.potions;
    }
    public boolean hasPotion(int floor, int x, int y) {
        for (int[] cell : potions(floor))
            if (cell[0] == x && cell[1] == y) return true;
        return false;
    }
    public void removePotion(int floor, int x, int y) {
        FloorState state = floors.get(floor);
        if (state != null) state.potions.removeIf(cell -> cell[0] == x && cell[1] == y);
    }
    public void addPotion(int floor, int x, int y) {
        FloorState state = floors.computeIfAbsent(floor, key -> new FloorState());
        state.potions.add(new int[]{x, y});
    }
    //endregion

    //region Accessors
    public Random random() { return random; }
    public int enemyTotal() { return enemyTotal; }
    //endregion
}