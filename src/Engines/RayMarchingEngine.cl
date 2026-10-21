#define RM_HIT 0.001f
#define RM_STEP_SCALE 0.8f
#define RM_MAXD 48.0f
#define RM_MIN_STEP 0.001f
#define RM_BIG 3.0e38f
#define RM_MAX_SEG 10
#define RM_MAX_FIN 4
#define RM_PORTAL 5
#define RM_WALL_BASE 0xECD485
#define RM_CEIL_BASE 0x816E1E
#define RM_FLOOR_BASE 0xD3AF63
#define RM_FINISH_BASE 0x33FF66
#define WORLD __global const int* maps, __global const int* grid, __global const int* meta, __global const int* portals, __global const float* fieldWall, __global const float* fieldFinish
#define WORLDCALL maps, grid, meta, portals, fieldWall, fieldFinish
#define TEXARGS __global const int* wallTex, int ww, int wh, __global const int* finishTex, int fw, int fh, __global const int* floorTex, int flw, int flh, __global const int* ceilTex, int clw, int clh
#define TEXCALL wallTex, ww, wh, finishTex, fw, fh, floorTex, flw, flh, ceilTex, clw, clh

typedef struct {
    float x, y, z, dx, dy, dz, dist, hitU, hitFrac, hitX, hitY, hitDist;
    int layer, hops, hitKind, hitType, hitSide, exhausted;
} RS;
typedef struct {
    int segCount, finCount, wallSide, exhausted, segLayer[RM_MAX_SEG], finSide[RM_MAX_FIN];
    float segStartDist[RM_MAX_SEG], segStartX[RM_MAX_SEG], segStartY[RM_MAX_SEG], segDirX[RM_MAX_SEG], segDirY[RM_MAX_SEG];
    float finDist[RM_MAX_FIN], finU[RM_MAX_FIN];
    float wallDist, wallU, exitD;
} Col;
typedef struct {
    int hit, isPlane, type, side, layer;
    float distance, x, y, z, wallU, wallV;
} TR;
static int applyBrightnessRM(int color, float b) {
    int r = (int)fmin(255.0f, ((color >> 16) & 255) * b);
    int g = (int)fmin(255.0f, ((color >> 8) & 255) * b);
    int bl = (int)fmin(255.0f, (color & 255) * b);
    return (r << 16) | (g << 8) | bl;
}
static int tintColorRM(int color, int tint, float amount) {
    int r = (color >> 16) & 255, g = (color >> 8) & 255, b = color & 255;
    int tr = (tint >> 16) & 255, tg = (tint >> 8) & 255, tb = tint & 255;
    r = (int)(r * (1 - amount) + tr * amount);
    g = (int)(g * (1 - amount) + tg * amount);
    b = (int)(b * (1 - amount) + tb * amount);
    return (r << 16) | (g << 8) | b;
}
static int cellTypeRM(WORLD, int layer, int x, int y) {
    int mw = meta[8 * layer + 1], mh = meta[8 * layer + 2];
    if (x < 0 || y < 0 || x >= mw || y >= mh) return -1;
    return maps[meta[8 * layer] + y * mw + x];
}
static int isSolidRM(WORLD, int layer, int x, int y) {
    int t = cellTypeRM(WORLDCALL, layer, x, y);
    if (t < 0) return 1;
    return t == 1 || t == 2;
}
static int tintStairsRM(int color, float wx, float wy, int layer, __global const int* maps, __global const int* meta) {
    int fx = (int)floor(wx), fy = (int)floor(wy);
    int mw = meta[8 * layer + 1], mh = meta[8 * layer + 2];
    if (fx < 0 || fy < 0 || fx >= mw || fy >= mh) return color;
    int cell = maps[meta[8 * layer] + fy * mw + fx];
    if (cell == 3) return tintColorRM(color, 0x3399FF, 0.45f);
    if (cell == 4) return tintColorRM(color, 0xFFAA33, 0.45f);
    return color;
}
static int sampleNearestRM(__global const int* tex, int tw, int th, float wx, float wy, int base) {
    if (tw <= 0 || th <= 0) return base;
    float fx = wx - floor(wx), fy = wy - floor(wy);
    int tx = min((int)(fx * tw), tw - 1);
    int ty = min((int)(fy * th), th - 1);
    return tex[ty * tw + tx];
}
static float sdfSampleRM(__global const float* F, int off, int cols, int rows, float x, float y) {
    float gx = clamp(x * 8.0f, 0.0f, cols - 1.000001f), gy = clamp(y * 8.0f, 0.0f, rows - 1.000001f);
    int i0 = (int)gx, j0 = (int)gy;
    int i1 = min(i0 + 1, cols - 1), j1 = min(j0 + 1, rows - 1);
    float fx = gx - i0, fy = gy - j0;
    float a = F[off + j0 * cols + i0], b = F[off + j0 * cols + i1];
    float c = F[off + j1 * cols + i0], d = F[off + j1 * cols + i1];
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy;
}
static float wallAtRM(WORLD, int layer, float x, float y) { return sdfSampleRM(fieldWall, meta[8 * layer + 3], meta[8 * layer + 4], meta[8 * layer + 5], x, y); }
static float finishAtRM(WORLD, int layer, float x, float y) { return sdfSampleRM(fieldFinish, meta[8 * layer + 3], meta[8 * layer + 4], meta[8 * layer + 5], x, y); }
static void warpPortalRM(__global const int* P, float hitX, float hitY, float dirX, float dirY, float* outX, float* outY, float* outDX, float* outDY) {
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
static int enterPortalRM(RS* s, int cx, int cy, int floors, WORLD) {
    if (s->hops >= 8) return 0;
    int mw = meta[8 * s->layer + 1], mh = meta[8 * s->layer + 2];
    if (cx < 0 || cy < 0 || cx >= mw || cy >= mh) return 0;
    int pidx = grid[meta[8 * s->layer] + cy * mw + cx];
    if (pidx < 0) return 0;
    __global const int* P = portals + 9 * pidx;
    if (s->dx * P[2] + s->dy * P[3] <= 1e-9f) return 0;
    float horizontal = sqrt(s->dx * s->dx + s->dy * s->dy);
    float nx, ny, ndx, ndy;
    warpPortalRM(P, s->x, s->y, s->dx, s->dy, &nx, &ny, &ndx, &ndy);
    float newH = sqrt(ndx * ndx + ndy * ndy);
    s->x = nx; s->y = ny;
    if (newH > 1e-9f) { s->dx = ndx * horizontal / newH; s->dy = ndy * horizontal / newH; }
    int lf = P[8];
    if (floors > 0 && lf >= 0 && lf < floors) s->layer = 1 + lf;
    s->hops++;
    return 1;
}
static float portalStepLimitRM(WORLD, int layer, float x, float y, float dx, float dy) {
    int cx = (int)floor(x), cy = (int)floor(y);
    float best = INFINITY;
    if (dx > 1e-12f && cellTypeRM(WORLDCALL, layer, cx + 1, cy) == RM_PORTAL) best = fmin(best, (cx + 1 - x) / dx);
    else if (dx < -1e-12f && cellTypeRM(WORLDCALL, layer, cx - 1, cy) == RM_PORTAL) best = fmin(best, (x - cx) / -dx);
    if (dy > 1e-12f && cellTypeRM(WORLDCALL, layer, cx, cy + 1) == RM_PORTAL) best = fmin(best, (cy + 1 - y) / dy);
    else if (dy < -1e-12f && cellTypeRM(WORLDCALL, layer, cx, cy - 1) == RM_PORTAL) best = fmin(best, (y - cy) / -dy);
    return fmax(0.0f, best);
}
static void resolveWallFaceRM(RS* s, WORLD) {
    float fx = s->x - floor(s->x), fy = s->y - floor(s->y);
    int cx = (int)floor(s->x), cy = (int)floor(s->y);
    float best = RM_BIG;
    int face = -1;
    if (isSolidRM(WORLDCALL, s->layer, cx - 1, cy) && fx < best) { best = fx; face = 0; }
    if (isSolidRM(WORLDCALL, s->layer, cx + 1, cy) && 1 - fx < best) { best = 1 - fx; face = 1; }
    if (isSolidRM(WORLDCALL, s->layer, cx, cy - 1) && fy < best) { best = fy; face = 2; }
    if (isSolidRM(WORLDCALL, s->layer, cx, cy + 1) && 1 - fy < best) { best = 1 - fy; face = 3; }
    int side = face >= 2 ? 1 : 0;
    float u = side == 0 ? fy : fx;
    if ((side == 0 && s->dx < 0) || (side == 1 && s->dy > 0)) u = 1 - u;
    s->hitSide = side;
    s->hitU = clamp(u, 0.0f, 0.999999f);
}
static void setWallFractionRM(RS* s) {
    float wallH = s->hitType == 2 ? 0.5f : 1.0f;
    s->hitFrac = clamp((wallH - s->z) / wallH, 0.0f, 0.999999f);
}
static int shadeMarchRM(RS* s, float light, int tinted, WORLD, TEXARGS) {
    if (s->hitKind == 1) {
        int finish = s->hitType == 2;
        int tw = finish ? fw : ww, th = finish ? fh : wh;
        __global const int* tex = finish ? finishTex : wallTex;
        int color;
        if (tw > 0 && th > 0) {
            int tx = min((int)(s->hitU * tw), tw - 1);
            int ty = min((int)(s->hitFrac * th), th - 1);
            color = tex[ty * tw + tx];
        } else color = finish ? RM_FINISH_BASE : RM_WALL_BASE;
        float b = clamp(1.0f - s->hitDist / light, 0.03f, 1.0f);
        if (s->hitSide == 1) b *= 0.85f;
        return applyBrightnessRM(color, b);
    }
    int isFloor = s->hitKind == 2;
    int color = isFloor ? sampleNearestRM(floorTex, flw, flh, s->hitX, s->hitY, RM_FLOOR_BASE) : sampleNearestRM(ceilTex, clw, clh, s->hitX, s->hitY, RM_CEIL_BASE);
    if (isFloor && tinted) color = tintStairsRM(color, s->hitX, s->hitY, s->layer, maps, meta);
    float b = clamp(1.0f - s->hitDist / light, 0.05f, 1.0f);
    return applyBrightnessRM(color, b);
}
static TR traceRayRM(float ox, float oy, float oz, float rx, float ry, float rz, int startLayer, int floors, WORLD) {
    TR r;
    r.hit = 0; r.isPlane = 0; r.type = 0; r.side = 0; r.layer = startLayer;
    r.distance = 0; r.x = 0; r.y = 0; r.z = 0; r.wallU = 0; r.wallV = 0;
    int sw = meta[8 * startLayer + 1], sh = meta[8 * startLayer + 2];
    float totalDistance = 0.0f;
    int hops = 0, steps = 0;
    float wallT = INFINITY;
    int wallType = 0, wallSide = 0;
    int layer = startLayer;
    int done = 0;
    while (!done) {
        float horizontal = sqrt(rx * rx + ry * ry);
        if (horizontal <= 1e-9f) break;
        float planeHere = rz < -1e-9f ? -oz / rz : (rz > 1e-9f ? (1.0f - oz) / rz : INFINITY);
        float dx = rx / horizontal, dy = ry / horizontal;
        int mapX = (int)floor(ox), mapY = (int)floor(oy);
        float deltaX = fabs(1.0f / dx), deltaY = fabs(1.0f / dy);
        float sideX = (dx < 0 ? ox - mapX : mapX + 1.0f - ox) * deltaX;
        float sideY = (dy < 0 ? oy - mapY : mapY + 1.0f - oy) * deltaY;
        int restart = 0;
        while (mapX >= 0 && mapY >= 0 && mapX < sw && mapY < sh) {
            float horizontalT;
            int side;
            if (sideX < sideY) { horizontalT = sideX; sideX += deltaX; mapX += dx < 0 ? -1 : 1; side = 0; }
            else { horizontalT = sideY; sideY += deltaY; mapY += dy < 0 ? -1 : 1; side = 1; }
            if (mapX < 0 || mapY < 0 || mapX >= sw || mapY >= sh) break;
            float candidateT = horizontalT / horizontal;
            if (candidateT >= planeHere || ++steps > 6000) { done = 1; break; }
            int type = cellTypeRM(WORLDCALL, layer, mapX, mapY);
            if (type == RM_PORTAL && hops < 8) {
                int pidx = grid[meta[8 * layer] + mapY * meta[8 * layer + 1] + mapX];
                if (pidx >= 0) {
                    __global const int* P = portals + 9 * pidx;
                    if (rx * P[2] + ry * P[3] > 1e-9f) {
                        float candidateZ = oz + rz * candidateT;
                        if (candidateZ >= 0.0f && candidateZ <= 1.0f) {
                            float hx = ox + rx * candidateT, hy = oy + ry * candidateT;
                            totalDistance += candidateT;
                            float nx, ny, ndx, ndy;
                            warpPortalRM(P, hx, hy, rx, ry, &nx, &ny, &ndx, &ndy);
                            ox = nx; oy = ny; rx = ndx; ry = ndy;
                            int lf = P[8];
                            if (floors > 0 && lf >= 0 && lf < floors) layer = 1 + lf;
                            oz = candidateZ;
                            hops++;
                            restart = 1;
                            break;
                        }
                    }
                }
            }
            if (restart) break;
            if (type != 1 && type != 2) continue;
            float candidateZ = oz + rz * candidateT;
            float wh2 = type == 2 ? 0.5f : 1.0f;
            if (candidateZ >= 0.0f && candidateZ <= wh2) {
                wallT = candidateT; wallType = type; wallSide = side;
                done = 1;
                break;
            }
        }
        if (done) break;
        if (restart) continue;
        break;
    }
    r.layer = layer;
    float planeT = INFINITY;
    int planeType = 0;
    if (rz < -1e-9f) { planeT = -oz / rz; planeType = -1; }
    else if (rz > 1e-9f) { planeT = (1.0f - oz) / rz; planeType = 1; }

    if (wallT < planeT && isfinite(wallT)) {
        float wallHeight = wallType == 2 ? 0.5f : 1.0f;
        r.hit = 1;
        r.distance = totalDistance + wallT;
        r.x = ox + rx * wallT;
        r.y = oy + ry * wallT;
        r.z = clamp(oz + rz * wallT, 0.0f, wallHeight);
        r.type = wallType;
        r.side = wallSide;
        float wallU = wallSide == 0 ? r.y : r.x;
        wallU -= floor(wallU);
        if ((wallSide == 0 && rx < 0) || (wallSide == 1 && ry > 0)) wallU = 1.0f - wallU;
        r.wallU = clamp(wallU, 0.0f, 0.999999f);
        r.wallV = clamp(r.z / wallHeight, 0.0f, 0.999999f);
    }
    else if (isfinite(planeT) && planeT > 0) {
        r.hit = 1;
        r.distance = totalDistance + planeT;
        r.x = ox + rx * planeT;
        r.y = oy + ry * planeT;
        r.z = planeType < 0 ? 0 : 1;
        r.type = planeType;
        r.side = 0;
        r.isPlane = 1;
    }
    return r;
}
static int shadeTraceRM(TR h, float light, int tinted, WORLD, TEXARGS) {
    if (!h.hit) return RM_CEIL_BASE;
    if (h.isPlane) {
        int isFloor = h.type == -1;
        int color = isFloor ? sampleNearestRM(floorTex, flw, flh, h.x, h.y, RM_FLOOR_BASE) : sampleNearestRM(ceilTex, clw, clh, h.x, h.y, RM_CEIL_BASE);
        if (isFloor && tinted) color = tintStairsRM(color, h.x, h.y, h.layer, maps, meta);
        return applyBrightnessRM(color, clamp(1.0f - h.distance / light, 0.05f, 1.0f));
    }
    int finish = h.type == 2;
    int tw = finish ? fw : ww, th = finish ? fh : wh;
    __global const int* tex = finish ? finishTex : wallTex;
    int color;
    if (tw <= 0 || th <= 0) color = finish ? RM_FINISH_BASE : RM_WALL_BASE;
    else {
        int tx = min((int)(h.wallU * tw), tw - 1);
        int ty = min((int)((1.0f - h.wallV) * th), th - 1);
        color = tex[ty * tw + max(0, tx)];
    }
    float b = clamp(1.0f - h.distance / light, 0.03f, 1.0f);
    if (h.side == 1) b *= 0.85f;
    return applyBrightnessRM(color, b);
}
static void marchSphereRM(RS* s, int maxSteps, int floors, WORLD) {
    s->hitKind = 0;
    s->exhausted = 0;
    float x = s->x, y = s->y, z = s->z, dx = s->dx, dy = s->dy, dz = s->dz, dist = s->dist;
    float planeAbs = dist + (dz < 0 ? z / -dz : (dz > 0 ? (1.0f - z) / dz : INFINITY));
    float prevWall = INFINITY;
    int i = 0;
    for (; i < maxSteps && dist < RM_MAXD; i++) {
        int cx = (int)floor(x), cy = (int)floor(y);
        int t0 = cellTypeRM(WORLDCALL, s->layer, cx, cy);
        if (t0 < 0) return;
        if (t0 == RM_PORTAL) {
            s->x = x; s->y = y; s->z = z; s->dx = dx; s->dy = dy; s->dz = dz; s->dist = dist;
            if (enterPortalRM(s, cx, cy, floors, WORLDCALL)) {
                x = s->x; y = s->y; z = s->z; dx = s->dx; dy = s->dy; dist = s->dist;
                prevWall = wallAtRM(WORLDCALL, s->layer, x, y);
                continue;
            }
        }
        int layer = s->layer;
        float dWall = wallAtRM(WORLDCALL, layer, x, y);
        int approaching = dWall < prevWall;
        prevWall = dWall;
        int hasFin = meta[8 * layer + 6];
        int hasPort = meta[8 * layer + 7];
        float dFinish = hasFin ? finishAtRM(WORLDCALL, layer, x, y) : RM_BIG;
        if (z <= RM_HIT || 1.0f - z <= RM_HIT) {
            s->x = x; s->y = y; s->z = z; s->dx = dx; s->dy = dy; s->dz = dz; s->dist = dist;
            s->hitKind = z <= RM_HIT ? 2 : 3;
            s->hitX = s->x; s->hitY = s->y; s->hitDist = s->dist;
            return;
        }
        if (dWall < RM_HIT && dist > RM_HIT && approaching) {
            s->x = x; s->y = y; s->z = z; s->dx = dx; s->dy = dy; s->dz = dz; s->dist = dist;
            s->hitKind = 1; s->hitType = 1; s->hitDist = s->dist;
            resolveWallFaceRM(s, WORLDCALL);
            setWallFractionRM(s);
            return;
        }
        if (dFinish < RM_HIT && z <= 0.5f && dist > RM_HIT) {
            s->x = x; s->y = y; s->z = z; s->dx = dx; s->dy = dy; s->dz = dz; s->dist = dist;
            s->hitKind = 1; s->hitType = 2; s->hitDist = s->dist;
            resolveWallFaceRM(s, WORLDCALL);
            setWallFractionRM(s);
            return;
        }
        float dFinishBox = hasFin ? sqrt(dFinish * dFinish + fmax(0.0f, z - 0.5f) * fmax(0.0f, z - 0.5f)) : RM_BIG;
        float distance = fmin(fmin(dWall, dFinishBox), fmin(z, 1.0f - z));
        float step = clamp(distance * RM_STEP_SCALE, RM_MIN_STEP, 0.5f);
        if (hasPort) step = fmin(step, portalStepLimitRM(WORLDCALL, layer, x, y, dx, dy) + 1e-5f);
        if (dWall < RM_HIT) step = fmax(step, 4 * RM_HIT);
        step = fmin(step, planeAbs - dist);
        x += dx * step;
        y += dy * step;
        z += dz * step;
        dist += step;
    }
    s->x = x; s->y = y; s->z = z; s->dx = dx; s->dy = dy; s->dz = dz; s->dist = dist;
    if (dist < RM_MAXD) s->exhausted = 1;
}
static void addSegRM(Col* c, float dist, RS* s) {
    int k = c->segCount++;
    c->segStartDist[k] = dist; c->segStartX[k] = s->x; c->segStartY[k] = s->y;
    c->segDirX[k] = s->dx; c->segDirY[k] = s->dy; c->segLayer[k] = s->layer;
}
static void marchColumnRM(Col* c, float px, float py, float ux, float uy, int floors, int maxSteps, WORLD) {
    RS s;
    s.x = px; s.y = py; s.z = 0; s.dx = ux; s.dy = uy; s.dz = 0; s.dist = 0;
    s.layer = 0; s.hops = 0; s.hitKind = 0; s.exhausted = 0;
    c->segCount = 0; c->finCount = 0; c->exhausted = 0;
    c->wallDist = INFINITY; c->exitD = INFINITY; c->wallU = 0; c->wallSide = 0;
    addSegRM(c, 0.0f, &s);
    float dist = 0.0f;
    int inFinish = 0;
    float prevWall = INFINITY;
    int i = 0;
    for (; i < maxSteps && dist < RM_MAXD; i++) {
        int layer = s.layer;
        int cx = (int)floor(s.x), cy = (int)floor(s.y);
        int t0 = cellTypeRM(WORLDCALL, layer, cx, cy);
        if (t0 < 0) { c->exitD = dist; return; }
        if (t0 == RM_PORTAL && enterPortalRM(&s, cx, cy, floors, WORLDCALL)) {
            addSegRM(c, dist, &s);
            prevWall = wallAtRM(WORLDCALL, s.layer, s.x, s.y);
            continue;
        }
        layer = s.layer;
        float dWall = wallAtRM(WORLDCALL, layer, s.x, s.y);
        int approaching = dWall < prevWall;
        prevWall = dWall;
        int hasFin = meta[8 * layer + 6];
        int hasPort = meta[8 * layer + 7];
        float dFinish = hasFin ? finishAtRM(WORLDCALL, layer, s.x, s.y) : RM_BIG;
        if (dWall < RM_HIT && dist > RM_HIT && approaching) {
            resolveWallFaceRM(&s, WORLDCALL);
            c->wallDist = dist; c->wallU = s.hitU; c->wallSide = s.hitSide;
            return;
        }
        if (dFinish < RM_HIT && dist > RM_HIT) {
            if (!inFinish && c->finCount < RM_MAX_FIN) {
                resolveWallFaceRM(&s, WORLDCALL);
                int k = c->finCount++;
                c->finDist[k] = dist; c->finU[k] = s.hitU; c->finSide[k] = s.hitSide;
            }
            inFinish = 1;
        } else inFinish = 0;
        float step = clamp(fmin(dWall, dFinish) * RM_STEP_SCALE, RM_MIN_STEP, 0.5f);
        if (dWall < RM_HIT) step = fmax(step, 4 * RM_HIT);
        if (hasPort) step = fmin(step, portalStepLimitRM(WORLDCALL, layer, s.x, s.y, s.dx, s.dy) + 1e-5f);
        s.x += s.dx * step;
        s.y += s.dy * step;
        dist += step;
    }
    if (dist < RM_MAXD && i >= maxSteps) c->exhausted = 1;
}
static int columnRowRM(Col* c, RS* rs, float z0, float sigma, float light, int tinted, WORLD, TEXARGS) {
    float scale = sqrt(1.0f + sigma * sigma);
    float planeD = sigma < 0 ? z0 / -sigma : (sigma > 0 ? (1.0f - z0) / sigma : INFINITY);
    int floorPlane = sigma < 0;
    float nearest = fmin(fmin(c->wallDist, planeD), c->exitD);
    for (int k = 0; k < c->finCount; k++) {
        float d = c->finDist[k];
        if (d >= nearest) break;
        float z = z0 + sigma * d;
        if (z <= 0.5f) {
            rs->hitKind = 1; rs->hitType = 2; rs->hitSide = c->finSide[k]; rs->hitU = c->finU[k];
            rs->hitDist = d * scale;
            rs->hitFrac = clamp((0.5f - z) / 0.5f, 0.0f, 0.999999f);
            return shadeMarchRM(rs, light, tinted, WORLDCALL, TEXCALL);
        }
    }
    if (c->wallDist < planeD && c->wallDist < c->exitD) {
        float z = z0 + sigma * c->wallDist;
        rs->hitKind = 1; rs->hitType = 1; rs->hitSide = c->wallSide; rs->hitU = c->wallU;
        rs->hitDist = c->wallDist * scale;
        rs->hitFrac = clamp(1.0f - z, 0.0f, 0.999999f);
        return shadeMarchRM(rs, light, tinted, WORLDCALL, TEXCALL);
    }
    if (isfinite(planeD) && planeD < c->exitD && planeD < RM_MAXD) {
        int k = 0;
        for (int j = 1; j < c->segCount && c->segStartDist[j] <= planeD; j++) k = j;
        float along = planeD - c->segStartDist[k];
        rs->hitKind = floorPlane ? 2 : 3;
        rs->hitX = c->segStartX[k] + c->segDirX[k] * along;
        rs->hitY = c->segStartY[k] + c->segDirY[k] * along;
        rs->hitDist = planeD * scale;
        rs->layer = c->segLayer[k];
        return shadeMarchRM(rs, light, tinted, WORLDCALL, TEXCALL);
    }
    return RM_CEIL_BASE;
}
__kernel void rm_render(WORLD, __global const int* wallTex, __global const int* finishTex, __global const int* floorTex, __global const int* ceilTex,
                        __global const float* camX, __global const float* dp, __global const int* ip, __global int* out) {
    int x = get_global_id(0), y0 = get_global_id(1);
    int W = ip[0], H = ip[1], floors = ip[2], tinted = ip[3];
    int ww = ip[4], wh = ip[5], fw = ip[6], fh = ip[7], flw = ip[8], flh = ip[9], clw = ip[10], clh = ip[11];
    int mode = ip[12], maxSteps = ip[13];
    if (x >= W) return;
    float playerX = dp[0], playerY = dp[1], camZ = dp[2];
    float dirX = dp[3], dirY = dp[4], planeLength = dp[5];
    float pitchSin = dp[6], pitchCos = dp[7], halfHeight = dp[8], focal = dp[9], light = dp[10];

    if (mode == 1) {
        if (y0 != 0) return;
        float lateral = camX[x] * planeLength;
        float hx = dirX - lateral * dirY, hy = dirY + lateral * dirX;
        float hl = sqrt(hx * hx + hy * hy);
        Col c;
        marchColumnRM(&c, playerX, playerY, hx / hl, hy / hl, floors, maxSteps, WORLDCALL);
        RS rs;
        for (int y = 0; y < H; y++) {
            float v = (halfHeight - (y + 0.5f)) / focal;
            int color;
            if (c.exhausted) {
                float inv = 1.0f / sqrt(hx * hx + hy * hy + v * v);
                TR t = traceRayRM(playerX, playerY, camZ, hx * inv, hy * inv, v * inv, 0, floors, WORLDCALL);
                color = shadeTraceRM(t, light, tinted, WORLDCALL, TEXCALL);
            } else color = columnRowRM(&c, &rs, camZ, v / hl, light, tinted, WORLDCALL, TEXCALL);
            out[y * W + x] = color;
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
    RS s;
    s.x = playerX; s.y = playerY; s.z = camZ;
    s.dx = rx / len; s.dy = ry / len; s.dz = rz / len;
    s.dist = 0; s.layer = 0; s.hops = 0; s.hitKind = 0; s.exhausted = 0;
    marchSphereRM(&s, maxSteps, floors, WORLDCALL);
    int color;
    if (s.exhausted) {
        float inv = 1.0f / sqrt(rx * rx + ry * ry + rz * rz);
        TR t = traceRayRM(playerX, playerY, camZ, rx * inv, ry * inv, rz * inv, 0, floors, WORLDCALL);
        color = shadeTraceRM(t, light, tinted, WORLDCALL, TEXCALL);
    } else color = s.hitKind == 0 ? RM_CEIL_BASE : shadeMarchRM(&s, light, tinted, WORLDCALL, TEXCALL);
    out[y * W + x] = color;
}