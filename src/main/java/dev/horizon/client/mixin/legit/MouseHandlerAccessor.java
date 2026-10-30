package dev.horizon.client.mixin.legit;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Sets the mouse position vanilla tracks (the original's `IMouse.setPosition`). */
@Mixin(MouseHandler.class)
public interface MouseHandlerAccessor {
	@Accessor("xpos")
	void horizon_setXpos(double x);

	@Accessor("ypos")
	void horizon_setYpos(double y);
}
