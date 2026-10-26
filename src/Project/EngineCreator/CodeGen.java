package Project.EngineCreator;

import java.util.*;

/** Turns a validated project into a RenderEngine class (Java) and, optionally, OpenCL C kernels.*/
public final class CodeGen {
    private CodeGen() {}

    static String className(EcProject p) { return "Ext_" + p.name; }
    static String kernelName(EcProject p, int pass) { return p.name + "_p" + pass; }

    private static final int INLINE_ALWAYS = 24;
    private static final int INLINE_ONCE = 160;

    static List<String> body(EcProject p, int pass, boolean java) {
        List<String> lines = new ArrayList<>();
        NodeInst out = p.output(pass);
        List<Integer> order = Graph.order(p, Objects.requireNonNull(out).id);
        Map<String, Integer> reads = new HashMap<>();
        for (int id : Objects.requireNonNull(order)) {
            NodeKind k = Nodes.get(Objects.requireNonNull(p.node(id)).kind);
            for (Port port : k.inputs()) {
                Link l = p.incoming(id, port.name());
                if (l != null) reads.merge(varOf(l.from(), l.fromPort()), 1, Integer::sum);
            }
        }
        Map<String, String> inlined = new HashMap<>();
        for (int id : order) {
            NodeInst n = p.node(id);
            NodeKind k = Nodes.get(Objects.requireNonNull(n).kind);
            String[] in = new String[k.inputs().size()];
            for (int i = 0; i < in.length; i++) {
                Link l = p.incoming(id, k.inputs().get(i).name());
                if (l == null) in[i] = "0";
                else {
                    String v = varOf(l.from(), l.fromPort());
                    in[i] = inlined.getOrDefault(v, v);
                }
            }
            String[] outs = new String[k.outputs().size()];
            DataType[] types = new DataType[outs.length];
            for (int i = 0; i < outs.length; i++) {
                String port = k.outputs().get(i).name();
                outs[i] = varOf(id, port);
                types[i] = Graph.outType(p, id, port, 0);
            }
            List<String> tmp = new ArrayList<>();
            Emit e = new Emit(java, id, in, outs, types, n.params, tmp);
            k.emitter().emit(e);
            if (outs.length == 1 && tmp.size() == 1) {
                String name = outs[0];
                String head = e.typeName(types[0]) + " " + name + " = ";
                String line = tmp.getFirst();
                if (line.startsWith(head) && line.endsWith(";")) {
                    String expr = line.substring(head.length(), line.length() - 1);
                    int n_ = reads.getOrDefault(name, 0);
                    if (n_ == 0) continue;
                    if (expr.length() <= INLINE_ALWAYS || (n_ == 1 && expr.length() <= INLINE_ONCE)) {
                        inlined.put(name, "(" + expr + ")");
                        continue;
                    }
                }
            }
            lines.addAll(tmp);
        }
        return lines;
    }

    static String varOf(int node, String port) { return "v" + node + "_" + port; }

    private static final String SHADE_PARAMS_JAVA =
            "int px, int py, int[] map, int[] pi, double[] pf, int[] prev, int[] texWall, int[] texFloor, int[] texCeil, int[] texFinish";
    private static final String SHADE_PARAMS_C =
            "int px, int py, global const int* map, global const int* pi, global const float* pf, global const int* prev, " +
                    "global const int* texWall, global const int* texFloor, global const int* texCeil, global const int* texFinish";

