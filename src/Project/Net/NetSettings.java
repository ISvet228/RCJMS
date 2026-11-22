package Project.Net;

import Project.Helpers.Settings;

public final class NetSettings {
    //region Variables
    private static final String VISIBLE_KEY = "net.visible";
    private static final String SIMPLE_SPRITE_KEY = "net.simpleSprite";
    private static final String NAME_KEY = "net.name";
    private static final String PORT_KEY = "net.port";
    private static final String MAX_PLAYERS_KEY = "net.maxplayers";
    private static final String DEFAULT_NAME = "Room";
    private static final int NAME_LIMIT = 32;
    private static final int DEFAULT_PORT = 7777, MIN_PORT = 1024, MAX_PORT = 65535;
    private static final int DEFAULT_MAX_PLAYERS = 4, MIN_MAX_PLAYERS = 2, MAX_MAX_PLAYERS = NetProtocol.MAX_PLAYERS;
    //endregion

    //region Constructors
    private NetSettings() {}
    //endregion

    //region Show Players
    private static volatile boolean showPlayers = true;
    public static boolean showPlayers() { return showPlayers; }
    public static void setShowPlayers(boolean show) { showPlayers = show; }
    static void resetShowPlayers() { showPlayers = true; }
    public static boolean simplePlayerSprite() { return Settings.getBoolean(SIMPLE_SPRITE_KEY, false); }
    public static void setSimplePlayerSprite(boolean simple) { Settings.set(SIMPLE_SPRITE_KEY, String.valueOf(simple)); }
    //endregion

    //region Visibility
    public static boolean visible() { return Settings.getBoolean(VISIBLE_KEY, true); }
    public static void setVisible(boolean visible) { Settings.set(VISIBLE_KEY, String.valueOf(visible)); }
    //endregion

    //region Name
    public static String playerName() {
        String name = clean(Settings.getString(NAME_KEY, DEFAULT_NAME));
        return name.isEmpty() ? DEFAULT_NAME : name;
    }
    public static void setPlayerName(String name) {
        String cleaned = clean(name);
        Settings.set(NAME_KEY, cleaned.isEmpty() ? DEFAULT_NAME : cleaned);
    }

    private static String clean(String name) {
        if (name == null) return "";
        String trimmed = name.replaceAll("\\p{Cntrl}", "").trim();
        return trimmed.codePoints().limit(NAME_LIMIT).collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
    }
    //endregion

    //region Port
    public static int port() { return Math.clamp(Settings.getInt(PORT_KEY, DEFAULT_PORT), MIN_PORT, MAX_PORT); }
    public static void setPort(int port) { Settings.set(PORT_KEY, String.valueOf(Math.clamp(port, MIN_PORT, MAX_PORT))); }
    //endregion

    //region Players
    public static int maxPlayers() { return Math.clamp(Settings.getInt(MAX_PLAYERS_KEY, DEFAULT_MAX_PLAYERS), MIN_MAX_PLAYERS, MAX_MAX_PLAYERS); }
    public static void setMaxPlayers(int maxPlayers) { Settings.set(MAX_PLAYERS_KEY, String.valueOf(Math.clamp(maxPlayers, MIN_MAX_PLAYERS, MAX_MAX_PLAYERS))); }
    //endregion
}