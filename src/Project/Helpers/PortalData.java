package Project.Helpers;
import Project.EngineCreator.*;
import Project.ThemeCreator.*;
import Project.CreatorKit.*;

import java.util.HashMap;
import java.util.Map;

public final class PortalData {
    //region Variables
    private final Map<Long, Portal> portals = new HashMap<>();
    private static final double EDGE_MARGIN = 5e-4;
    //endregion

    //region Linking
    public void link(int ax, int ay, int adx, int ady, int bx, int by, int bdx, int bdy) { link(ax, ay, adx, ady, false, bx, by, bdx, bdy, false); }
    public void link(int ax, int ay, int adx, int ady, boolean aOneSided, int bx, int by, int bdx, int bdy, boolean bOneSided) {
        Portal portal = new Portal(ax, ay, adx, ady);
        portal.linkedX = bx; portal.linkedY = by; portal.linkedDirX = bOneSided ? -bdx : bdx; portal.linkedDirY = bOneSided ? -bdy : bdy;
        Portal b = new Portal(bx, by, bdx, bdy);
        b.linkedX = ax; b.linkedY = ay; b.linkedDirX = aOneSided ? -adx : adx; b.linkedDirY = aOneSided ? -ady : ady;
        portals.put(key(ax, ay), portal);
        portals.put(key(bx, by), b);
    }
    public static void linkFloors(PortalData portalData, int aFloor, int ax, int ay, int adx, int ady, boolean aOneSided,
                                  PortalData db, int bFloor, int bx, int by, int bdx, int bdy, boolean bOneSided) {
        Portal portal = new Portal(ax, ay, adx, ady);
        portal.linkedX = bx; portal.linkedY = by; portal.linkedDirX = bOneSided ? -bdx : bdx; portal.linkedDirY = bOneSided ? -bdy : bdy; portal.linkedFloor = bFloor;
        Portal b = new Portal(bx, by, bdx, bdy);
        b.linkedX = ax; b.linkedY = ay; b.linkedDirX = aOneSided ? -adx : adx; b.linkedDirY = aOneSided ? -ady : ady; b.linkedFloor = aFloor;
        portalData.portals.put(key(ax, ay), portal);
        db.portals.put(key(bx, by), b);
    }
    //endregion

    //region Portal Storage
    public void remove(int x, int y) { portals.remove(key(x, y)); }
    public Portal get(int x, int y) { return portals.get(key(x, y)); }
    public java.util.Collection<Portal> values() { return portals.values(); }
    public boolean isEmpty() { return portals.isEmpty(); }
    private static long key(int x, int y) { return (((long) y) << 32) ^ (x & 0xffffffffL); }
    //endregion

    //region Warping
    public static double[] transformPoint(Portal portal, double x, double y) {
        double nax = portal.dirX, nay = portal.dirY, mbx = portal.linkedDirX, mby = portal.linkedDirY;
        double entryBaseX = portal.x + Math.max(nax, 0) + Math.max(nay, 0);
        double entryBaseY = portal.y + Math.max(nay, 0) + Math.max(-nax, 0);
        double exitBaseX = portal.linkedX + Math.max(mbx, 0) + Math.max(mby, 0);
        double exitBaseY = portal.linkedY + Math.max(mby, 0) + Math.max(-mbx, 0);
        double cosT = nax * mbx + nay * mby, sinT = nax * mby - nay * mbx;
        double px = x - entryBaseX, py = y - entryBaseY;
        return new double[]{exitBaseX + px * cosT - py * sinT, exitBaseY + px * sinT + py * cosT};
    }
    public static double[] teleport(Portal portal, double x, double y) {
        double nax = portal.dirX, nay = portal.dirY, mbx = portal.linkedDirX, mby = portal.linkedDirY;
        double ex = portal.x + Math.max(nax, 0) + Math.max(nay, 0), ey = portal.y + Math.max(nay, 0) + Math.max(-nax, 0);
        double xx = portal.linkedX + Math.max(mbx, 0) + Math.max(mby, 0), xy = portal.linkedY + Math.max(mby, 0) + Math.max(-mbx, 0);
        double depth = Math.abs((x - ex) * nax + (y - ey) * nay);
        double u = (x - ex) * (-nay) + (y - ey) * nax;
        return new double[]{xx + u * (-mby) + depth * mbx, xy + u * mbx + depth * mby};
    }
    public static boolean crossesWindow(Portal portal, double x0, double y0, double x1, double y1) {
        double nax = portal.dirX, nay = portal.dirY;
        double ex = portal.x + Math.max(nax, 0) + Math.max(nay, 0), ey = portal.y + Math.max(nay, 0) + Math.max(-nax, 0);
        double s0 = (x0 - ex) * nax + (y0 - ey) * nay, s1 = (x1 - ex) * nax + (y1 - ey) * nay;
        if (!(s0 < 0 && s1 >= 0)) return false;
        double t = s0 / (s0 - s1);
        double hx = x0 + (x1 - x0) * t, hy = y0 + (y1 - y0) * t;
        double u = (hx - ex) * (-nay) + (hy - ey) * nax;
        return u >= -1e-9 && u <= 1 + 1e-9;
    }
    public static boolean entersFromCorrectSide(Portal portal, double dirX, double dirY) { return dirX * portal.dirX + dirY * portal.dirY > 1e-9; }
    public static double[] transformDirection(Portal portal, double dirX, double dirY) {
        double cosT = portal.dirX * portal.linkedDirX + portal.dirY * portal.linkedDirY;
        double sinT = portal.dirX * portal.linkedDirY - portal.dirY * portal.linkedDirX;
        return new double[]{dirX * cosT - dirY * sinT, dirX * sinT + dirY * cosT};
    }
    public static double[] warp(Portal portal, double hitX, double hitY, double dirX, double dirY) {
        double nax = portal.dirX, nay = portal.dirY, mbx = portal.linkedDirX, mby = portal.linkedDirY;

        double u = (hitX - (portal.x + Math.max(nax, 0) + Math.max(nay, 0))) * (-nay) + (hitY - (portal.y + Math.max(nay, 0) + Math.max(-nax, 0))) * nax;
        u = Math.clamp(u, EDGE_MARGIN, 1.0 - EDGE_MARGIN);
        double cosT = nax * mbx + nay * mby, sinT = nax * mby - nay * mbx;
        double ndx = dirX * cosT - dirY * sinT, ndy = dirX * sinT + dirY * cosT;
        double ex = (portal.linkedX + Math.max(mbx, 0) + Math.max(mby, 0)) + u * (-mby), ey = (portal.linkedY + Math.max(mby, 0) + Math.max(-mbx, 0)) + u * mbx;

        double eps = 1e-4;
        return new double[]{ex + ndx * eps, ey + ndy * eps, ndx, ndy};
    }
    //endregion

    //region Portal Record
    public static final class Portal {
        public final int x, y, dirX, dirY;
        public int linkedX, linkedY, linkedDirX, linkedDirY, linkedFloor = -1;

        public Portal(int x, int y, int dirX, int dirY) { this.x = x; this.y = y; this.dirX = dirX; this.dirY = dirY; }

        public int exitCellX() { return linkedX + linkedDirX; }
        public int exitCellY() { return linkedY + linkedDirY; }
    }
    //endregion
}