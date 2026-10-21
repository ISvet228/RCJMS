package GameViewComponents;

public interface RenderEngine {
    String id();
    String displayName();
    default int order() { return 100; }
    default String hintKey() { return null; }
    default String qualityHintKey() { return null; }
    boolean hasHyperbolicGeometry();
    default boolean hasOpenCL() { return false; }
    default String openClKey() { return id(); }
    default boolean supportsMapSize(int width, int height) { return true; }
    default void prepare(RenderFrame frame) {}
    void render(RenderFrame frame);
}