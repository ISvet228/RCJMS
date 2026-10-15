package GameViewComponents;

import Helpers.*;

public final class SoftwareRayCastingEngine extends RenderEngineBase {
    private static final double RC_MAX_DISTANCE = 48;

    public void renderSoftwareRayCasting() {
        if (RenderEngineConfig.GEOMETRY_MODE == MazeGenerator.GeometryMode.WRONG) { renderSoftwareRayCastingHyp(); return; }
        if (cameraPitch == 0) { renderSoftwareRayCastingColumns(); return; }
        final double dirX = Math.cos(cameraAngle), dirY = Math.sin(cameraAngle);
        final double planeLength = Math.tan(Math.PI / 3.0 / 2.0);
        final int width = renderWidth, height = renderHeight;
        final double focal = width / (2.0 * planeLength);
        final double halfHeight = height / 2.0;
        final double pitchSin = Math.sin(cameraPitch), pitchCos = Math.cos(cameraPitch);
        ensureRaycastCaches(width, height);
        fillRowCaches(focal, halfHeight, pitchSin, pitchCos);
        final double[] rowInvA = rowInvACache, rowZA = rowZACache;
        final double lightDistance = effectiveFlashlightDistance(false);
        final int[][] baseMap = map;
        final PortalData basePortals = portals;
        parallelRange(0, height, y -> {
            RayState s = new RayState();
            double rowZ = rowZA[y], rowScale = rowInvA[y];
            for (int x = 0; x < width; x++) {
                double lateral = cameraXCache[x] * planeLength * rowScale;
                s.reset(playerX, playerY, cameraHeight, dirX - lateral * dirY, dirY + lateral * dirX, rowZ, baseMap, basePortals, null);
                softwareMarchExact(s, RC_MAX_DISTANCE);
                renderPixels[y * width + x] = s.hitKind == 0 ? ceilingBaseColor : shadeMarch(s, lightDistance);
            }
        });
        copyRenderBuffer();
    }

    private void renderSoftwareRayCastingColumns() {
        final int width = renderWidth, height = renderHeight;
        final double dirX = Math.cos(cameraAngle), dirY = Math.sin(cameraAngle);
        final double planeLength = Math.tan(Math.PI / 3.0 / 2.0);
        final double focal = width / (2.0 * planeLength), halfHeight = height / 2.0;
        ensureRaycastCaches(width, height);
        fillRowCaches(focal, halfHeight, 0.0, 1.0);
        final int[][] baseMap = map;
        final PortalData basePortals = portals;
        final double lightDistance = effectiveFlashlightDistance(false);
        final double z0 = cameraHeight;
        final double[] rowZ = rowZACache;
        parallelRange(0, width, x -> {
            final double lateral = cameraXCache[x] * planeLength;
            final double hx = dirX - lateral * dirY, hy = dirY + lateral * dirX;
            final double hl = Math.sqrt(hx * hx + hy * hy);
            ColumnRay c = new ColumnRay();
            softwareMarchColumn(c, playerX, playerY, hx / hl, hy / hl, baseMap, basePortals, RC_MAX_DISTANCE);
            RayState rs = new RayState();
            for (int y = 0; y < height; y++) {
                renderPixels[y * width + x] = shadeColumnRow(c, rs, z0, rowZ[y] / hl, lightDistance);
            }
        });
        copyRenderBuffer();
    }
    private void renderSoftwareRayCastingHyp() {
        final int width = renderWidth, height = renderHeight;
        final double dirX = Math.cos(cameraAngle), dirY = Math.sin(cameraAngle);
        final double focal = height, horizon = height / 2.0 + cameraPitch * height / 2.0;
        final double step = softwareRayCastStep();
        final int[][] baseMap = map;
        final double lightDistance = effectiveFlashlightDistance(true);
        parallelRange(0, height, y -> {
            RayState s = new RayState();
            for (int x = 0; x < width; x++) {
                s.resetHyp(playerX, playerY, cameraHeight, hypPixelRay(cameraXCache[x], y + 0.5, dirX, dirY, focal, horizon), baseMap, null);
                softwareMarchNaive(s, step, RC_MAX_DISTANCE);
                renderPixels[y * width + x] = s.hitKind == 0 ? ceilingBaseColor : shadeMarch(s, lightDistance);
            }
        });
        copyRenderBuffer();
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
            if (type == MazeGenerator.PORTAL && enterPortal(s, cx, cy)) {
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
    private static double softwareRayCastStep() { return RenderEngineConfig.renderEngineQuality == 0 ? 0.15 : RenderEngineConfig.renderEngineQuality == 1 ? 0.08 : 0.04; }
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
            if (type == MazeGenerator.PORTAL && enterPortal(s, cx, cy)) {
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