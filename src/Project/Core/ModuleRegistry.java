package Project.Core;

import Project.Filters.FilterModule;

import java.util.List;
import Project.Audio.*;
import Project.Progress.*;
import Project.Horror.*;
import Project.Roguelike.*;

import Project.Net.*;

public final class ModuleRegistry {
    private ModuleRegistry() {}
    public static List<GameModule> create() {
        return List.of(
                new ScreenshotModule(),
                new StatsModule(),
                new AchievementModule(),
                new LeaderboardModule(),
                new SoundModule(),
                new MusicModule(),
                new FilterModule(),
                new NetModule(),
                new RoguelikeModule(),
                new HorrorModule()
        );
    }
}