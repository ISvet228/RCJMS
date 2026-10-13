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
        final double step = softwareRayCastStep();
        final int maxSteps = (int) Math.ceil(RC_MAX_DISTANCE / step) + 1;
        final double lightDistance = effectiveFlashlightDistance(false);
        final int[][] baseMap = map;
        final PortalData basePortals = portals;
        parallelRange(0, height, y -> {
            RayState s = new RayState();
            double rowZ = rowZA[y], rowScale = rowInvA[y];
            for (int x = 0; x < width; x++) {
                double lateral = cameraXCache[x] * planeLength * rowScale;
                s.reset(playerX, playerY, cameraHeight, dirX - lateral * dirY, dirY + lateral * dirX, rowZ, baseMap, basePortals, null);
                softwareMarch(s, step, RC_MAX_DISTANCE, maxSteps);
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
        parallelRange(0, height, y -> {
            RayState s = new RayState();
            for (int x = 0; x < width; x++) {
                s.resetHyp(playerX, playerY, cameraHeight, hypPixelRay(cameraXCache[x], y + 0.5, dirX, dirY, focal, horizon), baseMap, null);
                softwareMarchNaive(s, step, RC_MAX_DISTANCE);
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
            boolean alongX = tX <= tY;
            double t = alongX ? tX : tY;
            if (dist + t > maxDistance) { s.x += ux * (maxDistance - dist); s.y += uy * (maxDistance - dist); return; }
            s.x += ux * t; s.y += uy * t; dist += t;
            if (alongX) cx += ux > 0 ? 1 : -1; else cy += uy > 0 ? 1 : -1;
            if (cx < 0 || cy < 0 || cx >= m[0].length || cy >= m.length) { c.exitD = dist; return; }
            int type = m[cy][cx];
            if (type == MazeGenerator.PORTAL && enterPortal(s, cx, cy)) {
                addColumnSegment(c, dist, s);
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
        for (int i = 0; i < maxSteps && s.dist < maxDistance; i++) {
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
            s.z = hypHeight(s);
            int type = m[cy][cx];
            if ((type == 1 || type == 2) && s.z >= 0.0 && s.z <= wallHeightForType(type)) {
                s.hitKind = HIT_WALL;
                s.hitType = type;
                s.hitDist = hypPerceivedOf(s);
                resolveWallFace(s);
                setWallFraction(s);
                return;
            }
        }
    }
    private void softwareMarch(RayState s, double step, double maxDistance, int maxSteps) {
        s.hitKind = 0;
        int cx = (int) Math.floor(s.x), cy = (int) Math.floor(s.y);
        int side = 0;
        for (int i = 0; i < maxSteps && s.dist < maxDistance; i++) {
            double px = s.x, py = s.y, pz = s.z;
            double sx = s.dx * step, sy = s.dy * step, sz = s.dz * step;
            double nxp = px + sx, nyp = py + sy, nzp = pz + sz;
            int nx = (int) Math.floor(nxp), ny = (int) Math.floor(nyp);
            if (nx < 0 || ny < 0 || nx >= s.map[0].length || ny >= s.map.length) return;

            double tPlane = Double.POSITIVE_INFINITY;
            int planeKind = 0;
            if (nzp <= 0 && sz < 0) { tPlane = Math.clamp(-pz / sz, 0.0, 1.0); planeKind = HIT_FLOOR; }
            else if (nzp >= 1.0 && sz > 0) { tPlane = Math.clamp((1.0 - pz) / sz, 0.0, 1.0); planeKind = HIT_CEIL; }

            boolean crossed = nx != cx || ny != cy;
            int wallSide = side;
            double tWall = 1.0;
            if (crossed) {
                wallSide = nx != cx ? 0 : 1;
                double boundary = wallSide == 0 ? (s.dx > 0 ? nx : nx + 1) : (s.dy > 0 ? ny : ny + 1);
                double along = wallSide == 0 ? sx : sy;
                tWall = Math.clamp((boundary - (wallSide == 0 ? px : py)) / along, 0.0, 1.0);
            }

            if (planeKind != 0 && tPlane <= tWall) {
                s.x = px + sx * tPlane;
                s.y = py + sy * tPlane;
                s.z = planeKind == HIT_FLOOR ? 0.0 : 1.0;
                s.dist += step * tPlane;
                setPlaneHit(s, planeKind);
                return;
            }

            s.x = px + sx * tWall;
            s.y = py + sy * tWall;
            s.z = pz + sz * tWall;
            s.dist += step * tWall;
            if (crossed) {
                side = wallSide;
                cx = nx;
                cy = ny;
            }
            int type = s.map[cy][cx];
            if (type == MazeGenerator.PORTAL) {
                if (enterPortal(s, cx, cy)) {
                    cx = (int) Math.floor(s.x);
                    cy = (int) Math.floor(s.y);
                }
            }
            else if ((type == 1 || type == 2) && s.z <= wallHeightForType(type)) {
                s.hitKind = HIT_WALL;
                s.hitType = type;
                s.hitDist = s.dist;
                s.hitSide = side;
                double ux = side == 0 ? s.y - Math.floor(s.y) : s.x - Math.floor(s.x);
                if ((side == 0 && s.dx < 0) || (side == 1 && s.dy > 0)) ux = 1 - ux;
                s.hitU = Math.clamp(ux, 0.0, 0.999999);
                setWallFraction(s);
                return;
            }
        }
    }
}