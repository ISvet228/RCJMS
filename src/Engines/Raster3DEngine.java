package Engines;

import Project.Game.*;
import Project.Helpers.*;
import Project.OpenCl.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

public final class Raster3DEngine implements RenderEngine {
    //region Variables
    //region Engine Identity
    static final String ID = "raster3d";
    //endregion
    //region Lighting
    static final double TORCH_RIGHT = 0.3, TORCH_FORWARD = 0.25, TORCH_DROP = 0.25, TORCH_RANGE = 3.0;
    static final double AMBIENT = 0.22;
    static final double SIDE_SHADE_Y = 0.85;
    static final double WARM_RED = 0.12, WARM_BLUE = 0.18;
    static final int FOG = 0x120C1E;
    static final double FOG_START = 2.0, FOG_RANGE = 7.0, FOG_MAX = 0.8;
    //endregion
    //region Textures
    private static final int WALL = 0, FINISH = 1, FLOOR = 2, CEILING = 3;
    private static final int[] BASE = {0xECD485, 0x33FF66, 0xD3AF63, 0x816E1E};
    //endregion
    //region Frame Limits
    private static final double NEAR = 0.05;
    static final double APERTURE_EPS = 1e-3;
    private static final int BAND_ROWS = 8;
    static final int HYP_SPLIT = 6;
    static final int GRID_COUNT = (HYP_SPLIT + 1) * (HYP_SPLIT + 2) / 2;
    private static final int[][] GRID_INDEX = gridIndex();
    static final int MAX_DEPTH = 3;
    static final int MAX_APS = 32;
    static final int STACK = MAX_DEPTH + 1;
    static final int AP_STRIDE = 12;
    private static final int[] PRISM_PLANES = {0, 3, 4};
    private static final Aperture IDENTITY = new Aperture(0, -1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0);
    private static final int[][] NO_MAP = new int[0][];
    //endregion
    //region Projected Triangle Buffer
    private static final int SX = 0, SY = 3, IZ = 6, SZ = 9, TZ = 12, INV = 15, MIN_X = 16, MAX_X = 17, MIN_Y = 18, MAX_Y = 19,
            TEX = 20, WALL_FLAG = 21, SHADE = 22, TINT = 23, APER = 24, LAYER = 25, STRIDE = 26;
    private static double[] projected = new double[STRIDE * 256];
    private static int projectedCount;
    private static double[] depth = new double[0];
    private static double[] stT = new double[0];
    private static int[] stI = new int[0];
    private static int[] stN = new int[0];
    private static final Object LOCK = new Object();
    //endregion
    //region Mesh Cache
    private static volatile Mesh[] cachedMeshes;
    private static volatile int[][][] cachedLayers;
    private static volatile boolean cachedSky;
    //endregion
    //endregion

    //region Engine Identity
    @Override public String id() { return ID; }
    @Override public String displayName() { return "3D"; }
    @Override public boolean hasHyperbolicGeometry() { return true; }
    @Override public boolean hasOpenCL() { return true; }
    //endregion

    //region Frame Rendering
    @Override public void render(RenderFrame renderFrame) {
        final int w = renderFrame.renderWidth, h = renderFrame.renderHeight;
        if (w <= 0 || h <= 0 || renderFrame.renderPixels == null || renderFrame.renderPixels.length < w * h || renderFrame.map == null) return;

        synchronized (LOCK) {
            int[][][] layers = layersOf(renderFrame);
            Mesh[] meshes = meshesFor(renderFrame, layers);
            Target target = new Target(renderFrame, w, h, layers);
            project(target, meshes);
            if (hasOpenCL() && Gpu.tryRender(renderFrame, target)) {
                DDAEngine.copyRenderBuffer(renderFrame);
                return;
            }
            int bands = (h + BAND_ROWS - 1) / BAND_ROWS;
            IntStream.range(0, bands).parallel().forEach(b -> {
                int y0 = b * BAND_ROWS, y1 = Math.min(h, y0 + BAND_ROWS);
                clearBand(target, y0, y1);
                apertureBand(target, y0, y1);
                drawBand(target, y0, y1);
                if (target.sky) skyBand(target, y0, y1);
            });
        }
        DDAEngine.copyRenderBuffer(renderFrame);
    }
    static int[][][] layersOf(RenderFrame renderFrame) {
        int floors = renderFrame.map3D == null ? 0 : renderFrame.map3D.length;
        int[][][] layers = new int[1 + floors][][];
        layers[0] = renderFrame.map;
        for (int f = 0; f < floors; f++) layers[f + 1] = renderFrame.map3D[f] == null ? NO_MAP : renderFrame.map3D[f];
        return layers;
    }
    private static Mesh[] meshesFor(RenderFrame renderFrame, int[][][] layers) {
        Mesh[] meshes = cachedMeshes;
        if (meshes != null && cachedSky == renderFrame.skyCeiling && sameLayers(cachedLayers, layers)) return meshes;
        meshes = new Mesh[layers.length];
        for (int l = 0; l < layers.length; l++) meshes[l] = build(layers[l], renderFrame.skyCeiling);
        cachedMeshes = meshes;
        cachedLayers = layers;
        cachedSky = renderFrame.skyCeiling;
        return meshes;
    }
    private static boolean sameLayers(int[][][] a, int[][][] b) {
        if (a == null || a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) if (a[i] != b[i]) return false;
        return true;
    }
    //endregion

