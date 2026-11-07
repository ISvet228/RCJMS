package Project.EngineCreator;
import Project.CreatorKit.*;

import java.util.*;

public final class OriginalEngines {
    //region Variables
    static final List<Original> ALL = List.of(
            new Original("dda"),
            new Original("src"),
            new Original("raymarch"),
            new Original("raytrace"),
            new Original("moriarty"));
    private static final double TAN_HALF_FOV = Math.tan(Math.toRadians(30));
    private static final double HYP_PLANE_LENGTH = Math.tan(Math.toRadians(70));
    private static final double AMBIENT = 0.015, FLASH = 0.5, FLAT_LIGHT = 0.12;
    //endregion

    //region Constructors
    private OriginalEngines() {}
    //endregion

    //region Engines
    record Original(String id) {
    String title() { return L.t("ec.og." + id); }
 }
    static CkProject build(String id) {
        Original found = ALL.stream().filter(original -> original.id().equals(id)).findFirst().orElseThrow(() -> new IllegalArgumentException("unknown original " + id));
        CkProject ckProject = new CkProject();
        ckProject.name = "Original" + switch (id) {
            case "dda" -> "DDA";
            case "src" -> "SRC";
            case "raymarch" -> "RayMarching";
            case "raytrace" -> "RayTracing";
            default -> "Moriarty";
        };
        ckProject.title = found.title();
        ckProject.setGeometry(0, CkProject.EUCLID);
        ckProject.setGeometry(1, CkProject.HYPER);
        for (int pass = 0; pass < 2; pass++) {
            B b = new B(ckProject, pass);
            Scene s = scene(b, marchRadii(id), pass == 1);
            int color = switch (id) {
                case "dda" -> dda(b, s);
                case "src" -> src(b, s);
                case "raymarch" -> raymarch(b, s);
                case "raytrace" -> raytrace(b, s);
                default -> moriarty(b, s);
            };
            int out = ckProject.add("output", 900, 120, pass).id;
            b.link(color, "out", out, "color");
            Layout.arrange(ckProject, pass);
        }
        return ckProject;
    }
    private static int moriarty(B b, Scene scene) {
        int on = qualityAtLeast(b, scene.qualityF(), 1);
        int lz = lightLz(b, scene);
        int len = lightLen(b, lz);
        Along along = floorAlong(b, scene), ce = ceilingAlong(b, scene);

        int ndlW = bin(b, "div", facing(b, scene), "out", len, "out");
        Lit litW = gameLight(b, scene.P(), occlusion(b, scene), ndlW, fillWall(b, scene), scene.isFinish());
        int wallBase = litColor(b, wallTexture(b, scene), litW);
        int wallPix = sel(b, on, wallReflectionGame(b, scene, wallBase, litW), wallBase);

        int ndlF = bin(b, "div", bin(b, "max", lz, "out", b.c(0), "out"), "out", len, "out");
        Lit litF = gameLight(b, along.planeDist(), b.c(1), ndlF, b.c(0.85), noFinish(b));
        int floorBase = litColor(b, surfaceTexture(b, scene, along, false), litF);
        int floorPix = sel(b, on, floorReflectionGame(b, scene, along, floorBase, litF), floorBase);

        int ndlC = bin(b, "div", bin(b, "max", un(b, "neg", lz, "out"), "out", b.c(0), "out"), "out", len, "out");
        Lit litC = gameLight(b, ce.planeDist(), b.c(1), ndlC, b.c(0), noFinish(b));
        int ceilPix = litColor(b, surfaceTexture(b, scene, ce, true), litC);
        return compose(b, scene, wallPix, ceilPix, floorPix);
    }
    private static int raytrace(B b, Scene scene) {
        int lz = lightLz(b, scene);
        int len = lightLen(b, lz);
        Along along = floorAlong(b, scene), ce = ceilingAlong(b, scene);

        int ndlW = bin(b, "div", facing(b, scene), "out", len, "out");
        Lit litW = gameLight(b, scene.P(), occlusion(b, scene), ndlW, fillWall(b, scene), scene.isFinish());
        int wallBase = litColor(b, wallTexture(b, scene), litW);
        int wallPix = wallReflectionGame(b, scene, wallBase, litW);

        int ndlF = bin(b, "div", bin(b, "max", lz, "out", b.c(0), "out"), "out", len, "out");
        Lit litF = gameLight(b, along.planeDist(), b.c(1), ndlF, b.c(0.85), noFinish(b));
        int floorBase = litColor(b, surfaceTexture(b, scene, along, false), litF);
        int floorPix = floorReflectionGame(b, scene, along, floorBase, litF);

        int ndlC = bin(b, "div", bin(b, "max", un(b, "neg", lz, "out"), "out", b.c(0), "out"), "out", len, "out");
        Lit litC = gameLight(b, ce.planeDist(), b.c(1), ndlC, b.c(0), noFinish(b));
        int ceilPix = litColor(b, surfaceTexture(b, scene, ce, true), litC);
        return compose(b, scene, wallPix, ceilPix, floorPix);
    }
    //endregion

