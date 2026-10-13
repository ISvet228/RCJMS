package GameViewComponents;

import Helpers.*;

public final class RayMarchingEngine  extends RenderEngineBase {
    private  static final double RM_HIT = 0.001, RM_STEP_SCALE = 0.8, RM_MAX_DISTANCE = 48, RM_MIN_STEP = 0.001;

    public void renderRayMarching() {
        if (RenderEngineConfig.GEOMETRY_MODE == MazeGenerator.GeometryMode.WRONG) { renderRayMarchingHyp(); return; }
        if (cameraPitch == 0) { renderRayMarchingColumns(); return; }
        final double dirX = Math.cos(cameraAngle), dirY = Math.sin(cameraAngle);
        final double planeLength = Math.tan(Math.PI / 3.0 / 2.0);
        final int width = renderWidth, height = renderHeight;
        final double focal = width / (2.0 * planeLength);
        final double halfHeight = height / 2.0;
        final double pitchSin = Math.sin(cameraPitch), pitchCos = Math.cos(cameraPitch);
        ensureRaycastCaches(width, height);
        fillRowCaches(focal, halfHeight, pitchSin, pitchCos);
        prepareSdfFields();
        final double[] rowInvA = rowInvACache, rowZA = rowZACache;
        final int maxSteps = rayMarchSteps();
        final double lightDistance = effectiveFlashlightDistance(false);
        final int[][] baseMap = map;
        final PortalData basePortals = portals;
        final SDFField baseField = sdfFieldFor(baseMap);
        parallelRange(0, height, y -> {
            RayState s = new RayState();
            double rowZ = rowZA[y], rowScale = rowInvA[y];
            for (int x = 0; x < width; x++) {
                double lateral = cameraXCache[x] * planeLength * rowScale;
                s.reset(playerX, playerY, cameraHeight, dirX - lateral * dirY, dirY + lateral * dirX, rowZ, baseMap, basePortals, baseField);
                marchSphere(s, RM_MAX_DISTANCE, maxSteps);
                if (s.exhausted) {
                    double rx = dirX - lateral * dirY, ry = dirY + lateral * dirX;
                    double inv = 1.0 / Math.sqrt(rx * rx + ry * ry + rowZ * rowZ);
                    TraceHit fallback = traceRay(playerX, playerY, cameraHeight, rx * inv, ry * inv, rowZ * inv, baseMap, basePortals, new TraceHit(), MAX_PRIMARY_STEPS);
                    renderPixels[y * width + x] = shadeTraceHit(fallback, lightDistance);
                }
                else renderPixels[y * width + x] = s.hitKind == 0 ? ceilingBaseColor : shadeMarch(s, lightDistance);
            }
        });
        copyRenderBuffer();
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
    private static int rayMarchSteps() { return RenderEngineConfig.renderEngineQuality == 0 ? 48 : RenderEngineConfig.renderEngineQuality == 1 ? 96 : 160; }
    private static void storeMarch(RayState s, double x, double y, double z, double dx, double dy, double dz, double dist) {
        s.x = x; s.y = y; s.z = z; s.dx = dx; s.dy = dy; s.dz = dz; s.dist = dist;
    }
    private void renderRayMarchingColumns() {
        final int width = renderWidth, height = renderHeight;
        final double dirX = Math.cos(cameraAngle), dirY = Math.sin(cameraAngle);
        final double planeLength = Math.tan(Math.PI / 3.0 / 2.0);
        final double focal = width / (2.0 * planeLength), halfHeight = height / 2.0;
        ensureRaycastCaches(width, height);
        fillRowCaches(focal, halfHeight, 0.0, 1.0);
        prepareSdfFields();
        final SDFField baseField = sdfFieldFor(map);
        final int[][] baseMap = map;
        final PortalData basePortals = portals;
        final int maxSteps = rayMarchSteps();
        final double lightDistance = effectiveFlashlightDistance(false);
        final double z0 = cameraHeight;
        final double[] rowZ = rowZACache;
        parallelRange(0, width, x -> {
            final double lateral = cameraXCache[x] * planeLength;
            final double hx = dirX - lateral * dirY, hy = dirY + lateral * dirX;
            final double hl = Math.sqrt(hx * hx + hy * hy);
            ColumnRay c = new ColumnRay();
            marchColumn(c, playerX, playerY, hx / hl, hy / hl, baseField, baseMap, basePortals, RM_MAX_DISTANCE, maxSteps);
            RayState rs = new RayState();
            for (int y = 0; y < height; y++) {
                int color;
                if (c.exhausted) {
                    double vz = rowZ[y];
                    double inv = 1.0 / Math.sqrt(hx * hx + hy * hy + vz * vz);
                    color = shadeTraceHit(traceRay(playerX, playerY, z0, hx * inv, hy * inv, vz * inv, baseMap, basePortals, new TraceHit(), MAX_PRIMARY_STEPS), lightDistance);
                }
                else color = shadeColumnRow(c, rs, z0, rowZ[y] / hl, lightDistance);
                renderPixels[y * width + x] = color;
            }
        });
        copyRenderBuffer();
    }
    private void renderRayMarchingHyp() {
        final int width = renderWidth, height = renderHeight;
        final double dirX = Math.cos(cameraAngle), dirY = Math.sin(cameraAngle);
        final double horizon = height / 2.0 + cameraPitch * height / 2.0;
        prepareSdfFields();
        final SDFField baseField = sdfFieldFor(map);
        final int[][] baseMap = map;
        final int maxSteps = rayMarchSteps();
        final double lightDistance = effectiveFlashlightDistance(true);
        parallelRange(0, height, y -> {
            RayState s = new RayState();
            for (int x = 0; x < width; x++) {
                double[] pr = hypPixelRay(cameraXCache[x], y + 0.5, dirX, dirY, height, horizon);
                s.resetHyp(playerX, playerY, cameraHeight, pr, baseMap, baseField);
                marchSphereHyp(s, RM_MAX_DISTANCE, maxSteps);
                if (s.exhausted) {
                    TraceHit fallback = traceHyp(playerX, playerY, cameraHeight, pr[0], pr[1], pr[2], pr[3], new TraceHit(), MAX_PRIMARY_STEPS);
                    renderPixels[y * width + x] = shadeTraceHit(fallback, lightDistance);
                }
                else renderPixels[y * width + x] = s.hitKind == 0 ? ceilingBaseColor : shadeMarch(s, lightDistance);
            }
        });
        copyRenderBuffer();
    }
    private void prepareSdfFields() {
        if (map3D != null)
            for (int[][] floor : map3D)
                if (!sdfFields.containsKey(floor))
                    sdfFields.put(floor, new SDFField(floor));
        else if (!sdfFields.containsKey(map)) {
            if (sdfFields.size() > 32)
                sdfFields.clear();
            sdfFields.put(map, new SDFField(map));
        }
    }
    private void marchColumn(ColumnRay c, double px, double py, double ux, double uy, SDFField field0, int[][] map0, PortalData portals0, double maxDistance, int maxSteps) {
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
        for (; i < maxSteps && dist < maxDistance; i++) {
            int[][] m = s.map;
            SDFField field = s.field;
            int cx = (int) Math.floor(s.x), cy = (int) Math.floor(s.y);
            if (cx < 0 || cy < 0 || cx >= m[0].length || cy >= m.length) { c.exitD = dist; return; }
            if (m[cy][cx] == MazeGenerator.PORTAL && enterPortal(s, cx, cy)) {
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
        if (dist < maxDistance && i >= maxSteps) c.exhausted = true;
    }
    private void marchSphereHyp(RayState s, double maxDistance, int maxSteps) {
        s.hitKind = 0;
        s.exhausted = false;
        final double plane = Math.min(s.floorD, s.ceilD);
        int i = 0;
        for (; i < maxSteps && s.dist < maxDistance; i++) {
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
        if (s.dist < maxDistance && i >= maxSteps) s.exhausted = true;
    }
    private void marchSphere(RayState s, double maxDistance, int maxSteps) {
        s.hitKind = 0;
        s.exhausted = false;
        int[][] map = s.map;
        SDFField field = s.field;
        double x = s.x, y = s.y, z = s.z, dx = s.dx, dy = s.dy, dz = s.dz, dist = s.dist;
        final double planeAbs = dist + (dz < 0 ? z / -dz : dz > 0 ? (1.0 - z) / dz : Double.POSITIVE_INFINITY);
        double prevWall = Double.POSITIVE_INFINITY;
        int i = 0;
        for (; i < maxSteps && dist < maxDistance; i++) {
            int cx = (int) Math.floor(x), cy = (int) Math.floor(y);
            if (cx < 0 || cy < 0 || cx >= map[0].length || cy >= map.length) return;
            if (map[cy][cx] == MazeGenerator.PORTAL) {
                storeMarch(s, x, y, z, dx, dy, dz, dist);
                if (enterPortal(s, cx, cy)) {
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
        if (dist < maxDistance) s.exhausted = true;
    }
}