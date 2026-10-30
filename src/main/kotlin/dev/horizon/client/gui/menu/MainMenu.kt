package dev.horizon.client.gui.menu

import com.mojang.blaze3d.platform.cursor.CursorTypes
import dev.horizon.client.HorizonClient
import dev.horizon.client.event.EventBus
import dev.horizon.client.event.TitleScreenInitEvent
import dev.horizon.client.gui.Cursor
import dev.horizon.client.gui.Keys
import dev.horizon.client.render.Canvas
import dev.horizon.client.render.Colors
import dev.horizon.client.render.Easing
import dev.horizon.client.render.Fonts
import dev.horizon.client.render.Icon
import dev.horizon.client.render.Theme
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.SharedConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.CreditsAndAttributionScreen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen
import net.minecraft.client.gui.screens.options.OptionsScreen
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import kotlin.math.floor

/**
 * Not in the original: the client's own title screen ([Menus]). It extends vanilla's, so vanilla's checks for the title
 * screen still hold, but draws none of it: the animated backdrop, the original's logo in the theme colours, a greeting, four
 * buttons (vanilla's actions), and the versions and Mojang's copyright line (opening the credits, as vanilla's does) in
 * the bottom corners. Everything fades and slides in the first time a menu is shown, not again when coming back to it
 * from the screens it opens.
 */
class MainMenu : TitleScreen(), MenuScreen {
    private val mc get() = Minecraft.getInstance()

    private val buttons = listOf(
        MenuButton("Singleplayer") { mc.gui.setScreen(SelectWorldScreen(this)) },
        MenuButton("Multiplayer") { mc.gui.setScreen(if (mc.options.skipMultiplayerWarning) JoinMultiplayerScreen(this) else SafetyScreen(this)) },
        MenuButton("Options") { mc.gui.setScreen(OptionsScreen(this, mc.options)) },
        MenuButton("Quit", Menus::back) { mc.stop() },
    )
    private val copyright = Component.translatable("title.credits").string
    private val versions = "Horizon $VERSION  -  Minecraft ${SharedConstants.getCurrentVersion().name()}"
    private var copyrightHovered = false
    private var openedAt = 0L
    private var visits = 0

    // Vanilla's buttons, splash, logo and Realms notifications are all left out.
    override fun init() {
        TitleScreenInitEvent.cancelled = false
        EventBus.post(TitleScreenInitEvent)
    }

    // The first time it's shown everything comes in one after another; coming back to it, quicker and nearly together.
    override fun added() {
        openedAt = System.currentTimeMillis()
        visits++
    }

    override fun removed() {}

    override fun tick() {}

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        extractPanorama(graphics, delta)
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {}

    /** How far element [i] is into its entrance (0..1), each starting a little after the one before. */
    private fun entrance(i: Int): Float {
        val first = visits <= 1
        val t = (System.currentTimeMillis() - openedAt - i * (if (first) 60L else 20L)) / (if (first) 700.0 else 350.0)
        return Easing.OUT_EXPO.apply(t.coerceIn(0.0, 1.0)).toFloat()
    }

    override fun draw(mouseX: Float, mouseY: Float) {
        // At startup the loading screen fades out over this one; the entrance waits for it.
        if (mc.gui.overlay() != null) openedAt = System.currentTimeMillis()
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f

        val bw = 150f
        val bh = 20f
        val gap = 5f
        val block = LOGO_H + 24f + bh * 3 + gap * 2
        var y = floor(((h - block) / 2f).coerceAtLeast(8f))

        var k = entrance(0)
        Canvas.alpha = k
        // Whole pixels, so the logo's raster lands 1:1 on the screen.
        logo.draw(floor(cx - LOGO_W / 2f), y + floor((1f - k) * 8f), LOGO_W, LOGO_H, Theme.gradient(0), Theme.gradient(180), 1f)
        y += LOGO_H + 6f
        k = entrance(1)
        Canvas.alpha = k
        Fonts.medium75.drawMiddleCentered("Welcome back, ${mc.user.name}", cx, y + 3f + (1f - k) * 8f, Colors.withAlpha(-1, 0.55f))
        y += 18f

        for ((i, b) in buttons.withIndex()) {
            k = entrance(2 + minOf(i, 2))
            Canvas.alpha = k
            val slide = (1f - k) * 8f
            when (i) {
                0, 1 -> b.draw(cx - bw / 2f, y + slide, bw, bh, mouseX, mouseY)
                2 -> b.draw(cx - bw / 2f, y + slide, (bw - gap) / 2f, bh, mouseX, mouseY)
                else -> b.draw(cx + gap / 2f, y + slide, (bw - gap) / 2f, bh, mouseX, mouseY)
            }
            if (i < 2) y += bh + gap
        }

        k = entrance(5)
        Canvas.alpha = k
        val font = Fonts.medium75
        font.draw(versions, 6f, h - 11f, Colors.withAlpha(-1, 0.4f))
        val cw = font.width(copyright)
        copyrightHovered = mouseX >= w - cw - 6f && mouseX <= w - 6f && mouseY >= h - 13f && mouseY <= h - 2f
        if (copyrightHovered) Cursor.request(CursorTypes.POINTING_HAND)
        font.draw(copyright, w - cw - 6f, h - 11f, Colors.withAlpha(-1, if (copyrightHovered) 0.85f else 0.4f))
        Canvas.alpha = 1f
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (event.button() != Keys.MOUSE_LEFT) return false
        for (b in buttons) if (b.click(event)) return true
        if (copyrightHovered) {
            Menus.open()
            mc.gui.setScreen(CreditsAndAttributionScreen(this))
            return true
        }
        return false
    }

    private companion object {
        // logo.svg is 232 x 46; whole numbers, or Icon rasterises it at 1x (blurry, then pixelated).
        const val LOGO_H = 26f
        const val LOGO_W = 131f
        val logo by lazy { Icon("logo", LOGO_W, LOGO_H) }
        val VERSION: String = FabricLoader.getInstance().getModContainer(HorizonClient.ID).map { it.metadata.version.friendlyString }.orElse("")
    }
}