    //region Lighting
    static double[] placeTorch(int[][] map, double[] eye, double sinA, double cosA) {
        double tz = Math.max(0.05, eye[2] - TORCH_DROP);
        for (double s : new double[]{1.0, 0.75, 0.5, 0.25}) {
            double tx = eye[0] - s * (TORCH_RIGHT * sinA + TORCH_FORWARD * cosA);
            double ty = eye[1] + s * (TORCH_RIGHT * cosA - TORCH_FORWARD * sinA);
            boolean open = cellHeight(map, (int) Math.floor(tx), (int) Math.floor(ty)) <= 0;
            if (open && !shadowed(map, eye[0], eye[1], eye[2], tx, ty, tz)) return new double[]{tx, ty, tz};
        }
        return new double[]{eye[0], eye[1], tz};
    }
    static int light(int color, double shade, double torch, double z) {
        double k = (AMBIENT + torch) * shade;
        double r = ((color >> 16) & 255) * k * (1 + WARM_RED * torch);
        double g = ((color >> 8) & 255) * k;
        double b = (color & 255) * k * (1 - WARM_BLUE * torch);
        double fog = Math.clamp((z - FOG_START) / FOG_RANGE, 0.0, 1.0) * FOG_MAX;
        r = Math.min(255, r * (1 - fog) + ((FOG >> 16) & 255) * fog);
        g = Math.min(255, g * (1 - fog) + ((FOG >> 8) & 255) * fog);
        b = Math.min(255, b * (1 - fog) + (FOG & 255) * fog);
        return ((int) r << 16) | ((int) g << 8) | (int) b;
    }
    static double cellHeight(int[][] map, int x, int y) {
        if (y < 0 || y >= map.length || x < 0 || x >= map[y].length) return 1.0;
        int type = map[y][x];
        return type == 1 ? 1.0 : type == 2 ? 0.5 : 0.0;
    }
    static boolean shadowed(int[][] map, double fx, double fy, double fz, double lx, double ly, double lz) {
        if (cellHeight(map, (int) Math.floor(lx), (int) Math.floor(ly)) > 0) return false;
        double dx = lx - fx, dy = ly - fy;
        if (Math.abs(dx) + Math.abs(dy) < 1e-9) return false;
        double sx = fx + dx * 1e-4, sy = fy + dy * 1e-4;
        int cx = (int) Math.floor(sx), cy = (int) Math.floor(sy);
        int stepX = dx > 0 ? 1 : -1, stepY = dy > 0 ? 1 : -1;
        double tDeltaX = Math.abs(dx) > 1e-12 ? 1.0 / Math.abs(dx) : 1e30;
        double tDeltaY = Math.abs(dy) > 1e-12 ? 1.0 / Math.abs(dy) : 1e30;
        double tMaxX = dx > 0 ? (cx + 1 - fx) / dx : dx < 0 ? (cx - fx) / dx : 1e30;
        double tMaxY = dy > 0 ? (cy + 1 - fy) / dy : dy < 0 ? (cy - fy) / dy : 1e30;
        double t0 = 0;
        for (int guard = 0; guard < 4096; guard++) {
            double t1 = Math.min(Math.min(tMaxX, tMaxY), 1.0);
            double za = fz + t0 * (lz - fz), zb = fz + t1 * (lz - fz);
            double hgt = cellHeight(map, cx, cy);
            if (hgt > 0 && Math.min(za, zb) < hgt) return true;
            if (t1 >= 1.0) return false;
            if (tMaxX < tMaxY) {
                t0 = tMaxX;
                tMaxX += tDeltaX;
                cx += stepX;
            } else {
                t0 = tMaxY;
                tMaxY += tDeltaY;
                cy += stepY;
            }
        }
        return false;
    }
    //endregion

    //region Hyperbolic View
    static double warpDepth(double flat, double r) { return r * Math.sinh(flat / r); }
    static double unwarpDepth(double seen, double r) {
        double q = seen / r;
        return r * Math.log(q + Math.sqrt(q * q + 1.0));
    }
    private static void warpVertex(double[] v, double r) {
        double seen = warpDepth(v[2], r);
        v[0] = v[0] * seen / v[2];
        v[2] = seen;
    }
    private static void pushWarped(Target target, Scratch scratch, double[] p0, double[] p1, double[] p2, Tri tri, int aper, int layer) {
        int n = HYP_SPLIT;
        double[][] g = scratch.grid;
        for (int i = 0; i <= n; i++) {
            for (int j = 0; i + j <= n; j++) {
                double[] v = g[GRID_INDEX[i][j]];
                double fi = (double) i / n, fj = (double) j / n;
                for (int k = 0; k < 5; k++) v[k] = p0[k] + (p1[k] - p0[k]) * fi + (p2[k] - p0[k]) * fj;
                warpVertex(v, target.curvature);
            }
        }
        for (int i = 0; i < n; i++) {
            for (int j = 0; i + j < n; j++) {
                pushTriangle(target, g[GRID_INDEX[i][j]], g[GRID_INDEX[i + 1][j]], g[GRID_INDEX[i][j + 1]], tri, aper, layer);
                if (i + j + 1 < n)
                    pushTriangle(target, g[GRID_INDEX[i + 1][j]], g[GRID_INDEX[i + 1][j + 1]], g[GRID_INDEX[i][j + 1]], tri, aper, layer);
            }
        }
    }
    private static int[][] gridIndex() {
        int[][] idx = new int[Raster3DEngine.HYP_SPLIT + 1][Raster3DEngine.HYP_SPLIT + 1];
        int c = 0;
        for (int i = 0; i <= Raster3DEngine.HYP_SPLIT; i++) for (int j = 0; i + j <= Raster3DEngine.HYP_SPLIT; j++) idx[i][j] = c++;
        return idx;
    }
    //endregion

