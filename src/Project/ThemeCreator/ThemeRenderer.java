package Project.ThemeCreator;
import Project.EngineCreator.*;
import Project.CreatorKit.*;

import java.awt.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;

public final class ThemeRenderer {
    //region Variables
    //region Render Settings
    public static final int PARALLEL_OBJECTS = 64;
    //endregion
    //region Caches
    private static final Map<String, Color> COLORS = new java.util.concurrent.ConcurrentHashMap<>();
    //endregion
    //endregion

    //region Constructors
    private ThemeRenderer() {}
    //endregion

    //region Frame Building
    public static Frame frame(ThemeDoc doc, double t) {
        Map<Integer, Map<String, Object>> props = propsAt(doc, t);
        return new Frame(props, worldsFrom(doc, props), drawOrder(doc, props));
    }
    public static List<ThemeObject> drawOrder(ThemeDoc doc, Map<Integer, Map<String, Object>> props) {
        List<ThemeObject> out = new ArrayList<>();
        for (ThemeObject themeObject : doc.treeOrder()) if (visibleAt(themeObject, props)) out.add(themeObject);
        out.sort(Comparator.comparingInt(themeObject -> layerAt(themeObject, props)));
        return out;
    }
    public static boolean visibleAt(ThemeObject themeObject, Map<Integer, Map<String, Object>> props) {
        Map<String, Object> p = props.get(themeObject.id);
        return p != null && p.get("visible") instanceof Boolean b ? b : themeObject.visible;
    }
    @SuppressWarnings("unchecked")
    static Map<Integer, Map<String, Object>> propsAt(ThemeDoc doc, double t) {
        int n = doc.objects.size();
        Map<String, Object>[] overrides = new Map[n];
        if (n >= PARALLEL_OBJECTS) {
            java.util.stream.IntStream.range(0, n).parallel()
                    .forEach(i -> overrides[i] = ThemeGraph.evaluate(doc.objects.get(i).graph, t));
        } else {
            for (int i = 0; i < n; i++) overrides[i] = ThemeGraph.evaluate(doc.objects.get(i).graph, t);
        }
        Map<Integer, Map<String, Object>> props = new HashMap<>(n * 2);
        for (int i = 0; i < n; i++) {
            ThemeObject themeObject = doc.objects.get(i);
            Map<String, Object> over = overrides[i];
            Map<String, Object> eff = over.isEmpty() ? themeObject.props : new HashMap<>(themeObject.props);
            if (!over.isEmpty()) eff.putAll(over);
            props.put(themeObject.id, eff);
        }
        return props;
    }
    public static Map<Integer, AffineTransform> worldsAt(ThemeDoc doc, double t) {
        return worldsFrom(doc, propsAt(doc, t));
    }
    public static Map<Integer, AffineTransform> worldsFrom(ThemeDoc doc, Map<Integer, Map<String, Object>> props) {
        Map<Integer, ThemeObject> byId = new HashMap<>();
        for (ThemeObject themeObject : doc.objects) byId.put(themeObject.id, themeObject);
        Map<Integer, AffineTransform> world = new HashMap<>();
        for (ThemeObject themeObject : doc.objects) worldOf(byId, themeObject, props, world, new HashSet<>());
        return world;
    }
    public static double[] tilt(Map<String, Object> p) {
        double cosX = Math.max(1e-3, Math.cos(Math.toRadians(d(p, "rotX", 0))));
        double cosY = Math.max(1e-3, Math.cos(Math.toRadians(d(p, "rotY", 0))));
        return new double[]{cosX, cosY};
    }
    private static AffineTransform worldOf(Map<Integer, ThemeObject> byId, ThemeObject themeObject, Map<Integer, Map<String, Object>> props,
                                           Map<Integer, AffineTransform> world, Set<Integer> busy) {
        AffineTransform done = world.get(themeObject.id);
        if (done != null) return done;
        if (!busy.add(themeObject.id)) return new AffineTransform();
        ThemeObject up = byId.get(themeObject.parent);
        AffineTransform parent = up == null ? new AffineTransform() : worldOf(byId, up, props, world, busy);
        Map<String, Object> p = props.get(themeObject.id);
        AffineTransform local = new AffineTransform();
        local.translate(d(p, "x", 0), d(p, "y", 0));
        local.rotate(Math.toRadians(d(p, "rot", 0)));
        double[] tilt = tilt(p);
        local.scale(d(p, "scale", 1) * tilt[1], d(p, "scaleY", d(p, "scale", 1)) * tilt[0]);
        AffineTransform all = new AffineTransform(parent);
        all.concatenate(local);
        world.put(themeObject.id, all);
        return all;
    }
    private static int layerAt(ThemeObject themeObject, Map<Integer, Map<String, Object>> props) {
        Map<String, Object> p = props.get(themeObject.id);
        return p != null && p.get("layer") instanceof Number n ? ThemeDoc.clampLayer(n.intValue()) : themeObject.layer;
    }
    public static final class Frame {
        final Map<Integer, Map<String, Object>> props;
        final Map<Integer, AffineTransform> world;
        final List<ThemeObject> order;

