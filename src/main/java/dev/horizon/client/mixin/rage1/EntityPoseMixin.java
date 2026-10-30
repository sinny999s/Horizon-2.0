package dev.horizon.client.mixin.rage1;

import dev.horizon.client.event.EventBus;
import dev.horizon.client.event.SetPoseEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The local player's pose; cancelling the event returns the event's pose instead. */
@Mixin(Entity.class)
public abstract class EntityPoseMixin {
	@Inject(method = "getPose", at = @At("HEAD"), cancellable = true)
	private void horizon$pose(CallbackInfoReturnable<Pose> cir) {
		if ((Object) this != Minecraft.getInstance().player) return;
		SetPoseEvent e = EventBus.INSTANCE.post(new SetPoseEvent());
		if (e.getCancelled()) cir.setReturnValue(e.getPose());
	}
}
