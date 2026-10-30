package dev.horizon.client.mixin.rage1;

import dev.horizon.client.event.EventBus;
import dev.horizon.client.event.ShouldDismountEvent;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Whether a player wants to get off its vehicle (sneak); cancelling the event says no. */
@Mixin(Player.class)
public abstract class PlayerRidingMixin {
	@Inject(method = "wantsToStopRiding", at = @At("HEAD"), cancellable = true)
	private void horizon$dismount(CallbackInfoReturnable<Boolean> cir) {
		if (EventBus.INSTANCE.post(new ShouldDismountEvent()).getCancelled()) cir.setReturnValue(false);
	}
}
