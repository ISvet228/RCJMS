package Project.Roguelike;

import Project.Audio.*;
import Project.Core.*;
import Project.Game.DevConsole;
import Project.Helpers.NSLocalizedString;

import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.util.*;

public final class RoguelikeModule implements GameModule {
    //region Constants
    private static final String COMMAND = "roguelike";
    private static final double ATTACK_COOLDOWN = 0.35;
    private static final int LOG_LINES = 3;
    private static final double POTION_VIEW_SIZE = 0.6;
    //endregion

    //region Variables
    private boolean enabled;
    private Dungeon dungeon;
    private RogueStats stats = new RogueStats();
    private final CombatLog log = new CombatLog();
    private final DeathSequence death = new DeathSequence();
    private double attackCooldown;
    //endregion

    //region Identity
    @Override public String id() { return "roguelike"; }
    //endregion

    //region Console
    public RoguelikeModule() { }

    @Override  public void onConsoleReady(GameContext context) { context.command(COMMAND, NSLocalizedString.get("rogue.usage"), this::runCommand); }

    private void runCommand(DevConsole.Args args) {
        List<String> values = args.values();
        String word = values.isEmpty() ? "status" : values.getFirst().toLowerCase(Locale.ROOT);
        switch (word) {
            case "on" -> {
                setEnabled(true);
                args.info(NSLocalizedString.get("rogue.on"));
            }
            case "off" -> {
                setEnabled(false);
                args.info(NSLocalizedString.get("rogue.off"));
            }
            case "status" -> args.info(NSLocalizedString.format("rogue.status", NSLocalizedString.get(isEnabled() ? "rogue.state.on" : "rogue.state.off")));
            default -> args.warn(NSLocalizedString.get("rogue.usage"));
        }
    }

    private synchronized boolean isEnabled() { return enabled; }
    private synchronized void setEnabled(boolean value) {
        enabled = value;
        RunChoice.setRoguelike(value);
        dungeon = null;
        if (!value) death.reset();
    }
    //endregion

    //region Run Lifecycle
    @Override public synchronized void onRunStart(GameContext context) {
        enabled = RunChoice.roguelike();
        if (enabled) startRun(context);
        else dungeon = null;
    }

    private void startRun(GameContext context) {
        long seed = (context.isCustomMap() ? "custom" : context.seed()).hashCode() * 31L + 7L;
        dungeon = new Dungeon(context.mazeWidth(), context.mazeHeight(), context.floorCount(), seed);
        stats = new RogueStats();
        log.clear();
        death.reset();
        attackCooldown = 0;
        log.add(NSLocalizedString.get("rogue.log.start"));
    }
    //endregion

    //region Input
    @Override public synchronized boolean onKey(GameContext context, KeyEvent keyEvent, boolean down) {
        if (!enabled || !down) return false;
        if (death.active()) {
            if (!death.ready()) return false;
            context.restart();
            return true;
        }
        if (KeyBindings.matches(KeyBindings.Action.ATTACK, keyEvent)) {
            attack(context);
            return true;
        }
        if (KeyBindings.matches(KeyBindings.Action.POTION, keyEvent)) {
            drink();
            return true;
        }
        return false;
    }

    private void attack(GameContext context) {
        if (dungeon == null || attackCooldown > 0) return;
        attackCooldown = ATTACK_COOLDOWN;
        int floor = context.currentFloor();
        Enemy target = nearestInCone(context, floor);
        if (target == null) {
            log.add(NSLocalizedString.get("rogue.log.miss"));
            Sound.play(Sound.Effect.BUMP);
            return;
        }
        log.add(NSLocalizedString.format("rogue.log.hit", Combat.PLAYER_DAMAGE));
        Sound.play(Sound.Effect.HIT);
        if (target.hurt(Combat.PLAYER_DAMAGE)) defeat(floor, target);
    }

