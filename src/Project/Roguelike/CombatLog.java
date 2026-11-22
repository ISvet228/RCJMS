package Project.Roguelike;

import java.util.*;

public final class CombatLog {
    private static final int KEEP = 20;
    private final Deque<String> lines = new ArrayDeque<>();

    //region Writing
    public synchronized void add(String text) {
        lines.addLast(text);
        while (lines.size() > KEEP) lines.removeFirst();
    }
    public synchronized void clear() { lines.clear(); }
    //endregion

    //region Reading
    public synchronized List<String> last(int count) {
        List<String> all = new ArrayList<>(lines);
        return all.subList(Math.max(0, all.size() - count), all.size());
    }
    //endregion
}