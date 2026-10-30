package dev.horizon.client.mixin;

import dev.horizon.client.module.cosmetic.CustomHotbar;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.contextualbar.ContextualBar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ContextualBar.class)
public interface ContextualBarMixin {
	@Inject(method = "extractExperienceLevel", at = @At("HEAD"), cancellable = true)
	private static void horizon$extractExperienceLevel(GuiGraphicsExtractor graphics, Font font, int level, CallbackInfo ci) {
		if (CustomHotbar.isXpActive()) {
			ci.cancel();
		}
	}
}
