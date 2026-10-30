package dev.horizon.client.mixin.rage1;

import dev.horizon.client.event.ClearClickMapEvent;
import dev.horizon.client.event.EventBus;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The client-side simulation of a container click; cancelling skips it, so the click packet lists no changes. */
@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {
	@Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
	private void horizon$clicked(CallbackInfo ci) {
		if (EventBus.INSTANCE.post(new ClearClickMapEvent()).getCancelled()) ci.cancel();
	}
}
