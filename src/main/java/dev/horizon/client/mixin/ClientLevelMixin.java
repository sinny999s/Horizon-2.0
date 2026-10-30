package dev.horizon.client.mixin;

import dev.horizon.client.event.EntitySpawnEvent;
import dev.horizon.client.event.EventBus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** An entity was added to the client level. */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
	@Inject(method = "addEntity", at = @At("TAIL"))
	private void horizon$addEntity(Entity entity, CallbackInfo ci) {
		EventBus.INSTANCE.post(new EntitySpawnEvent(entity));
	}
}
