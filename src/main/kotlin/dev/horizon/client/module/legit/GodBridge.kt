package dev.horizon.client.module.legit

import dev.horizon.client.combat.Inventory
import dev.horizon.client.combat.Placer
import dev.horizon.client.combat.PlaceBoxes
import dev.horizon.client.combat.PlayerState
import dev.horizon.client.combat.Rng
import dev.horizon.client.combat.Rotation
import dev.horizon.client.event.KeyboardInputEvent
import dev.horizon.client.event.MouseTurnEvent
import dev.horizon.client.event.Render2DEvent
import dev.horizon.client.event.Render3DEvent
import dev.horizon.client.event.TickEvent
import dev.horizon.client.gui.Keys
import dev.horizon.client.module.Animation
import dev.horizon.client.module.Categories
import dev.horizon.client.module.Module
import dev.horizon.client.module.Modules
import dev.horizon.client.module.Render
import dev.horizon.client.module.basic.GuiMove
import dev.horizon.client.module.basic.Sprint
import dev.horizon.client.render.Fonts
import dev.horizon.client.render.Theme
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Input
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Not in the original: GodBridge assist module.
 * Assists with advanced bridging techniques:
 * - Legit: Automatically sneaks at the edge of blocks when moving backwards (Ninja / Fast Bridge).
 * - GodBridge: Automatically steers & angles the camera (Pitch Lock / Auto-Angle) and places blocks while walking
 *   at full speed diagonally without sneaking.
 * Includes Pitch Check, Auto-Angle / Diagonal Snap, Activation Blocks requirement, Block Count HUD display,
 * and Block Selection (Whitelist/Blacklist).
 */
class GodBridge : Module("godbridge", Categories.LEGIT, listOf("god-bridge", "scaffold-legit", "fast-bridge-plus")) {
    enum class Mode { LEGIT, GOD_BRIDGE }
    enum class FilterMode { NONE, BLACKLIST, WHITELIST }
    enum class AngleMode { NONE, SNAP_DIAGONAL, SMOOTH_AIM, HARD_LOCK }

    val mode = enum("mode", Mode.GOD_BRIDGE)

    // Legit mode settings
    val sneakDelay = int("sneak-delay", 2, 0..10) { mode.value == Mode.LEGIT }
    val requireSneak = bool("require-sneak", false) { mode.value == Mode.LEGIT }
    val edgeDistance = float("edge-distance", 0.18f, 0.05f..0.29f) { mode.value == Mode.LEGIT }

    // GodBridge mode settings
    val autoAngle = enum("auto-angle", AngleMode.SMOOTH_AIM) { mode.value == Mode.GOD_BRIDGE }
    val aimSpeed = float("aim-speed", 300.0f, 60.0f..600.0f) { mode.value == Mode.GOD_BRIDGE && autoAngle.value == AngleMode.SMOOTH_AIM }
    val targetPitch = float("target-pitch", 76.5f, 65.0f..85.0f) { mode.value == Mode.GOD_BRIDGE && autoAngle.value != AngleMode.NONE }
    val activationBlocks = int("activation-blocks", 0, 0..10) { mode.value == Mode.GOD_BRIDGE }
    val placeDelay = int("place-delay", 0, 0..5) { mode.value == Mode.GOD_BRIDGE }
    val requireRightClick = bool("require-right-click", false) { mode.value == Mode.GOD_BRIDGE }

    // General settings
    val blockCount = bool("block-count", true)
    val pitchCheck = bool("pitch-check", true)
    val minPitch = float("min-pitch", 55.0f, 25.0f..85.0f) { pitchCheck.value }
    val blockSelection = enum("block-selection", FilterMode.NONE)
    val blockList = blocks("blocks", blockList("block.minecraft.tnt", "block.minecraft.sand", "block.minecraft.gravel")) { blockSelection.value != FilterMode.NONE }

