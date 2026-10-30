package shot;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;

/**
 * Dumps the port's (dev.horizon.client) modules for the ClickGUI mockup: each module's id, sub-category, state and bind, and
 * each setting's type, value, default, range, enum constants and group. Visibility is sampled: every bool/enum/number
 * setting is changed alone and in pairs to find which ones a setting's predicate reads, then the predicate is
 * evaluated over all combinations of those, so the mockup can look the answer up in a table.
 */
final class KModuleDump {
	private static final ClassLoader CL = KModuleDump.class.getClassLoader();
	private static Method getValue, setValue, getDefault, getVisible, invoke;

	static void dump(Path file) throws Exception {
		Class<?> setting = k("dev.horizon.client.setting.Setting");
		getValue = setting.getMethod("getValue");
		setValue = setting.getMethod("setValue", Object.class);
		getDefault = setting.getMethod("getDefault");
		getVisible = setting.getMethod("getVisible");
		invoke = k("kotlin.jvm.functions.Function0").getMethod("invoke");

		Object modules = k("dev.horizon.client.module.Modules").getField("INSTANCE").get(null);
		List<?> all = (List<?>) modules.getClass().getMethod("getAll").invoke(modules);
		JsonArray out = new JsonArray();
		for (Object m : all) out.add(module(m));

		JsonObject root = new JsonObject();
		root.add("modules", out);
		root.add("labels", labels());
		root.add("theme", theme());
		root.add("hud", hud());
		JsonObject reg = new JsonObject();
		reg.add("blocks", registry(BuiltInRegistries.BLOCK.stream().map(b -> b.getDescriptionId()).toList()));
		reg.add("items", registry(BuiltInRegistries.ITEM.stream().map(i -> i.getDescriptionId()).toList()));
		reg.add("entities", registry(BuiltInRegistries.ENTITY_TYPE.stream().map(e -> e.getDescriptionId()).toList()));
		root.add("registries", reg);
		Files.writeString(file, new GsonBuilder().disableHtmlEscaping().create().toJson(root));
	}

	private static Class<?> k(String name) throws ClassNotFoundException {
		return Class.forName(name, true, CL);
	}

	private static Object call(Object o, String method) throws Exception {
		Method m = o.getClass().getMethod(method);
		m.setAccessible(true);
		return m.invoke(o);
	}

	private static JsonObject module(Object m) throws Exception {
		JsonObject mo = new JsonObject();
		mo.addProperty("id", (String) call(m, "getId"));
		mo.addProperty("name", ((String) call(m, "getName")).trim());
		mo.addProperty("sub", (String) call(call(m, "getSubCategory"), "getId"));
		mo.addProperty("class", m.getClass().getSimpleName());
		mo.addProperty("enabled", (Boolean) call(m, "getEnabled"));
		JsonArray aliases = new JsonArray();
		for (Object a : (List<?>) call(m, "getAliases")) aliases.add((String) a);
		mo.add("aliases", aliases);

		List<?> settings = (List<?>) call(m, "getSettings");
		Map<Object, Integer> groups = new IdentityHashMap<>();
		for (int i = 0; i < settings.size(); i++) {
			Object s = settings.get(i);
			if (s.getClass().getSimpleName().equals("GroupSetting")) groups.put(getDefault.invoke(s), i);
		}
		JsonArray so = new JsonArray();
		for (Object s : settings) {
			JsonObject o = new JsonObject();
			String type = s.getClass().getSimpleName().replace("Setting", "").toLowerCase();
			o.addProperty("type", type);
			o.addProperty("id", (String) call(s, "getId"));
			o.addProperty("name", ((String) call(s, "getDisplayName")).trim());
			o.add("value", value(getValue.invoke(s)));
			o.add("default", value(getDefault.invoke(s)));
			Object parent = call(s, "getParent");
			if (parent != null) o.addProperty("parent", groups.getOrDefault(parent, -1));
			switch (type) {
				case "int" -> {
					Object r = call(s, "getRange");
					o.addProperty("min", (Integer) call(r, "getFirst"));
					o.addProperty("max", (Integer) call(r, "getLast"));
				}
				case "float" -> {
					Object r = call(s, "getRange");
					o.addProperty("min", (Float) call(r, "getStart"));
					o.addProperty("max", (Float) call(r, "getEndInclusive"));
				}
				case "enum" -> {
					JsonArray c = new JsonArray();
					for (Object e : (List<?>) call(s, "getConstants")) c.add(((Enum<?>) e).name());
					o.add("constants", c);
				}
				default -> {}
			}
			so.add(o);
		}
		mo.add("settings", so);
		mo.add("visibility", visibility(settings));
		return mo;
	}

