package dev.horizon.client.event

import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Pose
import net.minecraft.world.level.material.Fluid
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.phys.Vec3

/**
 * `FlowingFluid.getFlow` HEAD (any fluid, any entity, any thread): cancelling makes the flow at [pos] [vec] instead of
 * vanilla's.
 */
class WaterPushEvent(val pos: BlockPos, val fluid: Fluid, val state: FluidState) : Event() {
    var vec: Vec3 = Vec3.ZERO
}

/** `LocalPlayer.moveTowardsClosestSpace` HEAD (pushed out of a block); cancelling skips the push. */
class PushOutOfBlocksEvent : Event()

/** Each `push(x, y, z)` inside the local player's `Entity.push(Entity)`; cancelling makes it a push by zero. */
class EntityPushEvent : Event()

/** The local player's `isFallFlying` TAIL with vanilla's [value]; cancelling returns the event's [value]. */
class FallFlyingEvent(var value: Boolean) : Event()

