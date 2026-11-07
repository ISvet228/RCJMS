package Project.ThemeCreator;

import java.util.*;

public final class ThemeTypes {
    //region Variables
    //region Type Table
    public static final List<String> TYPES = List.of("empty", "rect", "ellipse", "ring", "arc", "glow", "dot", "polygon", "polyline");
    public static final String BOX_POINTS = "0,0 100,0 100,100 0,100";
    private static final List<Spec> TRANSFORM = List.of(num("x", 0), num("y", 0), num("rotX", 0), num("rotY", 0),
            num("rot", 0), num("scale", 1), num("scaleY", 1));
    private static final Map<String, List<Spec>> SPECS = new LinkedHashMap<>();
    //endregion
    //endregion

    //region Constructors
    private ThemeTypes() {}
    //endregion

    //region Type Registry
    static {
        SPECS.put("empty", List.of());
        SPECS.put("rect", fill(num("w", 100), num("h", 100)));
        SPECS.put("ellipse", fill(num("w", 100), num("h", 100)));
        SPECS.put("ring", List.of(num("w", 100), num("h", 100), num("stroke", 2), col("color", "#FFFFFF"), num("alpha", 1)));
        SPECS.put("arc", List.of(num("w", 100), num("h", 100), num("start", 0), num("extent", 90), num("stroke", 2),
                col("color", "#FFFFFF"), num("alpha", 1)));
        SPECS.put("glow", List.of(num("radius", 100), col("color", "#FFFFFF"), num("alpha", 1)));
        SPECS.put("dot", List.of(num("radius", 2), col("color", "#FFFFFF"), num("alpha", 1)));
        SPECS.put("polygon", fill(text("points", BOX_POINTS)));
        SPECS.put("polyline", List.of(text("points", BOX_POINTS), num("stroke", 2), col("color", "#FFFFFF"), num("alpha", 1)));
    }
    public static boolean known(String type) { return SPECS.containsKey(type); }
    public static List<Spec> specs(String type) {
        List<Spec> all = new ArrayList<>(TRANSFORM);
        all.addAll(SPECS.getOrDefault(type, List.of()));
        return all;
    }
    public static Map<String, Object> defaults(String type) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (Spec spec : specs(type)) if (spec.def() != null) m.put(spec.key(), spec.def());
        return m;
    }
    public static Object defaultOf(String type, String key) {
        for (Spec spec : specs(type)) if (spec.key().equals(key)) return spec.def();
        return null;
    }
    private static List<Spec> fill(Spec... size) {
        List<Spec> out = new ArrayList<>(List.of(size));
        out.add(choice("fill", "solid", "solid", "linear"));
        out.add(col("color", "#FFFFFF"));
        out.add(num("alpha", 1));
        out.add(col("color2", null));
        out.add(num("alpha2", null));
        out.add(col("color3", null));
        out.add(num("alpha3", null));
        out.add(num("midAt", 0.5));
        out.add(num("gradY0", null));
        out.add(num("gradY1", null));
        return out;
    }
    //endregion

    //region Property Specs
    private static Spec num(String key, Object def) { return new Spec(key, Kind.NUM, def, List.of()); }
    private static Spec col(String key, String def) { return new Spec(key, Kind.COLOR, def, List.of()); }
    private static Spec text(String key, String def) { return new Spec(key, Kind.TEXT, def, List.of()); }
    private static Spec choice(String key, String def, String... options) { return new Spec(key, Kind.CHOICE, def, List.of(options)); }
    public enum Kind { NUM, COLOR, CHOICE, TEXT }
    public record Spec(String key, Kind kind, Object def, List<String> choices) { }
    //endregion
}
