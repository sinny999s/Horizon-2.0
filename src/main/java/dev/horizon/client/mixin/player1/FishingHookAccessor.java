package dev.horizon.client.mixin.player1;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The hook's synced "a fish is biting" flag, which auto-fish's 1.17 mode reads. */
@Mixin(FishingHook.class)
public interface FishingHookAccessor {
	@Accessor("DATA_BITING")
	static EntityDataAccessor<Boolean> horizon_dataBiting() {
		throw new AssertionError();
	}
}
