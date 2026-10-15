package GameViewComponents;

import Helpers.*;

public final class RayTracingEngine extends RenderEngineBase {
    private static final int[] REFLECTION_STEPS = {6, 16, 48};
    private static final int[] REFLECTION_DEPTH = {1, 1, 2};
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
    private static volatile double glossiness = .75;

    public void renderRayTracing() {
        final int width = renderWidth, height = renderHeight;
        final int quality = Math.clamp(RenderEngineConfig.renderEngineQuality, 0, 2);
        final int reflectionSteps = REFLECTION_STEPS[quality];
        final int reflectionDepth = REFLECTION_DEPTH[quality];
        final int[] rtPixels = renderPixels;
        final double tanHalfFov = Math.tan(Math.toRadians(60.0) * 0.5);
        final double verticalTan = tanHalfFov / ((double) width / height);
        final double dirX = Math.cos(cameraAngle), dirY = Math.sin(cameraAngle);
        final double cp = Math.cos(cameraPitch), sp = Math.sin(cameraPitch);
        final boolean hyperbolic = RenderEngineConfig.GEOMETRY_MODE == MazeGenerator.GeometryMode.WRONG;
        final double horizonH = height / 2.0 + cameraPitch * height / 2.0;
        prepareSdfFields();

        parallelRange(0, height, y -> {
            TraceHit primary = new TraceHit();
            TraceHit[] reflectionScratch = {new TraceHit(), new TraceHit()};
            for (int x = 0; x < width; x++) {
                if (hyperbolic) {
                    double[] pr = hypPixelRay((x + 0.5) / width * 2.0 - 1.0, y + 0.5, dirX, dirY, height, horizonH);
                    TraceHit hitH = traceHyp(playerX, playerY, cameraHeight, pr[0], pr[1], pr[2], pr[3], primary, MAX_PRIMARY_STEPS);
                    rtPixels[y * width + x] = hitH.hit
                            ? shadeSurface(hitH, -hitH.dirX, -hitH.dirY, -hitH.dirZ + 0.18, 0, reflectionDepth, reflectionSteps, reflectionScratch)
                            : ceilingBaseColor;
                    continue;
                }
                double sx = ((x + 0.5) / width * 2.0 - 1.0) * tanHalfFov;
                double sy = (1.0 - (y + 0.5) / height * 2.0) * verticalTan;
                double rx = dirX * cp + (-dirY) * sx + (-dirX * sp) * sy;
                double ry = dirY * cp + dirX * sx + (-dirY * sp) * sy;
                double rz = sp + cp * sy;
                double inv = 1.0 / Math.sqrt(rx * rx + ry * ry + rz * rz);
                rx *= inv; ry *= inv; rz *= inv;
                TraceHit hit = traceRay(playerX, playerY, cameraHeight, rx, ry, rz, map, portals, primary, MAX_PRIMARY_STEPS);
                if (!hit.hit) { rtPixels[y * width + x] = ceilingBaseColor; continue; }
                rtPixels[y * width + x] = shadeSurface(hit, -hit.dirX, -hit.dirY, -hit.dirZ + 0.18, 0, reflectionDepth, reflectionSteps, reflectionScratch);
            }
        });
        copyRenderBuffer();
    }

