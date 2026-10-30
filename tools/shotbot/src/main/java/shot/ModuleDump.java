package shot;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;

/**
 * Dumps the original's modules and their settings by reflection: ids, classes, defaults, ranges, groups, enum constants,
 * value labels, and visibility samples (each setting's predicate evaluated under changed driver values) so the
 * show/hide rules can be reconstructed.
 */
final class ModuleDump {
	private static final String P = "su.horizon.";

	static void dump(Path file) throws Exception {
		Class<?> cb = Class.forName(P + "cb");
		Class<?> hs = Class.forName(P + "hs");
		Field modsField = cb.getDeclaredField("O");
		modsField.setAccessible(true);
		Iterable<?> mods = (Iterable<?>) modsField.get(null);
		Field fId = field(hs, "r"), fDef = field(hs, "w"), fCur = field(hs, "O"), fParent = field(hs, "S"), fVis = field(hs, "c"), fName = field(hs, "d");
		JsonArray out = new JsonArray();
		for (Object m : mods) {
			JsonObject mo = new JsonObject();
			mo.addProperty("class", m.getClass().getName());
			JsonObject mf = new JsonObject();
			List<Object> settings = null;
			for (Class<?> k = m.getClass(); k != null && k != Object.class; k = k.getSuperclass()) {
				for (Field f : k.getDeclaredFields()) {
					if (Modifier.isStatic(f.getModifiers())) continue;
					f.setAccessible(true);
					Object v = f.get(m);
					if (k.getName().equals(P + "_7")) {
						if (v instanceof List<?> l && !l.isEmpty() && hs.isInstance(l.get(0))) settings = new ArrayList<>(l);
						mf.addProperty(f.getName(), describe(v, hs, fId));
					}
				}
			}
			mo.add("fields", mf);
			JsonArray so = new JsonArray();
			if (settings != null) {
				Map<Object, Integer> groups = new IdentityHashMap<>();
				for (int i = 0; i < settings.size(); i++) {
					Object s = settings.get(i);
					if (s.getClass().getSimpleName().equals("hf")) groups.put(fCur.get(s), i);
				}
				for (Object s : settings) {
					JsonObject o = new JsonObject();
					o.addProperty("cls", s.getClass().getSimpleName());
					o.addProperty("id", (String) fId.get(s));
					o.addProperty("name", (String) fName.get(s));
					Object def = fDef.get(s);
					o.addProperty("default", value(def));
					o.addProperty("defaultClass", def == null ? "null" : def.getClass().getName());
					Object par = fParent.get(s);
					o.addProperty("parent", par == null ? -1 : groups.getOrDefault(par, -2));
					if (def instanceof Enum<?> e) {
						JsonArray c = new JsonArray();
						for (Object k : e.getDeclaringClass().getEnumConstants()) c.add(((Enum<?>) k).name());
						o.add("constants", c);
						o.addProperty("enumClass", e.getDeclaringClass().getName());
					}
					if (s.getClass().getSimpleName().equals("hf")) {
						for (Field f : def.getClass().getDeclaredFields()) {
							if (Modifier.isStatic(f.getModifiers())) continue;
							f.setAccessible(true);
							Object v = f.get(def);
							if (v instanceof Enum<?> en) o.addProperty("groupMode", en.name());
						}
					}
					JsonObject extra = new JsonObject();
					for (Field f : s.getClass().getDeclaredFields()) {
						if (Modifier.isStatic(f.getModifiers())) continue;
						f.setAccessible(true);
						extra.addProperty(f.getName(), describe(f.get(s), hs, fId));
					}
					o.add("extra", extra);
					o.addProperty("visibleClass", fVis.get(s).getClass().getName());
					so.add(o);
				}
				mo.add("visibility", visibility(settings, fCur, fDef, fVis));
			}
			mo.add("settings", so);
			out.add(mo);
		}
		JsonObject root = new JsonObject();
		root.add("modules", out);
		root.add("labels", labels());
		Files.writeString(file, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root));
	}

	private static Field field(Class<?> k, String name) throws Exception {
		Field f = k.getDeclaredField(name);
		f.setAccessible(true);
		return f;
	}

	private static String value(Object v) {
		if (v == null) return "null";
		if (v instanceof Enum<?> e) return e.name();
		if (v instanceof java.awt.Color c) return String.valueOf(c.getRGB());
		return describeValueObject(v);
	}

	/** Value objects (lists of entities/blocks/items, notation text, binds...) by their fields. */
	private static String describeValueObject(Object v) {
		if (v instanceof Boolean || v instanceof Number || v instanceof String) return String.valueOf(v);
		StringBuilder sb = new StringBuilder(v.getClass().getSimpleName()).append('{');
		for (Field f : v.getClass().getDeclaredFields()) {
			if (Modifier.isStatic(f.getModifiers())) continue;
			try {
				f.setAccessible(true);
				Object x = f.get(v);
				sb.append(f.getName()).append('=');
				if (x instanceof List<?> l) {
					sb.append('[');
					for (Object e : l) sb.append(registryId(e)).append(',');
					sb.append(']');
				} else {
					sb.append(x);
				}
				sb.append(';');
			} catch (Throwable t) {
				sb.append("ERR;");
			}
		}
		return sb.append('}').toString();
	}

	private static String registryId(Object e) {
		try {
			return (String) e.getClass().getMethod("getDescriptionId").invoke(e);
		} catch (Throwable t) {
			return String.valueOf(e);
		}
	}

	private static String describe(Object v, Class<?> hs, Field fId) throws Exception {
		if (v == null) return "null";
		if (v instanceof List<?> l) {
			StringBuilder sb = new StringBuilder("List[");
			for (Object e : l) sb.append(hs.isInstance(e) ? "S:" + fId.get(e) : String.valueOf(e)).append(',');
			return sb.append(']').toString();
		}
		if (hs.isInstance(v)) return "S:" + fId.get(v);
		if (isIntRange(v)) return "IntRange " + call(v, "getFirst") + ".." + call(v, "getLast");
		if (isFloatRange(v)) return "FloatRange " + call(v, "getStart") + ".." + call(v, "getEndInclusive");
		return v.getClass().getName() + ": " + v;
	}

	/** Each driver (bool/enum, and numbers at their ends) changed alone and in pairs, plus random assignments. */
	private static JsonObject visibility(List<Object> settings, Field fCur, Field fDef, Field fVis) throws Exception {
		List<Integer> drivers = new ArrayList<>();
		List<Object[]> values = new ArrayList<>();
		for (int i = 0; i < settings.size(); i++) {
			Object s = settings.get(i);
			Object def = fDef.get(s);
			if (def instanceof Boolean) {
				drivers.add(i);
				values.add(new Object[] {false, true});
			} else if (def instanceof Enum<?> e) {
				drivers.add(i);
				values.add(e.getDeclaringClass().getEnumConstants());
			} else if (def instanceof Integer || def instanceof Float) {
				Object range = null;
				for (Field f : s.getClass().getDeclaredFields()) {
					if (Modifier.isStatic(f.getModifiers())) continue;
					f.setAccessible(true);
					Object x = f.get(s);
					if (isIntRange(x) || isFloatRange(x)) range = x;
				}
				if (isIntRange(range)) {
					drivers.add(i);
					values.add(new Object[] {call(range, "getFirst"), def, call(range, "getLast")});
				} else if (isFloatRange(range)) {
					drivers.add(i);
					values.add(new Object[] {call(range, "getStart"), def, call(range, "getEndInclusive")});
				}
			}
		}
		Object[] saved = new Object[settings.size()];
		for (int i = 0; i < settings.size(); i++) saved[i] = fCur.get(settings.get(i));
		JsonObject res = new JsonObject();
		JsonArray d = new JsonArray();
		for (int i : drivers) d.add(i);
		res.add("drivers", d);
		JsonArray rows = new JsonArray();
		Runnable defaults = () -> {
			try {
				for (int i : drivers) fCur.set(settings.get(i), fDef.get(settings.get(i)));
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		};
		Supplier<String> eval = () -> {
			StringBuilder sb = new StringBuilder();
			for (Object s : settings) {
				try {
					Object fn = fVis.get(s);
					java.lang.reflect.Method inv = fn.getClass().getMethod("invoke");
					inv.setAccessible(true);
					Object r = inv.invoke(fn);
					sb.append(Boolean.TRUE.equals(r) ? '1' : '0');
				} catch (Throwable t) {
					sb.append('E');
				}
			}
			return sb.toString();
		};
		defaults.run();
		rows.add(row(new int[0], new Object[0], eval.get()));
		for (int a = 0; a < drivers.size(); a++) {
			for (Object va : values.get(a)) {
				defaults.run();
				fCur.set(settings.get(drivers.get(a)), va);
				rows.add(row(new int[] {drivers.get(a)}, new Object[] {va}, eval.get()));
				for (int b = a + 1; b < drivers.size(); b++) {
					for (Object vb : values.get(b)) {
						fCur.set(settings.get(drivers.get(b)), vb);
						rows.add(row(new int[] {drivers.get(a), drivers.get(b)}, new Object[] {va, vb}, eval.get()));
					}
					fCur.set(settings.get(drivers.get(b)), fDef.get(settings.get(drivers.get(b))));
				}
			}
		}
		Random rnd = new Random(1);
		JsonArray random = new JsonArray();
		for (int n = 0; n < 200 && !drivers.isEmpty(); n++) {
			int[] idx = new int[drivers.size()];
			Object[] vals = new Object[drivers.size()];
			for (int k = 0; k < drivers.size(); k++) {
				Object[] vs = values.get(k);
				idx[k] = drivers.get(k);
				vals[k] = vs[rnd.nextInt(vs.length)];
				fCur.set(settings.get(drivers.get(k)), vals[k]);
			}
			random.add(row(idx, vals, eval.get()));
		}
		res.add("rows", rows);
		res.add("random", random);
		for (int i = 0; i < settings.size(); i++) fCur.set(settings.get(i), saved[i]);
		return res;
	}

	private static boolean isIntRange(Object v) {
		return v != null && v.getClass().getName().equals("kotlin.ranges.IntRange");
	}

	private static boolean isFloatRange(Object v) {
		if (v == null) return false;
		for (Class<?> i : v.getClass().getInterfaces()) if (i.getName().equals("kotlin.ranges.ClosedFloatingPointRange")) return true;
		return false;
	}

	private static Object call(Object o, String m) {
		try {
			java.lang.reflect.Method k = o.getClass().getMethod(m);
			k.setAccessible(true);
			return k.invoke(o);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static JsonObject row(int[] idx, Object[] vals, String vis) {
		JsonObject o = new JsonObject();
		JsonArray a = new JsonArray();
		for (int k = 0; k < idx.length; k++) {
			JsonArray p = new JsonArray();
			p.add(idx[k]);
			p.add(vals[k] instanceof Enum<?> e ? e.name() : String.valueOf(vals[k]));
			a.add(p);
		}
		o.add("set", a);
		o.addProperty("vis", vis);
		return o;
	}

	/** The original's value labels (`k9`): setting id, value, text. */
	private static JsonArray labels() {
		JsonArray a = new JsonArray();
		try {
			Class<?> k9 = Class.forName(P + "k9");
			for (Field f : k9.getDeclaredFields()) {
				if (!Modifier.isStatic(f.getModifiers())) continue;
				f.setAccessible(true);
				Object inst = f.get(null);
				if (inst == null) continue;
				for (Field g : inst.getClass().getDeclaredFields()) {
					if (Modifier.isStatic(g.getModifiers())) continue;
					g.setAccessible(true);
					if (g.get(inst) instanceof List<?> l) for (Object e : l) a.add(describeValueObject(e));
				}
				if (inst instanceof List<?> l) for (Object e : l) a.add(describeValueObject(e));
			}
		} catch (Throwable t) {
			a.add("ERR " + t);
		}
		return a;
	}
}
