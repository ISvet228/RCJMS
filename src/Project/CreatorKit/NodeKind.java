package Project.CreatorKit;
import Project.EngineCreator.*;

import java.util.*;

public record NodeKind(String id, String categoryId, List<Port> inputs, List<Port> outputs, List<Param> params, Emitter emitter) {
    //region Display
    public String title() { return CustomNodes.isCustom(id) ? CustomNodes.titleOf(id) : L.t("ec.n." + id); }
    public String help() { return CustomNodes.isCustom(id) ? CustomNodes.helpOf(id) : L.t("ec.n." + id + ".help"); }
    public String category() { return L.t("ec.c." + categoryId); }
    //endregion

    //region Port Lookup
    public boolean isOutput() { return id.equals("output"); }
    public int inputIndex(String name) {
        for (int i = 0; i < inputs.size(); i++) if (inputs.get(i).name().equals(name)) return i;
        return -1;
    }
    public int outputIndex(String name) {
        for (int i = 0; i < outputs.size(); i++) if (outputs.get(i).name().equals(name)) return i;
        return -1;
    }
    public Param param(String key) {
        for (Param param : params) if (param.key().equals(key)) return param;
        return null;
    }
    //endregion
}