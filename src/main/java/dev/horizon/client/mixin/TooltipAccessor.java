package dev.horizon.client.mixin;

import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A tooltip's text, for the client's Options menu. */
@Mixin(Tooltip.class)
public interface TooltipAccessor {
	@Accessor("message")
	Component horizon_message();
}