        Frame(Map<Integer, Map<String, Object>> props, Map<Integer, AffineTransform> world, List<ThemeObject> order) {
            this.props = props;
            this.world = world;
            this.order = order;
        }
    }
    //endregion

    //region Painting
    public static void paint(Graphics2D g2d, ThemeDoc doc, double t, int w, int h) {
        paint(g2d, doc, frame(doc, t), w, h);
    }
    public static void paint(Graphics2D g2d, ThemeDoc doc, Frame frame, int w, int h) {
        if (ThemeGpu.paint(g2d, frame, w, h)) return;
        AffineTransform base = g2d.getTransform();
        Graphics2D workG2d = (Graphics2D) g2d.create();
        try {
            for (ThemeObject themeObject : frame.order) {
                AffineTransform affineTransform = frame.world.get(themeObject.id);
                if (affineTransform == null) continue;
                workG2d.setTransform(base);
                workG2d.transform(affineTransform);
                draw(workG2d, themeObject.type, frame.props.get(themeObject.id), affineTransform, base, w, h);
            }
        } finally {
            workG2d.dispose();
        }
    }
    private static void draw(Graphics2D g2d, String type, Map<String, Object> p, AffineTransform world, AffineTransform base, int w, int h) {
        switch (type) {
            case "rect" -> fill(g2d, new Rectangle2D.Double(0, 0, d(p, "w", 100), d(p, "h", 100)), p);
            case "ellipse" -> fill(g2d, new Ellipse2D.Double(0, 0, d(p, "w", 100), d(p, "h", 100)), p);
            case "polygon" -> fill(g2d, polygon(p, true), p);
            case "ring" -> {
                stroke(g2d, p);
                g2d.draw(new Ellipse2D.Double(0, 0, d(p, "w", 100), d(p, "h", 100)));
            }
            case "arc" -> {
                stroke(g2d, p);
                g2d.draw(new Arc2D.Double(0, 0, d(p, "w", 100), d(p, "h", 100), d(p, "start", 0), d(p, "extent", 90), Arc2D.OPEN));
            }
            case "polyline" -> {
                stroke(g2d, p);
                g2d.draw(polygon(p, false));
            }
            case "glow" -> glow(g2d, p, world, base, w, h);
            case "dot" -> {
                double r = Math.max(0, d(p, "radius", 2));
                g2d.setColor(withAlpha(c(p, "color", Color.WHITE), d(p, "alpha", 1)));
                g2d.fill(new Ellipse2D.Double(-r, -r, 2 * r, 2 * r));
            }
            default -> { }
        }
    }
    private static void stroke(Graphics2D g2d, Map<String, Object> p) {
        g2d.setStroke(new BasicStroke((float) Math.max(0.1, d(p, "stroke", 2))));
        g2d.setColor(withAlpha(c(p, "color", Color.WHITE), d(p, "alpha", 1)));
    }
    private static void fill(Graphics2D g2d, Shape shape, Map<String, Object> p) {
        double a1 = d(p, "alpha", 1);
        Color first = withAlpha(c(p, "color", Color.WHITE), a1);
        Color second = c(p, "color2", null), third = c(p, "color3", null);
        if (!"linear".equals(s(p, "fill", "solid")) || second == null) {
            g2d.setColor(first);
            g2d.fill(shape);
            return;
        }
        double y0 = d(p, "gradY0", 0), y1 = d(p, "gradY1", d(p, "h", 100));
        if (y1 == y0) y1 = y0 + 1;
        Color color = withAlpha(second, d(p, "alpha2", a1));
        if (third != null) {
            double at = Math.max(0.001, Math.min(0.999, d(p, "midAt", 0.5)));
            Color k3 = withAlpha(third, d(p, "alpha3", a1));
            g2d.setPaint(new LinearGradientPaint(0, (float) y0, 0, (float) y1, new float[]{0f, (float) at, 1f},
                    new Color[]{first, color, k3}));
        } else {
            g2d.setPaint(new LinearGradientPaint(0, (float) y0, 0, (float) y1, new float[]{0f, 1f}, new Color[]{first, color}));
        }
        g2d.fill(shape);
    }
    private static void glow(Graphics2D g2d, Map<String, Object> p, AffineTransform world, AffineTransform base, int w, int h) {
        double radius = d(p, "radius", 100);
        Color color = c(p, "color", Color.WHITE);
        int a = alphaByte(d(p, "alpha", 1));
        Point2D centre = world.transform(new Point2D.Double(0, 0), null);
        double scale = Math.sqrt(Math.abs(world.getDeterminant()));
        Color clear = new Color(color.getRed(), color.getGreen(), color.getBlue(), 0);
        Color mid = new Color(color.getRed(), color.getGreen(), color.getBlue(), a * 3 / 4);
        g2d.setTransform(base);
        double r = Math.max(0.01, radius * scale);
        g2d.setPaint(new RadialGradientPaint(centre, (float) r, new float[]{0f, 0.5f, 1f},
                new Color[]{new Color(color.getRed(), color.getGreen(), color.getBlue(), a), mid, clear}));
        int x0 = (int) Math.max(0, Math.floor(centre.getX() - r)), y0 = (int) Math.max(0, Math.floor(centre.getY() - r));
        int x1 = (int) Math.min(w, Math.ceil(centre.getX() + r) + 1), y1 = (int) Math.min(h, Math.ceil(centre.getY() + r) + 1);
        if (x1 > x0 && y1 > y0) g2d.fillRect(x0, y0, x1 - x0, y1 - y0);
    }
    private static Path2D.Double polygon(Map<String, Object> p, boolean closed) {
        Path2D.Double path = new Path2D.Double();
        List<double[]> pts = points(p);
        for (int i = 0; i < pts.size(); i++) {
            if (i == 0) path.moveTo(pts.get(i)[0], pts.get(i)[1]);
            else path.lineTo(pts.get(i)[0], pts.get(i)[1]);
        }
        if (closed && !pts.isEmpty()) path.closePath();
        return path;
    }
    private static Ellipse2D.Double circle(double r) { return new Ellipse2D.Double(-r, -r, 2 * r, 2 * r); }
    //endregion

