package dev.horizon.client.mixin;

import dev.horizon.client.event.EventBus;
import dev.horizon.client.event.RenderEntityEvent;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Each entity about to be drawn goes through RenderEntityEvent; cancelling skips it this frame. */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private <E extends Entity> void horizon$shouldRender(E entity, Frustum frustum, double x, double y, double z, float partialTick,
	                                                    CallbackInfoReturnable<Boolean> cir) {
		if (EventBus.INSTANCE.post(new RenderEntityEvent(entity)).getCancelled()) cir.setReturnValue(false);
	}
}
