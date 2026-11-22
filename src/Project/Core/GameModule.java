package Project.Core;

import java.awt.Graphics2D;
import java.awt.event.KeyEvent;

public interface GameModule {
    String id();
    default void onConsoleReady(GameContext context) {}
    default void onRunStart(GameContext context) {}
    default void onRunFinish(GameContext context) {}
    default void onPauseChanged(GameContext context, boolean paused) {}
    default boolean onKey(GameContext context, KeyEvent keyEvent, boolean down) { return false; }
    default void update(GameContext context, double seconds) {}
    default void paintHud(GameContext context, Graphics2D g2d, int width, int height) {}
    default void postProcess(GameContext context, int[] pixels, int width, int height) {}
}