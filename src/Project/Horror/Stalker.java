package Project.Horror;

import Project.Roguelike.Projection;
import Project.Helpers.*;

import java.util.*;

public final class Stalker {
    //region Constants
    public static final double FIGURE_SCREEN = 0.55;
    static final double NOTICE_RANGE = 10, SEE_RANGE = 12, HEAR_RANGE = 1.5, CLOSE_RANGE = 3.5;
    static final double PATROL_SPEED = 0.9, RETREAT_SPEED = 1.6, CHASE_SPEED = 1.1, SEARCH_SPEED = 0.9;
    static final double LOSE_SECONDS = 5, SEARCH_SECONDS = 4, WINDUP_SECONDS = 0.9;
    static final double STRIKE_RANGE = 0.9, WINDUP_RANGE = 1.4, WINDUP_CREEP = 0.7;
    static final double WATCH_MIN = 3.5, WATCH_MAX = 7;
    static final double REPLAN_SECONDS = 0.35;
    static final double SCENT_RANGE = 14;
    static final double NEAR_RANGE = 12, NEAR_SECONDS = 6;
    static final double TRACK_RANGE = 3;
    static final int AMBUSH_RADIUS = 16, ROUTE_NODES = 12000, STAIRS_CLEARANCE = 6;
    static final double ADJACENT_HEAR = 1.0;
    private static final int[][] DIRECTIONS = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
    //endregion

    //region Variables
    public enum State { AMBUSH, WATCH, RETREAT, CHASE, WINDUP, SEARCH }
    public enum Event { NONE, NOTICED, KILL }

    private final Random random;
    private double x, y;
    private int floor;
    private State state = State.AMBUSH;
    private final ArrayDeque<int[]> path = new ArrayDeque<>();
    private Level level = Level.single(new int[][] { { MazeGenerator.WALL } });

    public interface Level {
        int[][] map(int floor);
        PortalData portals(int floor);
        int floors();
        static Level single(int[][] map) {
            return new Level() {
                @Override public int[][] map(int floor) { return map; }
                @Override public PortalData portals(int floor) { return null; }
                @Override public int floors() { return 1; }
            };
        }
    }
    private boolean planned;
    private Set<Integer> claims = new HashSet<>();
    private static final int SPREAD = 5;
    private boolean moving;
    private double replanIn, lostFor, watchFor, windupFor, searchFor, stateTime, clock, nearFor;
    private double lastX, lastY;
    private boolean hasLast;
    private double stride, alert, lean, duck, arms, scan;
    //endregion

    //region Constructors
    public Stalker(Random random) { this.random = random; }
    /** Ambush cells picked by the other stalkers of the run; this stalker keeps away from them. */
    public void shareClaims(Set<Integer> shared) { claims = shared; }
    //endregion

    //region State
    public void place(int[] cell, int floor) {
        this.x = cell[0] + 0.5;
        this.y = cell[1] + 0.5;
        this.floor = floor;
    }

    public double x() { return x; }
    public double y() { return y; }
    public int floor() { return floor; }
    public State state() { return state; }
    public double stride() { return stride; }
    public double alert() { return alert; }
    public double lean() { return lean; }
    public double duck() { return duck; }
    public double arms() { return arms; }
    public double scan() { return scan; }
    public double clock() { return clock; }

    public boolean mirror(int floor, double x, double y, State next, double stride, double alert, double lean, double duck,
                          double arms, double scan, double clock) {
        boolean started = next == State.CHASE && state != State.CHASE;
        this.floor = floor;
        this.x = x;
        this.y = y;
        this.state = next;
        this.stride = stride;
        this.alert = alert;
        this.lean = lean;
        this.duck = duck;
        this.arms = arms;
        this.scan = scan;
        this.clock = clock;
        return started;
    }
    //endregion

