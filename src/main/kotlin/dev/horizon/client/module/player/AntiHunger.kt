package dev.horizon.client.module.player

import dev.horizon.client.event.SendPacketEvent
import dev.horizon.client.mixin.player1.ServerboundMovePlayerPacketAccessor
import dev.horizon.client.module.Categories
import dev.horizon.client.module.Module
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket

/** The original's "anti-hunger" (`a3`): every movement packet says off-ground, and START_SPRINTING is never sent. */
class AntiHunger : Module("anti-hunger", Categories.PLAYER) {
    val cancelGround = bool("cancel-ground", true)
    val cancelSprint = bool("cancel-sprint", true)

    init {
        // `a3.B`
        on<SendPacketEvent> { e ->
            val p = e.packet
            if (p is ServerboundMovePlayerPacket && cancelGround.value) (p as ServerboundMovePlayerPacketAccessor).horizon_setOnGround(false)
            if (p !is ServerboundPlayerCommandPacket || !cancelSprint.value) return@on
            if (p.action != ServerboundPlayerCommandPacket.Action.START_SPRINTING) return@on
            e.cancel()
        }
    }
}
