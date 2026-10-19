package Engines;

import GameViewComponents.*;
import Helpers.*;
import java.awt.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntConsumer;

public final class MoriartyEngine implements RenderEngine {
    //region Shared helpers (kept inside this engine)

    //region Engine settings (copied from the RenderFrame before every render)
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
    static void traceHyp(RenderFrame fr, double ox, double oy, double z0, double ux, double uy, double L, double slope, TraceHit result, int maxSteps) {
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
    }
    //endregion

    //region Tracing
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
    static int planeColor(RuntimeTexture texture, double worldX, double worldY, int baseColor) {
        return sampleTexture(texture, worldX, worldY, baseColor);
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
        double[] wallGrid() { return fullGrid(false); }
        double[] finishGrid() { return hasFinish ? fullGrid(true) : new double[cols * rows]; }

        private double[] fullGrid(boolean finish) {
            double[] grid = new double[cols * rows];
            for (int j = 0; j < rows; j++)
                for (int i = 0; i < cols; i++) grid[j * cols + i] = value(finish, i, j);
            return grid;
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
    @Override public String id() { return "moriarty"; }
    @Override public String displayName() { return "Moriarty Engine"; }
    @Override public int order() { return 4; }
    @Override public String hintKey() { return "mm.engine_moriarty"; }
    @Override public String qualityHintKey() { return "mm.quality_moriarty"; }
    @Override public boolean hasHyperbolicGeometry() { return false; }
    @Override public void render(RenderFrame frame) {
        fr = frame; syncConfig(frame);
        renderMoriarty();
    }
    private static final int MAX_MAP_CELLS = 201 * 201;
    private static final int MAX_CACHED_MAPS = 64;
    @Override public boolean supportsMapSize(int width, int height) { return (long) width * height <= MAX_MAP_CELLS; }
    @Override public void prepare(RenderFrame frame) {
        fr = frame; syncConfig(frame);
        prepareMaps();
    }
    //endregion

    //region Tuning
    private static final int[] AO_SAMPLES = {2, 3, 4};
    private static final int[] REFLECTION_DEPTH = {0, 1, 1};
    private static final int[] REFLECTION_STEPS = {12, 32, 64};
    private static final int[] EDGE_GRID = {1, 2, 3};
    private static final int MAX_EDGE_GRID = 3;
    private static final double[] AO_HEIGHTS = {0.08, 0.20, 0.36, 0.60};
    private static final double AO_STRENGTH = 1.1, AO_FLOOR = 0.25;
    private static final double EDGE_RATIO = 0.25;
    private static final double CORNER_RADIUS = Math.min(0.2, 1.0 / 3.0);
    private static final int RASTER_PER_SDF_STEP = 8;
    private static final double SDF_WINDOW_CELLS = 4.0;
    private static final double GLOSSINESS = 0.75;
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
    private static final double INF = Double.POSITIVE_INFINITY;
    private static final double MAX_COLUMN_DISTANCE = 48;
    private static final double HYP_PLANE_LENGTH = Math.tan(Math.toRadians(140.0) / 2.0);
    private static final int MAX_FIN = 4;
    private static final int KIND_NONE = 0, KIND_FLOOR = 1, KIND_CEIL = 2, KIND_WALL = 3, KIND_FINISH = 4;
    //endregion

    //region Per-frame state
    private int quality, aoSamples, reflectionDepth, reflectionSteps, edgeGrid;
    private int[][] baseMap;
    private PortalData basePortals;
    private MapData baseData;
    private final ConcurrentHashMap<int[][], MapData> mapCache = new ConcurrentHashMap<>();
    private double tanHalfFov, verticalTan, dirX, dirY, cosP, sinP, horizonH;
    private boolean hyperbolic, columnPath;
    private Column[] columns = new Column[0];
    private Column[] subColumns = new Column[0];
    private double[] rowV = new double[0];
    private int[] gKind = new int[0];
    private double[] gDist = new double[0];
    private boolean[] gEdge = new boolean[0];
    //endregion

    private void renderMoriarty() {
        final int width = fr.renderWidth, height = fr.renderHeight;
        quality = Math.clamp(cfgQuality, 0, 2);
        aoSamples = AO_SAMPLES[quality];
        reflectionDepth = REFLECTION_DEPTH[quality];
        reflectionSteps = REFLECTION_STEPS[quality];
        edgeGrid = EDGE_GRID[quality];
        hyperbolic = cfgGeometry == MazeGenerator.GeometryMode.WRONG;
        columnPath = !hyperbolic && fr.cameraPitch == 0;

        tanHalfFov = Math.tan(Math.toRadians(60.0) * 0.5);
        verticalTan = tanHalfFov / ((double) width / height);
        dirX = Math.cos(fr.cameraAngle);
        dirY = Math.sin(fr.cameraAngle);
        cosP = Math.cos(fr.cameraPitch);
        sinP = Math.sin(fr.cameraPitch);
        horizonH = height / 2.0 + fr.cameraPitch * height / 2.0;
        baseMap = fr.map;
        basePortals = fr.portals;

        prepareMaps();
        baseData = mapDataOf(baseMap);
        ensureBuffers(width, height);

        if (columnPath) {
            parallelRange(fr, 0, width, x -> buildColumn(columns[x], x + 0.5, width));
            if (edgeGrid > 1) {
                parallelRange(fr, 0, width * edgeGrid, s -> {
                    int x = s / edgeGrid, k = s % edgeGrid;
                    buildColumn(subColumns[s], x + (k + 0.5) / edgeGrid, width);
                });
            }
            for (int y = 0; y < height; y++) rowV[y] = (1.0 - (y + 0.5) / height * 2.0) * verticalTan;
        }

        if (edgeGrid > 1) {
            parallelRange(fr, 0, height, y -> {
                TraceHit hit = new TraceHit();
                for (int x = 0; x < width; x++) {
                    primaryPixel(x, y, hit);
                    int i = y * width + x;
                    gKind[i] = kindOf(hit);
                    gDist[i] = hit.hit ? hit.distance : INF;
                }
            });
            parallelRange(fr, 0, height, y -> markEdges(y, width, height));
        }
        parallelRange(fr, 0, height, y -> {
            TraceHit hit = new TraceHit(), sub = new TraceHit();
            TraceHit[] reflectionScratch = {new TraceHit(), new TraceHit()};
            for (int x = 0; x < width; x++) {
                int i = y * width + x;
                if (edgeGrid > 1 && gEdge[i]) {
                    fr.renderPixels[i] = shadeSupersampled(x, y, sub, reflectionScratch);
                    continue;
                }
                primaryPixel(x, y, hit);
                fr.renderPixels[i] = shadeHit(hit, 0.0, reflectionDepth, reflectionSteps, reflectionScratch);
            }
        });
        copyRenderBuffer(fr);
    }

    //region Buffers
    private void ensureBuffers(int width, int height) {
        int count = width * height;
        if (gKind.length != count) { gKind = new int[count]; gDist = new double[count]; gEdge = new boolean[count]; }
        if (columns.length != width) {
            columns = new Column[width];
            for (int x = 0; x < width; x++) columns[x] = new Column();
        }
        if (subColumns.length != width * MAX_EDGE_GRID) {
            subColumns = new Column[width * MAX_EDGE_GRID];
            for (int s = 0; s < subColumns.length; s++) subColumns[s] = new Column();
        }
        if (rowV.length != height) rowV = new double[height];
    }
    //endregion

    //region Primary visibility
    private void buildColumn(Column c, double fx, int width) {
        double sx = (fx / width * 2.0 - 1.0) * tanHalfFov;
        double hx = dirX - sx * dirY, hy = dirY + sx * dirX;
        double hl = Math.sqrt(hx * hx + hy * hy);
        c.ux = hx / hl;
        c.uy = hy / hl;
        c.hl = hl;
        c.fallback = !walkColumn(c, fr.playerX, fr.playerY, c.ux, c.uy, baseMap, baseData.corners);
    }
    private static boolean walkColumn(Column c, double px, double py, double ux, double uy, int[][] m, byte[] corners) {
        c.wallDist = INF;
        c.wallTexU = 0;
        c.wallSide = 0;
        c.wallCurved = false;
        c.wallNx = c.wallNy = 0;
        c.finCount = 0;
        final int mapWidth = m[0].length, mapHeight = m.length;
        double x = px, y = py, dist = 0;
        int cx = (int) Math.floor(x), cy = (int) Math.floor(y);
        if (corners != null && cx >= 0 && cy >= 0 && cx < mapWidth && cy < mapHeight && m[cy][cx] == 0) {
            int fillet = (corners[cy * mapWidth + cx] >> 4) & 15;
            if (fillet != 0) {
                double sHit = concaveEntry(px, py, ux, uy, cx, cy, fillet, c.scratch);
                if (sHit != INF && sHit <= MoriartyEngine.MAX_COLUMN_DISTANCE) {
                    int side = Math.abs(c.scratch.nrmX) >= Math.abs(c.scratch.nrmY) ? 0 : 1;
                    c.wallDist = sHit;
                    c.wallTexU = faceCoord(side, px + ux * sHit, py + uy * sHit, ux, uy);
                    c.wallSide = side;
                    c.wallCurved = c.scratch.curved;
                    c.wallNx = c.scratch.nrmX;
                    c.wallNy = c.scratch.nrmY;
                    return true;
                }
            }
        }
        while (dist < MoriartyEngine.MAX_COLUMN_DISTANCE) {
            double tX = ux > 0 ? (cx + 1 - x) / ux : ux < 0 ? (x - cx) / -ux : INF;
            double tY = uy > 0 ? (cy + 1 - y) / uy : uy < 0 ? (y - cy) / -uy : INF;
            boolean corner = Math.abs(tX - tY) <= 1e-9;
            boolean alongX = tX < tY;
            double t = Math.min(tX, tY);
            if (dist + t > MoriartyEngine.MAX_COLUMN_DISTANCE) return true;
            x += ux * t; y += uy * t; dist += t;
            if (corner) { cx += ux > 0 ? 1 : -1; cy += uy > 0 ? 1 : -1; alongX = true; }
            else if (alongX) cx += ux > 0 ? 1 : -1;
            else cy += uy > 0 ? 1 : -1;
            if (cx < 0 || cy < 0 || cx >= mapWidth || cy >= mapHeight) return true;
            int type = m[cy][cx];
            if (type == MazeGenerator.PORTAL) return false;
            if (type == 0) {
                int fillet = corners == null ? 0 : (corners[cy * mapWidth + cx] >> 4) & 15;
                if (fillet != 0) {
                    double sHit = concaveEntry(px, py, ux, uy, cx, cy, fillet, c.scratch);
                    if (sHit != INF) {
                        if (sHit > MoriartyEngine.MAX_COLUMN_DISTANCE) return true;
                        int side = Math.abs(c.scratch.nrmX) >= Math.abs(c.scratch.nrmY) ? 0 : 1;
                        c.wallDist = sHit;
                        c.wallTexU = faceCoord(side, px + ux * sHit, py + uy * sHit, ux, uy);
                        c.wallSide = side;
                        c.wallCurved = c.scratch.curved;
                        c.wallNx = c.scratch.nrmX;
                        c.wallNy = c.scratch.nrmY;
                        return true;
                    }
                }
                continue;
            }
            if (type != 1 && type != 2) continue;

            int mask = corners == null ? 0 : corners[cy * mapWidth + cx] & 15;
            double hitDist, qx = x, qy = y;
            int side;
            boolean curved = false;
            double nx = 0, ny = 0;
            if (mask == 0 || !inRoundedCorner(mask, x - cx, y - cy)) {
                hitDist = dist;
                side = alongX ? 0 : 1;
            } else {
                hitDist = roundedEntry(px, py, ux, uy, cx, cy, mask, c.scratch);
                if (hitDist == INF) continue;
                if (hitDist > MoriartyEngine.MAX_COLUMN_DISTANCE) return true;
                curved = c.scratch.curved;
                nx = c.scratch.nrmX;
                ny = c.scratch.nrmY;
                side = Math.abs(nx) >= Math.abs(ny) ? 0 : 1;
                qx = px + ux * hitDist;
                qy = py + uy * hitDist;
            }
            double u = faceCoord(side, qx, qy, ux, uy);
            if (type == 1) {
                c.wallDist = hitDist; c.wallTexU = u; c.wallSide = side;
                c.wallCurved = curved; c.wallNx = nx; c.wallNy = ny;
                return true;
            }
            if (c.finCount < MAX_FIN) {
                int k = c.finCount++;
                c.finDist[k] = hitDist; c.finU[k] = u; c.finSide[k] = side;
                c.finCurved[k] = curved; c.finNx[k] = nx; c.finNy[k] = ny;
            }
        }
        return true;
    }

    private void primaryPixel(int x, int y, TraceHit out) {
        if (columnPath && !columns[x].fallback) columnHitAt(columns[x], rowV[y], out);
        else cameraTrace(x + 0.5, y + 0.5, out);
    }
    private void columnHitAt(Column c, double v, TraceHit out) {
        out.reset();
        double hl = c.hl, ux = c.ux, uy = c.uy;
        double len = Math.sqrt(hl * hl + v * v);
        double hf = hl / len;
        double rzUnit = v / len;
        double sigma = rzUnit / hf;
        double z0 = fr.cameraHeight;
        double planeD = sigma < -1e-12 ? -z0 / sigma : sigma > 1e-12 ? (1.0 - z0) / sigma : INF;
        double wallD = c.wallDist;
        double nearest = Math.min(wallD, planeD);

        for (int k = 0; k < c.finCount; k++) {
            double d = c.finDist[k];
            if (d >= nearest) break;
            double z = z0 + sigma * d;
            if (z <= 0.5) {
                fill(out, d, hf, ux, uy, rzUnit, z, false);
                out.type = 2; out.side = c.finSide[k]; out.wallU = c.finU[k];
                out.wallV = Math.clamp(z / 0.5, 0.0, 0.999999); out.z = Math.clamp(z, 0.0, 0.5);
                setNormal(out, c.finCurved[k], c.finNx[k], c.finNy[k]);
                return;
            }
        }
        if (wallD < planeD) {
            double z = z0 + sigma * wallD;
            fill(out, wallD, hf, ux, uy, rzUnit, z, false);
            out.type = 1; out.side = c.wallSide; out.wallU = c.wallTexU;
            out.wallV = Math.clamp(z, 0.0, 0.999999); out.z = Math.clamp(z, 0.0, 1.0);
            setNormal(out, c.wallCurved, c.wallNx, c.wallNy);
            return;
        }
        if (planeD < INF) {
            boolean floor = sigma < 0;
            fill(out, planeD, hf, ux, uy, rzUnit, floor ? 0.0 : 1.0, true);
            out.type = floor ? -1 : 1; out.z = floor ? 0.0 : 1.0; out.side = 0;
        }
    }

    private void fill(TraceHit out, double horizontal, double hf, double ux, double uy, double rzUnit, double z, boolean plane) {
        out.hit = true;
        out.hyp = false;
        out.isPlane = plane;
        out.distance = horizontal / hf;
        out.x = fr.playerX + ux * horizontal;
        out.y = fr.playerY + uy * horizontal;
        out.z = z;
        out.dirX = ux * hf; out.dirY = uy * hf; out.dirZ = rzUnit;
        out.map = baseMap; out.portals = basePortals;
    }

    private static void setNormal(TraceHit out, boolean curved, double nx, double ny) {
        out.curved = curved;
        out.nrmX = nx;
        out.nrmY = ny;
    }

    private void cameraTrace(double fx, double fy, TraceHit out) {
        int width = fr.renderWidth, height = fr.renderHeight;
        if (hyperbolic) {
            double cx = fx / width * 2.0 - 1.0;
            double rdX = dirX - dirY * HYP_PLANE_LENGTH * cx, rdY = dirY + dirX * HYP_PLANE_LENGTH * cx;
            double L = Math.sqrt(rdX * rdX + rdY * rdY);
            traceHyp(fr, fr.playerX, fr.playerY, fr.cameraHeight, rdX / L, rdY / L, L, -(fy - horizonH) / height, out, MAX_PRIMARY_STEPS);
            return;
        }
        double sx = (fx / width * 2.0 - 1.0) * tanHalfFov;
        double sy = (1.0 - fy / height * 2.0) * verticalTan;
        double rx = dirX * cosP - dirY * sx - dirX * sinP * sy;
        double ry = dirY * cosP + dirX * sx - dirY * sinP * sy;
        double rz = sinP + cosP * sy;
        double inv = 1.0 / Math.sqrt(rx * rx + ry * ry + rz * rz);
        traceRayM(fr.playerX, fr.playerY, fr.cameraHeight, rx * inv, ry * inv, rz * inv, baseMap, basePortals, out, MAX_PRIMARY_STEPS);
    }

    private static int kindOf(TraceHit h) {
        if (!h.hit) return KIND_NONE;
        if (h.isPlane) return h.type == -1 ? KIND_FLOOR : KIND_CEIL;
        return h.type == 2 ? KIND_FINISH : KIND_WALL;
    }

    private void markEdges(int y, int width, int height) {
        for (int x = 0; x < width; x++) {
            int i = y * width + x;
            int kind = gKind[i];
            double d = gDist[i];
            boolean edge = false;
            for (int n = 0; n < 4 && !edge; n++) {
                int nx = x + (n == 0 ? -1 : n == 1 ? 1 : 0), ny = y + (n == 2 ? -1 : n == 3 ? 1 : 0);
                if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
                int j = ny * width + nx;
                if (gKind[j] != kind) edge = true;
                else if (kind != KIND_NONE && Math.abs(gDist[j] - d) > EDGE_RATIO * Math.max(d, gDist[j])) edge = true;
            }
            gEdge[i] = edge;
        }
    }

    private int shadeSupersampled(int x, int y, TraceHit sub, TraceHit[] scratch) {
        final int n = edgeGrid * edgeGrid;
        int r = 0, g = 0, b = 0;
        for (int j = 0; j < edgeGrid; j++) {
            for (int k = 0; k < edgeGrid; k++) {
                subSampleHit(x, y, j, k, sub);
                int c = shadeHit(sub, 0.0, reflectionDepth, reflectionSteps, scratch);
                r += (c >> 16) & 255; g += (c >> 8) & 255; b += c & 255;
            }
        }
        return ((r / n) << 16) | ((g / n) << 8) | (b / n);
    }

    private void subSampleHit(int x, int y, int j, int k, TraceHit out) {
        if (columnPath) {
            Column c = subColumns[x * edgeGrid + k];
            if (!c.fallback) {
                double fy = y + (j + 0.5) / edgeGrid;
                columnHitAt(c, (1.0 - fy / fr.renderHeight * 2.0) * verticalTan, out);
                return;
            }
        }
        cameraTrace(x + (k + 0.5) / edgeGrid, y + (j + 0.5) / edgeGrid, out);
    }
    //endregion

    //region Rounded wall corners
    private static byte[] buildCorners(int[][] m) {
        if (CORNER_RADIUS <= 0) return null;
        final int w = m[0].length, h = m.length;
        byte[] out = new byte[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int t = m[y][x];
                int mask = 0;
                for (int k = 0; k < 4; k++) {
                    int nx = x + ((k & 1) == 0 ? -1 : 1), ny = y + ((k & 2) == 0 ? -1 : 1);
                    if (t == 1 || t == 2) {
                        if (isOpenCell(m, nx, y) && isOpenCell(m, x, ny)) mask |= 1 << k;
                    } else if (t == 0) {
                        if (!isOpenCell(m, nx, y) && !isOpenCell(m, x, ny)) mask |= 1 << (4 + k);
                    }
                }
                out[y * w + x] = (byte) mask;
            }
        }
        return out;
    }

    private static boolean isOpenCell(int[][] m, int x, int y) {
        if (y < 0 || x < 0 || y >= m.length || x >= m[0].length) return false;
        int t = m[y][x];
        return t != 1 && t != 2;
    }
    private static boolean inCornerMaterial(int mask, double lx, double ly) {
        if (mask == 0) return false;
        final double r = CORNER_RADIUS, hi = 1.0 - r;
        for (int k = 0; k < 4; k++) {
            if (((mask >> k) & 1) == 0) continue;
            boolean left = (k & 1) == 0, low = (k & 2) == 0;
            double x0 = left ? 0 : hi, x1 = left ? r : 1, y0 = low ? 0 : hi, y1 = low ? r : 1;
            if (lx < x0 || lx > x1 || ly < y0 || ly > y1) continue;
            double qx = lx - (left ? r : hi), qy = ly - (low ? r : hi);
            if (qx * qx + qy * qy > r * r) return true;
        }
        return false;
    }
    private static boolean wallMaterial(int[][] m, byte[] corners, int cx, int cy, double lx, double ly) {
        int t = m[cy][cx];
        if (t == 1) return !inCornerMaterial(corners[cy * m[0].length + cx] & 15, lx, ly);
        if (t == 0) return inCornerMaterial((corners[cy * m[0].length + cx] >> 4) & 15, lx, ly);
        return false;
    }
    private static double[] roundedWallField(int[][] m, byte[] corners, int sub, int cols, int rows) {
        final int mw = m[0].length, mh = m.length;
        final int R = RASTER_PER_SDF_STEP * sub;
        final int W = mw * R, H = mh * R;
        final double BIG = 1e9;
        double[] g = new double[W * H];
        for (int py = 0; py < H; py++) {
            int cy = py / R;
            double ly = (py + 0.5) / R - cy;
            for (int px = 0; px < W; px++) {
                int cx = px / R;
                double lx = (px + 0.5) / R - cx;
                g[py * W + px] = wallMaterial(m, corners, cx, cy, lx, ly) ? 0.0 : BIG;
            }
        }
        squaredDistanceTransform(g, W, H);
        for (int k = 0; k < g.length; k++) g[k] = Math.sqrt(g[k]) / R;
        double[] out = new double[cols * rows];
        final double step = R / (double) sub;
        for (int j = 0; j < rows; j++)
            for (int i = 0; i < cols; i++) {
                double gx = Math.clamp(i * step - 0.5, 0, W - 1.000001), gy = Math.clamp(j * step - 0.5, 0, H - 1.000001);
                int x0 = (int) gx, y0 = (int) gy;
                double fx = gx - x0, fy = gy - y0;
                double a = g[y0 * W + x0], b = g[y0 * W + x0 + 1], c = g[(y0 + 1) * W + x0], d = g[(y0 + 1) * W + x0 + 1];
                double v = (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy;
                out[j * cols + i] = Math.min(SDF_WINDOW_CELLS, v);
            }
        return out;
    }

    private static void squaredDistanceTransform(double[] g, int W, int H) {
        int n = Math.max(W, H);
        double[] f = new double[n], d = new double[n], z = new double[n + 1];
        int[] v = new int[n];
        for (int y = 0; y < H; y++) {
            System.arraycopy(g, y * W, f, 0, W);
            envelope1D(f, W, d, v, z);
            System.arraycopy(d, 0, g, y * W, W);
        }
        for (int x = 0; x < W; x++) {
            for (int y = 0; y < H; y++) f[y] = g[y * W + x];
            envelope1D(f, H, d, v, z);
            for (int y = 0; y < H; y++) g[y * W + x] = d[y];
        }
    }

    private static void envelope1D(double[] f, int n, double[] d, int[] v, double[] z) {
        int k = 0;
        v[0] = 0;
        z[0] = Double.NEGATIVE_INFINITY;
        z[1] = Double.POSITIVE_INFINITY;
        for (int q = 1; q < n; q++) {
            double s = ((f[q] + (double) q * q) - (f[v[k]] + (double) v[k] * v[k])) / (2.0 * (q - v[k]));
            while (s <= z[k]) {
                k--;
                s = ((f[q] + (double) q * q) - (f[v[k]] + (double) v[k] * v[k])) / (2.0 * (q - v[k]));
            }
            k++;
            v[k] = q;
            z[k] = s;
            z[k + 1] = Double.POSITIVE_INFINITY;
        }
        k = 0;
        for (int q = 0; q < n; q++) {
            while (z[k + 1] < q) k++;
            double dq = q - v[k];
            d[q] = dq * dq + f[v[k]];
        }
    }
    private static final class MapData {
        final byte[] corners;
        final int cols, rows, sub;
        final boolean hasFinish;
        final double[] sdf;

        MapData(int[][] m, SDFField f, byte[] corners) {
            this.corners = corners;
            cols = f.cols;
            rows = f.rows;
            sub = (cols - 1) / m[0].length;
            hasFinish = f.hasFinish;
            sdf = new double[cols * rows * 2];
            double[] wall = corners != null ? roundedWallField(m, corners, sub, cols, rows) : f.wallGrid();
            double[] finish = f.finishGrid();
            for (int p = 0; p < cols * rows; p++) {
                sdf[2 * p] = wall[p];
                sdf[2 * p + 1] = finish[p];
            }
        }
    }
    private void prepareMaps() {
        // Prepare every floor up front so switching floors during play never has to build data on demand.
        java.util.ArrayList<int[][]> todo = new java.util.ArrayList<>();
        todo.add(fr.map);
        if (fr.map3D != null)
            for (int[][] floor : fr.map3D)
                if (!todo.contains(floor)) todo.add(floor);
        for (int k = 0; k < todo.size(); k++) {
            Progress.report("load.render", k / (double) todo.size());
            mapDataFor(todo.get(k));
        }
        Progress.report("load.render", 1.0);
    }
    private MapData mapDataFor(int[][] m) {
        MapData d = mapCache.get(m);
        if (d != null) return d;
        synchronized (mapCache) {
            d = mapCache.get(m);
            if (d == null) {
                d = new MapData(m, sdfFieldFor(m), buildCorners(m));
                if (mapCache.size() >= MAX_CACHED_MAPS) mapCache.clear();
                mapCache.put(m, d);
            }
        }
        return d;
    }
    private MapData mapDataOf(int[][] m) { return mapDataFor(m); }
    private static double concaveEntry(double px, double py, double dx, double dy, int cx, int cy, int mask, TraceHit out) {
        final double r = CORNER_RADIUS, hi = 1.0 - r;
        final double lx = px - cx, ly = py - cy;
        double best = INF;
        for (int k = 0; k < 4; k++) {
            if (((mask >> k) & 1) == 0) continue;
            boolean left = (k & 1) == 0, low = (k & 2) == 0;
            double x0 = left ? 0.0 : hi, x1 = left ? r : 1.0;
            double y0 = low ? 0.0 : hi, y1 = low ? r : 1.0;
            double tx0 = -INF, tx1 = INF, ty0 = -INF, ty1 = INF;
            if (Math.abs(dx) < 1e-12) { if (lx < x0 || lx > x1) continue; }
            else {
                double a = (x0 - lx) / dx, b = (x1 - lx) / dx;
                tx0 = Math.min(a, b); tx1 = Math.max(a, b);
            }
            if (Math.abs(dy) < 1e-12) { if (ly < y0 || ly > y1) continue; }
            else {
                double a = (y0 - ly) / dy, b = (y1 - ly) / dy;
                ty0 = Math.min(a, b); ty1 = Math.max(a, b);
            }
            double t0 = Math.max(Math.max(tx0, ty0), 0.0), t1 = Math.min(tx1, ty1);
            if (t0 > t1 || t0 >= best) continue;

            double ccx = left ? r : hi, ccy = low ? r : hi;
            double qx = lx - ccx, qy = ly - ccy;
            if (t0 == 0 && qx * qx + qy * qy >= r * r) continue;
            double ex = qx + dx * t0, ey = qy + dy * t0;
            if (ex * ex + ey * ey >= r * r) {
                if (tx0 >= ty0) { out.nrmX = dx > 0 ? -1 : 1; out.nrmY = 0; }
                else { out.nrmX = 0; out.nrmY = dy > 0 ? -1 : 1; }
                out.curved = false;
                best = t0;
            }
            else {
                double b = dx * qx + dy * qy, c = qx * qx + qy * qy - r * r;
                double t2 = -b + Math.sqrt(b * b - c);
                if (t2 <= t1 && t2 < best) {
                    out.nrmX = -(qx + dx * t2) / r;
                    out.nrmY = -(qy + dy * t2) / r;
                    out.curved = true;
                    best = t2;
                }
            }
        }
        return best;
    }
    private static boolean inRoundedCorner(int mask, double lx, double ly) {
        final double r = CORNER_RADIUS, hi = 1.0 - r;
        if (!(lx < r || lx > hi) || !(ly < r || ly > hi)) return false;
        int k = (lx > hi ? 1 : 0) | (ly > hi ? 2 : 0);
        return ((mask >> k) & 1) != 0;
    }
    private static double roundedEntry(double px, double py, double dx, double dy, int cx, int cy, int mask, TraceHit out) {
        final double r = CORNER_RADIUS, hi = 1.0 - r;
        final double lx = px - cx, ly = py - cy;
        double best = INF;
        best = rectEntry(lx, ly, dx, dy, 0.0, 1.0, r, hi, best, out);
        best = rectEntry(lx, ly, dx, dy, r, hi, 0.0, 1.0, best, out);
        for (int k = 0; k < 4; k++) {
            boolean left = (k & 1) == 0, low = (k & 2) == 0;
            if (((mask >> k) & 1) != 0) {
                best = diskEntry(lx, ly, dx, dy, left ? r : hi, low ? r : hi, r, best, out);
            } else {
                best = rectEntry(lx, ly, dx, dy, left ? 0.0 : hi, left ? r : 1.0, low ? 0.0 : hi, low ? r : 1.0, best, out);
            }
        }
        return best;
    }

    private static double rectEntry(double lx, double ly, double dx, double dy, double x0, double x1, double y0, double y1, double best, TraceHit out) {
        double tx0 = -INF, tx1 = INF, ty0 = -INF, ty1 = INF;
        if (Math.abs(dx) < 1e-12) { if (lx < x0 || lx > x1) return best; }
        else {
            double a = (x0 - lx) / dx, b = (x1 - lx) / dx;
            tx0 = Math.min(a, b); tx1 = Math.max(a, b);
        }
        if (Math.abs(dy) < 1e-12) { if (ly < y0 || ly > y1) return best; }
        else {
            double a = (y0 - ly) / dy, b = (y1 - ly) / dy;
            ty0 = Math.min(a, b); ty1 = Math.max(a, b);
        }
        double t0 = Math.max(tx0, ty0), t1 = Math.min(tx1, ty1);
        if (t0 > t1 || t1 < 0 || t0 >= best) return best;
        if (tx0 >= ty0) { out.nrmX = dx > 0 ? -1 : 1; out.nrmY = 0; }
        else { out.nrmX = 0; out.nrmY = dy > 0 ? -1 : 1; }
        out.curved = false;
        return Math.max(t0, 0.0);
    }
    private static double diskEntry(double lx, double ly, double dx, double dy, double ccx, double ccy, double r, double best, TraceHit out) {
        double px = lx - ccx, py = ly - ccy;
        double b = dx * px + dy * py;
        double disc = b * b - (px * px + py * py - r * r);
        if (disc < 0) return best;
        double sq = Math.sqrt(disc);
        double t0 = -b - sq, t1 = -b + sq;
        if (t1 < 0 || t0 >= best) return best;
        double t = Math.max(t0, 0.0);
        out.nrmX = (px + dx * t) / r;
        out.nrmY = (py + dy * t) / r;
        out.curved = true;
        return t;
    }
    private static double faceCoord(int side, double px, double py, double dirX, double dirY) {
        double u = side == 0 ? py - Math.floor(py) : px - Math.floor(px);
        if ((side == 0 && dirX < 0) || (side == 1 && dirY > 0)) u = 1.0 - u;
        return Math.clamp(u, 0.0, 0.999999);
    }
    private void traceRayM(double ox, double oy, double oz, double rx, double ry, double rz, int[][] startMap, PortalData startPortals, TraceHit result, int maxSteps) {
        result.reset();
        final int mapWidth = startMap[0].length, mapHeight = startMap.length;
        double totalDistance = 0;
        int hops = 0, steps = 0;
        double wallT = INF;
        int wallType = 0, wallSide = 0;
        boolean wallCurved = false;
        double wallNx = 0, wallNy = 0;
        int[][] rayMap = startMap;
        PortalData rayPortals = startPortals;
        MapData rayData = mapDataOf(rayMap);

        segments:
        while (true) {
            double horizontal = Math.sqrt(rx * rx + ry * ry);
            if (horizontal <= 1e-9) break;
            double planeHere = rz < -1e-9 ? -oz / rz : rz > 1e-9 ? (1.0 - oz) / rz : INF;

            double dx = rx / horizontal, dy = ry / horizontal;
            int mapX = (int) Math.floor(ox), mapY = (int) Math.floor(oy);
            double deltaX = Math.abs(1.0 / dx), deltaY = Math.abs(1.0 / dy);
            double sideX = (dx < 0 ? ox - mapX : mapX + 1.0 - ox) * deltaX;
            double sideY = (dy < 0 ? oy - mapY : mapY + 1.0 - oy) * deltaY;
            if (mapX >= 0 && mapY >= 0 && mapX < mapWidth && mapY < mapHeight && rayData.corners != null
                    && rayMap[mapY][mapX] == 0) {
                int fillet = (rayData.corners[mapY * rayMap[0].length + mapX] >> 4) & 15;
                if (fillet != 0) {
                    double s = concaveEntry(ox, oy, dx, dy, mapX, mapY, fillet, result);
                    if (s != INF) {
                        double t = s / horizontal;
                        double candidateZ = oz + rz * t;
                        if (t < planeHere && candidateZ >= 0.0 && candidateZ <= wallHeightForType(1)) {
                            wallT = t; wallType = 1;
                            wallCurved = result.curved; wallNx = result.nrmX; wallNy = result.nrmY;
                            wallSide = Math.abs(wallNx) >= Math.abs(wallNy) ? 0 : 1;
                            break;
                        }
                    }
                }
            }

            while (mapX >= 0 && mapY >= 0 && mapX < mapWidth && mapY < mapHeight) {
                double horizontalT;
                int side;
                if (sideX < sideY) {
                    horizontalT = sideX; sideX += deltaX; mapX += dx < 0 ? -1 : 1; side = 0;
                } else {
                    horizontalT = sideY; sideY += deltaY; mapY += dy < 0 ? -1 : 1; side = 1;
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
                                rayData = mapDataOf(rayMap);
                            }
                            oz = candidateZ;
                            hops++;
                            continue segments;
                        }
                    }
                }

                if (type != 1 && type != 2) {
                    int fillet = rayData.corners == null || type != 0 ? 0 : (rayData.corners[mapY * rayMap[0].length + mapX] >> 4) & 15;
                    if (fillet != 0) {
                        double s = concaveEntry(ox, oy, dx, dy, mapX, mapY, fillet, result);
                        if (s != INF) {
                            double t = s / horizontal;
                            if (t >= planeHere) break segments;
                            double candidateZ = oz + rz * t;
                            if (candidateZ >= 0.0 && candidateZ <= wallHeightForType(1)) {
                                wallT = t; wallType = 1;
                                wallCurved = result.curved; wallNx = result.nrmX; wallNy = result.nrmY;
                                wallSide = Math.abs(wallNx) >= Math.abs(wallNy) ? 0 : 1;
                                break segments;
                            }
                        }
                    }
                    continue;
                }
                int mask = rayData.corners == null ? 0 : rayData.corners[mapY * rayMap[0].length + mapX] & 15;
                if (mask == 0 || !inRoundedCorner(mask, ox + dx * horizontalT - mapX, oy + dy * horizontalT - mapY)) {
                    double candidateZ = oz + rz * candidateT;
                    if (candidateZ >= 0.0 && candidateZ <= wallHeightForType(type)) {
                        wallT = candidateT; wallType = type; wallSide = side;
                        break segments;
                    }
                    continue;
                }
                double s = roundedEntry(ox, oy, dx, dy, mapX, mapY, mask, result);
                if (s == INF) continue;
                double t = s / horizontal;
                if (t >= planeHere) break segments;
                double candidateZ = oz + rz * t;
                if (candidateZ < 0.0 || candidateZ > wallHeightForType(type)) continue;
                wallT = t; wallType = type;
                wallCurved = result.curved; wallNx = result.nrmX; wallNy = result.nrmY;
                wallSide = Math.abs(wallNx) >= Math.abs(wallNy) ? 0 : 1;
                break segments;
            }
            break;
        }

