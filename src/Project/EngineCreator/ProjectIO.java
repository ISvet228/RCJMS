package Project.EngineCreator;
import Project.CreatorKit.*;

import java.util.*;

public final class ProjectIO {
    //region Constructors
    private ProjectIO() {}
    //endregion

    //region Serialization
    static String toJson(CkProject ckProject) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("{\n");
        stringBuilder.append("  \"format\": \"rcjms-engine-project\",\n  \"version\": 2,\n");
        stringBuilder.append("  \"name\": ").append(Json.quote(ckProject.name)).append(",\n");
        stringBuilder.append("  \"title\": ").append(Json.quote(ckProject.title)).append(",\n");
        stringBuilder.append("  \"openCl\": ").append(ckProject.openCl).append(",\n");
        stringBuilder.append("  \"passGeometry\": {");
        int gi = 0;
        for (Map.Entry<Integer, String> e : new TreeMap<>(ckProject.passGeometry).entrySet()) {
            if (gi++ > 0) stringBuilder.append(", ");
            stringBuilder.append(Json.quote(String.valueOf(e.getKey()))).append(": ").append(Json.quote(e.getValue()));
        }
        stringBuilder.append("},\n");
        stringBuilder.append("  \"curvature\": ").append(ckProject.curvature).append(",\n");
        stringBuilder.append("  \"nextId\": ").append(ckProject.nextId).append(",\n");
        stringBuilder.append("  \"nodes\": [\n");
        for (int i = 0; i < ckProject.nodes.size(); i++) {
            NodeInst nodeInst = ckProject.nodes.get(i);
            stringBuilder.append("    {\"id\": ").append(nodeInst.id)
                    .append(", \"kind\": ").append(Json.quote(nodeInst.kind))
                    .append(", \"pass\": ").append(nodeInst.pass)
                    .append(", \"x\": ").append(nodeInst.x)
                    .append(", \"y\": ").append(nodeInst.y)
                    .append(", \"params\": {");
            int k = 0;
            for (Map.Entry<String, String> e : nodeInst.params.entrySet()) {
                if (k++ > 0) stringBuilder.append(", ");
                stringBuilder.append(Json.quote(e.getKey())).append(": ").append(Json.quote(e.getValue()));
            }
            stringBuilder.append("}");
            if (!nodeInst.comment.isEmpty()) stringBuilder.append(", \"comment\": ").append(Json.quote(nodeInst.comment));
            stringBuilder.append("}");
            stringBuilder.append(i + 1 < ckProject.nodes.size() ? ",\n" : "\n");
        }
        stringBuilder.append("  ],\n  \"links\": [\n");
        for (int i = 0; i < ckProject.links.size(); i++) {
            Link link = ckProject.links.get(i);
            stringBuilder.append("    {\"from\": ").append(link.from())
                    .append(", \"fromPort\": ").append(Json.quote(link.fromPort()))
                    .append(", \"to\": ").append(link.to())
                    .append(", \"toPort\": ").append(Json.quote(link.toPort())).append("}");
            stringBuilder.append(i + 1 < ckProject.links.size() ? ",\n" : "\n");
        }
        stringBuilder.append("  ],\n  \"groups\": [");
        for (int i = 0; i < ckProject.groups.size(); i++) {
            NodeGroup nodeGroup = ckProject.groups.get(i);
            if (i > 0) stringBuilder.append(", ");
            stringBuilder.append("{\"id\": ").append(nodeGroup.id).append(", \"name\": ").append(Json.quote(nodeGroup.name))
                    .append(", \"color\": ").append(Json.quote(nodeGroup.color)).append(", \"nodes\": [");
            for (int k = 0; k < nodeGroup.nodes.size(); k++) stringBuilder.append(k > 0 ? ", " : "").append(nodeGroup.nodes.get(k));
            stringBuilder.append("]}");
        }
        stringBuilder.append("],\n  \"nextGroupId\": ").append(ckProject.nextGroupId).append(",\n");

