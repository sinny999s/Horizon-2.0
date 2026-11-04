package dev.horizon.client.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Vanilla left/right click invokers and miss-cooldown reset for legit macros and ghost modules. */
@Mixin(Minecraft.class)
public interface MinecraftAccessor {
	@Invoker("startAttack")
	boolean horizon_startAttack();

	@Invoker("startUseItem")
	void horizon_startUseItem();

	@Invoker("pick")
	void horizon_pick(float partialTick);

	@Accessor("missTime")
	void horizon_setMissTime(int missTime);
}
