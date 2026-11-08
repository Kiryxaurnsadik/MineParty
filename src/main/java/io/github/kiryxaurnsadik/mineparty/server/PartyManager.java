package io.github.kiryxaurnsadik.mineparty.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import io.github.kiryxaurnsadik.mineparty.network.MPNetwork;
import io.github.kiryxaurnsadik.mineparty.party.Limits;
import io.github.kiryxaurnsadik.mineparty.party.MarkerSettings;
import io.github.kiryxaurnsadik.mineparty.party.PartyData;
import io.github.kiryxaurnsadik.mineparty.party.PingType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Server side of MineParty: the source of truth for parties. Clients only send requests;
 * every rule (15 members, 3 parties, roles) is checked here.
 *
 * Demo: parties live in memory and are lost on server restart.
 */
public final class PartyManager {
    private static final int STATUS_INTERVAL_TICKS = 5;
    private static final int PINGS_PER_WINDOW = 3;
    private static final int PING_WINDOW_TICKS = 40;

    private final MinecraftServer server;
    private final Map<UUID, ServerParty> parties = new LinkedHashMap<>();
    /** Player -> party ids in join order. */
    private final Map<UUID, LinkedHashSet<UUID>> membership = new HashMap<>();
    /** Invited player -> (party id -> expiry tick). */
    private final Map<UUID, Map<UUID, PendingInvite>> invites = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    private final Map<UUID, List<Integer>> pingTimes = new HashMap<>();
    private int tick;

    private record PendingInvite(UUID party, String from, int expiresAt) {
    }

    public PartyManager(MinecraftServer server) {
        this.server = server;
    }

    // ------------------------------------------------------------------ helpers

    String nameOf(UUID uuid) {
        return names.getOrDefault(uuid, uuid.toString().substring(0, 8));
    }

    boolean isOnline(UUID uuid) {
        return server.getPlayerList().getPlayer(uuid) != null;
    }

    private ServerPlayer player(UUID uuid) {
        return server.getPlayerList().getPlayer(uuid);
    }

    private static boolean hasMod(ServerPlayer player) {
        return ServerPlayNetworking.canSend(player, MPNetwork.PartyList.TYPE);
    }

    private static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (ServerPlayNetworking.canSend(player, payload.type())) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    private List<ServerParty> partiesOf(UUID player) {
        List<ServerParty> list = new ArrayList<>();
        for (UUID id : membership.getOrDefault(player, new LinkedHashSet<>())) {
            ServerParty party = parties.get(id);
            if (party != null) {
                list.add(party);
            }
        }
        return list;
    }

    public List<UUID> partyIdsOf(UUID player) {
        return new ArrayList<>(membership.getOrDefault(player, new LinkedHashSet<>()));
    }

    static MutableComponent partyTag(ServerParty party) {
        TextColor color = TextColor.fromRgb(party.color);
        return Component.literal(party.icon + " " + party.name)
                .withStyle(style -> style.withColor(color).withBold(party.bold));
    }

