package dev.horizon.client

import net.fabricmc.loader.api.FabricLoader
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridLayout
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer

/**
 * The original's `PreLaunch`: on Windows, a small window with the ASCII banner shows while the game starts; it is hidden
 * once the client has initialised ([hide]).
 */
object PreLaunch : PreLaunchEntrypoint {
    private var frame: JFrame? = null
    private var timer: Timer? = null

    override fun onPreLaunch() {
        if (!System.getProperty("os.name").startsWith("Windows")) return
        SwingUtilities.invokeLater { show() }
    }

    private fun show() {
        val f = JFrame("Horizon")
        f.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
        f.contentPane.add(LoadingPanel)
        f.pack()
        f.isVisible = true
        f.isResizable = false
        f.layout = GridLayout()
        f.setLocationRelativeTo(null)
        frame = f
        val t = Timer(100) { LoadingPanel.repaint() }
        t.isRepeats = true
        t.start()
        timer = t
    }

    /**
     * `PreLaunch.c`. The original only hid the window, which kept the AWT thread alive after the game closed (the shutdown
     * watchdog then wrote a crash report on every exit); here the timer is stopped and the window disposed.
     */
    fun hide() {
        SwingUtilities.invokeLater {
            timer?.stop()
            frame?.dispose()
            timer = null
            frame = null
        }
    }
}

/** The loading window: the banner, version and greeting. */
object LoadingPanel : JPanel() {
    private fun readResolve(): Any = LoadingPanel

    private val version = FabricLoader.getInstance().getModContainer("horizon").orElseThrow().metadata.version

    init {
        preferredSize = Dimension(510, 140)
        background = Color(10, 10, 10)
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.color = Color.GREEN
        g2.font = Font("Monospaced", Font.BOLD, 12)
        for ((i, line) in BANNER.withIndex()) g2.drawString(line, 10, 20 + i * 10)
        g2.drawString("v$version", 10, 110)
        g2.drawString("Welcome back, " + System.getProperty("user.name") + "!", 10, 125)
    }

    private val BANNER = listOf(
        " /\$\$   /\$\$                     /\$\$                              ",
        "| \$\$  | \$\$                    |__/                              ",
        "| \$\$  | \$\$  /\$\$\$\$\$\$   /\$\$\$\$\$\$  /\$\$ /\$\$\$\$\$\$\$\$  /\$\$\$\$\$\$  /\$\$\$\$\$\$\$ ",
        "| \$\$\$\$\$\$\$\$ /\$\$__  \$\$ /\$\$__  \$\$| \$\$|____ /\$\$/ /\$\$__  \$\$| \$\$__  \$\$",
        "| \$\$__  \$\$| \$\$  \\ \$\$| \$\$  \\__/| \$\$   /\$\$\$\$/ | \$\$  \\ \$\$| \$\$  \\ \$\$",
        "| \$\$  | \$\$| \$\$  | \$\$| \$\$      | \$\$  /\$\$__/  | \$\$  | \$\$| \$\$  | \$\$",
        "| \$\$  | \$\$|  \$\$\$\$\$\$/| \$\$      | \$\$ /\$\$\$\$\$\$\$\$|  \$\$\$\$\$\$/| \$\$  | \$\$",
        "|__/  |__/ \\______/ |__/      |__/|________/ \\______/ |__/  |__/",
    )
}
