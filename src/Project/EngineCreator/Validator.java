package Project.EngineCreator;

import java.util.*;
import java.util.regex.Pattern;

record Issue(boolean error, int node, String text) {
    @Override public String toString() { return (error ? L.t("ec.issue.error") : L.t("ec.issue.warning")) + " " + text; } }

/** Checks a project before code is generated. Errors block saving; warnings do not. */
public final class Validator {
    private static final Pattern NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]{2,31}");
    private static final Pattern HEX = Pattern.compile("#[0-9A-Fa-f]{6}");

    private Validator() {}

    static List<Issue> validate(EcProject p) {
        List<Issue> out = new ArrayList<>();
        for (NodeInst n : p.nodes) {
            if (Nodes.find(n.kind) == null) {
                out.add(err(n.id, L.f("ec.v.unknownNode", n.kind)));
                return out;
            }
        }
        if (!NAME.matcher(p.name).matches()) out.add(err(-1, L.t("ec.v.name")));
        if (p.title == null || p.title.isBlank() || p.title.length() > 60) out.add(err(-1, L.t("ec.v.title")));
        if (p.usesHyperbolic() && (p.curvature < 0.01 || p.curvature > 100 || !Double.isFinite(p.curvature))) out.add(err(-1, L.t("ec.v.curvature")));

        for (NodeInst n : p.nodes) {
            NodeKind k = Nodes.get(n.kind);
            for (Param prm : k.params()) checkParam(n, k, prm, out);
            if (n.kind.equals("neighbor") && n.pass == 0) out.add(err(n.id, L.f("ec.v.neighborPass0", k.title())));
        }

        for (int pass = 0; pass < p.passCount(); pass++) {
            int outputs = 0;
            for (NodeInst n : p.nodes) {
                if (n.pass != pass || !Nodes.get(n.kind).isOutput()) continue;
                outputs++;
                if (outputs > 1) out.add(err(n.id, L.f("ec.v.twoOutputs", pass + 1)));
            }
            if (outputs == 0) out.add(err(-1, L.f("ec.v.noOutput", pass + 1)));
        }
        for (Link l : p.links) {
            NodeInst to = p.node(l.to()), from = p.node(l.from());
            if (to == null || from == null) { out.add(err(-1, L.t("ec.v.danglingLink"))); continue; }
            if (to.pass != from.pass) { out.add(err(to.id, L.t("ec.v.crossPass"))); continue; }
            NodeKind tk = Nodes.get(to.kind), fk = Nodes.get(from.kind);
            int ii = tk.inputIndex(l.toPort()), oi = fk.outputIndex(l.fromPort());
            if (ii < 0 || oi < 0) { out.add(err(to.id, L.t("ec.v.badPort"))); continue; }
            DataType want = tk.inputs().get(ii).type();
            DataType have = Graph.outType(p, from.id, l.fromPort(), 0);
            if (have != null && Graph.incompatible(have, want))
                out.add(err(to.id, L.f("ec.v.typeMismatch", tk.title(), l.toPort(), want.label(), have.label(), fk.title())));
        }
        for (int pass = 0; pass < p.passCount(); pass++) {
            NodeInst outNode = p.output(pass);
            if (outNode == null) continue;
            List<Integer> order = Graph.order(p, outNode.id);
            if (order == null) {
                out.add(err(-1, L.t("ec.v.cycle")));
                return out;
            }
            Set<Integer> used = new HashSet<>(order);
            for (int id : order) {
                NodeInst n = p.node(id);
                assert n != null;
                NodeKind k = Nodes.get(n.kind);
                for (Port port : k.inputs())
                    if (p.incoming(id, port.name()) == null)
                        out.add(err(id, L.f("ec.v.inputMissing", k.title(), port.name())));
                if (k.id().equals("select")) {
                    Link a = p.incoming(id, "a"), b = p.incoming(id, "b");
                    if (a != null && b != null) {
                        DataType ta = Graph.outType(p, a.from(), a.fromPort(), 0);
                        DataType tb = Graph.outType(p, b.from(), b.fromPort(), 0);
                        if (ta != null && tb != null && ta != tb)
                            out.add(err(id, L.f("ec.v.selectTypes", ta.label(), tb.label())));
                    }
                }
            }
            for (NodeInst n : p.nodes)
                if (n.pass == pass && !used.contains(n.id))
                    out.add(new Issue(false, n.id, L.f("ec.v.unused", Nodes.get(n.kind).title())));
        }
        return out;
    }

    private static void checkParam(NodeInst n, NodeKind k, Param prm, List<Issue> out) {
        String v = n.params.get(prm.key());
        if (v == null) { out.add(err(n.id, L.f("ec.v.paramMissing", k.title(), prm.label()))); return; }
        String where = k.title();
        switch (prm.kind()) {
            case FLOAT -> {
                try {
                    double d = Double.parseDouble(v.trim());
                    if (!Double.isFinite(d) || d < prm.min() || d > prm.max())
                        out.add(err(n.id, L.f("ec.v.param.range", where, prm.label(), num(prm.min()), num(prm.max()))));
                } catch (NumberFormatException e) { out.add(err(n.id, L.f("ec.v.param.number", where, prm.label(), v))); }
            }
            case INT -> {
                try {
                    int d = Integer.parseInt(v.trim());
                    if (d < prm.min() || d > prm.max())
                        out.add(err(n.id, L.f("ec.v.param.range", where, prm.label(), (int) prm.min(), (int) prm.max())));
                } catch (NumberFormatException e) { out.add(err(n.id, L.f("ec.v.param.number", where, prm.label(), v))); }
            }
            case COLOR -> { if (!HEX.matcher(v).matches()) out.add(err(n.id, L.f("ec.v.param.color", where, prm.label()))); }
            case CHOICE -> { if (!prm.choices().contains(v)) out.add(err(n.id, L.f("ec.v.param.choice", where, prm.label()))); }
        }
    }

    static Issue err(int node, String text) { return new Issue(true, node, text); }
    private static String num(double d) { return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d); }
}