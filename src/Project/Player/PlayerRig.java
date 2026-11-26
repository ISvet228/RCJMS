package Project.Player;

import Project.Game.SpriteLight;

import java.awt.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;

public final class PlayerRig {
    //region Constants
    public static final String[] PROPS = {"x", "y", "w", "h", "angle", "alpha"};
    private static final int SKIN = 0xD6B496, HAIR = 0x221812, EYE = 0x121216, LEG = 0x26242C, SHOE = 0x121014, SHIRT = 0x3E6FB0;
    //endregion

    //region Types
    public enum Kind { ELLIPSE, BOX, CAPSULE }

    public static final class Part {
        public final String name;
        public final Kind kind;
        public String parent = "";
        public int color = SHIRT;
        public boolean playerColor, visible = true;
        public final double[] values = {0, 0, 20, 20, 0, 1};

        Part(String name, Kind kind) {
            this.name = name;
            this.kind = kind;
        }

        public double get(String prop) { return values[indexOf(prop)]; }

        void set(String prop, double value) { values[indexOf(prop)] = value; }

        static int indexOf(String prop) {
            for (int i = 0; i < PROPS.length; i++) if (PROPS[i].equals(prop)) return i;
            throw new IllegalArgumentException("Unknown property " + prop);
        }
    }

    public record Key(double time, double value) {}

    public static final class Clip {
        public final String name;
        public final double length;
        public final boolean loop = true;
        final Map<String, Map<String, List<Key>>> tracks = new LinkedHashMap<>();

        Clip(String name, double length) {
            this.name = name;
            this.length = length;
        }

        List<Key> track(String part, String prop) {
            Map<String, List<Key>> byProp = tracks.get(part);
            return byProp == null ? null : byProp.get(prop);
        }

        void putKey(String part, String prop, double time, double value) {
            List<Key> keys = tracks.computeIfAbsent(part, k -> new LinkedHashMap<>())
                    .computeIfAbsent(prop, k -> new ArrayList<>());
            keys.add(new Key(time, value));
            keys.sort((a, b) -> Double.compare(a.time(), b.time()));
        }
    }
    //endregion

    //region Variables
    private final List<Part> parts = new ArrayList<>();
    private final List<Clip> clips = new ArrayList<>();
    private static PlayerRig original;
    //endregion

    //region Original Player
    public static synchronized PlayerRig original() {
        if (original == null) original = build();
        return original;
    }

    private static PlayerRig build() {
        PlayerRig rig = new PlayerRig();
        rig.add("Shadow", Kind.ELLIPSE, "", 0, 0.5, 44, 7, 0, 0.43, 0x000000, false);
        for (int side = -1; side <= 1; side += 2) {
            String s = side < 0 ? " L" : " R";
            double hipX = side * 6.0;
            rig.add("Hip" + s, Kind.ELLIPSE, "", hipX, -40, 0, 0, 0, 1, 0, false);
            rig.add("Leg" + s, Kind.CAPSULE, "Hip" + s, 0, 19, 7, 38, 0, 1, LEG, false);
            rig.add("Shoe" + s, Kind.ELLIPSE, "Hip" + s, 0, 38, 12, 12, 0, 1, SHOE, false);
        }
        rig.add("Torso", Kind.BOX, "", 0, -58, 26, 36, 0, 1, SHIRT, true);
        for (int side = -1; side <= 1; side += 2) {
            String s = side < 0 ? " L" : " R";
            rig.add("Shoulder" + s, Kind.ELLIPSE, "Torso", side * 12.0, -14, 0, 0, 0, 1, 0, false);
            rig.add("Arm" + s, Kind.CAPSULE, "Shoulder" + s, side * 3.5, 20, 7, 40, -side * 9.9, 1, SHIRT, true);
            rig.add("Hand" + s, Kind.ELLIPSE, "Arm" + s, 0, 20, 10, 10, 0, 1, SKIN, false);
        }
        rig.add("Head", Kind.ELLIPSE, "", 0, -92, 20, 24, 0, 1, SKIN, false);
        rig.add("Hair", Kind.ELLIPSE, "Head", 0, -6.5, 22, 13, 0, 1, HAIR, false);
        rig.add("Eye L", Kind.ELLIPSE, "Head", -3.5, 1, 3, 4, 0, 1, EYE, false);
        rig.add("Eye R", Kind.ELLIPSE, "Head", 3.5, 1, 3, 4, 0, 1, EYE, false);

        Clip idle = new Clip("idle", 2.856);
        double[] breath = {0, 1, 0, -1, 0};
        for (int k = 0; k < 5; k++) {
            double time = k * idle.length / 4;
            idle.putKey("Torso", "y", time, -58 - 1.4 * breath[k]);
            idle.putKey("Head", "y", time, -92 - 1.7 * breath[k]);
        }
        rig.clips.add(idle);

        Clip walk = new Clip("walk", 0.9);
        double[] swing = {0, 1, 0, -1, 0};
        for (int k = 0; k < 5; k++) {
            double time = k * walk.length / 4;
            walk.putKey("Hip L", "angle", time, -17 * swing[k]);
            walk.putKey("Hip R", "angle", time, 17 * swing[k]);
            walk.putKey("Arm L", "angle", time, 9.9 + 12 * swing[k]);
            walk.putKey("Arm R", "angle", time, -9.9 - 12 * swing[k]);
        }
        rig.clips.add(walk);
        return rig;
    }

