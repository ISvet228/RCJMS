package Project.Game;

import Project.Helpers.*;

import java.io.IOException;

final class GameConsoleCommands {
    //region Host
    interface Host {
        DevConsole console();
        boolean busy();
        boolean paused();
        void setPaused(boolean paused);
        boolean customMap();
        int mapWidth();
        int mapHeight();
        boolean hasFloors();
        int floorCount();
        int[][][] floorLayers();
        MazeGenerator.GeometryMode geometry();
        MazeGenerator.FinishMode finishMode();
        String seedText();
        RenderEngine currentEngine();
        String renderEngineId();
        void setRenderEngineId(String id);
        boolean openClAcceleration();
        void setOpenClAcceleration(boolean enabled);
        void setDetailedLog(boolean enabled);
        void restart(GameView.LevelSpec level);
    }
    //endregion

    private final Host host;

    GameConsoleCommands(Host host) { this.host = host; }

    //region Helpers
    private static RenderEngine findEngine(String name) {
        for (RenderEngine renderEngine : RenderEngines.all())
            if (renderEngine.id().equalsIgnoreCase(name) || renderEngine.displayName().equalsIgnoreCase(name)) return renderEngine;
        return null;
    }
    private static String nameOf(RenderEngine engine) {
        return engine == null ? "none" : engine.displayName();
    }
    static boolean resolveOpenClForSwap(boolean supported, Boolean requested, boolean savedOpenCl) {
        boolean wanted = requested != null ? requested : savedOpenCl;
        return wanted && supported;
    }
    private void reportGpuState(RenderEngine engine) {
        if (engine == null || !host.openClAcceleration() || !engine.hasOpenCL()) return;
        Project.OpenCl.GpuRuntime.Health health = Project.OpenCl.GpuRuntime.healthFor(engine.openClKey());
        if (health == Project.OpenCl.GpuRuntime.Health.UNAVAILABLE)
            host.console().log(DevConsole.Level.WARN, "GPU is not usable: " + Project.OpenCl.GpuRuntime.reasonFor(engine.openClKey()));
        else if (health == Project.OpenCl.GpuRuntime.Health.PROBING) host.console().log(DevConsole.Level.INFO, "GPU is still being checked");
    }
    private void persistSettings(String engineId, boolean openCl) {
        try {
            SaveData.Data data = SaveData.load();
            SaveData.saveSettings(data.language, data.theme, data.renderScale, data.vsync, engineId,
                    data.renderEngineQuality, data.windowWidth, data.windowHeight, data.fullscreen, openCl);
            host.console().log(DevConsole.Level.INFO, "saved to the settings file");
        } catch (IOException exception) { throw new IllegalArgumentException("could not save settings: " + exception.getMessage()); }
    }
    //endregion

