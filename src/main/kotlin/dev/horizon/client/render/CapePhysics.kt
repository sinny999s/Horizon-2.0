package dev.horizon.client.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.math.Axis
import dev.horizon.client.module.Modules
import dev.horizon.client.module.cosmetic.Cape
import net.minecraft.client.Minecraft
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.player.Player
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.joml.Vector4f
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Full reverse-engineered WaveyCapes cloth physics & renderer.
 * 
 * Features:
 * - 16-point 3D Verlet integration cloth simulation (StickSimulation3d)
 * - Per-tick player velocity & body rotation movement projection
 * - Crouch, swimming, jump & fall damping
 * - Length constraint relaxation, self-clipping & body clipping prevention
 * - Angle-limiting arc smoothing (preventHardBend 20°)
 * - Natural sine wave wind ripple equations
 * - Continuous smooth multi-matrix mesh generation with averaged normal vectors (Phong-style smooth shading)
 * - Support for both Smooth Ribbon Mesh and Blocky Segmented styles
 */
object CapePhysics {
    const val PART_COUNT = 16

    private val simulations = ConcurrentHashMap<Int, StickSimulation3d>()
    private var lastTickTime = 0L

    // Reusable matrices & vectors for rendering to avoid GC churn
    private val positionMatrices = Array(PART_COUNT) { Matrix4f() }
    private val frontNormalVecs = Array(PART_COUNT) { Vector3f() }
    private val backNormalVecs = Array(PART_COUNT) { Vector3f() }

    private val NORMAL_ORIGIN = Vector3f()
    private val NORMAL_EDGE = Vector3f()
    private val NORMAL_FACE = Vector3f()
    private val NORMAL_TOP = Vector3f()
    private val NORMAL_BOTTOM = Vector3f()
    private val SCRATCH_VEC4 = Vector4f()

    /**
     * Called once per game tick to step the Verlet simulation for all active players.
     */
    fun tick(mc: Minecraft) {
        val capeModule = Modules.get<Cape>()
        if (!capeModule.enabled || !capeModule.waveyCapes.value) {
            if (simulations.isNotEmpty()) simulations.clear()
            return
        }

        val player = mc.player ?: return
        val level = mc.level ?: return

        updatePlayerSimulation(player)

        // Clean up simulations for players no longer in the level
        if (simulations.size > 20) {
            val validIds = HashSet<Int>()
            for (p in level.players()) validIds.add(p.id)
            simulations.keys.retainAll(validIds)
        }
    }

    private fun updatePlayerSimulation(player: Player) {
        val sim = simulations.computeIfAbsent(player.id) {
            StickSimulation3d().apply { init(PART_COUNT) }
        }
        if (sim.empty()) sim.init(PART_COUNT)

        val mc = Minecraft.getInstance()
        val delta = mc.deltaTracker.getGameTimeDeltaPartialTick(false)
        val avatarState = (player as? net.minecraft.client.player.AbstractClientPlayer)?.avatarState()
        val cloakX = avatarState?.getInterpolatedCloakX(delta) ?: player.x
        val cloakZ = avatarState?.getInterpolatedCloakZ(delta) ?: player.z

        // Exact WaveyCapes CapeHolder.simulate math:
        val d3 = cloakX - player.x
        val d5 = cloakZ - player.z
        val yBodyRot = player.yBodyRot
        val sinBody = sin(yBodyRot * 0.017453292f)
        val cosBody = -cos(yBodyRot * 0.017453292f)

        var heightMultiplier = 6f
        val strafeMultiplier = 2f
        if (player.isUnderWater) heightMultiplier *= 2f

        val jumpFall = ((player.yo - player.y) * 10.0).coerceIn(0.0, 1.0)
        val underWater = player.isUnderWater
        val baseGravity = 25f
        sim.gravity = if (underWater) baseGravity / 10f else baseGravity

        val yawRad = Math.toRadians(-player.yRot.toDouble()).toFloat()
        val strafe = cos(yawRad) * (player.x - player.xo) - sin(yawRad) * (player.z - player.zo)

        val motionX = (d3 * sinBody + d5 * cosBody) + jumpFall + (if (player.isCrouching && !sim.sneaking) 3.0 else 0.0)
        val motionY = (player.y - player.yo) * heightMultiplier + (if (player.isCrouching && !sim.sneaking) 1.0 else 0.0)
        val motionZ = -strafe * strafeMultiplier
        sim.sneaking = player.isCrouching

        var moveVec = Vector3f(motionX.toFloat(), motionY.toFloat(), motionZ.toFloat())
        var gravVec = Vector3f(0f, -1f, 0f)

        if (player.isVisuallySwimming) {
            val pitch = Math.toRadians((player.xRot + 90f).toDouble()).toFloat()
            val q = Quaternionf().rotateX(pitch)
            gravVec = q.transform(gravVec)
            moveVec = q.transform(moveVec)
        }

        sim.setGravityDirection(gravVec.x, gravVec.y, gravVec.z)
        sim.applyMovement(moveVec.x, moveVec.y, moveVec.z)
        sim.simulate()
    }

