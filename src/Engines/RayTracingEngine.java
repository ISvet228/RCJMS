package Engines;

import GameViewComponents.*;
import Helpers.*;
import java.awt.*;
import java.util.function.IntConsumer;

public final class RayTracingEngine implements RenderEngine {
    //region Shared helpers
    //region Engine settings
    static volatile int cfgQuality = 1, cfgOutW = 960, cfgOutH = 540;
    static volatile MazeGenerator.GeometryMode cfgGeometry = MazeGenerator.GeometryMode.EUCLIDEAN;
    static volatile double cfgCurvature = HyperbolicMath.DEFAULT_CURVATURE;
    static final SdfCache sdfCache = new SdfCache();
    static void syncConfig(RenderFrame f) {
        cfgQuality = f.quality;
        cfgGeometry = f.geometry;
        cfgCurvature = f.curvature;
        cfgOutW = f.outputWidth;
        cfgOutH = f.outputHeight;
    }
    //endregion

    //region Constants
    static final int MAX_PRIMARY_STEPS = 6000;
    static final int wallBaseColor = 0xECD485;
    static final int ceilingBaseColor = 0x816E1E;
    static final int floorBaseColor = 0xD3AF63;
    static final int finishBaseColor = 0x33FF66;
    private static final double HYP_PLANE_LENGTH = Math.tan(Math.toRadians(140.0) / 2.0);
    private static final int SDF_SUB = 8, SDF_WINDOW = 4;
    //endregion

