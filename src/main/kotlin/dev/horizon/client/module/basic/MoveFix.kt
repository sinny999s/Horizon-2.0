package dev.horizon.client.module.basic

import dev.horizon.client.module.Categories
import dev.horizon.client.module.Module

/**
 * The original's "move-fix" (`bg`): no handlers of its own, as in the original; the rotation manager (`RotationManager`, the original's
 * `n8`) reads [enabled] and [mode] live every time a movement hook fires.
 */
class MoveFix : Module("move-fix", Categories.BASIC) {
    enum class Mode { FREE, FOCUS }

    val mode = enum("mode", Mode.FREE)
}