    /**
     * Intercepts vanilla CapeLayer rendering and draws the dynamic wavy mesh.
     */
    fun render(
        poseStack: PoseStack,
        collector: SubmitNodeCollector,
        lightCoords: Int,
        state: AvatarRenderState,
        textureId: Identifier,
        root: ModelPart,
        body: ModelPart
    ): Boolean {
        val capeModule = Modules.get<Cape>()
        if (!capeModule.enabled || !capeModule.waveyCapes.value) return false

        val mc = Minecraft.getInstance()
        if (state.id != (mc.player?.id ?: -1)) {
            return false
        }

        val player = mc.player
        val sim = simulations.computeIfAbsent(state.id) {
            StickSimulation3d().apply { init(PART_COUNT) }
        }
        if (sim.empty()) {
            sim.init(PART_COUNT)
            if (player != null) updatePlayerSimulation(player)
        }

        val delta = mc.deltaTracker.getGameTimeDeltaPartialTick(false)
        val underWater = player?.isUnderWater ?: false
        val renderType: RenderType = RenderTypes.entityCutout(textureId, false)

        // Setup base body coordinate space
        poseStack.pushPose()
        root.translateAndRotate(poseStack)
        body.translateAndRotate(poseStack)

        // Save base transformation into a scratch PoseStack to derive per-segment matrices
        val basePose = poseStack.last()
        val scratchStack = PoseStack()

        for (i in 0 until PART_COUNT) {
            scratchStack.last().set(basePose)
            modifyPoseStackSimulation(scratchStack, delta, i, sim, state, underWater)
            positionMatrices[i].set(scratchStack.last().pose())
        }

        // Calculate segment face normals and submit smooth geometry
        collector.submitCustomGeometry(poseStack, renderType) { _, consumer ->
            renderSmoothCape(consumer, lightCoords)
        }

        poseStack.popPose()
        return true
    }

    private fun modifyPoseStackSimulation(
        stack: PoseStack,
        delta: Float,
        part: Int,
        sim: StickSimulation3d,
        state: AvatarRenderState,
        underWater: Boolean
    ) {
        stack.pushPose()
        // Upper back offset (torso depth is 4 pixels, centered at 0: back is +2 pixels = 0.125 blocks)
        stack.translate(0.0, 0.0, 0.125)

        val p0X = sim.getLerpX(0, delta)
        val p0Y = sim.getLerpY(0, delta)
        val p0Z = sim.getLerpZ(0, delta)

        val piX = sim.getLerpX(part, delta)
        val piY = sim.getLerpY(part, delta)
        val piZ = sim.getLerpZ(part, delta)

        val dx = (piX - p0X).coerceAtMost(0f)
        val dy = p0Y - part.toFloat() - piY
        val dz = p0Z - piZ

        val rotation = getRotation(delta, part, sim)
        val timeDiv = if (underWater) 9L else 3L
        val time = (System.currentTimeMillis() / timeDiv) % 360L
        val progress = (part + 1).toFloat() / 16f
        val windSwing = (sin(Math.toRadians((progress * 360.0 - time))) * 3.0).toFloat()

        val sideLean = state.capeLean2 / 2f

        stack.rotate(Axis.XP.rotationDegrees(6f + windSwing))
        if (sideLean != 0f) {
            stack.rotate(Axis.ZP.rotationDegrees(sideLean))
            stack.rotate(Axis.YP.rotationDegrees(180f - sideLean))
        } else {
            stack.rotate(Axis.YP.rotationDegrees(180f))
        }

        stack.translate(-dz / 16f, dy / 16f, dx / 16f)
        stack.translate(0.0, 0.03, -0.03)
        stack.translate(0f, part.toFloat() / 16f, 0f)
        stack.rotate(Axis.XP.rotationDegrees(-rotation))
        stack.translate(0f, -part.toFloat() / 16f, 0f)
        stack.translate(0.0, -0.03, 0.03)
    }