    //region Node Builder
    private static int bin(B b, String kind, int a, String ap, int c, String cp) {
        int x = b.n(kind);
        b.link(a, ap, x, "a");
        b.link(c, cp, x, "b");
        return x;
    }
    private static int bin(B b, int a, int c) { return bin(b, "greaterEq", a, "out", c, "out"); }
    private static int un(B b, String kind, int a, String ap) {
        int x = b.n(kind);
        b.link(a, ap, x, "a");
        return x;
    }
    private static int with(B b, String kind, Object... triples) {
        int x = b.n(kind);
        for (int i = 0; i + 2 < triples.length; i += 3) b.link((Integer) triples[i], (String) triples[i + 1], x, (String) triples[i + 2]);
        return x;
    }
    private static int sel(B b, int cond, int whenTrue, int whenFalse) { return with(b, "select", cond, "out", "cond", whenTrue, "out", "a", whenFalse, "out", "b"); }
    private static int qualityAtLeast(B b, int qualityF, int level) { return bin(b, qualityF, b.c(level)); }
    private static int noFinish(B b) { return b.n("constBool", "value", "false"); }
    private static final class B {
        final CkProject p;
        final int pass;
        private int count = 0;
        Integer normal;

        B(CkProject ckProject, int pass) { this.p = ckProject; this.pass = pass; }

        int n(String kind, String... keyValues) {
            NodeInst inst = p.add(kind, 40 + (count % 5) * 200, 40 + (count / 5) * 120, pass);
            count++;
            for (int i = 0; i + 1 < keyValues.length; i += 2) inst.params.put(keyValues[i], keyValues[i + 1]);
            return inst.id;
        }
        int c(double value) { return n("constFloat", "value", String.valueOf(value)); }
        int col() { return n("constColor", "color", "#000000"); }
        int ci(int value) { return n("constInt", "value", String.valueOf(value)); }

        void link(int from, String fromPort, int to, String toPort) {
            p.setLink(new Link(from, fromPort, to, toPort));
        }
        void note(int node, String text) {
            if (text.length() > NodeGeometry.COMMENT_MAX) text = text.substring(0, NodeGeometry.COMMENT_MAX);
            for (NodeInst inst : p.nodes) if (inst.id == node) inst.comment = text;
        }
        void setTexture(int node, String texture) { for (NodeInst inst : p.nodes) if (inst.id == node) inst.params.put("texture", texture); }
    }
    //endregion

