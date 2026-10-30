package shot;

import com.sun.management.ThreadMXBean;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * "perf <label>" ... "perfoff": frame times (from FrameTimeMixin), garbage collection and the render thread's allocation
 * between the two, and a sampling profile of the render thread: for each method, how many samples had it anywhere on the
 * stack. Appends a block per window to perf.txt in the script's output folder, for comparing windows in one run.
 */
final class PerfLog {
	private static final int SAMPLE_MS = 5;
	private static volatile boolean on;
	private static long[] frames = new long[1 << 16];
	private static int count;
	private static long last;
	private static String label;
	private static Thread render;
	private static Thread sampler;
	private static final Map<String, int[]> inclusive = new HashMap<>();
	private static int samples;
	private static long startNs, gcCount, gcMs, allocated;

	static void frame() {
		if (!on) return;
		long now = System.nanoTime();
		if (last != 0 && count < frames.length) frames[count++] = now - last;
		last = now;
	}

	private static long[] gc() {
		long c = 0, t = 0;
		for (GarbageCollectorMXBean b : ManagementFactory.getGarbageCollectorMXBeans()) {
			c += b.getCollectionCount();
			t += b.getCollectionTime();
		}
		return new long[] {c, t};
	}

	private static long alloc() {
		return ((ThreadMXBean) ManagementFactory.getThreadMXBean()).getThreadAllocatedBytes(render.threadId());
	}

	static void start(String name) {
		label = name;
		render = Thread.currentThread();
		count = 0;
		last = 0;
		samples = 0;
		inclusive.clear();
		long[] g = gc();
		gcCount = g[0];
		gcMs = g[1];
		allocated = alloc();
		startNs = System.nanoTime();
		on = true;
		sampler = new Thread(() -> {
			Set<String> seen = new HashSet<>();
			while (on) {
				StackTraceElement[] st = render.getStackTrace();
				seen.clear();
				for (StackTraceElement e : st) {
					String k = e.getClassName() + "." + e.getMethodName();
					if (seen.add(k)) inclusive.computeIfAbsent(k, x -> new int[1])[0]++;
				}
				samples++;
				try {
					Thread.sleep(SAMPLE_MS);
				} catch (InterruptedException x) {
					return;
				}
			}
		}, "shotbot perf");
		sampler.setDaemon(true);
		sampler.start();
	}

	static void stop(Path file) throws IOException, InterruptedException {
		on = false;
		sampler.join();
		double secs = (System.nanoTime() - startNs) / 1e9;
		long[] t = Arrays.copyOf(frames, count);
		Arrays.sort(t);
		long[] g = gc();
		StringBuilder b = new StringBuilder();
		b.append(String.format("=== %s: %.1fs, %d frames, %.0f fps, median %.2f ms, p99 %.2f ms (1%% low %.0f fps), worst %.1f ms; GC %d times %d ms; render thread allocates %.0f MB/s%n",
			label, secs, count, count / secs, ms(t, 0.5), ms(t, 0.99), 1000 / Math.max(ms(t, 0.99), 0.01), ms(t, 1), g[0] - gcCount, g[1] - gcMs, (alloc() - allocated) / 1e6 / secs));
		List<Map.Entry<String, int[]>> all = new ArrayList<>(inclusive.entrySet());
		all.sort(Comparator.comparingInt((Map.Entry<String, int[]> e) -> -e.getValue()[0]));
		int shown = 0;
		for (Map.Entry<String, int[]> e : all) {
			if (!e.getKey().startsWith("dev.horizon.client") || shown++ >= 28) continue;
			b.append(String.format("  %5.1f%% %s%n", 100.0 * e.getValue()[0] / samples, e.getKey()));
		}
		b.append("  -- anything:\n");
		shown = 0;
		for (Map.Entry<String, int[]> e : all) {
			if (shown++ >= 34) break;
			b.append(String.format("  %5.1f%% %s%n", 100.0 * e.getValue()[0] / samples, e.getKey()));
		}
		Files.createDirectories(file.getParent());
		Files.writeString(file, b.toString(), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
	}

	private static double ms(long[] sorted, double q) {
		if (sorted.length == 0) return 0;
		return sorted[Math.min(sorted.length - 1, (int) (q * sorted.length))] / 1e6;
	}
}