    static String javaSource(EcProject p) {
        int passes = p.passCount();
        String cls = className(p);
        StringBuilder s = new StringBuilder();
        s.append("// Made By Render Engine Creator (RCJMS) Automatically.\n");
        s.append("// DO NOT Edit Code: After Next Save Changes Will Be Lost.\n");
        s.append("import Project.Game.RenderEngine;\n");
        s.append("import Project.Game.RenderFrame;\n");
        s.append("import Project.Helpers.MazeGenerator;\n");
        s.append("import Project.Helpers.PortalData;\n");
        if (p.openCl) s.append("import Project.OpenCl.GpuProgram;\nimport Project.OpenCl.GpuRuntime;\n");
        s.append("\npublic final class ").append(cls).append(" implements RenderEngine {\n");
        s.append("\tprivate static final String ID = ").append(Json.quote(p.name)).append(";\n");
        s.append("\tprivate static final String TITLE = ").append(Json.quote(p.title)).append(";\n");
        s.append("\tprivate static final int PASSES = ").append(passes).append(";\n");
        s.append("\tprivate static final boolean HYPERBOLIC = ").append(p.usesHyperbolic()).append(";\n");
        s.append("\tprivate static final int[] PASS_GEOM = {");
        for (int k = 0; k < passes; k++) {
            String g = p.geometry(k);
            s.append(k > 0 ? ", " : "").append(EcProject.EUCLID.equals(g) ? 1 : EcProject.HYPER.equals(g) ? 2 : 0);
        }
        s.append("};\n");
        s.append("\tprivate static final long T0 = System.nanoTime();\n");
        if (p.openCl) {
            s.append("\tprivate static final class Kernel {\n");
            s.append("\t\tstatic final String SOURCE = Project.OpenCl.KernelSource.load(").append(cls).append(".class);\n");
            s.append("\t}\n");
            s.append("\tprivate static final String[] KERNEL_NAMES = {");
            for (int k = 0; k < passes; k++) s.append(k > 0 ? ", " : "").append(Json.quote(kernelName(p, k)));
            s.append("};\n");
        }
        s.append("\n");
        s.append("\t@Override public String id() { return ID; }\n");
        s.append("\t@Override public String displayName() { return TITLE; }\n");
        s.append("\t@Override public int order() { return 200; }\n");
        s.append("\t@Override public boolean hasHyperbolicGeometry() { return HYPERBOLIC; }\n");
        s.append("\t@Override public boolean hasOpenCL() { return ").append(p.openCl).append("; }\n\n");

        s.append("\t@Override public void render(RenderFrame f) {\n");
        s.append("\t\tfinal int W = f.renderWidth, H = f.renderHeight;\n");
        s.append("\t\tif (W <= 0 || H <= 0 || f.renderPixels == null) return;\n");
        s.append("\t\tfinal int mapH = f.map == null ? 0 : f.map.length;\n");
        s.append("\t\tfinal int mapW = mapH == 0 ? 0 : f.map[0].length;\n");
        s.append("\t\tfinal int[] map = new int[mapW * mapH];\n");
        s.append("\t\tfor (int y = 0; y < mapH; y++) System.arraycopy(f.map[y], 0, map, y * mapW, mapW);\n");
        s.append("\t\tfinal int[] texWall = f.wallTexture.pixels(), texFloor = f.floorTexture.pixels();\n");
        s.append("\t\tfinal int[] texCeil = f.ceilingTexture.pixels(), texFinish = f.finishTexture.pixels();\n");
        s.append("\t\tfinal int[] pi0 = { W, H, f.quality, mapW, mapH,\n");
        s.append("\t\t\t\tf.wallTexture.width(), f.wallTexture.height(),\n");
        s.append("\t\t\t\tf.floorTexture.width(), f.floorTexture.height(),\n");
        s.append("\t\t\t\tf.ceilingTexture.width(), f.ceilingTexture.height(),\n");
        s.append("\t\t\t\tf.finishTexture.width(), f.finishTexture.height(),\n");
        s.append("\t\t\t\tf.geometry == MazeGenerator.GeometryMode.WRONG ? 1 : 0 };\n");
        // pi[14 + 6*cell ..] holds the portal links (see NodeLibrary.emitRayGridPortal) and stop deleting my comments
        s.append("\t\tfinal int[] pi = java.util.Arrays.copyOf(pi0, 14 + 6 * mapW * mapH);\n");
        s.append("\t\tif (f.portals != null) for (PortalData.Portal p : f.portals.values()) {\n");
        s.append("\t\t\tif (p.x < 0 || p.y < 0 || p.x >= mapW || p.y >= mapH) continue;\n");
        s.append("\t\t\tint b = 14 + 6 * (p.y * mapW + p.x);\n");
        s.append("\t\t\tpi[b] = p.dirX; pi[b + 1] = p.dirY; pi[b + 2] = p.linkedX; pi[b + 3] = p.linkedY;\n");
        s.append("\t\t\tpi[b + 4] = p.linkedDirX; pi[b + 5] = p.linkedDirY;\n");
        s.append("\t\t}\n");
        s.append("\t\tfinal double[] pf = { f.cameraAngle, f.cameraHeight, f.cameraPitch, f.playerX, f.playerY,\n");
        s.append("\t\t\t\tf.curvature, (System.nanoTime() - T0) / 1e9 };\n");
        s.append("\t\tfinal boolean hyp = f.geometry == MazeGenerator.GeometryMode.WRONG;\n");
        s.append("\t\tint active = 0;\n");
        s.append("\t\tfor (int g : PASS_GEOM) if (g == 0 || g == (hyp ? 2 : 1)) active++;\n");
        s.append("\t\tint[] prev = new int[W * H];\n");
        s.append("\t\tfor (int k = 0; k < PASSES; k++) {\n");
        s.append("\t\t\tint g = PASS_GEOM[k];\n");
        s.append("\t\t\tif (active > 0 && g != 0 && g != (hyp ? 2 : 1)) continue;\n");
        s.append("\t\t\tfinal int pass = k;\n");
        s.append("\t\t\tfinal int[] from = prev;\n");
        s.append("\t\t\tfinal int[] out = new int[W * H];\n");
        if (p.openCl)
            s.append("\t\t\tif (!Gpu.pass(pass, W, H, map, pi, pf, from, texWall, texFloor, texCeil, texFinish, out)) {\n");
        else
            s.append("\t\t\t{\n");
        s.append("\t\t\t\tjava.util.function.IntConsumer row = py -> {\n");
        s.append("\t\t\t\t\tfor (int px = 0; px < W; px++)\n");
        s.append("\t\t\t\t\t\tout[py * W + px] = shade(pass, px, py, map, pi, pf, from, texWall, texFloor, texCeil, texFinish);\n");
        s.append("\t\t\t\t};\n");
        s.append("\t\t\t\tif (f.renderWorkers != null) f.renderWorkers.execute(0, H, row);\n");
        s.append("\t\t\t\telse for (int py = 0; py < H; py++) row.accept(py);\n");
        s.append("\t\t\t}\n");
        s.append("\t\t\tprev = out;\n");
        s.append("\t\t}\n");
        s.append("\t\tSystem.arraycopy(prev, 0, f.renderPixels, 0, W * H);\n");
        s.append("\t\tif (f.bufferedImage != null) {\n");
        s.append("\t\tint[] shown = ((java.awt.image.DataBufferInt) f.bufferedImage.getRaster().getDataBuffer()).getData();\n");
        s.append("\t\tif (W == f.bufferedImage.getWidth() && H == f.bufferedImage.getHeight()) System.arraycopy(f.renderPixels, 0, shown, 0, shown.length);\n");
        s.append("\t\telse {\n");
        s.append("\t\t\tjava.awt.Graphics2D g = f.bufferedImage.createGraphics();\n");
        s.append("\t\t\tg.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);\n");
        s.append("\t\t\tg.drawImage(f.renderImage, 0, 0, f.bufferedImage.getWidth(), f.bufferedImage.getHeight(), null);\n");
        s.append("\t\t\tg.dispose();\n");
        s.append("\t\t}\n");
        s.append("\t\t}\n");
        s.append("\t}\n\n");

        s.append("\tstatic int shade(int pass, ").append(SHADE_PARAMS_JAVA).append(") {\n");
        s.append("\t\tswitch (pass) {\n");
        for (int k = 0; k < passes; k++)
            s.append("\t\t\tcase ").append(k).append(": return shade_").append(k).append("(px, py, map, pi, pf, prev, texWall, texFloor, texCeil, texFinish);\n");
        s.append("\t\t\tdefault: return 0;\n\t\t}\n\t}\n\n");

        for (int k = 0; k < passes; k++) {
            s.append("\tstatic int shade_").append(k).append("(").append(SHADE_PARAMS_JAVA).append(") {\n");
            for (String line : body(p, k, true)) s.append("\t\t").append(line).append("\n");
            s.append("\t}\n\n");
        }

        for (String line : JAVA_PRELUDE) s.append("\t").append(line).append("\n");
        s.append("\n");
        if (p.openCl) appendJavaGpu(s);
        s.append("}\n");
        return s.toString();
    }

