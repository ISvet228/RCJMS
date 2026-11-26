package Project.Game;

import Project.RCJMS;
import Project.Helpers.*;

import java.awt.*;
import java.awt.image.BufferedImage;

final class FloorTransition {
    //region Timing
    private static final double STAIR_ANIMATION_SPEED = 1.0;
    private static final double STEP_DURATION = 1.6;
    private static final double CROSS_DURATION = 1.1;
    private static final double STEP_HEIGHT = 0.3;
    private static final double STEP_LEAN = 0.12;
    private static final double STEP_WALK = 0.3;
    private static final int STAIR_STEPS = 5;
    private static final double FOOTFALL = 0.03;
    private static final double LAND_SHAKE = 0.03;
    private static final double CAPTION_ROLL_DURATION = 0.5;
    private static final double CAPTION_HOLD_DURATION = 0.9;
    private static final double CAPTION_FADE_DURATION = 0.4;
    private static final Font CAPTION_FONT = FontCache.get(Font.MONOSPACED, Font.BOLD, 56);
    //endregion

    //region State
    private boolean active, switchApplied;
    private double time;
    private int from, to, direction;   // direction: +1 up, -1 down, 0 portal jump
    private double climbStartX, climbStartY;

    boolean active() { return active; }
    int toFloor() { return to; }
    boolean climbing() { return direction != 0 && !switchApplied; }
    boolean locksControls() { return active && direction != 0 && time < STEP_DURATION + CROSS_DURATION; }

    void beginClimb(int fromFloor, int toFloor, double startX, double startY) {
        from = fromFloor;
        to = toFloor;
        direction = toFloor > fromFloor ? 1 : -1;
        climbStartX = startX;
        climbStartY = startY;
        active = true;
        switchApplied = false;
        time = 0;
    }
    void beginPortal(int fromFloor, int toFloor) {
        from = fromFloor;
        to = toFloor;
        direction = 0;
        active = true;
        switchApplied = true;
        time = 0;
    }
    void end() {
        active = false;
        switchApplied = false;
        time = 0;
        direction = 0;
    }
    boolean advance(double dt) {
        time += dt * Math.max(0.05, STAIR_ANIMATION_SPEED);
        boolean switchNow = false;
        if (!switchApplied && time >= STEP_DURATION) {
            switchApplied = true;
            switchNow = true;
        }
        if (time >= totalDuration()) end();
        return switchNow;
    }
    //endregion

    //region Camera Offsets
    double climbX(double cameraAngle) {
        int cell = (int) Math.floor(climbStartX);
        return Math.clamp(climbStartX + Math.cos(cameraAngle) * walk(), cell + 0.15, cell + 0.85);
    }
    double climbY(double cameraAngle) {
        int cell = (int) Math.floor(climbStartY);
        return Math.clamp(climbStartY + Math.sin(cameraAngle) * walk(), cell + 0.15, cell + 0.85);
    }
    private double walk() { return STEP_WALK * smooth(stepPhase()); }
    double stepHeightOffset() {
        if (!active || direction == 0) return 0;
        if (!switchApplied) {
            double bob = FOOTFALL * Math.abs(Math.sin(Math.PI * STAIR_STEPS * stepPhase()));
            return direction * STEP_HEIGHT * smooth(stepPhase()) + bob;
        }
        return direction * STEP_HEIGHT * (1 - smooth(crossPhase()));
    }
    double stepPitchOffset() {
        if (!active || direction == 0) return 0;
        if (!switchApplied) return direction * STEP_LEAN * smooth(stepPhase());
        return direction * STEP_LEAN * (1 - smooth(crossPhase()));
    }
    double landingShake() {
        if (!active || !switchApplied || direction == 0) return 0;
        double t = crossPhase();
        if (t >= 1) return 0;
        return LAND_SHAKE * Math.exp(-4 * t) * Math.sin(26 * t);
    }
    private static double smooth(double t) { return t * t * (3 - 2 * t); }
    private double stepPhase() { return Math.clamp(time / STEP_DURATION, 0, 1); }
    private double crossPhase() { return Math.clamp((time - crossStart()) / CROSS_DURATION, 0, 1); }
    private double crossStart() { return direction == 0 ? 0 : STEP_DURATION; }
    private double totalDuration() {
        return direction == 0 ? CROSS_DURATION
                : STEP_DURATION + CROSS_DURATION + CAPTION_ROLL_DURATION + CAPTION_HOLD_DURATION + CAPTION_FADE_DURATION;
    }
    //endregion

