package dev.horizon.client.mixin;

import dev.horizon.client.event.EventBus;
import dev.horizon.client.event.MoveRelativeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The local player's input-to-velocity step; cancelling adds the event's velocity instead. */
@Mixin(Entity.class)
public abstract class EntityMixin {
	@Shadow
	public abstract Vec3 getDeltaMovement();

	@Shadow
	public abstract void setDeltaMovement(Vec3 v);

	@Inject(method = "moveRelative", at = @At("HEAD"), cancellable = true)
	private void horizon$moveRelative(float speed, Vec3 input, CallbackInfo ci) {
		if ((Object) this != Minecraft.getInstance().player) return;
		MoveRelativeEvent e = EventBus.INSTANCE.post(new MoveRelativeEvent(input, speed));
		if (e.getCancelled()) {
			setDeltaMovement(getDeltaMovement().add(e.getVelocity()));
			ci.cancel();
		}
	}
}
