package dev.horizon.client.module.attack

import dev.horizon.client.combat.Packets
import dev.horizon.client.combat.PlayerState
import dev.horizon.client.combat.Rotation
import dev.horizon.client.combat.Scheduler
import dev.horizon.client.config.Friends
import dev.horizon.client.module.Modules
import dev.horizon.client.module.basic.Sprint
import dev.horizon.client.module.rage.MaceElytraTarget
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket
import net.minecraft.tags.FluidTags
import net.minecraft.util.Mth
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.animal.Animal
import net.minecraft.world.entity.animal.feline.Cat
import net.minecraft.world.entity.animal.wolf.Wolf
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.monster.Enderman
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin
import net.minecraft.world.entity.npc.villager.Villager
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.entity.projectile.ShulkerBullet
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.EnchantmentHelper
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.level.block.AirBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LiquidBlock
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private val mc get() = Minecraft.getInstance()

/** The original's `vt`: Aura's target finder. */
class AuraTargets(private val aura: Aura) {
    /** The original's `vt.i`: keeps the previous target while it stays valid (Distance and Health sorts only). */
    fun select(previous: Entity?): Entity? {
        val best = findBest()
        if (previous != null && aura.sort.value != Aura.Sort.FOV) {
            if (best !is Projectile && !isInvalid(previous)) return previous
        }
        return best
    }

    private fun findBest(): Entity? {
        val p = mc.player ?: return null
        val level = mc.level ?: return null
        val list = ArrayList<LivingEntity>()
        for (e in level.entitiesForRendering()) {
            if ((e is ShulkerBullet || e is LargeFireball) && e.isAlive) {
                if (inRange(e) && aura.projectiles.value) return e
            }
            if (isInvalid(e)) continue
            if (e is LivingEntity) list += e
        }
        return when (aura.sort.value) {
            Aura.Sort.Distance -> list.minByOrNull { p.distanceToSqr(it.position()) }
            Aura.Sort.FOV -> list.minByOrNull { fovMetric(it) }
            Aura.Sort.Health -> list.minByOrNull { it.health + it.absorptionAmount }
        }
    }

    private fun fovMetric(e: Entity): Float {
        val p = mc.player!!
        return abs(Mth.wrapDegrees(Math.toDegrees(atan2(e.z - p.z, e.x - p.x)) - 90.0) - Mth.wrapDegrees(p.yRot)).toFloat()
    }

    /** The original's `vt.B`. */
    fun searchRange(): Float {
        val p = mc.player!!
        var r = aura.range.value + 2.0f
        if (p.isFallFlying || Modules.get<MaceElytraTarget>().enabled) r += 64.0f
        return if (aura.effectiveRotation().extendsSearch(aura)) r else aura.range.value
    }

    /** From the cached eyes to the aim point of the rotation-mode setting (not the effective mode, as in the original). */
    private fun inRange(e: Entity): Boolean =
        PlayerState.eye.distanceTo(AuraRotation.of(aura.rotationMode.value).aimPoint(aura, e)) - searchRange() <= 0

    private fun isValidProjectile(e: Entity) =
        (e is ShulkerBullet || e is LargeFireball) && e.isAlive && PlayerState.eye.distanceTo(e.position()) - searchRange() < 0 &&
            aura.projectiles.value

    /** The original's `vt.T`. */
    fun isInvalid(e: Entity): Boolean {
        val p = mc.player ?: return true
        if (isValidProjectile(e)) return false
        if (e !is LivingEntity) return true
        if (e.isDeadOrDying || !e.isAlive) return true
        if (e is ArmorStand || e is Cat) return true
        if (e.tickCount < 0) return true
        val fov = if (aura.fov.value == 1) 180 else aura.fov.value
        if (!inFov(e.position(), fov)) return true
        if (excludedByType(e)) return true
        if (e is Player) {
            if (e === p) return true
            if (Friends.isFriend(e.gameProfile.name)) return true
            if (e.isCreative && aura.ignoreCreative.value) return true
            if (e.armorValue == 0 && aura.ignoreNaked.value) return true
            if (e.isInvisible && aura.ignoreInvisibleEntities.value) return true
            if (e.teamColor == p.teamColor && aura.ignoreTeam.value && p.teamColor != 0xFFFFFF) return true
        }
        if (!inRange(e)) return true
        if (e.hasCustomName() && aura.ignoreNamed.value) return true
        // Legit only fights who it can see, whatever ignore-walls says, and lets go as they duck behind cover.
        return aura.effectiveRotation() === LegitRotation && !visible(e)
    }

    /**
     * Not in the original: some part of [e] (head, middle or feet) can be seen from the eyes with no block in the way, glass
     * included (the hit's own check stops at block outlines too).
     */
    private fun visible(e: Entity): Boolean {
        val p = mc.player ?: return false
        val level = mc.level ?: return false
        val eye = p.eyePosition
        val b = e.boundingBox
        val c = b.center
        for (y in doubleArrayOf(b.maxY - 0.1, c.y, b.minY + 0.15)) {
            val hit = level.clip(ClipContext(eye, Vec3(c.x, y, c.z), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p))
            if (hit.type == HitResult.Type.MISS) return true
        }
        return false
    }

