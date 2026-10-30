package dev.horizon.client.mixin;

import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.options.ChatOptionsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Vanilla's chat options, for the client's Options menu. */
@Mixin(ChatOptionsScreen.class)
public interface ChatOptionsScreenInvoker {
	@Invoker("options")
	static OptionInstance<?>[] horizon_options(Options options) {
		throw new AssertionError();
	}
}
