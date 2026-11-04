package Project.ThemeCreator;
import Project.EngineCreator.*;
import Project.CreatorKit.*;

import java.util.*;

public final class ThemeNodes {
    //region Variables
    //region Category Names
    public static final String CAT_SRC = "tsrc", CAT_INPUT = "tinput", CAT_ANIM = "tanim", CAT_ARITH = "tarith", CAT_TRIG = "ttrig",
            CAT_EASE = "tease", CAT_SHAPE = "tshape", CAT_PATTERN = "tpattern", CAT_LOGIC = "tlogic", CAT_COL = "tcol", CAT_SET = "tset";
    //endregion
    //region Type Shorthands
    private static final DataType F = DataType.FLOAT, B = DataType.BOOL, C = DataType.COLOR;
    //endregion
    //region Constant Lists
    private static final double BIG = 1e6;
    private static final List<String> UNARY = List.of("th.sin", "th.cos", "th.abs", "th.sign", "th.floor", "th.ceil", "th.round",
            "th.fract", "th.sqrt", "th.exp", "th.log", "th.tri", "th.ease", "th.deg", "th.rad");
    private static final List<String> BINARY = List.of("th.add", "th.sub", "th.mul", "th.div", "th.mod", "th.pow", "th.min", "th.max", "th.atan2");
    private static final List<String> COMPARE = List.of("th.gt", "th.lt", "th.ge", "th.le", "th.eq");
    //endregion
    //region Setter Tables
    private static final Map<String, Double> NUMBER_SETTERS = new LinkedHashMap<>();
    private static final List<String> COLOR_SETTERS = List.of("color", "color2", "color3");
    public static final List<String> SHARED_SETTERS = List.of("th.setPosition", "th.setRotation", "th.setScale", "th.setSize", "th.setGradient", "th.setColor");
    //endregion
    //region Install State
    private static boolean installed;
    //endregion
    //endregion

    //region Constructors
    private ThemeNodes() {}
    //endregion

