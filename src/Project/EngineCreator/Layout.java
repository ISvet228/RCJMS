package Project.EngineCreator;
import Project.CreatorKit.CkProject;
import Project.CreatorKit.Link;
import Project.CreatorKit.NodeGeometry;
import Project.CreatorKit.NodeInst;

import java.util.*;

/** Arranges the nodes of one pass left to right: sources on the left, the output on the right. */
public final class Layout {
    //region Variables
    public static final int GAP = 22, LEFT = 40, TOP = 60;
    //endregion

    //region Constructors
    private Layout() {}
    //endregion

    //region Arrangement
    public static int height(NodeInst nodeInst) { return NodeGeometry.height(nodeInst); }
    public static void arrange(CkProject ckProject, int pass) {
        NodeInst out = ckProject.output(pass);
        Map<Integer, Integer> depth = new HashMap<>();
        if (out != null) depth.put(out.id, 0);
        for (int round = 0; round <= ckProject.nodes.size() && out != null; round++) {
            boolean changed = false;
            for (Link link : ckProject.links) {
                Integer d = depth.get(link.to());
                if (d == null) continue;
                if (depth.getOrDefault(link.from(), -1) < d + 1) {
                    depth.put(link.from(), d + 1);
                    changed = true;
                }
            }
            if (!changed) break;
        }
        int maxDepth = depth.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        int widest = 0;
        for (NodeInst nodeInst : ckProject.nodes) if (nodeInst.pass == pass) widest = Math.max(widest, NodeGeometry.width(nodeInst));
        int column = widest + 60;

        Map<Integer, Integer> nextY = new HashMap<>();
        List<NodeInst> dangling = new ArrayList<>();
        for (NodeInst nodeInst : ckProject.nodes) {
            if (nodeInst.pass != pass) continue;
            Integer d = depth.get(nodeInst.id);
            if (d == null) { dangling.add(nodeInst); continue; }
            nodeInst.x = LEFT + (maxDepth - d) * column;
            nodeInst.y = nextY.getOrDefault(d, TOP);
            nextY.put(d, nodeInst.y + height(nodeInst) + GAP);
        }
        int bottom = nextY.values().stream().mapToInt(Integer::intValue).max().orElse(TOP) + GAP;
        for (NodeInst nodeInst : dangling) {
            nodeInst.x = LEFT;
            nodeInst.y = bottom;
            bottom += height(nodeInst) + GAP;
        }
    }
    //endregion
}