    //region Step
    public Event update(int[][] map, double px, double py, boolean sameFloor, double viewAngle, double dt) {
        return update(Level.single(map), px, py, sameFloor, viewAngle, dt);
    }
    public Event update(Level level, double px, double py, boolean sameFloor, double viewAngle, double dt) {
        this.level = level;
        int[][] map = level.map(floor);
        clock += dt;
        stateTime += dt;
        double distance = Math.hypot(x - px, y - py);
        boolean visible = sameFloor && distance <= SEE_RANGE && Projection.clearLine(map, px, py, x, y);
        boolean sees = visible && distance <= NOTICE_RANGE;
        boolean seen = visible && inView(viewAngle, px, py);
        if (sees) {
            lastX = px;
            lastY = py;
            hasLast = true;
            lostFor = 0;
        }
        else lostFor += dt;
        if (!planned) {
            planned = true;
            startAmbush(map, px, py, sameFloor);
        }
        if (state != State.CHASE && state != State.WINDUP) {
            nearFor = sameFloor && distance <= NEAR_RANGE ? nearFor + dt : Math.max(0, nearFor - dt * 0.5);
            if (sameFloor && nearFor >= NEAR_SECONDS) {
                animate(dt);
                return hunt(px, py, false);
            }
        }
        Event event = switch (state) {
            case AMBUSH -> ambush(map, px, py, sameFloor, seen, sees, distance, dt);
            case WATCH -> watch(map, px, py, sameFloor, seen, sees, distance, dt);
            case RETREAT -> retreat(map, px, py, sameFloor, sees, distance, dt);
            case CHASE -> chase(map, px, py, sees, distance, dt);
            case WINDUP -> windup(map, px, py, distance, dt);
            case SEARCH -> search(map, px, py, sameFloor, sees, dt);
        };
        animate(dt);
        return event;
    }

    private Event ambush(int[][] map, double px, double py, boolean sameFloor, boolean seen, boolean sees, double distance, double dt) {
        if (seen) return retreatFrom(map, px, py);
        if (sameFloor && (heard(map, px, py, distance) || (sees && distance <= CLOSE_RANGE))) return hunt(px, py, sees && distance <= CLOSE_RANGE);
        if (followPath(map, dt, PATROL_SPEED)) {
            if (sameFloor) {
                state = State.WATCH;
                stateTime = 0;
                watchFor = WATCH_MIN + random.nextDouble() * (WATCH_MAX - WATCH_MIN);
            }
            else startAmbush(map, px, py, false);
        }
        return Event.NONE;
    }

    private Event watch(int[][] map, double px, double py, boolean sameFloor, boolean seen, boolean sees, double distance, double dt) {
        watchFor -= dt;
        if (seen) return retreatFrom(map, px, py);
        if (!sameFloor) {
            startAmbush(map, px, py, false);
            return Event.NONE;
        }
        if (heard(map, px, py, distance) || (sees && distance <= CLOSE_RANGE)) return hunt(px, py, sees && distance <= CLOSE_RANGE);
        if (watchFor <= 0 && sees && distance <= NOTICE_RANGE) return hunt(px, py, true);
        if (watchFor <= 0) startAmbush(map, px, py, true);
        return Event.NONE;
    }

    private Event retreat(int[][] map, double px, double py, boolean sameFloor, boolean sees, double distance, double dt) {
        if (sameFloor && heard(map, px, py, distance)) return hunt(px, py, false);
        if (followPath(map, dt, RETREAT_SPEED)) startAmbush(map, px, py, sameFloor);
        return Event.NONE;
    }

    private Event chase(int[][] map, double px, double py, boolean sees, double distance, double dt) {
        replanIn -= dt;
        boolean tracks = sees || distance <= TRACK_RANGE;
        if (tracks) {
            lastX = px;
            lastY = py;
            hasLast = true;
            if (replanIn <= 0) {
                replanIn = REPLAN_SECONDS;
                setRoute(map, cellOf(px), cellOf(py));
            }
        }
        if (sees && distance <= WINDUP_RANGE) {
            state = State.WINDUP;
            stateTime = 0;
            windupFor = 0;
            path.clear();
            return Event.NONE;
        }
        if (!tracks && lostFor > LOSE_SECONDS) return beginSearch(map);
        if (tracks && path.isEmpty()) stepToward(map, px, py, CHASE_SPEED, dt);
        if (followPath(map, dt, CHASE_SPEED) && !tracks) return beginSearch(map);
        return Event.NONE;
    }
    private Event windup(int[][] map, double px, double py, double distance, double dt) {
        windupFor += dt;
        if (distance > WINDUP_RANGE + 0.5) {
            state = State.CHASE;
            replanIn = 0;
            return Event.NONE;
        }
        moving = false;
        if (distance > STRIKE_RANGE) stepToward(map, px, py, WINDUP_CREEP, dt);
        if (windupFor >= WINDUP_SECONDS) {
            if (distance <= STRIKE_RANGE + 0.3) return Event.KILL;
            state = State.CHASE;
            replanIn = 0;
        }
        return Event.NONE;
    }

