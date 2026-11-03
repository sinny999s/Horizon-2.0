package dev.horizon.client.module.attack

import java.util.Random
import kotlin.test.Test
import kotlin.test.assertTrue

class LegacyClickerTest {
    private fun simulateCps(minCps: Int, maxCps: Int, pattern: LegacyClicker.Pattern, seconds: Int = 40): Pair<Double, Set<Int>> {
        val state = LegacyClicker.State(Random(42L))
        var clicks = 0
        var lastClickTick = -1
        val tickGaps = HashSet<Int>()
        val totalTicks = seconds * 20
        for (tick in 0 until totalTicks) {
            val nowMs = tick * 50L
            if (state.ready(nowMs)) {
                state.onClick(nowMs, minCps, maxCps, pattern)
                clicks++
                if (lastClickTick >= 0) tickGaps += (tick - lastClickTick)
                lastClickTick = tick
            }
        }
        return (clicks.toDouble() / seconds) to tickGaps
    }

    @Test
    fun distinguishesMediumAndHighCpsAcrossPatterns() {
        for (pattern in LegacyClicker.Pattern.entries) {
            val (cpsLow, gapsLow) = simulateCps(7, 9, pattern)
            val (cpsMid, gapsMid) = simulateCps(11, 13, pattern)
            val (cpsHigh, _) = simulateCps(15, 17, pattern)

            assertTrue(cpsLow in 6.5..9.8, "$pattern 7..9 gave $cpsLow")
            assertTrue(cpsMid in 10.2..13.8, "$pattern 11..13 gave $cpsMid")
            assertTrue(cpsHigh in 14.0..18.0, "$pattern 15..17 gave $cpsHigh")
            assertTrue(cpsHigh - cpsMid > 2.0, "$pattern should separate 15..17 ($cpsHigh) from 11..13 ($cpsMid)")
            if (pattern != LegacyClicker.Pattern.FLAT) {
                assertTrue(gapsLow.size >= 2, "$pattern 7..9 should vary tick gaps, got $gapsLow")
                assertTrue(gapsMid.size >= 2, "$pattern 11..13 should vary tick gaps, got $gapsMid")
            }
        }
    }

    @Test
    fun handlesReversedMinMaxAndIdleGapWithoutBursting() {
        val state = LegacyClicker.State(Random(99L))
        assertTrue(state.ready(0L))
        state.onClick(0L, 16, 12, LegacyClicker.Pattern.HUMAN)

        // Idle for 5 seconds, then resume: should fire once on resume, not bank 60 clicks.
        assertTrue(state.ready(5000L))
        state.onClick(5000L, 16, 12, LegacyClicker.Pattern.HUMAN)
        // With 12..16 CPS (~62..83 ms), 1ms later cannot already be due.
        assertTrue(!state.ready(5001L))
    }
}
