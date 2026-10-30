package dev.horizon.client.mixin.cosmetic1;

import net.minecraft.client.model.object.crystal.EndCrystalModel;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The original's EndCrystalRendererAccessor. */
@Mixin(EndCrystalRenderer.class)
public interface EndCrystalRendererAccessor {
	@Accessor("model")
	EndCrystalModel horizon_model();
}
