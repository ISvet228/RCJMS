package Project.Helpers;

import java.awt.Font;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class FontCache {
    //region Variables
    private static final int LIMIT = 256;
    private static final Map<Key, Font> FONTS = new ConcurrentHashMap<>();
    //endregion

    //region Constructors
    private FontCache() {}
    //endregion

    //region Lookup
    public static Font get(String family, int style, float size) {
        Key key = new Key(family, style, size);
        Font font = FONTS.get(key);
        if (font == null) {
            if (FONTS.size() >= LIMIT) FONTS.clear();
            font = new Font(family, style, 1).deriveFont(size);
            FONTS.put(key, font);
        }
        return font;
    }
    //endregion

    //region Key
    private record Key(String family, int style, float size) {}
    //endregion
}