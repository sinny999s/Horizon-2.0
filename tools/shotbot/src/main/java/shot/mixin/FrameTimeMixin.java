package shot.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** One frame for PerfLog: Minecraft.runTick runs once per frame. */
@Mixin(Minecraft.class)
public abstract class FrameTimeMixin {
	@Inject(method = "runTick", at = @At("HEAD"))
	private void shot$frame(boolean advanceGameTime, CallbackInfo ci) {
		shot.PerfBridge.frame();
	}
}
