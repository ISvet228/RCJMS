package GameViewComponents;

import Helpers.*;

import java.awt.image.BufferedImage;

public final class RenderFrame {
    //region Output buffers
    public BufferedImage bufferedImage, renderImage;
    public int[] renderPixels;
    public int[] pixels;
    public int renderHeight, renderWidth;
    public int outputWidth, outputHeight;
    public RenderWorkers renderWorkers;
    //endregion

    //region Render settings
    public int quality;
    public MazeGenerator.GeometryMode geometry = MazeGenerator.GeometryMode.EUCLIDEAN;
    public double curvature = HyperbolicMath.DEFAULT_CURVATURE;
    //endregion

    //region Camera and player
    public double cameraAngle, cameraHeight, cameraPitch;
    public double[] cameraXCache;
    public double playerX, playerY;
    //endregion

    //region World
    public RuntimeTexture wallTexture, floorTexture, ceilingTexture, finishTexture;
    public PortalData[] floorPortalsAll;
    public PortalData portals;
    public int[][][] map3D;
    public int[][] map;
    //endregion

    //region Scratch buffers reused between frames and engines
    public int[] wallTopCache, wallBottomCache;
    public double[] portalFloorEdgeCache, portalCeilEdgeCache;
    public double[] rowInvACache, rowZACache;
    //endregion
}