    //region Scene
    private record Scene(boolean hyper, String dxP, String dyP, int dxN, int dyN, int grid, int P, int scale, int invL, int curv, int r, int z,
                         int h, int hgt, int isWall, int wallV, int isFinish, int player, int qualityF, boolean marching) { }
    private static double[] marchRadii(String id) {
        return switch (id) {
            case "raymarch" -> new double[]{0.0, 0.06, 48, 96, 160};
            case "moriarty" -> new double[]{0.2, 0.2, 0, 0, 0};
            default -> null;
        };
    }
    private static Scene scene(B b, double[] march, boolean hyper) {
        int sx = b.n("screenX");
        b.note(sx, "Column position: -1 left edge, +1 right edge.");
        int sy = b.n("screenY");
        b.note(sy, "Row position: -1 bottom, +1 top of the view.");
        int pose = b.n("cameraPose");
        int curv = hyper ? b.n("curvature") : -1;
        int pos = b.n("playerPosition");

        int dxN, dyN, scale = -1, invL = -1, r;
        String dxP = "out", dyP = "out";
        if (!hyper) {
            int cosA = un(b, "cos", pose, "angle"), sinA = un(b, "sin", pose, "angle");
            int cosP = un(b, "cos", pose, "pitch"), sinP = un(b, "sin", pose, "pitch");
            int xT = bin(b, "mul", sx, "out", b.c(TAN_HALF_FOV), "out");
            int hF = un(b, "toFloat", b.n("height"), "out");
            int wF = un(b, "toFloat", b.n("width"), "out");
            int vScale = bin(b, "mul", bin(b, "div", hF, "out", wF, "out"), "out", b.c(TAN_HALF_FOV), "out");
            int yV = bin(b, "mul", sy, "out", vScale, "out");
            int fwd = bin(b, "sub", cosP, "out", bin(b, "mul", yV, "out", sinP, "out"), "out");
            int up = bin(b, "add", sinP, "out", bin(b, "mul", yV, "out", cosP, "out"), "out");
            int lh = un(b, "sqrt", bin(b, "add", bin(b, "mul", fwd, "out", fwd, "out"), "out",
                    bin(b, "mul", xT, "out", xT, "out"), "out"), "out");
            dxN = bin(b, "div", bin(b, "sub", bin(b, "mul", fwd, "out", cosA, "out"), "out",
                    bin(b, "mul", xT, "out", sinA, "out"), "out"), "out", lh, "out");
            dyN = bin(b, "div", bin(b, "add", bin(b, "mul", fwd, "out", sinA, "out"), "out",
                    bin(b, "mul", xT, "out", cosA, "out"), "out"), "out", lh, "out");
            r = bin(b, "div", up, "out", lh, "out");
        } else {
            int cosA = un(b, "cos", pose, "angle");
            int sinA = un(b, "sin", pose, "angle");
            int lp = b.c(HYP_PLANE_LENGTH);
            int rdX = bin(b, "sub", cosA, "out", bin(b, "mul", bin(b, "mul", sinA, "out", lp, "out"), "out", sx, "out"), "out");
            int rdY = bin(b, "add", sinA, "out", bin(b, "mul", bin(b, "mul", cosA, "out", lp, "out"), "out", sx, "out"), "out");
            scale = un(b, "sqrt", bin(b, "add", bin(b, "mul", rdX, "out", rdX, "out"), "out", bin(b, "mul", rdY, "out", rdY, "out"), "out"), "out");
            dxN = bin(b, "div", rdX, "out", scale, "out");
            dyN = bin(b, "div", rdY, "out", scale, "out");
            invL = bin(b, "div", b.c(1), "out", scale, "out");
            r = bin(b, "add", bin(b, "mul", sy, "out", b.c(0.5), "out"), "out", bin(b, "mul", pose, "pitch", b.c(0.5), "out"), "out");
        }

        int grid;
        if (march == null) grid = b.n("rayGridPortal", "maxSteps", "128");
        else grid = b.n("marchRounded", "maxSteps", "96", "outerRadius", String.valueOf(march[0]), "innerRadius", String.valueOf(march[1]),
                    "q0Steps", String.valueOf((int) march[2]), "q1Steps", String.valueOf((int) march[3]), "q2Steps", String.valueOf((int) march[4]));

        b.note(grid, march == null ? "Walks the grid to the first wall; portals are passed." : "Sphere tracing over rounded walls, steps by quality.");
        b.link(pos, "x", grid, "originX");
        b.link(pos, "y", grid, "originY");
        b.link(dxN, dxP, grid, "dirX");
        b.link(dyN, dyP, grid, "dirY");

        int P;
        if (!hyper) P = bin(b, "mul", grid, "distance", b.c(1), "out");
        else {
            int t = bin(b, "mul", grid, "distance", invL, "out");
            P = with(b, "hypDistance", t, "out", "t", curv, "out", "k");
        }

        int isFinish = bin(b, "equalInt", grid, "cell", b.ci(2), "out");
        int h = sel(b, isFinish, b.c(0.5), b.c(1));
        int rp = bin(b, "mul", r, "out", P, "out");
        int hgt = bin(b, "add", rp, "out", pose, "height");
        int aboveFloor = bin(b, "greaterEq", rp, "out", un(b, "neg", pose, "height"), "out");
        int belowTop = bin(b, "lessEq", rp, "out", bin(b, "sub", h, "out", pose, "height"), "out");
        int isWall = bin(b, "and", bin(b, "and", grid, "hit", aboveFloor, "out"), "out", belowTop, "out");

        int vWall = bin(b, "div", hgt, "out", h, "out");
        int wallV = with(b, "clamp", bin(b, "sub", b.c(1), "out", vWall, "out"), "out", "x", b.c(0), "out", "lo", b.c(0.999), "out", "hi");

        int qualityNode = b.n("quality");
        b.note(qualityNode, "Render quality 0, 1, 2 from the settings.");
        int qualityF = un(b, "toFloat", qualityNode, "out");
        return new Scene(hyper, dxP, dyP, dxN, dyN, grid, P, scale, invL, curv, r, pose, h, hgt, isWall, wallV, isFinish, pos, qualityF, march != null);
    }
    private static int normal(B b, Scene scene) {
        if (scene.grid() >= 0 && b.p.nodes.stream().anyMatch(nodeInst -> nodeInst.id == scene.grid() && nodeInst.kind.equals("marchRounded"))) return scene.grid();
        if (b.normal == null) {
            int normal = b.n("wallNormal");
            b.link(scene.dxN(), scene.dxP(), normal, "dirX");
            b.link(scene.dyN(), scene.dyP(), normal, "dirY");
            b.link(scene.grid(), "side", normal, "side");
            b.normal = normal;
        }
        return b.normal;
    }
    private static int rayDist(B b, Scene scene, int planeDist) {
        if (!scene.hyper()) return planeDist;
        int asinhPart = un(b, "asinh", bin(b, "div", planeDist, "out", scene.curv(), "out"), "out");
        int kAsinh = bin(b, "mul", scene.curv(), "out", asinhPart, "out");
        return bin(b, "mul", scene.scale(), "out", kAsinh, "out");
    }
    private static int perceived(B b, Scene scene, int ray) {
        if (!scene.hyper()) return bin(b, "mul", ray, "distance", b.c(1), "out");
        int t = bin(b, "mul", ray, "distance", scene.invL(), "out");
        return with(b, "hypDistance", t, "out", "t", scene.curv(), "out", "k");
    }
    private static int dda(B b, Scene scene) {
        int wallShade = bin(b, "mul", sideShade(b, scene), "out", light(b, scene, scene.P(), facing(b, scene)), "out");
        int wallPix = fogWhenQuality(b, scene, with(b, "brightness", wallTexture(b, scene), "out", "c", wallShade, "out", "k"), scene.P());
        Along along = floorAlong(b, scene), ce = ceilingAlong(b, scene);
        int floorPix = fogAlways(b, with(b, "brightness", surfaceTexture(b, scene, along, false), "out", "c",
                light(b, scene, along.planeDist(), -1), "out", "k"), along.planeDist());
        int ceilPix = fogAlways(b, with(b, "brightness", surfaceTexture(b, scene, ce, true), "out", "c",
                bin(b, "mul", b.c(0.6), "out", light(b, scene, ce.planeDist(), -1), "out"), "out", "k"), ce.planeDist());
        return compose(b, scene, wallPix, ceilPix, floorPix);
    }
    private static int src(B b, Scene scene) {
        Along along = floorAlong(b, scene), ce = ceilingAlong(b, scene);
        int wallPix = with(b, "brightness", wallTexture(b, scene), "out", "c", light(b, scene, scene.P(), facing(b, scene)), "out", "k");
        int floorPix = fogAlways(b, with(b, "brightness", surfaceTexture(b, scene, along, false), "out", "c",
                light(b, scene, along.planeDist(), -1), "out", "k"), along.planeDist());
        int ceilPix = fogAlways(b, with(b, "brightness", surfaceTexture(b, scene, ce, true), "out", "c",
                bin(b, "mul", b.c(0.5), "out", light(b, scene, ce.planeDist(), -1), "out"), "out", "k"), ce.planeDist());
        return compose(b, scene, wallPix, ceilPix, floorPix);
    }
    private static int raymarch(B b, Scene scene) {
        int shade = bin(b, "mul", sideShade(b, scene), "out", light(b, scene, scene.P(), facing(b, scene)), "out");
        int shaded = sel(b, qualityAtLeast(b, scene.qualityF(), 2), bin(b, "mul", shade, "out", occlusion(b, scene), "out"), shade);
        int wallPix = fogWhenQuality(b, scene, with(b, "brightness", wallTexture(b, scene), "out", "c", shaded, "out", "k"), scene.P());
        Along along = floorAlong(b, scene), ce = ceilingAlong(b, scene);
        int floorPix = fogAlways(b, with(b, "brightness", surfaceTexture(b, scene, along, false), "out", "c",
                light(b, scene, along.planeDist(), -1), "out", "k"), along.planeDist());
        int ceilPix = fogAlways(b, with(b, "brightness", surfaceTexture(b, scene, ce, true), "out", "c",
                bin(b, "mul", b.c(0.6), "out", light(b, scene, ce.planeDist(), -1), "out"), "out", "k"), ce.planeDist());
        return compose(b, scene, wallPix, ceilPix, floorPix);
    }
    private static int dirZ(B b, Scene scene) {
        int rr = bin(b, "mul", scene.r(), "out", scene.r(), "out");
        return bin(b, "div", scene.r(), "out", un(b, "sqrt", bin(b, "add", b.c(1), "out", rr, "out"), "out"), "out");
    }
    //endregion

