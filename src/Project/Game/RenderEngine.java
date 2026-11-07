package Project.Game;

public interface RenderEngine {
    //region Engine Identity
    String id();
    String displayName();
    default int order() { return 100; }
    //endregion

    //region Hints
    default String hintKey() { return null; }
    default String qualityHintKey() { return null; }
    //endregion

    //region Capabilities
    boolean hasHyperbolicGeometry();
    default boolean hasOpenCL() { return false; }
    default String openClKey() { return id(); }
    default boolean supportsMapSize(int width, int height) { return true; }
    //endregion

    //region Rendering
    default void prepare(RenderFrame frame) {}
    void render(RenderFrame frame);
    //endregion
}