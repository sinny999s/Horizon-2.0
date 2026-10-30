package dev.horizon.client.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The hotbar slot the game mode last told the server about. */
@Mixin(MultiPlayerGameMode.class)
public interface GameModeAccessor {
	@Accessor("carriedIndex")
	void horizon_setCarriedIndex(int slot);

	@Accessor("destroyProgress")
	float horizon_getDestroyProgress();

	@Accessor("destroyProgress")
	void horizon_setDestroyProgress(float progress);
}
