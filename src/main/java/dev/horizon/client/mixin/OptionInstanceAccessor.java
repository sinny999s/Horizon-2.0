package dev.horizon.client.mixin;

import java.util.function.Function;
import net.minecraft.client.OptionInstance;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** An option's caption, how it writes a value, and its tooltip: the client's Options menu draws them its own way. */
@Mixin(OptionInstance.class)
public interface OptionInstanceAccessor<T> {
	@Accessor("caption")
	Component horizon_caption();

	@Accessor("toString")
	Function<T, Component> horizon_label();

	@Accessor("tooltip")
	OptionInstance.TooltipSupplier<T> horizon_tooltip();
}
