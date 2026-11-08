package io.github.kiryxaurnsadik.mineparty.party;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.network.FriendlyByteBuf;

/** Snapshot of one party as the client sees it. */
public record PartyData(UUID id, String name, int color, boolean bold, String icon, UUID leader, UUID deputy,
                        List<Member> members, MarkerSettings markers) {

    public record Member(UUID uuid, String name, boolean online) {
    }

    public boolean isLeader(UUID player) {
        return leader.equals(player);
    }

    public boolean isDeputy(UUID player) {
        return deputy != null && deputy.equals(player);
    }

    public boolean contains(UUID player) {
        for (Member member : members) {
            if (member.uuid().equals(player)) {
                return true;
            }
        }
        return false;
    }

    public static PartyData read(FriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        String name = buf.readUtf(Limits.MAX_NAME_LENGTH * 4);
        int color = buf.readInt();
        boolean bold = buf.readBoolean();
        String icon = buf.readUtf(8);
        UUID leader = buf.readUUID();
        UUID deputy = buf.readBoolean() ? buf.readUUID() : null;
        int count = Math.min(buf.readVarInt(), Limits.MAX_MEMBERS);
        List<Member> members = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            members.add(new Member(buf.readUUID(), buf.readUtf(16), buf.readBoolean()));
        }
        return new PartyData(id, name, color, bold, icon, leader, deputy, members, MarkerSettings.read(buf));
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(id);
        buf.writeUtf(name, Limits.MAX_NAME_LENGTH * 4);
        buf.writeInt(color);
        buf.writeBoolean(bold);
        buf.writeUtf(icon, 8);
        buf.writeUUID(leader);
        buf.writeBoolean(deputy != null);
        if (deputy != null) {
            buf.writeUUID(deputy);
        }
        buf.writeVarInt(members.size());
        for (Member member : members) {
            buf.writeUUID(member.uuid());
            buf.writeUtf(member.name(), 16);
            buf.writeBoolean(member.online());
        }
        markers.write(buf);
    }
}