    //region Openings And Portals
    private record Aperture(int layer, int parent, double ex, double ey, double xx, double xy, double c, double s, double nx, double ny, double edx, double edy) { }
    private static Aperture[] aperturesOf(RenderFrame renderFrame, int layerCount) {
        List<Aperture> list = new ArrayList<>();
        addOpenings(renderFrame, layerCount, 0, IDENTITY, -1, 1, -1, -1, new int[]{0}, list);
        return list.toArray(new Aperture[0]);
    }
    private static void addOpenings(RenderFrame renderFrame, int layerCount, int owner, Aperture parentMap, int parentIdx, int depth,
                                    int bannedX, int bannedY, int[] chain, List<Aperture> out) {
        PortalData portals = portalsOf(renderFrame, owner);
        if (depth > MAX_DEPTH || portals == null || portals.isEmpty()) return;
        for (PortalData.Portal portal : portals.values()) {
            if (out.size() >= MAX_APS) return;
            if (portal.x == bannedX && portal.y == bannedY) continue;
            int target = targetLayer(renderFrame, portal);
            if (target >= layerCount) continue;
            if ((portal.dirX == 0 && portal.dirY == 0) || (portal.linkedDirX == 0 && portal.linkedDirY == 0)) continue;
            if (depth > 1 && onChain(chain, target)) continue;
            Aperture aperture = composed(parentMap, parentIdx, portal, target);
            out.add(aperture);
            int[] next = Arrays.copyOf(chain, chain.length + 1);
            next[chain.length] = target;
            addOpenings(renderFrame, layerCount, target, aperture, out.size() - 1, depth + 1, portal.linkedX, portal.linkedY, next, out);
        }
    }
    private static boolean onChain(int[] chain, int layer) {
        for (int c : chain) if (c == layer) return true;
        return false;
    }
    private static int targetLayer(RenderFrame renderFrame, PortalData.Portal portal) {
        if (portal.linkedFloor < 0) return 0;
        if (renderFrame.map3D != null && portal.linkedFloor < renderFrame.map3D.length && renderFrame.map3D[portal.linkedFloor] == renderFrame.map) return 0;
        return portal.linkedFloor + 1;
    }
    private static PortalData portalsOf(RenderFrame renderFrame, int layer) {
        if (layer == 0) return renderFrame.portals;
        return renderFrame.floorPortalsAll == null || layer - 1 >= renderFrame.floorPortalsAll.length ? null : renderFrame.floorPortalsAll[layer - 1];
    }
    private static Aperture composed(Aperture par, int parIdx, PortalData.Portal portal, int target) {
        double nax = portal.dirX, nay = portal.dirY, mbx = portal.linkedDirX, mby = portal.linkedDirY;
        double ex = portal.x + Math.max(nax, 0) + Math.max(nay, 0), ey = portal.y + Math.max(nay, 0) + Math.max(-nax, 0);
        double xx = portal.linkedX + Math.max(mbx, 0) + Math.max(mby, 0), xy = portal.linkedY + Math.max(mby, 0) + Math.max(-mbx, 0);
        double c = nax * mbx + nay * mby, s = nax * mby - nay * mbx;
        double dx = ex - par.xx(), dy = ey - par.xy();
        double mex = par.ex() + par.c() * dx + par.s() * dy;
        double mey = par.ey() - par.s() * dx + par.c() * dy;
        double mc = par.c() * c - par.s() * s, ms = par.s() * c + par.c() * s;
        double mnx = par.c() * nax + par.s() * nay, mny = -par.s() * nax + par.c() * nay;
        double medx = par.c() * (-nay) + par.s() * nax, medy = -par.s() * (-nay) + par.c() * nax;
        return new Aperture(target, parIdx, mex, mey, xx, xy, mc, ms, mnx, mny, medx, medy);
    }
    private static void toMain(Aperture aperture, double[] v, int i, double[] out) {
        double dx = v[5 * i] - aperture.xx(), dy = v[5 * i + 1] - aperture.xy();
        out[0] = aperture.ex() + dx * aperture.c() + dy * aperture.s();
        out[1] = aperture.ey() - dx * aperture.s() + dy * aperture.c();
        out[2] = v[5 * i + 2];
        out[3] = v[5 * i + 3];
        out[4] = v[5 * i + 4];
    }
    private static double[] toLinked(Aperture aperture, double x, double y) {
        double px = x - aperture.ex(), py = y - aperture.ey();
        return new double[]{aperture.xx() + aperture.c() * px - aperture.s() * py, aperture.xy() + aperture.s() * px + aperture.c() * py};
    }
    private static int[] chainOf(Aperture[] aps, int ta) {
        int n = 0;
        for (int id = ta; id >= 0 && n < MAX_DEPTH; id = aps[id].parent()) n++;
        int[] chain = new int[n];
        int id = ta;
        for (int k = n - 1; k >= 0; k--) {
            chain[k] = id;
            id = aps[id].parent();
        }
        return chain;
    }
    private static int topAt(int idx, double z) {
        int top = -1, base = idx * STACK, n = stN[idx];
        for (int k = 0; k < n; k++) {
            if (stT[base + k] - APERTURE_EPS >= z) break;
            top = stI[base + k];
        }
        return top;
    }
    //endregion

