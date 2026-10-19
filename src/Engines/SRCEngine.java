package Engines;

import GameViewComponents.*;
import Helpers.*;
import java.awt.*;
import java.awt.image.*;
import java.util.function.IntConsumer;

public final class SRCEngine implements RenderEngine {
    //region Shared helpers

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
    static final int wallBaseColor = 0xECD485;
    static final int ceilingBaseColor = 0x816E1E;
    static final int floorBaseColor = 0xD3AF63;
    static final int finishBaseColor = 0x33FF66;
    private static final double flashlightDistance = 2;
    private static final double HYP_PLANE_LENGTH = Math.tan(Math.toRadians(140.0) / 2.0);
    static final int HIT_WALL = 1, HIT_FLOOR = 2, HIT_CEIL = 3;
    static final double RM_MAX_DISTANCE = 48;
    private static final int SDF_SUB = 8;
    //endregion

    //region Caches
    static void ensureRaycastCaches(RenderFrame fr, int screenWidth, int screenHeight) {
        if (fr.wallTopCache == null || fr.wallTopCache.length != screenWidth) {
            fr.wallTopCache = new int[screenWidth]; fr.wallBottomCache = new int[screenWidth];
            fr.portalFloorEdgeCache = new double[screenWidth]; fr.portalCeilEdgeCache = new double[screenWidth];
        }
        if (fr.rowInvACache == null || fr.rowInvACache.length != screenHeight) fr.rowInvACache = new double[screenHeight]; fr.rowZACache = new double[screenHeight];
    }
    static void fillRowCaches(RenderFrame fr, double focal, double halfHeight, double pitchSin, double pitchCos) {
        final int screenHeight = fr.renderHeight;
        for (int y = 0; y < screenHeight; y++) {
            double v = (halfHeight - (y + 0.5)) / focal;
            double a = pitchCos - pitchSin * v;
            if (Math.abs(a) < 0.02) a = a < 0 ? -0.02 : 0.02;
            fr.rowInvACache[y] = 1.0 / a;
            fr.rowZACache[y] = (pitchSin + pitchCos * v) / a;
        }
    }
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
    static double hypHeight(RayState rayState) {
        return rayState.hypZ0 + rayState.slope * HyperbolicMath.hyperbolicDistance(rayState.dist * rayState.invL, cfgCurvature);
    }
    static double hypPerceivedOf(RayState rayState) {
        return HyperbolicMath.hyperbolicDistance(rayState.dist * rayState.invL, cfgCurvature);
    }
    static double[] hypPixelRay(double cx, double py, double dirX, double dirY, double focal, double horizon) {
        double rdX = dirX - dirY * HYP_PLANE_LENGTH * cx, rdY = dirY + dirX * HYP_PLANE_LENGTH * cx;
        double L = Math.sqrt(rdX * rdX + rdY * rdY);
        return new double[]{rdX / L, rdY / L, L, -(py - horizon) / focal};
    }
    //endregion

    //region Tracing
    static void setWallFraction(RayState s) {
        double wallH = wallHeightForType(s.hitType);
        s.hitFrac = Math.clamp((wallH - s.z) / wallH, 0.0, 0.999999);
    }
    static void setPlaneHit(RayState s, int kind) {
        s.hitKind = kind;
        s.hitX = s.x;
        s.hitY = s.y;
        s.hitDist = s.dist;
    }
    //endregion

