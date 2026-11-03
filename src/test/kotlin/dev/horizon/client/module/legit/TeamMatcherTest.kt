package dev.horizon.client.module.legit

import dev.horizon.client.module.legit.TeamMatcher.Span
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TeamMatcherTest {
    @Test
    fun `extracts username colour instead of leading gray level bracket`() {
        // Hypixel BedWars / SkyWars tab format: gray star bracket followed by team-coloured player name.
        val redPlayer = TeamMatcher.extractLegacyColor("§7[245✫] §cRedWarrior", "RedWarrior")
        val bluePlayer = TeamMatcher.extractLegacyColor("§7[118✫] §9BlueDefender", "BlueDefender")
        val redMate = TeamMatcher.extractLegacyColor("§7[12✫] §c§lRedBuddy", "RedBuddy")

        assertEquals(0xFF5555, redPlayer)
        assertEquals(0x5555FF, bluePlayer)
        assertEquals(0xFF5555, redMate)
        assertTrue(TeamMatcher.colorsMatch(redPlayer, redMate))
        assertFalse(TeamMatcher.colorsMatch(redPlayer, bluePlayer))
    }

    @Test
    fun `extracts component style colour and bungee hex codes`() {
        val spans = listOf(
            Span("[Lv50] ", 0xAAAAAA),
            Span("[RED] ", 0xFF5555),
            Span("Alice", 0xFF5555)
        )
        assertEquals(0xFF5555, TeamMatcher.extractNameColor(spans, "Alice"))

        val hex = TeamMatcher.extractLegacyColor("§x§3§4§9§8§d§bHexBlueUser", "HexBlueUser")
        assertEquals(0x3498DB, hex)
    }

    @Test
    fun `ignores default white unteamed players when ignoreWhite is enabled`() {
        assertNull(TeamMatcher.extractLegacyColor("§fSurvivalGuy", "SurvivalGuy", ignoreWhite = true))
        assertEquals(0xFFFFFF, TeamMatcher.extractLegacyColor("§fSurvivalGuy", "SurvivalGuy", ignoreWhite = false))
        assertFalse(TeamMatcher.colorsMatch(0xFFFFFF, 0xFFFFFF, ignoreWhite = true))
    }

    @Test
    fun `matches dyed leather armour within tolerance while rejecting default undyed leather`() {
        assertNull(TeamMatcher.normalizeArmorColor(TeamMatcher.DEFAULT_LEATHER_RGB, ignoreDefaultLeather = true))
        val redA = TeamMatcher.normalizeArmorColor(0xFF2222, ignoreDefaultLeather = true)
        val redB = TeamMatcher.normalizeArmorColor(0xF62825, ignoreDefaultLeather = true)
        val blue = TeamMatcher.normalizeArmorColor(0x2244FF, ignoreDefaultLeather = true)

        assertTrue(TeamMatcher.colorsMatch(redA, redB, tolerance = 16))
        assertFalse(TeamMatcher.colorsMatch(redA, blue, tolerance = 16))
    }

    @Test
    fun `extracts clan prefix tag while skipping pure numeric level tags`() {
        assertEquals("HORIZON", TeamMatcher.extractPrefixTag("§7[100] §b[Horizon] §fDevyn", "Devyn"))
        assertEquals("RED", TeamMatcher.extractPrefixTag("§c[RED] Fighter", "Fighter"))
        assertNull(TeamMatcher.extractPrefixTag("§7[100] SoloPlayer", "SoloPlayer"))
    }
}
