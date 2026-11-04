#define R3D_TILE 16
#define R3D_STRIDE 26
#define R3D_AP_STRIDE 12
#define R3D_MAX_AP 32
#define R3D_MAX_DEPTH 3
#define R3D_STACK 4
#define R3D_CEIL_BASE 0x816E1E
#define R3D_WALL_BASE 0xECD485
#define R3D_FINISH_BASE 0x33FF66
#define R3D_FLOOR_BASE 0xD3AF63
#define R3D_AMBIENT 0.22f
#define R3D_TORCH_RANGE 3.0f
#define R3D_FOG 0x120C1E
#define R3D_FOG_START 2.0f
#define R3D_FOG_RANGE 7.0f
#define R3D_FOG_MAX 0.8f
#define R3D_WARM_RED 0.12f
#define R3D_WARM_BLUE 0.18f
#define R3D_APERTURE_EPS 1e-3f

static int r3dTint(int color, int tint, float amount) {
    int r = (color >> 16) & 255, g = (color >> 8) & 255, b = color & 255;
    int tr = (tint >> 16) & 255, tg = (tint >> 8) & 255, tb = tint & 255;
    r = (int)(r * (1.0f - amount) + tr * amount);
    g = (int)(g * (1.0f - amount) + tg * amount);
    b = (int)(b * (1.0f - amount) + tb * amount);
    return (r << 16) | (g << 8) | b;
}

static int r3dSample(__global const int* tex, int tw, int th, float s, float t, int wall, int base) {
    if (tw <= 0 || th <= 0) return base;
    float fs = s - floor(s), ft = t - floor(t);
    int x = min((int)(fs * (float)tw), tw - 1);
    int y = wall ? min((int)((1.0f - ft) * (float)th), th - 1) : min((int)(ft * (float)th), th - 1);
    return tex[y * tw + x];
}

static int r3dLight(int color, float shade, float torch, float z) {
    float k = (R3D_AMBIENT + torch) * shade;
    float r = ((color >> 16) & 255) * k * (1.0f + R3D_WARM_RED * torch);
    float g = ((color >> 8) & 255) * k;
    float b = (color & 255) * k * (1.0f - R3D_WARM_BLUE * torch);
    float fog = clamp((z - R3D_FOG_START) / R3D_FOG_RANGE, 0.0f, 1.0f) * R3D_FOG_MAX;
    r = fmin(255.0f, r * (1.0f - fog) + ((R3D_FOG >> 16) & 255) * fog);
    g = fmin(255.0f, g * (1.0f - fog) + ((R3D_FOG >> 8) & 255) * fog);
    b = fmin(255.0f, b * (1.0f - fog) + (R3D_FOG & 255) * fog);
    return ((int)r << 16) | ((int)g << 8) | (int)b;
}

static float r3dWarp(float flat, float r) { return r * sinh(flat / r); }
static float r3dUnwarp(float seen, float r) { return r * asinh(seen / r); }

static float r3dCellHeight(__global const int* cells, __global const int* lmeta, int layer, int x, int y) {
    int off = lmeta[layer * 3], w = lmeta[layer * 3 + 1], h = lmeta[layer * 3 + 2];
    if (x < 0 || y < 0 || x >= w || y >= h) return 1.0f;
    int type = cells[off + y * w + x];
    return type == 1 ? 1.0f : (type == 2 ? 0.5f : 0.0f);
}

static int r3dShadowed(__global const int* cells, __global const int* lmeta, int layer,
                       float fx, float fy, float fz, float lx, float ly, float lz) {
    if (r3dCellHeight(cells, lmeta, layer, (int)floor(lx), (int)floor(ly)) > 0.0f) return 0;
    float dx = lx - fx, dy = ly - fy;
    if (fabs(dx) + fabs(dy) < 1e-9f) return 0;
    float sx = fx + dx * 1e-4f, sy = fy + dy * 1e-4f;
    int cx = (int)floor(sx), cy = (int)floor(sy);
    int stepX = dx > 0.0f ? 1 : -1, stepY = dy > 0.0f ? 1 : -1;
    float tdx = fabs(dx) > 1e-12f ? 1.0f / fabs(dx) : 1e30f;
    float tdy = fabs(dy) > 1e-12f ? 1.0f / fabs(dy) : 1e30f;
    float tmx = dx > 0.0f ? (cx + 1 - fx) / dx : (dx < 0.0f ? (cx - fx) / dx : 1e30f);
    float tmy = dy > 0.0f ? (cy + 1 - fy) / dy : (dy < 0.0f ? (cy - fy) / dy : 1e30f);
    float t0 = 0.0f;
    for (int guard = 0; guard < 4096; guard++) {
        float t1 = fmin(fmin(tmx, tmy), 1.0f);
        float za = fz + t0 * (lz - fz), zb = fz + t1 * (lz - fz);
        float hgt = r3dCellHeight(cells, lmeta, layer, cx, cy);
        if (hgt > 0.0f && fmin(za, zb) < hgt) return 1;
        if (t1 >= 1.0f) return 0;
        if (tmx < tmy) { t0 = tmx; tmx += tdx; cx += stepX; }
        else { t0 = tmy; tmy += tdy; cy += stepY; }
    }
    return 0;
}

