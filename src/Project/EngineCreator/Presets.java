package Project.EngineCreator;

import java.util.*;

public final class Presets {
    record Preset(String id, boolean filter, boolean newPass) { String title() { return L.t("ec.pr." + id); } }

    static final List<Preset> ALL = List.of(
            new Preset("walls", false, false),
            new Preset("hypwalls", false, false),
            new Preset("sky", false, false),
            new Preset("posterize", true, false),
            new Preset("vignette", true, false),
            new Preset("grain", true, false),
            new Preset("blur", false, true));

    private Presets() {}

    static EcProject newProject() {
        EcProject p = new EcProject();
        p.title = L.t("ec.ui.defaultTitle");
        p.add("output", 900, 120, 0);
        return p;
    }
    static int apply(EcProject p, String id, int currentPass) {
        return switch (id) {
            case "walls" -> { walls(p, currentPass, false); yield currentPass; }
            case "hypwalls" -> {
                boolean empty = p.passCount() == 1 && p.nodes.size() <= 1;
                int pass = empty ? currentPass : p.passCount();
                walls(p, pass, true);
                p.setGeometry(pass, EcProject.HYPER);
                yield pass;
            }
            case "sky" -> { sky(p, currentPass); yield currentPass; }
            case "posterize" -> { filter(p, currentPass); yield currentPass; }
            case "vignette" -> { vignette(p, currentPass); yield currentPass; }
            case "grain" -> { grain(p, currentPass); yield currentPass; }
            case "blur" -> { blur(p); yield p.passCount() - 1; }
            default -> throw new IllegalStateException(L.f("ec.pr.unknown", id));
        };
    }
    private record Builder(EcProject p, int pass) {
        int n(String kind, String... keyValues) {
            int idx = (int) p.nodes.stream().filter(x -> x.pass == pass).count();
            NodeInst inst = p.add(kind, 40 + (idx % 5) * 200, 40 + (idx / 5) * 120, pass);
            for (int i = 0; i + 1 < keyValues.length; i += 2) inst.params.put(keyValues[i], keyValues[i + 1]);
            return inst.id;
        }
        void link(int from, String fromPort, int to, String toPort) { p.setLink(new Link(from, fromPort, to, toPort)); }
    }
    private static int ensureOutput(EcProject p, int pass) {
        NodeInst out = p.output(pass);
        if (out != null) return out.id;
        return p.add("output", 900, 120, pass).id;
    }
    private static void walls(EcProject p, int pass, boolean hyperbolic) {
        Builder b = new Builder(p, pass);
        int out = ensureOutput(p, pass);
        double fov = hyperbolic ? 140 : 60;

        int sx = b.n("screenX"), ca = b.n("cameraAngle");
        int rd = b.n("rayDirection", "fov", String.valueOf((int) fov));
        b.link(sx, "out", rd, "screenX");
        b.link(ca, "out", rd, "cameraAngle");

        int rg = b.n("rayGrid", "maxSteps", "64");
        b.link(b.n("playerX"), "out", rg, "originX");
        b.link(b.n("playerY"), "out", rg, "originY");
        b.link(rd, "dirX", rg, "dirX");
        b.link(rd, "dirY", rg, "dirY");

        int dist = rg;
        if (hyperbolic) {
            int hd = b.n("hypDistance");
            b.link(rg, "distance", hd, "t");
            b.link(b.n("curvature"), "out", hd, "k");
            dist = hd;
        }

        int sy = b.n("screenY");
        int dmax = b.n("max");
        b.link(dist, hyperbolic ? "out" : "distance", dmax, "a");
        b.link(b.n("constFloat", "value", "0.05"), "out", dmax, "b");
        int div = b.n("div");
        b.link(sy, "out", div, "a");
        b.link(dmax, "out", div, "b");
        int half = b.n("constFloat", "value", "0.5");
        int mul = b.n("mul");
        b.link(div, "out", mul, "a");
        b.link(half, "out", mul, "b");
        int add = b.n("add");
        b.link(mul, "out", add, "a");
        b.link(half, "out", add, "b");
        int fr = b.n("fract");
        b.link(add, "out", fr, "a");

        int tex = b.n("sampleTexture", "texture", "wall");
        b.link(rg, "hitU", tex, "u");
        b.link(fr, "out", tex, "v");

        int fog = b.n("fog");
        b.link(tex, "out", fog, "c");
        b.link(b.n("constColor", "color", "#1B2A41"), "out", fog, "fogColor");
        b.link(dist, hyperbolic ? "out" : "distance", fog, "distance");
        b.link(b.n("constFloat", "value", "0.12"), "out", fog, "density");

        int sel = b.n("select");
        b.link(rg, "hit", sel, "cond");
        b.link(fog, "out", sel, "a");
        b.link(b.n("constColor", "color", "#05070D"), "out", sel, "b");
        b.link(sel, "out", out, "color");
    }

