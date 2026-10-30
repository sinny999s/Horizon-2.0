package dev.horizon.client.mixin;

import dev.horizon.client.event.EventBus;
import dev.horizon.client.event.PlayerUpdateEvent;
import dev.horizon.client.event.PostSyncEvent;
import dev.horizon.client.event.PreSyncEvent;
import dev.horizon.client.event.UsingItemEvent;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Player update (start of the player tick) and the movement packet's pre/post sync (cancelling PreSync skips it). */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void horizon$tick(CallbackInfo ci) {
		if (EventBus.INSTANCE.post(new PlayerUpdateEvent()).getCancelled()) ci.cancel();
	}

	@Inject(method = "sendPosition", at = @At("HEAD"), cancellable = true)
	private void horizon$preSync(CallbackInfo ci) {
		if (EventBus.INSTANCE.post(new PreSyncEvent()).getCancelled()) ci.cancel();
	}

	@Inject(method = "sendPosition", at = @At("RETURN"))
	private void horizon$postSync(CallbackInfo ci) {
		EventBus.INSTANCE.post(new PostSyncEvent());
	}

	@Inject(method = "isUsingItem", at = @At("HEAD"), cancellable = true)
	private void horizon$isUsingItem(CallbackInfoReturnable<Boolean> cir) {
		UsingItemEvent.INSTANCE.setCancelled(false);
		if (EventBus.INSTANCE.post(UsingItemEvent.INSTANCE).getCancelled()) cir.setReturnValue(true);
	}
}
