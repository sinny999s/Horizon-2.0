package dev.horizon.client.mixin.pvp;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The sprint state last sent to the server (vanilla's sprint sync compares against it). */
@Mixin(LocalPlayer.class)
public interface PvpLocalPlayerAccessor {
	@Accessor("wasSprinting")
	void horizon_setWasSprinting(boolean wasSprinting);
}
