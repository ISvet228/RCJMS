#define MO_R 0.2f
#define MO_MAXCOL 48.0f
#define MO_PRIM_STEPS 6000
#define MO_EDGE_RATIO 0.25f
#define MO_WALL_BASE 0xECD485
#define MO_CEIL_BASE 0x816E1E
#define MO_FLOOR_BASE 0xD3AF63
#define MO_FINISH_BASE 0x33FF66
#define WORLD __global const int* maps, __global const int* grid, __global const int* meta, __global const int* corners, __global const float* sdf, __global const int* portals
#define WCALL maps, grid, meta, corners, sdf, portals
#define TEXARGS __global const int* wallTex, int ww, int wh, __global const int* finishTex, int fw, int fh, __global const int* floorTex, int flw, int flh, __global const int* ceilTex, int clw, int clh
#define TCALL wallTex, ww, wh, finishTex, fw, fh, floorTex, flw, flh, ceilTex, clw, clh
#define COLF 35

__constant float MO_AO_H[4] = {0.08f, 0.20f, 0.36f, 0.60f};

typedef struct {
    float playerX, playerY, camZ, dirX, dirY, cosP, sinP, tanHalf, verticalTan;
    int W, H, floors, tinted, aoN, reflDepth, reflSteps, edgeGrid, columnPath;
} Par;

typedef struct {
    int hit, isPlane, type, side, layer, curved;
    float distance, x, y, z, dirX, dirY, dirZ, wallU, wallV, nrmX, nrmY;
} TH;

typedef struct { float nrmX, nrmY; int curved; } NR;

typedef struct {
    float ux, uy, hl, wallDist, wallTexU, wallNx, wallNy;
    int fallback, wallSide, wallCurved, finCount;
    float finDist[4], finU[4], finNx[4], finNy[4];
    int finSide[4], finCurved[4];
} Col;

typedef struct { float reflectivity, nx, ny, nz, dist; } LO;

static Par makePar(__global const float* dp, __global const int* ip) {
    Par p;
    p.playerX = dp[0]; p.playerY = dp[1]; p.camZ = dp[2];
    p.dirX = dp[3]; p.dirY = dp[4]; p.cosP = dp[5]; p.sinP = dp[6];
    p.tanHalf = dp[7]; p.verticalTan = dp[8];
    p.W = ip[0]; p.H = ip[1]; p.floors = ip[2]; p.tinted = ip[3];
    p.aoN = ip[13]; p.reflDepth = ip[14]; p.reflSteps = ip[15]; p.edgeGrid = ip[16]; p.columnPath = ip[17];
    return p;
}

static float smoothM(float t) { t = clamp(t, 0.0f, 1.0f); return t * t * (3.0f - 2.0f * t); }

static float glintPowM(float x, int exp) {
    float x2 = x * x, x4 = x2 * x2, x8 = x4 * x4, x16 = x8 * x8;
    if (exp == 16) return x16;
    if (exp == 24) return x16 * x8;
    return x16 * x8 * x4 * x2;
}

static float toneCurveM(float v) { return v <= 0.7f ? v : 0.7f + 0.3f * (1.0f - exp(-(v - 0.7f) / 0.3f)); }

static int toneToByteM(float light) {
    if (!(light > 0)) return 0;
    if (light >= 3.0f) return 255;
    int idx = (int)(light * 2048 / 3.0f);
    return (int)(toneCurveM(idx * 3.0f / 2048) * 255.0f + 0.5f);
}

static float wallOcclusionM(float z, float height) {
    float ao = 1.0f - 0.45f * (1.0f - smoothM(z / 0.22f));
    ao *= 1.0f - 0.25f * (1.0f - smoothM((height - z) / 0.14f));
    return ao;
}

static int mixM(int a, int b, float amount) {
    amount = clamp(amount, 0.0f, 1.0f);
    int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
    int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
    return ((int)(ar * (1 - amount) + br * amount) << 16) | ((int)(ag * (1 - amount) + bg * amount) << 8) | (int)(ab * (1 - amount) + bb * amount);
}

static int tintColorM(int color, int tint, float amount) {
    int r = (color >> 16) & 255, g = (color >> 8) & 255, b = color & 255;
    int tr = (tint >> 16) & 255, tg = (tint >> 8) & 255, tb = tint & 255;
    r = (int)(r * (1 - amount) + tr * amount);
    g = (int)(g * (1 - amount) + tg * amount);
    b = (int)(b * (1 - amount) + tb * amount);
    return (r << 16) | (g << 8) | b;
}

static int applyBrightnessM(int color, float brightness) {
    int r = (int)fmin(255.0f, ((color >> 16) & 255) * brightness);
    int g = (int)fmin(255.0f, ((color >> 8) & 255) * brightness);
    int b = (int)fmin(255.0f, (color & 255) * brightness);
    return (r << 16) | (g << 8) | b;
}

static int tintStairsM(__global const int* maps, __global const int* meta, int color, float wx, float wy, int L) {
    int fx = (int)floor(wx), fy = (int)floor(wy);
    int mw = meta[8 * L + 1], mh = meta[8 * L + 2];
    if (fx < 0 || fy < 0 || fx >= mw || fy >= mh) return color;
    int cell = maps[meta[8 * L] + fy * mw + fx];
    if (cell == 3) return tintColorM(color, 0x3399FF, 0.45f);
    if (cell == 4) return tintColorM(color, 0xFFAA33, 0.45f);
    return color;
}

