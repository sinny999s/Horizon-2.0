package dev.horizon.client.mixin;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The last rotation sent to the server. */
@Mixin(LocalPlayer.class)
public interface LocalPlayerAccessor {
	@Accessor("yRotLast")
	float horizon_yRotLast();

	@Accessor("xRotLast")
	float horizon_xRotLast();
}
