package io.github.kiryxaurnsadik.mineparty.client;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import io.github.kiryxaurnsadik.mineparty.client.render.PartyHud;
import io.github.kiryxaurnsadik.mineparty.client.render.Projection;
import io.github.kiryxaurnsadik.mineparty.client.render.WorldMarkers;
import io.github.kiryxaurnsadik.mineparty.client.ui.HudEditorScreen;
import io.github.kiryxaurnsadik.mineparty.client.ui.PartyScreen;
import io.github.kiryxaurnsadik.mineparty.client.ui.RadialMenuScreen;
import io.github.kiryxaurnsadik.mineparty.network.MPNetwork;
import io.github.kiryxaurnsadik.mineparty.party.PartyData;
import io.github.kiryxaurnsadik.mineparty.party.PingType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class MinePartyClient implements ClientModInitializer {
    private static final String CATEGORY = "key.categories.mineparty";
    private static final long DOUBLE_CLICK_MS = 300;
    private static final double INVITE_RANGE = 64;
    private static final double PING_RANGE = 256;

    private static KeyMapping openKey;
    private static KeyMapping inviteModeKey;
    private static KeyMapping radialKey;
    private static KeyMapping chatModeKey;
    private static KeyMapping cycleKey;
    private static KeyMapping hudKey;

    /** Single middle click = warning, double click = danger. The first click waits a moment for a second one. */
    private static long pendingPingAt;
    private static Vec3 pendingPingPos;

    @Override
    public void onInitializeClient() {
        ClientConfig.load();
        openKey = register("key.mineparty.open", GLFW.GLFW_KEY_P);
        inviteModeKey = register("key.mineparty.invite_mode", GLFW.GLFW_KEY_I);
        radialKey = register("key.mineparty.radial", GLFW.GLFW_KEY_V);
        chatModeKey = register("key.mineparty.chat_mode", GLFW.GLFW_KEY_Y);
        cycleKey = register("key.mineparty.cycle_party", GLFW.GLFW_KEY_U);
        hudKey = register("key.mineparty.hud_editor", GLFW.GLFW_KEY_H);

        ClientPlayNetworking.registerGlobalReceiver(MPNetwork.PartyList.TYPE, (payload, context) -> {
            ClientPartyState.setParties(payload.parties());
            refreshScreen();
        });
        ClientPlayNetworking.registerGlobalReceiver(MPNetwork.InviteList.TYPE, (payload, context) -> {
            boolean more = payload.invites().size() > ClientPartyState.invites().size();
            ClientPartyState.setInvites(payload.invites());
            if (more) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME, 1.2f));
            }
            refreshScreen();
        });
        ClientPlayNetworking.registerGlobalReceiver(MPNetwork.MemberStatusList.TYPE,
                (payload, context) -> ClientPartyState.setStatuses(payload.members()));
        ClientPlayNetworking.registerGlobalReceiver(MPNetwork.Presence.TYPE,
                (payload, context) -> ClientPartyState.setPresence(payload.modded(), payload.icons()));
        ClientPlayNetworking.registerGlobalReceiver(MPNetwork.PingBroadcast.TYPE, (payload, context) -> {
            ClientPartyState.addPing(payload);
            float pitch = switch (payload.pingType()) {
                case WARNING -> 1.0f;
                case DANGER -> 0.6f;
                case HERE -> 1.5f;
            };
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(
                    payload.pingType() == PingType.DANGER ? SoundEvents.NOTE_BLOCK_BIT : SoundEvents.NOTE_BLOCK_BELL, pitch));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientPartyState.reset());

        ClientTickEvents.START_CLIENT_TICK.register(MinePartyClient::onTick);

        WorldRenderEvents.LAST.register(context ->
                Projection.capture(context.positionMatrix(), context.projectionMatrix(), context.camera().getPosition()));
        HudRenderCallback.EVENT.register((graphics, deltaTracker) -> {
            WorldMarkers.render(graphics, deltaTracker.getGameTimeDeltaPartialTick(true));
            PartyHud.render(graphics, false);
        });

        // Party chat channel: while it is on, normal chat lines go to the active party.
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            PartyData party = ClientPartyState.active();
            if (ClientPartyState.partyChatMode && party != null && ClientPartyState.serverHasMod()) {
                ClientPlayNetworking.send(new MPNetwork.PartyAction(MPNetwork.Action.CHAT, party.id(), null, message));
                return false;
            }
            return true;
        });
    }

    private static KeyMapping register(String name, int key) {
        return KeyBindingHelper.registerKeyBinding(new KeyMapping(name, InputConstants.Type.KEYSYM, key, CATEGORY));
    }

    private static void refreshScreen() {
        if (Minecraft.getInstance().screen instanceof PartyScreen screen) {
            screen.refresh();
        }
    }

    private static void actionBar(Component message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(message, true);
        }
    }

    private static void onTick(Minecraft mc) {
        if (mc.player == null) {
            return;
        }
        if (pendingPingPos != null && System.currentTimeMillis() - pendingPingAt > DOUBLE_CLICK_MS) {
            sendPingAt(PingType.WARNING, pendingPingPos);
            pendingPingPos = null;
        }
        if (mc.screen != null) {
            return;
        }
        while (openKey.consumeClick()) {
            mc.setScreen(new PartyScreen());
        }
        while (inviteModeKey.consumeClick()) {
            toggleInviteMode();
        }
        while (chatModeKey.consumeClick()) {
            if (ClientPartyState.active() == null) {
                actionBar(Component.literal("Вы не в отряде").withStyle(ChatFormatting.RED));
            } else {
                ClientPartyState.partyChatMode = !ClientPartyState.partyChatMode;
                actionBar(Component.literal(ClientPartyState.partyChatMode ? "Чат: отряд" : "Чат: общий"));
            }
        }
        while (cycleKey.consumeClick()) {
            ClientPartyState.cycleActive();
            PartyData party = ClientPartyState.active();
            if (party != null) {
                actionBar(Component.literal("Активный отряд: ").append(io.github.kiryxaurnsadik.mineparty.client.ui.UiUtil.partyTitle(party)));
            }
        }
        while (hudKey.consumeClick()) {
            mc.setScreen(new HudEditorScreen());
        }
        if (radialKey.consumeClick()) {
            while (radialKey.consumeClick()) {
                // drain
            }
            mc.setScreen(new RadialMenuScreen(KeyBindingHelper.getBoundKeyOf(radialKey)));
            return;
        }

        // Middle mouse button (the vanilla "pick block" key). In invite mode it invites the player
        // under the crosshair; otherwise, outside creative and while in a party, it places a ping.
        boolean inviteMode = ClientPartyState.inviteMode;
        boolean pings = ClientPartyState.active() != null && !mc.player.isCreative();
        if (inviteMode || pings) {
            while (mc.options.keyPickItem.consumeClick()) {
                if (inviteMode) {
                    inviteLookedAt();
                } else {
                    onPingClick();
                }
            }
        }
    }

    public static void toggleInviteMode() {
        ClientPartyState.inviteMode = !ClientPartyState.inviteMode;
        actionBar(Component.literal(ClientPartyState.inviteMode
                ? "Режим приглашения включён"
                : "Режим приглашения выключен").withStyle(ChatFormatting.YELLOW));
    }

    private static Player lookedAtPlayer(double range) {
        Minecraft mc = Minecraft.getInstance();
        Entity camera = mc.getCameraEntity();
        if (camera == null || mc.level == null) {
            return null;
        }
        Vec3 eye = camera.getEyePosition(1f);
        Vec3 look = camera.getViewVector(1f);
        Vec3 end = eye.add(look.scale(range));
        HitResult block = camera.pick(range, 1f, false);
        double maxSq = block.getType() == HitResult.Type.MISS ? range * range : block.getLocation().distanceToSqr(eye);
        AABB box = camera.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(camera, eye, end, box,
                e -> e instanceof Player && e != mc.player && !e.isSpectator(), maxSq);
        return hit != null && hit.getEntity() instanceof Player player ? player : null;
    }

    public static void inviteLookedAt() {
        PartyData party = ClientPartyState.active();
        if (!ClientPartyState.serverHasMod()) {
            actionBar(Component.literal("На сервере нет MineParty").withStyle(ChatFormatting.RED));
            return;
        }
        if (party == null) {
            actionBar(Component.literal("Сначала создайте отряд (P)").withStyle(ChatFormatting.RED));
            return;
        }
        Player target = lookedAtPlayer(INVITE_RANGE);
        if (target == null) {
            actionBar(Component.literal("Наведите прицел на игрока").withStyle(ChatFormatting.GRAY));
            return;
        }
        ClientPlayNetworking.send(new MPNetwork.PartyAction(MPNetwork.Action.INVITE, party.id(), target.getUUID(), null));
    }

    private static Vec3 pingTarget() {
        Minecraft mc = Minecraft.getInstance();
        Entity camera = mc.getCameraEntity();
        if (camera == null) {
            return null;
        }
        Vec3 eye = camera.getEyePosition(1f);
        Vec3 look = camera.getViewVector(1f);
        HitResult block = camera.pick(PING_RANGE, 1f, false);
        double maxSq = block.getType() == HitResult.Type.MISS ? PING_RANGE * PING_RANGE : block.getLocation().distanceToSqr(eye);
        AABB box = camera.getBoundingBox().expandTowards(look.scale(PING_RANGE)).inflate(1.0);
        EntityHitResult entity = ProjectileUtil.getEntityHitResult(camera, eye, eye.add(look.scale(PING_RANGE)), box,
                e -> e != mc.player && !e.isSpectator() && e.isPickable(), maxSq);
        if (entity != null) {
            return entity.getEntity().position().add(0, entity.getEntity().getBbHeight() + 0.3, 0);
        }
        return block.getType() == HitResult.Type.MISS ? null : block.getLocation();
    }

    private static void onPingClick() {
        Vec3 target = pingTarget();
        if (target == null) {
            return;
        }
        if (pendingPingPos != null && System.currentTimeMillis() - pendingPingAt <= DOUBLE_CLICK_MS) {
            pendingPingPos = null;
            sendPingAt(PingType.DANGER, target);
        } else {
            pendingPingPos = target;
            pendingPingAt = System.currentTimeMillis();
        }
    }

    public static void sendPing(PingType type) {
        Vec3 target = pingTarget();
        if (target != null) {
            sendPingAt(type, target);
        }
    }

    private static void sendPingAt(PingType type, Vec3 pos) {
        PartyData party = ClientPartyState.active();
        if (party == null || !ClientPartyState.serverHasMod()) {
            return;
        }
        ClientPlayNetworking.send(new MPNetwork.PingRequest(type, ClientConfig.get().pingAllParties ? null : party.id(),
                pos.x, pos.y, pos.z));
    }
}
