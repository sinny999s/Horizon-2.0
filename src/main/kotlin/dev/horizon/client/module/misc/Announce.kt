package dev.horizon.client.module.misc

import dev.horizon.client.combat.MsTimer
import dev.horizon.client.event.PlayerUpdateEvent
import dev.horizon.client.module.Categories
import dev.horizon.client.module.Module
import kotlin.random.Random

/**
 * Not in the original: periodically sends a configured announcement message to chat or as a command at a
 * configurable interval (default 5.01s, slider range 1.0s..30.0s).
 */
class Announce : Module("announce", Categories.MISC) {
    val message = string("message", "Horizon 2.0 on top")
    val delay = float("delay", 5.01f, 1.0f..30.0f)
    val antiSpam = bool("anti-spam", false)
    val global = bool("global", false)
    val sendNow = action("send-now") {
        send()
    }

    private val timer = MsTimer()

    init {
        on<PlayerUpdateEvent> { tick() }
    }

    override fun onEnable() {
        timer.reset()
    }

    private fun tick() {
        if (mc.player == null || mc.connection == null) return
        val raw = message.value.trim()
        if (raw.isEmpty()) return

        if (timer.passed((delay.value * 1000).toLong())) {
            timer.reset()
            send()
        }
    }

    fun send() {
        val connection = mc.connection ?: return
        var msg = message.value.trim()
        if (msg.isEmpty()) return

        if (antiSpam.value) {
            msg += tag()
        }

        if (msg.startsWith("/")) {
            connection.sendCommand(msg.removeRange(0, 1))
        } else if (global.value && !msg.startsWith("!")) {
            connection.sendChat("!$msg")
        } else {
            connection.sendChat(msg)
        }
    }

    private fun tag() = " [" + (Random.nextInt(26) + 97).toChar() + Random.nextInt(10) + (Random.nextInt(26) + 97).toChar() + "]"
}
