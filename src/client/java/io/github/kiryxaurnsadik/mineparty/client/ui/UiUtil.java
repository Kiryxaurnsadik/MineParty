package io.github.kiryxaurnsadik.mineparty.client.ui;

import java.util.UUID;

import io.github.kiryxaurnsadik.mineparty.party.PartyData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;

public final class UiUtil {
    /** Translucent black, like the reference screenshots: the world stays visible behind the panel. */
    public static final int PANEL_BG = 0x8C000000;
    public static final int HEADER_BG = 0xB0000000;
    public static final int ROW_HOVER = 0x30FFFFFF;
    public static final int GOLD = 0xFFD700;
    public static final int SILVER = 0xC0C0C0;
    public static final String CROWN = "♛";

    /** The 16 vanilla chat colours. */
    public static final int[] PALETTE = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };
    public static final String[] PARTY_ICONS = {
            "⚔", "☠", "★", "✦", "❂", "⚑", "☘", "❤", "⌂", "⚒", "☀", "☾"
    };
    public static final String[] MARKER_SYMBOLS = {"▼", "●", "◆", "✦", "★", "✖", "✚", "▲"};

    private UiUtil() {
    }

    public static int argb(int rgb) {
        return 0xFF000000 | rgb;
    }

    public static int argb(int rgb, float alpha) {
        return ((int) (Math.max(0f, Math.min(1f, alpha)) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    public static void panel(GuiGraphics g, int x, int y, int w, int h, int accentRgb) {
        g.fill(x, y, x + w, y + h, PANEL_BG);
        int border = argb(accentRgb, 0.75f);
        g.fill(x, y, x + w, y + 1, border);
        g.fill(x, y + h - 1, x + w, y + h, border);
        g.fill(x, y, x + 1, y + h, border);
        g.fill(x + w - 1, y, x + w, y + h, border);
    }

    public static PlayerSkin skin(UUID uuid) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(uuid);
            if (info != null) {
                return info.getSkin();
            }
        }
        return DefaultPlayerSkin.get(uuid);
    }

    public static void head(GuiGraphics g, UUID uuid, int x, int y, int size) {
        PlayerFaceRenderer.draw(g, skin(uuid), x, y, size);
    }

    public static MutableComponent partyTitle(PartyData party) {
        TextColor color = TextColor.fromRgb(party.color());
        return Component.literal(party.icon() + " " + party.name())
                .withStyle(style -> style.withColor(color).withBold(party.bold()));
    }

    public static MutableComponent colored(String text, int rgb) {
        TextColor color = TextColor.fromRgb(rgb);
        return Component.literal(text).withStyle(style -> style.withColor(color));
    }

    public static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public static String formatDistance(double blocks) {
        if (blocks >= 1000) {
            return String.format(java.util.Locale.ROOT, "%.1fкм", blocks / 1000.0);
        }
        return (int) blocks + "м";
    }
}