    //region Geometry and Hit Tests
    public static List<double[]> points(Map<String, Object> p) {
        List<double[]> out = new ArrayList<>();
        for (String part : s(p, "points", "").trim().split("[\\s;]+")) {
            String[] xy = part.split(",");
            if (xy.length < 2) continue;
            try {
                out.add(new double[]{Double.parseDouble(xy[0]), Double.parseDouble(xy[1])});
            } catch (NumberFormatException exception) { }
        }
        for (int i = 0; i < out.size(); i++) {
            if (p.get("p" + i + "x") instanceof Number nx) out.get(i)[0] = nx.doubleValue();
            if (p.get("p" + i + "y") instanceof Number ny) out.get(i)[1] = ny.doubleValue();
        }
        return out;
    }
    public static Shape outline(String type, Map<String, Object> p) {
        return switch (type) {
            case "rect" -> new Rectangle2D.Double(0, 0, d(p, "w", 100), d(p, "h", 100));
            case "ellipse", "ring" -> new Ellipse2D.Double(0, 0, d(p, "w", 100), d(p, "h", 100));
            case "arc" -> new Arc2D.Double(0, 0, d(p, "w", 100), d(p, "h", 100), d(p, "start", 0), d(p, "extent", 90), Arc2D.OPEN);
            case "glow" -> circle(d(p, "radius", 100));
            case "dot" -> circle(Math.max(2, d(p, "radius", 2)));
            case "polygon" -> polygon(p, true);
            case "polyline" -> polygon(p, false);
            default -> null;
        };
    }
    public static boolean hit(String type, Map<String, Object> p, Point2D local, double tol) {
        Shape shape = outline(type, p);
        if (shape == null) return false;
        return switch (type) {
            case "rect", "ellipse", "polygon", "glow", "dot" -> shape.contains(local)
                    || type.equals("glow") && local.distance(0, 0) <= d(p, "radius", 100);
            default -> new BasicStroke((float) (Math.max(0.1, d(p, "stroke", 2)) + 2 * tol)).createStrokedShape(shape).contains(local);
        };
    }
    //endregion

    //region Property Access
    static double d(Map<String, Object> p, String key, double fallback) {
        Object v = p.get(key);
        return v instanceof Number n ? n.doubleValue() : fallback;
    }
    public static String s(Map<String, Object> p, String key, String fallback) {
        Object v = p.get(key);
        return v instanceof String str ? str : fallback;
    }
    public static Color c(Map<String, Object> p, String key, Color fallback) {
        Object v = p.get(key);
        if (v instanceof Color col) return col;
        if (v instanceof String str) {
            Color parsed = COLORS.get(str);
            if (parsed == null) {
                parsed = ThemeGraph.parseColor(str, fallback);
                if (parsed != null && COLORS.size() < 4096) COLORS.put(str, parsed);
            }
            return parsed;
        }
        return fallback;
    }
    //endregion

    //region Colour Helpers
    public static Color withAlpha(Color color, double alpha) {
        if (color == null) return null;
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alphaByte(alpha));
    }
    private static int alphaByte(double a) { return (int) Math.round(255 * Math.max(0, Math.min(1, a))); }
    //endregion
}