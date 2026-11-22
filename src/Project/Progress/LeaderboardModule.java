package Project.Progress;

import Project.Core.*;
import Project.Helpers.NSLocalizedString;

public final class LeaderboardModule implements GameModule {
    @Override public String id() { return "leaderboard"; }
    @Override public void onRunFinish(GameContext context) {
        try {
            if (context.isCustomMap()) return;
            String key = Leaderboard.key(context.seed(), context.mazeWidth(), context.mazeHeight(), context.floorCount(), context.hasFloors(), context.geometry());
            int rank = Leaderboard.record(key, context.elapsedSeconds());
            if (rank > 0) context.flash(NSLocalizedString.format("lb.rank", rank));
        } catch (RuntimeException ignored) { }
    }
}