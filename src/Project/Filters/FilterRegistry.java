package Project.Filters;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class FilterRegistry {
    //region Constructors
    private FilterRegistry() {}
    //endregion

    //region Registry
    public static List<Filter> create() {
        List<Filter> filters = new ArrayList<>(List.of(
                new Scanlines(),
                new Crt(),
                new Pixelate(),
                new Sepia(),
                new Invert(),
                new ChromaticAberration(),
                new NightVision(),
                new ThermalPalette(),
                new Posterize(),
                new Vignette(),
                new FilmGrain()
        ));
        filters.sort(Comparator.comparingInt(Filter::order));
        return filters;
    }
    //endregion
}