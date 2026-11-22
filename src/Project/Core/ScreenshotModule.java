package Project.Core;

import Project.Audio.*;
import Project.Net.*;
import Project.Helpers.*;

import javax.imageio.ImageIO;
import java.awt.image.*;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class ScreenshotModule implements GameModule {
    //region Variables
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private final CoreKeyEdge keyEdge = new CoreKeyEdge();
    private volatile boolean pending;
    //endregion

    //region Identity
    @Override public String id() { return "screenshot"; }
    //endregion

    //region Input
    @Override public boolean onKey(GameContext context, java.awt.event.KeyEvent keyEvent, boolean down) {
        boolean matched = KeyBindings.matches(KeyBindings.Action.SCREENSHOT, keyEvent);
        if (keyEdge.firstPress(matched, down)) pending = true;
        return matched;
    }
    //endregion

    //region Frame
    @Override public void postProcess(GameContext context, int[] pixels, int width, int height) {
        if (!pending) return;
        pending = false;
        try {
            Path file = nextFile();
            save(pixels, width, height, file);
            Sound.play(Sound.Effect.SHUTTER);
            context.flash(NSLocalizedString.get("feat.screenshot.saved") + " " + file);
        }
        catch (IOException | RuntimeException exception) { context.flash(NSLocalizedString.get("feat.screenshot.failed")); }
    }
    //endregion

    //region Files
    private static Path nextFile() throws IOException {
        Path folder = AppPaths.DATA_DIR.resolve("screenshots");
        Files.createDirectories(folder);
        String stamp = LocalDateTime.now().format(STAMP);
        Path file = folder.resolve("rcjms-" + stamp + ".png");
        for (int n = 2; Files.exists(file); n++) file = folder.resolve("rcjms-" + stamp + "-" + n + ".png");
        return file;
    }
    private static void save(int[] pixels, int width, int height, Path file) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int[] target = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        System.arraycopy(pixels, 0, target, 0, Math.min(pixels.length, target.length));
        ImageIO.write(image, "png", file.toFile());
    }
    //endregion
}