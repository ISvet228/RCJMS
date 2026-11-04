package Project.Game;
import Project.EngineCreator.*;
import Project.ThemeCreator.*;
import Project.CreatorKit.*;

import Project.Helpers.*;

public final class RenderEngineConfig {
    //region Variables
    //region Geometry Settings
    public static MazeGenerator.GeometryMode GEOMETRY_MODE = MazeGenerator.GeometryMode.EUCLIDEAN;
    public static double HYPERBOLIC_CURVATURE = HyperbolicMath.DEFAULT_CURVATURE;
    //endregion
    //region Quality Settings
    public static volatile int renderEngineQuality = 1;
    public static final int QUALITY_AUTO = 3;
    //endregion
    //endregion

    //region Constructors
    private RenderEngineConfig() {}
    //endregion
}
