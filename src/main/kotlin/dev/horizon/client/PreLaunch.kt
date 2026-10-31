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
 * Pre-launch splash: a dark rounded card with a minimal horizon mark, version badges, a greeting and a progress bar that follows
 * the client's real start-up stages ([stage]). It fades in when the game starts loading and fades out once the client
 * has initialised ([hide]).
 */
object PreLaunch : PreLaunchEntrypoint {
    private var frame: JFrame? = null
    private var timer: Timer? = null

    override fun onPreLaunch() {
        if (!System.getProperty("os.name").startsWith("Windows")) return
        SwingUtilities.invokeLater { show() }
    }

    /** Reports what the client is doing now and how far along start-up is (0..1); safe from any thread. */
    fun stage(text: String, progress: Float) = LoadingPanel.setStage(text, progress)

    private fun show() {
        val f = JFrame("Horizon")
        f.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
        f.isUndecorated = true
        f.isResizable = false
        f.contentPane.add(LoadingPanel)
        f.pack()
        f.setLocationRelativeTo(null)

        // drag the card anywhere
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

        try { f.background = Color(0, 0, 0, 0) } catch (ignored: Exception) {}
        try { f.shape = RoundRectangle2D.Double(0.0, 0.0, LoadingPanel.W.toDouble(), LoadingPanel.H.toDouble(), 18.0, 18.0) } catch (ignored: Exception) {}
        try { f.opacity = 0f } catch (ignored: Exception) {}

        f.isVisible = true
        frame = f

        val t = Timer(16) {
            LoadingPanel.tick()
            try { f.opacity = LoadingPanel.windowOpacity() } catch (ignored: Exception) {}
            LoadingPanel.repaint()
            if (LoadingPanel.finished()) dispose()
        }
        t.isRepeats = true
        t.start()
        timer = t
    }

    private fun dispose() {
        timer?.stop()
        frame?.dispose()
        timer = null
        frame = null
    }

    /** Starts the fade-out; the window is disposed when it is done (the AWT thread must not outlive the game). */
    fun hide() {
        SwingUtilities.invokeLater {
            if (frame == null) return@invokeLater
            LoadingPanel.startClosing()
        }
    }
}

/** The splash card. */
object LoadingPanel : JPanel() {
    const val W = 460
    const val H = 170

    private fun readResolve(): Any = LoadingPanel

    private val version = FabricLoader.getInstance().getModContainer("horizon")
        .map { it.metadata.version.friendlyString }.orElse("2.0.3")
    private val mcVersion = FabricLoader.getInstance().getModContainer("minecraft")
        .map { it.metadata.version.friendlyString }.orElse("26.3")
    private val userName = System.getProperty("user.name", "Player")

    private var ticks = 0
    private var closing = -1
    private var closeHovered = false

    @Volatile private var status = "Starting up"
    @Volatile private var target = 0.04f
    private var shown = 0f