    private void add(String name, Kind kind, String parent, double x, double y, double w, double h, double angle, double alpha,
                     int color, boolean playerColor) {
        Part part = new Part(name, kind);
        part.parent = parent;
        part.set("x", x);
        part.set("y", y);
        part.set("w", w);
        part.set("h", h);
        part.set("angle", angle);
        part.set("alpha", alpha);
        part.color = color;
        part.playerColor = playerColor;
        parts.add(part);
    }
    //endregion

    //region Evaluation
    private Part part(String name) {
        for (Part part : parts) if (part.name.equals(name)) return part;
        return null;
    }

    private Clip clip(String name) {
        for (Clip clip : clips) if (clip.name.equals(name)) return clip;
        return null;
    }
    private Clip clipFor(boolean moving) { return clip(moving ? "walk" : "idle"); }

    private static double clipTime(Clip clip, double clock) {
        double length = Math.max(0.05, clip.length);
        return clip.loop ? clock % length : Math.clamp(clock, 0, length);
    }
    private double value(Part part, String prop, Clip clip, double t) {
        List<Key> keys = clip == null ? null : clip.track(part.name, prop);
        if (keys == null || keys.isEmpty()) return part.get(prop);
        if (t <= keys.getFirst().time()) return keys.getFirst().value();
        Key last = keys.getLast();
        if (t >= last.time()) return last.value();
        for (int i = 0; i + 1 < keys.size(); i++) {
            Key a = keys.get(i), b = keys.get(i + 1);
            if (t < a.time() || t > b.time()) continue;
            double f = (t - a.time()) / Math.max(1e-6, b.time() - a.time());
            f = f * f * (3 - 2 * f);
            return a.value() + (b.value() - a.value()) * f;
        }
        return last.value();
    }
    private Map<String, AffineTransform> transforms(Clip clip, double t) {
        Map<String, AffineTransform> done = new HashMap<>();
        for (Part part : parts) world(part, clip, t, done, 0);
        return done;
    }

    private AffineTransform world(Part part, Clip clip, double t, Map<String, AffineTransform> done, int depth) {
        AffineTransform known = done.get(part.name);
        if (known != null) return known;
        AffineTransform world = new AffineTransform();
        Part parent = depth < parts.size() && !part.parent.isEmpty() ? part(part.parent) : null;
        if (parent != null && parent != part) world = new AffineTransform(world(parent, clip, t, done, depth + 1));
        AffineTransform local = AffineTransform.getTranslateInstance(value(part, "x", clip, t), value(part, "y", clip, t));
        local.rotate(Math.toRadians(value(part, "angle", clip, t)));
        world.concatenate(local);
        done.put(part.name, world);
        return world;
    }
    //endregion

    //region Drawing
    private void draw(Graphics2D g2d, double cx, double bottom, double size, Clip clip, double t, float[] light, int playerRgb) {
        Map<String, AffineTransform> transforms = transforms(clip, t);
        double s = size / 100.0;
        AffineTransform base = AffineTransform.getTranslateInstance(cx, bottom);
        base.scale(s, s);
        for (Part part : parts) {
            AffineTransform world = transforms.get(part.name);
            if (world == null || !part.visible) continue;
            double w = Math.max(0, value(part, "w", clip, t)), h = Math.max(0, value(part, "h", clip, t));
            double alpha = Math.clamp(value(part, "alpha", clip, t), 0, 1);
            if (w <= 0 || h <= 0 || alpha <= 0) continue;
            Graphics2D g = (Graphics2D) g2d.create();
            try {
                AffineTransform at = new AffineTransform(base);
                at.concatenate(world);
                g.transform(at);
                g.setColor(withAlpha(part.playerColor ? playerRgb : part.color, alpha, light));
                g.fill(shape(part.kind, w, h));
            } finally {
                g.dispose();
            }
        }
    }
    public void drawLive(Graphics2D g2d, double cx, double bottom, double size, double clock, double speed, float[] light, int playerRgb) {
        Clip clip = clipFor(speed > 0.05);
        if (clip == null) return;
        draw(g2d, cx, bottom, size, clip, clipTime(clip, clock), light, playerRgb);
    }

    private static Shape shape(Kind kind, double w, double h) {
        return switch (kind) {
            case ELLIPSE -> new Ellipse2D.Double(-w / 2, -h / 2, w, h);
            case CAPSULE -> new RoundRectangle2D.Double(-w / 2, -h / 2, w, h, Math.min(w, h), Math.min(w, h));
            case BOX -> new Rectangle2D.Double(-w / 2, -h / 2, w, h);
        };
    }

    private static Color withAlpha(int rgb, double alpha, float[] light) {
        Color color = new Color(rgb & 0xFFFFFF);
        if (light != null) color = SpriteLight.tint(color, light);
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), (int) Math.round(alpha * 255));
    }
    //endregion
}