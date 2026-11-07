package Project.CreatorKit;
import Project.EngineCreator.*;
import Project.ThemeCreator.*;

import java.util.*;

public record Param(String key, Kind kind, String def, List<String> choices, double min, double max) {
    //region Parameter Kinds
    public enum Kind {FLOAT, INT, COLOR, CHOICE, TEXT}
    //endregion

    //region Labels and Formatting
    public String label() { return L.t("ec.p." + key); }
    public String choiceLabel(String value) { return L.t("ec.o." + value); }
    private static String fmt(double d) { return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d); }
    //endregion

    //region Value Normalization
    public static String normalizeHex(String v) {
        String s = v == null ? "" : v.strip();
        if (s.startsWith("#")) s = s.substring(1);
        return s.matches("[0-9A-Fa-f]{6}") ? "#" + s.toUpperCase(Locale.ROOT) : null;
    }
    //endregion

    //region Parameter Factories
    public static Param num(String key, double def, double min, double max) { return new Param(key, Kind.FLOAT, fmt(def), List.of(), min, max); }
    public static Param whole(String key, int def, int min, int max) { return new Param(key, Kind.INT, String.valueOf(def), List.of(), min, max); }
    public static Param color(String key, String def) { return new Param(key, Kind.COLOR, def, List.of(), 0, 0); }
    public static Param choice(String key, String def, String... options) { return new Param(key, Kind.CHOICE, def, List.of(options), 0, 0);}
    public static Param text(String key, String def) { return new Param(key, Kind.TEXT, def, List.of(), 0, 0); }
    //endregion
}