    //region Clipping And Projection
    private static int clipPrism(Aperture aperture, Scratch scratch, int n) {
        double[][] src = scratch.a, dst = scratch.b;
        for (int k = 0; k < PRISM_PLANES.length && n > 0; k++) {
            int plane = PRISM_PLANES[k];
            n = clipPlane(src, n, dst, plane, aperture);
            double[][] tmp = src;
            src = dst;
            dst = tmp;
        }
        scratch.res = src;
        return n;
    }
    private static double planeValue(double[] v, int plane, Aperture aperture) {
        double along = aperture.edx() * (v[0] - aperture.ex()) + aperture.edy() * (v[1] - aperture.ey());
        return switch (plane) {
            case 0 -> aperture.nx() * (v[0] - aperture.ex()) + aperture.ny() * (v[1] - aperture.ey());
            case 1 -> along;
            case 2 -> 1.0 - along;
            case 3 -> v[2];
            default -> 1.0 - v[2];
        };
    }
    private static int clipPlane(double[][] in, int n, double[][] out, int plane, Aperture aperture) {
        int m = 0;
        for (int i = 0; i < n; i++) {
            double[] a = in[i], b = in[(i + 1) % n];
            double da = planeValue(a, plane, aperture), db = planeValue(b, plane, aperture);
            boolean ina = da >= 0, inb = db >= 0;
            if (ina) System.arraycopy(a, 0, out[m++], 0, 5);
            if (ina != inb) {
                double t = da / (da - db);
                double[] p = out[m++];
                for (int k = 0; k < 5; k++) p[k] = a[k] + (b[k] - a[k]) * t;
            }
        }
        return m;
    }
    private static void toCamera(Target target, double[] v, double[] out) {
        double rx = v[0] - target.eye[0], ry = v[1] - target.eye[1], rz = v[2] - target.eye[2];
        out[0] = rx * target.r[0] + ry * target.r[1] + rz * target.r[2];
        out[1] = rx * target.u[0] + ry * target.u[1] + rz * target.u[2];
        out[2] = rx * target.f[0] + ry * target.f[1] + rz * target.f[2];
        out[3] = v[3];
        out[4] = v[4];
    }
    private static void project(Target target, Mesh[] meshes) {
        projectedCount = 0;
        Scratch scratch = new Scratch();
        for (Tri tri : meshes[0].tris) {
            for (int i = 0; i < 3; i++) System.arraycopy(tri.v, 5 * i, scratch.a[i], 0, 5);
            emit(target, scratch.a, 3, tri, -1, 0, scratch);
        }
        for (int i = 0; i < target.aps.length; i++) {
            Aperture aperture = target.aps[i];
            for (Tri tri : meshes[aperture.layer()].tris) {
                for (int k = 0; k < 3; k++) toMain(aperture, tri.v, k, scratch.a[k]);
                int n = clipPrism(aperture, scratch, 3);
                if (n >= 3) emit(target, scratch.res, n, tri, i, aperture.layer(), scratch);
            }
        }
    }
    private static void emit(Target target, double[][] poly, int n, Tri tri, int aper, int layer, Scratch scratch) {
        for (int i = 0; i < n; i++) toCamera(target, poly[i], scratch.cam[i]);
        int m = clipNear(scratch.cam, n, scratch.camClip);
        for (int k = 1; k + 1 < m; k++) {
            if (target.hyp) pushWarped(target, scratch, scratch.camClip[0], scratch.camClip[k], scratch.camClip[k + 1], tri, aper, layer);
            else pushTriangle(target, scratch.camClip[0], scratch.camClip[k], scratch.camClip[k + 1], tri, aper, layer);
        }
    }
    private static int clipNear(double[][] in, int n, double[][] out) {
        int m = 0;
        for (int i = 0; i < n; i++) {
            double[] a = in[i], b = in[(i + 1) % n];
            boolean ina = a[2] >= NEAR, inb = b[2] >= NEAR;
            if (ina) System.arraycopy(a, 0, out[m++], 0, 5);
            if (ina != inb) {
                double t = (NEAR - a[2]) / (b[2] - a[2]);
                double[] p = out[m++];
                for (int k = 0; k < 5; k++) p[k] = a[k] + (b[k] - a[k]) * t;
            }
        }
        return m;
    }
    private static void pushTriangle(Target target, double[] p0, double[] p1, double[] p2, Tri tri, int aper, int layer) {
        if (projectedCount * STRIDE + STRIDE > projected.length) projected = Arrays.copyOf(projected, projected.length * 2);
        int o = projectedCount * STRIDE;
        double[][] p = {p0, p1, p2};
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (int i = 0; i < 3; i++) {
            double iz = 1.0 / p[i][2];
            double sx = target.w / 2.0 + target.focal * p[i][0] * iz;
            double sy = target.h / 2.0 - target.focal * p[i][1] * iz;
            projected[o + SX + i] = sx;
            projected[o + SY + i] = sy;
            projected[o + IZ + i] = iz;
            projected[o + SZ + i] = p[i][3] * iz;
            projected[o + TZ + i] = p[i][4] * iz;
            minX = Math.min(minX, sx); maxX = Math.max(maxX, sx);
            minY = Math.min(minY, sy); maxY = Math.max(maxY, sy);
        }
        double denom = (projected[o + SY + 1] - projected[o + SY + 2]) * (projected[o + SX] - projected[o + SX + 2])
                + (projected[o + SX + 2] - projected[o + SX + 1]) * (projected[o + SY] - projected[o + SY + 2]);
        if (Math.abs(denom) < 1e-12) return;
        projected[o + INV] = 1.0 / denom;
        projected[o + MIN_X] = Math.max(0, Math.floor(minX));
        projected[o + MAX_X] = Math.min(target.w - 1, Math.ceil(maxX));
        projected[o + MIN_Y] = Math.max(0, Math.floor(minY));
        projected[o + MAX_Y] = Math.min(target.h - 1, Math.ceil(maxY));
        if (projected[o + MIN_X] > projected[o + MAX_X] || projected[o + MIN_Y] > projected[o + MAX_Y]) return;
        projected[o + TEX] = tri.tex();
        projected[o + WALL_FLAG] = tri.wall() ? 1 : 0;
        projected[o + SHADE] = tri.shade();
        projected[o + TINT] = tri.tint();
        projected[o + APER] = aper;
        projected[o + LAYER] = layer;
        projectedCount++;
    }
    private static final class Scratch {
        final double[][] a = new double[12][5], b = new double[12][5], cam = new double[12][5], camClip = new double[12][5];
        final double[][] grid = new double[GRID_COUNT][5];
        double[][] res;
    }
    //endregion

