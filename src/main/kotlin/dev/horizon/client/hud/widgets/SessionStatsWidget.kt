package dev.horizon.client.hud.widgets

import dev.horizon.client.event.EventBus
import dev.horizon.client.event.PlayerDeathEvent
import dev.horizon.client.event.ReceivePacketEvent
import dev.horizon.client.gui.modern.Tok
import dev.horizon.client.hud.HudWidget
import dev.horizon.client.render.Animation
import dev.horizon.client.render.Easing
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket
import kotlin.math.max

/**
 * Not in the original: Session Stats HUD widget showing session playtime, kills, deaths, K/D ratio, and totem pops.
 */
object SessionStatsWidget : HudWidget("session-stats-widget") {
    val showPlaytime = bool("playtime", true)
    val showKills = bool("kills", true)
    val showDeaths = bool("deaths", true)
    val showKd = bool("kd", true)
    val showPops = bool("pops", true)

    var kills = 0
    var deaths = 0
    var pops = 0
    val sessionStartTime = System.currentTimeMillis()

    private var wasDead = false
    private val headerAnim = Animation(Easing.OUT_BACK, 250)

    init {
        EventBus.on<PlayerDeathEvent>(this) { e ->
            val p = mc.player ?: return@on
            if (e.player !== p && e.player.distanceTo(p) < 40f) {
                kills++
            }
        }
        EventBus.on<ReceivePacketEvent>(this) { e ->
            val pk = e.packet
            if (pk is ClientboundEntityEventPacket && pk.eventId.toInt() == 35) {
                val ent = pk.getEntity(mc.level ?: return@on)
                if (ent === mc.player) {
                    pops++
                }
            }
        }
    }

    override fun tick() {
        val p = mc.player ?: return
        val dead = p.isDeadOrDying || p.health <= 0f
        if (dead && !wasDead) {
            deaths++
        }
        wasDead = dead
    }

    override fun render() {
        val title = "Session Stats"
        val alignRight = left - screenW / 2f > 0
        headerAnim.update(if (enabled || editorMode) 1f else 0f)
        header(title, headerAnim.value, alignRight)

        val rows = ArrayList<Pair<String, String>>()

        if (showPlaytime.value) {
            val uptime = System.currentTimeMillis() - sessionStartTime
            val hours = uptime / 3600000
            val minutes = (uptime % 3600000) / 60000
            val seconds = (uptime % 60000) / 1000
            val timeStr = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m ${seconds}s"
            rows += "Playtime" to timeStr
        }

        if (showKills.value) {
            rows += "Kills" to kills.toString()
        }

        if (showDeaths.value) {
            rows += "Deaths" to deaths.toString()
        }

        if (showKd.value) {
            val kd = if (deaths == 0) kills.toFloat() else kills.toFloat() / deaths
            rows += "K/D" to String.format("%.2f", kd)
        }

        if (showPops.value) {
            rows += "Totems" to pops.toString()
        }

        var width = headerWidth(title)
        for ((label, value) in rows) {
            val rowW = cellWidth(label) + 2f + cellWidth(value)
            width = max(width, rowW)
        }

        var y = 17f
        for ((label, value) in rows) {
            val lw = cellWidth(label)
            val vw = cellWidth(value)
            val totalW = lw + 2f + vw
            val rx = if (alignRight) left + width - totalW else left

            cell(label, rx, top + y, color = Tok.T3, alpha = 1f)
            cell(value, rx + lw + 2f, top + y, color = Tok.T1, alpha = 1f)
            y += 14f
        }

        size(width, y - 1)
    }
}
