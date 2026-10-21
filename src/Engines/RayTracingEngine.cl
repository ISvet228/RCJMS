    #define PORTAL_TYPE 5
    #define MAX_PRIMARY_STEPS 6000
    #define WALL_BASE 0xECD485
    #define CEILING_BASE 0x816E1E
    #define FLOOR_BASE 0xD3AF63
    #define FINISH_BASE 0x33FF66

    typedef struct {
        int hit, isPlane, type, side, layer;
        float distance, wallU, wallV, x, y, z, dirX, dirY, dirZ;
    } Hit;

    static float wallHeightFor(int type) { return type == 2 ? 0.5f : 1.0f; }

    static int cellAt(__global const int* maps, __global const int* meta, int layer, int x, int y) { return maps[meta[3 * layer] + y * meta[3 * layer + 1] + x]; }

    static void warpPoint(__global const int* P, float hitX, float hitY, float dirX, float dirY, float* outX, float* outY, float* outDX, float* outDY) {
        float nax = P[2], nay = P[3], mbx = P[6], mby = P[7];
        float px = P[0], py = P[1], lx = P[4], ly = P[5];
        float u = (hitX - (px + fmax(nax, 0.0f) + fmax(nay, 0.0f))) * (-nay) + (hitY - (py + fmax(nay, 0.0f) + fmax(-nax, 0.0f))) * nax;
        u = clamp(u, 5e-4f, 1.0f - 5e-4f);
        float cosT = nax * mbx + nay * mby, sinT = nax * mby - nay * mbx;
        float ndx = dirX * cosT - dirY * sinT, ndy = dirX * sinT + dirY * cosT;
        float ex = (lx + fmax(mbx, 0.0f) + fmax(mby, 0.0f)) + u * (-mby);
        float ey = (ly + fmax(mby, 0.0f) + fmax(-mbx, 0.0f)) + u * mbx;
        float eps = 1e-4f;
        *outX = ex + ndx * eps; *outY = ey + ndy * eps; *outDX = ndx; *outDY = ndy;
    }
    static Hit traceRay(float ox, float oy, float oz, float rx, float ry, float rz, int startLayer, int floors, __global const int* maps, __global const int* meta,
                        __global const int* grid, __global const int* portals, int maxSteps) {
        Hit r;
        r.hit = 0; r.isPlane = 0; r.type = 0; r.side = 0; r.layer = startLayer;
        r.distance = 0; r.wallU = 0; r.wallV = 0; r.x = 0; r.y = 0;

        float totalDistance = 0;
        int hops = 0, steps = 0;
        float wallT = INFINITY;
        int wallType = 0, wallSide = 0;
        int rayLayer = startLayer;
        int finished = 0;

        while (1) {
            float horizontal = sqrt(rx * rx + ry * ry);
            if (horizontal <= 1e-9f) break;
            float planeHere = rz < -1e-9f ? -oz / rz : (rz > 1e-9f ? (1.0f - oz) / rz : INFINITY);

            float dx = rx / horizontal, dy = ry / horizontal;
            int mapX = (int)floor(ox), mapY = (int)floor(oy);
            float deltaX = fabs(1.0f / dx), deltaY = fabs(1.0f / dy);
            float sideX = (dx < 0 ? ox - mapX : mapX + 1.0f - ox) * deltaX;
            float sideY = (dy < 0 ? oy - mapY : mapY + 1.0f - oy) * deltaY;
            int mw = meta[3 * rayLayer + 1], mh = meta[3 * rayLayer + 2];

            int leftSegment = 0;
            while (mapX >= 0 && mapY >= 0 && mapX < mw && mapY < mh) {
                float horizontalT;
                int side;
                if (sideX < sideY) { horizontalT = sideX; sideX += deltaX; mapX += dx < 0 ? -1 : 1; side = 0; }
                else { horizontalT = sideY; sideY += deltaY; mapY += dy < 0 ? -1 : 1; side = 1; }
                if (mapX < 0 || mapY < 0 || mapX >= mw || mapY >= mh) break;

                float candidateT = horizontalT / horizontal;
                if (candidateT >= planeHere || ++steps > maxSteps) { finished = 1; break; }

                int type = cellAt(maps, meta, rayLayer, mapX, mapY);
                if (type == PORTAL_TYPE && hops < 8) {
                    int pidx = grid[meta[3 * rayLayer] + mapY * mw + mapX];
                    if (pidx >= 0) {
                        __global const int* P = portals + 9 * pidx;
                        if (rx * P[2] + ry * P[3] > 1e-9f) {
                            float candidateZ = oz + rz * candidateT;
                            if (candidateZ >= 0.0f && candidateZ <= 1.0f) {
                                float hx = ox + rx * candidateT, hy = oy + ry * candidateT;
                                totalDistance += candidateT;
                                float nx, ny, ndx, ndy;
                                warpPoint(P, hx, hy, rx, ry, &nx, &ny, &ndx, &ndy);
                                ox = nx; oy = ny; rx = ndx; ry = ndy;
                                int lf = P[8];
                                if (floors > 0 && lf >= 0 && lf < floors) rayLayer = 1 + lf;
                                oz = candidateZ;
                                hops++;
                                leftSegment = 1;
                                break;
                            }
                        }
                    }
                }
                if (leftSegment) break;

                if (type != 1 && type != 2) continue;

                float candidateZ = oz + rz * candidateT;
                if (candidateZ >= 0.0f && candidateZ <= wallHeightFor(type)) {
                    wallT = candidateT; wallType = type; wallSide = side;
                    finished = 1;
                    break;
                }
            }
            if (finished) break;
            if (leftSegment) continue;
            break;
        }

        r.layer = rayLayer;
        float planeT = INFINITY;
        int planeType = 0;
        if (rz < -1e-9f) { planeT = -oz / rz; planeType = -1; }
        else if (rz > 1e-9f) { planeT = (1.0f - oz) / rz; planeType = 1; }

        if (wallT < planeT && wallT < INFINITY) {
            float wallH = wallHeightFor(wallType);
            float hx = ox + rx * wallT, hy = oy + ry * wallT;
            float z = clamp(oz + rz * wallT, 0.0f, wallH);
            r.hit = 1;
            r.distance = totalDistance + wallT;
            r.x = hx; r.y = hy; r.z = z;
            r.dirX = rx; r.dirY = ry; r.dirZ = rz;
            r.type = wallType;
            r.side = wallSide;
            float wallU = wallSide == 0 ? hy : hx;
            wallU -= floor(wallU);
            if ((wallSide == 0 && rx < 0) || (wallSide == 1 && ry > 0)) wallU = 1.0f - wallU;
            r.wallU = clamp(wallU, 0.0f, 0.999999f);
            r.wallV = clamp(z / wallH, 0.0f, 0.999999f);
        }
        else if (planeT < INFINITY && planeT > 0) {
            r.hit = 1;
            r.isPlane = 1;
            r.distance = totalDistance + planeT;
            r.x = ox + rx * planeT;
            r.y = oy + ry * planeT;
            r.z = planeType < 0 ? 0.0f : 1.0f;
            r.dirX = rx; r.dirY = ry; r.dirZ = rz;
            r.type = planeType;
            r.side = 0;
        }
        return r;
    }
    static int applyBrightnessGpu(int color, float b) {
        int r = (int)fmin(255.0f, ((color >> 16) & 255) * b);
        int g = (int)fmin(255.0f, ((color >> 8) & 255) * b);
        int bl = (int)fmin(255.0f, (color & 255) * b);
        return (r << 16) | (g << 8) | bl;
    }
    static int tintColorGpu(int color, int tint, float amount) {
        int r = (color >> 16) & 255, g = (color >> 8) & 255, b = color & 255;
        int tr = (tint >> 16) & 255, tg = (tint >> 8) & 255, tb = tint & 255;
        r = (int)(r * (1 - amount) + tr * amount);
        g = (int)(g * (1 - amount) + tg * amount);
        b = (int)(b * (1 - amount) + tb * amount);
        return (r << 16) | (g << 8) | b;
    }
    static int tintStairsGpu(int color, float wx, float wy, int layer,
                             __global const int* maps, __global const int* meta) {
        int fx = (int)floor(wx), fy = (int)floor(wy);
        if (fx < 0 || fy < 0 || fy >= meta[3 * layer + 2] || fx >= meta[3 * layer + 1]) return color;
        int cell = cellAt(maps, meta, layer, fx, fy);
        if (cell == 3) return tintColorGpu(color, 0x3399FF, 0.45f);
        if (cell == 4) return tintColorGpu(color, 0xFFAA33, 0.45f);
        return color;
    }
    static int sampleNearest(__global const int* tex, int tw, int th, float wx, float wy, int base) {
        if (tw <= 0 || th <= 0) return base;
        float fracX = wx - floor(wx), fracY = wy - floor(wy);
        int tx = min((int)(fracX * tw), tw - 1);
        int ty = min((int)(fracY * th), th - 1);
        return tex[ty * tw + tx];
    }
    #define FLOOR_BASE_RT 0xD3AF63
    #define CEILING_BASE_RT 0x816E1E
    #define WALL_BASE_RT 0xECD485
    #define FINISH_BASE_RT 0x33FF66
    #define GLOSS 0.75f
    #define FLASH_RANGE_SQ_INV (1.0f / (3.0f * 3.0f))
    #define TONE_STEPS 2048
    #define TONE_MAX 3.0f
    #define SDF_SUB_RT 8

    typedef struct {
        int lit;
        float refl, dist, nx, ny, nz;
    } Shade;

    static float smoothT(float t) {
        t = clamp(t, 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }
    static float glintPowT(float x, int e) {
        float x2 = x * x, x4 = x2 * x2, x8 = x4 * x4, x16 = x8 * x8;
        if (e == 16) return x16;
        if (e == 24) return x16 * x8;
        return x16 * x8 * x4 * x2;
    }
    static int toneToByte(float light, __global const float* lut) {
        if (!(light > 0)) return 0;
        if (light >= TONE_MAX) return 255;
        return (int)(lut[(int)(light * TONE_STEPS / TONE_MAX)] * 255.0f + 0.5f);
    }
    static int mixColorRT(int a, int b, float amount) {
        amount = clamp(amount, 0.0f, 1.0f);
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return ((int)(ar * (1 - amount) + br * amount) << 16) | ((int)(ag * (1 - amount) + bg * amount) << 8) | (int)(ab * (1 - amount) + bb * amount);
    }
    static float wallOcclusionRT(float z, float height) {
        float ao = 1.0f - 0.45f * (1.0f - smoothT(z / 0.22f));
        ao *= 1.0f - 0.25f * (1.0f - smoothT((height - z) / 0.14f));
        return ao;
    }
    static float sdfSampleRT(__global const float* g, int cols, int rows, float x, float y) {
        float gx = clamp(x * SDF_SUB_RT, 0.0f, cols - 1.000001f);
        float gy = clamp(y * SDF_SUB_RT, 0.0f, rows - 1.000001f);
        int i0 = (int)gx, j0 = (int)gy;
        int i1 = min(i0 + 1, cols - 1), j1 = min(j0 + 1, rows - 1);
        float fx = gx - i0, fy = gy - j0;
        float a = g[j0 * cols + i0], b = g[j0 * cols + i1];
        float c = g[j1 * cols + i0], d = g[j1 * cols + i1];
        return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy;
    }
    static float planeOcclusionRT(__global const float* sdfW, __global const float* sdfF, __global const int* sm, int layer, float x, float y) {
        int off = sm[4 * layer], cols = sm[4 * layer + 1], rows = sm[4 * layer + 2], hasFinish = sm[4 * layer + 3];
        float d = sdfSampleRT(sdfW + off, cols, rows, x, y);
        if (hasFinish) d = fmin(d, sdfSampleRT(sdfF + off, cols, rows, x, y));
        const float nearReach = 0.30f, nearStrength = 0.6f;
        const float farReach = 0.90f, farStrength = 0.2f;
        float ao = 1.0f - nearStrength * (1.0f - smoothT(d / nearReach));
        ao *= 1.0f - farStrength * (1.0f - smoothT(d / farReach));
        return ao;
    }
    static int sampleWallRT(__global const int* tex, int tw, int th, Hit h, int isFinish) {
        if (tw <= 0 || th <= 0) return isFinish ? FINISH_BASE_RT : WALL_BASE_RT;
        int tx = min((int)(h.wallU * tw), tw - 1);
        int ty = min((int)((1.0f - h.wallV) * th), th - 1);
        return tex[ty * tw + max(0, tx)];
    }
    static int sampleFloorCeilRT(__global const int* tex, int tw, int th, float wx, float wy, int base) { return sampleNearest(tex, tw, th, wx, wy, base); }
    static Shade shadeOneRT(Hit h, float lx, float ly, float lz, float pathBefore, __global const int* maps, __global const int* meta,
                            __global const int* wallTex, int ww, int wh, __global const int* finishTex, int fw, int fh, __global const int* floorTex, int flw, int flh,
                            __global const int* ceilTex, int clw, int clh, __global const float* sdfW, __global const float* sdfF, __global const int* sm,
                            __global const float* lut, int tinted) {
        Shade s;
        float llen = sqrt(lx * lx + ly * ly + lz * lz);
        if (llen > 1e-9f) { lx /= llen; ly /= llen; lz /= llen; }

        int base;
        float nx, ny, nz, ao, specStrength, reflectivity;
        int shininess;
        int emissive = 0;
        if (h.isPlane) {
            int isFloor = h.type == -1;
            base = isFloor ? sampleFloorCeilRT(floorTex, flw, flh, h.x, h.y, FLOOR_BASE_RT) : sampleFloorCeilRT(ceilTex, clw, clh, h.x, h.y, CEILING_BASE_RT);
            if (isFloor && tinted) base = tintStairsGpu(base, h.x, h.y, h.layer, maps, meta);
            nx = 0; ny = 0; nz = isFloor ? 1 : -1;
            ao = planeOcclusionRT(sdfW, sdfF, sm, h.layer, h.x, h.y);
            shininess = isFloor ? 30 : 16;
            specStrength = isFloor ? 0.16f : 0.10f;
            reflectivity = isFloor ? 0.12f : 0.06f;
        }
        else {
            int finish = h.type == 2;
            base = finish ? sampleWallRT(finishTex, fw, fh, h, 1) : sampleWallRT(wallTex, ww, wh, h, 0);
            nx = h.side == 0 ? (h.dirX > 0 ? -1 : 1) : 0;
            ny = h.side == 1 ? (h.dirY > 0 ? -1 : 1) : 0;
            nz = 0;
            emissive = finish;
            ao = finish ? 1.0f : wallOcclusionRT(h.z, h.type == 2 ? 0.5f : 1.0f);
            shininess = 24;
            specStrength = 0.10f;
            reflectivity = finish ? 0.08f : 0.24f;
        }

        float dist = pathBefore + h.distance;
        float att = 1.0f / (1.0f + dist * dist * FLASH_RANGE_SQ_INV);
        float ndl = fmax(0.0f, nx * lx + ny * ly + nz * lz);
        float ndf = fmax(0.0f, nx * 0.46f + ny * 0.36f + nz * 0.85f);
        float flash = 0.95f * att * ndl;

        float glint = 0;
        if (ndl > 0) {
            float hx = lx - h.dirX, hy = ly - h.dirY, hz = lz - h.dirZ;
            float hlen = sqrt(hx * hx + hy * hy + hz * hz);
            if (hlen > 1e-9f) glint = specStrength * att * GLOSS * glintPowT(fmax(0.0f, (nx * hx + ny * hy + nz * hz) / hlen), shininess);
        }

        float fillMix = 0.6f + 0.4f * ao;
        float lightR = 0.10f * ao + 0.30f * ndf * ao + 1.0f * flash * fillMix + 1.0f * glint;
        float lightG = 0.115f * ao + 0.28f * ndf * ao + 0.88f * flash * fillMix + 0.88f * glint;
        float lightB = 0.17f * ao + 0.25f * ndf * ao + 0.70f * flash * fillMix + 0.70f * glint;
        if (emissive) {
            lightR = fmax(lightR, 0.7f);
            lightG = fmax(lightG, 0.7f);
            lightB = fmax(lightB, 0.7f);
        }

        int br = (base >> 16) & 255, bg = (base >> 8) & 255, bb = base & 255;
        s.lit = (toneToByte(br / 255.0f * lightR, lut) << 16) | (toneToByte(bg / 255.0f * lightG, lut) << 8) |  toneToByte(bb / 255.0f * lightB, lut);

        float level = (lightR + lightG + lightB) / 3.0f;
        reflectivity *= GLOSS * clamp(level, 0.15f, 1.0f);

        s.refl = reflectivity;
        s.dist = dist;
        s.nx = nx; s.ny = ny; s.nz = nz;
        return s;
    }

    __kernel void rt_render(__global const int* maps, __global const int* grid, __global const int* meta, __global const int* portals,
                            __global const int* wallTex, __global const int* finishTex, __global const int* floorTex, __global const int* ceilTex,
                            __global const float* sdfW, __global const float* sdfF, __global const int* sm, __global const float* lut,
                            __global const float* dp, __global const int* ip,__global int* out){
        int x = get_global_id(0), y = get_global_id(1);
        int W = ip[0], H = ip[1], steps = ip[2], depthMax = ip[3], floors = ip[4], tinted = ip[5];
        if (x >= W || y >= H) return;

        float px = dp[0], py = dp[1], pz = dp[2];
        float dirX = dp[3], dirY = dp[4], cp = dp[5], sp = dp[6], tanHalfFov = dp[7], verticalTan = dp[8];

        float sx = ((x + 0.5f) / W * 2.0f - 1.0f) * tanHalfFov;
        float sy = (1.0f - (y + 0.5f) / H * 2.0f) * verticalTan;
        float rx = dirX * cp + (-dirY) * sx + (-dirX * sp) * sy;
        float ry = dirY * cp + dirX * sx + (-dirY * sp) * sy;
        float rz = sp + cp * sy;
        float inv = 1.0f / sqrt(rx * rx + ry * ry + rz * rz);
        rx *= inv; ry *= inv; rz *= inv;

        Hit cur = traceRay(px, py, pz, rx, ry, rz, 0, floors, maps, meta, grid, portals, MAX_PRIMARY_STEPS);
        if (!cur.hit) { out[y * W + x] = CEILING_BASE_RT; return; }

        int lits[3];
        float refls[3];
        int count = 0;
        int result = 0;
        float lx = -cur.dirX, ly = -cur.dirY, lz = -cur.dirZ + 0.18f;
        float pathBefore = 0;
        int depthLeft = depthMax;
        for (int k = 0; ; k++) {
            Shade s = shadeOneRT(cur, lx, ly, lz, pathBefore, maps, meta, wallTex, ip[6], ip[7], finishTex, ip[8], ip[9],
                                 floorTex, ip[10], ip[11], ceilTex, ip[12], ip[13], sdfW, sdfF, sm, lut, tinted);
            lits[k] = s.lit;
            refls[k] = s.refl;
            count = k + 1;
            if (depthLeft <= 0 || s.refl < 0.012f) { result = s.lit; break; }

            float dot = cur.dirX * s.nx + cur.dirY * s.ny + cur.dirZ * s.nz;
            float rrx = cur.dirX - 2.0f * dot * s.nx;
            float rry = cur.dirY - 2.0f * dot * s.ny;
            float rrz = cur.dirZ - 2.0f * dot * s.nz;
            float rlen = sqrt(rrx * rrx + rry * rry + rrz * rrz);
            if (rlen < 1e-9f) { result = s.lit; break; }
            rrx /= rlen; rry /= rlen; rrz /= rlen;

            Hit child = traceRay(cur.x + s.nx * 0.004f, cur.y + s.ny * 0.004f, cur.z + s.nz * 0.004f, rrx, rry, rrz, cur.layer, floors, maps, meta, grid, portals, steps);
            if (!child.hit) { result = s.lit; break; }

            cur = child;
            lx = -rrx; ly = -rry; lz = -rrz + 0.18f;
            pathBefore = s.dist;
            depthLeft--;
        }
        for (int k = count - 2; k >= 0; k--) result = mixColorRT(lits[k], result, refls[k]);
        out[y * W + x] = result;
    }