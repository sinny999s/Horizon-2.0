package dev.horizon.client.mixin;

import dev.horizon.client.event.CrosshairEvent;
import dev.horizon.client.event.EventBus;
import dev.horizon.client.event.PostTasksEvent;
import dev.horizon.client.event.PostTickEvent;
import dev.horizon.client.event.TickEvent;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tick, post-tick, post-tasks and crosshair-pick events. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Inject(method = "tick", at = @At("HEAD"))
	private void horizon$tick(CallbackInfo ci) {
		EventBus.INSTANCE.post(new TickEvent());
	}

	@Inject(method = "tick", at = @At("RETURN"))
	private void horizon$postTick(CallbackInfo ci) {
		EventBus.INSTANCE.post(new PostTickEvent());
	}

	@Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;runAllTasks()V", shift = At.Shift.AFTER))
	private void horizon$postTasks(boolean advanceGameTime, CallbackInfo ci) {
		EventBus.INSTANCE.post(new PostTasksEvent());
	}

	@Inject(method = "pick(F)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;raycastHitResult(FLnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/phys/HitResult;"), cancellable = true)
	private void horizon$pick(float partialTick, CallbackInfo ci) {
		if (EventBus.INSTANCE.post(new CrosshairEvent()).getCancelled()) ci.cancel();
	}
}