    private fun getRotation(delta: Float, part: Int, sim: StickSimulation3d): Float {
        if (part == 15) {
            return getRotation(delta, 14, sim)
        }
        val x1 = sim.getLerpX(part, delta)
        val y1 = sim.getLerpY(part, delta)
        val x2 = sim.getLerpX(part + 1, delta)
        val y2 = sim.getLerpY(part + 1, delta)
        return (Math.toDegrees(atan2((x2 - x1).toDouble(), (y2 - y1).toDouble())) + 180.0).toFloat()
    }

    private fun renderSmoothCape(consumer: VertexConsumer, light: Int) {
        val uScale = 1f / 64f
        val vScale = 1f / 32f

        // Precompute normal vectors for all 16 segments
        for (i in 0 until PART_COUNT) {
            val prevIdx = (i - 1).coerceAtLeast(0)
            val nextIdx = (i + 1).coerceAtMost(15)

            // Front normal (facing outward from back)
            getNormalVec(
                positionMatrices[prevIdx], positionMatrices[prevIdx], positionMatrices[i],
                0.3125f, i * 0.0625f, -0.0625f,
                -0.3125f, i * 0.0625f, -0.0625f,
                0.3125f, (i + 1) * 0.0625f, -0.0625f,
                light == 15728880,
                frontNormalVecs[i]
            )

            // Back normal (facing player torso)
            getNormalVec(
                positionMatrices[prevIdx], positionMatrices[prevIdx], positionMatrices[i],
                0.3125f, (i + 1) * 0.0625f, 0f,
                -0.3125f, (i + 1) * 0.0625f, 0f,
                0.3125f, i * 0.0625f, 0f,
                light == 15728880,
                backNormalVecs[i]
            )
        }

        // Render each segment quad bridged between positionMatrices[i-1] and positionMatrices[i]
        for (i in 0 until PART_COUNT) {
            val prevIdx = (i - 1).coerceAtLeast(0)
            val nextIdx = (i + 1).coerceAtMost(15)

            val vTop = (1f + i) * vScale
            val vBottom = (1f + i + 1) * vScale

            // 1. Top Cap (only at segment 0)
            if (i == 0) {
                val uTopMin = 1f * uScale
                val uTopMax = 11f * uScale
                val vCapMin = 0f * vScale
                val vCapMax = 1f * vScale

                getNormalVec(
                    positionMatrices[0], positionMatrices[0], positionMatrices[0],
                    0.3125f, 0f, 0f,
                    -0.3125f, 0f, 0f,
                    0.3125f, 0f, 0.0625f,
                    light == 15728880,
                    NORMAL_FACE
                )

                addVertex(consumer, positionMatrices[0], 0.3125f, 0f, 0f, uTopMax, vCapMax, light, NORMAL_FACE)
                addVertex(consumer, positionMatrices[0], -0.3125f, 0f, 0f, uTopMin, vCapMax, light, NORMAL_FACE)
                addVertex(consumer, positionMatrices[0], -0.3125f, 0f, -0.0625f, uTopMin, vCapMin, light, NORMAL_FACE)
                addVertex(consumer, positionMatrices[0], 0.3125f, 0f, -0.0625f, uTopMax, vCapMin, light, NORMAL_FACE)
            }

            // 2. Bottom Cap (only at segment 15)
            if (i == 15) {
                val uBotMin = 11f * uScale
                val uBotMax = 21f * uScale
                val vBotMin = 0f * vScale
                val vBotMax = 1f * vScale

                getNormalVec(
                    positionMatrices[15], positionMatrices[15], positionMatrices[15],
                    0.3125f, 16 * 0.0625f, -0.0625f,
                    -0.3125f, 16 * 0.0625f, -0.0625f,
                    0.3125f, 16 * 0.0625f, 0f,
                    light == 15728880,
                    NORMAL_FACE
                )

                addVertex(consumer, positionMatrices[15], 0.3125f, 16 * 0.0625f, -0.0625f, uBotMax, vBotMin, light, NORMAL_FACE)
                addVertex(consumer, positionMatrices[15], -0.3125f, 16 * 0.0625f, -0.0625f, uBotMin, vBotMin, light, NORMAL_FACE)
                addVertex(consumer, positionMatrices[15], -0.3125f, 16 * 0.0625f, 0f, uBotMin, vBotMax, light, NORMAL_FACE)
                addVertex(consumer, positionMatrices[15], 0.3125f, 16 * 0.0625f, 0f, uBotMax, vBotMax, light, NORMAL_FACE)
            }

            // 3. Right side edge (-X = -0.3125f)
            val uRightMin = 0f * uScale
            val uRightMax = 1f * uScale

            getNormalVec(
                positionMatrices[i], positionMatrices[i], positionMatrices[prevIdx],
                -0.3125f, (i + 1) * 0.0625f, 0f,
                -0.3125f, (i + 1) * 0.0625f, -0.0625f,
                -0.3125f, i * 0.0625f, 0f,
                light == 15728880,
                NORMAL_FACE
            )

            addVertex(consumer, positionMatrices[i], -0.3125f, (i + 1) * 0.0625f, 0f, uRightMin, vBottom, light, NORMAL_FACE)
            addVertex(consumer, positionMatrices[i], -0.3125f, (i + 1) * 0.0625f, -0.0625f, uRightMax, vBottom, light, NORMAL_FACE)
            addVertex(consumer, positionMatrices[prevIdx], -0.3125f, i * 0.0625f, -0.0625f, uRightMax, vTop, light, NORMAL_FACE)
            addVertex(consumer, positionMatrices[prevIdx], -0.3125f, i * 0.0625f, 0f, uRightMin, vTop, light, NORMAL_FACE)

            // 4. Left side edge (+X = 0.3125f)
            val uLeftMin = 11f * uScale
            val uLeftMax = 12f * uScale

            getNormalVec(
                positionMatrices[i], positionMatrices[i], positionMatrices[prevIdx],
                0.3125f, (i + 1) * 0.0625f, -0.0625f,
                0.3125f, (i + 1) * 0.0625f, 0f,
                0.3125f, i * 0.0625f, -0.0625f,
                light == 15728880,
                NORMAL_FACE
            )

            addVertex(consumer, positionMatrices[i], 0.3125f, (i + 1) * 0.0625f, -0.0625f, uLeftMin, vBottom, light, NORMAL_FACE)
            addVertex(consumer, positionMatrices[i], 0.3125f, (i + 1) * 0.0625f, 0f, uLeftMax, vBottom, light, NORMAL_FACE)
            addVertex(consumer, positionMatrices[prevIdx], 0.3125f, i * 0.0625f, 0f, uLeftMax, vTop, light, NORMAL_FACE)
            addVertex(consumer, positionMatrices[prevIdx], 0.3125f, i * 0.0625f, -0.0625f, uLeftMin, vTop, light, NORMAL_FACE)

            // 5. Front face (outside facing away from player, z = -0.0625f)
            val uBackMin = 1f * uScale
            val uBackMax = 11f * uScale

            average(frontNormalVecs[i], frontNormalVecs[prevIdx], NORMAL_TOP)
            average(frontNormalVecs[i], frontNormalVecs[nextIdx], NORMAL_BOTTOM)

            addVertex(consumer, positionMatrices[prevIdx], 0.3125f, i * 0.0625f, -0.0625f, uBackMax, vTop, light, NORMAL_TOP)
            addVertex(consumer, positionMatrices[prevIdx], -0.3125f, i * 0.0625f, -0.0625f, uBackMin, vTop, light, NORMAL_TOP)
            addVertex(consumer, positionMatrices[i], -0.3125f, (i + 1) * 0.0625f, -0.0625f, uBackMin, vBottom, light, NORMAL_BOTTOM)
            addVertex(consumer, positionMatrices[i], 0.3125f, (i + 1) * 0.0625f, -0.0625f, uBackMax, vBottom, light, NORMAL_BOTTOM)

            // 6. Back face (inside facing player torso, z = 0f)
            val uFrontMin = 12f * uScale
            val uFrontMax = 22f * uScale

            average(backNormalVecs[i], backNormalVecs[prevIdx], NORMAL_TOP)
            average(backNormalVecs[i], backNormalVecs[nextIdx], NORMAL_BOTTOM)

            addVertex(consumer, positionMatrices[prevIdx], 0.3125f, i * 0.0625f, 0f, uFrontMin, vTop, light, NORMAL_TOP)
            addVertex(consumer, positionMatrices[prevIdx], -0.3125f, i * 0.0625f, 0f, uFrontMax, vTop, light, NORMAL_TOP)
            addVertex(consumer, positionMatrices[i], -0.3125f, (i + 1) * 0.0625f, 0f, uFrontMax, vBottom, light, NORMAL_BOTTOM)
            addVertex(consumer, positionMatrices[i], 0.3125f, (i + 1) * 0.0625f, 0f, uFrontMin, vBottom, light, NORMAL_BOTTOM)
        }
    }

