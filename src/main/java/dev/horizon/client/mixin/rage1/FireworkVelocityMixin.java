package dev.horizon.client.mixin.rage1;

import dev.horizon.client.event.EventBus;
import dev.horizon.client.event.FireworkVelocityEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** The velocity a firework gives the local player it's attached to; cancelling the event sets its velocity instead. */
@Mixin(FireworkRocketEntity.class)
public abstract class FireworkVelocityMixin {
	@Shadow
	private LivingEntity attachedToEntity;

	@ModifyArg(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"))
	private Vec3 horizon$velocity(Vec3 velocity) {
		if (attachedToEntity != Minecraft.getInstance().player) return velocity;
		FireworkVelocityEvent e = EventBus.INSTANCE.post(new FireworkVelocityEvent(velocity));
		return e.getCancelled() ? e.getVelocity() : velocity;
	}
}
