package dev.horizon.client.event

/**
 * `PlayerTabOverlay.getPlayerInfos`, on its `limit(80)`; one shared instance. Cancelling lifts the limit to 1000
 * (the original's PlayerListSizeEvent).
 */
object PlayerListSizeEvent : Event()

/** `TitleScreen.init` RETURN; one shared instance (the original's PostTitleScreenInitEvent). */
object TitleScreenInitEvent : Event()
