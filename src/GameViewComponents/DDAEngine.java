package GameViewComponents;

import Helpers.*;

public final class DDAEngine extends RenderEngineBase {
    public static final double NO_EDGE = 1e9;

    public void renderRayCasting() {
        final boolean hyperbolic = RenderEngineConfig.GEOMETRY_MODE == MazeGenerator.GeometryMode.WRONG;
        final double dirX = Math.cos(cameraAngle), dirY = Math.sin(cameraAngle);
        final double planeLength = Math.tan((hyperbolic ? Math.toRadians(140) : Math.PI / 3.0) / 2.0);
        final double planeX = -dirY * planeLength, planeY = dirX * planeLength;
        final double focal = hyperbolic ? renderHeight : renderWidth / (2.0 * planeLength);
        final double halfHeight = renderHeight / 2.0;
        final double pitchSin = Math.sin(cameraPitch), pitchCos = Math.cos(cameraPitch);
        final double horizon = hyperbolic ? halfHeight + cameraPitch * halfHeight : halfHeight + focal * Math.tan(cameraPitch);
        final double lightDistance = effectiveFlashlightDistance(hyperbolic);
        final int screenWidth = renderWidth, screenHeight = renderHeight;

        final int wallWidth = wallTexture.width(), wallHeight = wallTexture.height();
        final int finishWidth = finishTexture.width(), finishHeight = finishTexture.height();
        final int mapWidth = map[0].length, mapHeight = map.length;
        final int[][] baseMap = map;
        final int[] wallPixels = wallTexture.pixels(), finishPixels = finishTexture.pixels();
        final PortalData basePortals = portals;

        ensureRaycastCaches(screenWidth, screenHeight);
        final double[] floorEdge = portalFloorEdgeCache, ceilEdge = portalCeilEdgeCache;
        final double[] rowInvA = rowInvACache, rowZA = rowZACache;
        final int[] wallTop = wallTopCache, wallBottom = wallBottomCache;
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
        if (!hyperbolic && cameraPitch != 0) {
            renderPitchedPerPixel(dirX, dirY, planeLength, rowInvA, rowZA, lightDistance);
            copyRenderBuffer();
            return;
        }

        parallelRange(0, screenWidth, x -> {
            wallTop[x] = Integer.MAX_VALUE; wallBottom[x] = -1;
            floorEdge[x] = -NO_EDGE; ceilEdge[x] = NO_EDGE;
            double ox = playerX, oy = playerY;
            double rayDirX = dirX + planeX * cameraXCache[x], rayDirY = dirY + planeY * cameraXCache[x];
            double totalDistance = 0, localPerp = 0;
            int hops = 0, side = 0, hitType = 0, hitCellX = 0, hitCellY = 0;
            boolean hit = false;
            int[][] rayMap = baseMap;
            PortalData rayPortals = basePortals;
            double[] segments5 = null;
            int[][][] segmentMaps = null;
            int segmentCount = 0;

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
                            if (map3D != null && portal.linkedFloor >= 0 && portal.linkedFloor < map3D.length) {
                                rayMap = map3D[portal.linkedFloor];
                                rayPortals = floorPortalsAll[portal.linkedFloor];
                            }
                            if (segments5 == null) { segments5 = new double[8 * 5]; segmentMaps = new int[8][][]; }
                            segments5[segmentCount * 5] = totalDistance;
                            segments5[segmentCount * 5 + 1] = ox; segments5[segmentCount * 5 + 2] = oy;
                            segments5[segmentCount * 5 + 3] = rayDirX; segments5[segmentCount * 5 + 4] = rayDirY;
                            segmentMaps[segmentCount++] = rayMap;
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
            double projected = hyperbolic ? HyperbolicMath.hyperbolicDistance(perp, RenderEngineConfig.HYPERBOLIC_CURVATURE) : perp;
            double wallTopZ = wallHeightForType(hitType) - cameraHeight;
            boolean exactFace = !hyperbolic && segmentCount == 0;
            double lateral = cameraXCache[x] * planeLength;
            double faceNf = side == 0 ? dirX : dirY, faceNr = side == 0 ? -dirY : dirX;
            double faceC = side == 0 ? ((rayDirX > 0 ? hitCellX : hitCellX + 1) - playerX) : ((rayDirY > 0 ? hitCellY : hitCellY + 1) - playerY);
            int drawStart, drawEnd;
            if (hyperbolic) {
                int wallHeightPx = (int) (focal / projected);
                drawStart = Math.max(hitType == 2 ? (int) horizon : (int) (horizon - wallHeightPx / 2.0), 0);
                drawEnd = Math.min((int) (horizon + wallHeightPx / 2.0), screenHeight - 1);
            }
            else {
                double top, bottom;
                if (exactFace) {
                    top = solveWallEdge(faceC, faceNf, faceNr, lateral, wallTopZ, focal, halfHeight, pitchSin, pitchCos, perp);
                    bottom = solveWallEdge(faceC, faceNf, faceNr, lateral, (-cameraHeight), focal, halfHeight, pitchSin, pitchCos, perp);
                }
                else {
                    top = projectY(perp, wallTopZ, focal, halfHeight, pitchSin, pitchCos);
                    bottom = projectY(perp, (-cameraHeight), focal, halfHeight, pitchSin, pitchCos);
                }
                drawStart = (int) Math.max(0, Math.ceil(Math.max(top, -1) - 0.5));
                drawEnd = (int) Math.min(screenHeight - 1, Math.floor(Math.min(bottom, screenHeight + 1) - 0.5));
            }

            if (segmentCount > 0) {
                shadeExactColumn(x, dirX, dirY, planeLength, pitchSin, pitchCos, halfHeight, focal);
                wallTop[x] = 0; wallBottom[x] = screenHeight - 1;
                return;
            }
            if (drawStart > drawEnd) return;
            wallTop[x] = drawStart; wallBottom[x] = drawEnd;

            double wallX = side == 0 ? oy + localPerp * rayDirY : ox + localPerp * rayDirX;
            wallX -= Math.floor(wallX);
            if (side == 0 && rayDirX < 0) wallX = 1.0 - wallX;
            if (side == 1 && rayDirY > 0) wallX = 1.0 - wallX;

            double brightness = Math.clamp(1.0 - projected / lightDistance, 0.03, 1.0);
            if (side == 1) brightness *= 0.85;
            int texWidth = hitType == 2 ? finishWidth : wallWidth;
            int texX = Math.min((int) (wallX * texWidth), texWidth - 1);
            if (texX < 0) texX = 0;
            int offset = drawStart * screenWidth + x;
            int wallHeightPx = hyperbolic ? (int) (focal / projected) : 0;
            double wallHeightOld = Math.max(wallHeightPx, 1.0);

            for (int y = drawStart; y <= drawEnd; y++) {
                double fraction;
                double rowWallX = wallX;
                if (hyperbolic) fraction = hitType == 2 ? (y - horizon) / Math.max(wallHeightPx / 2.0, 1.0) : (y - (horizon - wallHeightPx / 2.0)) / wallHeightOld;
                else if (exactFace) {
                    double lateralRow = lateral * rowInvA[y];
                    double faceDen = faceNf + lateralRow * faceNr;
                    double depth = Math.abs(faceDen) > 1e-9 ? faceC / faceDen : perp;
                    double along = side == 0 ? playerY + depth * (dirY + lateralRow * dirX) : playerX + depth * (dirX - lateralRow * dirY);
                    double cellLo = side == 0 ? hitCellY : hitCellX;
                    if (along >= cellLo && along <= cellLo + 1.0) {
                        fraction = (wallTopZ - (depth * rowZA[y])) / (wallTopZ + cameraHeight);
                        rowWallX = along - cellLo;
                        if (side == 0 && rayDirX < 0 || side == 1 && rayDirY > 0) rowWallX = 1.0 - rowWallX;
                    }
                    else {
                        double[] rowHit = castWallForRow(playerX, playerY, dirX - lateralRow * dirY, dirY + lateralRow * dirX, baseMap, mapWidth, mapHeight);
                        if (rowHit != null) {
                            double rowTopZ = wallHeightForType((int) rowHit[3]) - cameraHeight;
                            fraction = (rowTopZ - (rowHit[0] * rowZA[y])) / (rowTopZ + cameraHeight);
                            rowWallX = rowHit[2];
                        } else {
                            fraction = (wallTopZ - (depth * rowZA[y])) / (wallTopZ + cameraHeight);
                            rowWallX = wallX;
                        }
                    }
                }
                else {
                    double v = (halfHeight - (y + 0.5)) / focal;
                    double den = pitchCos - v * pitchSin;
                    if (Math.abs(den) < 1e-6) den = 1e-6;
                    fraction = (wallTopZ - (perp * (pitchSin + v * pitchCos) / den)) / (wallTopZ + cameraHeight);
                }
                if (exactFace) {
                    texX = Math.min((int) (rowWallX * texWidth), texWidth - 1);
                    if (texX < 0) texX = 0;
                }
                int texY = (int) (Math.clamp(fraction, 0.0, 0.999999) * (hitType == 2 ? finishHeight : wallHeight));
                int color;
                if (hitType == 2 && finishWidth > 0 && finishHeight > 0) color = finishPixels[texY * finishWidth + texX];
                else if (hitType != 2 && wallWidth > 0 && wallHeight > 0) color = wallPixels[texY * wallWidth + texX];
                else color = hitType == 2 ? finishBaseColor : wallBaseColor;
                renderPixels[offset] = applyBrightness(color, brightness);
                offset += screenWidth;
            }
        });

        renderHorizontalSurfaces(dirX, dirY, planeX, planeY, horizon, focal, halfHeight, pitchSin, pitchCos, hyperbolic);
        copyRenderBuffer();
    }

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
        int side = 0;
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
    private int surfaceColor(RuntimeTexture texture, double worldX, double worldY, int baseColor, int quality) {
        if (quality == 0) return baseColor;
        if (quality == 2) return sampleTextureBilinear(texture, worldX, worldY, baseColor);
        return sampleTexture(texture, worldX, worldY, baseColor);
    }
    private void shadeExactColumn(int x, double dirX, double dirY, double planeLength, double pitchSin, double pitchCos, double halfHeight, double focal) {
        final int width = renderWidth, height = renderHeight;
        final double lateral = cameraXCache[x] * planeLength;
        final double lightDistance = effectiveFlashlightDistance(false);
        final TraceHit hit = new TraceHit();
        for (int y = 0; y < height; y++) {
            double v = (halfHeight - (y + 0.5)) / focal;
            double rx = dirX * pitchCos - dirY * lateral - dirX * pitchSin * v;
            double ry = dirY * pitchCos + dirX * lateral - dirY * pitchSin * v;
            double rz = pitchSin + pitchCos * v;
            double inv = 1.0 / Math.sqrt(rx * rx + ry * ry + rz * rz);
            traceRay(playerX, playerY, cameraHeight, rx * inv, ry * inv, rz * inv, map, portals, hit, MAX_PRIMARY_STEPS);
            renderPixels[y * width + x] = shadeTraceHit(hit, lightDistance);
        }
    }
    private void shadeExactColumnHyp(int x, double dirX, double dirY, double focal, double horizon) {
        final int width = renderWidth, height = renderHeight;
        final double lightDistance = effectiveFlashlightDistance(true);
        for (int y = 0; y < height; y++) {
            double[] pr = hypPixelRay(cameraXCache[x], y + 0.5, dirX, dirY, focal, horizon);
            TraceHit hit = traceHyp(playerX, playerY, cameraHeight, pr[0], pr[1], pr[2], pr[3], new TraceHit(), MAX_PRIMARY_STEPS);
            renderPixels[y * width + x] = shadeTraceHit(hit, lightDistance);
        }
    }
    private void renderPitchedPerPixel(double dirX, double dirY, double planeLength, double[] rowInvA, double[] rowZA, double lightDistance) {
        final int width = renderWidth, height = renderHeight;
        final int[][] m = map;
        final int mw = map[0].length, mh = map.length;
        final double h = cameraHeight;
        final double baseX = playerX, baseY = playerY;
        final int wallWidth = wallTexture.width(), wallHeight = wallTexture.height();
        final int finishWidth = finishTexture.width(), finishHeight = finishTexture.height();
        final int[] wallPixels = wallTexture.pixels(), finishPixels = finishTexture.pixels();

        parallelRange(0, height, y -> {
            final double rowZ = rowZA[y];
            final double rowScale = rowInvA[y];
            final boolean floorPlane = rowZ < 0;
            final double tPlane;
            if (rowZ < -1e-12) tPlane = -h / rowZ;
            else if (rowZ > 1e-12) tPlane = (1.0 - h) / rowZ;
            else tPlane = Double.POSITIVE_INFINITY;

            for (int x = 0; x < width; x++) {
                double lateralRow = cameraXCache[x] * planeLength * rowScale;
                double rx = dirX - lateralRow * dirY, ry = dirY + lateralRow * dirX;
                int offset = y * width + x;

                int mapX = (int) Math.floor(baseX), mapY = (int) Math.floor(baseY);
                double deltaX = rx == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / rx);
                double deltaY = ry == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / ry);
                double sideX = (rx < 0 ? baseX - mapX : mapX + 1.0 - baseX) * deltaX;
                double sideY = (ry < 0 ? baseY - mapY : mapY + 1.0 - baseY) * deltaY;
                int stepX = rx < 0 ? -1 : 1, stepY = ry < 0 ? -1 : 1;

                boolean wallFound = false, exactPortal = false;
                int wType = 0, wSide = 0;
                double wT = 0;
                while (true) {
                    double tNext;
                    if (sideX < sideY) { tNext = sideX; sideX += deltaX; mapX += stepX; wSide = 0; }
                    else { tNext = sideY; sideY += deltaY; mapY += stepY; wSide = 1; }
                    if (tNext >= tPlane) break;
                    if (mapX < 0 || mapY < 0 || mapX >= mw || mapY >= mh) break;
                    int type = m[mapY][mapX];
                    if (type == MazeGenerator.PORTAL) {
                        PortalData.Portal portal = portals.get(mapX, mapY);
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
                    renderPixels[offset] = shadeTraceHit(traceRay(baseX, baseY, h, rx / n, ry / n, rowZ / n, m, portals, new TraceHit(), MAX_PRIMARY_STEPS), lightDistance);
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
                    double brightness = Math.clamp(1.0 - wT / lightDistance, 0.03, 1.0);
                    if (wSide == 1) brightness *= 0.85;
                    renderPixels[offset] = applyBrightness(color, brightness);
                }
                else if (Double.isFinite(tPlane)) {
                    double wx = baseX + rx * tPlane, wy = baseY + ry * tPlane;
                    int color = floorPlane ? surfaceColor(floorTexture, wx, wy, floorBaseColor, RenderEngineConfig.renderEngineQuality) : surfaceColor(ceilingTexture, wx, wy, ceilingBaseColor, RenderEngineConfig.renderEngineQuality);
                    if (floorPlane && is3D()) color = tintStairs(color, wx, wy, m);
                    double brightness = Math.clamp(1.0 - tPlane / lightDistance, 0.05, 1.0);
                    renderPixels[offset] = applyBrightness(color, brightness);
                }
                else {
                    renderPixels[offset] = ceilingBaseColor;
                }
            }
        });
    }
    private void renderHorizontalSurfaces(double dirX, double dirY, double planeX, double planeY, double horizon, double focal,
                                         double halfHeight, double sinP, double cosP, boolean hyperbolic) {
        final double lightDistance = effectiveFlashlightDistance(hyperbolic);
        final int width = renderWidth;
        final int[] wallTop = wallTopCache, wallBottom = wallBottomCache;
        final double[] floorEdge = portalFloorEdgeCache, ceilEdge = portalCeilEdgeCache;
        final boolean tinted = is3D();
        final int quality = RenderEngineConfig.renderEngineQuality;
        final double[] rowInvA = rowInvACache;

        parallelRange(0, renderHeight, y -> {
            double depth = rowDepth(y, horizon, focal, halfHeight, sinP, cosP, (-cameraHeight), hyperbolic);
            boolean floor = depth > 0;
            if (!floor) {
                depth = rowDepth(y, horizon, focal, halfHeight, sinP, cosP, (1.0 - cameraHeight), hyperbolic);
                if (!(depth > 0)) return;
            }
            double rowScale = rowInvA[y];
            double vecX = planeX * rowScale, vecY = planeY * rowScale;
            double leftRayX = dirX - vecX, leftRayY = dirY - vecY;
            double rayStepX = (2.0 * vecX) / width, rayStepY = (2.0 * vecY) / width;
            double perceived = depth;
            double flatDepth = hyperbolic ? HyperbolicMath.inverseHyperbolicDistance(perceived, RenderEngineConfig.HYPERBOLIC_CURVATURE) : depth;
            double brightness = Math.clamp(1.0 - perceived / lightDistance, 0.05, 1.0);
            double worldX = playerX + flatDepth * leftRayX, worldY = playerY + flatDepth * leftRayY;
            double stepX = flatDepth * rayStepX, stepY = flatDepth * rayStepY;
            int offset = y * width;
            for (int x = 0; x < width; x++) {
                boolean covered = (y >= wallTop[x] && y <= wallBottom[x]) || (floor ? y <= floorEdge[x] : y >= ceilEdge[x]);
                if (!covered) {
                    int color = floor ? surfaceColor(floorTexture, worldX, worldY, floorBaseColor, quality) : surfaceColor(ceilingTexture, worldX, worldY, ceilingBaseColor, quality);
                    if (floor && tinted) color = tintStairs(color, worldX, worldY, map);
                    renderPixels[offset + x] = applyBrightness(color, brightness);
                }
                worldX += stepX; worldY += stepY;
            }
        });
    }
}