package Project.CreatorKit;

import java.util.*;

public final class NodeInst {
    //region Variables
    //region Identity
    public final int id;
    public final String kind;
    //endregion
    //region Placement
    public int x, y;
    public int pass;
    //endregion
    //region Content
    public final LinkedHashMap<String, String> params = new LinkedHashMap<>();
    public String comment = "";
    //endregion
    //endregion

    //region Constructors
    public NodeInst(int id, String kind, int x, int y, int pass) {
        this.id = id;
        this.kind = kind;
        this.x = x;
        this.y = y;
        this.pass = pass;
    }
    //endregion
}