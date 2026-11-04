package Project.Game;

public final class SkyCeiling {
    //region Variables
    //region Star Field
    private static final double STAR_CELL = 0.035;
    private static final double STAR_DENSITY = 0.14;
    private static final int STAR_WARM = 0xFFE9C4, STAR_COOL = 0xBFD4FF;
    //endregion
    //region Sky And Moon
    private static final double MOON_AZ = 2.2, MOON_EL = 0.25, MOON_RADIUS = 0.085;
    private static final int HORIZON_RGB = 0x1A2750, ZENITH_RGB = 0x03040C, MOON_RGB = 0xE8E4D0;
    //endregion
    //region GPU Source
    public static final String GPU_SOURCE = """
        #define SKY_HORIZON_RGB 0x1A2750
        #define SKY_ZENITH_RGB 0x03040C
        #define SKY_MOON_RGB 0xE8E4D0
        #define SKY_STAR_WARM 0xFFE9C4
        #define SKY_STAR_COOL 0xBFD4FF
        #define SKY_STAR_CELL 0.035f
        #define SKY_STAR_DENSITY 0.14f
        #define SKY_MOON_AZ 2.2f
        #define SKY_MOON_EL 0.25f
        #define SKY_MOON_RADIUS 0.085f
        static int skyLerpGpu(int a, int b, float t) {
            int r = (int)(((a >> 16) & 255) * (1.0f - t) + ((b >> 16) & 255) * t);
            int g = (int)(((a >> 8) & 255) * (1.0f - t) + ((b >> 8) & 255) * t);
            int bl = (int)((a & 255) * (1.0f - t) + (b & 255) * t);
            return (r << 16) | (g << 8) | bl;
        }
        static int skyAddGpu(int base, int add, float amount) {
            int r = min(255, (int)(((base >> 16) & 255) + ((add >> 16) & 255) * amount));
            int g = min(255, (int)(((base >> 8) & 255) + ((add >> 8) & 255) * amount));
            int b = min(255, (int)((base & 255) + (add & 255) * amount));
            return (r << 16) | (g << 8) | b;
        }
        static float skyHashGpu(int i, int j, int salt) {
            uint h = (uint)i * 374761393u + (uint)j * 668265263u + (uint)salt * 1442695041u;
            h = (h ^ (h >> 13)) * 1274126177u;
            h = h ^ (h >> 16);
            return (float)(h & 0xFFFFFFu) / 16777216.0f;
        }
        static int skyColorGpu(float az, float el, float time) {
            float t = clamp(el / 1.1f, 0.0f, 1.0f);
            t = t * t * (3.0f - 2.0f * t);
            int color = skyLerpGpu(SKY_HORIZON_RGB, SKY_ZENITH_RGB, t);
            float gx = az / SKY_STAR_CELL, gy = el / SKY_STAR_CELL;
            int cx = (int)floor(gx), cy = (int)floor(gy);
            float starLight = 0.0f;
            int starColor = 0;
            int nx = (gx - cx < 0.5f) ? -1 : 1, ny = (gy - cy < 0.5f) ? -1 : 1;
            for (int k = 0; k < 4; k++) {
                int dx = (k & 1) == 0 ? 0 : nx;
                int dy = (k & 2) == 0 ? 0 : ny;
                int i = cx + dx, j = cy + dy;
                if (skyHashGpu(i, j, 1) >= SKY_STAR_DENSITY) continue;
                float sx = i + skyHashGpu(i, j, 2), sy = j + skyHashGpu(i, j, 3);
                float size = 0.25f + 0.5f * skyHashGpu(i, j, 4);
                float d = sqrt((gx - sx) * (gx - sx) + (gy - sy) * (gy - sy)) / size;
                if (d >= 1.0f) continue;
                float falloff = (1.0f - d) * (1.0f - d);
                float twinkle = 0.6f + 0.4f * sin(time * (1.2f + 2.5f * skyHashGpu(i, j, 5)) + 6.283f * skyHashGpu(i, j, 6));
                float light = falloff * twinkle;
                if (light > starLight) {
                    starLight = light;
                    starColor = skyHashGpu(i, j, 7) < 0.5f ? SKY_STAR_WARM : SKY_STAR_COOL;
                }
            }
            if (starLight > 0.0f) color = skyAddGpu(color, starColor, min(1.0f, starLight));
            float dAz = remainder(az - SKY_MOON_AZ, 6.2831853f) * cos(SKY_MOON_EL);
            float dEl = el - SKY_MOON_EL;
            float dMoon2 = dAz * dAz + dEl * dEl;
            if (dMoon2 < SKY_MOON_RADIUS * SKY_MOON_RADIUS * 9.0f) {
                float dMoon = sqrt(dMoon2);
                float glow = exp(-pow(dMoon / (SKY_MOON_RADIUS * 1.8f), 2.0f)) * 0.18f;
                color = skyAddGpu(color, SKY_MOON_RGB, glow);
                if (dMoon < SKY_MOON_RADIUS) {
                    float edge = clamp((SKY_MOON_RADIUS - dMoon) / (SKY_MOON_RADIUS * 0.12f), 0.0f, 1.0f);
                    color = skyLerpGpu(color, SKY_MOON_RGB, edge);
                }
            }
            return color;
        }
        static int skyDirGpu(float dx, float dy, float dz, float time) {
            return skyColorGpu(atan2(dy, dx), atan2(dz, sqrt(dx * dx + dy * dy)), time);
        }
        """;
    //endregion
    //endregion

