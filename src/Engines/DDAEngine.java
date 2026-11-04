package Engines;
import Project.Game.*;
import Project.Game.SkyCeiling;
import Project.Helpers.*;
import java.awt.*;
import java.util.function.IntConsumer;
import Project.OpenCl.*;

public final class DDAEngine implements RenderEngine {
    //region Variables
    //region Render Configuration
    static volatile int cfgQuality = 1, cfgOutW = 960, cfgOutH = 540;
    static volatile MazeGenerator.GeometryMode cfgGeometry = MazeGenerator.GeometryMode.EUCLIDEAN;
    static volatile double cfgCurvature = HyperbolicMath.DEFAULT_CURVATURE;
    private RenderFrame fr;
    //endregion
    //region Render Constants
    static final String ID = "dda";
    static final int MAX_PRIMARY_STEPS = 6000;
    static final int wallBaseColor = 0xECD485;
    static final int ceilingBaseColor = 0x816E1E;
    static final int floorBaseColor = 0xD3AF63;
    static final int finishBaseColor = 0x33FF66;
    private static final double flashlightDistance = 2;
    private static final double HYP_PLANE_LENGTH = Math.tan(Math.toRadians(140.0) / 2.0);
    //endregion
    //endregion

    //region Engine Identity
    @Override public String id() { return ID; }
    @Override public String displayName() { return "DDA"; }
    @Override public int order() { return 0; }
    @Override public String hintKey() { return "engine_dda"; }
    @Override public String qualityHintKey() { return "quality_dda"; }
    @Override public boolean hasHyperbolicGeometry() { return true; }
    @Override public boolean hasOpenCL() { return true; }
    //endregion

    //region Lifecycle
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
        renderRayCasting();
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
    //endregion