    //region Drawing
    void drawEffects(BufferedImage image, int floorCount) {
        if (direction == 0) return;
        if (!switchApplied) {
            drawDarkness(image, smooth(stepPhase()));
            return;
        }
        drawDarkness(image, 1 - smooth(crossPhase()));
        double captionTime = time - (STEP_DURATION + CROSS_DURATION);
        if (captionTime >= 0 && captionTime < CAPTION_ROLL_DURATION + CAPTION_HOLD_DURATION + CAPTION_FADE_DURATION) drawFloorCaption(image, floorCount, captionTime);
    }
    private static void drawDarkness(BufferedImage image, double amount) {
        float alpha = (float) Math.clamp(amount, 0, 1);
        if (alpha <= 0.005f) return;
        Graphics2D g2d = image.createGraphics();
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, image.getWidth(), image.getHeight());
        g2d.dispose();
    }
    private void drawFloorCaption(BufferedImage image, int floorCount, double captionTime) {
        Graphics2D g2d = image.createGraphics();
        g2d.scale(GameView.uiScale(), GameView.uiScale());
        g2d.setFont(CAPTION_FONT);
        FontMetrics fontMetrics = g2d.getFontMetrics();

        float alpha;
        double rollProgress;

        if (captionTime < CAPTION_ROLL_DURATION) {
            alpha = Math.clamp((float) (captionTime / Math.max(0.001, CAPTION_ROLL_DURATION * 0.4)), 0f, 1f);
            rollProgress = 1f - Math.pow(1f - Math.clamp((float) (captionTime / CAPTION_ROLL_DURATION), 0f, 1f), 3);
        }
        else if (captionTime < (CAPTION_ROLL_DURATION + CAPTION_HOLD_DURATION)) {
            alpha = 1f;
            rollProgress = 1;
        }
        else {
            alpha = Math.clamp((float) (1.0 - (captionTime - (CAPTION_ROLL_DURATION + CAPTION_HOLD_DURATION)) / CAPTION_FADE_DURATION), 0f, 1f);
            rollProgress = 1;
        }

        String prefix = NSLocalizedString.get("floor") + ": ";
        String suffix = "/" + floorCount;
        String oldNumber = String.valueOf(from + 1);
        String newNumber = String.valueOf(to + 1);

        int prefixWidth = fontMetrics.stringWidth(prefix);
        int numWidth = Math.max(fontMetrics.stringWidth(oldNumber), fontMetrics.stringWidth(newNumber));

        int baseX = (RCJMS.SCREEN_WIDTH - (prefixWidth + numWidth +  fontMetrics.stringWidth(suffix))) / 2;
        int rowHeight = fontMetrics.getHeight();

        Composite oldComposite = g2d.getComposite();
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

        g2d.setColor(Color.BLACK);
        g2d.drawString(prefix, baseX + 2, 112);
        g2d.setColor(Color.WHITE);
        g2d.drawString(prefix, baseX, 110);

        int numX = baseX + prefixWidth;
        Shape oldClip = g2d.getClip();
        g2d.clipRect(numX, 110 - fontMetrics.getAscent(), numWidth, rowHeight);

        int slideOffset = (int) (rollProgress * rowHeight);
        g2d.setColor(Color.BLACK);
        g2d.drawString(oldNumber, numX + 2, 112 - slideOffset);
        g2d.setColor(Color.WHITE);
        g2d.drawString(oldNumber, numX, 110 - slideOffset);

        g2d.setColor(Color.BLACK);
        g2d.drawString(newNumber, numX + 2, 112 + rowHeight - slideOffset);
        g2d.setColor(Color.WHITE);
        g2d.drawString(newNumber, numX, 110 + rowHeight - slideOffset);

        g2d.setClip(oldClip);

        g2d.setColor(Color.BLACK);
        g2d.drawString(suffix, numX + numWidth + 2, 110 + 2);
        g2d.setColor(Color.WHITE);
        g2d.drawString(suffix, numX + numWidth, 110);

        g2d.setComposite(oldComposite);
        g2d.dispose();
    }
    //endregion
}