    //region Constructors
    private SkyCeiling() {}
    //endregion

    //region Sky Color
    public static int colorDir(double dx, double dy, double dz, double time) {
        return color(Math.atan2(dy, dx), Math.atan2(dz, Math.sqrt(dx * dx + dy * dy)), time);
    }
    public static int color(double azimuth, double elevation, double time) {
        double t = Math.clamp(elevation / 1.1, 0, 1);
        t = t * t * (3 - 2 * t);
        int color = lerpRgb(HORIZON_RGB, ZENITH_RGB, t);

        double gx = azimuth / STAR_CELL, gy = elevation / STAR_CELL;
        int cx = (int) Math.floor(gx), cy = (int) Math.floor(gy);
        double starLight = 0;
        int starColor = 0;
        int nx = gx - cx < 0.5 ? -1 : 1, ny = gy - cy < 0.5 ? -1 : 1;
        for (int k = 0; k < 4; k++) {
            int dx = (k & 1) == 0 ? 0 : nx;
            int dy = (k & 2) == 0 ? 0 : ny;
            int i = cx + dx, j = cy + dy;
            double roll = hash(i, j, 1);
            if (roll >= STAR_DENSITY) continue;
            double sx = i + hash(i, j, 2), sy = j + hash(i, j, 3);
            double size = 0.25 + 0.5 * hash(i, j, 4);
            double d = Math.sqrt((gx - sx) * (gx - sx) + (gy - sy) * (gy - sy)) / size;
            if (d >= 1) continue;
            double falloff = (1 - d) * (1 - d);
            double twinkle = 0.6 + 0.4 * Math.sin(time * (1.2 + 2.5 * hash(i, j, 5)) + 6.283 * hash(i, j, 6));
            double light = falloff * twinkle;
            if (light > starLight) {
                starLight = light;
                starColor = hash(i, j, 7) < 0.5 ? STAR_WARM : STAR_COOL;
            }
        }
        if (starLight > 0) color = addScaled(color, starColor, Math.min(1.0, starLight));

        double dAz = Math.IEEEremainder(azimuth - MOON_AZ, 2 * Math.PI) * Math.cos(MOON_EL);
        double dEl = elevation - MOON_EL;
        double dMoon2 = dAz * dAz + dEl * dEl;
        if (dMoon2 < MOON_RADIUS * MOON_RADIUS * 9) {
            double dMoon = Math.sqrt(dMoon2);
            double glow = Math.exp(-Math.pow(dMoon / (MOON_RADIUS * 1.8), 2)) * 0.18;
            color = addScaled(color, MOON_RGB, glow);
            if (dMoon < MOON_RADIUS) {
                double edge = Math.clamp((MOON_RADIUS - dMoon) / (MOON_RADIUS * 0.12), 0, 1);
                color = lerpRgb(color, MOON_RGB, edge);
            }
        }
        return color;
    }
    private static int lerpRgb(int a, int b, double t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }
    private static int addScaled(int base, int add, double amount) {
        int r = Math.min(255, (int) (((base >> 16) & 255) + ((add >> 16) & 255) * amount));
        int g = Math.min(255, (int) (((base >> 8) & 255) + ((add >> 8) & 255) * amount));
        int b = Math.min(255, (int) ((base & 255) + (add & 255) * amount));
        return (r << 16) | (g << 8) | b;
    }
    private static double hash(int i, int j, int salt) {
        int h = i * 374761393 + j * 668265263 + salt * 1442695041;
        h = (h ^ (h >>> 13)) * 1274126177;
        h = h ^ (h >>> 16);
        return (h & 0xFFFFFF) / 16777216.0;
    }
    //endregion
}