package Project.EngineCreator;

import Project.Helpers.NSLocalizedString;

public final class L {
    private L() {}
    static String t(String key) { return NSLocalizedString.get(key); }
    static String f(String key, Object... args) { return NSLocalizedString.format(key, args); }
}