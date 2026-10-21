package Engines;

import GameViewComponents.*;
import Helpers.*;
import OpenCL.*;
import java.awt.*;
import java.util.Arrays;
import java.util.function.IntConsumer;

public final class RayMarchingEngine  implements RenderEngine {

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

    @FunctionalInterface
    interface PlaneColor { int at(RuntimeTexture texture, double worldX, double worldY, int baseColor); }

    //region Constants
    static final int MAX_PRIMARY_STEPS = 6000;
    static final int wallBaseColor = 0xECD485;
    static final int ceilingBaseColor = 0x816E1E;
    static final int floorBaseColor = 0xD3AF63;
    static final int finishBaseColor = 0x33FF66;
    private static final double flashlightDistance = 2;
    private static final double HYP_PLANE_LENGTH = Math.tan(Math.toRadians(140.0) / 2.0);
    static final int HIT_WALL = 1, HIT_FLOOR = 2, HIT_CEIL = 3;
    private static final int SDF_SUB = 8, SDF_WINDOW = 4;
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
    static TraceHit traceHyp(RenderFrame fr, double ox, double oy, double z0, double ux, double uy, double L, double slope, TraceHit result) {
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
        for (int steps = 0; steps < RayMarchingEngine.MAX_PRIMARY_STEPS; steps++) {
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
    static TraceHit traceRay(RenderFrame fr, double ox, double oy, double oz, double rx, double ry, double rz, int[][] startMap, PortalData startPortals, TraceHit result) {
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
                if (candidateT >= planeHere || ++steps > RayMarchingEngine.MAX_PRIMARY_STEPS) break segments;

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
    static void resolveWallFace(RayState s) {
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
    private static int tintColor(int color, int tint) {
        int r = (color >> 16) & 255, g = (color >> 8) & 255, b = color & 255;
        int tr = (tint >> 16) & 255, tg = (tint >> 8) & 255, tb = tint & 255;
        r = (int)(r * (1 - 0.45) + tr * 0.45); g = (int)(g * (1 - 0.45) + tg * 0.45); b = (int)(b * (1 - 0.45) + tb * 0.45);
        return (r << 16) | (g << 8) | b;
    }
    static int applyBrightness(int color, double brightness) {
        int r = (int) Math.min(255, ((color >> 16) & 255) * brightness);
        int g = (int) Math.min(255, ((color >> 8) & 255) * brightness);
        int b = (int) Math.min(255, (color & 255) * brightness);
        return (r << 16) | (g << 8) | b;
    }
    static int shadeTraceHit(RenderFrame fr, TraceHit hit, double lightDistance) {
        return shadeTraceHit(fr, hit, lightDistance, RayMarchingEngine::planeColor);
    }
    static int shadeTraceHit(RenderFrame fr, TraceHit hit, double lightDistance, PlaneColor planeFn) {
        if (!hit.hit) return ceilingBaseColor;
        if (hit.isPlane) {
            boolean floor = hit.type == -1;
            int color = floor ? planeFn.at(fr.floorTexture, hit.x, hit.y, floorBaseColor) : planeFn.at(fr.ceilingTexture, hit.x, hit.y, ceilingBaseColor);
            if (floor && is3D(fr)) color = tintStairs(color, hit.x, hit.y, hit.map);
            return applyBrightness(color, Math.clamp(1.0 - hit.distance / lightDistance, 0.05, 1.0));
        }
        boolean finish = hit.type == 2;
        int color = sampleWallTexture(finish ? fr.finishTexture : fr.wallTexture, hit);
        double brightness = Math.clamp(1.0 - hit.distance / lightDistance, 0.03, 1.0);
        if (hit.side == 1) brightness *= 0.85;
        return applyBrightness(color, brightness);
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
        if (cell == 3) return tintColor(color, 0x3399FF);
        if (cell == 4) return tintColor(color, 0xFFAA33);
        return color;
    }
    //endregion

    //region Utilities
    private static boolean isSolidCell(int[][] m, int x, int y) {
        if (y < 0 || x < 0 || y >= m.length || x >= m[0].length) return true;
        return m[y][x] == 1 || m[y][x] == 2;
    }
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
            if (s.field != null) s.field = sdfFieldFor(s.map);
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
    static void parallelRange(RenderFrame fr, int end, IntConsumer task) {
        if (end <= 0) return;
        fr.renderWorkers.execute(0, end, task);
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

    //region Module
    private RenderFrame fr;
    static final String ID = "raymarching";
    @Override public String id() { return ID; }
    @Override public String displayName() { return "Ray Marching"; }
    @Override public int order() { return 2; }
    @Override public String hintKey() { return "mm.engine_rm"; }
    @Override public String qualityHintKey() { return "mm.quality_rm"; }
    @Override public boolean hasHyperbolicGeometry() { return true; }
    @Override public boolean hasOpenCL() { return true; }
    @Override public void render(RenderFrame frame) {
        fr = frame;
        syncConfig(frame);
        if (hasOpenCL() && cfgGeometry != MazeGenerator.GeometryMode.WRONG && Gpu.tryRender(frame)) {
            copyRenderBuffer(frame);
            return;
        }
        renderRayMarching();
    }
    @Override public void prepare(RenderFrame frame) { prepareSdfFields(frame); }
    //endregion
    private  static final double RM_HIT = 0.001, RM_STEP_SCALE = 0.8, RM_MAX_DISTANCE = 48, RM_MIN_STEP = 0.001;

    private void renderRayMarching() {
        if (cfgGeometry == MazeGenerator.GeometryMode.WRONG) { renderRayMarchingHyp(); return; }
        if (fr.cameraPitch == 0) { renderRayMarchingColumns(); return; }
        final double dirX = Math.cos(fr.cameraAngle), dirY = Math.sin(fr.cameraAngle);
        final double planeLength = Math.tan(Math.PI / 3.0 / 2.0);
        final int width = fr.renderWidth, height = fr.renderHeight;
        final double focal = width / (2.0 * planeLength);
        final double halfHeight = height / 2.0;
        final double pitchSin = Math.sin(fr.cameraPitch), pitchCos = Math.cos(fr.cameraPitch);
        ensureRaycastCaches(fr, width, height);
        fillRowCaches(fr, focal, halfHeight, pitchSin, pitchCos);
        prepareSdfFields(fr);
        final double[] rowInvA = fr.rowInvACache, rowZA = fr.rowZACache;
        final int maxSteps = rayMarchSteps();
        final double lightDistance = effectiveFlashlightDistance(false);
        final int[][] baseMap = fr.map;
        final PortalData basePortals = fr.portals;
        final SDFField baseField = sdfFieldFor(baseMap);
        parallelRange(fr, height, y -> {
            RayState s = new RayState();
            double rowZ = rowZA[y], rowScale = rowInvA[y];
            for (int x = 0; x < width; x++) {
                double lateral = fr.cameraXCache[x] * planeLength * rowScale;
                s.reset(fr.playerX, fr.playerY, fr.cameraHeight, dirX - lateral * dirY, dirY + lateral * dirX, rowZ, baseMap, basePortals, baseField);
                marchSphere(s, maxSteps);
                if (s.exhausted) {
                    double rx = dirX - lateral * dirY, ry = dirY + lateral * dirX;
                    double inv = 1.0 / Math.sqrt(rx * rx + ry * ry + rowZ * rowZ);
                    TraceHit fallback = traceRay(fr, fr.playerX, fr.playerY, fr.cameraHeight, rx * inv, ry * inv, rowZ * inv, baseMap, basePortals, new TraceHit());
                    fr.renderPixels[y * width + x] = shadeTraceHit(fr, fallback, lightDistance);
                }
                else fr.renderPixels[y * width + x] = s.hitKind == 0 ? ceilingBaseColor : shadeMarch(fr, s, lightDistance);
            }
        });
        copyRenderBuffer(fr);
    }

    private static boolean isPortalCell(int[][] m, int x, int y) {
        return y >= 0 && x >= 0 && y < m.length && x < m[0].length && m[y][x] == MazeGenerator.PORTAL;
    }
    private static double portalStepLimit(int[][] m, double x, double y, double dx, double dy) {
        int cx = (int) Math.floor(x), cy = (int) Math.floor(y);
        double best = Double.POSITIVE_INFINITY;
        if (dx > 1e-12 && isPortalCell(m, cx + 1, cy)) best = Math.min(best, (cx + 1 - x) / dx);
        else if (dx < -1e-12 && isPortalCell(m, cx - 1, cy)) best = Math.min(best, (x - cx) / -dx);
        if (dy > 1e-12 && isPortalCell(m, cx, cy + 1)) best = Math.min(best, (cy + 1 - y) / dy);
        else if (dy < -1e-12 && isPortalCell(m, cx, cy - 1)) best = Math.min(best, (y - cy) / -dy);
        return Math.max(0.0, best);
    }
    private static int rayMarchSteps() { return cfgQuality == 0 ? 48 : cfgQuality == 1 ? 96 : 160; }
    private static void storeMarch(RayState s, double x, double y, double z, double dx, double dy, double dz, double dist) {
        s.x = x; s.y = y; s.z = z; s.dx = dx; s.dy = dy; s.dz = dz; s.dist = dist;
    }
    private void renderRayMarchingColumns() {
        final int width = fr.renderWidth, height = fr.renderHeight;
        final double dirX = Math.cos(fr.cameraAngle), dirY = Math.sin(fr.cameraAngle);
        final double planeLength = Math.tan(Math.PI / 3.0 / 2.0);
        final double focal = width / (2.0 * planeLength), halfHeight = height / 2.0;
        ensureRaycastCaches(fr, width, height);
        fillRowCaches(fr, focal, halfHeight, 0.0, 1.0);
        prepareSdfFields(fr);
        final SDFField baseField = sdfFieldFor(fr.map);
        final int[][] baseMap = fr.map;
        final PortalData basePortals = fr.portals;
        final int maxSteps = rayMarchSteps();
        final double lightDistance = effectiveFlashlightDistance(false);
        final double z0 = fr.cameraHeight;
        final double[] rowZ = fr.rowZACache;
        parallelRange(fr, width, x -> {
            final double lateral = fr.cameraXCache[x] * planeLength;
            final double hx = dirX - lateral * dirY, hy = dirY + lateral * dirX;
            final double hl = Math.sqrt(hx * hx + hy * hy);
            ColumnRay c = new ColumnRay();
            marchColumn(c, fr.playerX, fr.playerY, hx / hl, hy / hl, baseField, baseMap, basePortals, maxSteps);
            RayState rs = new RayState();
            for (int y = 0; y < height; y++) {
                int color;
                if (c.exhausted) {
                    double vz = rowZ[y];
                    double inv = 1.0 / Math.sqrt(hx * hx + hy * hy + vz * vz);
                    color = shadeTraceHit(fr, traceRay(fr, fr.playerX, fr.playerY, z0, hx * inv, hy * inv, vz * inv, baseMap, basePortals, new TraceHit()), lightDistance);
                }
                else color = shadeColumnRow(fr, c, rs, z0, rowZ[y] / hl, lightDistance);
                fr.renderPixels[y * width + x] = color;
            }
        });
        copyRenderBuffer(fr);
    }
    private void renderRayMarchingHyp() {
        final int width = fr.renderWidth, height = fr.renderHeight;
        final double dirX = Math.cos(fr.cameraAngle), dirY = Math.sin(fr.cameraAngle);
        final double horizon = height / 2.0 + fr.cameraPitch * height / 2.0;
        prepareSdfFields(fr);
        final SDFField baseField = sdfFieldFor(fr.map);
        final int[][] baseMap = fr.map;
        final int maxSteps = rayMarchSteps();
        final double lightDistance = effectiveFlashlightDistance(true);
        parallelRange(fr, height, y -> {
            RayState s = new RayState();
            for (int x = 0; x < width; x++) {
                double[] pr = hypPixelRay(fr.cameraXCache[x], y + 0.5, dirX, dirY, height, horizon);
                s.resetHyp(fr.playerX, fr.playerY, fr.cameraHeight, pr, baseMap, baseField);
                marchSphereHyp(s, maxSteps);
                if (s.exhausted) {
                    TraceHit fallback = traceHyp(fr, fr.playerX, fr.playerY, fr.cameraHeight, pr[0], pr[1], pr[2], pr[3], new TraceHit());
                    fr.renderPixels[y * width + x] = shadeTraceHit(fr, fallback, lightDistance);
                }
                else fr.renderPixels[y * width + x] = s.hitKind == 0 ? ceilingBaseColor : shadeMarch(fr, s, lightDistance);
            }
        });
        copyRenderBuffer(fr);
    }
    private void marchColumn(ColumnRay c, double px, double py, double ux, double uy, SDFField field0, int[][] map0, PortalData portals0, int maxSteps) {
        RayState s = c.ray;
        s.x = px; s.y = py; s.dx = ux; s.dy = uy; s.dz = 0; s.dist = 0;
        s.map = map0; s.portals = portals0; s.field = field0; s.hops = 0; s.hitKind = 0;
        c.segCount = 0; c.finCount = 0; c.exhausted = false;
        c.wallDist = Double.POSITIVE_INFINITY; c.exitD = Double.POSITIVE_INFINITY;
        addColumnSegment(c, 0, s);
        double dist = 0;
        boolean inFinish = false;
        double prevWall = Double.POSITIVE_INFINITY;
        int i = 0;
        for (; i < maxSteps && dist < RayMarchingEngine.RM_MAX_DISTANCE; i++) {
            int[][] m = s.map;
            SDFField field = s.field;
            int cx = (int) Math.floor(s.x), cy = (int) Math.floor(s.y);
            if (cx < 0 || cy < 0 || cx >= m[0].length || cy >= m.length) { c.exitD = dist; return; }
            if (m[cy][cx] == MazeGenerator.PORTAL && enterPortal(fr, s, cx, cy)) {
                addColumnSegment(c, dist, s);
                prevWall = s.field.wallAt(s.x, s.y);
                continue;
            }
            double dWall = field.wallAt(s.x, s.y);
            boolean approaching = dWall < prevWall;
            prevWall = dWall;
            double dFinish = field.hasFinish ? field.finishAt(s.x, s.y) : Double.MAX_VALUE;
            if (dWall < RM_HIT && dist > RM_HIT && approaching) {
                resolveWallFace(s);
                c.wallDist = dist; c.wallTexU = s.hitU; c.wallSide = s.hitSide;
                return;
            }
            if (dFinish < RM_HIT && dist > RM_HIT) {
                if (!inFinish && c.finCount < ColumnRay.MAX_FIN) {
                    resolveWallFace(s);
                    int k = c.finCount++;
                    c.finishDist[k] = dist; c.finishTexU[k] = s.hitU; c.finishSide[k] = s.hitSide;
                }
                inFinish = true;
            }
            else inFinish = false;
            double step = Math.clamp(Math.min(dWall, dFinish) * RM_STEP_SCALE, RM_MIN_STEP, 0.5);
            if (dWall < RM_HIT) step = Math.max(step, 4 * RM_HIT);
            if (field.hasPortals) step = Math.min(step, portalStepLimit(m, s.x, s.y, s.dx, s.dy) + 1e-7);
            s.x += s.dx * step;
            s.y += s.dy * step;
            dist += step;
        }
        if (dist < RayMarchingEngine.RM_MAX_DISTANCE) c.exhausted = true;
    }
    private void marchSphereHyp(RayState s, int maxSteps) {
        s.hitKind = 0;
        s.exhausted = false;
        final double plane = Math.min(s.floorD, s.ceilD);
        int i = 0;
        for (; i < maxSteps && s.dist < RayMarchingEngine.RM_MAX_DISTANCE; i++) {
            if (s.dist >= plane) {
                boolean floor = s.floorD <= s.ceilD;
                s.z = floor ? 0.0 : 1.0;
                s.hitKind = floor ? HIT_FLOOR : HIT_CEIL;
                s.hitX = s.x; s.hitY = s.y; s.hitDist = hypPerceivedOf(s);
                return;
            }
            int cx = (int) Math.floor(s.x), cy = (int) Math.floor(s.y);
            if (cx < 0 || cy < 0 || cx >= s.map[0].length || cy >= s.map.length) return;
            s.z = hypHeight(s);
            SDFField field = s.field;
            double dWall = field.wallAt(s.x, s.y);
            double dFinish = field.hasFinish ? field.finishAt(s.x, s.y) : Double.MAX_VALUE;
            if (dWall < RM_HIT && s.dist > RM_HIT) {
                s.hitKind = HIT_WALL; s.hitType = 1; s.hitDist = hypPerceivedOf(s);
                resolveWallFace(s); setWallFraction(s);
                return;
            }
            if (dFinish < RM_HIT && s.z <= 0.5 && s.dist > RM_HIT) {
                s.hitKind = HIT_WALL; s.hitType = 2; s.hitDist = hypPerceivedOf(s);
                resolveWallFace(s); setWallFraction(s);
                return;
            }
            double distance = Math.min(dWall, dFinish);
            double maxStep = 0.5;
            double step = Math.clamp(distance * RM_STEP_SCALE, RM_MIN_STEP, maxStep);
            step = Math.min(step, plane - s.dist);
            s.x += s.dx * step; s.y += s.dy * step; s.dist += step;
        }
        if (s.dist < RayMarchingEngine.RM_MAX_DISTANCE) s.exhausted = true;
    }
    private void marchSphere(RayState s, int maxSteps) {
        s.hitKind = 0;
        s.exhausted = false;
        int[][] map = s.map;
        SDFField field = s.field;
        double x = s.x, y = s.y, z = s.z, dx = s.dx, dy = s.dy, dz = s.dz, dist = s.dist;
        final double planeAbs = dist + (dz < 0 ? z / -dz : dz > 0 ? (1.0 - z) / dz : Double.POSITIVE_INFINITY);
        double prevWall = Double.POSITIVE_INFINITY;
        int i = 0;
        for (; i < maxSteps && dist < RayMarchingEngine.RM_MAX_DISTANCE; i++) {
            int cx = (int) Math.floor(x), cy = (int) Math.floor(y);
            if (cx < 0 || cy < 0 || cx >= map[0].length || cy >= map.length) return;
            if (map[cy][cx] == MazeGenerator.PORTAL) {
                storeMarch(s, x, y, z, dx, dy, dz, dist);
                if (enterPortal(fr, s, cx, cy)) {
                    x = s.x; y = s.y; z = s.z; dx = s.dx; dy = s.dy; dist = s.dist;
                    map = s.map; field = s.field;
                    prevWall = field.wallAt(x, y);
                    continue;
                }
            }
            double dWall = field.wallAt(x, y);
            boolean approaching = dWall < prevWall;
            prevWall = dWall;
            double dFinish = field.hasFinish ? field.finishAt(x, y) : Double.MAX_VALUE;
            if (z <= RM_HIT || 1.0 - z <= RM_HIT) {
                storeMarch(s, x, y, z, dx, dy, dz, dist);
                setPlaneHit(s, z <= RM_HIT ? HIT_FLOOR : HIT_CEIL);
                return;
            }
            if (dWall < RM_HIT && dist > RM_HIT && approaching) {
                storeMarch(s, x, y, z, dx, dy, dz, dist);
                s.hitKind = HIT_WALL;
                s.hitType = 1;
                s.hitDist = s.dist;
                resolveWallFace(s);
                setWallFraction(s);
                return;
            }
            if (dFinish < RM_HIT && z <= 0.5 && dist > RM_HIT) {
                storeMarch(s, x, y, z, dx, dy, dz, dist);
                s.hitKind = HIT_WALL;
                s.hitType = 2;
                s.hitDist = s.dist;
                resolveWallFace(s);
                setWallFraction(s);
                return;
            }
            double dFinishBox = field.hasFinish ? Math.sqrt(dFinish * dFinish + Math.max(0.0, z - 0.5) * Math.max(0.0, z - 0.5)) : Double.MAX_VALUE;
            double distance = Math.min(Math.min(dWall, dFinishBox), Math.min(z, 1.0 - z));
            double step = Math.clamp(distance * RM_STEP_SCALE, RM_MIN_STEP, 0.5);
            if (field.hasPortals) step = Math.min(step, portalStepLimit(map, x, y, dx, dy) + 1e-7);
            if (dWall < RM_HIT) step = Math.max(step, 4 * RM_HIT);
            step = Math.min(step, planeAbs - dist);
            x += dx * step;
            y += dy * step;
            z += dz * step;
            dist += step;
        }
        storeMarch(s, x, y, z, dx, dy, dz, dist);
        if (dist < RayMarchingEngine.RM_MAX_DISTANCE) s.exhausted = true;
    }

    //region OpenCL plug-in
    static final class Gpu {
        private final String source = OpenCL.KernelSource.load(RayMarchingEngine.class);

        private static final int DP_COUNT = 12, IP_COUNT = 14;
        private static Gpu instance;
        private static boolean disabled;

        private final GpuProgram program = new GpuProgram(source, "rm_render");
        private GpuProgram.Buffer maps, grid, meta, portals, fieldWall, fieldFinish, camX, dp, ip, out, wall, finish, floor, ceil;
        private int[] wallSrc, finishSrc, floorSrc, ceilSrc;
        private int[][][] fieldLayers = new int[0][][];
        private double[] fieldWallData = new double[0], fieldFinishData = new double[0];
        private int[] fieldOffset = new int[0], fieldCols = new int[0], fieldRows = new int[0];
        private boolean[] fieldHasFinish = new boolean[0], fieldHasPortals = new boolean[0];

        static boolean tryRender(RenderFrame fr) { synchronized (GpuRuntime.LOCK) { return tryRenderLocked(fr); } }

        private static boolean tryRenderLocked(RenderFrame fr) {
            if (disabled || GpuRuntime.session() == null) return false;
            try {
                if (instance == null) instance = new Gpu();
                instance.render(fr);
                return true;
            }
            catch (RuntimeException | Error e) {
                disabled = true;
                OpenCL.GpuRuntime.reportFailure(ID, String.valueOf(e));
                return false;
            }
        }

        private GpuProgram.Buffer ensure(GpuProgram.Buffer current, long bytes) {
            long size = Math.max(4L, bytes);
            if (current != null && current.bytes() == size) return current;
            if (current != null) program.release(current);
            return program.buffer(size);
        }

        private void render(RenderFrame fr) {
            final int width = fr.renderWidth, height = fr.renderHeight;
            if (width <= 0 || height <= 0) return;
            if (fr.renderPixels == null || fr.renderPixels.length < width * height) throw new IllegalStateException("render buffer too small");
            if (fr.cameraXCache == null || fr.cameraXCache.length < width) throw new IllegalStateException("camera cache too small");

            int[][][] layers = layersOf(fr);
            boolean rebuilt = !Arrays.deepEquals(layers, fieldLayers);
            if (rebuilt) buildFields(layers);

            Scene scene = Scene.of(fr, layers, this);
            maps = ensure(maps, 4L * scene.maps.length);
            grid = ensure(grid, 4L * scene.grid.length);
            meta = ensure(meta, 4L * scene.meta.length);
            portals = ensure(portals, 4L * scene.portals.length);
            GpuProgram.Buffer before = fieldWall;
            fieldWall = ensure(fieldWall, 4L * fieldWallData.length);
            boolean wroteFields = rebuilt || fieldWall != before;
            before = fieldFinish;
            fieldFinish = ensure(fieldFinish, 4L * fieldFinishData.length);
            wroteFields |= fieldFinish != before;
            camX = ensure(camX, 4L * width);
            dp = ensure(dp, 4L * DP_COUNT);
            ip = ensure(ip, 4L * IP_COUNT);
            out = ensure(out, 4L * width * height);

            program.write(maps, scene.maps, scene.maps.length);
            program.write(grid, scene.grid, scene.grid.length);
            program.write(meta, scene.meta, scene.meta.length);
            program.write(portals, scene.portals, scene.portals.length);
            if (wroteFields) {
                program.write(fieldWall, fieldWallData, fieldWallData.length);
                program.write(fieldFinish, fieldFinishData, fieldFinishData.length);
            }
            program.write(camX, java.util.Arrays.copyOf(fr.cameraXCache, width), width);
            program.write(dp, cameraParams(fr, width, height), DP_COUNT);
            program.write(ip, ipValues(fr, scene, width, height), IP_COUNT);

            before = wall;
            wall = ensure(wall, 4L * fr.wallTexture.pixels().length);
            if (wall != before || fr.wallTexture.pixels() != wallSrc) { wallSrc = fr.wallTexture.pixels(); program.write(wall, wallSrc, wallSrc.length); }
            before = finish;
            finish = ensure(finish, 4L * fr.finishTexture.pixels().length);
            if (finish != before || fr.finishTexture.pixels() != finishSrc) { finishSrc = fr.finishTexture.pixels(); program.write(finish, finishSrc, finishSrc.length); }
            before = floor;
            floor = ensure(floor, 4L * fr.floorTexture.pixels().length);
            if (floor != before || fr.floorTexture.pixels() != floorSrc) { floorSrc = fr.floorTexture.pixels(); program.write(floor, floorSrc, floorSrc.length); }
            before = ceil;
            ceil = ensure(ceil, 4L * fr.ceilingTexture.pixels().length);
            if (ceil != before || fr.ceilingTexture.pixels() != ceilSrc) { ceilSrc = fr.ceilingTexture.pixels(); program.write(ceil, ceilSrc, ceilSrc.length); }

            boolean columns = fr.cameraPitch == 0;
            program.run(width, columns ? 1 : height, maps, grid, meta, portals, fieldWall, fieldFinish, wall, finish, floor, ceil, camX, dp, ip, out);
            program.read(out, fr.renderPixels, width * height);
        }
        private static int[][][] layersOf(RenderFrame fr) {
            int floors = fr.map3D == null ? 0 : fr.map3D.length;
            int[][][] layers = new int[1 + floors][][];
            layers[0] = fr.map;
            assert fr.map3D != null;
            System.arraycopy(fr.map3D, 0, layers, 1, floors);
            return layers;
        }
        private void buildFields(int[][][] layers) {
            int n = layers.length;
            double[][] wallParts = new double[n][], finishParts = new double[n][];
            fieldOffset = new int[n]; fieldCols = new int[n]; fieldRows = new int[n];
            fieldHasFinish = new boolean[n]; fieldHasPortals = new boolean[n];
            int total = 0;
            for (int l = 0; l < n; l++) {
                int[][] m = layers[l];
                boolean finish = false, portal = false;
                for (int[] row : m) for (int t : row) {
                    if (t == 2) finish = true;
                    else if (t == MazeGenerator.PORTAL) portal = true;
                }
                fieldHasFinish[l] = finish;
                fieldHasPortals[l] = portal;
                fieldCols[l] = m[0].length * SDF_SUB + 1;
                fieldRows[l] = m.length * SDF_SUB + 1;
                wallParts[l] = sdfField(m, false);
                finishParts[l] = finish ? sdfField(m, true) : new double[wallParts[l].length];
                fieldOffset[l] = total;
                total += wallParts[l].length;
            }
            fieldWallData = new double[total];
            fieldFinishData = new double[total];
            for (int l = 0; l < n; l++) {
                System.arraycopy(wallParts[l], 0, fieldWallData, fieldOffset[l], wallParts[l].length);
                System.arraycopy(finishParts[l], 0, fieldFinishData, fieldOffset[l], finishParts[l].length);
            }
            fieldLayers = layers.clone();
        }

        static double[] sdfField(int[][] m, boolean finish) {
            final int mh = m.length, mw = m[0].length;
            final int cols = mw * SDF_SUB + 1, rows = mh * SDF_SUB + 1;
            final double none = Double.POSITIVE_INFINITY;
            double[] gaps = new double[mh * cols];
            for (int i = 0; i < cols; i++) {
                double px = i / (double) SDF_SUB;
                int cx = (int) Math.floor(px);
                int x0 = Math.max(0, cx - SDF_WINDOW), x1 = Math.min(mw - 1, cx + SDF_WINDOW);
                for (int yy = 0; yy < mh; yy++) {
                    double best = none;
                    for (int xx = x0; xx <= x1; xx++) {
                        int t = m[yy][xx];
                        if (finish ? t != 2 : t != 1) continue;
                        double dx = Math.max(Math.max(xx - px, px - (xx + 1)), 0);
                        double sq = dx * dx;
                        if (sq < best) best = sq;
                    }
                    gaps[yy * cols + i] = best;
                }
            }
            double[] out = new double[cols * rows];
            for (int j = 0; j < rows; j++) {
                double py = j / (double) SDF_SUB;
                int cy = (int) Math.floor(py);
                int y0 = Math.max(0, cy - SDF_WINDOW), y1 = Math.min(mh - 1, cy + SDF_WINDOW);
                for (int i = 0; i < cols; i++) {
                    double best = none;
                    for (int yy = y0; yy <= y1; yy++) {
                        double gap = gaps[yy * cols + i];
                        if (gap == none) continue;
                        double dy = Math.max(Math.max(yy - py, py - (yy + 1)), 0);
                        double sq = dy * dy + gap;
                        if (sq < best) best = sq;
                    }
                    out[j * cols + i] = best == none ? SDF_WINDOW : Math.sqrt(best);
                }
            }
            return out;
        }

        static int[] ipValues(RenderFrame fr, Scene scene, int width, int height) {
            return new int[]{
                    width, height, scene.floors, scene.tinted ? 1 : 0,
                    fr.wallTexture.width(), fr.wallTexture.height(),
                    fr.finishTexture.width(), fr.finishTexture.height(),
                    fr.floorTexture.width(), fr.floorTexture.height(),
                    fr.ceilingTexture.width(), fr.ceilingTexture.height(),
                    fr.cameraPitch == 0 ? 1 : 0,
                    cfgQuality == 0 ? 48 : cfgQuality == 1 ? 96 : 160
            };
        }
        static double[] cameraParams(RenderFrame fr, int width, int height) {
            double dirX = Math.cos(fr.cameraAngle), dirY = Math.sin(fr.cameraAngle);
            double planeLength = Math.tan(Math.PI / 3.0 / 2.0);
            double focal = width / (2.0 * planeLength);
            double halfHeight = height / 2.0;
            double pitchSin = Math.sin(fr.cameraPitch), pitchCos = Math.cos(fr.cameraPitch);
            return new double[]{
                    fr.playerX, fr.playerY, fr.cameraHeight,
                    dirX, dirY, planeLength,
                    pitchSin, pitchCos, halfHeight, focal,
                    flashlightDistance, 0.0
            };
        }
        static final class Scene {
            int[] maps, grid, meta, portals;
            int floors;
            boolean tinted;

            static Scene of(RenderFrame fr, int[][][] layers, Gpu owner) {
                int floors = layers.length - 1;
                int layerCount = layers.length;

                java.util.List<PortalData> layerPortals = new java.util.ArrayList<>(layerCount);
                layerPortals.add(fr.portals == null ? new PortalData() : fr.portals);
                for (int f = 0; f < floors; f++) {
                    PortalData pd = fr.floorPortalsAll == null || f >= fr.floorPortalsAll.length ? null : fr.floorPortalsAll[f];
                    layerPortals.add(pd == null ? new PortalData() : pd);
                }

                int cells = 0, portalCount = 0;
                for (int l = 0; l < layerCount; l++) {
                    int[][] m = layers[l];
                    cells += m.length * m[0].length;
                    portalCount += layerPortals.get(l).values().size();
                }

                Scene s = new Scene();
                s.floors = floors;
                s.tinted = fr.map3D != null && fr.map3D.length > 1;
                s.maps = new int[cells];
                s.grid = new int[cells];
                s.meta = new int[8 * layerCount];
                s.portals = new int[Math.max(1, 9 * portalCount)];
                java.util.Arrays.fill(s.grid, -1);

                int offset = 0, portalIndex = 0;
                for (int l = 0; l < layerCount; l++) {
                    int[][] m = layers[l];
                    int h = m.length, w = m[0].length;
                    s.meta[8 * l] = offset;
                    s.meta[8 * l + 1] = w;
                    s.meta[8 * l + 2] = h;
                    s.meta[8 * l + 3] = owner.fieldOffset[l];
                    s.meta[8 * l + 4] = owner.fieldCols[l];
                    s.meta[8 * l + 5] = owner.fieldRows[l];
                    s.meta[8 * l + 6] = owner.fieldHasFinish[l] ? 1 : 0;
                    s.meta[8 * l + 7] = owner.fieldHasPortals[l] ? 1 : 0;
                    for (int y = 0; y < h; y++) System.arraycopy(m[y], 0, s.maps, offset + y * w, w);
                    for (PortalData.Portal p : layerPortals.get(l).values()) {
                        if (p.x < 0 || p.y < 0 || p.x >= w || p.y >= h) continue;
                        s.grid[offset + p.y * w + p.x] = portalIndex;
                        int base = 9 * portalIndex;
                        s.portals[base] = p.x;
                        s.portals[base + 1] = p.y;
                        s.portals[base + 2] = p.dirX;
                        s.portals[base + 3] = p.dirY;
                        s.portals[base + 4] = p.linkedX;
                        s.portals[base + 5] = p.linkedY;
                        s.portals[base + 6] = p.linkedDirX;
                        s.portals[base + 7] = p.linkedDirY;
                        s.portals[base + 8] = p.linkedFloor;
                        portalIndex++;
                    }
                    offset += w * h;
                }
                return s;
            }
        }
    }
    //endregion
}