package dev.horizon.client.module.basic

import dev.horizon.client.event.TickEvent
import dev.horizon.client.module.Categories
import dev.horizon.client.module.Module

/** The original's "sprint" (`bf`): sprints whenever moving (not while gliding, nor under water with pause-in-water). */
class Sprint : Module("sprint", Categories.BASIC) {
    val pauseInWater = bool("pause-in-water", false)

    /**
     * Skip the next forced sprint (the original's `bf.F`): set by Aura's sprint reset. Only this module's tick clears it, so
     * with Sprint off it stays set and Aura keeps sprinting off (as in the original).
     */
    @Volatile
    var skipOnce = false

    init {
        on<TickEvent>(10) {
            val p = mc.player ?: return@on
            if (mc.level == null) return@on
            if (skipOnce) {
                skipOnce = false
                return@on
            }
            val mv = p.input.moveVector
            if (mv.y == 0f && mv.x == 0f) return@on
            if (pauseInWater.value && p.isUnderWater) return@on
            if (p.isFallFlying) return@on
            p.isSprinting = true
        }
    }
}
