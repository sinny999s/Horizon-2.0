package dev.horizon.client.mixin;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The world list and its search box, for the client's Singleplayer screen ({@code WorldsScreen}). */
@Mixin(SelectWorldScreen.class)
public interface SelectWorldScreenAccessor {
	@Accessor("list")
	WorldSelectionList horizon_list();

	@Accessor("searchBox")
	EditBox horizon_searchBox();
}
