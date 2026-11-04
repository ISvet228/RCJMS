package Project.EngineCreator;

import java.util.*;

public final class NodeGroup {
    //region Variables
    //region Palette
    public static final String[] PALETTE = {"#4FC3F7", "#81C784", "#FFB74D", "#F06292", "#BA68C8", "#FFD54F"};
    public static final int NAME_MAX = 40;
    //endregion
    //region Group Data
    public final int id;
    public String name;
    public String color;
    public final List<Integer> nodes = new ArrayList<>();
    //endregion
    //endregion

    //region Constructors
    public NodeGroup(int id, String name, String color) {
        this.id = id;
        this.name = name;
        this.color = color;
    }
    //endregion

    //region Color Assignment
    public String nextColor() {
        int i = Arrays.asList(PALETTE).indexOf(color);
        return PALETTE[(i + 1) % PALETTE.length];
    }
    //endregion
}
