package dev.horizon.client.mixin;

import dev.horizon.client.event.EventBus;
import dev.horizon.client.event.HotbarRenderEvent;
import dev.horizon.client.hud.widgets.Crosshair;
import dev.horizon.client.module.cosmetic.CustomHotbar;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The Crosshair widget hides the vanilla crosshair; events around the hotbar. */
@Mixin(Hud.class)
public abstract class HudMixin {
	@Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
	private void horizon$crosshair(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
		if (Crosshair.INSTANCE.getEnabled()) ci.cancel();
	}

	/**
	 * The pre and post events come in pairs (Offhand hides the offhand item between them), so a hotbar Custom Hotbar
	 * replaces gets neither: a cancel here skips the RETURN hook.
	 */
	@Inject(method = "extractItemHotbar", at = @At("HEAD"), cancellable = true)
	private void horizon$hotbarPre(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
		if (CustomHotbar.isHotbarActive()) {
			ci.cancel();
			return;
		}
		EventBus.INSTANCE.post(new HotbarRenderEvent(true));
	}

	@Inject(method = "extractItemHotbar", at = @At("RETURN"))
	private void horizon$hotbarPost(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
		EventBus.INSTANCE.post(new HotbarRenderEvent(false));
	}

	// Custom Hotbar's health pill replaces the hearts and armour, its food pill the food and air bubbles.
	@Inject(method = "extractHearts", at = @At("HEAD"), cancellable = true)
	private void horizon$hearts(GuiGraphicsExtractor graphics, Player player, int x, int y, int rowHeight, int regen, float maxHealth,
	                            int health, int displayHealth, int absorption, boolean blink, CallbackInfo ci) {
		if (CustomHotbar.isHealthActive()) ci.cancel();
	}

	@Inject(method = "extractArmor", at = @At("HEAD"), cancellable = true)
	private static void horizon$armor(GuiGraphicsExtractor graphics, Player player, int y, int rows, int rowHeight, int x, CallbackInfo ci) {
		if (CustomHotbar.isHealthActive()) ci.cancel();
	}

	@Inject(method = "extractFood", at = @At("HEAD"), cancellable = true)
	private void horizon$food(GuiGraphicsExtractor graphics, Player player, int y, int x, CallbackInfo ci) {
		if (CustomHotbar.isFoodActive()) ci.cancel();
	}

	@Inject(method = "extractAirBubbles", at = @At("HEAD"), cancellable = true)
	private void horizon$air(GuiGraphicsExtractor graphics, Player player, int vehicleHearts, int y, int x, CallbackInfo ci) {
		if (CustomHotbar.isFoodActive()) ci.cancel();
	}
}