    private fun renderBlockyCape(consumer: VertexConsumer, light: Int) {
        val uScale = 1f / 64f
        val vScale = 1f / 32f

        for (i in 0 until PART_COUNT) {
            val mat = positionMatrices[i]
            val y1 = i * 0.0625f
            val y2 = (i + 1) * 0.0625f
            val v1 = (1f + i) * vScale
            val v2 = (1f + i + 1) * vScale

            // Front face (outside)
            addVertex(consumer, mat, 0.3125f, y1, -0.0625f, 11f * uScale, v1, light, 0f, 0f, -1f)
            addVertex(consumer, mat, -0.3125f, y1, -0.0625f, 1f * uScale, v1, light, 0f, 0f, -1f)
            addVertex(consumer, mat, -0.3125f, y2, -0.0625f, 1f * uScale, v2, light, 0f, 0f, -1f)
            addVertex(consumer, mat, 0.3125f, y2, -0.0625f, 11f * uScale, v2, light, 0f, 0f, -1f)

            // Back face (inside)
            addVertex(consumer, mat, 0.3125f, y1, 0f, 12f * uScale, v1, light, 0f, 0f, 1f)
            addVertex(consumer, mat, -0.3125f, y1, 0f, 22f * uScale, v1, light, 0f, 0f, 1f)
            addVertex(consumer, mat, -0.3125f, y2, 0f, 22f * uScale, v2, light, 0f, 0f, 1f)
            addVertex(consumer, mat, 0.3125f, y2, 0f, 12f * uScale, v2, light, 0f, 0f, 1f)

            // Right side
            addVertex(consumer, mat, -0.3125f, y2, 0f, 0f * uScale, v2, light, -1f, 0f, 0f)
            addVertex(consumer, mat, -0.3125f, y2, -0.0625f, 1f * uScale, v2, light, -1f, 0f, 0f)
            addVertex(consumer, mat, -0.3125f, y1, -0.0625f, 1f * uScale, v1, light, -1f, 0f, 0f)
            addVertex(consumer, mat, -0.3125f, y1, 0f, 0f * uScale, v1, light, -1f, 0f, 0f)

            // Left side
            addVertex(consumer, mat, 0.3125f, y2, -0.0625f, 11f * uScale, v2, light, 1f, 0f, 0f)
            addVertex(consumer, mat, 0.3125f, y2, 0f, 12f * uScale, v2, light, 1f, 0f, 0f)
            addVertex(consumer, mat, 0.3125f, y1, 0f, 12f * uScale, v1, light, 1f, 0f, 0f)
            addVertex(consumer, mat, 0.3125f, y1, -0.0625f, 11f * uScale, v1, light, 1f, 0f, 0f)

            if (i == 0) {
                // Top cap
                addVertex(consumer, mat, 0.3125f, 0f, 0f, 11f * uScale, 1f * vScale, light, 0f, -1f, 0f)
                addVertex(consumer, mat, -0.3125f, 0f, 0f, 1f * uScale, 1f * vScale, light, 0f, -1f, 0f)
                addVertex(consumer, mat, -0.3125f, 0f, -0.0625f, 1f * uScale, 0f, light, 0f, -1f, 0f)
                addVertex(consumer, mat, 0.3125f, 0f, -0.0625f, 11f * uScale, 0f, light, 0f, -1f, 0f)
            }
            if (i == 15) {
                // Bottom cap
                addVertex(consumer, mat, 0.3125f, 16 * 0.0625f, -0.0625f, 21f * uScale, 0f, light, 0f, 1f, 0f)
                addVertex(consumer, mat, -0.3125f, 16 * 0.0625f, -0.0625f, 11f * uScale, 0f, light, 0f, 1f, 0f)
                addVertex(consumer, mat, -0.3125f, 16 * 0.0625f, 0f, 11f * uScale, 1f * vScale, light, 0f, 1f, 0f)
                addVertex(consumer, mat, 0.3125f, 16 * 0.0625f, 0f, 21f * uScale, 1f * vScale, light, 0f, 1f, 0f)
            }
        }
    }

