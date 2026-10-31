package dev.horizon.client

import net.fabricmc.loader.api.FabricLoader
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.awt.geom.RoundRectangle2D
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.math.sin

/**
 * Pre-launch splash screen: displays a sleek, modernized dark card window with live loading status,
 * user greeting, and animated shimmer while Minecraft starts; disposed once the client has initialized.
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
        f.isUndecorated = true
        f.isResizable = false
        f.contentPane.add(LoadingPanel)
        f.pack()
        f.setLocationRelativeTo(null)

        // Draggable window
        var dragPoint: Point? = null
        f.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                dragPoint = e.point
            }
        })
        f.addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseDragged(e: MouseEvent) {
                val p = dragPoint ?: return
                f.setLocation(e.xOnScreen - p.x, e.yOnScreen - p.y)
            }
        })

        // Shape and background translucency
        try {
            f.background = Color(0, 0, 0, 0)
        } catch (ignored: Exception) {
        }
        try {
            f.shape = RoundRectangle2D.Double(0.0, 0.0, 460.0, 155.0, 16.0, 16.0)
        } catch (ignored: Exception) {
        }

        f.isVisible = true
        frame = f

        val t = Timer(25) {
            LoadingPanel.tick()
            LoadingPanel.repaint()
        }
        t.isRepeats = true
        t.start()
        timer = t
    }

    fun hide() {
        SwingUtilities.invokeLater {
            timer?.stop()
            frame?.dispose()
            timer = null
            frame = null
        }
    }
}

/** The modern loading window panel. */
object LoadingPanel : JPanel() {
    private fun readResolve(): Any = LoadingPanel

    private val version = FabricLoader.getInstance().getModContainer("horizon")
        .map { it.metadata.version.friendlyString }.orElse("2.0.3")

    private val userName = System.getProperty("user.name", "Player")

    private var animTicks = 0
    private var closeHovered = false

    private val loadingSteps = listOf(
        "Loading client subsystems...",
        "Initializing mixins and bytecode patches...",
        "Configuring anarchy modules & HUD...",
        "Applying performance optimizations...",
        "Starting Minecraft 26.3..."
    )