    private fun inFov(pos: Vec3, fov: Int): Boolean {
        val cam = mc.gameRenderer.mainCamera()
        val cp = cam.position()
        val d = Mth.wrapDegrees(Mth.wrapDegrees(Math.toDegrees(atan2(pos.z - cp.z, pos.x - cp.x)) - 90.0).toFloat() - Mth.wrapDegrees(cam.yRot()))
        return abs(d) - fov <= 0
    }

    private fun excludedByType(e: Entity): Boolean {
        if (e is Monster && !aura.hostiles.value) return true
        if (aura.onlyAngryHostiles.value) {
            if (e is Enderman && !e.isCreepy) return true
            if (e is ZombifiedPiglin && !e.isAggressive) return true
            if (e is Wolf && !e.isAggressive) return true
        }
        if (e is Player && !aura.players.value) return true
        if (e is Player && aura.antibot.value && isBot(e)) return true
        if (e is Villager && !aura.villagers.value) return true
        if (e is Mob && !aura.mobs.value) return true
        if (e is Animal) return !aura.animals.value
        return false
    }

    /** Full, fresh, unenchanted armour with leather or iron in it, an empty off-hand and something in the main hand. */
    private fun isBot(p: Player): Boolean {
        if (p === mc.player) return false
        val armor = listOf(p.inventory.getItem(39), p.inventory.getItem(38), p.inventory.getItem(37), p.inventory.getItem(36))
        return armor.all { it.item != Items.AIR && !it.isDamaged } && armor.all { it.isEnchantable } && p.offhandItem.item == Items.AIR &&
            armor.any { it.item in BOT_ARMOR } && p.mainHandItem.item != Items.AIR
    }

    private companion object {
        val BOT_ARMOR = setOf(Items.LEATHER_BOOTS, Items.LEATHER_LEGGINGS, Items.LEATHER_CHESTPLATE, Items.LEATHER_HELMET,
            Items.IRON_BOOTS, Items.IRON_LEGGINGS, Items.IRON_CHESTPLATE, Items.IRON_HELMET)
    }
}

/** The original's `co`: when Aura may hit next. */
class AttackTimer(private val aura: Aura) : AuraHook {
    /** Ticks until the next hit. */
    var countdown = 0

    /** The original's `co.E`: the last value [post] gave [countdown] (elytra-target compares the two). */
    var scheduled = 0
    private var counter = 0

    override fun tick() {
        countdown -= 1
    }

    /** The original's `co.R(n)`: due within [n] ticks and the crit timing allows it (or old-delay). */
    fun ready(n: Int): Boolean = (critGate(n) || aura.oldDelay.value) && countdown - n <= 0

    private fun critGate(n: Int): Boolean {
        val p = mc.player ?: return false
        val level = mc.level ?: return false
        val jump = mc.options.keyJump.isDown
        val airOk: Boolean = run {
            if (p.onGround()) return@run !jump
            var fd = PlayerState.fallDistance
            if (!(fd > 0.0)) {
                if (p.deltaMovement.y - 0.0031 >= 0) return@run !jump
                if (n == 0) return@run !jump
                fd = PlayerState.fallDistance
            }
            if (fd < 1.13) return@run true
            if (fd > 1.146) return@run true
            !jump
        }
        if (!p.isInWater) {
            val noCrit = p.hasEffect(MobEffects.BLINDNESS) || p.hasEffect(MobEffects.LEVITATION) || p.isEyeInFluid(FluidTags.WATER) ||
                (p.isInLava && p.isEyeInFluid(FluidTags.LAVA)) || touching(Blocks.COBWEB) || touching(Blocks.SWEET_BERRY_BUSH) ||
                p.isSuppressingSlidingDownLadder || p.abilities.flying
            return noCrit || airOk
        }
        val feet = BlockPos.containing(p.position())
        val atSurface = !p.isSwimming && level.getBlockState(feet).block is LiquidBlock && level.getBlockState(feet.above()).block is AirBlock
        val notSurfaceOrFlying = !atSurface || p.abilities.flying
        if (!p.isSwimming && !jump) return true
        return notSurfaceOrFlying || airOk
    }

    private fun touching(b: Block): Boolean {
        val p = mc.player ?: return false
        val level = mc.level ?: return false
        val c = BlockPos.containing(p.position())
        for (x in -2..2) for (y in -1..4) for (z in -2..2) {
            val pos = c.offset(x, y, z)
            if (p.boundingBox.intersects(AABB(pos)) && level.getBlockState(pos).block == b) return true
        }
        return false
    }

    /** The original's `co.r`: schedule the next hit. */
    override fun post(rotation: Rotation) {
        val tA = (20.0f / aura.minCps.value).toInt() - 1
        val tB = (20.0f / aura.maxCps.value).toInt() - 1
        val lo = min(tA, tB)
        val hi = max(tA, tB)
        counter++
        countdown = if (aura.oldDelay.value) {
            ((lo..hi).random(Random.Default) + sin((System.currentTimeMillis() % 100) / 100.0 * 2 * PI) * 1.5 + 1.5).toInt().coerceIn(lo, hi)
        } else {
            baseDelay()
        }
        scheduled = countdown
    }

