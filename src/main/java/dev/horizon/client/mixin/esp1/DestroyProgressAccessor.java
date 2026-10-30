package dev.horizon.client.mixin.esp1;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The local player's mining progress (the original's InteractionManagerAccessor.getCurBlockDamageMP). */
@Mixin(MultiPlayerGameMode.class)
public interface DestroyProgressAccessor {
	@Accessor("destroyProgress")
	float horizon_getDestroyProgress();
}
