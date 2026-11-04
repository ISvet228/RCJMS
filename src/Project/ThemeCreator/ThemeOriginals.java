package Project.ThemeCreator;
import Project.CreatorKit.*;

import java.util.*;

public final class ThemeOriginals {
    //region Variables
    private static final double W = 960, H = 540;
    private static final int PART = 64;
    private static final double TILT_DEG = -14, FLATNESS = 0.3;
    //endregion

    //region Constructors
    private ThemeOriginals() {}
    //endregion

    //region Template Registry
    public static List<ThemeDoc> originals() {
        return arranged(List.of(space(), nebula(), blackhole(), sunset(), aurora(), ocean()));
    }
    public static List<ThemeDoc> templates() {
        return arranged(List.of(blank(), starfield(), gradientSun()));
    }
    private static List<ThemeDoc> arranged(List<ThemeDoc> docs) {
        for (ThemeDoc themeDoc : docs) {
            for (ThemeObject themeObject : themeDoc.objects) ThemeLayout.arrange(themeObject.graph);
            compressLayers(themeDoc);
        }
        return docs;
    }
    //endregion

    //region Template Designs
    static ThemeDoc space() {
        ThemeDoc themeDoc = doc("space", "Space", 0, "mm.bg_space");
        rect(themeDoc, "Base", -100, 0, 0, W, H, "#0F0F23");
        Obj obj = glow(themeDoc, "Nebula A", -90, 288, 216, 300, "#463C8C", 40 / 255.0);
        obj.drivePosition(obj.add(obj.mul(obj.sin(obj.time(0.05)), 30), 288), obj.add(obj.mul(obj.cos(obj.time(0.07)), 20), 216));
        Obj b = glow(themeDoc, "Nebula B", -89, 720, 378, 260, "#285AA0", 35 / 255.0);
        b.drivePosition(b.add(b.mul(b.cos(b.time(0.04)), 25), 720), b.add(b.mul(b.sin(b.time(0.06)), 20), 378));
        stars(themeDoc);
        glow(themeDoc, "Overlay", 0, 480, 280, 430, "#0F0F23", 200 / 255.0);
        return themeDoc;
    }
    public static ThemeDoc nebula() {
        ThemeDoc themeDoc = doc("nebula", "Nebula", 1, "mm.bg_nebula");
        rect(themeDoc, "Base", -100, 0, 0, W, H, "#0F0F23");
        Obj obj = glow(themeDoc, "Cloud A", -90, 300, 200, 380, "#782896", 140 / 255.0);
        obj.drivePosition(obj.add(obj.mul(obj.sin(obj.time(0.11)), 45), 300), obj.add(obj.mul(obj.cos(obj.time(0.13)), 35), 200));
        obj.drive("alpha", obj.add(obj.mul(obj.sin(obj.time(0.5)), 25 / 255.0), 140 / 255.0));
        Obj b = glow(themeDoc, "Cloud B", -89, 700, 380, 420, "#147896", 110 / 255.0);
        b.drivePosition(b.add(b.mul(b.cos(b.time(0.09)), 40), 700), b.add(b.mul(b.sin(b.time(0.1)), 30), 380));
        b.drive("alpha", b.add(b.mul(b.cos(b.time(0.4)), 25 / 255.0), 110 / 255.0));
        return themeDoc;
    }
    public static ThemeDoc blackhole() {
        ThemeDoc themeDoc = doc("blackhole", "Black hole", 2, "mm.bg_blackhole");
        double cx = W * 0.72, cy = H * 0.5, r = H * 0.2;
        rect(themeDoc, "Base", -100, 0, 0, W, H, "#030308");
        glow(themeDoc, "Halo", -90, cx, cy, r * 4.5, "#FF8C28", 60 / 255.0);
        glow(themeDoc, "Core glow", -89, cx, cy, r * 2.2, "#FFAA50", 80 / 255.0);
        diskRings(themeDoc, cx, cy, r);
        disk(themeDoc, cx, cy, r);
        obj(themeDoc, "ellipse", "Event horizon", -77, 0).v("x", cx - r).v("y", cy - r).v("w", r * 2).v("h", r * 2).v("color", "#000000");
        Obj ring = obj(themeDoc, "ring", "Photon ring", -76, 0).v("x", cx - r * 1.04).v("y", cy - r * 1.04)
                .v("w", r * 2.08).v("h", r * 2.08).v("stroke", r * 0.07).v("color", "#FFE6B4");
        ring.drive("alpha", ring.add(ring.mul(ring.sin(ring.time(2)), 40 / 255.0), 200 / 255.0));
        obj(themeDoc, "ring", "Outer ring", -75, 0).v("x", cx - r * 1.35).v("y", cy - r * 1.35).v("w", r * 2.7).v("h", r * 2.7)
                .v("stroke", r * 0.02).v("color", "#FFC878").v("alpha", 60 / 255.0);
        return themeDoc;
    }
    public static ThemeDoc sunset() {
        ThemeDoc themeDoc = doc("sunset", "Sunset", 4, "mm.bg_sunset");
        double horizon = H * 0.62, sunX = W / 2, sunY = horizon - H * 0.02;
        int hz = (int) horizon;
        obj(themeDoc, "rect", "Sky", -100, 0).v("x", 0).v("y", 0).v("w", W).v("h", hz + 1)
                .v("fill", "linear").v("color", "#120628").v("color2", "#781E5A").v("color3", "#FF7846").v("midAt", 0.6)
                .v("gradY0", 0).v("gradY1", horizon);
        Obj sun = obj(themeDoc, "ellipse", "Sun", -95, 0).v("fill", "linear").v("color", "#FFE678").v("color2", "#FF5A5A").v("gradY0", 0);
        N radius = sunRadius(sun);
        sun.drivePosition(sun.sub(sunX, radius), sun.sub(sunY, radius));
        sun.driveSize(sun.mul(radius, 2), sun.mul(radius, 2));
        sun.drive("gradY1", radius);
        for (int i = 0; i < 5; i++) {
            Obj s = obj(themeDoc, "rect", "Sun stripe " + (i + 1), -94, 0).v("x", 0).v("w", W).v("h", 6).v("color", "#781E5A");
            double k = -0.35 + i * 0.22 + i * i * 0.02;
            s.drive("y", s.floor(s.add(s.mul(sunRadius(s), k), sunY)));
        }
        obj(themeDoc, "rect", "Sea", -93, 0).v("x", 0).v("y", hz).v("w", W).v("h", H - hz)
                .v("fill", "linear").v("color", "#280832").v("color2", "#080210")
                .v("gradY0", horizon - hz).v("gradY1", H - hz);
        for (int i = 1; i <= 6; i++) {
            int y = (int) (horizon + i * i * H * 0.006);
            Obj s = obj(themeDoc, "rect", "Reflection " + i, -92, 0).v("y", y).v("h", 2)
                    .v("color", "#FF7846").v("alpha", (110 - 12 * i) / 255.0);
            N half = s.floor(s.mul(sunRadius(s), 1.1 - 0.12 * i));
            N shift = truncate(s, s.mul(s.sin(s.add(s.time(0.9), i)), 12));
            s.drive("x", s.add(s.sub(sunX, half), shift));
            s.drive("w", s.mul(half, 2));
        }
        return themeDoc;
    }
    public static ThemeDoc aurora() {
        ThemeDoc themeDoc = doc("aurora", "Aurora", 3, "mm.bg_aurora");
        double bottom = H * 0.62;
        rect(themeDoc, "Sky", -100, 0, 0, W, H, "#030614").v("fill", "linear").v("color2", "#0C1428").v("gradY0", 0).v("gradY1", H);
        String[] curtains = {"#3CFF96", "#28DCFF", "#A05AFF"};
        for (int k = 0; k < curtains.length; k++) {
            double freq = 0.011 + 0.004 * k;
            for (int part = 0; part < W / PART; part++) {
                int from = part * PART, to = from + PART;
                Obj c = obj(themeDoc, "polygon", "Curtain " + (k + 1) + " part " + (part + 1), -90 + k, 0);
                N phase = c.add(c.time(0.5 + 0.2 * k), 1.7 * k);
                N amp = c.add(c.mul(c.sin(c.add(c.time(0.7), k)), 0.3 * H * 0.06), H * 0.06);
                N half = c.mul(amp, 0.5);
                N base = c.add(c.mul(c.sin(c.add(c.time(0.3), k)), H * 0.02), H * (0.18 + 0.12 * k));
                N shimmer = c.add(c.mul(c.sin(c.add(c.time(1.3), 2.0 * k)), 10 / 255.0), 150 / 255.0);
                List<String> pts = new ArrayList<>();
                int index = 0;
                for (int x = from; x <= to; x += 8, index++) {
                    pts.add(x + ",0");
                    c.drive("p" + index + "y", c.add(c.mul(c.sin(c.add(phase, x * freq)), amp), base));
                }
                for (int x = to; x >= from; x -= 8, index++) {
                    pts.add(x + ",0");
                    c.drive("p" + index + "y", c.add(c.mul(c.sin(c.add(phase, x * freq * 0.7)), half), bottom));
                }
                c.v("points", String.join(" ", pts)).v("fill", "linear").v("color", curtains[k])
                        .v("color2", curtains[k]).v("alpha2", 40 / 255.0).v("color3", curtains[k]).v("alpha3", 0)
                        .v("midAt", 0.6).v("gradY1", bottom);
                c.drive("alpha", shimmer);
                c.drive("gradY0", base);
            }
        }
        obj(themeDoc, "polygon", "Ground", -80, 0).v("points", groundPoints()).v("color", "#020408").v("alpha", 1);
        return themeDoc;
    }
    public static ThemeDoc ocean() {
        ThemeDoc themeDoc = doc("ocean", "Ocean", 5, "mm.bg_ocean");
        rect(themeDoc, "Water", -100, 0, 0, W, H, "#0A4664").v("fill", "linear").v("color2", "#02081A").v("gradY0", 0).v("gradY1", H);
        double[][] rays = table(5L, 9);
        for (int i = 0; i < rays.length; i++) {
            double[] ray = rays[i];
            double top = ray[1] * W * 0.04, spread = ray[2] * W * 0.09 + 30;
            double alpha = ((int) (ray[3] * 45) + 15) / 255.0;
            double length = Math.hypot(spread, H), angle = Math.atan2(-spread, H);
            double cos = Math.cos(angle), sin = Math.sin(angle);
            double[][] corners = {{0, -10}, {top, -10}, {top + spread, H}, {spread * 0.2, H}};
            List<String> pts = new ArrayList<>();
            for (double[] c : corners) pts.add(pt(c[0] * cos + c[1] * sin, -c[0] * sin + c[1] * cos));
            Obj s = obj(themeDoc, "polygon", "Light ray " + (i + 1), -90 + i, 0).v("points", String.join(" ", pts))
                    .v("rot", Math.toDegrees(angle)).v("fill", "linear").v("color", "#B4F0FF").v("alpha", alpha)
                    .v("color2", "#B4F0FF").v("alpha2", 0).v("gradY0", 0).v("gradY1", length);
            s.drive("x", s.add(s.mul(s.sin(s.add(s.time(0.3), ray[3] * 6)), 30), ray[0] * W));
        }
        double[][] bubbles = table(6L, 60);
        for (int i = 0; i < bubbles.length; i++) {
            double[] b = bubbles[i];
            double speed = 25 + b[3] * 40, size = 2 + b[2] * 4;
            Obj ring = obj(themeDoc, "ring", "Bubble " + (i + 1), -80, 0).v("w", size).v("h", size).v("stroke", 1)
                    .v("color", "#C8F0FF").v("alpha", 90 / 255.0);
            ring.drivePosition(ring.add(ring.mul(ring.sin(ring.add(ring.time(1.5), b[2] * 10)), 6), b[0] * W - size / 2),
                    ring.add(ring.sub(H, ring.mod(ring.add(ring.time(speed), b[1] * H), H + 20)), -size / 2));
        }
        return themeDoc;
    }
    static ThemeDoc blank() {
        ThemeDoc themeDoc = doc("blank", "Blank", 100, null);
        rect(themeDoc, "Background", -100, 0, 0, W, H, "#000000");
        return themeDoc;
    }
    public static ThemeDoc starfield() {
        ThemeDoc themeDoc = doc("starfield", "Starfield", 100, null);
        rect(themeDoc, "Background", -100, 0, 0, W, H, "#050510");
        Obj group = obj(themeDoc, "empty", "Stars", -50, 0);
        Random random = new Random(3L);
        for (int i = 0; i < 120; i++) {
            Obj star = obj(themeDoc, "dot", "Star " + (i + 1), -50, group.o.id).v("x", random.nextDouble() * W)
                    .v("y", random.nextDouble() * H).v("radius", 0.6 + random.nextDouble() * 1.4).v("color", "#FFFFFF");
            double phase = random.nextDouble() * Math.PI * 2, speed = 0.5 + random.nextDouble() * 2;
            star.drive("alpha", star.add(star.mul(star.sin(star.add(star.time(speed), phase)), 0.35), 0.6));
        }
        return themeDoc;
    }
    public static ThemeDoc gradientSun() {
        ThemeDoc themeDoc = doc("sunset-template", "Gradient sun", 100, null);
        obj(themeDoc, "rect", "Sky", -100, 0).v("x", 0).v("y", 0).v("w", W).v("h", H)
                .v("fill", "linear").v("color", "#1B0B3A").v("color2", "#FF7A59");
        obj(themeDoc, "ellipse", "Sun", -90, 0).v("x", W / 2 - 110).v("y", H * 0.5 - 110).v("w", 220).v("h", 220).v("color", "#FFE678");
        return themeDoc;
    }
    //endregion

