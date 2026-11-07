package Project.Vulkan;

import java.awt.image.*;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.*;

public final class Vk {
    //region Variables
    //region Vulkan Constants
    static final int FORMAT_BGRA8 = 44, FORMAT_RG32F = 103, FORMAT_RGBA32F = 109;
    static final int SAMPLES = 4;
    static final int VERTEX_STRIDE = 24;
    static final int USAGE_TRANSFER_SRC = 0x1, USAGE_TRANSFER_DST = 0x2, USAGE_COLOR_ATTACHMENT = 0x10, USAGE_VERTEX = 0x80;
    static final int MEM_DEVICE_LOCAL = 0x1, MEM_HOST_VISIBLE = 0x2, MEM_HOST_COHERENT = 0x4;
    static final int LAYOUT_COLOR_ATTACHMENT = 2, LAYOUT_TRANSFER_SRC = 6;
    //endregion
    //region Load Status
    private static Gpu shared;
    private static String reason = "not tried yet";
    private static String info = "";
    private static boolean tried;
    //endregion
    //endregion

    //region Constructors
    private Vk() {}
    //endregion

    //region Load Status Reporting
    public static synchronized String info() { return info; }
    public static synchronized String reason() { return reason; }
    //endregion

    //region Native Bindings
    static void check(int result, String what) {
        if (result != 0) throw new IllegalStateException(what + " failed, VkResult " + result);
    }
    static MemorySegment cString(Arena a, String s) {
        byte[] b = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        MemorySegment out = a.allocate(b.length + 1L, 1);
        for (int i = 0; i < b.length; i++) out.set(JAVA_BYTE, i, b[i]);
        out.set(JAVA_BYTE, b.length, (byte) 0);
        return out;
    }
    static MemorySegment ints(Arena a, int... values) {
        MemorySegment out = a.allocate(4L * values.length, 4);
        for (int i = 0; i < values.length; i++) out.set(JAVA_INT, 4L * i, values[i]);
        return out;
    }
    static MemorySegment rec(Arena a, long size) { return a.allocate(size, 8); }
    static final class Api {
        static final Linker LINKER = Linker.nativeLinker();
        static final SymbolLookup LIB = load();
        static final MethodHandle CREATE_INSTANCE = fn("vkCreateInstance", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
        static final MethodHandle ENUM_DEVICES = fn("vkEnumeratePhysicalDevices", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
        static final MethodHandle PHYS_MEMORY = fn("vkGetPhysicalDeviceMemoryProperties", FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));
        static final MethodHandle PHYS_PROPS = fn("vkGetPhysicalDeviceProperties", FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));
        static final MethodHandle PHYS_QUEUES = fn("vkGetPhysicalDeviceQueueFamilyProperties", FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS));
        static final MethodHandle CREATE_DEVICE = fn("vkCreateDevice", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
        static final MethodHandle GET_QUEUE = fn("vkGetDeviceQueue", FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT, JAVA_INT, ADDRESS));
        static final MethodHandle CREATE_IMAGE = fn("vkCreateImage", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
        static final MethodHandle CREATE_BUFFER = fn("vkCreateBuffer", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
        static final MethodHandle IMAGE_REQS = fn("vkGetImageMemoryRequirements", FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG, ADDRESS));
        static final MethodHandle BUFFER_REQS = fn("vkGetBufferMemoryRequirements", FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG, ADDRESS));
        static final MethodHandle ALLOCATE = fn("vkAllocateMemory", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
        static final MethodHandle BIND_IMAGE = fn("vkBindImageMemory", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG, JAVA_LONG, JAVA_LONG));
        static final MethodHandle BIND_BUFFER = fn("vkBindBufferMemory", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG, JAVA_LONG, JAVA_LONG));
        static final MethodHandle CREATE_VIEW = fn("vkCreateImageView", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
        static final MethodHandle MAP = fn("vkMapMemory", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_INT, ADDRESS));
        static final MethodHandle UNMAP = fn("vkUnmapMemory", FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG));
        static final MethodHandle FREE_MEMORY = fn("vkFreeMemory", FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG, ADDRESS));
        static final MethodHandle CREATE_RENDER_PASS = fn("vkCreateRenderPass", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
        static final MethodHandle CREATE_FRAMEBUFFER = fn("vkCreateFramebuffer", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
        static final MethodHandle CREATE_SHADER = fn("vkCreateShaderModule", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
        static final MethodHandle CREATE_LAYOUT = fn("vkCreatePipelineLayout", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
        static final MethodHandle CREATE_PIPELINES = fn("vkCreateGraphicsPipelines", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG, JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
        static final MethodHandle CREATE_POOL = fn("vkCreateCommandPool", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
        static final MethodHandle ALLOC_CMD = fn("vkAllocateCommandBuffers", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
        static final MethodHandle BEGIN_CMD = fn("vkBeginCommandBuffer", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
        static final MethodHandle END_CMD = fn("vkEndCommandBuffer", FunctionDescriptor.of(JAVA_INT, ADDRESS));
        static final MethodHandle RESET_CMD = fn("vkResetCommandBuffer", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));
        static final MethodHandle CREATE_FENCE = fn("vkCreateFence", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
        static final MethodHandle SUBMIT = fn("vkQueueSubmit", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_LONG));
        static final MethodHandle WAIT_FENCES = fn("vkWaitForFences", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, JAVA_LONG));
        static final MethodHandle RESET_FENCES = fn("vkResetFences", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS));
        static final MethodHandle DEVICE_IDLE = fn("vkDeviceWaitIdle", FunctionDescriptor.of(JAVA_INT, ADDRESS));
        static final MethodHandle CMD_BEGIN_PASS = fn("vkCmdBeginRenderPass", FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, JAVA_INT));
        static final MethodHandle CMD_END_PASS = fn("vkCmdEndRenderPass", FunctionDescriptor.ofVoid(ADDRESS));
        static final MethodHandle CMD_BIND_PIPELINE = fn("vkCmdBindPipeline", FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT, JAVA_LONG));
        static final MethodHandle CMD_BIND_VERTEX = fn("vkCmdBindVertexBuffers", FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS));
        static final MethodHandle CMD_DRAW = fn("vkCmdDraw", FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT));
        static final MethodHandle CMD_COPY = fn("vkCmdCopyImageToBuffer", FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG, JAVA_INT, JAVA_LONG, JAVA_INT, ADDRESS));
        static final MethodHandle CMD_SET_VIEWPORT = fn("vkCmdSetViewport", FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT, JAVA_INT, ADDRESS));
        static final MethodHandle CMD_SET_SCISSOR = fn("vkCmdSetScissor", FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT, JAVA_INT, ADDRESS));
        static final MethodHandle DESTROY_IMAGE = fn("vkDestroyImage", FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG, ADDRESS));
        static final MethodHandle DESTROY_VIEW = fn("vkDestroyImageView", FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG, ADDRESS));
        static final MethodHandle DESTROY_FRAMEBUFFER = fn("vkDestroyFramebuffer", FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG, ADDRESS));
        static final MethodHandle DESTROY_BUFFER = fn("vkDestroyBuffer", FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG, ADDRESS));

        private Api() {}

        static MethodHandle fn(String name, FunctionDescriptor d) {
            return LINKER.downcallHandle(LIB.find(name).orElseThrow(() -> new IllegalStateException("missing " + name)), d);
        }
        private static SymbolLookup load() {
            for (String name : new String[]{"libvulkan.so.1", "libvulkan.so", "vulkan-1.dll", "libvulkan.1.dylib"}) {
                try {
                    return SymbolLookup.libraryLookup(name, Arena.global());
                } catch (IllegalArgumentException notHere) { }
            }
            throw new IllegalStateException("no Vulkan loader (libvulkan.so.1 or vulkan-1.dll) on this system");
        }
    }
    //endregion

    //region GPU Access
    public static synchronized Gpu gpu() {
        if (!tried) {
            tried = true;
            try {
                shared = new Gpu();
                reason = "ready";
                System.out.println("[Vulkan] renderer ready: " + info);
            } catch (Throwable exception) {
                shared = null;
                reason = exception.toString();
                System.out.println("[Vulkan] not available, using Java2D: " + reason);
            }
        }
        return shared;
    }
    public static final class Gpu {
        private static final Arena SHARED = Arena.ofShared();
        private final long device, queue, commandBuffer, pool, fence, layout, renderPass, pipeline, vertexModule, fragmentModule;
        private final int queueFamily;
        private final MemorySegment memoryProperties;
        private Buffer vertices;
        private Target target;

        private Gpu() throws Throwable {
            try (Arena a = Arena.ofConfined()) {
                MemorySegment app = rec(a, 48);
                app.set(JAVA_INT, 0, 0);
                app.set(ADDRESS, 16, cString(a, "rcjms-theme"));
                app.set(JAVA_INT, 24, 1);
                app.set(JAVA_INT, 44, 1 << 22);
                MemorySegment ici = rec(a, 64);
                ici.set(JAVA_INT, 0, 1);
                ici.set(ADDRESS, 24, app);
                MemorySegment instOut = rec(a, 8);
                check((int) Api.CREATE_INSTANCE.invoke(ici, MemorySegment.NULL, instOut), "vkCreateInstance");
                MemorySegment instance = instOut.get(ADDRESS, 0);

                MemorySegment count = rec(a, 4);
                check((int) Api.ENUM_DEVICES.invoke(instance, count, MemorySegment.NULL), "vkEnumeratePhysicalDevices");
                int n = count.get(JAVA_INT, 0);
                if (n == 0) throw new IllegalStateException("no Vulkan device");
                MemorySegment devs = rec(a, 8L * n);
                check((int) Api.ENUM_DEVICES.invoke(instance, count, devs), "vkEnumeratePhysicalDevices");
                MemorySegment phys = devs.get(ADDRESS, 0);

                memoryProperties = SHARED.allocate(520, 8);
                Api.PHYS_MEMORY.invoke(phys, memoryProperties);
                MemorySegment props = SHARED.allocate(824, 8);
                Api.PHYS_PROPS.invoke(phys, props);
                int api = props.get(JAVA_INT, 0);
                StringBuilder name = new StringBuilder();
                for (int i = 0; i < 256 && props.get(JAVA_BYTE, 20 + i) != 0; i++) name.append((char) (props.get(JAVA_BYTE, 20 + i) & 0xFF));
                info = name + " (Vulkan " + (api >>> 22) + "." + ((api >>> 12) & 0x3FF) + "." + (api & 0xFFF) + ")";

                MemorySegment qc = rec(a, 4);
                Api.PHYS_QUEUES.invoke(phys, qc, MemorySegment.NULL);
                int qn = qc.get(JAVA_INT, 0);
                MemorySegment qp = rec(a, 24L * qn);
                Api.PHYS_QUEUES.invoke(phys, qc, qp);
                int qf = -1;
                for (int i = 0; i < qn && qf < 0; i++) {
                    if ((qp.get(JAVA_INT, 24L * i) & 0x1) != 0) qf = i;
                }
                if (qf < 0) throw new IllegalStateException("no graphics queue");
                queueFamily = qf;

                MemorySegment prio = rec(a, 4);
                prio.set(JAVA_FLOAT, 0, 1f);
                MemorySegment dqci = rec(a, 40);
                dqci.set(JAVA_INT, 0, 2);
                dqci.set(JAVA_INT, 20, qf);
                dqci.set(JAVA_INT, 24, 1);
                dqci.set(ADDRESS, 32, prio);
                MemorySegment dci = rec(a, 72);
                dci.set(JAVA_INT, 0, 3);
                dci.set(JAVA_INT, 20, 1);
                dci.set(ADDRESS, 24, dqci);
                MemorySegment devOut = rec(a, 8);
                check((int) Api.CREATE_DEVICE.invoke(phys, dci, MemorySegment.NULL, devOut), "vkCreateDevice");
                device = devOut.get(ADDRESS, 0).address();
                MemorySegment qOut = rec(a, 8);
                Api.GET_QUEUE.invoke(MemorySegment.ofAddress(device), qf, 0, qOut);
                queue = qOut.get(ADDRESS, 0).address();

                vertexModule = shaderModule(a, Spv.vertexShader());
                fragmentModule = shaderModule(a, Spv.fragmentShader());
                layout = pipelineLayout(a);
                renderPass = createRenderPass(a);
                pipeline = createPipeline(a);

                MemorySegment cpci = rec(a, 24);
                cpci.set(JAVA_INT, 0, 39);
                cpci.set(JAVA_INT, 16, 0x2);
                cpci.set(JAVA_INT, 20, qf);
                MemorySegment poolOut = rec(a, 8);
                check((int) Api.CREATE_POOL.invoke(dev(), cpci, MemorySegment.NULL, poolOut), "vkCreateCommandPool");
                pool = poolOut.get(JAVA_LONG, 0);
                MemorySegment cbai = rec(a, 32);
                cbai.set(JAVA_INT, 0, 40);
                cbai.set(JAVA_LONG, 16, pool);
                cbai.set(JAVA_INT, 28, 1);
                MemorySegment cbOut = rec(a, 8);
                check((int) Api.ALLOC_CMD.invoke(dev(), cbai, cbOut), "vkAllocateCommandBuffers");
                commandBuffer = cbOut.get(ADDRESS, 0).address();
                MemorySegment fci = rec(a, 24);
                fci.set(JAVA_INT, 0, 8);
                MemorySegment fenceOut = rec(a, 8);
                check((int) Api.CREATE_FENCE.invoke(dev(), fci, MemorySegment.NULL, fenceOut), "vkCreateFence");
                fence = fenceOut.get(JAVA_LONG, 0);
            }
        }

        public synchronized BufferedImage render(float[] verts, int count, int w, int h) throws Throwable {
            if (w <= 0 || h <= 0) throw new IllegalArgumentException("size " + w + "x" + h);
            Target t = target(w, h);
            if (count > 0) {
                ensureVertices((long) count * VERTEX_STRIDE);
                MemorySegment vm = MemorySegment.ofAddress(vertices.address).reinterpret(vertices.capacity);
                for (int i = 0; i < count * 6; i++) {
                    float v = verts[i];
                    if (i % 6 == 0) v = v / w * 2f - 1f;
                    else if (i % 6 == 1) v = v / h * 2f - 1f;
                    vm.set(JAVA_FLOAT, i * 4L, v);
                }
            }
            try (Arena a = Arena.ofConfined()) {
                record(a, t, count);
                MemorySegment cbArr = rec(a, 8);
                cbArr.set(ADDRESS, 0, MemorySegment.ofAddress(commandBuffer));
                MemorySegment submit = rec(a, 72);
                submit.set(JAVA_INT, 0, 4);
                submit.set(JAVA_INT, 40, 1);
                submit.set(ADDRESS, 48, cbArr);
                MemorySegment fenceArr = rec(a, 8);
                fenceArr.set(JAVA_LONG, 0, fence);
                check((int) Api.SUBMIT.invoke(MemorySegment.ofAddress(queue), 1, submit, fence), "vkQueueSubmit");
                check((int) Api.WAIT_FENCES.invoke(dev(), 1, fenceArr, 1, -1L), "vkWaitForFences");
                check((int) Api.RESET_FENCES.invoke(dev(), 1, fenceArr), "vkResetFences");
            }
            MemorySegment read = MemorySegment.ofAddress(t.readback.address).reinterpret(t.readback.capacity);
            int[] px = ((DataBufferInt) t.image.getRaster().getDataBuffer()).getData();
            MemorySegment.copy(read, JAVA_INT_UNALIGNED, 0, px, 0, px.length);
            return t.image;
        }
        private void record(Arena a, Target target, int count) throws Throwable {
            MemorySegment cb = MemorySegment.ofAddress(commandBuffer);
            check((int) Api.RESET_CMD.invoke(cb, 0), "vkResetCommandBuffer");
            MemorySegment bi = rec(a, 32);
            bi.set(JAVA_INT, 0, 42);
            bi.set(JAVA_INT, 16, 0x1);
            check((int) Api.BEGIN_CMD.invoke(cb, bi), "vkBeginCommandBuffer");
            MemorySegment clears = rec(a, 32);
            MemorySegment rpbi = rec(a, 64);
            rpbi.set(JAVA_INT, 0, 43);
            rpbi.set(JAVA_LONG, 16, renderPass);
            rpbi.set(JAVA_LONG, 24, target.framebuffer);
            rpbi.set(JAVA_INT, 40, target.w);
            rpbi.set(JAVA_INT, 44, target.h);
            rpbi.set(JAVA_INT, 48, 2);
            rpbi.set(ADDRESS, 56, clears);
            Api.CMD_BEGIN_PASS.invoke(cb, rpbi, 0);
            Api.CMD_BIND_PIPELINE.invoke(cb, 0, pipeline);
            MemorySegment vp = rec(a, 24);
            vp.set(JAVA_FLOAT, 8, (float) target.w);
            vp.set(JAVA_FLOAT, 12, (float) target.h);
            vp.set(JAVA_FLOAT, 20, 1f);
            Api.CMD_SET_VIEWPORT.invoke(cb, 0, 1, vp);
            MemorySegment sc = rec(a, 16);
            sc.set(JAVA_INT, 8, target.w);
            sc.set(JAVA_INT, 12, target.h);
            Api.CMD_SET_SCISSOR.invoke(cb, 0, 1, sc);
            if (count > 0) {
                MemorySegment vb = rec(a, 8);
                vb.set(JAVA_LONG, 0, vertices.handle);
                Api.CMD_BIND_VERTEX.invoke(cb, 0, 1, vb, rec(a, 8));
                Api.CMD_DRAW.invoke(cb, count, 1, 0, 0);
            }
            Api.CMD_END_PASS.invoke(cb);
            MemorySegment region = rec(a, 56);
            region.set(JAVA_INT, 16, 1);
            region.set(JAVA_INT, 28, 1);
            region.set(JAVA_INT, 44, target.w);
            region.set(JAVA_INT, 48, target.h);
            region.set(JAVA_INT, 52, 1);
            Api.CMD_COPY.invoke(cb, target.resolveImage, LAYOUT_TRANSFER_SRC, target.readback.handle, 1, region);
            check((int) Api.END_CMD.invoke(cb), "vkEndCommandBuffer");
        }

        private long shaderModule(Arena a, int[] code) throws Throwable {
            MemorySegment words = ints(a, code);
            MemorySegment smci = rec(a, 40);
            smci.set(JAVA_INT, 0, 16);
            smci.set(JAVA_LONG, 24, code.length * 4L);
            smci.set(ADDRESS, 32, words);
            MemorySegment out = rec(a, 8);
            check((int) Api.CREATE_SHADER.invoke(dev(), smci, MemorySegment.NULL, out), "vkCreateShaderModule");
            return out.get(JAVA_LONG, 0);
        }
        private long pipelineLayout(Arena a) throws Throwable {
            MemorySegment plci = rec(a, 48);
            plci.set(JAVA_INT, 0, 30);
            MemorySegment out = rec(a, 8);
            check((int) Api.CREATE_LAYOUT.invoke(dev(), plci, MemorySegment.NULL, out), "vkCreatePipelineLayout");
            return out.get(JAVA_LONG, 0);
        }
        private long createRenderPass(Arena a) throws Throwable {
            MemorySegment att = rec(a, 72);
            att.set(JAVA_INT, 4, FORMAT_BGRA8);
            att.set(JAVA_INT, 8, SAMPLES);
            att.set(JAVA_INT, 12, 1);
            att.set(JAVA_INT, 16, 1);
            att.set(JAVA_INT, 20, 2);
            att.set(JAVA_INT, 24, 2);
            att.set(JAVA_INT, 28, 0);
            att.set(JAVA_INT, 32, LAYOUT_COLOR_ATTACHMENT);
            att.set(JAVA_INT, 40, FORMAT_BGRA8);
            att.set(JAVA_INT, 44, 1);
            att.set(JAVA_INT, 48, 2);
            att.set(JAVA_INT, 52, 0);
            att.set(JAVA_INT, 56, 2);
            att.set(JAVA_INT, 60, 2);
            att.set(JAVA_INT, 64, 0);
            att.set(JAVA_INT, 68, LAYOUT_TRANSFER_SRC);
            MemorySegment refs = rec(a, 16);
            refs.set(JAVA_INT, 0, 0);
            refs.set(JAVA_INT, 4, LAYOUT_COLOR_ATTACHMENT);
            refs.set(JAVA_INT, 8, 1);
            refs.set(JAVA_INT, 12, LAYOUT_COLOR_ATTACHMENT);
            MemorySegment sub = rec(a, 72);
            sub.set(JAVA_INT, 4, 0);
            sub.set(JAVA_INT, 24, 1);
            sub.set(ADDRESS, 32, refs.asSlice(0, 8));
            sub.set(ADDRESS, 40, refs.asSlice(8, 8));
            MemorySegment dep = rec(a, 28);
            dep.set(JAVA_INT, 4, -1);
            dep.set(JAVA_INT, 8, 0x400);
            dep.set(JAVA_INT, 12, 0x1000);
            dep.set(JAVA_INT, 16, 0x100);
            dep.set(JAVA_INT, 20, 0x800);
            MemorySegment rpci = rec(a, 64);
            rpci.set(JAVA_INT, 0, 38);
            rpci.set(JAVA_INT, 20, 2);
            rpci.set(ADDRESS, 24, att);
            rpci.set(JAVA_INT, 32, 1);
            rpci.set(ADDRESS, 40, sub);
            rpci.set(JAVA_INT, 48, 1);
            rpci.set(ADDRESS, 56, dep);
            MemorySegment out = rec(a, 8);
            check((int) Api.CREATE_RENDER_PASS.invoke(dev(), rpci, MemorySegment.NULL, out), "vkCreateRenderPass");
            return out.get(JAVA_LONG, 0);
        }
        private long createPipeline(Arena a) throws Throwable {
            MemorySegment main = cString(a, "main");
            MemorySegment stages = rec(a, 96);
            stages.set(JAVA_INT, 0, 18);
            stages.set(JAVA_INT, 20, 0x1);
            stages.set(JAVA_LONG, 24, vertexModule);
            stages.set(ADDRESS, 32, main);
            stages.set(JAVA_INT, 48, 18);
            stages.set(JAVA_INT, 68, 0x10);
            stages.set(JAVA_LONG, 72, fragmentModule);
            stages.set(ADDRESS, 80, main);
            MemorySegment vbind = rec(a, 12);
            vbind.set(JAVA_INT, 4, VERTEX_STRIDE);
            MemorySegment vattr = rec(a, 32);
            vattr.set(JAVA_INT, 0, 0);
            vattr.set(JAVA_INT, 8, FORMAT_RG32F);
            vattr.set(JAVA_INT, 12, 0);
            vattr.set(JAVA_INT, 16, 1);
            vattr.set(JAVA_INT, 24, FORMAT_RGBA32F);
            vattr.set(JAVA_INT, 28, 8);
            MemorySegment vis = rec(a, 48);
            vis.set(JAVA_INT, 0, 19);
            vis.set(JAVA_INT, 20, 1);
            vis.set(ADDRESS, 24, vbind);
            vis.set(JAVA_INT, 32, 2);
            vis.set(ADDRESS, 40, vattr);
            MemorySegment ias = rec(a, 32);
            ias.set(JAVA_INT, 0, 20);
            ias.set(JAVA_INT, 20, 3);
            MemorySegment vps = rec(a, 48);
            vps.set(JAVA_INT, 0, 22);
            vps.set(JAVA_INT, 20, 1);
            vps.set(JAVA_INT, 32, 1);
            MemorySegment rs = rec(a, 64);
            rs.set(JAVA_INT, 0, 23);
            rs.set(JAVA_FLOAT, 56, 1f);
            MemorySegment ms = rec(a, 48);
            ms.set(JAVA_INT, 0, 24);
            ms.set(JAVA_INT, 20, SAMPLES);
            MemorySegment cba = rec(a, 32);
            cba.set(JAVA_INT, 0, 1);
            cba.set(JAVA_INT, 4, 1);
            cba.set(JAVA_INT, 8, 7);
            cba.set(JAVA_INT, 12, 0);
            cba.set(JAVA_INT, 16, 1);
            cba.set(JAVA_INT, 20, 7);
            cba.set(JAVA_INT, 24, 0);
            cba.set(JAVA_INT, 28, 0xF);
            MemorySegment cb = rec(a, 56);
            cb.set(JAVA_INT, 0, 26);
            cb.set(JAVA_INT, 28, 1);
            cb.set(ADDRESS, 32, cba);
            MemorySegment dyn = rec(a, 32);
            dyn.set(JAVA_INT, 0, 27);
            dyn.set(JAVA_INT, 20, 2);
            dyn.set(ADDRESS, 24, ints(a, 0, 1));
            MemorySegment gpci = rec(a, 144);
            gpci.set(JAVA_INT, 0, 28);
            gpci.set(JAVA_INT, 20, 2);
            gpci.set(ADDRESS, 24, stages);
            gpci.set(ADDRESS, 32, vis);
            gpci.set(ADDRESS, 40, ias);
            gpci.set(ADDRESS, 56, vps);
            gpci.set(ADDRESS, 64, rs);
            gpci.set(ADDRESS, 72, ms);
            gpci.set(ADDRESS, 88, cb);
            gpci.set(ADDRESS, 96, dyn);
            gpci.set(JAVA_LONG, 104, layout);
            gpci.set(JAVA_LONG, 112, renderPass);
            gpci.set(JAVA_INT, 136, -1);
            MemorySegment out = rec(a, 8);
            check((int) Api.CREATE_PIPELINES.invoke(dev(), 0L, 1, gpci, MemorySegment.NULL, out), "vkCreateGraphicsPipelines");
            return out.get(JAVA_LONG, 0);
        }

        private MemorySegment dev() { return MemorySegment.ofAddress(device); }

        private int memType(int typeBits, int required) {
            int count = memoryProperties.get(JAVA_INT, 0);
            for (int i = 0; i < count; i++) {
                if ((typeBits & (1 << i)) == 0) continue;
                int flags = memoryProperties.get(JAVA_INT, 4 + i * 8L);
                if ((flags & required) == required) return i;
            }
            throw new IllegalStateException("no memory type with flags " + required);
        }
        private long[] requirements(Arena a, long handle, boolean image) throws Throwable {
            MemorySegment mr = rec(a, 24);
            if (image) Api.IMAGE_REQS.invoke(dev(), handle, mr);
            else Api.BUFFER_REQS.invoke(dev(), handle, mr);
            return new long[]{mr.get(JAVA_LONG, 0), mr.get(JAVA_INT, 16)};
        }
        private long allocate(Arena a, long size, int typeIndex) throws Throwable {
            MemorySegment ai = rec(a, 32);
            ai.set(JAVA_INT, 0, 5);
            ai.set(JAVA_LONG, 16, size);
            ai.set(JAVA_INT, 24, typeIndex);
            MemorySegment out = rec(a, 8);
            check((int) Api.ALLOCATE.invoke(dev(), ai, MemorySegment.NULL, out), "vkAllocateMemory");
            return out.get(JAVA_LONG, 0);
        }
        private Buffer hostBuffer(Arena a, long size, int usage) throws Throwable {
            MemorySegment bci = rec(a, 56);
            bci.set(JAVA_INT, 0, 12);
            bci.set(JAVA_LONG, 24, size);
            bci.set(JAVA_INT, 32, usage);
            MemorySegment out = rec(a, 8);
            check((int) Api.CREATE_BUFFER.invoke(dev(), bci, MemorySegment.NULL, out), "vkCreateBuffer");
            long buffer = out.get(JAVA_LONG, 0);
            long[] req = requirements(a, buffer, false);
            long memory = allocate(a, req[0], memType((int) req[1], MEM_HOST_VISIBLE | MEM_HOST_COHERENT));
            check((int) Api.BIND_BUFFER.invoke(dev(), buffer, memory, 0L), "vkBindBufferMemory");
            MemorySegment ppData = rec(a, 8);
            check((int) Api.MAP.invoke(dev(), memory, 0L, req[0], 0, ppData), "vkMapMemory");
            return new Buffer(buffer, memory, ppData.get(ADDRESS, 0).address(), req[0]);
        }
        private void releaseBuffer(Buffer buffer) throws Throwable {
            if (buffer == null) return;
            Api.UNMAP.invoke(dev(), buffer.memory);
            try (Arena _ = Arena.ofConfined()) {
                Api.FREE_MEMORY.invoke(dev(), buffer.memory, MemorySegment.NULL);
                Api.DESTROY_BUFFER.invoke(dev(), buffer.handle, MemorySegment.NULL);
            }
        }

        private long[] image(Arena a, int w, int h, int samples, int usage) throws Throwable {
            MemorySegment ci = rec(a, 88);
            ci.set(JAVA_INT, 0, 14);
            ci.set(JAVA_INT, 20, 1);
            ci.set(JAVA_INT, 24, FORMAT_BGRA8);
            ci.set(JAVA_INT, 28, w);
            ci.set(JAVA_INT, 32, h);
            ci.set(JAVA_INT, 36, 1);
            ci.set(JAVA_INT, 40, 1);
            ci.set(JAVA_INT, 44, 1);
            ci.set(JAVA_INT, 48, samples);
            ci.set(JAVA_INT, 52, 0);
            ci.set(JAVA_INT, 56, usage);
            MemorySegment out = rec(a, 8);
            check((int) Api.CREATE_IMAGE.invoke(dev(), ci, MemorySegment.NULL, out), "vkCreateImage");
            long image = out.get(JAVA_LONG, 0);
            long[] req = requirements(a, image, true);
            long memory = allocate(a, req[0], memType((int) req[1], MEM_DEVICE_LOCAL));
            check((int) Api.BIND_IMAGE.invoke(dev(), image, memory, 0L), "vkBindImageMemory");
            return new long[]{image, memory};
        }
        private long view(Arena a, long image) throws Throwable {
            MemorySegment ivi = rec(a, 80);
            ivi.set(JAVA_INT, 0, 15);
            ivi.set(JAVA_LONG, 24, image);
            ivi.set(JAVA_INT, 32, 1);
            ivi.set(JAVA_INT, 36, FORMAT_BGRA8);
            ivi.set(JAVA_INT, 56, 1);
            ivi.set(JAVA_INT, 64, 1);
            ivi.set(JAVA_INT, 72, 1);
            MemorySegment out = rec(a, 8);
            check((int) Api.CREATE_VIEW.invoke(dev(), ivi, MemorySegment.NULL, out), "vkCreateImageView");
            return out.get(JAVA_LONG, 0);
        }

        private Target target(int w, int h) throws Throwable {
            if (target != null && target.w == w && target.h == h) return target;
            if (target != null) {
                check((int) Api.DEVICE_IDLE.invoke(dev()), "vkDeviceWaitIdle");
                releaseTarget(target);
                target = null;
            }
            try (Arena a = Arena.ofConfined()) {
                Target t = new Target(w, h);
                long[] msaa = image(a, w, h, SAMPLES, USAGE_COLOR_ATTACHMENT);
                t.msaaImage = msaa[0];
                t.msaaMemory = msaa[1];
                t.msaaView = view(a, t.msaaImage);
                long[] resolve = image(a, w, h, 1, USAGE_COLOR_ATTACHMENT | USAGE_TRANSFER_SRC);
                t.resolveImage = resolve[0];
                t.resolveMemory = resolve[1];
                t.resolveView = view(a, t.resolveImage);
                MemorySegment views = rec(a, 16);
                views.set(JAVA_LONG, 0, t.msaaView);
                views.set(JAVA_LONG, 8, t.resolveView);
                MemorySegment fbci = rec(a, 64);
                fbci.set(JAVA_INT, 0, 37);
                fbci.set(JAVA_LONG, 24, renderPass);
                fbci.set(JAVA_INT, 32, 2);
                fbci.set(ADDRESS, 40, views);
                fbci.set(JAVA_INT, 48, w);
                fbci.set(JAVA_INT, 52, h);
                fbci.set(JAVA_INT, 56, 1);
                MemorySegment out = rec(a, 8);
                check((int) Api.CREATE_FRAMEBUFFER.invoke(dev(), fbci, MemorySegment.NULL, out), "vkCreateFramebuffer");
                t.framebuffer = out.get(JAVA_LONG, 0);
                t.readback = hostBuffer(a, (long) w * h * 4, USAGE_TRANSFER_DST);
                t.image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE);
                target = t;
                return t;
            }
        }
        private void releaseTarget(Target target) throws Throwable {
            try (Arena _ = Arena.ofConfined()) {
                Api.DESTROY_FRAMEBUFFER.invoke(dev(), target.framebuffer, MemorySegment.NULL);
                Api.DESTROY_VIEW.invoke(dev(), target.msaaView, MemorySegment.NULL);
                Api.DESTROY_VIEW.invoke(dev(), target.resolveView, MemorySegment.NULL);
                Api.DESTROY_IMAGE.invoke(dev(), target.msaaImage, MemorySegment.NULL);
                Api.DESTROY_IMAGE.invoke(dev(), target.resolveImage, MemorySegment.NULL);
                Api.FREE_MEMORY.invoke(dev(), target.msaaMemory, MemorySegment.NULL);
                Api.FREE_MEMORY.invoke(dev(), target.resolveMemory, MemorySegment.NULL);
            }
            releaseBuffer(target.readback);
        }

        private void ensureVertices(long bytes) throws Throwable {
            if (vertices != null && vertices.capacity >= bytes) return;
            long size = Math.max(bytes, Math.max(1 << 16, vertices == null ? 0 : vertices.capacity * 2));
            check((int) Api.DEVICE_IDLE.invoke(dev()), "vkDeviceWaitIdle");
            releaseBuffer(vertices);
            try (Arena a = Arena.ofConfined()) {
                vertices = hostBuffer(a, size, USAGE_VERTEX);
            }
        }
    }
    //endregion

    //region Buffers and Targets
    static final class Buffer {
        final long handle, memory, address, capacity;
        Buffer(long handle, long memory, long address, long capacity) {
            this.handle = handle;
            this.memory = memory;
            this.address = address;
            this.capacity = capacity;
        }
    }
    static final class Target {
        final int w, h;
        long msaaImage, msaaMemory, msaaView, resolveImage, resolveMemory, resolveView, framebuffer;
        Buffer readback;
        BufferedImage image;
        Target(int w, int h) { this.w = w; this.h = h; }
    }
    //endregion
}