package dev.horizon.client.mixin;

import dev.horizon.client.event.EventBus;
import dev.horizon.client.event.FireworkVectorEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** The direction a firework boosts the local player in. */
@Mixin(FireworkRocketEntity.class)
public abstract class FireworkRocketEntityMixin {
	@Shadow
	private LivingEntity attachedToEntity;

	@Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getLookAngle()Lnet/minecraft/world/phys/Vec3;"))
	private Vec3 horizon$look(LivingEntity entity) {
		if (attachedToEntity == Minecraft.getInstance().player) {
			FireworkVectorEvent e = EventBus.INSTANCE.post(new FireworkVectorEvent(entity.getLookAngle()));
			if (e.getCancelled()) return e.getVector();
		}
		return entity.getLookAngle();
	}
}
