package de.wcore.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.SkinManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {

    @Accessor("skinManager")
    SkinManager wcore$getSkinManager();
}
