package Project.EngineCreator;

import java.util.*;

enum DataType {
    FLOAT, INT, BOOL, COLOR, ANY;
    String label() { return L.t("ec.type." + name().toLowerCase(Locale.ROOT)); }
}
record Port(String name, DataType type) {}
record Param(String key, Kind kind, String def, List<String> choices, double min, double max) {
    enum Kind { FLOAT, INT, COLOR, CHOICE }

    String label() { return L.t("ec.p." + key); }
    String choiceLabel(String value) { return L.t("ec.o." + value); }

    static Param num(String key, double def, double min, double max) { return new Param(key, Kind.FLOAT, fmt(def), List.of(), min, max); }
    static Param whole(String key, int def, int min, int max) { return new Param(key, Kind.INT, String.valueOf(def), List.of(), min, max); }
    static Param color(String key, String def) {
        return new Param(key, Kind.COLOR, def, List.of(), 0, 0);
    }
    static Param choice(String key, String def, String... options) { return new Param(key, Kind.CHOICE, def, List.of(options), 0, 0); }
    private static String fmt(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }
}

final class Emit {
    final boolean java;
    final int id;
    private final String[] in, out;
    private final DataType[] outTypes;
    private final Map<String, String> params;
    final List<String> lines;

    Emit(boolean java, int id, String[] in, String[] out, DataType[] outTypes, Map<String, String> params, List<String> lines) {
        this.java = java;
        this.id = id;
        this.in = in;
        this.out = out;
        this.outTypes = outTypes;
        this.params = params;
        this.lines = lines;
    }

    String in(int i) { return in[i]; }
    String out(int i) { return out[i]; }
    DataType outType(int i) { return outTypes[i]; }

    String ft() { return java ? "double" : "float"; }
    String bt() { return java ? "boolean" : "bool"; }

    String typeName(DataType t) {
        return switch (t) {
            case FLOAT -> ft();
            case INT, COLOR -> "int";
            case BOOL -> bt();
            case ANY -> throw new IllegalStateException("type not resolved");
        };
    }

    String flit(double d) {
        String s = d + "f";
        return d < 0 ? "(" + s + ")" : s;
    }
    String ilit(int v) { return v < 0 ? "(" + v + ")" : String.valueOf(v); }

    String param(String key) { return params.get(key); }
    double fparam(String key) { return Double.parseDouble(params.get(key).trim()); }
    int iparam(String key) { return Integer.parseInt(params.get(key).trim()); }

    void line(String s) { lines.add(s); }
    void decl(int i, String expr) { line(typeName(outTypes[i]) + " " + out[i] + " = " + expr + ";"); }
}

@FunctionalInterface interface Emitter { void emit(Emit e); }
@FunctionalInterface interface Ex { String of(Emit e); }

record NodeKind(String id, String categoryId, List<Port> inputs, List<Port> outputs, List<Param> params, Emitter emitter) {
    String title() { return L.t("ec.n." + id); }
    String help() { return L.t("ec.n." + id + ".help"); }
    String category() { return L.t("ec.c." + categoryId); }

    boolean isOutput() { return id.equals("output"); }

    int inputIndex(String name) {
        for (int i = 0; i < inputs.size(); i++) if (inputs.get(i).name().equals(name)) return i;
        return -1;
    }
    int outputIndex(String name) {
        for (int i = 0; i < outputs.size(); i++) if (outputs.get(i).name().equals(name)) return i;
        return -1;
    }
    Param param(String key) {
        for (Param p : params) if (p.key().equals(key)) return p;
        return null;
    }
}

final class Nodes {
    static final String CAT_IN = "in", CAT_CONST = "const", CAT_MATH = "math", CAT_LOGIC = "logic", CAT_CONV = "conv", CAT_COLOR = "color",
            CAT_MAP = "map", CAT_TEX = "tex", CAT_RAY = "ray", CAT_GEO = "geo", CAT_FX = "fx", CAT_OUT = "out";

    private static final DataType F = DataType.FLOAT, I = DataType.INT, B = DataType.BOOL, C = DataType.COLOR, A = DataType.ANY;

    static final List<NodeKind> ALL = new ArrayList<>();
    private static final Map<String, NodeKind> BY_ID = new HashMap<>();

    private Nodes() {}
    static NodeKind find(String id) { return BY_ID.get(id); }
    static NodeKind get(String id) {
        NodeKind k = BY_ID.get(id);
        if (k == null) throw new IllegalArgumentException("unknown node " + id);
        return k;
    }

    private static Port p(String name, DataType t) { return new Port(name, t); }

