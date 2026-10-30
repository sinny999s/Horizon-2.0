package dev.horizon.client.mixin;

import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.options.controls.ControlsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Vanilla's movement options, for the client's Options menu. */
@Mixin(ControlsScreen.class)
public interface ControlsScreenInvoker {
	@Invoker("options")
	static OptionInstance<?>[] horizon_options(Options options) {
		throw new AssertionError();
	}
}
