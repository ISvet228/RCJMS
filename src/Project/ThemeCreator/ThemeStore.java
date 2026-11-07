package Project.ThemeCreator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** The themes the user saved: one JSON file per theme in Mods/Themes. Built-in originals are never written here. */
public final class ThemeStore {
    //region Variables
    public static final String FOLDER = "Mods/Themes";
    private static final String EXT = ".json";
    //endregion

    //region Constructors
    private ThemeStore() {}
    //endregion

    //region Storage Paths
    public static Path folder() { return Paths.get(FOLDER); }
    private static Path file(String id) { return folder().resolve(id + EXT); }
    //endregion

    //region Saved Themes
    public static List<String> ids() {
        List<String> out = new ArrayList<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(folder(), "*" + EXT)) {
            for (Path path : files) {
                String name = path.getFileName().toString();
                out.add(name.substring(0, name.length() - EXT.length()));
            }
        } catch (IOException exception) { return out; }
        Collections.sort(out);
        return out;
    }
    public static ThemeDoc load(String id) throws IOException { return ThemeIO.fromJson(Files.readString(file(id), StandardCharsets.UTF_8)); }
    public static void save(ThemeDoc doc) throws IOException {
        Files.createDirectories(folder());
        Path target = file(doc.id);
        Path temp = folder().resolve(doc.id + ".tmp");
        Files.writeString(temp, ThemeIO.toJson(doc), StandardCharsets.UTF_8);
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
    public static String slug(String name) {
        String s = name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return s.isEmpty() ? "theme" : s;
    }
    public static void delete(String id) throws IOException { Files.deleteIfExists(file(id)); }
    //endregion
}