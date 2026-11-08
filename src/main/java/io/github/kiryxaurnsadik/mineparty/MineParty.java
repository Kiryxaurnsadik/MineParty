package io.github.kiryxaurnsadik.mineparty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.kiryxaurnsadik.mineparty.network.MPNetwork;
import io.github.kiryxaurnsadik.mineparty.server.PartyCommands;
import io.github.kiryxaurnsadik.mineparty.server.PartyManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class MineParty implements ModInitializer {
    public static final String MOD_ID = "mineparty";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static PartyManager manager;

    public static PartyManager manager() {
        return manager;
    }

    @Override
    public void onInitialize() {
        MPNetwork.register();

        ServerLifecycleEvents.SERVER_STARTED.register(server -> manager = new PartyManager(server));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> manager = null);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (manager != null) {
                manager.tick();
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (manager != null) {
                manager.onJoin(handler.getPlayer());
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (manager != null) {
                manager.onLeave(handler.getPlayer());
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(MPNetwork.PartyAction.TYPE, (payload, context) -> {
            if (manager != null) {
                manager.handle(context.player(), payload);
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(MPNetwork.UpdateStyle.TYPE, (payload, context) -> {
            if (manager != null) {
                manager.updateStyle(context.player(), payload);
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(MPNetwork.UpdateMarkers.TYPE, (payload, context) -> {
            if (manager != null) {
                manager.updateMarkers(context.player(), payload);
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(MPNetwork.PingRequest.TYPE, (payload, context) -> {
            if (manager != null) {
                manager.ping(context.player(), payload);
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                PartyCommands.register(dispatcher, MineParty::manager));

        LOGGER.info("MineParty loaded");
    }
}
