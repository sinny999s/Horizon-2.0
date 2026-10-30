package dev.horizon.client.module.misc

import dev.horizon.client.event.TickEvent
import dev.horizon.client.module.Categories
import dev.horizon.client.module.Module
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.client.gui.screens.DisconnectedScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.resolver.ServerAddress
import net.minecraft.network.chat.Component
import kotlin.math.ceil

/**
 * Not in the original: rejoins the server after you're kicked or lose connection, [delay] seconds after the disconnected
 * screen comes up, and keeps trying while the server is down or refusing. That screen also gets a Reconnect button
 * (DisconnectedScreenMixin), with the module on or off, which shows the countdown; leaving the screen stops it. Auto
 * Leave's own disconnects (reasons starting "[the original]") are never rejoined.
 */
class AutoReconnect : Module("auto-reconnect", Categories.MISC) {
    val delay = int("delay", 5, 1..120)

    init {
        on<TickEvent> {
            if (mc.gui.screen() !is DisconnectedScreen || autoLeft || last == null) {
                deadline = 0L
                return@on
            }
            val now = System.currentTimeMillis()
            if (deadline == 0L) deadline = now + delay.value * 1000L
            if (now >= deadline) {
                deadline = 0L
                reconnect()
            } else {
                button?.message = label()
            }
        }
    }

    override fun onDisable() {
        deadline = 0L
        button?.message = label()
    }

    companion object {
        /** The server you last joined or tried to (ConnectScreenMixin). */
        private var last: ServerData? = null

        /** The disconnected screen's Reconnect button, and the screen that one goes back to. */
        private var button: Button? = null
        private var parent: Screen? = null

        /** The disconnected screen showing is Auto Leave's. */
        private var autoLeft = false

        /** When the countdown ends (0: not counting). */
        private var deadline = 0L

        @JvmStatic
        fun connecting(server: ServerData?) {
            if (server != null) last = server
        }

        /** The button DisconnectedScreenMixin adds under the back button: reconnects now. */
        @JvmStatic
        fun button(parent: Screen, reason: Component): Button {
            this.parent = parent
            autoLeft = reason.string.startsWith("[Horizon]")
            deadline = 0L
            return Button.builder(label()) { reconnect() }.width(200).build().also {
                it.active = last != null
                button = it
            }
        }

        private fun label(): Component {
            if (deadline == 0L) return Component.literal("Reconnect")
            val left = ceil((deadline - System.currentTimeMillis()) / 1000.0).toInt().coerceAtLeast(0)
            return Component.literal("Reconnect in ${left}s")
        }

        private fun reconnect() {
            val server = last ?: return
            ConnectScreen.startConnecting(parent ?: JoinMultiplayerScreen(TitleScreen()), Minecraft.getInstance(),
                ServerAddress.parseString(server.ip), server, false, null)
        }
    }
}
