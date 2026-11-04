package Project.CreatorKit;
import Project.EngineCreator.*;

import java.util.*;

public final class Nodes {
    //region Variables
    //region Category Names
    public static final String CAT_IN = "in", CAT_CONST = "const", CAT_MATH = "math", CAT_LOGIC = "logic", CAT_CONV = "conv", CAT_COLOR = "color",
            CAT_MAP = "map", CAT_TEX = "tex", CAT_RAY = "ray", CAT_GEO = "geo", CAT_FX = "fx", CAT_OUT = "out";
    //endregion
    //region Type Shorthands
    private static final DataType F = DataType.FLOAT, I = DataType.INT, B = DataType.BOOL, C = DataType.COLOR, A = DataType.ANY;
    //endregion
    //region Registry State
    public static final List<NodeKind> ALL = new ArrayList<>();
    private static final Map<String, NodeKind> BY_ID = new HashMap<>();
    public static final String THEME_PREFIX = "th.";
    //endregion
    //endregion

    //region Constructors
    private Nodes() {}
    //endregion

    //region Registry
    //region Built-in Nodes
    static {
        // ---- inputs ----
        pure("pixelX", CAT_IN, List.of(), I, emit -> "px");
        pure("pixelY", CAT_IN, List.of(), I, emit -> "py");
        pure("screenX", CAT_IN, List.of(), F, emit -> "(2.0f * ((" + emit.ft() + ")px + 0.5f) / (" + emit.ft() + ")pi[0] - 1.0f)");
        pure("screenY", CAT_IN, List.of(), F, emit -> "(1.0f - 2.0f * ((" + emit.ft() + ")py + 0.5f) / (" + emit.ft() + ")pi[1])");
        pure("width", CAT_IN, List.of(), I, emit -> "pi[0]");
        pure("height", CAT_IN, List.of(), I, emit -> "pi[1]");
        pure("quality", CAT_IN, List.of(), I, emit -> "pi[2]");
        pure("cameraAngle", CAT_IN, List.of(), F, emit -> "pf[0]");
        pure("cameraHeight", CAT_IN, List.of(), F, emit -> "pf[1]");
        pure("cameraPitch", CAT_IN, List.of(), F, emit -> "pf[2]");
        pure("playerX", CAT_IN, List.of(), F, emit -> "pf[3]");
        pure("playerY", CAT_IN, List.of(), F, emit -> "pf[4]");
        pure("time", CAT_IN, List.of(), F, emit -> "pf[6]");
        custom("cameraPose", CAT_IN, List.of(), List.of(p("angle", F), p("height", F), p("pitch", F)), emit -> {
            emit.decl(0, "pf[0]");
            emit.decl(1, "pf[1]");
            emit.decl(2, "pf[2]");
        });
        custom("playerPosition", CAT_IN, List.of(), List.of(p("x", F), p("y", F)), emit -> {
            emit.decl(0, "pf[3]");
            emit.decl(1, "pf[4]");
        });

        // ---- constants ----
        pure("constFloat", CAT_CONST, List.of(), F, emit -> emit.flit(emit.fparam("value")), Param.num("value", 1, -1e6, 1e6));
        pure("constInt", CAT_CONST, List.of(), I, emit -> emit.ilit(emit.iparam("value")), Param.whole("value", 1, -1000000, 1000000));
        pure("constColor", CAT_CONST, List.of(), C,
                emit -> "((int)0xFF" + Objects.requireNonNull(Param.normalizeHex(emit.param("color"))).substring(1) + ")",
                Param.color("color", "#FFFFFF"));

        // ---- math ----
        op2("add", CAT_MATH, F, F, "+");
        op2("sub", CAT_MATH, F, F, "-");
        op2("mul", CAT_MATH, F, F, "*");
        pure("div", CAT_MATH, List.of(p("a", F), p("b", F)), F, emit -> "rf_div(" + emit.in(0) + ", " + emit.in(1) + ")");
        pure("neg", CAT_MATH, List.of(p("a", F)), F, emit -> "(-" + emit.in(0) + ")");
        fn1("sin", CAT_MATH, "rf_sin");
        fn1("cos", CAT_MATH, "rf_cos");
        fn1("tan", CAT_MATH, "rf_tan");
        fn1("atan", CAT_MATH, "rf_atan");
        fn1("sqrt", CAT_MATH, "rf_sqrt");
        fn1("abs", CAT_MATH, "rf_abs");
        fn1("floor", CAT_MATH, "rf_floor");
        fn1("fract", CAT_MATH, "rf_fract");
        fn1("exp", CAT_MATH, "rf_exp");
        fn1("asinh", CAT_MATH, "rf_asinh");
        pure("pow", CAT_MATH, List.of(p("a", F), p("b", F)), F, emit -> "rf_pow(" + emit.in(0) + ", " + emit.in(1) + ")");
        pure("min", CAT_MATH, List.of(p("a", F), p("b", F)), F, emit -> "rf_min(" + emit.in(0) + ", " + emit.in(1) + ")");
        pure("max", CAT_MATH, List.of(p("a", F), p("b", F)), F, emit -> "rf_max(" + emit.in(0) + ", " + emit.in(1) + ")");
        pure("clamp", CAT_MATH, List.of(p("x", F), p("lo", F), p("hi", F)), F, emit -> "rf_clamp(" + emit.in(0) + ", " + emit.in(1) + ", " + emit.in(2) + ")");
        pure("mix", CAT_MATH, List.of(p("a", F), p("b", F), p("t", F)), F, emit -> "rf_mix(" + emit.in(0) + ", " + emit.in(1) + ", " + emit.in(2) + ")");

        // ---- logic ----
        op2("less", CAT_LOGIC, F, B, "<");
        op2("greater", CAT_LOGIC, F, B, ">");
        op2("and", CAT_LOGIC, B, B, "&&");
        op2("or", CAT_LOGIC, B, B, "||");
        pure("not", CAT_LOGIC, List.of(p("a", B)), B, emit -> "(!" + emit.in(0) + ")");
        pure("select", CAT_LOGIC, List.of(p("cond", B), p("a", A), p("b", A)), A,
                emit -> "(" + emit.in(0) + " ? " + emit.in(1) + " : " + emit.in(2) + ")");

        // ---- conversions ----
        pure("toFloat", CAT_CONV, List.of(p("a", I)), F, emit -> "((" + emit.ft() + ")" + emit.in(0) + ")");
        pure("toInt", CAT_CONV, List.of(p("a", F)), I, emit -> "((int)rf_floor(" + emit.in(0) + "))");

        // ---- color ----
        pure("makeColor", CAT_COLOR, List.of(p("r", F), p("g", F), p("b", F)), C, emit -> "rf_rgb(" + emit.in(0) + ", " + emit.in(1) + ", " + emit.in(2) + ")");
        custom("splitColor", CAT_COLOR, List.of(p("c", C)), List.of(p("r", F), p("g", F), p("b", F)), emit -> {
            emit.decl(0, "rf_chR(" + emit.in(0) + ")");
            emit.decl(1, "rf_chG(" + emit.in(0) + ")");
            emit.decl(2, "rf_chB(" + emit.in(0) + ")");
        });
        pure("mixColor", CAT_COLOR, List.of(p("a", C), p("b", C), p("t", F)), C, emit -> "rf_mixColor(" + emit.in(0) + ", " + emit.in(1) + ", " + emit.in(2) + ")");
        pure("brightness", CAT_COLOR, List.of(p("c", C), p("k", F)), C, emit -> "rf_brightness(" + emit.in(0) + ", " + emit.in(1) + ")");
        pure("fog", CAT_COLOR, List.of(p("c", C), p("fogColor", C), p("distance", F), p("density", F)), C, emit -> "rf_fog(" + emit.in(0) + ", " + emit.in(1) + ", " + emit.in(2) + ", " + emit.in(3) + ")");
        pure("posterize", CAT_COLOR, List.of(p("c", C), p("levels", F)), C, emit -> "rf_posterize(" + emit.in(0) + ", " + emit.in(1) + ")");

        // ---- map and textures ----
        pure("mapCell", CAT_MAP, List.of(p("x", I), p("y", I)), I, emit -> "rf_mapAt(map, pi, " + emit.in(0) + ", " + emit.in(1) + ")");
        custom("sampleTexture", CAT_TEX, List.of(p("u", F), p("v", F)), List.of(p("out", C)), Nodes::emitTexture,
                Param.choice("texture", "wall", "wall", "floor", "ceiling", "finish"));

        // ---- rays ----
        custom("rayDirection", CAT_RAY, List.of(p("screenX", F), p("cameraAngle", F)),
                List.of(p("dirX", F), p("dirY", F)), Nodes::emitRayDirection, Param.num("fov", 60, 1, 170));
        custom("rayGrid", CAT_RAY, List.of(p("originX", F), p("originY", F), p("dirX", F), p("dirY", F)),
                List.of(p("hit", B), p("distance", F), p("side", I), p("cell", I), p("hitU", F)),
                Nodes::emitRayGrid, Param.whole("maxSteps", 64, 1, 2000));
        custom("rayGridPortal", CAT_RAY, List.of(p("originX", F), p("originY", F), p("dirX", F), p("dirY", F)),
                List.of(p("hit", B), p("distance", F), p("side", I), p("cell", I), p("hitU", F)),
                Nodes::emitRayGridPortal, Param.whole("maxSteps", 128, 1, 2000));

        // ---- geometry ----
        pure("curvature", CAT_GEO, List.of(), F, emit -> "pf[5]");
        pure("hyperbolic", CAT_GEO, List.of(), B, emit -> "(pi[13] != 0)");
        pure("hypDistance", CAT_GEO, List.of(p("t", F), p("k", F)), F,
                emit -> "rf_hypd(" + emit.in(0) + ", " + emit.in(1) + ")");

        // ---- effects: noise and previous pass ----
        pure("hash", CAT_FX, List.of(p("x", F), p("y", F), p("seed", F)), F, emit -> "rf_hash(" + emit.in(0) + ", " + emit.in(1) + ", " + emit.in(2) + ")");
        pure("neighbor", CAT_FX, List.of(), C, emit -> "rf_prev(prev, pi, px + " + emit.ilit(emit.iparam("dx")) + ", py + " + emit.ilit(emit.iparam("dy")) + ")",
                Param.whole("dx", 0, -8, 8), Param.whole("dy", 0, -8, 8));

        // ---- extra math ----
        pure("mod", CAT_MATH, List.of(p("a", F), p("b", F)), F, emit -> "(" + emit.in(0) + " - " + emit.in(1) + " * rf_floor(rf_div(" + emit.in(0) + ", " + emit.in(1) + ")))");
        pure("round", CAT_MATH, List.of(p("a", F)), F, emit -> "rf_floor(" + emit.in(0) + " + 0.5f)");
        pure("ceil", CAT_MATH, List.of(p("a", F)), F, emit -> "(-rf_floor(-" + emit.in(0) + "))");
        pure("sign", CAT_MATH, List.of(p("a", F)), F, emit -> "(" + emit.in(0) + " < 0.0f ? -1.0f : (" + emit.in(0) + " > 0.0f ? 1.0f : 0.0f))");
        pure("step", CAT_MATH, List.of(p("edge", F), p("x", F)), F, emit -> "(" + emit.in(1) + " < " + emit.in(0) + " ? 0.0f : 1.0f)");
        pure("wave", CAT_MATH, List.of(p("t", F), p("freq", F)), F, emit -> "(0.5f + 0.5f * rf_sin(" + emit.in(0) + " * " + emit.in(1) + "))");
        pure("schlick", CAT_MATH, List.of(p("cosine", F)), F, emit -> "rf_pow(rf_clamp(1.0f - " + emit.in(0) + ", 0.0f, 1.0f), 5.0f)");
        custom("smoothstep", CAT_MATH, List.of(p("edge0", F), p("edge1", F), p("x", F)), List.of(p("out", F)), emit -> {
            String t = "sm" + emit.id;
            emit.line(emit.ft() + " " + t + " = rf_clamp(rf_div(" + emit.in(2) + " - " + emit.in(0) + ", " + emit.in(1) + " - " + emit.in(0) + "), 0.0f, 1.0f);");
            emit.decl(0, t + " * " + t + " * (3.0f - 2.0f * " + t + ")");
        });
        custom("smoothMin", CAT_MATH, List.of(p("a", F), p("b", F), p("k", F)), List.of(p("out", F)), emit -> {
            String h = "smh" + emit.id;
            emit.line(emit.ft() + " " + h + " = rf_clamp(0.5f + 0.5f * rf_div(" + emit.in(1) + " - " + emit.in(0) + ", " + emit.in(2) + "), 0.0f, 1.0f);");
            emit.decl(0, "rf_mix(" + emit.in(1) + ", " + emit.in(0) + ", " + h + ") - " + emit.in(2) + " * " + h + " * (1.0f - " + h + ")");
        });
        pure("dot3", CAT_MATH, List.of(p("ax", F), p("ay", F), p("az", F), p("bx", F), p("by", F), p("bz", F)), F,
                emit -> "(" + emit.in(0) + " * " + emit.in(3) + " + " + emit.in(1) + " * " + emit.in(4) + " + " + emit.in(2) + " * " + emit.in(5) + ")");
        pure("length3", CAT_MATH, List.of(p("x", F), p("y", F), p("z", F)), F,
                emit -> "rf_sqrt(" + emit.in(0) + " * " + emit.in(0) + " + " + emit.in(1) + " * " + emit.in(1) + " + " + emit.in(2) + " * " + emit.in(2) + ")");

        // ---- extra logic ----
        op2("greaterEq", CAT_LOGIC, F, B, ">=");
        op2("lessEq", CAT_LOGIC, F, B, "<=");
        op2("equalInt", CAT_LOGIC, I, B, "==");
        op2("xor", CAT_LOGIC, B, B, "!=");

        // ---- extra color ----
        pure("luma", CAT_COLOR, List.of(p("c", C)), F,
                emit -> "(0.299f * rf_chR(" + emit.in(0) + ") + 0.587f * rf_chG(" + emit.in(0) + ") + 0.114f * rf_chB(" + emit.in(0) + "))");
        pure("invertColor", CAT_COLOR, List.of(p("c", C)), C,
                emit -> "rf_rgb(1.0f - rf_chR(" + emit.in(0) + "), 1.0f - rf_chG(" + emit.in(0) + "), 1.0f - rf_chB(" + emit.in(0) + "))");
        pure("grayscale", CAT_COLOR, List.of(p("c", C)), C, emit -> {
            String c = emit.in(0);
            String l = "(0.299f * rf_chR(" + c + ") + 0.587f * rf_chG(" + c + ") + 0.114f * rf_chB(" + c + "))";
            return "rf_rgb(" + l + ", " + l + ", " + l + ")";
        });
        pure("addColor", CAT_COLOR, List.of(p("a", C), p("b", C)), C, emit -> {
            String a = emit.in(0), b = emit.in(1);
            return "rf_rgb(rf_clamp(rf_chR(" + a + ") + rf_chR(" + b + "), 0.0f, 1.0f), rf_clamp(rf_chG(" + a + ") + rf_chG(" + b
                    + "), 0.0f, 1.0f), rf_clamp(rf_chB(" + a + ") + rf_chB(" + b + "), 0.0f, 1.0f))";
        });
        pure("multiplyColor", CAT_COLOR, List.of(p("a", C), p("b", C)), C, emit -> {
            String a = emit.in(0), b = emit.in(1);
            return "rf_rgb(rf_chR(" + a + ") * rf_chR(" + b + "), rf_chG(" + a + ") * rf_chG(" + b + "), rf_chB(" + a + ") * rf_chB(" + b + "))";
        });

        // ---- extra constants ----
        pure("constBool", CAT_CONST, List.of(), B, emit -> emit.param("value").equals("true") ? "(1 == 1)" : "(1 == 0)",
                Param.choice("value", "true", "true", "false"));

        // ---- extra geometry, noise, patterns ----
        pure("radius", CAT_FX, List.of(p("x", F), p("y", F)), F, emit -> "rf_sqrt(" + emit.in(0) + " * " + emit.in(0) + " + " + emit.in(1) + " * " + emit.in(1) + ")");
        custom("valueNoise", CAT_FX, List.of(p("x", F), p("y", F)), List.of(p("out", F)), emit -> {
            String n = "vn" + emit.id + "_";
            String seed = emit.flit(emit.fparam("seed"));
            emit.line(emit.ft() + " " + n + "ix = rf_floor(" + emit.in(0) + "), " + n + "iy = rf_floor(" + emit.in(1) + ");");
            emit.line(emit.ft() + " " + n + "ux = " + emit.in(0) + " - " + n + "ix, " + n + "uy = " + emit.in(1) + " - " + n + "iy;");
            emit.line(n + "ux = " + n + "ux * " + n + "ux * (3.0f - 2.0f * " + n + "ux);");
            emit.line(n + "uy = " + n + "uy * " + n + "uy * (3.0f - 2.0f * " + n + "uy);");
            emit.line(emit.ft() + " " + n + "a = rf_hash(" + n + "ix, " + n + "iy, " + seed + "), " + n + "b = rf_hash(" + n + "ix + 1.0f, " + n + "iy, " + seed + ");");
            emit.line(emit.ft() + " " + n + "c = rf_hash(" + n + "ix, " + n + "iy + 1.0f, " + seed + "), " + n + "d = rf_hash(" + n + "ix + 1.0f, " + n + "iy + 1.0f, " + seed + ");");
            emit.decl(0, "rf_mix(rf_mix(" + n + "a, " + n + "b, " + n + "ux), rf_mix(" + n + "c, " + n + "d, " + n + "ux), " + n + "uy)");
        }, Param.num("seed", 0, -1000, 1000));
        pure("checker", CAT_TEX, List.of(p("u", F), p("v", F)), F,
                emit -> {
                    String s = emit.flit(emit.fparam("scale"));
                    return "(rf_fract((rf_floor(" + emit.in(0) + " * " + s + ") + rf_floor(" + emit.in(1) + " * " + s + ")) * 0.5f) < 0.5f ? 0.0f : 1.0f)";
                },
                Param.num("scale", 4, 0.1, 1000));
        custom("sdfBox", CAT_GEO, List.of(p("px", F), p("py", F), p("pz", F), p("hx", F), p("hy", F), p("hz", F)),
                List.of(p("d", F)), emit -> {
                    String q = "bq" + emit.id + "_";
                    emit.line(emit.ft() + " " + q + "x = rf_abs(" + emit.in(0) + ") - " + emit.in(3) + ", " + q + "y = rf_abs(" + emit.in(1) + ") - "
                            + emit.in(4) + ", " + q + "z = rf_abs(" + emit.in(2) + ") - " + emit.in(5) + ";");
                    emit.decl(0, "rf_sqrt(rf_max(" + q + "x, 0.0f) * rf_max(" + q + "x, 0.0f) + rf_max(" + q + "y, 0.0f) * rf_max(" + q
                            + "y, 0.0f) + rf_max(" + q + "z, 0.0f) * rf_max(" + q + "z, 0.0f)) + rf_min(rf_max(" + q + "x, rf_max(" + q
                            + "y, " + q + "z)), 0.0f)");
                });

        // ---- extra tracing ----
        custom("pointOnRay", CAT_RAY, List.of(p("ox", F), p("oy", F), p("dx", F), p("dy", F), p("t", F)),
                List.of(p("x", F), p("y", F)), emit -> {
                    emit.decl(0, emit.in(0) + " + " + emit.in(2) + " * " + emit.in(4));
                    emit.decl(1, emit.in(1) + " + " + emit.in(3) + " * " + emit.in(4));
                });
        custom("wallNormal", CAT_RAY, List.of(p("dirX", F), p("dirY", F), p("side", I)),
                List.of(p("nx", F), p("ny", F)), emit -> {
                    emit.decl(0, "(" + emit.in(2) + " == 0 ? (" + emit.in(0) + " < 0.0f ? 1.0f : -1.0f) : 0.0f)");
                    emit.decl(1, "(" + emit.in(2) + " == 1 ? (" + emit.in(1) + " < 0.0f ? 1.0f : -1.0f) : 0.0f)");
                });
        custom("reflect2", CAT_RAY, List.of(p("dirX", F), p("dirY", F), p("nx", F), p("ny", F)),
                List.of(p("rx", F), p("ry", F)), emit -> {
                    String d = "rdt" + emit.id;
                    emit.line(emit.ft() + " " + d + " = " + emit.in(0) + " * " + emit.in(2) + " + " + emit.in(1) + " * " + emit.in(3) + ";");
                    emit.decl(0, emit.in(0) + " - 2.0f * " + d + " * " + emit.in(2));
                    emit.decl(1, emit.in(1) + " - 2.0f * " + d + " * " + emit.in(3));
                });
        pure("isWall", CAT_MAP, List.of(p("x", I), p("y", I)), B, emit -> "(rf_mapAt(map, pi, " + emit.in(0) + ", " + emit.in(1) + ") > 0)");

        // ---- marching over rounded walls ----
        custom("marchRounded", CAT_RAY, List.of(p("originX", F), p("originY", F), p("dirX", F), p("dirY", F)),
                List.of(p("hit", B), p("distance", F), p("side", I), p("cell", I), p("hitU", F), p("nx", F), p("ny", F)),
                Nodes::emitMarchRounded, Param.whole("maxSteps", 96, 1, 2000), Param.num("outerRadius", 0.2, 0, 0.5),
                Param.num("innerRadius", 0, 0, 0.5),
                Param.whole("q0Steps", 0, 0, 1000), Param.whole("q1Steps", 0, 0, 1000), Param.whole("q2Steps", 0, 0, 1000));

        // ---- game shading: one node for the whole light, one for the lit color ----
        custom("gameLight", CAT_FX, List.of(p("dist", F), p("ao", F), p("ndl", F), p("ndf", F), p("finish", B)),
                List.of(p("r", F), p("g", F), p("b", F), p("level", F)), Nodes::emitGameLight);
        custom("litColor", CAT_COLOR, List.of(p("c", C), p("r", F), p("g", F), p("b", F)),
                List.of(p("out", C)), Nodes::emitLitColor);

        // ---- output ----
        add(new NodeKind("output", CAT_OUT, List.of(p("color", C)), List.of(), List.of(), emit -> emit.line("return (" + emit.in(0) + " | 0xFF000000);")));
    }
    //endregion
    //region Node Builders
    @FunctionalInterface public interface Ex {
String of(Emit emit);
 }
    private static Port p(String name, DataType dataType) { return new Port(name, dataType); }
    private static void add(NodeKind nodeKind) {
        ALL.add(nodeKind);
        BY_ID.put(nodeKind.id(), nodeKind);
    }
    private static void pure(String id, String cat, List<Port> ins, DataType out, Ex ex, Param... ps) {
        add(new NodeKind(id, cat, ins, List.of(new Port("out", out)), List.of(ps), emit -> emit.decl(0, ex.of(emit))));
    }
    private static void custom(String id, String cat, List<Port> ins, List<Port> outs, Emitter emitter, Param... ps) {
        add(new NodeKind(id, cat, ins, outs, List.of(ps), emitter));
    }
    private static void op2(String id, String cat, DataType inT, DataType outT, String op) {
        pure(id, cat, List.of(p("a", inT), p("b", inT)), outT, emit -> "(" + emit.in(0) + " " + op + " " + emit.in(1) + ")");
    }
    private static void fn1(String id, String cat, String fn) { pure(id, cat, List.of(p("a", F)), F, emit -> fn + "(" + emit.in(0) + ")"); }
    //endregion
    //region Lookup
    public static NodeKind find(String id) { return BY_ID.get(id); }
    public static NodeKind get(String id) {
        NodeKind nodeKind = BY_ID.get(id);
        if (nodeKind == null) throw new IllegalArgumentException("unknown node " + id);
        return nodeKind;
    }
    public static boolean isTheme(String id) { return id.startsWith(THEME_PREFIX); }
    public static List<NodeKind> engineKinds() {
        List<NodeKind> out = new ArrayList<>();
        for (NodeKind nodeKind : ALL) if (!isTheme(nodeKind.id())) out.add(nodeKind);
        return out;
    }
    public static List<NodeKind> themeKinds() {
        List<NodeKind> out = new ArrayList<>();
        for (NodeKind nodeKind : ALL) if (isTheme(nodeKind.id())) out.add(nodeKind);
        return out;
    }
    //endregion
    //region Registration
    public static void registerTheme(NodeKind nodeKind) {
        if (!isTheme(nodeKind.id())) throw new IllegalArgumentException("not a theme node: " + nodeKind.id());
        if (BY_ID.containsKey(nodeKind.id())) return;
        ALL.add(nodeKind);
        BY_ID.put(nodeKind.id(), nodeKind);
    }
    public static void registerCustom(NodeKind nodeKind) {
        if (!CustomNodes.isCustom(nodeKind.id())) throw new IllegalArgumentException("not a custom node: " + nodeKind.id());
        unregisterCustom(nodeKind.id());
        ALL.add(nodeKind);
        BY_ID.put(nodeKind.id(), nodeKind);
    }
    public static void unregisterCustom(String id) {
        if (!CustomNodes.isCustom(id)) return;
        NodeKind old = BY_ID.remove(id);
        if (old != null) ALL.remove(old);
    }
    //endregion
    //endregion

