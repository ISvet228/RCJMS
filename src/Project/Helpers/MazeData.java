package Project.Helpers;

import Project.Helpers.maze.MazeAlgorithms;

public record MazeData(String seed, int width, int height, int floors, boolean hasFloors, int geometry, String algorithm,
                       int finishMode, int[][][] cells, PortalData[] portals) {
    public static MazeData generate(String seed, int width, int height, int floors, int geometry, boolean hasFloors, String algorithm, int finishMode) {
        String text = SeedUtil.normalize(seed);
        long value = SeedUtil.toLong(text);
        MazeAlgorithm chosen = MazeAlgorithms.find(algorithm);
        MazeGenerator.GeometryMode mode = MazeGenerator.GeometryMode.values()[Math.clamp(geometry, 0, MazeGenerator.GeometryMode.values().length - 1)];
        MazeGenerator.FinishMode finish = MazeGenerator.FinishMode.values()[Math.clamp(finishMode, 0, MazeGenerator.FinishMode.values().length - 1)];
        if (hasFloors) {
            int count = Math.max(2, floors);
            MazeGenerator3D generator = new MazeGenerator3D(width, height, count, value, mode).useAlgorithm(chosen);
            int[][][] cells = generator.generate(finish);
            PortalData[] portalsOf = new PortalData[count];
            for (int f = 0; f < count; f++) portalsOf[f] = generator.getPortals(f);
            return new MazeData(text, cells[0][0].length, cells[0].length, count, true, geometry, chosen.id(), finish.ordinal(), cells, portalsOf);
        }
        MazeGenerator generator = new MazeGenerator(width, height, value, mode).useAlgorithm(chosen);
        int[][] map = generator.generate(finish);
        return new MazeData(text, map[0].length, map.length, 1, false, geometry, chosen.id(), finish.ordinal(), new int[][][]{map}, new PortalData[]{generator.getPortals()});
    }
}