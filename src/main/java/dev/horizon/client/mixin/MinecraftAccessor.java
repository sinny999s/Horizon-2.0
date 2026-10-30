package dev.horizon.client.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** A vanilla right-click, for legit item macros. */
@Mixin(Minecraft.class)
public interface MinecraftAccessor {
	@Invoker("startUseItem")
	void horizon_startUseItem();
}
