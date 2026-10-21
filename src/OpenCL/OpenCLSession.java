package OpenCL;

import java.lang.foreign.*;
import java.util.concurrent.*;

import static java.lang.foreign.ValueLayout.*;

public final class OpenCLSession implements AutoCloseable {
    static final String SELFTEST_SOURCE = """ 
            __kernel void rcjms_selftest(__global int* out) { size_t i = get_global_id(0); out[i] = (int)(i * 3 + 7); }""";
    static final int SELFTEST_COUNT = 64;

    public static final class Unavailable extends RuntimeException { public Unavailable(String message) { super(message); }}

    static final int DEVICE_EXTENSIONS = 0x1030;

    private final CL.Api api;
    private final MemorySegment device, context, queue;
    public final String platformName, deviceName;
    final boolean needsFp64Pragma;
    private boolean closed;

    private OpenCLSession(CL.Api api, MemorySegment device, MemorySegment context, MemorySegment queue, String platformName, String deviceName, boolean needsFp64Pragma) {
        this.api = api;
        this.device = device;
        this.context = context;
        this.queue = queue;
        this.platformName = platformName;
        this.deviceName = deviceName;
        this.needsFp64Pragma = needsFp64Pragma;
    }
    public record Outcome(OpenCLSession session, String reason) { public boolean available() { return session != null; } }

