package io.github.kiryxaurnsadik.mineparty.party;

public enum PingType {
    WARNING("⚠", 0xFFD23F),
    DANGER("!", 0xFF4040),
    HERE("↓", 0x4CD964);

    public final String symbol;
    public final int color;

    PingType(String symbol, int color) {
        this.symbol = symbol;
        this.color = color;
    }
}
