package io.github.kiryxaurnsadik.mineparty.client.render;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.github.kiryxaurnsadik.mineparty.client.ClientConfig;
import io.github.kiryxaurnsadik.mineparty.client.ClientPartyState;
import io.github.kiryxaurnsadik.mineparty.client.ui.UiUtil;
import io.github.kiryxaurnsadik.mineparty.network.MPNetwork;
import io.github.kiryxaurnsadik.mineparty.party.PartyData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Party HUD (top right by default): heads, crowns of the leader and deputy, health and gear.
 * Position, size and detail level are personal settings, edited in {@code HudEditorScreen}.
 */
public final class PartyHud {
    private static final int PADDING = 4;
    private static int lastX;
    private static int lastY;
    private static int lastW;
    private static int lastH;

    private record Row(UUID uuid, String name, boolean online, boolean leader, boolean deputy, float health,
                       float maxHealth, float absorption, List<ItemStack> armor) {
    }

    private PartyHud() {
    }

    /** Bounds of the HUD as last drawn, in GUI pixels: x, y, width, height. */
    public static int[] lastBounds() {
        return new int[] {lastX, lastY, lastW, lastH};
    }

    public static void render(GuiGraphics g, boolean preview) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || (mc.options.hideGui && !preview)) {
            return;
        }
        drawBanner(g, mc);

        PartyData party = ClientPartyState.active();
        if (party == null && !preview) {
            lastW = 0;
            lastH = 0;
            return;
        }
        List<Row> rows = party != null ? rows(mc, party) : sampleRows(mc.player);
        Component title = party != null ? UiUtil.partyTitle(party) : Component.literal("⚔ Пример отряда");
        int partyCount = ClientPartyState.parties().size();
        if (partyCount > 1) {
            title = title.copy().append(Component.literal("  " + (ClientPartyState.parties().indexOf(party) + 1) + "/" + partyCount)
                    .withStyle(s -> s.withColor(0xAAAAAA).withBold(false)));
        }

        ClientConfig cfg = ClientConfig.get();
        Font font = mc.font;
        int mode = Math.max(0, Math.min(2, cfg.hudMode));
        int width = mode == 0 ? 110 : mode == 1 ? 140 : 180;
        int rowH = mode == 0 ? 15 : mode == 1 ? 26 : 26 + 4 * 7;
        int height = PADDING * 2 + 11 + rows.size() * rowH;

        float scale = Math.max(0.5f, Math.min(2f, cfg.hudScale));
        int screenW = g.guiWidth();
        int x = (int) (screenW - cfg.hudRight - width * scale);
        int y = cfg.hudTop;
        lastX = x;
        lastY = y;
        lastW = (int) (width * scale);
        lastH = (int) (height * scale);

        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        g.fill(0, 0, width, height, UiUtil.argb(0, cfg.hudBackground));
        int accent = party != null ? party.color() : 0x55FFFF;
        g.fill(0, 0, width, 1, UiUtil.argb(accent, 0.8f));
        g.drawString(font, title, PADDING, PADDING, 0xFFFFFFFF, true);

        int ry = PADDING + 11;
        for (Row row : rows) {
            drawRow(g, font, row, PADDING, ry, width - PADDING * 2, mode);
            ry += rowH;
        }
        g.pose().popPose();
    }

    private static void drawBanner(GuiGraphics g, Minecraft mc) {
        List<String> lines = new ArrayList<>();
        if (ClientPartyState.inviteMode) {
            lines.add("Режим приглашения включён — СКМ по игроку");
        }
        if (ClientPartyState.partyChatMode) {
            lines.add("Чат: отряд");
        }
        int y = 4;
        for (String line : lines) {
            int lw = mc.font.width(line);
            int bx = g.guiWidth() / 2 - lw / 2;
            g.fill(bx - 4, y - 2, bx + lw + 4, y + 10, 0x90000000);
            g.drawString(mc.font, line, bx, y, 0xFFFFD23F, true);
            y += 14;
        }
    }

    private static void drawRow(GuiGraphics g, Font font, Row row, int x, int y, int w, int mode) {
        int headSize = mode == 0 ? 12 : 16;
        UiUtil.head(g, row.uuid(), x, y, headSize);
        if (!row.online()) {
            g.fill(x, y, x + headSize, y + headSize, 0xA0000000);
        }
        int tx = x + headSize + 4;
        int nameColor = row.online() ? 0xFFFFFFFF : 0xFF777777;
        if (row.leader() || row.deputy()) {
            g.drawString(font, UiUtil.CROWN, tx, y + 1, UiUtil.argb(row.leader() ? UiUtil.GOLD : UiUtil.SILVER), true);
            tx += font.width(UiUtil.CROWN) + 2;
        }
        g.drawString(font, row.name(), tx, y + 1, nameColor, true);

        if (!row.online()) {
            return;
        }
        boolean known = row.maxHealth() > 0;
        String hp = known ? (int) Math.ceil(row.health()) + "❤" : "?";
        g.drawString(font, hp, x + w - font.width(hp), y + 1, 0xFFFF5555, true);

        int barX = x + headSize + 4;
        int barY = mode == 0 ? y + 11 : y + 11;
        int barW = mode == 0 ? w - headSize - 4 : 56;
        g.fill(barX, barY, barX + barW, barY + 3, 0xFF3A0000);
        if (known) {
            float total = row.maxHealth() + row.absorption();
            int hpW = (int) (barW * Math.min(1f, row.health() / total));
            int absW = (int) (barW * Math.min(1f, row.absorption() / total));
            g.fill(barX, barY, barX + hpW, barY + 3, 0xFFE03030);
            g.fill(barX + hpW, barY, barX + hpW + absW, barY + 3, 0xFFFFD23F);
        }
        if (mode == 0) {
            return;
        }

        int ix = barX + barW + 4;
        for (ItemStack stack : row.armor()) {
            if (!stack.isEmpty()) {
                g.pose().pushPose();
                g.pose().translate(ix, y + 9, 0);
                g.pose().scale(0.625f, 0.625f, 1f);
                g.renderItem(stack, 0, 0);
                g.renderItemDecorations(font, stack, 0, 0);
                g.pose().popPose();
            }
            ix += 11;
        }
        if (mode == 2) {
            int ny = y + 22;
            for (ItemStack stack : row.armor()) {
                if (!stack.isEmpty()) {
                    g.pose().pushPose();
                    g.pose().translate(barX, ny, 0);
                    g.pose().scale(0.7f, 0.7f, 1f);
                    g.drawString(font, stack.getHoverName(), 0, 0, 0xFFCCCCCC, false);
                    g.pose().popPose();
                    ny += 7;
                }
            }
        }
    }

    private static List<Row> rows(Minecraft mc, PartyData party) {
        List<Row> rows = new ArrayList<>();
        LocalPlayer self = mc.player;
        for (PartyData.Member member : party.members()) {
            boolean leader = party.isLeader(member.uuid());
            boolean deputy = party.isDeputy(member.uuid());
            if (member.uuid().equals(self.getUUID())) {
                rows.add(new Row(member.uuid(), member.name(), true, leader, deputy, self.getHealth(), self.getMaxHealth(),
                        self.getAbsorptionAmount(), armorOf(self)));
                continue;
            }
            MPNetwork.MemberStatus status = ClientPartyState.status(member.uuid());
            if (status != null) {
                rows.add(new Row(member.uuid(), member.name(), member.online(), leader, deputy, status.health(),
                        status.maxHealth(), status.absorption(), status.gear().subList(0, 4)));
            } else {
                rows.add(new Row(member.uuid(), member.name(), member.online(), leader, deputy, 0, 0, 0, List.of()));
            }
        }
        return rows;
    }

    private static List<ItemStack> armorOf(LocalPlayer player) {
        return List.of(player.getItemBySlot(EquipmentSlot.HEAD), player.getItemBySlot(EquipmentSlot.CHEST),
                player.getItemBySlot(EquipmentSlot.LEGS), player.getItemBySlot(EquipmentSlot.FEET));
    }

    private static List<Row> sampleRows(LocalPlayer self) {
        return List.of(
                new Row(self.getUUID(), self.getGameProfile().getName(), true, true, false, self.getHealth(),
                        self.getMaxHealth(), self.getAbsorptionAmount(), armorOf(self)),
                new Row(new UUID(1, 1), "Steve", true, false, true, 14, 20, 4, List.of()),
                new Row(new UUID(2, 2), "Alex", false, false, false, 0, 0, 0, List.of()));
    }
}
