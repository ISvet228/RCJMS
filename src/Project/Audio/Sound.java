package Project.Audio;

import Project.Helpers.Settings;

import javax.sound.sampled.*;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class Sound {
    //region Effects
    public enum Effect { STEP, BUMP, PICKUP, HIT, FINISH, UNLOCK, SHUTTER, CLICK, SCREAM, HEARTBEAT, HUNTER_STEP, SCREECH }
    //endregion

    //region Variables
    public static final int RATE = 44100;
    private static final String MUTE_KEY = "sound.muted";
    private static final String VOLUME_KEY = "sound.volume";
    private static final int QUEUE_LIMIT = 12;
    private static final int CHUNK_BYTES = 2048;
    private static final Map<Effect, byte[]> CACHE = new EnumMap<>(Effect.class);
    private static final BlockingQueue<byte[]> QUEUE = new ArrayBlockingQueue<>(QUEUE_LIMIT);
    private static final AtomicInteger GENERATION = new AtomicInteger();
    private static volatile Boolean muted;
    private static volatile SourceDataLine activeLine;
    private static volatile boolean audioBroken;
    private static volatile boolean runActive;
    private static Thread player;
    //endregion

    //region Constructors
    private Sound() {}
    //endregion

    //region Mute
    public static boolean isMuted() {
        Boolean value = muted;
        if (value == null) {
            value = Settings.getBoolean(MUTE_KEY, false);
            muted = value;
        }
        return value;
    }
    public static void setMuted(boolean value) {
        synchronized (Sound.class) {
            muted = value;
            try { Settings.set(MUTE_KEY, String.valueOf(value)); }
            catch (RuntimeException ignored) { }
        }
        if (value) silence();
        Music.muteChanged(value);
    }
    private static void silence() {
        GENERATION.incrementAndGet();
        QUEUE.clear();
        SourceDataLine line = activeLine;
        if (line != null) {
            try { line.flush(); }
            catch (RuntimeException ignored) { }
        }
    }
    public static float effectLevel() {
        return Math.clamp(Settings.getInt(VOLUME_KEY, 100), 0, 100) / 100f;
    }
    //endregion

    //region Playback
    public static void play(Effect effect) {
        try {
            if (effect == null || !runActive || isMuted() || audioBroken) return;
            QUEUE.offer(scale(samples(effect), effectLevel()));
            ensurePlayer();
        }
        catch (RuntimeException ignored) { }
    }

    public static void clearQueue() { QUEUE.clear(); }
    public static void setRunActive(boolean active) { runActive = active; }
    public static synchronized byte[] samples(Effect effect) {
        return CACHE.computeIfAbsent(effect, Sound::build);
    }
    public static byte[] scale(byte[] pcm, float level) {
        float gain = Math.clamp(level, 0, 1);
        byte[] out = new byte[pcm.length];
        for (int i = 0; i + 1 < pcm.length; i += 2) {
            int value = (short) ((pcm[i] & 0xFF) | (pcm[i + 1] << 8));
            int scaled = Math.round(value * gain);
            out[i] = (byte) (scaled & 0xFF);
            out[i + 1] = (byte) ((scaled >> 8) & 0xFF);
        }
        return out;
    }
    private static synchronized void ensurePlayer() {
        if (player != null) return;
        player = new Thread(Sound::playLoop, "rcjms-sound");
        player.setDaemon(true);
        player.start();
    }
    private static void playLoop() {
        SourceDataLine line = null;
        try {
            while (true) {
                byte[] data = QUEUE.take();
                int generation = GENERATION.get();
                if (isMuted()) continue;
                if (line == null) {
                    line = openLine();
                    activeLine = line;
                }
                if (line == null) { audioBroken = true; QUEUE.clear(); return; }
                writeChunks(line, data, generation);
            }
        }
        catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        catch (RuntimeException ignored) { audioBroken = true; }
        finally {
            activeLine = null;
            if (line != null) { line.stop(); line.flush(); line.close(); }
            synchronized (Sound.class) { player = null; }
        }
    }
    private static void writeChunks(SourceDataLine line, byte[] data, int generation) {
        for (int offset = 0; offset < data.length; offset += CHUNK_BYTES) {
            if (isMuted() || GENERATION.get() != generation) return;
            line.write(data, offset, Math.min(CHUNK_BYTES, data.length - offset));
        }
    }

    private static SourceDataLine openLine() {
        try {
            AudioFormat format = new AudioFormat(RATE, 16, 1, true, false);
            SourceDataLine line = AudioSystem.getSourceDataLine(format);
            line.open(format, RATE / 4);
            line.start();
            return line;
        }
        catch (Exception | LinkageError exception) { return null; }
    }
    //endregion

    //region Synthesis
    private interface Tone { double at(double t); }
    private static byte[] build(Effect effect) {
        return toPcm(render(length(effect), tone(effect)));
    }

    private static double length(Effect effect) {
        return switch (effect) {
            case STEP -> 0.05;
            case BUMP -> 0.12;
            case PICKUP -> 0.2;
            case HIT -> 0.28;
            case FINISH -> 0.9;
            case UNLOCK -> 0.45;
            case SHUTTER -> 0.14;
            case CLICK -> 0.02;
            case SCREAM -> 1.5;
            case HEARTBEAT -> 0.6;
            case HUNTER_STEP -> 0.24;
            case SCREECH -> 0.9;
        };
    }
    private static Tone tone(Effect effect) {
        Random random = new Random(effect.ordinal() + 7L);
        return switch (effect) {
            case STEP -> t -> 0.5 * (0.7 * Math.sin(TAU * 110 * t) * Math.exp(-t * 60) + 0.3 * noise(random) * Math.exp(-t * 90));
            case BUMP -> t -> 0.4 * (Math.signum(Math.sin(TAU * 90 * t)) * Math.exp(-t * 25) + 0.5 * noise(random) * Math.exp(-t * 40));
            case PICKUP -> t -> 0.45 * Math.sin(TAU * (700 + 900 * t / 0.2) * t) * Math.min(1, t / 0.005) * Math.exp(-t * 12);
            case HIT -> t -> 0.6 * noise(random) * Math.exp(-t * 14) * 0.6 + 0.6 * Math.sin(TAU * 70 * t) * Math.exp(-t * 10);
            case FINISH -> finishTone();
            case UNLOCK -> unlockTone();
            case SHUTTER -> shutterTone(random);
            case CLICK -> t -> 0.5 * Math.sin(TAU * 1800 * t) * Math.exp(-t * 200);
            case SCREAM -> screamTone(random);
            case HEARTBEAT -> heartbeatTone();
            case HUNTER_STEP -> t -> 0.7 * (Math.sin(TAU * 48 * t) + 0.5 * noise(random) * Math.exp(-t * 30)) * Math.exp(-t * 18);
            case SCREECH -> screechTone(random);
        };
    }

    private static Tone finishTone() {
        double[] notes = { 523.25, 659.25, 783.99, 1046.5 };
        return t -> {
            int index = Math.min(notes.length - 1, (int) (t / 0.16));
            double local = t - index * 0.16;
            double f = notes[index];
            return 0.35 * (Math.sin(TAU * f * local) + 0.3 * Math.sin(2 * TAU * f * local)) * Math.exp(-local * 6);
        };
    }

    private static Tone unlockTone() {
        return t -> {
            double local = t < 0.12 ? t : t - 0.12;
            double f = t < 0.12 ? 880 : 1320;
            return 0.4 * Math.sin(TAU * f * local) * Math.min(1, local / 0.005) * Math.exp(-local * 9);
        };
    }

    private static Tone shutterTone(Random random) {
        double[] state = { 0 };
        return t -> {
            state[0] = state[0] * 0.6 + noise(random) * 0.4;
            return 0.7 * state[0] * Math.min(1, t / 0.004) * Math.exp(-t * 20);
        };
    }
    private static Tone screamTone(Random random) {
        return t -> {
            double f = 1400 * Math.pow(0.35, t / 1.5) + 40 * Math.sin(TAU * 7 * t);
            double burst = t < 0.08 ? noise(random) : 0;
            return 0.45 * Math.sin(TAU * f * t) + 0.25 * Math.signum(Math.sin(TAU * f * t)) * Math.exp(-t * 1.5) + 0.7 * burst * Math.exp(-t * 6);
        };
    }
    private static Tone heartbeatTone() {
        return t -> {
            double first = Math.sin(TAU * 58 * t) * Math.exp(-t * 26);
            double second = Math.sin(TAU * 52 * (t - 0.22)) * Math.exp(-(t - 0.22) * 30) * (t >= 0.22 ? 0.75 : 0);
            return 0.8 * (first + second);
        };
    }
    private static Tone screechTone(Random random) {
        return t -> {
            double f = 2100 + 500 * Math.sin(TAU * 9 * t) + 400 * t;
            double grit = 0.25 * noise(random);
            return 0.35 * (Math.sin(TAU * f * t) + grit) * Math.min(1, t / 0.02) * (1 - t / 0.9);
        };
    }

    private static final double TAU = Math.PI * 2;
    private static double noise(Random random) { return random.nextDouble() * 2 - 1; }
    private static double[] render(double seconds, Tone tone) {
        int count = (int) Math.round(seconds * RATE);
        double[] out = new double[count];
        int fade = Math.max(1, (int) (0.003 * RATE));
        for (int i = 0; i < count; i++) {
            double edge = Math.min(1, (count - i) / (double) fade);
            out[i] = clamp(tone.at(i / (double) RATE)) * edge;
        }
        return out;
    }
    public static byte[] toPcm(double[] samples) {
        byte[] pcm = new byte[samples.length * 2];
        for (int i = 0; i < samples.length; i++) {
            int value = (int) Math.round(clamp(samples[i]) * 32767);
            pcm[2 * i] = (byte) (value & 0xFF);
            pcm[2 * i + 1] = (byte) ((value >> 8) & 0xFF);
        }
        return pcm;
    }
    private static double clamp(double value) { return Math.clamp(value, -1, 1); }
    //endregion
}