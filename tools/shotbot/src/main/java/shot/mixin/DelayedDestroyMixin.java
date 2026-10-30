package shot.mixin;

import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * With SHOT_FIX_DELAYED set: a delayed destroy counts the ticks since it started. 26.3's own tick() passes the start tick
 * as the tick count, so a delayed destroy either fires at once (online long enough for the block) or never; this plays
 * what a server with the sum right would do, for testing packet mining.
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class DelayedDestroyMixin {
	private static final boolean FIX = System.getenv("SHOT_FIX_DELAYED") != null;

	@Shadow private int gameTicks;
	@Shadow private int delayedTickStart;

	@ModifyArg(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayerGameMode;incrementDestroyProgress(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;I)F", ordinal = 0), index = 2)
	private int shot$delayedTicks(int ticks) {
		return FIX ? gameTicks - delayedTickStart : ticks;
	}
}