    //region Star and Disk Art
    private static void stars(ThemeDoc themeDoc) {
        Obj group = obj(themeDoc, "empty", "Stars", -50, 0);
        Random random = new Random(7L);
        for (int i = 0; i < 80; i++) {
            double x = random.nextInt((int) W), y0 = random.nextInt((int) H);
            int s = 15 + random.nextInt(30);
            double speed = 1 + random.nextDouble() * 4, rot = random.nextDouble() * 360, spin = -5 + random.nextDouble() * 10;
            Obj star = obj(themeDoc, "empty", "Star " + (i + 1), -50, group.o.id).v("x", x).v("y", y0).v("rot", rot);
            star.drive("y", star.sub(star.mod(star.add(star.time(60 * speed), y0 + s), H + 2 * s), s));
            star.drive("rot", star.add(star.time(60 * spin), rot));
            obj(themeDoc, "dot", "Star " + (i + 1) + " halo", -50, star.o.id).v("radius", s).v("color", "#FFFFB4").v("alpha", 80 / 255.0);
            int q = s / 4;
            String body = String.join(" ", pt(0, -s), pt(q, -q), pt(s, 0), pt(q, q), pt(0, s), pt(-q, q), pt(-s, 0), pt(-q, -q));
            obj(themeDoc, "polygon", "Star " + (i + 1) + " body", -50, star.o.id).v("points", body).v("color", "#FFFFFF");
        }
    }
    private static void diskRings(ThemeDoc themeDoc, double cx, double cy, double r) {
        Obj tilt = obj(themeDoc, "empty", "Disk rings", -80, 0).v("x", cx).v("y", cy).v("rot", TILT_DEG);
        for (int i = 0; i < 24; i++) {
            double u = i / 23.0, rx = r * (2.9 - 1.6 * u), ry = rx * FLATNESS;
            String color = hex(255, (int) (110 + 120 * u), (int) (40 + 150 * u));
            obj(themeDoc, "ellipse", "Disk ring " + (i + 1), -80, tilt.o.id).v("x", -rx).v("y", -ry).v("w", rx * 2).v("h", ry * 2)
                    .v("color", color).v("alpha", (int) (30 + 30 * u) / 255.0);
        }
    }
    private static void disk(ThemeDoc themeDoc, double cx, double cy, double r) {
        double[][] dots = diskDots();
        double[][] clumps = clumpDots();
        Obj dotGroup = obj(themeDoc, "empty", "Disk dots", -79, 0);
        for (double[] dot : dots) {
            double rho = dot[0], th0 = dot[1], omega = dot[2], factor = dot[3], sizeF = dot[4];
            double heat = Math.clamp((rho - 1.2) / 1.7, 0, 1);
            double size = Math.max(1.0, sizeF * r * (1.2 - 0.4 * heat));
            double[] pr = project(rho, r);
            Obj o = obj(themeDoc, "dot", "Disk dot", -79, dotGroup.o.id).v("radius", size / 2)
                    .v("color", hex(255, (int) (210 - 120 * heat), (int) (160 - 140 * heat))).v("alpha", 0);
            N t = o.time(omega);
            o.drivePosition(o.add(o.mul(o.cos(o.add(t, th0 - pr[1])), pr[0]), cx), o.add(o.mul(o.cos(o.add(t, th0 - pr[3])), pr[2]), cy));
            o.drive("layer", o.select(gate(o, t, th0, true), -74.0, -79.0));
            double k = factor * (1 - 0.5 * heat);
            o.drive("alpha", o.add(o.mul(o.cos(o.add(t, th0 - 0.6)), 0.225 * k), 0.775 * k));
        }
        Obj clumpGroup = obj(themeDoc, "empty", "Disk clumps", -78, 0);
        for (double[] clump : clumps) {
            double rho = clump[0], th0 = clump[1], omega = clump[2], factor = clump[3], span = clump[4];
            double[] pr = project(rho, r);
            Obj o = obj(themeDoc, "polyline", "Disk clump", -78, clumpGroup.o.id).v("points", "0,0 0,0 0,0 0,0 0,0 0,0 0,0")
                    .v("stroke", Math.max(1.5, r * 0.035)).v("color", "#FFEBBE").v("alpha", 0);
            N t = o.time(omega);
            for (int i = 0; i <= 6; i++) {
                double a = th0 + span * i / 6.0;
                o.drive("p" + i + "x", o.add(o.mul(o.cos(o.add(t, a - pr[1])), pr[0]), cx));
                o.drive("p" + i + "y", o.add(o.mul(o.cos(o.add(t, a - pr[3])), pr[2]), cy));
            }
            double mid = th0 + span / 2, amp = factor * 230 / 255.0;
            o.drive("layer", o.select(gate(o, t, mid, true), -73.0, -78.0));
            o.drive("alpha", o.add(o.mul(o.cos(o.add(t, mid - 0.6)), 0.2 * amp), 0.8 * amp));
        }
    }
    private static N gate(Obj obj, N t, double th0, boolean front) {
        N sinTheta = obj.cos(obj.add(t, th0 - Math.PI / 2));
        return front ? obj.gt(sinTheta, 0.0) : obj.le(sinTheta, 0.0);
    }
    private static double[] project(double rho, double r) {
        double tilt = Math.toRadians(TILT_DEG), cos = Math.cos(tilt), sin = Math.sin(tilt);
        double ax = r * rho * cos, bx = -FLATNESS * r * rho * sin;
        double ay = r * rho * sin, by = FLATNESS * r * rho * cos;
        return new double[]{Math.hypot(ax, bx), Math.atan2(bx, ax), Math.hypot(ay, by), Math.atan2(by, ay)};
    }
    private static double[][] diskDots() {
        Random random = new Random(11L);
        double[][] dots = new double[1400][];
        for (int i = 0; i < 1400; i++) {
            double radius = 1.22 + 1.7 * Math.pow(random.nextDouble(), 1.5);
            dots[i] = new double[]{radius, random.nextDouble() * Math.PI * 2, 0.9 / Math.pow(radius, 1.5),
                    0.35 + 0.65 * random.nextDouble(), 0.012 + 0.02 * random.nextDouble()};
        }
        return dots;
    }
    private static double[][] clumpDots() {
        Random random = new Random(12L);
        double[][] clumps = new double[48][];
        for (int i = 0; i < 48; i++) {
            double radius = 1.4 + 1.2 * random.nextDouble();
            clumps[i] = new double[]{radius, random.nextDouble() * Math.PI * 2, 0.9 / Math.pow(radius, 1.5),
                    0.6 + 0.4 * random.nextDouble(), 0.18 + 0.25 * random.nextDouble()};
        }
        return clumps;
    }
    private static N sunRadius(Obj obj) {
        return obj.add(obj.mul(obj.sin(obj.time(0.6)), 0.006 * H), 0.24 * H);
    }
    //endregion