    init {
        preferredSize = Dimension(460, 155)
        isOpaque = false

        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.x in 428..450 && e.y in 8..30) {
                    PreLaunch.hide()
                }
            }
        })
        addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                val hovered = e.x in 428..450 && e.y in 8..30
                if (hovered != closeHovered) {
                    closeHovered = hovered
                    repaint()
                }
            }
        })
    }

    fun tick() {
        animTicks++
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        val w = width.toFloat()
        val h = height.toFloat()

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB)
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

        // Background dark card
        val bgGrad = GradientPaint(0f, 0f, Color(18, 16, 24), 0f, h, Color(11, 10, 15))
        g2.paint = bgGrad
        g2.fill(RoundRectangle2D.Float(1f, 1f, w - 2f, h - 2f, 16f, 16f))

        // Outer border
        g2.color = Color(42, 38, 54)
        g2.stroke = BasicStroke(1.2f)
        g2.draw(RoundRectangle2D.Float(1f, 1f, w - 2f, h - 2f, 16f, 16f))

        // Top accent line
        val topGrad = LinearGradientPaint(
            0f, 1f, w, 1f,
            floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f),
            arrayOf(
                Color(255, 42, 74, 0),
                Color(255, 42, 74, 180),
                Color(255, 70, 100, 240),
                Color(255, 42, 74, 180),
                Color(255, 42, 74, 0)
            )
        )
        g2.paint = topGrad
        g2.stroke = BasicStroke(1.5f)
        g2.drawLine(20, 2, (w - 20).toInt(), 2)

        // Horizon Brand
        g2.font = Font("Segoe UI", Font.BOLD, 24)
        g2.color = Color(245, 245, 247)
        g2.drawString("HORIZON", 24, 38)

        // Version Pill
        val vX = 145f
        val vY = 22f
        val vW = 54f
        val vH = 19f
        g2.color = Color(255, 42, 74, 35)
        g2.fill(RoundRectangle2D.Float(vX, vY, vW, vH, 9f, 9f))
        g2.color = Color(255, 42, 74, 95)
        g2.stroke = BasicStroke(1f)
        g2.draw(RoundRectangle2D.Float(vX, vY, vW, vH, 9f, 9f))

        g2.font = Font("Segoe UI", Font.BOLD, 11)
        g2.color = Color(255, 95, 120)
        g2.drawString("v$version", vX + 8f, vY + 14f)

        // Fabric 26.3 badge
        val mX = vX + vW + 8f
        val mW = 84f
        g2.color = Color(255, 255, 255, 12)
        g2.fill(RoundRectangle2D.Float(mX, vY, mW, vH, 9f, 9f))
        g2.color = Color(255, 255, 255, 22)
        g2.draw(RoundRectangle2D.Float(mX, vY, mW, vH, 9f, 9f))
        g2.font = Font("Segoe UI", Font.BOLD, 10)
        g2.color = Color(165, 163, 178)
        g2.drawString("26.3 • FABRIC", mX + 7f, vY + 13.5f)

        // Close button (x)
        g2.font = Font("Segoe UI", Font.PLAIN, 15)
        g2.color = if (closeHovered) Color(245, 245, 247) else Color(110, 108, 122)
        g2.drawString("×", (w - 24).toInt(), 26)

        // User Greeting with online dot
        val dotX = 25
        val dotY = 67
        val pulseAlpha = (0.25f + 0.35f * (sin(animTicks * 0.12).toFloat() * 0.5f + 0.5f)).coerceIn(0f, 1f)
        g2.color = Color(52, 215, 123, (pulseAlpha * 255).toInt())
        g2.fillOval(dotX - 2, dotY - 2, 10, 10)
        g2.color = Color(52, 215, 123)
        g2.fillOval(dotX, dotY, 6, 6)

        g2.font = Font("Segoe UI", Font.PLAIN, 13)
        g2.color = Color(160, 158, 172)
        g2.drawString("Welcome back, ", 40, 74)
        val wLen = g2.fontMetrics.stringWidth("Welcome back, ")
        g2.font = Font("Segoe UI", Font.BOLD, 13)
        g2.color = Color(245, 245, 247)
        g2.drawString("$userName!", 40 + wLen, 74)

        // Status text (animated rotating phases)
        val stepIdx = ((animTicks / 40) % loadingSteps.size)
        g2.font = Font("Segoe UI", Font.PLAIN, 11)
        g2.color = Color(135, 133, 148)
        g2.drawString(loadingSteps[stepIdx], 24, 104)

        // Progress bar
        val trackX = 24f
        val trackY = 114f
        val trackW = w - 48f
        val trackH = 5f

        // Track background
        g2.color = Color(24, 22, 32)
        g2.fill(RoundRectangle2D.Float(trackX, trackY, trackW, trackH, 5f, 5f))

        // Progress highlight (smooth laser shimmer)
        val barW = 120f
        val progress = (animTicks * 3.5f) % (trackW + barW * 2)
        val barX = trackX - barW + progress

        g2.clip(RoundRectangle2D.Float(trackX, trackY, trackW, trackH, 5f, 5f))
        val barGrad = LinearGradientPaint(
            barX, trackY, barX + barW, trackY,
            floatArrayOf(0f, 0.5f, 1f),
            arrayOf(Color(255, 42, 74, 30), Color(255, 55, 85, 240), Color(180, 20, 50, 30))
        )
        g2.paint = barGrad
        g2.fill(RoundRectangle2D.Float(barX, trackY, barW, trackH, 0f, 0f))
        g2.clip = null

        // Subtitle / footer
        g2.font = Font("Segoe UI", Font.PLAIN, 10)
        g2.color = Color(85, 82, 98)
        g2.drawString("Next-Generation Anarchy Client", 24, 138)
    }
}
