package Project.Audio;

import Project.Core.GameContext;
import Project.Core.GameModule;
import Project.Helpers.Settings;
import Project.Game.GameView;

import Project.Net.*;

public final class MusicModule implements GameModule {
    //region Variables
    private static final String ON_KEY = "music.on";
    private boolean finished;
    //endregion

    //region Identity
    @Override public String id() { return "music"; }
    //endregion

    //region Run Lifecycle
    @Override public void onRunStart(GameContext context) {
        finished = false;
        sync(context);
    }
    @Override public void onPauseChanged(GameContext context, boolean paused) {
        sync(context);
    }
    @Override public void onRunFinish(GameContext context) {
        finished = true;
        Music.stop();
    }
    //endregion

    //region Simulation
    @Override public void update(GameContext context, double seconds) {
        sync(context);
    }
    //endregion

    //region Playback
    private static boolean enabled() { return Settings.getBoolean(ON_KEY, true); }
    private void sync(GameContext context) {
        if (!enabled() || finished) { Music.show(null); return; }
        if (context.isPaused()) { Music.pause(); return; }
        String seed = context.isCustomMap() || context.seed().isEmpty() ? "custom" : context.seed();
        Music.resume();
        Music.show(Music.key(styleFor(), seed));
    }
    private static Music.Style styleFor() {
        if (GameView.isTextMode()) return Music.Style.TEXT;
        boolean doom = GameView.isDoomMovement(), night = GameView.isNoCeiling();
        if (doom && night) return Music.Style.DOOM_NIGHT;
        if (doom) return Music.Style.DOOM;
        if (night) return Music.Style.NIGHT;
        return Music.Style.MAZE;
    }
    //endregion
}