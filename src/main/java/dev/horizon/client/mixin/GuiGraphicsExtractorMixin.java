package dev.horizon.client.mixin;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.platform.cursor.CursorType;
import dev.horizon.client.gui.Cursor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The cursor the client's widgets asked for in the last frame, set by vanilla's one cursor update a frame. */
@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsExtractorMixin {
	@Inject(method = "applyCursor", at = @At("HEAD"))
	private void horizon$cursor(Window window, CallbackInfo ci) {
		CursorType type = Cursor.INSTANCE.getLast();
		if (type != null) ((GuiGraphicsExtractor) (Object) this).requestCursor(type);
	}
}
