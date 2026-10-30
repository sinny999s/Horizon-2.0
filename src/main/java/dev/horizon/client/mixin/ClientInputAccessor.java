package dev.horizon.client.mixin;

import net.minecraft.client.player.ClientInput;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Sets the movement vector (Move Fix remaps it to the server yaw). */
@Mixin(ClientInput.class)
public interface ClientInputAccessor {
	@Accessor("moveVector")
	void horizon_setMoveVector(Vec2 v);
}
