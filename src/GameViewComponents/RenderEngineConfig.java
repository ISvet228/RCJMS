package GameViewComponents;

import Helpers.*;

public final class RenderEngineConfig {
    public static MazeGenerator.GeometryMode GEOMETRY_MODE = MazeGenerator.GeometryMode.EUCLIDEAN;
    public static double HYPERBOLIC_CURVATURE = HyperbolicMath.DEFAULT_CURVATURE;
    public static volatile int renderEngineQuality = 1;
    public static final int QUALITY_AUTO = 3;
    private RenderEngineConfig() {}
}
