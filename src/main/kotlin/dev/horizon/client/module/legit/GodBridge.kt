package dev.horizon.client.module.legit

import dev.horizon.client.combat.Inventory
import dev.horizon.client.combat.Movement
import dev.horizon.client.combat.PlayerState
import dev.horizon.client.combat.Rotation
import dev.horizon.client.combat.Rng
import dev.horizon.client.event.KeyboardInputEvent
import dev.horizon.client.event.MouseTurnEvent
import dev.horizon.client.event.Render2DEvent
import dev.horizon.client.event.SendPacketEvent
import dev.horizon.client.event.TickEvent
import dev.horizon.client.gui.Keys
import dev.horizon.client.mixin.MinecraftAccessor
import dev.horizon.client.module.Categories
import dev.horizon.client.module.Module
import dev.horizon.client.module.Modules
import dev.horizon.client.module.basic.GuiMove
import dev.horizon.client.module.basic.Sprint
import dev.horizon.client.render.Fonts
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
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
import net.minecraft.world.phys.HitResult
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
 * - GodBridge: Automates a hand-played god bridge. The camera is turned with ordinary mouse counts onto the
 *   diagonal angle, and blocks are placed by vanilla's own right-click on whatever the crosshair is on (no custom
 *   rotation packets, no fabricated hit positions); the 7-block jump is a real input press.
 * Includes Pitch Check, Auto-Angle / Diagonal Snap, Activation Blocks requirement, Block Count HUD display,
 * and Block Selection (Whitelist/Blacklist).
 */
class GodBridge : Module("godbridge", Categories.LEGIT, listOf("god-bridge", "scaffold-legit", "fast-bridge-plus")) {
    enum class Mode { LEGIT, GOD_BRIDGE }
    enum class FilterMode { NONE, BLACKLIST, WHITELIST }
    enum class AngleMode { NONE, SMOOTH_DIAGONAL, SNAP_DIAGONAL, HARD_LOCK }

    val mode = enum("mode", Mode.GOD_BRIDGE)

    // Legit mode settings
    val sneakDelay = int("sneak-delay", 2, 0..10) { mode.value == Mode.LEGIT }
    val requireSneak = bool("require-sneak", false) { mode.value == Mode.LEGIT }
    val edgeDistance = float("edge-distance", 0.18f, 0.05f..0.29f) { mode.value == Mode.LEGIT }