    private static void appendJavaGpu(StringBuilder s) {
        s.append("\tprivate static final class Gpu {\n");
        s.append("\t\tprivate static final GpuProgram[] programs = new GpuProgram[PASSES];\n");
        s.append("\t\tprivate static GpuProgram.Buffer bOut, bPrev, bMap, bPi, bPf, bWall, bFloor, bCeil, bFinish;\n");
        s.append("\t\tprivate static boolean disabled;\n\n");
        s.append("\t\tstatic boolean pass(int k, int W, int H, int[] map, int[] pi, double[] pf, int[] prev,\n");
        s.append("\t\t\t\t\t\t\tint[] wall, int[] floor, int[] ceil, int[] finish, int[] out) {\n");
        s.append("\t\t\tsynchronized (GpuRuntime.LOCK) {\n");
        s.append("\t\t\t\tif (disabled || GpuRuntime.session() == null) return false;\n");
        s.append("\t\t\t\ttry {\n");
        s.append("\t\t\t\t\tif (programs[k] == null) programs[k] = new GpuProgram(Kernel.SOURCE, KERNEL_NAMES[k]);\n");
        s.append("\t\t\t\t\tGpuProgram g = programs[k];\n");
        s.append("\t\t\t\t\tbOut = ensure(g, bOut, 4L * W * H);\n");
        s.append("\t\t\t\t\tbPrev = ensure(g, bPrev, 4L * W * H);\n");
        s.append("\t\t\t\t\tbMap = ensure(g, bMap, 4L * map.length);\n");
        s.append("\t\t\t\t\tbPi = ensure(g, bPi, 4L * pi.length);\n");
        s.append("\t\t\t\t\tbPf = ensure(g, bPf, 4L * pf.length);\n");
        s.append("\t\t\t\t\tbWall = ensure(g, bWall, 4L * wall.length);\n");
        s.append("\t\t\t\t\tbFloor = ensure(g, bFloor, 4L * floor.length);\n");
        s.append("\t\t\t\t\tbCeil = ensure(g, bCeil, 4L * ceil.length);\n");
        s.append("\t\t\t\t\tbFinish = ensure(g, bFinish, 4L * finish.length);\n");
        s.append("\t\t\t\t\tg.write(bPrev, prev, prev.length);\n");
        s.append("\t\t\t\t\tg.write(bMap, map, map.length);\n");
        s.append("\t\t\t\t\tg.write(bPi, pi, pi.length);\n");
        s.append("\t\t\t\t\tg.write(bPf, pf, pf.length);\n");
        s.append("\t\t\t\t\tg.write(bWall, wall, wall.length);\n");
        s.append("\t\t\t\t\tg.write(bFloor, floor, floor.length);\n");
        s.append("\t\t\t\t\tg.write(bCeil, ceil, ceil.length);\n");
        s.append("\t\t\t\t\tg.write(bFinish, finish, finish.length);\n");
        s.append("\t\t\t\t\tg.run(W, H, bOut, bPrev, bMap, bPi, bPf, bWall, bFloor, bCeil, bFinish);\n");
        s.append("\t\t\t\t\tg.read(bOut, out, W * H);\n");
        s.append("\t\t\t\t\treturn true;\n");
        s.append("\t\t\t\t} catch (RuntimeException | Error e) {\n");
        s.append("\t\t\t\t\tdisabled = true;\n");
        s.append("\t\t\t\t\tGpuRuntime.reportFailure(ID, String.valueOf(e));\n");
        s.append("\t\t\t\t\treturn false;\n");
        s.append("\t\t\t\t}\n");
        s.append("\t\t\t}\n");
        s.append("\t\t}\n\n");
        s.append("\t\tprivate static GpuProgram.Buffer ensure(GpuProgram g, GpuProgram.Buffer current, long bytes) {\n");
        s.append("\t\t\tlong size = Math.max(4L, bytes);\n");
        s.append("\t\t\tif (current != null && current.bytes() == size) return current;\n");
        s.append("\t\t\tif (current != null) g.release(current);\n");
        s.append("\t\t\treturn g.buffer(size);\n");
        s.append("\t\t}\n");
        s.append("\t}\n");
    }

