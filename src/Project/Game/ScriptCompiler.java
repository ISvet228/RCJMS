package Project.Game;

import javax.tools.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

public final class ScriptCompiler {
    private ScriptCompiler() {}

    static boolean available() {
        return ToolProvider.getSystemJavaCompiler() != null;
    }

    static List<Path> compile(List<Path> sources, Path build, String classpath, List<String> problems) {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        List<Path> compiled = new ArrayList<>();
        for (Path source : sources) {
            String name = source.getFileName().toString();
            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
            try (StandardJavaFileManager files = javac.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
                String level = String.valueOf(Runtime.version().feature());
                List<String> options = List.of("--enable-preview", "-source", level, "-target", level, "-classpath", classpath, "-encoding", "UTF-8", "-proc:none", "-nowarn", "-d", build.toString());
                boolean ok = javac.getTask(null, files, diagnostics, options, null, files.getJavaFileObjects(source.toFile())).call();
                if (!ok) {
                    problems.add(name + ": does not compile");
                    for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics())
                        if (d.getKind() == Diagnostic.Kind.ERROR)
                            problems.add("    line " + d.getLineNumber() + ": " + d.getMessage(Locale.ROOT));
                    continue;
                }
                compiled.add(source);
            } catch (IOException e) { problems.add(name + ": " + e.getMessage()); }
        }
        return compiled;
    }
}