package dev.horizon.client.event

import net.minecraft.world.entity.Pose
import net.minecraft.world.phys.Vec3

/** `TridentItem.releaseUsing` HEAD, for the local player with a riptide trident; cancelling skips vanilla's release. */
class TridentUseEvent : Event()

/** `TridentItem.use` HEAD, for a riptide trident out of water and rain; cancelling starts using it anyway. */
class TridentWeatherEvent : Event()

/** The local player's `LivingEntity.travel` HEAD ([pre]) or RETURN; cancelling moves the player by [vec] (and skips travel at HEAD). */
class ElytraTravelEvent(val pre: Boolean, var vec: Vec3) : Event()
