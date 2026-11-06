package de.wcore.mixin;

import de.wcore.WCoreClient;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Locale;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {

    @Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
    private void wcore$replaceSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
        PlayerSkin replacement = WCoreClient.SKINS.get(self.getName().getString().toLowerCase(Locale.ROOT));
        if (replacement != null) {
            cir.setReturnValue(replacement);
        }
    }
}