    public static Outcome tryOpen(long timeoutMillis) {
        ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "OpenCL-init");
            t.setDaemon(true);
            return t;
        });
        try {
            Future<OpenCLSession> future = exec.submit(OpenCLSession::open);
            return new Outcome(future.get(timeoutMillis, TimeUnit.MILLISECONDS), null);
        }
        catch (TimeoutException e) { return new Outcome(null, "OpenCL initialisation timed out after " + timeoutMillis + " ms"); }
        catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            return new Outcome(null, reasonOf(cause));
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Outcome(null, "OpenCL initialisation interrupted");
        }
        catch (Throwable t) { return new Outcome(null, reasonOf(t)); }
        finally { exec.shutdownNow(); }
    }
    private static String reasonOf(Throwable t) {
        if (t instanceof UnsatisfiedLinkError) return "OpenCL driver not installed: " + t.getMessage();
        if (t instanceof Unavailable || t instanceof CL.CLException) return t.getMessage();
        if (t instanceof IllegalCallerException) return "native access not enabled: " + t.getMessage();
        return "OpenCL unavailable: " + t;
    }
    static OpenCLSession open() {
        CL.Api api = new CL.Api(CL.loadLibrary());
        MemorySegment device = null, context = null, queue = null;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment err = arena.allocate(JAVA_INT);

            MemorySegment platformCount = arena.allocate(JAVA_INT);
            int rc = api.getPlatformIDs(0, MemorySegment.NULL, platformCount);
            if (rc == CL.PLATFORM_NOT_FOUND_KHR) throw new Unavailable("no OpenCL platform installed");
            CL.check(rc, "clGetPlatformIDs");
            int platformTotal = platformCount.get(JAVA_INT, 0);
            if (platformTotal <= 0) throw new Unavailable("no OpenCL platform installed");

            MemorySegment platforms = arena.allocate(ADDRESS.byteSize() * platformTotal);
            CL.check(api.getPlatformIDs(platformTotal, platforms, MemorySegment.NULL), "clGetPlatformIDs");

            String platformName = null;
            for (int p = 0; p < platformTotal && device == null; p++) {
                MemorySegment platform = platforms.getAtIndex(ADDRESS, p);
                MemorySegment count = arena.allocate(JAVA_INT);
                if (api.getDeviceIDs(platform, CL.DEVICE_TYPE_GPU, 0, MemorySegment.NULL, count) != CL.SUCCESS) continue;
                int deviceTotal = count.get(JAVA_INT, 0);
                if (deviceTotal <= 0) continue;
                MemorySegment devices = arena.allocate(ADDRESS.byteSize() * deviceTotal);
                if (api.getDeviceIDs(platform, CL.DEVICE_TYPE_GPU, deviceTotal, devices, MemorySegment.NULL) != CL.SUCCESS) continue;
                for (int d = 0; d < deviceTotal; d++) {
                    MemorySegment candidate = devices.getAtIndex(ADDRESS, d);
                    if (CL.deviceInt(api, candidate, CL.DEVICE_AVAILABLE) == CL.TRUE && CL.deviceInt(api, candidate, CL.DEVICE_COMPILER_AVAILABLE) == CL.TRUE) {
                        device = candidate;
                        platformName = CL.platformString(api, platform, CL.PLATFORM_NAME);
                        break;
                    }
                }
            }
            if (device == null) throw new Unavailable("no available OpenCL GPU device with a compiler");

            String deviceName = CL.deviceString(api, device, CL.DEVICE_NAME);
            String extensions = CL.deviceString(api, device, DEVICE_EXTENSIONS);
            boolean fp64Extension = extensions.contains("cl_khr_fp64");
            MemorySegment deviceArray = arena.allocate(ADDRESS.byteSize());
            deviceArray.setAtIndex(ADDRESS, 0, device);

            context = api.createContext(MemorySegment.NULL, 1, deviceArray, err);
            CL.check(err.get(JAVA_INT, 0), "clCreateContext");

            queue = api.createQueue(context, device, 0, err);
            CL.check(err.get(JAVA_INT, 0), "clCreateCommandQueue");

            selfTest(api, arena, context, device, queue, deviceArray);

            return new OpenCLSession(api, device, context, queue, platformName, deviceName, fp64Extension);
        }
        catch (RuntimeException | Error e) {
            releaseQuietly(api, queue, context);
            throw e;
        }
    }

    private static void selfTest(CL.Api api, Arena arena, MemorySegment context, MemorySegment device, MemorySegment queue, MemorySegment deviceArray) {
        MemorySegment err = arena.allocate(JAVA_INT);
        MemorySegment sourceArray = arena.allocate(ADDRESS.byteSize());
        sourceArray.setAtIndex(ADDRESS, 0, CL.cString(arena, SELFTEST_SOURCE));

        MemorySegment program = api.createProgram(context, 1, sourceArray, MemorySegment.NULL, err);
        CL.check(err.get(JAVA_INT, 0), "clCreateProgramWithSource");
        MemorySegment kernel = MemorySegment.NULL, buffer = MemorySegment.NULL;
        try {
            int build = api.buildProgram(program, 1, deviceArray, CL.cString(arena, ""));
            if (build != CL.SUCCESS) throw new Unavailable("OpenCL kernel build failed: " + CL.buildLog(api, program, device));
            kernel = api.createKernel(program, CL.cString(arena, "rcjms_selftest"), err);
            CL.check(err.get(JAVA_INT, 0), "clCreateKernel");

            long bytes = 4L * SELFTEST_COUNT;
            buffer = api.createBuffer(context, CL.MEM_WRITE_ONLY, bytes, MemorySegment.NULL, err);
            CL.check(err.get(JAVA_INT, 0), "clCreateBuffer");

            MemorySegment memArg = arena.allocate(ADDRESS.byteSize());
            memArg.set(ADDRESS, 0, buffer);
            CL.check(api.setKernelArg(kernel, 0, ADDRESS.byteSize(), memArg), "clSetKernelArg");

            MemorySegment globalSize = arena.allocate(JAVA_LONG);
            globalSize.set(JAVA_LONG, 0, SELFTEST_COUNT);
            CL.check(api.enqueueNDRange(queue, kernel, 1, globalSize, MemorySegment.NULL), "clEnqueueNDRangeKernel");

            MemorySegment out = arena.allocate(bytes);
            CL.check(api.enqueueRead(queue, buffer, true, 0, bytes, out), "clEnqueueReadBuffer");
            CL.check(api.finish(queue), "clFinish");

            for (int i = 0; i < SELFTEST_COUNT; i++) {
                int expected = i * 3 + 7;
                int actual = out.getAtIndex(JAVA_INT, i);
                if (actual != expected) throw new Unavailable("OpenCL self-test produced wrong output at " + i + ": " + actual + " != " + expected);
            }
        }
        finally {
            if (buffer.address() != 0) api.releaseMem(buffer);
            if (kernel.address() != 0) api.releaseKernel(kernel);
            api.releaseProgram(program);
        }
    }
    private static void releaseQuietly(CL.Api api, MemorySegment queue, MemorySegment context) {
        if (queue != null && queue.address() != 0) api.releaseQueue(queue);
        if (context != null && context.address() != 0) api.releaseContext(context);
    }
    public String describe() { return "OpenCL GPU: " + deviceName + " (" + platformName + ")"; }

    CL.Api api() { return api; }
    MemorySegment device() { return device; }
    MemorySegment context() { return context; }
    MemorySegment queue() { return queue; }
    boolean isClosed() { return closed; }
    @Override public void close() {
        synchronized (GpuRuntime.LOCK) {
            if (closed) return;
            closed = true;
            releaseQuietly(api, queue, context);
        }
    }
}