    //region Setup
    static {
        String[] names = {"x", "y", "rot", "rotX", "rotY", "scale", "scaleY", "layer", "w", "h", "stroke", "radius",
                "start", "extent", "alpha", "alpha2", "alpha3", "midAt", "gradY0", "gradY1"};
        double[] starts = {0, 0, 0, 0, 0, 1, 1, 0, 100, 100, 2, 100, 0, 90, 1, 1, 1, 0.5, 0, 100};
        for (int i = 0; i < names.length; i++) NUMBER_SETTERS.put(names[i], starts[i]);
    }
    public static synchronized void install() {
        if (installed) return;
        installed = true;
        add("th.time", CAT_ANIM, List.of(), List.of(new Port("out", F)), List.of(Param.num("speed", 1, -BIG, BIG)));
        add("th.number", CAT_SRC, List.of(), List.of(new Port("out", F)), List.of(Param.num("value", 0, -BIG, BIG)));
        add("th.int", CAT_SRC, List.of(), List.of(new Port("out", F)), List.of(Param.whole("value", 1, -1000000, 1000000)));
        add("th.bool", CAT_SRC, List.of(), List.of(new Port("out", B)), List.of(Param.choice("value", "true", "true", "false")));
        add("th.random", CAT_SRC, List.of(), List.of(new Port("out", F)), List.of(Param.whole("seed", 1, 0, 999999)));
        add("th.mouseSpeed", CAT_INPUT, List.of(), List.of(new Port("out", F)), List.of());
        add("th.color", CAT_SRC, List.of(), List.of(new Port("out", C)), List.of(Param.color("color", "#FFFFFF")));

        add("th.key", CAT_INPUT, List.of(), List.of(new Port("out", B)), List.of(Param.text("key", "Space")));
        add("th.axis", CAT_INPUT, List.of(), List.of(new Port("out", F)),
                List.of(Param.text("negative", "A"), Param.text("positive", "D")));
        add("th.mouseX", CAT_INPUT, List.of(), List.of(new Port("out", F)), List.of());
        add("th.mouseY", CAT_INPUT, List.of(), List.of(new Port("out", F)), List.of());
        add("th.mouseDX", CAT_INPUT, List.of(), List.of(new Port("out", F)), List.of());
        add("th.mouseDY", CAT_INPUT, List.of(), List.of(new Port("out", F)), List.of());
        add("th.mouseDown", CAT_INPUT, List.of(), List.of(new Port("out", B)),
                List.of(Param.choice("button", "left", "left", "middle", "right")));

        for (String id : UNARY) numeric(id, catFor(id), new String[]{"a"}, new double[]{0}, F);
        for (String id : BINARY) numeric(id, catFor(id), new String[]{"a", "b"}, new double[]{0, 1}, F);
        numeric("th.lerp", CAT_EASE, new String[]{"a", "b", "t"}, new double[]{0, 1, 0.5}, F);
        numeric("th.clamp", CAT_EASE, new String[]{"a", "lo", "hi"}, new double[]{0, 0, 1}, F);
        numeric("th.smooth", CAT_EASE, new String[]{"a", "lo", "hi"}, new double[]{0, 0, 1}, F);
        numeric("th.smootherstep", CAT_EASE, new String[]{"a", "lo", "hi"}, new double[]{0, 0, 1}, F);
        numeric("th.inverseLerp", CAT_EASE, new String[]{"a", "lo", "hi"}, new double[]{0, 0, 1}, F);
        numeric("th.remap", CAT_EASE, new String[]{"a", "inLo", "inHi", "outLo", "outHi"}, new double[]{0, 0, 1, 0, 1}, F);
        add("th.table", CAT_EASE, ports(new String[]{"a"}, F), List.of(new Port("out", F)),
                List.of(Param.num("period", 1, 0.0001, BIG), Param.text("values", "0")));

        add("th.beat", CAT_ANIM, List.of(), List.of(new Port("out", F)), List.of(Param.num("bpm", 120, 0, BIG)));
        add("th.sdfCircle", CAT_SHAPE, ports(new String[]{"x", "y", "radius"}, F), List.of(new Port("out", F)),
                List.of(Param.num("x", 0, -BIG, BIG), Param.num("y", 0, -BIG, BIG), Param.num("radius", 0.2, 0, BIG)));
        add("th.sdfBox", CAT_SHAPE, ports(new String[]{"x", "y", "halfWidth", "halfHeight"}, F), List.of(new Port("out", F)),
                List.of(Param.num("x", 0, -BIG, BIG), Param.num("y", 0, -BIG, BIG), Param.num("halfWidth", 0.2, 0, BIG), Param.num("halfHeight", 0.2, 0, BIG)));
        add("th.circleMask", CAT_SHAPE, ports(new String[]{"x", "y", "radius", "softness"}, F), List.of(new Port("out", F)),
                List.of(Param.num("x", 0, -BIG, BIG), Param.num("y", 0, -BIG, BIG), Param.num("radius", 0.2, 0, BIG), Param.num("softness", 0.01, 0, BIG)));
        add("th.boxMask", CAT_SHAPE, ports(new String[]{"x", "y", "halfWidth", "halfHeight", "softness"}, F), List.of(new Port("out", F)),
                List.of(Param.num("x", 0, -BIG, BIG), Param.num("y", 0, -BIG, BIG), Param.num("halfWidth", 0.2, 0, BIG),
                        Param.num("halfHeight", 0.2, 0, BIG), Param.num("softness", 0.01, 0, BIG)));
        add("th.ringMask", CAT_SHAPE, ports(new String[]{"x", "y", "radius", "thickness", "softness"}, F), List.of(new Port("out", F)),
                List.of(Param.num("x", 0, -BIG, BIG), Param.num("y", 0, -BIG, BIG), Param.num("radius", 0.2, 0, BIG),
                        Param.num("thickness", 0.02, 0, BIG), Param.num("softness", 0.01, 0, BIG)));
        add("th.toNumber", CAT_LOGIC, List.of(new Port("a", B)), List.of(new Port("out", F)), bools("a"));
        add("th.toBool", CAT_LOGIC, ports(new String[]{"a"}, F), List.of(new Port("out", B)), List.of(Param.num("a", 0, -BIG, BIG)));
        add("th.grayColor", CAT_COL, ports(new String[]{"v"}, F), List.of(new Port("out", C)), List.of(Param.num("v", 0, -BIG, BIG)));
        add("th.channel", CAT_COL, List.of(new Port("color", C)), List.of(new Port("out", F)),
                List.of(Param.color("color", "#FFFFFF"), Param.choice("channel", "r", "r", "g", "b", "a")));
        add("th.neg", CAT_ARITH, ports(new String[]{"a"}, F), List.of(new Port("out", F)), List.of(Param.num("a", 0, -BIG, BIG)));
        add("th.tan", CAT_TRIG, ports(new String[]{"a"}, F), List.of(new Port("out", F)), List.of(Param.num("a", 0, -BIG, BIG)));
        add("th.distance", CAT_SHAPE, ports(new String[]{"x1", "y1", "x2", "y2"}, F), List.of(new Port("out", F)),
                List.of(Param.num("x1", 0, -BIG, BIG), Param.num("y1", 0, -BIG, BIG), Param.num("x2", 1, -BIG, BIG), Param.num("y2", 1, -BIG, BIG)));
        add("th.wave", CAT_TRIG, ports(new String[]{"x"}, F), List.of(new Port("out", F)),
                List.of(Param.num("x", 0, -BIG, BIG), Param.choice("shape", "sine", "sine", "triangle", "saw", "square")));
        add("th.equal", CAT_LOGIC, ports(new String[]{"a", "b", "tolerance"}, F), List.of(new Port("out", B)),
                List.of(Param.num("a", 0, -BIG, BIG), Param.num("b", 0, -BIG, BIG), Param.num("tolerance", 0.001, 0, BIG)));
        add("th.noise2", CAT_PATTERN, ports(new String[]{"x", "y"}, F), List.of(new Port("out", F)),
                List.of(Param.num("x", 0, -BIG, BIG), Param.num("y", 0, -BIG, BIG), Param.whole("seed", 1, 0, 999999)));
        add("th.fbm", CAT_PATTERN, ports(new String[]{"x", "y"}, F), List.of(new Port("out", F)),
                List.of(Param.num("x", 0, -BIG, BIG), Param.num("y", 0, -BIG, BIG), Param.whole("octaves", 4, 1, 8)));
        add("th.checker", CAT_PATTERN, ports(new String[]{"x", "y", "scale"}, F), List.of(new Port("out", F)),
                List.of(Param.num("x", 0, -BIG, BIG), Param.num("y", 0, -BIG, BIG), Param.num("scale", 4, 0, BIG)));
        add("th.stripes", CAT_PATTERN, ports(new String[]{"x", "period"}, F), List.of(new Port("out", F)),
                List.of(Param.num("x", 0, -BIG, BIG), Param.num("period", 0.2, 0, BIG)));
        add("th.grid", CAT_PATTERN, ports(new String[]{"x", "y", "cell", "line"}, F), List.of(new Port("out", F)),
                List.of(Param.num("x", 0, -BIG, BIG), Param.num("y", 0, -BIG, BIG), Param.num("cell", 0.1, 0, BIG), Param.num("line", 0.01, 0, BIG)));
        add("th.easeIn", CAT_EASE, ports(new String[]{"x"}, F), List.of(new Port("out", F)), List.of(Param.num("x", 0, -BIG, BIG)));
        add("th.easeOut", CAT_EASE, ports(new String[]{"x"}, F), List.of(new Port("out", F)), List.of(Param.num("x", 0, -BIG, BIG)));
        add("th.easeInOut", CAT_EASE, ports(new String[]{"x"}, F), List.of(new Port("out", F)), List.of(Param.num("x", 0, -BIG, BIG)));
        add("th.dt", CAT_ANIM, List.of(), List.of(new Port("out", F)), List.of());
        add("th.integrate", CAT_ANIM, ports(new String[]{"x"}, F), List.of(new Port("out", F)), List.of(Param.num("x", 0, -BIG, BIG)));
        add("th.edge", CAT_ANIM, List.of(new Port("in", B)), List.of(new Port("out", B)), bools("in"));
        add("th.toggle", CAT_ANIM, List.of(new Port("in", B)), List.of(new Port("out", B)), bools("in"));
        add("th.counter", CAT_ANIM, List.of(new Port("in", B)), List.of(new Port("out", F)), bools("in"));
        add("th.timeSinceEdge", CAT_ANIM, List.of(new Port("in", B)), List.of(new Port("out", F)), bools("in"));
        add("th.randomOnEdge", CAT_ANIM, List.of(new Port("in", B)), List.of(new Port("out", F)), bools("in"));
        add("th.keyHeld", CAT_INPUT, List.of(), List.of(new Port("out", B)), List.of(Param.text("key", "Space")));
        add("th.keyPressed", CAT_INPUT, List.of(), List.of(new Port("out", B)), List.of(Param.text("key", "Space")));
        add("th.polygonMask", CAT_SHAPE, ports(new String[]{"x", "y", "radius", "angle", "softness"}, F), List.of(new Port("out", F)),
                List.of(Param.num("x", 0, -BIG, BIG), Param.num("y", 0, -BIG, BIG), Param.num("radius", 0.2, 0, BIG),
                        Param.num("angle", 0, -BIG, BIG), Param.num("softness", 0.01, 0, BIG), Param.whole("sides", 3, 3, 64)));
        add("th.follow", CAT_ANIM, ports(new String[]{"x", "speed"}, F), List.of(new Port("out", F)),
                List.of(Param.num("x", 0, -BIG, BIG), Param.num("speed", 6, 0, BIG)));
        add("th.pingPong", CAT_ANIM, ports(new String[]{"x"}, F), List.of(new Port("out", F)), List.of(Param.num("x", 0, -BIG, BIG)));
        add("th.quantize", CAT_EASE, ports(new String[]{"x", "steps"}, F), List.of(new Port("out", F)),
                List.of(Param.num("x", 0, -BIG, BIG), Param.whole("steps", 6, 1, 1000)));
        add("th.keys4", CAT_ANIM, ports(new String[]{"x", "v0", "v1", "v2", "v3"}, F), List.of(new Port("out", F)),
                List.of(Param.num("x", 0, -BIG, BIG), Param.num("v0", 0, -BIG, BIG), Param.num("v1", 1, -BIG, BIG),
                        Param.num("v2", 0, -BIG, BIG), Param.num("v3", 1, -BIG, BIG)));
        add("th.select4", CAT_LOGIC, ports(new String[]{"index", "v0", "v1", "v2", "v3"}, F), List.of(new Port("out", F)),
                List.of(Param.num("index", 0, -BIG, BIG), Param.num("v0", 0, -BIG, BIG), Param.num("v1", 1, -BIG, BIG),
                        Param.num("v2", 0, -BIG, BIG), Param.num("v3", 1, -BIG, BIG)));
        add("th.loop", CAT_ANIM, ports(new String[]{"time", "period"}, F), List.of(new Port("out", F)),
                List.of(Param.num("time", 0, -BIG, BIG), Param.num("period", 1, 0, BIG)));
        add("th.frameIndex", CAT_ANIM, ports(new String[]{"time", "fps", "frames"}, F), List.of(new Port("out", F)),
                List.of(Param.num("time", 0, -BIG, BIG), Param.num("fps", 12, 0, BIG), Param.whole("frames", 8, 1, 100000)));
        add("th.frameRandom", CAT_ANIM, ports(new String[]{"time", "fps"}, F), List.of(new Port("out", F)),
                List.of(Param.num("time", 0, -BIG, BIG), Param.num("fps", 30, 0, BIG)));
        add("th.hashStep", CAT_ANIM, ports(new String[]{"time", "period"}, F), List.of(new Port("out", F)),
                List.of(Param.num("time", 0, -BIG, BIG), Param.num("period", 0.5, 0, BIG)));
        add("th.step", CAT_EASE, ports(new String[]{"a", "edge"}, F), List.of(new Port("out", F)),
                List.of(Param.num("a", 0, -BIG, BIG), Param.num("edge", 0.5, -BIG, BIG)));
        add("th.pulse", CAT_EASE, ports(new String[]{"a"}, F), List.of(new Port("out", F)),
                List.of(Param.num("a", 0, -BIG, BIG), Param.num("duty", 0.5, 0, 1)));
        add("th.noise", CAT_PATTERN, ports(new String[]{"a"}, F), List.of(new Port("out", F)),
                List.of(Param.num("a", 0, -BIG, BIG), Param.whole("seed", 1, 0, 999999)));
        numeric("th.hypot", CAT_SHAPE, new String[]{"a", "b"}, new double[]{0, 0}, F);
        for (String id : COMPARE) numeric(id, CAT_LOGIC, new String[]{"a", "b"}, new double[]{0, 0}, B);
        add("th.and", CAT_LOGIC, ports(new String[]{"a", "b"}, B), List.of(new Port("out", B)), bools("a", "b"));
        add("th.or", CAT_LOGIC, ports(new String[]{"a", "b"}, B), List.of(new Port("out", B)), bools("a", "b"));
        add("th.xor", CAT_LOGIC, ports(new String[]{"a", "b"}, B), List.of(new Port("out", B)), bools("a", "b"));
        add("th.selectColor", CAT_LOGIC, List.of(new Port("cond", B), new Port("a", C), new Port("b", C)), List.of(new Port("out", C)),
                List.of(Param.choice("cond", "false", "false", "true"), Param.color("a", "#000000"), Param.color("b", "#FFFFFF")));
        add("th.not", CAT_LOGIC, ports(new String[]{"a"}, B), List.of(new Port("out", B)), bools("a"));
        add("th.select", CAT_LOGIC, List.of(new Port("cond", B), new Port("a", F), new Port("b", F)), List.of(new Port("out", F)),
                List.of(Param.choice("cond", "false", "false", "true"), Param.num("a", 0, -BIG, BIG), Param.num("b", 1, -BIG, BIG)));

        add("th.rgb", CAT_COL, ports(new String[]{"r", "g", "b", "a"}, F), List.of(new Port("out", C)),
                List.of(Param.num("r", 255, 0, 255), Param.num("g", 255, 0, 255), Param.num("b", 255, 0, 255), Param.num("a", 1, 0, 1)));
        add("th.hsv", CAT_COL, ports(new String[]{"h", "s", "v"}, F), List.of(new Port("out", C)),
                List.of(Param.num("h", 0, -BIG, BIG), Param.num("s", 1, 0, 1), Param.num("v", 1, 0, 1)));
        add("th.mix", CAT_COL, List.of(new Port("a", C), new Port("b", C), new Port("t", F)), List.of(new Port("out", C)),
                List.of(Param.color("a", "#000000"), Param.color("b", "#FFFFFF"), Param.num("t", 0.5, 0, 1)));
        add("th.alpha", CAT_COL, List.of(new Port("color", C), new Port("a", F)), List.of(new Port("out", C)),
                List.of(Param.color("color", "#FFFFFF"), Param.num("a", 1, 0, 1)));
        add("th.bright", CAT_COL, List.of(new Port("color", C), new Port("k", F)), List.of(new Port("out", C)),
                List.of(Param.color("color", "#FFFFFF"), Param.num("k", 1, 0, BIG)));
        add("th.gray", CAT_COL, List.of(new Port("color", C)), List.of(new Port("out", F)), List.of(Param.color("color", "#FFFFFF")));
        add("th.scaleColor", CAT_COL, List.of(new Port("color", C), new Port("factor", F)), List.of(new Port("out", C)),
                List.of(Param.color("color", "#FFFFFF"), Param.num("factor", 1, 0, BIG)));
        add("th.addColor", CAT_COL, List.of(new Port("a", C), new Port("b", C)), List.of(new Port("out", C)),
                List.of(Param.color("a", "#000000"), Param.color("b", "#000000")));
        add("th.multiplyColor", CAT_COL, List.of(new Port("a", C), new Port("b", C)), List.of(new Port("out", C)),
                List.of(Param.color("a", "#FFFFFF"), Param.color("b", "#FFFFFF")));
        add("th.invert", CAT_COL, List.of(new Port("color", C)), List.of(new Port("out", C)), List.of(Param.color("color", "#FFFFFF")));

        for (Map.Entry<String, Double> e : NUMBER_SETTERS.entrySet()) {
            String key = e.getKey();
            if (key.equals("layer")) add("th.set.layer", CAT_SET, List.of(new Port("v", F)), List.of(), List.of(Param.num("v", 0, -127, 128)));
            else add("th.set." + key, CAT_SET, List.of(new Port("v", F)), List.of(), List.of(Param.num("v", e.getValue(), -BIG, BIG)));
        }
        for (String key : COLOR_SETTERS) add("th.set." + key, CAT_SET, List.of(new Port("v", C)), List.of(), List.of(Param.color("v", "#FFFFFF")));
        add("th.set.fill", CAT_SET, List.of(new Port("v", B)), List.of(), bools("v"));
        String[] pointNames = {"p0x", "p0y", "p1x", "p1y", "p2x", "p2y", "p3x", "p3y"};
        double[] pointStarts = {0, 0, 100, 0, 100, 100, 0, 100};
        List<Port> pointIn = new ArrayList<>();
        List<Param> pointParams = new ArrayList<>();
        for (int i = 0; i < pointNames.length; i++) {
            pointIn.add(new Port(pointNames[i], F));
            pointParams.add(Param.num(pointNames[i], pointStarts[i], -BIG, BIG));
        }
        add("th.set.points", CAT_SET, pointIn, List.of(), pointParams);
        add("th.setPosition", CAT_SET, List.of(new Port("x", F), new Port("y", F)), List.of(),
                List.of(Param.num("x", 0, -BIG, BIG), Param.num("y", 0, -BIG, BIG)));
        add("th.setRotation", CAT_SET, List.of(new Port("rotX", F), new Port("rotY", F), new Port("rot", F)), List.of(),
                List.of(Param.num("rotX", 0, -BIG, BIG), Param.num("rotY", 0, -BIG, BIG), Param.num("rot", 0, -BIG, BIG)));
        add("th.setScale", CAT_SET, List.of(new Port("scale", F), new Port("scaleY", F)), List.of(),
                List.of(Param.num("scale", 1, -BIG, BIG), Param.num("scaleY", 1, -BIG, BIG)));
        add("th.setSize", CAT_SET, List.of(new Port("w", F), new Port("h", F)), List.of(),
                List.of(Param.num("w", 100, -BIG, BIG), Param.num("h", 100, -BIG, BIG)));
        add("th.setGradient", CAT_SET, List.of(new Port("gradY0", F), new Port("gradY1", F)), List.of(),
                List.of(Param.num("gradY0", 0, -BIG, BIG), Param.num("gradY1", 100, -BIG, BIG)));
        add("th.setColor", CAT_SET, List.of(new Port("color", C)), List.of(), List.of(Param.color("color", "#FFFFFF")));
        add("th.set.visible", CAT_SET, List.of(new Port("v", B)), List.of(), bools("v"));
        add("th.set.pointX", CAT_SET, List.of(new Port("v", F)), List.of(), List.of(Param.whole("index", 0, 0, 100000), Param.num("v", 0, -BIG, BIG)));
        add("th.set.pointY", CAT_SET, List.of(new Port("v", F)), List.of(), List.of(Param.whole("index", 0, 0, 100000), Param.num("v", 0, -BIG, BIG)));
    }
    //endregion

