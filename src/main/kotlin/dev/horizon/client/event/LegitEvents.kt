package dev.horizon.client.event

import net.minecraft.world.entity.Entity

/**
 * `ClientLevel.addEntity` HEAD, before the entity is in the level (the original's `EntitySpawn`; [EntitySpawnEvent] is its
 * `AfterEntitySpawn`). Cancelling keeps the entity out of the level.
 */
class PreEntitySpawnEvent(val entity: Entity) : Event()
