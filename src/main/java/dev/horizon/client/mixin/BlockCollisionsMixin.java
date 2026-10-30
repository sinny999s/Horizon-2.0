package dev.horizon.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.horizon.client.event.CollisionEvent;
import dev.horizon.client.event.EventBus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockCollisions;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Lets modules change the block state collisions see, while [CollisionEvent.active]. */
@Mixin(BlockCollisions.class)
public abstract class BlockCollisionsMixin {
	@WrapOperation(method = "computeNext", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/BlockGetter;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
	private BlockState horizon$collisionState(BlockGetter getter, BlockPos pos, Operation<BlockState> original) {
		BlockState state = original.call(getter, pos);
		if (!CollisionEvent.active) return state;
		return EventBus.INSTANCE.post(new CollisionEvent(pos, state)).getState();
	}
}
