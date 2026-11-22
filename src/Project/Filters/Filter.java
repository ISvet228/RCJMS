package Project.Filters;

public interface Filter {
    String id();
    String nameKey();
    int order();
    void apply(int[] pixels, int width, int height, double time);
}