    private fun addVertex(
        consumer: VertexConsumer,
        mat: Matrix4f,
        x: Float, y: Float, z: Float,
        u: Float, v: Float,
        light: Int,
        normal: Vector3f
    ) {
        consumer.addVertex(mat, x, y, z)
            .setColor(255, 255, 255, 255)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(normal.x, normal.y, normal.z)
    }

    private fun addVertex(
        consumer: VertexConsumer,
        mat: Matrix4f,
        x: Float, y: Float, z: Float,
        u: Float, v: Float,
        light: Int,
        nx: Float, ny: Float, nz: Float
    ) {
        consumer.addVertex(mat, x, y, z)
            .setColor(255, 255, 255, 255)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(nx, ny, nz)
    }

    private fun getNormalVec(
        m1: Matrix4f, m2: Matrix4f, m3: Matrix4f,
        x1: Float, y1: Float, z1: Float,
        x2: Float, y2: Float, z2: Float,
        x3: Float, y3: Float, z3: Float,
        invert: Boolean,
        dest: Vector3f
    ): Vector3f {
        transform(m1, x1, y1, z1, NORMAL_ORIGIN)
        transform(m2, x2, y2, z2, dest)
        transform(m3, x3, y3, z3, NORMAL_EDGE)

        dest.sub(NORMAL_ORIGIN)
        NORMAL_EDGE.sub(NORMAL_ORIGIN)
        dest.cross(NORMAL_EDGE)
        dest.normalize()

        if (invert) dest.mul(-1f)
        return dest
    }

