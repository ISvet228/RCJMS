package Project.EngineCreator;
import Project.CreatorKit.*;

import java.util.*;
import java.util.regex.Pattern;

record Issue(boolean error, int node, String text) {
//region Formatting
@Override public String toString() { return (error ? L.t("ec.issue.error") : L.t("ec.issue.warning")) + " " + text; }
//endregion
 }

/** Checks a project before code is generated. Errors block saving; warnings do not. */
public final class Validator {
    //region Variables
    private static final Pattern NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]{2,31}");
    //endregion

    //region Constructors
    private Validator() {}
    //endregion

    //region Validation
    static List<Issue> validate(CkProject ckProject) {
        List<Issue> out = new ArrayList<>();
        for (NodeInst nodeInst : ckProject.nodes) {
            if (Nodes.find(nodeInst.kind) == null) {
                out.add(err(nodeInst.id, L.f("ec.v.unknownNode", nodeInst.kind)));
                return out;
            }
        }
        if (!NAME.matcher(ckProject.name).matches()) out.add(err(-1, L.t("ec.v.name")));
        if (ckProject.title == null || ckProject.title.isBlank() || ckProject.title.length() > 60) out.add(err(-1, L.t("ec.v.title")));
        if (ckProject.usesHyperbolic() && (ckProject.curvature < 0.01 || ckProject.curvature > 100 || !Double.isFinite(ckProject.curvature))) out.add(err(-1, L.t("ec.v.curvature")));

        for (NodeInst nodeInst : ckProject.nodes) {
            NodeKind nodeKind = Nodes.get(nodeInst.kind);
            for (Param prm : nodeKind.params()) checkParam(nodeInst, nodeKind, prm, out);
            if (nodeInst.kind.equals("neighbor") && nodeInst.pass == 0) out.add(err(nodeInst.id, L.f("ec.v.neighborPass0", nodeKind.title())));
        }

        for (int pass = 0; pass < ckProject.passCount(); pass++) {
            int outputs = 0;
            for (NodeInst nodeInst : ckProject.nodes) {
                if (nodeInst.pass != pass || !Nodes.get(nodeInst.kind).isOutput()) continue;
                outputs++;
                if (outputs > 1) out.add(err(nodeInst.id, L.f("ec.v.twoOutputs", pass + 1)));
            }
            if (outputs == 0) out.add(err(-1, L.f("ec.v.noOutput", pass + 1)));
        }
        for (Link link : ckProject.links) {
            NodeInst nodeInst = ckProject.node(link.to()), from = ckProject.node(link.from());
            if (nodeInst == null || from == null) { out.add(err(-1, L.t("ec.v.danglingLink"))); continue; }
            if (nodeInst.pass != from.pass) { out.add(err(nodeInst.id, L.t("ec.v.crossPass"))); continue; }
            NodeKind nodeKind = Nodes.get(nodeInst.kind), fk = Nodes.get(from.kind);
            int ii = nodeKind.inputIndex(link.toPort()), oi = fk.outputIndex(link.fromPort());
            if (ii < 0 || oi < 0) { out.add(err(nodeInst.id, L.t("ec.v.badPort"))); continue; }
            DataType want = nodeKind.inputs().get(ii).type();
            DataType have = Graph.outType(ckProject, from.id, link.fromPort(), 0);
            if (have != null && Graph.incompatible(have, want))
                out.add(err(nodeInst.id, L.f("ec.v.typeMismatch", nodeKind.title(), link.toPort(), want.label(), have.label(), fk.title())));
        }
        for (int pass = 0; pass < ckProject.passCount(); pass++) {
            NodeInst outNode = ckProject.output(pass);
            if (outNode == null) continue;
            List<Integer> order = Graph.order(ckProject, outNode.id);
            if (order == null) {
                out.add(err(-1, L.t("ec.v.cycle")));
                return out;
            }
            Set<Integer> used = new HashSet<>(order);
            for (int id : order) {
                NodeInst nodeInst = ckProject.node(id);
                assert nodeInst != null;
                NodeKind nodeKind = Nodes.get(nodeInst.kind);
                for (Port port : nodeKind.inputs())
                    if (ckProject.incoming(id, port.name()) == null)
                        out.add(err(id, L.f("ec.v.inputMissing", nodeKind.title(), port.name())));
                if (nodeKind.id().equals("select")) {
                    Link link = ckProject.incoming(id, "a"), b = ckProject.incoming(id, "b");
                    if (link != null && b != null) {
                        DataType dataType = Graph.outType(ckProject, link.from(), link.fromPort(), 0);
                        DataType tb = Graph.outType(ckProject, b.from(), b.fromPort(), 0);
                        if (dataType != null && tb != null && dataType != tb)
                            out.add(err(id, L.f("ec.v.selectTypes", dataType.label(), tb.label())));
                    }
                }
            }
            for (NodeInst nodeInst : ckProject.nodes)
                if (nodeInst.pass == pass && !used.contains(nodeInst.id))
                    out.add(new Issue(false, nodeInst.id, L.f("ec.v.unused", Nodes.get(nodeInst.kind).title())));
        }
        return out;
    }
    private static void checkParam(NodeInst nodeInst, NodeKind nodeKind, Param prm, List<Issue> out) {
        String v = nodeInst.params.get(prm.key());
        if (v == null) { out.add(err(nodeInst.id, L.f("ec.v.paramMissing", nodeKind.title(), prm.label()))); return; }
        String where = nodeKind.title();
        switch (prm.kind()) {
            case FLOAT -> {
                try {
                    double d = Double.parseDouble(v.trim());
                    if (!Double.isFinite(d) || d < prm.min() || d > prm.max())
                        out.add(err(nodeInst.id, L.f("ec.v.param.range", where, prm.label(), num(prm.min()), num(prm.max()))));
                } catch (NumberFormatException exception) { out.add(err(nodeInst.id, L.f("ec.v.param.number", where, prm.label(), v))); }
            }
            case INT -> {
                try {
                    int d = Integer.parseInt(v.trim());
                    if (d < prm.min() || d > prm.max())
                        out.add(err(nodeInst.id, L.f("ec.v.param.range", where, prm.label(), (int) prm.min(), (int) prm.max())));
                } catch (NumberFormatException exception) { out.add(err(nodeInst.id, L.f("ec.v.param.number", where, prm.label(), v))); }
            }
            case COLOR -> { if (Param.normalizeHex(v) == null) out.add(err(nodeInst.id, L.f("ec.v.param.color", where, prm.label()))); }
            case CHOICE -> { if (!prm.choices().contains(v)) out.add(err(nodeInst.id, L.f("ec.v.param.choice", where, prm.label()))); }
        }
    }
    //endregion

    //region Issues
    static Issue err(int node, String text) { return new Issue(true, node, text); }
    private static String num(double d) { return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d); }
    //endregion
}