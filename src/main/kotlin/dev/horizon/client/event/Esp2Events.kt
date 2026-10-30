package dev.horizon.client.event

import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState

/** The original's BlockStateEvent: `LevelChunk.setBlockState` RETURN in the client level ([previous] = what it returned). */
class BlockStateEvent(val pos: BlockPos, val state: BlockState, val previous: BlockState?) : Event()

/**
 * The original's XRayBlockEvent: asked for every face `Block.shouldRenderFace` decides while x-ray is on ([XRayState]);
 * cancelling draws the face, otherwise it is hidden. Posted from the section-compile threads.
 */
class XRayBlockEvent(val block: Block) : Event()

/** The original's LightMapUpdateEvent: `Lightmap.render` HEAD; cancelling fills the lightmap white instead (full bright). */
class LightMapUpdateEvent : Event()

/** The original's EntityRemove: `ClientLevel.removeEntity` HEAD ([entity] is null for an unknown id); cancelling keeps it. */
class EntityRemoveEvent(val entity: Entity?) : Event()

/**
 * The original's FreecamStateEvent: posted for the camera's rotation and position in `Camera.alignWithEntity`; cancelling
 * puts the camera at [x], [y], [z] with [yaw], [pitch].
 */
class FreecamStateEvent : Event() {
    var x = 0.0
    var y = 0.0
    var z = 0.0
    var yaw = 0f
    var pitch = 0f
}

/** The original's `GofraState.xray`: x-ray's render hooks are live (set by the x-ray module, read by the esp2 mixins). */
object XRayState {
    @JvmField
    @Volatile
    var active = false
}