    private fun transform(m: Matrix4f, x: Float, y: Float, z: Float, dest: Vector3f): Vector3f {
        SCRATCH_VEC4.set(x, y, z, 1f)
        m.transform(SCRATCH_VEC4)
        dest.set(SCRATCH_VEC4.x, SCRATCH_VEC4.y, SCRATCH_VEC4.z)
        return dest
    }

    private fun average(a: Vector3f, b: Vector3f, dest: Vector3f): Vector3f {
        dest.set((a.x + b.x) / 2f, (a.y + b.y) / 2f, (a.z + b.z) / 2f)
        return dest
    }

    /**
     * Complete 16-point 3D Verlet physics stick simulation reverse-engineered from WaveyCapes.
     */
    class StickSimulation3d {
        private var x = FloatArray(0)
        private var y = FloatArray(0)
        private var z = FloatArray(0)
        private var prevX = FloatArray(0)
        private var prevY = FloatArray(0)
        private var prevZ = FloatArray(0)

        private var gravityX = 0f
        private var gravityY = -1f
        private var gravityZ = 0f

        var gravity = 25f
        var sneaking = false

        private var dirX = 0f
        private var dirY = 0f
        private var dirZ = 0f

        fun empty(): Boolean = x.size < 2

        fun init(count: Int): Boolean {
            if (x.size == count) return false
            x = FloatArray(count)
            y = FloatArray(count)
            z = FloatArray(count)
            prevX = FloatArray(count)
            prevY = FloatArray(count)
            prevZ = FloatArray(count)

            for (i in 0 until count) {
                y[i] = -i.toFloat()
                prevY[i] = -i.toFloat()
                x[i] = -i.toFloat()
                prevX[i] = -i.toFloat()
                z[i] = 0f
                prevZ[i] = 0f
            }
            return true
        }

        fun getLerpX(index: Int, delta: Float): Float {
            val pX = prevX[index]
            return pX + (x[index] - pX) * delta
        }

        fun getLerpY(index: Int, delta: Float): Float {
            val pY = prevY[index]
            return pY + (y[index] - pY) * delta
        }

        fun getLerpZ(index: Int, delta: Float): Float {
            val pZ = prevZ[index]
            return pZ + (z[index] - pZ) * delta
        }

        fun setGravityDirection(gx: Float, gy: Float, gz: Float) {
            gravityX = gx
            gravityY = gy
            gravityZ = gz
        }

