package Project.Game;
import Project.EngineCreator.*;
import Project.ThemeCreator.*;
import Project.CreatorKit.*;

public final class NoRenderEngineException extends RuntimeException {
    //region Constructors
    public NoRenderEngineException(int width, int height) { super("No render engine supports a " + width + "x" + height + " map"); }
    //endregion
}