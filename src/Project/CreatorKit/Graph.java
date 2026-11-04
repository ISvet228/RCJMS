package Project.CreatorKit;

import java.util.*;

/** Type resolution and graph helpers. */
public final class Graph {
    //region Constructors
    private Graph() {}
    //endregion

    //region Type Resolution
    public static DataType outType(CkProject ckProject, int node, String port, int depth) {
        NodeInst nodeInst = ckProject.node(node);
        if (nodeInst == null || depth > 64) return null;
        NodeKind nodeKind = Nodes.find(nodeInst.kind);
        if (nodeKind == null) return null;
        int oi = nodeKind.outputIndex(port);
        if (oi < 0) return null;
        DataType dataType = nodeKind.outputs().get(oi).type();
        if (dataType != DataType.ANY) return dataType;
        Link link = ckProject.incoming(node, "a");
        return link == null ? null : outType(ckProject, link.from(), link.fromPort(), depth + 1);
    }
    public static boolean incompatible(DataType have, DataType want) { return want != DataType.ANY && have != want && (want != DataType.FLOAT || have != DataType.INT); }
    //endregion

    //region Ordering And Upstream
    public static List<Integer> order(CkProject ckProject, int root) {
        List<Integer> out = new ArrayList<>();
        if (!visit(ckProject, root, new HashSet<>(), new HashSet<>(), out)) return null;
        return out;
    }
    public static Set<Integer> upstream(CkProject ckProject, int root) {
        Set<Integer> s = new HashSet<>();
        collect(ckProject, root, s);
        return s;
    }
    private static boolean visit(CkProject ckProject, int id, Set<Integer> done, Set<Integer> active, List<Integer> out) {
        if (done.contains(id)) return true;
        if (!active.add(id)) return false;
        for (Link link : ckProject.links) if (link.to() == id && !visit(ckProject, link.from(), done, active, out)) return false;
        active.remove(id);
        done.add(id);
        out.add(id);
        return true;
    }
    private static void collect(CkProject ckProject, int id, Set<Integer> s) {
        if (!s.add(id)) return;
        for (Link link : ckProject.links) if (link.to() == id) collect(ckProject, link.from(), s);
    }
    //endregion
}