    //region Euclidean Tracing
    //region Grid Stepping
    static TraceHit traceRay(RenderFrame renderFrame, double ox, double oy, double oz, double rx, double ry, double rz,
                             int[][] startMap, PortalData startPortals, TraceHit result) {
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
                if (candidateT >= planeHere || ++steps > DDAEngine.MAX_PRIMARY_STEPS) break segments;

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
                            if (renderFrame.map3D != null && portal.linkedFloor >= 0 && portal.linkedFloor < renderFrame.map3D.length) {
                                rayMap = renderFrame.map3D[portal.linkedFloor];
                                rayPortals = renderFrame.floorPortalsAll[portal.linkedFloor];
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
        result.dirX = rx; result.dirY = ry; result.dirZ = rz;

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
    static double wallHeightForType(int type) { return type == 2 ? 0.5 : 1.0; }
    //endregion

    //region Column Rendering
    private void renderRayCasting() {
        final boolean hyperbolic = cfgGeometry == MazeGenerator.GeometryMode.WRONG;
        final double dirX = Math.cos(fr.cameraAngle), dirY = Math.sin(fr.cameraAngle);
        final double planeLength = Math.tan((hyperbolic ? Math.toRadians(140) : Math.PI / 3.0) / 2.0);
        final double planeX = -dirY * planeLength, planeY = dirX * planeLength;
        final double focal = hyperbolic ? fr.renderHeight : fr.renderWidth / (2.0 * planeLength);
        final double halfHeight = fr.renderHeight / 2.0;
        final double pitchSin = Math.sin(fr.cameraPitch), pitchCos = Math.cos(fr.cameraPitch);
        final double horizon = hyperbolic ? halfHeight + fr.cameraPitch * halfHeight : halfHeight + focal * Math.tan(fr.cameraPitch);
        final double lightDistance = effectiveFlashlightDistance(hyperbolic);
        final int screenWidth = fr.renderWidth, screenHeight = fr.renderHeight;

        final int wallWidth = fr.wallTexture.width(), wallHeight = fr.wallTexture.height();
        final int mapWidth = fr.map[0].length, mapHeight = fr.map.length;
        final int[][] baseMap = fr.map;
        final int[] wallPixels = fr.wallTexture.pixels();
        final PortalData basePortals = fr.portals;

        ensureRaycastCaches(fr, screenWidth, screenHeight);
        final double[] floorEdge = fr.portalFloorEdgeCache, ceilEdge = fr.portalCeilEdgeCache;
        final double[] rowInvA = fr.rowInvACache, rowZA = fr.rowZACache;
        final int[] wallTop = fr.wallTopCache, wallBottom = fr.wallBottomCache;
        if (!hyperbolic) {
            for (int y = 0; y < screenHeight; y++) {
                double v = (halfHeight - (y + 0.5)) / focal;
                double a = pitchCos - pitchSin * v;
                if (Math.abs(a) < 0.02) a = a < 0 ? -0.02 : 0.02;
                rowInvA[y] = 1.0 / a;
                rowZA[y] = (pitchSin + pitchCos * v) / a;
            }
        }
        else { java.util.Arrays.fill(rowInvA, 1.0); java.util.Arrays.fill(rowZA, 0.0); }
        if (!hyperbolic && fr.cameraPitch != 0) {
            renderPitchedPerPixel(dirX, dirY, planeLength, rowInvA, rowZA, lightDistance);
            copyRenderBuffer(fr);
            return;
        }

        parallelRange(fr, screenWidth, x -> {
            wallTop[x] = Integer.MAX_VALUE; wallBottom[x] = -1;
            floorEdge[x] = -1e9; ceilEdge[x] = 1e9;
            double ox = fr.playerX, oy = fr.playerY;
            double rayDirX = dirX + planeX * fr.cameraXCache[x], rayDirY = dirY + planeY * fr.cameraXCache[x];
            double totalDistance = 0, localPerp = 0;
            int hops = 0, side = 0, hitType = 0, hitCellX = 0, hitCellY = 0;
            boolean hit = false;
            int[][] rayMap = baseMap;
            PortalData rayPortals = basePortals;
            double[] segments5 = null;

            segments:
            while (!hit) {
                int mapX = (int) Math.floor(ox), mapY = (int) Math.floor(oy);
                double deltaDistX = rayDirX == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / rayDirX);
                double deltaDistY = rayDirY == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / rayDirY);
                double sideDistX = (rayDirX < 0 ? ox - mapX : mapX + 1.0 - ox) * deltaDistX;
                double sideDistY = (rayDirY < 0 ? oy - mapY : mapY + 1.0 - oy) * deltaDistY;

                while (true) {
                    if (sideDistX < sideDistY) {
                        sideDistX += deltaDistX;
                        mapX += rayDirX < 0 ? -1 : 1;
                        side = 0;
                    } else {
                        sideDistY += deltaDistY;
                        mapY += rayDirY < 0 ? -1 : 1;
                        side = 1;
                    }
                    if (mapX < 0 || mapY < 0 || mapX >= mapWidth || mapY >= mapHeight) return;
                    hitType = rayMap[mapY][mapX];
                    if (hitType == MazeGenerator.PORTAL && hops < 8) {
                        PortalData.Portal portal = rayPortals.get(mapX, mapY);
                        if (portal != null && PortalData.entersFromCorrectSide(portal, rayDirX, rayDirY)) {
                            double t = side == 0 ? sideDistX - deltaDistX : sideDistY - deltaDistY;
                            double hx = ox + rayDirX * t, hy = oy + rayDirY * t;
                            totalDistance += t;
                            double[] w = PortalData.warp(portal, hx, hy, rayDirX, rayDirY);
                            ox = w[0]; oy = w[1]; rayDirX = w[2]; rayDirY = w[3];
                            if (fr.map3D != null && portal.linkedFloor >= 0 && portal.linkedFloor < fr.map3D.length) {
                                rayMap = fr.map3D[portal.linkedFloor];
                                rayPortals = fr.floorPortalsAll[portal.linkedFloor];
                            }
                            if (segments5 == null) segments5 = new double[8 * 5];
                            segments5[0] = totalDistance;
                            segments5[1] = ox; segments5[2] = oy;
                            segments5[3] = rayDirX; segments5[4] = rayDirY;
                            hops++;
                            continue segments;
                        }
                    }

                    if (hitType == 2) {
                        double candidate = totalDistance + (side == 0 ? sideDistX - deltaDistX : sideDistY - deltaDistY);
                        double topRow = hyperbolic ? horizon : projectY(candidate, 0.0, focal, halfHeight, pitchSin, pitchCos);
                        if (topRow >= screenHeight) continue;
                        if (hyperbolic) shadeExactColumnHyp(x, dirX, dirY, focal, horizon);
                        else shadeExactColumn(x, dirX, dirY, planeLength, pitchSin, pitchCos, halfHeight, focal);
                        wallTop[x] = 0; wallBottom[x] = screenHeight - 1;
                        return;
                    }
                    if (hitType == 1) {
                        localPerp = side == 0 ? sideDistX - deltaDistX : sideDistY - deltaDistY;
                        hitCellX = mapX; hitCellY = mapY;
                        hit = true;
                        break;
                    }
                }
            }

            double perp = Math.max(totalDistance + localPerp, 0.0001);
            double projected = hyperbolic ? HyperbolicMath.hyperbolicDistance(perp, cfgCurvature) : perp;
            double wallTopZ = wallHeightForType(hitType) - fr.cameraHeight;
            boolean exactFace = !hyperbolic;
            double lateral = fr.cameraXCache[x] * planeLength;
            double faceNf = side == 0 ? dirX : dirY, faceNr = side == 0 ? -dirY : dirX;
            double faceC = side == 0 ? ((rayDirX > 0 ? hitCellX : hitCellX + 1) - fr.playerX) : ((rayDirY > 0 ? hitCellY : hitCellY + 1) - fr.playerY);
            int drawStart, drawEnd;
            if (hyperbolic) {
                int wallHeightPx = (int) (focal / projected);
                drawStart = Math.max((int) (horizon - wallHeightPx / 2.0), 0);
                drawEnd = Math.min((int) (horizon + wallHeightPx / 2.0), screenHeight - 1);
            }
            else {
                double top, bottom;
                top = solveWallEdge(faceC, faceNf, faceNr, lateral, wallTopZ, focal, halfHeight, pitchSin, pitchCos, perp);
                bottom = solveWallEdge(faceC, faceNf, faceNr, lateral, (-fr.cameraHeight), focal, halfHeight, pitchSin, pitchCos, perp);
                drawStart = (int) Math.max(0, Math.ceil(Math.max(top, -1) - 0.5));
                drawEnd = (int) Math.min(screenHeight - 1, Math.floor(Math.min(bottom, screenHeight + 1) - 0.5));
            }

            if (drawStart > drawEnd) return;
            wallTop[x] = drawStart; wallBottom[x] = drawEnd;

            double wallX = side == 0 ? oy + localPerp * rayDirY : ox + localPerp * rayDirX;
            wallX -= Math.floor(wallX);
            if (side == 0 && rayDirX < 0) wallX = 1.0 - wallX;
            if (side == 1 && rayDirY > 0) wallX = 1.0 - wallX;

            double brightness = Math.clamp(1.0 - projected / lightDistance, 0.03, 1.0);
            if (side == 1) brightness *= 0.85;
            int texX = Math.min((int) (wallX * wallWidth), wallWidth - 1);
            if (texX < 0) texX = 0;
            int offset = drawStart * screenWidth + x;
            int wallHeightPx = hyperbolic ? (int) (focal / projected) : 0;
            double wallHeightOld = Math.max(wallHeightPx, 1.0);

            for (int y = drawStart; y <= drawEnd; y++) {
                double fraction;
                double rowWallX = wallX;
                double rowT = perp, rowNorm = 1.0;
                if (hyperbolic) fraction = (y - (horizon - wallHeightPx / 2.0)) / wallHeightOld;
                else {
                    double lateralRow = lateral * rowInvA[y];
                    double faceDen = faceNf + lateralRow * faceNr;
                    double depth = Math.abs(faceDen) > 1e-9 ? faceC / faceDen : perp;
                    double along = side == 0 ? fr.playerY + depth * (dirY + lateralRow * dirX) : fr.playerX + depth * (dirX - lateralRow * dirY);
                    double cellLo = side == 0 ? hitCellY : hitCellX;
                    rowT = depth;
                    rowNorm = Math.sqrt(1.0 + lateralRow * lateralRow + rowZA[y] * rowZA[y]);
                    if (along >= cellLo && along <= cellLo + 1.0) {
                        fraction = (wallTopZ - (depth * rowZA[y])) / (wallTopZ + fr.cameraHeight);
                        rowWallX = along - cellLo;
                        if (side == 0 && rayDirX < 0 || side == 1 && rayDirY > 0) rowWallX = 1.0 - rowWallX;
                    }
                    else {
                        double[] rowHit = castWallForRow(fr.playerX, fr.playerY, dirX - lateralRow * dirY, dirY + lateralRow * dirX, baseMap, mapWidth, mapHeight);
                        if (rowHit != null) {
                            double rowTopZ = wallHeightForType((int) rowHit[3]) - fr.cameraHeight;
                            fraction = (rowTopZ - (rowHit[0] * rowZA[y])) / (rowTopZ + fr.cameraHeight);
                            rowWallX = rowHit[2];
                            rowT = rowHit[0];
                        }
                        else {
                            fraction = (wallTopZ - (depth * rowZA[y])) / (wallTopZ + fr.cameraHeight);
                            rowWallX = wallX;
                        }
                    }
                }
                if (exactFace) {
                    texX = Math.min((int) (rowWallX * wallWidth), wallWidth - 1);
                    if (texX < 0) texX = 0;
                }
                int texY = (int) (Math.clamp(fraction, 0.0, 0.999999) * wallHeight);
                int color;
                if (wallWidth > 0 && wallHeight > 0) color = wallPixels[texY * wallWidth + texX];
                else color = wallBaseColor;
                double rowBrightness = exactFace ? wallBrightness(rowT * rowNorm, lightDistance, side) : brightness;
                fr.renderPixels[offset] = applyBrightness(color, rowBrightness);
                offset += screenWidth;
            }
        });

        renderHorizontalSurfaces(dirX, dirY, planeX, planeY, horizon, focal, halfHeight, pitchSin, pitchCos, hyperbolic);
        copyRenderBuffer(fr);
    }
    private void shadeExactColumn(int x, double dirX, double dirY, double planeLength, double pitchSin, double pitchCos, double halfHeight, double focal) {
        final int width = fr.renderWidth, height = fr.renderHeight;
        final double lateral = fr.cameraXCache[x] * planeLength;
        final double lightDistance = effectiveFlashlightDistance(false);
        final TraceHit hit = new TraceHit();
        for (int y = 0; y < height; y++) {
            double v = (halfHeight - (y + 0.5)) / focal;
            double rx = dirX * pitchCos - dirY * lateral - dirX * pitchSin * v;
            double ry = dirY * pitchCos + dirX * lateral - dirY * pitchSin * v;
            double rz = pitchSin + pitchCos * v;
            double inv = 1.0 / Math.sqrt(rx * rx + ry * ry + rz * rz);
            traceRay(fr, fr.playerX, fr.playerY, fr.cameraHeight, rx * inv, ry * inv, rz * inv, fr.map, fr.portals, hit);
            fr.renderPixels[y * width + x] = shadeTraceHit(hit, lightDistance);
        }
    }
    //endregion
    //endregion