static float2 r3dToLinked(__global const float* ap, float x, float y) {
    float px = x - ap[0], py = y - ap[1];
    return (float2)(ap[2] + ap[4] * px - ap[5] * py, ap[3] + ap[5] * px + ap[4] * py);
}

static int r3dThroughChain(__global const float* apv, int ta, __global const int* cells, __global const int* lmeta,
                           float x, float y, float z, float tx, float ty, float tz)
{
    int chain[R3D_MAX_DEPTH];
    int n = 0;
    int id = ta;
    while (id >= 0 && n < R3D_MAX_DEPTH) {
        chain[n++] = id;
        id = (int)apv[id * R3D_AP_STRIDE + 11];
    }
    for (int i = 0, j = n - 1; i < j; i++, j--) { int t = chain[i]; chain[i] = chain[j]; chain[j] = t; }

    float dx = x - tx, dy = y - ty, dz = z - tz;
    float hx[R3D_MAX_DEPTH], hy[R3D_MAX_DEPTH], hz[R3D_MAX_DEPTH];
    float sPrev = 0.0f;
    for (int i = 0; i < n; i++) {
        __global const float* ap = apv + chain[i] * R3D_AP_STRIDE;
        float nd = dx * ap[6] + dy * ap[7];
        if (fabs(nd) < 1e-9f) return 0;
        float s = ((ap[0] - tx) * ap[6] + (ap[1] - ty) * ap[7]) / nd;
        if (s <= sPrev || s >= 1.0f) return 0;
        float px = tx + s * dx, py = ty + s * dy, pz = tz + s * dz;
        float along = (px - ap[0]) * ap[8] + (py - ap[1]) * ap[9];
        if (along < 0.0f || along > 1.0f || pz < 0.0f || pz > 1.0f) return 0;
        hx[i] = px;
        hy[i] = py;
        hz[i] = pz;
        sPrev = s;
    }
    if (r3dShadowed(cells, lmeta, 0, tx, ty, tz, hx[0], hy[0], hz[0])) return 0;
    for (int i = 0; i < n; i++) {
        __global const float* ap = apv + chain[i] * R3D_AP_STRIDE;
        float fx = x, fy = y, fz = z;
        if (i + 1 < n) { fx = hx[i + 1]; fy = hy[i + 1]; fz = hz[i + 1]; }
        float2 p1 = r3dToLinked(ap, fx, fy);
        float2 p0 = r3dToLinked(ap, hx[i], hy[i]);
        int layer = (int)ap[10];
        if (r3dShadowed(cells, lmeta, layer, p1.x, p1.y, fz, p0.x, p0.y, hz[i])) return 0;
    }
    return 1;
}

