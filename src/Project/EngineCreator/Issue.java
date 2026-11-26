package Project.EngineCreator;
import Project.CreatorKit.L;
record Issue(boolean error, int node, String text) {
    @Override public String toString() { return (error ? L.t("ec.issue.error") : L.t("ec.issue.warning")) + " " + text; }
}