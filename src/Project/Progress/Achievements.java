package Project.Progress;

import Project.Helpers.*;

import java.awt.*;
import java.awt.geom.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.function.Predicate;

public final class Achievements {
    //region Types
    public record Achievement(String id, String nameKey, String descKey) {}
    public record Totals(int finishes, int threeD, int fog, int custom, int nonEuclidean, int distinctSeeds, int geometryMask, int horror) {}
    public record Run(double seconds, int floors, boolean hasFloors, String seed, int geometry, boolean fog, boolean custom, boolean horror, Totals totals) {}
    private record Rule(Achievement achievement, Predicate<Run> test) {}
    //endregion

    //region Variables
    private static final String FILE_NAME = "achivements.prt";
    private static final String UNLOCKED = "unlocked.";
    private static final String FINISHES = "stat.finishes";
    private static final String THREE_D = "stat.3d";
    private static final String FOG = "stat.fog";
    private static final String CUSTOM = "stat.custom";
    private static final String NON_EUCLIDEAN = "stat.bent";
    private static final String GEOMETRY_MASK = "stat.geo";
    private static final String HORROR = "stat.horror";
    private static final String SEED = "stat.seed.";
    private static final int MAX_SEEDS = 25;
    private static final int GEOMETRY_EUCLIDEAN = 0, GEOMETRY_WRONG = 1, GEOMETRY_LOOPED = 2;
    private static final int ALL_GEOMETRIES_MASK = 0b111;

    private static final List<Rule> RULES = List.of(
            rule("first_finish", r -> r.totals().finishes() >= 1),
            rule("under_120", r -> r.seconds() < 120),
            rule("under_90", r -> r.seconds() < 90),
            rule("under_60", r -> r.seconds() < 60),
            rule("fast_3d", r -> r.hasFloors() && r.seconds() < 90),
            rule("fast_3d_60", r -> r.hasFloors() && r.seconds() < 60),
            rule("fog_under_90", r -> r.fog() && r.seconds() < 90),
            rule("fog_under_60", r -> r.fog() && r.seconds() < 60),
            rule("custom_under_60", r -> r.custom() && r.seconds() < 60),
            rule("three_d_finish", Run::hasFloors),
            rule("threed_5", r -> r.totals().threeD() >= 5),
            rule("threed_10", r -> r.totals().threeD() >= 10),
            rule("threed_25", r -> r.totals().threeD() >= 25),
            rule("both_dimensions", r -> r.totals().threeD() >= 1 && r.totals().finishes() - r.totals().threeD() >= 1),
            rule("two_floors", r -> r.floors() >= 2),
            rule("three_floors", r -> r.floors() >= 3),
            rule("four_floors", r -> r.floors() >= 4),
            rule("five_floors", r -> r.floors() >= 5),
            rule("finishes_5", r -> r.totals().finishes() >= 5),
            rule("finishes_10", r -> r.totals().finishes() >= 10),
            rule("finishes_25", r -> r.totals().finishes() >= 25),
            rule("finishes_50", r -> r.totals().finishes() >= 50),
            rule("seeded_finish", r -> r.seed() != null && !r.seed().isBlank()),
            rule("seeds_5", r -> r.totals().distinctSeeds() >= 5),
            rule("seeds_10", r -> r.totals().distinctSeeds() >= 10),
            rule("seeds_25", r -> r.totals().distinctSeeds() >= 25),
            rule("fog_finish", Run::fog),
            rule("fog_5", r -> r.totals().fog() >= 5),
            rule("fog_10", r -> r.totals().fog() >= 10),
            rule("fog_25", r -> r.totals().fog() >= 25),
            rule("fog_hyperbolic", r -> r.fog() && r.geometry() != GEOMETRY_EUCLIDEAN),
            rule("hyperbolic_finish", r -> r.geometry() != GEOMETRY_EUCLIDEAN),
            rule("bent_5", r -> r.totals().nonEuclidean() >= 5),
            rule("bent_25", r -> r.totals().nonEuclidean() >= 25),
            rule("wrong_finish", r -> r.geometry() == GEOMETRY_WRONG),
            rule("looped_finish", r -> r.geometry() == GEOMETRY_LOOPED),
            rule("all_geometries", r -> (r.totals().geometryMask() & ALL_GEOMETRIES_MASK) == ALL_GEOMETRIES_MASK),
            rule("custom_finish", Run::custom),
            rule("custom_5", r -> r.totals().custom() >= 5),
            rule("horror_finish", Run::horror),
            rule("horror_fast", r -> r.horror() && r.seconds() < 120),
            rule("horror_5", r -> r.totals().horror() >= 5),
            rule("horror_10", r -> r.totals().horror() >= 10),
            rule("horror_caught", r -> false)
    );