    // GodBridge mode settings
    val autoAngle = enum("auto-angle", AngleMode.NONE) { mode.value == Mode.GOD_BRIDGE }
    val aimSpeed = float("aim-speed", 300.0f, 60.0f..600.0f) { mode.value == Mode.GOD_BRIDGE && autoAngle.value == AngleMode.SMOOTH_DIAGONAL }
    val targetPitch = float("target-pitch", 76.5f, 65.0f..85.0f) { mode.value == Mode.GOD_BRIDGE && autoAngle.value != AngleMode.NONE }
    val autoJump = bool("auto-jump", true) { mode.value == Mode.GOD_BRIDGE }
    val jumpInterval = int("jump-interval", 7, 3..15) { mode.value == Mode.GOD_BRIDGE && autoJump.value }
    val activationBlocks = int("activation-blocks", 3, 0..10) { mode.value == Mode.GOD_BRIDGE }
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
    private var activated = false
    private var bridgeBlocksPlaced = 0
    private var lastPlaceTick = 0
    private var lastActiveTick = 0
    private var lastPlacedPos: BlockPos? = null
    /** A use was sent for this position; wait for its local block update before counting it. */
    private val pending = ArrayList<Pair<BlockPos, Int>>()
    /** Ticks to wait before the next use, re-rolled after each one like a human's click rhythm. */
    private var nextDelay = 0
    /** Set when a block-interval jump is due; applied to the next input tick as a real jump key press. */
    private var jumpRequested = false

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
        activated = (activationBlocks.value == 0)
        bridgeBlocksPlaced = 0
        lastPlaceTick = 0
        lastActiveTick = 0
        lastPlacedPos = null
        pending.clear()
        nextDelay = 0
        jumpRequested = false
        vYaw = 0.0
        vPitch = 0.0
        carryX = 0.0
        carryY = 0.0
        lastNs = 0L
    }

    init {
        // Track manual block placements for activation
        on<SendPacketEvent> { e ->
            if (mode.value != Mode.GOD_BRIDGE) return@on
            val pk = e.packet as? ServerboundUseItemOnPacket ?: return@on
            val p = mc.player ?: return@on
            if (activated) return@on

            val stack = p.getItemInHand(pk.hand)
            if (!isValidBlock(stack)) return@on

            val movingBack = Movement.moving() && (p.input.keyPresses.backward() || p.input.moveVector.y < 0f)
            if (movingBack) {
                val clickPos = pk.hitResult.blockPos
                val face = pk.hitResult.direction
                val placedPos = clickPos.relative(face)
                if (placedPos.y <= p.blockY && placedPos != lastPlacedPos) {
                    lastPlacedPos = placedPos
                    placedCount++
                    lastActiveTick = p.tickCount
                    if (placedCount >= activationBlocks.value) {
                        activated = true
                        bridgeBlocksPlaced = 0
                    }
                }
            }
        }

        // Handle Legit mode sneaking via input tick
        on<KeyboardInputEvent>(-10) {
            val p = mc.player ?: return@on
            val level = mc.level ?: return@on
            val keys = p.input.keyPresses

            if (mode.value != Mode.LEGIT) {
                sneaking = false
                // The block-interval jump goes through the input like a real space press.
                if (jumpRequested && p.onGround()) {
                    if (!keys.jump()) {
                        p.input.keyPresses = Input(keys.forward(), keys.backward(), keys.left(), keys.right(), true, keys.shift(), keys.sprint())
                    }
                    jumpRequested = false
                }
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

        // GodBridge mode: aims like a player (camera turns only) and uses the item through vanilla's own
        // right-click path on whatever the crosshair is really on, so every packet matches a hand-played bridge.
        on<TickEvent> {
            val p = mc.player ?: return@on
            val level = mc.level ?: return@on
            if (mc.gui.screen() != null) return@on
            if (mode.value != Mode.GOD_BRIDGE) return@on

            if (p.abilities.flying || p.isInLiquid || p.isPassenger || p.isFallFlying) {
                resetState()
                return@on
            }

            // Must be moving backwards or diagonally backwards to bridge
            val movingBack = Movement.moving() && (p.input.keyPresses.backward() || p.input.moveVector.y < 0f)

            if (!movingBack) {
                // If stopped moving for more than 25 ticks, reset activation so manual bridging is required again
                if (p.tickCount - lastActiveTick > 25) {
                    if (activationBlocks.value > 0) activated = false
                    placedCount = 0
                    bridgeBlocksPlaced = 0
                }
                jumpRequested = false
                vYaw = 0.0
                vPitch = 0.0
                return@on
            }
            lastActiveTick = p.tickCount

            // With manual aiming, preserve the pitch guard. Auto-angle puts the crosshair on the bridge itself,
            // so gating it before it can turn would deadlock.
            if (pitchCheck.value && autoAngle.value == AngleMode.NONE && p.xRot < minPitch.value) return@on

            if (requireRightClick.value && !(PlayerState.physical(mc.options.keyUse) || mc.options.keyUse.isDown)) return@on

            // Waiting for manual activation blocks to complete before taking over
            if (!activated) return@on

            if (!isValidBlock(p.mainHandItem)) {
                val found = Inventory.hotbarFind { isValidBlock(it) } ?: return@on
                Inventory.selectHotbar(found.slot)
                return@on
            }

            countPending(p, level)

            if (p.tickCount - lastPlaceTick < nextDelay) return@on
            if (p.isUsingItem) return@on

            val hit = mc.hitResult as? BlockHitResult ?: return@on
            if (hit.type != HitResult.Type.BLOCK) return@on
            if (!canBridgeOn(p, level, hit)) return@on

            // The real thing a right click would do: vanilla startUseItem on the current crosshair target.
            val target = hit.blockPos.relative(hit.direction)
            pending += target to p.tickCount
            lastPlaceTick = p.tickCount
            nextDelay = placeDelay.value + Rng.int(0, 1)
            (mc as MinecraftAccessor).horizon_startUseItem()

            // Counted as it is placed, so the jump for the interval's last block starts right away: a jump a
            // few ticks late has already carried the player past the block it needs to land on. A refused
            // placement takes the count back in countPending.
            if (target != lastPlacedPos) {
                lastPlacedPos = target
                bridgeBlocksPlaced++
                if (autoJump.value && bridgeBlocksPlaced >= jumpInterval.value) {
                    bridgeBlocksPlaced = 0
                    jumpRequested = true
                }
            }
        }

        // Camera steering on MouseTurnEvent: ordinary mouse-count turns, so sensitivity steps are respected.
        on<MouseTurnEvent> {
            val p = mc.player ?: return@on
            val level = mc.level ?: return@on
            val angle = autoAngle.value
            if (mode.value != Mode.GOD_BRIDGE || angle == AngleMode.NONE) return@on

            // Only steer when activated and actively bridging backwards
            if (!activated) return@on
            val movingBack = Movement.moving() && (p.input.keyPresses.backward() || p.input.moveVector.y < 0f)
            if (!movingBack) {
                vYaw = 0.0
                vPitch = 0.0
                return@on
            }
            if (requireRightClick.value && !(PlayerState.physical(mc.options.keyUse) || mc.options.keyUse.isDown)) {
                vYaw = 0.0
                vPitch = 0.0
                return@on
            }

            val now = System.nanoTime()
            val dt = if (lastNs == 0L) 0.0 else ((now - lastNs) / 1e9).coerceIn(0.0, 0.05)
            lastNs = now

            // Nearest diagonal / axis heading and the configured pitch. The camera never chases the bridge's edge:
            // the pitch only settles on the set angle, and placing is left to wherever the crosshair really is.
            val wantYaw = ((p.yRot / 45f).roundToInt() * 45f).toDouble()
            val wantPitch = targetPitch.value.toDouble()
            val errYaw = Mth.wrapDegrees((wantYaw - p.yRot).toFloat()).toDouble()
            val errPitch = wantPitch - p.xRot

            val f = mc.options.sensitivity().get() * 0.6 + 0.2
            val perCount = f * f * f * 8.0
            val step = perCount * 0.15

            if (angle == AngleMode.SMOOTH_DIAGONAL) {
                val maxSpeed = aimSpeed.value.toDouble()
                val stiffness = 160.0
                val damping = 0.85
                val c = 2.0 * sqrt(stiffness) * damping
                vYaw = (vYaw + (stiffness * errYaw - c * vYaw) * dt).coerceIn(-maxSpeed, maxSpeed)
                vPitch = (vPitch + (stiffness * errPitch - c * vPitch) * dt).coerceIn(-maxSpeed, maxSpeed)
                carryX += vYaw * dt / step
                carryY += vPitch * dt / step
            } else {
                // SNAP / HARD_LOCK: close the whole error at once, still in whole mouse counts.
                carryX += errYaw / step
                carryY += errPitch / step
            }
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
            val status = if (mode.value == Mode.GOD_BRIDGE && activationBlocks.value > 0 && !activated) {
                " [$placedCount/${activationBlocks.value}]"
            } else ""
            val text = "$totalBlocks blocks$status"
            val font = Fonts.medium8
            val w = font.width(text)
            val cx = mc.window.guiScaledWidth / 2f
            val cy = mc.window.guiScaledHeight / 2f + 16f
            val col = if (activated) {
                if (totalBlocks > 32) 0xFF55FF55.toInt() else if (totalBlocks > 10) 0xFFFFAA00.toInt() else 0xFFFF5555.toInt()
            } else {
                0xFFF5F5F7.toInt()
            }
            font.draw(text, cx - w / 2f, cy, col)
            font.flush()
        }
    }

    /**
     * Whether a right click on [hit] would extend the bridge: a solid, non-interactive block whose touching tile is
     * free, at or below the walking level and close to the feet, so the module never builds walls or towers up.
     */
    private fun canBridgeOn(p: Player, level: Level, hit: BlockHitResult): Boolean {
        val clicked = level.getBlockState(hit.blockPos)
        if (clicked.canBeReplaced()) return false
        val target = hit.blockPos.relative(hit.direction)
        if (!level.getBlockState(target).canBeReplaced()) return false
        if (hit.direction == Direction.DOWN) return false
        if (target.y.toDouble() >= p.y) return false
        if (target.y < p.blockY - 2) return false
        // Only click plain blocks, never a chest, door, crafting table etc. next to the bridge.
        if (clicked.getMenuProvider(level, hit.blockPos) != null) return false
        return true
    }

    /** Counts a sent use once its block shows up locally, and queues the auto-jump every [jumpInterval] blocks. */
    private fun countPending(p: Player, level: Level) {
        val iter = pending.iterator()
        while (iter.hasNext()) {
            val (target, tick) = iter.next()
            val age = p.tickCount - tick
            if (level.getBlockState(target).canBeReplaced()) {
                // The client predicts the block the moment it uses; if it's gone again the server refused it
                // (or it never went down), so it is retried on the very next tick.
                if (age >= 1) {
                    iter.remove()
                    lastPlaceTick = 0
                    nextDelay = 0
                    if (bridgeBlocksPlaced > 0) bridgeBlocksPlaced--
                    lastPlacedPos = null
                }
            } else if (age >= 3) {
                // Still there after a server round trip: it really was placed.
                iter.remove()
            }
        }
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
