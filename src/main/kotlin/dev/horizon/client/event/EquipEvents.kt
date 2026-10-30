package dev.horizon.client.event

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState

/** The original's PickUpBlockEvent: `MultiPlayerGameMode.handlePickItemFromBlock` HEAD; cancelling skips the pick. */
class PickBlockEvent : Event()

/**
 * The original's CalcBlockBreakingDeltaEvent: `BlockBehaviour.getDestroyProgress` HEAD, for every caller (the integrated
 * server's too, as in the original); cancelling returns [delta].
 */
class BreakDeltaEvent(val pos: BlockPos, val state: BlockState) : Event() {
    var delta = 0f
}