    //region Commands
    void register() {
        host.console().register("--help", "--help", args -> {
            args.info("commands:");
            for (String line : host.console().helpLines()) args.info("  " + line);
            args.info("options start with a dash; -s saves a change to the settings file");
        });
        host.console().register("engine", "engine [<name>] [-l] [-on|-off](openCL) [-s]  (no name: current engine, -l: list engines)", this::engineCommand);
        host.console().register("log", "log [-s] [-c] [-f [-off]]  (-s: save the terminal to a file, -c: clear the terminal, -f: detailed logs on, -f -off: off)", this::logCommand);
        host.console().register("openCL", "openCL [-on|-off] [-s]  (no option: show the state)", this::openClCommand);
        host.console().register("pause", "pause", args -> {
            if (host.busy()) throw new IllegalArgumentException("the level is still loading");
            if (host.paused()) args.info("already paused");
            else host.setPaused(true);
        });
        host.console().alias("--p", "pause");
        host.console().register("resume", "resume", args -> {
            if (!host.paused()) args.info("the game is not paused");
            else host.setPaused(false);
        });
        host.console().alias("--r", "resume");
        host.console().register("restart", "restart [W [H]] [-fN] [-sSEED] [-g1|-g2|-g3]  (no options: same level)", this::restartCommand);
        host.console().alias("--rs", "restart");
        host.console().register("status", "status", args -> {
            RenderEngine engine = host.currentEngine();
            args.info("engine: " + (engine == null ? "none" : engine.id() + " (" + engine.displayName() + ")"));
            args.info("OpenCL acceleration: " + (host.openClAcceleration() ? "ON" : "OFF"));
            if (engine != null && host.openClAcceleration() && engine.hasOpenCL()) {
                Project.OpenCl.GpuRuntime.Health health = Project.OpenCl.GpuRuntime.healthFor(engine.openClKey());
                String reason = Project.OpenCl.GpuRuntime.reasonFor(engine.openClKey());
                args.info("GPU: " + health + (reason.isEmpty() ? "" : " (" + reason + ")"));
            }
            args.info("paused: " + (host.paused() ? "yes" : "no") + ", loading: " + (host.busy() ? "yes" : "no"));
        });
        host.console().alias("--s", "status");
        host.console().register("maze", "maze  (size, floors, finish, geometry, seed, finish position)", this::mazeCommand);
        host.console().alias("--m", "maze");
    }
    //endregion
    //region Command Handlers
    private void restartCommand(DevConsole.Args args) {
        if (host.busy()) throw new IllegalArgumentException("the level is still loading, try again");
        java.util.List<String> values = args.values();
        String floorsText = args.optionValue("-f");
        String seedText = args.optionValue("-s");
        String geometryText = args.optionValue("-g");
        if (values.isEmpty() && floorsText == null && seedText == null && geometryText == null) {
            host.restart(null);
            return;
        }
        if (host.customMap()) throw new IllegalArgumentException("a custom map keeps its own size: restart it without options");
        if (values.size() > 2) throw new IllegalArgumentException("restart takes a width and an optional height: restart 90 [70]");

        int width = values.isEmpty() ? host.mapWidth() : parseSize(values.get(0), "width");
        int height = values.size() == 2 ? parseSize(values.get(1), "height") : (values.isEmpty() ? host.mapHeight() : width);
        int floors = floorsText == null ? 1 : parseFloors(floorsText);
        String seed = seedText == null ? null : seedFrom(seedText, args);
        MazeGenerator.GeometryMode geometry = geometryText == null ? MazeGenerator.GeometryMode.EUCLIDEAN : geometryFrom(geometryText);

        RenderEngine engine = host.currentEngine();
        if (geometry == MazeGenerator.GeometryMode.WRONG && (engine == null || !engine.hasHyperbolicGeometry()))
            throw new IllegalArgumentException("engine " + nameOf(engine) + " does not support WRONG geometry (-g2)");
        if (geometry == MazeGenerator.GeometryMode.WRONG && host.openClAcceleration())
            args.warn("OpenCL is not used in WRONG geometry; it applies again in other geometries");
        if (engine != null && !engine.supportsMapSize(width, height))
            args.warn("engine " + engine.displayName() + " does not support " + width + "x" + height + ", another engine may draw the frame");
        args.info("restarting: " + width + "x" + height + ", " + (floors > 1 ? floors + " floors" : "one floor") + ", "
                + geometry + (seed != null ? ", seed " + seed : ", random seed"));
        host.restart(new GameView.LevelSpec(width, height, floors, seed, geometry));
    }
    private void mazeCommand(DevConsole.Args args) {
        boolean multiFloor = host.hasFloors();
        args.info("size: " + host.mapWidth() + " x " + host.mapHeight() + (multiFloor ? ", " + host.floorCount() + " floors" : ", one floor"));
        args.info("finish mode: " + host.finishMode());
        args.info("geometry: " + host.geometry());
        args.info("seed: " + (host.customMap() ? "none (custom map)" : host.seedText()));
        int[][][] layers = host.floorLayers();
        int found = 0;
        for (int f = 0; f < layers.length; f++)
            for (int y = 0; y < layers[f].length; y++)
                for (int x = 0; x < layers[f][y].length; x++)
                    if (layers[f][y][x] == 2) {
                        found++;
                        args.info("finish: " + (multiFloor ? "floor " + (f + 1) + ", " : "") + "x " + x + ", y " + y);
                    }
        if (found == 0) args.info("finish: none");
    }
    private void logCommand(DevConsole.Args args) {
        boolean save = args.flag("-s");
        boolean clear = args.flag("-c");
        boolean detailed = args.flag("-f");
        boolean off = args.flag("-off");
        if (detailed) {
            if (save || clear || !args.values().isEmpty()) throw new IllegalArgumentException("-f works alone, add -off to stop");
            host.setDetailedLog(!off);
            args.info("detailed log: " + (!off ? "ON (engine, geometry and frame timing are printed)" : "OFF"));
            return;
        }
        if (off) throw new IllegalArgumentException("-off only works with -f");
        if (!args.values().isEmpty()) throw new IllegalArgumentException("unexpected argument: " + args.values().getFirst());
        if (!save && !clear) {
            args.info("usage: log [-s] [-c]");
            return;
        }
        String text = host.console().transcript();
        java.nio.file.Path file = null;
        if (save) {
            String stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            file = AppPaths.file("log_" + stamp + ".txt");
            try { java.nio.file.Files.writeString(file, text, java.nio.charset.StandardCharsets.UTF_8); }
            catch (IOException exception) { throw new IllegalArgumentException("could not save the log: " + exception.getMessage()); }
        }
        if (clear) host.console().clearLog();
        if (file != null) args.info("terminal saved to " + file);
    }
    private void openClCommand(DevConsole.Args args) {
        boolean on = args.flag("-on");
        boolean off = args.flag("-off");
        boolean save = args.flag("-s");
        if (!args.values().isEmpty()) throw new IllegalArgumentException("unexpected argument: " + args.values().getFirst());
        if (on && off) throw new IllegalArgumentException("use either -on or -off, not both");
        RenderEngine engine = host.currentEngine();
        if (on && host.geometry() == MazeGenerator.GeometryMode.WRONG) {
            args.warn("OpenCL is not used in WRONG geometry: the GPU path renders only Euclidean and looped maps");
            throw new IllegalArgumentException("cannot turn OpenCL ON in WRONG geometry (-g2)");
        }
        if (!on && !off) {
            if (save) throw new IllegalArgumentException("-s needs -on or -off");
            args.info("OpenCL acceleration: " + (host.openClAcceleration() ? "ON" : "OFF"));
            reportGpuState(engine);
            return;
        }
        if (on && (engine == null || !engine.hasOpenCL()))
            throw new IllegalArgumentException("engine " + nameOf(engine) + " has no OpenCL path, OpenCL stays OFF");
        host.setOpenClAcceleration(on);
        args.info("OpenCL acceleration " + (on ? "ON" : "OFF"));
        reportGpuState(engine);
        if (save) persistSettings(host.renderEngineId(), on);
    }
    private void engineCommand(DevConsole.Args args) {
        boolean list = args.flag("-l");
        boolean on = args.flag("-on");
        boolean off = args.flag("-off");
        boolean save = args.flag("-s");
        java.util.List<String> values = args.values();
        RenderEngine current = host.currentEngine();

        if (list) {
            if (!values.isEmpty() || on || off || save) throw new IllegalArgumentException("-l only lists the engines");
            for (RenderEngine renderEngine : RenderEngines.all())
                args.info((renderEngine == current ? "* " : "  ") + renderEngine.id() + " = " + renderEngine.displayName() + (renderEngine.hasOpenCL() ? "  [OpenCL]" : ""));
            return;
        }
        if (values.isEmpty() && !on && !off && !save) {
            if (current == null) { args.info("engine: none"); return; }
            args.info("engine: " + current.id() + " (" + current.displayName() + "), OpenCL acceleration " + (host.openClAcceleration() ? "ON" : "OFF"));
            reportGpuState(current);
            return;
        }
        if (values.size() > 1) throw new IllegalArgumentException("too many arguments for engine: " + values.subList(1, values.size()));
        if (on && off) throw new IllegalArgumentException("use either -on or -off, not both");
        RenderEngine target = values.isEmpty() ? current : findEngine(values.getFirst());
        if (target == null && values.isEmpty()) throw new IllegalArgumentException("no engine is selected (use engine <name>, see engine -l)");
        if (target == null) throw new IllegalArgumentException("unknown engine: " + values.getFirst() + " (see engine -l)");
        Boolean requested = on ? Boolean.TRUE : off ? Boolean.FALSE : null;
        if (on && host.geometry() == MazeGenerator.GeometryMode.WRONG) {
            args.warn("OpenCL is not used in WRONG geometry: the GPU path renders only Euclidean and looped maps");
            throw new IllegalArgumentException("cannot turn OpenCL ON in WRONG geometry (-g2)");
        }

        host.setRenderEngineId(target.id());
        boolean savedOpenCl = SaveData.load().openCl;
        boolean resolved = resolveOpenClForSwap(target.hasOpenCL(), requested, savedOpenCl);
        if (requested != null && requested && !target.hasOpenCL())
            args.warn("engine " + target.displayName() + " has no OpenCL path, OpenCL stays OFF");
        else if (requested == null && savedOpenCl && !target.hasOpenCL())
            args.info("saved OpenCL setting is ON, but engine " + target.displayName() + " has no OpenCL path: OFF");
        host.setOpenClAcceleration(resolved);
        if (!target.supportsMapSize(host.mapWidth(), host.mapHeight()))
            args.warn("engine " + target.displayName() + " does not support this map size, another engine may draw the frame");
        args.info("engine: " + target.id() + " (" + target.displayName() + "), OpenCL acceleration " + (host.openClAcceleration() ? "ON" : "OFF"));
        reportGpuState(target);
        if (save) persistSettings(target.id(), host.openClAcceleration());
    }
    //endregion