    private static void add(NodeKind k) {
        ALL.add(k);
        BY_ID.put(k.id(), k);
    }
    private static void pure(String id, String cat, List<Port> ins, DataType out, Ex ex, Param... ps) {
        add(new NodeKind(id, cat, ins, List.of(new Port("out", out)), List.of(ps), e -> e.decl(0, ex.of(e))));
    }
    private static void custom(String id, String cat, List<Port> ins, List<Port> outs, Emitter em, Param... ps) {
        add(new NodeKind(id, cat, ins, outs, List.of(ps), em));
    }
    private static void op2(String id, String cat, DataType inT, DataType outT, String op) {
        pure(id, cat, List.of(p("a", inT), p("b", inT)), outT, e -> "(" + e.in(0) + " " + op + " " + e.in(1) + ")");
    }
    private static void fn1(String id, String cat, String fn) {
        pure(id, cat, List.of(p("a", F)), F, e -> fn + "(" + e.in(0) + ")");
    }
    static {
        // ---- inputs ----
        pure("pixelX", CAT_IN, List.of(), I, e -> "px");
        pure("pixelY", CAT_IN, List.of(), I, e -> "py");
        pure("screenX", CAT_IN, List.of(), F, e -> "(2.0f * ((" + e.ft() + ")px + 0.5f) / (" + e.ft() + ")pi[0] - 1.0f)");
        pure("screenY", CAT_IN, List.of(), F, e -> "(1.0f - 2.0f * ((" + e.ft() + ")py + 0.5f) / (" + e.ft() + ")pi[1])");
        pure("width", CAT_IN, List.of(), I, e -> "pi[0]");
        pure("height", CAT_IN, List.of(), I, e -> "pi[1]");
        pure("quality", CAT_IN, List.of(), I, e -> "pi[2]");
        pure("cameraAngle", CAT_IN, List.of(), F, e -> "pf[0]");
        pure("cameraHeight", CAT_IN, List.of(), F, e -> "pf[1]");
        pure("cameraPitch", CAT_IN, List.of(), F, e -> "pf[2]");
        pure("playerX", CAT_IN, List.of(), F, e -> "pf[3]");
        pure("playerY", CAT_IN, List.of(), F, e -> "pf[4]");
        pure("time", CAT_IN, List.of(), F, e -> "pf[6]");

        // ---- constants ----
        pure("constFloat", CAT_CONST, List.of(), F, e -> e.flit(e.fparam("value")), Param.num("value", 1, -1e6, 1e6));
        pure("constInt", CAT_CONST, List.of(), I, e -> e.ilit(e.iparam("value")), Param.whole("value", 1, -1000000, 1000000));
        pure("constColor", CAT_CONST, List.of(), C,
                e -> "((int)0xFF" + e.param("color").substring(1).toUpperCase(Locale.ROOT) + ")",
                Param.color("color", "#FFFFFF"));

        // ---- math ----
        op2("add", CAT_MATH, F, F, "+");
        op2("sub", CAT_MATH, F, F, "-");
        op2("mul", CAT_MATH, F, F, "*");
        pure("div", CAT_MATH, List.of(p("a", F), p("b", F)), F, e -> "rf_div(" + e.in(0) + ", " + e.in(1) + ")");
        pure("neg", CAT_MATH, List.of(p("a", F)), F, e -> "(-" + e.in(0) + ")");
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
        pure("pow", CAT_MATH, List.of(p("a", F), p("b", F)), F, e -> "rf_pow(" + e.in(0) + ", " + e.in(1) + ")");
        pure("min", CAT_MATH, List.of(p("a", F), p("b", F)), F, e -> "rf_min(" + e.in(0) + ", " + e.in(1) + ")");
        pure("max", CAT_MATH, List.of(p("a", F), p("b", F)), F, e -> "rf_max(" + e.in(0) + ", " + e.in(1) + ")");
        pure("clamp", CAT_MATH, List.of(p("x", F), p("lo", F), p("hi", F)), F, e -> "rf_clamp(" + e.in(0) + ", " + e.in(1) + ", " + e.in(2) + ")");
        pure("mix", CAT_MATH, List.of(p("a", F), p("b", F), p("t", F)), F, e -> "rf_mix(" + e.in(0) + ", " + e.in(1) + ", " + e.in(2) + ")");

        // ---- logic ----
        op2("less", CAT_LOGIC, F, B, "<");
        op2("greater", CAT_LOGIC, F, B, ">");
        op2("and", CAT_LOGIC, B, B, "&&");
        op2("or", CAT_LOGIC, B, B, "||");
        pure("not", CAT_LOGIC, List.of(p("a", B)), B, e -> "(!" + e.in(0) + ")");
        pure("select", CAT_LOGIC, List.of(p("cond", B), p("a", A), p("b", A)), A,
                e -> "(" + e.in(0) + " ? " + e.in(1) + " : " + e.in(2) + ")");

        // ---- conversions ----
        pure("toFloat", CAT_CONV, List.of(p("a", I)), F, e -> "((" + e.ft() + ")" + e.in(0) + ")");
        pure("toInt", CAT_CONV, List.of(p("a", F)), I, e -> "((int)rf_floor(" + e.in(0) + "))");

        // ---- colour ----
        pure("makeColor", CAT_COLOR, List.of(p("r", F), p("g", F), p("b", F)), C, e -> "rf_rgb(" + e.in(0) + ", " + e.in(1) + ", " + e.in(2) + ")");
        custom("splitColor", CAT_COLOR, List.of(p("c", C)), List.of(p("r", F), p("g", F), p("b", F)), e -> {
            e.decl(0, "rf_chR(" + e.in(0) + ")");
            e.decl(1, "rf_chG(" + e.in(0) + ")");
            e.decl(2, "rf_chB(" + e.in(0) + ")");
        });
        pure("mixColor", CAT_COLOR, List.of(p("a", C), p("b", C), p("t", F)), C, e -> "rf_mixColor(" + e.in(0) + ", " + e.in(1) + ", " + e.in(2) + ")");
        pure("brightness", CAT_COLOR, List.of(p("c", C), p("k", F)), C, e -> "rf_brightness(" + e.in(0) + ", " + e.in(1) + ")");
        pure("fog", CAT_COLOR, List.of(p("c", C), p("fogColor", C), p("distance", F), p("density", F)), C, e -> "rf_fog(" + e.in(0) + ", " + e.in(1) + ", " + e.in(2) + ", " + e.in(3) + ")");
        pure("posterize", CAT_COLOR, List.of(p("c", C), p("levels", F)), C, e -> "rf_posterize(" + e.in(0) + ", " + e.in(1) + ")");

        // ---- map and textures ----
        pure("mapCell", CAT_MAP, List.of(p("x", I), p("y", I)), I, e -> "rf_mapAt(map, pi, " + e.in(0) + ", " + e.in(1) + ")");
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
        pure("curvature", CAT_GEO, List.of(), F, e -> "pf[5]");
        pure("hyperbolic", CAT_GEO, List.of(), B, e -> "(pi[13] != 0)");
        pure("hypDistance", CAT_GEO, List.of(p("t", F), p("k", F)), F,
                e -> "rf_hypd(" + e.in(0) + ", " + e.in(1) + ")");

        // ---- effects: noise and previous pass ----
        pure("hash", CAT_FX, List.of(p("x", F), p("y", F), p("seed", F)), F, e -> "rf_hash(" + e.in(0) + ", " + e.in(1) + ", " + e.in(2) + ")");
        pure("neighbor", CAT_FX, List.of(), C, e -> "rf_prev(prev, pi, px + " + e.ilit(e.iparam("dx")) + ", py + " + e.ilit(e.iparam("dy")) + ")",
                Param.whole("dx", 0, -8, 8), Param.whole("dy", 0, -8, 8));

        // ---- extra math ----
        pure("mod", CAT_MATH, List.of(p("a", F), p("b", F)), F, e -> "(" + e.in(0) + " - " + e.in(1) + " * rf_floor(rf_div(" + e.in(0) + ", " + e.in(1) + ")))");
        pure("round", CAT_MATH, List.of(p("a", F)), F, e -> "rf_floor(" + e.in(0) + " + 0.5f)");
        pure("ceil", CAT_MATH, List.of(p("a", F)), F, e -> "(-rf_floor(-" + e.in(0) + "))");
        pure("sign", CAT_MATH, List.of(p("a", F)), F, e -> "(" + e.in(0) + " < 0.0f ? -1.0f : (" + e.in(0) + " > 0.0f ? 1.0f : 0.0f))");
        pure("step", CAT_MATH, List.of(p("edge", F), p("x", F)), F, e -> "(" + e.in(1) + " < " + e.in(0) + " ? 0.0f : 1.0f)");
        pure("wave", CAT_MATH, List.of(p("t", F), p("freq", F)), F, e -> "(0.5f + 0.5f * rf_sin(" + e.in(0) + " * " + e.in(1) + "))");
        pure("schlick", CAT_MATH, List.of(p("cosine", F)), F, e -> "rf_pow(rf_clamp(1.0f - " + e.in(0) + ", 0.0f, 1.0f), 5.0f)");
        custom("smoothstep", CAT_MATH, List.of(p("edge0", F), p("edge1", F), p("x", F)), List.of(p("out", F)), e -> {
            String t = "sm" + e.id;
            e.line(e.ft() + " " + t + " = rf_clamp(rf_div(" + e.in(2) + " - " + e.in(0) + ", " + e.in(1) + " - " + e.in(0) + "), 0.0f, 1.0f);");
            e.decl(0, t + " * " + t + " * (3.0f - 2.0f * " + t + ")");
        });
        custom("smoothMin", CAT_MATH, List.of(p("a", F), p("b", F), p("k", F)), List.of(p("out", F)), e -> {
            String h = "smh" + e.id;
            e.line(e.ft() + " " + h + " = rf_clamp(0.5f + 0.5f * rf_div(" + e.in(1) + " - " + e.in(0) + ", " + e.in(2) + "), 0.0f, 1.0f);");
            e.decl(0, "rf_mix(" + e.in(1) + ", " + e.in(0) + ", " + h + ") - " + e.in(2) + " * " + h + " * (1.0f - " + h + ")");
        });
        pure("dot3", CAT_MATH, List.of(p("ax", F), p("ay", F), p("az", F), p("bx", F), p("by", F), p("bz", F)), F,
                e -> "(" + e.in(0) + " * " + e.in(3) + " + " + e.in(1) + " * " + e.in(4) + " + " + e.in(2) + " * " + e.in(5) + ")");
        pure("length3", CAT_MATH, List.of(p("x", F), p("y", F), p("z", F)), F,
                e -> "rf_sqrt(" + e.in(0) + " * " + e.in(0) + " + " + e.in(1) + " * " + e.in(1) + " + " + e.in(2) + " * " + e.in(2) + ")");

        // ---- extra logic ----
        op2("greaterEq", CAT_LOGIC, F, B, ">=");
        op2("lessEq", CAT_LOGIC, F, B, "<=");
        op2("equalInt", CAT_LOGIC, I, B, "==");
        op2("xor", CAT_LOGIC, B, B, "!=");

        // ---- extra colour ----
        pure("luma", CAT_COLOR, List.of(p("c", C)), F,
                e -> "(0.299f * rf_chR(" + e.in(0) + ") + 0.587f * rf_chG(" + e.in(0) + ") + 0.114f * rf_chB(" + e.in(0) + "))");
        pure("invertColor", CAT_COLOR, List.of(p("c", C)), C,
                e -> "rf_rgb(1.0f - rf_chR(" + e.in(0) + "), 1.0f - rf_chG(" + e.in(0) + "), 1.0f - rf_chB(" + e.in(0) + "))");
        pure("grayscale", CAT_COLOR, List.of(p("c", C)), C, e -> {
            String c = e.in(0);
            String l = "(0.299f * rf_chR(" + c + ") + 0.587f * rf_chG(" + c + ") + 0.114f * rf_chB(" + c + "))";
            return "rf_rgb(" + l + ", " + l + ", " + l + ")";
        });
        pure("addColor", CAT_COLOR, List.of(p("a", C), p("b", C)), C, e -> {
            String a = e.in(0), b = e.in(1);
            return "rf_rgb(rf_clamp(rf_chR(" + a + ") + rf_chR(" + b + "), 0.0f, 1.0f), rf_clamp(rf_chG(" + a + ") + rf_chG(" + b
                    + "), 0.0f, 1.0f), rf_clamp(rf_chB(" + a + ") + rf_chB(" + b + "), 0.0f, 1.0f))";
        });
        pure("multiplyColor", CAT_COLOR, List.of(p("a", C), p("b", C)), C, e -> {
            String a = e.in(0), b = e.in(1);
            return "rf_rgb(rf_chR(" + a + ") * rf_chR(" + b + "), rf_chG(" + a + ") * rf_chG(" + b + "), rf_chB(" + a + ") * rf_chB(" + b + "))";
        });

        // ---- extra constants ----
        pure("constBool", CAT_CONST, List.of(), B, e -> e.param("value").equals("true") ? "(1 == 1)" : "(1 == 0)",
                Param.choice("value", "true", "true", "false"));

        // ---- extra geometry, noise, patterns ----
        pure("radius", CAT_FX, List.of(p("x", F), p("y", F)), F,
                e -> "rf_sqrt(" + e.in(0) + " * " + e.in(0) + " + " + e.in(1) + " * " + e.in(1) + ")");
        custom("valueNoise", CAT_FX, List.of(p("x", F), p("y", F)), List.of(p("out", F)), e -> {
            String n = "vn" + e.id + "_";
            String seed = e.flit(e.fparam("seed"));
            e.line(e.ft() + " " + n + "ix = rf_floor(" + e.in(0) + "), " + n + "iy = rf_floor(" + e.in(1) + ");");
            e.line(e.ft() + " " + n + "ux = " + e.in(0) + " - " + n + "ix, " + n + "uy = " + e.in(1) + " - " + n + "iy;");
            e.line(n + "ux = " + n + "ux * " + n + "ux * (3.0f - 2.0f * " + n + "ux);");
            e.line(n + "uy = " + n + "uy * " + n + "uy * (3.0f - 2.0f * " + n + "uy);");
            e.line(e.ft() + " " + n + "a = rf_hash(" + n + "ix, " + n + "iy, " + seed + "), " + n + "b = rf_hash(" + n + "ix + 1.0f, " + n + "iy, " + seed + ");");
            e.line(e.ft() + " " + n + "c = rf_hash(" + n + "ix, " + n + "iy + 1.0f, " + seed + "), " + n + "d = rf_hash(" + n + "ix + 1.0f, " + n + "iy + 1.0f, " + seed + ");");
            e.decl(0, "rf_mix(rf_mix(" + n + "a, " + n + "b, " + n + "ux), rf_mix(" + n + "c, " + n + "d, " + n + "ux), " + n + "uy)");
        }, Param.num("seed", 0, -1000, 1000));
        pure("checker", CAT_TEX, List.of(p("u", F), p("v", F)), F,
                e -> {
                    String s = e.flit(e.fparam("scale"));
                    return "(rf_fract((rf_floor(" + e.in(0) + " * " + s + ") + rf_floor(" + e.in(1) + " * " + s + ")) * 0.5f) < 0.5f ? 0.0f : 1.0f)";
                },
                Param.num("scale", 4, 0.1, 1000));
        custom("sdfBox", CAT_GEO, List.of(p("px", F), p("py", F), p("pz", F), p("hx", F), p("hy", F), p("hz", F)),
                List.of(p("d", F)), e -> {
                    String q = "bq" + e.id + "_";
                    e.line(e.ft() + " " + q + "x = rf_abs(" + e.in(0) + ") - " + e.in(3) + ", " + q + "y = rf_abs(" + e.in(1) + ") - "
                            + e.in(4) + ", " + q + "z = rf_abs(" + e.in(2) + ") - " + e.in(5) + ";");
                    e.decl(0, "rf_sqrt(rf_max(" + q + "x, 0.0f) * rf_max(" + q + "x, 0.0f) + rf_max(" + q + "y, 0.0f) * rf_max(" + q
                            + "y, 0.0f) + rf_max(" + q + "z, 0.0f) * rf_max(" + q + "z, 0.0f)) + rf_min(rf_max(" + q + "x, rf_max(" + q
                            + "y, " + q + "z)), 0.0f)");
                });

        // ---- extra tracing ----
        custom("pointOnRay", CAT_RAY, List.of(p("ox", F), p("oy", F), p("dx", F), p("dy", F), p("t", F)),
                List.of(p("x", F), p("y", F)), e -> {
                    e.decl(0, e.in(0) + " + " + e.in(2) + " * " + e.in(4));
                    e.decl(1, e.in(1) + " + " + e.in(3) + " * " + e.in(4));
                });
        custom("wallNormal", CAT_RAY, List.of(p("dirX", F), p("dirY", F), p("side", I)),
                List.of(p("nx", F), p("ny", F)), e -> {
                    e.decl(0, "(" + e.in(2) + " == 0 ? (" + e.in(0) + " < 0.0f ? 1.0f : -1.0f) : 0.0f)");
                    e.decl(1, "(" + e.in(2) + " == 1 ? (" + e.in(1) + " < 0.0f ? 1.0f : -1.0f) : 0.0f)");
                });
        custom("reflect2", CAT_RAY, List.of(p("dirX", F), p("dirY", F), p("nx", F), p("ny", F)),
                List.of(p("rx", F), p("ry", F)), e -> {
                    String d = "rdt" + e.id;
                    e.line(e.ft() + " " + d + " = " + e.in(0) + " * " + e.in(2) + " + " + e.in(1) + " * " + e.in(3) + ";");
                    e.decl(0, e.in(0) + " - 2.0f * " + d + " * " + e.in(2));
                    e.decl(1, e.in(1) + " - 2.0f * " + d + " * " + e.in(3));
                });
        pure("isWall", CAT_MAP, List.of(p("x", I), p("y", I)), B, e -> "(rf_mapAt(map, pi, " + e.in(0) + ", " + e.in(1) + ") > 0)");

        // ---- marching over rounded walls ----
        custom("marchRounded", CAT_RAY, List.of(p("originX", F), p("originY", F), p("dirX", F), p("dirY", F)),
                List.of(p("hit", B), p("distance", F), p("side", I), p("cell", I), p("hitU", F), p("nx", F), p("ny", F)),
                Nodes::emitMarchRounded, Param.whole("maxSteps", 96, 1, 2000), Param.num("outerRadius", 0.2, 0, 0.5),
                Param.num("innerRadius", 0, 0, 0.5),
                Param.whole("q0Steps", 0, 0, 1000), Param.whole("q1Steps", 0, 0, 1000), Param.whole("q2Steps", 0, 0, 1000));

        // ---- game shading: one node for the whole light, one for the lit colour ----
        custom("gameLight", CAT_FX, List.of(p("dist", F), p("ao", F), p("ndl", F), p("ndf", F), p("finish", B)),
                List.of(p("r", F), p("g", F), p("b", F), p("level", F)), Nodes::emitGameLight);
        custom("litColour", CAT_COLOR, List.of(p("c", C), p("r", F), p("g", F), p("b", F)),
                List.of(p("out", C)), Nodes::emitLitColor);

        // ---- output ----
        add(new NodeKind("output", CAT_OUT, List.of(p("color", C)), List.of(), List.of(), e -> e.line("return (" + e.in(0) + " | 0xFF000000);")));
    }