    //region Aurora Geometry
    private static String groundPoints() {
        List<String> pts = new ArrayList<>();
        pts.add(pt(0, H));
        pts.add(pt(0, 0.86 * H));
        cubic(pts, 0, 0.86 * H, 0.2 * W, 0.78 * H, 0.35 * W, 0.92 * H, 0.55 * W, 0.84 * H);
        cubic(pts, 0.55 * W, 0.84 * H, 0.72 * W, 0.78 * H, 0.85 * W, 0.9 * H, W, 0.82 * H);
        pts.add(pt(W, H));
        return String.join(" ", pts);
    }
    private static void cubic(List<String> out, double x0, double y0, double x1, double y1, double x2, double y2, double x3, double y3) {
        for (int i = 1; i <= 64; i++) {
            double t = i / 64.0, u = 1 - t;
            out.add(pt(u * u * u * x0 + 3 * u * u * t * x1 + 3 * u * t * t * x2 + t * t * t * x3,
                    u * u * u * y0 + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t * t * t * y3));
        }
    }
    //endregion

    //region Numeric Helpers
    private static N truncate(Obj obj, N v) { return obj.mul(obj.sign(v), obj.floor(obj.abs(v))); }
    private static double[][] table(long seed, int count) {
        Random random = new Random(seed);
        double[][] rows = new double[count][4];
        for (double[] row : rows) for (int c = 0; c < 4; c++) row[c] = random.nextDouble();
        return rows;
    }
    private static String pt(double x, double y) { return x + "," + y; }
    private static String hex(int r, int g, int b) { return String.format("#%02X%02X%02X", r, g, b); }
    private static String fmt(double v) { return String.valueOf(v); }
    //endregion

