package io.github.kiryxaurnsadik.mineparty.party;

import net.minecraft.network.FriendlyByteBuf;

/**
 * How allies are marked in the world. Only the party leader can change it,
 * including whether markers are visible through walls.
 *
 * @param color RGB, or -1 to use the party colour
 */
public record MarkerSettings(String symbol, int color, boolean throughWalls, int maxDistance, boolean showName) {
    public static final MarkerSettings DEFAULT = new MarkerSettings("▼", -1, true, 1000, true);

    public MarkerSettings {
        if (!Limits.isValidIcon(symbol)) {
            symbol = DEFAULT.symbol();
        }
        maxDistance = Math.max(Limits.MIN_MARKER_DISTANCE, Math.min(Limits.MAX_MARKER_DISTANCE, maxDistance));
        if (color != -1) {
            color &= 0xFFFFFF;
        }
    }

    public static MarkerSettings read(FriendlyByteBuf buf) {
        return new MarkerSettings(buf.readUtf(8), buf.readInt(), buf.readBoolean(), buf.readVarInt(), buf.readBoolean());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(symbol, 8);
        buf.writeInt(color);
        buf.writeBoolean(throughWalls);
        buf.writeVarInt(maxDistance);
        buf.writeBoolean(showName);
    }

    public int effectiveColor(int partyColor) {
        return color == -1 ? partyColor : color;
    }
}
