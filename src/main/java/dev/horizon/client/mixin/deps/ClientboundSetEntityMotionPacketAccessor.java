package dev.horizon.client.mixin.deps;

import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Rewrites a velocity packet's motion (anti-knock-back's Custom mode). */
@Mixin(ClientboundSetEntityMotionPacket.class)
public interface ClientboundSetEntityMotionPacketAccessor {
	@Mutable
	@Accessor("movement")
	void horizon_setMovement(Vec3 movement);
}