	private static JsonElement value(Object v) throws Exception {
		if (v == null) return JsonNull.INSTANCE;
		if (v instanceof Boolean b) return new JsonPrimitive(b);
		if (v instanceof Number n) return new JsonPrimitive(n);
		if (v instanceof String s) return new JsonPrimitive(s);
		if (v instanceof Enum<?> e) return new JsonPrimitive(e.name());
		if (v instanceof java.awt.Color c) return new JsonPrimitive(c.getRGB());
		if (v instanceof List<?> l) {
			JsonArray a = new JsonArray();
			for (Object e : l) a.add((String) e.getClass().getMethod("getDescriptionId").invoke(e));
			return a;
		}
		String cls = v.getClass().getSimpleName();
		switch (cls) {
			case "Bind" -> {
				JsonObject o = new JsonObject();
				o.addProperty("key", (Integer) call(v, "getKey"));
				o.addProperty("hold", (Boolean) call(v, "getHold"));
				o.addProperty("mouse", (Boolean) call(v, "getMouse"));
				Object keys = k("dev.horizon.client.gui.Keys").getField("INSTANCE").get(null);
				o.addProperty("name", (String) keys.getClass().getMethod("name", v.getClass()).invoke(keys, v));
				return o;
			}
			case "Group" -> {
				return new JsonPrimitive(((Enum<?>) call(v, "getMode")).name());
			}
			case "Notation" -> {
				return new JsonPrimitive((String) call(v, "getText"));
			}
			default -> {
				return JsonNull.INSTANCE;
			}
		}
	}

	// ---- visibility ----

	private static boolean eval(Object s) {
		try {
			return Boolean.TRUE.equals(invoke.invoke(getVisible.invoke(s)));
		} catch (Throwable t) {
			return true;
		}
	}

	private static boolean[] evalAll(List<?> settings) {
		boolean[] r = new boolean[settings.size()];
		for (int i = 0; i < r.length; i++) r[i] = eval(settings.get(i));
		return r;
	}

