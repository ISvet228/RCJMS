package Project.OpenCl;
import Project.EngineCreator.*;
import Project.ThemeCreator.*;
import Project.CreatorKit.*;

import java.io.*;
import java.nio.charset.StandardCharsets;

public final class KernelSource {
    //region Constructors
    private KernelSource() {}
    //endregion

    //region Loading
    public static String load(Class<?> owner) { return load(owner, ""); }
    public static String load(Class<?> owner, String inlineSource) {
        String fileName = owner.getSimpleName() + ".cl";
        String inline = inlineSource == null ? "" : inlineSource;
        try (InputStream in = owner.getResourceAsStream(fileName)) {
            if (in == null) {
                if (inline.isBlank()) throw new IllegalStateException("missing " + fileName + " (no OpenCL source for " + owner.getSimpleName() + ")");
                return inline;
            }
            String file = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return inline.isBlank() ? file : inline + "\n" + file;
        } catch (IOException exception) { throw new IllegalStateException("cannot read " + fileName + ": " + exception.getMessage(), exception); }
    }
    //endregion
}