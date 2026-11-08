package io.github.kiryxaurnsadik.mineparty.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import io.github.kiryxaurnsadik.mineparty.network.MPNetwork;
import io.github.kiryxaurnsadik.mineparty.party.PartyData;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Local copy of everything the server told us. GUI, HUD and markers only read from here. */
public final class ClientPartyState {
    public static final long PING_LIFETIME_MS = 8000;

    public record ActivePing(MPNetwork.PingBroadcast ping, long createdAt) {
    }

    private static List<PartyData> parties = List.of();
    private static UUID activeId;
    private static List<MPNetwork.Invite> invites = List.of();
    private static Map<UUID, MPNetwork.MemberStatus> statuses = Map.of();
    private static Set<UUID> modded = Set.of();
    private static Map<UUID, List<MPNetwork.PartyIcon>> icons = Map.of();
    private static final List<ActivePing> pings = new ArrayList<>();

    public static boolean inviteMode;
    public static boolean partyChatMode;

    private ClientPartyState() {
    }

    public static boolean serverHasMod() {
        return ClientPlayNetworking.canSend(MPNetwork.PartyAction.TYPE);
    }

    public static void reset() {
        parties = List.of();
        activeId = null;
        invites = List.of();
        statuses = Map.of();
        modded = Set.of();
        icons = Map.of();
        pings.clear();
        inviteMode = false;
        partyChatMode = false;
    }

    public static List<PartyData> parties() {
        return parties;
    }

    public static PartyData active() {
        for (PartyData party : parties) {
            if (party.id().equals(activeId)) {
                return party;
            }
        }
        return parties.isEmpty() ? null : parties.get(0);
    }

    public static void setActive(UUID id) {
        activeId = id;
    }

    public static void cycleActive() {
        if (parties.isEmpty()) {
            return;
        }
        PartyData current = active();
        int index = parties.indexOf(current);
        activeId = parties.get((index + 1) % parties.size()).id();
    }

    public static void setParties(List<PartyData> list) {
        UUID previous = activeId;
        int oldSize = parties.size();
        parties = List.copyOf(list);
        if (list.size() > oldSize && !list.isEmpty()) {
            // Just created or joined a party: make it active.
            activeId = list.get(list.size() - 1).id();
        } else if (previous == null || active() == null || !active().id().equals(previous)) {
            activeId = list.isEmpty() ? null : list.get(0).id();
        }
        if (list.isEmpty()) {
            partyChatMode = false;
        }
    }

    public static List<MPNetwork.Invite> invites() {
        return invites;
    }

    public static void setInvites(List<MPNetwork.Invite> list) {
        invites = List.copyOf(list);
    }

    public static MPNetwork.MemberStatus status(UUID uuid) {
        return statuses.get(uuid);
    }

    public static void setStatuses(List<MPNetwork.MemberStatus> list) {
        Map<UUID, MPNetwork.MemberStatus> map = new HashMap<>();
        for (MPNetwork.MemberStatus status : list) {
            map.put(status.uuid(), status);
        }
        statuses = map;
    }

    public static boolean hasMod(UUID uuid) {
        return modded.contains(uuid);
    }

    public static List<MPNetwork.PartyIcon> iconsOf(UUID uuid) {
        return icons.getOrDefault(uuid, List.of());
    }

    public static void setPresence(Set<UUID> moddedPlayers, Map<UUID, List<MPNetwork.PartyIcon>> partyIcons) {
        modded = new HashSet<>(moddedPlayers);
        icons = new HashMap<>(partyIcons);
    }

    public static void addPing(MPNetwork.PingBroadcast ping) {
        // A new ping from the same player near an old one replaces it.
        pings.removeIf(p -> p.ping().from().equals(ping.from())
                && distanceSq(p.ping(), ping) < 9.0);
        pings.add(new ActivePing(ping, System.currentTimeMillis()));
        while (pings.size() > 30) {
            pings.remove(0);
        }
    }

    private static double distanceSq(MPNetwork.PingBroadcast a, MPNetwork.PingBroadcast b) {
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        double dz = a.z() - b.z();
        return dx * dx + dy * dy + dz * dz;
    }

    public static List<ActivePing> pings() {
        long now = System.currentTimeMillis();
        pings.removeIf(p -> now - p.createdAt() > PING_LIFETIME_MS);
        return pings;
    }

    /** All allies (any of my parties), without me, each once. */
    public static Set<UUID> allies(UUID self) {
        Set<UUID> set = new java.util.LinkedHashSet<>();
        for (PartyData party : parties) {
            for (PartyData.Member member : party.members()) {
                if (!member.uuid().equals(self)) {
                    set.add(member.uuid());
                }
            }
        }
        return set;
    }

    /** The party whose marker settings apply to an ally: the active party if they are in it, else the first shared one. */
    public static PartyData markerPartyFor(UUID ally) {
        PartyData active = active();
        if (active != null && active.contains(ally)) {
            return active;
        }
        for (PartyData party : parties) {
            if (party.contains(ally)) {
                return party;
            }
        }
        return null;
    }
}
