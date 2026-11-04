package Project.ThemeCreator;
import Project.CreatorKit.*;

import java.awt.Color;
import java.util.*;

public final class ThemeGraph {
    //region Variables
    //region Caches
    private static final Map<CkProject, Plan> PLANS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<String, double[]> TABLES = new java.util.concurrent.ConcurrentHashMap<>();
    //endregion
    //endregion

    //region Constructors
    private ThemeGraph() {}
    //endregion

    //region Graph Evaluation
    public static void changed(CkProject ckProject) {
        PLANS.remove(ckProject);
    }
    public static Map<String, Object> liveOverrides(CkProject ckProject) {
        Plan plan = PLANS.get(ckProject);
        return plan == null ? Map.of() : plan.lastOverrides;
    }
    public static Map<String, Object> evaluate(CkProject ckProject, double t) {
        if (ckProject.nodes.isEmpty()) return Map.of();
        ThemeNodes.install();
        Map<String, Object> overrides = new LinkedHashMap<>();
        Plan p = plan(ckProject);
        if (p.order == null) return overrides;
        Object[] out = new Object[p.size];
        Slot slot = new Slot();
        p.dt = Double.isNaN(p.lastT) ? 0 : Math.clamp(t - p.lastT, 0, 1);
        p.lastT = t;
        for (NodeInst nodeInst : p.order) {
            slot.value = null;
            compute(p, nodeInst, t, out, slot, overrides);
            out[nodeInst.id] = slot.value;
        }
        p.lastOut = out;
        p.lastOverrides = overrides;
        return overrides;
    }
    public static String liveText(CkProject ckProject, int node) {
        Plan plan = PLANS.get(ckProject);
        if (plan == null || plan.lastOut == null || node < 0 || node >= plan.lastOut.length) return null;
        return format(plan.lastOut[node]);
    }
    static String format(Object v) {
        if (v instanceof Double d) return d.isNaN() ? "—" : String.format(Locale.ROOT, "%.2f", d);
        if (v instanceof Boolean b) return b ? "true" : "false";
        if (v instanceof Color color) return String.format(Locale.ROOT, "#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
        return null;
    }
    //endregion

    //region Plan Building
    public static List<NodeInst> order(CkProject ckProject) { return orderOf(ckProject); }
    private static Plan plan(CkProject ckProject) {
        Plan plan = PLANS.get(ckProject);
        if (plan == null || plan.nodes != ckProject.nodes.size() || plan.links != ckProject.links.size()) {
            plan = new Plan(ckProject);
            PLANS.put(ckProject, plan);
        }
        return plan;
    }
    private static List<NodeInst> orderOf(CkProject ckProject) {
        Map<Integer, Integer> state = new HashMap<>();
        List<NodeInst> out = new ArrayList<>();
        List<NodeInst> sorted = new ArrayList<>(ckProject.nodes);
        sorted.sort(Comparator.comparingInt(nodeInst -> nodeInst.id));
        for (NodeInst nodeInst : sorted) if (!visit(ckProject, nodeInst, state, out)) return null;
        return out;
    }
    private static boolean visit(CkProject ckProject, NodeInst nodeInst, Map<Integer, Integer> state, List<NodeInst> out) {
        Integer s = state.get(nodeInst.id);
        if (s != null) return s == 2;
        state.put(nodeInst.id, 1);
        for (Link link : ckProject.links) {
            if (link.to() != nodeInst.id) continue;
            NodeInst from = ckProject.node(link.from());
            if (from != null && !visit(ckProject, from, state, out)) return false;
        }
        state.put(nodeInst.id, 2);
        out.add(nodeInst);
        return true;
    }
    private static final class Plan {
        final int nodes, links;
        final int size;
        final List<NodeInst> order;
        final Map<String, Link>[] incoming;
        final Map<String, Object>[] literals;
        final Map<Integer, double[]> memory = new HashMap<>();
        double lastT = Double.NaN, dt;
        Object[] lastOut;
        Map<String, Object> lastOverrides = Map.of();

        @SuppressWarnings("unchecked")
        Plan(CkProject ckProject) {
            nodes = ckProject.nodes.size();
            links = ckProject.links.size();
            int max = 0;
            for (NodeInst nodeInst : ckProject.nodes) max = Math.max(max, nodeInst.id);
            size = max + 1;
            incoming = new Map[size];
            literals = new Map[size];
            for (Link link : ckProject.links) {
                if (link.to() < 0 || link.to() >= size) continue;
                if (incoming[link.to()] == null) incoming[link.to()] = new HashMap<>();
                incoming[link.to()].put(link.toPort(), link);
            }
            for (NodeInst nodeInst : ckProject.nodes) literals[nodeInst.id] = literalsOf(nodeInst);
            order = orderOf(ckProject);
        }
    }
    //endregion

    //region Node Computation
    private static void compute(Plan plan, NodeInst nodeInst, double t, Object[] out,
                                Slot values, Map<String, Object> overrides) {
        switch (nodeInst.kind) {
            case "th.time" -> values.put("out", t * num(lit(plan, nodeInst, "speed"), 1));
            case "th.number" -> values.put("out", num(lit(plan, nodeInst, "value"), 0));
            case "th.int" -> values.put("out", (double) Math.round(num(lit(plan, nodeInst, "value"), 0)));
            case "th.bool" -> values.put("out", Boolean.TRUE.equals(lit(plan, nodeInst, "value")));
            case "th.random" -> values.put("out", new Random((long) num(lit(plan, nodeInst, "seed"), 1)).nextDouble());
            case "th.dt" -> values.put("out", plan.dt);
            case "th.integrate" -> {
                double[] m = mem(plan, nodeInst, 1);
                if (Double.isNaN(m[0])) m[0] = 0;
                m[0] += x(plan, nodeInst, "x", out) * plan.dt;
                values.put("out", m[0]);
            }
            case "th.edge" -> {
                double[] m = mem(plan, nodeInst, 1);
                double now = bool(plan, nodeInst, "in", out) ? 1 : 0;
                double prev = Double.isNaN(m[0]) ? now : m[0];
                values.put("out", now > 0.5 && prev <= 0.5);
                m[0] = now;
            }
            case "th.toggle" -> {
                double[] m = mem(plan, nodeInst, 2);
                double now = bool(plan, nodeInst, "in", out) ? 1 : 0;
                double prev = Double.isNaN(m[1]) ? now : m[1];
                double val = Double.isNaN(m[0]) ? 0 : m[0];
                if (now > 0.5 && prev <= 0.5) val = 1 - val;
                m[0] = val;
                m[1] = now;
                values.put("out", val > 0.5);
            }
            case "th.counter" -> {
                double[] m = mem(plan, nodeInst, 2);
                double now = bool(plan, nodeInst, "in", out) ? 1 : 0;
                double prev = Double.isNaN(m[1]) ? now : m[1];
                double count = Double.isNaN(m[0]) ? 0 : m[0];
                if (now > 0.5 && prev <= 0.5) count++;
                m[0] = count;
                m[1] = now;
                values.put("out", count);
            }
            case "th.timeSinceEdge" -> {
                double[] m = mem(plan, nodeInst, 2);
                double now = bool(plan, nodeInst, "in", out) ? 1 : 0;
                double prev = Double.isNaN(m[1]) ? now : m[1];
                if (Double.isNaN(m[0])) m[0] = t;
                if (now > 0.5 && prev <= 0.5) m[0] = t;
                m[1] = now;
                values.put("out", t - m[0]);
            }
            case "th.randomOnEdge" -> {
                double[] m = mem(plan, nodeInst, 3);
                double now = bool(plan, nodeInst, "in", out) ? 1 : 0;
                double prev = Double.isNaN(m[1]) ? now : m[1];
                double count = Double.isNaN(m[2]) ? 0 : m[2];
                if (Double.isNaN(m[0])) m[0] = hash(count + 0.5, 7);
                if (now > 0.5 && prev <= 0.5) {
                    count++;
                    m[0] = hash(count + 0.5, 7);
                }
                m[1] = now;
                m[2] = count;
                values.put("out", m[0]);
            }
            case "th.keyHeld" -> values.put("out", ThemeInput.held(text(plan, nodeInst, "key", "Space")));
            case "th.keyPressed" -> {
                double[] m = mem(plan, nodeInst, 1);
                double presses = ThemeInput.presses(text(plan, nodeInst, "key", "Space"));
                if (Double.isNaN(m[0])) {
                    m[0] = presses;
                    values.put("out", false);
                } else {
                    values.put("out", presses != m[0]);
                    m[0] = presses;
                }
            }
            case "th.polygonMask" -> {
                int sides = (int) Math.max(3, Math.round(num(lit(plan, nodeInst, "sides"), 3)));
                double px = x(plan, nodeInst, "x", out), py = x(plan, nodeInst, "y", out), r = x(plan, nodeInst, "radius", out);
                double ang = x(plan, nodeInst, "angle", out), soft = Math.max(0, x(plan, nodeInst, "softness", out));
                double k = 2 * Math.PI / sides;
                double th = Math.atan2(py, px) - ang;
                double a = th - k * Math.floor(th / k) - k / 2;
                double reach = r * Math.cos(Math.PI / sides) / Math.cos(a);
                values.put("out", 1 - smooth01(-soft, soft, Math.hypot(px, py) - reach));
            }
            case "th.follow" -> {
                double target = x(plan, nodeInst, "x", out), speed = Math.max(0, x(plan, nodeInst, "speed", out));
                double[] m = plan.memory.computeIfAbsent(nodeInst.id, k -> new double[]{Double.NaN});
                if (Double.isNaN(m[0])) m[0] = target;
                else m[0] += (target - m[0]) * (1 - Math.exp(-speed * plan.dt));
                values.put("out", m[0]);
            }
            case "th.color" -> values.put("out", color(lit(plan, nodeInst, "color"), Color.WHITE));

            case "th.key" -> values.put("out", ThemeInput.down(text(plan, nodeInst, "key", "Space")));
            case "th.axis" -> values.put("out", (ThemeInput.down(text(plan, nodeInst, "negative", "A")) ? -1.0 : 0.0)
                    + (ThemeInput.down(text(plan, nodeInst, "positive", "D")) ? 1.0 : 0.0));
            case "th.mouseX" -> values.put("out", ThemeInput.mouseX());
            case "th.mouseY" -> values.put("out", ThemeInput.mouseY());
            case "th.mouseDX" -> values.put("out", ThemeInput.deltaX());
            case "th.mouseDY" -> values.put("out", ThemeInput.deltaY());
            case "th.mouseDown" -> values.put("out", ThemeInput.buttonDown(text(plan, nodeInst, "button", "left")));

            case "th.sin" -> values.put("out", Math.sin(x(plan, nodeInst, "a", out)));
            case "th.cos" -> values.put("out", Math.cos(x(plan, nodeInst, "a", out)));
            case "th.table" -> values.put("out", table(text(plan, nodeInst, "values", "0"), num(lit(plan, nodeInst, "period"), 1), x(plan, nodeInst, "a", out)));
            case "th.abs" -> values.put("out", Math.abs(x(plan, nodeInst, "a", out)));
            case "th.sign" -> values.put("out", Math.signum(x(plan, nodeInst, "a", out)));
            case "th.floor" -> values.put("out", Math.floor(x(plan, nodeInst, "a", out)));
            case "th.ceil" -> values.put("out", Math.ceil(x(plan, nodeInst, "a", out)));
            case "th.round" -> values.put("out", (double) Math.round(x(plan, nodeInst, "a", out)));
            case "th.fract" -> {
                double a = x(plan, nodeInst, "a", out);
                values.put("out", a - Math.floor(a));
            }
            case "th.sqrt" -> values.put("out", Math.sqrt(Math.max(0, x(plan, nodeInst, "a", out))));
            case "th.exp" -> values.put("out", Math.exp(x(plan, nodeInst, "a", out)));
            case "th.log" -> values.put("out", Math.log(Math.max(1e-12, x(plan, nodeInst, "a", out))));
            case "th.tri" -> {
                double a = x(plan, nodeInst, "a", out);
                values.put("out", Math.abs((a - Math.floor(a)) * 2 - 1));
            }
            case "th.ease" -> {
                double a = clamp01(x(plan, nodeInst, "a", out));
                values.put("out", a * a * (3 - 2 * a));
            }
            case "th.deg" -> values.put("out", x(plan, nodeInst, "a", out) * Math.PI / 180);
            case "th.rad" -> values.put("out", x(plan, nodeInst, "a", out) * 180 / Math.PI);

            case "th.add" -> values.put("out", x(plan, nodeInst, "a", out) + x(plan, nodeInst, "b", out));
            case "th.sub" -> values.put("out", x(plan, nodeInst, "a", out) - x(plan, nodeInst, "b", out));
            case "th.mul" -> values.put("out", x(plan, nodeInst, "a", out) * x(plan, nodeInst, "b", out));
            case "th.div" -> {
                double b = x(plan, nodeInst, "b", out);
                values.put("out", b == 0 ? 0.0 : x(plan, nodeInst, "a", out) / b);
            }
            case "th.mod" -> {
                double a = x(plan, nodeInst, "a", out), b = x(plan, nodeInst, "b", out);
                values.put("out", b == 0 ? 0.0 : a - b * Math.floor(a / b));
            }
            case "th.pow" -> values.put("out", Math.pow(x(plan, nodeInst, "a", out), x(plan, nodeInst, "b", out)));
            case "th.min" -> values.put("out", Math.min(x(plan, nodeInst, "a", out), x(plan, nodeInst, "b", out)));
            case "th.max" -> values.put("out", Math.max(x(plan, nodeInst, "a", out), x(plan, nodeInst, "b", out)));
            case "th.atan2" -> values.put("out", Math.atan2(x(plan, nodeInst, "a", out), x(plan, nodeInst, "b", out)));

            case "th.lerp" -> {
                double a = x(plan, nodeInst, "a", out), b = x(plan, nodeInst, "b", out), k = x(plan, nodeInst, "t", out);
                values.put("out", a + (b - a) * k);
            }
            case "th.clamp" -> {
                double a = x(plan, nodeInst, "a", out), lo = x(plan, nodeInst, "lo", out), hi = x(plan, nodeInst, "hi", out);
                values.put("out", Math.max(lo, Math.min(hi, a)));
            }
            case "th.smooth" -> {
                double a = x(plan, nodeInst, "a", out), lo = x(plan, nodeInst, "lo", out), hi = x(plan, nodeInst, "hi", out);
                double k = hi == lo ? (a >= hi ? 1 : 0) : clamp01((a - lo) / (hi - lo));
                values.put("out", k * k * (3 - 2 * k));
            }
            case "th.smootherstep" -> {
                double a = x(plan, nodeInst, "a", out), lo = x(plan, nodeInst, "lo", out), hi = x(plan, nodeInst, "hi", out);
                double k = hi == lo ? (a >= hi ? 1 : 0) : clamp01((a - lo) / (hi - lo));
                values.put("out", k * k * k * (k * (k * 6 - 15) + 10));
            }
            case "th.inverseLerp" -> {
                double a = x(plan, nodeInst, "a", out), lo = x(plan, nodeInst, "lo", out), hi = x(plan, nodeInst, "hi", out);
                values.put("out", hi == lo ? 0 : (a - lo) / (hi - lo));
            }
            case "th.remap" -> {
                double a = x(plan, nodeInst, "a", out), inLo = x(plan, nodeInst, "inLo", out), inHi = x(plan, nodeInst, "inHi", out);
                double outLo = x(plan, nodeInst, "outLo", out), outHi = x(plan, nodeInst, "outHi", out);
                values.put("out", inHi == inLo ? outLo : outLo + (a - inLo) / (inHi - inLo) * (outHi - outLo));
            }

            case "th.gt" -> values.put("out", x(plan, nodeInst, "a", out) > x(plan, nodeInst, "b", out));
            case "th.lt" -> values.put("out", x(plan, nodeInst, "a", out) < x(plan, nodeInst, "b", out));
            case "th.ge" -> values.put("out", x(plan, nodeInst, "a", out) >= x(plan, nodeInst, "b", out));
            case "th.le" -> values.put("out", x(plan, nodeInst, "a", out) <= x(plan, nodeInst, "b", out));
            case "th.eq" -> values.put("out", Math.abs(x(plan, nodeInst, "a", out) - x(plan, nodeInst, "b", out)) < 1e-9);
            case "th.and" -> values.put("out", bool(plan, nodeInst, "a", out) && bool(plan, nodeInst, "b", out));
            case "th.or" -> values.put("out", bool(plan, nodeInst, "a", out) || bool(plan, nodeInst, "b", out));
            case "th.not" -> values.put("out", !bool(plan, nodeInst, "a", out));
            case "th.select" -> values.put("out", x(plan, nodeInst, bool(plan, nodeInst, "cond", out) ? "a" : "b", out));

            case "th.rgb" -> values.put("out", new Color(channel(x(plan, nodeInst, "r", out)), channel(x(plan, nodeInst, "g", out)),
                    channel(x(plan, nodeInst, "b", out)), alphaByte(x(plan, nodeInst, "a", out))));
            case "th.hsv" -> {
                double h = x(plan, nodeInst, "h", out);
                values.put("out", Color.getHSBColor((float) (h - Math.floor(h)), (float) clamp01(x(plan, nodeInst, "s", out)),
                        (float) clamp01(x(plan, nodeInst, "v", out))));
            }
            case "th.mix" -> {
                Color a = color(in(plan, nodeInst, "a", out), Color.BLACK), b = color(in(plan, nodeInst, "b", out), Color.WHITE);
                double k = clamp01(x(plan, nodeInst, "t", out));
                values.put("out", new Color(mixChannel(a.getRed(), b.getRed(), k), mixChannel(a.getGreen(), b.getGreen(), k),
                        mixChannel(a.getBlue(), b.getBlue(), k), mixChannel(a.getAlpha(), b.getAlpha(), k)));
            }
            case "th.alpha" -> {
                Color c = color(in(plan, nodeInst, "color", out), Color.WHITE);
                values.put("out", new Color(c.getRed(), c.getGreen(), c.getBlue(), alphaByte(x(plan, nodeInst, "a", out))));
            }
            case "th.bright" -> {
                Color c = color(in(plan, nodeInst, "color", out), Color.WHITE);
                double k = Math.max(0, x(plan, nodeInst, "k", out));
                values.put("out", new Color(channel(c.getRed() * k), channel(c.getGreen() * k), channel(c.getBlue() * k), c.getAlpha()));
            }

            case "th.setPosition" -> {
                overrides.put("x", x(plan, nodeInst, "x", out));
                overrides.put("y", x(plan, nodeInst, "y", out));
            }
            case "th.setRotation" -> {
                overrides.put("rotX", x(plan, nodeInst, "rotX", out));
                overrides.put("rotY", x(plan, nodeInst, "rotY", out));
                overrides.put("rot", x(plan, nodeInst, "rot", out));
            }
            case "th.setScale" -> {
                overrides.put("scale", x(plan, nodeInst, "scale", out));
                overrides.put("scaleY", x(plan, nodeInst, "scaleY", out));
            }
            case "th.setSize" -> {
                overrides.put("w", x(plan, nodeInst, "w", out));
                overrides.put("h", x(plan, nodeInst, "h", out));
            }
            case "th.setGradient" -> {
                overrides.put("gradY0", x(plan, nodeInst, "gradY0", out));
                overrides.put("gradY1", x(plan, nodeInst, "gradY1", out));
            }
            case "th.setColor" -> {
                Color c = color(in(plan, nodeInst, "color", out), Color.WHITE);
                overrides.put("color", new Color(c.getRed(), c.getGreen(), c.getBlue()));
                overrides.put("alpha", c.getAlpha() / 255.0);
            }
            case "th.set.visible" -> overrides.put("visible", bool(plan, nodeInst, "v", out));
            case "th.set.pointX" -> overrides.put("p" + (int) num(lit(plan, nodeInst, "index"), 0) + "x", x(plan, nodeInst, "v", out));
            case "th.set.pointY" -> overrides.put("p" + (int) num(lit(plan, nodeInst, "index"), 0) + "y", x(plan, nodeInst, "v", out));
            case "th.set.scale" -> overrides.put("scale", x(plan, nodeInst, "v", out));
            case "th.set.rot" -> overrides.put("rot", x(plan, nodeInst, "v", out));
            case "th.set.layer" -> overrides.put("layer", ThemeDoc.clampLayer((int) Math.round(x(plan, nodeInst, "v", out))));
            default -> setProperty(plan, nodeInst, t, out, values, overrides);
        }
    }
    private static void setProperty(Plan plan, NodeInst nodeInst, double t, Object[] out, Slot values, Map<String, Object> overrides) {
        String key = ThemeNodes.settableKey(nodeInst.kind);
        if (key == null && nodeInst.kind.equals("th.set.points")) {
            for (String name : new String[]{"p0x", "p0y", "p1x", "p1y", "p2x", "p2y", "p3x", "p3y"})
                overrides.put(name, x(plan, nodeInst, name, out));
            return;
        }
        if (key == null && pureNode(plan, nodeInst, t, out, values)) return;
        if (key == null) {
            if (nodeInst.kind.equals("th.beat")) values.put("out", t * Math.max(0, num(lit(plan, nodeInst, "bpm"), 120)) / 60.0 % 1.0);
            else if (nodeInst.kind.equals("th.step")) values.put("out", x(plan, nodeInst, "a", out) >= x(plan, nodeInst, "edge", out) ? 1.0 : 0.0);
            else if (nodeInst.kind.equals("th.pulse")) {
                double a = x(plan, nodeInst, "a", out);
                values.put("out", a - Math.floor(a) < clamp01(x(plan, nodeInst, "duty", out)) ? 1.0 : 0.0);
            }
            else if (nodeInst.kind.equals("th.noise")) values.put("out", noise(x(plan, nodeInst, "a", out), (long) num(lit(plan, nodeInst, "seed"), 1)));
            else if (nodeInst.kind.equals("th.hypot")) values.put("out", Math.hypot(x(plan, nodeInst, "a", out), x(plan, nodeInst, "b", out)));
            else if (nodeInst.kind.equals("th.xor")) values.put("out", bool(plan, nodeInst, "a", out) ^ bool(plan, nodeInst, "b", out));
            else if (nodeInst.kind.equals("th.gray")) {
                Color c = color(in(plan, nodeInst, "color", out), Color.WHITE);
                values.put("out", (0.2126 * c.getRed() + 0.7152 * c.getGreen() + 0.0722 * c.getBlue()) / 255.0);
            } else if (nodeInst.kind.equals("th.invert")) {
                Color c = color(in(plan, nodeInst, "color", out), Color.WHITE);
                values.put("out", new Color(255 - c.getRed(), 255 - c.getGreen(), 255 - c.getBlue(), c.getAlpha()));
            }
            return;
        }
        if (ThemeNodes.isColorKey(key)) overrides.put(key, color(in(plan, nodeInst, "v", out), Color.WHITE));
        else if (key.equals("fill")) overrides.put(key, bool(plan, nodeInst, "v", out) ? "linear" : "solid");
        else if (key.equals("layer")) overrides.put(key, ThemeDoc.clampLayer((int) Math.round(x(plan, nodeInst, "v", out))));
        else overrides.put(key, x(plan, nodeInst, "v", out));
    }
    private static boolean pureNode(Plan plan, NodeInst nodeInst, double t, Object[] out, Slot values) {
        switch (nodeInst.kind) {
            case "th.mouseSpeed" -> values.put("out", ThemeInput.speed());
            case "th.neg" -> values.put("out", -x(plan, nodeInst, "a", out));
            case "th.tan" -> values.put("out", Math.tan(x(plan, nodeInst, "a", out)));
            case "th.distance" -> values.put("out", Math.hypot(x(plan, nodeInst, "x2", out) - x(plan, nodeInst, "x1", out), x(plan, nodeInst, "y2", out) - x(plan, nodeInst, "y1", out)));
            case "th.wave" -> {
                double v = x(plan, nodeInst, "x", out), f = v - Math.floor(v);
                String shape = text(plan, nodeInst, "shape", "sine");
                values.put("out", switch (shape) {
                    case "triangle" -> 4 * Math.abs(f - 0.5) - 1;
                    case "saw" -> 2 * f - 1;
                    case "square" -> f < 0.5 ? 1 : -1;
                    default -> Math.sin(2 * Math.PI * v);
                });
            }
            case "th.equal" -> values.put("out", Math.abs(x(plan, nodeInst, "a", out) - x(plan, nodeInst, "b", out)) <= Math.max(0, x(plan, nodeInst, "tolerance", out)));
            case "th.noise2" -> values.put("out", noise2(x(plan, nodeInst, "x", out), x(plan, nodeInst, "y", out), (long) num(lit(plan, nodeInst, "seed"), 1)));
            case "th.fbm" -> {
                int octaves = (int) Math.clamp(Math.round(x(plan, nodeInst, "octaves", out)), 1, 8);
                double sum = 0, amp = 0.5, fr = 1, norm = 0, px = x(plan, nodeInst, "x", out), py = x(plan, nodeInst, "y", out);
                for (int k = 0; k < octaves; k++) {
                    sum += amp * noise2(px * fr, py * fr, 1);
                    norm += amp;
                    amp *= 0.5;
                    fr *= 2;
                }
                values.put("out", norm == 0 ? 0 : sum / norm);
            }
            case "th.checker" -> {
                long cells = (long) Math.floor(x(plan, nodeInst, "x", out) * x(plan, nodeInst, "scale", out)) + (long) Math.floor(x(plan, nodeInst, "y", out) * x(plan, nodeInst, "scale", out));
                values.put("out", Math.floorMod(cells, 2L) == 0 ? 0.0 : 1.0);
            }
            case "th.stripes" -> {
                double period = x(plan, nodeInst, "period", out);
                double q = period == 0 ? 0 : x(plan, nodeInst, "x", out) / period;
                values.put("out", q - Math.floor(q));
            }
            case "th.grid" -> {
                double cell = x(plan, nodeInst, "cell", out);
                if (cell == 0) { values.put("out", 0.0); break; }
                double fx = x(plan, nodeInst, "x", out) / cell, fy = x(plan, nodeInst, "y", out) / cell;
                double lw = x(plan, nodeInst, "line", out) / cell;
                values.put("out", fx - Math.floor(fx) < lw || fy - Math.floor(fy) < lw ? 1.0 : 0.0);
            }
            case "th.sdfCircle" -> values.put("out", Math.hypot(x(plan, nodeInst, "x", out), x(plan, nodeInst, "y", out)) - x(plan, nodeInst, "radius", out));
            case "th.sdfBox" -> values.put("out", sdfBox(x(plan, nodeInst, "x", out), x(plan, nodeInst, "y", out), x(plan, nodeInst, "halfWidth", out), x(plan, nodeInst, "halfHeight", out)));
            case "th.circleMask" -> {
                double s = Math.max(0, x(plan, nodeInst, "softness", out));
                values.put("out", 1 - smooth01(-s, s, Math.hypot(x(plan, nodeInst, "x", out), x(plan, nodeInst, "y", out)) - x(plan, nodeInst, "radius", out)));
            }
            case "th.boxMask" -> {
                double s = Math.max(0, x(plan, nodeInst, "softness", out));
                values.put("out", 1 - smooth01(-s, s, sdfBox(x(plan, nodeInst, "x", out), x(plan, nodeInst, "y", out), x(plan, nodeInst, "halfWidth", out), x(plan, nodeInst, "halfHeight", out))));
            }
            case "th.ringMask" -> {
                double s = Math.max(0, x(plan, nodeInst, "softness", out));
                double ring = Math.abs(Math.hypot(x(plan, nodeInst, "x", out), x(plan, nodeInst, "y", out)) - x(plan, nodeInst, "radius", out)) - x(plan, nodeInst, "thickness", out) / 2;
                values.put("out", 1 - smooth01(-s, s, ring));
            }
            case "th.toNumber" -> values.put("out", bool(plan, nodeInst, "a", out) ? 1.0 : 0.0);
            case "th.toBool" -> values.put("out", x(plan, nodeInst, "a", out) > 0.5);
            case "th.grayColor" -> {
                int g = channel(clamp01(x(plan, nodeInst, "v", out)) * 255);
                values.put("out", new Color(g, g, g));
            }
            case "th.channel" -> {
                Color c = color(in(plan, nodeInst, "color", out), Color.WHITE);
                values.put("out", switch (text(plan, nodeInst, "channel", "r")) {
                    case "g" -> c.getGreen() / 255.0;
                    case "b" -> c.getBlue() / 255.0;
                    case "a" -> c.getAlpha() / 255.0;
                    default -> c.getRed() / 255.0;
                });
            }
            case "th.easeIn" -> { double v = clamp01(x(plan, nodeInst, "x", out)); values.put("out", v * v * v); }
            case "th.easeOut" -> { double v = clamp01(x(plan, nodeInst, "x", out)); values.put("out", 1 - Math.pow(1 - v, 3)); }
            case "th.easeInOut" -> {
                double v = clamp01(x(plan, nodeInst, "x", out));
                values.put("out", v < 0.5 ? 4 * v * v * v : 1 - Math.pow(-2 * v + 2, 3) / 2);
            }
            case "th.pingPong" -> {
                double h = x(plan, nodeInst, "x", out) / 2;
                values.put("out", 1 - Math.abs(2 * (h - Math.floor(h)) - 1));
            }
            case "th.quantize" -> {
                double steps = x(plan, nodeInst, "steps", out);
                values.put("out", steps <= 0 ? 0 : Math.floor(clamp01(x(plan, nodeInst, "x", out)) * steps) / steps);
            }
            case "th.keys4" -> {
                double v = clamp01(x(plan, nodeInst, "x", out)) * 3;
                int seg = Math.min(2, (int) Math.floor(v));
                double f = v - seg;
                double a = x(plan, nodeInst, "v" + seg, out), b = x(plan, nodeInst, "v" + (seg + 1), out);
                values.put("out", a + (b - a) * f);
            }
            case "th.select4" -> values.put("out", x(plan, nodeInst, "v" + Math.floorMod((int) Math.floor(x(plan, nodeInst, "index", out)), 4), out));
            case "th.loop" -> {
                double period = x(plan, nodeInst, "period", out);
                double q = period == 0 ? 0 : x(plan, nodeInst, "time", out) / period;
                values.put("out", q - Math.floor(q));
            }
            case "th.frameIndex" -> {
                long frames = Math.max(1, Math.round(x(plan, nodeInst, "frames", out)));
                values.put("out", (double) Math.floorMod((long) Math.floor(x(plan, nodeInst, "time", out) * x(plan, nodeInst, "fps", out)), frames));
            }
            case "th.frameRandom" -> {
                double fps = x(plan, nodeInst, "fps", out);
                values.put("out", hash(Math.floor(x(plan, nodeInst, "time", out) * fps) + 0.5, 0));
            }
            case "th.hashStep" -> {
                double period = x(plan, nodeInst, "period", out);
                values.put("out", period <= 0 ? 0 : hash(Math.floor(x(plan, nodeInst, "time", out) / period) + 0.5, 0));
            }
            case "th.selectColor" -> {
                boolean on = bool(plan, nodeInst, "cond", out);
                values.put("out", color(in(plan, nodeInst, on ? "a" : "b", out), Color.WHITE));
            }
            case "th.scaleColor" -> {
                Color c = color(in(plan, nodeInst, "color", out), Color.WHITE);
                double k = Math.max(0, x(plan, nodeInst, "factor", out));
                values.put("out", new Color(channel(c.getRed() * k), channel(c.getGreen() * k), channel(c.getBlue() * k), c.getAlpha()));
            }
            case "th.addColor" -> {
                Color a = color(in(plan, nodeInst, "a", out), Color.BLACK), b = color(in(plan, nodeInst, "b", out), Color.BLACK);
                values.put("out", new Color(channel(a.getRed() + b.getRed()), channel(a.getGreen() + b.getGreen()), channel(a.getBlue() + b.getBlue()), a.getAlpha()));
            }
            case "th.multiplyColor" -> {
                Color a = color(in(plan, nodeInst, "a", out), Color.WHITE), b = color(in(plan, nodeInst, "b", out), Color.WHITE);
                values.put("out", new Color(channel(a.getRed() * b.getRed() / 255.0), channel(a.getGreen() * b.getGreen() / 255.0),
                        channel(a.getBlue() * b.getBlue() / 255.0), a.getAlpha()));
            }
            default -> { return false; }
        }
        return true;
    }
    private static final class Slot {
        Object value;

        void put(String port, Object v) { value = v; }
    }
    //endregion

    //region Inputs and Parameters
    private static Object in(Plan plan, NodeInst nodeInst, String port, Object[] out) {
        Map<String, Link> into = plan.incoming[nodeInst.id];
        Link link = into == null ? null : into.get(port);
        if (link != null) return "out".equals(link.fromPort()) && link.from() < out.length ? out[link.from()] : null;
        return lit(plan, nodeInst, port);
    }
    private static double x(Plan plan, NodeInst nodeInst, String port, Object[] out) {
        return num(in(plan, nodeInst, port, out), 0);
    }
    private static boolean bool(Plan plan, NodeInst nodeInst, String port, Object[] out) {
        return in(plan, nodeInst, port, out) instanceof Boolean b && b;
    }
    public static double num(Object v, double fallback) {
        if (v instanceof Boolean b) return b ? 1 : 0;
        return v instanceof Number n ? n.doubleValue() : fallback;
    }
    private static Object lit(Plan plan, NodeInst nodeInst, String key) {
        Map<String, Object> m = plan.literals[nodeInst.id];
        return m == null ? null : m.get(key);
    }
    private static Map<String, Object> literalsOf(NodeInst nodeInst) {
        Map<String, Object> m = new HashMap<>();
        NodeKind nodeKind = Nodes.find(nodeInst.kind);
        if (nodeKind != null) for (Param prm : nodeKind.params()) m.put(prm.key(), parse(nodeInst.params.getOrDefault(prm.key(), prm.def())));
        for (Map.Entry<String, String> e : nodeInst.params.entrySet()) m.putIfAbsent(e.getKey(), parse(e.getValue()));
        return m;
    }
    private static Object parse(String raw) {
        if (raw == null) return null;
        String s = raw.strip();
        if (s.equals("true") || s.equals("false")) return Boolean.parseBoolean(s);
        if (s.startsWith("#") && (Param.normalizeHex(s) != null || s.matches("#[0-9A-Fa-f]{8}"))) return parseColor(s, Color.WHITE);
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException exception) {
            return s;
        }
    }
    private static String text(Plan plan, NodeInst nodeInst, String key, String fallback) {
        Object v = lit(plan, nodeInst, key);
        return v instanceof String s && !s.isEmpty() ? s : fallback;
    }
    private static double[] mem(Plan plan, NodeInst nodeInst, int size) {
        return plan.memory.computeIfAbsent(nodeInst.id, k -> {
            double[] m = new double[size];
            Arrays.fill(m, Double.NaN);
            return m;
        });
    }
    //endregion