    private static String finishAt(String x, String y) {
        return "(rf_mapAt(map, pi, " + x + ", " + y + ") == 2)";
    }
    private static String wallAt(String x, String y) {
        return "(rf_mapAt(map, pi, " + x + ", " + y + ") == 1)";
    }
    private static String solidAt(String x, String y) {
        String m = "rf_mapAt(map, pi, " + x + ", " + y + ")";
        return "(" + m + " == 1 || " + m + " == 2)";
    }
    private static void emitWallSdf(Emit e, String p, String sx, String sy, String dVar, String cVar, String xVar, String yVar, String filVar, String fcxVar, String fcyVar,
                                    double outer, double inner) {
        String F_ = e.ft(), B_ = e.bt();
        String O = e.flit(outer), I = e.flit(inner);
        String X = p + "X", Y = p + "Y";
        e.line(F_ + " " + dVar + " = 1e30f;");
        e.line("int " + cVar + " = 0;");
        e.line("{");
        e.line("\tint " + p + "cx = (int)rf_floor(" + sx + "), " + p + "cy = (int)rf_floor(" + sy + ");");
        e.line("\tfor (int " + p + "j = -1; " + p + "j <= 1; " + p + "j++) for (int " + p + "i = -1; " + p + "i <= 1; " + p + "i++) {");
        e.line("\t\tint " + X + " = " + p + "cx + " + p + "i, " + Y + " = " + p + "cy + " + p + "j;");
        e.line("\t\tint " + p + "c = rf_mapAt(map, pi, " + X + ", " + Y + ");");
        e.line("\t\tif (" + p + "c == 1 || " + p + "c == 2) {");
        e.line("\t\t\t" + B_ + " " + p + "r = " + solidAt(X + " + 1", Y) + ", " + p + "l = " + solidAt(X + " - 1", Y) + ";");
        e.line("\t\t\t" + B_ + " " + p + "u = " + solidAt(X, Y + " + 1") + ", " + p + "d = " + solidAt(X, Y + " - 1") + ";");

        e.line("\t\t\t" + F_ + " " + p + "ro = " + p + "c == 1 ? " + O + " : 0.0f;");
        e.line("\t\t\t" + F_ + " " + p + "tr = (!" + p + "r && !" + p + "u && !" + finishAt(X + " + 1", Y + " + 1") + ") ? " + p + "ro : 0.0f;");
        e.line("\t\t\t" + F_ + " " + p + "tl = (!" + p + "l && !" + p + "u && !" + finishAt(X + " - 1", Y + " + 1") + ") ? " + p + "ro : 0.0f;");
        e.line("\t\t\t" + F_ + " " + p + "br = (!" + p + "r && !" + p + "d && !" + finishAt(X + " + 1", Y + " - 1") + ") ? " + p + "ro : 0.0f;");
        e.line("\t\t\t" + F_ + " " + p + "bl = (!" + p + "l && !" + p + "d && !" + finishAt(X + " - 1", Y + " - 1") + ") ? " + p + "ro : 0.0f;");
        e.line("\t\t\t" + F_ + " " + p + "qx = " + sx + " - ((float)" + X + " + 0.5f), " + p + "qy = " + sy + " - ((float)" + Y + " + 0.5f);");
        e.line("\t\t\t" + F_ + " " + p + "rr = " + p + "qx > 0.0f ? (" + p + "qy > 0.0f ? " + p + "tr : " + p + "br) : (" + p + "qy > 0.0f ? " + p + "tl : " + p + "bl);");
        e.line("\t\t\t" + F_ + " " + p + "hh = 0.5f - " + p + "rr;");
        e.line("\t\t\t" + F_ + " " + p + "ax = rf_abs(" + p + "qx) - " + p + "hh, " + p + "ay = rf_abs(" + p + "qy) - " + p + "hh;");
        e.line("\t\t\t" + F_ + " " + p + "dd = rf_sqrt(rf_max(" + p + "ax, 0.0f) * rf_max(" + p + "ax, 0.0f) + rf_max(" + p
                + "ay, 0.0f) * rf_max(" + p + "ay, 0.0f)) + rf_min(rf_max(" + p + "ax, " + p + "ay), 0.0f) - " + p + "rr;");
        e.line("\t\t\tif (" + p + "dd < " + dVar + ") { " + dVar + " = " + p + "dd; " + cVar + " = " + p + "c; "
                + xVar + " = " + X + "; " + yVar + " = " + Y + "; " + filVar + " = 0; }");
        e.line("\t\t} else if (" + inner + " > 0.0) {");

        e.line("\t\t\tfor (int " + p + "k = 0; " + p + "k < 4; " + p + "k++) {");
        e.line("\t\t\t\tint " + p + "ax = " + p + "k & 1, " + p + "ay = " + p + "k >> 1;");
        e.line("\t\t\t\tint " + p + "sxs = " + p + "ax * 2 - 1, " + p + "sys = " + p + "ay * 2 - 1;");
        String hN = "(" + X + " + " + p + "sxs)", vN = "(" + Y + " + " + p + "sys)";
        e.line("\t\t\t\tif (" + wallAt(hN, Y) + " && " + wallAt(X, vN) + " && " + wallAt(hN, vN) + ") {");
        e.line("\t\t\t\t\t" + F_ + " " + p + "qx0 = (float)" + X + " + " + p + "ax, " + p + "qy0 = (float)" + Y + " + " + p + "ay;");
        e.line("\t\t\t\t\t" + F_ + " " + p + "cfx = " + p + "qx0 - " + p + "sxs * " + I + ", " + p + "cfy = " + p + "qy0 - " + p + "sys * " + I + ";");
        e.line("\t\t\t\t\t" + F_ + " " + p + "scx = " + p + "qx0 - " + p + "sxs * " + I + " * 0.5f, " + p + "scy = " + p + "qy0 - " + p + "sys * " + I + " * 0.5f;");
        e.line("\t\t\t\t\t" + F_ + " " + p + "dsq = rf_max(rf_abs(" + sx + " - " + p + "scx), rf_abs(" + sy + " - " + p + "scy)) - " + I + " * 0.5f;");
        e.line("\t\t\t\t\t" + F_ + " " + p + "dc = " + I + " - rf_sqrt((" + sx + " - " + p + "cfx) * (" + sx + " - " + p + "cfx) + (" + sy + " - " + p + "cfy) * (" + sy + " - " + p + "cfy));");
        e.line("\t\t\t\t\t" + F_ + " " + p + "piece = rf_max(" + p + "dsq, " + p + "dc);");
        e.line("\t\t\t\t\tif (" + p + "piece < " + dVar + ") { " + dVar + " = " + p + "piece; " + cVar + " = 1; "
                + xVar + " = " + X + " + " + p + "sxs; " + yVar + " = " + Y + "; " + filVar + " = 1; "
                + fcxVar + " = " + p + "cfx; " + fcyVar + " = " + p + "cfy; }");
        e.line("\t\t\t\t}");
        e.line("\t\t\t}");
        e.line("\t\t}");
        e.line("\t}");
        e.line("}");
    }
    private static void emitMarchRounded(Emit e) {
        int steps = e.iparam("maxSteps");
        int q0 = e.iparam("q0Steps"), q1 = e.iparam("q1Steps"), q2 = e.iparam("q2Steps");
        String stepExpr = q0 > 0 ? "(pi[2] == 0 ? " + q0 + " : pi[2] == 1 ? " + q1 + " : " + q2 + ")" : String.valueOf(steps);
        double outer = e.fparam("outerRadius"), inner = e.fparam("innerRadius");
        String F_ = e.ft(), B_ = e.bt();
        String r = "mr" + e.id + "_";
        String ox = e.in(0), oy = e.in(1), dx0 = e.in(2), dy0 = e.in(3);
        String ux = r + "ux", uy = r + "uy", len = r + "len", t = r + "t", found = r + "found";
        String px = r + "px", py = r + "py", cell = r + "cell", bx = r + "bx", by = r + "by";
        e.line(F_ + " " + len + " = rf_sqrt(" + dx0 + " * " + dx0 + " + " + dy0 + " * " + dy0 + ");");
        e.line(F_ + " " + ux + " = rf_div(" + dx0 + ", " + len + "), " + uy + " = rf_div(" + dy0 + ", " + len + ");");
        e.line(F_ + " " + t + " = 0.0f, " + px + " = " + ox + ", " + py + " = " + oy + ";");
        e.line(B_ + " " + found + " = false;"); // u can find me here
        e.line("int " + cell + " = 0, " + bx + " = 0, " + by + " = 0, " + r + "fil = 0;");
        e.line(F_ + " " + r + "fcx = 0.0f, " + r + "fcy = 0.0f;");
        e.line("for (int " + r + "s = 0; " + r + "s < " + stepExpr + " && !" + found + "; " + r + "s++) {");
        e.line("\t" + px + " = " + ox + " + " + ux + " * " + t + ";");
        e.line("\t" + py + " = " + oy + " + " + uy + " * " + t + ";");
        emitWallSdf(e, r + "a", px, py, r + "best", r + "bc", bx, by, r + "fil", r + "fcx", r + "fcy", outer, inner);
        e.line("\tif (" + r + "best < 0.001f) { " + found + " = true; " + cell + " = " + r + "bc; break; }");
        e.line("\t" + t + " += rf_min(rf_max(" + r + "best, 0.002f), 1.0f);");
        e.line("\tif (" + t + " > 200.0f) break;");
        e.line("}");

        String nx = r + "nx", ny = r + "ny";
        String hh = r + "hh", qx = r + "qx", qy = r + "qy", ax = r + "ax", ay = r + "ay", gl = r + "gl";
        e.line(F_ + " " + hh + " = 0.5f;");
        e.line(F_ + " " + qx + " = " + px + " - ((float)" + bx + " + 0.5f), " + qy + " = " + py + " - ((float)" + by + " + 0.5f);");
        e.line(F_ + " " + ax + " = rf_abs(" + qx + ") - " + hh + ", " + ay + " = rf_abs(" + qy + ") - " + hh + ";");
        e.line(F_ + " " + nx + " = 0.0f, " + ny + " = 0.0f;");
        e.line("if (" + ax + " > 0.0f && " + ay + " > 0.0f) {");
        e.line("\t" + F_ + " " + gl + " = rf_sqrt(" + ax + " * " + ax + " + " + ay + " * " + ay + ");");
        e.line("\t" + nx + " = (" + qx + " >= 0.0f ? " + ax + " : -" + ax + ") / " + gl + ";");
        e.line("\t" + ny + " = (" + qy + " >= 0.0f ? " + ay + " : -" + ay + ") / " + gl + ";");
        e.line("} else if (" + ax + " > " + ay + ") {");
        e.line("\t" + nx + " = " + qx + " >= 0.0f ? 1.0f : -1.0f;");
        e.line("} else {");
        e.line("\t" + ny + " = " + qy + " >= 0.0f ? 1.0f : -1.0f;");
        e.line("}");

        e.line("if (" + r + "fil == 1) {");
        e.line("\t" + F_ + " " + r + "fx = " + r + "fcx - " + px + ", " + r + "fy = " + r + "fcy - " + py + ";");
        e.line("\t" + F_ + " " + r + "fl = rf_sqrt(" + r + "fx * " + r + "fx + " + r + "fy * " + r + "fy);");
        e.line("\tif (" + r + "fl > 1e-9f) { " + nx + " = " + r + "fx / " + r + "fl; " + ny + " = " + r + "fy / " + r + "fl; }");
        e.line("}");
        String w = r + "w";
        e.line(F_ + " " + w + " = rf_clamp(0.5f + 4.0f * (rf_abs(" + ny + ") - rf_abs(" + nx + ")), 0.0f, 1.0f);");
        e.decl(0, found);
        e.decl(1, found + " ? " + t + " : 0.0f");
        e.decl(2, "rf_abs(" + nx + ") > rf_abs(" + ny + ") ? 0 : 1");
        e.decl(3, found + " ? " + cell + " : 0");
        e.decl(4, "(1.0f - " + w + ") * rf_fract(" + py + ") + " + w + " * rf_fract(" + px + ")");
        e.decl(5, nx);
        e.decl(6, ny);
    }
    private static void emitGameLight(Emit e) {
        String F_ = e.ft();
        String n = "gl" + e.id + "_";
        String dist = e.in(0), ao = e.in(1), ndl = e.in(2), ndf = e.in(3), fin = e.in(4);
        e.line(F_ + " " + n + "att = 1.0f / (1.0f + " + dist + " * " + dist + " / 9.0f);");
        e.line(F_ + " " + n + "ft = 0.95f * " + n + "att * " + ndl + " * (0.6f + 0.4f * " + ao + ");");
        String[] ch = {"r", "g", "b"};
        String[] amb = {"0.10f", "0.115f", "0.17f"}, fill = {"0.30f", "0.28f", "0.25f"}, flash = {"1.0f", "0.88f", "0.70f"};
        for (int c = 0; c < 3; c++) e.line(F_ + " " + n + ch[c] + " = " + ao + " * (" + amb[c] + " + " + ndf + " * " + fill[c] + ") + " + n + "ft * " + flash[c] + ";");
        for (int c = 0; c < 3; c++) e.decl(c, fin + " ? rf_max(" + n + ch[c] + ", 0.7f) : " + n + ch[c]);
        e.decl(3, "(" + n + "r + " + n + "g + " + n + "b) / 3.0f");
    }

