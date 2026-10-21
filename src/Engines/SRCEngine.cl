    #define RC_PORTAL 5
    #define RC_HIT_WALL 1
    #define RC_HIT_FLOOR 2
    #define RC_HIT_CEIL 3
    #define RC_MAX_SEG 10
    #define RC_MAX_FIN 4
    #define RC_MAX_DIST 48.0f
    #define RC_EDGE 5e-4f
    #define RC_WALL_BASE 0xECD485
    #define RC_CEIL_BASE 0x816E1E
    #define RC_FLOOR_BASE 0xD3AF63
    #define RC_FINISH_BASE 0x33FF66
    #define MAPARGS __global const int* maps, __global const int* grid, __global const int* meta, __global const int* portals
    #define MAPCALL maps, grid, meta, portals
    #define TEXARGS __global const int* wallTex, int ww, int wh, __global const int* finishTex, int fw, int fh, __global const int* floorTex, int flw, int flh, __global const int* ceilTex, int clw, int clh
    #define TEXCALL wallTex, ww, wh, finishTex, fw, fh, floorTex, flw, flh, ceilTex, clw, clh

    typedef struct { float x, y, dx, dy; int layer, hops; } Ray;
    typedef struct { int kind, type, side, layer; float u, frac, hx, hy, dist; } Hit;
    typedef struct {
        int segCount, finCount, wallSide, segLayer[RC_MAX_SEG], finSide[RC_MAX_FIN];
        float segStartDist[RC_MAX_SEG], segStartX[RC_MAX_SEG], segStartY[RC_MAX_SEG], segDirX[RC_MAX_SEG], segDirY[RC_MAX_SEG];
        float finDist[RC_MAX_FIN], finU[RC_MAX_FIN];
        float wallDist, wallU, exitD;
    } Column;

    static int applyBrightnessRC(int color, float b) {
        int r = (int)fmin(255.0f, ((color >> 16) & 255) * b);
        int g = (int)fmin(255.0f, ((color >> 8) & 255) * b);
        int bl = (int)fmin(255.0f, (color & 255) * b);
        return (r << 16) | (g << 8) | bl;
    }
    static int tintColorRC(int color, int tint, float amount) {
        int r = (color >> 16) & 255, g = (color >> 8) & 255, b = color & 255;
        int tr = (tint >> 16) & 255, tg = (tint >> 8) & 255, tb = tint & 255;
        r = (int)(r * (1 - amount) + tr * amount);
        g = (int)(g * (1 - amount) + tg * amount);
        b = (int)(b * (1 - amount) + tb * amount);
        return (r << 16) | (g << 8) | b;
    }
    static int tintStairsRC(int color, float wx, float wy, int layer, __global const int* maps, __global const int* meta) {
        int fx = (int)floor(wx), fy = (int)floor(wy);
        int mw = meta[3 * layer + 1], mh = meta[3 * layer + 2];
        if (fx < 0 || fy < 0 || fx >= mw || fy >= mh) return color;
        int cell = maps[meta[3 * layer] + fy * mw + fx];
        if (cell == 3) return tintColorRC(color, 0x3399FF, 0.45f);
        if (cell == 4) return tintColorRC(color, 0xFFAA33, 0.45f);
        return color;
    }
    static int sampleNearestRC(__global const int* tex, int tw, int th, float wx, float wy, int base) {
        if (tw <= 0 || th <= 0) return base;
        float fx = wx - floor(wx), fy = wy - floor(wy);
        int tx = min((int)(fx * tw), tw - 1);
        int ty = min((int)(fy * th), th - 1);
        return tex[ty * tw + tx];
    }
    static void warpPortalRC(__global const int* P, float hitX, float hitY, float dirX, float dirY, float* outX, float* outY, float* outDX, float* outDY) {
        float nax = P[2], nay = P[3], mbx = P[6], mby = P[7];
        float px = P[0], py = P[1], lx = P[4], ly = P[5];
        float u = (hitX - (px + fmax(nax, 0.0f) + fmax(nay, 0.0f))) * (-nay) + (hitY - (py + fmax(nay, 0.0f) + fmax(-nax, 0.0f))) * nax;
        u = clamp(u, RC_EDGE, 1.0f - RC_EDGE);
        float cosT = nax * mbx + nay * mby, sinT = nax * mby - nay * mbx;
        float ndx = dirX * cosT - dirY * sinT, ndy = dirX * sinT + dirY * cosT;
        float ex = (lx + fmax(mbx, 0.0f) + fmax(mby, 0.0f)) + u * (-mby);
        float ey = (ly + fmax(mby, 0.0f) + fmax(-mbx, 0.0f)) + u * mbx;
        float eps = 1e-4f;
        *outX = ex + ndx * eps; *outY = ey + ndy * eps; *outDX = ndx; *outDY = ndy;
    }
    static int enterPortalRC(Ray* s, int cx, int cy, int floors, MAPARGS) {
        if (s->hops >= 8) return 0;
        int mw = meta[3 * s->layer + 1], mh = meta[3 * s->layer + 2];
        if (cx < 0 || cy < 0 || cx >= mw || cy >= mh) return 0;
        int pidx = grid[meta[3 * s->layer] + cy * mw + cx];
        if (pidx < 0) return 0;
        __global const int* P = portals + 9 * pidx;
        if (s->dx * P[2] + s->dy * P[3] <= 1e-9f) return 0;
        float horizontal = sqrt(s->dx * s->dx + s->dy * s->dy);
        float nx, ny, ndx, ndy;
        warpPortalRC(P, s->x, s->y, s->dx, s->dy, &nx, &ny, &ndx, &ndy);
        float newH = sqrt(ndx * ndx + ndy * ndy);
        s->x = nx; s->y = ny;
        if (newH > 1e-9f) { s->dx = ndx * horizontal / newH; s->dy = ndy * horizontal / newH; }
        int lf = P[8];
        if (floors > 0 && lf >= 0 && lf < floors) s->layer = 1 + lf;
        s->hops++;
        return 1;
    }
    static void addSegRC(Column* c, float dist, Ray* s) {
        int k = c->segCount++;
        c->segStartDist[k] = dist; c->segStartX[k] = s->x; c->segStartY[k] = s->y;
        c->segDirX[k] = s->dx; c->segDirY[k] = s->dy; c->segLayer[k] = s->layer;
    }
    static void marchColumnRC(Column* c, float px, float py, float ux, float uy, int floors, float maxDistance, MAPARGS) {
        Ray s;
        s.x = px; s.y = py; s.dx = ux; s.dy = uy; s.layer = 0; s.hops = 0;
        c->segCount = 0; c->finCount = 0; c->wallSide = 0; c->wallU = 0.0f;
        c->wallDist = INFINITY; c->exitD = INFINITY;
        addSegRC(c, 0.0f, &s);
        float dist = 0.0f;
        int cx = (int)floor(s.x), cy = (int)floor(s.y);
        while (dist < maxDistance) {
            int mo = meta[3 * s.layer], mw = meta[3 * s.layer + 1], mh = meta[3 * s.layer + 2];
            float dx = s.dx, dy = s.dy;
            float tX = dx > 0 ? (cx + 1 - s.x) / dx : (dx < 0 ? (s.x - cx) / -dx : INFINITY);
            float tY = dy > 0 ? (cy + 1 - s.y) / dy : (dy < 0 ? (s.y - cy) / -dy : INFINITY);
            int corner = fabs(tX - tY) <= 1e-5f;
            int alongX = tX < tY;
            float t = fmin(tX, tY);
            if (dist + t > maxDistance) {
                s.x += dx * (maxDistance - dist); s.y += dy * (maxDistance - dist);
                return;
            }
            s.x += dx * t; s.y += dy * t; dist += t;
            if (corner) { cx += dx > 0 ? 1 : -1; cy += dy > 0 ? 1 : -1; alongX = 1; }
            else if (alongX) cx += dx > 0 ? 1 : -1;
            else cy += dy > 0 ? 1 : -1;
            if (cx < 0 || cy < 0 || cx >= mw || cy >= mh) { c->exitD = dist; return; }
            int type = maps[mo + cy * mw + cx];
            if (type == RC_PORTAL && enterPortalRC(&s, cx, cy, floors, MAPCALL)) {
                addSegRC(c, dist, &s);
                cx = (int)floor(s.x); cy = (int)floor(s.y);
                continue;
            }
            if (type == 1 || type == 2) {
                int side = alongX ? 0 : 1;
                float u = side == 0 ? s.y - floor(s.y) : s.x - floor(s.x);
                if ((side == 0 && s.dx < 0) || (side == 1 && s.dy > 0)) u = 1.0f - u;
                u = clamp(u, 0.0f, 0.999999f);
                if (type == 1) { c->wallDist = dist; c->wallU = u; c->wallSide = side; return; }
                if (c->finCount < RC_MAX_FIN) {
                    int k = c->finCount++;
                    c->finDist[k] = dist; c->finU[k] = u; c->finSide[k] = side;
                }
            }
        }
    }
    static Hit columnHitRC(Column* c, float z0, float sigma) {
        Hit h;
        h.kind = 0; h.type = 0; h.side = 0; h.layer = 0; h.u = 0.0f; h.frac = 0.0f; h.hx = 0.0f; h.hy = 0.0f; h.dist = 0.0f;
        float scale = sqrt(1.0f + sigma * sigma);
        float planeD = sigma < 0 ? z0 / -sigma : (sigma > 0 ? (1.0f - z0) / sigma : INFINITY);
        int floorPlane = sigma < 0;
        float nearest = fmin(fmin(c->wallDist, planeD), c->exitD);
        for (int k = 0; k < c->finCount; k++) {
            float d = c->finDist[k];
            if (d >= nearest) break;
            float z = z0 + sigma * d;
            if (z <= 0.5f) {
                h.kind = RC_HIT_WALL; h.type = 2; h.side = c->finSide[k]; h.u = c->finU[k];
                h.dist = d * scale;
                h.frac = clamp((0.5f - z) / 0.5f, 0.0f, 0.999999f);
                return h;
            }
        }
        if (c->wallDist < planeD && c->wallDist < c->exitD) {
            float z = z0 + sigma * c->wallDist;
            h.kind = RC_HIT_WALL; h.type = 1; h.side = c->wallSide; h.u = c->wallU;
            h.dist = c->wallDist * scale;
            h.frac = clamp(1.0f - z, 0.0f, 0.999999f);
            return h;
        }
        if (isfinite(planeD) && planeD < c->exitD && planeD < RC_MAX_DIST) {
            int k = 0;
            for (int j = 1; j < c->segCount && c->segStartDist[j] <= planeD; j++) k = j;
            float along = planeD - c->segStartDist[k];
            h.kind = floorPlane ? RC_HIT_FLOOR : RC_HIT_CEIL;
            h.hx = c->segStartX[k] + c->segDirX[k] * along;
            h.hy = c->segStartY[k] + c->segDirY[k] * along;
            h.dist = planeD * scale;
            h.layer = c->segLayer[k];
            return h;
        }
        return h;
    }
    static Hit marchExactRC(float x, float y, float z, float dx, float dy, float dz, int floors, float maxDistance, MAPARGS) {
        Hit h;
        h.kind = 0; h.type = 0; h.side = 0; h.layer = 0; h.u = 0.0f; h.frac = 0.0f; h.hx = 0.0f; h.hy = 0.0f; h.dist = 0.0f;
        Ray s;
        s.x = x; s.y = y; s.dx = dx; s.dy = dy; s.layer = 0; s.hops = 0;
        float hl = sqrt(dx * dx + dy * dy);
        if (hl < 1e-9f) {
            float t = dz < 0 ? z / -dz : (dz > 0 ? (1.0f - z) / dz : INFINITY);
            if (!(t <= maxDistance)) return h;
            h.kind = dz < 0 ? RC_HIT_FLOOR : RC_HIT_CEIL;
            h.hx = x; h.hy = y; h.dist = t;
            return h;
        }
        float scale = 1.0f / hl;
        float sigma = dz / hl;
        float maxH = maxDistance / scale;
        float d = 0.0f, zc = z;
        int cx = (int)floor(s.x), cy = (int)floor(s.y);
        int side = 0;
        for (int guard = 0; guard < 8192 && d < maxH; guard++) {
            int mo = meta[3 * s.layer], mw = meta[3 * s.layer + 1], mh = meta[3 * s.layer + 2];
            if (cx < 0 || cy < 0 || cx >= mw || cy >= mh) return h;
            float ux = s.dx / hl, uy = s.dy / hl;
            float tX = ux > 0 ? (cx + 1 - s.x) / ux : (ux < 0 ? (s.x - cx) / -ux : INFINITY);
            float tY = uy > 0 ? (cy + 1 - s.y) / uy : (uy < 0 ? (s.y - cy) / -uy : INFINITY);
            int corner = fabs(tX - tY) <= 1e-5f;
            int alongX = tX < tY;
            float boundary = fmin(tX, tY);
            float seg = fmin(boundary, maxH - d);
            float tPlane = sigma < 0 ? zc / -sigma : (sigma > 0 ? (1.0f - zc) / sigma : INFINITY);
            if (tPlane <= seg) {
                s.x += ux * tPlane; s.y += uy * tPlane;
                h.kind = sigma < 0 ? RC_HIT_FLOOR : RC_HIT_CEIL;
                h.hx = s.x; h.hy = s.y; h.dist = (d + tPlane) * scale; h.layer = s.layer;
                return h;
            }
            s.x += ux * seg; s.y += uy * seg;
            zc += sigma * seg;
            d += seg;
            if (d >= maxH) return h;
            if (corner) {
                cx += ux > 0 ? 1 : -1; cy += uy > 0 ? 1 : -1;
                s.x = ux > 0 ? cx : cx + 1;
                s.y = uy > 0 ? cy : cy + 1;
                side = 0;
            }
            else if (alongX) {
                cx += ux > 0 ? 1 : -1; side = 0;
                s.x = ux > 0 ? cx : cx + 1;
            }
            else {
                cy += uy > 0 ? 1 : -1; side = 1;
                s.y = uy > 0 ? cy : cy + 1;
            }
            if (cx < 0 || cy < 0 || cx >= mw || cy >= mh) return h;
            int type = maps[mo + cy * mw + cx];
            if (type == RC_PORTAL && enterPortalRC(&s, cx, cy, floors, MAPCALL)) {
                cx = (int)floor(s.x); cy = (int)floor(s.y);
                continue;
            }
            float wallH = type == 2 ? 0.5f : 1.0f;
            if ((type == 1 || type == 2) && zc <= wallH) {
                h.kind = RC_HIT_WALL; h.type = type; h.side = side; h.layer = s.layer;
                h.dist = d * scale;
                float u = side == 0 ? s.y - floor(s.y) : s.x - floor(s.x);
                if ((side == 0 && s.dx < 0) || (side == 1 && s.dy > 0)) u = 1 - u;
                h.u = clamp(u, 0.0f, 0.999999f);
                h.frac = clamp((wallH - zc) / wallH, 0.0f, 0.999999f);
                return h;
            }
        }
        return h;
    }
    static int shadeHitRC(Hit h, float light, int tinted, MAPARGS, TEXARGS) {
        if (h.kind == 0) return RC_CEIL_BASE;
        if (h.kind == RC_HIT_WALL) {
            int finish = h.type == 2;
            int tw = finish ? fw : ww, th = finish ? fh : wh;
            int color;
            if (tw > 0 && th > 0) {
                __global const int* tex = finish ? finishTex : wallTex;
                int tx = min((int)(h.u * tw), tw - 1);
                int ty = min((int)(h.frac * th), th - 1);
                color = tex[ty * tw + tx];
            }
            else color = finish ? RC_FINISH_BASE : RC_WALL_BASE;
            float b = clamp(1.0f - h.dist / light, 0.03f, 1.0f);
            if (h.side == 1) b *= 0.85f;
            return applyBrightnessRC(color, b);
        }
        int isFloor = h.kind == RC_HIT_FLOOR;
        int color = isFloor ? sampleNearestRC(floorTex, flw, flh, h.hx, h.hy, RC_FLOOR_BASE) : sampleNearestRC(ceilTex, clw, clh, h.hx, h.hy, RC_CEIL_BASE);
        if (isFloor && tinted) color = tintStairsRC(color, h.hx, h.hy, h.layer, maps, meta);
        float b = clamp(1.0f - h.dist / light, 0.05f, 1.0f);
        return applyBrightnessRC(color, b);
    }
    __kernel void src_render(MAPARGS, __global const int* wallTex, __global const int* finishTex, __global const int* floorTex,  __global const int* ceilTex,
                             __global const float* camX, __global const float* dp, __global const int* ip, __global int* out) {
        int x = get_global_id(0), y0 = get_global_id(1);
        int W = ip[0], H = ip[1], floors = ip[2], tinted = ip[3];
        int ww = ip[4], wh = ip[5], fw = ip[6], fh = ip[7], flw = ip[8], flh = ip[9], clw = ip[10], clh = ip[11];
        int mode = ip[12];
        if (x >= W) return;
        float playerX = dp[0], playerY = dp[1], camZ = dp[2];
        float dirX = dp[3], dirY = dp[4], planeLength = dp[5];
        float pitchSin = dp[6], pitchCos = dp[7], halfHeight = dp[8], focal = dp[9];
        float light = dp[10], maxDist = dp[11];

        if (mode == 1) {
            if (y0 != 0) return;
            float lateral = camX[x] * planeLength;
            float hx = dirX - lateral * dirY, hy = dirY + lateral * dirX;
            float hl = sqrt(hx * hx + hy * hy);
            Column c;
            marchColumnRC(&c, playerX, playerY, hx / hl, hy / hl, floors, maxDist, MAPCALL);
            for (int y = 0; y < H; y++) {
                float sigma = ((halfHeight - (y + 0.5f)) / focal) / hl;
                Hit h = columnHitRC(&c, camZ, sigma);
                out[y * W + x] = shadeHitRC(h, light, tinted, MAPCALL, TEXCALL);
            }
            return;
        }

        int y = y0;
        if (y >= H) return;
        float v = (halfHeight - (y + 0.5f)) / focal;
        float a = pitchCos - pitchSin * v;
        if (fabs(a) < 0.02f) a = a < 0 ? -0.02f : 0.02f;
        float rowScale = 1.0f / a;
        float rowZ = (pitchSin + pitchCos * v) / a;
        float lateral = camX[x] * planeLength * rowScale;
        float rx = dirX - lateral * dirY, ry = dirY + lateral * dirX, rz = rowZ;
        float len = sqrt(rx * rx + ry * ry + rz * rz);
        Hit h = marchExactRC(playerX, playerY, camZ, rx / len, ry / len, rz / len, floors, maxDist, MAPCALL);
        out[y * W + x] = shadeHitRC(h, light, tinted, MAPCALL, TEXCALL);
    }