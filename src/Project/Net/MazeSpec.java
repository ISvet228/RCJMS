package Project.Net;

import Project.Helpers.maze.ClassicCarver;

public record MazeSpec(String seed, int width, int height, int floors, int geometry, boolean hasFloors, boolean fog, String algorithm, int finishMode, boolean horror) {
    public MazeSpec(String seed, int width, int height, int floors, int geometry, boolean hasFloors, boolean fog, String algorithm, int finishMode) {
        this(seed, width, height, floors, geometry, hasFloors, fog, algorithm, finishMode, false);
    }
    public MazeSpec(String seed, int width, int height, int floors, int geometry, boolean hasFloors, boolean fog) {
        this(seed, width, height, floors, geometry, hasFloors, fog, ClassicCarver.ID, 0, false);
    }
}