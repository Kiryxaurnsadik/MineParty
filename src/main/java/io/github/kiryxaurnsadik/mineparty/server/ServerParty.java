package io.github.kiryxaurnsadik.mineparty.server;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import io.github.kiryxaurnsadik.mineparty.party.MarkerSettings;
import io.github.kiryxaurnsadik.mineparty.party.PartyData;

/** Mutable party kept by the server. */
final class ServerParty {
    final UUID id = UUID.randomUUID();
    String name;
    int color = 0x55FFFF;
    boolean bold = false;
    String icon = "⚔";
    UUID leader;
    UUID deputy;
    final Set<UUID> members = new LinkedHashSet<>();
    MarkerSettings markers = MarkerSettings.DEFAULT;

    ServerParty(UUID leader, String name) {
        this.leader = leader;
        this.name = name;
        members.add(leader);
    }

    boolean canInvite(UUID player) {
        return player.equals(leader) || player.equals(deputy);
    }

    PartyData toData(PartyManager manager) {
        List<PartyData.Member> list = new ArrayList<>(members.size());
        for (UUID uuid : members) {
            list.add(new PartyData.Member(uuid, manager.nameOf(uuid), manager.isOnline(uuid)));
        }
        return new PartyData(id, name, color, bold, icon, leader, deputy, list, markers);
    }
}
