package Project.Progress;

import Project.Audio.*;
import Project.Core.*;
import Project.Helpers.NSLocalizedString;
import Project.Horror.HorrorModule;

import java.util.List;

public final class AchievementModule implements GameModule {
    //region Identity
    @Override public String id() { return "achievements"; }
    //endregion

    //region Run Lifecycle
    @Override public void onRunFinish(GameContext context) {
        try {
            List<Achievements.Achievement> unlocked = Achievements.recordFinish(
                    context.elapsedSeconds(), context.floorCount(), context.hasFloors(), context.seed(),
                    context.geometry(), context.fogOfWar(), context.isCustomMap(), HorrorModule.active(context));
            if (unlocked.isEmpty()) return;
            StringBuilder names = new StringBuilder();
            for (Achievements.Achievement achievement : unlocked) {
                if (!names.isEmpty()) names.append(", ");
                names.append(NSLocalizedString.get(achievement.nameKey()));
            }
            context.flash(NSLocalizedString.format("ach.unlocked", names));
            Sound.play(Sound.Effect.UNLOCK);
        } catch (RuntimeException ignored) { }
    }
    //endregion
}