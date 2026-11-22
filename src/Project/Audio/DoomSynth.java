package Project.Audio;

import java.util.*;

final class DoomSynth {
    //region Variables
    private static final double SECONDS_PER_TICK = 60.0 / (DoomScore.BPM * DoomScore.TICKS_PER_QUARTER);
    private static final double BEND_RANGE_SEMITONES = 2.0;
    private static final double GUITAR_BASE = 0.55, BASS_BASE = 0.6, DRUM_BASE = 0.7;
    private static final double RATE = Sound.RATE;
    //endregion

    //region Loop
    static int loopSamples() { return (int) Math.round(DoomScore.LAST_TICK * SECONDS_PER_TICK * RATE); }
    //endregion

    //region Rendering
    /** Renders the whole score into mix (one loop), with the given random source for the drums' noise. */
    static void render(double[] mix, Random random) {
        int count = 0;
        String[] items = DoomScore.NOTES.split(";");
        int[] start = new int[items.length], length = new int[items.length], channel = new int[items.length];
        int[] key = new int[items.length], velocity = new int[items.length];
        for (String item : items) {
            String[] f = item.split(",");
            start[count] = (int) Math.round(Long.parseLong(f[0]) * SECONDS_PER_TICK * RATE);
            length[count] = (int) Math.round(Long.parseLong(f[1]) * SECONDS_PER_TICK * RATE);
            channel[count] = Integer.parseInt(f[2]);
            key[count] = Integer.parseInt(f[3]);
            velocity[count] = Integer.parseInt(f[4]);
            count++;
        }
        BendTrack[] bends = BendTrack.build();
        double[] volume = channelVolumes();
        int[] change = new int[mix.length + 1];
        for (int n = 0; n < count; n++) {
            if (start[n] >= mix.length) continue;
            double amp = velocity[n] / 127.0;
            int ch = channel[n];
            int used;
            if (ch == 9) {
                used = drum(mix, start[n], key[n], amp * DRUM_BASE * volume[9], random);
            } else {
                BendTrack bend = bends[ch];
                int tail = (int) (0.04 * RATE);
                int span = Math.min(length[n] + tail, mix.length - start[n]);
                used = span;
                voice(mix, start[n], span, ch, key[n], amp * volume[ch] * (ch == 2 ? BASS_BASE : GUITAR_BASE), bend);
            }
            int end = Math.min(mix.length, start[n] + used);
            change[start[n]]++;
            change[end]--;
        }
        int sounding = 0;
        for (int i = 0; i < mix.length; i++) {
            sounding += change[i];
            if (sounding > 1) mix[i] /= Math.sqrt(sounding);
        }
        int fade = (int) (0.006 * RATE);
        for (int i = 0; i < fade && i < mix.length; i++) {
            double g = i / (double) fade;
            mix[i] *= g;
            mix[mix.length - 1 - i] *= g;
        }
    }
    private static double[] channelVolumes() {
        double[] volume = new double[10];
        java.util.Arrays.fill(volume, 1.0);
        for (String item : DoomScore.VOLUMES.split(";")) {
            String[] f = item.split(",");
            if (f.length < 3) continue;
            int ch = Integer.parseInt(f[0]);
            volume[ch] = Integer.parseInt(f[2]) / 127.0;
        }
        return volume;
    }
    //endregion

