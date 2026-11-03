package dev.horizon.client.module.basic

import dev.horizon.client.event.KeyboardInputEvent
import dev.horizon.client.event.TickEvent
import dev.horizon.client.module.Categories
import dev.horizon.client.module.Module
import dev.horizon.client.module.Modules
import dev.horizon.client.module.attack.Aura
import net.minecraft.world.entity.player.Input

/**
 * The original's "sprint" (`bf`), extended with [Mode.VANILLA]:
 * - [Mode.VANILLA] feeds a real sprint key press into [KeyboardInputEvent] when moving forward so `LocalPlayer.aiStep`
 *   applies all normal vanilla sprint rules (forward-only, hunger, sneak, item use, collisions, no omni FOV zoom).
 * - [Mode.OMNI] preserves the original's `TickEvent` omni-sprint behaviour whenever moving in any direction.
 */
class Sprint : Module("sprint", Categories.BASIC) {
    enum class Mode { VANILLA, OMNI }

    val mode = enum("mode", Mode.VANILLA)
    val pauseInWater = bool("pause-in-water", false)

    /**
     * Skip the next forced sprint (the original's `bf.F`): set by Aura's sprint reset. Only this module's tick clears it, so
     * with Sprint off it stays set and Aura keeps sprinting off (as in the original).
     */
    @Volatile
    var skipOnce = false

    private var skipInputTick = false

    override fun onDisable() {
        skipInputTick = false
    }

    init {
        on<TickEvent>(10) {
            val p = mc.player ?: return@on
            if (mc.level == null) return@on
            if (skipOnce) {
                skipOnce = false
                skipInputTick = true
                return@on
            }
            skipInputTick = false
            if (mode.value != Mode.OMNI) return@on
            val mv = p.input.moveVector
            if (mv.y == 0f && mv.x == 0f) return@on
            if (pauseInWater.value && p.isUnderWater) return@on
            if (p.isFallFlying) return@on
            p.isSprinting = true
        }

        on<KeyboardInputEvent>(-5) {
            if (mode.value != Mode.VANILLA) return@on
            val p = mc.player ?: return@on
            if (mc.level == null) return@on
            if (skipInputTick || skipOnce) {
                skipInputTick = false
                skipOnce = false
                return@on
            }
            if (Modules.get<Aura>().sprintHook.resetting) return@on
            if (pauseInWater.value && p.isUnderWater) return@on
            val keys = p.input.keyPresses
            if (p.input.moveVector.y <= 1.0e-5f || keys.shift()) return@on
            if (!keys.sprint()) {
                p.input.keyPresses = Input(
                    keys.forward(),
                    keys.backward(),
                    keys.left(),
                    keys.right(),
                    keys.jump(),
                    keys.shift(),
                    true
                )
            }
        }
    }
}
