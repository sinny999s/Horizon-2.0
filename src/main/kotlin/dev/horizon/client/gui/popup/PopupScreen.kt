package dev.horizon.client.gui.popup

import dev.horizon.client.gui.modern.ModernGui
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

/** Hosts a [Popup] (Inventory Manager's kit editor) over the world; closing it goes back to the ClickGUI. */
object PopupScreen : Screen(Component.literal("horizon-popup")) {
    /** The popup on top; set it, then open this screen. */
    var popup: Popup? = null

    private var mouseX = 0
    private var mouseY = 0

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, delta)
        this.mouseX = mouseX
        this.mouseY = mouseY
    }

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {}

    override fun isPauseScreen() = false

    /** The frame, from the client's 2D layer. */
    fun draw() {
        val p = popup
        if (p == null) {
            Minecraft.getInstance().gui.setScreen(ModernGui)
            return
        }
        p.render(mouseX, mouseY)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        popup?.mouseClicked(event.x().toInt(), event.y().toInt(), event.button())
        return false
    }

    override fun mouseScrolled(x: Double, y: Double, scrollX: Double, scrollY: Double): Boolean {
        popup?.mouseScrolled(scrollY)
        return true
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        popup?.keyPressed(event.key())
        return false
    }

    // 26.3 (SDL) only delivers typed characters while a screen has text input on; 26.2 always delivered them.
    override fun added() {
        super.added()
        Minecraft.getInstance().textInputManager().startTextInput(this)
    }

    override fun removed() {
        super.removed()
        Minecraft.getInstance().textInputManager().stopTextInput(this)
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        event.codepointAsString().firstOrNull()?.let { popup?.charTyped(it) }
        return true
    }
}
