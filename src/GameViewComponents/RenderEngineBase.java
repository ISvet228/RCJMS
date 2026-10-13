package GameViewComponents;

import Helpers.*;

import java.awt.*;
import java.awt.image.*;
import java.util.function.IntConsumer;

public abstract class RenderEngineBase {
    //region Shared render state
    public BufferedImage bufferedImage, renderImage;
    public RenderWorkers renderWorkers;
    public double cameraAngle, cameraHeight, cameraPitch;
    public double[] cameraXCache;
    public RuntimeTexture wallTexture, floorTexture, ceilingTexture, finishTexture;
    public PortalData[] floorPortalsAll;
    public PortalData portals;
    public int[][][] map3D;
    public int[][] map;
    public int[] renderPixels;
    public int[] pixels;
    public double playerX, playerY;
    public int renderHeight, renderWidth;
    //endregion

    //region Shared constants and buffers
    protected static final int MAX_PRIMARY_STEPS = 6000;
    protected int[] wallTopCache, wallBottomCache;
    protected double[] portalFloorEdgeCache, portalCeilEdgeCache;
    protected double[] rowInvACache, rowZACache;
    protected final java.util.IdentityHashMap<int[][], SDFField> sdfFields = new java.util.IdentityHashMap<>();
    protected final int wallBaseColor = 0xECD485;
    protected final int ceilingBaseColor = 0x816E1E;
    protected final int floorBaseColor = 0xD3AF63;
    protected final int finishBaseColor = 0x33FF66;
    private final double flashlightDistance = 2;
    private static final double HYP_PLANE_LENGTH = Math.tan(Math.toRadians(140.0) / 2.0);
    protected static final int HIT_WALL = 1, HIT_FLOOR = 2, HIT_CEIL = 3;
    protected static final double RM_MAX_DISTANCE = 48;
    private static final int SDF_SUB = 8, SDF_WINDOW = 4;
    //endregion

    //region Constants and caches
    protected void ensureRaycastCaches(int screenWidth, int screenHeight) {
        if (wallTopCache == null || wallTopCache.length != screenWidth) {
            wallTopCache = new int[screenWidth]; wallBottomCache = new int[screenWidth];
            portalFloorEdgeCache = new double[screenWidth]; portalCeilEdgeCache = new double[screenWidth];
        }
        if (rowInvACache == null || rowInvACache.length != screenHeight)
            rowInvACache = new double[screenHeight]; rowZACache = new double[screenHeight];
    }
    protected void fillRowCaches(double focal, double halfHeight, double pitchSin, double pitchCos) {
        final int screenHeight = renderHeight;
        for (int y = 0; y < screenHeight; y++) {
            double v = (halfHeight - (y + 0.5)) / focal;
            double a = pitchCos - pitchSin * v;
            if (Math.abs(a) < 0.02) a = a < 0 ? -0.02 : 0.02;
            rowInvACache[y] = 1.0 / a;
            rowZACache[y] = (pitchSin + pitchCos * v) / a;
        }
    }
    //endregion

    //region Hyperbolic render
    private static double hypFloorDistance(double z0, double slope, double L) {
        if (slope >= 0 || z0 <= 1e-9) return Double.POSITIVE_INFINITY;
        return L * HyperbolicMath.inverseHyperbolicDistance(z0 / -slope, RenderEngineConfig.HYPERBOLIC_CURVATURE);
    }
    private static double hypCeilDistance(double z0, double slope, double L) {
        if (slope <= 0 || z0 >= 1.0 - 1e-9) return Double.POSITIVE_INFINITY;
        return L * HyperbolicMath.inverseHyperbolicDistance((1.0 - z0) / slope, RenderEngineConfig.HYPERBOLIC_CURVATURE);
    }
    protected static double hypHeight(RayState rayState) {
        return rayState.hypZ0 + rayState.slope * HyperbolicMath.hyperbolicDistance(rayState.dist * rayState.invL, RenderEngineConfig.HYPERBOLIC_CURVATURE);
    }
    protected static double hypPerceivedOf(RayState rayState) {
        return HyperbolicMath.hyperbolicDistance(rayState.dist * rayState.invL, RenderEngineConfig.HYPERBOLIC_CURVATURE);
    }
    protected static double[] hypPixelRay(double cx, double py, double dirX, double dirY, double focal, double horizon) {
        double rdX = dirX - dirY * HYP_PLANE_LENGTH * cx, rdY = dirY + dirX * HYP_PLANE_LENGTH * cx;
        double L = Math.sqrt(rdX * rdX + rdY * rdY);
        return new double[]{rdX / L, rdY / L, L, -(py - horizon) / focal};
    }
    protected TraceHit traceHyp(double ox, double oy, double z0, double ux, double uy, double L, double slope, TraceHit result, int maxSteps) {
        result.reset();
        final int[][] m = map;
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
            double z = z0 + slope * HyperbolicMath.hyperbolicDistance(enter * invL, RenderEngineConfig.HYPERBOLIC_CURVATURE);
            if (z >= 0.0 && z <= wallHeightForType(type)) { wallD = enter; wallType = type; wallSide = side; break; }
        }

