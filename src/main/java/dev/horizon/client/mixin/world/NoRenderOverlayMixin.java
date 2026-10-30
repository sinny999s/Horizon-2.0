package dev.horizon.client.mixin.world;

import dev.horizon.client.event.EventBus;
import dev.horizon.client.event.RenderFireOverlayEvent;
import dev.horizon.client.event.RenderInWallOverlayEvent;
import dev.horizon.client.event.RenderUnderwaterOverlayEvent;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** First-person screen overlays in {@link ScreenEffectRenderer}: fire, underwater, and in-wall block suffocation. */
@Mixin(ScreenEffectRenderer.class)
public abstract class NoRenderOverlayMixin {
	@Inject(method = "submitFire", at = @At("HEAD"), cancellable = true)
	private static void horizon$fireOverlay(CallbackInfo ci) {
		if (EventBus.INSTANCE.post(new RenderFireOverlayEvent()).getCancelled()) ci.cancel();
	}

	@Inject(method = "submitWater", at = @At("HEAD"), cancellable = true)
	private static void horizon$waterOverlay(CallbackInfo ci) {
		if (EventBus.INSTANCE.post(new RenderUnderwaterOverlayEvent()).getCancelled()) ci.cancel();
	}

	@Inject(method = "submitBlockSprite", at = @At("HEAD"), cancellable = true)
	private static void horizon$blockOverlay(CallbackInfo ci) {
		if (EventBus.INSTANCE.post(new RenderInWallOverlayEvent()).getCancelled()) ci.cancel();
	}
}