	/**
	 * {"drivers": [[index, values...]...], "rules": [per setting: true/false, or {"on": [driver indices], "table": "0110"}]}.
	 * Number drivers are sampled at [min, default, max]; the mockup maps a value below/at/above the default onto them.
	 * The table's first listed driver changes fastest.
	 */
	private static JsonObject visibility(List<?> settings) throws Exception {
		int n = settings.size();
		List<Integer> drivers = new ArrayList<>();
		List<Object[]> values = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			Object s = settings.get(i);
			Object def = getDefault.invoke(s);
			String type = s.getClass().getSimpleName();
			if (def instanceof Boolean) {
				drivers.add(i);
				values.add(new Object[] {false, true});
			} else if (def instanceof Enum<?> e) {
				drivers.add(i);
				values.add(e.getDeclaringClass().getEnumConstants());
			} else if (type.equals("IntSetting")) {
				Object r = call(s, "getRange");
				drivers.add(i);
				values.add(new Object[] {call(r, "getFirst"), def, call(r, "getLast")});
			} else if (type.equals("FloatSetting")) {
				Object r = call(s, "getRange");
				drivers.add(i);
				values.add(new Object[] {call(r, "getStart"), def, call(r, "getEndInclusive")});
			}
		}
		Object[] saved = new Object[n];
		for (int i = 0; i < n; i++) saved[i] = getValue.invoke(settings.get(i));
		JsonObject res = new JsonObject();
		try {
			int d = drivers.size();
			Object[] defaults = new Object[d];
			for (int a = 0; a < d; a++) defaults[a] = getDefault.invoke(settings.get(drivers.get(a)));
			Runnable reset = () -> {
				try {
					for (int a = 0; a < d; a++) setValue.invoke(settings.get(drivers.get(a)), defaults[a]);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			};
			reset.run();
			boolean[] base = evalAll(settings);
			List<TreeSet<Integer>> on = new ArrayList<>();
			for (int i = 0; i < n; i++) on.add(new TreeSet<>());
			Map<Long, boolean[]> single = new HashMap<>();
			for (int a = 0; a < d; a++) {
				Object[] va = values.get(a);
				for (int x = 0; x < va.length; x++) {
					if (va[x].equals(defaults[a])) continue;
					setValue.invoke(settings.get(drivers.get(a)), va[x]);
					boolean[] v = evalAll(settings);
					single.put(((long) a << 32) | x, v);
					for (int s = 0; s < n; s++) if (v[s] != base[s]) on.get(s).add(a);
					setValue.invoke(settings.get(drivers.get(a)), defaults[a]);
				}
			}
			for (int a = 0; a < d; a++) {
				for (int b = a + 1; b < d; b++) {
					Object[] va = values.get(a), vb = values.get(b);
					for (int x = 0; x < va.length; x++) {
						boolean[] sa = single.get(((long) a << 32) | x);
						if (sa == null) continue;
						for (int y = 0; y < vb.length; y++) {
							boolean[] sb = single.get(((long) b << 32) | y);
							if (sb == null) continue;
							setValue.invoke(settings.get(drivers.get(a)), va[x]);
							setValue.invoke(settings.get(drivers.get(b)), vb[y]);
							boolean[] v = evalAll(settings);
							for (int s = 0; s < n; s++) {
								if (v[s] != sb[s]) on.get(s).add(a);
								if (v[s] != sa[s]) on.get(s).add(b);
							}
							setValue.invoke(settings.get(drivers.get(a)), defaults[a]);
							setValue.invoke(settings.get(drivers.get(b)), defaults[b]);
						}
					}
				}
			}
			// Random assignments catch predicates over three or more drivers: where the table would be wrong, every
			// driver that flips the answer on its own there joins the setting's drivers.
			Random rnd = new Random(1);
			int[] pick = new int[d];
			for (int round = 0; round < 400 && d > 0; round++) {
				for (int a = 0; a < d; a++) {
					pick[a] = rnd.nextInt(values.get(a).length);
					setValue.invoke(settings.get(drivers.get(a)), values.get(a)[pick[a]]);
				}
				boolean[] v = evalAll(settings);
				for (int s = 0; s < n; s++) {
					if (v[s] == lookup(settings, s, on.get(s), drivers, values, defaults, pick)) continue;
					for (int a = 0; a < d; a++) {
						if (on.get(s).contains(a)) continue;
						Object[] va = values.get(a);
						for (int x = 0; x < va.length; x++) {
							if (x == pick[a]) continue;
							setValue.invoke(settings.get(drivers.get(a)), va[x]);
							if (eval(settings.get(s)) != v[s]) on.get(s).add(a);
						}
						setValue.invoke(settings.get(drivers.get(a)), values.get(a)[pick[a]]);
					}
				}
			}
			reset.run();
			JsonArray dj = new JsonArray();
			for (int a = 0; a < d; a++) {
				JsonArray e = new JsonArray();
				e.add(drivers.get(a));
				for (Object v : values.get(a)) e.add(value(v));
				dj.add(e);
			}
			res.add("drivers", dj);
			JsonArray rules = new JsonArray();
			for (int s = 0; s < n; s++) {
				TreeSet<Integer> r = on.get(s);
				if (r.isEmpty()) {
					rules.add(base[s]);
					continue;
				}
				int size = 1;
				for (int a : r) size *= values.get(a).length;
				if (size > 8192) {
					rules.add(true);
					continue;
				}
				StringBuilder table = new StringBuilder();
				int[] idx = new int[d];
				for (int c = 0; c < size; c++) {
					int rest = c;
					for (int a : r) {
						idx[a] = rest % values.get(a).length;
						rest /= values.get(a).length;
						setValue.invoke(settings.get(drivers.get(a)), values.get(a)[idx[a]]);
					}
					table.append(eval(settings.get(s)) ? '1' : '0');
				}
				reset.run();
				JsonObject rule = new JsonObject();
				JsonArray onj = new JsonArray();
				for (int a : r) onj.add(a);
				rule.add("on", onj);
				rule.addProperty("table", table.toString());
				rules.add(rule);
			}
			res.add("rules", rules);
		} finally {
			for (int i = 0; i < n; i++) setValue.invoke(settings.get(i), saved[i]);
		}
		return res;
	}

