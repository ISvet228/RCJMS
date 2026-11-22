package Project.Audio;

import Project.Core.*;

public final class SoundModule implements GameModule {
    //region Identity
    @Override public String id() { return "sound"; }
    //endregion

    //region Run Lifecycle
    @Override public void onRunStart(GameContext context) { Sound.clearQueue(); }
    @Override public void onPauseChanged(GameContext context, boolean paused) { if (paused) Sound.clearQueue(); }
    @Override public void onRunFinish(GameContext context) {
        Sound.clearQueue();
        Sound.play(Sound.Effect.FINISH);
    }
    //endregion
}