package dev.horizon.client.mixin;

import dev.horizon.client.event.EventBus;
import dev.horizon.client.event.SelectSlotEvent;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hotbar slot changes go through SelectSlotEvent (cancellable). */
@Mixin(Inventory.class)
public abstract class InventoryMixin {
	@Inject(method = "setSelectedSlot", at = @At("HEAD"), cancellable = true)
	private void horizon$select(int slot, CallbackInfo ci) {
		if (EventBus.INSTANCE.post(new SelectSlotEvent(slot)).getCancelled()) ci.cancel();
	}
}
