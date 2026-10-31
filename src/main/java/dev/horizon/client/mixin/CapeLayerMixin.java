package dev.horizon.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.horizon.client.render.CapePhysics;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CapeLayer.class)
public abstract class CapeLayerMixin {
	@Shadow
	private HumanoidModel model;

	@Inject(
		method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/AvatarRenderState;FF)V",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;III)V"
		),
		cancellable = true
	)
	private void horizon$renderWaveyCape(
		PoseStack poseStack,
		SubmitNodeCollector collector,
		int lightCoords,
		AvatarRenderState state,
		float yRot,
		float xRot,
		CallbackInfo ci
	) {
		PlayerSkin skin = state.skin;
		if (skin == null) return;
		ClientAsset.Texture capeTexture = skin.cape();
		if (capeTexture == null) return;
		Identifier texturePath = capeTexture.texturePath();
		if (texturePath == null) return;

		this.model.setupAnim(state);
		boolean handled = CapePhysics.INSTANCE.render(poseStack, collector, lightCoords, state, texturePath, this.model.root(), this.model.body);
		if (handled) {
			poseStack.popPose();
			ci.cancel();
		}
	}
}
