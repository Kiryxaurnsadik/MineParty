package io.github.kiryxaurnsadik.mineparty.client;

import java.util.List;
import java.util.UUID;

import io.github.kiryxaurnsadik.mineparty.network.MPNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;

/** Badge for players with MineParty, plus the icons of every party the player is in. */
public final class Badges {
    public static final String MOD_BADGE = "◆";
    public static final int MOD_BADGE_COLOR = 0x55FFFF;

    private Badges() {
    }

    public static Component decorate(UUID uuid, Component name) {
        boolean modded = ClientPartyState.hasMod(uuid);
        List<MPNetwork.PartyIcon> icons = ClientPartyState.iconsOf(uuid);
        if (!modded && icons.isEmpty()) {
            return name;
        }
        MutableComponent result = Component.empty();
        if (modded) {
            TextColor badge = TextColor.fromRgb(MOD_BADGE_COLOR);
            result.append(Component.literal(MOD_BADGE + " ").withStyle(s -> s.withColor(badge)));
        }
        result.append(name);
        for (MPNetwork.PartyIcon icon : icons) {
            TextColor color = TextColor.fromRgb(icon.color());
            result.append(Component.literal(" " + icon.icon()).withStyle(s -> s.withColor(color)));
        }
        return result;
    }
}