    private var sneaking = false
    private var sneakTimer = 0
    private var placedCount = 0
    private var lastPlaceTick = 0
    private var lastTargetPos: BlockPos? = null
    private var currentPlacement: BlockHitResult? = null

    // Legit camera rotation physics variables
    private var vYaw = 0.0
    private var vPitch = 0.0
    private var carryX = 0.0
    private var carryY = 0.0
    private var lastNs = 0L

    override fun onEnable() = resetState()
    override fun onDisable() = resetState()

    private fun resetState() {
        sneaking = false
        sneakTimer = 0
        placedCount = 0
        lastPlaceTick = 0
        lastTargetPos = null
        currentPlacement = null
        vYaw = 0.0
        vPitch = 0.0
        carryX = 0.0
        carryY = 0.0
        lastNs = 0L
    }

    init {
        // Handle Legit mode sneaking via input tick
        on<KeyboardInputEvent>(-10) {
            val p = mc.player ?: return@on
            val level = mc.level ?: return@on
            val keys = p.input.keyPresses

            if (mode.value != Mode.LEGIT) {
                sneaking = false
                return@on
            }

            if (!activeForLegit(p, keys)) {
                sneaking = false
                sneakTimer = 0
                return@on
            }

            val edge = overEdge(p, level, keys)
            if (edge && (p.onGround() || sneaking)) {
                sneaking = true
                sneakTimer = sneakDelay.value
            } else if (sneaking) {
                if (sneakTimer > 0) {
                    sneakTimer--
                } else {
                    sneaking = false
                }
            }

            if (sneaking && !keys.shift()) {
                Modules.get<Sprint>().releaseKey()
                p.input.keyPresses = Input(
                    keys.forward(),
                    keys.backward(),
                    keys.left(),
                    keys.right(),
                    keys.jump(),
                    true,
                    false
                )
            }
        }

        // Handle GodBridge mode auto-placement & finding bridge placement
        on<TickEvent> {
            val p = mc.player ?: return@on
            val level = mc.level ?: return@on
            if (mc.gui.screen() != null) return@on
            if (mode.value != Mode.GOD_BRIDGE) return@on

            if (p.abilities.flying || p.isInLiquid || p.isPassenger || p.isFallFlying) {
                placedCount = 0
                currentPlacement = null
                return@on
            }

            if (pitchCheck.value && p.xRot < minPitch.value) {
                placedCount = 0
                currentPlacement = null
                return@on
            }

            if (requireRightClick.value && !(PlayerState.physical(mc.options.keyUse) || mc.options.keyUse.isDown)) {
                placedCount = 0
                currentPlacement = null
                return@on
            }

            val curHolding = p.mainHandItem
            if (!isValidBlock(curHolding)) {
                val found = Inventory.hotbarFind { isValidBlock(it) }
                if (found != null) {
                    Inventory.selectHotbar(found.slot)
                } else {
                    currentPlacement = null
                    return@on
                }
            }

            // Find best target block to place against
            val placement = findGodBridgePlacement(p, level)
            currentPlacement = placement

            if (placement == null) return@on

            // Snap or Hard Lock handling during tick if configured
            when (autoAngle.value) {
                AngleMode.SNAP_DIAGONAL -> {
                    // Snap yaw to nearest 45-degree angle, pitch to targetPitch
                    val snappedYaw = (p.yRot / 45f).roundToInt() * 45f
                    p.yRot = snappedYaw
                    p.xRot = targetPitch.value
                }
                AngleMode.HARD_LOCK -> {
                    val eye = p.eyePosition
                    val loc = placement.location
                    val dx = loc.x - eye.x
                    val dz = loc.z - eye.z
                    val wantYaw = (Math.toDegrees(atan2(dz, dx)) - 90.0).toFloat()
                    p.yRot = wantYaw
                    p.xRot = targetPitch.value
                }
                else -> {}
            }

            // Activation blocks check
            if (placedCount < activationBlocks.value) {
                if (PlayerState.physical(mc.options.keyUse) || mc.options.keyUse.isDown) {
                    if (p.tickCount - lastPlaceTick > 4) {
                        placedCount++
                        lastPlaceTick = p.tickCount
                    }
                }
                return@on
            }

            // Place block
            if (p.tickCount - lastPlaceTick >= placeDelay.value) {
                lastPlaceTick = p.tickCount
                lastTargetPos = placement.blockPos.relative(placement.direction)
                mc.gameMode?.useItemOn(p, InteractionHand.MAIN_HAND, placement)
                PlaceBoxes.add(placement.blockPos.relative(placement.direction), Theme.primary or (0xFF shl 24), Theme.primary or (0x44 shl 24), Animation.Fade, Render.BOTH)
            }
        }

        // Smooth aiming on MouseTurnEvent for legit steering
        on<MouseTurnEvent> {
            val p = mc.player ?: return@on
            if (mode.value != Mode.GOD_BRIDGE || autoAngle.value != AngleMode.SMOOTH_AIM) return@on
            val placement = currentPlacement ?: return@on
            val now = System.nanoTime()
            val dt = if (lastNs == 0L) 0.0 else ((now - lastNs) / 1e9).coerceIn(0.0, 0.05)
            lastNs = now

            val eye = p.eyePosition
            val aim = placement.location
            val dx = aim.x - eye.x
            val dz = aim.z - eye.z
            val wantYaw = Math.toDegrees(atan2(dz, dx)) - 90.0
            val wantPitch = targetPitch.value.toDouble()
            val errYaw = Mth.wrapDegrees(wantYaw - p.yRot)
            val errPitch = wantPitch - p.xRot

            val maxSpeed = aimSpeed.value.toDouble()
            val stiffness = 160.0
            val damping = 0.85
            val c = 2.0 * sqrt(stiffness) * damping

            vYaw = (vYaw + (stiffness * errYaw - c * vYaw) * dt).coerceIn(-maxSpeed, maxSpeed)
            vPitch = (vPitch + (stiffness * errPitch - c * vPitch) * dt).coerceIn(-maxSpeed, maxSpeed)

            val f = mc.options.sensitivity().get() * 0.6 + 0.2
            val perCount = f * f * f * 8.0
            val step = perCount * 0.15
            carryX += vYaw * dt / step
            carryY += vPitch * dt / step
            val countsX = carryX.toInt()
            val countsY = carryY.toInt()
            carryX -= countsX
            carryY -= countsY
            if (countsX != 0 || countsY != 0) p.turn(countsX * perCount, countsY * perCount)
        }

        // Render Block Count in center of screen
        on<Render2DEvent> { e ->
            if (!blockCount.value) return@on
            val p = mc.player ?: return@on
            val totalBlocks = countUsableBlocks(p)
            val text = "$totalBlocks blocks"
            val font = Fonts.medium8
            val w = font.width(text)
            val cx = mc.window.guiScaledWidth / 2f
            val cy = mc.window.guiScaledHeight / 2f + 16f
            val col = if (totalBlocks > 32) 0xFFF5F5F7.toInt() else if (totalBlocks > 10) 0xFFFFAA00.toInt() else 0xFFFF5555.toInt()
            font.draw(text, cx - w / 2f, cy, col)
            font.flush()
        }

        on<Render3DEvent> {
            val bp = lastTargetPos ?: return@on
            PlaceBoxes.add(bp, Theme.primary or (0xFF shl 24), Theme.primary or (0x22 shl 24), Animation.Static, Render.BOTH)
        }
    }

