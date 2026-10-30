package dev.horizon.client.mixin;

import dev.horizon.client.module.cosmetic.CustomHotbar;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.contextualbar.ExperienceBar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ExperienceBar.class)
public abstract class ExperienceBarMixin {
	@Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
	private void horizon$xpBackground(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
		if (CustomHotbar.isXpActive()) {
			ci.cancel();
		}
	}

	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void horizon$xpState(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
		if (CustomHotbar.isXpActive()) {
			ci.cancel();
		}
	}
}
