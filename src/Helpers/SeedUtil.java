package Helpers;

import java.util.concurrent.ThreadLocalRandom;

public final class SeedUtil {
    //region Variables
    public static final int LENGTH = 7;
    private static final String RANDOM_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String PACKED_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    //endregion

    //region Constructors
    private SeedUtil() {}
    //endregion

    //region Public API
    public static String randomSeed() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        StringBuilder seed = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) seed.append(RANDOM_ALPHABET.charAt(random.nextInt(RANDOM_ALPHABET.length())));
        return seed.toString();
    }
    public static String normalize(String text) {
        if (text == null) return "";
        StringBuilder seed = new StringBuilder(LENGTH);
        for (int i = 0; i < text.length() && seed.length() < LENGTH; i++)
            if (!Character.isWhitespace(text.charAt(i))) seed.append(text.charAt(i));
        return seed.toString();
    }
    public static boolean isAllowedChar(int codePoint) {
        return !Character.isWhitespace(codePoint);
    }
    public static long toLong(String seedText) {
        String seed = normalize(seedText);
        if (isPackable(seed)) return pack(seed);
        long hash = 1125899906842597L;
        for (int i = 0; i < seed.length(); i++) hash = 31 * hash + seed.charAt(i);
        return hash;
    }
    //endregion

    //region Helpers
    private static boolean isPackable(String seed) {
        for (int i = 0; i < seed.length(); i++) if (PACKED_ALPHABET.indexOf(seed.charAt(i)) < 0) return false;
        return true;
    }
    private static long pack(String seed) {
        long value = seed.length();
        for (int i = 0; i < seed.length(); i++) value = value * PACKED_ALPHABET.length() + PACKED_ALPHABET.indexOf(seed.charAt(i));
        return value;
    }
    //endregion
}