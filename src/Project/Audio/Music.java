package Project.Audio;

import Project.Helpers.Settings;

import javax.sound.sampled.*;
import java.util.Random;
import java.util.concurrent.*;

public final class Music {
    //region Variables
    private static final int LOOP_SECONDS = 32;
    private static final int LOOP = LOOP_SECONDS * Sound.RATE;
    private static final int[] ROOTS = { 45, 47, 48, 50 };
    private static final int[] PENTA_MAJOR = { 0, 2, 4, 7, 9 };
    private static final int[] PENTA_MINOR = { 0, 3, 5, 7, 10 };
    private static final int[] PHRYGIAN = { 0, 1, 3, 5, 7, 8, 10 };
    private static final float BASE_GAIN = 0.3f;
    private static final String VOLUME_KEY = "music.volume";
    private static final int DEFAULT_VOLUME = 35;
    private static final long LEASE_NANOS = 2_000_000_000L;
    private static final long WATCH_MILLIS = 250;
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "rcjms-music");
        thread.setDaemon(true);
        return thread;
    });
    private static String wanted;
    private static boolean leased;
    private static String cachedKey;
    private static byte[] cachedPcm;
    private static Clip clip;
    private static boolean paused;
    private static int pausedFrame;
    private static volatile long lastBeat;
    private static Thread watchdog;
    //endregion

    //region Styles
    public enum Style {
        CALM(32),
        CREDITS(48),
        MAZE(64),
        TEXT(48),
        DOOM(64),
        NIGHT(32),
        DOOM_NIGHT(64);
        private final int beats;
        Style(int beats) { this.beats = beats; }
        public int beats() { return beats; }
    }
    //endregion

    //region Constructors
    private Music() {}
    //endregion

    //region Control
    public static String key(Style style, String seed) { return style.name() + '|' + seed; }
    public static synchronized void show(String key) {
        heartbeat();
        ensureWatchdog();
        if (key == null) { stop(); return; }
        play(key, true);
    }
    public static synchronized void showScene(Style style) {
        ensureWatchdog();
        play(key(style, "scene"), false);
    }
    public static synchronized void stop() {
        wanted = null;
        leased = false;
        paused = false;
        stopClip();
    }
    public static synchronized void pause() {
        if (paused) return;
        paused = true;
        if (clip == null) return;
        pausedFrame = clip.getFramePosition();
        clip.stop();
    }
    public static synchronized void resume() {
        if (!paused) return;
        paused = false;
        heartbeat();
        if (clip == null) return;
        try {
            clip.setFramePosition(pausedFrame);
            clip.loop(Clip.LOOP_CONTINUOUSLY);
            clip.start();
        }
        catch (RuntimeException ignored) { }
    }
    public static synchronized void muteChanged(boolean muted) {
        if (muted) { stopClip(); return; }
        String key = wanted;
        if (key != null) WORKER.execute(() -> prepare(key));
    }
    public static void volumeChanged() {
        WORKER.execute(Music::restartAtVolume);
    }
    public static void heartbeat() { lastBeat = System.nanoTime(); }

    private static void play(String key, boolean game) {
        if (key.equals(wanted) && game == leased) return;
        wanted = key;
        leased = game;
        stopClip();
        WORKER.execute(() -> prepare(key));
    }

    private static void prepare(String key) {
        try {
            byte[] pcm = pcmFor(key);
            synchronized (Music.class) {
                if (key.equals(wanted) && !Sound.isMuted()) startClip(pcm, 0);
            }
        }
        catch (RuntimeException ignored) { }
    }
    private static void restartAtVolume() {
        try {
            synchronized (Music.class) {
                if (clip == null || wanted == null || !wanted.equals(cachedKey)) return;
                startClip(cachedPcm, clip.getFramePosition());
            }
        }
        catch (RuntimeException ignored) { }
    }

    private static byte[] pcmFor(String key) {
        synchronized (Music.class) { if (key.equals(cachedKey)) return cachedPcm; }
        byte[] pcm = render(key, BASE_GAIN);
        synchronized (Music.class) { cachedKey = key; cachedPcm = pcm; }
        return pcm;
    }

    private static synchronized void startClip(byte[] pcm, int framePosition) {
        stopClip();
        try {
            AudioFormat format = new AudioFormat(Sound.RATE, 16, 1, true, false);
            byte[] scaled = Sound.scale(pcm, volume());
            Clip next = AudioSystem.getClip();
            next.open(format, scaled, 0, scaled.length);
            next.setFramePosition(Math.floorMod(framePosition, Math.max(1, scaled.length / 2)));
            clip = next;
            if (paused) {
                pausedFrame = next.getFramePosition();
                return;
            }
            next.loop(Clip.LOOP_CONTINUOUSLY);
            next.start();
        }
        catch (Exception | LinkageError ignored) {
            clip = null;
        }
    }

    private static synchronized void stopClip() {
        if (clip == null) return;
        try { clip.stop(); clip.close(); }
        catch (RuntimeException ignored) { }
        clip = null;
    }
    public static float volume() {
        int percent = Math.clamp(Settings.getInt(VOLUME_KEY, DEFAULT_VOLUME), 0, 100);
        return percent / 100f;
    }
    private static synchronized void ensureWatchdog() {
        if (watchdog != null) return;
        watchdog = new Thread(Music::watchLease, "rcjms-music-watch");
        watchdog.setDaemon(true);
        watchdog.start();
    }
    private static void watchLease() {
        while (true) {
            try { Thread.sleep(WATCH_MILLIS); }
            catch (InterruptedException ignored) { return; }
            synchronized (Music.class) { if (wanted != null && leased && !paused && System.nanoTime() - lastBeat > LEASE_NANOS) stop(); }
        }
    }
    //endregion

    //region Composition
    public static byte[] render(String key, float gain) {
        int bar = key.indexOf('|');
        Style style = bar < 0 ? Style.MAZE : Style.valueOf(key.substring(0, bar));
        Random random = new Random(hash(key));
        boolean doomStyle = style == Style.DOOM || style == Style.DOOM_NIGHT;
        double[] mix = new double[doomStyle ? doomLength() : LOOP];
        switch (style) {
            case CALM -> calm(mix, random);
            case CREDITS -> credits(mix, random);
            case MAZE -> maze(mix, random);
            case TEXT -> text(mix, random);
            case DOOM -> doom(mix, random);
            case NIGHT -> night(mix, random);
            case DOOM_NIGHT -> {
                double[] riff = new double[mix.length];
                double[] night = new double[mix.length];
                doom(riff, random);
                night(night, random);
                for (int i = 0; i < mix.length; i++) mix[i] = 0.55 * riff[i] + night[i];
            }
        }
        if (doomStyle) limitLoud(mix, DOOM_LOUDNESS);
        else normalize(mix, gain);
        return Sound.toPcm(mix);
    }
    private static void calm(double[] mix, Random random) {
        int beats = Style.CALM.beats(), beat = LOOP / beats;
        int root = pickRoot(random);
        int[][] chords = { {0, 4, 7}, {-3, 0, 4}, {5, 9, 12}, {7, 11, 14} };
        for (int c = 0; c < chords.length; c++)
            for (int offset : chords[c]) pad(mix, c * 8 * beat, 8 * beat, root + offset, 0.10);
        for (int b = 0; b < beats; b += 4) {
            if (random.nextDouble() < 0.5) continue;
            int midi = root + (random.nextBoolean() ? 24 : 12) + PENTA_MAJOR[random.nextInt(PENTA_MAJOR.length)];
            pluck(mix, b * beat, 4 * beat, midi, 0.12);
        }
    }
    private static void credits(double[] mix, Random random) {
        int beats = Style.CREDITS.beats(), beat = LOOP / beats, eighth = beat / 2;
        int root = pickRoot(random);
        int[][] chords = { {0, 4, 7}, {7, 11, 14}, {9, 12, 16}, {5, 9, 12} };
        for (int e = 0; e < beats * 2; e++) {
            int[] tones = chords[(e / 24) % chords.length];
            int midi = root + 12 + tones[e % 3] + 12 * ((e / 3) % 2);
            pluck(mix, e * eighth, 2 * eighth, midi, 0.15);
        }
        for (int c = 0; c < chords.length; c++) pad(mix, c * 24 * eighth, 24 * eighth, root - 12 + chords[c][0], 0.12);
    }
    private static void maze(double[] mix, Random random) {
        int beats = Style.MAZE.beats(), beat = LOOP / beats, eighth = beat / 2;
        int root = pickRoot(random);
        int[][] chords = { {0, 3, 7}, {-4, 0, 3}, {-2, 2, 5}, {-5, -2, 2} };
        for (int c = 0; c < chords.length; c++)
            for (int offset : chords[c]) pad(mix, c * 16 * beat, 16 * beat, root + offset, 0.07);
        for (int b = 0; b < beats; b += 8)
            pluck(mix, b * beat, 8 * beat, root - 12 + (b % 16 == 8 ? 7 : 0), 0.18);
        int degree = 2;
        for (int e = 0; e < beats * 2; e += 4) {
            if (random.nextDouble() < 0.4) continue;
            degree = Math.clamp(degree, -2, PENTA_MINOR.length * 2 + 1);
            pluck(mix, e * eighth, 4 * eighth, root + 12 + Music.PENTA_MINOR[Math.floorMod(degree, Music.PENTA_MINOR.length)] + 12 * Math.floorDiv(degree, Music.PENTA_MINOR.length), 0.16);
        }
    }
    private static void text(double[] mix, Random random) {
        int beats = Style.TEXT.beats(), beat = LOOP / beats, eighth = beat / 2;
        int root = pickRoot(random);
        for (int e = 0; e < beats * 2; e++) {
            click(mix, random, e * eighth, 0.035);
            if (random.nextDouble() < 0.4) continue;
            int midi = root + 12 + PHRYGIAN[random.nextInt(PHRYGIAN.length)] + (random.nextBoolean() ? 12 : 0);
            marimba(mix, e * eighth, 3 * eighth, midi, 0.2);
        }
    }
    private static final double DOOM_LOUDNESS = 0.36, LIMIT_DRIVE = 1.25;
    private static void doom(double[] mix, Random random) { DoomSynth.render(mix, random); }
    private static int doomLength() { return DoomSynth.loopSamples(); }
    private static void night(double[] mix, Random random) {
        int beats = Style.NIGHT.beats(), beat = mix.length / beats;
        int root = pickRoot(random) - 12;
        int[][] chords = { {0, 3, 7, 14}, {-2, 3, 7, 10}, {-4, 0, 3, 7}, {-5, -2, 2, 7} };
        for (int c = 0; c < chords.length; c++)
            for (int offset : chords[c]) pad(mix, c * 8 * beat, 8 * beat, root + offset, 0.07);
        wind(mix, random, 0.15);
        for (int b = 0; b < beats; b++) {
            if (random.nextDouble() < 0.15)
                pluck(mix, b * beat + random.nextInt(beat), 2 * beat, root + 24 + PENTA_MINOR[random.nextInt(PENTA_MINOR.length)], 0.06);
        }
        for (int c = 0; c < 14; c++) {
            int start = random.nextInt(mix.length - Sound.RATE);
            int count = 2 + random.nextInt(3);
            double frequency = 3900 + random.nextInt(800);
            for (int p = 0; p < count; p++) chirp(mix, start + p * (int) (0.35 * Sound.RATE), frequency, 0.05);
        }
        owl(mix, random.nextInt(mix.length - Sound.RATE), 0.05);
        owl(mix, random.nextInt(mix.length - Sound.RATE), 0.04);
    }
    private static int pickRoot(Random random) { return ROOTS[random.nextInt(ROOTS.length)]; }
    private static double frequency(int midi) { return 440 * Math.pow(2, (midi - 69) / 12.0); }
    private static void pluck(double[] mix, int start, int length, int midi, double amp) {
        double f = frequency(midi);
        double attack = 0.005 * Sound.RATE;
        for (int i = 0; i < length && start + i < mix.length; i++) {
            double t = (start + i) / (double) Sound.RATE;
            double env = Math.min(1, i / attack) * Math.exp(-3.0 * i / length) * (1 - i / (double) length);
            double wave = Math.sin(2 * Math.PI * f * t) + 0.5 * Math.sin(4 * Math.PI * f * t) + 0.25 * Math.sin(6 * Math.PI * f * t);
            mix[start + i] += amp * env * wave;
        }
    }
    private static void marimba(double[] mix, int start, int length, int midi, double amp) {
        double f = frequency(midi);
        for (int i = 0; i < length && start + i < mix.length; i++) {
            double t = (start + i) / (double) Sound.RATE;
            double env = Math.exp(-6.0 * i / length) * (1 - i / (double) length);
            double wave = Math.sin(2 * Math.PI * f * t) + 0.3 * Math.sin(8 * Math.PI * f * t);
            mix[start + i] += amp * env * wave;
        }
    }
    private static void pad(double[] mix, int start, int length, int midi, double amp) {
        double f = frequency(midi);
        for (int i = 0; i < length && start + i < mix.length; i++) {
            double t = (start + i) / (double) Sound.RATE;
            double env = Math.pow(Math.sin(Math.PI * i / length), 2);
            double wave = Math.sin(2 * Math.PI * f * t) + 0.6 * Math.sin(2 * Math.PI * f * 1.004 * t) + 0.2 * Math.sin(4 * Math.PI * f * t);
            mix[start + i] += amp * env * wave;
        }
    }
    private static void click(double[] mix, Random random, int start, double amp) {
        int length = (int) (0.01 * Sound.RATE);
        for (int i = 0; i < length && start + i < mix.length; i++) {
            double t = i / (double) Sound.RATE;
            mix[start + i] += amp * Math.exp(-300 * t) * (random.nextDouble() * 2 - 1);
        }
    }
    private static void chirp(double[] mix, int start, double frequency, double amp) {
        int pulse = (int) (0.022 * Sound.RATE), gap = (int) (0.06 * Sound.RATE);
        for (int p = 0; p < 4; p++) {
            int from = start + p * gap;
            for (int i = 0; i < pulse && from + i < mix.length; i++) {
                double env = Math.pow(Math.sin(Math.PI * i / pulse), 2);
                mix[from + i] += amp * env * Math.sin(2 * Math.PI * frequency * i / Sound.RATE);
            }
        }
    }
    private static void owl(double[] mix, int start, double amp) {
        int length = (int) (0.9 * Sound.RATE);
        double phase = 0;
        for (int i = 0; i < length && start + i < mix.length; i++) {
            phase += 2 * Math.PI * (380 - 80 * (i / (double) length)) / Sound.RATE;
            double env = Math.pow(Math.sin(Math.PI * i / length), 2);
            mix[start + i] += amp * env * Math.sin(phase);
        }
    }
    private static void wind(double[] mix, Random random, double amp) {
        double[] noise = new double[mix.length];
        for (int i = 0; i < noise.length; i++) noise[i] = random.nextGaussian();
        double y = 0;
        for (int i = 0; i < 2 * noise.length; i++) {
            int index = i % noise.length;
            y += 0.02 * (noise[index] - y);
            if (i < noise.length) continue;
            double swell = 0.6 + 0.4 * Math.sin(2 * Math.PI * index / mix.length);
            mix[index] += amp * swell * y;
        }
    }
    private static void limitLoud(double[] mix, double targetRms) {
        double sum = 0;
        for (double value : mix) sum += value * value;
        double rms = Math.sqrt(sum / mix.length);
        if (rms <= 0) return;
        double scale = targetRms / rms * LIMIT_DRIVE;
        for (int i = 0; i < mix.length; i++) mix[i] = Math.tanh(mix[i] * scale);
    }
    private static void normalize(double[] mix, float gain) {
        double peak = 0;
        for (double value : mix) peak = Math.max(peak, Math.abs(value));
        if (peak <= 0) return;
        double scale = gain / peak;
        for (int i = 0; i < mix.length; i++) mix[i] *= scale;
    }
    private static long hash(String key) {
        long hash = 1125899973951L;
        for (int i = 0; i < key.length(); i++) hash = hash * 31 + key.charAt(i);
        return hash;
    }
    //endregion
}