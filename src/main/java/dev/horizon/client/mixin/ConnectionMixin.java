package dev.horizon.client.mixin;

import dev.horizon.client.config.Proxies;
import dev.horizon.client.event.AfterSendPacketEvent;
import dev.horizon.client.event.EventBus;
import dev.horizon.client.event.ReceivePacketEvent;
import dev.horizon.client.event.SendPacketEvent;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import net.minecraft.network.BandwidthDebugMonitor;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The selected proxy goes first in the pipeline of every connection to a server (not singleplayer). Received packets
 * (network thread) and sent packets are posted as events; cancelling drops them.
 */
@Mixin(Connection.class)
public abstract class ConnectionMixin {
	@Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
	private void horizon$receive(ChannelHandlerContext ctx, Packet<?> packet, CallbackInfo ci) {
		if (EventBus.INSTANCE.post(new ReceivePacketEvent(packet)).getCancelled()) ci.cancel();
	}

	@Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V", at = @At("HEAD"), cancellable = true)
	private void horizon$send(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
		if (EventBus.INSTANCE.post(new SendPacketEvent(packet)).getCancelled()) ci.cancel();
	}

	@Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V", at = @At("RETURN"))
	private void horizon$afterSend(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
		EventBus.INSTANCE.post(new AfterSendPacketEvent(packet));
	}

	@Inject(method = "configureSerialization", at = @At("RETURN"))
	private static void horizon$proxy(ChannelPipeline pipeline, PacketFlow flow, boolean local, BandwidthDebugMonitor monitor, CallbackInfo ci) {
		if (flow != PacketFlow.CLIENTBOUND || local) return;
		ChannelHandler handler = Proxies.INSTANCE.handler();
		if (handler != null) pipeline.addFirst(handler);
	}
}