    static String clSource(EcProject p) {
        int passes = p.passCount();
        StringBuilder s = new StringBuilder();
        s.append("// OpenCL-kernels created by Render Engine Creator automatically.\n");
        for (String line : C_PRELUDE) s.append(line).append("\n");
        s.append("\n");
        for (int k = 0; k < passes; k++) {
            s.append("int shade_").append(k).append("(").append(SHADE_PARAMS_C).append(") {\n");
            for (String line : body(p, k, false)) s.append("\t").append(line).append("\n");
            s.append("}\n\n");
        }
        for (int k = 0; k < passes; k++) {
            s.append("__kernel void ").append(kernelName(p, k)).append("(global int* out, global const int* prev,\n");
            s.append("\t\tglobal const int* map, global const int* pi, global const float* pf,\n");
            s.append("\t\tglobal const int* texWall, global const int* texFloor, global const int* texCeil,\n");
            s.append("\t\tglobal const int* texFinish) {\n");
            s.append("\tint px = (int)get_global_id(0);\n");
            s.append("\tint py = (int)get_global_id(1);\n");
            s.append("\tif (px >= pi[0] || py >= pi[1]) return;\n");
            s.append("\tout[py * pi[0] + px] = shade_").append(k).append("(px, py, map, pi, pf, prev, texWall, texFloor, texCeil, texFinish);\n");
            s.append("}\n\n");
        }
        return s.toString();
    }