    private static Path root = AppPaths.DATA_DIR;
    private static final Properties PROPERTIES = new Properties();
    private static boolean loaded;
    //endregion

    //region Constructors
    private Achievements() {}
    //endregion

    //region Catalogue
    public static List<Achievement> all() {
        List<Achievement> list = new ArrayList<>(RULES.size());
        for (Rule rule : RULES) list.add(rule.achievement());
        return List.copyOf(list);
    }
    public static synchronized boolean isUnlocked(String id) {
        ensureLoaded();
        return PROPERTIES.containsKey(UNLOCKED + id);
    }
    public static synchronized int unlockedCount() {
        ensureLoaded();
        int count = 0;
        for (Rule rule : RULES) if (PROPERTIES.containsKey(UNLOCKED + rule.achievement().id())) count++;
        return count;
    }
    //endregion

    //region Run Processing
    public static synchronized List<Achievement> recordFinish(double seconds, int floors, boolean hasFloors, String seed, int geometry, boolean fog, boolean custom, boolean horror) {
        ensureLoaded();
        String safeSeed = seed == null ? "" : seed;
        addFinish(safeSeed, hasFloors, fog, custom, geometry, horror);
        Totals totals = totals();
        Run run = new Run(seconds, floors, hasFloors, safeSeed, geometry, fog, custom, horror, totals);
        List<Achievement> unlocked = new ArrayList<>();
        for (Rule rule : RULES) {
            String id = rule.achievement().id();
            if (!PROPERTIES.containsKey(UNLOCKED + id) && rule.test().test(run)) {
                PROPERTIES.setProperty(UNLOCKED + id, String.valueOf(System.currentTimeMillis()));
                unlocked.add(rule.achievement());
            }
        }
        save();
        return unlocked;
    }
    public static synchronized Achievement unlock(String id) {
        ensureLoaded();
        if (PROPERTIES.containsKey(UNLOCKED + id)) return null;
        Achievement found = null;
        for (Rule rule : RULES) if (rule.achievement().id().equals(id)) found = rule.achievement();
        if (found == null) return null;
        PROPERTIES.setProperty(UNLOCKED + id, String.valueOf(System.currentTimeMillis()));
        save();
        return found;
    }
    static List<String> matchingIds(Run run) {
        List<String> ids = new ArrayList<>();
        for (Rule rule : RULES) if (rule.test().test(run)) ids.add(rule.achievement().id());
        return ids;
    }
    //endregion

