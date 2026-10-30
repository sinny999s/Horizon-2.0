package dev.horizon.client.mixin;

import net.minecraft.client.gui.screens.FaviconTexture;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A world's icon, for the client's Singleplayer screen. */
@Mixin(WorldSelectionList.WorldListEntry.class)
public interface WorldListEntryAccessor {
	@Accessor("icon")
	FaviconTexture horizon_icon();
}