        fun applyMovement(mx: Float, my: Float, mz: Float) {
            prevX[0] = x[0]
            prevY[0] = y[0]
            prevZ[0] = z[0]
            x[0] += mx
            y[0] += my
            z[0] += mz
        }

        fun simulate() {
            applyGravity()
            preventClipping()
            preventSelfClipping(32)
            applyMotion()
            preventSelfClipping(32)
            for (i in 1 until (x.size - 2)) {
                preventHardBend(i, 20f)
            }
            limitLength()
        }

        private fun applyGravity() {
            val g = gravity * 0.05f
            for (i in 1 until x.size) {
                prevX[i] = x[i]
                prevY[i] = y[i]
                prevZ[i] = z[i]
                x[i] += gravityX * g
                y[i] += gravityY * g
                z[i] += gravityZ * g
            }
        }

        private fun preventClipping() {
            for (i in 1 until x.size) {
                if (x[i] - x[0] > 0f) {
                    x[i] = x[0]
                }
                val frac = i.toFloat() / x.size.toFloat()
                val maxZ = frac * frac * 5.0f
                val dZ = z[0] - z[i]
                if (dZ > maxZ) {
                    z[i] = z[0] - maxZ
                }
                if (dZ < -maxZ) {
                    z[i] = z[0] + maxZ
                }
            }
        }

        private fun direction(i: Int, j: Int) {
            val dx = x[i] - x[j]
            val dy = y[i] - y[j]
            val dz = z[i] - z[j]
            val len = sqrt(dx * dx + dy * dy + dz * dz)
            if (len < 1.0e-4f) {
                dirX = 0f
                dirY = 0f
                dirZ = 0f
            } else {
                dirX = dx / len
                dirY = dy / len
                dirZ = dz / len
            }
        }

        private fun separate(i: Int, j: Int) {
            val mx = (x[i] + x[j]) / 2f
            val my = (y[i] + y[j]) / 2f
            val mz = (z[i] + z[j]) / 2f
            x[i] = mx + dirX / 2f
            y[i] = my + dirY / 2f
            z[i] = mz + dirZ / 2f
            x[j] = mx - dirX / 2f
            y[j] = my - dirY / 2f
            z[j] = mz - dirZ / 2f
        }

        private fun applyMotion() {
            for (iter in 0 until 3) {
                for (i in (x.size - 2) downTo 0) {
                    direction(i, i + 1)
                    separate(i, i + 1)
                }
            }
        }

        private fun preventSelfClipping(maxIterations: Int) {
            var count = 0
            do {
                var changed = false
                for (i in 0 until x.size) {
                    for (j in (i + 1) until x.size) {
                        val dx = x[i] - x[j]
                        val dy = y[i] - y[j]
                        val dz = z[i] - z[j]
                        if ((dx * dx + dy * dy + dz * dz) < 0.99f) {
                            changed = true
                            count++
                            direction(i, j)
                            separate(i, j)
                        }
                    }
                }
            } while (changed && count < maxIterations)
        }

        private fun preventHardBend(i: Int, maxAngle: Float) {
            val d1x = x[i - 1] - x[i]
            val d1y = y[i - 1] - y[i]
            val d2x = x[i - 1] - x[i + 1]
            val d2y = y[i - 1] - y[i + 1]
            val dot = d1x * d2x + d1y * d2y
            val cross = d1x * d2y - d1y * d2x
            val angle = Math.toDegrees(atan2(cross.toDouble(), dot.toDouble()))
            if (angle < -maxAngle) {
                swing(i, -maxAngle * 2f)
            }
            if (angle > maxAngle) {
                swing(i, maxAngle * 2f)
            }
        }

        private fun swing(i: Int, angleDeg: Float) {
            val dx = x[i] - x[i - 1]
            val dy = y[i] - y[i - 1]
            val dz = z[i] - z[i - 1]
            val rad = Math.toRadians(angleDeg.toDouble()).toFloat()
            val c = cos(rad)
            val s = sin(rad)
            x[i + 1] = x[i] + (c * dx - s * dy)
            y[i + 1] = y[i] + (s * dx + c * dy)
            z[i + 1] = z[i] + dz
        }

        private fun limitLength() {
            for (i in 0 until (x.size - 1)) {
                direction(i, i + 1)
                x[i + 1] = x[i] - dirX
                y[i + 1] = y[i] - dirY
                z[i + 1] = z[i] - dirZ
            }
        }
    }
}
