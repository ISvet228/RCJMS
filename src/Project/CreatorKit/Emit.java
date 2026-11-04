package Project.CreatorKit;
import Project.EngineCreator.*;
import Project.ThemeCreator.*;

import java.util.*;

public final class Emit {
    //region Variables
    //region Target Language
    public final boolean java;
    //endregion
    //region Node Ports
    public final int id;
    private final String[] in, out;
    private final DataType[] outTypes;
    //endregion
    //region Node Output
    private final Map<String, String> params;
    public final List<String> lines;
    //endregion
    //endregion

    //region Constructors
    public Emit(boolean java, int id, String[] in, String[] out, DataType[] outTypes, Map<String, String> params, List<String> lines) {
        this.java = java;
        this.id = id;
        this.in = in;
        this.out = out;
        this.outTypes = outTypes;
        this.params = params;
        this.lines = lines;
    }
    //endregion

    //region Ports
    public String in(int i) { return in[i]; }
    public String out(int i) { return out[i]; }
    public DataType outType(int i) { return outTypes[i]; }
    //endregion

    //region Type Names
    public String ft() { return java ? "double" : "float"; }
    public String bt() { return java ? "boolean" : "bool"; }
    public String typeName(DataType dataType) {
        return switch (dataType) {
            case FLOAT -> ft();
            case INT, COLOR -> "int";
            case BOOL -> bt();
            case ANY -> throw new IllegalStateException("type not resolved");
        };
    }
    //endregion

    //region Literals
    public String flit(double d) {
        String s = d + "f";
        return d < 0 ? "(" + s + ")" : s;
    }
    public String ilit(int v) { return v < 0 ? "(" + v + ")" : String.valueOf(v); }
    //endregion

    //region Parameters
    public String param(String key) { return params.get(key); }
    public double fparam(String key) { return Double.parseDouble(params.get(key).trim()); }
    public int iparam(String key) { return Integer.parseInt(params.get(key).trim()); }
    //endregion

    //region Code Lines
    public void line(String s) { lines.add(s); }
    public void decl(int i, String expr) { line(typeName(outTypes[i]) + " " + out[i] + " = " + expr + ";"); }
    //endregion
}