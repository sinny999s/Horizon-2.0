package dev.horizon.client

import dev.horizon.client.combat.Swings
import dev.horizon.client.combat.Explosions
import dev.horizon.client.combat.Inventory
import dev.horizon.client.combat.BasicUtil
import dev.horizon.client.command.Commands
import dev.horizon.client.combat.Particles3D
import dev.horizon.client.combat.Placer
import dev.horizon.client.combat.PlayerState
import dev.horizon.client.combat.Predictor
import dev.horizon.client.combat.RotationManager
import dev.horizon.client.combat.Scheduler
import dev.horizon.client.combat.TimerManager
import dev.horizon.client.config.ConfigImport
import dev.horizon.client.config.Configs
import dev.horizon.client.config.Macros
import dev.horizon.client.config.Proxies
import dev.horizon.client.gui.menu.Menus
import dev.horizon.client.hud.Hud
import dev.horizon.client.hud.ServerInfo
import dev.horizon.client.hud.WaypointRenderer
import dev.horizon.client.hud.widgets.StaffDetector
import dev.horizon.client.module.HorizonModules
import dev.horizon.client.render.Pipelines
import dev.horizon.client.render.World3D
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import org.slf4j.LoggerFactory

object HorizonClient : ClientModInitializer {
    const val ID = "horizon"
    val LOG = LoggerFactory.getLogger("HorizonClient")

    private var lastSave = System.currentTimeMillis()

    override fun onInitializeClient() {
        Pipelines.init()
        WaypointRenderer.init()
        PlayerState.init()
        Inventory.init()
        RotationManager.init()
        Swings.init()
        Placer.init()
        Predictor.init()
        Explosions.init()
        Scheduler.init()
        BasicUtil.init()
        TimerManager.init()
        World3D.init()
        Particles3D.init()
        StaffDetector.init()
        HorizonModules.register()
        Commands.init()
        AnarchyServers.init()
        Menus.init()
        ClientLifecycleEvents.CLIENT_STARTED.register {
            PreLaunch.hide()
            ConfigImport.runIfFirstStart()
            Configs.startup()
        }
        // The original saved every 60 s in a world (every 2.5 s while the Windows screen is open) and at shutdown.
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (mc.player == null) return@register
            ServerInfo.tick()
            Hud.tick()
            Proxies.tick()
            Macros.tick()
            val now = System.currentTimeMillis()
            val windowsOpen = mc.gui.screen() === dev.horizon.client.gui.modern.ModernWindows
            val interval = if (windowsOpen) 2500L else 60000L
            if (now - lastSave >= interval) {
                lastSave = now
                Configs.autosave()
            }
        }
        ClientLifecycleEvents.CLIENT_STOPPING.register { Configs.saveAll() }
        LOG.info("HorizonClient loaded")
    }
}
