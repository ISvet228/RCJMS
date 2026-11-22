package Project.Net;

import Project.Audio.*;
import Project.Core.*;
import Project.Roguelike.*;

import Project.Horror.HorrorModule;

import Project.Game.*;
import Project.Helpers.*;
import Project.Helpers.maze.MazeAlgorithms;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.*;
import java.util.List;

public final class NetModule implements GameModule {
    //region Variables
    private static final int MAX_PORT = 65535;
    private static final double FLASH_SECONDS = 1.5;
    private static final int MARGIN = 12, LINE_HEIGHT = 18;
    private static final Font TEXT_FONT = FontCache.get(Font.SANS_SERIF, Font.BOLD, 14);
    private static final Color TEXT_SHADOW = new Color(0, 0, 0, 200);

    private NetSession session;
    private boolean handingOff;
    private double finishSeconds = Double.NaN;
    private final Set<Integer> finishedSeen = new HashSet<>();
    private double flashLeft;
    //endregion

    //region Identity
    @Override public String id() { return "net"; }
    //endregion

    //region Console
    @Override public void onConsoleReady(GameContext context) {
        GameView.miniMapMarkers = NetModule::markersOnFloor;
        context.command("host", get("net.usage.host"), args -> host(context, args));
        context.command("join", get("net.usage.join"), args -> join(context, args));
        context.command("netstop", get("net.usage.stop"), this::stop);
        context.command("netstatus", get("net.usage.status"), this::status);
    }

    private void host(GameContext context, DevConsole.Args args) {
        List<String> values = args.values();
        int port = values.isEmpty() ? NetSettings.port() : portOf(values, 0);
        if (port < 0) { args.warn(get("net.usage.host")); return; }
        if (NetSession.current() != null) { args.warn(get("net.alreadyActive")); return; }
        if (context.isCustomMap()) { args.warn(get("net.customMap")); return; }
        try {
            session = NetSession.host(port, specOf(context));
            args.info(format("net.hosting", session.port()));
        } catch (IOException | RuntimeException exception) { args.warn(format("net.error", exception.getMessage())); }
    }

    private void join(GameContext context, DevConsole.Args args) {
        List<String> values = args.values();
        int port = portOf(values, 1);
        if (values.isEmpty() || port < 0) { args.warn(get("net.usage.join")); return; }
        if (NetSession.current() != null) { args.warn(get("net.alreadyActive")); return; }
        try {
            NetSession.Joined joined = NetSession.join(values.getFirst(), port);
            session = NetSession.current();
            context.useMaze(joined.maze(), joined.spec().fog());
            args.info(format("net.joined", values.getFirst(), port));
        } catch (IOException | RuntimeException exception) { args.warn(format("net.error", exception.getMessage())); }
    }

    private void stop(DevConsole.Args args) {
        NetSession current = NetSession.current();
        if (current == null) { args.warn(get("net.notActive")); return; }
        current.close();
        session = null;
        args.info(get("net.stopped"));
    }

    private void status(DevConsole.Args args) {
        NetSession current = NetSession.current();
        if (current == null) args.info(get("net.status.none"));
        else if (current.isHost()) args.info(format("net.status.host", current.port(), current.playerCount()));
        else args.info(format("net.status.client", current.playerCount()));
    }
    private static int portOf(List<String> values, int index) {
        if (values.size() <= index) return -1;
        try {
            int port = Integer.parseInt(values.get(index));
            return port >= 1 && port <= MAX_PORT ? port : -1;
        } catch (NumberFormatException exception) { return -1; }
    }
    //endregion

    //region Run Lifecycle
    @Override public void onRunStart(GameContext context) {
        finishSeconds = Double.NaN;
        flashLeft = 0;
        finishedSeen.clear();
        NetSession current = session(context);
        if (current == null) return;
        current.newRun();
        for (Rival rival : current.rivals()) {
            if (!Double.isNaN(rival.finishSeconds())) finishedSeen.add(rival.id());
        }
        if (!canonical(current.spec()).equals(specOf(context))) {
            matchRace(context, current);
            return;
        }
        publish(context, current);
    }
    private void matchRace(GameContext context, NetSession current) {
        if (handingOff || context.isCustomMap()) {
            leave(context, current);
            return;
        }
        handingOff = true;
        try { context.useMaze(current.maze(), current.spec().fog()); }
        catch (RuntimeException exception) { leave(context, current); }
        finally { handingOff = false; }
    }

    private void leave(GameContext context, NetSession current) {
        current.close();
        context.clearMaze();
        session = null;
        context.flash(get("net.mazeChanged"));
    }

    @Override public void onRunFinish(GameContext context) {
        finishSeconds = context.elapsedSeconds();
        NetSession current = session(context);
        if (current == null) return;
        publish(context, current);
        current.close();
        session = null;
        context.clearMaze();
    }
    //endregion

    //region Simulation
    @Override public void update(GameContext context, double seconds) {
        NetSession current = session(context);
        if (current == null) return;
        publish(context, current);
        flashLeft = Math.max(0, flashLeft - seconds);
        for (NetSession.Caught caught : current.takeCaught())
            if (caught.id() != current.localId()) context.flash(format("net.caughtFlash", playerName(caught.id())));
        if (!Double.isNaN(finishSeconds)) return;
        for (Rival rival : current.rivals()) {
            if (!Double.isNaN(rival.finishSeconds()) && finishedSeen.add(rival.id())) {
                flashLeft = FLASH_SECONDS;
                context.flash(format("net.finishedFirst", playerName(rival.id())));
            }
        }
    }
    private void publish(GameContext context, NetSession current) {
        current.publish(context.playerX(), context.playerY(), context.currentFloor(), context.cameraAngle(), finishSeconds);
    }
    //endregion

