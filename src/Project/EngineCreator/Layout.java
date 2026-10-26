package Project.EngineCreator;

import java.util.*;

/** Arranges the nodes of one pass left to right: sources on the left, the output on the right. */
public final class Layout {
    static final int GAP = 22, LEFT = 40, TOP = 60;

    private Layout() {}

    static int height(NodeInst n) { return NodeGeometry.height(n); }

    static void arrange(EcProject p, int pass) {
        NodeInst out = p.output(pass);
        Map<Integer, Integer> depth = new HashMap<>();
        if (out != null) depth.put(out.id, 0);
        for (int round = 0; round <= p.nodes.size() && out != null; round++) {
            boolean changed = false;
            for (Link l : p.links) {
                Integer d = depth.get(l.to());
                if (d == null) continue;
                if (depth.getOrDefault(l.from(), -1) < d + 1) {
                    depth.put(l.from(), d + 1);
                    changed = true;
                }
            }
            if (!changed) break;
        }
        int maxDepth = depth.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        int widest = 0;
        for (NodeInst n : p.nodes) if (n.pass == pass) widest = Math.max(widest, NodeGeometry.width(n));
        int column = widest + 60;

        Map<Integer, Integer> nextY = new HashMap<>();
        List<NodeInst> dangling = new ArrayList<>();
        for (NodeInst n : p.nodes) {
            if (n.pass != pass) continue;
            Integer d = depth.get(n.id);
            if (d == null) { dangling.add(n); continue; }
            n.x = LEFT + (maxDepth - d) * column;
            n.y = nextY.getOrDefault(d, TOP);
            nextY.put(d, n.y + height(n) + GAP);
        }
        int bottom = nextY.values().stream().mapToInt(Integer::intValue).max().orElse(TOP) + GAP;
        for (NodeInst n : dangling) {
            n.x = LEFT;
            n.y = bottom;
            bottom += height(n) + GAP;
        }
    }
}