package dev.horizon.client.combat

import dev.horizon.client.module.ColorMode
import dev.horizon.client.render.Theme
import java.awt.Color

/** The original's colour helpers the cosmetic modules share (`sf.q.v`, `sf.q.O`) and their colour-mode switch (`ku`). */
object Cosmetic1Util {
    /** The original's `sf.q.v(speed, offset, s, b)`: a hue cycling with wall-clock time, opaque. */
    fun rainbow(speed: Int, offset: Int, s: Float, b: Float): Int {
        val h = ((System.currentTimeMillis() / speed + offset) % 360).toInt()
        return Color.HSBtoRGB(h / 360.0f, s, b) or (255 shl 24)
    }

    /** The original's `sf.q.O(speed, offset)`: the same hue at saturation 0.5. */
    fun sky(speed: Int, offset: Int): Int {
        val h = ((System.currentTimeMillis() / speed + offset) % 360).toInt() % 360
        return Color.getHSBColor((h / 360.0).toFloat(), 0.5f, 1.0f).rgb
    }

    /** The original's colour-mode switch (`ku.N`): [custom] for CUSTOM, otherwise the theme gradient or a rainbow at [offset]. */
    fun modeColor(mode: ColorMode, custom: Int, offset: Int): Int = when (mode) {
        ColorMode.CUSTOM -> custom
        ColorMode.SYNC -> Theme.gradient(offset)
        ColorMode.SKY -> sky(18, offset)
        ColorMode.LIGHT_RGB -> rainbow(18, offset, 0.6f, 1.0f)
        ColorMode.RGB -> rainbow(18, offset, 1.0f, 1.0f)
    }
}