static int sampleNearestM(__global const int* tex, int tw, int th, float wx, float wy, int base) {
    if (tw <= 0 || th <= 0) return base;
    float fx = wx - floor(wx), fy = wy - floor(wy);
    int tx = min((int)(fx * tw), tw - 1);
    int ty = min((int)(fy * th), th - 1);
    return tex[ty * tw + tx];
}
static int cellT(WORLD, int L, int x, int y) {
    int w = meta[8 * L + 1], h = meta[8 * L + 2];
    if (x < 0 || y < 0 || x >= w || y >= h) return -1;
    return maps[meta[8 * L] + y * w + x];
}

static int cornerM(WORLD, int L, int x, int y) { return corners[meta[8 * L] + y * meta[8 * L + 1] + x]; }
static float gridVal(WORLD, int L, int ch, float x, float y) {
    int cols = meta[8 * L + 4], rows = meta[8 * L + 5], po = meta[8 * L + 3];
    float gx = clamp(x * 8.0f, 0.0f, cols - 1.000001f), gy = clamp(y * 8.0f, 0.0f, rows - 1.000001f);
    int i0 = (int)gx, j0 = (int)gy;
    int i1 = min(i0 + 1, cols - 1), j1 = min(j0 + 1, rows - 1);
    float fx = gx - i0, fy = gy - j0;
    float a = sdf[2 * (po + j0 * cols + i0) + ch], b = sdf[2 * (po + j0 * cols + i1) + ch];
    float c = sdf[2 * (po + j1 * cols + i0) + ch], d = sdf[2 * (po + j1 * cols + i1) + ch];
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy;
}

static float solidDistM(float dw, float df, int hasFinish, float z) {
    float dz = fmax(0.0f, z - 1.0f);
    float d = sqrt(dw * dw + dz * dz);
    if (hasFinish) {
        float ddz = fmax(0.0f, z - 0.5f);
        d = fmin(d, sqrt(df * df + ddz * ddz));
    }
    return d;
}
static float occlusionM(WORLD, int L, int aoN, float px, float py, float pz, float nx, float ny, float nz) {
    float occ = 0.0f, weight = 1.0f;
    int hasFin = meta[8 * L + 6];
    int flat = nx == 0 && ny == 0;
    for (int i = 0; i < aoN; i++) {
        float h = MO_AO_H[i];
        float x = flat ? px : px + nx * h;
        float y = flat ? py : py + ny * h;
        float dw = gridVal(WCALL, L, 0, x, y);
        float df = hasFin ? gridVal(WCALL, L, 1, x, y) : 0.0f;
        float d = solidDistM(dw, df, hasFin, pz + nz * h);
        occ += fmax(0.0f, h - d) * weight;
        weight *= 0.6f;
    }
    return clamp(1.0f - 1.1f * occ, 0.25f, 1.0f);
}

static float faceCoordM(int side, float px, float py, float dirX, float dirY) {
    float u = side == 0 ? py - floor(py) : px - floor(px);
    if ((side == 0 && dirX < 0) || (side == 1 && dirY > 0)) u = 1.0f - u;
    return clamp(u, 0.0f, 0.999999f);
}

