package dev.horizon.client.gui

import dev.horizon.client.HorizonClient
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent

/**
 * The original's GUI sounds (assets/horizon/sounds). The original played them as non-relative sounds at the world origin, so they
 * faded with the distance from 0,0,0; here they play as UI sounds at volume 1, how they sound next to the origin.
 */
enum class Sounds(file: String) {
    ENABLE("enable"),
    DISABLE("disable"),
    SETTINGS_OPEN("settings-open"),
    SETTINGS_CLOSE("settings-close"),
    NO_SETTINGS("no-settings"),
    SWITCH_LEFT("switch-left"),
    SWITCH_RIGHT("switch-right");

    private val event = SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(HorizonClient.ID, file))

    fun play() {
        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(event, 1f, 1f))
    }
}