    //region Marching
    static void addColumnSegment(ColumnRay c, double dist, RayState s) {
        int k = c.segCount++;
        c.segStartDist[k] = dist; c.segStartX[k] = s.x; c.segStartY[k] = s.y; c.segDirX[k] = s.dx; c.segDirY[k] = s.dy;
        c.segMap[k] = s.map; c.segDistField[k] = s.field;
    }
    static int shadeColumnRow(RenderFrame fr, ColumnRay c, RayState rs, double z0, double sigma, double lightDistance) {
        final double scale = Math.sqrt(1.0 + sigma * sigma);
        final double planeD = sigma < 0 ? z0 / -sigma : sigma > 0 ? (1.0 - z0) / sigma : Double.POSITIVE_INFINITY;
        final boolean floor = sigma < 0;
        final double nearest = Math.min(Math.min(c.wallDist, planeD), c.exitD);
        for (int k = 0; k < c.finCount; k++) {
            double d = c.finishDist[k];
            if (d >= nearest) break;
            double z = z0 + sigma * d;
            if (z <= 0.5) {
                rs.hitKind = HIT_WALL; rs.hitType = 2; rs.hitSide = c.finishSide[k]; rs.hitU = c.finishTexU[k];
                rs.hitDist = d * scale;
                rs.hitFrac = Math.clamp((0.5 - z) / 0.5, 0.0, 0.999999);
                return shadeMarch(fr, rs, lightDistance);
            }
        }
        if (c.wallDist < planeD && c.wallDist < c.exitD) {
            double z = z0 + sigma * c.wallDist;
            rs.hitKind = HIT_WALL; rs.hitType = 1; rs.hitSide = c.wallSide; rs.hitU = c.wallTexU;
            rs.hitDist = c.wallDist * scale;
            rs.hitFrac = Math.clamp(1.0 - z, 0.0, 0.999999);
            return shadeMarch(fr, rs, lightDistance);
        }
        if (Double.isFinite(planeD) && planeD < c.exitD && planeD < RM_MAX_DISTANCE) {
            int k = 0;
            for (int j = 1; j < c.segCount && c.segStartDist[j] <= planeD; j++) k = j;
            double along = planeD - c.segStartDist[k];
            rs.hitKind = floor ? HIT_FLOOR : HIT_CEIL;
            rs.hitX = c.segStartX[k] + c.segDirX[k] * along;
            rs.hitY = c.segStartY[k] + c.segDirY[k] * along;
            rs.hitDist = planeD * scale;
            rs.map = c.segMap[k];
            return shadeMarch(fr, rs, lightDistance);
        }
        return ceilingBaseColor;
    }
    static int shadeMarch(RenderFrame fr, RayState s, double lightDistance) {
        if (s.hitKind == HIT_WALL) {
            boolean finish = s.hitType == 2;
            RuntimeTexture texture = finish ? fr.finishTexture : fr.wallTexture;
            int color;
            if (texture.width() > 0 && texture.height() > 0) {
                int tx = Math.min((int) (s.hitU * texture.width()), texture.width() - 1);
                int ty = Math.min((int) (s.hitFrac * texture.height()), texture.height() - 1);
                color = texture.pixels()[ty * texture.width() + tx];
            }
            else color = finish ? finishBaseColor : wallBaseColor;
            double brightness = Math.clamp(1.0 - s.hitDist / lightDistance, 0.03, 1.0);
            if (s.hitSide == 1) brightness *= 0.85;
            return applyBrightness(color, brightness);
        }
        boolean floor = s.hitKind == HIT_FLOOR;
        int color = floor ? planeColor(fr.floorTexture, s.hitX, s.hitY, floorBaseColor) : planeColor(fr.ceilingTexture, s.hitX, s.hitY, ceilingBaseColor);
        if (floor && is3D(fr)) color = tintStairs(color, s.hitX, s.hitY, s.map);
        double brightness = Math.clamp(1.0 - s.hitDist / lightDistance, 0.05, 1.0);
        return applyBrightness(color, brightness);
    }
    //endregion

