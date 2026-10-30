package dev.horizon.client.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The block-prediction sequence numbers that use-item and block-action packets carry. */
@Mixin(ClientLevel.class)
public interface ClientLevelAccessor {
	@Invoker("getBlockStatePredictionHandler")
	BlockStatePredictionHandler horizon_predictionHandler();
}