    private static final List<String> JAVA_PRELUDE = List.of(
            "static double rf_div(double a, double b) { return b == 0.0 ? 0.0 : a / b; }",
            "static double rf_sin(double x) { return Math.sin(x); }",
            "static double rf_cos(double x) { return Math.cos(x); }",
            "static double rf_tan(double x) { return Math.tan(x); }",
            "static double rf_atan(double x) { return Math.atan(x); }",
            "static double rf_sqrt(double x) { return Math.sqrt(x > 0.0 ? x : 0.0); }",
            "static double rf_abs(double x) { return Math.abs(x); }",
            "static double rf_floor(double x) { return Math.floor(x); }",
            "static double rf_fract(double x) { return x - Math.floor(x); }",
            "static double rf_exp(double x) { return Math.exp(x); }",
            "static double rf_asinh(double x) { double a = Math.abs(x); return Math.copySign(Math.log(a + Math.sqrt(a * a + 1.0)), x); }",
            "static double rf_pow(double a, double b) { return Math.pow(a, b); }",
            "static double rf_min(double a, double b) { return a < b ? a : b; }",
            "static double rf_max(double a, double b) { return a > b ? a : b; }",
            "static double rf_clamp(double x, double lo, double hi) { return x < lo ? lo : (x > hi ? hi : x); }",
            "static double rf_mix(double a, double b, double t) { return a + (b - a) * t; }",
            "static double rf_hash(double x, double y, double s) { double v = Math.sin(x * 12.9898 + y * 78.233 + s * 37.719) * 43758.5453; return v - Math.floor(v); }",
            "static double rf_hypd(double t, double k) { double r = Math.max(k, 0.0001); return r * Math.sinh(t / r); }",
            "static double rf_chR(int c) { return ((c >> 16) & 255) / 255.0; }",
            "static double rf_chG(int c) { return ((c >> 8) & 255) / 255.0; }",
            "static double rf_chB(int c) { return (c & 255) / 255.0; }",
            "static int rf_q(double v) { return (int) (rf_clamp(v, 0.0, 1.0) * 255.0 + 0.5); }",
            "static int rf_rgb(double r, double g, double b) { return 0xFF000000 | (rf_q(r) << 16) | (rf_q(g) << 8) | rf_q(b); }",
            "static int rf_scale(int ch, double k) { return (int) (rf_clamp(ch * k, 0.0, 255.0) + 0.5); }",
            "static int rf_lerpc(int a, int b, double t) { return (int) (a + (b - a) * t + 0.5); }",
            "static int rf_mixColor(int a, int b, double t) {",
            "\tdouble k = rf_clamp(t, 0.0, 1.0);",
            "\tint r = rf_lerpc((a >> 16) & 255, (b >> 16) & 255, k);",
            "\tint g = rf_lerpc((a >> 8) & 255, (b >> 8) & 255, k);",
            "\tint bl = rf_lerpc(a & 255, b & 255, k);",
            "\treturn 0xFF000000 | (r << 16) | (g << 8) | bl;",
            "}",
            "static int rf_brightness(int c, double k) {",
            "\treturn 0xFF000000 | (rf_scale((c >> 16) & 255, k) << 16) | (rf_scale((c >> 8) & 255, k) << 8) | rf_scale(c & 255, k);",
            "}",
            "static int rf_fog(int c, int fog, double dist, double density) { return rf_mixColor(c, fog, 1.0 - rf_exp(-density * dist)); }",
            "static int rf_post(int ch, double L) { return rf_q(rf_floor(ch / 255.0 * L) / (L - 1.0)); }",
            "static int rf_posterize(int c, double levels) {",
            "\tdouble L = rf_max(levels, 2.0);",
            "\treturn 0xFF000000 | (rf_post((c >> 16) & 255, L) << 16) | (rf_post((c >> 8) & 255, L) << 8) | rf_post(c & 255, L);",
            "}",
            "static int rf_mapAt(int[] map, int[] pi, int x, int y) {",
            "\tint w = pi[3], h = pi[4];",
            "\tif (x < 0 || y < 0 || x >= w || y >= h) return -1;",
            "\treturn map[y * w + x];",
            "}",
            "static int rf_prev(int[] prev, int[] pi, int x, int y) {",
            "\tint w = pi[0], h = pi[1];",
            "\tif (w <= 0 || h <= 0) return 0;",
            "\tint cx = Math.max(0, Math.min(w - 1, x)), cy = Math.max(0, Math.min(h - 1, y));",
            "\treturn prev[cy * w + cx];",
            "}"
    );