        result.map = rayMap;
        result.portals = rayPortals;

        double planeT = INF;
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
            setNormal(result, wallCurved, wallNx, wallNy);
            result.wallU = faceCoord(wallSide, result.x, result.y, rx, ry);
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
            setNormal(result, false, 0, 0);
        }
    }
    //endregion

    //region Shading (raymarch occlusion + ray-traced reflections)
    private int shadeHit(TraceHit hit, double pathBefore, int depth, int steps, TraceHit[] scratch) {
        if (!hit.hit) return ceilingBaseColor;
        double lx = -hit.dirX, ly = -hit.dirY, lz = -hit.dirZ + 0.18;
        double llen = Math.sqrt(lx * lx + ly * ly + lz * lz);
        if (llen > 1e-9) { lx /= llen; ly /= llen; lz /= llen; }

        MapData data = hit.map == baseMap ? baseData : mapDataOf(hit.map);
        int base;
        double nx, ny, nz, ao, specStrength, reflectivity;
        int shininess;
        boolean emissive = false;
        if (hit.isPlane) {
            boolean floor = hit.type == -1;
            base = planeColor(floor ? fr.floorTexture : fr.ceilingTexture, hit.x, hit.y, floor ? floorBaseColor : ceilingBaseColor);
            if (floor && is3D(fr)) base = tintStairs(base, hit.x, hit.y, hit.map);
            nx = 0; ny = 0; nz = floor ? 1 : -1;
            ao = occlusionMarch(data, hit.x, hit.y, hit.z, nx, ny, nz);
            shininess = floor ? 30 : 16;
            specStrength = floor ? 0.16 : 0.10;
            reflectivity = floor ? 0.12 : 0.06;
        } else {
            boolean finish = hit.type == 2;
            base = sampleWallTexture(finish ? fr.finishTexture : fr.wallTexture, hit);
            if (hit.curved) {
                nx = hit.nrmX;
                ny = hit.nrmY;
            } else {
                nx = hit.side == 0 ? (hit.dirX > 0 ? -1 : 1) : 0;
                ny = hit.side == 1 ? (hit.dirY > 0 ? -1 : 1) : 0;
            }
            nz = 0;
            emissive = finish;
            ao = finish ? 1.0 : wallOcclusion(hit.z, wallHeightForType(hit.type)) * occlusionMarch(data, hit.x, hit.y, hit.z, nx, ny, nz);
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
            if (hlen > 1e-9) glint = specStrength * att * GLOSSINESS * glintPow(Math.max(0, (nx * hx + ny * hy + nz * hz) / hlen), shininess);
        }

        double fillMix = 0.6 + 0.4 * ao;
        double lightR = AMBIENT_LIGHT[0] * ao + FILL_LIGHT[0] * ndf * ao + FLASH_LIGHT[0] * flash * fillMix + FLASH_LIGHT[0] * glint;
        double lightG = AMBIENT_LIGHT[1] * ao + FILL_LIGHT[1] * ndf * ao + FLASH_LIGHT[1] * flash * fillMix + FLASH_LIGHT[1] * glint;
        double lightB = AMBIENT_LIGHT[2] * ao + FILL_LIGHT[2] * ndf * ao + FLASH_LIGHT[2] * flash * fillMix + FLASH_LIGHT[2] * glint;
        if (emissive) { lightR = Math.max(lightR, 0.7); lightG = Math.max(lightG, 0.7); lightB = Math.max(lightB, 0.7); }

        int br = (base >> 16) & 255, bg = (base >> 8) & 255, bb = base & 255;
        int lit = (toneToByte(br / 255.0 * lightR) << 16) | (toneToByte(bg / 255.0 * lightG) << 8) | toneToByte(bb / 255.0 * lightB);

        double level = (lightR + lightG + lightB) / 3.0;
        reflectivity *= GLOSSINESS * Math.clamp(level, 0.15, 1.0);
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
        } else {
            traceRayM(hit.x + nx * 0.004, hit.y + ny * 0.004, hit.z + nz * 0.004, rrx, rry, rrz, hit.map, hit.portals, reflected, steps);
        }
        if (!reflected.hit) return lit;
        int reflectedColor = shadeHit(reflected, dist, depth - 1, steps, scratch);
        return mixColor(lit, reflectedColor, reflectivity);
    }

    private double occlusionMarch(MapData md, double px, double py, double pz, double nx, double ny, double nz) {
        double occ = 0, weight = 1.0;
        if (nx == 0 && ny == 0) {
            double dw = gridValue(md, 0, px, py);
            double df = md.hasFinish ? gridValue(md, 1, px, py) : 0.0;
            for (int i = 0; i < aoSamples; i++) {
                double h = AO_HEIGHTS[i];
                double d = solidDistanceAt(dw, df, md.hasFinish, pz + nz * h);
                occ += Math.max(0.0, h - d) * weight;
                weight *= 0.6;
            }
        }
        else {
            final int cols = md.cols, rows = md.rows;
            final double[] g = md.sdf;
            final double sub = md.sub;
            final boolean hasFinish = md.hasFinish;
            for (int i = 0; i < aoSamples; i++) {
                double h = AO_HEIGHTS[i];
                double x = px + nx * h, y = py + ny * h;
                double gx = Math.clamp(x * sub, 0, cols - 1.000001), gy = Math.clamp(y * sub, 0, rows - 1.000001);
                int i0 = (int) gx, j0 = (int) gy;
                int i1 = Math.min(i0 + 1, cols - 1), j1 = Math.min(j0 + 1, rows - 1);
                double fx = gx - i0, fy = gy - j0;
                int p00 = (j0 * cols + i0) * 2, p01 = (j0 * cols + i1) * 2, p10 = (j1 * cols + i0) * 2, p11 = (j1 * cols + i1) * 2;
                double dw = (g[p00] * (1 - fx) + g[p01] * fx) * (1 - fy) + (g[p10] * (1 - fx) + g[p11] * fx) * fy;
                double df = hasFinish ? (g[p00 + 1] * (1 - fx) + g[p01 + 1] * fx) * (1 - fy) + (g[p10 + 1] * (1 - fx) + g[p11 + 1] * fx) * fy : 0.0;
                double d = solidDistanceAt(dw, df, hasFinish, pz + nz * h);
                occ += Math.max(0.0, h - d) * weight;
                weight *= 0.6;
            }
        }
        return Math.clamp(1.0 - AO_STRENGTH * occ, AO_FLOOR, 1.0);
    }
    private static double solidDistanceAt(double dw, double df, boolean hasFinish, double z) {
        double dz = Math.max(0.0, z - 1.0);
        double d = Math.sqrt(dw * dw + dz * dz);
        if (hasFinish) {
            double ddz = Math.max(0.0, z - 0.5);
            d = Math.min(d, Math.sqrt(df * df + ddz * ddz));
        }
        return d;
    }
    private static double gridValue(MapData md, int channel, double x, double y) {
        final int cols = md.cols, rows = md.rows;
        final double[] g = md.sdf;
        double gx = Math.clamp(x * md.sub, 0, cols - 1.000001), gy = Math.clamp(y * md.sub, 0, rows - 1.000001);
        int i0 = (int) gx, j0 = (int) gy;
        int i1 = Math.min(i0 + 1, cols - 1), j1 = Math.min(j0 + 1, rows - 1);
        double fx = gx - i0, fy = gy - j0;
        double a = g[(j0 * cols + i0) * 2 + channel], b = g[(j0 * cols + i1) * 2 + channel];
        double c = g[(j1 * cols + i0) * 2 + channel], d = g[(j1 * cols + i1) * 2 + channel];
        return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy;
    }
    //endregion

    //region Light helpers
    private static double[] buildToneLut() {
        double[] lut = new double[TONE_STEPS + 1];
        for (int i = 0; i <= TONE_STEPS; i++) lut[i] = toneCurve(i * TONE_MAX / TONE_STEPS);
        return lut;
    }
    private static int toneToByte(double light) {
        if (!(light > 0)) return 0;
        if (light >= TONE_MAX) return 255;
        return (int) (TONE_LUT[(int) (light * TONE_STEPS / TONE_MAX)] * 255.0 + 0.5);
    }
    //endregion

    //region Nested Types
    private static final class Column {
        double ux, uy, hl;
        boolean fallback;
        double wallDist, wallTexU, wallNx, wallNy;
        int wallSide;
        boolean wallCurved;
        int finCount;
        final double[] finDist = new double[MAX_FIN], finU = new double[MAX_FIN];
        final double[] finNx = new double[MAX_FIN], finNy = new double[MAX_FIN];
        final int[] finSide = new int[MAX_FIN];
        final boolean[] finCurved = new boolean[MAX_FIN];
        final TraceHit scratch = new TraceHit();
    }
    //endregion
}