    //region Surfaces
    private record Along(int planeDist, int rayDist) { }
    private static int wallTexture(B b, Scene scene) {
        int wall = with(b, "sampleTexture", scene.grid(), "hitU", "u", scene.wallV(), "out", "v");
        b.note(wall, "Wall texture: u along the wall, v up the wall.");
        b.setTexture(wall, "wall");
        int finish = with(b, "sampleTexture", scene.grid(), "hitU", "u", scene.wallV(), "out", "v");
        b.setTexture(finish, "finish");
        return sel(b, scene.isFinish(), finish, wall);
    }
    private static Along floorAlong(B b, Scene scene) {
        int pf = bin(b, "div", scene.z(), "height", un(b, "neg", scene.r(), "out"), "out");
        return new Along(pf, rayDist(b, scene, pf));
    }
    private static Along ceilingAlong(B b, Scene scene) {
        int pc = bin(b, "div", bin(b, "sub", b.c(1), "out", scene.z(), "height"), "out", scene.r(), "out");
        return new Along(pc, rayDist(b, scene, pc));
    }
    private static int surfaceTexture(B b, Scene scene, Along along, boolean ceiling) {
        int wx = with(b, "add", scene.player(), "x", "a", bin(b, "mul", scene.dxN(), scene.dxP(), along.rayDist(), "out"), "out", "b");
        int wy = with(b, "add", scene.player(), "y", "a", bin(b, "mul", scene.dyN(), scene.dyP(), along.rayDist(), "out"), "out", "b");
        int tex = with(b, "sampleTexture", wx, "out", "u", wy, "out", "v");
        b.setTexture(tex, ceiling ? "ceiling" : "floor");
        return tex;
    }
    private static int fillWall(B b, Scene scene) {
        int n = normal(b, scene);
        int dot = with(b, "dot3", n, "nx", "ax", n, "ny", "ay", b.c(0), "out", "az",
                b.c(0.46), "out", "bx", b.c(0.36), "out", "by", b.c(0.85), "out", "bz");
        return bin(b, "max", dot, "out", b.c(0), "out");
    }
    private static int compose(B b, Scene scene, int wall, int ceiling, int floor) {
        int upper = sel(b, bin(b, "greaterEq", scene.r(), "out", b.c(0), "out"), ceiling, floor);
        return sel(b, scene.isWall(), wall, upper);
    }
    private static int occlusion(B b, Scene scene) {
        int s1 = with(b, "smoothstep", b.c(0), "out", "edge0", b.c(0.22), "out", "edge1", scene.hgt(), "out", "x");
        int aoBase = bin(b, "sub", b.c(1), "out", bin(b, "mul", b.c(0.45), "out", bin(b, "sub", b.c(1), "out", s1, "out"), "out"), "out");
        int topDist = bin(b, "sub", scene.h(), "out", scene.hgt(), "out");
        int s2 = with(b, "smoothstep", b.c(0), "out", "edge0", b.c(0.14), "out", "edge1", topDist, "out", "x");
        int aoTop = bin(b, "sub", b.c(1), "out", bin(b, "mul", b.c(0.25), "out", bin(b, "sub", b.c(1), "out", s2, "out"), "out"), "out");
        return bin(b, "mul", aoBase, "out", aoTop, "out");
    }
    //endregion

