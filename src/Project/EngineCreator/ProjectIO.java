package Project.EngineCreator;

import java.util.*;

public final class ProjectIO {
    private ProjectIO() {}

    static String toJson(EcProject p) {
        StringBuilder s = new StringBuilder();
        s.append("{\n");
        s.append("  \"format\": \"rcjms-engine-project\",\n  \"version\": 2,\n");
        s.append("  \"name\": ").append(Json.quote(p.name)).append(",\n");
        s.append("  \"title\": ").append(Json.quote(p.title)).append(",\n");
        s.append("  \"openCl\": ").append(p.openCl).append(",\n");
        s.append("  \"passGeometry\": {");
        int gi = 0;
        for (Map.Entry<Integer, String> e : new TreeMap<>(p.passGeometry).entrySet()) {
            if (gi++ > 0) s.append(", ");
            s.append(Json.quote(String.valueOf(e.getKey()))).append(": ").append(Json.quote(e.getValue()));
        }
        s.append("},\n");
        s.append("  \"curvature\": ").append(p.curvature).append(",\n");
        s.append("  \"nextId\": ").append(p.nextId).append(",\n");
        s.append("  \"nodes\": [\n");
        for (int i = 0; i < p.nodes.size(); i++) {
            NodeInst n = p.nodes.get(i);
            s.append("    {\"id\": ").append(n.id)
                    .append(", \"kind\": ").append(Json.quote(n.kind))
                    .append(", \"pass\": ").append(n.pass)
                    .append(", \"x\": ").append(n.x)
                    .append(", \"y\": ").append(n.y)
                    .append(", \"params\": {");
            int k = 0;
            for (Map.Entry<String, String> e : n.params.entrySet()) {
                if (k++ > 0) s.append(", ");
                s.append(Json.quote(e.getKey())).append(": ").append(Json.quote(e.getValue()));
            }
            s.append("}");
            if (!n.comment.isEmpty()) s.append(", \"comment\": ").append(Json.quote(n.comment));
            s.append("}");
            s.append(i + 1 < p.nodes.size() ? ",\n" : "\n");
        }
        s.append("  ],\n  \"links\": [\n");
        for (int i = 0; i < p.links.size(); i++) {
            Link l = p.links.get(i);
            s.append("    {\"from\": ").append(l.from())
                    .append(", \"fromPort\": ").append(Json.quote(l.fromPort()))
                    .append(", \"to\": ").append(l.to())
                    .append(", \"toPort\": ").append(Json.quote(l.toPort())).append("}");
            s.append(i + 1 < p.links.size() ? ",\n" : "\n");
        }
        s.append("  ]\n}\n");
        return s.toString();
    }

    @SuppressWarnings("unchecked")
    static EcProject fromJson(String text) {
        Object root = Json.parse(text);
        if (!(root instanceof Map<?, ?> raw)) throw new IllegalArgumentException("not a project file");
        Map<String, Object> m = (Map<String, Object>) raw;
        if (!"rcjms-engine-project".equals(m.get("format"))) throw new IllegalArgumentException("not a project file");
        EcProject p = new EcProject();
        p.name = String.valueOf(m.getOrDefault("name", p.name));
        p.title = String.valueOf(m.getOrDefault("title", p.title));
        p.openCl = Boolean.TRUE.equals(m.get("openCl"));
        if (m.get("curvature") instanceof Double c) p.curvature = c;
        int maxId = 0;
        for (Object o : list(m.get("nodes"))) {
            Map<String, Object> nm = (Map<String, Object>) o;
            int id = (int) num(nm.get("id"));
            int pass = nm.get("pass") instanceof Double d ? (int) (double) d : 0;
            NodeInst n = new NodeInst(id, String.valueOf(nm.get("kind")), (int) num(nm.get("x")), (int) num(nm.get("y")), pass);
            if (nm.get("params") instanceof Map<?, ?> pm)
                for (Map.Entry<?, ?> e : pm.entrySet()) n.params.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
            if (nm.get("comment") instanceof String c) n.comment = c.length() > NodeGeometry.COMMENT_MAX ? c.substring(0, NodeGeometry.COMMENT_MAX) : c;
            p.nodes.add(n);
            maxId = Math.max(maxId, id);
        }
        for (Object o : list(m.get("links"))) {
            Map<String, Object> lm = (Map<String, Object>) o;
            p.links.add(new Link((int) num(lm.get("from")), String.valueOf(lm.get("fromPort")), (int) num(lm.get("to")), String.valueOf(lm.get("toPort"))));
        }
        if (m.get("passGeometry") instanceof Map<?, ?> pg)
            for (Map.Entry<?, ?> e : pg.entrySet()) p.setGeometry(Integer.parseInt(String.valueOf(e.getKey())), String.valueOf(e.getValue()));
        // version 1 files had one geometry for the whole project
        if ("hyperbolic".equals(m.get("geometry"))) for (int k = 0; k < p.passCount(); k++) p.setGeometry(k, EcProject.HYPER);
        p.nextId = Math.max((int) num(m.get("nextId")), maxId + 1);
        return p;
    }
    private static List<?> list(Object o) { return o instanceof List<?> l ? l : List.of(); }
    private static double num(Object o) {
        if (o instanceof Double d) return d;
        throw new IllegalArgumentException("number expected");
    }
}