static int inRoundedCornerM(int mask, float lx, float ly) {
    float r = MO_R, hi = 1.0f - r;
    if (!(lx < r || lx > hi) || !(ly < r || ly > hi)) return 0;
    int k = (lx > hi ? 1 : 0) | (ly > hi ? 2 : 0);
    return ((mask >> k) & 1) != 0;
}
static float concaveEntryM(float px, float py, float dx, float dy, int cx, int cy, int mask, NR* out) {
    float r = MO_R, hi = 1.0f - r;
    float lx = px - cx, ly = py - cy;
    float best = INFINITY;
    for (int k = 0; k < 4; k++) {
        if (((mask >> k) & 1) == 0) continue;
        int left = (k & 1) == 0, low = (k & 2) == 0;
        float x0 = left ? 0.0f : hi, x1 = left ? r : 1.0f;
        float y0 = low ? 0.0f : hi, y1 = low ? r : 1.0f;
        float tx0 = -INFINITY, tx1 = INFINITY, ty0 = -INFINITY, ty1 = INFINITY;
        if (fabs(dx) < 1e-12f) { if (lx < x0 || lx > x1) continue; }
        else { float a = (x0 - lx) / dx, b = (x1 - lx) / dx; tx0 = fmin(a, b); tx1 = fmax(a, b); }
        if (fabs(dy) < 1e-12f) { if (ly < y0 || ly > y1) continue; }
        else { float a = (y0 - ly) / dy, b = (y1 - ly) / dy; ty0 = fmin(a, b); ty1 = fmax(a, b); }
        float t0 = fmax(fmax(tx0, ty0), 0.0f), t1 = fmin(tx1, ty1);
        if (t0 > t1 || t0 >= best) continue;

        float ccx = left ? r : hi, ccy = low ? r : hi;
        float qx = lx - ccx, qy = ly - ccy;
        if (t0 == 0 && qx * qx + qy * qy >= r * r) continue;
        float ex = qx + dx * t0, ey = qy + dy * t0;
        if (ex * ex + ey * ey >= r * r) {
            if (tx0 >= ty0) { out->nrmX = dx > 0 ? -1 : 1; out->nrmY = 0; }
            else { out->nrmX = 0; out->nrmY = dy > 0 ? -1 : 1; }
            out->curved = 0;
            best = t0;
        } else {
            float b = dx * qx + dy * qy, c = qx * qx + qy * qy - r * r;
            float t2 = -b + sqrt(b * b - c);
            if (t2 <= t1 && t2 < best) {
                out->nrmX = -(qx + dx * t2) / r;
                out->nrmY = -(qy + dy * t2) / r;
                out->curved = 1;
                best = t2;
            }
        }
    }
    return best;
}
static float rectEntryM(float lx, float ly, float dx, float dy, float x0, float x1, float y0, float y1, float best, NR* out) {
    float tx0 = -INFINITY, tx1 = INFINITY, ty0 = -INFINITY, ty1 = INFINITY;
    if (fabs(dx) < 1e-12f) { if (lx < x0 || lx > x1) return best; }
    else { float a = (x0 - lx) / dx, b = (x1 - lx) / dx; tx0 = fmin(a, b); tx1 = fmax(a, b); }
    if (fabs(dy) < 1e-12f) { if (ly < y0 || ly > y1) return best; }
    else { float a = (y0 - ly) / dy, b = (y1 - ly) / dy; ty0 = fmin(a, b); ty1 = fmax(a, b); }
    float t0 = fmax(tx0, ty0), t1 = fmin(tx1, ty1);
    if (t0 > t1 || t1 < 0 || t0 >= best) return best;
    if (tx0 >= ty0) { out->nrmX = dx > 0 ? -1 : 1; out->nrmY = 0; }
    else { out->nrmX = 0; out->nrmY = dy > 0 ? -1 : 1; }
    out->curved = 0;
    return fmax(t0, 0.0f);
}
static float diskEntryM(float lx, float ly, float dx, float dy, float ccx, float ccy, float r, float best, NR* out) {
    float px = lx - ccx, py = ly - ccy;
    float b = dx * px + dy * py;
    float disc = b * b - (px * px + py * py - r * r);
    if (disc < 0) return best;
    float sq = sqrt(disc);
    float t0 = -b - sq, t1 = -b + sq;
    if (t1 < 0 || t0 >= best) return best;
    float t = fmax(t0, 0.0f);
    out->nrmX = (px + dx * t) / r;
    out->nrmY = (py + dy * t) / r;
    out->curved = 1;
    return t;
}
static float roundedEntryM(float px, float py, float dx, float dy, int cx, int cy, int mask, NR* out) {
    float r = MO_R, hi = 1.0f - r;
    float lx = px - cx, ly = py - cy;
    float best = INFINITY;
    best = rectEntryM(lx, ly, dx, dy, 0.0f, 1.0f, r, hi, best, out);
    best = rectEntryM(lx, ly, dx, dy, r, hi, 0.0f, 1.0f, best, out);
    for (int k = 0; k < 4; k++) {
        int left = (k & 1) == 0, low = (k & 2) == 0;
        if (((mask >> k) & 1) != 0) best = diskEntryM(lx, ly, dx, dy, left ? r : hi, low ? r : hi, r, best, out);
        else best = rectEntryM(lx, ly, dx, dy, left ? 0.0f : hi, left ? r : 1.0f, low ? 0.0f : hi, low ? r : 1.0f, best, out);
    }
    return best;
}
static int walkColumnM(WORLD, Col* c, float px, float py, float ux, float uy) {
    c->wallDist = INFINITY; c->wallTexU = 0.0f; c->wallSide = 0; c->wallCurved = 0;
    c->wallNx = 0.0f; c->wallNy = 0.0f; c->finCount = 0;
    int mw = meta[1], mh = meta[2];
    float x = px, y = py, dist = 0.0f;
    int cx = (int)floor(x), cy = (int)floor(y);
    NR nr;
    if (cx >= 0 && cy >= 0 && cx < mw && cy < mh && cellT(WCALL, 0, cx, cy) == 0) {
        int fillet = (cornerM(WCALL, 0, cx, cy) >> 4) & 15;
        if (fillet != 0) {
            float sHit = concaveEntryM(px, py, ux, uy, cx, cy, fillet, &nr);
            if (sHit != INFINITY && sHit <= MO_MAXCOL) {
                int side = fabs(nr.nrmX) >= fabs(nr.nrmY) ? 0 : 1;
                c->wallDist = sHit;
                c->wallTexU = faceCoordM(side, px + ux * sHit, py + uy * sHit, ux, uy);
                c->wallSide = side;
                c->wallCurved = nr.curved;
                c->wallNx = nr.nrmX;
                c->wallNy = nr.nrmY;
                return 1;
            }
        }
    }
    while (dist < MO_MAXCOL) {
        float tX = ux > 0 ? (cx + 1 - x) / ux : (ux < 0 ? (x - cx) / -ux : INFINITY);
        float tY = uy > 0 ? (cy + 1 - y) / uy : (uy < 0 ? (y - cy) / -uy : INFINITY);
        int corner = fabs(tX - tY) <= 1e-5f;
        int alongX = tX < tY;
        float t = fmin(tX, tY);
        if (dist + t > MO_MAXCOL) return 1;
        x += ux * t; y += uy * t; dist += t;
        if (corner) { cx += ux > 0 ? 1 : -1; cy += uy > 0 ? 1 : -1; alongX = 1; }
        else if (alongX) cx += ux > 0 ? 1 : -1;
        else cy += uy > 0 ? 1 : -1;
        if (cx < 0 || cy < 0 || cx >= mw || cy >= mh) return 1;
        int type = cellT(WCALL, 0, cx, cy);
        if (type == 5) return 0;
        if (type == 0) {
            int fillet = (cornerM(WCALL, 0, cx, cy) >> 4) & 15;
            if (fillet != 0) {
                float sHit = concaveEntryM(px, py, ux, uy, cx, cy, fillet, &nr);
                if (sHit != INFINITY) {
                    if (sHit > MO_MAXCOL) return 1;
                    int side = fabs(nr.nrmX) >= fabs(nr.nrmY) ? 0 : 1;
                    c->wallDist = sHit;
                    c->wallTexU = faceCoordM(side, px + ux * sHit, py + uy * sHit, ux, uy);
                    c->wallSide = side;
                    c->wallCurved = nr.curved;
                    c->wallNx = nr.nrmX;
                    c->wallNy = nr.nrmY;
                    return 1;
                }
            }
            continue;
        }
        if (type != 1 && type != 2) continue;

        int mask = cornerM(WCALL, 0, cx, cy) & 15;
        float hitDist, qx = x, qy = y;
        int side;
        int curved = 0;
        float nx = 0.0f, ny = 0.0f;
        if (mask == 0 || !inRoundedCornerM(mask, x - cx, y - cy)) {
            hitDist = dist;
            side = alongX ? 0 : 1;
        } else {
            hitDist = roundedEntryM(px, py, ux, uy, cx, cy, mask, &nr);
            if (hitDist == INFINITY) continue;
            if (hitDist > MO_MAXCOL) return 1;
            curved = nr.curved;
            nx = nr.nrmX; ny = nr.nrmY;
            side = fabs(nx) >= fabs(ny) ? 0 : 1;
            qx = px + ux * hitDist;
            qy = py + uy * hitDist;
        }
        float u = faceCoordM(side, qx, qy, ux, uy);
        if (type == 1) {
            c->wallDist = hitDist; c->wallTexU = u; c->wallSide = side;
            c->wallCurved = curved; c->wallNx = nx; c->wallNy = ny;
            return 1;
        }
        if (c->finCount < 4) {
            int k = c->finCount++;
            c->finDist[k] = hitDist; c->finU[k] = u; c->finSide[k] = side;
            c->finCurved[k] = curved; c->finNx[k] = nx; c->finNy[k] = ny;
        }
    }
    return 1;
}
static void buildColumnM(WORLD, Par* p, Col* c, float fx) {
    float sx = (fx / p->W * 2.0f - 1.0f) * p->tanHalf;
    float hx = p->dirX - sx * p->dirY, hy = p->dirY + sx * p->dirX;
    float hl = sqrt(hx * hx + hy * hy);
    c->ux = hx / hl;
    c->uy = hy / hl;
    c->hl = hl;
    c->fallback = !walkColumnM(WCALL, c, p->playerX, p->playerY, c->ux, c->uy);
}
static void columnHitM(Par* p, Col* c, float v, TH* out) {
    out->hit = 0; out->isPlane = 0; out->type = 0; out->side = 0; out->layer = 0; out->curved = 0;
    out->distance = 0; out->x = 0; out->y = 0; out->z = 0; out->dirX = 0; out->dirY = 0; out->dirZ = 0;
    out->wallU = 0; out->wallV = 0; out->nrmX = 0; out->nrmY = 0;
    float hl = c->hl, ux = c->ux, uy = c->uy;
    float len = sqrt(hl * hl + v * v);
    float hf = hl / len;
    float rzUnit = v / len;
    float sigma = rzUnit / hf;
    float z0 = p->camZ;
    float planeD = sigma < -1e-12f ? -z0 / sigma : (sigma > 1e-12f ? (1.0f - z0) / sigma : INFINITY);
    float wallD = c->wallDist;
    float nearest = fmin(wallD, planeD);

    for (int k = 0; k < c->finCount; k++) {
        float d = c->finDist[k];
        if (d >= nearest) break;
        float z = z0 + sigma * d;
        if (z <= 0.5f) {
            out->hit = 1; out->isPlane = 0; out->distance = d / hf;
            out->x = p->playerX + ux * d; out->y = p->playerY + uy * d; out->z = clamp(z, 0.0f, 0.5f);
            out->dirX = ux * hf; out->dirY = uy * hf; out->dirZ = rzUnit;
            out->type = 2; out->side = c->finSide[k]; out->wallU = c->finU[k];
            out->wallV = clamp(z / 0.5f, 0.0f, 0.999999f);
            out->curved = c->finCurved[k]; out->nrmX = c->finNx[k]; out->nrmY = c->finNy[k];
            return;
        }
    }
    if (wallD < planeD) {
        float z = z0 + sigma * wallD;
        out->hit = 1; out->isPlane = 0; out->distance = wallD / hf;
        out->x = p->playerX + ux * wallD; out->y = p->playerY + uy * wallD; out->z = clamp(z, 0.0f, 1.0f);
        out->dirX = ux * hf; out->dirY = uy * hf; out->dirZ = rzUnit;
        out->type = 1; out->side = c->wallSide; out->wallU = c->wallTexU;
        out->wallV = clamp(z, 0.0f, 0.999999f);
        out->curved = c->wallCurved; out->nrmX = c->wallNx; out->nrmY = c->wallNy;
        return;
    }
    if (planeD < INFINITY) {
        int floorP = sigma < 0;
        float pz = floorP ? 0.0f : 1.0f;
        out->hit = 1; out->isPlane = 1; out->distance = planeD / hf;
        out->x = p->playerX + ux * planeD; out->y = p->playerY + uy * planeD; out->z = pz;
        out->dirX = ux * hf; out->dirY = uy * hf; out->dirZ = rzUnit;
        out->type = floorP ? -1 : 1; out->z = floorP ? 0.0f : 1.0f; out->side = 0;
    }
}