    //region Painting
    @Override public void paintHud(GameContext context, Graphics2D g2d, int width, int height) {
        NetSession current = session(context);
        if (current == null) return;
        if (NetSettings.showPlayers()) {
            for (Rival rival : current.rivals()) {
                Projection.Sprite sprite = rivalSprite(rival, context.currentFloor(), context.playerX(), context.playerY(),
                        context.cameraAngle(), context.cameraPitch(), context.eyeHeight(), width, height, context.map());
                if (sprite == null) continue;
                Color color = rivalColor(rival.id(), current);
                if (NetSettings.simplePlayerSprite()) RivalSprite.paint(g2d, sprite, color, playerName(rival.id()));
                else PlayerSprite.paint(g2d, sprite, rival, color, playerName(rival.id()), System.nanoTime() / 1e9);
            }
        }
        drawList(g2d, current);
    }
    @Override public boolean onKey(GameContext context, KeyEvent keyEvent, boolean down) {
        if (!down || !KeyBindings.matches(KeyBindings.Action.GHOST, keyEvent) || session(context) == null) return false;
        NetSettings.setShowPlayers(!NetSettings.showPlayers());
        return true;
    }
    static Projection.Sprite rivalSprite(Rival rival, int floor, double px, double py, double angle, double pitch,
                                         double eyeHeight, int width, int height, int[][] map) {
        if (rival.floor() != floor || !Double.isFinite(rival.x()) || !Double.isFinite(rival.y())) return null;
        return Projection.project(px, py, angle, pitch, eyeHeight, rival.x(), rival.y(), width, height, map);
    }

    private void drawList(Graphics2D g2d, NetSession current) {
        List<String> lines = new ArrayList<>();
        lines.add(format("net.players", current.playerCount()));
        for (Rival rival : current.rivals()) lines.add(rivalLine(rival, current));
        Graphics2D g = (Graphics2D) g2d.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(TEXT_FONT);
            int y = MARGIN + LINE_HEIGHT;
            for (String line : lines) {
                g.setColor(TEXT_SHADOW);
                g.drawString(line, MARGIN + 1, y + 1);
                g.setColor(Color.WHITE);
                g.drawString(line, MARGIN, y);
                y += LINE_HEIGHT;
            }
        } finally {
            g.dispose();
        }
    }
    private String rivalLine(Rival rival, NetSession current) {
        String name = playerName(rival.id());
        if (!Double.isNaN(rival.finishSeconds())) return format("net.rival.finished", name, rival.finishSeconds());
        return current.isCaught(rival.id()) ? format("net.rival.caught", name) : format("net.rival.running", name);
    }
    static List<GameView.MiniMapMarker> markersOnFloor(int floor) {
        NetSession current = NetSession.current();
        if (current == null || !NetSettings.showPlayers()) return List.of();
        List<GameView.MiniMapMarker> markers = new ArrayList<>();
        for (Rival rival : current.rivals()) {
            if (rival.floor() != floor || !Double.isFinite(rival.x()) || !Double.isFinite(rival.y())) continue;
            markers.add(new GameView.MiniMapMarker(rival.x(), rival.y(), rivalColor(rival.id(), current).getRGB(), playerName(rival.id())));
        }
        return markers;
    }
    private static final Color[] PLAYER_COLORS = {
            new Color(235, 70, 70), new Color(245, 140, 40), new Color(235, 210, 50), new Color(120, 200, 70),
            new Color(60, 200, 170), new Color(70, 140, 240), new Color(160, 90, 235), new Color(235, 90, 190)
    };
    private static Color rivalColor(int id, NetSession session) {
        List<Integer> active = new ArrayList<>();
        active.add(session.localId());
        for (Rival rival : session.rivals()) if (!active.contains(rival.id())) active.add(rival.id());
        java.util.Collections.sort(active);
        int rank = Math.max(0, active.indexOf(id));
        return PLAYER_COLORS[rank % PLAYER_COLORS.length];
    }
    //endregion

    //region Helpers
    private NetSession session(GameContext context) {
        if (session != null && session.isClosed()) {
            session = null;
            if (NetSession.current() == null) {
                context.clearMaze();
                context.flash(get("net.disconnected"));
            }
        }
        if (session == null) session = NetSession.current();
        return session;
    }

    private static MazeSpec specOf(GameContext context) {
        return new MazeSpec(context.seed(), context.mazeWidth(), context.mazeHeight(), context.floorCount(),
                context.geometry(), context.hasFloors(), context.fogOfWar(), context.algorithm(), context.finishMode(), HorrorModule.wanted());
    }
    private static MazeSpec canonical(MazeSpec spec) {
        int floors = spec.hasFloors() ? Math.max(2, spec.floors()) : 1;
        return new MazeSpec(SeedUtil.normalize(spec.seed()), spec.width(), spec.height(), floors, spec.geometry(), spec.hasFloors(), spec.fog(),
                MazeAlgorithms.find(spec.algorithm()).id(), spec.finishMode(), spec.horror());
    }
    private static String playerName(int id) { return format("net.player", id); }
    private static String get(String key) { return NSLocalizedString.get(key); }
    private static String format(String key, Object... args) { return NSLocalizedString.format(key, args); }
    //endregion
}