    //region Signal Functions
    static double sdfBox(double x, double y, double hw, double hh) {
        double dx = Math.abs(x) - hw, dy = Math.abs(y) - hh;
        return Math.hypot(Math.max(dx, 0), Math.max(dy, 0)) + Math.min(Math.max(dx, dy), 0);
    }
    static double smooth01(double e0, double e1, double v) {
        if (e1 == e0) return v < e0 ? 0 : 1;
        double t = clamp01((v - e0) / (e1 - e0));
        return t * t * (3 - 2 * t);
    }
    static double noise2(double x, double y, long seed) {
        double ix = Math.floor(x), iy = Math.floor(y), fx = x - ix, fy = y - iy;
        double ux = fx * fx * (3 - 2 * fx), uy = fy * fy * (3 - 2 * fy);
        double a = hash(ix, iy * 131 + seed), b = hash(ix + 1, iy * 131 + seed);
        double c = hash(ix, (iy + 1) * 131 + seed), d = hash(ix + 1, (iy + 1) * 131 + seed);
        double top = a + (b - a) * ux, bottom = c + (d - c) * ux;
        return top + (bottom - top) * uy;
    }
    static double hash(double i, double j) {
        double s = Math.sin(i * 127.1 + j * 311.7) * 43758.5453123;
        return s - Math.floor(s);
    }
    static double noise(double a, long seed) {
        double i = Math.floor(a), f = a - i, u = f * f * (3 - 2 * f);
        return hash(i, seed) + (hash(i + 1, seed) - hash(i, seed)) * u;
    }
    private static double hash(double i, long seed) {
        long h = (long) i * 0x9E3779B97F4A7C15L + seed * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 11) * 0x1.0p-53;
    }
    private static double clamp01(double v) { return Math.max(0, Math.min(1, v)); }
    //endregion

    //region Tables
    public static double table(String values, double period, double t) {
        double[] v = TABLES.computeIfAbsent(values, ThemeGraph::parseTable);
        if (v.length == 0) return 0;
        if (v.length == 1 || period <= 0) return v[0];
        double u = (((t % period) + period) % period) / period * v.length;
        int i = (int) Math.floor(u);
        double f = u - i;
        return v[i % v.length] * (1 - f) + v[(i + 1) % v.length] * f;
    }
    public static double[] parseTable(String s) {
        String[] parts = s.trim().split("[,\\s]+");
        double[] out = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try { out[i] = Double.parseDouble(parts[i]); } catch (NumberFormatException exception) { out[i] = 0; }
        }
        return out;
    }
    //endregion

    //region Colour Helpers
    public static Color parseColor(String text, Color fallback) {
        if (text != null && text.strip().matches("#?[0-9A-Fa-f]{8}")) {
            String digits = text.strip().replace("#", "");
            int rgb = Integer.parseInt(digits.substring(0, 6), 16);
            int alpha = Integer.parseInt(digits.substring(6, 8), 16);
            return new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, alpha);
        }
        String hex = Param.normalizeHex(text);
        if (hex == null) return fallback;
        return new Color(Integer.parseInt(hex.substring(1), 16));
    }
    private static Color color(Object v, Color fallback) {
        return v instanceof Color color ? color : fallback;
    }
    private static int channel(double v) { return (int) Math.max(0, Math.min(255, Math.round(v))); }
    private static int alphaByte(double a) { return (int) Math.max(0, Math.min(255, Math.round(255 * clamp01(a)))); }
    private static int mixChannel(int a, int b, double k) { return (int) Math.round(a + (b - a) * k); }
    //endregion
}