    //region Textures and shading
    private static int tintColor(int color, int tint, double amount) {
        int r = (color >> 16) & 255, g = (color >> 8) & 255, b = color & 255;
        int tr = (tint >> 16) & 255, tg = (tint >> 8) & 255, tb = tint & 255;
        r = (int)(r * (1 - amount) + tr * amount); g = (int)(g * (1 - amount) + tg * amount); b = (int)(b * (1 - amount) + tb * amount);
        return (r << 16) | (g << 8) | b;
    }
    static int applyBrightness(int color, double brightness) {
        int r = (int) Math.min(255, ((color >> 16) & 255) * brightness);
        int g = (int) Math.min(255, ((color >> 8) & 255) * brightness);
        int b = (int) Math.min(255, (color & 255) * brightness);
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
    static boolean enterPortal(RenderFrame fr, RayState s, int cx, int cy) {
        if (s.hops >= 8 || s.portals == null) return false;
        PortalData.Portal portal = s.portals.get(cx, cy);
        if (portal == null || !PortalData.entersFromCorrectSide(portal, s.dx, s.dy)) return false;
        double horizontal = Math.sqrt(s.dx * s.dx + s.dy * s.dy);
        double[] warped = PortalData.warp(portal, s.x, s.y, s.dx, s.dy);
        double newHorizontal = Math.sqrt(warped[2] * warped[2] + warped[3] * warped[3]);
        s.x = warped[0];
        s.y = warped[1];
        if (newHorizontal > 1e-9) {
            s.dx = warped[2] * horizontal / newHorizontal;
            s.dy = warped[3] * horizontal / newHorizontal;
        }
        if (fr.map3D != null && portal.linkedFloor >= 0 && portal.linkedFloor < fr.map3D.length) {
            s.map = fr.map3D[portal.linkedFloor];
            s.portals = fr.floorPortalsAll[portal.linkedFloor];
            if (s.field != null) s.field = sdfFieldFor(fr, s.map);
        }
        s.hops++;
        return true;
    }
    static boolean is3D(RenderFrame fr) { return fr.map3D != null && fr.map3D.length > 1; }
    static double wallHeightForType(int type) { return type == 2 ? 0.5 : 1.0; }
    static double effectiveFlashlightDistance(boolean hyperbolic) { return hyperbolic ? flashlightDistance * 1.6 : flashlightDistance; }
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
        static final int MAX_FIELDS = 32;
        final java.util.concurrent.ConcurrentHashMap<int[][], SDFField> fields = new java.util.concurrent.ConcurrentHashMap<>();

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
        void dropTiles() {
            for (int k = 0; k < tilesX * tilesY; k++) {
                wallTiles.set(k, null);
                finishTiles.set(k, null);
            }
        }
    }
    static SDFField sdfFieldFor(RenderFrame fr, int[][] m) {
        return sdfCache.fieldFor(m);
    }
    public static final class ColumnRay {
        static final int MAX_SEG = 10, MAX_FIN = 4;
        final RayState ray = new RayState();
        int segCount, finCount;
        boolean exhausted;
        final double[] segStartDist = new double[MAX_SEG], segStartX = new double[MAX_SEG], segStartY = new double[MAX_SEG];
        final double[] segDirX = new double[MAX_SEG], segDirY = new double[MAX_SEG];
        final int[][][] segMap = new int[MAX_SEG][][];
        final SDFField[] segDistField = new SDFField[MAX_SEG];
        final double[] finishDist = new double[MAX_FIN], finishTexU = new double[MAX_FIN];
        final int[] finishSide = new int[MAX_FIN];
        double wallDist, wallTexU;
        int wallSide;
        double exitD;
    }
    public static final class RayState {
        double x, y, z, dx, dy, dz, dist,
                hitU, hitFrac, hitX, hitY, hitDist,
                hypZ0, slope, L, invL, floorD, ceilD;
        int[][] map;
        PortalData portals;
        SDFField field;
        int hops, hitKind, hitType, hitSide;
        boolean hyp, exhausted;
        void resetHyp(double x0, double y0, double z0, double[] ray, int[][] m, SDFField f) {
            x = x0; y = y0; z = z0;
            dx = ray[0]; dy = ray[1]; dz = 0;
            L = ray[2]; invL = 1.0 / L;
            slope = ray[3];
            hypZ0 = z0;
            floorD = hypFloorDistance(z0, slope, L);
            ceilD = hypCeilDistance(z0, slope, L);
            dist = 0; map = m; portals = null; field = f; hops = 0; hitKind = 0;
            hyp = true; exhausted = false;
        }
        void reset(double x0, double y0, double z0, double rx, double ry, double rz, int[][] m, PortalData p, SDFField f) {
            double len = Math.sqrt(rx * rx + ry * ry + rz * rz);
            x = x0; y = y0; z = z0;
            dx = rx / len; dy = ry / len; dz = rz / len;
            dist = 0;
            map = m;
            portals = p;
            field = f;
            hops = 0;
            hitKind = 0;
        }
    }
    //endregion
    //endregion

    //region Module
    private RenderFrame fr;
    @Override public String id() { return "raycasting"; }
    @Override public String displayName() { return "Ray Casting"; }
    @Override public int order() { return 3; }
    @Override public String hintKey() { return "mm.engine_rc"; }
    @Override public String qualityHintKey() { return "mm.quality_rc"; }
    @Override public boolean hasHyperbolicGeometry() { return true; }
    @Override public void render(RenderFrame frame) { fr = frame; syncConfig(frame); renderSoftwareRayCasting(); }
    //endregion
    private static final double RC_MAX_DISTANCE = 48;

