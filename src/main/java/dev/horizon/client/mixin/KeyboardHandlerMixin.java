package dev.horizon.client.mixin;

import dev.horizon.client.module.Binds;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Key binds, after vanilla has handled the key (so the key that opens the ClickGUI doesn't reach it). */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
	@Inject(method = "keyPress", at = @At("TAIL"))
	private void horizon$keyPress(long handle, int action, KeyEvent event, CallbackInfo ci) {
		Binds.INSTANCE.onKey(event.key(), action);
	}
}
