package dev.horizon.client.mixin.esp1;

import net.minecraft.world.level.BaseSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A spawner's ticks to its next spawn (the original read the field directly). */
@Mixin(BaseSpawner.class)
public interface BaseSpawnerAccessor {
	@Accessor("spawnDelay")
	int horizon_getSpawnDelay();
}
