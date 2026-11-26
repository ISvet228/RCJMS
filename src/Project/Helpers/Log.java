package Project.Helpers;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public final class Log {
    private static final Map<String, Logger> LOGGERS = new ConcurrentHashMap<>();
    private static final StackWalker WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    private Log() { }

    public static void error(Throwable problem) { log(Level.ERROR, "unexpected error", problem); }
    public static void error(String message, Throwable problem) { log(Level.ERROR, message, problem); }
    public static void warn(String message, Throwable problem) { log(Level.WARNING, message, problem); }
    public static void warn(String message) { log(Level.WARNING, message, null); }
    public static void debug(Throwable ignored) { log(Level.DEBUG, "ignored", ignored); }
    public static void debug(String message, Throwable ignored) { log(Level.DEBUG, message, ignored); }

    private static void log(Level level, String message, Throwable problem) {
        String caller = WALKER.walk(frames -> frames.map(StackWalker.StackFrame::getDeclaringClass)
                .filter(type -> type != Log.class).findFirst().map(Class::getName).orElse("RCJMS"));
        Logger logger = LOGGERS.computeIfAbsent(caller, name -> name != null ? System.getLogger(name) : null);
        assert logger != null;
        if (!logger.isLoggable(level)) return;
        if (problem == null) logger.log(level, message);
        else logger.log(level, message, problem);
    }
}
