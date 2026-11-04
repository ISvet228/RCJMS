package Project.CreatorKit;
import Project.Helpers.NSLocalizedString;

public final class L {
    //region Constructors
    private L() {}
    //endregion

    //region Translation
    public static String t(String key) { return NSLocalizedString.get(key); }
    public static String f(String key, Object... args) { return NSLocalizedString.format(key, args); }
    //endregion
}