package dev.horizon.client.module.legit

import dev.horizon.client.module.Categories
import dev.horizon.client.module.Module
import dev.horizon.client.module.Modules
import dev.horizon.client.setting.GroupMode
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.player.Player
import net.minecraft.world.scores.PlayerTeam
import java.util.Optional

/**
 * Not in the original: universal multi-server teammate detection. Combines scoreboard team membership, scoreboard
 * `TeamColor` / prefix colour (for servers that give every player a unique scoreboard team for nametag health/ranks),
 * tab-list / display-name colour on the username itself, dyed leather armour colour (BedWars / Bridge / WoolWars), and
 * `[TAG]` clan prefixes. Never calls `Entity.getTeamColor()`, which `GlowEsp` overrides.
 */
class Teams : Module("teams", Categories.LEGIT, listOf("team-check", "teammates", "allies")) {
    enum class ArmorSlot { HELMET_OR_CHEST, ANY_MATCH, HELMET_ONLY, CHEST_ONLY }

    val checks = group("checks", GroupMode.BOOLS)
    val scoreboardTeam = bool("scoreboard-team", true, parent = checks.value)
    val teamColor = bool("team-color", true, parent = checks.value)
    val nameColor = bool("name-color", true, parent = checks.value)
    val armorColor = bool("armor-color", true, parent = checks.value)
    val prefixTag = bool("prefix-tag", false, parent = checks.value)

    val armorSlot = enum("armor-slot", ArmorSlot.HELMET_OR_CHEST) { armorColor.value }
    val armorTolerance = int("armor-tolerance", 16, 0..64) { armorColor.value }
    val ignoreDefaultLeather = bool("ignore-default-leather", true) { armorColor.value }
    val ignoreWhite = bool("ignore-white", true) { teamColor.value || nameColor.value }
    val espAllyColor = bool("esp-ally-color", true)

    fun matches(self: Player, other: Player): Boolean {
        if (self === other) return true
        val noWhite = ignoreWhite.value

        if (scoreboardTeam.value) {
            val st = self.team
            val ot = other.team
            if (st != null && ot != null && st.isAlliedTo(ot)) return true
        }
        if (teamColor.value) {
            val sc = rawScoreboardColor(self, noWhite)
            val oc = rawScoreboardColor(other, noWhite)
            if (TeamMatcher.colorsMatch(sc, oc, ignoreWhite = noWhite)) return true
        }
        if (nameColor.value) {
            val sc = displayColor(self, noWhite)
            val oc = displayColor(other, noWhite)
            if (TeamMatcher.colorsMatch(sc, oc, ignoreWhite = noWhite)) return true
        }
        if (armorColor.value && armorMatches(self, other)) return true
        if (prefixTag.value) {
            val st = playerTag(self)
            val ot = playerTag(other)
            if (st != null && st == ot) return true
        }
        return false
    }

