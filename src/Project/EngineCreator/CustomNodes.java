package Project.EngineCreator;

import Project.CreatorKit.*;
import Project.Helpers.AppPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class CustomNodes {
    //region Variables
    //region Keys And Format
    public static final String CATEGORY = "custom";
    public static final String PREFIX = "custom.";
    public static final String FILE = "customNodes.txt";
    private static final String FORMAT = "rcjms-custom-nodes";
    //endregion
    //region Definitions
    private static final Map<String, Def> DEFS = new LinkedHashMap<>();
    //endregion
    //endregion

    //region Constructors
    private CustomNodes() {}
    //endregion

    //region Registry
    public static boolean isCustom(String kind) { return kind != null && kind.startsWith(PREFIX); }
    public static String titleOf(String kind) {
        Def def = DEFS.get(kind);
        return def == null ? kind : def.title();
    }
    public static String helpOf(String kind) {
        Def def = DEFS.get(kind);
        if (def == null) return "";
        return def.description().isBlank() ? L.f("ec.g.customHelp", def.inner().size()) : def.description();
    }
    public static Collection<Def> all() { return List.copyOf(DEFS.values()); }
    public static Def def(String kind) { return DEFS.get(kind); }
    public static Def defOf(CkProject ckProject, String kind) {
        Def def = DEFS.get(kind);
        return def != null ? def : ckProject.backup.get(kind);
    }
    private static void register(Def def) {
        DEFS.put(def.kind(), def);
        Nodes.registerCustom(kindOf(def));
    }
    private static NodeKind kindOf(Def def) {
        List<Port> ins = new ArrayList<>(), outs = new ArrayList<>();
        for (Exposed exposed : def.inputs()) ins.add(new Port(exposed.name(), exposed.type()));
        for (Exposed exposed : def.outputs()) outs.add(new Port(exposed.name(), exposed.type()));
        return new NodeKind(def.kind(), CATEGORY, List.copyOf(ins), List.copyOf(outs), List.of(),
                _ -> { throw new IllegalStateException("custom node " + def.kind() + " must be expanded before code generation"); });
    }
    public static void check(CkProject ckProject, Collection<Integer> selection) {
        if (selection.isEmpty()) throw new IllegalArgumentException(L.t("ec.g.nothingSelected"));
        int pass = -1;
        for (NodeInst nodeInst : ckProject.nodes) {
            if (!selection.contains(nodeInst.id)) continue;
            if (pass >= 0 && nodeInst.pass != pass) throw new IllegalArgumentException(L.t("ec.g.samePass"));
            pass = nodeInst.pass;
            if (Nodes.get(nodeInst.kind).isOutput()) throw new IllegalArgumentException(L.t("ec.g.noOutput"));
        }
    }
    public static NodeInst create(CkProject ckProject, Collection<Integer> selection, String title, String description) throws IOException {
        check(ckProject, selection);
        Set<Integer> sel = Set.copyOf(selection);
        List<NodeInst> nodes = new ArrayList<>();
        for (NodeInst nodeInst : ckProject.nodes) if (sel.contains(nodeInst.id)) nodes.add(nodeInst);
        int pass = nodes.getFirst().pass;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        for (NodeInst nodeInst : nodes) {
            minX = Math.min(minX, nodeInst.x);
            minY = Math.min(minY, nodeInst.y);
        }

        Map<Integer, Integer> index = new HashMap<>();
        List<Inner> inner = new ArrayList<>();
        for (NodeInst nodeInst : nodes) {
            index.put(nodeInst.id, inner.size());
            inner.add(new Inner(nodeInst.kind, nodeInst.x - minX, nodeInst.y - minY, new LinkedHashMap<>(nodeInst.params), nodeInst.comment));
        }
        List<InnerLink> innerLinks = new ArrayList<>();
        for (Link link : ckProject.links) if (sel.contains(link.from()) && sel.contains(link.to()))
            innerLinks.add(new InnerLink(index.get(link.from()), link.fromPort(), index.get(link.to()), link.toPort()));

        List<Exposed> inputs = new ArrayList<>(), outputs = new ArrayList<>();
        List<Feed> feeds = new ArrayList<>(), exits = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (NodeInst nodeInst : nodes) {
            NodeKind nodeKind = Nodes.get(nodeInst.kind);
            int idx = index.get(nodeInst.id);
            for (Port port : nodeKind.inputs()) {
                Link link = ckProject.incoming(nodeInst.id, port.name());
                if (link != null && sel.contains(link.from())) continue;
                String name = uniqueName(names, nodeKind.title() + "." + port.name());
                inputs.add(new Exposed(name, port.type(), idx, port.name()));
                if (link != null) feeds.add(new Feed(link, name));
            }
            for (Port port : nodeKind.outputs()) {
                List<Link> outside = new ArrayList<>();
                for (Link link : ckProject.links)
                    if (link.from() == nodeInst.id && link.fromPort().equals(port.name()) && !sel.contains(link.to())) outside.add(link);
                if (outside.isEmpty()) continue;
                DataType dataType = Graph.outType(ckProject, nodeInst.id, port.name(), 0);
                String name = uniqueName(names, nodeKind.title() + "." + port.name());
                outputs.add(new Exposed(name, dataType == null ? port.type() : dataType, idx, port.name()));
                for (Link link : outside) exits.add(new Feed(link, name));
            }
        }

        NodeGroup only = null;
        boolean whole = true;
        for (NodeInst nodeInst : nodes) {
            NodeGroup nodeGroup = ckProject.groupOf(nodeInst.id);
            if (nodeGroup == null || (only != null && nodeGroup != only)) whole = false;
            only = nodeGroup;
        }

        String kind = PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Def def = new Def(kind, title.strip(), description.strip(), List.copyOf(inner), List.copyOf(innerLinks), List.copyOf(inputs), List.copyOf(outputs));
        register(def);
        NodeInst instance = ckProject.add(kind, minX, minY, pass);
        if (whole && only != null) only.nodes.add(instance.id);
        for (int id : sel) ckProject.removeNode(id);
        for (Feed feed : feeds) ckProject.setLink(new Link(feed.link().from(), feed.link().fromPort(), instance.id, feed.name()));
        for (Feed feed : exits) ckProject.setLink(new Link(instance.id, feed.name(), feed.link().to(), feed.link().toPort()));
        save();
        return instance;
    }
    //endregion

    //region Persistence
    public static String load() {
        Path file = AppPaths.file(FILE);
        if (!Files.exists(file)) return null;
        try {
            for (Def def : parseFile(Files.readString(file, StandardCharsets.UTF_8))) register(def);
            return null;
        }
        catch (IOException | RuntimeException exception) { return L.f("ec.g.loadFailed", exception.getMessage()); }
    }
    public static void update(Def def) throws IOException {
        register(def);
        save();
    }
    public static void updateTexts(String kind, String title, String description) throws IOException {
        Def def = DEFS.get(kind);
        if (def == null) return;
        update(new Def(def.kind(), title.strip(), description.strip(), def.inner(), def.links(), def.inputs(), def.outputs()));
    }
    public static void delete(String kind) throws IOException {
        if (DEFS.remove(kind) == null) return;
        Nodes.unregisterCustom(kind);
        save();
    }
    public static void save() throws IOException { Files.writeString(AppPaths.file(FILE), fileText(DEFS.values()), StandardCharsets.UTF_8); }
    public static String fileText(Collection<Def> defs) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("{\n  \"format\": ").append(Json.quote(FORMAT)).append(",\n  \"version\": 1,\n  \"nodes\": [\n");
        int i = 0;
        for (Def def : defs) {
            stringBuilder.append("    ").append(toJson(def));
            stringBuilder.append(++i < defs.size() ? ",\n" : "\n");
        }
        stringBuilder.append("  ]\n}\n");
        return stringBuilder.toString();
    }
    public static String toJson(Def def) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("{\"kind\": ").append(Json.quote(def.kind())).append(", \"title\": ").append(Json.quote(def.title()))
                .append(", \"description\": ").append(Json.quote(def.description())).append(", \"inner\": [");
        for (int i = 0; i < def.inner().size(); i++) {
            Inner inner = def.inner().get(i);
            if (i > 0) stringBuilder.append(", ");
            stringBuilder.append("{\"kind\": ").append(Json.quote(inner.kind())).append(", \"x\": ").append(inner.x()).append(", \"y\": ").append(inner.y()).append(", \"params\": {");
            int k = 0;
            for (Map.Entry<String, String> e : inner.params().entrySet()) {
                if (k++ > 0) stringBuilder.append(", ");
                stringBuilder.append(Json.quote(e.getKey())).append(": ").append(Json.quote(e.getValue()));
            }
            stringBuilder.append("}, \"comment\": ").append(Json.quote(inner.comment())).append("}");
        }
        stringBuilder.append("], \"links\": [");
        for (int i = 0; i < def.links().size(); i++) {
            InnerLink innerLink = def.links().get(i);
            if (i > 0) stringBuilder.append(", ");
            stringBuilder.append("{\"from\": ").append(innerLink.from()).append(", \"fromPort\": ").append(Json.quote(innerLink.fromPort()))
                    .append(", \"to\": ").append(innerLink.to()).append(", \"toPort\": ").append(Json.quote(innerLink.toPort())).append("}");
        }
        stringBuilder.append("], \"inputs\": [");
        appendExposed(stringBuilder, def.inputs());
        stringBuilder.append("], \"outputs\": [");
        appendExposed(stringBuilder, def.outputs());
        stringBuilder.append("]}");
        return stringBuilder.toString();
    }
    public static List<Def> parseFile(String text) {
        Object root = Json.parse(text);
        if (!(root instanceof Map<?, ?> m) || !FORMAT.equals(m.get("format"))) throw new IllegalArgumentException("not a custom node file");
        List<Def> out = new ArrayList<>();
        for (Object o : list(m.get("nodes"))) out.add(parseDef(o));
        return out;
    }
    public static Def parseDef(Object o) {
        if (!(o instanceof Map<?, ?> m)) throw new IllegalArgumentException("custom node expected");
        String kind = text(m.get("kind"));
        if (!isCustom(kind)) throw new IllegalArgumentException("bad custom node id: " + kind);
        List<Inner> inner = new ArrayList<>();
        for (Object x : list(m.get("inner"))) {
            Map<?, ?> im = map(x);
            Map<String, String> params = new LinkedHashMap<>();
            if (im.get("params") instanceof Map<?, ?> pm)
                for (Map.Entry<?, ?> e : pm.entrySet()) params.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
            inner.add(new Inner(text(im.get("kind")), integer(im.get("x")), integer(im.get("y")), params, text(im.get("comment"))));
        }
        List<InnerLink> links = new ArrayList<>();
        for (Object x : list(m.get("links"))) {
            Map<?, ?> lm = map(x);
            links.add(new InnerLink(integer(lm.get("from")), text(lm.get("fromPort")), integer(lm.get("to")), text(lm.get("toPort"))));
        }
        return new Def(kind, text(m.get("title")), text(m.get("description")), List.copyOf(inner), List.copyOf(links),
                exposed(m.get("inputs")), exposed(m.get("outputs")));
    }
    //endregion

    //region Editing Custom Nodes
    // ---------- editing inside a custom node ----------
    public static CkProject openInner(Def def) {
        CkProject ckProject = new CkProject();
        ckProject.name = "inner";
        ckProject.title = def.title();
        for (int i = 0; i < def.inner().size(); i++) {
            Inner inner = def.inner().get(i);
            NodeInst node = new NodeInst(i + 1, inner.kind(), inner.x() + 40, inner.y() + 60, 0);
            node.params.putAll(inner.params());
            node.comment = inner.comment();
            ckProject.nodes.add(node);
        }
        for (InnerLink innerLink : def.links()) ckProject.links.add(new Link(innerLink.from() + 1, innerLink.fromPort(), innerLink.to() + 1, innerLink.toPort()));
        ckProject.nextId = def.inner().size() + 1;
        return ckProject;
    }
    public static Rebuilt rebuild(CkProject inner, Def old, String title, String description) {
        List<NodeInst> nodes = new ArrayList<>(inner.nodes);
        nodes.sort(Comparator.comparingInt(nodeInst -> nodeInst.id));
        Map<Integer, Integer> newIndex = new HashMap<>();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        for (NodeInst nodeInst : nodes) {
            newIndex.put(nodeInst.id, newIndex.size());
            minX = Math.min(minX, nodeInst.x);
            minY = Math.min(minY, nodeInst.y);
        }
        if (nodes.isEmpty()) minX = minY = 0;

        Map<Integer, Integer> oldToNew = new HashMap<>();
        for (int i = 0; i < old.inner().size(); i++) if (newIndex.containsKey(i + 1)) oldToNew.put(i, newIndex.get(i + 1));

        List<Inner> inner2 = new ArrayList<>();
        for (NodeInst nodeInst : nodes) inner2.add(new Inner(nodeInst.kind, nodeInst.x - minX, nodeInst.y - minY, new LinkedHashMap<>(nodeInst.params), nodeInst.comment));
        List<InnerLink> links = new ArrayList<>();
        Set<String> fedInside = new HashSet<>(), usedInside = new HashSet<>();
        for (Link link : inner.links) {
            if (!newIndex.containsKey(link.from()) || !newIndex.containsKey(link.to())) continue;
            links.add(new InnerLink(newIndex.get(link.from()), link.fromPort(), newIndex.get(link.to()), link.toPort()));
            fedInside.add(newIndex.get(link.to()) + ":" + link.toPort());
            usedInside.add(newIndex.get(link.from()) + ":" + link.fromPort());
        }

        Set<String> names = new HashSet<>();
        List<Exposed> inputs = new ArrayList<>(), outputs = new ArrayList<>();
        for (NodeInst nodeInst : nodes) {
            NodeKind nodeKind = Nodes.get(nodeInst.kind);
            int idx = newIndex.get(nodeInst.id);
            int oldIdx = nodeInst.id - 1;
            for (Port port : nodeKind.inputs()) {
                if (fedInside.contains(idx + ":" + port.name())) continue;
                String name = previousName(old.inputs(), oldIdx, port.name());
                if (name == null) name = uniqueName(names, nodeKind.title() + "." + port.name());
                else names.add(name);
                inputs.add(new Exposed(name, port.type(), idx, port.name()));
            }
            for (Port port : nodeKind.outputs()) {
                boolean was = previousName(old.outputs(), oldIdx, port.name()) != null;
                if (usedInside.contains(idx + ":" + port.name()) && !was) continue;
                DataType dataType = Graph.outType(inner, nodeInst.id, port.name(), 0);
                String name = previousName(old.outputs(), oldIdx, port.name());
                if (name == null) name = uniqueName(names, nodeKind.title() + "." + port.name());
                else names.add(name);
                outputs.add(new Exposed(name, dataType == null ? port.type() : dataType, idx, port.name()));
            }
        }
        Def def = new Def(old.kind(), title.strip(), description.strip(), List.copyOf(inner2), List.copyOf(links), List.copyOf(inputs), List.copyOf(outputs));
        return new Rebuilt(def, oldToNew);
    }
    public static void migrate(CkProject ckProject, Def old, Def neu, Map<Integer, Integer> oldToNew) {
        Set<Integer> instances = new HashSet<>();
        for (NodeInst nodeInst : ckProject.nodes) if (nodeInst.kind.equals(old.kind())) instances.add(nodeInst.id);
        if (instances.isEmpty()) return;
        Map<String, String> inMap = portMap(old.inputs(), neu.inputs(), oldToNew);
        Map<String, String> outMap = portMap(old.outputs(), neu.outputs(), oldToNew);
        List<Link> rebuilt = new ArrayList<>();
        for (Link link : ckProject.links) {
            if (instances.contains(link.to())) {
                String np = inMap.get(link.toPort());
                if (np != null) rebuilt.add(new Link(link.from(), link.fromPort(), link.to(), np));
            }
            else if (instances.contains(link.from())) {
                String np = outMap.get(link.fromPort());
                if (np != null) rebuilt.add(new Link(link.from(), np, link.to(), link.toPort()));
            }
            else rebuilt.add(link);
        }
        ckProject.links.clear();
        ckProject.links.addAll(rebuilt);
    }
    //endregion

    //region Port Mapping
    private static String previousName(List<Exposed> ports, int node, String port) {
        for (Exposed exposed : ports) if (exposed.node() == node && exposed.port().equals(port)) return exposed.name();
        return null;
    }
    private static Map<String, String> portMap(List<Exposed> oldPorts, List<Exposed> newPorts, Map<Integer, Integer> oldToNew) {
        Map<String, String> map = new HashMap<>();
        for (Exposed exposed : oldPorts) {
            Integer node = oldToNew.get(exposed.node());
            if (node == null) continue;
            for (Exposed f : newPorts)
                if (f.node() == node && f.port().equals(exposed.port())) { map.put(exposed.name(), f.name()); break; }
        }
        return map;
    }
    private static Exposed find(List<Exposed> ports, String name) {
        for (Exposed exposed : ports) if (exposed.name().equals(name)) return exposed;
        return null;
    }
    private static void appendExposed(StringBuilder stringBuilder, List<Exposed> ports) {
        for (int i = 0; i < ports.size(); i++) {
            Exposed exposed = ports.get(i);
            if (i > 0) stringBuilder.append(", ");
            stringBuilder.append("{\"name\": ").append(Json.quote(exposed.name())).append(", \"type\": ").append(Json.quote(exposed.type().name()))
                    .append(", \"node\": ").append(exposed.node()).append(", \"port\": ").append(Json.quote(exposed.port())).append("}");
        }
    }
    private static List<Exposed> exposed(Object o) {
        List<Exposed> out = new ArrayList<>();
        for (Object x : list(o)) {
            Map<?, ?> m = map(x);
            out.add(new Exposed(text(m.get("name")), DataType.valueOf(text(m.get("type"))), integer(m.get("node")), text(m.get("port"))));
        }
        return List.copyOf(out);
    }
    private static String uniqueName(Set<String> used, String base) {
        String name = base;
        int n = 2;
        while (!used.add(name)) name = base + " " + n++;
        return name;
    }
    //endregion

    //region Flattening
    public static CkProject flatten(CkProject ckProject) {
        CkProject out = new CkProject();
        out.name = ckProject.name;
        out.title = ckProject.title;
        out.openCl = ckProject.openCl;
        out.curvature = ckProject.curvature;
        out.passGeometry.putAll(ckProject.passGeometry);
        out.nextId = ckProject.nextId;
        out.backup.putAll(ckProject.backup);
        for (NodeInst nodeInst : ckProject.nodes) {
            NodeInst c = new NodeInst(nodeInst.id, nodeInst.kind, nodeInst.x, nodeInst.y, nodeInst.pass);
            c.params.putAll(nodeInst.params);
            c.comment = nodeInst.comment;
            out.nodes.add(c);
        }
        out.links.addAll(ckProject.links);
        for (int steps = 0; ; steps++) {
            NodeInst instance = null;
            for (NodeInst nodeInst : out.nodes) if (isCustom(nodeInst.kind)) { instance = nodeInst; break; }
            if (instance == null) return out;
            if (steps > 10000) throw new IllegalStateException("custom nodes are nested too deeply");
            Def def = defOf(out, instance.kind);
            if (def == null) throw new IllegalStateException("custom node " + instance.kind + " is not defined");
            expandInstance(out, instance, def);
        }
    }
    public static void expandMissing(CkProject ckProject) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (NodeInst nodeInst : List.copyOf(ckProject.nodes)) {
                if (!isCustom(nodeInst.kind) || Nodes.find(nodeInst.kind) != null) continue;
                Def def = ckProject.backup.get(nodeInst.kind);
                if (def == null) continue;
                try {
                    expandInstance(ckProject, nodeInst, def);
                    changed = true;
                } catch (IllegalArgumentException _) { }
            }
        }
        ckProject.pruneGroups();
    }
    public static void expandInstance(CkProject out, NodeInst instance, Def def) {
        Map<Integer, Integer> ids = new HashMap<>();
        for (int i = 0; i < def.inner().size(); i++) {
            Inner inner = def.inner().get(i);
            NodeInst nodeInst = out.add(inner.kind(), instance.x + inner.x(), instance.y + inner.y(), instance.pass);
            nodeInst.params.clear();
            nodeInst.params.putAll(inner.params());
            nodeInst.comment = inner.comment();
            ids.put(i, nodeInst.id);
        }
        List<Link> rebuilt = new ArrayList<>();
        for (Link link : out.links) {
            if (link.to() == instance.id) {
                Exposed exposed = find(def.inputs(), link.toPort());
                rebuilt.add(exposed == null ? link : new Link(link.from(), link.fromPort(), ids.get(exposed.node()), exposed.port()));
            }
            else if (link.from() == instance.id) {
                Exposed exposed = find(def.outputs(), link.fromPort());
                rebuilt.add(exposed == null ? link : new Link(ids.get(exposed.node()), exposed.port(), link.to(), link.toPort()));
            }
            else rebuilt.add(link);
        }
        for (InnerLink innerLink : def.links()) rebuilt.add(new Link(ids.get(innerLink.from()), innerLink.fromPort(), ids.get(innerLink.to()), innerLink.toPort()));
        out.links.clear();
        out.links.addAll(rebuilt);
        out.nodes.removeIf(nodeInst -> nodeInst.id == instance.id);
    }
    public static List<Def> usedBy(CkProject ckProject) {
        if (!ckProject.backupCustom) return List.of();
        Map<String, Def> found = new LinkedHashMap<>();
        Deque<String> todo = new ArrayDeque<>();
        for (NodeInst nodeInst : ckProject.nodes) todo.add(nodeInst.kind);
        todo.addAll(ckProject.backup.keySet());
        while (!todo.isEmpty()) {
            String kind = todo.poll();
            if (!isCustom(kind) || found.containsKey(kind)) continue;
            Def def = defOf(ckProject, kind);
            if (def == null) continue;
            found.put(kind, def);
            for (Inner inner : def.inner()) todo.add(inner.kind());
        }
        return List.copyOf(found.values());
    }
    //endregion

    //region Value Conversion
    private static List<?> list(Object o) { return o instanceof List<?> l ? l : List.of(); }
    private static Map<?, ?> map(Object o) {
        if (o instanceof Map<?, ?> m) return m;
        throw new IllegalArgumentException("object expected");
    }
    private static String text(Object o) { return o == null ? "" : String.valueOf(o); }
    private static int integer(Object o) {
        if (o instanceof Double d) return (int) (double) d;
        throw new IllegalArgumentException("number expected");
    }
    //endregion

    //region Node Types
    public record Inner(String kind, int x, int y, Map<String, String> params, String comment) { }
    public record InnerLink(int from, String fromPort, int to, String toPort) { }
    public record Exposed(String name, DataType type, int node, String port) { }
    public record Def(String kind, String title, String description, List<Inner> inner, List<InnerLink> links, List<Exposed> inputs, List<Exposed> outputs) { }
    public record Rebuilt(Def def, Map<Integer, Integer> oldToNew) { }
    private record Feed(Link link, String name) { }
    //endregion
}