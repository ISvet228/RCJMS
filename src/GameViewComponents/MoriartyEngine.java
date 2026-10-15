package GameViewComponents;

import Helpers.*;

public final class MoriartyEngine extends RenderEngineBase {
    //region Tuning (index = quality tier 0 low, 1 medium, 2 high)
    private static final int[] AO_SAMPLES = {2, 3, 4};
    private static final int[] REFLECTION_DEPTH = {0, 1, 2};
    private static final int[] REFLECTION_STEPS = {12, 32, 64};
    private static final int[] EDGE_GRID = {1, 2, 3};
    private static final double[] AO_HEIGHTS = {0.08, 0.20, 0.36, 0.60};
    private static final double AO_STRENGTH = 1.1, AO_FLOOR = 0.25;
    private static final double EDGE_RATIO = 0.25;
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
    private static final int KIND_NONE = 0, KIND_FLOOR = 1, KIND_CEIL = 2, KIND_WALL = 3, KIND_FINISH = 4;
    //endregion

    //region Per-frame state
    private int quality, aoSamples, reflectionDepth, reflectionSteps, edgeGrid;
    private int[][] baseMap;
    private PortalData basePortals;
    private SDFField baseField;
    private double tanHalfFov, verticalTan, dirX, dirY, cosP, sinP, horizonH;
    private boolean hyperbolic, columnPath;
    private ColumnRay[] columns = new ColumnRay[0];
    private double[] columnUx = new double[0], columnUy = new double[0], columnHl = new double[0];
    private boolean[] columnExact = new boolean[0];
    private double[] rowV = new double[0];
    private int[] gKind = new int[0];
    private double[] gDist = new double[0];
    private boolean[] gEdge = new boolean[0];
    //endregion

    public void renderMoriarty() {
        final int width = renderWidth, height = renderHeight;
        quality = Math.clamp(RenderEngineConfig.renderEngineQuality, 0, 2);
        aoSamples = AO_SAMPLES[quality];
        reflectionDepth = REFLECTION_DEPTH[quality];
        reflectionSteps = REFLECTION_STEPS[quality];
        edgeGrid = EDGE_GRID[quality];
        hyperbolic = RenderEngineConfig.GEOMETRY_MODE == MazeGenerator.GeometryMode.WRONG;
        columnPath = !hyperbolic && cameraPitch == 0;

        tanHalfFov = Math.tan(Math.toRadians(60.0) * 0.5);
        verticalTan = tanHalfFov / ((double) width / height);
        dirX = Math.cos(cameraAngle);
        dirY = Math.sin(cameraAngle);
        cosP = Math.cos(cameraPitch);
        sinP = Math.sin(cameraPitch);
        horizonH = height / 2.0 + cameraPitch * height / 2.0;
        baseMap = map;
        basePortals = portals;

        prepareSdfFields();
        baseField = sdfFieldFor(baseMap);
        ensureBuffers(width, height);

        if (columnPath) {
            parallelRange(0, width, x -> buildColumn(x, width));
            rowV = new double[height];
            for (int y = 0; y < height; y++) rowV[y] = (1.0 - (y + 0.5) / height * 2.0) * verticalTan;
        }
        parallelRange(0, height, y -> {
            TraceHit hit = new TraceHit();
            for (int x = 0; x < width; x++) {
                primaryPixel(x, y, hit);
                int i = y * width + x;
                gKind[i] = kindOf(hit);
                gDist[i] = hit.hit ? hit.distance : INF;
            }
        });
        if (edgeGrid > 1) parallelRange(0, height, y -> markEdges(y, width, height));

        parallelRange(0, height, y -> {
            TraceHit hit = new TraceHit(), sub = new TraceHit();
            TraceHit[] reflectionScratch = {new TraceHit(), new TraceHit()};
            for (int x = 0; x < width; x++) {
                int i = y * width + x;
                primaryPixel(x, y, hit);
                int color = shadeHit(hit, 0.0, reflectionDepth, reflectionSteps, reflectionScratch);
                if (edgeGrid > 1 && gEdge[i]) {
                    int r = 0, g = 0, b = 0, n = edgeGrid * edgeGrid;
                    for (int j = 0; j < edgeGrid; j++) for (int k = 0; k < edgeGrid; k++) {
                        cameraTrace(x + (k + 0.5) / edgeGrid, y + (j + 0.5) / edgeGrid, sub);
                        int c = shadeHit(sub, 0.0, reflectionDepth, reflectionSteps, reflectionScratch);
                        r += (c >> 16) & 255; g += (c >> 8) & 255; b += c & 255;
                    }
                    color = ((r / n) << 16) | ((g / n) << 8) | (b / n);
                }
                renderPixels[i] = color;
            }
        });
        copyRenderBuffer();
    }

    //region Buffers
    private void ensureBuffers(int width, int height) {
        int count = width * height;
        if (gKind.length != count) { gKind = new int[count]; gDist = new double[count]; gEdge = new boolean[count]; }
        if (columns.length != width) {
            columns = new ColumnRay[width];
            for (int x = 0; x < width; x++) columns[x] = new ColumnRay();
            columnUx = new double[width]; columnUy = new double[width]; columnHl = new double[width];
            columnExact = new boolean[width];
        }
    }
    //endregion

