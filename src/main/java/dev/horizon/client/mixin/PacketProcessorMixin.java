package dev.horizon.client.mixin;

import dev.horizon.client.event.AfterReceivePacketEvent;
import dev.horizon.client.event.EventBus;
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** After a packet was handled on the main thread; bundles are posted as their packets. */
@Mixin(targets = "net.minecraft.network.PacketProcessor$ListenerAndPacket")
public abstract class PacketProcessorMixin {
	@Shadow
	@Final
	private Packet<?> packet;

	@Inject(method = "handle", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/Packet;handle(Lnet/minecraft/network/PacketListener;)V", shift = At.Shift.AFTER))
	private void horizon$afterHandle(CallbackInfo ci) {
		if (packet instanceof BundlePacket<?> bundle) {
			for (Packet<?> p : bundle.subPackets()) EventBus.INSTANCE.post(new AfterReceivePacketEvent(p));
		} else {
			EventBus.INSTANCE.post(new AfterReceivePacketEvent(packet));
		}
	}
}