    private Enemy nearestInCone(GameContext context, int floor) {
        Enemy best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Enemy enemy : dungeon.enemies(floor)) {
            if (!Combat.inAttackCone(context.playerX(), context.playerY(), context.cameraAngle(), enemy.x(), enemy.y())) continue;
            double distance = Math.hypot(enemy.x() - context.playerX(), enemy.y() - context.playerY());
            if (distance < bestDistance) {
                best = enemy;
                bestDistance = distance;
            }
        }
        return best;
    }

    private void defeat(int floor, Enemy enemy) {
        dungeon.removeEnemy(floor, enemy);
        stats.kill();
        log.add(NSLocalizedString.get("rogue.log.kill"));
        if (Combat.dropsPotion(dungeon.random())) {
            dungeon.addPotion(floor, enemy.cellX(), enemy.cellY());
            log.add(NSLocalizedString.get("rogue.log.drop"));
        }
    }

    private void drink() {
        if (dungeon == null) return;
        if (stats.canDrink()) {
            int healed = stats.drink();
            log.add(NSLocalizedString.format("rogue.log.drink", healed));
            Sound.play(Sound.Effect.PICKUP);
        }
        else if (stats.drinkLimitReached()) { log.add(NSLocalizedString.format("rogue.log.limit", RogueStats.MAX_DRINKS)); }
        else { log.add(NSLocalizedString.get("rogue.log.no_potion")); }
    }
    //endregion

    //region Simulation
    @Override public synchronized void update(GameContext context, double seconds) {
        if (!enabled) return;
        if (dungeon == null) startRun(context);
        if (death.active()) {
            death.update(seconds);
            return;
        }
        attackCooldown = Math.max(0, attackCooldown - seconds);
        int floor = context.currentFloor();
        int[][] map = context.map();
        int cellX = cellOf(context.playerX(), map[0].length), cellY = cellOf(context.playerY(), map.length);
        dungeon.ensureFloor(floor, map, cellX, cellY);
        pickUpPotion(floor, cellX, cellY);
        for (Enemy enemy : new ArrayList<>(dungeon.enemies(floor))) {
            boolean strikes = enemy.update(map, context.portalsOf(floor), context.playerX(), context.playerY(), seconds, dungeon.random(), (x, y) -> dungeon.occupied(floor, x, y, enemy));
            if (enemy.takeNotice()) log.add(NSLocalizedString.get("rogue.log.notice"));
            if (strikes) hurtPlayer(enemy.damage());
            if (death.active()) break;
        }
    }

    private void pickUpPotion(int floor, int cellX, int cellY) {
        if (dungeon.hasPotion(floor, cellX, cellY) && stats.addPotion()) {
            dungeon.removePotion(floor, cellX, cellY);
            log.add(NSLocalizedString.get("rogue.log.pickup"));
            Sound.play(Sound.Effect.PICKUP);
        }
    }

    private void hurtPlayer(int damage) {
        boolean dead = stats.damage(damage);
        log.add(NSLocalizedString.format("rogue.log.hurt", damage));
        Sound.play(Sound.Effect.HIT);
        if (dead) {
            death.start();
            log.add(NSLocalizedString.get("rogue.log.death"));
        }
    }

    private static int cellOf(double coordinate, int limit) { return Math.clamp((long) Math.floor(coordinate), 0, limit - 1); }
    //endregion

    //region Painting
    @Override public synchronized void paintHud(GameContext context, Graphics2D g2d, int width, int height) {
        if (!enabled || dungeon == null) return;
        paintWorld(context, g2d, width, height);
        RogueHud.paintPanel(g2d, height, stats, log.last(LOG_LINES),
                NSLocalizedString.format("rogue.hud.keys", KeyBindings.keyName(KeyBindings.Action.ATTACK), KeyBindings.keyName(KeyBindings.Action.POTION)), LOG_LINES);
        RogueHud.paintDeath(g2d, width, height, death);
    }

    private void paintWorld(GameContext context, Graphics2D g2d, int width, int height) {
        int floor = context.currentFloor();
        int[][] map = context.map();
        for (int[] cell : dungeon.potions(floor)) {
            Projection.Sprite sprite = Projection.project(context.playerX(), context.playerY(), context.cameraAngle(), context.cameraPitch(), context.eyeHeight(), cell[0] + 0.5, cell[1] + 0.5, width, height, map);
            if (sprite != null) sprite.paint(g2d, height, g -> EnemySprite.paintPotion(g, sprite));
        }
        for (Enemy enemy : dungeon.enemies(floor)) {
            Projection.Sprite sprite = Projection.project(context.playerX(), context.playerY(), context.cameraAngle(), context.cameraPitch(), context.eyeHeight(), enemy.x(), enemy.y(), width, height, map);
            if (sprite != null) sprite.paint(g2d, height, g -> EnemySprite.paintEnemy(g, sprite, enemy.variant(), enemy.hp() / (double) enemy.maxHp()));
        }
    }
    //endregion
}