package dev.horizon.client.mixin.basic;

import com.mojang.authlib.GameProfile;
import dev.horizon.client.event.EventBus;
import dev.horizon.client.event.MoveEvent;
import dev.horizon.client.event.PushOutOfBlocksEvent;
import dev.horizon.client.event.ShouldStopSprintingEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The local player's push out of blocks (its moves and sprint stop are hooked in mixin/rage1/LocalPlayerMoveMixin). */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerBasicMixin extends AbstractClientPlayer {
	public LocalPlayerBasicMixin(ClientLevel level, GameProfile profile) {
		super(level, profile);
	}

	@Inject(method = "moveTowardsClosestSpace(DD)V", at = @At("HEAD"), cancellable = true)
	private void horizon$pushOutOfBlocks(double x, double z, CallbackInfo ci) {
		if (EventBus.INSTANCE.post(new PushOutOfBlocksEvent()).getCancelled()) ci.cancel();
	}
}
