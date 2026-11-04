package Project.ThemeCreator;
import Project.CreatorKit.*;
import java.util.*;

public final class ThemeObject {
    //region Variables
    //region Identity
    public final int id;
    public String name;
    public String type;
    //endregion
    //region Hierarchy
    public int parent;
    public int layer;
    public boolean visible = true;
    //endregion
    //region Properties
    public final Map<String, Object> props = new LinkedHashMap<>();
    public CkProject graph = new CkProject();
    //endregion
    //endregion

    //region Constructors
    public ThemeObject(int id, String type, String name) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.props.putAll(ThemeTypes.defaults(type));
    }
    //endregion

    //region Property Access
    public double num(String key, double fallback) {
        Object v = props.get(key);
        return v instanceof Number n ? n.doubleValue() : fallback;
    }
    public boolean hasNum(String key) { return props.get(key) instanceof Number; }
    public String str(String key, String fallback) {
        Object v = props.get(key);
        return v instanceof String s ? s : fallback;
    }
    //endregion
}
