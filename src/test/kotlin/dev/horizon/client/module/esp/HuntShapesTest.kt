package dev.horizon.client.module.esp

import dev.horizon.client.module.esp.HuntShapes.Cell
import dev.horizon.client.module.esp.HuntShapes.Column
import dev.horizon.client.module.esp.HuntShapes.P
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HuntShapesTest {
    private fun rect(w: Int, h: Int) = buildSet { for (u in 0 until w) for (v in 0 until h) add(Cell(u, v)) }

    @Test
    fun portalSizes() {
        assertNotNull(HuntShapes.portalRect(rect(2, 3), 8, 8))
        assertNotNull(HuntShapes.portalRect(rect(4, 5), 8, 8))
        assertNull(HuntShapes.portalRect(rect(1, 3), 8, 8), "too thin")
        assertNull(HuntShapes.portalRect(rect(2, 2), 8, 8), "too low")
        assertNull(HuntShapes.portalRect(rect(9, 3), 8, 8), "too wide")
        assertNull(HuntShapes.portalRect(emptySet(), 8, 8))
    }

    @Test
    fun cornersOnlyForAFrame() {
        val frame = rect(4, 5) - setOf(Cell(0, 0), Cell(3, 0), Cell(0, 4), Cell(3, 4))
        assertEquals(HuntShapes.Rect(0, 0, 3, 4), HuntShapes.portalRect(frame, 8, 8))
        assertNull(HuntShapes.portalRect(rect(3, 3) - Cell(0, 0), 8, 8), "too small to be a frame")
        assertNull(HuntShapes.portalRect(rect(4, 5) - Cell(1, 2), 8, 8), "a hole in the middle")
        assertNull(HuntShapes.portalRect(rect(4, 5) - setOf(Cell(1, 0), Cell(0, 0)), 8, 8), "not only corners")
    }

    /** A straight line of columns from x = 0, each linked to its neighbours. */
    private fun line(n: Int, height: (Int) -> Int = { 2 }) = (0 until n).map { i ->
        Column(P(i, 0, 0), height(i), listOfNotNull(if (i > 0) P(i - 1, 0, 0) else null, if (i < n - 1) P(i + 1, 0, 0) else null))
    }

    @Test
    fun aStraightTunnelIsOneBox() {
        val p = HuntShapes.passages(line(12), 10).single()
        assertEquals(12, p.length)
        assertFalse(p.stairs)
        assertEquals(listOf(HuntShapes.Box(0, 0, 0, 11, 1, 0)), p.boxes)
    }

    @Test
    fun tooShortOrUnevenIsDropped() {
        assertTrue(HuntShapes.passages(line(9), 10).isEmpty())
        assertTrue(HuntShapes.passages(line(12) { 2 + it % 3 }, 10).isEmpty(), "varying height is a natural crack")
        assertEquals(1, HuntShapes.passages(line(12) { if (it == 5) 3 else 2 }, 10).size, "one odd column is fine")
    }

    @Test
    fun stepsMakeStairs() {
        val stairs = (0 until 10).map { i ->
            Column(P(i, i, 0), 3, listOfNotNull(if (i > 0) P(i - 1, i - 1, 0) else null, if (i < 9) P(i + 1, i + 1, 0) else null))
        }
        val p = HuntShapes.passages(stairs, 10).single()
        assertTrue(p.stairs)
        assertEquals(10, p.boxes.size)
    }

    @Test
    fun separatePassagesStaySeparate() {
        val second = line(12).map { c -> Column(P(c.pos.x, c.pos.y, 5), c.height, c.links.map { P(it.x, it.y, 5) }) }
        assertEquals(2, HuntShapes.passages(line(12) + second, 10).size)
    }
}