    //region Mesh Building
    private record Tri(double[] v, int tex, boolean wall, double shade, int tint) { }
    private static final class Mesh { final List<Tri> tris = new ArrayList<>(); }
    private static boolean solid(int type) { return type == 1 || type == 2; }
    private static Mesh build(int[][] map, boolean sky) {
        Mesh mesh = new Mesh();
        for (int y = 0; y < map.length; y++) {
            for (int x = 0; x < map[y].length; x++) {
                int type = map[y][x];
                if (!sky) quad(mesh, x, y, 1.0, CEILING, 0);
                if (solid(type)) {
                    double top = type == 2 ? 0.5 : 1.0;
                    if (type == 2) quad(mesh, x, y, top, FLOOR, 0);
                    addWalls(mesh, map, x, y, top, type == 2 ? FINISH : WALL);
                    continue;
                }
                int tint = type == 3 ? 0x3399FF : type == 4 ? 0xFFAA33 : 0;
                quad(mesh, x, y, 0.0, FLOOR, tint);
            }
        }
        return mesh;
    }
    private static void quad(Mesh mesh, int x, int y, double z, int tex, int tint) {
        double[] a = vertex(x, y, z, x, y), b = vertex(x + 1, y, z, x + 1, y);
        double[] c = vertex(x + 1, y + 1, z, x + 1, y + 1), d = vertex(x, y + 1, z, x, y + 1);
        addTri(mesh, a, b, c, tex, false, 1.0, tint);
        addTri(mesh, a, c, d, tex, false, 1.0, tint);
    }
    private static void addWalls(Mesh mesh, int[][] map, int x, int y, double top, int tex) {
        if (openAt(map, x - 1, y)) wallFace(mesh, x, y, top, tex, 0, 1.0);
        if (openAt(map, x + 1, y)) wallFace(mesh, x + 1, y, top, tex, 0, 1.0);
        if (openAt(map, x, y - 1)) wallFace(mesh, x, y, top, tex, 1, SIDE_SHADE_Y);
        if (openAt(map, x, y + 1)) wallFace(mesh, x, y + 1, top, tex, 1, SIDE_SHADE_Y);
    }
    private static boolean openAt(int[][] map, int x, int y) { return y >= 0 && y < map.length && x >= 0 && x < map[y].length && !solid(map[y][x]); }
    private static void wallFace(Mesh mesh, int px, int py, double top, int tex, int axis, double shade) {
        double[] a, b, c, d;
        if (axis == 0) {
            a = vertex(px, py, 0, py, 0);
            b = vertex(px, py + 1, 0, py + 1, 0);
            c = vertex(px, py + 1, top, py + 1, 1);
            d = vertex(px, py, top, py, 1);
        } else {
            a = vertex(px, py, 0, px, 0);
            b = vertex(px + 1, py, 0, px + 1, 0);
            c = vertex(px + 1, py, top, px + 1, 1);
            d = vertex(px, py, top, px, 1);
        }
        addTri(mesh, a, b, c, tex, true, shade, 0);
        addTri(mesh, a, c, d, tex, true, shade, 0);
    }
    private static double[] vertex(double x, double y, double z, double s, double t) {
        return new double[]{x, y, z, s, t};
    }
    private static void addTri(Mesh mesh, double[] a, double[] b, double[] c, int tex, boolean wall, double shade, int tint) {
        double[] v = new double[15];
        System.arraycopy(a, 0, v, 0, 5);
        System.arraycopy(b, 0, v, 5, 5);
        System.arraycopy(c, 0, v, 10, 5);
        mesh.tris.add(new Tri(v, tex, wall, shade, tint));
    }
    //endregion

    //region Drawing
    private static final class Target {
        final int w, h;
        final int[] out;
        final boolean sky;
        final double skyTime;
        final double focal;
        final double[] f, r, u, eye, torch;
        final boolean hyp;
        final double curvature;
        final int[][][] layers;
        final Aperture[] aps;
        final int[][] pix = new int[4][];
        final int[] tw = new int[4], th = new int[4];

