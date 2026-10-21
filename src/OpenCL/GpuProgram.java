package OpenCL;

import java.lang.foreign.*;

import static java.lang.foreign.ValueLayout.*;

public final class GpuProgram implements AutoCloseable {
    public static final class Buffer {
        private MemorySegment mem;
        final long bytes;
        private Buffer(MemorySegment mem, long bytes) { this.mem = mem; this.bytes = bytes; }
        public long bytes() { return bytes; }
        boolean alive() { return mem != null && mem.address() != 0; }
    }

    private final OpenCLSession session;
    private final CL.Api api;
    private final MemorySegment program, kernel;
    private boolean closed;

    public GpuProgram(String source, String kernelName) {
        synchronized (GpuRuntime.LOCK) {
            OpenCLSession s = GpuRuntime.session();
            if (s == null) throw new OpenCLSession.Unavailable("no OpenCL session");
            this.session = s;
            this.api = s.api();
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment err = arena.allocate(JAVA_INT);
                MemorySegment sources = arena.allocate(ADDRESS.byteSize());
                sources.setAtIndex(ADDRESS, 0, CL.cString(arena, (s.needsFp64Pragma ? "#pragma OPENCL EXTENSION cl_khr_fp64 : enable\n" : "") + source));
                program = api.createProgram(s.context(), 1, sources, MemorySegment.NULL, err);
                CL.check(err.get(JAVA_INT, 0), "clCreateProgramWithSource");

                MemorySegment devices = arena.allocate(ADDRESS.byteSize());
                devices.setAtIndex(ADDRESS, 0, s.device());
                if (api.buildProgram(program, 1, devices, CL.cString(arena, "")) != CL.SUCCESS) {
                    String log = CL.buildLog(api, program, s.device());
                    api.releaseProgram(program);
                    throw new OpenCLSession.Unavailable("kernel " + kernelName + " build failed: " + log);
                }
                kernel = api.createKernel(program, CL.cString(arena, kernelName), err);
                CL.check(err.get(JAVA_INT, 0), "clCreateKernel");
            }
        }
    }
    public Buffer buffer(long bytes) {
        long size = Math.max(4L, bytes);
        synchronized (GpuRuntime.LOCK) {
            requireOpen();
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment err = arena.allocate(JAVA_INT);
                MemorySegment mem = api.createBuffer(session.context(), CL.MEM_READ_WRITE, size, MemorySegment.NULL, err);
                CL.check(err.get(JAVA_INT, 0), "clCreateBuffer");
                return new Buffer(mem, size);
            }
        }
    }
    public void write(Buffer buffer, int[] values, int count) {
        if (count == 0) return;
        checkCount(buffer, count, 4L, values.length);
        synchronized (GpuRuntime.LOCK) {
            requireLive(buffer);
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment host = arena.allocate(4L * count);
                MemorySegment.copy(values, 0, host, JAVA_INT, 0, count);
                CL.check(api.enqueueWrite(session.queue(), buffer.mem, true, 0, 4L * count, host), "clEnqueueWriteBuffer");
            }
        }
    }
    public void write(Buffer buffer, double[] values, int count) {
        if (count == 0) return;
        checkCount(buffer, count, 4L, values.length);
        synchronized (GpuRuntime.LOCK) {
            requireLive(buffer);
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment host = arena.allocate(4L * count);
                for (int i = 0; i < count; i++) host.setAtIndex(JAVA_FLOAT, i, (float) values[i]);
                CL.check(api.enqueueWrite(session.queue(), buffer.mem, true, 0, 4L * count, host), "clEnqueueWriteBuffer");
            }
        }
    }
    public void read(Buffer buffer, int[] dst, int count) {
        if (count == 0) return;
        checkCount(buffer, count, 4L, dst.length);
        synchronized (GpuRuntime.LOCK) {
            requireLive(buffer);
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment host = arena.allocate(4L * count);
                CL.check(api.enqueueRead(session.queue(), buffer.mem, true, 0, 4L * count, host), "clEnqueueReadBuffer");
                MemorySegment.copy(host, JAVA_INT, 0, dst, 0, count);
            }
        }
    }
    public void run(int width, int height, Buffer... args) {
        synchronized (GpuRuntime.LOCK) {
            requireOpen();
            for (Buffer b : args) requireLive(b);
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment slot = arena.allocate(ADDRESS.byteSize());
                for (int i = 0; i < args.length; i++) {
                    slot.set(ADDRESS, 0, args[i].mem);
                    CL.check(api.setKernelArg(kernel, i, ADDRESS.byteSize(), slot), "clSetKernelArg");
                }
                MemorySegment global = arena.allocate(16);
                global.set(JAVA_LONG, 0, width);
                global.set(JAVA_LONG, 8, height);
                CL.check(api.enqueueNDRange(session.queue(), kernel, 2, global, MemorySegment.NULL), "clEnqueueNDRangeKernel");
                CL.check(api.finish(session.queue()), "clFinish");
            }
        }
    }
    public void release(Buffer buffer) {
        if (buffer == null) return;
        synchronized (GpuRuntime.LOCK) {
            if (buffer.alive() && !session.isClosed()) api.releaseMem(buffer.mem);
            buffer.mem = null;
        }
    }
    @Override public void close() {
        synchronized (GpuRuntime.LOCK) {
            if (closed) return;
            closed = true;
            if (session.isClosed()) return;
            if (kernel.address() != 0) api.releaseKernel(kernel);
            if (program.address() != 0) api.releaseProgram(program);
        }
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("GPU program is closed");
        if (session.isClosed()) throw new IllegalStateException("OpenCL session is closed");
    }
    private void requireLive(Buffer b) {
        requireOpen();
        if (b == null || !b.alive()) throw new IllegalStateException("device buffer is released");
    }
    private static void checkCount(Buffer buffer, int count, long elementBytes, int arrayLength) {
        if (count < 0 || count > arrayLength) throw new IllegalArgumentException("count " + count + " exceeds array length " + arrayLength);
        if (elementBytes * count > buffer.bytes)
            throw new IllegalArgumentException("buffer of " + buffer.bytes + " bytes cannot hold " + count + " elements of " + elementBytes + " bytes");
    }
}