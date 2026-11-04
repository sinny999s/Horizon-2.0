package dev.horizon.client.hud.widgets

import dev.horizon.client.event.EventBus
import dev.horizon.client.event.ScoreBoardRenderEvent
import dev.horizon.client.gui.modern.Tok
import dev.horizon.client.hud.HudWidget
import dev.horizon.client.render.Animation
import dev.horizon.client.render.Colors
import dev.horizon.client.render.Easing
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.numbers.StyledFormat
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.Objective
import net.minecraft.world.scores.PlayerScoreEntry
import net.minecraft.world.scores.PlayerTeam
import java.util.Optional
import kotlin.math.max

/**
 * Not in the original: the server's sidebar scoreboard in the HUD's look, movable in the HUD editor. The same lines as
 * vanilla's sidebar (your team colour's objective if it has one, else the sidebar's; up to 15 entries, highest score
 * first, names inside their team's prefix and suffix, scores in the objective's own number format, so a server that
 * hides them keeps them hidden), as a title plate over one panel, with the server's colours. Vanilla's own sidebar is
 * hidden while this shows it ([hideVanilla]); [scores] can drop the numbers too.
 */
object ScoreboardWidget : HudWidget("scoreboard-widget") {
    private val scores = bool("scores", true)
    private val hideVanilla = bool("hide-vanilla", true)

    /** A run of text in one colour. */
    private class Run(val text: String, val color: Int)
    private class Line(val name: List<Run>, val score: List<Run>)

    private val anim = Animation(Easing.OUT_BACK, 250)

    init {
        EventBus.on<ScoreBoardRenderEvent>(this) { e -> if (enabled && hideVanilla.value) e.cancel() }
    }

    override fun render() {
        val objective = sidebar()
        val (title, lines) = if (objective != null) {
            runs(objective.displayName, Tok.T1) to lines(objective)
        } else if (editorMode) {
            SAMPLE_TITLE to SAMPLE
        } else {
            anim.update(0f)
            size(0f, 0f)
            return
        }
        anim.update(1f)
        val a = anim.value.coerceIn(0f, 1f)
        val alignRight = left - screenW / 2f > 0

        // The title plate: the icon, a divider, the objective's name.
        val titleW = width(title) + 25f
        var w = titleW
        for (l in lines) w = max(w, PAD + width(l.name) + (if (l.score.isEmpty()) 0f else GAP + width(l.score)) + PAD)
        val hx = left + if (alignRight) w - titleW else 0f
        plate(hx, top, titleW, 16f, a)
        icon(hx + 3.5f, top + 3f, a)
        divider(hx + 16.5f, top + 3.75f, a)
        draw(title, hx + 20f, top + 8f, a)
        if (lines.isEmpty()) {
            size(w, 16f)
            return
        }

        // One panel under it: names on the left, scores on the right.
        val py = top + 17f
        val ph = lines.size * ROW + 5f
        plate(left, py, w, ph, a)
        var y = py + 2.5f + ROW / 2f
        for (l in lines) {
            draw(l.name, left + PAD, y, a)
            if (l.score.isNotEmpty()) draw(l.score, left + w - PAD - width(l.score), y, a)
            y += ROW
        }
        size(w, 17f + ph)
    }

    /** The objective vanilla's sidebar would show: your team colour's own slot first, then the sidebar's. */
    private fun sidebar(): Objective? {
        val p = mc.player ?: return null
        val board = mc.level?.scoreboard ?: return null
        board.getPlayersTeam(p.scoreboardName)?.color?.let { c ->
            if (c.isPresent) board.getDisplayObjective(c.get().displaySlot())?.let { return it }
        }
        return board.getDisplayObjective(DisplaySlot.SIDEBAR)
    }

    private fun lines(o: Objective): List<Line> {
        val board = o.scoreboard
        val format = o.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT)
        return board.listPlayerScores(o).asSequence()
            .filter { !it.isHidden }
            .sortedWith(ORDER)
            .take(15)
            .map { e ->
                val name = PlayerTeam.formatNameForTeam(board.getPlayersTeam(e.owner()), e.ownerName())
                Line(runs(name, Tok.T1), if (scores.value) runs(e.formatValue(format), SCORE) else emptyList())
            }
            .toList()
    }

    /** [c] as coloured runs: its styles' colours, and any § codes typed into its text (old-style servers), over [base]. */
    private fun runs(c: Component, base: Int): List<Run> {
        val out = ArrayList<Run>()
        c.visit({ style: Style, text: String ->
            var color = style.color?.let { 0xFF000000.toInt() or it.value } ?: base
            val sb = StringBuilder()
            var i = 0
            while (i < text.length) {
                if (text[i] == '§' && i + 1 < text.length) {
                    if (sb.isNotEmpty()) out += Run(sb.toString(), color).also { sb.clear() }
                    val code = text[i + 1].lowercaseChar()
                    val n = "0123456789abcdef".indexOf(code)
                    if (code == 'r') color = base else if (n >= 0) color = CODES[n]
                    i += 2
                    continue
                }
                sb.append(text[i])
                i++
            }
            if (sb.isNotEmpty()) out += Run(sb.toString(), color)
            Optional.empty<Unit>()
        }, Style.EMPTY)
        return out
    }

    private fun width(runs: List<Run>) = runs.sumOf { tw(it.text).toDouble() }.toFloat()

    private fun draw(runs: List<Run>, x: Float, cy: Float, a: Float) {
        var cx = x
        for (r in runs) {
            ctext(r.text, cx, cy, Colors.withAlpha(r.color, Colors.alphaF(r.color) * a))
            cx += tw(r.text)
        }
    }

    /** Vanilla's sidebar order: highest score first, then by name. */
    private val ORDER = Comparator.comparingInt<PlayerScoreEntry> { it.value() }.reversed()
        .thenComparing({ it.owner() }, String.CASE_INSENSITIVE_ORDER)

    /** Vanilla's colours for the codes 0-9 and a-f. */
    private val CODES = intArrayOf(0xFF000000.toInt(), 0xFF0000AA.toInt(), 0xFF00AA00.toInt(), 0xFF00AAAA.toInt(), 0xFFAA0000.toInt(),
        0xFFAA00AA.toInt(), 0xFFFFAA00.toInt(), 0xFFAAAAAA.toInt(), 0xFF555555.toInt(), 0xFF5555FF.toInt(), 0xFF55FF55.toInt(),
        0xFF55FFFF.toInt(), 0xFFFF5555.toInt(), 0xFFFF55FF.toInt(), 0xFFFFFF55.toInt(), -1)

    /** Vanilla's sidebar red, for a score with no colour of its own. */
    private const val SCORE = 0xFFFF5555.toInt()
    private const val ROW = 10f
    private const val PAD = 5f
    private const val GAP = 8f

    /** What the HUD editor shows with no scoreboard up, so it can be placed. */
    private val SAMPLE_TITLE = listOf(Run("BED WARS", 0xFFFFFF55.toInt()))
    private val SAMPLE = listOf(
        Line(listOf(Run("Diamond II in 4:12", Tok.T1)), emptyList()),
        Line(listOf(Run("R ", 0xFFFF5555.toInt()), Run("Red: ", Tok.T1), Run("✔", 0xFF55FF55.toInt())), emptyList()),
        Line(listOf(Run("B ", 0xFF5555FF.toInt()), Run("Blue: ", Tok.T1), Run("2", 0xFFFFFF55.toInt())), emptyList()),
        Line(listOf(Run("Kills: ", Tok.T1), Run("3", 0xFF55FF55.toInt())), emptyList()),
    )
}
