package io.github.kiryxaurnsadik.mineparty.client.render;

import java.util.UUID;

import io.github.kiryxaurnsadik.mineparty.client.ClientPartyState;
import io.github.kiryxaurnsadik.mineparty.client.ui.UiUtil;
import io.github.kiryxaurnsadik.mineparty.network.MPNetwork;
import io.github.kiryxaurnsadik.mineparty.party.MarkerSettings;
import io.github.kiryxaurnsadik.mineparty.party.PartyData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Ally markers and pings, projected onto the HUD. */
public final class WorldMarkers {
    private WorldMarkers() {
    }

    public static void render(GuiGraphics g, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) {
            return;
        }
        int w = g.guiWidth();
        int h = g.guiHeight();
        Vec3 cam = Projection.camera();
        String dimension = mc.level.dimension().location().toString();

        for (UUID ally : ClientPartyState.allies(mc.player.getUUID())) {
            PartyData party = ClientPartyState.markerPartyFor(ally);
            if (party == null) {
                continue;
            }
            MarkerSettings markers = party.markers();
            Vec3 pos;
            String name;
            Player entity = mc.level.getPlayerByUUID(ally);
            if (entity != null) {
                pos = entity.getPosition(partialTick).add(0, entity.getBbHeight() + 0.6, 0);
                name = entity.getGameProfile().getName();
            } else {
                MPNetwork.MemberStatus status = ClientPartyState.status(ally);
                if (status == null || !status.dimension().equals(dimension)) {
                    continue;
                }
                pos = new Vec3(status.x(), status.y() + 2.4, status.z());
                name = nameIn(party, ally);
            }
            double distance = pos.distanceTo(cam);
            if (distance > markers.maxDistance() || distance < 2.0) {
                continue;
            }
            if (!markers.throughWalls() && blocked(mc, cam, pos)) {
                continue;
            }
            float[] screen = Projection.toScreen(pos, w, h);
            if (screen == null) {
                continue;
            }
            int color = markers.effectiveColor(party.color());
            String label = markers.showName() ? name + " – " + UiUtil.formatDistance(distance) : null;
            drawMarker(g, mc.font, screen, w, h, markers.symbol(), color, label, 1f);
        }

        long now = System.currentTimeMillis();
        for (ClientPartyState.ActivePing active : ClientPartyState.pings()) {
            MPNetwork.PingBroadcast ping = active.ping();
            Vec3 pos = new Vec3(ping.x(), ping.y(), ping.z());
            float[] screen = Projection.toScreen(pos, w, h);
            if (screen == null) {
                continue;
            }
            long age = now - active.createdAt();
            float alpha = age > ClientPartyState.PING_LIFETIME_MS - 1500
                    ? (ClientPartyState.PING_LIFETIME_MS - age) / 1500f : 1f;
            String label = ping.fromName() + " · " + UiUtil.formatDistance(pos.distanceTo(cam));
            drawMarker(g, mc.font, screen, w, h, ping.pingType().symbol, ping.pingType().color, label, alpha);
        }
    }

    private static String nameIn(PartyData party, UUID uuid) {
        for (PartyData.Member member : party.members()) {
            if (member.uuid().equals(uuid)) {
                return member.name();
            }
        }
        return "?";
    }

    private static boolean blocked(Minecraft mc, Vec3 from, Vec3 to) {
        BlockHitResult hit = mc.level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
        return hit.getType() == HitResult.Type.BLOCK;
    }

    private static void drawMarker(GuiGraphics g, Font font, float[] screen, int w, int h, String symbol, int rgb,
                                   String label, float alpha) {
        if (alpha <= 0.05f) {
            return;
        }
        // Keep markers that are off to the side on the screen edge.
        float x = Math.max(12, Math.min(w - 12, screen[0]));
        float y = Math.max(12, Math.min(h - 20, screen[1]));
        int a = (int) (alpha * 255) << 24;

        TextColor color = TextColor.fromRgb(rgb);
        MutableComponent sym = Component.literal(symbol).withStyle(style -> style.withColor(color).withBold(true));
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(1.5f, 1.5f, 1f);
        g.drawString(font, sym, -font.width(sym) / 2, -font.lineHeight / 2, a | rgb, true);
        g.pose().popPose();

        if (label != null) {
            g.pose().pushPose();
            g.pose().translate(x, y + 9, 0);
            g.pose().scale(0.75f, 0.75f, 1f);
            int lw = font.width(label);
            g.fill(-lw / 2 - 2, -1, lw / 2 + 2, font.lineHeight, ((int) (alpha * 0x60) << 24));
            g.drawString(font, label, -lw / 2, 0, a | 0xFFFFFF, false);
            g.pose().popPose();
        }
    }
}