    //region Hyperbolic render
    private static double hypFloorDistance(double z0, double slope, double L) {
        if (slope >= 0 || z0 <= 1e-9) return Double.POSITIVE_INFINITY;
        return L * HyperbolicMath.inverseHyperbolicDistance(z0 / -slope, cfgCurvature);
    }
    private static double hypCeilDistance(double z0, double slope, double L) {
        if (slope <= 0 || z0 >= 1.0 - 1e-9) return Double.POSITIVE_INFINITY;
        return L * HyperbolicMath.inverseHyperbolicDistance((1.0 - z0) / slope, cfgCurvature);
    }
    static double[] hypPixelRay(double cx, double py, double dirX, double dirY, double focal, double horizon) {
        double rdX = dirX - dirY * HYP_PLANE_LENGTH * cx, rdY = dirY + dirX * HYP_PLANE_LENGTH * cx;
        double L = Math.sqrt(rdX * rdX + rdY * rdY);
        return new double[]{rdX / L, rdY / L, L, -(py - horizon) / focal};
    }
    static TraceHit traceHyp(RenderFrame fr, double ox, double oy, double z0, double ux, double uy, double L, double slope, TraceHit result, int maxSteps) {
        result.reset();
        final int[][] m = fr.map;
        final int mapWidth = m[0].length, mapHeight = m.length;
        final double invL = 1.0 / L;
        final double floorD = hypFloorDistance(z0, slope, L), ceilD = hypCeilDistance(z0, slope, L);
        final double planeLimit = Math.min(floorD, ceilD);

        int mapX = (int) Math.floor(ox), mapY = (int) Math.floor(oy);
        double deltaX = ux == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / ux);
        double deltaY = uy == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / uy);
        double sideX = (ux < 0 ? ox - mapX : mapX + 1.0 - ox) * deltaX;
        double sideY = (uy < 0 ? oy - mapY : mapY + 1.0 - oy) * deltaY;
        int stepX = ux < 0 ? -1 : 1, stepY = uy < 0 ? -1 : 1;

        double wallD = Double.POSITIVE_INFINITY;
        int wallType = 0, wallSide = 0;
        for (int steps = 0; steps < maxSteps; steps++) {
            double enter;
            int side;
            if (sideX < sideY) { enter = sideX; sideX += deltaX; mapX += stepX; side = 0; }
            else { enter = sideY; sideY += deltaY; mapY += stepY; side = 1; }
            if (enter >= planeLimit) break;
            if (mapX < 0 || mapY < 0 || mapX >= mapWidth || mapY >= mapHeight) break;
            int type = m[mapY][mapX];
            if (type != 1 && type != 2) continue;
            double z = z0 + slope * HyperbolicMath.hyperbolicDistance(enter * invL, cfgCurvature);
            if (z >= 0.0 && z <= wallHeightForType(type)) { wallD = enter; wallType = type; wallSide = side; break; }
        }

        result.map = m;
        result.portals = null;
        result.hyp = true;
        result.hypSlope = slope;
        double dzdd;
        if (wallD < planeLimit) {
            double z = z0 + slope * HyperbolicMath.hyperbolicDistance(wallD * invL, cfgCurvature);
            dzdd = slope * Math.cosh(wallD * invL / cfgCurvature) * invL;
            double len = Math.sqrt(1.0 + dzdd * dzdd);
            result.hit = true;
            result.distance = HyperbolicMath.hyperbolicDistance(wallD * invL, cfgCurvature);
            result.x = ox + ux * wallD; result.y = oy + uy * wallD;
            result.z = Math.clamp(z, 0.0, wallHeightForType(wallType));
            result.type = wallType; result.side = wallSide;
            result.dirX = ux / len; result.dirY = uy / len; result.dirZ = dzdd / len;
            double wallU = wallSide == 0 ? result.y : result.x;
            wallU -= Math.floor(wallU);
            if ((wallSide == 0 && ux < 0) || (wallSide == 1 && uy > 0)) wallU = 1.0 - wallU;
            result.wallU = Math.clamp(wallU, 0.0, 0.999999);
            result.wallV = Math.clamp(result.z / wallHeightForType(wallType), 0.0, 0.999999);
        }
        else if (Double.isFinite(planeLimit)) {
            boolean floor = floorD <= ceilD;
            dzdd = slope * Math.cosh(planeLimit * invL / cfgCurvature) * invL;
            double len = Math.sqrt(1.0 + dzdd * dzdd);
            result.hit = true;
            result.isPlane = true;
            result.type = floor ? -1 : 1;
            result.z = floor ? 0.0 : 1.0;
            result.distance = HyperbolicMath.hyperbolicDistance(planeLimit * invL, cfgCurvature);
            result.x = ox + ux * planeLimit; result.y = oy + uy * planeLimit;
            result.dirX = ux / len; result.dirY = uy / len; result.dirZ = dzdd / len;
        }
        return result;
    }
    //endregion

    //region Tracing
    static TraceHit traceRay(RenderFrame fr, double ox, double oy, double oz, double rx, double ry, double rz, int[][] startMap, PortalData startPortals, TraceHit result, int maxSteps) {
        result.reset();
        final int mapWidth = startMap[0].length, mapHeight = startMap.length;

        double totalDistance = 0;
        int hops = 0, steps = 0;
        double wallT = Double.POSITIVE_INFINITY;
        int wallType = 0, wallSide = 0;
        int[][] rayMap = startMap;
        PortalData rayPortals = startPortals;

        segments:
        while (true) {
            double horizontal = Math.sqrt(rx * rx + ry * ry);
            if (horizontal <= 1e-9) break;
            double planeHere = rz < -1e-9 ? -oz / rz : rz > 1e-9 ? (1.0 - oz) / rz : Double.POSITIVE_INFINITY;

            double dx = rx / horizontal, dy = ry / horizontal;
            int mapX = (int) Math.floor(ox), mapY = (int) Math.floor(oy);
            double deltaX = Math.abs(1.0 / dx), deltaY = Math.abs(1.0 / dy);
            double sideX = (dx < 0 ? ox - mapX : mapX + 1.0 - ox) * deltaX;
            double sideY = (dy < 0 ? oy - mapY : mapY + 1.0 - oy) * deltaY;

            while (mapX >= 0 && mapY >= 0 && mapX < mapWidth && mapY < mapHeight) {
                double horizontalT;
                int side;
                if (sideX < sideY) {
                    horizontalT = sideX;
                    sideX += deltaX;
                    mapX += dx < 0 ? -1 : 1;
                    side = 0;
                }
                else {
                    horizontalT = sideY;
                    sideY += deltaY;
                    mapY += dy < 0 ? -1 : 1;
                    side = 1;
                }
                if (mapX < 0 || mapY < 0 || mapX >= mapWidth || mapY >= mapHeight) break;

                double candidateT = horizontalT / horizontal;
                if (candidateT >= planeHere || ++steps > maxSteps) break segments;

                int type = rayMap[mapY][mapX];
                if (type == MazeGenerator.PORTAL && hops < 8) {
                    PortalData.Portal portal = rayPortals.get(mapX, mapY);
                    if (portal != null && PortalData.entersFromCorrectSide(portal, rx, ry)) {
                        double candidateZ = oz + rz * candidateT;
                        if (candidateZ >= 0.0 && candidateZ <= 1.0) {
                            double hx = ox + rx * candidateT, hy = oy + ry * candidateT;
                            totalDistance += candidateT;
                            double[] w = PortalData.warp(portal, hx, hy, rx, ry);
                            ox = w[0]; oy = w[1]; rx = w[2]; ry = w[3];
                            if (fr.map3D != null && portal.linkedFloor >= 0 && portal.linkedFloor < fr.map3D.length) {
                                rayMap = fr.map3D[portal.linkedFloor];
                                rayPortals = fr.floorPortalsAll[portal.linkedFloor];
                            }
                            oz = candidateZ;
                            hops++;
                            continue segments;
                        }
                    }
                }

                if (type != 1 && type != 2) continue;

                double candidateZ = oz + rz * candidateT;
                if (candidateZ >= 0.0 && candidateZ <= wallHeightForType(type)) {
                    wallT = candidateT;
                    wallType = type;
                    wallSide = side;
                    break segments;
                }
            }
            break;
        }

        result.map = rayMap;
        result.portals = rayPortals;

        double planeT = Double.POSITIVE_INFINITY;
        int planeType = 0;
        if (rz < -1e-9) { planeT = -oz / rz; planeType = -1; }
        else if (rz > 1e-9) { planeT = (1.0 - oz) / rz; planeType = 1; }

        if (wallT < planeT && Double.isFinite(wallT)) {
            double wallHeight = wallHeightForType(wallType);
            result.hit = true;
            result.distance = totalDistance + wallT;
            result.x = ox + rx * wallT;
            result.y = oy + ry * wallT;
            result.z = Math.clamp(oz + rz * wallT, 0.0, wallHeight);
            result.type = wallType;
            result.side = wallSide;
            result.dirX = rx; result.dirY = ry; result.dirZ = rz;

            double wallU = wallSide == 0 ? result.y : result.x;
            wallU -= Math.floor(wallU);
            if ((wallSide == 0 && rx < 0) || (wallSide == 1 && ry > 0)) wallU = 1.0 - wallU;
            result.wallU = Math.clamp(wallU, 0.0, 0.999999);
            result.wallV = Math.clamp(result.z / wallHeight, 0.0, 0.999999);
        }
        else if (Double.isFinite(planeT) && planeT > 0) {
            result.hit = true;
            result.distance = totalDistance + planeT;
            result.x = ox + rx * planeT;
            result.y = oy + ry * planeT;
            result.z = planeType < 0 ? 0 : 1;
            result.type = planeType;
            result.side = 0;
            result.dirX = rx; result.dirY = ry; result.dirZ = rz;
            result.isPlane = true;
        }
        return result;
    }
    static double smooth(double t) { t = Math.clamp(t, 0.0, 1.0); return t * t * (3.0 - 2.0 * t); }
    static double glintPow(double x, int exp) {
        double x2 = x * x, x4 = x2 * x2, x8 = x4 * x4, x16 = x8 * x8;
        if (exp == 16) return x16;
        if (exp == 24) return x16 * x8;
        return x16 * x8 * x4 * x2;
    }
    static double toneCurve(double v) { return v <= 0.7 ? v : 0.7 + 0.3 * (1.0 - Math.exp(-(v - 0.7) / 0.3)); }
    static double wallOcclusion(double z, double height) {
        double ao = 1.0 - 0.45 * (1.0 - smooth(z / 0.22));
        ao *= 1.0 - 0.25 * (1.0 - smooth((height - z) / 0.14));
        return ao;
    }
    static int mixColor(int a, int b, double amount) {
        amount = Math.clamp(amount, 0, 1);
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return ((int) (ar * (1 - amount) + br * amount) << 16) | ((int) (ag * (1 - amount) + bg * amount) << 8) | (int) (ab * (1 - amount) + bb * amount);
    }
    //endregion

    //region Textures and shading
    private static int tintColor(int color, int tint, double amount) {
        int r = (color >> 16) & 255, g = (color >> 8) & 255, b = color & 255;
        int tr = (tint >> 16) & 255, tg = (tint >> 8) & 255, tb = tint & 255;
        r = (int)(r * (1 - amount) + tr * amount); g = (int)(g * (1 - amount) + tg * amount); b = (int)(b * (1 - amount) + tb * amount);
        return (r << 16) | (g << 8) | b;
    }
    static int sampleTexture(RuntimeTexture texture, double worldX, double worldY, int baseColor) {
        int width = texture.width(), height = texture.height();
        if (width <= 0 || height <= 0) return baseColor;
        double fracX = worldX - Math.floor(worldX);
        double fracY = worldY - Math.floor(worldY);
        int tx = Math.min((int)(fracX * width), width - 1);
        int ty = Math.min((int)(fracY * height), height - 1);
        return texture.pixels()[ty * width + tx];
    }
    static int sampleWallTexture(RuntimeTexture texture, TraceHit hit) {
        if (texture.width() <= 0 || texture.height() <= 0) return hit.type == 2 ? finishBaseColor : wallBaseColor;
        int tx = Math.min((int) (hit.wallU * texture.width()), texture.width() - 1);
        int ty = Math.min((int) ((1.0 - hit.wallV) * texture.height()), texture.height() - 1);
        return texture.pixels()[ty * texture.width() + Math.max(0, tx)];
    }
    static int tintStairs(int color, double worldX, double worldY, int[][] floorMap) {
        int fx = (int) Math.floor(worldX), fy = (int) Math.floor(worldY);
        if (fx < 0 || fy < 0 || fy >= floorMap.length || fx >= floorMap[0].length) return color;
        int cell = floorMap[fy][fx];
        if (cell == 3) return tintColor(color, 0x3399FF, 0.45);
        if (cell == 4) return tintColor(color, 0xFFAA33, 0.45);
        return color;
    }
    //endregion

    //region Utilities
    static boolean is3D(RenderFrame fr) { return fr.map3D != null && fr.map3D.length > 1; }
    static double wallHeightForType(int type) { return type == 2 ? 0.5 : 1.0; }
    static void copyRenderBuffer(RenderFrame fr) {
        if (fr.renderWidth == fr.bufferedImage.getWidth() && fr.renderHeight == fr.bufferedImage.getHeight()) {
            System.arraycopy(fr.renderPixels, 0, fr.pixels, 0, fr.pixels.length);
        } else {
            Graphics2D g2d = fr.bufferedImage.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g2d.drawImage(fr.renderImage, 0, 0, cfgOutW, cfgOutH, null);
            g2d.dispose();
        }
    }
    static void parallelRange(RenderFrame fr, int start, int end, IntConsumer task) {
        int range = end - start;
        if (range <= 0) return;
        fr.renderWorkers.execute(start, end, task);
    }
    //endregion

    //region Nested Types
    static final int SDF_TILE = 256;
    public static final class SdfCache {
        static final long BUDGET_BYTES = 256L << 20;
        static final int MAX_FIELDS = 32;
        final java.util.concurrent.ConcurrentHashMap<int[][], SDFField> fields = new java.util.concurrent.ConcurrentHashMap<>();
        private long bytes;

        SDFField fieldFor(int[][] m) {
            SDFField field = fields.get(m);
            if (field != null) return field;
            synchronized (this) {
                field = fields.get(m);
                if (field == null) {
                    if (fields.size() > MAX_FIELDS) clear();
                    field = new SDFField(this, m);
                    fields.put(m, field);
                }
                return field;
            }
        }
        synchronized void clear() {
            for (SDFField f : fields.values()) f.dropTiles();
            fields.clear();
            bytes = 0;
        }
        synchronized void charge(long extraBytes) {
            bytes += extraBytes;
            if (bytes > BUDGET_BYTES) {
                for (SDFField f : fields.values()) f.dropTiles();
                bytes = 0;
            }
        }
    }
    public static final class SDFField {
        private final SdfCache owner;
        private final int[][] m;
        private final int mw, mh;
        final int cols, rows, tilesX, tilesY;
        final boolean hasPortals, hasFinish;
        private final java.util.concurrent.atomic.AtomicReferenceArray<double[]> wallTiles, finishTiles;

        SDFField(SdfCache owner, int[][] m) {
            this.owner = owner;
            this.m = m;
            mh = m.length;
            mw = m[0].length;
            cols = mw * SDF_SUB + 1;
            rows = mh * SDF_SUB + 1;
            tilesX = (cols + SDF_TILE - 1) / SDF_TILE;
            tilesY = (rows + SDF_TILE - 1) / SDF_TILE;
            wallTiles = new java.util.concurrent.atomic.AtomicReferenceArray<>(tilesX * tilesY);
            finishTiles = new java.util.concurrent.atomic.AtomicReferenceArray<>(tilesX * tilesY);
            boolean portals = false, finish = false;
            for (int y = 0; y < mh; y++)
                for (int x = 0; x < mw; x++) {
                    int t = m[y][x];
                    if (t == 2) finish = true;
                    else if (t == MazeGenerator.PORTAL) portals = true;
                }
            hasPortals = portals;
            hasFinish = finish;
        }

        double wallAt(double x, double y) { return sample(false, x, y); }
        double finishAt(double x, double y) { return hasFinish ? sample(true, x, y) : 0.0; }
        private double sample(boolean finish, double x, double y) {
            double gx = Math.clamp(x * SDF_SUB, 0, cols - 1.000001), gy = Math.clamp(y * SDF_SUB, 0, rows - 1.000001);
            int i0 = (int) gx, j0 = (int) gy;
            int i1 = Math.min(i0 + 1, cols - 1), j1 = Math.min(j0 + 1, rows - 1);
            double fx = gx - i0, fy = gy - j0;
            double a = value(finish, i0, j0), b = value(finish, i1, j0), c = value(finish, i0, j1), d = value(finish, i1, j1);
            return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy;
        }
        double value(boolean finish, int i, int j) {
            int tx = i / SDF_TILE, ty = j / SDF_TILE;
            int index = ty * tilesX + tx;
            double[] tile = (finish ? finishTiles : wallTiles).get(index);
            if (tile == null) tile = buildTile(finish, tx, ty);
            return tile[(j - ty * SDF_TILE) * SDF_TILE + (i - tx * SDF_TILE)];
        }

        private double[] buildTile(boolean finish, int tx, int ty) {
            java.util.concurrent.atomic.AtomicReferenceArray<double[]> tiles = finish ? finishTiles : wallTiles;
            int index = ty * tilesX + tx;
            synchronized (owner) {
                double[] tile = tiles.get(index);
                if (tile != null) return tile;
                tile = computeTile(finish, tx * SDF_TILE, ty * SDF_TILE);
                tiles.set(index, tile);
                owner.charge(8L * tile.length);
                return tile;
            }
        }

        void dropTiles() {
            for (int k = 0; k < tilesX * tilesY; k++) {
                wallTiles.set(k, null);
                finishTiles.set(k, null);
            }
        }

        private boolean isSolid(boolean finish, int y, int x) {
            int t = m[y][x];
            return finish ? t == 2 : t == 1;
        }
        private double[] computeTile(boolean finish, int i0, int j0) {
            final double none = Double.POSITIVE_INFINITY;
            final int iEnd = Math.min(cols, i0 + SDF_TILE), jEnd = Math.min(rows, j0 + SDF_TILE);
            final int yLo = Math.max(0, (int) Math.floor(j0 / (double) SDF_SUB) - SDF_WINDOW);
            final int yHi = Math.min(mh - 1, (int) Math.floor((jEnd - 1) / (double) SDF_SUB) + SDF_WINDOW);
            final int width = iEnd - i0;
            double[] gaps = new double[(yHi - yLo + 1) * width];
            for (int i = i0; i < iEnd; i++) {
                double px = i / (double) SDF_SUB;
                int cx = (int) Math.floor(px);
                int x0 = Math.max(0, cx - SDF_WINDOW), x1 = Math.min(mw - 1, cx + SDF_WINDOW);
                for (int yy = yLo; yy <= yHi; yy++) {
                    double best = none;
                    for (int xx = x0; xx <= x1; xx++) {
                        if (!isSolid(finish, yy, xx)) continue;
                        double dx = Math.max(Math.max(xx - px, px - (xx + 1)), 0);
                        double sq = dx * dx;
                        if (sq < best) best = sq;
                    }
                    gaps[(yy - yLo) * width + (i - i0)] = best;
                }
            }
            double[] tile = new double[SDF_TILE * SDF_TILE];
            for (int j = j0; j < jEnd; j++) {
                double py = j / (double) SDF_SUB;
                int cy = (int) Math.floor(py);
                int y0 = Math.max(0, cy - SDF_WINDOW), y1 = Math.min(mh - 1, cy + SDF_WINDOW);
                for (int i = i0; i < iEnd; i++) {
                    double best = none;
                    for (int yy = y0; yy <= y1; yy++) {
                        double gap = gaps[(yy - yLo) * width + (i - i0)];
                        if (gap == none) continue;
                        double dy = Math.max(Math.max(yy - py, py - (yy + 1)), 0);
                        double sq = dy * dy + gap;
                        if (sq < best) best = sq;
                    }
                    tile[(j - j0) * SDF_TILE + (i - i0)] = best == none ? SDF_WINDOW : Math.sqrt(best);
                }
            }
            return tile;
        }
    }
    static void prepareSdfFields(RenderFrame fr) {
        if (fr.map3D != null) for (int[][] floor : fr.map3D) sdfFieldFor(floor);
        else sdfFieldFor(fr.map);
    }
    static SDFField sdfFieldFor(int[][] m) {
        return sdfCache.fieldFor(m);
    }
    public static final class TraceHit {
        void reset() {
            x = y = z = distance = wallU = wallV = dirX = dirY = dirZ = hypSlope = 0; type = side = 0;
            hit = isPlane = hyp = false; map = null; portals = null;
            curved = false; nrmX = nrmY = 0;
        }
        double x, y, z, distance;
        int type, side;
        double wallU, wallV;
        double dirX, dirY, dirZ;
        boolean hit, isPlane, hyp;
        double hypSlope;
        int[][] map;
        PortalData portals;
        boolean curved;
        double nrmX, nrmY;
    }
    //endregion
    //endregion
    //region Module
    private RenderFrame fr;
    @Override public String id() { return "raytracing"; }
    @Override public String displayName() { return "Ray Tracing"; }
    @Override public int order() { return 1; }
    @Override public String hintKey() { return "mm.engine_rt"; }
    @Override public String qualityHintKey() { return "mm.quality_rt"; }
    @Override public boolean hasHyperbolicGeometry() { return true; }
    @Override public void render(RenderFrame frame) { fr = frame; syncConfig(frame); renderRayTracing(); }
    @Override public void prepare(RenderFrame frame) { prepareSdfFields(frame); }
    //endregion
    private static final int[] REFLECTION_STEPS = {6, 16, 48};
    private static final int[] REFLECTION_DEPTH = {1, 1, 2};
    private static final double FLASH_RANGE = 3.0;
    private static final double[] AMBIENT_LIGHT = {0.10, 0.115, 0.17};
    private static final double[] FILL_LIGHT = {0.30, 0.28, 0.25};
    private static final double FILL_DIR_X = 0.46, FILL_DIR_Y = 0.36, FILL_DIR_Z = 0.85;
    private static final double[] FLASH_LIGHT = {1.0, 0.88, 0.70};
    private static final double FLASH_STRENGTH = 0.95;
    private static final double FLASH_RANGE_SQ_INV = 1.0 / (FLASH_RANGE * FLASH_RANGE);
    private static final int TONE_STEPS = 2048;
    private static final double TONE_MAX = 3.0;
    private static final double[] TONE_LUT = buildToneLut();
    private static final double glossiness = .75;

    private void renderRayTracing() {
        final int width = fr.renderWidth, height = fr.renderHeight;
        final int quality = Math.clamp(cfgQuality, 0, 2);
        final int reflectionSteps = REFLECTION_STEPS[quality];
        final int reflectionDepth = REFLECTION_DEPTH[quality];
        final int[] rtPixels = fr.renderPixels;
        final double tanHalfFov = Math.tan(Math.toRadians(60.0) * 0.5);
        final double verticalTan = tanHalfFov / ((double) width / height);
        final double dirX = Math.cos(fr.cameraAngle), dirY = Math.sin(fr.cameraAngle);
        final double cp = Math.cos(fr.cameraPitch), sp = Math.sin(fr.cameraPitch);
        final boolean hyperbolic = cfgGeometry == MazeGenerator.GeometryMode.WRONG;
        final double horizonH = height / 2.0 + fr.cameraPitch * height / 2.0;
        prepareSdfFields(fr);

        parallelRange(fr, 0, height, y -> {
            TraceHit primary = new TraceHit();
            TraceHit[] reflectionScratch = {new TraceHit(), new TraceHit()};
            for (int x = 0; x < width; x++) {
                if (hyperbolic) {
                    double[] pr = hypPixelRay((x + 0.5) / width * 2.0 - 1.0, y + 0.5, dirX, dirY, height, horizonH);
                    TraceHit hitH = traceHyp(fr, fr.playerX, fr.playerY, fr.cameraHeight, pr[0], pr[1], pr[2], pr[3], primary, MAX_PRIMARY_STEPS);
                    rtPixels[y * width + x] = hitH.hit
                            ? shadeSurface(hitH, -hitH.dirX, -hitH.dirY, -hitH.dirZ + 0.18, 0, reflectionDepth, reflectionSteps, reflectionScratch)
                            : ceilingBaseColor;
                    continue;
                }
                double sx = ((x + 0.5) / width * 2.0 - 1.0) * tanHalfFov;
                double sy = (1.0 - (y + 0.5) / height * 2.0) * verticalTan;
                double rx = dirX * cp + (-dirY) * sx + (-dirX * sp) * sy;
                double ry = dirY * cp + dirX * sx + (-dirY * sp) * sy;
                double rz = sp + cp * sy;
                double inv = 1.0 / Math.sqrt(rx * rx + ry * ry + rz * rz);
                rx *= inv; ry *= inv; rz *= inv;
                TraceHit hit = traceRay(fr, fr.playerX, fr.playerY, fr.cameraHeight, rx, ry, rz, fr.map, fr.portals, primary, MAX_PRIMARY_STEPS);
                if (!hit.hit) { rtPixels[y * width + x] = ceilingBaseColor; continue; }
                rtPixels[y * width + x] = shadeSurface(hit, -hit.dirX, -hit.dirY, -hit.dirZ + 0.18, 0, reflectionDepth, reflectionSteps, reflectionScratch);
            }
        });
        copyRenderBuffer(fr);
    }

    private static double[] buildToneLut() {
        double[] lut = new double[TONE_STEPS + 1];
        for (int i = 0; i <= TONE_STEPS; i++) lut[i] = toneCurve(i * TONE_MAX / TONE_STEPS);
        return lut;
    }
    private static double planeOcclusion(SDFField field, double x, double y) {
        double d = field.wallAt(x, y);
        if (field.hasFinish) d = Math.min(d, field.finishAt(x, y));
        final double nearReach = 0.30, nearStrength = 0.6;
        final double farReach = 0.90, farStrength = 0.2;
        double ao = 1.0 - nearStrength * (1.0 - smooth(d / nearReach));
        ao *= 1.0 - farStrength * (1.0 - smooth(d / farReach));
        return ao;
    }
    private static int toneToByte(double light) {
        if (!(light > 0)) return 0;
        if (light >= TONE_MAX) return 255;
        return (int) (TONE_LUT[(int) (light * TONE_STEPS / TONE_MAX)] * 255.0 + 0.5);
    }
    private int shadeSurface(TraceHit hit, double lx, double ly, double lz, double pathBefore, int depth, int steps, TraceHit[] scratch) {
        if (!hit.hit) return ceilingBaseColor;
        double llen = Math.sqrt(lx * lx + ly * ly + lz * lz);
        if (llen > 1e-9) { lx /= llen; ly /= llen; lz /= llen; }

        int base;
        double nx, ny, nz, ao, specStrength, reflectivity;
        int shininess;
        boolean emissive = false;
        if (hit.isPlane) {
            boolean floor = hit.type == -1;
            base = sampleTexture(floor ? fr.floorTexture : fr.ceilingTexture, hit.x, hit.y, floor ? floorBaseColor : ceilingBaseColor);
            if (floor && is3D(fr)) base = tintStairs(base, hit.x, hit.y, hit.map);
            nx = 0; ny = 0; nz = floor ? 1 : -1;
            ao = planeOcclusion(sdfFieldFor(hit.map), hit.x, hit.y);
            shininess = floor ? 30 : 16;
            specStrength = floor ? 0.16 : 0.10;
            reflectivity = floor ? 0.12 : 0.06;
        }
        else {
            boolean finish = hit.type == 2;
            base = sampleWallTexture(finish ? fr.finishTexture : fr.wallTexture, hit);
            nx = hit.side == 0 ? (hit.dirX > 0 ? -1 : 1) : 0;
            ny = hit.side == 1 ? (hit.dirY > 0 ? -1 : 1) : 0;
            nz = 0;
            emissive = finish;
            ao = finish ? 1.0 : wallOcclusion(hit.z, wallHeightForType(hit.type));
            shininess = 24;
            specStrength = 0.10;
            reflectivity = finish ? 0.08 : 0.24;
        }

        double dist = pathBefore + hit.distance;
        double att = 1.0 / (1.0 + dist * dist * FLASH_RANGE_SQ_INV);
        double ndl = Math.max(0, nx * lx + ny * ly + nz * lz);
        double ndf = Math.max(0, nx * FILL_DIR_X + ny * FILL_DIR_Y + nz * FILL_DIR_Z);
        double flash = FLASH_STRENGTH * att * ndl;

        double glint = 0;
        if (ndl > 0) {
            double hx = lx - hit.dirX, hy = ly - hit.dirY, hz = lz - hit.dirZ;
            double hlen = Math.sqrt(hx * hx + hy * hy + hz * hz);
            if (hlen > 1e-9) {
                glint = specStrength * att * glossiness * glintPow(Math.max(0, (nx * hx + ny * hy + nz * hz) / hlen), shininess);
            }
        }

        double fillMix = 0.6 + 0.4 * ao;
        double lightR = AMBIENT_LIGHT[0] * ao + FILL_LIGHT[0] * ndf * ao + FLASH_LIGHT[0] * flash * fillMix + FLASH_LIGHT[0] * glint;
        double lightG = AMBIENT_LIGHT[1] * ao + FILL_LIGHT[1] * ndf * ao + FLASH_LIGHT[1] * flash * fillMix + FLASH_LIGHT[1] * glint;
        double lightB = AMBIENT_LIGHT[2] * ao + FILL_LIGHT[2] * ndf * ao + FLASH_LIGHT[2] * flash * fillMix + FLASH_LIGHT[2] * glint;
        if (emissive) { lightR = Math.max(lightR, 0.7); lightG = Math.max(lightG, 0.7); lightB = Math.max(lightB, 0.7); }

        int br = (base >> 16) & 255, bg = (base >> 8) & 255, bb = base & 255;
        int lit = (toneToByte(br / 255.0 * lightR) << 16) | (toneToByte(bg / 255.0 * lightG) << 8) | toneToByte(bb / 255.0 * lightB);

        double level = (lightR + lightG + lightB) / 3.0;
        reflectivity *= glossiness * Math.clamp(level, 0.15, 1.0);
        if (depth <= 0 || reflectivity < 0.012) return lit;

        double dot = hit.dirX * nx + hit.dirY * ny + hit.dirZ * nz;
        double rrx = hit.dirX - 2.0 * dot * nx, rry = hit.dirY - 2.0 * dot * ny, rrz = hit.dirZ - 2.0 * dot * nz;
        double rlen = Math.sqrt(rrx * rrx + rry * rry + rrz * rrz);
        if (rlen < 1e-9) return lit;
        rrx /= rlen; rry /= rlen; rrz /= rlen;

        TraceHit reflected = scratch[depth - 1];
        if (hit.hyp) {
            double hl = Math.sqrt(rrx * rrx + rry * rry);
            if (hl < 1e-9) return lit;
            double reflectedSlope = hit.isPlane ? -hit.hypSlope : hit.hypSlope;
            traceHyp(fr, hit.x + nx * 0.004, hit.y + ny * 0.004, hit.z + nz * 0.004, rrx / hl, rry / hl, 1.0, reflectedSlope, reflected, steps);
        }
        else traceRay(fr, hit.x + nx * 0.004, hit.y + ny * 0.004, hit.z + nz * 0.004, rrx, rry, rrz, hit.map, hit.portals, reflected, steps);
        if (!reflected.hit) return lit;
        int reflectedColor = shadeSurface(reflected, -rrx, -rry, -rrz + 0.18, dist, depth - 1, steps, scratch);
        return mixColor(lit, reflectedColor, reflectivity);
    }
}
