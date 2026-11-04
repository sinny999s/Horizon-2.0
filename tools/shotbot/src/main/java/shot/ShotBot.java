package shot;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Screenshot;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * Drives the game from a script (SHOT_SCRIPT, one command a line) and saves screenshots to SHOT_OUT.
 * Coordinates are window pixels. Works on 26.2 and 26.3: the input hooks are found by reflection.
 */
public final class ShotBot implements ClientModInitializer {
	private static final List<String> lines = new ArrayList<>();
	private static Path out;
	private static PrintWriter log;
	private static int pc, waitTicks, titleTicks, worldTicks;
	private static boolean creating, inWorld;
	private static String lastWorld;
	private static double mx, my;
	private static Method onMove, onButton, onScroll, keyPress, charTyped;

	@Override
	public void onInitializeClient() {
		String script = System.getenv("SHOT_SCRIPT");
		if (script == null) return;
		try {
			lines.addAll(Files.readAllLines(Path.of(script)));
			out = Path.of(System.getenv().getOrDefault("SHOT_OUT", "shots"));
			Files.createDirectories(out);
			log = new PrintWriter(Files.newBufferedWriter(out.resolve("shot.log")), true);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		for (Method m : MouseHandler.class.getDeclaredMethods()) {
			if (m.getName().equals("onMove")) onMove = m;
			if (m.getName().equals("onButton")) onButton = m;
			if (m.getName().equals("onScroll")) onScroll = m;
		}
		for (Method m : KeyboardHandler.class.getDeclaredMethods()) {
			if (m.getName().equals("keyPress")) keyPress = m;
			if (m.getName().equals("charTyped")) charTyped = m;
		}
		for (Method m : new Method[] {onMove, onButton, onScroll, keyPress, charTyped}) m.setAccessible(true);
		ClientTickEvents.END_CLIENT_TICK.register(ShotBot::tick);
	}

	private static void log(String s) {
		System.out.println("SHOT: " + s);
		log.println(s);
	}

	/** Key mappings held down by "hold", with ticks left. */
	private static final java.util.Map<net.minecraft.client.KeyMapping, Integer> held = new java.util.HashMap<>();

	/** Capes left for "capes-all". */
	private static final java.util.ArrayDeque<Object> capes = new java.util.ArrayDeque<>();

	private static Object module(String id) throws Exception {
		Class<?> mods = Class.forName("dev.horizon.client.module.Modules", true, ShotBot.class.getClassLoader());
		for (Object m : (java.util.List<?>) mods.getMethod("getAll").invoke(mods.getField("INSTANCE").get(null))) {
			if (id.equals(m.getClass().getMethod("getId").invoke(m))) return m;
		}
		throw new IllegalArgumentException("no module " + id);
	}

	private static void setting(String module, String id, String value) throws Exception {
		Object m = module(module);
		for (Object s : (java.util.List<?>) m.getClass().getMethod("getSettings").invoke(m)) {
			if (!id.equals(s.getClass().getMethod("getId").invoke(s))) continue;
			s.getClass().getMethod("parse", String.class).invoke(s, value);
			return;
		}
		throw new IllegalArgumentException("no setting " + id);
	}

	private static void tick(Minecraft mc) {
		PacketLog.tick();
		if (!capes.isEmpty() && mc.level != null && mc.level.getGameTime() % 3 == 0) {
			try {
				setting("cape", "style", capes.poll().toString());
			} catch (Exception e) {
				log("ERROR " + e);
			}
		}
		held.replaceAll((k, v) -> v - 1);
		held.entrySet().removeIf(e -> {
			if (e.getValue() < 0) {
				e.getKey().setDown(false);
				return true;
			}
			e.getKey().setDown(true);
			return false;
		});
		try {
			step(mc);
		} catch (Throwable t) {
			log("ERROR at line " + pc + ": " + t);
			t.printStackTrace(log);
			pc++;
		}
	}

	private static void step(Minecraft mc) throws Exception {
		if (waitTicks > 0) {
			waitTicks--;
			return;
		}
		if (creating && !inWorld) {
			if (mc.player != null && mc.level != null && ++worldTicks > 60) {
				inWorld = true;
				log("in world");
			}
			return;
		}
		while (pc < lines.size()) {
			String line = lines.get(pc).trim();
			pc++;
			if (line.isEmpty() || line.startsWith("#")) continue;
			String[] a = line.split("\\s+");
			long h = mc.getWindow().handle();
			switch (a[0]) {
				case "title" -> {
					// Wait on the title screen until it settles.
					if (mc.gui.screen() == null || mc.gui.overlay() != null || ++titleTicks < Integer.parseInt(a[1])) {
						pc--;
						return;
					}
				}
				case "world" -> {
					// "world" is a fresh superflat world; "world normal <seed>" a fresh default world with that seed.
					creating = true;
					boolean normal = a.length > 1 && a[1].equals("normal");
					long seed = normal && a.length > 2 ? Long.parseLong(a[2]) : 1L;
					LevelSettings settings = new LevelSettings("Shot", GameType.CREATIVE, LevelSettings.DifficultySettings.DEFAULT, true,
						WorldDataConfiguration.DEFAULT);
					lastWorld = "shot-" + System.currentTimeMillis();
					mc.createWorldOpenFlows().createFreshLevel(lastWorld, settings, new WorldOptions(seed, false, false),
						p -> p.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(normal ? WorldPresets.NORMAL : WorldPresets.FLAT).value()
							.createWorldDimensions(), mc.gui.screen());
					return;
				}
				case "rejoin" -> {
					// Leave the world (saving it) and open it again, like a relog.
					inWorld = false;
					worldTicks = 0;
					mc.disconnectWithSavingScreen();
					mc.createWorldOpenFlows().openWorld(lastWorld, () -> {});
					return;
				}
				case "wait" -> {
					waitTicks = Integer.parseInt(a[1]) - 1;
					return;
				}
				case "cmd" -> mc.player.connection.sendCommand(line.substring(4).trim());
				case "key" -> {
					int mods = a.length > 2 ? Integer.parseInt(a[2]) : 0;
					KeyEvent e = new KeyEvent(key(a[1]), 0, mods);
					keyPress.invoke(mc.keyboardHandler, h, 1, e);
					keyPress.invoke(mc.keyboardHandler, h, 0, e);
				}
				case "keydown" -> keyPress.invoke(mc.keyboardHandler, h, 1, new KeyEvent(key(a[1]), 0, a.length > 2 ? Integer.parseInt(a[2]) : 0));
				case "keyup" -> keyPress.invoke(mc.keyboardHandler, h, 0, new KeyEvent(key(a[1]), 0, a.length > 2 ? Integer.parseInt(a[2]) : 0));
				case "type" -> {
					for (int cp : line.substring(5).codePoints().toArray()) charTyped.invoke(mc.keyboardHandler, h, new CharacterEvent(cp));
				}
				case "move" -> move(mc, h, Double.parseDouble(a[1]), Double.parseDouble(a[2]));
				case "click", "down", "up" -> {
					if (a.length > 2) move(mc, h, Double.parseDouble(a[1]), Double.parseDouble(a[2]));
					int button = button(a.length > 3 ? a[3] : "L");
					MouseButtonInfo b = new MouseButtonInfo(button, 0);
					if (!a[0].equals("up")) onButton.invoke(mc.mouseHandler, h, b, 1);
					if (!a[0].equals("down")) onButton.invoke(mc.mouseHandler, h, b, 0);
				}
				case "scroll" -> {
					move(mc, h, Double.parseDouble(a[1]), Double.parseDouble(a[2]));
					onScroll.invoke(mc.mouseHandler, h, 0.0, Double.parseDouble(a[3]));
				}
				case "shot" -> {
					Path file = out.resolve(a[1] + ".png");
					Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), img -> {
						try (img) {
							img.writeToFile(file);
							log("shot " + file.getFileName() + " " + img.getWidth() + "x" + img.getHeight());
						} catch (IOException e) {
							log("shot failed " + e);
						}
					});
				}
				case "screen" -> log("screen " + (mc.gui.screen() == null ? "none" : mc.gui.screen().getClass().getName()) + " gui "
					+ mc.getWindow().getGuiScaledWidth() + "x" + mc.getWindow().getGuiScaledHeight() + " scale " + mc.getWindow().getGuiScale()
					+ " window " + mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight());
				case "log" -> log(line.substring(4));
				case "packets" -> {
					PacketLog.start(mc, out.resolve(a[1]));
					log("packets to " + a[1]);
				}
				case "packetsoff" -> PacketLog.stop();
				case "hold" -> {
					// hold <options field, e.g. keyUp> <ticks>: kept down every tick (a real key press needs window focus).
					var f = mc.options.getClass().getField(a[1]);
					net.minecraft.client.KeyMapping k = (net.minecraft.client.KeyMapping) f.get(mc.options);
					k.setDown(true);
					held.put(k, Integer.parseInt(a[2]));
				}
				case "note" -> PacketLog.note(line.substring(5));
				case "fakeheld" -> PacketLog.fakeHeld(mc, a[1], a[2]);
				case "fakemove" -> PacketLog.fakeMove(mc, a[1], Double.parseDouble(a[2]), Double.parseDouble(a[3]), Double.parseDouble(a[4]));
				case "fakeinvis" -> PacketLog.fakeInvisible(mc, a[1], Boolean.parseBoolean(a[2]));
				case "fakeplayer" -> PacketLog.fakePlayer(mc, a[1], Double.parseDouble(a[2]), Double.parseDouble(a[3]), Double.parseDouble(a[4]),
					a.length > 5 ? Float.parseFloat(a[5]) : 0f);
				case "enums" -> {
					// Each line of the class list: a class name. Logs Class.FIELD = NAME for each enum constant.
					try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out.resolve(a[2])))) {
						for (String c : Files.readAllLines(Path.of(a[1]))) {
							try {
								Class<?> k = Class.forName(c.trim(), true, ShotBot.class.getClassLoader());
								if (!Enum.class.isAssignableFrom(k)) continue;
								for (java.lang.reflect.Field f : k.getDeclaredFields()) {
									if (!java.lang.reflect.Modifier.isStatic(f.getModifiers()) || !k.isAssignableFrom(f.getType())) continue;
									f.setAccessible(true);
									Enum<?> e = (Enum<?>) f.get(null);
									w.println(c.trim() + "." + f.getName() + "	" + e.ordinal() + "	" + e.name());
								}
							} catch (Throwable t) {
								w.println(c.trim() + "	ERR " + t);
							}
						}
					}
					log("enums written");
				}
				case "modules" -> {
					ModuleDump.dump(out.resolve(a[1]));
					log("modules written");
				}
				case "perf" -> PerfLog.start(line.substring(5));
				case "perfoff" -> PerfLog.stop(out.resolve("perf.txt"));
				case "toggle" -> {
					// toggle <module id> on|off: the port's module, without a bind
					Class<?> mods = Class.forName("dev.horizon.client.module.Modules", true, ShotBot.class.getClassLoader());
					Object modules = mods.getField("INSTANCE").get(null);
					boolean found = false;
					for (Object m : (java.util.List<?>) mods.getMethod("getAll").invoke(modules)) {
						Object id = m.getClass().getMethod("getName").invoke(m);
						if (!a[1].equals(String.valueOf(id).trim()) && !a[1].equals(m.getClass().getMethod("getId").invoke(m))) continue;
						m.getClass().getMethod(a[2].equals("on") ? "enable" : "disable").invoke(m);
						found = true;
					}
					if (!found) log("ERROR no module " + a[1]);
				}
				case "hudlog" -> {
					// Every HUD widget's place and size in GUI units, as the frame's stick-together rules left them.
					Class<?> hud = Class.forName("dev.horizon.client.hud.Hud", true, ShotBot.class.getClassLoader());
					for (Object w : (java.util.List<?>) hud.getMethod("getWidgets").invoke(hud.getField("INSTANCE").get(null))) {
						Class<?> c = Class.forName("dev.horizon.client.hud.HudWidget", true, ShotBot.class.getClassLoader());
						log("HUD " + c.getMethod("getId").invoke(w) + " on=" + c.getMethod("getEnabled").invoke(w)
							+ " left=" + c.getMethod("getLeft").invoke(w) + " top=" + c.getMethod("getTop").invoke(w)
							+ " w=" + c.getMethod("getWidth").invoke(w) + " h=" + c.getMethod("getHeight").invoke(w));
					}
				}
				case "set" -> {
					// set <module id> <setting id> <value>: a module's setting, as a config would load it
					setting(a[1], a[2], a[3]);
				}
				case "capes-count" -> {
					try (var f = java.nio.file.Files.list(mc.gameDirectory.toPath().resolve("horizon/capes"))) {
						log("capes saved: " + f.count());
					}
				}
				case "capes-all" -> {
					// Queues every cape: the next ticks wear each for a few ticks, so each gets fetched.
					Object module = module("cape");
					Object style = module.getClass().getMethod("getStyle").invoke(module);
					capes.addAll((java.util.List<?>) style.getClass().getMethod("getConstants").invoke(style));
				}
				case "kmodules" -> {
					// The port's modules and settings, for the ClickGUI mockup.
					KModuleDump.dump(out.resolve(a[1]));
					log("kmodules written");
				}
				case "getters" -> {
					// Each line: class, method, ... (tab separated). Invokes the pure static getter and logs its value.
					try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out.resolve(a[2])))) {
						for (String g : Files.readAllLines(Path.of(a[1]))) {
							String[] f = g.split("	");
							try {
								Method m = Class.forName(f[0], true, ShotBot.class.getClassLoader()).getDeclaredMethod(f[1]);
								m.setAccessible(true);
								Object v = m.invoke(null);
								String s = v == null ? "null" : v.getClass().isArray() ? "array[" + java.lang.reflect.Array.getLength(v) + "]" : String.valueOf(v);
								if (s.length() > 80) s = s.substring(0, 80);
								w.println(f[0] + "." + f[1] + "	" + f[2] + "	" + s);
							} catch (Throwable t) {
								w.println(f[0] + "." + f[1] + "	ERR " + t);
							}
						}
					}
					log("getters written");
				}
				case "quit" -> {
					PacketLog.stop();
					log("quit");
					mc.stop();
					return;
				}
				default -> log("unknown command " + line);
			}
		}
	}

	private static void move(Minecraft mc, long h, double x, double y) throws Exception {
		if (onMove.getParameterCount() == 3) onMove.invoke(mc.mouseHandler, h, x, y);
		else onMove.invoke(mc.mouseHandler, h, x, y, x - mx, y - my);
		mx = x;
		my = y;
	}

	/** A key by InputConstants name (I, ESCAPE, F1, LALT...) or a raw code; the codes differ between 26.2 and 26.3. */
	private static int key(String name) throws Exception {
		if (name.matches("-?[0-9]+")) return Integer.parseInt(name);
		return com.mojang.blaze3d.platform.InputConstants.class.getField("KEY_" + name).getInt(null);
	}

	/** L, R or M (26.2 counts mouse buttons from 0, 26.3 from 1). */
	private static int button(String name) throws Exception {
		String f = switch (name) { case "R" -> "MOUSE_BUTTON_RIGHT"; case "M" -> "MOUSE_BUTTON_MIDDLE"; default -> "MOUSE_BUTTON_LEFT"; };
		return com.mojang.blaze3d.platform.InputConstants.class.getField(f).getInt(null);
	}

	static String fmt(double d) {
		return String.format(Locale.ROOT, "%.1f", d);
	}
}
