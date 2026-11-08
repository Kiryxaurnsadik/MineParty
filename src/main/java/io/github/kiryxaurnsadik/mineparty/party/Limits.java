package io.github.kiryxaurnsadik.mineparty.party;

/** Hard limits shared by the server and the client UI. */
public final class Limits {
    public static final int MAX_MEMBERS = 15;
    public static final int MAX_PARTIES_PER_PLAYER = 3;
    public static final int MAX_NAME_LENGTH = 24;
    public static final int MAX_CHAT_LENGTH = 256;
    public static final int INVITE_TIMEOUT_TICKS = 20 * 60;
    public static final int MIN_MARKER_DISTANCE = 16;
    public static final int MAX_MARKER_DISTANCE = 5000;
    public static final double MAX_PING_DISTANCE = 300.0;

    private Limits() {
    }

    /** Accepts exactly one visible code point that is not a control or formatting character. */
    public static boolean isValidIcon(String icon) {
        if (icon == null || icon.isEmpty() || icon.codePointCount(0, icon.length()) != 1) {
            return false;
        }
        int cp = icon.codePointAt(0);
        int type = Character.getType(cp);
        return cp != '§' && !Character.isWhitespace(cp) && type != Character.CONTROL
                && type != Character.FORMAT && type != Character.UNASSIGNED
                && type != Character.SURROGATE && type != Character.PRIVATE_USE;
    }

    public static String cleanName(String name) {
        if (name == null) {
            return "";
        }
        String cleaned = name.replace("§", "").strip();
        if (cleaned.length() > MAX_NAME_LENGTH) {
            cleaned = cleaned.substring(0, MAX_NAME_LENGTH);
        }
        return cleaned;
    }
}