    //region Instruments
    private static void voice(double[] mix, int start, int length, int ch, int key, double amp, BendTrack bend) {
        double attack = 0.004 * RATE, release = 0.03 * RATE;
        boolean bass = ch == 2;
        double phase = 0;
        double base = 440.0 * Math.pow(2, (key - 69) / 12.0);
        double lastSemitones = Double.NaN, ratio = 1;
        for (int i = 0; i < length && start + i < mix.length; i++) {
            int index = start + i;
            double semitones = bend.at(index);
            if (semitones != lastSemitones) { ratio = Math.pow(2, semitones / 12.0); lastSemitones = semitones; }
            phase += 2 * Math.PI * base * ratio / RATE;
            double env = Math.min(1, i / attack) * Math.min(1, (length - i) / release);
            double tone = bass
                    ? Math.sin(phase) + 0.3 * Math.sin(2 * phase) + 0.08 * Math.sin(3 * phase)
                    : saw(phase);
            mix[index] += amp * env * (bass ? tone * 0.6 : driven(tone));
        }
    }
    private static double saw(double phase) {
        double sum = 0;
        for (int n = 1; n <= 6; n++) sum += Math.sin(n * phase) / n;
        return sum * 0.6;
    }
    private static double driven(double x) {
        double y = 3.0 * x;
        return y / (1 + Math.abs(y)) / 1.2;
    }
    private static int drum(double[] mix, int start, int key, double amp, Random random) {
        int length;
        if (key == 35 || key == 36) length = kick(mix, start, 45, amp);
        else if (key == 38 || key == 40) length = snare(mix, start, amp, random);
        else if (key == 41 || key == 43 || key == 45 || key == 47 || key == 48 || key == 50) length = tom(mix, start, key, amp);
        else if (key == 42 || key == 44) length = hat(mix, start, 0.04, amp, random);
        else if (key == 46) length = hat(mix, start, 0.22, amp, random);
        else if (key == 49 || key == 57) length = cymbal(mix, start, 1.1, amp, random);
        else length = cymbal(mix, start, 0.5, amp * 0.6, random);
        return length;
    }
    private static int kick(double[] mix, int start, double base, double amp) {
        int length = (int) (0.35 * RATE);
        double phase = 0;
        for (int i = 0; i < length && start + i < mix.length; i++) {
            double t = i / RATE;
            double f = base + 110 * Math.exp(-t / 0.03);
            phase += 2 * Math.PI * f / RATE;
            mix[start + i] += amp * Math.exp(-t / 0.12) * Math.sin(phase);
        }
        return length;
    }
    private static int snare(double[] mix, int start, double amp, Random random) {
        int length = (int) (0.25 * RATE);
        double phase = 0;
        for (int i = 0; i < length && start + i < mix.length; i++) {
            double t = i / RATE;
            phase += 2 * Math.PI * 190 / RATE;
            double tone = Math.sin(phase) * Math.exp(-t / 0.05) * 0.5;
            double noise = (random.nextDouble() * 2 - 1) * Math.exp(-t / 0.09) * 0.6;
            mix[start + i] += amp * (tone + noise);
        }
        return length;
    }
    private static int tom(double[] mix, int start, int key, double amp) {
        int length = (int) (0.35 * RATE);
        double base = 110 * Math.pow(2, (key - 45) / 12.0);
        double phase = 0;
        for (int i = 0; i < length && start + i < mix.length; i++) {
            double t = i / RATE;
            phase += 2 * Math.PI * (base + 40 * Math.exp(-t / 0.05)) / RATE;
            mix[start + i] += amp * 0.8 * Math.exp(-t / 0.18) * Math.sin(phase);
        }
        return length;
    }
    private static int hat(double[] mix, int start, double decay, double amp, Random random) {
        int length = (int) (decay * 5 * RATE);
        double previous = 0;
        for (int i = 0; i < length && start + i < mix.length; i++) {
            double t = i / RATE;
            double noise = random.nextDouble() * 2 - 1;
            double bright = noise - previous; // a difference filter keeps the high part of the noise
            previous = noise;
            mix[start + i] += amp * 0.5 * bright * Math.exp(-t / decay);
        }
        return length;
    }
    private static int cymbal(double[] mix, int start, double decay, double amp, Random random) {
        int length = (int) (decay * 4 * RATE);
        double previous = 0;
        for (int i = 0; i < length && start + i < mix.length; i++) {
            double t = i / RATE;
            double noise = random.nextDouble() * 2 - 1;
            double bright = noise - previous;
            previous = noise;
            mix[start + i] += amp * 0.4 * bright * Math.exp(-t / decay);
        }
        return length;
    }
    //endregion

    //region Bend Track
        private record BendTrack(int[] samples, double[] semitones) {
        static BendTrack[] build() {
                List<int[]> rows = new ArrayList<>();
                for (String item : DoomScore.BENDS.split(";")) {
                    String[] f = item.split(",");
                    if (f.length < 3) continue;
                    rows.add(new int[]{Integer.parseInt(f[0]), (int) Math.round(Long.parseLong(f[1]) * SECONDS_PER_TICK * RATE), Integer.parseInt(f[2])});
                }
                BendTrack[] tracks = new BendTrack[10];
                for (int ch = 0; ch < 10; ch++) {
                    List<int[]> mine = new ArrayList<>();
                    for (int[] row : rows) if (row[0] == ch) mine.add(row);
                    mine.sort(Comparator.comparingInt(row -> row[1]));
                    int[] samples = new int[mine.size()];
                    double[] values = new double[mine.size()];
                    for (int i = 0; i < mine.size(); i++) {
                        samples[i] = mine.get(i)[1];
                        values[i] = mine.get(i)[2] / 8192.0 * BEND_RANGE_SEMITONES;
                    }
                    tracks[ch] = new BendTrack(samples, values);
                }
                return tracks;
            }

        double at(int sample) {
                int low = 0, high = samples.length - 1, found = -1;
                while (low <= high) {
                    int mid = (low + high) >>> 1;
                    if (samples[mid] <= sample) {
                        found = mid;
                        low = mid + 1;
                    } else high = mid - 1;
                }
                return found < 0 ? 0 : semitones[found];
            }
        }
    //endregion
}