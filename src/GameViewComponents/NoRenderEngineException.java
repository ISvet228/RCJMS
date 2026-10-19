package GameViewComponents;

public final class NoRenderEngineException extends RuntimeException {
    public NoRenderEngineException(int width, int height) { super("No render engine supports a " + width + "x" + height + " map"); }
}