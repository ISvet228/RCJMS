package Project.OpenCl;
import Project.EngineCreator.*;
import Project.ThemeCreator.*;
import Project.CreatorKit.*;

import java.util.Map;
import java.util.function.Consumer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class GpuRuntime {
    //region Variables
    //region Probe State
    private static final long PROBE_TIMEOUT_MS = 20_000;
    private static final AtomicBoolean started = new AtomicBoolean();
    private static volatile boolean probing;
    //endregion
    //region Session State
    private static volatile OpenCLSession session;
    private static volatile String status = "OpenCL: not started";
    public static final Object LOCK = new Object();
    //endregion
    //region Failures
    private static final Map<String, String> failures = new ConcurrentHashMap<>();
    //endregion
    //region Settings
    private static volatile boolean enabled = true;
    private static volatile Consumer<String> sink = System.err::println;
    //endregion
    //endregion

    //region Constructors
    private GpuRuntime() {}
    //endregion

    //region Session
    public static OpenCLSession session() {
        if (!enabled) return null;
        ensureStarted();
        return session;
    }
    private static void ensureStarted() {
        if (started.compareAndSet(false, true)) startProbe();
    }
    private static void startProbe() {
        if ("off".equalsIgnoreCase(System.getProperty("rcjms.opencl", "auto").trim())) {
            status = "OpenCL disabled by -Drcjms.opencl=off";
            emit("[OpenCL] " + status);
            return;
        }
        status = "OpenCL: probing";
        probing = true;
        Thread probe = new Thread(() -> {
            try {
                OpenCLSession.Outcome outcome = OpenCLSession.tryOpen(PROBE_TIMEOUT_MS);
                if (outcome.available()) {
                    synchronized (LOCK) {
                        session = outcome.session();
                        status = outcome.session().describe();
                        Runtime.getRuntime().addShutdownHook(new Thread(GpuRuntime::shutdown, "OpenCL-shutdown"));
                    }
                    emit("[OpenCL] " + status);
                }
                else {
                    status = "OpenCL unavailable: " + outcome.reason();
                    emit("[OpenCL] " + status + " -> using CPU rendering");
                }
            } finally {
                probing = false;
            }
        }, "OpenCL-probe");
        probe.setDaemon(true);
        probe.start();
    }
    private static void shutdown() {
        synchronized (LOCK) {
            OpenCLSession openCLSession = session;
            session = null;
            status = "OpenCL: shut down";
            if (openCLSession != null) openCLSession.close();
        }
    }
    //endregion

    //region Health
    public enum Health { OFF, PROBING, READY, UNAVAILABLE }
    public static Health healthFor(String engineId) {
        if (!enabled) return Health.OFF;
        ensureStarted();
        if (probing) return Health.PROBING;
        if (session == null || failures.containsKey(engineId)) return Health.UNAVAILABLE;
        return Health.READY;
    }
    public static String reasonFor(String engineId) {
        String failure = failures.get(engineId);
        if (failure != null) return failure;
        return session == null ? status : "";
    }
    public static void reportFailure(String engineId, String reason) {
        failures.put(engineId, reason);
        emit("[OpenCL] " + engineId + " GPU rendering disabled, using CPU: " + reason);
    }
    //endregion

    //region Switches
    public static void setEnabled(boolean value) { enabled = value; }
    public static boolean isEnabled() { return enabled; }
    //endregion

    //region Logging
    public static void setLogSink(Consumer<String> value) { sink = value == null ? System.err::println : value; }
    private static void emit(String line) { sink.accept(line); }
    //endregion
}