    private static double[] buildToneLut() {
        double[] lut = new double[TONE_STEPS + 1];
        for (int i = 0; i <= TONE_STEPS; i++) lut[i] = toneCurve(i * TONE_MAX / TONE_STEPS);
        return lut;
    }
    private static double planeOcclusion(SDFField field, double x, double y) {
        double d = field.wallAt(x, y);
        if (field.hasFinish) d = Math.min(d, field.finishAt(x, y));
        final double nearReach = 0.30, nearStrength = 0.6;
        final double farReach = 0.90, farStrength = 0.2;
        double ao = 1.0 - nearStrength * (1.0 - smooth(d / nearReach));
        ao *= 1.0 - farStrength * (1.0 - smooth(d / farReach));
        return ao;
    }
    private static int toneToByte(double light) {
        if (!(light > 0)) return 0;
        if (light >= TONE_MAX) return 255;
        return (int) (TONE_LUT[(int) (light * TONE_STEPS / TONE_MAX)] * 255.0 + 0.5);
    }
    private int shadeSurface(TraceHit hit, double lx, double ly, double lz, double pathBefore, int depth, int steps, TraceHit[] scratch) {
        if (!hit.hit) return ceilingBaseColor;
        double llen = Math.sqrt(lx * lx + ly * ly + lz * lz);
        if (llen > 1e-9) { lx /= llen; ly /= llen; lz /= llen; }

        int base;
        double nx, ny, nz, ao, specStrength, reflectivity;
        int shininess;
        boolean emissive = false;
        if (hit.isPlane) {
            boolean floor = hit.type == -1;
            base = sampleTexture(floor ? floorTexture : ceilingTexture, hit.x, hit.y, floor ? floorBaseColor : ceilingBaseColor);
            if (floor && is3D()) base = tintStairs(base, hit.x, hit.y, hit.map);
            nx = 0; ny = 0; nz = floor ? 1 : -1;
            ao = planeOcclusion(sdfFieldFor(hit.map), hit.x, hit.y);
            shininess = floor ? 30 : 16;
            specStrength = floor ? 0.16 : 0.10;
            reflectivity = floor ? 0.12 : 0.06;
        }
        else {
            boolean finish = hit.type == 2;
            base = sampleWallTexture(finish ? finishTexture : wallTexture, hit);
            nx = hit.side == 0 ? (hit.dirX > 0 ? -1 : 1) : 0;
            ny = hit.side == 1 ? (hit.dirY > 0 ? -1 : 1) : 0;
            nz = 0;
            emissive = finish;
            ao = finish ? 1.0 : wallOcclusion(hit.z, wallHeightForType(hit.type));
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
            if (hlen > 1e-9) {
                glint = specStrength * att * glossiness * glintPow(Math.max(0, (nx * hx + ny * hy + nz * hz) / hlen), shininess);
            }
        }

        double fillMix = 0.6 + 0.4 * ao;
        double lightR = AMBIENT_LIGHT[0] * ao + FILL_LIGHT[0] * ndf * ao + FLASH_LIGHT[0] * flash * fillMix + FLASH_LIGHT[0] * glint;
        double lightG = AMBIENT_LIGHT[1] * ao + FILL_LIGHT[1] * ndf * ao + FLASH_LIGHT[1] * flash * fillMix + FLASH_LIGHT[1] * glint;
        double lightB = AMBIENT_LIGHT[2] * ao + FILL_LIGHT[2] * ndf * ao + FLASH_LIGHT[2] * flash * fillMix + FLASH_LIGHT[2] * glint;
        if (emissive) { lightR = Math.max(lightR, 0.7); lightG = Math.max(lightG, 0.7); lightB = Math.max(lightB, 0.7); }

        int br = (base >> 16) & 255, bg = (base >> 8) & 255, bb = base & 255;
        int lit = (toneToByte(br / 255.0 * lightR) << 16) | (toneToByte(bg / 255.0 * lightG) << 8) | toneToByte(bb / 255.0 * lightB);

        double level = (lightR + lightG + lightB) / 3.0;
        reflectivity *= glossiness * Math.clamp(level, 0.15, 1.0);
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
        }
        else traceRay(hit.x + nx * 0.004, hit.y + ny * 0.004, hit.z + nz * 0.004, rrx, rry, rrz, hit.map, hit.portals, reflected, steps);
        if (!reflected.hit) return lit;
        int reflectedColor = shadeSurface(reflected, -rrx, -rry, -rrz + 0.18, dist, depth - 1, steps, scratch);
        return mixColor(lit, reflectedColor, reflectivity);
    }
}