    //region Primary visibility (DDA)
    private void buildColumn(int x, int width) {
        double sx = ((x + 0.5) / width * 2.0 - 1.0) * tanHalfFov;
        double hx = dirX - sx * dirY, hy = dirY + sx * dirX;
        double hl = Math.sqrt(hx * hx + hy * hy);
        columnUx[x] = hx / hl;
        columnUy[x] = hy / hl;
        columnHl[x] = hl;
        columnExact[x] = !walkColumn(columns[x], playerX, playerY, columnUx[x], columnUy[x], baseMap, MAX_COLUMN_DISTANCE);
    }
    private static boolean walkColumn(ColumnRay c, double px, double py, double ux, double uy, int[][] m, double maxDistance) {
        c.segCount = 0; c.finCount = 0; c.wallDist = INF; c.exitD = INF; c.exhausted = false;
        double x = px, y = py, dist = 0;
        int cx = (int) Math.floor(x), cy = (int) Math.floor(y);
        while (dist < maxDistance) {
            double tX = ux > 0 ? (cx + 1 - x) / ux : ux < 0 ? (x - cx) / -ux : INF;
            double tY = uy > 0 ? (cy + 1 - y) / uy : uy < 0 ? (y - cy) / -uy : INF;
            boolean corner = Math.abs(tX - tY) <= 1e-9;
            boolean alongX = tX < tY;
            double t = Math.min(tX, tY);
            if (dist + t > maxDistance) return true;
            x += ux * t; y += uy * t; dist += t;
            if (corner) { cx += ux > 0 ? 1 : -1; cy += uy > 0 ? 1 : -1; alongX = true; }
            else if (alongX) cx += ux > 0 ? 1 : -1;
            else cy += uy > 0 ? 1 : -1;
            if (cx < 0 || cy < 0 || cx >= m[0].length || cy >= m.length) return true;
            int type = m[cy][cx];
            if (type == MazeGenerator.PORTAL) return false;
            if (type == 1 || type == 2) {
                int side = alongX ? 0 : 1;
                double u = side == 0 ? y - Math.floor(y) : x - Math.floor(x);
                if ((side == 0 && ux < 0) || (side == 1 && uy > 0)) u = 1.0 - u;
                u = Math.clamp(u, 0.0, 0.999999);
                if (type == 1) { c.wallDist = dist; c.wallTexU = u; c.wallSide = side; return true; }
                if (c.finCount < ColumnRay.MAX_FIN) {
                    int k = c.finCount++;
                    c.finishDist[k] = dist; c.finishTexU[k] = u; c.finishSide[k] = side;
                }
            }
        }
        return true;
    }
    private void primaryPixel(int x, int y, TraceHit out) {
        if (columnPath && !columnExact[x]) columnHit(x, y, out);
        else cameraTrace(x + 0.5, y + 0.5, out);
    }
    private void columnHit(int x, int y, TraceHit out) {
        ColumnRay c = columns[x];
        out.reset();
        double v = rowV[y];
        double hl = columnHl[x], ux = columnUx[x], uy = columnUy[x];
        double len = Math.sqrt(hl * hl + v * v);
        double hf = hl / len;
        double rzUnit = v / len;
        double sigma = rzUnit / hf;
        double z0 = cameraHeight;
        double planeD = sigma < -1e-12 ? -z0 / sigma : sigma > 1e-12 ? (1.0 - z0) / sigma : INF;
        double wallD = c.wallDist;
        double nearest = Math.min(wallD, planeD);

        for (int k = 0; k < c.finCount; k++) {
            double d = c.finishDist[k];
            if (d >= nearest) break;
            double z = z0 + sigma * d;
            if (z <= 0.5) {
                fill(out, d, hf, ux, uy, rzUnit, z0 + sigma * d, z0, false);
                out.type = 2; out.side = c.finishSide[k]; out.wallU = c.finishTexU[k];
                out.wallV = Math.clamp(z / 0.5, 0.0, 0.999999); out.z = Math.clamp(z, 0.0, 0.5);
                return;
            }
        }
        if (wallD < planeD) {
            double z = z0 + sigma * wallD;
            fill(out, wallD, hf, ux, uy, rzUnit, z, z0, false);
            out.type = 1; out.side = c.wallSide; out.wallU = c.wallTexU;
            out.wallV = Math.clamp(z, 0.0, 0.999999); out.z = Math.clamp(z, 0.0, 1.0);
            return;
        }
        if (planeD < INF) {
            boolean floor = sigma < 0;
            fill(out, planeD, hf, ux, uy, rzUnit, floor ? 0.0 : 1.0, z0, true);
            out.type = floor ? -1 : 1; out.z = floor ? 0.0 : 1.0; out.side = 0;
        }
    }
    private void fill(TraceHit out, double horizontal, double hf, double ux, double uy, double rzUnit, double z, double z0, boolean plane) {
        out.hit = true;
        out.hyp = false;
        out.isPlane = plane;
        out.distance = horizontal / hf;
        out.x = playerX + ux * horizontal;
        out.y = playerY + uy * horizontal;
        out.z = z;
        out.dirX = ux * hf; out.dirY = uy * hf; out.dirZ = rzUnit;
        out.map = baseMap; out.portals = basePortals;
    }
    private void cameraTrace(double fx, double fy, TraceHit out) {
        int width = renderWidth, height = renderHeight;
        if (hyperbolic) {
            double[] pr = hypPixelRay(fx / width * 2.0 - 1.0, fy, dirX, dirY, height, horizonH);
            traceHyp(playerX, playerY, cameraHeight, pr[0], pr[1], pr[2], pr[3], out, MAX_PRIMARY_STEPS);
            return;
        }
        double sx = (fx / width * 2.0 - 1.0) * tanHalfFov;
        double sy = (1.0 - fy / height * 2.0) * verticalTan;
        double rx = dirX * cosP - dirY * sx - dirX * sinP * sy;
        double ry = dirY * cosP + dirX * sx - dirY * sinP * sy;
        double rz = sinP + cosP * sy;
        double inv = 1.0 / Math.sqrt(rx * rx + ry * ry + rz * rz);
        traceRay(playerX, playerY, cameraHeight, rx * inv, ry * inv, rz * inv, baseMap, basePortals, out, MAX_PRIMARY_STEPS);
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
    //endregion

    //region Shading (raymarch occlusion + ray-traced reflections)
    private int shadeHit(TraceHit hit, double pathBefore, int depth, int steps, TraceHit[] scratch) {
        if (!hit.hit) return ceilingBaseColor;
        double lx = -hit.dirX, ly = -hit.dirY, lz = -hit.dirZ + 0.18;
        double llen = Math.sqrt(lx * lx + ly * ly + lz * lz);
        if (llen > 1e-9) { lx /= llen; ly /= llen; lz /= llen; }

        SDFField field = hit.map == baseMap ? baseField : sdfFieldFor(hit.map);
        int base;
        double nx, ny, nz, ao, specStrength, reflectivity;
        int shininess;
        boolean emissive = false;
        if (hit.isPlane) {
            boolean floor = hit.type == -1;
            base = planeColor(floor ? floorTexture : ceilingTexture, hit.x, hit.y, floor ? floorBaseColor : ceilingBaseColor);
            if (floor && is3D()) base = tintStairs(base, hit.x, hit.y, hit.map);
            nx = 0; ny = 0; nz = floor ? 1 : -1;
            ao = occlusionMarch(field, hit.x, hit.y, hit.z, nx, ny, nz);
            shininess = floor ? 30 : 16;
            specStrength = floor ? 0.16 : 0.10;
            reflectivity = floor ? 0.12 : 0.06;
        } else {
            boolean finish = hit.type == 2;
            base = sampleWallTexture(finish ? finishTexture : wallTexture, hit);
            nx = hit.side == 0 ? (hit.dirX > 0 ? -1 : 1) : 0;
            ny = hit.side == 1 ? (hit.dirY > 0 ? -1 : 1) : 0;
            nz = 0;
            emissive = finish;
            ao = finish ? 1.0 : wallOcclusion(hit.z, wallHeightForType(hit.type)) * occlusionMarch(field, hit.x, hit.y, hit.z, nx, ny, nz);
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
            traceHyp(hit.x + nx * 0.004, hit.y + ny * 0.004, hit.z + nz * 0.004, rrx / hl, rry / hl, 1.0, reflectedSlope, reflected, steps);
        } else {
            traceRay(hit.x + nx * 0.004, hit.y + ny * 0.004, hit.z + nz * 0.004, rrx, rry, rrz, hit.map, hit.portals, reflected, steps);
        }
        if (!reflected.hit) return lit;
        int reflectedColor = shadeHit(reflected, dist, depth - 1, steps, scratch);
        return mixColor(lit, reflectedColor, reflectivity);
    }
    private double occlusionMarch(SDFField field, double px, double py, double pz, double nx, double ny, double nz) {
        double occ = 0, weight = 1.0;
        for (int i = 0; i < aoSamples; i++) {
            double h = AO_HEIGHTS[i];
            double d = solidDistance(field, px + nx * h, py + ny * h, pz + nz * h);
            occ += Math.max(0.0, h - d) * weight;
            weight *= 0.6;
        }
        return Math.clamp(1.0 - AO_STRENGTH * occ, AO_FLOOR, 1.0);
    }
    private static double solidDistance(SDFField f, double x, double y, double z) {
        double dw = f.wallAt(x, y), dz = Math.max(0.0, z - 1.0);
        double d = Math.sqrt(dw * dw + dz * dz);
        if (f.hasFinish) {
            double df = f.finishAt(x, y), ddz = Math.max(0.0, z - 0.5);
            d = Math.min(d, Math.sqrt(df * df + ddz * ddz));
        }
        return d;
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
}