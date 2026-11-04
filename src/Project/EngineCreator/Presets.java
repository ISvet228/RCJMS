package Project.EngineCreator;
import Project.CreatorKit.*;

import java.util.*;

public final class Presets {
    //region Variables
    static final List<Preset> ALL = List.of(
            new Preset("walls", false, false),
            new Preset("hypwalls", false, false),
            new Preset("sky", false, false),
            new Preset("posterize", true, false),
            new Preset("vignette", true, false),
            new Preset("grain", true, false),
            new Preset("blur", false, true));
    //endregion

    //region Constructors
    private Presets() {}
    //endregion

    //region Applying Presets
    static CkProject newProject() {
        CkProject ckProject = new CkProject();
        ckProject.title = L.t("ec.ui.defaultTitle");
        ckProject.add("output", 900, 120, 0);
        return ckProject;
    }
    static int apply(CkProject ckProject, String id, int currentPass) {
        return switch (id) {
            case "walls" -> { walls(ckProject, currentPass, false); yield currentPass; }
            case "hypwalls" -> {
                boolean empty = ckProject.passCount() == 1 && ckProject.nodes.size() <= 1;
                int pass = empty ? currentPass : ckProject.passCount();
                walls(ckProject, pass, true);
                ckProject.setGeometry(pass, CkProject.HYPER);
                yield pass;
            }
            case "sky" -> { sky(ckProject, currentPass); yield currentPass; }
            case "posterize" -> { filter(ckProject, currentPass); yield currentPass; }
            case "vignette" -> { vignette(ckProject, currentPass); yield currentPass; }
            case "grain" -> { grain(ckProject, currentPass); yield currentPass; }
            case "blur" -> { blur(ckProject); yield ckProject.passCount() - 1; }
            default -> throw new IllegalStateException(L.f("ec.pr.unknown", id));
        };
    }
    private static int ensureOutput(CkProject ckProject, int pass) {
        NodeInst out = ckProject.output(pass);
        if (out != null) return out.id;
        return ckProject.add("output", 900, 120, pass).id;
    }
    record Preset(String id, boolean filter, boolean newPass) {
    String title() { return L.t("ec.pr." + id); }
 }
    //endregion

    //region Scene Presets
    private static void walls(CkProject ckProject, int pass, boolean hyperbolic) {
        Builder builder = new Builder(ckProject, pass);
        int out = ensureOutput(ckProject, pass);
        double fov = hyperbolic ? 140 : 60;

        int sx = builder.n("screenX"), ca = builder.n("cameraAngle");
        int rd = builder.n("rayDirection", "fov", String.valueOf((int) fov));
        builder.link(sx, "out", rd, "screenX");
        builder.link(ca, "out", rd, "cameraAngle");

        int rg = builder.n("rayGrid", "maxSteps", "64");
        builder.link(builder.n("playerX"), "out", rg, "originX");
        builder.link(builder.n("playerY"), "out", rg, "originY");
        builder.link(rd, "dirX", rg, "dirX");
        builder.link(rd, "dirY", rg, "dirY");

        int dist = rg;
        if (hyperbolic) {
            int hd = builder.n("hypDistance");
            builder.link(rg, "distance", hd, "t");
            builder.link(builder.n("curvature"), "out", hd, "k");
            dist = hd;
        }

        int sy = builder.n("screenY");
        int dmax = builder.n("max");
        builder.link(dist, hyperbolic ? "out" : "distance", dmax, "a");
        builder.link(builder.n("constFloat", "value", "0.05"), "out", dmax, "b");
        int div = builder.n("div");
        builder.link(sy, "out", div, "a");
        builder.link(dmax, "out", div, "b");
        int half = builder.n("constFloat", "value", "0.5");
        int mul = builder.n("mul");
        builder.link(div, "out", mul, "a");
        builder.link(half, "out", mul, "b");
        int add = builder.n("add");
        builder.link(mul, "out", add, "a");
        builder.link(half, "out", add, "b");
        int fr = builder.n("fract");
        builder.link(add, "out", fr, "a");

        int tex = builder.n("sampleTexture", "texture", "wall");
        builder.link(rg, "hitU", tex, "u");
        builder.link(fr, "out", tex, "v");

        int fog = builder.n("fog");
        builder.link(tex, "out", fog, "c");
        builder.link(builder.n("constColor", "color", "#1B2A41"), "out", fog, "fogColor");
        builder.link(dist, hyperbolic ? "out" : "distance", fog, "distance");
        builder.link(builder.n("constFloat", "value", "0.12"), "out", fog, "density");

        int sel = builder.n("select");
        builder.link(rg, "hit", sel, "cond");
        builder.link(fog, "out", sel, "a");
        builder.link(builder.n("constColor", "color", "#05070D"), "out", sel, "b");
        builder.link(sel, "out", out, "color");
    }
    private static void sky(CkProject ckProject, int pass) {
        Builder builder = new Builder(ckProject, pass);
        int out = ensureOutput(ckProject, pass);
        int sy = builder.n("screenY");
        int mul = builder.n("mul");
        builder.link(sy, "out", mul, "a");
        builder.link(builder.n("constFloat", "value", "0.5"), "out", mul, "b");
        int add = builder.n("add");
        builder.link(mul, "out", add, "a");
        builder.link(builder.n("constFloat", "value", "0.5"), "out", add, "b");
        int mix = builder.n("mixColor");
        builder.link(builder.n("constColor", "color", "#1E3A8A"), "out", mix, "a");
        builder.link(builder.n("constColor", "color", "#F59E0B"), "out", mix, "b");
        builder.link(add, "out", mix, "t");
        builder.link(mix, "out", out, "color");
    }
    private static Link pictureOf(CkProject ckProject, int pass) {
        NodeInst out = ckProject.output(pass);
        Link src = out == null ? null : ckProject.incoming(out.id, "color");
        if (src == null) throw new IllegalStateException(L.t("ec.pr.needPicture"));
        return src;
    }
    //endregion

