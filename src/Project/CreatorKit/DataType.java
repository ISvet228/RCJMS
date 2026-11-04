package Project.CreatorKit;
import java.util.*;

public enum DataType {
    FLOAT, INT, BOOL, COLOR, ANY;
    public String label() { return L.t("ec.type." + name().toLowerCase(Locale.ROOT)); }
}