package dev.horizon.client.mixin.player1;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Rewrites a movement packet's on-ground flag (the original's `ServerboundMovePlayerPacketAccessor.setGround`). */
@Mixin(ServerboundMovePlayerPacket.class)
public interface ServerboundMovePlayerPacketAccessor {
	@Mutable
	@Accessor("onGround")
	void horizon_setOnGround(boolean onGround);
}