    private fun armorMatches(self: Player, other: Player): Boolean {
        val tol = armorTolerance.value
        val noPlain = ignoreDefaultLeather.value
        val slots = when (armorSlot.value) {
            ArmorSlot.HELMET_ONLY -> listOf(EquipmentSlot.HEAD)
            ArmorSlot.CHEST_ONLY -> listOf(EquipmentSlot.CHEST)
            ArmorSlot.HELMET_OR_CHEST -> listOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST)
            ArmorSlot.ANY_MATCH -> ALL_ARMOR_SLOTS
        }
        for (slot in slots) {
            val sc = dyedSlotColor(self, slot, noPlain) ?: continue
            val oc = dyedSlotColor(other, slot, noPlain) ?: continue
            if (TeamMatcher.colorsMatch(sc, oc, tolerance = tol)) return true
        }
        if (armorSlot.value == ArmorSlot.ANY_MATCH) {
            val selfColors = slots.mapNotNull { dyedSlotColor(self, it, noPlain) }
            if (selfColors.isEmpty()) return false
            val otherColors = slots.mapNotNull { dyedSlotColor(other, it, noPlain) }
            for (sc in selfColors) for (oc in otherColors) {
                if (TeamMatcher.colorsMatch(sc, oc, tolerance = tol)) return true
            }
        }
        return false
    }

    companion object {
        private val ALL_ARMOR_SLOTS = listOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)
        private val module by lazy { Modules.get<Teams>() }

        /** True when [Teams] is enabled and [player] is on the local player's team. */
        fun isTeammate(player: Player): Boolean {
            val m = module
            if (!m.enabled) return false
            val self = m.mc.player ?: return false
            if (player === self) return false
            return m.matches(self, player)
        }

        /** True when [Teams] is enabled with `esp-ally-color` on and [player] is a teammate. */
        fun isEspAlly(player: Player): Boolean = module.espAllyColor.value && isTeammate(player)

        /** Scoreboard team colour comparison safe from `GlowEsp`'s `Entity.getTeamColor` hook. */
        fun rawScoreboardSameColor(a: Player, b: Player): Boolean {
            val ca = rawScoreboardColor(a, ignoreWhite = true) ?: return false
            val cb = rawScoreboardColor(b, ignoreWhite = true) ?: return false
            return ca == cb
        }

        /**
         * Resolves [player]'s 24-bit RGB server team colour (scoreboard `TeamColor` -> team prefix -> tab/display name ->
         * dyed helmet/chestplate), or `null` if unteamed. Safe to call inside `GlowEsp`.
         */
        fun resolveColor(player: Player, ignoreWhite: Boolean = true): Int? {
            rawScoreboardColor(player, ignoreWhite)?.let { return it }
            displayColor(player, ignoreWhite)?.let { return it }
            val noPlain = if (module.enabled) module.ignoreDefaultLeather.value else true
            dyedSlotColor(player, EquipmentSlot.HEAD, noPlain)?.let { return it }
            dyedSlotColor(player, EquipmentSlot.CHEST, noPlain)?.let { return it }
            return null
        }

        private fun rawScoreboardColor(player: Player, ignoreWhite: Boolean): Int? {
            val team = player.team ?: return null
            val tc = team.color.orElse(null)
            if (tc != null) {
                val rgb = tc.rgb() and 0xFFFFFF
                if (!ignoreWhite || rgb != TeamMatcher.WHITE_RGB) return rgb
            }
            componentColor(team.playerPrefix, "", ignoreWhite)?.let { return it }
            return null
        }

        private fun displayColor(player: Player, ignoreWhite: Boolean): Int? {
            val uname = player.gameProfile.name()
            val tab = module.mc.connection?.getPlayerInfo(player.uuid)?.tabListDisplayName
            if (tab != null) {
                componentColor(tab, uname, ignoreWhite)?.let { return it }
            }
            return componentColor(player.displayName, uname, ignoreWhite)
        }

        private fun playerTag(player: Player): String? {
            val uname = player.gameProfile.name()
            val tab = module.mc.connection?.getPlayerInfo(player.uuid)?.tabListDisplayName
            if (tab != null) {
                TeamMatcher.extractPrefixTag(tab.string, uname)?.let { return it }
            }
            val team = player.team
            if (team != null) {
                TeamMatcher.extractPrefixTag(team.playerPrefix.string, "")?.let { return it }
            }
            return TeamMatcher.extractPrefixTag(player.displayName.string, uname)
        }

        private fun dyedSlotColor(player: Player, slot: EquipmentSlot, ignoreDefaultLeather: Boolean): Int? {
            val dyed = player.getItemBySlot(slot).get(DataComponents.DYED_COLOR) ?: return null
            return TeamMatcher.normalizeArmorColor(dyed.rgb(), ignoreDefaultLeather)
        }

        private fun componentColor(component: Component, playerName: String, ignoreWhite: Boolean): Int? {
            val spans = ArrayList<TeamMatcher.Span>(4)
            component.visit({ style: Style, text: String ->
                if (text.isNotEmpty()) spans += TeamMatcher.Span(text, style.color?.value)
                Optional.empty<Unit>()
            }, Style.EMPTY)
            return TeamMatcher.extractNameColor(spans, playerName, ignoreWhite)
        }
    }
}
