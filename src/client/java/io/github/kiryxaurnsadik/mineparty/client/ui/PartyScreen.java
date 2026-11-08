package io.github.kiryxaurnsadik.mineparty.client.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import io.github.kiryxaurnsadik.mineparty.client.ClientPartyState;
import io.github.kiryxaurnsadik.mineparty.network.MPNetwork;
import io.github.kiryxaurnsadik.mineparty.party.Limits;
import io.github.kiryxaurnsadik.mineparty.party.PartyData;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

/**
 * Party manager. Like the references: two translucent panels side by side,
 * "Players" on the left (with an "Invitations" tab), "Party" on the right.
 */
public class PartyScreen extends Screen {
    private static final int PANEL_W = 176;
    private static final int PANEL_H = 206;
    private static final int GAP = 12;
    private static final int HEADER_H = 18;
    private static final int ROW_H = 14;
    private static final int FOOTER_H = 26;

    private int leftX;
    private int rightX;
    private int top;
    private boolean invitesTab;
    private UUID selectedPlayer;
    private UUID selectedMember;
    private int playerScroll;
    private int memberScroll;

    public PartyScreen() {
        super(Component.literal("MineParty"));
    }

    private static void send(MPNetwork.Action action, UUID party, UUID target, String text) {
        ClientPlayNetworking.send(new MPNetwork.PartyAction(action, party, target, text));
    }

    private UUID self() {
        return minecraft.player.getUUID();
    }

    @Override
    protected void init() {
        int cx = width / 2;
        leftX = cx - GAP / 2 - PANEL_W;
        rightX = cx + GAP / 2;
        top = Math.max(20, height / 2 - PANEL_H / 2);
        int footerY = top + PANEL_H - FOOTER_H + 4;
        PartyData party = ClientPartyState.active();
        boolean online = ClientPartyState.serverHasMod();

        // Left panel footer
        if (!invitesTab) {
            Button add = Button.builder(Component.literal("Добавить"), b -> {
                if (party != null && selectedPlayer != null) {
                    send(MPNetwork.Action.INVITE, party.id(), selectedPlayer, null);
                }
            }).bounds(leftX + 6, footerY, PANEL_W - 12, 18).build();
            add.active = online && party != null && selectedPlayer != null
                    && (party.isLeader(self()) || party.isDeputy(self()))
                    && party.members().size() < Limits.MAX_MEMBERS;
            addRenderableWidget(add);
        }

        // Right panel
        if (ClientPartyState.parties().size() < Limits.MAX_PARTIES_PER_PLAYER) {
            Button create = Button.builder(Component.literal("+"), b -> send(MPNetwork.Action.CREATE, null, null, ""))
                    .bounds(rightX + PANEL_W - 17, top + 2, 14, 14).build();
            create.active = online;
            addRenderableWidget(create);
        }
        if (party == null) {
            Button create = Button.builder(Component.literal("Создать отряд"),
                    b -> send(MPNetwork.Action.CREATE, null, null, "")).bounds(rightX + 6, footerY, PANEL_W - 12, 18).build();
            create.active = online;
            addRenderableWidget(create);
            return;
        }

        boolean leader = party.isLeader(self());
        boolean deputy = party.isDeputy(self());
        boolean memberSelected = selectedMember != null && party.contains(selectedMember) && !selectedMember.equals(self());
        int bw = (PANEL_W - 12 - 4 - 20) / 2;

        Button kick = Button.builder(Component.literal("Убрать"),
                b -> send(MPNetwork.Action.KICK, party.id(), selectedMember, null)).bounds(rightX + 6, footerY, bw, 18).build();
        kick.active = memberSelected && (leader || (deputy && !party.isLeader(selectedMember)));
        addRenderableWidget(kick);

        addRenderableWidget(Button.builder(Component.literal("Покинуть"),
                b -> send(MPNetwork.Action.LEAVE, party.id(), null, null)).bounds(rightX + 8 + bw, footerY, bw, 18).build());

        Button settings = Button.builder(Component.literal("⚙"),
                b -> minecraft.setScreen(new PartySettingsScreen(this, party))).bounds(rightX + PANEL_W - 26, footerY, 20, 18).build();
        settings.active = leader;
        addRenderableWidget(settings);

        if (leader && memberSelected) {
            int y2 = footerY - 21;
            boolean isDeputy = party.isDeputy(selectedMember);
            addRenderableWidget(Button.builder(Component.literal(isDeputy ? "Снять зам." : "Заместитель"),
                    b -> send(MPNetwork.Action.SET_DEPUTY, party.id(), selectedMember, null))
                    .bounds(rightX + 6, y2, (PANEL_W - 14) / 2, 18).build());
            addRenderableWidget(Button.builder(Component.literal("Сделать лидером"),
                    b -> send(MPNetwork.Action.PROMOTE_LEADER, party.id(), selectedMember, null))
                    .bounds(rightX + 8 + (PANEL_W - 14) / 2, y2, (PANEL_W - 14) / 2, 18).build());
        }
    }

