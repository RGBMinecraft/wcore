package de.wcore.mixin;

import de.wcore.WCoreNames;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Ersetzt den Namen in der Spielerliste (Tab). */
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {

    @Inject(method = "getNameForDisplay", at = @At("HEAD"), cancellable = true, require = 0)
    private void wcore$tabName(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
        String name = WCoreNames.displayFor(info.getProfile().name());
        if (name != null) {
            cir.setReturnValue(Component.literal(name));
        }
    }
}