        Target(RenderFrame renderFrame, int w, int h, int[][][] layers) {
            this.w = w;
            this.h = h;
            this.out = renderFrame.renderPixels;
            this.sky = renderFrame.skyCeiling;
            this.skyTime = renderFrame.skyTime;
            this.focal = w / (2.0 * Math.tan(Math.PI / 6.0));
            RuntimeTexture[] textures = {renderFrame.wallTexture, renderFrame.finishTexture, renderFrame.floorTexture, renderFrame.ceilingTexture};
            for (int i = 0; i < 4; i++) {
                if (textures[i] == null) continue;
                pix[i] = textures[i].pixels();
                tw[i] = textures[i].width();
                th[i] = textures[i].height();
            }
            double yaw = renderFrame.cameraAngle, pitch = renderFrame.cameraPitch;
            double cosP = Math.cos(pitch), sinP = Math.sin(pitch), cosA = Math.cos(yaw), sinA = Math.sin(yaw);
            f = new double[]{cosP * cosA, cosP * sinA, sinP};
            r = new double[]{-sinA, cosA, 0};
            u = new double[]{-sinP * cosA, -sinP * sinA, cosP};
            eye = new double[]{renderFrame.playerX, renderFrame.playerY, renderFrame.cameraHeight};
            this.layers = layers;
            torch = placeTorch(layers[0], eye, sinA, cosA);
            this.hyp = renderFrame.geometry == MazeGenerator.GeometryMode.WRONG;
            this.curvature = Math.max(renderFrame.curvature, 1e-4);
            this.aps = aperturesOf(renderFrame, layers.length);
            if (depth.length < w * h) depth = new double[w * h];
            if (stN.length < w * h) {
                stN = new int[w * h];
                stT = new double[w * h * STACK];
                stI = new int[w * h * STACK];
            }
        }
    }
    private static void clearBand(Target target, int y0, int y1) {
        Arrays.fill(depth, y0 * target.w, y1 * target.w, 0.0);
        if (!target.sky) Arrays.fill(target.out, y0 * target.w, y1 * target.w, BASE[CEILING]);
    }
    private static void skyBand(Target target, int y0, int y1) {
        for (int y = y0; y < y1; y++) {
            double v = (target.h / 2.0 - (y + 0.5)) / target.focal;
            int rowBase = y * target.w;
            for (int x = 0; x < target.w; x++) {
                if (depth[rowBase + x] != 0.0) continue;
                double a = (x + 0.5 - target.w / 2.0) / target.focal;
                double dx = target.f[0] + a * target.r[0] + v * target.u[0];
                double dy = target.f[1] + a * target.r[1] + v * target.u[1];
                double dz = target.f[2] + a * target.r[2] + v * target.u[2];
                target.out[rowBase + x] = SkyCeiling.colorDir(dx, dy, dz, target.skyTime);
            }
        }
    }
    private static void apertureBand(Target target, int y0, int y1) {
        Aperture[] aps = target.aps;
        double[] cT = new double[Math.max(1, aps.length)];
        int[] cI = new int[Math.max(1, aps.length)];
        for (int y = y0; y < y1; y++) {
            double v = (target.h / 2.0 - (y + 0.5)) / target.focal;
            int rowBase = y * target.w;
            for (int x = 0; x < target.w; x++) {
                double a = (x + 0.5 - target.w / 2.0) / target.focal;
                double dx = target.f[0] + a * target.r[0] + v * target.u[0];
                double dy = target.f[1] + a * target.r[1] + v * target.u[1];
                double dz = target.f[2] + a * target.r[2] + v * target.u[2];
                int n = 0;
                for (int i = 0; i < aps.length; i++) {
                    Aperture aperture = aps[i];
                    double dn = dx * aperture.nx() + dy * aperture.ny();
                    if (dn <= 1e-12) continue;
                    double t = ((aperture.ex() - target.eye[0]) * aperture.nx() + (aperture.ey() - target.eye[1]) * aperture.ny()) / dn;
                    if (t <= 0) continue;
                    double X = target.eye[0] + t * dx, Y = target.eye[1] + t * dy, Z = target.eye[2] + t * dz;
                    double along = (X - aperture.ex()) * aperture.edx() + (Y - aperture.ey()) * aperture.edy();
                    if (along < 0 || along > 1 || Z < 0 || Z > 1) continue;
                    int j = n++;
                    while (j > 0 && cT[j - 1] > t) {
                        cT[j] = cT[j - 1];
                        cI[j] = cI[j - 1];
                        j--;
                    }
                    cT[j] = t;
                    cI[j] = i;
                }
                int idx = rowBase + x, base = idx * STACK, sN = 0;
                for (int k = 0; k < n; k++) {
                    int top = sN == 0 ? -1 : stI[base + sN - 1];
                    if (aps[cI[k]].parent() != top || sN == STACK) continue;
                    stI[base + sN] = cI[k];
                    stT[base + sN] = target.hyp ? warpDepth(cT[k], target.curvature) : cT[k];
                    sN++;
                }
                stN[idx] = sN;
            }
        }
    }
    private static void drawBand(Target target, int y0, int y1) {
        for (int i = 0; i < projectedCount; i++) {
            int o = i * STRIDE;
            int minY = (int) projected[o + MIN_Y], maxY = (int) projected[o + MAX_Y];
            int by0 = Math.max(minY, y0), by1 = Math.min(maxY, y1 - 1);
            if (by0 > by1) continue;
            drawTriangleRows(target, o, by0, by1);
        }
    }
    private static void drawTriangleRows(Target target, int o, int by0, int by1) {
        double sx0 = projected[o + SX], sx1 = projected[o + SX + 1], sx2 = projected[o + SX + 2];
        double sy0 = projected[o + SY], sy1 = projected[o + SY + 1], sy2 = projected[o + SY + 2];
        double inv = projected[o + INV];
        double a0 = (sy1 - sy2) * inv, b0 = (sx2 - sx1) * inv, c0 = (-(sy1 - sy2) * sx2 - (sx2 - sx1) * sy2) * inv;
        double a1 = (sy2 - sy0) * inv, b1 = (sx0 - sx2) * inv, c1 = (-(sy2 - sy0) * sx2 - (sx0 - sx2) * sy2) * inv;
        int minX = (int) projected[o + MIN_X], maxX = (int) projected[o + MAX_X];
        int tex = (int) projected[o + TEX];
        boolean wall = projected[o + WALL_FLAG] != 0;
        double shade = projected[o + SHADE];
        int tint = (int) projected[o + TINT];
        int ta = (int) projected[o + APER];
        double iz0 = projected[o + IZ], iz1 = projected[o + IZ + 1], iz2 = projected[o + IZ + 2];
        double sz0 = projected[o + SZ], sz1 = projected[o + SZ + 1], sz2 = projected[o + SZ + 2];
        double tz0 = projected[o + TZ], tz1 = projected[o + TZ + 1], tz2 = projected[o + TZ + 2];

        for (int py = by0; py <= by1; py++) {
            double cy = py + 0.5;
            int rowBase = py * target.w;
            for (int px = minX; px <= maxX; px++) {
                double cx = px + 0.5;
                double w0 = a0 * cx + b0 * cy + c0;
                double w1 = a1 * cx + b1 * cy + c1;
                double w2 = 1.0 - w0 - w1;
                if (w0 < -1e-9 || w1 < -1e-9 || w2 < -1e-9) continue;
                double invZ = w0 * iz0 + w1 * iz1 + w2 * iz2;
                int idx = rowBase + px;
                if (invZ <= depth[idx]) continue;
                double z = 1.0 / invZ;
                if (topAt(idx, z) != ta) continue;
                depth[idx] = invZ;
                double s = (w0 * sz0 + w1 * sz1 + w2 * sz2) * z;
                double tt = (w0 * tz0 + w1 * tz1 + w2 * tz2) * z;
                int color = sample(target, tex, wall, s, tt);
                if (tint != 0) color = tint(color, tint);
                target.out[idx] = shadePixel(target, px, py, z, ta, color, shade);
            }
        }
    }
    private static int shadePixel(Target target, int px, int py, double z, int ta, int color, double shade) {
        double a = (px + 0.5 - target.w / 2.0) / target.focal, v = (target.h / 2.0 - (py + 0.5)) / target.focal;
        double flat = target.hyp ? unwarpDepth(z, target.curvature) : z;
        double x = target.eye[0] + flat * (target.f[0] + a * target.r[0] + v * target.u[0]);
        double y = target.eye[1] + flat * (target.f[1] + a * target.r[1] + v * target.u[1]);
        double zz = target.eye[2] + flat * (target.f[2] + a * target.r[2] + v * target.u[2]);
        boolean lit = ta < 0
                ? !shadowed(target.layers[0], x, y, zz, target.torch[0], target.torch[1], target.torch[2])
                : throughChain(target, ta, x, y, zz);
        double dx = x - target.torch[0], dy = y - target.torch[1], dz = zz - target.torch[2];
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double torch = lit ? Math.clamp(1.0 - dist / TORCH_RANGE, 0.0, 1.0) : 0.0;
        return light(color, shade, torch, z);
    }
    private static boolean throughChain(Target target, int ta, double x, double y, double z) {
        Aperture[] aps = target.aps;
        int[] chain = chainOf(aps, ta);
        int k = chain.length;
        double tx = target.torch[0], ty = target.torch[1], tz = target.torch[2];
        double dx = x - tx, dy = y - ty, dz = z - tz;
        double[] hx = new double[k], hy = new double[k], hz = new double[k];
        double sPrev = 0;
        for (int i = 0; i < k; i++) {
            Aperture aperture = aps[chain[i]];
            double nd = dx * aperture.nx() + dy * aperture.ny();
            if (Math.abs(nd) < 1e-9) return false;
            double s = ((aperture.ex() - tx) * aperture.nx() + (aperture.ey() - ty) * aperture.ny()) / nd;
            if (s <= sPrev || s >= 1) return false;
            double px = tx + s * dx, py = ty + s * dy, pz = tz + s * dz;
            double along = (px - aperture.ex()) * aperture.edx() + (py - aperture.ey()) * aperture.edy();
            if (along < 0 || along > 1 || pz < 0 || pz > 1) return false;
            hx[i] = px;
            hy[i] = py;
            hz[i] = pz;
            sPrev = s;
        }
        if (shadowed(target.layers[0], tx, ty, tz, hx[0], hy[0], hz[0])) return false;
        for (int i = 0; i < k; i++) {
            Aperture aperture = aps[chain[i]];
            double[] p0 = toLinked(aperture, hx[i], hy[i]);
            double[] p1 = i + 1 < k ? toLinked(aperture, hx[i + 1], hy[i + 1]) : toLinked(aperture, x, y);
            double z1 = i + 1 < k ? hz[i + 1] : z;
            if (shadowed(target.layers[aperture.layer()], p1[0], p1[1], z1, p0[0], p0[1], hz[i])) return false;
        }
        return true;
    }
    private static int sample(Target target, int slot, boolean wall, double s, double tt) {
        int texW = target.tw[slot], texH = target.th[slot];
        if (texW <= 0 || texH <= 0 || target.pix[slot] == null) return BASE[slot];
        double fs = s - Math.floor(s), ft = tt - Math.floor(tt);
        int x = Math.min(texW - 1, (int) (fs * texW));
        int y = wall ? Math.min(texH - 1, (int) ((1.0 - ft) * texH)) : Math.min(texH - 1, (int) (ft * texH));
        return target.pix[slot][y * texW + x];
    }
    private static int tint(int color, int tint) {
        int r = (color >> 16) & 255, g = (color >> 8) & 255, b = color & 255;
        int tr = (tint >> 16) & 255, tg = (tint >> 8) & 255, tb = tint & 255;
        r = (int) (r * (1 - 0.45) + tr * 0.45);
        g = (int) (g * (1 - 0.45) + tg * 0.45);
        b = (int) (b * (1 - 0.45) + tb * 0.45);
        return (r << 16) | (g << 8) | b;
    }
    //endregion