    init {
        preferredSize = Dimension(W, H)
        isOpaque = false
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (inClose(e.x, e.y)) PreLaunch.hide()
            }
        })
        addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                val h = inClose(e.x, e.y)
                if (h != closeHovered) {
                    closeHovered = h
                    repaint()
                }
            }
        })
    }

    private fun inClose(x: Int, y: Int) = x in (W - 36)..(W - 12) && y in 10..34

    fun setStage(text: String, progress: Float) {
        status = text
        target = progress.coerceIn(0f, 1f)
    }

    fun tick() {
        ticks++
        shown += (target - shown) * 0.12f
        if (closing >= 0) closing++
    }

    fun startClosing() {
        if (closing < 0) closing = 0
    }

    fun windowOpacity(): Float {
        val fadeIn = (ticks / 14f).coerceIn(0f, 1f)
        val fadeOut = if (closing >= 0) 1f - (closing / 14f) else 1f
        return (fadeIn * fadeOut).coerceIn(0f, 1f)
    }

    fun finished() = closing >= 14

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        val w = width.toFloat()
        val h = height.toFloat()
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)

        val card = RoundRectangle2D.Float(1f, 1f, w - 2f, h - 2f, 18f, 18f)

        // card
        g2.paint = GradientPaint(0f, 0f, Color(20, 18, 27), 0f, h, Color(11, 10, 15))
        g2.fill(card)

        // soft accent glow in the top-left corner, clipped to the card
        val oldClip = g2.clip
        g2.clip = card
        g2.paint = RadialGradientPaint(
            Point2D(70f, 10f), 190f, floatArrayOf(0f, 1f),
            arrayOf(Color(255, 42, 74, 22), Color(255, 42, 74, 0)),
        )
        g2.fillRect(0, 0, W, H)
        g2.clip = oldClip

        // border and the accent edge on top
        g2.color = Color(46, 42, 58)
        g2.stroke = BasicStroke(1.1f)
        g2.draw(card)
        g2.paint = LinearGradientPaint(
            0f, 0f, w, 0f, floatArrayOf(0f, 0.3f, 0.5f, 0.7f, 1f),
            arrayOf(Color(255, 42, 74, 0), Color(255, 42, 74, 170), Color(255, 80, 108, 245), Color(255, 42, 74, 170), Color(255, 42, 74, 0)),
        )
        g2.stroke = BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g2.drawLine(24, 2, (w - 24).toInt(), 2)

        // logo mark: a thin sun arc resting on a horizon line, no tile
        val hy = 41f
        g2.stroke = BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g2.color = Color(255, 64, 92)
        g2.draw(java.awt.geom.Arc2D.Float(29f, hy - 8f, 16f, 16f, 0f, 180f, java.awt.geom.Arc2D.OPEN))
        g2.paint = LinearGradientPaint(24f, 0f, 50f, 0f, floatArrayOf(0f, 0.5f, 1f),
            arrayOf(Color(255, 64, 92, 60), Color(255, 64, 92), Color(255, 64, 92, 60)))
        g2.drawLine(24, hy.toInt(), 50, hy.toInt())

        // title and badges
        g2.font = Font("Segoe UI", Font.BOLD, 22)
        g2.color = Color(245, 245, 247)
        g2.drawString("Horizon", 60, 42)
        val titleW = g2.fontMetrics.stringWidth("Horizon")
        var bx = 60f + titleW + 10f
        bx += pill(g2, bx, 26f, "v$version", Color(255, 95, 120), Color(255, 42, 74, 34), Color(255, 42, 74, 100)) + 6f
        pill(g2, bx, 26f, "$mcVersion  FABRIC", Color(165, 163, 178), Color(255, 255, 255, 12), Color(255, 255, 255, 24))

        // greeting with a pulsing online dot
        val pulse = 0.5f + 0.5f * sin(ticks * 0.08).toFloat()
        g2.color = Color(52, 215, 123, (50 + 70 * pulse).toInt())
        g2.fillOval(59, 55, 11, 11)
        g2.color = Color(52, 215, 123)
        g2.fillOval(61, 57, 7, 7)
        g2.font = Font("Segoe UI", Font.PLAIN, 13)
        g2.color = Color(160, 158, 172)
        g2.drawString("Welcome back,", 76, 66)
        val wl = g2.fontMetrics.stringWidth("Welcome back, ")
        g2.font = Font("Segoe UI", Font.BOLD, 13)
        g2.color = Color(245, 245, 247)
        g2.drawString("$userName!", 76 + wl, 66)

        // close button
        g2.color = if (closeHovered) Color(255, 255, 255, 22) else Color(255, 255, 255, 0)
        g2.fillOval(W - 36, 10, 24, 24)
        g2.color = if (closeHovered) Color(245, 245, 247) else Color(110, 108, 122)
        g2.stroke = BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g2.drawLine(W - 28, 18, W - 20, 26)
        g2.drawLine(W - 20, 18, W - 28, 26)

        // status line and percent
        val pct = (shown * 100).toInt().coerceIn(0, 100)
        g2.font = Font("Segoe UI", Font.PLAIN, 11)
        g2.color = Color(150, 148, 163)
        g2.drawString(status + "...", 24, 108)
        g2.font = Font("Segoe UI", Font.BOLD, 11)
        g2.color = Color(245, 245, 247)
        val ps = "$pct%"
        g2.drawString(ps, w.toInt() - 24 - g2.fontMetrics.stringWidth(ps), 108)

        // progress bar: real progress with a moving shimmer on the filled part
        val tx = 24f
        val ty = 118f
        val tw = w - 48f
        val th = 6f
        g2.color = Color(26, 24, 35)
        g2.fill(RoundRectangle2D.Float(tx, ty, tw, th, 6f, 6f))
        val fw = (tw * shown).coerceAtLeast(th)
        val fill = RoundRectangle2D.Float(tx, ty, fw, th, 6f, 6f)
        g2.paint = GradientPaint(tx, 0f, Color(153, 20, 46), tx + fw, 0f, Color(255, 64, 92))
        g2.fill(fill)
        val clip2 = g2.clip
        g2.clip = fill
        val sx = tx - 70f + ((ticks * 3.2f) % (fw + 140f))
        g2.paint = LinearGradientPaint(sx, 0f, sx + 70f, 0f, floatArrayOf(0f, 0.5f, 1f),
            arrayOf(Color(255, 255, 255, 0), Color(255, 255, 255, 120), Color(255, 255, 255, 0)))
        g2.fillRect(tx.toInt(), ty.toInt(), fw.toInt() + 1, th.toInt())
        g2.clip = clip2

        // footer
        g2.font = Font("Segoe UI", Font.PLAIN, 10)
        g2.color = Color(92, 89, 106)
        g2.drawString("Anarchy client for Minecraft $mcVersion", 24, 152)
        val right = "Fabric Loader " + FabricLoader.getInstance().getModContainer("fabricloader")
            .map { it.metadata.version.friendlyString }.orElse("")
        g2.drawString(right, w.toInt() - 24 - g2.fontMetrics.stringWidth(right), 152)
    }

    /** A rounded badge; returns its width. */
    private fun pill(g2: Graphics2D, x: Float, y: Float, text: String, fg: Color, bg: Color, line: Color): Float {
        g2.font = Font("Segoe UI", Font.BOLD, 10)
        val w = g2.fontMetrics.stringWidth(text) + 16f
        val shape = RoundRectangle2D.Float(x, y, w, 18f, 9f, 9f)
        g2.color = bg
        g2.fill(shape)
        g2.color = line
        g2.stroke = BasicStroke(1f)
        g2.draw(shape)
        g2.color = fg
        g2.drawString(text, x + 8f, y + 12.5f)
        return w
    }
}

/** java.awt.geom.Point2D.Float shorthand for the gradient centres. */
@Suppress("FunctionName")
private fun Point2D(x: Float, y: Float) = java.awt.geom.Point2D.Float(x, y)
