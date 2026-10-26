package Project.EngineCreator;

import java.util.*;

final class NodeInst {
    final int id;
    final String kind;
    int x, y;
    int pass;
    final LinkedHashMap<String, String> params = new LinkedHashMap<>();
    String comment = "";

    NodeInst(int id, String kind, int x, int y, int pass) {
        this.id = id;
        this.kind = kind;
        this.x = x;
        this.y = y;
        this.pass = pass;
    }
}

record Link(int from, String fromPort, int to, String toPort) {}
public final class EcProject {
    String name = "MyEngine";
    String title = "";
    boolean openCl = true;
    double curvature = 4.0;
    final Map<Integer, String> passGeometry = new HashMap<>();
    static final String BOTH = "both", EUCLID = "euclid", HYPER = "hyper";
    int nextId = 1;
    final List<NodeInst> nodes = new ArrayList<>();
    final List<Link> links = new ArrayList<>();

    NodeInst add(String kind, int x, int y, int pass) {
        NodeInst n = new NodeInst(nextId++, kind, x, y, pass);
        for (Param p : Nodes.get(kind).params()) n.params.put(p.key(), p.def());
        nodes.add(n);
        return n;
    }
    int passCount() {
        int max = 0;
        for (NodeInst n : nodes) max = Math.max(max, n.pass);
        return max + 1;
    }
    NodeInst node(int id) {
        for (NodeInst n : nodes) if (n.id == id) return n;
        return null;
    }

    NodeInst output(int pass) {
        for (NodeInst n : nodes) {
            NodeKind k = Nodes.find(n.kind);
            if (k != null && k.isOutput() && n.pass == pass) return n;
        }
        return null;
    }
    Link incoming(int node, String port) {
        for (Link l : links) if (l.to() == node && l.toPort().equals(port)) return l;
        return null;
    }

    void setLink(Link link) {
        links.removeIf(l -> l.to() == link.to() && l.toPort().equals(link.toPort()));
        links.add(link);
    }
    void removeLink(Link link) { links.remove(link); }

    void removeNode(int id) {
        nodes.removeIf(n -> n.id == id);
        links.removeIf(l -> l.from() == id || l.to() == id);
    }

    String geometry(int pass) { return passGeometry.getOrDefault(pass, BOTH); }
    void setGeometry(int pass, String value) {
        if (BOTH.equals(value)) passGeometry.remove(pass);
        else passGeometry.put(pass, value);
    }
    boolean usesHyperbolic() { return passGeometry.containsValue(HYPER); }
    void removePass(int pass) {
        passGeometry.remove(pass);
        nodes.removeIf(n -> n.pass == pass);
        links.removeIf(l -> node(l.from()) == null || node(l.to()) == null);
    }
}