    //region GPU Rendering
    static final class Gpu {
        static final int TILE = 16;
        static final int DP_COUNT = 19, IP_COUNT = 14;
        private static final int[] NO_PIXELS = new int[1];
        private static Gpu instance;
        private static boolean disabled;
        private final GpuProgram program = new GpuProgram(KernelSource.load(Raster3DEngine.class, SkyCeiling.GPU_SOURCE), "r3d_render");
        private GpuProgram.Buffer tris, tileStart, tileList, ip, dp, apv, cells, lmeta, out;
        private final GpuProgram.Buffer[] tex = new GpuProgram.Buffer[4];
        private final int[][] texSrc = new int[4][];

        static void barycentric(double[] t, int o) {
            double sx0 = t[o + SX], sx1 = t[o + SX + 1], sx2 = t[o + SX + 2];
            double sy0 = t[o + SY], sy1 = t[o + SY + 1], sy2 = t[o + SY + 2];
            double inv = t[o + INV];
            t[o] = (sy1 - sy2) * inv;
            t[o + 1] = (sx2 - sx1) * inv;
            t[o + 2] = (-(sy1 - sy2) * sx2 - (sx2 - sx1) * sy2) * inv;
            t[o + 3] = (sy2 - sy0) * inv;
            t[o + 4] = (sx0 - sx2) * inv;
            t[o + 5] = (-(sy2 - sy0) * sx2 - (sx0 - sx2) * sy2) * inv;
        }
        static int[] ipValues(Target target, Bins bins) {
            return new int[]{
                    target.w, target.h, bins.tilesX, target.sky ? 1 : 0,
                    target.tw[WALL], target.th[WALL], target.tw[FINISH], target.th[FINISH],
                    target.tw[FLOOR], target.th[FLOOR], target.tw[CEILING], target.th[CEILING],
                    target.aps.length, target.layers.length
            };
        }
        static double[] dpValues(Target target) {
            return new double[]{
                    target.f[0], target.f[1], target.f[2],
                    target.r[0], target.r[1], target.r[2],
                    target.u[0], target.u[1], target.u[2],
                    target.eye[0], target.eye[1], target.eye[2],
                    target.skyTime, target.focal,
                    target.torch[0], target.torch[1], target.torch[2],
                    target.hyp ? 1 : 0, target.curvature
            };
        }
        static double[] apertureValues(Target target) {
            double[] v = new double[Math.max(1, target.aps.length) * AP_STRIDE];
            for (int i = 0; i < target.aps.length; i++) {
                Aperture aperture = target.aps[i];
                int o = i * AP_STRIDE;
                v[o] = aperture.ex(); v[o + 1] = aperture.ey(); v[o + 2] = aperture.xx(); v[o + 3] = aperture.xy();
                v[o + 4] = aperture.c(); v[o + 5] = aperture.s(); v[o + 6] = aperture.nx(); v[o + 7] = aperture.ny();
                v[o + 8] = aperture.edx(); v[o + 9] = aperture.edy(); v[o + 10] = aperture.layer(); v[o + 11] = aperture.parent();
            }
            return v;
        }
        static int[] cellsOf(int[][][] layers) {
            int total = 0;
            for (int[][] m : layers) total += m.length == 0 ? 0 : m.length * m[0].length;
            int[] flat = new int[total];
            int at = 0;
            for (int[][] m : layers) {
                if (m.length == 0) continue;
                int w = m[0].length;
                for (int[] row : m) for (int x = 0; x < w; x++) flat[at++] = x < row.length ? row[x] : 0;
            }
            return flat;
        }
        static int[] layerMeta(int[][][] layers) {
            int[] meta = new int[Math.max(1, layers.length) * 3];
            int offset = 0;
            for (int l = 0; l < layers.length; l++) {
                int[][] m = layers[l];
                int w = m.length == 0 ? 0 : m[0].length;
                meta[3 * l] = offset;
                meta[3 * l + 1] = w;
                meta[3 * l + 2] = m.length;
                offset += m.length * w;
            }
            return meta;
        }