static void warpPortalM(__global const int* P, float hitX, float hitY, float dirX, float dirY, float* outX, float* outY, float* outDX, float* outDY);
static void traceRayM(WORLD, float ox, float oy, float oz, float rx, float ry, float rz, int startL, int floors, int maxSteps, TH* result) {
    result->hit = 0; result->isPlane = 0; result->type = 0; result->side = 0; result->curved = 0;
    result->distance = 0; result->x = 0; result->y = 0; result->z = 0;
    result->dirX = 0; result->dirY = 0; result->dirZ = 0; result->wallU = 0; result->wallV = 0;
    result->nrmX = 0; result->nrmY = 0; result->isPlane = 0;
    int sw = meta[8 * startL + 1], sh = meta[8 * startL + 2];
    float totalDistance = 0.0f;
    int hops = 0, steps = 0;
    float wallT = INFINITY;
    int wallType = 0, wallSide = 0, wallCurved = 0;
    float wallNx = 0.0f, wallNy = 0.0f;
    int L = startL;
    NR nr;
    int brk = 0;

    while (!brk) {
        float horizontal = sqrt(rx * rx + ry * ry);
        if (horizontal <= 1e-9f) break;
        float planeHere = rz < -1e-9f ? -oz / rz : (rz > 1e-9f ? (1.0f - oz) / rz : INFINITY);
        float dx = rx / horizontal, dy = ry / horizontal;
        int mapX = (int)floor(ox), mapY = (int)floor(oy);
        float deltaX = fabs(1.0f / dx), deltaY = fabs(1.0f / dy);
        float sideX = (dx < 0 ? ox - mapX : mapX + 1.0f - ox) * deltaX;
        float sideY = (dy < 0 ? oy - mapY : mapY + 1.0f - oy) * deltaY;
        int mw = meta[8 * L + 1];

        if (mapX >= 0 && mapY >= 0 && mapX < sw && mapY < sh && cellT(WCALL, L, mapX, mapY) == 0) {
            int fillet = (cornerM(WCALL, L, mapX, mapY) >> 4) & 15;
            if (fillet != 0) {
                float s = concaveEntryM(ox, oy, dx, dy, mapX, mapY, fillet, &nr);
                if (s != INFINITY) {
                    float t = s / horizontal;
                    float candidateZ = oz + rz * t;
                    if (t < planeHere && candidateZ >= 0.0f && candidateZ <= 1.0f) {
                        wallT = t; wallType = 1;
                        wallCurved = nr.curved; wallNx = nr.nrmX; wallNy = nr.nrmY;
                        wallSide = fabs(wallNx) >= fabs(wallNy) ? 0 : 1;
                        brk = 1;
                        break;
                    }
                }
            }
        }

        int restart = 0;
        while (mapX >= 0 && mapY >= 0 && mapX < sw && mapY < sh) {
            float horizontalT;
            int side;
            if (sideX < sideY) { horizontalT = sideX; sideX += deltaX; mapX += dx < 0 ? -1 : 1; side = 0; }
            else { horizontalT = sideY; sideY += deltaY; mapY += dy < 0 ? -1 : 1; side = 1; }
            if (mapX < 0 || mapY < 0 || mapX >= sw || mapY >= sh) break;

            float candidateT = horizontalT / horizontal;
            if (candidateT >= planeHere || ++steps > maxSteps) { brk = 1; break; }

            int type = cellT(WCALL, L, mapX, mapY);
            if (type == 5 && hops < 8) {
                int pidx = grid[meta[8 * L] + mapY * mw + mapX];
                if (pidx >= 0) {
                    __global const int* P = portals + 9 * pidx;
                    if (rx * P[2] + ry * P[3] > 1e-9f) {
                        float candidateZ = oz + rz * candidateT;
                        if (candidateZ >= 0.0f && candidateZ <= 1.0f) {
                            float hx = ox + rx * candidateT, hy = oy + ry * candidateT;
                            totalDistance += candidateT;
                            float nx, ny, ndx, ndy;
                            warpPortalM(P, hx, hy, rx, ry, &nx, &ny, &ndx, &ndy);
                            ox = nx; oy = ny; rx = ndx; ry = ndy;
                            int lf = P[8];
                            if (floors > 0 && lf >= 0 && lf < floors) L = 1 + lf;
                            oz = candidateZ;
                            hops++;
                            restart = 1;
                            break;
                        }
                    }
                }
            }
            if (restart) break;

            if (type != 1 && type != 2) {
                int fillet = type == 0 ? (cornerM(WCALL, L, mapX, mapY) >> 4) & 15 : 0;
                if (fillet != 0) {
                    float s = concaveEntryM(ox, oy, dx, dy, mapX, mapY, fillet, &nr);
                    if (s != INFINITY) {
                        float t = s / horizontal;
                        if (t >= planeHere) { brk = 1; break; }
                        float candidateZ = oz + rz * t;
                        if (candidateZ >= 0.0f && candidateZ <= 1.0f) {
                            wallT = t; wallType = 1;
                            wallCurved = nr.curved; wallNx = nr.nrmX; wallNy = nr.nrmY;
                            wallSide = fabs(wallNx) >= fabs(wallNy) ? 0 : 1;
                            brk = 1;
                            break;
                        }
                    }
                }
                continue;
            }

            int mask = cornerM(WCALL, L, mapX, mapY) & 15;
            float wallH = type == 2 ? 0.5f : 1.0f;
            if (mask == 0 || !inRoundedCornerM(mask, ox + dx * horizontalT - mapX, oy + dy * horizontalT - mapY)) {
                float candidateZ = oz + rz * candidateT;
                if (candidateZ >= 0.0f && candidateZ <= wallH) {
                    wallT = candidateT; wallType = type; wallSide = side;
                    brk = 1;
                    break;
                }
                continue;
            }
            float s = roundedEntryM(ox, oy, dx, dy, mapX, mapY, mask, &nr);
            if (s == INFINITY) continue;
            float t = s / horizontal;
            if (t >= planeHere) { brk = 1; break; }
            float candidateZ = oz + rz * t;
            if (candidateZ < 0.0f || candidateZ > wallH) continue;
            wallT = t; wallType = type;
            wallCurved = nr.curved; wallNx = nr.nrmX; wallNy = nr.nrmY;
            wallSide = fabs(wallNx) >= fabs(wallNy) ? 0 : 1;
            brk = 1;
            break;
        }
        if (brk) break;
        if (restart) continue;
        break;
    }

    result->layer = L;
    float planeT = INFINITY;
    int planeType = 0;
    if (rz < -1e-9f) { planeT = -oz / rz; planeType = -1; }
    else if (rz > 1e-9f) { planeT = (1.0f - oz) / rz; planeType = 1; }

    if (wallT < planeT && isfinite(wallT)) {
        float wallHeight = wallType == 2 ? 0.5f : 1.0f;
        result->hit = 1;
        result->distance = totalDistance + wallT;
        result->x = ox + rx * wallT;
        result->y = oy + ry * wallT;
        result->z = clamp(oz + rz * wallT, 0.0f, wallHeight);
        result->type = wallType;
        result->side = wallSide;
        result->dirX = rx; result->dirY = ry; result->dirZ = rz;
        result->curved = wallCurved; result->nrmX = wallNx; result->nrmY = wallNy;
        result->wallU = faceCoordM(wallSide, result->x, result->y, rx, ry);
        result->wallV = clamp(result->z / wallHeight, 0.0f, 0.999999f);
    }
    else if (isfinite(planeT) && planeT > 0) {
        result->hit = 1;
        result->distance = totalDistance + planeT;
        result->x = ox + rx * planeT;
        result->y = oy + ry * planeT;
        result->z = planeType < 0 ? 0 : 1;
        result->type = planeType;
        result->side = 0;
        result->dirX = rx; result->dirY = ry; result->dirZ = rz;
        result->isPlane = 1;
        result->curved = 0; result->nrmX = 0; result->nrmY = 0;
    }
}