    /**
     * Determines the optimal block position and face to place against for GodBridging.
     * Checks what the crosshair is currently pointing at, or searches directly under/ahead of the player's movement.
     */
    private fun findGodBridgePlacement(p: Player, level: Level): BlockHitResult? {
        // 1. If currently aiming at a valid placeable block face, use it!
        val hit = mc.hitResult as? BlockHitResult
        if (hit != null && hit.type == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            val placedPos = hit.blockPos.relative(hit.direction)
            if (placedPos.y <= p.blockY && level.getBlockState(placedPos).canBeReplaced()) {
                return hit
            }
        }

        // 2. Otherwise calculate based on movement direction and blocks below feet
        val feet = BlockPos.containing(p.position())
        val underFeet = feet.below()

        // If under feet is air, find adjacent solid block to place from
        val candidates = ArrayList<BlockHitResult>()
        for (d in Direction.Plane.HORIZONTAL) {
            val neighbor = underFeet.relative(d)
            if (!level.getBlockState(neighbor).canBeReplaced()) {
                val hitVec = Vec3.atCenterOf(neighbor).add(Vec3(d.opposite.step()).scale(0.5))
                candidates += BlockHitResult(hitVec, d.opposite, neighbor, false)
            }
        }

        if (candidates.isNotEmpty()) {
            val eye = p.eyePosition
            return candidates.minByOrNull { eye.distanceToSqr(it.location) }
        }

        return null
    }