    private static void fail(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.RED));
    }

    private void tell(ServerParty party, Component message) {
        for (UUID uuid : party.members) {
            ServerPlayer member = player(uuid);
            if (member != null) {
                member.sendSystemMessage(message);
            }
        }
    }

    // ------------------------------------------------------------------ lifecycle

    public void onJoin(ServerPlayer player) {
        names.put(player.getUUID(), player.getGameProfile().getName());
        syncPlayer(player.getUUID());
        for (ServerParty party : partiesOf(player.getUUID())) {
            syncParty(party);
        }
        broadcastPresence();
    }

    public void onLeave(ServerPlayer player) {
        // The player stays in their parties while offline; members just see them as offline.
        List<ServerParty> list = partiesOf(player.getUUID());
        server.execute(() -> {
            list.forEach(this::syncParty);
            broadcastPresence();
        });
    }

    public void tick() {
        tick++;
        expireInvites();
        if (tick % STATUS_INTERVAL_TICKS == 0) {
            sendStatuses();
        }
    }

    private void expireInvites() {
        Iterator<Map.Entry<UUID, Map<UUID, PendingInvite>>> it = invites.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Map<UUID, PendingInvite>> entry = it.next();
            boolean removed = entry.getValue().values().removeIf(inv -> inv.expiresAt() <= tick || !parties.containsKey(inv.party()));
            if (removed) {
                ServerPlayer target = player(entry.getKey());
                if (target != null) {
                    sendInvites(target);
                }
            }
            if (entry.getValue().isEmpty()) {
                it.remove();
            }
        }
    }

    // ------------------------------------------------------------------ actions

    public void handle(ServerPlayer sender, MPNetwork.PartyAction action) {
        switch (action.action()) {
            case CREATE -> create(sender, action.text());
            case LEAVE -> leave(sender, action.party());
            case KICK -> kick(sender, action.party(), action.target());
            case PROMOTE_LEADER -> promoteLeader(sender, action.party(), action.target());
            case SET_DEPUTY -> setDeputy(sender, action.party(), action.target());
            case DISBAND -> disband(sender, action.party());
            case INVITE -> invite(sender, action.party(), action.target());
            case ACCEPT -> accept(sender, action.party());
            case DECLINE -> decline(sender, action.party());
            case CHAT -> chat(sender, action.party(), action.text());
        }
    }

    public void create(ServerPlayer sender, String requestedName) {
        UUID uuid = sender.getUUID();
        if (membership.getOrDefault(uuid, new LinkedHashSet<>()).size() >= Limits.MAX_PARTIES_PER_PLAYER) {
            fail(sender, "Нельзя состоять больше чем в " + Limits.MAX_PARTIES_PER_PLAYER + " отрядах.");
            return;
        }
        String name = Limits.cleanName(requestedName);
        if (name.isEmpty()) {
            name = "Отряд " + sender.getGameProfile().getName();
        }
        ServerParty party = new ServerParty(uuid, Limits.cleanName(name));
        parties.put(party.id, party);
        membership.computeIfAbsent(uuid, k -> new LinkedHashSet<>()).add(party.id);
        sender.sendSystemMessage(Component.literal("Создан отряд ").append(partyTag(party)));
        syncParty(party);
        broadcastPresence();
    }

    private ServerParty memberParty(ServerPlayer sender, UUID partyId) {
        ServerParty party = parties.get(partyId);
        if (party == null || !party.members.contains(sender.getUUID())) {
            fail(sender, "Вы не состоите в этом отряде.");
            return null;
        }
        return party;
    }

    private ServerParty leaderParty(ServerPlayer sender, UUID partyId) {
        ServerParty party = memberParty(sender, partyId);
        if (party != null && !party.leader.equals(sender.getUUID())) {
            fail(sender, "Это может только лидер отряда.");
            return null;
        }
        return party;
    }

    public void leave(ServerPlayer sender, UUID partyId) {
        ServerParty party = memberParty(sender, partyId);
        if (party == null) {
            return;
        }
        removeMember(party, sender.getUUID());
        sender.sendSystemMessage(Component.literal("Вы покинули отряд ").append(partyTag(party)));
        tell(party, Component.literal(sender.getGameProfile().getName() + " покинул отряд").withStyle(ChatFormatting.GRAY));
    }

    private void removeMember(ServerParty party, UUID uuid) {
        party.members.remove(uuid);
        LinkedHashSet<UUID> set = membership.get(uuid);
        if (set != null) {
            set.remove(party.id);
        }
        if (uuid.equals(party.deputy)) {
            party.deputy = null;
        }
        if (party.members.isEmpty()) {
            parties.remove(party.id);
        } else if (uuid.equals(party.leader)) {
            party.leader = party.deputy != null ? party.deputy : party.members.iterator().next();
            party.deputy = null;
        }
        syncPlayer(uuid);
        syncParty(party);
        broadcastPresence();
    }

    public void kick(ServerPlayer sender, UUID partyId, UUID target) {
        ServerParty party = memberParty(sender, partyId);
        if (party == null) {
            return;
        }
        boolean allowed = party.leader.equals(sender.getUUID())
                || (sender.getUUID().equals(party.deputy) && !target.equals(party.leader));
        if (!allowed || target.equals(sender.getUUID()) || !party.members.contains(target)) {
            fail(sender, "Нельзя убрать этого игрока.");
            return;
        }
        removeMember(party, target);
        tell(party, Component.literal(nameOf(target) + " убран из отряда").withStyle(ChatFormatting.GRAY));
        ServerPlayer kicked = player(target);
        if (kicked != null) {
            kicked.sendSystemMessage(Component.literal("Вас убрали из отряда ").append(partyTag(party)));
        }
    }

    public void promoteLeader(ServerPlayer sender, UUID partyId, UUID target) {
        ServerParty party = leaderParty(sender, partyId);
        if (party == null || !party.members.contains(target) || target.equals(sender.getUUID())) {
            return;
        }
        party.leader = target;
        if (target.equals(party.deputy)) {
            party.deputy = sender.getUUID();
        }
        tell(party, Component.literal(nameOf(target) + " теперь лидер отряда").withStyle(ChatFormatting.GOLD));
        syncParty(party);
    }

    public void setDeputy(ServerPlayer sender, UUID partyId, UUID target) {
        ServerParty party = leaderParty(sender, partyId);
        if (party == null || !party.members.contains(target) || target.equals(party.leader)) {
            return;
        }
        party.deputy = target.equals(party.deputy) ? null : target;
        syncParty(party);
    }

    public void disband(ServerPlayer sender, UUID partyId) {
        ServerParty party = leaderParty(sender, partyId);
        if (party == null) {
            return;
        }
        tell(party, Component.literal("Отряд ").append(partyTag(party)).append(" распущен"));
        parties.remove(party.id);
        for (UUID uuid : party.members) {
            LinkedHashSet<UUID> set = membership.get(uuid);
            if (set != null) {
                set.remove(party.id);
            }
            syncPlayer(uuid);
        }
        broadcastPresence();
    }

    public void invite(ServerPlayer sender, UUID partyId, UUID target) {
        ServerParty party = memberParty(sender, partyId);
        if (party == null) {
            return;
        }
        if (!party.canInvite(sender.getUUID())) {
            fail(sender, "Приглашать могут лидер и заместитель.");
            return;
        }
        ServerPlayer invited = player(target);
        if (invited == null || invited == sender) {
            fail(sender, "Игрок не найден.");
            return;
        }
        if (party.members.contains(target)) {
            fail(sender, invited.getGameProfile().getName() + " уже в отряде.");
            return;
        }
        if (party.members.size() >= Limits.MAX_MEMBERS) {
            fail(sender, "В отряде уже " + Limits.MAX_MEMBERS + " игроков.");
            return;
        }
        if (membership.getOrDefault(target, new LinkedHashSet<>()).size() >= Limits.MAX_PARTIES_PER_PLAYER) {
            fail(sender, invited.getGameProfile().getName() + " уже состоит в " + Limits.MAX_PARTIES_PER_PLAYER + " отрядах.");
            return;
        }
        String from = sender.getGameProfile().getName();
        invites.computeIfAbsent(target, k -> new LinkedHashMap<>())
                .put(party.id, new PendingInvite(party.id, from, tick + Limits.INVITE_TIMEOUT_TICKS));

        String id = party.id.toString();
        MutableComponent accept = Component.literal("[Принять]").withStyle(style -> style
                .withColor(ChatFormatting.GREEN)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/party accept " + id))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Вступить в отряд"))));
        MutableComponent decline = Component.literal("[Отклонить]").withStyle(style -> style
                .withColor(ChatFormatting.RED)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/party decline " + id)));
        invited.sendSystemMessage(Component.literal(from + " приглашает вас в отряд ")
                .append(partyTag(party)).append(" ").append(accept).append(" ").append(decline));
        sendInvites(invited);
        sender.sendSystemMessage(Component.literal("Приглашение отправлено: " + invited.getGameProfile().getName()).withStyle(ChatFormatting.GRAY));
    }

    public void accept(ServerPlayer sender, UUID partyId) {
        Map<UUID, PendingInvite> mine = invites.get(sender.getUUID());
        PendingInvite invite = mine == null ? null : mine.remove(partyId);
        sendInvites(sender);
        ServerParty party = parties.get(partyId);
        if (invite == null || party == null) {
            fail(sender, "Приглашение устарело.");
            return;
        }
        if (party.members.size() >= Limits.MAX_MEMBERS) {
            fail(sender, "В отряде уже нет места.");
            return;
        }
        LinkedHashSet<UUID> set = membership.computeIfAbsent(sender.getUUID(), k -> new LinkedHashSet<>());
        if (set.size() >= Limits.MAX_PARTIES_PER_PLAYER) {
            fail(sender, "Нельзя состоять больше чем в " + Limits.MAX_PARTIES_PER_PLAYER + " отрядах.");
            return;
        }
        party.members.add(sender.getUUID());
        set.add(party.id);
        tell(party, Component.literal(sender.getGameProfile().getName() + " вступил в отряд ").withStyle(ChatFormatting.GREEN).append(partyTag(party)));
        syncParty(party);
        broadcastPresence();
    }

    public void decline(ServerPlayer sender, UUID partyId) {
        Map<UUID, PendingInvite> mine = invites.get(sender.getUUID());
        if (mine != null) {
            mine.remove(partyId);
        }
        sendInvites(sender);
    }

    public void chat(ServerPlayer sender, UUID partyId, String text) {
        ServerParty party = memberParty(sender, partyId);
        String message = text == null ? "" : text.replace("§", "").strip();
        if (party == null || message.isEmpty()) {
            return;
        }
        if (message.length() > Limits.MAX_CHAT_LENGTH) {
            message = message.substring(0, Limits.MAX_CHAT_LENGTH);
        }
        MutableComponent line = Component.literal("[").withStyle(ChatFormatting.DARK_GRAY)
                .append(partyTag(party))
                .append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY));
        if (party.leader.equals(sender.getUUID())) {
            line.append(Component.literal("♛ ").withStyle(ChatFormatting.GOLD));
        }
        line.append(Component.literal(sender.getGameProfile().getName() + ": ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(message).withStyle(ChatFormatting.GRAY));
        tell(party, line);
    }

    public void updateStyle(ServerPlayer sender, MPNetwork.UpdateStyle update) {
        ServerParty party = leaderParty(sender, update.party());
        if (party == null) {
            return;
        }
        String name = Limits.cleanName(update.name());
        if (!name.isEmpty()) {
            party.name = name;
        }
        party.color = update.color() & 0xFFFFFF;
        party.bold = update.bold();
        if (Limits.isValidIcon(update.icon())) {
            party.icon = update.icon();
        }
        syncParty(party);
        broadcastPresence();
    }

    public void updateMarkers(ServerPlayer sender, MPNetwork.UpdateMarkers update) {
        // Only the leader may change markers, including "visible through walls".
        ServerParty party = leaderParty(sender, update.party());
        if (party == null) {
            return;
        }
        MarkerSettings m = update.markers();
        party.markers = new MarkerSettings(m.symbol(), m.color(), m.throughWalls(), m.maxDistance(), m.showName());
        syncParty(party);
    }

    public void ping(ServerPlayer sender, MPNetwork.PingRequest ping) {
        if (sender.position().distanceTo(new net.minecraft.world.phys.Vec3(ping.x(), ping.y(), ping.z())) > Limits.MAX_PING_DISTANCE) {
            return;
        }
        List<Integer> times = pingTimes.computeIfAbsent(sender.getUUID(), k -> new ArrayList<>());
        times.removeIf(t -> t < tick - PING_WINDOW_TICKS);
        if (times.size() >= PINGS_PER_WINDOW) {
            return;
        }
        times.add(tick);

        List<ServerParty> targets = new ArrayList<>();
        for (ServerParty party : partiesOf(sender.getUUID())) {
            if (ping.party() == null || party.id.equals(ping.party())) {
                targets.add(party);
            }
        }
        Set<UUID> sentTo = new HashSet<>();
        for (ServerParty party : targets) {
            MPNetwork.PingBroadcast broadcast = new MPNetwork.PingBroadcast(ping.pingType(), party.id, sender.getUUID(),
                    sender.getGameProfile().getName(), ping.x(), ping.y(), ping.z());
            for (UUID uuid : party.members) {
                ServerPlayer member = player(uuid);
                if (member != null && member.level() == sender.level() && sentTo.add(uuid)) {
                    send(member, broadcast);
                }
            }
        }
    }

    // ------------------------------------------------------------------ sync

    private void syncPlayer(UUID uuid) {
        ServerPlayer player = player(uuid);
        if (player == null) {
            return;
        }
        List<PartyData> list = new ArrayList<>();
        for (ServerParty party : partiesOf(uuid)) {
            list.add(party.toData(this));
        }
        send(player, new MPNetwork.PartyList(list));
        sendInvites(player);
    }

    private void syncParty(ServerParty party) {
        for (UUID uuid : party.members) {
            syncPlayer(uuid);
        }
    }

    private void sendInvites(ServerPlayer player) {
        List<MPNetwork.Invite> list = new ArrayList<>();
        Map<UUID, PendingInvite> mine = invites.get(player.getUUID());
        if (mine != null) {
            for (PendingInvite invite : mine.values()) {
                ServerParty party = parties.get(invite.party());
                if (party != null) {
                    list.add(new MPNetwork.Invite(party.id, party.name, party.icon, party.color, invite.from()));
                }
            }
        }
        send(player, new MPNetwork.InviteList(list));
    }

    private void broadcastPresence() {
        Set<UUID> modded = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (hasMod(player)) {
                modded.add(player.getUUID());
            }
        }
        for (ServerPlayer receiver : server.getPlayerList().getPlayers()) {
            if (!hasMod(receiver)) {
                continue;
            }
            // Every player who shares a party with the receiver, with the icons of ALL their parties.
            Map<UUID, List<MPNetwork.PartyIcon>> icons = new HashMap<>();
            for (ServerParty shared : partiesOf(receiver.getUUID())) {
                for (UUID member : shared.members) {
                    icons.computeIfAbsent(member, uuid -> {
                        List<MPNetwork.PartyIcon> list = new ArrayList<>();
                        for (ServerParty p : partiesOf(uuid)) {
                            list.add(new MPNetwork.PartyIcon(p.icon, p.color));
                        }
                        return list;
                    });
                }
            }
            send(receiver, new MPNetwork.Presence(modded, icons));
        }
    }

    private void sendStatuses() {
        Map<UUID, MPNetwork.MemberStatus> cache = new HashMap<>();
        for (ServerPlayer receiver : server.getPlayerList().getPlayers()) {
            if (!hasMod(receiver)) {
                continue;
            }
            Set<UUID> allies = new LinkedHashSet<>();
            for (ServerParty party : partiesOf(receiver.getUUID())) {
                allies.addAll(party.members);
            }
            allies.remove(receiver.getUUID());
            if (allies.isEmpty()) {
                continue;
            }
            List<MPNetwork.MemberStatus> list = new ArrayList<>();
            for (UUID ally : allies) {
                ServerPlayer p = player(ally);
                if (p != null) {
                    list.add(cache.computeIfAbsent(ally, k -> status(p)));
                }
            }
            send(receiver, new MPNetwork.MemberStatusList(list));
        }
    }

    private static MPNetwork.MemberStatus status(ServerPlayer p) {
        List<ItemStack> gear = List.of(
                p.getItemBySlot(EquipmentSlot.HEAD).copy(),
                p.getItemBySlot(EquipmentSlot.CHEST).copy(),
                p.getItemBySlot(EquipmentSlot.LEGS).copy(),
                p.getItemBySlot(EquipmentSlot.FEET).copy(),
                p.getMainHandItem().copy());
        return new MPNetwork.MemberStatus(p.getUUID(), p.level().dimension().location().toString(),
                p.getX(), p.getY(), p.getZ(), p.getHealth(), p.getMaxHealth(), p.getAbsorptionAmount(), gear);
    }
}
