package Project.Vulkan;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class Spv {
    //region Constructors
    private Spv() {}
    //endregion

    //region Shaders
    public static int[] vertexShader() {
        Builder builder = new Builder();
        builder.capability(1);
        builder.memoryModel();
        int tVoid = builder.id(), tFn = builder.id(), tF = builder.id(), tV2 = builder.id(), tV4 = builder.id();
        int pInV2 = builder.id(), pInV4 = builder.id(), pOutV4 = builder.id();
        int inPos = builder.id(), inCol = builder.id(), outCol = builder.id(), outPos = builder.id();
        int c0 = builder.id(), c1 = builder.id(), main = builder.id(), lbl = builder.id(), vPos = builder.id(), vPos4 = builder.id(), vCol = builder.id();
        builder.entryPoint(0, main, "main", inPos, inCol, outCol, outPos);
        builder.decorate(inPos, 30, 0);
        builder.decorate(inCol, 30, 1);
        builder.decorate(outCol, 30, 0);
        builder.decorate(outPos, 11, 0);
        builder.typeVoid(tVoid);
        builder.typeFunction(tFn, tVoid);
        builder.typeFloat(tF, 32);
        builder.typeVector(tV2, tF, 2);
        builder.typeVector(tV4, tF, 4);
        builder.typePointer(pInV2, 1, tV2);
        builder.typePointer(pInV4, 1, tV4);
        builder.typePointer(pOutV4, 3, tV4);
        builder.variable(pInV2, inPos, 1);
        builder.variable(pInV4, inCol, 1);
        builder.variable(pOutV4, outCol, 3);
        builder.variable(pOutV4, outPos, 3);
        builder.constant(tF, c0, 0x00000000);
        builder.constant(tF, c1, 0x3f800000);
        builder.function(tVoid, main, tFn);
        builder.label(lbl);
        builder.load(tV2, vPos, inPos);
        builder.composite(tV4, vPos4, vPos, c0, c1);
        builder.store(outPos, vPos4);
        builder.load(tV4, vCol, inCol);
        builder.store(outCol, vCol);
        builder.ret();
        builder.functionEnd();
        return builder.build();
    }
    public static int[] fragmentShader() {
        Builder builder = new Builder();
        builder.capability(1);
        builder.memoryModel();
        int tVoid = builder.id(), tFn = builder.id(), tF = builder.id(), tV4 = builder.id(), pIn = builder.id(), pOut = builder.id();
        int inC = builder.id(), outC = builder.id(), main = builder.id(), lbl = builder.id(), v = builder.id();
        builder.entryPoint(4, main, "main", inC, outC);
        builder.decorate(inC, 30, 0);
        builder.decorate(outC, 30, 0);
        builder.typeVoid(tVoid);
        builder.typeFunction(tFn, tVoid);
        builder.typeFloat(tF, 32);
        builder.typeVector(tV4, tF, 4);
        builder.typePointer(pIn, 1, tV4);
        builder.typePointer(pOut, 3, tV4);
        builder.variable(pIn, inC, 1);
        builder.variable(pOut, outC, 3);
        builder.function(tVoid, main, tFn);
        builder.label(lbl);
        builder.load(tV4, v, inC);
        builder.store(outC, v);
        builder.ret();
        builder.functionEnd();
        return builder.build();
    }
    //endregion

    //region Builder
    static final class Builder {
        private int bound = 1;
        private final List<Integer> caps = new ArrayList<>(), entries = new ArrayList<>(), decos = new ArrayList<>(),
                types = new ArrayList<>(), body = new ArrayList<>();

        int id() { return bound++; }
        static int[] str(String s) {
            byte[] b = (s + "\0").getBytes(StandardCharsets.UTF_8);
            int[] w = new int[(b.length + 3) / 4];
            for (int i = 0; i < b.length; i++) w[i / 4] |= (b[i] & 0xFF) << (8 * (i % 4));
            return w;
        }

        void capability(int c) { emit(caps, 17, c); }
        void memoryModel() { emit(caps, 14, 0, 1); }
        void entryPoint(int model, int fn, String name, int... ifaces) {
            int[] n = str(name);
            int[] w = new int[2 + n.length + ifaces.length];
            w[0] = model;
            w[1] = fn;
            System.arraycopy(n, 0, w, 2, n.length);
            System.arraycopy(ifaces, 0, w, 2 + n.length, ifaces.length);
            emit(entries, 15, w);
        }

        void decorate(int target, int decoration, int literal) { emit(decos, 71, target, decoration, literal); }

        void typeVoid(int r) { emit(types, 19, r); }
        void typeFunction(int r, int ret) { emit(types, 33, r, ret); }
        void typeFloat(int r, int width) { emit(types, 22, r, width); }
        void typeVector(int r, int component, int count) { emit(types, 23, r, component, count); }
        void typePointer(int r, int storage, int type) { emit(types, 32, r, storage, type); }

        void constant(int type, int r, int bits) { emit(types, 43, type, r, bits); }
        void variable(int ptrType, int r, int storage) { emit(types, 59, ptrType, r, storage); }
        void composite(int type, int r, int... parts) {
            int[] w = new int[2 + parts.length];
            w[0] = type;
            w[1] = r;
            System.arraycopy(parts, 0, w, 2, parts.length);
            emit(body, 80, w);
        }

        void function(int resultType, int r, int fnType) { emit(body, 54, resultType, r, 0, fnType); }
        void label(int r) { emit(body, 248, r); }
        void ret() { emit(body, 253); }
        void functionEnd() { emit(body, 56); }
        void load(int type, int r, int ptr) { emit(body, 61, type, r, ptr); }
        void store(int ptr, int obj) { emit(body, 62, ptr, obj); }

        int[] build() {
            List<Integer> all = new ArrayList<>(List.of(0x07230203, 0x00010000, 0, bound, 0));
            all.addAll(caps);
            all.addAll(entries);
            all.addAll(decos);
            all.addAll(types);
            all.addAll(body);
            return all.stream().mapToInt(Integer::intValue).toArray();
        }
        private static void emit(List<Integer> out, int opcode, int... words) {
            out.add(((words.length + 1) << 16) | opcode);
            for (int w : words) out.add(w);
        }
    }
    //endregion
}
