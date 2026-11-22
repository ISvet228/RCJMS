package Project.Filters;

import Project.Core.GameContext;
import Project.Core.GameModule;
import Project.Helpers.Settings;
import Project.Game.DevConsole;
import Project.Helpers.NSLocalizedString;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class FilterModule implements GameModule {
    //region Variables
    private static final String SETTING = "filters.current";
    private static final String NONE = "none";
    private final List<Filter> filters = FilterRegistry.create();
    private Filter current;
    private String loadedId = NONE;
    //endregion

    //region Constructors
    public FilterModule() {
        loadedId = Settings.getString(SETTING, NONE);
        current = find(loadedId);
    }
    //endregion

    //region Identity
    @Override public String id() { return "filters"; }
    //endregion

    //region Console
    @Override public void onConsoleReady(GameContext context) { context.command("filter", "filter <id|none|list>", this::runCommand); }
    private void runCommand(DevConsole.Args args) {
        List<String> values = args.values();
        if (values.isEmpty()) {
            args.info(NSLocalizedString.format("filter.console.current", currentName()));
            return;
        }
        String word = values.getFirst().toLowerCase(Locale.ROOT);
        if (word.equals("list")) {
            String ids = NONE + ", " + filters.stream().map(Filter::id).collect(Collectors.joining(", "));
            args.info(NSLocalizedString.format("filter.console.list", ids));
        } else if (word.equals(NONE)) {
            choose(null);
            args.info(NSLocalizedString.format("filter.console.set", currentName()));
        } else if (find(word) != null) {
            choose(find(word));
            args.info(NSLocalizedString.format("filter.console.set", currentName()));
        } else { args.warn(NSLocalizedString.format("filter.console.unknown", word)); }
    }
    //endregion

    //region Painting
    @Override public void postProcess(GameContext context, int[] pixels, int width, int height) {
        syncWithSettings();
        if (current != null) current.apply(pixels, width, height, context.elapsedSeconds());
    }
    private void syncWithSettings() {
        String id = Settings.getString(SETTING, NONE);
        if (id.equals(loadedId)) return;
        loadedId = id;
        current = find(id);
    }
    //endregion

    //region Choice
    private Filter find(String id) {
        for (Filter filter : filters) if (filter.id().equals(id)) return filter;
        return null;
    }
    private void choose(Filter filter) {
        current = filter;
        loadedId = filter == null ? NONE : filter.id();
        Settings.set(SETTING, loadedId);
    }
    private String currentName() { return current == null ? NSLocalizedString.get("filter.none") : NSLocalizedString.get(current.nameKey()); }
    //endregion
}