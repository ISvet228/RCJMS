package GameViewComponents;

import Helpers.*;

import java.awt.*;
import java.awt.image.*;

public final class RayTracingEngine extends RenderEngineBase {
    private static final int REFLECTION_STEPS = 48;
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
    private BufferedImage rtImage;
    private int[] rtPixels;
    private static volatile double glossiness = .75;

    public void renderRayTracing() {
        final int targetWidth = renderWidth, targetHeight = renderHeight;
        final double internalScale = RenderEngineConfig.renderEngineQuality == 0 ? 0.35 : RenderEngineConfig.renderEngineQuality == 1 ? 0.50 : 1;
        final int width = Math.max(240, (int) Math.round(targetWidth * internalScale));
        final int height = Math.max(135, (int) Math.round(targetHeight * internalScale));
        if (rtImage == null || rtImage.getWidth() != width || rtImage.getHeight() != height) {
            rtImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            rtPixels = ((DataBufferInt) rtImage.getRaster().getDataBuffer()).getData();
        }
        final int[] rtPixels = this.rtPixels;
        final double tanHalfFov = Math.tan(Math.toRadians(60.0) * 0.5);
        final double verticalTan = tanHalfFov / ((double) width / height);
        final double dirX = Math.cos(cameraAngle), dirY = Math.sin(cameraAngle);
        final double cp = Math.cos(cameraPitch), sp = Math.sin(cameraPitch);
        final boolean reflections = RenderEngineConfig.renderEngineQuality >= 1;
        final boolean hyperbolic = RenderEngineConfig.GEOMETRY_MODE == MazeGenerator.GeometryMode.WRONG;
        final double horizonH = height / 2.0 + cameraPitch * height / 2.0;

        parallelRange(0, height, y -> {
            TraceHit primary = new TraceHit(), secondary = new TraceHit();
            for (int x = 0; x < width; x++) {
                if (hyperbolic) {
                    double[] pr = hypPixelRay((x + 0.5) / width * 2.0 - 1.0, y + 0.5, dirX, dirY, height, horizonH);
                    TraceHit hitH = traceHyp(playerX, playerY, cameraHeight, pr[0], pr[1], pr[2], pr[3], primary, MAX_PRIMARY_STEPS);
                    rtPixels[y * width + x] = hitH.hit ? shadeSurface(hitH, -hitH.dirX, -hitH.dirY, -hitH.dirZ + 0.18, 0, reflections, secondary) : ceilingBaseColor;
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
                rtPixels[y * width + x] = shadeSurface(hit, -hit.dirX, -hit.dirY, -hit.dirZ + 0.18, 0, reflections, secondary);
            }
        });

        if (width == targetWidth && height == targetHeight) System.arraycopy(rtPixels, 0, renderPixels, 0, renderPixels.length);
        else {
            Graphics2D g2d = renderImage.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.drawImage(rtImage, 0, 0, targetWidth, targetHeight, null);
            g2d.dispose();
        }
        copyRenderBuffer();
    }

    private static boolean solidCell(int[][] m, int x, int y) {
        if (y < 0 || y >= m.length || x < 0 || x >= m[0].length) return true;
        int t = m[y][x];
        return t == 1 || t == 2;
    }
    private static double[] buildToneLut() {
        double[] lut = new double[TONE_STEPS + 1];
        for (int i = 0; i <= TONE_STEPS; i++) lut[i] = toneCurve(i * TONE_MAX / TONE_STEPS);
        return lut;
    }
    private static double glintPow(double x, int exp) {
        double x2 = x * x, x4 = x2 * x2, x8 = x4 * x4, x16 = x8 * x8;
        if (exp == 16) return x16;
        if (exp == 24) return x16 * x8;
        return x16 * x8 * x4 * x2;
    }
    private static double planeOcclusion(int[][] m, double x, double y) {
        int cx = (int) Math.floor(x), cy = (int) Math.floor(y);
        double fx = x - cx, fy = y - cy, ao = 1.0;
        final double reach = 0.30, strength = 0.6;
        if (solidCell(m, cx - 1, cy)) ao *= 1.0 - strength * (1.0 - smooth(fx / reach));
        if (solidCell(m, cx + 1, cy)) ao *= 1.0 - strength * (1.0 - smooth((1.0 - fx) / reach));
        if (solidCell(m, cx, cy - 1)) ao *= 1.0 - strength * (1.0 - smooth(fy / reach));
        if (solidCell(m, cx, cy + 1)) ao *= 1.0 - strength * (1.0 - smooth((1.0 - fy) / reach));
        return ao;
    }
    private static double smooth(double t) { t = Math.clamp(t, 0.0, 1.0); return t * t * (3.0 - 2.0 * t); }
    private static double toneCurve(double v) { return v <= 0.7 ? v : 0.7 + 0.3 * (1.0 - Math.exp(-(v - 0.7) / 0.3)); }
    private static double wallOcclusion(double z, double height) {
        double ao = 1.0 - 0.45 * (1.0 - smooth(z / 0.22));
        ao *= 1.0 - 0.25 * (1.0 - smooth((height - z) / 0.14));
        return ao;
    }
    private static int toneToByte(double light) {
        if (!(light > 0)) return 0;
        if (light >= TONE_MAX) return 255;
        return (int) (TONE_LUT[(int) (light * TONE_STEPS / TONE_MAX)] * 255.0 + 0.5);
    }
    private int mixColor(int a, int b, double amount) {
        amount = Math.clamp(amount, 0, 1);
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return ((int)(ar * (1 - amount) + br * amount) << 16) | ((int)(ag * (1 - amount) + bg * amount) << 8) | (int)(ab * (1 - amount) + bb * amount);
    }
    private int shadeSurface(TraceHit hit, double lx, double ly, double lz, double pathBefore, boolean reflections, TraceHit scratch) {
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
            ao = planeOcclusion(hit.map, hit.x, hit.y);
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
        if (!reflections || reflectivity < 0.012) return lit;

        double dot = hit.dirX * nx + hit.dirY * ny + hit.dirZ * nz;
        double rrx = hit.dirX - 2.0 * dot * nx, rry = hit.dirY - 2.0 * dot * ny, rrz = hit.dirZ - 2.0 * dot * nz;
        double rlen = Math.sqrt(rrx * rrx + rry * rry + rrz * rrz);
        if (rlen < 1e-9) return lit;
        rrx /= rlen; rry /= rlen; rrz /= rlen;

        TraceHit reflected;
        if (hit.hyp) {
            double hl = Math.sqrt(rrx * rrx + rry * rry);
            if (hl < 1e-9) return lit;
            double reflectedSlope = hit.isPlane ? -hit.hypSlope : hit.hypSlope;
            reflected = traceHyp(hit.x + nx * 0.004, hit.y + ny * 0.004, hit.z + nz * 0.004, rrx / hl, rry / hl, 1.0, reflectedSlope, scratch, REFLECTION_STEPS);
        }
        else reflected = traceRay(hit.x + nx * 0.004, hit.y + ny * 0.004, hit.z + nz * 0.004, rrx, rry, rrz, hit.map, hit.portals, scratch, REFLECTION_STEPS);
        int reflectedColor = reflected.hit ? shadeSurface(reflected, -rrx, -rry, -rrz + 0.18, dist, false, scratch) : ceilingBaseColor;
        return mixColor(lit, reflectedColor, reflectivity);
    }
}