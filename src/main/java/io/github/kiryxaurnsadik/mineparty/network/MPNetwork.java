package io.github.kiryxaurnsadik.mineparty.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import io.github.kiryxaurnsadik.mineparty.MineParty;
import io.github.kiryxaurnsadik.mineparty.party.Limits;
import io.github.kiryxaurnsadik.mineparty.party.MarkerSettings;
import io.github.kiryxaurnsadik.mineparty.party.PartyData;
import io.github.kiryxaurnsadik.mineparty.party.PingType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** All MineParty packets. The same messages will later travel through the relay as JSON. */
public final class MPNetwork {
    private MPNetwork() {
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String path) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MineParty.MOD_ID, path));
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(PartyAction.TYPE, PartyAction.CODEC);
        PayloadTypeRegistry.playC2S().register(UpdateStyle.TYPE, UpdateStyle.CODEC);
        PayloadTypeRegistry.playC2S().register(UpdateMarkers.TYPE, UpdateMarkers.CODEC);
        PayloadTypeRegistry.playC2S().register(PingRequest.TYPE, PingRequest.CODEC);

        PayloadTypeRegistry.playS2C().register(PartyList.TYPE, PartyList.CODEC);
        PayloadTypeRegistry.playS2C().register(InviteList.TYPE, InviteList.CODEC);
        PayloadTypeRegistry.playS2C().register(MemberStatusList.TYPE, MemberStatusList.CODEC);
        PayloadTypeRegistry.playS2C().register(PingBroadcast.TYPE, PingBroadcast.CODEC);
        PayloadTypeRegistry.playS2C().register(Presence.TYPE, Presence.CODEC);
    }

    // ---------------------------------------------------------------- client -> server

    public enum Action {
        CREATE, LEAVE, KICK, PROMOTE_LEADER, SET_DEPUTY, DISBAND, INVITE, ACCEPT, DECLINE, CHAT
    }

    /**
     * One generic request. {@code party} is the party it is about (for ACCEPT/DECLINE the inviting party),
     * {@code target} the player it is about, {@code text} a name or a chat line.
     */
    public record PartyAction(Action action, UUID party, UUID target, String text) implements CustomPacketPayload {
        public static final Type<PartyAction> TYPE = type("party_action");
        public static final StreamCodec<FriendlyByteBuf, PartyAction> CODEC =
                CustomPacketPayload.codec(PartyAction::write, PartyAction::new);
        private static final UUID NONE = new UUID(0, 0);

        public PartyAction(Action action, UUID party, UUID target, String text) {
            this.action = action;
            this.party = party == null ? NONE : party;
            this.target = target == null ? NONE : target;
            this.text = text == null ? "" : text;
        }

        private PartyAction(FriendlyByteBuf buf) {
            this(buf.readEnum(Action.class), buf.readUUID(), buf.readUUID(), buf.readUtf(Limits.MAX_CHAT_LENGTH * 4));
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeEnum(action);
            buf.writeUUID(party);
            buf.writeUUID(target);
            buf.writeUtf(text, Limits.MAX_CHAT_LENGTH * 4);
        }

        @Override
        public Type<PartyAction> type() {
            return TYPE;
        }
    }

    public record UpdateStyle(UUID party, String name, int color, boolean bold, String icon) implements CustomPacketPayload {
        public static final Type<UpdateStyle> TYPE = type("update_style");
        public static final StreamCodec<FriendlyByteBuf, UpdateStyle> CODEC =
                CustomPacketPayload.codec(UpdateStyle::write, UpdateStyle::new);

        private UpdateStyle(FriendlyByteBuf buf) {
            this(buf.readUUID(), buf.readUtf(Limits.MAX_NAME_LENGTH * 4), buf.readInt(), buf.readBoolean(), buf.readUtf(8));
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeUUID(party);
            buf.writeUtf(name, Limits.MAX_NAME_LENGTH * 4);
            buf.writeInt(color);
            buf.writeBoolean(bold);
            buf.writeUtf(icon, 8);
        }

        @Override
        public Type<UpdateStyle> type() {
            return TYPE;
        }
    }

    public record UpdateMarkers(UUID party, MarkerSettings markers) implements CustomPacketPayload {
        public static final Type<UpdateMarkers> TYPE = type("update_markers");
        public static final StreamCodec<FriendlyByteBuf, UpdateMarkers> CODEC =
                CustomPacketPayload.codec(UpdateMarkers::write, UpdateMarkers::new);

        private UpdateMarkers(FriendlyByteBuf buf) {
            this(buf.readUUID(), MarkerSettings.read(buf));
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeUUID(party);
            markers.write(buf);
        }

        @Override
        public Type<UpdateMarkers> type() {
            return TYPE;
        }
    }

    /** @param party the party to ping, or null for every party of the sender */
    public record PingRequest(PingType pingType, UUID party, double x, double y, double z) implements CustomPacketPayload {
        public static final Type<PingRequest> TYPE = type("ping_request");
        public static final StreamCodec<FriendlyByteBuf, PingRequest> CODEC =
                CustomPacketPayload.codec(PingRequest::write, PingRequest::new);

        private PingRequest(FriendlyByteBuf buf) {
            this(buf.readEnum(PingType.class), buf.readBoolean() ? buf.readUUID() : null,
                    buf.readDouble(), buf.readDouble(), buf.readDouble());
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeEnum(pingType);
            buf.writeBoolean(party != null);
            if (party != null) {
                buf.writeUUID(party);
            }
            buf.writeDouble(x);
            buf.writeDouble(y);
            buf.writeDouble(z);
        }

        @Override
        public Type<PingRequest> type() {
            return TYPE;
        }
    }

    // ---------------------------------------------------------------- server -> client

    /** Every party the receiving player is in, in join order. */
    public record PartyList(List<PartyData> parties) implements CustomPacketPayload {
        public static final Type<PartyList> TYPE = type("party_list");
        public static final StreamCodec<FriendlyByteBuf, PartyList> CODEC =
                CustomPacketPayload.codec(PartyList::write, PartyList::new);

        private PartyList(FriendlyByteBuf buf) {
            this(readList(buf));
        }

        private static List<PartyData> readList(FriendlyByteBuf buf) {
            int count = Math.min(buf.readVarInt(), Limits.MAX_PARTIES_PER_PLAYER);
            List<PartyData> list = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                list.add(PartyData.read(buf));
            }
            return list;
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeVarInt(parties.size());
            parties.forEach(p -> p.write(buf));
        }

        @Override
        public Type<PartyList> type() {
            return TYPE;
        }
    }

    public record Invite(UUID party, String partyName, String icon, int color, String from) {
    }

    public record InviteList(List<Invite> invites) implements CustomPacketPayload {
        public static final Type<InviteList> TYPE = type("invite_list");
        public static final StreamCodec<FriendlyByteBuf, InviteList> CODEC =
                CustomPacketPayload.codec(InviteList::write, InviteList::new);

        private InviteList(FriendlyByteBuf buf) {
            this(readList(buf));
        }

        private static List<Invite> readList(FriendlyByteBuf buf) {
            int count = Math.min(buf.readVarInt(), 64);
            List<Invite> list = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                list.add(new Invite(buf.readUUID(), buf.readUtf(Limits.MAX_NAME_LENGTH * 4), buf.readUtf(8),
                        buf.readInt(), buf.readUtf(16)));
            }
            return list;
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeVarInt(invites.size());
            for (Invite invite : invites) {
                buf.writeUUID(invite.party());
                buf.writeUtf(invite.partyName(), Limits.MAX_NAME_LENGTH * 4);
                buf.writeUtf(invite.icon(), 8);
                buf.writeInt(invite.color());
                buf.writeUtf(invite.from(), 16);
            }
        }

        @Override
        public Type<InviteList> type() {
            return TYPE;
        }
    }

    /** Position, health and gear of one ally. Armor is head, chest, legs, feet, then the main hand. */
    public record MemberStatus(UUID uuid, String dimension, double x, double y, double z, float health,
                               float maxHealth, float absorption, List<ItemStack> gear) {
    }

    public record MemberStatusList(List<MemberStatus> members) implements CustomPacketPayload {
        public static final Type<MemberStatusList> TYPE = type("member_status");
        public static final StreamCodec<RegistryFriendlyByteBuf, MemberStatusList> CODEC =
                CustomPacketPayload.codec(MemberStatusList::write, MemberStatusList::new);

        private MemberStatusList(RegistryFriendlyByteBuf buf) {
            this(readList(buf));
        }

        private static List<MemberStatus> readList(RegistryFriendlyByteBuf buf) {
            int count = Math.min(buf.readVarInt(), Limits.MAX_MEMBERS * Limits.MAX_PARTIES_PER_PLAYER);
            List<MemberStatus> list = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                UUID uuid = buf.readUUID();
                String dimension = buf.readUtf(256);
                double x = buf.readDouble();
                double y = buf.readDouble();
                double z = buf.readDouble();
                float health = buf.readFloat();
                float maxHealth = buf.readFloat();
                float absorption = buf.readFloat();
                List<ItemStack> gear = new ArrayList<>(5);
                for (int g = 0; g < 5; g++) {
                    gear.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
                }
                list.add(new MemberStatus(uuid, dimension, x, y, z, health, maxHealth, absorption, gear));
            }
            return list;
        }

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeVarInt(members.size());
            for (MemberStatus m : members) {
                buf.writeUUID(m.uuid());
                buf.writeUtf(m.dimension(), 256);
                buf.writeDouble(m.x());
                buf.writeDouble(m.y());
                buf.writeDouble(m.z());
                buf.writeFloat(m.health());
                buf.writeFloat(m.maxHealth());
                buf.writeFloat(m.absorption());
                for (int g = 0; g < 5; g++) {
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, g < m.gear().size() ? m.gear().get(g) : ItemStack.EMPTY);
                }
            }
        }

        @Override
        public Type<MemberStatusList> type() {
            return TYPE;
        }
    }

    public record PingBroadcast(PingType pingType, UUID party, UUID from, String fromName, double x, double y, double z)
            implements CustomPacketPayload {
        public static final Type<PingBroadcast> TYPE = type("ping");
        public static final StreamCodec<FriendlyByteBuf, PingBroadcast> CODEC =
                CustomPacketPayload.codec(PingBroadcast::write, PingBroadcast::new);

        private PingBroadcast(FriendlyByteBuf buf) {
            this(buf.readEnum(PingType.class), buf.readUUID(), buf.readUUID(), buf.readUtf(16),
                    buf.readDouble(), buf.readDouble(), buf.readDouble());
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeEnum(pingType);
            buf.writeUUID(party);
            buf.writeUUID(from);
            buf.writeUtf(fromName, 16);
            buf.writeDouble(x);
            buf.writeDouble(y);
            buf.writeDouble(z);
        }

        @Override
        public Type<PingBroadcast> type() {
            return TYPE;
        }
    }

    public record PartyIcon(String icon, int color) {
    }

    /**
     * Who has MineParty, and for every player that shares a party with the receiver the icons of
     * ALL their parties, so everyone sees when a player is in several parties.
     */
    public record Presence(Set<UUID> modded, Map<UUID, List<PartyIcon>> icons) implements CustomPacketPayload {
        public static final Type<Presence> TYPE = type("presence");
        public static final StreamCodec<FriendlyByteBuf, Presence> CODEC =
                CustomPacketPayload.codec(Presence::write, Presence::new);

        private Presence(FriendlyByteBuf buf) {
            this(readModded(buf), readIcons(buf));
        }

        private static Set<UUID> readModded(FriendlyByteBuf buf) {
            int count = Math.min(buf.readVarInt(), 10_000);
            Set<UUID> set = new HashSet<>();
            for (int i = 0; i < count; i++) {
                set.add(buf.readUUID());
            }
            return set;
        }

        private static Map<UUID, List<PartyIcon>> readIcons(FriendlyByteBuf buf) {
            int count = Math.min(buf.readVarInt(), 10_000);
            Map<UUID, List<PartyIcon>> map = new HashMap<>();
            for (int i = 0; i < count; i++) {
                UUID uuid = buf.readUUID();
                int n = Math.min(buf.readVarInt(), Limits.MAX_PARTIES_PER_PLAYER);
                List<PartyIcon> list = new ArrayList<>(n);
                for (int j = 0; j < n; j++) {
                    list.add(new PartyIcon(buf.readUtf(8), buf.readInt()));
                }
                map.put(uuid, list);
            }
            return map;
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeVarInt(modded.size());
            modded.forEach(buf::writeUUID);
            buf.writeVarInt(icons.size());
            icons.forEach((uuid, list) -> {
                buf.writeUUID(uuid);
                buf.writeVarInt(list.size());
                for (PartyIcon icon : list) {
                    buf.writeUtf(icon.icon(), 8);
                    buf.writeInt(icon.color());
                }
            });
        }

        @Override
        public Type<Presence> type() {
            return TYPE;
        }
    }
}
