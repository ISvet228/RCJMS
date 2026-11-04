package Project.ThemeCreator;
import Project.EngineCreator.*;
import Project.CreatorKit.*;
import java.util.*;

public final class ThemeDoc {
    //region Variables
    //region Layer Limits
    public static final int LAYER_MIN = -127, LAYER_MAX = 128;
    //endregion
    //region Document Identity
    public String id = "theme";
    public String name = "Theme";
    public String nameKey;
    public int order = 100;
    //endregion
    //region Object Tree
    public final List<ThemeObject> objects = new ArrayList<>();
    public int nextId = 1;
    //endregion
    //endregion

    //region Object Lookup
    public ThemeObject find(int id) {
        for (ThemeObject themeObject : objects) if (themeObject.id == id) return themeObject;
        return null;
    }
    public List<ThemeObject> children(int parent) {
        List<ThemeObject> out = new ArrayList<>();
        for (ThemeObject themeObject : objects) if (themeObject.parent == parent) out.add(themeObject);
        return out;
    }
    private static void walk(int parent, Map<Integer, List<ThemeObject>> kids, List<ThemeObject> out, Set<Integer> seen) {
        for (ThemeObject themeObject : kids.getOrDefault(parent, List.of())) {
            if (!seen.add(themeObject.id)) continue;
            out.add(themeObject);
            walk(themeObject.id, kids, out, seen);
        }
    }
    //endregion

    //region Object Editing
    public static int clampLayer(int v) { return Math.max(LAYER_MIN, Math.min(LAYER_MAX, v)); }
    public ThemeObject add(String type, int parent) {
        int newId = nextId++;
        ThemeObject themeObject = new ThemeObject(newId, ThemeTypes.known(type) ? type : "empty", type + " " + newId);
        themeObject.parent = find(parent) == null ? 0 : parent;
        objects.add(themeObject);
        return themeObject;
    }
    public boolean isWithin(int of, int candidate) {
        for (int id = candidate; id != 0; ) {
            if (id == of) return true;
            ThemeObject themeObject = find(id);
            if (themeObject == null) return false;
            id = themeObject.parent;
        }
        return false;
    }
    public boolean move(int id, int newParent, int beforeId) {
        ThemeObject themeObject = find(id);
        if (themeObject == null) return false;
        if (newParent != 0 && (find(newParent) == null || isWithin(id, newParent))) return false;
        objects.remove(themeObject);
        themeObject.parent = newParent;
        int at = objects.size();
        if (beforeId != 0) {
            ThemeObject before = find(beforeId);
            if (before != null && before.parent == newParent) at = objects.indexOf(before);
        } else {
            for (int i = 0; i < objects.size(); i++) if (objects.get(i).parent == newParent) at = i + 1;
            if (newParent == 0) at = objects.size();
        }
        objects.add(Math.min(at, objects.size()), themeObject);
        return true;
    }
    public void remove(int id) {
        ThemeObject themeObject = find(id);
        if (themeObject == null) return;
        for (ThemeObject c : children(id)) c.parent = themeObject.parent;
        objects.remove(themeObject);
    }
    public void repairParents() {
        for (ThemeObject themeObject : objects) if (themeObject.parent != 0 && find(themeObject.parent) == null) themeObject.parent = 0;
    }
    //endregion

    //region Draw Order
    public List<ThemeObject> treeOrder() {
        Map<Integer, List<ThemeObject>> kids = new HashMap<>();
        for (ThemeObject themeObject : objects) kids.computeIfAbsent(themeObject.parent, k -> new ArrayList<>()).add(themeObject);
        List<ThemeObject> tree = new ArrayList<>(objects.size());
        walk(0, kids, tree, new HashSet<>());
        return tree;
    }
    public List<ThemeObject> visibleTree() {
        List<ThemeObject> out = new ArrayList<>();
        for (ThemeObject themeObject : treeOrder()) if (themeObject.visible) out.add(themeObject);
        return out;
    }
    public List<ThemeObject> drawOrder() {
        List<ThemeObject> out = visibleTree();
        out.sort(Comparator.comparingInt((ThemeObject themeObject) -> themeObject.layer));
        return out;
    }
    //endregion
}