    //region Object Builders
    private static ThemeDoc doc(String id, String name, int order, String nameKey) {
        ThemeDoc themeDoc = new ThemeDoc();
        themeDoc.id = id;
        themeDoc.name = name;
        themeDoc.order = order;
        themeDoc.nameKey = nameKey;
        return themeDoc;
    }
    private static Obj obj(ThemeDoc themeDoc, String type, String name, int layer, int parent) {
        ThemeObject themeObject = themeDoc.add(type, parent);
        themeObject.name = name;
        themeObject.layer = layer;
        return new Obj(themeObject);
    }
    private static Obj rect(ThemeDoc themeDoc, String name, int layer, double x, double y, double w, double h, String color) {
        return obj(themeDoc, "rect", name, layer, 0).v("x", x).v("y", y).v("w", w).v("h", h).v("color", color);
    }
    private static Obj glow(ThemeDoc themeDoc, String name, int layer, double x, double y, double radius, String color, double alpha) {
        return obj(themeDoc, "glow", name, layer, 0).v("x", x).v("y", y).v("radius", radius).v("color", color).v("alpha", alpha);
    }
    public static void compressLayers(ThemeDoc themeDoc) {
        for (ThemeObject themeObject : themeDoc.objects) {
            for (NodeInst nodeInst : themeObject.graph.nodes) if (nodeInst.kind.equals("th.set.layer")) return;
        }
        TreeSet<Integer> used = new TreeSet<>();
        for (ThemeObject themeObject : themeDoc.objects) used.add(themeObject.layer);
        Map<Integer, Integer> rank = new HashMap<>();
        int r = 0;
        for (int layer : used) rank.put(layer, r++);
        for (ThemeObject themeObject : themeDoc.objects) themeObject.layer = rank.get(themeObject.layer);
    }
    //endregion

