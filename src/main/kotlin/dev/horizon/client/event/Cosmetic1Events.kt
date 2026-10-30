package dev.horizon.client.event


/** The original's RenderHandEvent (`GameRenderer.renderItemInHand` HEAD): cancelling skips vanilla's first-person hands. */
class RenderHandEvent : Event()

/** The original's PostRenderHandEvent: right after vanilla drew (or skipped) the first-person hands. */
class PostRenderHandEvent : Event()

/** The original's CrystalRenderEvent (`EndCrystalRenderer.submit` HEAD): cancelling hides the crystal. */
class CrystalRenderEvent : Event()

/** The original's SetCrystalScaleEvent (before the crystal's first `scale`): cancelling scales the crystal by [scale] first. */
class SetCrystalScaleEvent(val age: Float) : Event() {
    var scale = 1f
}

/** The original's CrystalYOffsetEvent (`EndCrystalRenderer.getY` HEAD): cancelling makes [offset] the crystal's bob height. */
class CrystalYOffsetEvent(val age: Float) : Event() {
    var offset = 0f
}

/** The original's CrystalCreateEvent (end crystal constructor TAIL, client and server): cancelling starts its age at 0. */
class CrystalCreateEvent : Event()
