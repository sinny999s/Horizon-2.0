package dev.horizon.client.mixin;

import net.minecraft.client.gui.screens.FaviconTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The uploaded icon texture (null until one is uploaded), for the client's world and server screens. */
@Mixin(FaviconTexture.class)
public interface FaviconTextureAccessor {
	@Accessor("texture")
	DynamicTexture horizon_texture();
}
