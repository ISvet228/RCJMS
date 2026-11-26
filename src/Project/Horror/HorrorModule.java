package Project.Horror;

import Project.Core.*;
import Project.Helpers.Settings;

import Project.*;
import Project.Audio.*;
import Project.Roguelike.Projection;
import Project.Helpers.*;
import Project.Progress.Achievements;
import Project.Net.*;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Random;

public final class HorrorModule implements GameModule {
    //region Constants
    public static final String SETTING = "horror.enabled";
    private static final int CELLS_PER_HUNTER = 400;
    private static final double JUMP_SECONDS = 2.6, READY_AFTER = JUMP_SECONDS + 0.5;
    private static final double HEARTBEAT_BASE = 1.1, FOOTSTEP_SECONDS = 0.45;
    private static final double FEAR_RATE = 2.0;
    //endregion

    //region Variables
    private boolean enabled;
    private List<Stalker> hunters;
    private Random random = new Random();
    private double fear, heartbeatIn, footstepIn;
    private long grainState = 0x9E3779B97F4A7C15L;
    private float[] vignetteEdge;
    private int vignetteWidth, vignetteHeight;
    private double jumpElapsed = -1;
    //endregion

    //region Identity
    @Override public String id() { return "horror"; }
    public static boolean wanted() {
        NetSession race = NetSession.current();
        if (race != null) return race.spec().horror();
        Boolean chosen = RunChoice.horror();
        return chosen != null ? chosen : Settings.getBoolean(SETTING, false);
    }
    public static boolean active(GameContext context) { return wanted() && available(context.geometry()); }
    private boolean perceives(GameContext context, Stalker hunter) {
        if (NetSession.current() == null) return true;
        double px = context.playerX(), py = context.playerY();
        double distance = Math.hypot(hunter.x() - px, hunter.y() - py);
        if (distance <= Stalker.HEAR_RANGE) return true;
        return distance <= Stalker.SEE_RANGE && Projection.clearLine(context.floorMap(hunter.floor()), px, py, hunter.x(), hunter.y());
    }
    //endregion

    //region Run Lifecycle
    public static boolean available(int geometry) {
        return geometry != MazeGenerator.GeometryMode.WRONG.ordinal();
    }

    @Override public synchronized void onRunStart(GameContext context) {
        boolean on = wanted();
        enabled = on && available(context.geometry());
        if (on && !enabled) context.flash(NSLocalizedString.get("horror.log.unavailable"));
        hunters = null;
        jumpElapsed = -1;
        fear = 0;
        heartbeatIn = 0;
        footstepIn = 0;
    }
    private void spawn(GameContext context) {
        long seed = (context.isCustomMap() ? "custom" : context.seed()).hashCode() * 31L + 11L;
        random = new Random(seed);
        NetSession net = NetSession.current();
        int players = net == null ? 1 : Math.max(1, net.playerCount());
        int blocks = (context.mazeWidth() * context.mazeHeight() + CELLS_PER_HUNTER - 1) / CELLS_PER_HUNTER;
        int perFloor = Math.max(1, blocks) * players;
        hunters = new ArrayList<>();
        Set<Integer> claims = ConcurrentHashMap.newKeySet();
        for (int floor = 0; floor < context.floorCount(); floor++) {
            int[][] map = context.floorMap(floor);
            for (int i = 0; i < perFloor; i++) {
                int[] cell = freeCell(map);
                if (cell == null) continue;
                Stalker hunter = new Stalker(random);
                hunter.shareClaims(claims);
                hunter.place(cell, floor);
                hunters.add(hunter);
            }
        }
        context.flash(NSLocalizedString.get("horror.log.start"));
    }

    private int[] freeCell(int[][] map) {
        for (int attempt = 0; attempt < 600; attempt++) {
            int x = 1 + random.nextInt(Math.max(1, map[0].length - 2));
            int y = 1 + random.nextInt(Math.max(1, map.length - 2));
            if (!Stalker.spawnable(map, x, y)) continue;
            if (Math.hypot(x - 1, y - 1) < 12) continue;
            return new int[] { x, y };
        }
        return null;
    }
    //endregion

    //region Input
    @Override public synchronized boolean onKey(GameContext context, KeyEvent keyEvent, boolean down) {
        if (!enabled || !down || jumpElapsed < READY_AFTER) return false;
        context.restart();
        return true;
    }
    //endregion

