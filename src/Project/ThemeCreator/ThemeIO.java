package Project.ThemeCreator;
import Project.CreatorKit.*;

import java.awt.Color;
import java.util.*;

/** Reads and writes a theme as JSON: the objects, their properties, and each object's node graph. */
public final class ThemeIO {
    //region Variables
    public static final String FORMAT = "rcjms-theme", VERSION = "1";
    //endregion

    //region Constructors
    private ThemeIO() {}
    //endregion

    //region Serialization
    public static String toJson(ThemeDoc doc) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("{\n");
        stringBuilder.append("  \"format\": ").append(Json.quote(FORMAT)).append(",\n");
        stringBuilder.append("  \"version\": ").append(VERSION).append(",\n");
        stringBuilder.append("  \"id\": ").append(Json.quote(doc.id)).append(",\n");
        stringBuilder.append("  \"name\": ").append(Json.quote(doc.name)).append(",\n");
        if (doc.nameKey != null) stringBuilder.append("  \"nameKey\": ").append(Json.quote(doc.nameKey)).append(",\n");
        stringBuilder.append("  \"order\": ").append(doc.order).append(",\n");
        stringBuilder.append("  \"objects\": [");
        for (int i = 0; i < doc.objects.size(); i++) {
            stringBuilder.append(i == 0 ? "\n" : ",\n");
            object(stringBuilder, doc.objects.get(i));
        }
        stringBuilder.append(doc.objects.isEmpty() ? "]\n}\n" : "\n  ]\n}\n");
        return stringBuilder.toString();
    }
    static ThemeDoc fromJson(String text) {
        ThemeNodes.install();
        Object root = Json.parse(text);
        if (!(root instanceof Map<?, ?> m)) throw new IllegalArgumentException("not a theme");
        if (!FORMAT.equals(m.get("format"))) throw new IllegalArgumentException("not a theme");
        ThemeDoc doc = new ThemeDoc();
        doc.id = str(m.get("id"), "theme");
        doc.name = str(m.get("name"), doc.id);
        doc.nameKey = m.get("nameKey") instanceof String k ? k : null;
        doc.order = (int) num(m.get("order"), 100);
        if (m.get("objects") instanceof List<?> list) {
            for (Object item : list) readObject(doc, (Map<?, ?>) item);
        }
        doc.repairParents();
        doc.nextId = 1;
        for (ThemeObject themeObject : doc.objects) doc.nextId = Math.max(doc.nextId, themeObject.id + 1);
        return doc;
    }
    private static void object(StringBuilder stringBuilder, ThemeObject themeObject) {
        stringBuilder.append("    {\n");
        stringBuilder.append("      \"id\": ").append(themeObject.id).append(",\n");
        stringBuilder.append("      \"name\": ").append(Json.quote(themeObject.name)).append(",\n");
        stringBuilder.append("      \"type\": ").append(Json.quote(themeObject.type)).append(",\n");
        stringBuilder.append("      \"parent\": ").append(themeObject.parent).append(",\n");
        stringBuilder.append("      \"layer\": ").append(ThemeDoc.clampLayer(themeObject.layer)).append(",\n");
        stringBuilder.append("      \"visible\": ").append(themeObject.visible).append(",\n");
        stringBuilder.append("      \"props\": {");
        int i = 0;
        for (Map.Entry<String, Object> e : themeObject.props.entrySet()) {
            stringBuilder.append(i++ == 0 ? "" : ",").append("\n        ").append(Json.quote(e.getKey())).append(": ").append(value(e.getValue()));
        }
        stringBuilder.append(themeObject.props.isEmpty() ? "},\n" : "\n      },\n");
        graph(stringBuilder, themeObject.graph);
        stringBuilder.append("    }");
    }
    private static void graph(StringBuilder stringBuilder, CkProject ckProject) {
        stringBuilder.append("      \"graph\": {\n        \"nodes\": [");
        for (int i = 0; i < ckProject.nodes.size(); i++) {
            NodeInst nodeInst = ckProject.nodes.get(i);
            stringBuilder.append(i == 0 ? "\n" : ",\n").append("          {\"id\": ").append(nodeInst.id)
                    .append(", \"kind\": ").append(Json.quote(nodeInst.kind))
                    .append(", \"x\": ").append(nodeInst.x).append(", \"y\": ").append(nodeInst.y).append(", \"params\": {");
            int j = 0;
            for (Map.Entry<String, String> e : nodeInst.params.entrySet())
                stringBuilder.append(j++ == 0 ? "" : ", ").append(Json.quote(e.getKey())).append(": ").append(Json.quote(e.getValue()));
            stringBuilder.append("}}");
        }
        stringBuilder.append(ckProject.nodes.isEmpty() ? "],\n" : "\n        ],\n");
        stringBuilder.append("        \"links\": [");
        for (int i = 0; i < ckProject.links.size(); i++) {
            Link link = ckProject.links.get(i);
            stringBuilder.append(i == 0 ? "\n" : ",\n").append("          {\"from\": ").append(link.from())
                    .append(", \"fromPort\": ").append(Json.quote(link.fromPort()))
                    .append(", \"to\": ").append(link.to()).append(", \"toPort\": ").append(Json.quote(link.toPort())).append("}");
        }
        stringBuilder.append(ckProject.links.isEmpty() ? "]\n" : "\n        ]\n").append("      }\n");
    }
    private static String value(Object v) {
        if (v instanceof Number n) {
            double d = n.doubleValue();
            if (!Double.isFinite(d)) return "0";
            return d == Math.rint(d) && Math.abs(d) < 1e15 ? String.valueOf((long) d) : String.valueOf(d);
        }
        if (v instanceof Boolean b) return b.toString();
        if (v instanceof Color color) return Json.quote(String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue()));
        return Json.quote(String.valueOf(v));
    }
    //endregion

    //region Parsing
    private static void readObject(ThemeDoc doc, Map<?, ?> m) {
        int id = (int) num(m.get("id"), doc.nextId);
        String type = str(m.get("type"), "empty");
        if (type.equals("group")) type = "empty";
        ThemeObject themeObject = new ThemeObject(id, type, str(m.get("name"), "object"));
        if (!ThemeTypes.known(themeObject.type)) themeObject.type = "empty";
        themeObject.parent = (int) num(m.get("parent"), 0);
        themeObject.layer = ThemeDoc.clampLayer((int) num(m.get("layer"), 0));
        themeObject.visible = !(m.get("visible") instanceof Boolean b) || b;
        if (m.get("props") instanceof Map<?, ?> props) {
            themeObject.props.clear();
            for (Map.Entry<?, ?> e : props.entrySet()) {
                if (e.getValue() instanceof Number n) themeObject.props.put(String.valueOf(e.getKey()), n.doubleValue());
                else if (e.getValue() instanceof String s) themeObject.props.put(String.valueOf(e.getKey()), s);
                else if (e.getValue() instanceof Boolean bo) themeObject.props.put(String.valueOf(e.getKey()), bo);
            }
        }
        if (m.get("graph") instanceof Map<?, ?> g) readGraph(themeObject.graph, g);
        doc.objects.add(themeObject);
    }
    private static void readGraph(CkProject ckProject, Map<?, ?> g) {
        if (g.get("nodes") instanceof List<?> nodes) {
            for (Object item : nodes) {
                Map<?, ?> n = (Map<?, ?>) item;
                NodeInst node = new NodeInst((int) num(n.get("id"), ckProject.nextId), str(n.get("kind"), "th.number"),
                        (int) num(n.get("x"), 0), (int) num(n.get("y"), 0), 0);
                if (n.get("params") instanceof Map<?, ?> params) {
                    for (Map.Entry<?, ?> e : params.entrySet()) node.params.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
                }
                ckProject.nodes.add(node);
                ckProject.nextId = Math.max(ckProject.nextId, node.id + 1);
            }
        }
        if (g.get("links") instanceof List<?> links) {
            for (Object item : links) {
                Map<?, ?> l = (Map<?, ?>) item;
                ckProject.links.add(new Link((int) num(l.get("from"), 0), str(l.get("fromPort"), ""), (int) num(l.get("to"), 0), str(l.get("toPort"), "")));
            }
        }
    }
    private static String str(Object v, String fallback) { return v instanceof String s ? s : fallback; }
    private static double num(Object v, double fallback) { return v instanceof Number n ? n.doubleValue() : fallback; }
    //endregion

    //region Copying
    public static ThemeDoc copy(ThemeDoc src) {
        ThemeDoc themeDoc = new ThemeDoc();
        themeDoc.id = src.id;
        themeDoc.name = src.name;
        themeDoc.nameKey = src.nameKey;
        themeDoc.order = src.order;
        themeDoc.nextId = src.nextId;
        for (ThemeObject themeObject : src.objects) {
            ThemeObject c = new ThemeObject(themeObject.id, themeObject.type, themeObject.name);
            c.parent = themeObject.parent;
            c.layer = themeObject.layer;
            c.visible = themeObject.visible;
            c.props.clear();
            c.props.putAll(themeObject.props);
            c.graph = copyGraph(themeObject.graph);
            themeDoc.objects.add(c);
        }
        return themeDoc;
    }
    private static CkProject copyGraph(CkProject ckProject) {
        CkProject c = new CkProject();
        c.nextId = ckProject.nextId;
        for (NodeInst nodeInst : ckProject.nodes) {
            NodeInst m = new NodeInst(nodeInst.id, nodeInst.kind, nodeInst.x, nodeInst.y, nodeInst.pass);
            m.params.putAll(nodeInst.params);
            m.comment = nodeInst.comment;
            c.nodes.add(m);
        }
        c.links.addAll(ckProject.links);
        return c;
    }
    //endregion
}
