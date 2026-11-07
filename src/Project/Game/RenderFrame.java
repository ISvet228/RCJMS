package Project.Game;

import Project.Helpers.*;

import java.awt.image.BufferedImage;

public final class RenderFrame {
    //region Variables
    //region Render Buffers
    public BufferedImage bufferedImage, renderImage;
    public int[] renderPixels;
    public int[] pixels;
    public int renderHeight, renderWidth;
    public int outputWidth, outputHeight;
    public RenderWorkers renderWorkers;
    //endregion
    //region Render Settings
    public int quality;
    public MazeGenerator.GeometryMode geometry = MazeGenerator.GeometryMode.EUCLIDEAN;
    public double curvature = HyperbolicMath.DEFAULT_CURVATURE;
    public boolean skyCeiling;
    public double skyTime;
    //endregion
    //region Camera and Player
    public double cameraAngle, cameraHeight, cameraPitch;
    public double playerX, playerY;
    public double[] cameraXCache;
    //endregion
    //region Maze Data
    public int[][][] map3D;
    public int[][] map;
    public PortalData[] floorPortalsAll;
    public PortalData portals;
    //endregion
    //region Textures
    public RuntimeTexture wallTexture, floorTexture, ceilingTexture, finishTexture;
    //endregion
    //region Edge Caches
    public int[] wallTopCache, wallBottomCache;
    public double[] portalFloorEdgeCache, portalCeilEdgeCache;
    public double[] rowInvACache, rowZACache;
    //endregion
    //endregion
}
