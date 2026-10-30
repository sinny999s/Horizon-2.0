package dev.horizon.client.mixin.world;

import com.mojang.renderpearl.api.commands.RenderPass;
import dev.horizon.client.event.EventBus;
import dev.horizon.client.event.WeatherRenderEvent;
import net.minecraft.client.renderer.WorldBorderRenderer;
import net.minecraft.client.renderer.oit.OitStage;
import net.minecraft.client.renderer.state.level.WorldBorderRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The world-border half of 26.2's weather pass (see {@link NoRenderWeatherMixin}). */
@Mixin(WorldBorderRenderer.class)
public abstract class NoRenderWorldBorderMixin {
	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void horizon$border(WorldBorderRenderState state, RenderPass pass, Vec3 camera, double depthFar, CallbackInfo ci) {
		if (EventBus.INSTANCE.post(new WeatherRenderEvent()).getCancelled()) ci.cancel();
	}

	@Inject(method = "renderOit", at = @At("HEAD"), cancellable = true)
	private void horizon$borderOit(WorldBorderRenderState state, Vec3 camera, double depthFar, OitStage stage, RenderPass pass, CallbackInfo ci) {
		if (EventBus.INSTANCE.post(new WeatherRenderEvent()).getCancelled()) ci.cancel();
	}
}