    //region Lighting
    private static int lightLz(B b, Scene scene) { return bin(b, "sub", b.c(0.18), "out", dirZ(b, scene), "out"); }
    private static int lightLen(B b, int lz) {
        return un(b, "sqrt", bin(b, "add", b.c(1), "out", bin(b, "mul", lz, "out", lz, "out"), "out"), "out");
    }
    private static int facing(B b, Scene scene) {
        int n = normal(b, scene);
        int dot = with(b, "dot3", n, "nx", "ax", n, "ny", "ay", b.c(0), "out", "az", scene.dxN(), scene.dxP(), "bx", scene.dyN(), scene.dyP(), "by", b.c(0), "out", "bz");
        return bin(b, "max", un(b, "neg", dot, "out"), "out", b.c(0), "out");
    }
    private static int light(B b, Scene scene, int dist, int facing) {
        int f = falloff(b, dist);
        if (facing >= 0) f = bin(b, "mul", f, "out", facing, "out");
        int lit = bin(b, "add", b.c(AMBIENT), "out", bin(b, "mul", b.c(FLASH), "out", f, "out"), "out");
        return sel(b, qualityAtLeast(b, scene.qualityF(), 1), lit, b.c(FLAT_LIGHT));
    }
    private static int sideShade(B b, Scene scene) {
        if (scene.marching()) {
            int ay = un(b, "abs", normal(b, scene), "ny");
            return bin(b, "sub", b.c(1), "out", bin(b, "mul", b.c(1 - 0.8), "out", ay, "out"), "out");
        }
        int isX = bin(b, "equalInt", scene.grid(), "side", b.ci(0), "out");
        return sel(b, isX, b.c(1), b.c(0.8));
    }
    private static int falloff(B b, int dist) {
        int q = bin(b, "div", dist, "out", b.c(3), "out");
        int q2 = bin(b, "mul", q, "out", q, "out");
        int f = with(b, "clamp", bin(b, "sub", b.c(1), "out", q2, "out"), "out", "x", b.c(0), "out", "lo", b.c(1), "out", "hi");
        return bin(b, "mul", f, "out", f, "out");
    }
    private record Lit(int node) { }
    private static Lit gameLight(B b, int dist, int ao, int ndl, int ndf, int finish) {
        int x = b.n("gameLight");
        b.note(x, "Game light: ambient, fill and flashlight falloff.");
        b.link(dist, "out", x, "dist");
        b.link(ao, "out", x, "ao");
        b.link(ndl, "out", x, "ndl");
        b.link(ndf, "out", x, "ndf");
        b.link(finish, "out", x, "finish");
        return new Lit(x);
    }
    private static int clampLevel(B b, Lit lit) { return with(b, "clamp", lit.node(), "level", "x", b.c(0.15), "out", "lo", b.c(1), "out", "hi"); }
    private static int litColor(B b, int tex, Lit lit) {
        int x = b.n("litColor");
        b.note(x, "Texture x light per channel, then the tone curve.");
        b.link(tex, "out", x, "c");
        b.link(lit.node(), "r", x, "r");
        b.link(lit.node(), "g", x, "g");
        b.link(lit.node(), "b", x, "b");
        return x;
    }
    //endregion