    private fun activeForLegit(p: Player, keys: Input): Boolean {
        if (p.abilities.flying || p.isInLiquid || p.isPassenger || p.isFallFlying) return false
        if (mc.gui.screen() != null && !Modules.get<GuiMove>().enabled) return false
        if (pitchCheck.value && p.xRot < minPitch.value) return false
        if (requireSneak.value && !(PlayerState.physical(mc.options.keyShift) || mc.options.keyShift.isDown)) return false
        if (!isValidBlock(p.mainHandItem) && !isValidBlock(p.offhandItem)) return false
        return keys.backward() || sneaking
    }

    private fun isValidBlock(stack: ItemStack): Boolean {
        if (stack.isEmpty || stack.item !is BlockItem) return false
        val block = (stack.item as BlockItem).block
        return when (blockSelection.value) {
            FilterMode.NONE -> true
            FilterMode.BLACKLIST -> block !in blockList.value
            FilterMode.WHITELIST -> block in blockList.value
        }
    }

    private fun countUsableBlocks(p: Player): Int {
        var count = 0
        val inv = p.inventory
        for (i in 0 until minOf(36, inv.containerSize)) {
            val s = inv.getItem(i)
            if (isValidBlock(s)) count += s.count
        }
        return count
    }

    private fun overEdge(p: Player, level: Level, keys: Input): Boolean {
        val d = edgeDistance.value.toDouble().coerceIn(0.05, 0.29)
        val b = p.boundingBox
        val yMin = p.y - 0.5
        val yMax = p.y + 0.02
        val here = AABB(b.minX + d, yMin, b.minZ + d, b.maxX - d, yMax, b.maxZ - d)
        if (level.noCollision(p, here)) return true

        val impulse = inputVector(p.yRot, keys, 0.08)
        val dx = p.deltaMovement.x + impulse[0]
        val dz = p.deltaMovement.z + impulse[1]
        if (dx * dx + dz * dz < 1e-6) return false
        val ahead = AABB(b.minX + d + dx, yMin, b.minZ + d + dz, b.maxX - d + dx, yMax, b.maxZ - d + dz)
        return level.noCollision(p, ahead)
    }

    private fun inputVector(yawDeg: Float, keys: Input, scale: Double): DoubleArray {
        val fwd = (if (keys.forward()) 1.0 else 0.0) - (if (keys.backward()) 1.0 else 0.0)
        val str = (if (keys.left()) 1.0 else 0.0) - (if (keys.right()) 1.0 else 0.0)
        if (fwd == 0.0 && str == 0.0) return doubleArrayOf(0.0, 0.0)
        val len = Mth.invSqrt(fwd * fwd + str * str)
        val nf = fwd * len * scale
        val ns = str * len * scale
        val rad = Math.toRadians(yawDeg.toDouble())
        val s = sin(rad)
        val c = cos(rad)
        return doubleArrayOf(ns * c - nf * s, nf * c + ns * s)
    }
}