    //region Simulation
    @Override public synchronized void update(GameContext context, double seconds) {
        if (!enabled) return;
        NetSession net = NetSession.current();
        boolean owner = net == null || net.isHost();
        if (net != null && net.consumeLocalCaught() && jumpElapsed < 0) strike(context);
        boolean dead = jumpElapsed >= 0;
        if (dead) jumpElapsed += seconds;
        if (owner) {
            if (hunters == null) spawn(context);
            if (net != null || !dead) step(context, net, seconds);
        }
        else mirror(context, net.hunters());
        if (dead) return;
        senses(context, seconds);
    }
    private void step(GameContext context, NetSession net, double seconds) {
        int selfId = net == null ? 0 : net.localId();
        List<Rival> targets = targets(context, net, selfId);
        for (Stalker hunter : hunters) {
            Rival target = nearest(hunter, targets);
            if (target == null) continue;
            boolean self = target.id() == selfId;
            Stalker.Event event = hunter.update(levelOf(context), target.x(), target.y(), target.floor() == hunter.floor(), target.angle(), seconds);
            if (event == Stalker.Event.KILL) {
                if (net == null) {
                    strike(context);
                    return;
                }
                net.caught(target.id());
            }
            else if (event == Stalker.Event.NOTICED && self) {
                Sound.play(Sound.Effect.SCREECH);
                context.flash(NSLocalizedString.get("horror.log.noticed"));
            }
        }
        if (net != null) net.publishHunters(snapshot());
    }
    private static Stalker.Level levelOf(GameContext context) {
        return new Stalker.Level() {
            @Override public int[][] map(int floor) { return context.floorMap(floor); }
            @Override public Project.Helpers.PortalData portals(int floor) { return context.portalsOf(floor); }
            @Override public int floors() { return context.floorCount(); }
        };
    }
    private static List<Rival> targets(GameContext context, NetSession net, int selfId) {
        List<Rival> targets = new ArrayList<>();
        if (net == null) {
            targets.add(new Rival(selfId, context.playerX(), context.playerY(), context.currentFloor(), context.cameraAngle(), Double.NaN, 0, 0));
            return targets;
        }
        if (!net.isCaught(selfId))
            targets.add(new Rival(selfId, context.playerX(), context.playerY(), context.currentFloor(), context.cameraAngle(), Double.NaN, 0, 0));
        for (Rival rival : net.rivals())
            if (Double.isNaN(rival.finishSeconds()) && !net.isCaught(rival.id())) targets.add(rival);
        return targets;
    }
    private static Rival nearest(Stalker hunter, List<Rival> targets) {
        Rival best = null;
        double bestScore = Double.MAX_VALUE;
        for (Rival target : targets) {
            double score = Math.hypot(target.x() - hunter.x(), target.y() - hunter.y()) + (target.floor() == hunter.floor() ? 0 : 1000);
            if (score < bestScore) {
                bestScore = score;
                best = target;
            }
        }
        return best;
    }

    private List<NetSession.Hunter> snapshot() {
        List<NetSession.Hunter> list = new ArrayList<>(hunters.size());
        for (Stalker hunter : hunters) {
            list.add(new NetSession.Hunter(hunter.floor(), hunter.x(), hunter.y(), hunter.state().ordinal(), hunter.stride(),
                    hunter.alert(), hunter.lean(), hunter.duck(), hunter.arms(), hunter.scan(), hunter.clock()));
        }
        return list;
    }
    private void mirror(GameContext context, List<NetSession.Hunter> sent) {
        if (hunters == null) hunters = new ArrayList<>();
        while (hunters.size() > sent.size()) hunters.removeLast();
        while (hunters.size() < sent.size()) hunters.add(new Stalker(random));
        Stalker.State[] states = Stalker.State.values();
        int floor = context.currentFloor();
        double px = context.playerX(), py = context.playerY();
        for (int i = 0; i < sent.size(); i++) {
            NetSession.Hunter pose = sent.get(i);
            Stalker.State state = states[Math.clamp(pose.state(), 0, states.length - 1)];
            boolean started = hunters.get(i).mirror(pose.floor(), pose.x(), pose.y(), state, pose.stride(), pose.alert(),
                    pose.lean(), pose.duck(), pose.arms(), pose.scan(), pose.clock());
            if (started && pose.floor() == floor && Math.hypot(pose.x() - px, pose.y() - py) <= 10 && perceives(context, hunters.get(i))) Sound.play(Sound.Effect.SCREECH);
        }
    }
    private void senses(GameContext context, double seconds) {
        int floor = context.currentFloor();
        double px = context.playerX(), py = context.playerY();
        double closeness = 0;
        boolean chasing = false;
        if (hunters != null) {
            for (Stalker hunter : hunters) {
                if (hunter.floor() != floor || !perceives(context, hunter)) continue;
                double distance = Math.hypot(hunter.x() - px, hunter.y() - py);
                boolean hunting = hunter.state() == Stalker.State.CHASE || hunter.state() == Stalker.State.WINDUP;
                chasing |= hunter.state() == Stalker.State.CHASE;
                double level = hunting ? 0.6 + 0.4 * (1 - distance / 10) : 0.35 * (1 - distance / 7);
                closeness = Math.max(closeness, Math.clamp(level, 0, 1));
            }
        }
        fear += (closeness - fear) * Math.min(1, seconds * FEAR_RATE);
        heartbeatIn -= seconds;
        if (fear > 0.25 && heartbeatIn <= 0) {
            Sound.play(Sound.Effect.HEARTBEAT);
            heartbeatIn = HEARTBEAT_BASE - 0.6 * fear;
        }
        footstepIn -= seconds;
        if (chasing && footstepIn <= 0) {
            Sound.play(Sound.Effect.HUNTER_STEP);
            footstepIn = FOOTSTEP_SECONDS;
        }
    }

