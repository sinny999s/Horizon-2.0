package dev.horizon.client.mixin.deps;

import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Whether the sound engine has loaded (the startup sound waits for it). */
@Mixin(SoundEngine.class)
public interface SoundEngineAccessor {
	@Accessor("loaded")
	boolean horizon_loaded();
}