    /** Re-run init when the party changes on the server. */
    public void refresh() {
        rebuildWidgets();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // No blur and no darkening: the world stays visible, like the references.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        PartyData party = ClientPartyState.active();
        int accent = party != null ? party.color() : 0xFFFFFF;
        renderLeft(g, mouseX, mouseY);
        renderRight(g, mouseX, mouseY, party, accent);
        if (!ClientPartyState.serverHasMod()) {
            String msg = "На этом сервере нет MineParty (relay появится позже)";
            g.drawCenteredString(font, msg, width / 2, top - 12, 0xFFFF5555);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private List<PlayerInfo> players() {
        List<PlayerInfo> list = new ArrayList<>();
        if (minecraft.getConnection() != null) {
            for (PlayerInfo info : minecraft.getConnection().getOnlinePlayers()) {
                if (!info.getProfile().getId().equals(self())) {
                    list.add(info);
                }
            }
        }
        list.sort(Comparator.comparing(info -> info.getProfile().getName().toLowerCase(java.util.Locale.ROOT)));
        return list;
    }

    private int visibleRows(boolean right) {
        int extra = right && selectedMember != null ? 21 : 0;
        return (PANEL_H - HEADER_H - FOOTER_H - 4 - extra) / ROW_H;
    }

    private void renderLeft(GuiGraphics g, int mouseX, int mouseY) {
        UiUtil.panel(g, leftX, top, PANEL_W, PANEL_H, 0xFFFFFF);
        g.fill(leftX + 1, top + 1, leftX + PANEL_W - 1, top + HEADER_H, UiUtil.HEADER_BG);
        int half = PANEL_W / 2;
        String playersLabel = "Игроки";
        String invitesLabel = "Приглашения" + (ClientPartyState.invites().isEmpty() ? "" : " (" + ClientPartyState.invites().size() + ")");
        g.drawCenteredString(font, playersLabel, leftX + half / 2, top + 5, invitesTab ? 0xFF999999 : 0xFFFFFFFF);
        g.drawCenteredString(font, invitesLabel, leftX + half + half / 2, top + 5, invitesTab ? 0xFFFFFFFF : 0xFF999999);
        g.fill(invitesTab ? leftX + half : leftX + 1, top + HEADER_H - 2, invitesTab ? leftX + PANEL_W - 1 : leftX + half, top + HEADER_H, 0xFFFFFFFF);

        int y = top + HEADER_H + 2;
        int rows = visibleRows(false);
        if (!invitesTab) {
            List<PlayerInfo> players = players();
            playerScroll = Math.max(0, Math.min(playerScroll, players.size() - rows));
            for (int i = playerScroll; i < players.size() && i < playerScroll + rows; i++) {
                PlayerInfo info = players.get(i);
                UUID uuid = info.getProfile().getId();
                boolean hover = UiUtil.inside(mouseX, mouseY, leftX + 2, y, PANEL_W - 4, ROW_H);
                if (uuid.equals(selectedPlayer)) {
                    g.fill(leftX + 2, y, leftX + PANEL_W - 2, y + ROW_H, 0x60E04040);
                } else if (hover) {
                    g.fill(leftX + 2, y, leftX + PANEL_W - 2, y + ROW_H, UiUtil.ROW_HOVER);
                }
                UiUtil.head(g, uuid, leftX + 5, y + 3, 8);
                boolean modded = ClientPartyState.hasMod(uuid);
                int tx = leftX + 17;
                g.drawString(font, info.getProfile().getName(), tx, y + 3, modded ? 0xFFFFFFFF : 0xFF888888, false);
                tx += font.width(info.getProfile().getName()) + 4;
                for (MPNetwork.PartyIcon icon : ClientPartyState.iconsOf(uuid)) {
                    g.drawString(font, icon.icon(), tx, y + 3, UiUtil.argb(icon.color()), false);
                    tx += font.width(icon.icon()) + 2;
                }
                if (modded) {
                    g.drawString(font, "◆", leftX + PANEL_W - 12, y + 3, 0xFF55FFFF, false);
                }
                y += ROW_H;
            }
            if (players.isEmpty()) {
                g.drawCenteredString(font, "Нет других игроков", leftX + PANEL_W / 2, y + 4, 0xFF888888);
            }
        } else {
            List<MPNetwork.Invite> invites = ClientPartyState.invites();
            for (MPNetwork.Invite invite : invites) {
                if (y + ROW_H * 2 > top + PANEL_H - 4) {
                    break;
                }
                g.drawString(font, UiUtil.colored(invite.icon() + " " + invite.partyName(), invite.color()), leftX + 6, y + 2, 0xFFFFFFFF, false);
                g.drawString(font, "от " + invite.from(), leftX + 6, y + 13, 0xFF999999, false);
                drawSmallButton(g, leftX + PANEL_W - 40, y + 4, "✔", 0xFF55FF55, mouseX, mouseY);
                drawSmallButton(g, leftX + PANEL_W - 22, y + 4, "✖", 0xFFFF5555, mouseX, mouseY);
                y += ROW_H * 2;
            }
            if (invites.isEmpty()) {
                g.drawCenteredString(font, "Нет приглашений", leftX + PANEL_W / 2, y + 4, 0xFF888888);
            }
        }
    }

    private void drawSmallButton(GuiGraphics g, int x, int y, String label, int color, int mouseX, int mouseY) {
        boolean hover = UiUtil.inside(mouseX, mouseY, x, y, 16, 16);
        g.fill(x, y, x + 16, y + 16, hover ? 0x80FFFFFF : 0x40FFFFFF);
        g.drawCenteredString(font, label, x + 8, y + 4, color);
    }

    private void renderRight(GuiGraphics g, int mouseX, int mouseY, PartyData active, int accent) {
        UiUtil.panel(g, rightX, top, PANEL_W, PANEL_H, accent);
        g.fill(rightX + 1, top + 1, rightX + PANEL_W - 1, top + HEADER_H, UiUtil.HEADER_BG);
        List<PartyData> parties = ClientPartyState.parties();
        if (parties.isEmpty()) {
            g.drawCenteredString(font, "Отряд", rightX + PANEL_W / 2, top + 5, 0xFFFFFFFF);
            g.drawCenteredString(font, "Вы не в отряде", rightX + PANEL_W / 2, top + PANEL_H / 2 - 10, 0xFF999999);
            return;
        }
        // Party tabs: one per party (up to 3).
        int tabsW = PANEL_W - 20;
        int tabW = tabsW / parties.size();
        for (int i = 0; i < parties.size(); i++) {
            PartyData p = parties.get(i);
            int tx = rightX + 1 + i * tabW;
            boolean isActive = p == active;
            String label = font.plainSubstrByWidth(p.icon() + " " + p.name(), tabW - 6);
            g.drawString(font, UiUtil.colored(label, p.color()).withStyle(s -> s.withBold(p.bold() && isActive)), tx + 4, top + 5, isActive ? 0xFFFFFFFF : 0xA0FFFFFF, false);
            if (isActive) {
                g.fill(tx, top + HEADER_H - 2, tx + tabW, top + HEADER_H, UiUtil.argb(p.color()));
            }
        }

        int y = top + HEADER_H + 2;
        String count = active.members().size() + " / " + Limits.MAX_MEMBERS;
        g.drawString(font, count, rightX + PANEL_W - 6 - font.width(count), y, 0xFF999999, false);
        g.drawString(font, UiUtil.partyTitle(active), rightX + 6, y, 0xFFFFFFFF, false);
        y += 12;

        int rows = visibleRows(true) - 1;
        List<PartyData.Member> members = active.members();
        memberScroll = Math.max(0, Math.min(memberScroll, members.size() - rows));
        for (int i = memberScroll; i < members.size() && i < memberScroll + rows; i++) {
            PartyData.Member member = members.get(i);
            boolean hover = UiUtil.inside(mouseX, mouseY, rightX + 2, y, PANEL_W - 4, ROW_H);
            if (member.uuid().equals(selectedMember)) {
                g.fill(rightX + 2, y, rightX + PANEL_W - 2, y + ROW_H, UiUtil.argb(accent, 0.35f));
            } else if (hover) {
                g.fill(rightX + 2, y, rightX + PANEL_W - 2, y + ROW_H, UiUtil.ROW_HOVER);
            }
            UiUtil.head(g, member.uuid(), rightX + 5, y + 3, 8);
            int tx = rightX + 17;
            if (active.isLeader(member.uuid()) || active.isDeputy(member.uuid())) {
                g.drawString(font, UiUtil.CROWN, tx, y + 3, UiUtil.argb(active.isLeader(member.uuid()) ? UiUtil.GOLD : UiUtil.SILVER), false);
                tx += font.width(UiUtil.CROWN) + 2;
            }
            g.drawString(font, member.name(), tx, y + 3, member.online() ? 0xFFFFFFFF : 0xFF777777, false);
            MPNetwork.MemberStatus status = ClientPartyState.status(member.uuid());
            if (!member.online()) {
                g.drawString(font, "не в сети", rightX + PANEL_W - 6 - font.width("не в сети"), y + 3, 0xFF777777, false);
            } else if (status != null) {
                String hp = (int) Math.ceil(status.health()) + "❤";
                g.drawString(font, hp, rightX + PANEL_W - 6 - font.width(hp), y + 3, 0xFFFF5555, false);
            }
            y += ROW_H;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        // Left tabs
        if (UiUtil.inside(mouseX, mouseY, leftX, top, PANEL_W, HEADER_H)) {
            invitesTab = mouseX >= leftX + PANEL_W / 2;
            rebuildWidgets();
            return true;
        }
        int rowsTop = top + HEADER_H + 2;
        if (!invitesTab && UiUtil.inside(mouseX, mouseY, leftX, rowsTop, PANEL_W, visibleRows(false) * ROW_H)) {
            int index = playerScroll + (int) ((mouseY - rowsTop) / ROW_H);
            List<PlayerInfo> players = players();
            if (index < players.size()) {
                UUID uuid = players.get(index).getProfile().getId();
                selectedPlayer = uuid.equals(selectedPlayer) ? null : uuid;
                rebuildWidgets();
                return true;
            }
        }
        if (invitesTab) {
            int y = rowsTop;
            for (MPNetwork.Invite invite : ClientPartyState.invites()) {
                if (UiUtil.inside(mouseX, mouseY, leftX + PANEL_W - 40, y + 4, 16, 16)) {
                    send(MPNetwork.Action.ACCEPT, invite.party(), null, null);
                    return true;
                }
                if (UiUtil.inside(mouseX, mouseY, leftX + PANEL_W - 22, y + 4, 16, 16)) {
                    send(MPNetwork.Action.DECLINE, invite.party(), null, null);
                    return true;
                }
                y += ROW_H * 2;
            }
        }
        // Party tabs
        List<PartyData> parties = ClientPartyState.parties();
        if (!parties.isEmpty() && UiUtil.inside(mouseX, mouseY, rightX, top, PANEL_W - 20, HEADER_H)) {
            int index = (int) ((mouseX - rightX - 1) / ((PANEL_W - 20) / parties.size()));
            if (index >= 0 && index < parties.size()) {
                ClientPartyState.setActive(parties.get(index).id());
                selectedMember = null;
                rebuildWidgets();
            }
            return true;
        }
        PartyData active = ClientPartyState.active();
        int membersTop = rowsTop + 12;
        if (active != null && UiUtil.inside(mouseX, mouseY, rightX, membersTop, PANEL_W, (visibleRows(true) - 1) * ROW_H)) {
            int index = memberScroll + (int) ((mouseY - membersTop) / ROW_H);
            if (index < active.members().size()) {
                UUID uuid = active.members().get(index).uuid();
                selectedMember = uuid.equals(selectedMember) ? null : uuid;
                rebuildWidgets();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int delta = scrollY > 0 ? -1 : 1;
        if (mouseX < width / 2.0) {
            playerScroll = Math.max(0, playerScroll + delta);
        } else {
            memberScroll = Math.max(0, memberScroll + delta);
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