    private static final List<String> C_PRELUDE = List.of(
            "inline float rf_div(float a, float b) { return b == 0.0f ? 0.0f : a / b; }",
            "inline float rf_sin(float x) { return sin(x); }",
            "inline float rf_cos(float x) { return cos(x); }",
            "inline float rf_tan(float x) { return tan(x); }",
            "inline float rf_atan(float x) { return atan(x); }",
            "inline float rf_sqrt(float x) { return sqrt(x > 0.0f ? x : 0.0f); }",
            "inline float rf_abs(float x) { return fabs(x); }",
            "inline float rf_floor(float x) { return floor(x); }",
            "inline float rf_fract(float x) { return x - floor(x); }",
            "inline float rf_exp(float x) { return exp(x); }",
            "inline float rf_asinh(float x) { float a = fabs(x); return copysign(log(a + sqrt(a * a + 1.0f)), x); }",
            "inline float rf_pow(float a, float b) { return pow(a, b); }",
            "inline float rf_min(float a, float b) { return a < b ? a : b; }",
            "inline float rf_max(float a, float b) { return a > b ? a : b; }",
            "inline float rf_clamp(float x, float lo, float hi) { return x < lo ? lo : (x > hi ? hi : x); }",
            "inline float rf_mix(float a, float b, float t) { return a + (b - a) * t; }",
            "inline float rf_hash(float x, float y, float s) { float v = sin(x * 12.9898f + y * 78.233f + s * 37.719f) * 43758.5453f; return v - floor(v); }",
            "inline float rf_hypd(float t, float k) { float r = fmax(k, 0.0001f); return r * sinh(t / r); }",
            "inline float rf_chR(int c) { return (float)((c >> 16) & 255) / 255.0f; }",
            "inline float rf_chG(int c) { return (float)((c >> 8) & 255) / 255.0f; }",
            "inline float rf_chB(int c) { return (float)(c & 255) / 255.0f; }",
            "inline int rf_q(float v) { return (int)(rf_clamp(v, 0.0f, 1.0f) * 255.0f + 0.5f); }",
            "inline int rf_rgb(float r, float g, float b) { return 0xFF000000 | (rf_q(r) << 16) | (rf_q(g) << 8) | rf_q(b); }",
            "inline int rf_scale(int ch, float k) { return (int)(rf_clamp((float)ch * k, 0.0f, 255.0f) + 0.5f); }",
            "inline int rf_lerpc(int a, int b, float t) { return (int)((float)a + ((float)b - (float)a) * t + 0.5f); }",
            "inline int rf_mixColor(int a, int b, float t) {",
            "\tfloat k = rf_clamp(t, 0.0f, 1.0f);",
            "\tint r = rf_lerpc((a >> 16) & 255, (b >> 16) & 255, k);",
            "\tint g = rf_lerpc((a >> 8) & 255, (b >> 8) & 255, k);",
            "\tint bl = rf_lerpc(a & 255, b & 255, k);",
            "\treturn 0xFF000000 | (r << 16) | (g << 8) | bl;",
            "}",
            "inline int rf_brightness(int c, float k) {",
            "\treturn 0xFF000000 | (rf_scale((c >> 16) & 255, k) << 16) | (rf_scale((c >> 8) & 255, k) << 8) | rf_scale(c & 255, k);",
            "}",
            "inline int rf_fog(int c, int fog, float dist, float density) { return rf_mixColor(c, fog, 1.0f - rf_exp(-density * dist)); }",
            "inline int rf_post(int ch, float L) { return rf_q(rf_floor((float)ch / 255.0f * L) / (L - 1.0f)); }",
            "inline int rf_posterize(int c, float levels) {",
            "\tfloat L = rf_max(levels, 2.0f);",
            "\treturn 0xFF000000 | (rf_post((c >> 16) & 255, L) << 16) | (rf_post((c >> 8) & 255, L) << 8) | rf_post(c & 255, L);",
            "}",
            "inline int rf_mapAt(global const int* map, global const int* pi, int x, int y) {",
            "\tint w = pi[3], h = pi[4];",
            "\tif (x < 0 || y < 0 || x >= w || y >= h) return -1;",
            "\treturn map[y * w + x];",
            "}",
            "inline int rf_prev(global const int* prev, global const int* pi, int x, int y) {",
            "\tint w = pi[0], h = pi[1];",
            "\tif (w <= 0 || h <= 0) return 0;",
            "\tint cx = max(0, min(w - 1, x)), cy = max(0, min(h - 1, y));",
            "\treturn prev[cy * w + cx];",
            "}"
    );
}