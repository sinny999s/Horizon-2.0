package dev.horizon.client.mixin;

import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Vanilla's video options by section, for the client's Options menu (without Sodium). */
@Mixin(VideoSettingsScreen.class)
public interface VideoSettingsScreenInvoker {
	@Invoker("displayOptions")
	static OptionInstance<?>[] horizon_display(Options options) {
		throw new AssertionError();
	}

	@Invoker("qualityOptions")
	static OptionInstance<?>[] horizon_quality(Options options) {
		throw new AssertionError();
	}

	@Invoker("preferenceOptions")
	static OptionInstance<?>[] horizon_preferences(Options options) {
		throw new AssertionError();
	}
}
