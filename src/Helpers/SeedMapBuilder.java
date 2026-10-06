package Helpers;

import java.util.ArrayList;
import java.util.List;

public final class SeedMapBuilder {
    //region Constructors
    private SeedMapBuilder() {}
    //endregion

    //region Public API
    public static Result build(Options options) {
        String seed = SeedUtil.normalize(options.seed());
        if (seed.isEmpty()) seed = SeedUtil.randomSeed();
        long seedNumber = SeedUtil.toLong(seed);

        MazeGenerator.GeometryMode geometry = options.looped() ? MazeGenerator.GeometryMode.LOOPED : MazeGenerator.GeometryMode.EUCLIDEAN;
        MazeGenerator.FinishMode finish = MazeGenerator.FinishMode.values()[Math.clamp(options.finishMode(), 0, MazeGenerator.FinishMode.values().length - 1)];

        List<int[][]> floors = new ArrayList<>();
        PortalData[] portals;
        if (options.floors() > 1) {
            MazeGenerator3D generator = new MazeGenerator3D(options.width(), options.height(), options.floors(), seedNumber, geometry);
            int[][][] generated = generator.generate(finish);
            portals = new PortalData[generated.length];
            for (int f = 0; f < generated.length; f++) {
                floors.add(generated[f]);
                portals[f] = generator.getPortals(f);
            }
        } else {
            MazeGenerator generator = new MazeGenerator(options.width(), options.height(), seedNumber, geometry);
            floors.add(generator.generate(finish));
            portals = new PortalData[]{generator.getPortals()};
        }
        return new Result(floors, toLinks(portals), seed);
    }
    //endregion

    //region Helpers
    private static List<int[]> toLinks(PortalData[] portals) {
        List<int[]> links = new ArrayList<>();
        for (int f = 0; f < portals.length; f++) {
            for (PortalData.Portal a : portals[f].values()) {
                int otherFloor = a.linkedFloor >= 0 ? a.linkedFloor : f;
                if (otherFloor >= portals.length) continue;
                PortalData.Portal b = portals[otherFloor].get(a.linkedX, a.linkedY);
                if (b == null) continue;
                boolean firstEnd = f != otherFloor ? f < otherFloor : (a.y != b.y ? a.y < b.y : a.x < b.x);
                if (!firstEnd) continue;
                links.add(new int[]{f, a.x, a.y, a.dirX, a.dirY, b.x, b.y, b.dirX, b.dirY, 0, 0, otherFloor});
            }
        }
        return links;
    }
    //endregion

    //region Nested Types
    public record Options(int width, int height, int floors, boolean looped, int finishMode, String seed) {}
    public record Result(List<int[][]> floors, List<int[]> portalLinks, String seed) {}
    //endregion
}