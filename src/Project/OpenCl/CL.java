package Project.OpenCl;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;

import static java.lang.foreign.ValueLayout.*;

public final class CL {
    private CL() {}

    static final int SUCCESS = 0;
    static final int PLATFORM_NOT_FOUND_KHR = -1001;

    static final long DEVICE_TYPE_GPU = 1L << 2;
    static final long MEM_READ_WRITE = 1L << 0;
    static final long MEM_WRITE_ONLY = 1L << 1;
    static final int FALSE = 0, TRUE = 1;

    static final int PLATFORM_NAME = 0x0902;
    static final int DEVICE_NAME = 0x102B;
    static final int DEVICE_AVAILABLE = 0x1027;
    static final int DEVICE_COMPILER_AVAILABLE = 0x1028;
    static final int PROGRAM_BUILD_LOG = 0x1183;

    static final class CLException extends RuntimeException {
        final int code;
        CLException(String what, int code) { super(what + " failed with OpenCL error " + code); this.code = code; }
    }
    static void check(int code, String what) { if (code != SUCCESS) throw new CLException(what, code); }
    static SymbolLookup loadLibrary() {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        String[] candidates;
        if (os.contains("win")) candidates = new String[]{"OpenCL.dll"};
        else if (os.contains("mac")) candidates = new String[]{"/System/Library/Frameworks/OpenCL.framework/OpenCL"};
        else candidates = new String[]{"libOpenCL.so.1", "libOpenCL.so"};
        IllegalArgumentException last = null;
        for (String name : candidates) {
            try { return SymbolLookup.libraryLookup(name, Arena.global()); }
            catch (IllegalArgumentException e) { last = e; }
        }
        throw new UnsatisfiedLinkError("OpenCL library not found (" + String.join(", ", candidates) + "): " + (last == null ? "" : last.getMessage()));
    }
    static final class Api {
        private static final Linker LINKER = Linker.nativeLinker();
        private final MethodHandle getPlatformIDs, getDeviceIDs, getPlatformInfo, getDeviceInfo;
        private final MethodHandle createContext, releaseContext, createQueue, releaseQueue, finish;
        private final MethodHandle createProgram, buildProgram, getBuildInfo, releaseProgram;
        private final MethodHandle createKernel, releaseKernel, setKernelArg, enqueueNDRange;
        private final MethodHandle createBuffer, releaseMem, enqueueWrite, enqueueRead;

