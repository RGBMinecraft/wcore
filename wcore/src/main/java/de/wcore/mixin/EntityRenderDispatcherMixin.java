package de.wcore.mixin;

import de.wcore.WCoreClient;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Locale;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void wcore$hideIgnored(E entity, Frustum frustum, double x, double y, double z,
                                                      CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof AbstractClientPlayer player
                && WCoreClient.IGNORED.contains(player.getName().getString().toLowerCase(Locale.ROOT))) {
            cir.setReturnValue(false);
        }
    }
}
