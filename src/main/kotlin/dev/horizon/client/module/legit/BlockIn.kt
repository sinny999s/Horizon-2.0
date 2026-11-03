package dev.horizon.client.module.legit

import dev.horizon.client.combat.Inventory
import dev.horizon.client.combat.Placer
import dev.horizon.client.combat.PlaceBoxes
import dev.horizon.client.combat.PlayerState
import dev.horizon.client.combat.Rotation
import dev.horizon.client.combat.bindHeld
import dev.horizon.client.event.MouseTurnEvent
import dev.horizon.client.event.Render3DEvent
import dev.horizon.client.event.TickEvent
import dev.horizon.client.module.Animation
import dev.horizon.client.module.Categories
import dev.horizon.client.module.Module
import dev.horizon.client.module.Render
import dev.horizon.client.render.Theme
import dev.horizon.client.setting.Bind
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.BlockItem
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Not in the original: Legit Block-In module designed for BedWars / SkyWars / PvP.
 * When enabled and the trigger bind is held (or while active), it uses smooth humanized legit rotations
 * to smoothly look at valid block faces surrounding and above the player, switching to building blocks and
 * placing them with full client-side sensitivity quantization to encase you in a safe defensive box.
 */
class BlockIn : Module("block-in", Categories.LEGIT, listOf("box-in", "self-box", "auto-block-in")) {
    val speed = float("speed", 260.0f, 60.0f..600.0f)
    val triggerBind = bind("trigger-bind", Bind())
    val onlyWhileHeld = bool("only-while-held", true)
    val placeDelay = int("place-delay", 1, 0..5)
    val autoDisable = bool("auto-disable", true)
    val prioritizeFloor = bool("prioritize-floor", true)
    val coverHead = bool("cover-head", true)

    private var targetHit: BlockHitResult? = null
    private var targetPos: BlockPos? = null
    private var vYaw = 0.0
    private var vPitch = 0.0
    private var carryX = 0.0
    private var carryY = 0.0
    private var lastNs = 0L
    private var delayTicks = 0
    private var wasHeld = false

    override fun onEnable() = resetState()
    override fun onDisable() = resetState()

    private fun resetState() {
        targetHit = null
        targetPos = null
        vYaw = 0.0
        vPitch = 0.0
        carryX = 0.0
        carryY = 0.0
        lastNs = 0L
        delayTicks = 0
        wasHeld = false
    }

    private fun isTriggerActive(): Boolean {
        if (triggerBind.value.key == -1) return true
        val held = bindHeld(triggerBind.value)
        return if (onlyWhileHeld.value) held else (held || wasHeld)
    }

    init {
        on<TickEvent> {
            val p = mc.player ?: return@on
            val level = mc.level ?: return@on
            if (mc.gui.screen() != null) return@on

            if (triggerBind.value.key != -1 && bindHeld(triggerBind.value)) {
                wasHeld = true
            }

            if (!isTriggerActive()) {
                targetHit = null
                targetPos = null
                return@on
            }

            if (delayTicks > 0) delayTicks--

            val feet = BlockPos.containing(p.position())
            val head = feet.above()
            val targets = ArrayList<BlockPos>()

            // 1. Floor if player is jumping or floor is air
            if (prioritizeFloor.value && level.getBlockState(feet.below()).canBeReplaced()) {
                targets += feet.below()
            }

            // 2. Four sides around feet
            for (d in HORIZONTALS) {
                val side = feet.relative(d)
                if (level.getBlockState(side).canBeReplaced()) targets += side
            }

            // 3. Four sides around head
            for (d in HORIZONTALS) {
                val side = head.relative(d)
                if (level.getBlockState(side).canBeReplaced()) targets += side
            }

            // 4. Roof over head
            if (coverHead.value && level.getBlockState(head.above()).canBeReplaced()) {
                targets += head.above()
            }

            if (targets.isEmpty()) {
                targetHit = null
                targetPos = null
                if (autoDisable.value && !onlyWhileHeld.value) disable()
                return@on
            }

            // Find best placeable target
            var bestHit: BlockHitResult? = null
            var bestPos: BlockPos? = null
            var bestDist = Double.MAX_VALUE
            val eye = p.eyePosition

            for (pos in targets) {
                val hit = Placer.findHit(pos, -1, dev.horizon.client.module.Interact.Strict, dev.horizon.client.combat.IgnoreMode.ONLY_CRYSTALS, 4.5f, 4.5f, emptyList())
                    ?: Placer.findHit(pos, -1, dev.horizon.client.module.Interact.Vanilla, dev.horizon.client.combat.IgnoreMode.ONLY_CRYSTALS, 4.5f, 4.5f, emptyList())
                    ?: continue
                val d = eye.distanceToSqr(hit.location)
                if (d < bestDist) {
                    bestDist = d
                    bestHit = hit
                    bestPos = pos
                }
            }

            targetHit = bestHit
            targetPos = bestPos

            // If we are looking close enough at the target face, place!
            val hit = targetHit ?: return@on
            val bp = targetPos ?: return@on
            if (delayTicks <= 0 && isAimingNear(p, hit.location)) {
                val blockSlot = Inventory.hotbarFind { it.item is BlockItem }
                if (blockSlot != null) {
                    val prev = p.inventory.selectedSlot
                    if (prev != blockSlot.slot) Inventory.selectHotbar(blockSlot.slot)
                    mc.gameMode?.useItemOn(p, InteractionHand.MAIN_HAND, hit)
                    PlaceBoxes.add(bp, Theme.primary or (0xFF shl 24), Theme.primary or (0x44 shl 24), Animation.Fade, Render.BOTH)
                    delayTicks = placeDelay.value
                }
            }
        }

        on<MouseTurnEvent> {
            val p = mc.player ?: return@on
            val hit = targetHit ?: return@on
            val now = System.nanoTime()
            val dt = if (lastNs == 0L) 0.0 else ((now - lastNs) / 1e9).coerceIn(0.0, 0.05)
            lastNs = now

            val eye = p.eyePosition
            val aim = hit.location
            val dx = aim.x - eye.x
            val dz = aim.z - eye.z
            val wantYaw = Math.toDegrees(atan2(dz, dx)) - 90.0
            val wantPitch = -Math.toDegrees(atan2(aim.y - eye.y, hypot(dx, dz)))
            val errYaw = Mth.wrapDegrees(wantYaw - p.yRot)
            val errPitch = wantPitch - p.xRot

            val maxSpeed = speed.value.toDouble()
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

        on<Render3DEvent> {
            val bp = targetPos ?: return@on
            val hit = targetHit ?: return@on
            PlaceBoxes.add(bp, Theme.primary or (0xFF shl 24), Theme.primary or (0x22 shl 24), Animation.Static, Render.BOTH)
        }
    }

    private fun isAimingNear(p: net.minecraft.world.entity.player.Player, to: Vec3): Boolean {
        val eye = p.eyePosition
        val dx = to.x - eye.x
        val dz = to.z - eye.z
        val wantYaw = Math.toDegrees(atan2(dz, dx)) - 90.0
        val wantPitch = -Math.toDegrees(atan2(to.y - eye.y, hypot(dx, dz)))
        val dy = abs(Mth.wrapDegrees(wantYaw - p.yRot))
        val dp = abs(wantPitch - p.xRot)
        return dy < 16.0 && dp < 16.0
    }

    private companion object {
        val HORIZONTALS = listOf(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST)
    }
}