    private static void emitLitColor(Emit e) {
        String F_ = e.ft();
        String n = "lc" + e.id + "_";
        String c = e.in(0);
        String[] ch = {"r", "g", "b"}, ext = {"rf_chR", "rf_chG", "rf_chB"};
        for (int k = 0; k < 3; k++) {
            String v = n + ch[k];
            e.line(F_ + " " + v + " = " + ext[k] + "(" + c + ") * " + e.in(1 + k) + ";");
            e.line(F_ + " " + v + "t = " + v + " <= 0.7f ? " + v + " : 0.7f + 0.3f * (1.0f - rf_exp(-(" + v + " - 0.7f) / 0.3f));");
        }
        e.decl(0, "rf_rgb(" + n + "rt, " + n + "gt, " + n + "bt)");
    }

    private static void emitRayDirection(Emit e) {
        double fov = Math.clamp(e.fparam("fov"), 1, 170);
        double t = Math.tan(Math.toRadians(fov) / 2);
        String a = "a" + e.id;
        e.line(e.ft() + " " + a + " = " + e.in(1) + " + rf_atan(" + e.in(0) + " * " + e.flit(t) + ");");
        e.decl(0, "rf_cos(" + a + ")");
        e.decl(1, "rf_sin(" + a + ")");
    }

