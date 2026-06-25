package bdj.hkb.urlShortner.util;

public class Base62Encoder {

    // The 62 URL-safe characters
    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int BASE = ALPHABET.length();

    /**
     * Converts a database ID (e.g., 10025) to a Base62 Short Code (e.g., "cL")
     */
    public static String encode(long id) {
        if (id == 0) {
            return String.valueOf(ALPHABET.charAt(0));
        }

        StringBuilder sb = new StringBuilder();
        while (id > 0) {
            sb.append(ALPHABET.charAt((int) (id % BASE)));
            id /= BASE;
        }

        // The algorithm calculates the string backwards, so we must reverse it
        return sb.reverse().toString();
    }

    /**
     * Converts a Base62 Short Code (e.g., "cL") back to a database ID (e.g., 10025).
     * This is useful if you ever want to look up a URL by decoding the string
     * mathematically rather than hitting a database index.
     */
    public static long decode(String shortCode) {
        long id = 0;
        for (int i = 0; i < shortCode.length(); i++) {
            id = id * BASE + ALPHABET.indexOf(shortCode.charAt(i));
        }
        return id;
    }
}
