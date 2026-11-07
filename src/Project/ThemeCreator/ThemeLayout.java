package Project.ThemeCreator;
import Project.CreatorKit.*;

import java.util.*;

public final class ThemeLayout {
    //region Variables
    public static final int GAP = 22, LEFT = 40, TOP = 60;
    //endregion

    //region Constructors
    private ThemeLayout() {}
    //endregion

    //region Layout
    public static void arrange(CkProject ckProject) {
        if (ckProject.nodes.isEmpty()) return;
        Map<Integer, Integer> depth = new HashMap<>();
        for (NodeInst nodeInst : ckProject.nodes) depth.put(nodeInst.id, 0);
        for (int round = 0; round <= ckProject.nodes.size(); round++) {
            boolean changed = false;
            for (Link link : ckProject.links) {
                int d = depth.getOrDefault(link.to(), 0) + 1;
                if (depth.getOrDefault(link.from(), 0) < d) {
                    depth.put(link.from(), d);
                    changed = true;
                }
            }
            if (!changed) break;
        }
        int maxDepth = depth.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        int widest = 0;
        for (NodeInst nodeInst : ckProject.nodes) widest = Math.max(widest, NodeGeometry.width(nodeInst));
        int column = widest + 60;

        Map<Integer, Integer> nextY = new HashMap<>();
        for (NodeInst nodeInst : ckProject.nodes) {
            int d = depth.get(nodeInst.id);
            nodeInst.x = LEFT + (maxDepth - d) * column;
            int y = nextY.getOrDefault(d, TOP);
            nodeInst.y = y;
            nextY.put(d, y + NodeGeometry.height(nodeInst) + GAP);
        }
    }
    //endregion
}