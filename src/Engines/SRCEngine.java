package Engines;
import Project.Game.*;
import Project.Game.SkyCeiling;
import Project.Helpers.*;
import Project.OpenCl.*;
import java.awt.*;
import java.util.function.IntConsumer;

public final class SRCEngine implements RenderEngine {
    //region Variables
    //region Render Configuration
    static volatile int cfgQuality = 1, cfgOutW = 960, cfgOutH = 540;
    static volatile MazeGenerator.GeometryMode cfgGeometry = MazeGenerator.GeometryMode.EUCLIDEAN;
    static volatile double cfgCurvature = HyperbolicMath.DEFAULT_CURVATURE;
    private RenderFrame fr;
    //endregion
    //region Caches
    static final SdfCache sdfCache = new SdfCache();
    //endregion
    //region Render Constants
    static final String ID = "raycasting";
    static final int wallBaseColor = 0xECD485;
    static final int ceilingBaseColor = 0x816E1E;
    static final int floorBaseColor = 0xD3AF63;
    static final int finishBaseColor = 0x33FF66;
    private static final double flashlightDistance = 2;
    private static final double HYP_PLANE_LENGTH = Math.tan(Math.toRadians(140.0) / 2.0);
    static final int HIT_WALL = 1, HIT_FLOOR = 2, HIT_CEIL = 3;
    static final double RM_MAX_DISTANCE = 48;
    private static final double RC_MAX_DISTANCE = 48;
    private static final int SDF_SUB = 8;
    static final int SDF_TILE = 256;
    //endregion
    //endregion

    //region Engine Identity
    @Override public String id() { return ID; }
    @Override public String displayName() { return "Ray Casting"; }
    @Override public int order() { return 3; }
    @Override public String hintKey() { return "mm.engine_rc"; }
    @Override public String qualityHintKey() { return "mm.quality_rc"; }
    @Override public boolean hasHyperbolicGeometry() { return true; }
    @Override public boolean hasOpenCL() { return true; }
    //endregion

    //region Frame Lifecycle
    static void syncConfig(RenderFrame renderFrame) {
        cfgQuality = renderFrame.quality;
        cfgGeometry = renderFrame.geometry;
        cfgCurvature = renderFrame.curvature;
        cfgOutW = renderFrame.outputWidth;
        cfgOutH = renderFrame.outputHeight;
    }
    @Override public void render(RenderFrame frame) {
        fr = frame;
        syncConfig(frame);
        if (hasOpenCL() && cfgGeometry != MazeGenerator.GeometryMode.WRONG && Gpu.tryRender(frame)) {
            copyRenderBuffer(frame);
            return;
        }
        renderSoftwareRayCasting();
    }
    static void copyRenderBuffer(RenderFrame renderFrame) {
        if (renderFrame.renderWidth == renderFrame.bufferedImage.getWidth() && renderFrame.renderHeight == renderFrame.bufferedImage.getHeight()) {
            System.arraycopy(renderFrame.renderPixels, 0, renderFrame.pixels, 0, renderFrame.pixels.length);
        } else {
            Graphics2D g2d = renderFrame.bufferedImage.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g2d.drawImage(renderFrame.renderImage, 0, 0, cfgOutW, cfgOutH, null);
            g2d.dispose();
        }
    }
    static void parallelRange(RenderFrame renderFrame, int end, IntConsumer task) {
        if (end <= 0) return;
        renderFrame.renderWorkers.execute(0, end, task);
    }
    static boolean is3D(RenderFrame renderFrame) { return renderFrame.map3D != null && renderFrame.map3D.length > 1; }
    //endregion

    //region Ray Setup Caches
    static void ensureRaycastCaches(RenderFrame renderFrame, int screenWidth, int screenHeight) {
        if (renderFrame.wallTopCache == null || renderFrame.wallTopCache.length != screenWidth) {
            renderFrame.wallTopCache = new int[screenWidth]; renderFrame.wallBottomCache = new int[screenWidth];
            renderFrame.portalFloorEdgeCache = new double[screenWidth]; renderFrame.portalCeilEdgeCache = new double[screenWidth];
        }
        if (renderFrame.rowInvACache == null || renderFrame.rowInvACache.length != screenHeight) renderFrame.rowInvACache = new double[screenHeight]; renderFrame.rowZACache = new double[screenHeight];
    }
    static void fillRowCaches(RenderFrame renderFrame, double focal, double halfHeight, double pitchSin, double pitchCos) {
        final int screenHeight = renderFrame.renderHeight;
        for (int y = 0; y < screenHeight; y++) {
            double v = (halfHeight - (y + 0.5)) / focal;
            double a = pitchCos - pitchSin * v;
            if (Math.abs(a) < 0.02) a = a < 0 ? -0.02 : 0.02;
            renderFrame.rowInvACache[y] = 1.0 / a;
            renderFrame.rowZACache[y] = (pitchSin + pitchCos * v) / a;
        }
    }
    //endregion

