package dev.horizon.client.mixin.deps;

import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Optional;

/** Rewrites an explosion's knockback on the local player (anti-knock-back). */
@Mixin(ClientboundExplodePacket.class)
public interface ClientboundExplodePacketAccessor {
	@Mutable
	@Accessor("playerKnockback")
	void horizon_setPlayerKnockback(Optional<Vec3> knockback);
}