    private Event search(int[][] map, double px, double py, boolean sameFloor, boolean sees, double dt) {
        if (sees) return hunt(px, py, true);
        if (!path.isEmpty()) {
            followPath(map, dt, SEARCH_SPEED);
            return Event.NONE;
        }
        searchFor += dt;
        if (searchFor >= SEARCH_SECONDS) startAmbush(map, px, py, sameFloor);
        return Event.NONE;
    }
    private boolean heard(int[][] map, double px, double py, double distance) {
        return distance <= HEAR_RANGE && (distance <= ADJACENT_HEAR || Projection.clearLine(map, px, py, x, y));
    }
    private Event hunt(double px, double py, boolean noticed) {
        state = State.CHASE;
        stateTime = 0;
        replanIn = 0;
        lostFor = 0;
        lastX = px;
        lastY = py;
        hasLast = true;
        nearFor = 0;
        path.clear();
        return noticed ? Event.NOTICED : Event.NONE;
    }

    private Event beginSearch(int[][] map) {
        state = State.SEARCH;
        stateTime = 0;
        searchFor = 0;
        path.clear();
        if (hasLast) setRoute(map, cellOf(lastX), cellOf(lastY));
        return Event.NONE;
    }

    private Event retreatFrom(int[][] map, double px, double py) {
        if (state == State.RETREAT) return Event.NONE;
        int[] target = null;
        int best = Integer.MAX_VALUE;
        for (Map.Entry<Integer, Integer> entry : reach(map, cellOf(x), cellOf(y), 8).entrySet()) {
            int id = entry.getKey();
            int cx = id % map[0].length, cy = id / map[0].length;
            if (Projection.clearLine(map, px, py, cx + 0.5, cy + 0.5)) continue;
            if (entry.getValue() < best) {
                best = entry.getValue();
                target = new int[] { cx, cy };
            }
        }
        state = State.RETREAT;
        stateTime = 0;
        path.clear();
        if (target == null) return Event.NONE;
        ArrayDeque<int[]> route = levelRoute(floor, target[0], target[1], ROUTE_NODES);
        if (route != null) path.addAll(route);
        return Event.NONE;
    }
    private void startAmbush(int[][] map, double px, double py, boolean hasPlayer) {
        int width = map[0].length;
        Map<Integer, Integer> mine = reach(map, cellOf(x), cellOf(y), AMBUSH_RADIUS);
        Map<Integer, Integer> theirs = hasPlayer ? reach(map, cellOf(px), cellOf(py), AMBUSH_RADIUS + 2) : null;
        int bestId = -1;
        double bestScore = Double.MAX_VALUE;
        for (Map.Entry<Integer, Integer> entry : mine.entrySet()) {
            int id = entry.getKey();
            int cx = id % width, cy = id / width;
            double own = entry.getValue();
            double score;
            if (claimed(id, width) || wallsAround(map, cx, cy) >= 3) continue;
            if (hasPlayer) {
                Integer playerSteps = theirs.get(id);
                if (playerSteps == null || playerSteps < 8 || playerSteps > 18) continue;
                if (Projection.clearLine(map, px, py, cx + 0.5, cy + 0.5)) continue;
                double straight = Math.hypot(cx + 0.5 - px, cy + 0.5 - py);
                score = own * 0.25 + Math.abs(playerSteps - 7) + Math.max(0, straight - 8) * 2 + (wallsAround(map, cx, cy) >= 2 ? 0 : 1.5) + random.nextDouble();
            }
            else {
                if (own < 4 || own > 10) continue;
                score = Math.abs(own - 6) + random.nextDouble() * 2;
            }
            if (score < bestScore) {
                bestScore = score;
                bestId = id;
            }
        }
        state = State.AMBUSH;
        stateTime = 0;
        path.clear();
        if (bestId < 0) {
            if (hasPlayer && Math.hypot(x - px, y - py) <= SCENT_RANGE) approach(map, px, py);
            else roamTo(map);
            if (path.isEmpty()) {
                state = State.WATCH;
                watchFor = 1.5;
            }
            return;
        }
        claims.add(bestId);
        setRoute(map, bestId % width, bestId / width);
        if (path.isEmpty()) {
            state = State.WATCH;
            watchFor = 1.5;
        }
    }
    private boolean claimed(int id, int width) {
        int cx = id % width, cy = id / width;
        for (int other : claims) {
            if (Math.abs(other % width - cx) <= SPREAD && Math.abs(other / width - cy) <= SPREAD) return true;
        }
        return false;
    }
    private void approach(int[][] map, double px, double py) {
        setRoute(map, cellOf(px), cellOf(py));
        if (!path.isEmpty()) return;
        int width = map[0].length;
        int best = -1;
        double bestDistance = Double.MAX_VALUE;
        for (Map.Entry<Integer, Integer> entry : reach(map, cellOf(x), cellOf(y), AMBUSH_RADIUS).entrySet()) {
            int id = entry.getKey();
            if (entry.getValue() < 2) continue;
            double distance = Math.hypot(id % width + 0.5 - px, (double) id / width + 0.5 - py);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = id;
            }
        }
        if (best >= 0) setRoute(map, best % width, best / width);
    }
    private void roamTo(int[][] map) {
        List<Integer> open = new ArrayList<>();
        int width = map[0].length;
        for (Map.Entry<Integer, Integer> entry : reach(map, cellOf(x), cellOf(y), 10).entrySet()) {
            int id = entry.getKey();
            if (entry.getValue() >= 3 && wallsAround(map, id % width, id / width) < 3) open.add(id);
        }
        if (open.isEmpty()) return;
        int id = open.get(random.nextInt(open.size()));
        setRoute(map, id % map[0].length, id / map[0].length);
    }

    private void setRoute(int[][] map, int tx, int ty) {
        path.clear();
        ArrayDeque<int[]> route = levelRoute(floor, tx, ty, ROUTE_NODES);
        if (route != null) path.addAll(route);
    }
    private boolean followPath(int[][] map, double dt, double speed) {
        moving = false;
        if (path.isEmpty()) return true;
        int[] next = path.peek();
        boolean jump = next.length > 3 && next[3] == 1;
        if (jump) {
            floor = next[2];
            x = next[0] + 0.5;
            y = next[1] + 0.5;
            path.poll();
            moving = true;
            return path.isEmpty();
        }
        double tx = next[0] + 0.5, ty = next[1] + 0.5;
        double dx = tx - x, dy = ty - y;
        double distance = Math.hypot(dx, dy);
        double step = speed * dt;
        if (distance <= step) {
            x = tx;
            y = ty;
            path.poll();
        }
        else {
            x += dx / distance * step;
            y += dy / distance * step;
        }
        moving = true;
        return path.isEmpty();
    }

    private boolean inView(double viewAngle, double px, double py) {
        double bearing = Math.atan2(y - py, x - px) - viewAngle;
        bearing = Math.atan2(Math.sin(bearing), Math.cos(bearing));
        return Math.abs(bearing) < Projection.halfFov() + 0.1;
    }

    private void animate(double dt) {
        boolean chasing = state == State.CHASE || state == State.WINDUP;
        stride = ease(stride, moving ? 1 : 0, dt, 8);
        alert = ease(alert, chasing || state == State.SEARCH ? 1 : state == State.WATCH ? 0.3 : 0, dt, 4);
        duck = ease(duck, state == State.RETREAT ? 1 : state == State.WATCH ? 0.5 : 0, dt, 3);
        arms = ease(arms, state == State.WINDUP ? 1 : chasing ? 0.5 : 0, dt, 5);
        double leanTarget = state == State.WATCH ? Math.sin(stateTime * 0.9) * 0.7 : 0;
        lean = ease(lean, leanTarget, dt, 2);
        scan = ease(scan, state == State.SEARCH && path.isEmpty() ? Math.sin(stateTime * 2.2) : 0, dt, 3);
    }

    private static double ease(double value, double target, double dt, double rate) {
        return value + (target - value) * Math.min(1, dt * rate);
    }
    //endregion

    //region Routes
    public static boolean passable(int[][] map, int x, int y) {
        return y >= 0 && y < map.length && x >= 0 && x < map[0].length && map[y][x] != MazeGenerator.WALL;
    }
    public static boolean spawnable(int[][] map, int x, int y) {
        if (!passable(map, x, y) || map[y][x] == MazeGenerator.PORTAL) return false;
        if (wallsAround(map, x, y) >= 3) return false;
        for (int dy = -STAIRS_CLEARANCE; dy <= STAIRS_CLEARANCE; dy++)
            for (int dx = -STAIRS_CLEARANCE; dx <= STAIRS_CLEARANCE; dx++) {
                int cx = x + dx, cy = y + dy;
                if (cy >= 0 && cy < map.length && cx >= 0 && cx < map[0].length && map[cy][cx] == MazeGenerator.PORTAL) return false;
            }
        return true;
    }
    private void stepToward(int[][] map, double tx, double ty, double speed, double dt) {
        double dx = tx - x, dy = ty - y, distance = Math.hypot(dx, dy);
        if (distance < 1e-6) return;
        double step = Math.min(distance, speed * dt);
        double nx = x + dx / distance * step, ny = y + dy / distance * step;
        if (!passable(map, cellOf(nx), cellOf(ny))) return;
        x = nx;
        y = ny;
        moving = true;
    }
    private static int cellOf(double coordinate) { return (int) Math.floor(coordinate); }
    private static int wallsAround(int[][] map, int x, int y) {
        int walls = 0;
        for (int[] d : DIRECTIONS) if (!passable(map, x + d[0], y + d[1])) walls++;
        return walls;
    }
    static Map<Integer, Integer> reach(int[][] map, int sx, int sy, int radius) {
        int width = map[0].length;
        Map<Integer, Integer> steps = new HashMap<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        int start = sy * width + sx;
        steps.put(start, 0);
        queue.add(start);
        while (!queue.isEmpty()) {
            int current = queue.poll();
            int depth = steps.get(current);
            if (depth >= radius) continue;
            int cx = current % width, cy = current / width;
            for (int[] d : DIRECTIONS) {
                int nx = cx + d[0], ny = cy + d[1];
                if (!passable(map, nx, ny)) continue;
                int id = ny * width + nx;
                if (steps.containsKey(id)) continue;
                steps.put(id, depth + 1);
                queue.add(id);
            }
        }
        return steps;
    }
    private ArrayDeque<int[]> levelRoute(int targetFloor, int tx, int ty, int maxNodes) {
        int[][] first = level.map(floor);
        int width = first[0].length, height = first.length, cells = width * height;
        int sx = cellOf(x), sy = cellOf(y);
        if (!passable(level.map(targetFloor), tx, ty) || !passable(first, sx, sy)) return null;
        int startId = floor * cells + sy * width + sx;
        int goalId = targetFloor * cells + ty * width + tx;
        Map<Integer, Integer> from = new HashMap<>();
        java.util.Set<Integer> jumps = new java.util.HashSet<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        from.put(startId, -1);
        queue.add(startId);
        while (!queue.isEmpty() && !from.containsKey(goalId)) {
            if (from.size() > maxNodes) return null;
            int current = queue.poll();
            int f = current / cells, rest = current % cells, cx = rest % width, cy = rest / width;
            int[][] map = level.map(f);
            PortalData portals = level.portals(f);
            PortalData.Portal portal = portals != null && map[cy][cx] == MazeGenerator.PORTAL ? portals.get(cx, cy) : null;
            int before = from.get(current);
            boolean throughPortal = false;
            if (portal != null && before >= 0) {
                int bf = before / cells, brest = before % cells;
                throughPortal = bf == f && brest % width == cx - portal.dirX && brest / width == cy - portal.dirY;
            }
            if (!throughPortal) {
                for (int[] d : DIRECTIONS) {
                    int nx = cx + d[0], ny = cy + d[1];
                    if (!passable(map, nx, ny)) continue;
                    int id = f * cells + ny * width + nx;
                    if (from.containsKey(id)) continue;
                    from.put(id, current);
                    queue.add(id);
                }
                continue;
            }
            int ef = portal.linkedFloor >= 0 ? portal.linkedFloor : f;
            int ex = portal.linkedX + portal.linkedDirX, ey = portal.linkedY + portal.linkedDirY;
            if (!passable(level.map(ef), ex, ey)) continue;
            int id = ef * cells + ey * width + ex;
            if (!from.containsKey(id)) {
                from.put(id, current);
                jumps.add(id);
                queue.add(id);
            }
        }
        if (!from.containsKey(goalId)) return null;
        ArrayDeque<int[]> out = new ArrayDeque<>();
        for (int node = goalId; node != startId; node = from.get(node)) {
            int rest = node % cells;
            out.addFirst(new int[] { rest % width, rest / width, node / cells, jumps.contains(node) ? 1 : 0 });
        }
        return out;
    }
    static ArrayDeque<int[]> route(int[][] map, int sx, int sy, int tx, int ty, int maxNodes) {
        if (!passable(map, tx, ty) || !passable(map, sx, sy)) return null;
        int width = map[0].length;
        int start = sy * width + sx, goal = ty * width + tx;
        Map<Integer, Integer> from = new HashMap<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        from.put(start, -1);
        queue.add(start);
        while (!queue.isEmpty() && !from.containsKey(goal)) {
            if (from.size() > maxNodes) return null;
            int current = queue.poll();
            int cx = current % width, cy = current / width;
            for (int[] d : DIRECTIONS) {
                int nx = cx + d[0], ny = cy + d[1];
                if (!passable(map, nx, ny)) continue;
                int id = ny * width + nx;
                if (from.containsKey(id)) continue;
                from.put(id, current);
                queue.add(id);
            }
        }
        if (!from.containsKey(goal)) return null;
        ArrayDeque<int[]> out = new ArrayDeque<>();
        for (int cell = goal; cell != start; cell = from.get(cell)) out.addFirst(new int[] { cell % width, cell / width });
        return out;
    }
    //endregion
}