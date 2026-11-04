package Project.ThemeCreator;

import java.util.*;

public final class ThemeInspectorLayout {
    //region Variables
    //region Layout Groups
    private static final Map<String, Group> GROUPS = new LinkedHashMap<>();
    //endregion
    //region Alpha Pairing
    private static final Map<String, String> ALPHA_OF = Map.of("color", "alpha", "color2", "alpha2", "color3", "alpha3");
    private static final Set<String> LINEAR_ONLY = Set.of("color2", "color3", "midAt");
    static {
        GROUPS.put("x", new Group("tc.row.position", false, "x", "X", "y", "Y"));
        GROUPS.put("rotX", new Group("tc.row.rotation", false, "rotX", "X", "rotY", "Y", "rot", "Z"));
        GROUPS.put("scale", new Group("tc.row.scale", false, "scale", "X", "scaleY", "Y"));
        GROUPS.put("w", new Group("tc.row.size", false, "w", "W", "h", "H"));
        GROUPS.put("gradY0", new Group("tc.row.gradient", true, "gradY0", "tc.axis.start", "gradY1", "tc.axis.end"));
    }
    //endregion
    //endregion

    //region Constructors
    private ThemeInspectorLayout() {}
    //endregion

    //region Row Building
    public static List<Row> rows(String type, Map<String, Object> props) {
        Set<String> keys = new HashSet<>();
        for (ThemeTypes.Spec spec : ThemeTypes.specs(type)) keys.add(spec.key());
        boolean linear = "linear".equals(props.get("fill")) || (!props.containsKey("fill") && "linear".equals(ThemeTypes.defaultOf(type, "fill")));
        List<Row> rows = new ArrayList<>();
        Set<String> done = new HashSet<>();
        for (ThemeTypes.Spec spec : ThemeTypes.specs(type)) {
            String key = spec.key();
            if (done.contains(key)) continue;
            Group group = GROUPS.get(key);
            if (group != null && keys.containsAll(group.members())) {
                done.addAll(group.members());
                if (group.linearOnly() && !linear) continue;
                List<Cell> cells = new ArrayList<>();
                for (int i = 0; i < group.members().size(); i++) {
                    cells.add(new Cell(group.members().get(i), group.labels().get(i), Kind.NUMBER, null));
                }
                rows.add(new Row(group.titleKey(), cells));
                continue;
            }
            if (ALPHA_OF.containsValue(key)) { done.add(key); continue; }
            if (LINEAR_ONLY.contains(key) && !linear) continue;
            if (spec.kind() == ThemeTypes.Kind.COLOR) {
                rows.add(new Row("tc.p." + key, List.of(new Cell(key, null, Kind.COLOR, ALPHA_OF.get(key)))));
                continue;
            }
            Kind kind = switch (spec.kind()) {
                case CHOICE -> Kind.CHOICE;
                case TEXT -> Kind.TEXT;
                default -> Kind.NUMBER;
            };
            rows.add(new Row("tc.p." + key, List.of(new Cell(key, null, kind, null))));
        }
        return rows;
    }
    public static String signature(List<Row> rows) {
        StringBuilder text = new StringBuilder();
        for (Row row : rows) {
            text.append(row.titleKey()).append('[');
            for (Cell cell : row.cells()) text.append(cell.key()).append(cell.kind()).append(';');
            text.append(']');
        }
        return text.toString();
    }
    //endregion

    //region Row Types
    public enum Kind { NUMBER, COLOR, CHOICE, TEXT }
    public record Cell(String key, String label, Kind kind, String alphaKey) { }
    public record Row(String titleKey, List<Cell> cells) { }
    private record Group(String titleKey, boolean linearOnly, List<String> members, List<String> labels) {
    Group(String titleKey, boolean linearOnly, String... pairs) { this(titleKey, linearOnly, pairsAt(pairs, 0), pairsAt(pairs, 1)); }

    private static List<String> pairsAt(String[] pairs, int offset) {
            List<String> out = new ArrayList<>();
            for (int i = offset; i < pairs.length; i += 2) out.add(pairs[i]);
            return out;
        }
    }
    //endregion
}