        stringBuilder.append("  \"backupCustomNodes\": ").append(ckProject.backupCustom).append(",\n");
        stringBuilder.append("  \"customNodes\": [\n");
        List<CustomNodes.Def> defs = CustomNodes.usedBy(ckProject);
        for (int i = 0; i < defs.size(); i++) {
            stringBuilder.append("    ").append(CustomNodes.toJson(defs.get(i))).append(i + 1 < defs.size() ? ",\n" : "\n");
        }
        stringBuilder.append("  ]\n}\n");
        return stringBuilder.toString();
    }
    @SuppressWarnings("unchecked")
    static CkProject fromJson(String text) {
        Object root = Json.parse(text);
        if (!(root instanceof Map<?, ?> raw)) throw new IllegalArgumentException("not a project file");
        Map<String, Object> m = (Map<String, Object>) raw;
        if (!"rcjms-engine-project".equals(m.get("format"))) throw new IllegalArgumentException("not a project file");
        CkProject ckProject = new CkProject();
        ckProject.name = String.valueOf(m.getOrDefault("name", ckProject.name));
        ckProject.title = String.valueOf(m.getOrDefault("title", ckProject.title));
        ckProject.openCl = Boolean.TRUE.equals(m.get("openCl"));
        if (m.get("curvature") instanceof Double c) ckProject.curvature = c;
        int maxId = 0;
        for (Object o : list(m.get("nodes"))) {
            Map<String, Object> nm = (Map<String, Object>) o;
            int id = (int) num(nm.get("id"));
            int pass = nm.get("pass") instanceof Double d ? (int) (double) d : 0;
            NodeInst nodeInst = new NodeInst(id, String.valueOf(nm.get("kind")), (int) num(nm.get("x")), (int) num(nm.get("y")), pass);
            if (nm.get("params") instanceof Map<?, ?> pm)
                for (Map.Entry<?, ?> e : pm.entrySet()) nodeInst.params.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
            if (nm.get("comment") instanceof String c) nodeInst.comment = c.length() > NodeGeometry.COMMENT_MAX ? c.substring(0, NodeGeometry.COMMENT_MAX) : c;
            ckProject.nodes.add(nodeInst);
            maxId = Math.max(maxId, id);
        }
        for (Object o : list(m.get("links"))) {
            Map<String, Object> lm = (Map<String, Object>) o;
            ckProject.links.add(new Link((int) num(lm.get("from")), String.valueOf(lm.get("fromPort")), (int) num(lm.get("to")), String.valueOf(lm.get("toPort"))));
        }
        if (m.get("passGeometry") instanceof Map<?, ?> pg)
            for (Map.Entry<?, ?> e : pg.entrySet()) ckProject.setGeometry(Integer.parseInt(String.valueOf(e.getKey())), String.valueOf(e.getValue()));
        if ("hyperbolic".equals(m.get("geometry"))) for (int k = 0; k < ckProject.passCount(); k++) ckProject.setGeometry(k, CkProject.HYPER);
        ckProject.nextId = Math.max((int) num(m.get("nextId")), maxId + 1);
        ckProject.backupCustom = !Boolean.FALSE.equals(m.get("backupCustomNodes"));
        if (ckProject.backupCustom)
            for (Object o : list(m.get("customNodes"))) {
                CustomNodes.Def def = CustomNodes.parseDef(o);
                ckProject.backup.put(def.kind(), def);
            }
        int maxGroup = 0;
        for (Object o : list(m.get("groups"))) {
            Map<String, Object> gm = (Map<String, Object>) o;
            int gid = (int) num(gm.get("id"));
            NodeGroup nodeGroup = new NodeGroup(gid, String.valueOf(gm.getOrDefault("name", "")), String.valueOf(gm.getOrDefault("color", NodeGroup.PALETTE[0])));
            for (Object id : list(gm.get("nodes"))) nodeGroup.nodes.add((int) num(id));
            ckProject.groups.add(nodeGroup);
            maxGroup = Math.max(maxGroup, gid);
        }
        ckProject.pruneGroups();
        ckProject.nextGroupId = Math.max(maxGroup + 1, m.get("nextGroupId") instanceof Double d ? (int) (double) d : 1);
        CustomNodes.expandMissing(ckProject);
        return ckProject;
    }
    //endregion

    //region JSON Helpers
    private static List<?> list(Object o) { return o instanceof List<?> l ? l : List.of(); }
    private static double num(Object o) {
        if (o instanceof Double d) return d;
        throw new IllegalArgumentException("number expected");
    }
    //endregion
}