    //region Argument Parsing
    private static int parseSize(String text, String what) {
        int value;
        try { value = Integer.parseInt(text); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException(what + " must be a number, got " + text); }
        if (value < 5 || value > 200) throw new IllegalArgumentException(what + " must be between 5 and 200");
        return value;
    }
    private static int parseFloors(String text) {
        int value;
        try { value = Integer.parseInt(text); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException("-f needs a number of floors, for example -f15"); }
        if (value != 1 && (value < 2 || value > 20)) throw new IllegalArgumentException("floors must be 1 or 2..20");
        return value;
    }
    private static String seedFrom(String text, DevConsole.Args args) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) throw new IllegalArgumentException("-s needs a seed, for example -sABC123");
        String seed = SeedUtil.normalize(trimmed);
        if (seed.length() < trimmed.replaceAll("\\s", "").length())
            args.warn("seed is longer than " + SeedUtil.LENGTH + " characters, shortened to " + seed);
        return seed;
    }
    private static MazeGenerator.GeometryMode geometryFrom(String text) {
        return switch (text.trim()) {
            case "1" -> MazeGenerator.GeometryMode.EUCLIDEAN;
            case "2" -> MazeGenerator.GeometryMode.WRONG;
            case "3" -> MazeGenerator.GeometryMode.LOOPED;
            default -> throw new IllegalArgumentException("geometry must be -g1 (Euclidean), -g2 (wrong) or -g3 (looped)");
        };
    }
    //endregion
}