    //region Drawing
    public static void paintIcon(Graphics2D g2d, Achievement achievement, boolean unlocked, int x, int y, int size) {
        Graphics2D g = (Graphics2D) g2d.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int index = indexOf(achievement);
            Color base = unlocked ? Color.getHSBColor((index * 0.618f) % 1f, 0.55f, 0.9f) : new Color(90, 90, 98);
            g.setColor(new Color(base.getRed() / 3, base.getGreen() / 3, base.getBlue() / 3, 230));
            g.fill(new Ellipse2D.Double(x, y, size, size));
            g.setColor(base);
            g.setStroke(new BasicStroke(Math.max(1f, size / 14f)));
            g.draw(new Ellipse2D.Double(x + size * 0.06, y + size * 0.06, size * 0.88, size * 0.88));
            if (unlocked) drawGlyph(g, index, x + size / 2.0, y + size / 2.0, size * 0.32);
            else drawLock(g, x + size / 2.0, y + size / 2.0, size * 0.32);
        } finally { g.dispose(); }
    }
    private static void drawGlyph(Graphics2D g, int index, double cx, double cy, double r) {
        g.setColor(Color.WHITE);
        switch (index % 5) {
            case 0 -> g.fill(star(cx, cy, r, r * 0.45));
            case 1 -> g.fill(new Ellipse2D.Double(cx - r * 0.7, cy - r * 0.7, r * 1.4, r * 1.4));
            case 2 -> g.fill(polygon(cx, cy, r, 3));
            case 3 -> g.fill(polygon(cx, cy, r, 4));
            default -> {
                g.setStroke(new BasicStroke((float) Math.max(1.5, r * 0.25), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new Ellipse2D.Double(cx - r * 0.7, cy - r * 0.7, r * 1.4, r * 1.4));
                g.draw(new Ellipse2D.Double(cx - r * 0.25, cy - r * 0.25, r * 0.5, r * 0.5));
            }
        }
    }
    private static void drawLock(Graphics2D g, double cx, double cy, double r) {
        g.setColor(new Color(160, 160, 168));
        g.fill(new RoundRectangle2D.Double(cx - r * 0.7, cy - r * 0.1, r * 1.4, r * 1.1, r * 0.25, r * 0.25));
        g.setStroke(new BasicStroke((float) Math.max(1.5, r * 0.25)));
        g.draw(new Arc2D.Double(cx - r * 0.45, cy - r * 0.9, r * 0.9, r, 0, 180, Arc2D.OPEN));
    }
    private static Path2D.Double star(double cx, double cy, double outer, double inner) {
        Path2D.Double path = new Path2D.Double();
        for (int i = 0; i < 5 * 2; i++) {
            double radius = (i % 2 == 0) ? outer : inner;
            double angle = -Math.PI / 2 + i * Math.PI / 5;
            double px = cx + Math.cos(angle) * radius, py = cy + Math.sin(angle) * radius;
            if (i == 0) path.moveTo(px, py); else path.lineTo(px, py);
        }
        path.closePath();
        return path;
    }
    private static Path2D.Double polygon(double cx, double cy, double r, int sides) {
        Path2D.Double path = new Path2D.Double();
        for (int i = 0; i < sides; i++) {
            double angle = -Math.PI / 2 + i * 2 * Math.PI / sides;
            double px = cx + Math.cos(angle) * r, py = cy + Math.sin(angle) * r;
            if (i == 0) path.moveTo(px, py); else path.lineTo(px, py);
        }
        path.closePath();
        return path;
    }
    private static int indexOf(Achievement achievement) {
        for (int i = 0; i < RULES.size(); i++) if (RULES.get(i).achievement().id().equals(achievement.id())) return i;
        return 0;
    }
    //endregion

    //region Storage
    static synchronized void setRoot(Path folder) {
        root = folder;
        PROPERTIES.clear();
        loaded = false;
    }
    private static Rule rule(String id, Predicate<Run> test) { return new Rule(new Achievement(id, "ach." + id + ".name", "ach." + id + ".desc"), test); }
    private static void addFinish(String seed, boolean hasFloors, boolean fog, boolean custom, int geometry, boolean horror) {
        PROPERTIES.setProperty(FINISHES, String.valueOf(count(FINISHES) + 1));
        if (horror) PROPERTIES.setProperty(HORROR, String.valueOf(count(HORROR) + 1));
        if (hasFloors) PROPERTIES.setProperty(THREE_D, String.valueOf(count(THREE_D) + 1));
        if (fog) PROPERTIES.setProperty(FOG, String.valueOf(count(FOG) + 1));
        if (custom) PROPERTIES.setProperty(CUSTOM, String.valueOf(count(CUSTOM) + 1));
        if (geometry != GEOMETRY_EUCLIDEAN) PROPERTIES.setProperty(NON_EUCLIDEAN, String.valueOf(count(NON_EUCLIDEAN) + 1));
        if (geometry >= 0 && geometry < Integer.SIZE - 1) {
            int mask = count(GEOMETRY_MASK) | (1 << geometry);
            PROPERTIES.setProperty(GEOMETRY_MASK, String.valueOf(mask));
        }
        if (custom || seed.isEmpty() || distinctSeeds() >= MAX_SEEDS) return;
        for (int i = 0; i < distinctSeeds(); i++) if (seed.equals(PROPERTIES.getProperty(SEED + i))) return;
        PROPERTIES.setProperty(SEED + distinctSeeds(), seed);
    }
    private static Totals totals() {
        int finishes = count(FINISHES);
        return new Totals(finishes, count(THREE_D), count(FOG), count(CUSTOM), count(NON_EUCLIDEAN), distinctSeeds(), count(GEOMETRY_MASK), count(HORROR));
    }
    private static int distinctSeeds() {
        int count = 0;
        while (PROPERTIES.containsKey(SEED + count)) count++;
        return count;
    }
    private static int count(String key) {
        try { return Integer.parseInt(PROPERTIES.getProperty(key, "0").trim()); }
        catch (NumberFormatException exception) { return 0; }
    }
    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        PROPERTIES.clear();
        Path file = root.resolve(FILE_NAME);
        if (!Files.isRegularFile(file)) return;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { PROPERTIES.load(reader); }
        catch (IOException | IllegalArgumentException exception) { PROPERTIES.clear(); }
    }
    private static void save() {
        try {
            StringWriter writer = new StringWriter();
            PROPERTIES.store(writer, "RCJMS achievements");
            AtomicFiles.writeString(root.resolve(FILE_NAME), writer.toString());
        } catch (IOException exception) { System.err.println("RCJMS: could not save achievements: " + exception.getMessage()); }
    }
    //endregion
}