    //region Software Ray Casting
    //region Column Rendering
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
        parallelRange(fr, height, y -> {
            RayState rayState = new RayState();
            double rowZ = rowZA[y], rowScale = rowInvA[y];
            for (int x = 0; x < width; x++) {
                double lateral = fr.cameraXCache[x] * planeLength * rowScale;
                rayState.reset(fr.playerX, fr.playerY, fr.cameraHeight, dirX - lateral * dirY, dirY + lateral * dirX, rowZ, baseMap, basePortals);
                softwareMarchExact(rayState);
                fr.renderPixels[y * width + x] = rayState.hitKind == 0 ? missColor(fr, rayState) : shadeMarch(fr, rayState, lightDistance);
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
        parallelRange(fr, width, x -> {
            final double lateral = fr.cameraXCache[x] * planeLength;
            final double hx = dirX - lateral * dirY, hy = dirY + lateral * dirX;
            final double hl = Math.sqrt(hx * hx + hy * hy);
            ColumnRay columnRay = new ColumnRay();
            softwareMarchColumn(columnRay, fr.playerX, fr.playerY, hx / hl, hy / hl, baseMap, basePortals);
            RayState rayState = new RayState();
            for (int y = 0; y < height; y++) {
                fr.renderPixels[y * width + x] = shadeColumnRow(fr, columnRay, rayState, z0, rowZ[y] / hl, lightDistance);
            }
        });
        copyRenderBuffer(fr);
    }
    private void softwareMarchColumn(ColumnRay columnRay, double px, double py, double ux, double uy, int[][] map0, PortalData portals0) {
        RayState rayState = columnRay.ray;
        rayState.x = px; rayState.y = py; rayState.dx = ux; rayState.dy = uy; rayState.dz = 0; rayState.dist = 0;
        rayState.map = map0; rayState.portals = portals0; rayState.field = null; rayState.hops = 0; rayState.hitKind = 0;
        columnRay.segCount = 0; columnRay.finCount = 0; columnRay.exhausted = false;
        columnRay.wallDist = Double.POSITIVE_INFINITY; columnRay.exitD = Double.POSITIVE_INFINITY;
        addColumnSegment(columnRay, 0, rayState);
        double dist = 0;
        int cx = (int) Math.floor(rayState.x), cy = (int) Math.floor(rayState.y);
        while (dist < SRCEngine.RC_MAX_DISTANCE) {
            int[][] m = rayState.map;
            double tX = ux > 0 ? (cx + 1 - rayState.x) / ux : ux < 0 ? (rayState.x - cx) / -ux : Double.POSITIVE_INFINITY;
            double tY = uy > 0 ? (cy + 1 - rayState.y) / uy : uy < 0 ? (rayState.y - cy) / -uy : Double.POSITIVE_INFINITY;
            final boolean corner = Math.abs(tX - tY) <= 1e-9;
            boolean alongX = tX < tY;
            double t = Math.min(tX, tY);
            if (dist + t > SRCEngine.RC_MAX_DISTANCE) { rayState.x += ux * (SRCEngine.RC_MAX_DISTANCE - dist); rayState.y += uy * (SRCEngine.RC_MAX_DISTANCE - dist); return; }
            rayState.x += ux * t; rayState.y += uy * t; dist += t;
            if (corner) { cx += ux > 0 ? 1 : -1; cy += uy > 0 ? 1 : -1; alongX = true; }
            else if (alongX) cx += ux > 0 ? 1 : -1; else cy += uy > 0 ? 1 : -1;
            if (cx < 0 || cy < 0 || cx >= m[0].length || cy >= m.length) { columnRay.exitD = dist; return; }
            int type = m[cy][cx];
            if (type == MazeGenerator.PORTAL && enterPortal(fr, rayState, cx, cy)) {
                addColumnSegment(columnRay, dist, rayState);
                ux = rayState.dx; uy = rayState.dy;
                cx = (int) Math.floor(rayState.x); cy = (int) Math.floor(rayState.y);
                continue;
            }
            if (type == 1 || type == 2) {
                int side = alongX ? 0 : 1;
                double u = side == 0 ? rayState.y - Math.floor(rayState.y) : rayState.x - Math.floor(rayState.x);
                if ((side == 0 && ux < 0) || (side == 1 && uy > 0)) u = 1.0 - u;
                u = Math.clamp(u, 0.0, 0.999999);
                if (type == 1) {
                    columnRay.wallDist = dist; columnRay.wallTexU = u; columnRay.wallSide = side;
                    return;
                }
                if (columnRay.finCount < ColumnRay.MAX_FIN) {
                    int k = columnRay.finCount++;
                    columnRay.finishDist[k] = dist; columnRay.finishTexU[k] = u; columnRay.finishSide[k] = side;
                }
            }
        }
    }
    static void addColumnSegment(ColumnRay columnRay, double dist, RayState rayState) {
        int k = columnRay.segCount++;
        columnRay.segStartDist[k] = dist; columnRay.segStartX[k] = rayState.x; columnRay.segStartY[k] = rayState.y; columnRay.segDirX[k] = rayState.dx; columnRay.segDirY[k] = rayState.dy;
        columnRay.segMap[k] = rayState.map; columnRay.segDistField[k] = rayState.field;
    }
    static int shadeColumnRow(RenderFrame renderFrame, ColumnRay columnRay, RayState rayState, double z0, double sigma, double lightDistance) {
        final double scale = Math.sqrt(1.0 + sigma * sigma);
        final double planeD = sigma < 0 ? z0 / -sigma : sigma > 0 ? (1.0 - z0) / sigma : Double.POSITIVE_INFINITY;
        final boolean floor = sigma < 0;
        final double nearest = Math.min(Math.min(columnRay.wallDist, planeD), columnRay.exitD);
        for (int k = 0; k < columnRay.finCount; k++) {
            double d = columnRay.finishDist[k];
            if (d >= nearest) break;
            double z = z0 + sigma * d;
            if (z <= 0.5) {
                rayState.hitKind = HIT_WALL; rayState.hitType = 2; rayState.hitSide = columnRay.finishSide[k]; rayState.hitU = columnRay.finishTexU[k];
                rayState.hitDist = d * scale;
                rayState.hitFrac = Math.clamp((0.5 - z) / 0.5, 0.0, 0.999999);
                return shadeMarch(renderFrame, rayState, lightDistance);
            }
        }
        if (columnRay.wallDist < planeD && columnRay.wallDist < columnRay.exitD) {
            double z = z0 + sigma * columnRay.wallDist;
            rayState.hitKind = HIT_WALL; rayState.hitType = 1; rayState.hitSide = columnRay.wallSide; rayState.hitU = columnRay.wallTexU;
            rayState.hitDist = columnRay.wallDist * scale;
            rayState.hitFrac = Math.clamp(1.0 - z, 0.0, 0.999999);
            return shadeMarch(renderFrame, rayState, lightDistance);
        }
        if (Double.isFinite(planeD) && planeD < columnRay.exitD && planeD < RM_MAX_DISTANCE) {
            int k = 0;
            for (int j = 1; j < columnRay.segCount && columnRay.segStartDist[j] <= planeD; j++) k = j;
            double along = planeD - columnRay.segStartDist[k];
            rayState.hitKind = floor ? HIT_FLOOR : HIT_CEIL;
            rayState.dx = columnRay.ray.dx; rayState.dy = columnRay.ray.dy; rayState.dz = sigma;
            rayState.hitX = columnRay.segStartX[k] + columnRay.segDirX[k] * along;
            rayState.hitY = columnRay.segStartY[k] + columnRay.segDirY[k] * along;
            rayState.hitDist = planeD * scale;
            rayState.map = columnRay.segMap[k];
            return shadeMarch(renderFrame, rayState, lightDistance);
        }
        return renderFrame.skyCeiling ? SkyCeiling.colorDir(columnRay.ray.dx, columnRay.ray.dy, sigma, renderFrame.skyTime) : ceilingBaseColor;
    }
    //endregion
    //region March Steps
    private static double softwareRayCastStep() { return cfgQuality == 0 ? 0.15 : cfgQuality == 1 ? 0.08 : 0.04; }
    private void softwareMarchNaive(RayState rayState, double step) {
        rayState.hitKind = 0;
        final int[][] m = rayState.map;
        final int mapW = m[0].length, mapH = m.length;
        final double plane = Math.min(rayState.floorD, rayState.ceilD);
        final int maxSteps = (int) Math.ceil(SRCEngine.RC_MAX_DISTANCE / step) + 1;
        int prevCx = (int) Math.floor(rayState.x), prevCy = (int) Math.floor(rayState.y);
        int faceSide = 0;
        double faceCoord = 0.0;
        for (int i = 0; i < maxSteps && rayState.dist < SRCEngine.RC_MAX_DISTANCE; i++) {
            final double px = rayState.x, py = rayState.y;
            double advance = Math.min(step, plane - rayState.dist);
            rayState.x += rayState.dx * advance; rayState.y += rayState.dy * advance; rayState.dist += advance;
            if (rayState.dist >= plane) {
                boolean floor = rayState.floorD <= rayState.ceilD;
                rayState.z = floor ? 0.0 : 1.0;
                rayState.hitKind = floor ? HIT_FLOOR : HIT_CEIL;
                rayState.hitX = rayState.x; rayState.hitY = rayState.y; rayState.hitDist = hypPerceivedOf(rayState);
                return;
            }
            int cx = (int) Math.floor(rayState.x), cy = (int) Math.floor(rayState.y);
            if (cx < 0 || cy < 0 || cx >= mapW || cy >= mapH) return;
            if (cx != prevCx || cy != prevCy) {
                double tX = cx != prevCx ? ((rayState.dx > 0 ? cx : cx + 1) - px) / rayState.dx : Double.POSITIVE_INFINITY;
                double tY = cy != prevCy ? ((rayState.dy > 0 ? cy : cy + 1) - py) / rayState.dy : Double.POSITIVE_INFINITY;
                if (tX < tY) {
                    faceSide = 0;
                    faceCoord = py + rayState.dy * tX;
                } else {
                    faceSide = 1;
                    faceCoord = px + rayState.dx * tY;
                }
                prevCx = cx; prevCy = cy;
            }
            rayState.z = hypHeight(rayState);
            int type = m[cy][cx];
            if ((type == 1 || type == 2) && rayState.z >= 0.0 && rayState.z <= wallHeightForType(type)) {
                rayState.hitKind = HIT_WALL;
                rayState.hitType = type;
                rayState.hitDist = hypPerceivedOf(rayState);
                rayState.hitSide = faceSide;
                double u = faceCoord - Math.floor(faceCoord);
                if ((faceSide == 0 && rayState.dx < 0) || (faceSide == 1 && rayState.dy > 0)) u = 1.0 - u;
                rayState.hitU = Math.clamp(u, 0.0, 0.999999);
                setWallFraction(rayState);
                return;
            }
        }
    }
    private void softwareMarchExact(RayState rayState) {
        rayState.hitKind = 0;
        final double hl = Math.sqrt(rayState.dx * rayState.dx + rayState.dy * rayState.dy);
        if (hl < 1e-9) {
            double t = rayState.dz < 0 ? rayState.z / -rayState.dz : rayState.dz > 0 ? (1.0 - rayState.z) / rayState.dz : Double.POSITIVE_INFINITY;
            if (!(t <= SRCEngine.RC_MAX_DISTANCE)) return;
            rayState.z = rayState.dz < 0 ? 0.0 : 1.0;
            rayState.dist = t;
            setPlaneHit(rayState, rayState.dz < 0 ? HIT_FLOOR : HIT_CEIL);
            return;
        }
        final double scale = 1.0 / hl;
        final double sigma = rayState.dz / hl;
        final double maxH = SRCEngine.RC_MAX_DISTANCE / scale;
        double d = 0;
        double zc = rayState.z;
        int cx = (int) Math.floor(rayState.x), cy = (int) Math.floor(rayState.y);
        int side;
        for (int guard = 0; guard < 8192 && d < maxH; guard++) {
            final int[][] m = rayState.map;
            if (cx < 0 || cy < 0 || cx >= m[0].length || cy >= m.length) return;
            final double ux = rayState.dx / hl, uy = rayState.dy / hl;
            double tX = ux > 0 ? (cx + 1 - rayState.x) / ux : ux < 0 ? (rayState.x - cx) / -ux : Double.POSITIVE_INFINITY;
            double tY = uy > 0 ? (cy + 1 - rayState.y) / uy : uy < 0 ? (rayState.y - cy) / -uy : Double.POSITIVE_INFINITY;
            final boolean corner = Math.abs(tX - tY) <= 1e-9;
            boolean alongX = tX < tY;
            double boundary = Math.min(tX, tY);
            double seg = Math.min(boundary, maxH - d);

            double tPlane = sigma < 0 ? zc / -sigma : sigma > 0 ? (1.0 - zc) / sigma : Double.POSITIVE_INFINITY;
            if (tPlane <= seg) {
                rayState.x += ux * tPlane; rayState.y += uy * tPlane;
                rayState.z = sigma < 0 ? 0.0 : 1.0;
                rayState.dist = (d + tPlane) * scale;
                setPlaneHit(rayState, sigma < 0 ? HIT_FLOOR : HIT_CEIL);
                return;
            }

            rayState.x += ux * seg; rayState.y += uy * seg;
            zc += sigma * seg;
            d += seg;
            rayState.z = zc;
            if (d >= maxH) return;

            if (corner) {
                cx += ux > 0 ? 1 : -1; cy += uy > 0 ? 1 : -1;
                rayState.x = ux > 0 ? cx : cx + 1;
                rayState.y = uy > 0 ? cy : cy + 1;
                side = 0;
            } else if (alongX) {
                cx += ux > 0 ? 1 : -1; side = 0;
                rayState.x = ux > 0 ? cx : cx + 1;
            } else {
                cy += uy > 0 ? 1 : -1; side = 1;
                rayState.y = uy > 0 ? cy : cy + 1;
            }
            if (cx < 0 || cy < 0 || cx >= m[0].length || cy >= m.length) return;

            int type = m[cy][cx];
            if (type == MazeGenerator.PORTAL && enterPortal(fr, rayState, cx, cy)) {
                cx = (int) Math.floor(rayState.x);
                cy = (int) Math.floor(rayState.y);
                continue;
            }
            if ((type == 1 || type == 2) && zc <= wallHeightForType(type)) {
                rayState.hitKind = HIT_WALL;
                rayState.hitType = type;
                rayState.hitSide = side;
                rayState.dist = d * scale;
                rayState.hitDist = rayState.dist;
                double u = side == 0 ? rayState.y - Math.floor(rayState.y) : rayState.x - Math.floor(rayState.x);
                if ((side == 0 && rayState.dx < 0) || (side == 1 && rayState.dy > 0)) u = 1 - u;
                rayState.hitU = Math.clamp(u, 0.0, 0.999999);
                setWallFraction(rayState);
                return;
            }
        }
    }
    static void setWallFraction(RayState rayState) {
        double wallH = wallHeightForType(rayState.hitType);
        rayState.hitFrac = Math.clamp((wallH - rayState.z) / wallH, 0.0, 0.999999);
    }
    static void setPlaneHit(RayState rayState, int kind) {
        rayState.hitKind = kind;
        rayState.hitX = rayState.x;
        rayState.hitY = rayState.y;
        rayState.hitDist = rayState.dist;
    }
    static boolean enterPortal(RenderFrame renderFrame, RayState rayState, int cx, int cy) {
        if (rayState.hops >= 8 || rayState.portals == null) return false;
        PortalData.Portal portal = rayState.portals.get(cx, cy);
        if (portal == null || !PortalData.entersFromCorrectSide(portal, rayState.dx, rayState.dy)) return false;
        double horizontal = Math.sqrt(rayState.dx * rayState.dx + rayState.dy * rayState.dy);
        double[] warped = PortalData.warp(portal, rayState.x, rayState.y, rayState.dx, rayState.dy);
        double newHorizontal = Math.sqrt(warped[2] * warped[2] + warped[3] * warped[3]);
        rayState.x = warped[0];
        rayState.y = warped[1];
        if (newHorizontal > 1e-9) {
            rayState.dx = warped[2] * horizontal / newHorizontal;
            rayState.dy = warped[3] * horizontal / newHorizontal;
        }
        if (renderFrame.map3D != null && portal.linkedFloor >= 0 && portal.linkedFloor < renderFrame.map3D.length) {
            rayState.map = renderFrame.map3D[portal.linkedFloor];
            rayState.portals = renderFrame.floorPortalsAll[portal.linkedFloor];
            if (rayState.field != null) rayState.field = sdfFieldFor(rayState.map);
        }
        rayState.hops++;
        return true;
    }
    static double wallHeightForType(int type) { return type == 2 ? 0.5 : 1.0; }
    //endregion
    //endregion

    //region Hyperbolic Geometry
    static double hypHeight(RayState rayState) {
        return rayState.hypZ0 + rayState.slope * HyperbolicMath.hyperbolicDistance(rayState.dist * rayState.invL, cfgCurvature); }
    static double hypPerceivedOf(RayState rayState) { return HyperbolicMath.hyperbolicDistance(rayState.dist * rayState.invL, cfgCurvature); }
    static double[] hypPixelRay(double cx, double py, double dirX, double dirY, double focal, double horizon) {
        double rdX = dirX - dirY * HYP_PLANE_LENGTH * cx, rdY = dirY + dirX * HYP_PLANE_LENGTH * cx;
        double L = Math.sqrt(rdX * rdX + rdY * rdY);
        return new double[]{rdX / L, rdY / L, L, -(py - horizon) / focal};
    }
    private static double hypFloorDistance(double z0, double slope, double L) {
        if (slope >= 0 || z0 <= 1e-9) return Double.POSITIVE_INFINITY;
        return L * HyperbolicMath.inverseHyperbolicDistance(z0 / -slope, cfgCurvature);
    }
    private static double hypCeilDistance(double z0, double slope, double L) {
        if (slope <= 0 || z0 >= 1.0 - 1e-9) return Double.POSITIVE_INFINITY;
        return L * HyperbolicMath.inverseHyperbolicDistance((1.0 - z0) / slope, cfgCurvature);
    }
    static double effectiveFlashlightDistance(boolean hyperbolic) { return hyperbolic ? flashlightDistance * 1.6 : flashlightDistance; }
    private void renderSoftwareRayCastingHyp() {
        final int width = fr.renderWidth, height = fr.renderHeight;
        final double dirX = Math.cos(fr.cameraAngle), dirY = Math.sin(fr.cameraAngle);
        final double horizon = height / 2.0 + fr.cameraPitch * height / 2.0;
        final double step = softwareRayCastStep();
        final int[][] baseMap = fr.map;
        final double lightDistance = effectiveFlashlightDistance(true);
        parallelRange(fr, height, y -> {
            RayState rayState = new RayState();
            for (int x = 0; x < width; x++) {
                rayState.resetHyp(fr.playerX, fr.playerY, fr.cameraHeight, hypPixelRay(fr.cameraXCache[x], y + 0.5, dirX, dirY, height, horizon), baseMap);
                softwareMarchNaive(rayState, step);
                fr.renderPixels[y * width + x] = rayState.hitKind == 0 ? missColor(fr, rayState) : shadeMarch(fr, rayState, lightDistance);
            }
        });
        copyRenderBuffer(fr);
    }
    //endregion

    //region Signed Distance Fields
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
                    field = new SDFField(m);
                    fields.put(m, field);
                }
                return field;
            }
        }
        synchronized void clear() {
            for (SDFField sdfField : fields.values()) sdfField.dropTiles();
            fields.clear();
        }
    }
    public static final class SDFField {
        final int cols, rows, tilesX, tilesY;
        final boolean hasPortals, hasFinish;
        private final java.util.concurrent.atomic.AtomicReferenceArray<double[]> wallTiles, finishTiles;

        SDFField(int[][] m) {
            int mh = m.length;
            int mw = m[0].length;
            cols = mw * SDF_SUB + 1;
            rows = mh * SDF_SUB + 1;
            tilesX = (cols + SDF_TILE - 1) / SDF_TILE;
            tilesY = (rows + SDF_TILE - 1) / SDF_TILE;
            wallTiles = new java.util.concurrent.atomic.AtomicReferenceArray<>(tilesX * tilesY);
            finishTiles = new java.util.concurrent.atomic.AtomicReferenceArray<>(tilesX * tilesY);
            boolean portals = false, finish = false;
            for (int[] ints : m)
                for (int x = 0; x < mw; x++) {
                    int t = ints[x];
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
    static SDFField sdfFieldFor(int[][] m) {
        return sdfCache.fieldFor(m);
    }
    //endregion

    //region Shading
    static int missColor(RenderFrame renderFrame, RayState rayState) {
        return renderFrame.skyCeiling ? SkyCeiling.colorDir(rayState.dx, rayState.dy, rayState.dz, renderFrame.skyTime) : ceilingBaseColor;
    }
    static int shadeMarch(RenderFrame renderFrame, RayState rayState, double lightDistance) {
        if (rayState.hitKind == HIT_WALL) {
            boolean finish = rayState.hitType == 2;
            RuntimeTexture texture = finish ? renderFrame.finishTexture : renderFrame.wallTexture;
            int color;
            if (texture.width() > 0 && texture.height() > 0) {
                int tx = Math.min((int) (rayState.hitU * texture.width()), texture.width() - 1);
                int ty = Math.min((int) (rayState.hitFrac * texture.height()), texture.height() - 1);
                color = texture.pixels()[ty * texture.width() + tx];
            }
            else color = finish ? finishBaseColor : wallBaseColor;
            double brightness = Math.clamp(1.0 - rayState.hitDist / lightDistance, 0.03, 1.0);
            if (rayState.hitSide == 1) brightness *= 0.85;
            return applyBrightness(color, brightness);
        }
        boolean floor = rayState.hitKind == HIT_FLOOR;
        if (!floor && renderFrame.skyCeiling) return SkyCeiling.colorDir(rayState.dx, rayState.dy, rayState.dz, renderFrame.skyTime);
        int color = floor ? planeColor(renderFrame.floorTexture, rayState.hitX, rayState.hitY, floorBaseColor) : planeColor(renderFrame.ceilingTexture, rayState.hitX, rayState.hitY, ceilingBaseColor);
        if (floor && is3D(renderFrame)) color = tintStairs(color, rayState.hitX, rayState.hitY, rayState.map);
        double brightness = Math.clamp(1.0 - rayState.hitDist / lightDistance, 0.05, 1.0);
        return applyBrightness(color, brightness);
    }
    static int applyBrightness(int color, double brightness) {
        int r = (int) Math.min(255, ((color >> 16) & 255) * brightness);
        int g = (int) Math.min(255, ((color >> 8) & 255) * brightness);
        int b = (int) Math.min(255, (color & 255) * brightness);
        return (r << 16) | (g << 8) | b;
    }
    private static int tintColor(int color, int tint) {
        int r = (color >> 16) & 255, g = (color >> 8) & 255, b = color & 255;
        int tr = (tint >> 16) & 255, tg = (tint >> 8) & 255, tb = tint & 255;
        r = (int)(r * (1 - 0.45) + tr * 0.45); g = (int)(g * (1 - 0.45) + tg * 0.45); b = (int)(b * (1 - 0.45) + tb * 0.45);
        return (r << 16) | (g << 8) | b;
    }
    static int tintStairs(int color, double worldX, double worldY, int[][] floorMap) {
        int fx = (int) Math.floor(worldX), fy = (int) Math.floor(worldY);
        if (fx < 0 || fy < 0 || fy >= floorMap.length || fx >= floorMap[0].length) return color;
        int cell = floorMap[fy][fx];
        if (cell == 3) return tintColor(color, 0x3399FF);
        if (cell == 4) return tintColor(color, 0xFFAA33);
        return color;
    }
    //endregion

    //region Textures
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
    //endregion

    //region Ray State
    public static final class ColumnRay {
        static final int MAX_SEG = 10, MAX_FIN = 4;
        int segCount, finCount;
        final double[] segStartDist = new double[MAX_SEG], segStartX = new double[MAX_SEG], segStartY = new double[MAX_SEG];
        final double[] segDirX = new double[MAX_SEG], segDirY = new double[MAX_SEG];
        final int[][][] segMap = new int[MAX_SEG][][];
        final SDFField[] segDistField = new SDFField[MAX_SEG];
        final double[] finishDist = new double[MAX_FIN], finishTexU = new double[MAX_FIN];
        final int[] finishSide = new int[MAX_FIN];
        double wallDist, wallTexU;
        int wallSide;
        double exitD;
        final RayState ray = new RayState();
        boolean exhausted;
    }
    public static final class RayState {
        double x, y, z, dx, dy, dz, dist, hitU, hitFrac, hitX, hitY, hitDist, hypZ0, slope, L, invL, floorD, ceilD;
        int[][] map;
        PortalData portals;
        SDFField field;
        int hops, hitKind, hitType, hitSide;
        boolean hyp, exhausted;

        void resetHyp(double x0, double y0, double z0, double[] ray, int[][] m) {
            x = x0; y = y0; z = z0;
            dx = ray[0]; dy = ray[1]; dz = 0;
            L = ray[2]; invL = 1.0 / L;
            slope = ray[3];
            hypZ0 = z0;
            floorD = hypFloorDistance(z0, slope, L);
            ceilD = hypCeilDistance(z0, slope, L);
            dist = 0; map = m; portals = null; field = null; hops = 0; hitKind = 0;
            hyp = true; exhausted = false;
        }
        void reset(double x0, double y0, double z0, double rx, double ry, double rz, int[][] m, PortalData portalData) {
            double len = Math.sqrt(rx * rx + ry * ry + rz * rz);
            x = x0; y = y0; z = z0;
            dx = rx / len; dy = ry / len; dz = rz / len;
            dist = 0;
            map = m;
            portals = portalData;
            field = null;
            hops = 0;
            hitKind = 0;
        }
    }
    //endregion

    //region GPU Acceleration
    static final class Gpu {
        private final String source = Project.OpenCl.KernelSource.load(SRCEngine.class, SkyCeiling.GPU_SOURCE);
        private static Gpu instance;
        private static boolean disabled;
        private static final int DP_COUNT = 13, IP_COUNT = 14;
        private final GpuProgram program = new GpuProgram(source, "src_render");
        private GpuProgram.Buffer maps, grid, meta, portals, camX, dp, ip, out, wall, finish, floor, ceil;
        private int[] wallSrc, finishSrc, floorSrc, ceilSrc;

        static boolean tryRender(RenderFrame renderFrame) {
            synchronized (GpuRuntime.LOCK) { return tryRenderLocked(renderFrame); }
        }
        private static boolean tryRenderLocked(RenderFrame renderFrame) {
            if (disabled || GpuRuntime.session() == null) return false;
            try {
                if (instance == null) instance = new Gpu();
                instance.render(renderFrame);
                return true;
            }
            catch (RuntimeException | Error exception) {
                disabled = true;
                exception.printStackTrace(System.err);
                Project.OpenCl.GpuRuntime.reportFailure(ID, String.valueOf(exception));
                return false;
            }
        }
        private void render(RenderFrame renderFrame) {
            final int width = renderFrame.renderWidth, height = renderFrame.renderHeight;
            if (width <= 0 || height <= 0) return;
            if (renderFrame.renderPixels == null || renderFrame.renderPixels.length < width * height) throw new IllegalStateException("render buffer too small");
            if (renderFrame.cameraXCache == null || renderFrame.cameraXCache.length < width) throw new IllegalStateException("camera cache too small");

            Scene scene = Scene.of(renderFrame);
            maps = ensure(maps, 4L * scene.maps.length);
            grid = ensure(grid, 4L * scene.grid.length);
            meta = ensure(meta, 4L * scene.meta.length);
            portals = ensure(portals, 4L * scene.portals.length);
            camX = ensure(camX, 4L * width);
            dp = ensure(dp, 4L * DP_COUNT);
            ip = ensure(ip, 4L * IP_COUNT);
            out = ensure(out, 4L * width * height);

            program.write(maps, scene.maps, scene.maps.length);
            program.write(grid, scene.grid, scene.grid.length);
            program.write(meta, scene.meta, scene.meta.length);
            program.write(portals, scene.portals, scene.portals.length);
            program.write(camX, java.util.Arrays.copyOf(renderFrame.cameraXCache, width), width);
            program.write(dp, cameraParams(renderFrame, width, height), DP_COUNT);
            program.write(ip, ipValues(renderFrame, scene, width, height), IP_COUNT);

            GpuProgram.Buffer before = wall;
            wall = ensure(wall, 4L * renderFrame.wallTexture.pixels().length);
            if (wall != before || renderFrame.wallTexture.pixels() != wallSrc) { wallSrc = renderFrame.wallTexture.pixels(); program.write(wall, wallSrc, wallSrc.length); }
            before = finish;
            finish = ensure(finish, 4L * renderFrame.finishTexture.pixels().length);
            if (finish != before || renderFrame.finishTexture.pixels() != finishSrc) { finishSrc = renderFrame.finishTexture.pixels(); program.write(finish, finishSrc, finishSrc.length); }
            before = floor;
            floor = ensure(floor, 4L * renderFrame.floorTexture.pixels().length);
            if (floor != before || renderFrame.floorTexture.pixels() != floorSrc) { floorSrc = renderFrame.floorTexture.pixels(); program.write(floor, floorSrc, floorSrc.length); }
            before = ceil;
            ceil = ensure(ceil, 4L * renderFrame.ceilingTexture.pixels().length);
            if (ceil != before || renderFrame.ceilingTexture.pixels() != ceilSrc) { ceilSrc = renderFrame.ceilingTexture.pixels(); program.write(ceil, ceilSrc, ceilSrc.length); }

            boolean columns = renderFrame.cameraPitch == 0;
            program.run(width, columns ? 1 : height, maps, grid, meta, portals, wall, finish, floor, ceil, camX, dp, ip, out);
            program.read(out, renderFrame.renderPixels, width * height);
        }
        private GpuProgram.Buffer ensure(GpuProgram.Buffer current, long bytes) {
            long size = Math.max(4L, bytes);
            if (current != null && current.bytes() == size) return current;
            if (current != null) program.release(current);
            return program.buffer(size);
        }

        static int[] ipValues(RenderFrame renderFrame, Scene scene, int width, int height) {
            return new int[]{
                    width, height, scene.floors, scene.tinted ? 1 : 0,
                    renderFrame.wallTexture.width(), renderFrame.wallTexture.height(),
                    renderFrame.finishTexture.width(), renderFrame.finishTexture.height(),
                    renderFrame.floorTexture.width(), renderFrame.floorTexture.height(),
                    renderFrame.ceilingTexture.width(), renderFrame.ceilingTexture.height(),
                    renderFrame.cameraPitch == 0 ? 1 : 0,
                    renderFrame.skyCeiling ? 1 : 0
            };
        }
        static double[] cameraParams(RenderFrame renderFrame, int width, int height) {
            double dirX = Math.cos(renderFrame.cameraAngle), dirY = Math.sin(renderFrame.cameraAngle);
            double planeLength = Math.tan(Math.PI / 3.0 / 2.0);
            double focal = width / (2.0 * planeLength);
            double halfHeight = height / 2.0;
            double pitchSin = Math.sin(renderFrame.cameraPitch), pitchCos = Math.cos(renderFrame.cameraPitch);
            return new double[]{ renderFrame.playerX, renderFrame.playerY, renderFrame.cameraHeight, dirX, dirY, planeLength, pitchSin, pitchCos, halfHeight, focal, flashlightDistance, RC_MAX_DISTANCE, renderFrame.skyTime };
        }
        static final class Scene {
            int[] maps, grid, meta, portals;
            int floors;
            boolean tinted;

            static Scene of(RenderFrame renderFrame) {
                int floors = renderFrame.map3D == null ? 0 : renderFrame.map3D.length;
                int layerCount = 1 + floors;

                java.util.List<int[][]> layerMaps = new java.util.ArrayList<>(layerCount);
                java.util.List<PortalData> layerPortals = new java.util.ArrayList<>(layerCount);
                layerMaps.add(renderFrame.map);
                layerPortals.add(renderFrame.portals == null ? new PortalData() : renderFrame.portals);
                for (int f = 0; f < floors; f++) {
                    layerMaps.add(renderFrame.map3D[f]);
                    PortalData portalData = renderFrame.floorPortalsAll == null || f >= renderFrame.floorPortalsAll.length ? null : renderFrame.floorPortalsAll[f];
                    layerPortals.add(portalData == null ? new PortalData() : portalData);
                }

                int cells = 0, portalCount = 0;
                for (int l = 0; l < layerCount; l++) {
                    int[][] m = layerMaps.get(l);
                    cells += m.length == 0 ? 0 : m.length * m[0].length;
                    portalCount += layerPortals.get(l).values().size();
                }

                Scene scene = new Scene();
                scene.floors = floors;
                scene.tinted = renderFrame.map3D != null && renderFrame.map3D.length > 1;
                scene.maps = new int[cells];
                scene.grid = new int[cells];
                scene.meta = new int[3 * layerCount];
                scene.portals = new int[Math.max(1, 9 * portalCount)];
                java.util.Arrays.fill(scene.grid, -1);

                int offset = 0, portalIndex = 0;
                for (int l = 0; l < layerCount; l++) {
                    int[][] m = layerMaps.get(l);
                    int h = m.length, w = h == 0 ? 0 : m[0].length;
                    scene.meta[3 * l] = offset;
                    scene.meta[3 * l + 1] = w;
                    scene.meta[3 * l + 2] = h;
                    for (int y = 0; y < h; y++) System.arraycopy(m[y], 0, scene.maps, offset + y * w, w);
                    for (PortalData.Portal portal : layerPortals.get(l).values()) {
                        if (portal.x < 0 || portal.y < 0 || portal.x >= w || portal.y >= h) continue;
                        scene.grid[offset + portal.y * w + portal.x] = portalIndex;
                        int base = 9 * portalIndex;
                        scene.portals[base] = portal.x;
                        scene.portals[base + 1] = portal.y;
                        scene.portals[base + 2] = portal.dirX;
                        scene.portals[base + 3] = portal.dirY;
                        scene.portals[base + 4] = portal.linkedX;
                        scene.portals[base + 5] = portal.linkedY;
                        scene.portals[base + 6] = portal.linkedDirX;
                        scene.portals[base + 7] = portal.linkedDirY;
                        scene.portals[base + 8] = portal.linkedFloor;
                        portalIndex++;
                    }
                    offset += w * h;
                }
                return scene;
            }
        }
    }
    //endregion
}