        result.map = m;
        result.portals = null;
        result.hyp = true;
        result.hypSlope = slope;
        double dzdd;
        if (wallD < planeLimit) {
            double z = z0 + slope * HyperbolicMath.hyperbolicDistance(wallD * invL, RenderEngineConfig.HYPERBOLIC_CURVATURE);
            dzdd = slope * Math.cosh(wallD * invL / RenderEngineConfig.HYPERBOLIC_CURVATURE) * invL;
            double len = Math.sqrt(1.0 + dzdd * dzdd);
            result.hit = true;
            result.distance = HyperbolicMath.hyperbolicDistance(wallD * invL, RenderEngineConfig.HYPERBOLIC_CURVATURE);
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
            dzdd = slope * Math.cosh(planeLimit * invL / RenderEngineConfig.HYPERBOLIC_CURVATURE) * invL;
            double len = Math.sqrt(1.0 + dzdd * dzdd);
            result.hit = true;
            result.isPlane = true;
            result.type = floor ? -1 : 1;
            result.z = floor ? 0.0 : 1.0;
            result.distance = HyperbolicMath.hyperbolicDistance(planeLimit * invL, RenderEngineConfig.HYPERBOLIC_CURVATURE);
            result.x = ox + ux * planeLimit; result.y = oy + uy * planeLimit;
            result.dirX = ux / len; result.dirY = uy / len; result.dirZ = dzdd / len;
        }
        return result;
    }
    //endregion

    //region Tracing
    protected TraceHit traceRay(double ox, double oy, double oz, double rx, double ry, double rz, int[][] startMap, PortalData startPortals, TraceHit result, int maxSteps) {
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
                            if (map3D != null && portal.linkedFloor >= 0 && portal.linkedFloor < map3D.length) {
                                rayMap = map3D[portal.linkedFloor];
                                rayPortals = floorPortalsAll[portal.linkedFloor];
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
    protected void resolveWallFace(RayState s) {
        double fx = s.x - Math.floor(s.x), fy = s.y - Math.floor(s.y);
        int cx = (int) Math.floor(s.x), cy = (int) Math.floor(s.y);
        double best = Double.MAX_VALUE;
        int face = -1;
        if (isSolidCell(s.map, cx - 1, cy) && fx < best) { best = fx; face = 0; }
        if (isSolidCell(s.map, cx + 1, cy) && 1 - fx < best) { best = 1 - fx; face = 1; }
        if (isSolidCell(s.map, cx, cy - 1) && fy < best) { best = fy; face = 2; }
        if (isSolidCell(s.map, cx, cy + 1) && 1 - fy < best) { best = 1 - fy; face = 3; }
        int side = face >= 2 ? 1 : 0;
        double u = side == 0 ? fy : fx;
        if ((side == 0 && s.dx < 0) || (side == 1 && s.dy > 0)) u = 1 - u;
        s.hitSide = side;
        s.hitU = Math.clamp(u, 0.0, 0.999999);
    }
    protected void setWallFraction(RayState s) {
        double wallH = wallHeightForType(s.hitType);
        s.hitFrac = Math.clamp((wallH - s.z) / wallH, 0.0, 0.999999);
    }
    protected void setPlaneHit(RayState s, int kind) {
        s.hitKind = kind;
        s.hitX = s.x;
        s.hitY = s.y;
        s.hitDist = s.dist;
    }
    //endregion

    //region Marching
    protected static void addColumnSegment(ColumnRay c, double dist, RayState s) {
        int k = c.segCount++;
        c.segStartDist[k] = dist; c.segStartX[k] = s.x; c.segStartY[k] = s.y; c.segDirX[k] = s.dx; c.segDirY[k] = s.dy;
        c.segMap[k] = s.map; c.segDistField[k] = s.field;
    }
    protected int shadeColumnRow(ColumnRay c, RayState rs, double z0, double sigma, double lightDistance) {
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
                return shadeMarch(rs, lightDistance);
            }
        }
        if (c.wallDist < planeD && c.wallDist < c.exitD) {
            double z = z0 + sigma * c.wallDist;
            rs.hitKind = HIT_WALL; rs.hitType = 1; rs.hitSide = c.wallSide; rs.hitU = c.wallTexU;
            rs.hitDist = c.wallDist * scale;
            rs.hitFrac = Math.clamp(1.0 - z, 0.0, 0.999999);
            return shadeMarch(rs, lightDistance);
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
            return shadeMarch(rs, lightDistance);
        }
        return ceilingBaseColor;
    }
    protected int shadeMarch(RayState s, double lightDistance) {
        if (s.hitKind == HIT_WALL) {
            boolean finish = s.hitType == 2;
            RuntimeTexture texture = finish ? finishTexture : wallTexture;
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
        int color = floor ? sampleTexture(floorTexture, s.hitX, s.hitY, floorBaseColor) : sampleTexture(ceilingTexture, s.hitX, s.hitY, ceilingBaseColor);
        if (floor && is3D()) color = tintStairs(color, s.hitX, s.hitY, s.map);
        double brightness = Math.clamp(1.0 - s.hitDist / lightDistance, 0.05, 1.0);
        return applyBrightness(color, brightness);
    }
    //endregion

    //region Textures and shading
    private int tintColor(int color, int tint, double amount) {
        int r = (color >> 16) & 255, g = (color >> 8) & 255, b = color & 255;
        int tr = (tint >> 16) & 255, tg = (tint >> 8) & 255, tb = tint & 255;
        r = (int)(r * (1 - amount) + tr * amount); g = (int)(g * (1 - amount) + tg * amount); b = (int)(b * (1 - amount) + tb * amount);
        return (r << 16) | (g << 8) | b;
    }
    protected static int applyBrightness(int color, double brightness) {
        int r = (int) Math.min(255, ((color >> 16) & 255) * brightness);
        int g = (int) Math.min(255, ((color >> 8) & 255) * brightness);
        int b = (int) Math.min(255, (color & 255) * brightness);
        return (r << 16) | (g << 8) | b;
    }
    protected int shadeTraceHit(TraceHit hit, double lightDistance) {
        if (!hit.hit) return ceilingBaseColor;
        if (hit.isPlane) {
            boolean floor = hit.type == -1;
            int color = floor ? sampleTexture(floorTexture, hit.x, hit.y, floorBaseColor) : sampleTexture(ceilingTexture, hit.x, hit.y, ceilingBaseColor);
            if (floor && is3D()) color = tintStairs(color, hit.x, hit.y, hit.map);
            return applyBrightness(color, Math.clamp(1.0 - hit.distance / lightDistance, 0.05, 1.0));
        }
        boolean finish = hit.type == 2;
        int color = sampleWallTexture(finish ? finishTexture : wallTexture, hit);
        double brightness = Math.clamp(1.0 - hit.distance / lightDistance, 0.03, 1.0);
        if (hit.side == 1) brightness *= 0.85;
        return applyBrightness(color, brightness);
    }
    protected int sampleTexture(RuntimeTexture texture, double worldX, double worldY, int baseColor) {
        int width = texture.width(), height = texture.height();
        if (width <= 0 || height <= 0) return baseColor;
        double fracX = worldX - Math.floor(worldX);
        double fracY = worldY - Math.floor(worldY);
        int tx = Math.min((int)(fracX * width), width - 1);
        int ty = Math.min((int)(fracY * height), height - 1);
        return texture.pixels()[ty * width + tx];
    }
    protected int sampleWallTexture(RuntimeTexture texture, TraceHit hit) {
        if (texture.width() <= 0 || texture.height() <= 0) return hit.type == 2 ? finishBaseColor : wallBaseColor;
        int tx = Math.min((int) (hit.wallU * texture.width()), texture.width() - 1);
        int ty = Math.min((int) ((1.0 - hit.wallV) * texture.height()), texture.height() - 1);
        return texture.pixels()[ty * texture.width() + Math.max(0, tx)];
    }
    protected int tintStairs(int color, double worldX, double worldY, int[][] floorMap) {
        int fx = (int) Math.floor(worldX), fy = (int) Math.floor(worldY);
        if (fx < 0 || fy < 0 || fy >= floorMap.length || fx >= floorMap[0].length) return color;
        int cell = floorMap[fy][fx];
        if (cell == 3) return tintColor(color, 0x3399FF, 0.45);
        if (cell == 4) return tintColor(color, 0xFFAA33, 0.45);
        return color;
    }
    //endregion

    //region Utilities
    private static boolean isSolidCell(int[][] m, int x, int y) {
        if (y < 0 || x < 0 || y >= m.length || x >= m[0].length) return true;
        return m[y][x] == 1 || m[y][x] == 2;
    }
    protected boolean enterPortal(RayState s, int cx, int cy) {
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
        if (map3D != null && portal.linkedFloor >= 0 && portal.linkedFloor < map3D.length) {
            s.map = map3D[portal.linkedFloor];
            s.portals = floorPortalsAll[portal.linkedFloor];
            if (s.field != null) s.field = sdfFieldFor(s.map);
        }
        s.hops++;
        return true;
    }
    protected boolean is3D() { return map3D != null && map3D.length > 1; }
    protected static double wallHeightForType(int type) { return type == 2 ? 0.5 : 1.0; }
    protected double effectiveFlashlightDistance(boolean hyperbolic) { return hyperbolic ? flashlightDistance * 1.6 : flashlightDistance; }
    protected void copyRenderBuffer() {
        if (RenderEngineConfig.renderScale == 1.0) System.arraycopy(renderPixels, 0, pixels, 0, pixels.length);
        else {
            Graphics2D g2d = bufferedImage.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g2d.drawImage(renderImage, 0, 0, RenderEngineConfig.outputWidth, RenderEngineConfig.outputHeight, null);
            g2d.dispose();
        }
    }
    protected void parallelRange(int start, int end, IntConsumer task) {
        int range = end - start;
        if (range <= 0) return;
        renderWorkers.execute(start, end, task);
    }
    //endregion

    //region Nested Types
    protected static final class SDFField {
        final int cols, rows;
        final double[] wall, finish;
        final boolean hasPortals, hasFinish;
        SDFField(int[][] m) {
            int mh = m.length, mw = m[0].length;
            cols = mw * SDF_SUB + 1;
            rows = mh * SDF_SUB + 1;
            boolean[][] wallCells = new boolean[mh][mw], finishCells = new boolean[mh][mw];
            boolean portals = false;
            for (int y = 0; y < mh; y++)
                for (int x = 0; x < mw; x++) {
                    int t = m[y][x];
                    if (t == 1) wallCells[y][x] = true;
                    else if (t == 2) finishCells[y][x] = true;
                    else if (t == MazeGenerator.PORTAL) portals = true;
                }
            hasPortals = portals;
            boolean anyFinish = false;
            for (int y = 0; y < mh && !anyFinish; y++)
                for (int x = 0; x < mw; x++) if (finishCells[y][x]) { anyFinish = true; break; }
            hasFinish = anyFinish;
            wall = new double[cols * rows];
            finish = new double[cols * rows];
            fill(wall, wallCells, mw, mh);
            if (hasFinish) fill(finish, finishCells, mw, mh);
        }
        private void fill(double[] grid, boolean[][] solid, int mw, int mh) {
            for (int j = 0; j < rows; j++) {
                double py = j / (double) SDF_SUB;
                int cy = (int) Math.floor(py);
                for (int i = 0; i < cols; i++) {
                    double px = i / (double) SDF_SUB;
                    int cx = (int) Math.floor(px);
                    double best = Double.MAX_VALUE;
                    for (int yy = Math.max(0, cy - SDF_WINDOW); yy <= Math.min(mh - 1, cy + SDF_WINDOW); yy++)
                        for (int xx = Math.max(0, cx - SDF_WINDOW); xx <= Math.min(mw - 1, cx + SDF_WINDOW); xx++) {
                            if (!solid[yy][xx]) continue;
                            double dx = Math.max(Math.max(xx - px, px - (xx + 1)), 0);
                            double dy = Math.max(Math.max(yy - py, py - (yy + 1)), 0);
                            double d = Math.sqrt(dx * dx + dy * dy);
                            if (d < best) best = d;
                        }
                    grid[j * cols + i] = best == Double.MAX_VALUE ? SDF_WINDOW : best;
                }
            }
        }
        private double sample(double[] grid, double x, double y) {
            double gx = Math.clamp(x * SDF_SUB, 0, cols - 1.000001), gy = Math.clamp(y * SDF_SUB, 0, rows - 1.000001);
            int i0 = (int) gx, j0 = (int) gy;
            int i1 = Math.min(i0 + 1, cols - 1), j1 = Math.min(j0 + 1, rows - 1);
            double fx = gx - i0, fy = gy - j0;
            double a = grid[j0 * cols + i0], b = grid[j0 * cols + i1], c = grid[j1 * cols + i0], d = grid[j1 * cols + i1];
            return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy;
        }
        double wallAt(double x, double y) { return sample(wall, x, y); }
        double finishAt(double x, double y) { return sample(finish, x, y); }
    }
    protected SDFField sdfFieldFor(int[][] m) {
        SDFField field = sdfFields.get(m);
        if (field == null) field = new SDFField(m);
        return field;
    }
    protected static final class ColumnRay {
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
    protected static final class RayState {
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
    protected static final class TraceHit {
        void reset() {
            x = y = z = distance = wallU = wallV = dirX = dirY = dirZ = hypSlope = 0; type = side = 0;
            hit = isPlane = hyp = false; map = null; portals = null;
        }
        double x, y, z, distance;
        int type, side;
        double wallU, wallV;
        double dirX, dirY, dirZ;
        boolean hit, isPlane, hyp;
        double hypSlope;
        int[][] map;
        PortalData portals;
    }
    //endregion
}