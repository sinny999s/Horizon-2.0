package dev.horizon.client.mixin.world;

import dev.horizon.client.event.EventBus;
import dev.horizon.client.event.NauseaRenderEvent;
import dev.horizon.client.event.RenderPortalOverlayEvent;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Clears portal and nausea effect intensities in {@link PlayerRenderState} so 3D camera wobble/warping is also prevented. */
@Mixin(LevelExtractor.class)
public abstract class NoRenderPlayerStateMixin {
	@Inject(method = "extractPlayerState", at = @At("RETURN"))
	private void horizon$playerState(Camera camera, DeltaTracker deltaTracker, float partialTick, PlayerRenderState state, CallbackInfo ci) {
		if (state.portalEffectIntensity > 0.0F && EventBus.INSTANCE.post(new RenderPortalOverlayEvent()).getCancelled()) {
			state.portalEffectIntensity = 0.0F;
		}
		if (state.nauseaEffectIntensity > 0.0F && EventBus.INSTANCE.post(new NauseaRenderEvent()).getCancelled()) {
			state.nauseaEffectIntensity = 0.0F;
		}
	}
}