    //region Fog
    private static int fogAlways(B b, int color, int dist) {
        return with(b, "fog", color, "out", "c", b.col(), "out", "fogColor", dist, "out", "distance", b.c(0.12), "out", "density");
    }
    private static int fogWhenQuality(B b, Scene scene, int color, int dist) { return sel(b, qualityAtLeast(b, scene.qualityF(), 1), fogAlways(b, color, dist), color); }
    //endregion

    //region Reflections
    private record Reflected(int color, int visible, int dist) { }
    private static Reflected reflectedSurface(B b, int ray, int Pr, int z0, int slope, int ox, int oy, int dxN, String dxP, int dyN, String dyP, int pathD) {
        int zW = bin(b, "add", z0, "out", bin(b, "mul", slope, "out", Pr, "out"), "out");
        int top = sel(b, bin(b, "equalInt", ray, "cell", b.ci(2), "out"), b.c(0.5), b.c(1));
        int planeValid = bin(b, "greater", un(b, "abs", slope, "out"), "out", b.c(1e-6), "out");
        int goesDown = bin(b, "less", slope, "out", b.c(0), "out");
        int df = bin(b, "div", z0, "out", un(b, "neg", slope, "out"), "out");
        int dc = bin(b, "div", bin(b, "sub", b.c(1), "out", z0, "out"), "out", slope, "out");
        int planeD = sel(b, planeValid, sel(b, goesDown, df, dc), b.c(1e6));

        int wallIn = bin(b, "and", bin(b, "greaterEq", zW, "out", b.c(0), "out"), "out", bin(b, "lessEq", zW, "out", top, "out"), "out");
        int wallVis = bin(b, "and", bin(b, "and", ray, "hit", wallIn, "out"), "out", bin(b, "lessEq", Pr, "out", planeD, "out"), "out");

        int vRef = with(b, "clamp", bin(b, "sub", b.c(1), "out", bin(b, "div", zW, "out", top, "out"), "out"), "out",
                "x", b.c(0), "out", "lo", b.c(0.999), "out", "hi");
        int wallTex = with(b, "sampleTexture", ray, "hitU", "u", vRef, "out", "v");
        b.setTexture(wallTex, "wall");

        int fx = with(b, "add", ox, "out", "a", bin(b, "mul", dxN, dxP, df, "out"), "out", "b");
        int fy = with(b, "add", oy, "out", "a", bin(b, "mul", dyN, dyP, df, "out"), "out", "b");
        int floorTex = with(b, "sampleTexture", fx, "out", "u", fy, "out", "v");
        b.setTexture(floorTex, "floor");
        int cx = with(b, "add", ox, "out", "a", bin(b, "mul", dxN, dxP, dc, "out"), "out", "b");
        int cy = with(b, "add", oy, "out", "a", bin(b, "mul", dyN, dyP, dc, "out"), "out", "b");
        int ceilTex = with(b, "sampleTexture", cx, "out", "u", cy, "out", "v");
        b.setTexture(ceilTex, "ceiling");

        int tex = sel(b, wallVis, wallTex, sel(b, goesDown, floorTex, ceilTex));
        int dist = sel(b, wallVis, bin(b, "add", pathD, "out", Pr, "out"), bin(b, "add", pathD, "out", planeD, "out"));
        Lit lit = gameLight(b, dist, b.c(1), b.c(0.8), b.c(0.4), noFinish(b));
        int color = litColor(b, tex, lit);
        int visible = bin(b, "or", wallVis, "out", planeValid, "out");
        return new Reflected(color, visible, sel(b, wallVis, Pr, planeD));
    }
    private static int wallReflectionGame(B b, Scene scene, int base, Lit lit) {
        if (b.pass != 0) return base;
        int n = normal(b, scene);
        int hit = b.n("pointOnRay");
        b.link(scene.player(), "x", hit, "ox");
        b.link(scene.player(), "y", hit, "oy");
        b.link(scene.dxN(), scene.dxP(), hit, "dx");
        b.link(scene.dyN(), scene.dyP(), hit, "dy");
        b.link(scene.grid(), "distance", hit, "t");
        int ox = with(b, "add", hit, "x", "a", with(b, "mul", n, "nx", "a", b.c(0.01), "out", "b"), "out", "b");
        int oy = with(b, "add", hit, "y", "a", with(b, "mul", n, "ny", "a", b.c(0.01), "out", "b"), "out", "b");

        int refl = b.n("reflect2");
        b.note(refl, "Reflected ray: what it meets is shown in the wall.");
        b.link(scene.dxN(), scene.dxP(), refl, "dirX");
        b.link(scene.dyN(), scene.dyP(), refl, "dirY");
        b.link(n, "nx", refl, "nx");
        b.link(n, "ny", refl, "ny");
        int ray = b.n("rayGridPortal", "maxSteps", "128");
        b.link(ox, "out", ray, "originX");
        b.link(oy, "out", ray, "originY");
        b.link(refl, "rx", ray, "dirX");
        b.link(refl, "ry", ray, "dirY");

        int Pr = perceived(b, scene, ray);
        int cosRV = bin(b, "add", bin(b, "mul", refl, "rx", scene.dxN(), scene.dxP()), "out", bin(b, "mul", refl, "ry", scene.dyN(), scene.dyP()), "out");
        int facingFade = with(b, "clamp", bin(b, "div", bin(b, "add", cosRV, "out", b.c(0.2), "out"), "out", b.c(0.5), "out"), "out",
                "x", b.c(0), "out", "lo", b.c(1), "out", "hi");
        Reflected reflected = reflectedSurface(b, ray, Pr, scene.hgt(), scene.r(), ox, oy, refl, "rx", refl, "ry", scene.P());
        int selfFade = with(b, "clamp", bin(b, "sub", Pr, "out", b.c(0.6), "out"), "out", "x", b.c(0), "out", "lo", b.c(1), "out", "hi");
        int strength = bin(b, "mul", bin(b, "mul", bin(b, "mul", clampLevel(b, lit), "out", b.c(0.75 * 0.24), "out"), "out", selfFade, "out"), "out", facingFade, "out");
        int mixed = with(b, "mixColor", base, "out", "a", reflected.color(), "out", "b", strength, "out", "t");
        return sel(b, reflected.visible(), mixed, base);
    }
    private static int floorReflectionGame(B b, Scene scene, Along along, int base, Lit lit) {
        int wx = with(b, "add", scene.player(), "x", "a", bin(b, "mul", scene.dxN(), scene.dxP(), along.rayDist(), "out"), "out", "b");
        int wy = with(b, "add", scene.player(), "y", "a", bin(b, "mul", scene.dyN(), scene.dyP(), along.rayDist(), "out"), "out", "b");
        int ray = b.n("rayGridPortal", "maxSteps", "128");
        b.note(ray, "Reflected ray from the floor point, rising.");
        b.link(wx, "out", ray, "originX");
        b.link(wy, "out", ray, "originY");
        b.link(scene.dxN(), scene.dxP(), ray, "dirX");
        b.link(scene.dyN(), scene.dyP(), ray, "dirY");

        int Pr = perceived(b, scene, ray);
        Reflected reflected = reflectedSurface(b, ray, Pr, b.c(0), un(b, "neg", scene.r(), "out"), wx, wy, scene.dxN(), scene.dxP(), scene.dyN(), scene.dyP(), along.planeDist());
        int fade = with(b, "clamp", bin(b, "sub", b.c(1), "out", bin(b, "div", reflected.dist(), "out", b.c(6), "out"), "out"), "out",
                "x", b.c(0), "out", "lo", b.c(1), "out", "hi");
        int strength = bin(b, "mul", bin(b, "mul", clampLevel(b, lit), "out", b.c(0.75 * 0.12), "out"), "out", fade, "out");
        int mixed = with(b, "mixColor", base, "out", "a", reflected.color(), "out", "b", strength, "out", "t");
        return sel(b, reflected.visible(), mixed, base);
    }
    //endregion
}