    /** The original's `co.A`: the weapon's cooldown in ticks (at least 10), with the randomize-delay modes. */
    private fun baseDelay(): Int {
        val p = mc.player ?: return 10
        val level = mc.level ?: return 10
        val mace = Modules.get<MaceElytraTarget>()
        if (mace.enabled && mace.windBurstSync.value) {
            val windBurst = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.WIND_BURST)
            if (EnchantmentHelper.getItemEnchantmentLevel(windBurst, p.mainHandItem) > 0) return 10
        }
        val aspd = p.getAttributeValue(Attributes.ATTACK_SPEED)
        var t = max((1.0 / aspd * 18.0).toInt(), 10)
        if (aspd == 4.0 || aspd in 4.4..4.401 || aspd in 4.8..4.801) t = 10
        val standing = p.onGround() && !mc.options.keyJump.isDown
        if (aura.splashWhileOnGround.value && standing) t = 12
        val extra = if (standing) 1 else 2
        return when (aura.randomizeDelay.value) {
            Aura.RandomizeDelay.OFF -> t
            Aura.RandomizeDelay.DEFAULT -> if (counter % 7 == 3) t + extra else t
            Aura.RandomizeDelay.VULCAN -> if (counter % 5 == 0) (if (standing) 14 else 13) else if (counter % 7 == 3) t + extra else t
            Aura.RandomizeDelay.LEGIT -> legitDelay(t)
        }
    }

    /**
     * Not in the original: hits spaced like a person clicking: mostly as the cooldown fills or a tick or two after, now and
     * then a little slower, rarely a tick early, and once in a while a longer pause.
     */
    private fun legitDelay(t: Int): Int {
        val r = Random.nextFloat()
        return when {
            r < 0.07f -> t - 1
            r < 0.80f -> t + (Random.nextDouble() * 1.6 + Random.nextDouble() * 1.2).toInt()
            r < 0.96f -> t + 2 + Random.nextInt(3)
            else -> t + 5 + Random.nextInt(4)
        }
    }
}

/** The original's `h0`: sprint resets around Aura's hits. */
class SprintHook(private val aura: Aura, private val timer: AttackTimer, private val offhand: OffhandSwapHook) : AuraHook {
    /** A legit sprint reset is running: sprinting is held off. */
    var resetting = false
    private var packetSprint = false

    private fun swapPending() = aura.offhandHitSwap.value && offhand.find() != -1

    /** The original's `h0.U`: may start a legit reset (then this tick doesn't hit). */
    fun gate(ready: Boolean): Boolean {
        val p = mc.player ?: return ready
        val mode = aura.sprint.value
        val legitExtra = mode == Aura.Sprint.LEGIT_EXTRA
        if (timer.ready(1) && p.deltaMovement.y - (-0.07) < 0) {
            val trigger = p.isSprinting || (swapPending() && mc.options.keyJump.isDown)
            val modeOk = mode == Aura.Sprint.LEGIT || legitExtra || swapPending()
            if (trigger && modeOk) {
                if (legitExtra) PlayerState.releaseMovementKeys()
                p.isSprinting = false
                Modules.get<Sprint>().skipOnce = true
                mc.options.keySprint.isDown = false
                resetting = true
                offhand.schedule()
                Scheduler.ticks(3) { restore(legitExtra) }
                return false
            }
        }
        return ready
    }

    private fun restore(legitExtra: Boolean) {
        resetting = false
        mc.options.keySprint.isDown = PlayerState.physical(mc.options.keySprint)
        if (legitExtra) PlayerState.resyncMovementKeys()
    }

    /** NORMAL / HVH: an airborne hit while the server thinks we sprint gets a stop-sprinting packet first. */
    override fun pre() {
        val p = mc.player ?: return
        if (!(PlayerState.serverSprinting && !p.isSwimming)) return
        if (aura.offhandHitSwap.value) return
        if (aura.sprint.value != Aura.Sprint.NORMAL && aura.sprint.value != Aura.Sprint.HVH) return
        if (p.onGround()) return
        packetSprint = true
        p.isSprinting = false
        Packets.send(ServerboundPlayerCommandPacket(p, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING))
    }

    override fun post(rotation: Rotation) {
        val p = mc.player ?: return
        if (packetSprint) {
            p.isSprinting = true
            Packets.send(ServerboundPlayerCommandPacket(p, ServerboundPlayerCommandPacket.Action.START_SPRINTING))
            packetSprint = false
        }
        resetting = false
        val m = aura.sprint.value
        if (m == Aura.Sprint.LEGIT_EXTRA || m == Aura.Sprint.LEGIT || swapPending()) {
            if (m == Aura.Sprint.LEGIT_EXTRA) PlayerState.resyncMovementKeys()
            p.isSprinting = PlayerState.physical(mc.options.keyUp)
        }
    }
}
