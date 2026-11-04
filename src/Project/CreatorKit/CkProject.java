package Project.CreatorKit;

import Project.EngineCreator.*;

import java.util.*;

public final class CkProject {
    //region Variables
    //region Project Settings
    public String name = "MyEngine";
    public String title = "";
    public boolean openCl = true;
    public double curvature = 4.0;
    public boolean backupCustom = true;
    //endregion
    //region Geometry Settings
    public final Map<Integer, String> passGeometry = new HashMap<>();
    public static final String BOTH = "both", EUCLID = "euclid", HYPER = "hyper";
    //endregion
    //region Graph Storage
    public int nextId = 1;
    public final List<NodeInst> nodes = new ArrayList<>();
    public final List<Link> links = new ArrayList<>();
    //endregion
    //region Groups
    public final List<NodeGroup> groups = new ArrayList<>();
    public int nextGroupId = 1;
    //endregion
    //region Backup
    public final Map<String, CustomNodes.Def> backup = new LinkedHashMap<>();
    //endregion
    //endregion

    //region Node Graph
    public NodeInst add(String kind, int x, int y, int pass) {
        NodeInst nodeInst = new NodeInst(nextId++, kind, x, y, pass);
        for (Param param : Nodes.get(kind).params()) nodeInst.params.put(param.key(), param.def());
        nodes.add(nodeInst);
        return nodeInst;
    }
    public NodeInst node(int id) {
        for (NodeInst nodeInst : nodes) if (nodeInst.id == id) return nodeInst;
        return null;
    }
    public void removeNode(int id) {
        nodes.removeIf(nodeInst -> nodeInst.id == id);
        links.removeIf(link -> link.from() == id || link.to() == id);
        pruneGroups();
    }
    public NodeInst output(int pass) {
        for (NodeInst nodeInst : nodes) {
            NodeKind nodeKind = Nodes.find(nodeInst.kind);
            if (nodeKind != null && nodeKind.isOutput() && nodeInst.pass == pass) return nodeInst;
        }
        return null;
    }
    public int passCount() {
        int max = 0;
        for (NodeInst nodeInst : nodes) max = Math.max(max, nodeInst.pass);
        return max + 1;
    }
    public Link incoming(int node, String port) {
        for (Link link : links) if (link.to() == node && link.toPort().equals(port)) return link;
        return null;
    }
    public void setLink(Link link) {
        links.removeIf(l -> l.to() == link.to() && l.toPort().equals(link.toPort()));
        links.add(link);
    }
    public void removeLink(Link link) { links.remove(link); }
    //endregion

    //region Groups
    public NodeGroup createGroup(Collection<Integer> ids) {
        List<Integer> members = new ArrayList<>();
        for (int id : ids) if (node(id) != null && !members.contains(id)) members.add(id);
        for (NodeGroup nodeGroup : groups) nodeGroup.nodes.removeAll(members);
        pruneGroups();
        int id = nextGroupId++;
        NodeGroup nodeGroup = new NodeGroup(id, L.f("ec.g.defaultName", groups.size() + 1), dominantColor(members));
        nodeGroup.nodes.addAll(members);
        groups.add(nodeGroup);
        return nodeGroup;
    }
    public NodeGroup groupOf(int nodeId) {
        for (NodeGroup nodeGroup : groups) if (nodeGroup.nodes.contains(nodeId)) return nodeGroup;
        return null;
    }
    public void ungroup(Collection<Integer> ids) {
        for (NodeGroup nodeGroup : groups) nodeGroup.nodes.removeAll(ids);
        pruneGroups();
    }
    public void pruneGroups() {
        groups.removeIf(nodeGroup -> {
            nodeGroup.nodes.removeIf(id -> node(id) == null);
            return nodeGroup.nodes.isEmpty();
        });
    }
    //endregion

    //region Pass Geometry
    public String geometry(int pass) { return passGeometry.getOrDefault(pass, BOTH); }
    public void setGeometry(int pass, String value) {
        if (BOTH.equals(value)) passGeometry.remove(pass);
        else passGeometry.put(pass, value);
    }
    public boolean usesHyperbolic() { return passGeometry.containsValue(HYPER); }
    public void removePass(int pass) {
        passGeometry.remove(pass);
        nodes.removeIf(nodeInst -> nodeInst.pass == pass);
        links.removeIf(link -> node(link.from()) == null || node(link.to()) == null);
        pruneGroups();
    }
    private String dominantColor(List<Integer> ids) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (int id : ids) {
            NodeInst nodeInst = node(id);
            NodeKind nodeKind = nodeInst == null ? null : Nodes.find(nodeInst.kind);
            if (nodeKind == null) continue;
            java.awt.Color color = NodeCanvas.categoryColor(nodeKind.categoryId());
            counts.merge(String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue()), 1, Integer::sum);
        }
        String best = NodeGroup.PALETTE[0];
        int most = 0;
        for (Map.Entry<String, Integer> e : counts.entrySet()) if (e.getValue() > most) { most = e.getValue(); best = e.getKey(); }
        return best;
    }
    //endregion
}