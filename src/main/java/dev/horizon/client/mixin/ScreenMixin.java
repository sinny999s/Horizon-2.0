package dev.horizon.client.mixin;

import dev.horizon.client.hud.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The HUD widgets' mouse position: the last one any screen was drawn with. */
@Mixin(Screen.class)
public abstract class ScreenMixin {
	@Inject(method = "extractRenderState", at = @At("HEAD"))
	private void horizon$mouse(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		Hud.INSTANCE.setMouseX(mouseX);
		Hud.INSTANCE.setMouseY(mouseY);
	}
}