    //region Shader Emitters
    //region Wall and Floor SDF
    private static String finishAt(String x, String y) { return "(rf_mapAt(map, pi, " + x + ", " + y + ") == 2)"; }
    private static String wallAt(String x, String y) { return "(rf_mapAt(map, pi, " + x + ", " + y + ") == 1)"; }
    private static String solidAt(String x, String y) {
        String m = "rf_mapAt(map, pi, " + x + ", " + y + ")";
        return "(" + m + " == 1 || " + m + " == 2)";
    }
    private static void emitWallSdf(Emit emit, String p, String sx, String sy, String dVar, String cVar, String xVar, String yVar, String filVar, String fcxVar, String fcyVar,
                                    double outer, double inner) {
        String F_ = emit.ft(), B_ = emit.bt();
        String O = emit.flit(outer), I = emit.flit(inner);
        String X = p + "X", Y = p + "Y";
        emit.line(F_ + " " + dVar + " = 1e30f;");
        emit.line("int " + cVar + " = 0;");
        emit.line("{");
        emit.line("\tint " + p + "cx = (int)rf_floor(" + sx + "), " + p + "cy = (int)rf_floor(" + sy + ");");
        emit.line("\tfor (int " + p + "j = -1; " + p + "j <= 1; " + p + "j++) for (int " + p + "i = -1; " + p + "i <= 1; " + p + "i++) {");
        emit.line("\t\tint " + X + " = " + p + "cx + " + p + "i, " + Y + " = " + p + "cy + " + p + "j;");
        emit.line("\t\tint " + p + "c = rf_mapAt(map, pi, " + X + ", " + Y + ");");
        emit.line("\t\tif (" + p + "c == 1 || " + p + "c == 2) {");
        emit.line("\t\t\t" + B_ + " " + p + "r = " + solidAt(X + " + 1", Y) + ", " + p + "l = " + solidAt(X + " - 1", Y) + ";");
        emit.line("\t\t\t" + B_ + " " + p + "u = " + solidAt(X, Y + " + 1") + ", " + p + "d = " + solidAt(X, Y + " - 1") + ";");

        emit.line("\t\t\t" + F_ + " " + p + "ro = " + p + "c == 1 ? " + O + " : 0.0f;");
        emit.line("\t\t\t" + F_ + " " + p + "tr = (!" + p + "r && !" + p + "u && !" + finishAt(X + " + 1", Y + " + 1") + ") ? " + p + "ro : 0.0f;");
        emit.line("\t\t\t" + F_ + " " + p + "tl = (!" + p + "l && !" + p + "u && !" + finishAt(X + " - 1", Y + " + 1") + ") ? " + p + "ro : 0.0f;");
        emit.line("\t\t\t" + F_ + " " + p + "br = (!" + p + "r && !" + p + "d && !" + finishAt(X + " + 1", Y + " - 1") + ") ? " + p + "ro : 0.0f;");
        emit.line("\t\t\t" + F_ + " " + p + "bl = (!" + p + "l && !" + p + "d && !" + finishAt(X + " - 1", Y + " - 1") + ") ? " + p + "ro : 0.0f;");
        emit.line("\t\t\t" + F_ + " " + p + "qx = " + sx + " - ((float)" + X + " + 0.5f), " + p + "qy = " + sy + " - ((float)" + Y + " + 0.5f);");
        emit.line("\t\t\t" + F_ + " " + p + "rr = " + p + "qx > 0.0f ? (" + p + "qy > 0.0f ? " + p + "tr : " + p + "br) : (" + p + "qy > 0.0f ? " + p + "tl : " + p + "bl);");
        emit.line("\t\t\t" + F_ + " " + p + "hh = 0.5f - " + p + "rr;");
        emit.line("\t\t\t" + F_ + " " + p + "ax = rf_abs(" + p + "qx) - " + p + "hh, " + p + "ay = rf_abs(" + p + "qy) - " + p + "hh;");
        emit.line("\t\t\t" + F_ + " " + p + "dd = rf_sqrt(rf_max(" + p + "ax, 0.0f) * rf_max(" + p + "ax, 0.0f) + rf_max(" + p
                + "ay, 0.0f) * rf_max(" + p + "ay, 0.0f)) + rf_min(rf_max(" + p + "ax, " + p + "ay), 0.0f) - " + p + "rr;");
        emit.line("\t\t\tif (" + p + "dd < " + dVar + ") { " + dVar + " = " + p + "dd; " + cVar + " = " + p + "c; "
                + xVar + " = " + X + "; " + yVar + " = " + Y + "; " + filVar + " = 0; }");
        emit.line("\t\t} else if (" + inner + " > 0.0) {");

        emit.line("\t\t\tfor (int " + p + "k = 0; " + p + "k < 4; " + p + "k++) {");
        emit.line("\t\t\t\tint " + p + "ax = " + p + "k & 1, " + p + "ay = " + p + "k >> 1;");
        emit.line("\t\t\t\tint " + p + "sxs = " + p + "ax * 2 - 1, " + p + "sys = " + p + "ay * 2 - 1;");
        String hN = "(" + X + " + " + p + "sxs)", vN = "(" + Y + " + " + p + "sys)";
        emit.line("\t\t\t\tif (" + wallAt(hN, Y) + " && " + wallAt(X, vN) + " && " + wallAt(hN, vN) + ") {");
        emit.line("\t\t\t\t\t" + F_ + " " + p + "qx0 = (float)" + X + " + " + p + "ax, " + p + "qy0 = (float)" + Y + " + " + p + "ay;");
        emit.line("\t\t\t\t\t" + F_ + " " + p + "cfx = " + p + "qx0 - " + p + "sxs * " + I + ", " + p + "cfy = " + p + "qy0 - " + p + "sys * " + I + ";");
        emit.line("\t\t\t\t\t" + F_ + " " + p + "scx = " + p + "qx0 - " + p + "sxs * " + I + " * 0.5f, " + p + "scy = " + p + "qy0 - " + p + "sys * " + I + " * 0.5f;");
        emit.line("\t\t\t\t\t" + F_ + " " + p + "dsq = rf_max(rf_abs(" + sx + " - " + p + "scx), rf_abs(" + sy + " - " + p + "scy)) - " + I + " * 0.5f;");
        emit.line("\t\t\t\t\t" + F_ + " " + p + "dc = " + I + " - rf_sqrt((" + sx + " - " + p + "cfx) * (" + sx + " - " + p + "cfx) + (" + sy + " - " + p + "cfy) * (" + sy + " - " + p + "cfy));");
        emit.line("\t\t\t\t\t" + F_ + " " + p + "piece = rf_max(" + p + "dsq, " + p + "dc);");
        emit.line("\t\t\t\t\tif (" + p + "piece < " + dVar + ") { " + dVar + " = " + p + "piece; " + cVar + " = 1; "
                + xVar + " = " + X + " + " + p + "sxs; " + yVar + " = " + Y + "; " + filVar + " = 1; "
                + fcxVar + " = " + p + "cfx; " + fcyVar + " = " + p + "cfy; }");
        emit.line("\t\t\t\t}");
        emit.line("\t\t\t}");
        emit.line("\t\t}");
        emit.line("\t}");
        emit.line("}");
    }
    private static void emitMarchRounded(Emit emit) {
        int steps = emit.iparam("maxSteps");
        int q0 = emit.iparam("q0Steps"), q1 = emit.iparam("q1Steps"), q2 = emit.iparam("q2Steps");
        String stepExpr = q0 > 0 ? "(pi[2] == 0 ? " + q0 + " : pi[2] == 1 ? " + q1 + " : " + q2 + ")" : String.valueOf(steps);
        double outer = emit.fparam("outerRadius"), inner = emit.fparam("innerRadius");
        String F_ = emit.ft(), B_ = emit.bt();
        String r = "mr" + emit.id + "_";
        String ox = emit.in(0), oy = emit.in(1), dx0 = emit.in(2), dy0 = emit.in(3);
        String ux = r + "ux", uy = r + "uy", len = r + "len", t = r + "t", found = r + "found";
        String px = r + "px", py = r + "py", cell = r + "cell", bx = r + "bx", by = r + "by";
        emit.line(F_ + " " + len + " = rf_sqrt(" + dx0 + " * " + dx0 + " + " + dy0 + " * " + dy0 + ");");
        emit.line(F_ + " " + ux + " = rf_div(" + dx0 + ", " + len + "), " + uy + " = rf_div(" + dy0 + ", " + len + ");");
        emit.line(F_ + " " + t + " = 0.0f, " + px + " = " + ox + ", " + py + " = " + oy + ";");
        emit.line(B_ + " " + found + " = false;"); // u can find me here
        emit.line("int " + cell + " = 0, " + bx + " = 0, " + by + " = 0, " + r + "fil = 0;");
        emit.line(F_ + " " + r + "fcx = 0.0f, " + r + "fcy = 0.0f;");
        emit.line("for (int " + r + "s = 0; " + r + "s < " + stepExpr + " && !" + found + "; " + r + "s++) {");
        emit.line("\t" + px + " = " + ox + " + " + ux + " * " + t + ";");
        emit.line("\t" + py + " = " + oy + " + " + uy + " * " + t + ";");
        emitWallSdf(emit, r + "a", px, py, r + "best", r + "bc", bx, by, r + "fil", r + "fcx", r + "fcy", outer, inner);
        emit.line("\tif (" + r + "best < 0.001f) { " + found + " = true; " + cell + " = " + r + "bc; break; }");
        emit.line("\t" + t + " += rf_min(rf_max(" + r + "best, 0.002f), 1.0f);");
        emit.line("\tif (" + t + " > 200.0f) break;");
        emit.line("}");

        String nx = r + "nx", ny = r + "ny";
        String hh = r + "hh", qx = r + "qx", qy = r + "qy", ax = r + "ax", ay = r + "ay", gl = r + "gl";
        emit.line(F_ + " " + hh + " = 0.5f;");
        emit.line(F_ + " " + qx + " = " + px + " - ((float)" + bx + " + 0.5f), " + qy + " = " + py + " - ((float)" + by + " + 0.5f);");
        emit.line(F_ + " " + ax + " = rf_abs(" + qx + ") - " + hh + ", " + ay + " = rf_abs(" + qy + ") - " + hh + ";");
        emit.line(F_ + " " + nx + " = 0.0f, " + ny + " = 0.0f;");
        emit.line("if (" + ax + " > 0.0f && " + ay + " > 0.0f) {");
        emit.line("\t" + F_ + " " + gl + " = rf_sqrt(" + ax + " * " + ax + " + " + ay + " * " + ay + ");");
        emit.line("\t" + nx + " = (" + qx + " >= 0.0f ? " + ax + " : -" + ax + ") / " + gl + ";");
        emit.line("\t" + ny + " = (" + qy + " >= 0.0f ? " + ay + " : -" + ay + ") / " + gl + ";");
        emit.line("} else if (" + ax + " > " + ay + ") {");
        emit.line("\t" + nx + " = " + qx + " >= 0.0f ? 1.0f : -1.0f;");
        emit.line("} else {");
        emit.line("\t" + ny + " = " + qy + " >= 0.0f ? 1.0f : -1.0f;");
        emit.line("}");

        emit.line("if (" + r + "fil == 1) {");
        emit.line("\t" + F_ + " " + r + "fx = " + r + "fcx - " + px + ", " + r + "fy = " + r + "fcy - " + py + ";");
        emit.line("\t" + F_ + " " + r + "fl = rf_sqrt(" + r + "fx * " + r + "fx + " + r + "fy * " + r + "fy);");
        emit.line("\tif (" + r + "fl > 1e-9f) { " + nx + " = " + r + "fx / " + r + "fl; " + ny + " = " + r + "fy / " + r + "fl; }");
        emit.line("}");
        String w = r + "w";
        emit.line(F_ + " " + w + " = rf_clamp(0.5f + 4.0f * (rf_abs(" + ny + ") - rf_abs(" + nx + ")), 0.0f, 1.0f);");
        emit.decl(0, found);
        emit.decl(1, found + " ? " + t + " : 0.0f");
        emit.decl(2, "rf_abs(" + nx + ") > rf_abs(" + ny + ") ? 0 : 1");
        emit.decl(3, found + " ? " + cell + " : 0");
        emit.decl(4, "(1.0f - " + w + ") * rf_fract(" + py + ") + " + w + " * rf_fract(" + px + ")");
        emit.decl(5, nx);
        emit.decl(6, ny);
    }
    //endregion
    //region Lighting and Texture
    private static void emitGameLight(Emit emit) {
        String F_ = emit.ft();
        String n = "gl" + emit.id + "_";
        String dist = emit.in(0), ao = emit.in(1), ndl = emit.in(2), ndf = emit.in(3), fin = emit.in(4);
        emit.line(F_ + " " + n + "att = 1.0f / (1.0f + " + dist + " * " + dist + " / 9.0f);");
        emit.line(F_ + " " + n + "ft = 0.95f * " + n + "att * " + ndl + " * (0.6f + 0.4f * " + ao + ");");
        String[] ch = {"r", "g", "b"};
        String[] amb = {"0.10f", "0.115f", "0.17f"}, fill = {"0.30f", "0.28f", "0.25f"}, flash = {"1.0f", "0.88f", "0.70f"};
        for (int c = 0; c < 3; c++) emit.line(F_ + " " + n + ch[c] + " = " + ao + " * (" + amb[c] + " + " + ndf + " * " + fill[c] + ") + " + n + "ft * " + flash[c] + ";");
        for (int c = 0; c < 3; c++) emit.decl(c, fin + " ? rf_max(" + n + ch[c] + ", 0.7f) : " + n + ch[c]);
        emit.decl(3, "(" + n + "r + " + n + "g + " + n + "b) / 3.0f");
    }
    private static void emitLitColor(Emit emit) {
        String F_ = emit.ft();
        String n = "lc" + emit.id + "_";
        String c = emit.in(0);
        String[] ch = {"r", "g", "b"}, ext = {"rf_chR", "rf_chG", "rf_chB"};
        for (int k = 0; k < 3; k++) {
            String v = n + ch[k];
            emit.line(F_ + " " + v + " = " + ext[k] + "(" + c + ") * " + emit.in(1 + k) + ";");
            emit.line(F_ + " " + v + "t = " + v + " <= 0.7f ? " + v + " : 0.7f + 0.3f * (1.0f - rf_exp(-(" + v + " - 0.7f) / 0.3f));");
        }
        emit.decl(0, "rf_rgb(" + n + "rt, " + n + "gt, " + n + "bt)");
    }
    private static void emitTexture(Emit emit) {
        int idx = switch (emit.param("texture")) {
            case "floor" -> 1;
            case "ceiling" -> 2;
            case "finish" -> 3;
            default -> 0;
        };
        String tex = switch (idx) {
            case 1 -> "texFloor";
            case 2 -> "texCeil";
            case 3 -> "texFinish";
            default -> "texWall";
        };
        int base = 5 + 2 * idx;
        String r = "t" + emit.id + "_";
        emit.line("int " + r + "w = pi[" + base + "], " + r + "h = pi[" + (base + 1) + "];");
        emit.line("int " + emit.out(0) + " = 0;");
        emit.line("if (" + r + "w > 0 && " + r + "h > 0) {");
        emit.line("\tint " + r + "x = (int)(rf_fract(" + emit.in(0) + ") * (" + emit.ft() + ")" + r + "w);");
        emit.line("\tint " + r + "y = (int)(rf_fract(" + emit.in(1) + ") * (" + emit.ft() + ")" + r + "h);");
        emit.line("\tif (" + r + "x >= " + r + "w) " + r + "x = " + r + "w - 1;");
        emit.line("\tif (" + r + "y >= " + r + "h) " + r + "y = " + r + "h - 1;");
        emit.line("\t" + emit.out(0) + " = " + tex + "[" + r + "y * " + r + "w + " + r + "x];");
        emit.line("}");
    }
    //endregion
    //region Ray Casting
    private static void emitRayDirection(Emit emit) {
        double fov = Math.clamp(emit.fparam("fov"), 1, 170);
        double t = Math.tan(Math.toRadians(fov) / 2);
        String a = "a" + emit.id;
        emit.line(emit.ft() + " " + a + " = " + emit.in(1) + " + rf_atan(" + emit.in(0) + " * " + emit.flit(t) + ");");
        emit.decl(0, "rf_cos(" + a + ")");
        emit.decl(1, "rf_sin(" + a + ")");
    }
    private static void emitRayGrid(Emit emit) {
        int steps = emit.iparam("maxSteps");
        String r = "r" + emit.id + "_";
        String F_ = emit.ft();
        String ox = emit.in(0), oy = emit.in(1), dx0 = emit.in(2), dy0 = emit.in(3);
        emit.line(F_ + " " + r + "len = rf_sqrt(" + dx0 + " * " + dx0 + " + " + dy0 + " * " + dy0 + ");");
        emit.line(F_ + " " + r + "dx = rf_div(" + dx0 + ", " + r + "len);");
        emit.line(F_ + " " + r + "dy = rf_div(" + dy0 + ", " + r + "len);");
        emit.line("int " + r + "mx = (int)rf_floor(" + ox + "), " + r + "my = (int)rf_floor(" + oy + ");");
        emit.line(F_ + " " + r + "ddx = " + r + "dx == 0.0f ? 1e30f : rf_abs(rf_div(1.0f, " + r + "dx));");
        emit.line(F_ + " " + r + "ddy = " + r + "dy == 0.0f ? 1e30f : rf_abs(rf_div(1.0f, " + r + "dy));");
        emit.line("int " + r + "sx = " + r + "dx < 0.0f ? -1 : 1;");
        emit.line("int " + r + "sy = " + r + "dy < 0.0f ? -1 : 1;");
        emit.line(F_ + " " + r + "sdx = (" + r + "dx < 0.0f ? " + ox + " - (" + F_ + ")" + r + "mx : (" + F_ + ")" + r + "mx + 1.0f - " + ox + ") * " + r + "ddx;");
        emit.line(F_ + " " + r + "sdy = (" + r + "dy < 0.0f ? " + oy + " - (" + F_ + ")" + r + "my : (" + F_ + ")" + r + "my + 1.0f - " + oy + ") * " + r + "ddy;");
        emit.line(emit.bt() + " " + emit.out(0) + " = false;");
        emit.line(F_ + " " + emit.out(1) + " = 0.0f;");
        emit.line("int " + emit.out(2) + " = 0;");
        emit.line("int " + emit.out(3) + " = 0;");
        emit.line(F_ + " " + emit.out(4) + " = 0.0f;");
        emit.line("for (int " + r + "s = 0; " + r + "s < " + steps + "; " + r + "s++) {");
        emit.line("\t" + F_ + " " + r + "d;");
        emit.line("\tint " + r + "sd;");
        emit.line("\tif (" + r + "sdx < " + r + "sdy) { " + r + "d = " + r + "sdx; " + r + "sdx += " + r + "ddx; " + r + "mx += " + r + "sx; " + r + "sd = 0; }");
        emit.line("\telse { " + r + "d = " + r + "sdy; " + r + "sdy += " + r + "ddy; " + r + "my += " + r + "sy; " + r + "sd = 1; }");
        emit.line("\tint " + r + "c = rf_mapAt(map, pi, " + r + "mx, " + r + "my);");
        emit.line("\tif (" + r + "c != 0) {");
        emit.line("\t\t" + emit.out(0) + " = true;");
        emit.line("\t\t" + emit.out(1) + " = " + r + "d;");
        emit.line("\t\t" + emit.out(2) + " = " + r + "sd;");
        emit.line("\t\t" + emit.out(3) + " = " + r + "c;");
        emit.line("\t\t" + emit.out(4) + " = rf_fract(" + r + "sd == 0 ? " + oy + " + " + r + "d * " + r + "dy : " + ox + " + " + r + "d * " + r + "dx);");
        emit.line("\t\tbreak;");
        emit.line("\t}");
        emit.line("}");
    }
    private static void emitRayGridPortal(Emit emit) {
        int steps = emit.iparam("maxSteps");
        String F_ = emit.ft(), B_ = emit.bt(), I_ = "int";
        String r = "pg" + emit.id + "_";
        String ox = r + "ox", oy = r + "oy", dx = r + "dx", dy = r + "dy";
        String mx = r + "mx", my = r + "my", sx = r + "sx", sy = r + "sy";
        String ddx = r + "ddx", ddy = r + "ddy", sdx = r + "sdx", sdy = r + "sdy";
        String total = r + "total", hops = r + "hops", reinit = r + "reinit", done = r + "done", found = r + "found";
        String hs = r + "hs", hc = r + "hc", ht = r + "ht", hu = r + "hu", b = r + "b";
        emit.line(F_ + " " + ox + " = " + emit.in(0) + ", " + oy + " = " + emit.in(1) + ";");
        emit.line(F_ + " " + dx + " = " + emit.in(2) + ", " + dy + " = " + emit.in(3) + ";");
        emit.line(F_ + " " + r + "len = rf_sqrt(" + dx + " * " + dx + " + " + dy + " * " + dy + ");");
        emit.line(dx + " = rf_div(" + dx + ", " + r + "len); " + dy + " = rf_div(" + dy + ", " + r + "len);");
        emit.line(F_ + " " + total + " = " + emit.flit(0) + ";");
        emit.line("int " + hops + " = 0;");
        emit.line(B_ + " " + reinit + " = true, " + done + " = false, " + found + " = false;");
        emit.line("int " + hs + " = 0, " + hc + " = 0;");
        emit.line(F_ + " " + ht + " = " + emit.flit(0) + ", " + hu + " = " + emit.flit(0) + ";");
        emit.line("int " + mx + " = 0, " + my + " = 0, " + sx + " = 1, " + sy + " = 1;");
        emit.line(F_ + " " + ddx + " = 0, " + ddy + " = 0, " + sdx + " = 0, " + sdy + " = 0;");
        emit.line("for (int " + r + "s = 0; " + r + "s < " + steps + " && !" + done + "; " + r + "s++) {");
        emit.line("\tif (" + reinit + ") {");
        emit.line("\t\t" + mx + " = (int)rf_floor(" + ox + "); " + my + " = (int)rf_floor(" + oy + ");");
        emit.line("\t\t" + ddx + " = " + dx + " == 0.0f ? " + emit.flit(1e30) + " : rf_abs(rf_div(1.0f, " + dx + "));");
        emit.line("\t\t" + ddy + " = " + dy + " == 0.0f ? " + emit.flit(1e30) + " : rf_abs(rf_div(1.0f, " + dy + "));");
        emit.line("\t\t" + sx + " = " + dx + " < 0.0f ? -1 : 1;");
        emit.line("\t\t" + sy + " = " + dy + " < 0.0f ? -1 : 1;");
        emit.line("\t\t" + sdx + " = (" + dx + " < 0.0f ? " + ox + " - (" + F_ + ")" + mx + " : (" + F_ + ")" + mx + " + 1.0f - " + ox + ") * " + ddx + ";");
        emit.line("\t\t" + sdy + " = (" + dy + " < 0.0f ? " + oy + " - (" + F_ + ")" + my + " : (" + F_ + ")" + my + " + 1.0f - " + oy + ") * " + ddy + ";");
        emit.line("\t\t" + reinit + " = false;");
        emit.line("\t}");
        emit.line("\t" + F_ + " " + r + "t; int " + r + "side;");
        emit.line("\tif (" + sdx + " < " + sdy + ") { " + r + "t = " + sdx + "; " + sdx + " += " + ddx + "; " + mx + " += " + sx + "; " + r + "side = 0; }");
        emit.line("\telse { " + r + "t = " + sdy + "; " + sdy + " += " + ddy + "; " + my + " += " + sy + "; " + r + "side = 1; }");
        emit.line("\tint " + r + "c = rf_mapAt(map, pi, " + mx + ", " + my + ");");
        emit.line("\tint " + b + " = 14 + 6 * (" + my + " * pi[3] + " + mx + ");");
        emit.line("\tif (" + r + "c == -1) { " + done + " = true; }");
        emit.line("\telse if (" + r + "c == 5 && " + hops + " < 8 && (pi[" + b + "] != 0 || pi[" + b + " + 1] != 0)"
                + " && " + dx + " * (" + F_ + ")pi[" + b + "] + " + dy + " * (" + F_ + ")pi[" + b + " + 1] > 0.0f) {");
        emit.line("\t\t" + F_ + " hx = " + ox + " + " + dx + " * " + r + "t, hy = " + oy + " + " + dy + " * " + r + "t;");
        emit.line("\t\t" + total + " += " + r + "t;");
        emit.line("\t\t" + F_ + " nax = (" + F_ + ")pi[" + b + "], nay = (" + F_ + ")pi[" + b + " + 1];");
        emit.line("\t\t" + F_ + " lx = (" + F_ + ")pi[" + b + " + 2], ly = (" + F_ + ")pi[" + b + " + 3];");
        emit.line("\t\t" + F_ + " mbx = (" + F_ + ")pi[" + b + " + 4], mby = (" + F_ + ")pi[" + b + " + 5];");
        emit.line("\t\t" + F_ + " u = (hx - (" + mx + " + rf_max(nax, 0.0f) + rf_max(nay, 0.0f))) * (-nay)"
                + " + (hy - (" + my + " + rf_max(nay, 0.0f) + rf_max(-nax, 0.0f))) * nax;");
        emit.line("\t\tu = rf_clamp(u, 5e-4f, 1.0f - 5e-4f);");
        emit.line("\t\t" + F_ + " cosT = nax * mbx + nay * mby, sinT = nax * mby - nay * mbx;");
        emit.line("\t\t" + F_ + " ndx = " + dx + " * cosT - " + dy + " * sinT, ndy = " + dx + " * sinT + " + dy + " * cosT;");
        emit.line("\t\t" + F_ + " ex = (lx + rf_max(mbx, 0.0f) + rf_max(mby, 0.0f)) + u * (-mby);");
        emit.line("\t\t" + F_ + " ey = (ly + rf_max(mby, 0.0f) + rf_max(-mbx, 0.0f)) + u * mbx;");
        emit.line("\t\t" + ox + " = ex + ndx * 1e-4f; " + oy + " = ey + ndy * 1e-4f;");
        emit.line("\t\t" + dx + " = ndx; " + dy + " = ndy;");
        emit.line("\t\t" + hops + "++;");
        emit.line("\t\t" + reinit + " = true;");
        emit.line("\t}");
        emit.line("\telse if (" + r + "c != 0 && " + r + "c != 5) {");
        emit.line("\t\t" + found + " = true; " + hs + " = " + r + "side; " + hc + " = " + r + "c;");
        emit.line("\t\t" + ht + " = " + total + " + " + r + "t;");
        emit.line("\t\t" + hu + " = rf_fract(" + r + "side == 0 ? " + oy + " + " + r + "t * " + dy + " : " + ox + " + " + r + "t * " + dx + ");");
        emit.line("\t\t" + done + " = true;");
        emit.line("\t}");
        emit.line("}");
        emit.line(emit.typeName(emit.outType(0)) + " " + emit.out(0) + " = " + found + ";");
        emit.line(emit.typeName(emit.outType(1)) + " " + emit.out(1) + " = " + found + " ? " + ht + " : " + emit.flit(0) + ";");
        emit.line(I_ + " " + emit.out(2) + " = " + hs + ";");
        emit.line(I_ + " " + emit.out(3) + " = " + hc + ";");
        emit.line(emit.typeName(emit.outType(4)) + " " + emit.out(4) + " = " + hu + ";");
    }
    //endregion
    //endregion
}