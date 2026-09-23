package Helpers;

import java.util.HashMap;
import java.util.Map;

public final class PortalData {
    public static final class Portal {
        public final int x, y, dirX, dirY;
        public int linkedX, linkedY, linkedDirX, linkedDirY;
        public Portal(int x, int y, int dirX, int dirY) { this.x = x; this.y = y; this.dirX = dirX; this.dirY = dirY; }
    }
    private final Map<Long, Portal> portals = new HashMap<>();
    private static long key(int x, int y) { return (((long) y) << 32) ^ (x & 0xffffffffL); }
    public void link(int ax, int ay, int adx, int ady, int bx, int by, int bdx, int bdy) {
        Portal a = new Portal(ax, ay, adx, ady);
        a.linkedX = bx; a.linkedY = by; a.linkedDirX = bdx; a.linkedDirY = bdy;
        Portal b = new Portal(bx, by, bdx, bdy);
        b.linkedX = ax; b.linkedY = ay; b.linkedDirX = adx; b.linkedDirY = ady;
        portals.put(key(ax, ay), a);
        portals.put(key(bx, by), b);
    }
    public Portal get(int x, int y) { return portals.get(key(x, y)); }
    public java.util.Collection<Portal> values() { return portals.values(); }
    public boolean isEmpty() { return portals.isEmpty(); }
    public static double[] transformPoint(Portal p, double x, double y) {
        double nax = p.dirX, nay = p.dirY, mbx = p.linkedDirX, mby = p.linkedDirY;
        double entryBaseX = p.x + Math.max(nax, 0) + Math.max(nay, 0);
        double entryBaseY = p.y + Math.max(nay, 0) + Math.max(-nax, 0);
        double exitBaseX = p.linkedX + Math.max(mbx, 0) + Math.max(mby, 0);
        double exitBaseY = p.linkedY + Math.max(mby, 0) + Math.max(-mbx, 0);
        double cosT = nax * mbx + nay * mby, sinT = nax * mby - nay * mbx;
        double px = x - entryBaseX, py = y - entryBaseY;
        return new double[]{exitBaseX + px * cosT - py * sinT, exitBaseY + px * sinT + py * cosT};
    }
    public static boolean entersFromCorrectSide(Portal p, double dirX, double dirY) { return dirX * p.dirX + dirY * p.dirY > 1e-9; }
    public static double[] transformDirection(Portal p, double dirX, double dirY) {
        double cosT = p.dirX * p.linkedDirX + p.dirY * p.linkedDirY;
        double sinT = p.dirX * p.linkedDirY - p.dirY * p.linkedDirX;
        return new double[]{dirX * cosT - dirY * sinT, dirX * sinT + dirY * cosT};
    }
    public static double[] warp(Portal p, double hitX, double hitY, double dirX, double dirY) {
        double nax = p.dirX, nay = p.dirY, mbx = p.linkedDirX, mby = p.linkedDirY;

        double entryBaseX = p.x + Math.max(nax, 0) + Math.max(nay, 0);
        double entryBaseY = p.y + Math.max(nay, 0) + Math.max(-nax, 0);
        double exitBaseX = p.linkedX + Math.max(mbx, 0) + Math.max(mby, 0);
        double exitBaseY = p.linkedY + Math.max(mby, 0) + Math.max(-mbx, 0);

        double u = (hitX - entryBaseX) * (-nay) + (hitY - entryBaseY) * nax;
        u = Math.clamp(u, 0.0, 0.999999);
        double cosT = nax * mbx + nay * mby, sinT = nax * mby - nay * mbx;
        double ndx = dirX * cosT - dirY * sinT, ndy = dirX * sinT + dirY * cosT;
        double ex = exitBaseX + u * (-mby), ey = exitBaseY + u * mbx;

        double eps = 1e-4;
        return new double[]{ex + ndx * eps, ey + ndy * eps, ndx, ndy};
    }
}