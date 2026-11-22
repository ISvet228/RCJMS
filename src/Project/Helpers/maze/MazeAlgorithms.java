package Project.Helpers.maze;

import Project.Helpers.*;

import java.util.List;

public final class MazeAlgorithms {
    //region Variables
    public static final String SETTING = "maze.algorithm";
    private static final List<MazeAlgorithm> ALL = List.of(new ClassicCarver(), new PrimCarver(), new KruskalCarver(), new EllerCarver(), new CaveCarver(), new RoomCarver());
    //endregion

    //region Constructors
    private MazeAlgorithms() {}
    //endregion

    //region Lookup
    public static List<MazeAlgorithm> all() { return ALL; }
    public static MazeAlgorithm find(String id) {
        for (MazeAlgorithm algorithm : ALL) if (algorithm.id().equals(id)) return algorithm;
        return ALL.getFirst();
    }
    public static MazeAlgorithm selected() {
        return find(Settings.getString(SETTING, ClassicCarver.ID));
    }
    //endregion
}