__kernel void r3d_render(__global const float* tris, __global const int* tileStart, __global const int* tileList,
                         __global const int* wallTex, __global const int* finishTex, __global const int* floorTex, __global const int* ceilTex,
                         __global const int* ip, __global const float* dp, __global const float* apv,
                         __global const int* cells, __global const int* lmeta, __global int* out)
{
    int x = get_global_id(0), y = get_global_id(1);
    int W = ip[0], H = ip[1], tilesX = ip[2], sky = ip[3], apCount = ip[12];
    if (x >= W || y >= H) return;

    float eyeX = dp[9], eyeY = dp[10], eyeZ = dp[11];
    float focal = dp[13];
    float a = ((float)x + 0.5f - (float)W * 0.5f) / focal;
    float v = ((float)H * 0.5f - ((float)y + 0.5f)) / focal;
    float dxr = dp[0] + a * dp[3] + v * dp[6];
    float dyr = dp[1] + a * dp[4] + v * dp[7];
    float dzr = dp[2] + a * dp[5] + v * dp[8];

    int hyp = dp[17] > 0.5f;
    float curv = dp[18];

    float cT[R3D_MAX_AP];
    int cI[R3D_MAX_AP];
    int nc = 0;
    for (int i = 0; i < apCount; i++) {
        __global const float* ap = apv + i * R3D_AP_STRIDE;
        float dn = dxr * ap[6] + dyr * ap[7];
        if (dn <= 1e-12f) continue;
        float t = ((ap[0] - eyeX) * ap[6] + (ap[1] - eyeY) * ap[7]) / dn;
        if (t <= 0.0f) continue;
        float X = eyeX + t * dxr, Y = eyeY + t * dyr, Z = eyeZ + t * dzr;
        float along = (X - ap[0]) * ap[8] + (Y - ap[1]) * ap[9];
        if (along < 0.0f || along > 1.0f || Z < 0.0f || Z > 1.0f) continue;
        int j = nc++;
        while (j > 0 && cT[j - 1] > t) { cT[j] = cT[j - 1]; cI[j] = cI[j - 1]; j--; }
        cT[j] = t;
        cI[j] = i;
    }
    float sT[R3D_STACK];
    int sI[R3D_STACK];
    int sN = 0;
    for (int k = 0; k < nc; k++) {
        int top = sN > 0 ? sI[sN - 1] : -1;
        if ((int)apv[cI[k] * R3D_AP_STRIDE + 11] != top || sN >= R3D_STACK) continue;
        sI[sN] = cI[k];
        sT[sN] = hyp ? r3dWarp(cT[k], curv) : cT[k];
        sN++;
    }

    int tile = (y / R3D_TILE) * tilesX + (x / R3D_TILE);
    float cx = (float)x + 0.5f, cy = (float)y + 0.5f;

    float best = 0.0f, bestS = 0.0f, bestT = 0.0f;
    int bestTri = -1;
    for (int k = tileStart[tile]; k < tileStart[tile + 1]; k++) {
        int tri = tileList[k];
        int o = tri * R3D_STRIDE;
        float w0 = tris[o] * cx + tris[o + 1] * cy + tris[o + 2];
        float w1 = tris[o + 3] * cx + tris[o + 4] * cy + tris[o + 5];
        float w2 = 1.0f - w0 - w1;
        if (w0 < -1e-6f || w1 < -1e-6f || w2 < -1e-6f) continue;
        float invZ = w0 * tris[o + 6] + w1 * tris[o + 7] + w2 * tris[o + 8];
        if (invZ <= best) continue;
        float z = 1.0f / invZ;
        int ta = (int)tris[o + 24];
        int top = -1;
        for (int s = 0; s < sN; s++) {
            if (sT[s] - R3D_APERTURE_EPS >= z) break;
            top = sI[s];
        }
        if (top != ta) continue;
        best = invZ;
        bestTri = tri;
        bestS = (w0 * tris[o + 9] + w1 * tris[o + 10] + w2 * tris[o + 11]) * z;
        bestT = (w0 * tris[o + 12] + w1 * tris[o + 13] + w2 * tris[o + 14]) * z;
    }

    if (bestTri < 0) {
        if (!sky) { out[y * W + x] = R3D_CEIL_BASE; return; }
        out[y * W + x] = skyDirGpu(dxr, dyr, dzr, dp[12]);
        return;
    }

    int o = bestTri * R3D_STRIDE;
    int slot = (int)tris[o + 20];
    int wall = tris[o + 21] != 0.0f;
    float shade = tris[o + 22];
    int tint = (int)tris[o + 23];
    int ta = (int)tris[o + 24];

    int color;
    if (slot == 0) color = r3dSample(wallTex, ip[4], ip[5], bestS, bestT, wall, R3D_WALL_BASE);
    else if (slot == 1) color = r3dSample(finishTex, ip[6], ip[7], bestS, bestT, wall, R3D_FINISH_BASE);
    else if (slot == 2) color = r3dSample(floorTex, ip[8], ip[9], bestS, bestT, wall, R3D_FLOOR_BASE);
    else color = r3dSample(ceilTex, ip[10], ip[11], bestS, bestT, wall, R3D_CEIL_BASE);
    if (tint != 0) color = r3dTint(color, tint, 0.45f);

    float z = 1.0f / best;
    float flat = hyp ? r3dUnwarp(z, curv) : z;
    float Px = eyeX + flat * dxr, Py = eyeY + flat * dyr, Pz = eyeZ + flat * dzr;
    int lit;
    if (ta < 0) lit = r3dShadowed(cells, lmeta, 0, Px, Py, Pz, dp[14], dp[15], dp[16]) == 0;
    else lit = r3dThroughChain(apv, ta, cells, lmeta, Px, Py, Pz, dp[14], dp[15], dp[16]);
    float ddx = Px - dp[14], ddy = Py - dp[15], ddz = Pz - dp[16];
    float dist = sqrt(ddx * ddx + ddy * ddy + ddz * ddz);
    float torch = lit ? clamp(1.0f - dist / R3D_TORCH_RANGE, 0.0f, 1.0f) : 0.0f;
    out[y * W + x] = r3dLight(color, shade, torch, z);
}