        Api(SymbolLookup lib) {
            getPlatformIDs = bind(lib, "clGetPlatformIDs", FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS, ADDRESS));
            getDeviceIDs = bind(lib, "clGetDeviceIDs", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG, JAVA_INT, ADDRESS, ADDRESS));
            getPlatformInfo = bind(lib, "clGetPlatformInfo", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_LONG, ADDRESS, ADDRESS));
            getDeviceInfo = bind(lib, "clGetDeviceInfo", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_LONG, ADDRESS, ADDRESS));
            createContext = bind(lib, "clCreateContext", FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
            releaseContext = bind(lib, "clReleaseContext", FunctionDescriptor.of(JAVA_INT, ADDRESS));
            createQueue = bind(lib, "clCreateCommandQueue", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, JAVA_LONG, ADDRESS));
            releaseQueue = bind(lib, "clReleaseCommandQueue", FunctionDescriptor.of(JAVA_INT, ADDRESS));
            finish = bind(lib, "clFinish", FunctionDescriptor.of(JAVA_INT, ADDRESS));
            createProgram = bind(lib, "clCreateProgramWithSource", FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
            buildProgram = bind(lib, "clBuildProgram", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
            getBuildInfo = bind(lib, "clGetProgramBuildInfo", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, JAVA_LONG, ADDRESS, ADDRESS));
            releaseProgram = bind(lib, "clReleaseProgram", FunctionDescriptor.of(JAVA_INT, ADDRESS));
            createKernel = bind(lib, "clCreateKernel", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS));
            releaseKernel = bind(lib, "clReleaseKernel", FunctionDescriptor.of(JAVA_INT, ADDRESS));
            setKernelArg = bind(lib, "clSetKernelArg", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_LONG, ADDRESS));
            enqueueNDRange = bind(lib, "clEnqueueNDRangeKernel", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, ADDRESS));
            createBuffer = bind(lib, "clCreateBuffer", FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_LONG, JAVA_LONG, ADDRESS, ADDRESS));
            releaseMem = bind(lib, "clReleaseMemObject", FunctionDescriptor.of(JAVA_INT, ADDRESS));
            enqueueWrite = bind(lib, "clEnqueueWriteBuffer", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, JAVA_LONG, JAVA_LONG, ADDRESS, JAVA_INT, ADDRESS, ADDRESS));
            enqueueRead = bind(lib, "clEnqueueReadBuffer", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, JAVA_LONG, JAVA_LONG, ADDRESS, JAVA_INT, ADDRESS, ADDRESS));
        }

        private static MethodHandle bind(SymbolLookup lib, String name, FunctionDescriptor fd) {
            MemorySegment symbol = lib.find(name).orElseThrow(() -> new UnsatisfiedLinkError("Missing OpenCL symbol " + name));
            return LINKER.downcallHandle(symbol, fd);
        }
        int getPlatformIDs(int n, MemorySegment platforms, MemorySegment count) {
            try { return (int) getPlatformIDs.invokeExact(n, platforms, count); } catch (Throwable t) { throw rethrow(t); }
        }
        int getDeviceIDs(MemorySegment platform, long type, int n, MemorySegment devices, MemorySegment count) {
            try { return (int) getDeviceIDs.invokeExact(platform, type, n, devices, count); } catch (Throwable t) { throw rethrow(t); }
        }
        int getPlatformInfo(MemorySegment platform, int param, long size, MemorySegment value, MemorySegment sizeRet) {
            try { return (int) getPlatformInfo.invokeExact(platform, param, size, value, sizeRet); } catch (Throwable t) { throw rethrow(t); }
        }
        int getDeviceInfo(MemorySegment device, int param, long size, MemorySegment value, MemorySegment sizeRet) {
            try { return (int) getDeviceInfo.invokeExact(device, param, size, value, sizeRet); } catch (Throwable t) { throw rethrow(t); }
        }
        MemorySegment createContext(MemorySegment props, int numDevices, MemorySegment devices, MemorySegment errcode) {
            try { return (MemorySegment) createContext.invokeExact(props, numDevices, devices, MemorySegment.NULL, MemorySegment.NULL, errcode); } catch (Throwable t) { throw rethrow(t); }
        }
        int releaseContext(MemorySegment ctx) {
            try { return (int) releaseContext.invokeExact(ctx); } catch (Throwable t) { throw rethrow(t); }
        }
        MemorySegment createQueue(MemorySegment ctx, MemorySegment device, long props, MemorySegment errcode) {
            try { return (MemorySegment) createQueue.invokeExact(ctx, device, props, errcode); } catch (Throwable t) { throw rethrow(t); }
        }
        int releaseQueue(MemorySegment queue) {
            try { return (int) releaseQueue.invokeExact(queue); } catch (Throwable t) { throw rethrow(t); }
        }
        int finish(MemorySegment queue) {
            try { return (int) finish.invokeExact(queue); } catch (Throwable t) { throw rethrow(t); }
        }
        MemorySegment createProgram(MemorySegment ctx, int count, MemorySegment strings, MemorySegment lengths, MemorySegment errcode) {
            try { return (MemorySegment) createProgram.invokeExact(ctx, count, strings, lengths, errcode); } catch (Throwable t) { throw rethrow(t); }
        }
        int buildProgram(MemorySegment program, int numDevices, MemorySegment devices, MemorySegment options) {
            try { return (int) buildProgram.invokeExact(program, numDevices, devices, options, MemorySegment.NULL, MemorySegment.NULL); } catch (Throwable t) { throw rethrow(t); }
        }
        int getBuildInfo(MemorySegment program, MemorySegment device, int param, long size, MemorySegment value, MemorySegment sizeRet) {
            try { return (int) getBuildInfo.invokeExact(program, device, param, size, value, sizeRet); } catch (Throwable t) { throw rethrow(t); }
        }
        int releaseProgram(MemorySegment program) {
            try { return (int) releaseProgram.invokeExact(program); } catch (Throwable t) { throw rethrow(t); }
        }
        MemorySegment createKernel(MemorySegment program, MemorySegment name, MemorySegment errcode) {
            try { return (MemorySegment) createKernel.invokeExact(program, name, errcode); } catch (Throwable t) { throw rethrow(t); }
        }
        int releaseKernel(MemorySegment kernel) {
            try { return (int) releaseKernel.invokeExact(kernel); } catch (Throwable t) { throw rethrow(t); }
        }
        int setKernelArg(MemorySegment kernel, int index, long size, MemorySegment value) {
            try { return (int) setKernelArg.invokeExact(kernel, index, size, value); } catch (Throwable t) { throw rethrow(t); }
        }
        int enqueueNDRange(MemorySegment queue, MemorySegment kernel, int dims, MemorySegment globalSize, MemorySegment localSize) {
            try { return (int) enqueueNDRange.invokeExact(queue, kernel, dims, MemorySegment.NULL, globalSize, localSize, 0, MemorySegment.NULL, MemorySegment.NULL); } catch (Throwable t) { throw rethrow(t); }
        }
        MemorySegment createBuffer(MemorySegment ctx, long flags, long size, MemorySegment hostPtr, MemorySegment errcode) {
            try { return (MemorySegment) createBuffer.invokeExact(ctx, flags, size, hostPtr, errcode); } catch (Throwable t) { throw rethrow(t); }
        }
        int releaseMem(MemorySegment mem) {
            try { return (int) releaseMem.invokeExact(mem); } catch (Throwable t) { throw rethrow(t); }
        }
        int enqueueWrite(MemorySegment queue, MemorySegment buffer, boolean blocking, long offset, long size, MemorySegment host) {
            try { return (int) enqueueWrite.invokeExact(queue, buffer, blocking ? TRUE : FALSE, offset, size, host, 0, MemorySegment.NULL, MemorySegment.NULL); } catch (Throwable t) { throw rethrow(t); }
        }
        int enqueueRead(MemorySegment queue, MemorySegment buffer, boolean blocking, long offset, long size, MemorySegment host) {
            try { return (int) enqueueRead.invokeExact(queue, buffer, blocking ? TRUE : FALSE, offset, size, host, 0, MemorySegment.NULL, MemorySegment.NULL); } catch (Throwable t) { throw rethrow(t); }
        }

        private static RuntimeException rethrow(Throwable t) {
            if (t instanceof RuntimeException re) return re;
            if (t instanceof Error err) throw err;
            return new IllegalStateException(t);
        }
    }

    interface InfoQuery { int run(long size, MemorySegment value, MemorySegment sizeRet); }
    static String platformString(Api api, MemorySegment platform, int param) {
        try (Arena arena = Arena.ofConfined()) {
            return readString(arena, (size, value, sizeRet) -> api.getPlatformInfo(platform, param, size, value, sizeRet));
        }
    }
    static String deviceString(Api api, MemorySegment device, int param) {
        try (Arena arena = Arena.ofConfined()) {
            return readString(arena, (size, value, sizeRet) -> api.getDeviceInfo(device, param, size, value, sizeRet));
        }
    }
    static String buildLog(Api api, MemorySegment program, MemorySegment device) {
        try (Arena arena = Arena.ofConfined()) {
            return readString(arena, (size, value, sizeRet) -> api.getBuildInfo(program, device, PROGRAM_BUILD_LOG, size, value, sizeRet));
        }
    }
    static int deviceInt(Api api, MemorySegment device, int param) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment v = arena.allocate(JAVA_INT);
            check(api.getDeviceInfo(device, param, 4, v, MemorySegment.NULL), "clGetDeviceInfo");
            return v.get(JAVA_INT, 0);
        }
    }
    static long deviceLong(Api api, MemorySegment device, int param) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment v = arena.allocate(JAVA_LONG);
            check(api.getDeviceInfo(device, param, 8, v, MemorySegment.NULL), "clGetDeviceInfo");
            return v.get(JAVA_LONG, 0);
        }
    }
    private static String readString(Arena arena, InfoQuery query) {
        MemorySegment sizeRet = arena.allocate(JAVA_LONG);
        if (query.run(0, MemorySegment.NULL, sizeRet) != SUCCESS) return "";
        long len = sizeRet.get(JAVA_LONG, 0);
        if (len <= 0) return "";
        MemorySegment buf = arena.allocate(len);
        if (query.run(len, buf, MemorySegment.NULL) != SUCCESS) return "";
        int end = (int) len;
        while (end > 0 && buf.get(JAVA_BYTE, end - 1) == 0) end--;
        byte[] bytes = new byte[end];
        for (int i = 0; i < end; i++) bytes[i] = buf.get(JAVA_BYTE, i);
        return new String(bytes, StandardCharsets.UTF_8);
    }
    static MemorySegment cString(Arena arena, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        MemorySegment seg = arena.allocate(bytes.length + 1L);
        for (int i = 0; i < bytes.length; i++) seg.set(JAVA_BYTE, i, bytes[i]);
        seg.set(JAVA_BYTE, bytes.length, (byte) 0);
        return seg;
    }
}