	/** What the table would say for setting [s] under the assignment [pick] (evaluated with the other drivers at default). */
	private static boolean lookup(List<?> settings, int s, TreeSet<Integer> on, List<Integer> drivers, List<Object[]> values,
								  Object[] defaults, int[] pick) throws Exception {
		Object[] now = new Object[drivers.size()];
		for (int a = 0; a < drivers.size(); a++) {
			now[a] = getValue.invoke(settings.get(drivers.get(a)));
			if (!on.contains(a)) setValue.invoke(settings.get(drivers.get(a)), defaults[a]);
		}
		boolean r = eval(settings.get(s));
		for (int a = 0; a < drivers.size(); a++) setValue.invoke(settings.get(drivers.get(a)), now[a]);
		return r;
	}

	// ---- extras ----

	private static JsonArray labels() {
		JsonArray a = new JsonArray();
		try {
			Object inst = k("dev.horizon.client.setting.ValueLabels").getField("INSTANCE").get(null);
			Field f = inst.getClass().getDeclaredField("labels");
			f.setAccessible(true);
			for (Object l : (List<?>) f.get(inst)) {
				JsonArray e = new JsonArray();
				for (String name : new String[] {"id", "value", "text"}) {
					Field g = l.getClass().getDeclaredField(name);
					g.setAccessible(true);
					e.add(value(g.get(l)));
				}
				a.add(e);
			}
		} catch (Throwable t) {
			a.add("ERR " + t);
		}
		return a;
	}

	/** The HUD widgets in the Hud editor's order: state, position (fractions of the GUI-scaled screen) and settings. */
	private static JsonArray hud() {
		JsonArray a = new JsonArray();
		try {
			Object hud = k("dev.horizon.client.hud.Hud").getField("INSTANCE").get(null);
			for (Object w : (List<?>) hud.getClass().getMethod("getWidgets").invoke(hud)) {
				JsonObject o = new JsonObject();
				o.addProperty("id", (String) call(w, "getId"));
				o.addProperty("name", ((String) call(w, "getName")).trim());
				o.addProperty("enabled", (Boolean) call(w, "getEnabled"));
				Object pos = call(w, "getPos");
				o.addProperty("x", (Float) call(pos, "getX"));
				o.addProperty("y", (Float) call(pos, "getY"));
				JsonArray so = new JsonArray();
				for (Object s : (List<?>) call(w, "getSettings")) {
					String id = (String) call(s, "getId");
					if (id.equals("enabled") || id.equals("position")) continue;
					JsonObject e = new JsonObject();
					e.addProperty("type", s.getClass().getSimpleName().replace("Setting", "").toLowerCase());
					e.addProperty("id", id);
					e.addProperty("name", ((String) call(s, "getDisplayName")).trim());
					e.add("value", value(getValue.invoke(s)));
					e.add("default", value(getDefault.invoke(s)));
					so.add(e);
				}
				o.add("settings", so);
				a.add(o);
			}
		} catch (Throwable t) {
			a.add("ERR " + t);
		}
		return a;
	}

	private static JsonObject theme() {
		JsonObject o = new JsonObject();
		try {
			Object themes = k("dev.horizon.client.render.Themes").getField("INSTANCE").get(null);
			Object cur = call(themes, "getCurrent");
			o.addProperty("id", (String) call(cur, "getId"));
			for (Map.Entry<?, ?> e : ((Map<?, ?>) call(cur, "colors")).entrySet()) o.addProperty((String) e.getKey(), (Integer) e.getValue());
		} catch (Throwable t) {
			o.addProperty("error", t.toString());
		}
		return o;
	}

	/** [description id, English name] pairs. */
	private static JsonArray registry(List<String> ids) {
		Language lang = Language.getInstance();
		JsonArray a = new JsonArray();
		for (String id : ids) {
			JsonArray e = new JsonArray();
			e.add(id);
			e.add(lang.getOrDefault(id));
			a.add(e);
		}
		return a;
	}
}
