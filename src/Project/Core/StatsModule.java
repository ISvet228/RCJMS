package Project.Core;

import Project.Net.*;
import Project.Helpers.*;

import java.awt.*;
import java.awt.event.KeyEvent;

public final class StatsModule implements GameModule {
    //region Variables
    private static final Font FONT = FontCache.get(Font.MONOSPACED, Font.BOLD, 14);
    private final CoreKeyEdge keyEdge = new CoreKeyEdge();
    private final CoreRun run = new CoreRun();
    private boolean visible;
    //endregion

    //region Identity
    @Override public String id() { return "stats"; }
    //endregion

    //region Run Lifecycle
    @Override public void onRunStart(GameContext context) {
        run.begin(context);
        CoreSession.runStarted();
    }
    @Override public void onRunFinish(GameContext context) {
        if (!run.isRunning()) return;
        run.end();
        CoreSession.runFinished(run.seconds());
    }
    //endregion

    //region Input and Simulation
    @Override public boolean onKey(GameContext context, KeyEvent keyEvent, boolean down) {
        boolean matched = KeyBindings.matches(KeyBindings.Action.STATS, keyEvent);
        if (keyEdge.firstPress(matched, down)) visible = !visible;
        return matched;
    }
    @Override public void update(GameContext context, double seconds) { CoreSession.addSteps(run.advance(context, seconds)); }
    //endregion

    //region Painting
    @Override public void paintHud(GameContext context, Graphics2D g2d, int width, int height) {
        if (!visible) return;
        String[] lines = {
                NSLocalizedString.get("feat.stats.title"),
                NSLocalizedString.get("feat.stats.runs") + ": " + CoreSession.runs(),
                NSLocalizedString.get("feat.stats.finishes") + ": " + CoreSession.finishes(),
                NSLocalizedString.get("feat.stats.session") + ": " + CoreRun.clock(CoreSession.sessionSeconds()),
                NSLocalizedString.get("feat.stats.best") + ": " + bestText(),
                NSLocalizedString.get("feat.stats.steps") + ": " + CoreSession.steps()
        };
        drawPanel(g2d, lines, width - 250, 56);
    }
    private static String bestText() {
        double best = CoreSession.bestSeconds();
        return Double.isNaN(best) ? "-" : CoreRun.clock(best);
    }
    private static void drawPanel(Graphics2D g2d, String[] lines, int x, int y) {
        g2d.setFont(FONT);
        FontMetrics metrics = g2d.getFontMetrics();
        int widest = 0;
        for (String line : lines) widest = Math.max(widest, metrics.stringWidth(line));
        int lineHeight = metrics.getHeight();
        g2d.setColor(new Color(0, 0, 0, 150));
        g2d.fillRoundRect(x - 10, y - 4, widest + 20, lines.length * lineHeight + 12, 10, 10);
        g2d.setColor(new Color(200, 235, 255));
        for (int i = 0; i < lines.length; i++) g2d.drawString(lines[i], x, y + metrics.getAscent() + i * lineHeight);
    }
    //endregion
}