    private static void sky(EcProject p, int pass) {
        Builder b = new Builder(p, pass);
        int out = ensureOutput(p, pass);
        int sy = b.n("screenY");
        int mul = b.n("mul");
        b.link(sy, "out", mul, "a");
        b.link(b.n("constFloat", "value", "0.5"), "out", mul, "b");
        int add = b.n("add");
        b.link(mul, "out", add, "a");
        b.link(b.n("constFloat", "value", "0.5"), "out", add, "b");
        int mix = b.n("mixColor");
        b.link(b.n("constColor", "color", "#1E3A8A"), "out", mix, "a");
        b.link(b.n("constColor", "color", "#F59E0B"), "out", mix, "b");
        b.link(add, "out", mix, "t");
        b.link(mix, "out", out, "color");
    }
    private static Link pictureOf(EcProject p, int pass) {
        NodeInst out = p.output(pass);
        Link src = out == null ? null : p.incoming(out.id, "color");
        if (src == null) throw new IllegalStateException(L.t("ec.pr.needPicture"));
        return src;
    }

    private static void filter(EcProject p, int pass) {
        Link src = pictureOf(p, pass);
        Builder b = new Builder(p, pass);
        int f = b.n("posterize");
        b.link(src.from(), src.fromPort(), f, "c");
        b.link(b.n("constFloat", "value", "4"), "out", f, "levels");
        b.link(f, "out", Objects.requireNonNull(p.output(pass)).id, "color");
    }
    private static void vignette(EcProject p, int pass) {
        Link src = pictureOf(p, pass);
        Builder b = new Builder(p, pass);
        int sx = b.n("screenX"), sy = b.n("screenY");
        int m1 = b.n("mul"), m2 = b.n("mul"), sum = b.n("add"), k = b.n("mul");
        b.link(sx, "out", m1, "a"); b.link(sx, "out", m1, "b");
        b.link(sy, "out", m2, "a"); b.link(sy, "out", m2, "b");
        b.link(m1, "out", sum, "a"); b.link(m2, "out", sum, "b");
        b.link(sum, "out", k, "a");
        b.link(b.n("constFloat", "value", "0.6"), "out", k, "b");
        int sub = b.n("sub");
        b.link(b.n("constFloat", "value", "1"), "out", sub, "a");
        b.link(k, "out", sub, "b");
        int clamp = b.n("clamp");
        b.link(sub, "out", clamp, "x");
        b.link(b.n("constFloat", "value", "0"), "out", clamp, "lo");
        b.link(b.n("constFloat", "value", "1"), "out", clamp, "hi");
        int br = b.n("brightness");
        b.link(src.from(), src.fromPort(), br, "c");
        b.link(clamp, "out", br, "k");
        b.link(br, "out", Objects.requireNonNull(p.output(pass)).id, "color");
    }
    private static void grain(EcProject p, int pass) {
        Link src;
        src = pictureOf(p, pass);
        Builder b = new Builder(p, pass);
        int px = b.n("pixelX"), py = b.n("pixelY");
        int fx = b.n("toFloat"), fy = b.n("toFloat");
        b.link(px, "out", fx, "a");
        b.link(py, "out", fy, "a");
        int noise = b.n("hash");
        b.link(fx, "out", noise, "x");
        b.link(fy, "out", noise, "y");
        b.link(b.n("time"), "out", noise, "seed");
        int scaled = b.n("mul");
        b.link(noise, "out", scaled, "a");
        b.link(b.n("constFloat", "value", "0.3"), "out", scaled, "b");
        int k = b.n("add");
        b.link(scaled, "out", k, "a");
        b.link(b.n("constFloat", "value", "0.85"), "out", k, "b");
        int br = b.n("brightness");
        b.link(src.from(), src.fromPort(), br, "c");
        b.link(k, "out", br, "k");
        b.link(br, "out", Objects.requireNonNull(p.output(pass)).id, "color");
    }
    private static void blur(EcProject p) {
        int pass = p.passCount();
        if (p.output(pass - 1) == null) throw new IllegalStateException(L.t("ec.pr.needPicture"));
        Builder b = new Builder(p, pass);
        int out = ensureOutput(p, pass);
        int m = b.n("neighbor", "dx", "0", "dy", "0");
        int l = b.n("neighbor", "dx", "-1", "dy", "0");
        int r = b.n("neighbor", "dx", "1", "dy", "0");
        int u = b.n("neighbor", "dx", "0", "dy", "-1");
        int d = b.n("neighbor", "dx", "0", "dy", "1");
        int m1 = b.n("mixColor");
        b.link(m, "out", m1, "a"); b.link(l, "out", m1, "b"); b.link(b.n("constFloat", "value", "0.5"), "out", m1, "t");
        int m2 = b.n("mixColor");
        b.link(m1, "out", m2, "a"); b.link(r, "out", m2, "b"); b.link(b.n("constFloat", "value", "0.33"), "out", m2, "t");
        int m3 = b.n("mixColor");
        b.link(m2, "out", m3, "a"); b.link(u, "out", m3, "b"); b.link(b.n("constFloat", "value", "0.25"), "out", m3, "t");
        int m4 = b.n("mixColor");
        b.link(m3, "out", m4, "a"); b.link(d, "out", m4, "b"); b.link(b.n("constFloat", "value", "0.2"), "out", m4, "t");
        b.link(m4, "out", out, "color");
    }
}