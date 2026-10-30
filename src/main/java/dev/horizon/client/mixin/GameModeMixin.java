package dev.horizon.client.mixin;

import dev.horizon.client.event.AttackBlockEvent;
import dev.horizon.client.event.BlockBreakProgressEvent;
import dev.horizon.client.event.ContainerClickEvent;
import dev.horizon.client.event.EventBus;
import dev.horizon.client.event.StopUsingItemEvent;
import dev.horizon.client.event.UseItemEvent;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Container clicks go through ContainerClickEvent (cancellable); item use is bracketed by UseItemEvent. */
@Mixin(MultiPlayerGameMode.class)
public abstract class GameModeMixin {
	@Inject(method = "handleContainerInput", at = @At("HEAD"), cancellable = true)
	private void horizon$click(int containerId, int slotId, int button, ContainerInput input, Player player, CallbackInfo ci) {
		if (EventBus.INSTANCE.post(new ContainerClickEvent(slotId, button, input)).getCancelled()) ci.cancel();
	}

	@Inject(method = "startDestroyBlock", at = @At("HEAD"), cancellable = true)
	private void horizon$attackBlock(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
		if (EventBus.INSTANCE.post(new AttackBlockEvent(pos, direction)).getCancelled()) cir.setReturnValue(false);
	}

	@Inject(method = "continueDestroyBlock", at = @At("HEAD"), cancellable = true)
	private void horizon$breakProgress(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
		if (EventBus.INSTANCE.post(new BlockBreakProgressEvent(pos, direction)).getCancelled()) cir.setReturnValue(false);
	}

	@Inject(method = "useItem", at = @At("HEAD"))
	private void horizon$useItem(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		EventBus.INSTANCE.post(new UseItemEvent(true));
	}

	@Inject(method = "useItem", at = @At("RETURN"))
	private void horizon$postUseItem(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		EventBus.INSTANCE.post(new UseItemEvent(false));
	}

	@Inject(method = "releaseUsingItem", at = @At("HEAD"), cancellable = true)
	private void horizon$releaseUsingItem(Player player, CallbackInfo ci) {
		StopUsingItemEvent.INSTANCE.setCancelled(false);
		if (EventBus.INSTANCE.post(StopUsingItemEvent.INSTANCE).getCancelled()) ci.cancel();
	}
}
