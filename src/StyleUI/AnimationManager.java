package StyleUI;

import javax.swing.*;
import java.util.LinkedHashSet;
import java.util.Set;

final class AnimationManager {
    private static final Set<Runnable> tasks = new LinkedHashSet<>();
    private static final Timer timer = new Timer(16, e -> tick());

    static { timer.setCoalesce(true); timer.setRepeats(true); }
    static void register(Runnable task) {
        if (task == null) return;
        tasks.add(task);
        if (!timer.isRunning()) timer.start();
    }
    static void unregister(Runnable task) {
        if (task == null) return;
        tasks.remove(task);
        if (tasks.isEmpty()) timer.stop();
    }
    private static void tick() {
        if (tasks.isEmpty()) {
            timer.stop();
            return;
        }
        for (Runnable task : tasks.toArray(Runnable[]::new)) task.run();
    }
}