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

    static Hit traceRay(float ox, float oy, float oz, float rx, float ry, float rz, int startLayer, int floors,
                        __global const int* maps, __global const int* meta, __global const int* grid, __global const int* portals, int maxSteps) {
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
    static int tintStairsGpu(int color, float wx, float wy, int layer, __global const int* maps, __global const int* meta) {
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
    static int sampleBilinear(__global const int* tex, int tw, int th, float wx, float wy, int base) {
        if (tw <= 0 || th <= 0) return base;
        float u = (wx - floor(wx)) * tw - 0.5f;
        float v = (wy - floor(wy)) * th - 0.5f;
        int x0 = (((int)floor(u)) % tw + tw) % tw, x1 = (x0 + 1) % tw;
        int y0 = (((int)floor(v)) % th + th) % th, y1 = (y0 + 1) % th;
        float fx = u - floor(u), fy = v - floor(v);
        int c00 = tex[y0 * tw + x0], c10 = tex[y0 * tw + x1], c01 = tex[y1 * tw + x0], c11 = tex[y1 * tw + x1];
        float w00 = (1 - fx) * (1 - fy), w10 = fx * (1 - fy), w01 = (1 - fx) * fy, w11 = fx * fy;
        int r = (int)(((c00 >> 16) & 255) * w00 + ((c10 >> 16) & 255) * w10 + ((c01 >> 16) & 255) * w01 + ((c11 >> 16) & 255) * w11);
        int g = (int)(((c00 >> 8) & 255) * w00 + ((c10 >> 8) & 255) * w10 + ((c01 >> 8) & 255) * w01 + ((c11 >> 8) & 255) * w11);
        int b = (int)((c00 & 255) * w00 + (c10 & 255) * w10 + (c01 & 255) * w01 + (c11 & 255) * w11);
        return (r << 16) | (g << 8) | b;
    }
    static int surfaceColorGpu(__global const int* tex, int tw, int th, float wx, float wy, int base, int quality) {
        if (quality == 0) return base;
        if (quality == 2) return sampleBilinear(tex, tw, th, wx, wy, base);
        return sampleNearest(tex, tw, th, wx, wy, base);
    }
    static int shadeHit(Hit h, float lightDistance, int tinted, int quality, __global const int* maps, __global const int* meta,
                        __global const int* wallTex, int ww, int wh, __global const int* finishTex, int fw, int fh,
                        __global const int* floorTex, int flw, int flh,__global const int* ceilTex, int clw, int clh) {
        if (!h.hit) return CEILING_BASE;
        if (h.isPlane) {
            int isFloor = h.type == -1;
            int color = isFloor ? surfaceColorGpu(floorTex, flw, flh, h.x, h.y, FLOOR_BASE, quality) : surfaceColorGpu(ceilTex, clw, clh, h.x, h.y, CEILING_BASE, quality);
            if (isFloor && tinted) color = tintStairsGpu(color, h.x, h.y, h.layer, maps, meta);
            return applyBrightnessGpu(color, clamp(1.0f - h.distance / lightDistance, 0.05f, 1.0f));
        }
        int finish = h.type == 2;
        int color;
        if (finish) {
            if (fw <= 0 || fh <= 0) color = FINISH_BASE;
            else {
                int tx = min((int)(h.wallU * fw), fw - 1);
                int ty = min((int)((1.0f - h.wallV) * fh), fh - 1);
                color = finishTex[ty * fw + max(0, tx)];
            }
        }
        else {
            if (ww <= 0 || wh <= 0) color = WALL_BASE;
            else {
                int tx = min((int)(h.wallU * ww), ww - 1);
                int ty = min((int)((1.0f - h.wallV) * wh), wh - 1);
                color = wallTex[ty * ww + max(0, tx)];
            }
        }
        float brightness = clamp(1.0f - h.distance / lightDistance, 0.03f, 1.0f);
        if (h.side == 1) brightness *= 0.85f;
        return applyBrightnessGpu(color, brightness);
    }
    __kernel void dda_render(__global const int* maps, __global const int* grid, __global const int* meta, __global const int* portals,
                             __global const int* wallTex, __global const int* finishTex, __global const int* floorTex, __global const int* ceilTex,
                             __global const float* camX, __global const float* dp, __global const int* ip, __global int* out)
    {
        int x = get_global_id(0), y = get_global_id(1);
        int W = ip[0], H = ip[1], quality = ip[2], floors = ip[3], tinted = ip[4];
        if (x >= W || y >= H) return;

        float playerX = dp[0], playerY = dp[1], cameraHeight = dp[2];
        float dirX = dp[3], dirY = dp[4], planeLength = dp[5];
        float pitchSin = dp[6], pitchCos = dp[7], halfHeight = dp[8], focal = dp[9], lightDistance = dp[10];

        float v = (halfHeight - (y + 0.5f)) / focal;
        float lateral = camX[x] * planeLength;
        float rx = dirX * pitchCos - dirY * lateral - dirX * pitchSin * v;
        float ry = dirY * pitchCos + dirX * lateral - dirY * pitchSin * v;
        float rz = pitchSin + pitchCos * v;
        float inv = 1.0f / sqrt(rx * rx + ry * ry + rz * rz);

        Hit h = traceRay(playerX, playerY, cameraHeight, rx * inv, ry * inv, rz * inv, 0, floors, maps, meta, grid, portals, MAX_PRIMARY_STEPS);
        out[y * W + x] = shadeHit(h, lightDistance, tinted, quality, maps, meta, wallTex, ip[5], ip[6], finishTex, ip[7], ip[8], floorTex, ip[9], ip[10], ceilTex, ip[11], ip[12]);
    }