    private void strike(GameContext context) {
        jumpElapsed = 0;
        fear = 1;
        Sound.clearQueue();
        Sound.play(Sound.Effect.SCREAM);
        Achievements.Achievement unlocked = Achievements.unlock("horror_caught");
        if (unlocked != null) context.flash(NSLocalizedString.format("ach.unlocked", NSLocalizedString.get(unlocked.nameKey())));
    }
    //endregion

    //region Painting
    @Override
    public synchronized void paintHud(GameContext context, Graphics2D g2d, int width, int height) {
        if (!enabled) return;
        if (jumpElapsed >= 0) {
            StalkerSprite.paintJumpscare(g2d, width, height, jumpElapsed, random);
            if (jumpElapsed >= JUMP_SECONDS) paintDeath(g2d, width, height);
            return;
        }
        if (hunters == null) return;
        int floor = context.currentFloor();
        for (Stalker hunter : hunters) {
            if (hunter.floor() != floor) continue;
            Projection.Sprite sprite = Projection.projectFigure(context.playerX(), context.playerY(), context.cameraAngle(),
                    context.cameraPitch(), context.eyeHeight(), hunter.x(), hunter.y(), width, height, context.map(), Stalker.FIGURE_SCREEN);
            if (sprite != null) sprite.paint(g2d, height, g -> StalkerSprite.paint(g, sprite, hunter));
        }
    }

    private void paintDeath(Graphics2D g2d, int width, int height) {
        Graphics2D g = (Graphics2D) g2d.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(new Color(0, 0, 0, 170));
            g.fillRect(0, 0, width, height);
            g.setColor(new Color(200, 20, 20));
            g.setFont(new Font(Font.SERIF, Font.BOLD, Math.max(24, height / 9)));
            centered(g, NSLocalizedString.get("horror.death.title"), width, height / 2);
            if (jumpElapsed >= READY_AFTER) {
                g.setColor(Color.LIGHT_GRAY);
                g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, Math.max(14, height / 30)));
                centered(g, NSLocalizedString.get("horror.death.press"), width, height / 2 + height / 8);
            }
        } finally { g.dispose(); }
    }

    private static void centered(Graphics2D g, String text, int width, int baseline) { g.drawString(text, (width - g.getFontMetrics().stringWidth(text)) / 2, baseline); }
    @Override public synchronized void postProcess(GameContext context, int[] pixels, int width, int height) {
        if (!enabled) return;
        boolean strike = jumpElapsed >= 0;
        if (!strike && fear < 0.01) return;
        float[] edge = vignette(width, height);
        int total = width * height;
        float darken = (float) (fear * 0.75), red = (float) (fear * 14);
        for (int index = 0; index < total; index++) {
            int pixel = pixels[index];
            float shade = 1 - darken * edge[index];
            int r = Math.min(255, (int) (((pixel >> 16) & 255) * shade + red));
            int g = (int) (((pixel >> 8) & 255) * shade);
            int b = (int) ((pixel & 255) * shade);
            pixels[index] = (r << 16) | (g << 8) | b;
        }
        int grained = (int) (total * fear * 0.04);
        for (int k = 0; k < grained; k++) {
            int shade = nextGrain() & 255;
            pixels[(nextGrain() >>> 1) % total] = (shade << 16) | (shade << 8) | shade;
        }
        if (strike) {
            int statics = (int) (total * 0.25);
            for (int k = 0; k < statics; k++) {
                int shade = nextGrain() & 255;
                pixels[(nextGrain() >>> 1) % total] = (shade << 16) | (shade << 8) | shade;
            }
        }
    }
    private float[] vignette(int width, int height) {
        if (vignetteWidth == width && vignetteHeight == height && vignetteEdge != null) return vignetteEdge;
        float[] edge = new float[width * height];
        double cx = width / 2.0, cy = height / 2.0;
        for (int y = 0; y < height; y++) {
            double dy = (y - cy) / cy;
            for (int x = 0; x < width; x++) {
                double dx = (x - cx) / cx;
                edge[y * width + x] = (float) Math.clamp((dx * dx * 0.6 + dy * dy - 0.15) / 0.9, 0, 1);
            }
        }
        vignetteEdge = edge;
        vignetteWidth = width;
        vignetteHeight = height;
        return edge;
    }
    private int nextGrain() {
        long x = grainState;
        x ^= x >>> 12;
        x ^= x << 25;
        x ^= x >>> 27;
        grainState = x;
        return (int) ((x * 0x2545F4914F6CDD1DL) >>> 32);
    }
    //endregion
}