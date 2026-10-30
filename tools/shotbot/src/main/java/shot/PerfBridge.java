package shot;

/** Public door to the package-private PerfLog for the mixin, which lives in another package. */
public final class PerfBridge {
	private PerfBridge() {
	}

	public static void frame() {
		PerfLog.frame();
	}
}
