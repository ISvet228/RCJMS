package Project.Helpers;
import Project.EngineCreator.*;
import Project.ThemeCreator.*;
import Project.CreatorKit.*;

public record RuntimeTexture(int width, int height, int[] pixels) {
//region Factories
public static RuntimeTexture empty() {
        return new RuntimeTexture(0, 0, new int[0]);
    }
//endregion

//region Conversion
public int[][] toMatrix() {
        if (width == 0 || height == 0) return new int[0][0];
        int[][] result = new int[height][width];
        for (int y = 0; y < height; y++) System.arraycopy(pixels, y * width, result[y], 0, width);
        return result;
    }
//endregion
}