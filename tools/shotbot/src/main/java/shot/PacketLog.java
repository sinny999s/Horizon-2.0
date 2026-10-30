package shot;

import com.mojang.authlib.GameProfile;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Logs every packet the client sends (one line: tick, packet class, its fields), from a Netty outbound handler at the
 * pipeline's tail, so it sees packets before encoding and works the same on 26.2 and 26.3 without mixins.
 */
final class PacketLog {
	static volatile int tick;
	private static PrintWriter out;
	private static Channel channel;
	private static int start;

	static void tick() {
		tick++;
	}

	static synchronized void start(Minecraft mc, Path file) throws Exception {
		out = new PrintWriter(Files.newBufferedWriter(file), true);
		start = tick;
		Connection c = mc.getConnection().getConnection();
		Channel ch = null;
		for (Class<?> k = c.getClass(); k != null; k = k.getSuperclass()) {
			for (Field f : k.getDeclaredFields()) {
				if (Channel.class.isAssignableFrom(f.getType())) {
					f.setAccessible(true);
					ch = (Channel) f.get(c);
				}
			}
		}
		if (ch == null) throw new IllegalStateException("no channel");
		channel = ch;
		if (ch.pipeline().get("shotbot_in") == null) {
			ChannelInboundHandlerAdapter in = new ChannelInboundHandlerAdapter() {
				@Override
				public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
					if (msg instanceof Packet<?> p) logIn(p);
					super.channelRead(ctx, msg);
				}
			};
			if (ch.pipeline().get("packet_handler") != null) ch.pipeline().addBefore("packet_handler", "shotbot_in", in);
			else ch.pipeline().addFirst("shotbot_in", in);
		}
		if (ch.pipeline().get("shotbot_log") == null) {
			ch.pipeline().addLast("shotbot_log", new ChannelOutboundHandlerAdapter() {
				@Override
				public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
					if (msg instanceof Packet<?> p) log(p);
					super.write(ctx, msg, promise);
				}
			});
		}
	}

	static synchronized void stop() {
		if (out != null) out.close();
		out = null;
	}

	static synchronized void note(String s) {
		if (out != null) out.println((tick - start) + "\t# " + s);
	}

	private static synchronized void log(Packet<?> p) {
		if (out == null) return;
		String name = p.getClass().getSimpleName();
		if (name.contains("KeepAlive") || name.contains("ClientTickEnd") || name.contains("ChunkBatch") || name.contains("Pong")) return;
		out.println((tick - start) + "\t" + name + "\t" + fields(p));
	}

	/** Incoming packets that matter for the combat comparisons, marked with "<". */
	private static synchronized void logIn(Packet<?> p) {
		if (out == null) return;
		String name = p.getClass().getSimpleName();
		if (name.equals("ClientboundBundlePacket")) {
			try {
				for (Object sub : (Iterable<?>) p.getClass().getMethod("subPackets").invoke(p)) if (sub instanceof Packet<?> s) logIn(s);
			} catch (Exception e) {
				out.println((tick - start) + "\t< bundle? " + e);
			}
			return;
		}
		if (!(name.contains("AddEntity") || name.contains("RemoveEntities") || name.contains("Explode") || name.contains("EntityEvent")
			|| name.contains("BlockUpdate"))) return;
		out.println((tick - start) + "\t< " + name + "\t" + fields(p));
	}

	private static String fields(Object o) {
		StringBuilder b = new StringBuilder();
		if (o.getClass().isRecord()) {
			for (RecordComponent rc : o.getClass().getRecordComponents()) {
				try {
					var m = rc.getAccessor();
					m.setAccessible(true);
					b.append(rc.getName()).append('=').append(value(m.invoke(o))).append(' ');
				} catch (Exception e) {
					b.append(rc.getName()).append("=? ");
				}
			}
			return b.toString().trim();
		}
		for (Class<?> k = o.getClass(); k != null && k != Object.class; k = k.getSuperclass()) {
			for (Field f : k.getDeclaredFields()) {
				if (Modifier.isStatic(f.getModifiers())) continue;
				try {
					f.setAccessible(true);
					b.append(f.getName()).append('=').append(value(f.get(o))).append(' ');
				} catch (Exception e) {
					b.append(f.getName()).append("=? ");
				}
			}
		}
		return b.toString().trim();
	}

	private static String value(Object v) {
		if (v == null) return "null";
		if (v instanceof Float f) return String.format(Locale.ROOT, "%.4f", f);
		if (v instanceof Double d) return String.format(Locale.ROOT, "%.4f", d);
		if (v instanceof Vec3 p) return String.format(Locale.ROOT, "(%.4f,%.4f,%.4f)", p.x, p.y, p.z);
		if (v instanceof BlockPos p) return "[" + p.getX() + "," + p.getY() + "," + p.getZ() + "]";
		if (v instanceof BlockHitResult h) return "hit{" + value(h.getBlockPos()) + " " + h.getDirection() + " " + value(h.getLocation()) + " " + h.isInside() + "}";
		if (v instanceof Enum<?> e) return e.name();
		if (v instanceof Number || v instanceof Boolean || v instanceof String) return v.toString();
		String s = v.toString();
		return s.length() > 120 ? s.substring(0, 120) : s;
	}

	/** A client-side player (the server knows nothing of it) for the combat modules to target. */
	static void fakePlayer(Minecraft mc, String name, double x, double y, double z, float yaw) {
		RemotePlayer p = new RemotePlayer(mc.level, new GameProfile(UUID.nameUUIDFromBytes(name.getBytes()), name));
		p.setId(-1000 - name.length());
		p.setPos(x, y, z);
		p.setYRot(yaw);
		p.setYHeadRot(yaw);
		p.xo = x;
		p.yo = y;
		p.zo = z;
		mc.level.addEntity(p);
		note("fakeplayer " + name + " id " + p.getId());
	}

	static void close() throws IOException {
		stop();
	}
}
