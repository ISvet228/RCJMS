package Project.Core;

public final class CoreKeyEdge {
    //region Variables
    private boolean held;
    //endregion

    //region Queries
    public boolean firstPress(boolean matched, boolean down) {
        if (!matched) return false;
        if (!down) {
            held = false;
            return false;
        }
        boolean first = !held;
        held = true;
        return first;
    }
    //endregion
}