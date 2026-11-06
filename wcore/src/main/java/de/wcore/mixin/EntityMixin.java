package de.wcore.mixin;

import de.wcore.WCoreNames;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Ersetzt den Anzeigenamen (Nametag ueber dem Kopf) von Spielern. */
@Mixin(Entity.class)
public abstract class EntityMixin {

    @Inject(method = "getDisplayName", at = @At("HEAD"), cancellable = true, require = 0)
    private void wcore$displayName(CallbackInfoReturnable<Component> cir) {
        if ((Object) this instanceof AbstractClientPlayer player) {
            String name = WCoreNames.displayFor(player.getName().getString());
            if (name != null) {
                cir.setReturnValue(Component.literal(name));
            }
        }
    }
}