static void warpPortalM(__global const int* P, float hitX, float hitY, float dirX, float dirY, float* outX, float* outY, float* outDX, float* outDY) {
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
static void cameraTraceM(WORLD, Par* p, float fx, float fy, TH* out) {
    float sx = (fx / p->W * 2.0f - 1.0f) * p->tanHalf;
    float sy = (1.0f - fy / p->H * 2.0f) * p->verticalTan;
    float rx = p->dirX * p->cosP - p->dirY * sx - p->dirX * p->sinP * sy;
    float ry = p->dirY * p->cosP + p->dirX * sx - p->dirY * p->sinP * sy;
    float rz = p->sinP + p->cosP * sy;
    float inv = 1.0f / sqrt(rx * rx + ry * ry + rz * rz);
    traceRayM(WCALL, p->playerX, p->playerY, p->camZ, rx * inv, ry * inv, rz * inv, 0, p->floors, MO_PRIM_STEPS, out);
}
static void storeCol(__global float* cb, int idx, Col* c) {
    int b = idx * COLF;
    cb[b] = c->ux; cb[b + 1] = c->uy; cb[b + 2] = c->hl; cb[b + 3] = c->wallDist; cb[b + 4] = c->wallTexU;
    cb[b + 5] = c->wallNx; cb[b + 6] = c->wallNy;
    cb[b + 7] = c->fallback; cb[b + 8] = c->wallSide; cb[b + 9] = c->wallCurved; cb[b + 10] = c->finCount;
    for (int k = 0; k < 4; k++) {
        cb[b + 11 + k] = c->finDist[k]; cb[b + 15 + k] = c->finU[k];
        cb[b + 19 + k] = c->finNx[k]; cb[b + 23 + k] = c->finNy[k];
        cb[b + 27 + k] = c->finSide[k]; cb[b + 31 + k] = c->finCurved[k];
    }
}
static void loadCol(__global const float* cb, int idx, Col* c) {
    int b = idx * COLF;
    c->ux = cb[b]; c->uy = cb[b + 1]; c->hl = cb[b + 2]; c->wallDist = cb[b + 3]; c->wallTexU = cb[b + 4];
    c->wallNx = cb[b + 5]; c->wallNy = cb[b + 6];
    c->fallback = (int)cb[b + 7]; c->wallSide = (int)cb[b + 8]; c->wallCurved = (int)cb[b + 9]; c->finCount = (int)cb[b + 10];
    for (int k = 0; k < 4; k++) {
        c->finDist[k] = cb[b + 11 + k]; c->finU[k] = cb[b + 15 + k];
        c->finNx[k] = cb[b + 19 + k]; c->finNy[k] = cb[b + 23 + k];
        c->finSide[k] = (int)cb[b + 27 + k]; c->finCurved[k] = (int)cb[b + 31 + k];
    }
}
__kernel void mo_columns(WORLD, __global float* colOut, __global const float* dp, __global const int* ip) {
    Par p = makePar(dp, ip);
    int t = get_global_id(0);
    if (t >= p.W * (1 + p.edgeGrid)) return;
    float fx;
    if (t < p.W) fx = t + 0.5f;
    else {
        int u = t - p.W;
        int x = u / p.edgeGrid, k = u % p.edgeGrid;
        fx = x + (k + 0.5f) / p.edgeGrid;
    }
    Col c;
    buildColumnM(WCALL, &p, &c, fx);
    storeCol(colOut, t, &c);
}
static void primaryM(WORLD, __global const float* colBuf, Par* p, int x, int y, TH* out) {
    if (p->columnPath) {
        Col c;
        loadCol(colBuf, x, &c);
        if (!c.fallback) {
            float v = (1.0f - (y + 0.5f) / p->H * 2.0f) * p->verticalTan;
            columnHitM(p, &c, v, out);
            return;
        }
    }
    cameraTraceM(WCALL, p, x + 0.5f, y + 0.5f, out);
}
static void subSampleM(WORLD, __global const float* colBuf, Par* p, int x, int y, int j, int k, TH* out) {
    if (p->columnPath) {
        Col c;
        loadCol(colBuf, p->W + x * p->edgeGrid + k, &c);
        if (!c.fallback) {
            float fy = y + (j + 0.5f) / p->edgeGrid;
            columnHitM(p, &c, (1.0f - fy / p->H * 2.0f) * p->verticalTan, out);
            return;
        }
    }
    cameraTraceM(WCALL, p, x + (k + 0.5f) / p->edgeGrid, y + (j + 0.5f) / p->edgeGrid, out);
}
static int litM(WORLD, TEXARGS, Par* p, TH* h, float pathBefore, LO* lo) {
    float lx = -h->dirX, ly = -h->dirY, lz = -h->dirZ + 0.18f;
    float llen = sqrt(lx * lx + ly * ly + lz * lz);
    if (llen > 1e-9f) { lx /= llen; ly /= llen; lz /= llen; }
    int L = h->layer;
    int base;
    float nx, ny, nz, ao, specStrength, reflectivity;
    int shininess;
    int emissive = 0;
    if (h->isPlane) {
        int floorP = h->type == -1;
        base = sampleNearestM(floorP ? floorTex : ceilTex, floorP ? flw : clw, floorP ? flh : clh, h->x, h->y, floorP ? MO_FLOOR_BASE : MO_CEIL_BASE);
        if (floorP && p->tinted) base = tintStairsM(maps, meta, base, h->x, h->y, L);
        nx = 0; ny = 0; nz = floorP ? 1 : -1;
        ao = occlusionM(WCALL, L, p->aoN, h->x, h->y, h->z, nx, ny, nz);
        shininess = floorP ? 30 : 16;
        specStrength = floorP ? 0.16f : 0.10f;
        reflectivity = floorP ? 0.12f : 0.06f;
    } else {
        int finish = h->type == 2;
        int tw = finish ? fw : ww, th = finish ? fh : wh;
        __global const int* tex = finish ? finishTex : wallTex;
        if (tw <= 0 || th <= 0) {
            base = finish ? MO_FINISH_BASE : MO_WALL_BASE;
        } else {
            int tx = min((int)(h->wallU * tw), tw - 1);
            int ty = min((int)((1.0f - h->wallV) * th), th - 1);
            base = tex[ty * tw + max(0, tx)];
        }
        if (h->curved) { nx = h->nrmX; ny = h->nrmY; }
        else {
            nx = h->side == 0 ? (h->dirX > 0 ? -1 : 1) : 0;
            ny = h->side == 1 ? (h->dirY > 0 ? -1 : 1) : 0;
        }
        nz = 0;
        emissive = finish;
        ao = finish ? 1.0f : wallOcclusionM(h->z, h->type == 2 ? 0.5f : 1.0f) * occlusionM(WCALL, L, p->aoN, h->x, h->y, h->z, nx, ny, nz);
        shininess = 24;
        specStrength = 0.10f;
        reflectivity = finish ? 0.08f : 0.24f;
    }

    float dist = pathBefore + h->distance;
    float att = 1.0f / (1.0f + dist * dist * (1.0f / 9.0f));
    float ndl = fmax(0.0f, nx * lx + ny * ly + nz * lz);
    float ndf = fmax(0.0f, nx * 0.46f + ny * 0.36f + nz * 0.85f);
    float flash = 0.95f * att * ndl;

    float glint = 0.0f;
    if (ndl > 0) {
        float hx = lx - h->dirX, hy = ly - h->dirY, hz = lz - h->dirZ;
        float hlen = sqrt(hx * hx + hy * hy + hz * hz);
        if (hlen > 1e-9f) glint = specStrength * att * 0.75f * glintPowM(fmax(0.0f, (nx * hx + ny * hy + nz * hz) / hlen), shininess);
    }

    float fillMix = 0.6f + 0.4f * ao;
    float lightR = 0.10f * ao + 0.30f * ndf * ao + 1.0f * flash * fillMix + 1.0f * glint;
    float lightG = 0.115f * ao + 0.28f * ndf * ao + 0.88f * flash * fillMix + 0.88f * glint;
    float lightB = 0.17f * ao + 0.25f * ndf * ao + 0.70f * flash * fillMix + 0.70f * glint;
    if (emissive) { lightR = fmax(lightR, 0.7f); lightG = fmax(lightG, 0.7f); lightB = fmax(lightB, 0.7f); }

    int br = (base >> 16) & 255, bg = (base >> 8) & 255, bb = base & 255;
    int lit = (toneToByteM(br / 255.0f * lightR) << 16) | (toneToByteM(bg / 255.0f * lightG) << 8) | toneToByteM(bb / 255.0f * lightB);

    float level = (lightR + lightG + lightB) / 3.0f;
    reflectivity *= 0.75f * clamp(level, 0.15f, 1.0f);
    lo->reflectivity = reflectivity;
    lo->nx = nx; lo->ny = ny; lo->nz = nz; lo->dist = dist;
    return lit;
}
static int shadeM(WORLD, TEXARGS, Par* p, TH* h, float pathBefore, int depth, int steps) {
    if (!h->hit) return MO_CEIL_BASE;
    LO lo;
    int lit = litM(WCALL, TCALL, p, h, pathBefore, &lo);
    if (depth <= 0 || lo.reflectivity < 0.012f) return lit;

    float dot = h->dirX * lo.nx + h->dirY * lo.ny + h->dirZ * lo.nz;
    float rrx = h->dirX - 2.0f * dot * lo.nx, rry = h->dirY - 2.0f * dot * lo.ny, rrz = h->dirZ - 2.0f * dot * lo.nz;
    float rlen = sqrt(rrx * rrx + rry * rry + rrz * rrz);
    if (rlen < 1e-9f) return lit;
    rrx /= rlen; rry /= rlen; rrz /= rlen;

    TH refl;
    traceRayM(WCALL, h->x + lo.nx * 0.004f, h->y + lo.ny * 0.004f, h->z + lo.nz * 0.004f, rrx, rry, rrz, h->layer, p->floors, steps, &refl);
    if (!refl.hit) return lit;
    LO lo2;
    int reflected = litM(WCALL, TCALL, p, &refl, lo.dist, &lo2);
    return mixM(lit, reflected, lo.reflectivity);
}
static int kindOfM(TH* h) {
    if (!h->hit) return 0;
    if (h->isPlane) return h->type == -1 ? 1 : 2;
    return h->type == 2 ? 4 : 3;
}
__kernel void mo_primary(WORLD, __global const float* colBuf, __global int* kindOut, __global float* distOut, __global const float* dp, __global const int* ip) {
    Par p = makePar(dp, ip);
    int x = get_global_id(0), y = get_global_id(1);
    if (x >= p.W || y >= p.H) return;
    TH h;
    primaryM(WCALL, colBuf, &p, x, y, &h);
    int i = y * p.W + x;
    kindOut[i] = kindOfM(&h);
    distOut[i] = h.hit ? h.distance : INFINITY;
}

__kernel void mo_shade(WORLD, __global const int* wallTex, __global const int* finishTex, __global const int* floorTex, __global const int* ceilTex,
                       __global const float* colBuf, __global const int* kindIn, __global const float* distIn,__global int* out,  __global const float* dp, __global const int* ip) {
    Par p = makePar(dp, ip);
    int ww = ip[4], wh = ip[5], fw = ip[6], fh = ip[7], flw = ip[8], flh = ip[9], clw = ip[10], clh = ip[11];
    int x = get_global_id(0), y = get_global_id(1);
    if (x >= p.W || y >= p.H) return;
    int i = y * p.W + x;

    int edge = 0;
    if (p.edgeGrid > 1) {
        int kind = kindIn[i];
        float d = distIn[i];
        for (int n = 0; n < 4 && !edge; n++) {
            int nx = x + (n == 0 ? -1 : (n == 1 ? 1 : 0));
            int ny = y + (n == 2 ? -1 : (n == 3 ? 1 : 0));
            if (nx < 0 || ny < 0 || nx >= p.W || ny >= p.H) continue;
            int j = ny * p.W + nx;
            if (kindIn[j] != kind) edge = 1;
            else if (kind != 0 && fabs(distIn[j] - d) > 0.25f * fmax(d, distIn[j])) edge = 1;
        }
    }

    if (edge) {
        int n = p.edgeGrid * p.edgeGrid;
        int r = 0, g = 0, b = 0;
        for (int j = 0; j < p.edgeGrid; j++) {
            for (int k = 0; k < p.edgeGrid; k++) {
                TH sub;
                subSampleM(WCALL, colBuf, &p, x, y, j, k, &sub);
                int c = shadeM(WCALL, TCALL, &p, &sub, 0.0f, p.reflDepth, p.reflSteps);
                r += (c >> 16) & 255; g += (c >> 8) & 255; b += c & 255;
            }
        }
        out[i] = ((r / n) << 16) | ((g / n) << 8) | (b / n);
    } else {
        TH h;
        primaryM(WCALL, colBuf, &p, x, y, &h);
        out[i] = shadeM(WCALL, TCALL, &p, &h, 0.0f, p.reflDepth, p.reflSteps);
    }
}