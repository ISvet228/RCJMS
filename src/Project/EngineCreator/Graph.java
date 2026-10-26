package Project.EngineCreator;

import java.util.*;

/** Type resolution and graph traversal helpers. */
public final class Graph {
    private Graph() {}

    static DataType outType(EcProject p, int node, String port, int depth) {
        NodeInst n = p.node(node);
        if (n == null || depth > 64) return null;
        NodeKind k = Nodes.find(n.kind);
        if (k == null) return null;
        int oi = k.outputIndex(port);
        if (oi < 0) return null;
        DataType t = k.outputs().get(oi).type();
        if (t != DataType.ANY) return t;
        Link a = p.incoming(node, "a");
        return a == null ? null : outType(p, a.from(), a.fromPort(), depth + 1);
    }
    static List<Integer> order(EcProject p, int root) {
        List<Integer> out = new ArrayList<>();
        if (!visit(p, root, new HashSet<>(), new HashSet<>(), out)) return null;
        return out;
    }
    private static boolean visit(EcProject p, int id, Set<Integer> done, Set<Integer> active, List<Integer> out) {
        if (done.contains(id)) return true;
        if (!active.add(id)) return false;
        for (Link l : p.links)
            if (l.to() == id && !visit(p, l.from(), done, active, out)) return false;
        active.remove(id);
        done.add(id);
        out.add(id);
        return true;
    }
    static Set<Integer> upstream(EcProject p, int root) {
        Set<Integer> s = new HashSet<>();
        collect(p, root, s);
        return s;
    }
    private static void collect(EcProject p, int id, Set<Integer> s) {
        if (!s.add(id)) return;
        for (Link l : p.links) if (l.to() == id) collect(p, l.from(), s);
    }
    static boolean incompatible(DataType have, DataType want) { return want != DataType.ANY && have != want && (want != DataType.FLOAT || have != DataType.INT); }
}