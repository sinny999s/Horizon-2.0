package dev.horizon.client.mixin.world;

import dev.horizon.client.event.DarknessEffectEvent;
import dev.horizon.client.event.EventBus;
import dev.horizon.client.event.GetFogModifierEvent;
import net.minecraft.client.renderer.fog.environment.BlindnessFogEnvironment;
import net.minecraft.client.renderer.fog.environment.DarknessFogEnvironment;
import net.minecraft.client.renderer.fog.environment.MobEffectFogEnvironment;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Skips {@link BlindnessFogEnvironment} and {@link DarknessFogEnvironment} fog/sky darkening when cancelled. */
@Mixin(MobEffectFogEnvironment.class)
public abstract class NoRenderBlindnessMixin {
	@Inject(method = "isApplicable", at = @At("HEAD"), cancellable = true)
	private void horizon$isApplicable(FogType type, Entity entity, CallbackInfoReturnable<Boolean> cir) {
		Object self = this;
		if (self instanceof BlindnessFogEnvironment && EventBus.INSTANCE.post(new GetFogModifierEvent()).getCancelled()) {
			cir.setReturnValue(false);
		} else if (self instanceof DarknessFogEnvironment && EventBus.INSTANCE.post(new DarknessEffectEvent()).getCancelled()) {
			cir.setReturnValue(false);
		}
	}
}