    //region Object Graph
    public static final class Obj {
        final ThemeObject o;
        final CkProject p;

        Obj(ThemeObject themeObject) {
            this.o = themeObject;
            this.p = themeObject.graph;
            ThemeNodes.install();
        }

        Obj v(String key, Object value) {
            if (value instanceof Number n) o.props.put(key, n.doubleValue());
            else o.props.put(key, value);
            return this;
        }
        void drive(String key, Object value) {
            boolean point = key.matches("p\\d+[xy]");
            String kind = point ? (key.endsWith("x") ? "th.set.pointX" : "th.set.pointY") : "th.set." + key;
            NodeInst nodeInst = p.add(kind, 0, 0, 0);
            if (point) nodeInst.params.put("index", key.substring(1, key.length() - 1));
            input(nodeInst, "v", value);
        }
        void drivePosition(Object x, Object y) { setter("th.setPosition", null, "x", x, "y", y); }
        void driveSize(Object w, Object h) { setter("th.setSize", null, "w", w, "h", h); }

        N time(double speed) {
            NodeInst nodeInst = p.add("th.time", 0, 0, 0);
            nodeInst.params.put("speed", fmt(speed));
            return new N(nodeInst.id);
        }
        N sin(Object a) { return op("th.sin", a); }
        N cos(Object a) { return op("th.cos", a); }
        N abs(Object a) { return op("th.abs", a); }
        N sign(Object a) { return op("th.sign", a); }
        N floor(Object a) { return op("th.floor", a); }
        N add(Object a, Object b) { return op("th.add", a, b); }
        N sub(Object a, Object b) { return op("th.sub", a, b); }
        N mul(Object a, Object b) { return op("th.mul", a, b); }
        N mod(Object a, Object b) { return op("th.mod", a, b); }
        N le(Object a, Object b) { return op("th.le", a, b); }
        N gt(Object a, Object b) { return op("th.gt", a, b); }
        N select(Object cond, Object a, Object b) { return op("th.select", cond, a, b); }
        private N op(String kind, Object... inputs) {
            NodeKind nodeKind = Nodes.get(kind);
            NodeInst nodeInst = p.add(kind, 0, 0, 0);
            for (int i = 0; i < inputs.length; i++) input(nodeInst, nodeKind.inputs().get(i).name(), inputs[i]);
            return new N(nodeInst.id);
        }

        private void input(NodeInst nodeInst, String port, Object value) {
            if (value instanceof N from) p.links.add(new Link(from.id(), "out", nodeInst.id, port));
            else nodeInst.params.put(port, fmt(((Number) value).doubleValue()));
        }
        private void setter(String kind, String prefix, Object... pairs) {
            NodeInst nodeInst = p.add(kind, 0, 0, 0);
            if (prefix != null) nodeInst.params.put("prefix", prefix);
            for (int i = 0; i < pairs.length; i += 2) input(nodeInst, (String) pairs[i], pairs[i + 1]);
        }
    }
    public record N(int id) {

    }
    //endregion
}