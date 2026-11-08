package io.github.kiryxaurnsadik.mineparty.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.kiryxaurnsadik.mineparty.client.Badges;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

/** MineParty badge and party icons next to names in the Tab list. */
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {
    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void mineparty$decorateName(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
        cir.setReturnValue(Badges.decorate(info.getProfile().getId(), cir.getReturnValue()));
    }
}