        static boolean tryRender(RenderFrame renderFrame, Target target) { synchronized (GpuRuntime.LOCK) { return tryRenderLocked(renderFrame, target); } }
        private static boolean tryRenderLocked(RenderFrame renderFrame, Target target) {
            if (disabled || GpuRuntime.session() == null) return false;
            try {
                if (instance == null) instance = new Gpu();
                instance.render(renderFrame, target);
                return true;
            }
            catch (RuntimeException | Error exception) {
                disabled = true;
                exception.printStackTrace(System.err);
                GpuRuntime.reportFailure(ID, String.valueOf(exception));
                return false;
            }
        }
        private void render(RenderFrame renderFrame, Target target) {
            final int w = target.w, h = target.h;
            if (renderFrame.renderPixels == null || renderFrame.renderPixels.length < w * h) throw new IllegalStateException("render buffer too small");
            final int count = projectedCount;
            Bins bins = Bins.of(count, w, h);
            int[] cellFlat = cellsOf(target.layers);
            int[] meta = layerMeta(target.layers);

            tris = ensure(tris, 4L * count * STRIDE);
            tileStart = ensure(tileStart, 4L * bins.start.length);
            tileList = ensure(tileList, 4L * bins.list.length);
            ip = ensure(ip, 4L * IP_COUNT);
            dp = ensure(dp, 4L * DP_COUNT);
            apv = ensure(apv, 4L * Math.max(1, target.aps.length) * AP_STRIDE);
            cells = ensure(cells, 4L * cellFlat.length);
            lmeta = ensure(lmeta, 4L * meta.length);
            out = ensure(out, 4L * w * h);

            double[] tri = Arrays.copyOf(projected, count * STRIDE);
            for (int i = 0; i < count; i++) barycentric(tri, i * STRIDE);
            program.write(tris, tri, count * STRIDE);
            program.write(tileStart, bins.start, bins.start.length);
            program.write(tileList, bins.list, bins.list.length);
            program.write(ip, ipValues(target, bins), IP_COUNT);
            program.write(dp, dpValues(target), DP_COUNT);
            double[] ap = apertureValues(target);
            program.write(apv, ap, ap.length);
            program.write(cells, cellFlat, cellFlat.length);
            program.write(lmeta, meta, meta.length);

            for (int i = 0; i < 4; i++) {
                int[] px = target.pix[i] == null ? NO_PIXELS : target.pix[i];
                GpuProgram.Buffer before = tex[i];
                tex[i] = ensure(tex[i], 4L * px.length);
                if (tex[i] != before || px != texSrc[i]) { texSrc[i] = px; program.write(tex[i], px, px.length); }
            }

            program.run(w, h, tris, tileStart, tileList, tex[0], tex[1], tex[2], tex[3], ip, dp, apv, cells, lmeta, out);
            program.read(out, renderFrame.renderPixels, w * h);
        }
        private GpuProgram.Buffer ensure(GpuProgram.Buffer current, long bytes) {
            long need = Math.max(4L, bytes);
            if (current != null && current.bytes() >= need) return current;
            if (current != null) program.release(current);
            return program.buffer(need + need / 2);
        }

        private record Bins(int tilesX, int[] start, int[] list) {
            static Bins of(int count, int w, int h) {
                        int tilesX = (w + TILE - 1) / TILE, tilesY = (h + TILE - 1) / TILE;
                        int tiles = tilesX * tilesY;
                        int[] start = new int[tiles + 1];
                        for (int i = 0; i < count; i++) {
                            int o = i * STRIDE;
                            int tx0 = (int) projected[o + MIN_X] / TILE, tx1 = (int) projected[o + MAX_X] / TILE;
                            int ty0 = (int) projected[o + MIN_Y] / TILE, ty1 = (int) projected[o + MAX_Y] / TILE;
                            for (int ty = ty0; ty <= ty1; ty++)
                                for (int tx = tx0; tx <= tx1; tx++) start[ty * tilesX + tx + 1]++;
                        }
                        for (int t = 0; t < tiles; t++) start[t + 1] += start[t];
                        int[] list = new int[start[tiles]];
                        int[] fill = Arrays.copyOf(start, tiles);
                        for (int i = 0; i < count; i++) {
                            int o = i * STRIDE;
                            int tx0 = (int) projected[o + MIN_X] / TILE, tx1 = (int) projected[o + MAX_X] / TILE;
                            int ty0 = (int) projected[o + MIN_Y] / TILE, ty1 = (int) projected[o + MAX_Y] / TILE;
                            for (int ty = ty0; ty <= ty1; ty++)
                                for (int tx = tx0; tx <= tx1; tx++) list[fill[ty * tilesX + tx]++] = i;
                        }
                        return new Bins(tilesX, start, list);
                    }
                }
    }
    //endregion
}