    private static void emitTexture(Emit e) {
        int idx = switch (e.param("texture")) {
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
        String r = "t" + e.id + "_";
        e.line("int " + r + "w = pi[" + base + "], " + r + "h = pi[" + (base + 1) + "];");
        e.line("int " + e.out(0) + " = 0;");
        e.line("if (" + r + "w > 0 && " + r + "h > 0) {");
        e.line("\tint " + r + "x = (int)(rf_fract(" + e.in(0) + ") * (" + e.ft() + ")" + r + "w);");
        e.line("\tint " + r + "y = (int)(rf_fract(" + e.in(1) + ") * (" + e.ft() + ")" + r + "h);");
        e.line("\tif (" + r + "x >= " + r + "w) " + r + "x = " + r + "w - 1;");
        e.line("\tif (" + r + "y >= " + r + "h) " + r + "y = " + r + "h - 1;");
        e.line("\t" + e.out(0) + " = " + tex + "[" + r + "y * " + r + "w + " + r + "x];");
        e.line("}");
    }

    private static void emitRayGrid(Emit e) {
        int steps = e.iparam("maxSteps");
        String r = "r" + e.id + "_";
        String F_ = e.ft();
        String ox = e.in(0), oy = e.in(1), dx0 = e.in(2), dy0 = e.in(3);
        e.line(F_ + " " + r + "len = rf_sqrt(" + dx0 + " * " + dx0 + " + " + dy0 + " * " + dy0 + ");");
        e.line(F_ + " " + r + "dx = rf_div(" + dx0 + ", " + r + "len);");
        e.line(F_ + " " + r + "dy = rf_div(" + dy0 + ", " + r + "len);");
        e.line("int " + r + "mx = (int)rf_floor(" + ox + "), " + r + "my = (int)rf_floor(" + oy + ");");
        e.line(F_ + " " + r + "ddx = " + r + "dx == 0.0f ? 1e30f : rf_abs(rf_div(1.0f, " + r + "dx));");
        e.line(F_ + " " + r + "ddy = " + r + "dy == 0.0f ? 1e30f : rf_abs(rf_div(1.0f, " + r + "dy));");
        e.line("int " + r + "sx = " + r + "dx < 0.0f ? -1 : 1;");
        e.line("int " + r + "sy = " + r + "dy < 0.0f ? -1 : 1;");
        e.line(F_ + " " + r + "sdx = (" + r + "dx < 0.0f ? " + ox + " - (" + F_ + ")" + r + "mx : (" + F_ + ")" + r + "mx + 1.0f - " + ox + ") * " + r + "ddx;");
        e.line(F_ + " " + r + "sdy = (" + r + "dy < 0.0f ? " + oy + " - (" + F_ + ")" + r + "my : (" + F_ + ")" + r + "my + 1.0f - " + oy + ") * " + r + "ddy;");
        e.line(e.bt() + " " + e.out(0) + " = false;");
        e.line(F_ + " " + e.out(1) + " = 0.0f;");
        e.line("int " + e.out(2) + " = 0;");
        e.line("int " + e.out(3) + " = 0;");
        e.line(F_ + " " + e.out(4) + " = 0.0f;");
        e.line("for (int " + r + "s = 0; " + r + "s < " + steps + "; " + r + "s++) {");
        e.line("\t" + F_ + " " + r + "d;");
        e.line("\tint " + r + "sd;");
        e.line("\tif (" + r + "sdx < " + r + "sdy) { " + r + "d = " + r + "sdx; " + r + "sdx += " + r + "ddx; " + r + "mx += " + r + "sx; " + r + "sd = 0; }");
        e.line("\telse { " + r + "d = " + r + "sdy; " + r + "sdy += " + r + "ddy; " + r + "my += " + r + "sy; " + r + "sd = 1; }");
        e.line("\tint " + r + "c = rf_mapAt(map, pi, " + r + "mx, " + r + "my);");
        e.line("\tif (" + r + "c != 0) {");
        e.line("\t\t" + e.out(0) + " = true;");
        e.line("\t\t" + e.out(1) + " = " + r + "d;");
        e.line("\t\t" + e.out(2) + " = " + r + "sd;");
        e.line("\t\t" + e.out(3) + " = " + r + "c;");
        e.line("\t\t" + e.out(4) + " = rf_fract(" + r + "sd == 0 ? " + oy + " + " + r + "d * " + r + "dy : " + ox + " + " + r + "d * " + r + "dx);");
        e.line("\t\tbreak;");
        e.line("\t}");
        e.line("}");
    }
    private static void emitRayGridPortal(Emit e) {
        int steps = e.iparam("maxSteps");
        String F_ = e.ft(), B_ = e.bt(), I_ = "int";
        String r = "pg" + e.id + "_";
        String ox = r + "ox", oy = r + "oy", dx = r + "dx", dy = r + "dy";
        String mx = r + "mx", my = r + "my", sx = r + "sx", sy = r + "sy";
        String ddx = r + "ddx", ddy = r + "ddy", sdx = r + "sdx", sdy = r + "sdy";
        String total = r + "total", hops = r + "hops", reinit = r + "reinit", done = r + "done", found = r + "found";
        String hs = r + "hs", hc = r + "hc", ht = r + "ht", hu = r + "hu", b = r + "b";
        e.line(F_ + " " + ox + " = " + e.in(0) + ", " + oy + " = " + e.in(1) + ";");
        e.line(F_ + " " + dx + " = " + e.in(2) + ", " + dy + " = " + e.in(3) + ";");
        e.line(F_ + " " + r + "len = rf_sqrt(" + dx + " * " + dx + " + " + dy + " * " + dy + ");");
        e.line(dx + " = rf_div(" + dx + ", " + r + "len); " + dy + " = rf_div(" + dy + ", " + r + "len);");
        e.line(F_ + " " + total + " = " + e.flit(0) + ";");
        e.line("int " + hops + " = 0;");
        e.line(B_ + " " + reinit + " = true, " + done + " = false, " + found + " = false;");
        e.line("int " + hs + " = 0, " + hc + " = 0;");
        e.line(F_ + " " + ht + " = " + e.flit(0) + ", " + hu + " = " + e.flit(0) + ";");
        e.line("int " + mx + " = 0, " + my + " = 0, " + sx + " = 1, " + sy + " = 1;");
        e.line(F_ + " " + ddx + " = 0, " + ddy + " = 0, " + sdx + " = 0, " + sdy + " = 0;");
        e.line("for (int " + r + "s = 0; " + r + "s < " + steps + " && !" + done + "; " + r + "s++) {");
        e.line("\tif (" + reinit + ") {");
        e.line("\t\t" + mx + " = (int)rf_floor(" + ox + "); " + my + " = (int)rf_floor(" + oy + ");");
        e.line("\t\t" + ddx + " = " + dx + " == 0.0f ? " + e.flit(1e30) + " : rf_abs(rf_div(1.0f, " + dx + "));");
        e.line("\t\t" + ddy + " = " + dy + " == 0.0f ? " + e.flit(1e30) + " : rf_abs(rf_div(1.0f, " + dy + "));");
        e.line("\t\t" + sx + " = " + dx + " < 0.0f ? -1 : 1;");
        e.line("\t\t" + sy + " = " + dy + " < 0.0f ? -1 : 1;");
        e.line("\t\t" + sdx + " = (" + dx + " < 0.0f ? " + ox + " - (" + F_ + ")" + mx + " : (" + F_ + ")" + mx + " + 1.0f - " + ox + ") * " + ddx + ";");
        e.line("\t\t" + sdy + " = (" + dy + " < 0.0f ? " + oy + " - (" + F_ + ")" + my + " : (" + F_ + ")" + my + " + 1.0f - " + oy + ") * " + ddy + ";");
        e.line("\t\t" + reinit + " = false;");
        e.line("\t}");
        e.line("\t" + F_ + " " + r + "t; int " + r + "side;");
        e.line("\tif (" + sdx + " < " + sdy + ") { " + r + "t = " + sdx + "; " + sdx + " += " + ddx + "; " + mx + " += " + sx + "; " + r + "side = 0; }");
        e.line("\telse { " + r + "t = " + sdy + "; " + sdy + " += " + ddy + "; " + my + " += " + sy + "; " + r + "side = 1; }");
        e.line("\tint " + r + "c = rf_mapAt(map, pi, " + mx + ", " + my + ");");
        e.line("\tint " + b + " = 14 + 6 * (" + my + " * pi[3] + " + mx + ");");
        e.line("\tif (" + r + "c == -1) { " + done + " = true; }");
        e.line("\telse if (" + r + "c == 5 && " + hops + " < 8 && (pi[" + b + "] != 0 || pi[" + b + " + 1] != 0)"
                + " && " + dx + " * (" + F_ + ")pi[" + b + "] + " + dy + " * (" + F_ + ")pi[" + b + " + 1] > 0.0f) {");
        e.line("\t\t" + F_ + " hx = " + ox + " + " + dx + " * " + r + "t, hy = " + oy + " + " + dy + " * " + r + "t;");
        e.line("\t\t" + total + " += " + r + "t;");
        e.line("\t\t" + F_ + " nax = (" + F_ + ")pi[" + b + "], nay = (" + F_ + ")pi[" + b + " + 1];");
        e.line("\t\t" + F_ + " lx = (" + F_ + ")pi[" + b + " + 2], ly = (" + F_ + ")pi[" + b + " + 3];");
        e.line("\t\t" + F_ + " mbx = (" + F_ + ")pi[" + b + " + 4], mby = (" + F_ + ")pi[" + b + " + 5];");
        e.line("\t\t" + F_ + " u = (hx - (" + mx + " + rf_max(nax, 0.0f) + rf_max(nay, 0.0f))) * (-nay)"
                + " + (hy - (" + my + " + rf_max(nay, 0.0f) + rf_max(-nax, 0.0f))) * nax;");
        e.line("\t\tu = rf_clamp(u, 5e-4f, 1.0f - 5e-4f);");
        e.line("\t\t" + F_ + " cosT = nax * mbx + nay * mby, sinT = nax * mby - nay * mbx;");
        e.line("\t\t" + F_ + " ndx = " + dx + " * cosT - " + dy + " * sinT, ndy = " + dx + " * sinT + " + dy + " * cosT;");
        e.line("\t\t" + F_ + " ex = (lx + rf_max(mbx, 0.0f) + rf_max(mby, 0.0f)) + u * (-mby);");
        e.line("\t\t" + F_ + " ey = (ly + rf_max(mby, 0.0f) + rf_max(-mbx, 0.0f)) + u * mbx;");
        e.line("\t\t" + ox + " = ex + ndx * 1e-4f; " + oy + " = ey + ndy * 1e-4f;");
        e.line("\t\t" + dx + " = ndx; " + dy + " = ndy;");
        e.line("\t\t" + hops + "++;");
        e.line("\t\t" + reinit + " = true;");
        e.line("\t}");
        e.line("\telse if (" + r + "c != 0 && " + r + "c != 5) {");
        e.line("\t\t" + found + " = true; " + hs + " = " + r + "side; " + hc + " = " + r + "c;");
        e.line("\t\t" + ht + " = " + total + " + " + r + "t;");
        e.line("\t\t" + hu + " = rf_fract(" + r + "side == 0 ? " + oy + " + " + r + "t * " + dy + " : " + ox + " + " + r + "t * " + dx + ");");
        e.line("\t\t" + done + " = true;");
        e.line("\t}");
        e.line("}");
        e.line(e.typeName(e.outType(0)) + " " + e.out(0) + " = " + found + ";");
        e.line(e.typeName(e.outType(1)) + " " + e.out(1) + " = " + found + " ? " + ht + " : " + e.flit(0) + ";");
        e.line(I_ + " " + e.out(2) + " = " + hs + ";");
        e.line(I_ + " " + e.out(3) + " = " + hc + ";");
        e.line(e.typeName(e.outType(4)) + " " + e.out(4) + " = " + hu + ";");
    }
}