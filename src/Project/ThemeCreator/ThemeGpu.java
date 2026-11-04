package Project.ThemeCreator;

import Project.Vulkan.Vk;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class ThemeGpu {
    //region Variables
    //region Shape Quality
    private static final int SEGMENTS = 96;
    private static final int DOT_SEGMENTS = 24;
    private static final double[] UNIT_COS = new double[DOT_SEGMENTS], UNIT_SIN = new double[DOT_SEGMENTS];
    static {
        for (int i = 0; i < DOT_SEGMENTS; i++) {
            double t = Math.toRadians(360.0 * i / DOT_SEGMENTS);
            UNIT_COS[i] = Math.cos(t);
            UNIT_SIN[i] = Math.sin(t);
        }
    }
    //endregion
    //region Runtime State
    private static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("theme.gpu"));
    private static final int MAX_SIDE = 8192;
    private static volatile boolean failed;
    //endregion
    //endregion

    //region Constructors
    private ThemeGpu() {}
    //endregion

    //region Lifecycle
    static void setEnabled(boolean on) { enabled = on; }
    static boolean isEnabled() { return enabled && !failed; }
    static void warmUp() {
        if (!enabled) return;
        Thread t = new Thread(Vk::gpu, "vulkan-init");
        t.setDaemon(true);
        t.start();
    }
    static boolean paint(Graphics2D g2d, ThemeRenderer.Frame frame, int w, int h) {
        if (!enabled || failed) return false;
        try { return paintOnGpu(g2d, frame, w, h); }
        catch (Throwable exception) {
            failed = true;
            System.err.println("theme GPU renderer stopped, using Java2D from now on: " + exception);
            return false;
        }
    }
    //endregion

    //region Geometry

    static List<double[]> ellipse(double cx, double cy, double rx, double ry, double start, double extent, int n) {
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i <= n; i++) {
            if (extent == 360 && i == n) break;
            double t = Math.toRadians(start + extent * i / n);
            out.add(new double[]{cx + rx * Math.cos(t), cy - ry * Math.sin(t)});
        }
        return out;
    }
    static List<double[]> circle(double cx, double cy, double r, int n) {
        return ellipse(cx, cy, r, r, 0, 360, n);
    }
    static List<double[]> convex(double[][] corners) {
        List<double[]> pts = new ArrayList<>();
        for (double[] c : corners) pts.add(c);
        return convex(pts);
    }
    static List<double[]> convex(List<double[]> pts) {
        List<double[]> tri = new ArrayList<>();
        for (int i = 1; i + 1 < pts.size(); i++) {
            tri.add(pts.get(0));
            tri.add(pts.get(i));
            tri.add(pts.get(i + 1));
        }
        return tri;
    }
    static List<double[]> cut(List<double[]> tris, double[] lines) {
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i + 2 < tris.size(); i += 3) {
            List<List<double[]>> pieces = new ArrayList<>();
            pieces.add(List.of(tris.get(i), tris.get(i + 1), tris.get(i + 2)));
            for (double c : lines) {
                List<List<double[]>> next = new ArrayList<>();
                for (List<double[]> piece : pieces) {
                    for (boolean above : new boolean[]{true, false}) {
                        List<double[]> part = clipHalf(piece, c, above);
                        if (part.size() >= 3) next.add(part);
                    }
                }
                pieces = next;
            }
            for (List<double[]> piece : pieces) out.addAll(convex(piece));
        }
        return out;
    }
    static List<double[]> triangles(List<double[]> poly) {
        int n = poly.size();
        List<double[]> out = new ArrayList<>();
        if (n < 3) return out;
        double area = 0;
        for (int i = 0; i < n; i++) {
            double[] a = poly.get(i), b = poly.get((i + 1) % n);
            area += a[0] * b[1] - b[0] * a[1];
        }
        double sign = area >= 0 ? 1 : -1;
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < n; i++) idx.add(i);
        int guard = 0;
        while (idx.size() > 3 && guard++ < n * n) {
            boolean clipped = false;
            for (int k = 0; k < idx.size() && !clipped; k++) {
                int m = idx.size();
                int i0 = idx.get((k + m - 1) % m), i1 = idx.get(k), i2 = idx.get((k + 1) % m);
                double[] a = poly.get(i0), b = poly.get(i1), c = poly.get(i2);
                if (sign * orient(a, b, c) <= 0) continue;
                boolean inside = false;
                for (int j : idx) {
                    if (j == i0 || j == i1 || j == i2) continue;
                    double[] q = poly.get(j);
                    if (sign * orient(a, b, q) >= 0 && sign * orient(b, c, q) >= 0 && sign * orient(c, a, q) >= 0) {
                        inside = true;
                        break;
                    }
                }
                if (inside) continue;
                out.add(a);
                out.add(b);
                out.add(c);
                idx.remove(k);
                clipped = true;
            }
            if (!clipped) break;
        }
        if (idx.size() == 3) {
            out.add(poly.get(idx.get(0)));
            out.add(poly.get(idx.get(1)));
            out.add(poly.get(idx.get(2)));
        }
        return out;
    }
    private static List<double[]> clipHalf(List<double[]> poly, double c, boolean above) {
        List<double[]> out = new ArrayList<>();
        int n = poly.size();
        for (int i = 0; i < n; i++) {
            double[] p = poly.get(i), q = poly.get((i + 1) % n);
            boolean pIn = above ? p[1] >= c : p[1] <= c;
            boolean qIn = above ? q[1] >= c : q[1] <= c;
            if (pIn) out.add(p);
            if (pIn != qIn) {
                double t = (c - p[1]) / (q[1] - p[1]);
                out.add(new double[]{p[0] + t * (q[0] - p[0]), c});
            }
        }
        return out;
    }
    private static double orient(double[] a, double[] b, double[] c) {
        return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
    }
    private static double[] tf(AffineTransform affineTransform, double x, double y) {
        double[] out = new double[2];
        affineTransform.transform(new double[]{x, y}, 0, out, 0, 1);
        return out;
    }
    static final class Triangles {
        float[] data = new float[6 * 3 * 256];
        private int n;

        int count() { return n / 6; }
        void add(AffineTransform affineTransform, double x, double y, float[] col) {
            put((float) (affineTransform.getScaleX() * x + affineTransform.getShearX() * y + affineTransform.getTranslateX()),
                (float) (affineTransform.getShearY() * x + affineTransform.getScaleY() * y + affineTransform.getTranslateY()), col);
        }
        void addDevice(double[] p, float[] col) {
            put((float) p[0], (float) p[1], col);
        }
        private void put(float x, float y, float[] col) {
            if (n + 6 > data.length) data = java.util.Arrays.copyOf(data, data.length * 2);
            data[n++] = x;
            data[n++] = y;
            data[n++] = col[0];
            data[n++] = col[1];
            data[n++] = col[2];
            data[n++] = col[3];
        }
    }
    //endregion

    //region Object Drawing
    private static boolean paintOnGpu(Graphics2D g2d, ThemeRenderer.Frame frame, int w, int h) throws Throwable {
        AffineTransform base = g2d.getTransform();
        if (base.getShearX() != 0 || base.getShearY() != 0 || base.getScaleX() <= 0 || base.getScaleY() <= 0) return false;
        int ox = (int) Math.floor(base.getTranslateX()), oy = (int) Math.floor(base.getTranslateY());
        int tw = (int) Math.ceil(base.getTranslateX() + w * base.getScaleX()) - ox;
        int th = (int) Math.ceil(base.getTranslateY() + h * base.getScaleY()) - oy;
        if (tw <= 0 || th <= 0 || tw > MAX_SIDE || th > MAX_SIDE) return false;
        Vk.Gpu gpu = Vk.gpu();
        if (gpu == null) return false;

        AffineTransform toTarget = AffineTransform.getTranslateInstance(-ox, -oy);
        toTarget.concatenate(base);
        Triangles tris = new Triangles();
        for (ThemeObject themeObject : frame.order) {
            AffineTransform world = frame.world.get(themeObject.id);
            if (world == null) continue;
            AffineTransform affineTransform = new AffineTransform(toTarget);
            affineTransform.concatenate(world);
            emit(tris, themeObject.type, frame.props.get(themeObject.id), affineTransform, world);
        }
        try {
            BufferedImage img = gpu.render(tris.data, tris.count(), tw, th);
            if (tris.count() > 0 && uniform(img))
                throw new IllegalStateException("the GPU returned an empty or white frame for " + tris.count() + " triangles");
            AffineTransform saved = g2d.getTransform();
            g2d.setTransform(new AffineTransform());
            g2d.drawImage(img, ox, oy, null);
            g2d.setTransform(saved);
            return true;
        } catch (Throwable exception) {
            failed = true;
            System.err.println("theme GPU renderer stopped, using Java2D from now on: " + exception);
            return false;
        }
    }
    private static void emit(Triangles tr, String type, Map<String, Object> p, AffineTransform affineTransform, AffineTransform world) {
        switch (type) {
            case "rect" -> {
                double w = ThemeRenderer.d(p, "w", 100), h = ThemeRenderer.d(p, "h", 100);
                fill(tr, convex(new double[][]{{0, 0}, {w, 0}, {w, h}, {0, h}}), p, affineTransform, null);
            }
            case "ellipse" -> {
                double w = ThemeRenderer.d(p, "w", 100), h = ThemeRenderer.d(p, "h", 100);
                fill(tr, convex(ellipse(w / 2, h / 2, w / 2, h / 2, 0, 360, SEGMENTS)), p, affineTransform, null);
            }
            case "polygon" -> fill(tr, triangles(ThemeRenderer.points(p)), p, affineTransform, null);
            case "ring" -> {
                double w = ThemeRenderer.d(p, "w", 100), h = ThemeRenderer.d(p, "h", 100);
                double s = Math.max(0.1, ThemeRenderer.d(p, "stroke", 2)) / 2;
                strip(tr, ellipse(w / 2, h / 2, w / 2 + s, h / 2 + s, 0, 360, SEGMENTS),
                        ellipse(w / 2, h / 2, w / 2 - s, h / 2 - s, 0, 360, SEGMENTS), stroked(p), affineTransform, true);
            }
            case "arc" -> {
                double w = ThemeRenderer.d(p, "w", 100), h = ThemeRenderer.d(p, "h", 100);
                double s = Math.max(0.1, ThemeRenderer.d(p, "stroke", 2)) / 2;
                double start = ThemeRenderer.d(p, "start", 0), extent = ThemeRenderer.d(p, "extent", 90);
                int n = Math.max(2, (int) Math.ceil(Math.abs(extent) / 360 * SEGMENTS));
                strip(tr, ellipse(w / 2, h / 2, w / 2 + s, h / 2 + s, start, extent, n),
                        ellipse(w / 2, h / 2, w / 2 - s, h / 2 - s, start, extent, n), stroked(p), affineTransform, false);
            }
            case "polyline" -> {
                double s = Math.max(0.1, ThemeRenderer.d(p, "stroke", 2)) / 2;
                List<double[]> pts = ThemeRenderer.points(p);
                for (int i = 0; i + 1 < pts.size(); i++) {
                    double[] a = pts.get(i), b = pts.get(i + 1);
                    double dx = b[0] - a[0], dy = b[1] - a[1], len = Math.hypot(dx, dy);
                    if (len == 0) continue;
                    double nx = -dy / len * s, ny = dx / len * s;
                    double[][] quad = {{a[0] + nx, a[1] + ny}, {a[0] - nx, a[1] - ny}, {b[0] - nx, b[1] - ny}, {b[0] + nx, b[1] + ny}};
                    fill(tr, convex(quad), null, affineTransform, stroked(p));
                }
            }
            case "dot" -> {
                double r = Math.max(0, ThemeRenderer.d(p, "radius", 2));
                float[] col = premult(withAlpha(p));
                for (int i = 0; i < DOT_SEGMENTS; i++) {
                    int j = (i + 1) % DOT_SEGMENTS;
                    tr.add(affineTransform, 0, 0, col);
                    tr.add(affineTransform, r * UNIT_COS[i], -r * UNIT_SIN[i], col);
                    tr.add(affineTransform, r * UNIT_COS[j], -r * UNIT_SIN[j], col);
                }
            }
            case "glow" -> glow(tr, p, world, affineTransform);
            default -> { }
        }
    }
    private static Color stroked(Map<String, Object> p) {
        return ThemeRenderer.withAlpha(ThemeRenderer.c(p, "color", Color.WHITE), ThemeRenderer.d(p, "alpha", 1));
    }
    private static Color withAlpha(Map<String, Object> p) {
        return ThemeRenderer.withAlpha(ThemeRenderer.c(p, "color", Color.WHITE), ThemeRenderer.d(p, "alpha", 1));
    }
    private static void fill(Triangles triangles, List<double[]> shapeTriangles, Map<String, Object> p, AffineTransform affineTransform, Color solid) {
        Gradient grad = p == null || solid != null ? null : Gradient.of(p);
        Color plain = solid != null ? solid : grad == null ? withAlpha(p) : null;
        if (grad != null) shapeTriangles = cut(shapeTriangles, grad.lines());
        float[] solidColor = grad == null ? premult(plain) : null;
        for (int i = 0; i + 2 < shapeTriangles.size(); i += 3) {
            for (int k = 0; k < 3; k++) {
                double[] v = shapeTriangles.get(i + k);
                triangles.add(affineTransform, v[0], v[1], solidColor != null ? solidColor : premult(grad.at(v[1])));
            }
        }
    }
    private static void glow(Triangles triangles, Map<String, Object> p, AffineTransform world, AffineTransform affineTransform) {
        double radius = ThemeRenderer.d(p, "radius", 100);
        Color color = ThemeRenderer.c(p, "color", Color.WHITE);
        int a = alphaByte(ThemeRenderer.d(p, "alpha", 1));
        double[] centre = tf(affineTransform, 0, 0);
        double r = Math.max(0.01, radius * Math.sqrt(Math.abs(affineTransform.getDeterminant())));
        Color inner = new Color(color.getRed(), color.getGreen(), color.getBlue(), a);
        Color mid = new Color(color.getRed(), color.getGreen(), color.getBlue(), a * 3 / 4);
        Color clear = new Color(color.getRed(), color.getGreen(), color.getBlue(), 0);
        List<double[]> r1 = circle(centre[0], centre[1], r / 2, SEGMENTS);
        List<double[]> r2 = circle(centre[0], centre[1], r, SEGMENTS);
        for (int i = 0; i < SEGMENTS; i++) {
            int j = (i + 1) % SEGMENTS;
            triangles.addDevice(centre, premult(inner));
            triangles.addDevice(r1.get(i), premult(mid));
            triangles.addDevice(r1.get(j), premult(mid));
            triangles.addDevice(r1.get(i), premult(mid));
            triangles.addDevice(r2.get(i), premult(clear));
            triangles.addDevice(r2.get(j), premult(clear));
            triangles.addDevice(r1.get(i), premult(mid));
            triangles.addDevice(r2.get(j), premult(clear));
            triangles.addDevice(r1.get(j), premult(mid));
        }
    }
    private static void strip(Triangles triangles, List<double[]> outer, List<double[]> inner, Color col, AffineTransform affineTransform, boolean closed) {
        int n = Math.min(outer.size(), inner.size());
        int segs = closed ? n : n - 1;
        float[] pc = premult(col);
        for (int i = 0; i < segs; i++) {
            int j = closed ? (i + 1) % n : i + 1;
            double[] o1 = outer.get(i), o2 = outer.get(j), i1 = inner.get(i), i2 = inner.get(j);
            for (double[] v : new double[][]{o1, i1, i2, o1, i2, o2}) triangles.add(affineTransform, v[0], v[1], pc);
        }
    }
    //endregion

    //region Colors
    static float[] premult(Color color) {
        float a = color.getAlpha() / 255f;
        return new float[]{color.getRed() / 255f * a, color.getGreen() / 255f * a, color.getBlue() / 255f * a, a};
    }
    private static boolean uniform(BufferedImage img) {
        int[] px = ((java.awt.image.DataBufferInt) img.getRaster().getDataBuffer()).getData();
        if (px.length == 0) return true;
        int first = px[0];
        if (first != 0 && first != 0xFFFFFFFF) return false;
        for (int p : px) if (p != first) return false;
        return true;
    }
    private static int alphaByte(double a) { return (int) Math.round(255 * Math.max(0, Math.min(1, a))); }
    private static final class Gradient {
        final double y0, y1, at;
        final Color first, second, third;

        private Gradient(double y0, double y1, double at, Color first, Color second, Color third) {
            this.y0 = y0;
            this.y1 = y1;
            this.at = at;
            this.first = first;
            this.second = second;
            this.third = third;
        }

        static Gradient of(Map<String, Object> p) {
            if (!"linear".equals(ThemeRenderer.s(p, "fill", "solid"))) return null;
            Color second = ThemeRenderer.c(p, "color2", null);
            if (second == null) return null;
            double a1 = ThemeRenderer.d(p, "alpha", 1);
            Color first = ThemeRenderer.withAlpha(ThemeRenderer.c(p, "color", Color.WHITE), a1);
            double y0 = ThemeRenderer.d(p, "gradY0", 0), y1 = ThemeRenderer.d(p, "gradY1", ThemeRenderer.d(p, "h", 100));
            if (y1 == y0) y1 = y0 + 1;
            Color color = ThemeRenderer.withAlpha(second, ThemeRenderer.d(p, "alpha2", a1));
            Color third = ThemeRenderer.c(p, "color3", null);
            if (third == null) return new Gradient(y0, y1, 1, first, color, null);
            double at = Math.max(0.001, Math.min(0.999, ThemeRenderer.d(p, "midAt", 0.5)));
            Color k3 = ThemeRenderer.withAlpha(third, ThemeRenderer.d(p, "alpha3", a1));
            return new Gradient(y0, y1, at, first, color, k3);
        }
        double[] lines() {
            return third == null ? new double[]{y0, y1} : new double[]{y0, y0 + at * (y1 - y0), y1};
        }
        Color at(double y) {
            double t = Math.max(0, Math.min(1, (y - y0) / (y1 - y0)));
            if (third == null) return mix(first, second, t);
            return t <= at ? mix(first, second, t / at) : mix(second, third, (t - at) / (1 - at));
        }
        private static Color mix(Color color, Color b, double t) {
            return new Color(lerp(color.getRed(), b.getRed(), t), lerp(color.getGreen(), b.getGreen(), t),
                    lerp(color.getBlue(), b.getBlue(), t), lerp(color.getAlpha(), b.getAlpha(), t));
        }
        private static int lerp(int a, int b, double t) { return (int) Math.round(a + (b - a) * t); }
    }
    //endregion
}