    private void renderSoftwareRayCasting() {
        if (cfgGeometry == MazeGenerator.GeometryMode.WRONG) { renderSoftwareRayCastingHyp(); return; }
        if (fr.cameraPitch == 0) { renderSoftwareRayCastingColumns(); return; }
        final double dirX = Math.cos(fr.cameraAngle), dirY = Math.sin(fr.cameraAngle);
        final double planeLength = Math.tan(Math.PI / 3.0 / 2.0);
        final int width = fr.renderWidth, height = fr.renderHeight;
        final double focal = width / (2.0 * planeLength);
        final double halfHeight = height / 2.0;
        final double pitchSin = Math.sin(fr.cameraPitch), pitchCos = Math.cos(fr.cameraPitch);
        ensureRaycastCaches(fr, width, height);
        fillRowCaches(fr, focal, halfHeight, pitchSin, pitchCos);
        final double[] rowInvA = fr.rowInvACache, rowZA = fr.rowZACache;
        final double lightDistance = effectiveFlashlightDistance(false);
        final int[][] baseMap = fr.map;
        final PortalData basePortals = fr.portals;
        parallelRange(fr, 0, height, y -> {
            RayState s = new RayState();
            double rowZ = rowZA[y], rowScale = rowInvA[y];
            for (int x = 0; x < width; x++) {
                double lateral = fr.cameraXCache[x] * planeLength * rowScale;
                s.reset(fr.playerX, fr.playerY, fr.cameraHeight, dirX - lateral * dirY, dirY + lateral * dirX, rowZ, baseMap, basePortals, null);
                softwareMarchExact(s, RC_MAX_DISTANCE);
                fr.renderPixels[y * width + x] = s.hitKind == 0 ? ceilingBaseColor : shadeMarch(fr, s, lightDistance);
            }
        });
        copyRenderBuffer(fr);
    }
    private void renderSoftwareRayCastingColumns() {
        final int width = fr.renderWidth, height = fr.renderHeight;
        final double dirX = Math.cos(fr.cameraAngle), dirY = Math.sin(fr.cameraAngle);
        final double planeLength = Math.tan(Math.PI / 3.0 / 2.0);
        final double focal = width / (2.0 * planeLength), halfHeight = height / 2.0;
        ensureRaycastCaches(fr, width, height);
        fillRowCaches(fr, focal, halfHeight, 0.0, 1.0);
        final int[][] baseMap = fr.map;
        final PortalData basePortals = fr.portals;
        final double lightDistance = effectiveFlashlightDistance(false);
        final double z0 = fr.cameraHeight;
        final double[] rowZ = fr.rowZACache;
        parallelRange(fr, 0, width, x -> {
            final double lateral = fr.cameraXCache[x] * planeLength;
            final double hx = dirX - lateral * dirY, hy = dirY + lateral * dirX;
            final double hl = Math.sqrt(hx * hx + hy * hy);
            ColumnRay c = new ColumnRay();
            softwareMarchColumn(c, fr.playerX, fr.playerY, hx / hl, hy / hl, baseMap, basePortals, RC_MAX_DISTANCE);
            RayState rs = new RayState();
            for (int y = 0; y < height; y++) {
                fr.renderPixels[y * width + x] = shadeColumnRow(fr, c, rs, z0, rowZ[y] / hl, lightDistance);
            }
        });
        copyRenderBuffer(fr);
    }
    private void renderSoftwareRayCastingHyp() {
        final int width = fr.renderWidth, height = fr.renderHeight;
        final double dirX = Math.cos(fr.cameraAngle), dirY = Math.sin(fr.cameraAngle);
        final double focal = height, horizon = height / 2.0 + fr.cameraPitch * height / 2.0;
        final double step = softwareRayCastStep();
        final int[][] baseMap = fr.map;
        final double lightDistance = effectiveFlashlightDistance(true);
        parallelRange(fr, 0, height, y -> {
            RayState s = new RayState();
            for (int x = 0; x < width; x++) {
                s.resetHyp(fr.playerX, fr.playerY, fr.cameraHeight, hypPixelRay(fr.cameraXCache[x], y + 0.5, dirX, dirY, focal, horizon), baseMap, null);
                softwareMarchNaive(s, step, RC_MAX_DISTANCE);
                fr.renderPixels[y * width + x] = s.hitKind == 0 ? ceilingBaseColor : shadeMarch(fr, s, lightDistance);
            }
        });
        copyRenderBuffer(fr);
    }
    private void softwareMarchColumn(ColumnRay c, double px, double py, double ux, double uy, int[][] map0, PortalData portals0, double maxDistance) {
        RayState s = c.ray;
        s.x = px; s.y = py; s.dx = ux; s.dy = uy; s.dz = 0; s.dist = 0;
        s.map = map0; s.portals = portals0; s.field = null; s.hops = 0; s.hitKind = 0;
        c.segCount = 0; c.finCount = 0; c.exhausted = false;
        c.wallDist = Double.POSITIVE_INFINITY; c.exitD = Double.POSITIVE_INFINITY;
        addColumnSegment(c, 0, s);
        double dist = 0;
        int cx = (int) Math.floor(s.x), cy = (int) Math.floor(s.y);
        while (dist < maxDistance) {
            int[][] m = s.map;
            double tX = ux > 0 ? (cx + 1 - s.x) / ux : ux < 0 ? (s.x - cx) / -ux : Double.POSITIVE_INFINITY;
            double tY = uy > 0 ? (cy + 1 - s.y) / uy : uy < 0 ? (s.y - cy) / -uy : Double.POSITIVE_INFINITY;
            final boolean corner = Math.abs(tX - tY) <= 1e-9;
            boolean alongX = tX < tY;
            double t = Math.min(tX, tY);
            if (dist + t > maxDistance) { s.x += ux * (maxDistance - dist); s.y += uy * (maxDistance - dist); return; }
            s.x += ux * t; s.y += uy * t; dist += t;
            if (corner) { cx += ux > 0 ? 1 : -1; cy += uy > 0 ? 1 : -1; alongX = true; }
            else if (alongX) cx += ux > 0 ? 1 : -1; else cy += uy > 0 ? 1 : -1;
            if (cx < 0 || cy < 0 || cx >= m[0].length || cy >= m.length) { c.exitD = dist; return; }
            int type = m[cy][cx];
            if (type == MazeGenerator.PORTAL && enterPortal(fr, s, cx, cy)) {
                addColumnSegment(c, dist, s);
                ux = s.dx; uy = s.dy;
                cx = (int) Math.floor(s.x); cy = (int) Math.floor(s.y);
                continue;
            }
            if (type == 1 || type == 2) {
                int side = alongX ? 0 : 1;
                double u = side == 0 ? s.y - Math.floor(s.y) : s.x - Math.floor(s.x);
                if ((side == 0 && ux < 0) || (side == 1 && uy > 0)) u = 1.0 - u;
                u = Math.clamp(u, 0.0, 0.999999);
                if (type == 1) {
                    c.wallDist = dist; c.wallTexU = u; c.wallSide = side;
                    return;
                }
                if (c.finCount < ColumnRay.MAX_FIN) {
                    int k = c.finCount++;
                    c.finishDist[k] = dist; c.finishTexU[k] = u; c.finishSide[k] = side;
                }
            }
        }
    }
    private static double softwareRayCastStep() { return cfgQuality == 0 ? 0.15 : cfgQuality == 1 ? 0.08 : 0.04; }
    private void softwareMarchNaive(RayState s, double step, double maxDistance) {
        s.hitKind = 0;
        final int[][] m = s.map;
        final int mapW = m[0].length, mapH = m.length;
        final double plane = Math.min(s.floorD, s.ceilD);
        final int maxSteps = (int) Math.ceil(maxDistance / step) + 1;
        int prevCx = (int) Math.floor(s.x), prevCy = (int) Math.floor(s.y);
        int faceSide = 0;
        double faceCoord = 0.0;
        for (int i = 0; i < maxSteps && s.dist < maxDistance; i++) {
            final double px = s.x, py = s.y;
            double advance = Math.min(step, plane - s.dist);
            s.x += s.dx * advance; s.y += s.dy * advance; s.dist += advance;
            if (s.dist >= plane) {
                boolean floor = s.floorD <= s.ceilD;
                s.z = floor ? 0.0 : 1.0;
                s.hitKind = floor ? HIT_FLOOR : HIT_CEIL;
                s.hitX = s.x; s.hitY = s.y; s.hitDist = hypPerceivedOf(s);
                return;
            }
            int cx = (int) Math.floor(s.x), cy = (int) Math.floor(s.y);
            if (cx < 0 || cy < 0 || cx >= mapW || cy >= mapH) return;
            if (cx != prevCx || cy != prevCy) {
                double tX = cx != prevCx ? ((s.dx > 0 ? cx : cx + 1) - px) / s.dx : Double.POSITIVE_INFINITY;
                double tY = cy != prevCy ? ((s.dy > 0 ? cy : cy + 1) - py) / s.dy : Double.POSITIVE_INFINITY;
                if (tX < tY) {
                    faceSide = 0;
                    faceCoord = py + s.dy * tX;
                } else {
                    faceSide = 1;
                    faceCoord = px + s.dx * tY;
                }
                prevCx = cx; prevCy = cy;
            }
            s.z = hypHeight(s);
            int type = m[cy][cx];
            if ((type == 1 || type == 2) && s.z >= 0.0 && s.z <= wallHeightForType(type)) {
                s.hitKind = HIT_WALL;
                s.hitType = type;
                s.hitDist = hypPerceivedOf(s);
                s.hitSide = faceSide;
                double u = faceCoord - Math.floor(faceCoord);
                if ((faceSide == 0 && s.dx < 0) || (faceSide == 1 && s.dy > 0)) u = 1.0 - u;
                s.hitU = Math.clamp(u, 0.0, 0.999999);
                setWallFraction(s);
                return;
            }
        }
    }
    private void softwareMarchExact(RayState s, double maxDistance) {
        s.hitKind = 0;
        final double hl = Math.sqrt(s.dx * s.dx + s.dy * s.dy);
        if (hl < 1e-9) {
            double t = s.dz < 0 ? s.z / -s.dz : s.dz > 0 ? (1.0 - s.z) / s.dz : Double.POSITIVE_INFINITY;
            if (!(t <= maxDistance)) return;
            s.z = s.dz < 0 ? 0.0 : 1.0;
            s.dist = t;
            setPlaneHit(s, s.dz < 0 ? HIT_FLOOR : HIT_CEIL);
            return;
        }
        final double scale = 1.0 / hl;
        final double sigma = s.dz / hl;
        final double maxH = maxDistance / scale;
        double d = 0;
        double zc = s.z;
        int cx = (int) Math.floor(s.x), cy = (int) Math.floor(s.y);
        int side = 0;
        for (int guard = 0; guard < 8192 && d < maxH; guard++) {
            final int[][] m = s.map;
            if (cx < 0 || cy < 0 || cx >= m[0].length || cy >= m.length) return;
            final double ux = s.dx / hl, uy = s.dy / hl;
            double tX = ux > 0 ? (cx + 1 - s.x) / ux : ux < 0 ? (s.x - cx) / -ux : Double.POSITIVE_INFINITY;
            double tY = uy > 0 ? (cy + 1 - s.y) / uy : uy < 0 ? (s.y - cy) / -uy : Double.POSITIVE_INFINITY;
            final boolean corner = Math.abs(tX - tY) <= 1e-9;
            boolean alongX = tX < tY;
            double boundary = Math.min(tX, tY);
            double seg = Math.min(boundary, maxH - d);

            double tPlane = sigma < 0 ? zc / -sigma : sigma > 0 ? (1.0 - zc) / sigma : Double.POSITIVE_INFINITY;
            if (tPlane <= seg) {
                s.x += ux * tPlane; s.y += uy * tPlane;
                s.z = sigma < 0 ? 0.0 : 1.0;
                s.dist = (d + tPlane) * scale;
                setPlaneHit(s, sigma < 0 ? HIT_FLOOR : HIT_CEIL);
                return;
            }

            s.x += ux * seg; s.y += uy * seg;
            zc += sigma * seg;
            d += seg;
            s.z = zc;
            if (d >= maxH) return;

            if (corner) {
                cx += ux > 0 ? 1 : -1; cy += uy > 0 ? 1 : -1;
                s.x = ux > 0 ? cx : cx + 1;
                s.y = uy > 0 ? cy : cy + 1;
                side = 0;
            } else if (alongX) {
                cx += ux > 0 ? 1 : -1; side = 0;
                s.x = ux > 0 ? cx : cx + 1;
            } else {
                cy += uy > 0 ? 1 : -1; side = 1;
                s.y = uy > 0 ? cy : cy + 1;
            }
            if (cx < 0 || cy < 0 || cx >= m[0].length || cy >= m.length) return;

            int type = m[cy][cx];
            if (type == MazeGenerator.PORTAL && enterPortal(fr, s, cx, cy)) {
                cx = (int) Math.floor(s.x);
                cy = (int) Math.floor(s.y);
                continue;
            }
            if ((type == 1 || type == 2) && zc <= wallHeightForType(type)) {
                s.hitKind = HIT_WALL;
                s.hitType = type;
                s.hitSide = side;
                s.dist = d * scale;
                s.hitDist = s.dist;
                double u = side == 0 ? s.y - Math.floor(s.y) : s.x - Math.floor(s.x);
                if ((side == 0 && s.dx < 0) || (side == 1 && s.dy > 0)) u = 1 - u;
                s.hitU = Math.clamp(u, 0.0, 0.999999);
                setWallFraction(s);
                return;
            }
        }
    }
}