    //region Setter Lookup
    public static List<String> ids() {
        List<String> out = new ArrayList<>();
        out.addAll(List.of("th.time", "th.beat", "th.number", "th.int", "th.bool", "th.random", "th.color"));
        out.addAll(List.of("th.axis", "th.mouseX", "th.mouseY", "th.mouseDX", "th.mouseDY", "th.mouseDown", "th.mouseSpeed"));
        out.addAll(UNARY);
        out.add("th.neg");
        out.add("th.tan");
        out.addAll(List.of("th.step", "th.pulse", "th.noise", "th.noise2", "th.fbm", "th.checker", "th.stripes", "th.grid", "th.wave"));
        out.addAll(List.of("th.sdfCircle", "th.sdfBox", "th.circleMask", "th.boxMask", "th.ringMask"));
        out.addAll(List.of("th.toNumber", "th.toBool"));
        out.addAll(BINARY);
        out.addAll(List.of("th.hypot", "th.distance"));
        out.addAll(List.of("th.lerp", "th.clamp", "th.smooth", "th.smootherstep", "th.inverseLerp", "th.remap", "th.table"));
        out.addAll(List.of("th.easeIn", "th.easeOut", "th.easeInOut", "th.pingPong", "th.quantize", "th.keys4", "th.loop",
                "th.frameIndex", "th.frameRandom", "th.hashStep", "th.select4"));
        out.add("th.follow");
        out.addAll(List.of("th.dt", "th.integrate", "th.edge", "th.toggle", "th.counter", "th.timeSinceEdge", "th.randomOnEdge",
                "th.keyHeld", "th.keyPressed", "th.polygonMask"));
        out.addAll(COMPARE);
        out.add("th.equal");
        out.addAll(List.of("th.and", "th.or", "th.xor", "th.not", "th.select", "th.selectColor"));
        out.addAll(List.of("th.rgb", "th.hsv", "th.mix", "th.alpha", "th.bright", "th.gray", "th.invert",
                "th.scaleColor", "th.addColor", "th.multiplyColor", "th.grayColor", "th.channel"));
        for (String key : settableKeys()) out.add("th.set." + key);
        out.add("th.set.points");
        out.add("th.set.pointX");
        out.add("th.set.visible");
        out.add("th.set.pointY");
        out.addAll(SHARED_SETTERS);
        return out;
    }
    public static List<String> settableKeys() {
        List<String> out = new ArrayList<>(NUMBER_SETTERS.keySet());
        out.addAll(COLOR_SETTERS);
        out.add("fill");
        return out;
    }
    public static String settableKey(String kind) {
        if (kind == null || !kind.startsWith("th.set.")) return null;
        String key = kind.substring("th.set.".length());
        return NUMBER_SETTERS.containsKey(key) || COLOR_SETTERS.contains(key) || key.equals("fill") ? key : null;
    }
    public static List<String> sharedKeys(String kind) {
        return switch (kind == null ? "" : kind) {
            case "th.setPosition" -> List.of("x", "y");
            case "th.setRotation" -> List.of("rotX", "rotY", "rot");
            case "th.setScale" -> List.of("scale", "scaleY");
            case "th.setSize" -> List.of("w", "h");
            case "th.setGradient" -> List.of("gradY0", "gradY1");
            case "th.setColor" -> List.of("color", "alpha");
            default -> List.of();
        };
    }
    public static boolean isColorKey(String key) { return COLOR_SETTERS.contains(key); }
    public static List<String> categories() {
        return List.of(CAT_SRC, CAT_INPUT, CAT_ANIM, CAT_ARITH, CAT_TRIG, CAT_EASE, CAT_SHAPE, CAT_PATTERN, CAT_LOGIC, CAT_COL, CAT_SET);
    }
    private static String catFor(String id) {
        return switch (id) {
            case "th.sin", "th.cos", "th.atan2", "th.tri" -> CAT_TRIG;
            case "th.ease" -> CAT_EASE;
            default -> CAT_ARITH;
        };
    }
    //endregion

    //region Node Builders
    private static void numeric(String id, String cat, String[] names, double[] defaults, DataType out) {
        List<Param> params = new ArrayList<>();
        for (int i = 0; i < names.length; i++) params.add(Param.num(names[i], defaults[i], -BIG, BIG));
        add(id, cat, ports(names, F), List.of(new Port("out", out)), params);
    }
    private static List<Param> bools(String... names) {
        List<Param> params = new ArrayList<>();
        for (String name : names) params.add(Param.choice(name, "false", "false", "true"));
        return params;
    }
    private static List<Port> ports(String[] names, DataType dataType) {
        List<Port> out = new ArrayList<>();
        for (String n : names) out.add(new Port(n, dataType));
        return out;
    }
    private static void add(String id, String cat, List<Port> in, List<Port> out, List<Param> params) {
        Nodes.registerTheme(new NodeKind(id, cat, in, out, params, emit -> { }));
    }
    //endregion
}