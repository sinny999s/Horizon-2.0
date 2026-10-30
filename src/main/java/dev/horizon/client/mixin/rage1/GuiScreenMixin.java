package dev.horizon.client.mixin.rage1;

import dev.horizon.client.event.EventBus;
import dev.horizon.client.event.ScreenEvent;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Opening or closing a screen; cancelling the event keeps the current one. */
@Mixin(Gui.class)
public abstract class GuiScreenMixin {
	@Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
	private void horizon$setScreen(Screen screen, CallbackInfo ci) {
		if (EventBus.INSTANCE.post(new ScreenEvent(screen)).getCancelled()) ci.cancel();
	}
}
