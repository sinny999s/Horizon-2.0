package dev.horizon.client.mixin;

import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.server.LanServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A LAN game's details, for the client's Multiplayer screen. */
@Mixin(ServerSelectionList.NetworkServerEntry.class)
public interface NetworkServerEntryAccessor {
	@Accessor("serverData")
	LanServer horizon_lanServer();
}
