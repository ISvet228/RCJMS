package GameViewComponents;

import Helpers.*;

public final class RenderEngineConfig {
    public static MazeGenerator.GeometryMode GEOMETRY_MODE = MazeGenerator.GeometryMode.EUCLIDEAN;
    public static double HYPERBOLIC_CURVATURE = HyperbolicMath.DEFAULT_CURVATURE;
    public static volatile double renderScale = 1.0;
    public static volatile int renderEngineQuality = 1;
    public static int outputWidth = 960, outputHeight = 540;

    private RenderEngineConfig() {}
}
