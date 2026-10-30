package dev.horizon.client.module.rage

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Synthetic chunk maps: everything within [range] chunks is known; old chunks are what [isOld] says. */
class TrailFinderTest {
    private val range = 40

    private fun known(cx: Int, cz: Int) = abs(cx) <= range && abs(cz) <= range

    /** Old chunks within [width] chunks of the line from the origin along [yaw] (both ways when [both]). */
    private fun line(yaw: Double, width: Double = 0.75, both: Boolean = true): (Int, Int) -> Boolean {
        val r = Math.toRadians(yaw)
        val dx = -sin(r)
        val dz = cos(r)
        return { cx, cz ->
            val x = cx + 0.5
            val z = cz + 0.5
            val along = x * dx + z * dz
            val across = abs(x * dz - z * dx)
            across <= width && (both || along >= -1)
        }
    }

    private fun angleDiff(a: Float, b: Double): Double {
        var d = (a - b) % 360.0
        if (d > 180) d -= 360
        if (d < -180) d += 360
        return abs(d)
    }

    @Test
    fun followsAStraightTrailAtAnAngle() {
        val old = line(30.0)
        val r = TrailFinder.steer(8.0, 8.0, 0f, old, ::known)
        assertEquals(TrailFinder.State.FOLLOWING, r.state)
        assertTrue(angleDiff(r.yaw, 30.0) <= 10, "yaw ${r.yaw}")
    }

    @Test
    fun keepsStraightOnATrailAlreadyAhead() {
        val r = TrailFinder.steer(8.0, 8.0, -45f, line(-45.0), ::known)
        assertEquals(TrailFinder.State.FOLLOWING, r.state)
        assertTrue(angleDiff(r.yaw, -45.0) <= 5, "yaw ${r.yaw}")
    }

    @Test
    fun followsACurveStepByStep() {
        // An arc of radius 40 chunks: centre at (40, 0) chunks, starting at the origin heading +Z.
        val cx0 = 40.0
        val old = { cx: Int, cz: Int -> abs(hypot(cx + 0.5 - cx0, cz + 0.5) - cx0) <= 0.8 && cz >= -1 }
        val wide = { cx: Int, cz: Int -> abs(cx) <= 200 && abs(cz) <= 200 }
        var x = 8.0
        var z = 8.0
        var yaw = 0f
        repeat(40) {
            val r = TrailFinder.steer(x, z, yaw, old, wide, reach = 8)
            assertEquals(TrailFinder.State.FOLLOWING, r.state, "lost at $x $z")
            yaw = r.yaw
            val rad = Math.toRadians(yaw.toDouble())
            x += -sin(rad) * 16
            z += cos(rad) * 16
        }
        // Still on the arc after 40 chunks of flying.
        assertTrue(abs(hypot(x / 16 - cx0, z / 16) - cx0) <= 2, "off the arc at ${x / 16} ${z / 16}")
    }

    @Test
    fun noTrailIsLost() {
        val r = TrailFinder.steer(8.0, 8.0, 0f, { _, _ -> false }, ::known)
        assertEquals(TrailFinder.State.LOST, r.state)
        assertEquals(0f, r.yaw)
    }

    @Test
    fun exploredLandKeepsTheHeading() {
        val r = TrailFinder.steer(8.0, 8.0, 12f, { cx, cz -> (cx * 7 + cz * 3) % 10 != 0 }, ::known)
        assertEquals(TrailFinder.State.EXPLORED, r.state)
        assertEquals(12f, r.yaw)
    }

    @Test
    fun doesNotTurnAroundForATrailBehind() {
        // The trail only runs backwards (-Z) from here; heading +Z there's nothing to follow.
        val old = line(180.0, both = false)
        val r = TrailFinder.steer(8.0, 8.0, 0f, old, ::known)
        assertEquals(TrailFinder.State.LOST, r.state)
    }

    @Test
    fun seesWhereATrailAheadEnds() {
        // Old chunks along +Z from the origin out to chunk 6 only.
        val old = { cx: Int, cz: Int -> cx == 0 && cz in 0..6 }
        val far = TrailFinder.farthestOld(8.0, 8.0, 0f, old, 10)
        assertTrue(far >= 6 * 16 - 8 && far <= 7 * 16, "far $far")
        assertEquals(0.0, TrailFinder.farthestOld(8.0, 8.0, 180f, old, 10))
    }

    @Test
    fun scatteredOldChunksAreNotATrail() {
        // A few lone old chunks in new land (worldgen noise) shouldn't count.
        val lone = setOf(3 to 2, -4 to 6, 5 to 9, -2 to -7)
        val r = TrailFinder.steer(8.0, 8.0, 0f, { cx, cz -> (cx to cz) in lone }, ::known)
        assertEquals(TrailFinder.State.LOST, r.state)
    }
}
