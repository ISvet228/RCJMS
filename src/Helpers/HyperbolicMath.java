package Helpers;

public final class HyperbolicMath {
    private HyperbolicMath() {}
    public static final double DEFAULT_CURVATURE = 4.0;

    public static double hyperbolicDistance(double flatDistance, double curvature) {
        double r = Math.max(curvature, 0.0001);
        return r * Math.sinh(flatDistance / r);
    }
    public static double poincareRadius(double flatDistance, double curvature) { return Math.tanh(flatDistance / (2.0 * Math.max(curvature, 0.0001))); }
    public static double inversePoincareRadius(double diskRadius, double curvature) { //may be useful in future i guess
        return 2.0 * (Math.max(curvature, 0.0001)) * atanh(Math.clamp(diskRadius, -0.999999, 0.999999));
    }
    private static double atanh(double x) { return 0.5 * Math.log((1.0 + x) / (1.0 - x)); }
}