    //region Hyperbolic Tracing
    //region Hyperbolic Geometry
    static double[] hypPixelRay(double cx, double py, double dirX, double dirY, double focal, double horizon) {
        double rdX = dirX - dirY * HYP_PLANE_LENGTH * cx, rdY = dirY + dirX * HYP_PLANE_LENGTH * cx;
        double L = Math.sqrt(rdX * rdX + rdY * rdY);
        return new double[]{rdX / L, rdY / L, L, -(py - horizon) / focal};
    }
    static TraceHit traceHyp(RenderFrame renderFrame, double ox, double oy, double z0, double ux, double uy, double L, double slope, TraceHit result) {
        result.reset();
        final int[][] m = renderFrame.map;
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
        for (int steps = 0; steps < DDAEngine.MAX_PRIMARY_STEPS; steps++) {
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
    private static double hypFloorDistance(double z0, double slope, double L) {
        if (slope >= 0 || z0 <= 1e-9) return Double.POSITIVE_INFINITY;
        return L * HyperbolicMath.inverseHyperbolicDistance(z0 / -slope, cfgCurvature);
    }
    private static double hypCeilDistance(double z0, double slope, double L) {
        if (slope <= 0 || z0 >= 1.0 - 1e-9) return Double.POSITIVE_INFINITY;
        return L * HyperbolicMath.inverseHyperbolicDistance((1.0 - z0) / slope, cfgCurvature);
    }
    static double effectiveFlashlightDistance(boolean hyperbolic) { return hyperbolic ? flashlightDistance * 1.6 : flashlightDistance; }
    //endregion
    //region Hyperbolic Rendering
    private void shadeExactColumnHyp(int x, double dirX, double dirY, double focal, double horizon) {
        final int width = fr.renderWidth, height = fr.renderHeight;
        final double lightDistance = effectiveFlashlightDistance(true);
        for (int y = 0; y < height; y++) {
            double[] pr = hypPixelRay(fr.cameraXCache[x], y + 0.5, dirX, dirY, focal, horizon);
            TraceHit hit = traceHyp(fr, fr.playerX, fr.playerY, fr.cameraHeight, pr[0], pr[1], pr[2], pr[3], new TraceHit());
            fr.renderPixels[y * width + x] = shadeTraceHit(hit, lightDistance);
        }
    }
    //endregion
    //endregion

    //region Surface Projection
    private static double solveWallEdge(double faceC, double nf, double nr, double lateral, double offset, double focal, double halfHeight, double sinP, double cosP, double startDepth) {
        double row = projectY(startDepth, offset, focal, halfHeight, sinP, cosP);
        for (int i = 0; i < 3 && Double.isFinite(row); i++) {
            double a = cosP - sinP * ((halfHeight - row) / focal);
            if (Math.abs(a) < 1e-4) break;
            double den = nf + lateral / a * nr;
            if (Math.abs(den) < 1e-9) break;
            double depth = faceC / den;
            if (!(depth > 0)) break;
            row = projectY(depth, offset, focal, halfHeight, sinP, cosP);
        }
        return row;
    }
    private static double projectY(double depth, double offset, double focal, double halfHeight, double sinP, double cosP) {
        double up = -depth * sinP + offset * cosP;
        double forward = depth * cosP + offset * sinP;
        if (forward <= 1e-6) return up < 0 ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
        return halfHeight - focal * up / forward;
    }
    private static double rowDepth(int y, double horizon, double focal, double halfHeight, double sinP, double cosP, double planeOffset, boolean hyperbolic) {
        if (hyperbolic) {
            double delta = planeOffset < 0 ? y - horizon : horizon - y;
            return delta > 0.0001 ? focal / (2.0 * delta) : Double.NaN;
        }
        double v = (halfHeight - (y + 0.5)) / focal;
        double den = sinP + v * cosP;
        if (Math.abs(den) < 1e-9) return Double.NaN;
        double depth = planeOffset * (cosP - v * sinP) / den;
        return depth > 0 ? depth : Double.NaN;
    }
    private static double[] castWallForRow(double ox, double oy, double rx, double ry, int[][] map, int mapWidth, int mapHeight) {
        int mapX = (int) Math.floor(ox), mapY = (int) Math.floor(oy);
        double deltaX = rx == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / rx);
        double deltaY = ry == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / ry);
        double sideX = (rx < 0 ? ox - mapX : mapX + 1.0 - ox) * deltaX;
        double sideY = (ry < 0 ? oy - mapY : mapY + 1.0 - oy) * deltaY;
        int stepX = rx < 0 ? -1 : 1, stepY = ry < 0 ? -1 : 1;
        int side;
        for (int guard = 0; guard < 4096; guard++) {
            if (sideX < sideY) { sideX += deltaX; mapX += stepX; side = 0; }
            else { sideY += deltaY; mapY += stepY; side = 1; }
            if (mapX < 0 || mapY < 0 || mapX >= mapWidth || mapY >= mapHeight) return null;
            int type = map[mapY][mapX];
            if (type == 1 || type == 2) {
                double t = side == 0 ? sideX - deltaX : sideY - deltaY;
                double hitX = ox + rx * t, hitY = oy + ry * t;
                double u = side == 0 ? hitY : hitX;
                u -= Math.floor(u);
                if ((side == 0 && rx < 0) || (side == 1 && ry > 0)) u = 1.0 - u;
                return new double[]{t, side, Math.clamp(u, 0.0, 0.999999), type};
            }
        }
        return null;
    }
    private void renderPitchedPerPixel(double dirX, double dirY, double planeLength, double[] rowInvA, double[] rowZA, double lightDistance) {
        final int width = fr.renderWidth, height = fr.renderHeight;
        final int[][] m = fr.map;
        final int mw = fr.map[0].length, mh = fr.map.length;
        final double h = fr.cameraHeight;
        final double baseX = fr.playerX, baseY = fr.playerY;
        final int wallWidth = fr.wallTexture.width(), wallHeight = fr.wallTexture.height();
        final int finishWidth = fr.finishTexture.width(), finishHeight = fr.finishTexture.height();
        final int[] wallPixels = fr.wallTexture.pixels(), finishPixels = fr.finishTexture.pixels();

        parallelRange(fr, height, y -> {
            final double rowZ = rowZA[y];
            final double rowScale = rowInvA[y];
            final boolean floorPlane = rowZ < 0;
            final double tPlane;
            if (rowZ < -1e-12) tPlane = -h / rowZ;
            else if (rowZ > 1e-12) tPlane = (1.0 - h) / rowZ;
            else tPlane = Double.POSITIVE_INFINITY;

            for (int x = 0; x < width; x++) {
                double lateralRow = fr.cameraXCache[x] * planeLength * rowScale;
                double rx = dirX - lateralRow * dirY, ry = dirY + lateralRow * dirX;
                int offset = y * width + x;
                final double norm = Math.sqrt(rx * rx + ry * ry + rowZ * rowZ);

                int mapX = (int) Math.floor(baseX), mapY = (int) Math.floor(baseY);
                double deltaX = rx == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / rx);
                double deltaY = ry == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / ry);
                double sideX = (rx < 0 ? baseX - mapX : mapX + 1.0 - baseX) * deltaX;
                double sideY = (ry < 0 ? baseY - mapY : mapY + 1.0 - baseY) * deltaY;
                int stepX = rx < 0 ? -1 : 1, stepY = ry < 0 ? -1 : 1;

                boolean wallFound = false, exactPortal = false;
                int wType = 0, wSide;
                double wT = 0;
                while (true) {
                    double tNext;
                    if (sideX < sideY) { tNext = sideX; sideX += deltaX; mapX += stepX; wSide = 0; }
                    else { tNext = sideY; sideY += deltaY; mapY += stepY; wSide = 1; }
                    if (tNext >= tPlane) break;
                    if (mapX < 0 || mapY < 0 || mapX >= mw || mapY >= mh) break;
                    int type = m[mapY][mapX];
                    if (type == MazeGenerator.PORTAL) {
                        PortalData.Portal portal = fr.portals.get(mapX, mapY);
                        if (portal != null && PortalData.entersFromCorrectSide(portal, rx, ry)) { exactPortal = true; break; }
                    }
                    if (type == 1 || type == 2) {
                        double zW = h + tNext * rowZ;
                        if (zW >= 0 && zW <= wallHeightForType(type)) {
                            wallFound = true; wType = type; wT = tNext;
                            break;
                        }
                    }
                }

                if (exactPortal) {
                    double n = Math.sqrt(rx * rx + ry * ry + rowZ * rowZ);
                    fr.renderPixels[offset] = shadeTraceHit(traceRay(fr, baseX, baseY, h, rx / n, ry / n, rowZ / n, m, fr.portals, new TraceHit()), lightDistance);
                    continue;
                }
                if (wallFound) {
                    double hitX = baseX + rx * wT, hitY = baseY + ry * wT;
                    double u = wSide == 0 ? hitY : hitX;
                    u -= Math.floor(u);
                    if ((wSide == 0 && rx < 0) || (wSide == 1 && ry > 0)) u = 1.0 - u;
                    double wallH = wallHeightForType(wType);
                    double fraction = (wallH - (h + wT * rowZ)) / wallH;
                    int color;
                    boolean finish = wType == 2;
                    int tw = finish ? finishWidth : wallWidth, th = finish ? finishHeight : wallHeight;
                    if (tw > 0 && th > 0) {
                        int tx = Math.min((int) (u * tw), tw - 1);
                        int ty = Math.min((int) (Math.clamp(fraction, 0.0, 0.999999) * th), th - 1);
                        color = finish ? finishPixels[ty * tw + tx] : wallPixels[ty * tw + tx];
                    }
                    else color = finish ? finishBaseColor : wallBaseColor;
                    double brightness = wallBrightness(wT * norm, lightDistance, wSide);
                    fr.renderPixels[offset] = applyBrightness(color, brightness);
                }
                else if (Double.isFinite(tPlane) && (floorPlane || !fr.skyCeiling)) {
                    double wx = baseX + rx * tPlane, wy = baseY + ry * tPlane;
                    int color = floorPlane ? surfaceColor(fr.floorTexture, wx, wy, floorBaseColor, cfgQuality) : surfaceColor(fr.ceilingTexture, wx, wy, ceilingBaseColor, cfgQuality);
                    if (floorPlane && is3D(fr)) color = tintStairs(color, wx, wy, m);
                    double brightness = Math.clamp(1.0 - tPlane * norm / lightDistance, 0.05, 1.0);
                    fr.renderPixels[offset] = applyBrightness(color, brightness);
                }
                else fr.renderPixels[offset] = fr.skyCeiling && !floorPlane ? SkyCeiling.colorDir(rx, ry, rowZ, fr.skyTime) : ceilingBaseColor;
            }
        });
    }
    private void skyRow(int y, double dirX, double dirY, double planeX, double planeY, double rowScale, double rowV,
                        int[] wallTop, int[] wallBottom, double[] ceilEdge) {
        final int width = fr.renderWidth;
        double vecX = planeX * rowScale, vecY = planeY * rowScale;
        double leftRayX = dirX - vecX, leftRayY = dirY - vecY;
        double rayStepX = (2.0 * vecX) / width, rayStepY = (2.0 * vecY) / width;
        for (int x = 0; x < width; x++) {
            boolean covered = (y >= wallTop[x] && y <= wallBottom[x]) || y >= ceilEdge[x];
            if (!covered) fr.renderPixels[y * width + x] = SkyCeiling.colorDir(leftRayX + x * rayStepX, leftRayY + x * rayStepY, rowV, fr.skyTime);
        }
    }
    private void renderHorizontalSurfaces(double dirX, double dirY, double planeX, double planeY, double horizon, double focal,
                                         double halfHeight, double sinP, double cosP, boolean hyperbolic) {
        final double lightDistance = effectiveFlashlightDistance(hyperbolic);
        final int width = fr.renderWidth;
        final int[] wallTop = fr.wallTopCache, wallBottom = fr.wallBottomCache;
        final double[] floorEdge = fr.portalFloorEdgeCache, ceilEdge = fr.portalCeilEdgeCache;
        final boolean tinted = is3D(fr);
        final int quality = cfgQuality;
        final double[] rowInvA = fr.rowInvACache, rowZA = fr.rowZACache;
        final double planeLength2 = planeX * planeX + planeY * planeY;

        parallelRange(fr, fr.renderHeight, y -> {
            double depth = rowDepth(y, horizon, focal, halfHeight, sinP, cosP, (-fr.cameraHeight), hyperbolic);
            boolean floor = depth > 0;
            if (!floor) {
                depth = rowDepth(y, horizon, focal, halfHeight, sinP, cosP, (1.0 - fr.cameraHeight), hyperbolic);
                if (!(depth > 0)) {
                    if (fr.skyCeiling) skyRow(y, dirX, dirY, planeX, planeY, rowInvA[y], rowZA[y], wallTop, wallBottom, ceilEdge);
                    return;
                }
            }
            double rowScale = rowInvA[y];
            double vecX = planeX * rowScale, vecY = planeY * rowScale;
            double leftRayX = dirX - vecX, leftRayY = dirY - vecY;
            double rayStepX = (2.0 * vecX) / width, rayStepY = (2.0 * vecY) / width;
            double perceived = depth;
            double flatDepth = hyperbolic ? HyperbolicMath.inverseHyperbolicDistance(perceived, cfgCurvature) : depth;
            double brightness = Math.clamp(1.0 - perceived / lightDistance, 0.05, 1.0);
            final double rowV = rowZA[y];
            double worldX = fr.playerX + flatDepth * leftRayX, worldY = fr.playerY + flatDepth * leftRayY;
            double stepX = flatDepth * rayStepX, stepY = flatDepth * rayStepY;
            int offset = y * width;
            for (int x = 0; x < width; x++) {
                boolean covered = (y >= wallTop[x] && y <= wallBottom[x]) || (floor ? y <= floorEdge[x] : y >= ceilEdge[x]);
                if (!covered && !floor && fr.skyCeiling) {
                    fr.renderPixels[offset + x] = SkyCeiling.colorDir(leftRayX + x * rayStepX, leftRayY + x * rayStepY, rowV, fr.skyTime);
                } else if (!covered) {
                    int color = floor ? surfaceColor(fr.floorTexture, worldX, worldY, floorBaseColor, quality) : surfaceColor(fr.ceilingTexture, worldX, worldY, ceilingBaseColor, quality);
                    if (floor && tinted) color = tintStairs(color, worldX, worldY, fr.map);
                    double pixelBrightness = brightness;
                    if (!hyperbolic) {
                        double c = fr.cameraXCache[x];
                        pixelBrightness = Math.clamp(1.0 - depth * Math.sqrt(1.0 + planeLength2 * c * c + rowV * rowV) / lightDistance, 0.05, 1.0);
                    }
                    fr.renderPixels[offset + x] = applyBrightness(color, pixelBrightness);
                }
                worldX += stepX; worldY += stepY;
            }
        });
    }
    //endregion

    //region Shading
    static int applyBrightness(int color, double brightness) {
        int r = (int) Math.min(255, ((color >> 16) & 255) * brightness);
        int g = (int) Math.min(255, ((color >> 8) & 255) * brightness);
        int b = (int) Math.min(255, (color & 255) * brightness);
        return (r << 16) | (g << 8) | b;
    }
    static int shadeTraceHit(RenderFrame renderFrame, TraceHit hit, double lightDistance, PlaneColor planeFn) {
        if (!hit.hit) return renderFrame.skyCeiling ? SkyCeiling.colorDir(hit.dirX, hit.dirY, hit.dirZ, renderFrame.skyTime) : ceilingBaseColor;
        if (hit.isPlane) {
            boolean floor = hit.type == -1;
            if (!floor && renderFrame.skyCeiling) return SkyCeiling.colorDir(hit.dirX, hit.dirY, hit.dirZ, renderFrame.skyTime);
            int color = floor ? planeFn.at(renderFrame.floorTexture, hit.x, hit.y, floorBaseColor) : planeFn.at(renderFrame.ceilingTexture, hit.x, hit.y, ceilingBaseColor);
            if (floor && is3D(renderFrame)) color = tintStairs(color, hit.x, hit.y, hit.map);
            return applyBrightness(color, Math.clamp(1.0 - hit.distance / lightDistance, 0.05, 1.0));
        }
        boolean finish = hit.type == 2;
        int color = sampleWallTexture(finish ? renderFrame.finishTexture : renderFrame.wallTexture, hit);
        double brightness = Math.clamp(1.0 - hit.distance / lightDistance, 0.03, 1.0);
        if (hit.side == 1) brightness *= 0.85;
        return applyBrightness(color, brightness);
    }
    private int shadeTraceHit(TraceHit hit, double lightDistance) { return shadeTraceHit(fr, hit, lightDistance, this::surfacePlaneColor); }
    private static double wallBrightness(double dist, double lightDistance, int side) {
        double b = Math.clamp(1.0 - dist / lightDistance, 0.03, 1.0);
        return side == 1 ? b * 0.85 : b;
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
    @FunctionalInterface interface PlaneColor { int at(RuntimeTexture texture, double worldX, double worldY, int baseColor); }
    static int sampleTexture(RuntimeTexture texture, double worldX, double worldY, int baseColor) {
        int width = texture.width(), height = texture.height();
        if (width <= 0 || height <= 0) return baseColor;
        double fracX = worldX - Math.floor(worldX);
        double fracY = worldY - Math.floor(worldY);
        int tx = Math.min((int)(fracX * width), width - 1);
        int ty = Math.min((int)(fracY * height), height - 1);
        return texture.pixels()[ty * width + tx];
    }
    private int sampleTextureBilinear(RuntimeTexture texture, double worldX, double worldY, int baseColor) {
        int width = texture.width(), height = texture.height();
        if (width <= 0 || height <= 0) return baseColor;
        double u = (worldX - Math.floor(worldX)) * width - 0.5;
        double v = (worldY - Math.floor(worldY)) * height - 0.5;
        int x0 = Math.floorMod((int) Math.floor(u), width), x1 = Math.floorMod(x0 + 1, width);
        int y0 = Math.floorMod((int) Math.floor(v), height), y1 = Math.floorMod(y0 + 1, height);
        double fx = u - Math.floor(u), fy = v - Math.floor(v);
        int[] p = texture.pixels();
        int c00 = p[y0 * width + x0], c10 = p[y0 * width + x1], c01 = p[y1 * width + x0], c11 = p[y1 * width + x1];
        double w00 = (1 - fx) * (1 - fy), w10 = fx * (1 - fy), w01 = (1 - fx) * fy, w11 = fx * fy;
        int r = (int) (((c00 >> 16) & 255) * w00 + ((c10 >> 16) & 255) * w10 + ((c01 >> 16) & 255) * w01 + ((c11 >> 16) & 255) * w11);
        int g = (int) (((c00 >> 8) & 255) * w00 + ((c10 >> 8) & 255) * w10 + ((c01 >> 8) & 255) * w01 + ((c11 >> 8) & 255) * w11);
        int b = (int) ((c00 & 255) * w00 + (c10 & 255) * w10 + (c01 & 255) * w01 + (c11 & 255) * w11);
        return (r << 16) | (g << 8) | b;
    }
    static int sampleWallTexture(RuntimeTexture texture, TraceHit hit) {
        if (texture.width() <= 0 || texture.height() <= 0) return hit.type == 2 ? finishBaseColor : wallBaseColor;
        int tx = Math.min((int) (hit.wallU * texture.width()), texture.width() - 1);
        int ty = Math.min((int) ((1.0 - hit.wallV) * texture.height()), texture.height() - 1);
        return texture.pixels()[ty * texture.width() + Math.max(0, tx)];
    }
    private int surfacePlaneColor(RuntimeTexture texture, double worldX, double worldY, int baseColor) { return surfaceColor(texture, worldX, worldY, baseColor, cfgQuality); }
    private int surfaceColor(RuntimeTexture texture, double worldX, double worldY, int baseColor, int quality) {
        if (quality == 0) return baseColor;
        if (quality == 2) return sampleTextureBilinear(texture, worldX, worldY, baseColor);
        return sampleTexture(texture, worldX, worldY, baseColor);
    }
    //endregion

    //region Ray State
    public static final class TraceHit {
        double x, y, z, distance;
        int type, side;
        double wallU, wallV;
        double dirX, dirY, dirZ;
        boolean hit, isPlane, hyp;
        double hypSlope;
        double nrmX, nrmY;
        int[][] map;
        PortalData portals;
        boolean curved;

        void reset() {
            x = y = z = distance = wallU = wallV = dirX = dirY = dirZ = hypSlope = 0; type = side = 0;
            hit = isPlane = hyp = false; map = null; portals = null;
            curved = false; nrmX = nrmY = 0;
        }
    }
    //endregion

    //region GPU Acceleration
    static final class Gpu {
        private final String source = Project.OpenCl.KernelSource.load(DDAEngine.class, SkyCeiling.GPU_SOURCE);
        private static Gpu instance;
        private static boolean disabled;
        private static final int DP_COUNT = 12, IP_COUNT = 14;
        private final GpuProgram program = new GpuProgram(source, "dda_render");
        private GpuProgram.Buffer maps, grid, meta, portals, camX, dp, ip, out, wall, finish, floor, ceil;
        private int[] wallSrc, finishSrc, floorSrc, ceilSrc;

        static boolean tryRender(RenderFrame renderFrame) { synchronized (GpuRuntime.LOCK) { return tryRenderLocked(renderFrame); } }
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

            program.run(width, height, maps, grid, meta, portals, wall, finish, floor, ceil, camX, dp, ip, out);
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
                    width, height, renderFrame.quality, scene.floors, scene.tinted ? 1 : 0,
                    renderFrame.wallTexture.width(), renderFrame.wallTexture.height(),
                    renderFrame.finishTexture.width(), renderFrame.finishTexture.height(),
                    renderFrame.floorTexture.width(), renderFrame.floorTexture.height(),
                    renderFrame.ceilingTexture.width(), renderFrame.ceilingTexture.height(),
                    renderFrame.skyCeiling ? 1 : 0
            };
        }
        static double[] cameraParams(RenderFrame renderFrame, int width, int height) {
            double dirX = Math.cos(renderFrame.cameraAngle), dirY = Math.sin(renderFrame.cameraAngle);
            double planeLength = Math.tan((Math.PI / 3.0) / 2.0);
            double focal = width / (2.0 * planeLength);
            double halfHeight = height / 2.0;
            double pitchSin = Math.sin(renderFrame.cameraPitch), pitchCos = Math.cos(renderFrame.cameraPitch);
            double lightDistance = 2.0;
            return new double[]{
                    renderFrame.playerX, renderFrame.playerY, renderFrame.cameraHeight,
                    dirX, dirY, planeLength,
                    pitchSin, pitchCos, halfHeight, focal, lightDistance, renderFrame.skyTime
            };
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