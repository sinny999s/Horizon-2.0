package dev.horizon.client.render

/** The original's font registry (`s6`): each getter is one fixed font. Sizes are GUI units. */
object Fonts {
    /** `s6.i()`: help text, slider widgets. */
    val medium75 by lazy { TextRenderer("sf_medium", 7.5f) }

    /** `s6.x()`: most setting widgets. */
    val medium8 by lazy { TextRenderer("sf_medium", 8f) }

    /** `s6.v()`: module names, tabs. */
    val medium95 by lazy { TextRenderer("sf_medium", 9.5f) }

    /** `s6.w()`: small labels in widgets. */
    val medium65 by lazy { TextRenderer("sf_medium", 6.5f) }

    /** `s6.j()`. */
    val bold12 by lazy { TextRenderer("sf_bold", 12f) }

    /** `s6.n()`. */
    val bold8 by lazy { TextRenderer("sf_bold", 8f) }

    /** `s6.u()`. */
    val bold20 by lazy { TextRenderer("sf_bold", 20f) }

    /** `s6.T()`. */
    val bold25 by lazy { TextRenderer("sf_bold", 25f) }

    /** `s6.m()`: panel titles. */
    val semibold12 by lazy { TextRenderer("sf_semibold", 12f) }

    /** `s6.F()`: "Toggle"/"Hold" in the bind popup. */
    val regular75 by lazy { TextRenderer("sf_regular", 7.5f) }

    /** Not in the original: the Hunt Map's top bar. Semibold here looks like a browser's medium (the mockup's) at this size. */
    val semibold8 by lazy { TextRenderer("sf_semibold", 8f) }

    /** Not in the original: the Hunt Map's group labels. */
    val semibold65 by lazy { TextRenderer("sf_semibold", 6.5f) }
}
