package Project.Progress;

import Project.Helpers.AppPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class Leaderboard {
    //region Types
    public record Entry(double seconds, long epochMillis) {}
    //endregion

    //region Variables
    private static final String FILE_NAME = "leaderboard.prt";
    private static final int TOP = 5;
    private static Path root = AppPaths.DATA_DIR;
    private static final Map<String, List<Entry>> BOARDS = new LinkedHashMap<>();
    private static boolean loaded;
    //endregion

    //region Constructors
    private Leaderboard() {}
    //endregion

    //region Keys
    public static String key(String seed, int width, int height, int floors, boolean hasFloors, int geometry) {
        String text = (seed == null ? "" : seed) + "|" + width + "x" + height + "|f" + floors + "|" + (hasFloors ? "floors" : "flat") + "|g" + geometry;
        return text.replaceAll("[\\t\\r\\n]", " ");
    }
    //endregion

    //region Queries
    public static synchronized double best(String key) {
        ensureLoaded();
        List<Entry> entries = BOARDS.get(key);
        return entries == null || entries.isEmpty() ? Double.NaN : entries.getFirst().seconds();
    }
    public static synchronized Map<String, List<Entry>> all() {
        ensureLoaded();
        Map<String, List<Entry>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<Entry>> board : BOARDS.entrySet()) copy.put(board.getKey(), List.copyOf(board.getValue()));
        return Collections.unmodifiableMap(copy);
    }
    //endregion

    //region Recording
    public static synchronized int record(String key, double seconds) {
        if (key == null || !Double.isFinite(seconds) || seconds <= 0) return 0;
        ensureLoaded();
        List<Entry> entries = BOARDS.computeIfAbsent(key, unused -> new ArrayList<>());
        int position = 0;
        while (position < entries.size() && entries.get(position).seconds() <= seconds) position++;
        if (position >= TOP) return 0;
        entries.add(position, new Entry(seconds, System.currentTimeMillis()));
        while (entries.size() > TOP) entries.removeLast();
        save();
        return position + 1;
    }
    //endregion

    //region Storage
    static synchronized void setRoot(Path folder) {
        root = folder;
        BOARDS.clear();
        loaded = false;
    }
    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        BOARDS.clear();
        Path file = root.resolve(FILE_NAME);
        if (!Files.isRegularFile(file)) return;
        try { for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) readLine(line); }
        catch (IOException | RuntimeException ignored) { BOARDS.clear(); }
        for (List<Entry> entries : BOARDS.values()) {
            entries.sort((a, b) -> Double.compare(a.seconds(), b.seconds()));
            while (entries.size() > TOP) entries.removeLast();
        }
    }
    private static void readLine(String line) {
        int last = line.lastIndexOf('\t');
        if (last < 0) return;
        int first = line.lastIndexOf('\t', last - 1);
        if (first <= 0) return;
        try {
            double seconds = Double.parseDouble(line.substring(first + 1, last));
            long millis = Long.parseLong(line.substring(last + 1).trim());
            if (!Double.isFinite(seconds) || seconds <= 0) return;
            BOARDS.computeIfAbsent(line.substring(0, first), unused -> new ArrayList<>()).add(new Entry(seconds, millis));
        } catch (NumberFormatException ignored) { }
    }
    private static void save() {
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, List<Entry>> board : BOARDS.entrySet()) {
            for (Entry entry : board.getValue())
                text.append(board.getKey()).append('\t').append(entry.seconds()).append('\t').append(entry.epochMillis()).append('\n');
        }
        try {
            Files.createDirectories(root);
            Files.writeString(root.resolve(FILE_NAME), text, StandardCharsets.UTF_8);
        } catch (IOException ignored) { }
    }
    //endregion
}