    //region Post Processing
    private static void filter(CkProject ckProject, int pass) {
        Link src = pictureOf(ckProject, pass);
        Builder builder = new Builder(ckProject, pass);
        int f = builder.n("posterize");
        builder.link(src.from(), src.fromPort(), f, "c");
        builder.link(builder.n("constFloat", "value", "4"), "out", f, "levels");
        builder.link(f, "out", Objects.requireNonNull(ckProject.output(pass)).id, "color");
    }
    private static void vignette(CkProject ckProject, int pass) {
        Link src = pictureOf(ckProject, pass);
        Builder builder = new Builder(ckProject, pass);
        int sx = builder.n("screenX"), sy = builder.n("screenY");
        int m1 = builder.n("mul"), m2 = builder.n("mul"), sum = builder.n("add"), k = builder.n("mul");
        builder.link(sx, "out", m1, "a"); builder.link(sx, "out", m1, "b");
        builder.link(sy, "out", m2, "a"); builder.link(sy, "out", m2, "b");
        builder.link(m1, "out", sum, "a"); builder.link(m2, "out", sum, "b");
        builder.link(sum, "out", k, "a");
        builder.link(builder.n("constFloat", "value", "0.6"), "out", k, "b");
        int sub = builder.n("sub");
        builder.link(builder.n("constFloat", "value", "1"), "out", sub, "a");
        builder.link(k, "out", sub, "b");
        int clamp = builder.n("clamp");
        builder.link(sub, "out", clamp, "x");
        builder.link(builder.n("constFloat", "value", "0"), "out", clamp, "lo");
        builder.link(builder.n("constFloat", "value", "1"), "out", clamp, "hi");
        int br = builder.n("brightness");
        builder.link(src.from(), src.fromPort(), br, "c");
        builder.link(clamp, "out", br, "k");
        builder.link(br, "out", Objects.requireNonNull(ckProject.output(pass)).id, "color");
    }
    private static void grain(CkProject ckProject, int pass) {
        Link src;
        src = pictureOf(ckProject, pass);
        Builder builder = new Builder(ckProject, pass);
        int px = builder.n("pixelX"), py = builder.n("pixelY");
        int fx = builder.n("toFloat"), fy = builder.n("toFloat");
        builder.link(px, "out", fx, "a");
        builder.link(py, "out", fy, "a");
        int noise = builder.n("hash");
        builder.link(fx, "out", noise, "x");
        builder.link(fy, "out", noise, "y");
        builder.link(builder.n("time"), "out", noise, "seed");
        int scaled = builder.n("mul");
        builder.link(noise, "out", scaled, "a");
        builder.link(builder.n("constFloat", "value", "0.3"), "out", scaled, "b");
        int k = builder.n("add");
        builder.link(scaled, "out", k, "a");
        builder.link(builder.n("constFloat", "value", "0.85"), "out", k, "b");
        int br = builder.n("brightness");
        builder.link(src.from(), src.fromPort(), br, "c");
        builder.link(k, "out", br, "k");
        builder.link(br, "out", Objects.requireNonNull(ckProject.output(pass)).id, "color");
    }
    private static void blur(CkProject ckProject) {
        int pass = ckProject.passCount();
        if (ckProject.output(pass - 1) == null) throw new IllegalStateException(L.t("ec.pr.needPicture"));
        Builder builder = new Builder(ckProject, pass);
        int out = ensureOutput(ckProject, pass);
        int m = builder.n("neighbor", "dx", "0", "dy", "0");
        int l = builder.n("neighbor", "dx", "-1", "dy", "0");
        int r = builder.n("neighbor", "dx", "1", "dy", "0");
        int u = builder.n("neighbor", "dx", "0", "dy", "-1");
        int d = builder.n("neighbor", "dx", "0", "dy", "1");
        int m1 = builder.n("mixColor");
        builder.link(m, "out", m1, "a"); builder.link(l, "out", m1, "b"); builder.link(builder.n("constFloat", "value", "0.5"), "out", m1, "t");
        int m2 = builder.n("mixColor");
        builder.link(m1, "out", m2, "a"); builder.link(r, "out", m2, "b"); builder.link(builder.n("constFloat", "value", "0.33"), "out", m2, "t");
        int m3 = builder.n("mixColor");
        builder.link(m2, "out", m3, "a"); builder.link(u, "out", m3, "b"); builder.link(builder.n("constFloat", "value", "0.25"), "out", m3, "t");
        int m4 = builder.n("mixColor");
        builder.link(m3, "out", m4, "a"); builder.link(d, "out", m4, "b"); builder.link(builder.n("constFloat", "value", "0.2"), "out", m4, "t");
        builder.link(m4, "out", out, "color");
    }
    //endregion

    //region Node Building
    private record Builder(CkProject p, int pass) {
    int n(String kind, String... keyValues) {
            int idx = (int) p.nodes.stream().filter(nodeInst -> nodeInst.pass == pass).count();
            NodeInst inst = p.add(kind, 40 + (idx % 5) * 200, 40 + (idx / 5) * 120, pass);
            for (int i = 0; i + 1 < keyValues.length; i += 2) inst.params.put(keyValues[i], keyValues[i + 1]);
            return inst.id;
        }
    void link(int from, String fromPort, int to, String toPort) { p.setLink(new Link(from, fromPort, to, toPort)); }
    }
    //endregion
}