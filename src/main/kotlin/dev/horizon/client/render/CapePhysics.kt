package dev.horizon.client.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import dev.horizon.client.module.cosmetic.Cape
import dev.horizon.client.module.Modules
import net.minecraft.client.Minecraft
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.Identifier
import org.joml.Matrix4f
import org.joml.Quaternionf
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * High-performance multi-segment cloth physics and smooth 3D ribbon mesh for Capes.
 * Divides the cape into horizontal ribbon segments that swing, flap, and ripple dynamically
 * based on player movement, velocity, jumping, wind, and body posture (sneaking/swimming).
 */
object CapePhysics {
    const val SEGMENTS = 16
    private const val CAPE_WIDTH = 10f / 16f
    private const val CAPE_HEIGHT = 16f / 16f
    private const val SEGMENT_HEIGHT = CAPE_HEIGHT / SEGMENTS
    private const val CAPE_DEPTH = 1f / 16f

    fun render(
        poseStack: PoseStack,
        collector: SubmitNodeCollector,
        lightCoords: Int,
        state: AvatarRenderState,
        textureId: Identifier,
        body: ModelPart
    ): Boolean {
        val capeModule = Modules.get<Cape>()
        if (!capeModule.enabled || !capeModule.physics.value) return false

        val mc = Minecraft.getInstance()
        if (capeModule.onlySelf.value && state.id != (mc.player?.id ?: -1)) {
            return false
        }

        // Base lean from entity movement state
        val baseAngleX = (6.0f + state.capeLean / 2.0f + state.capeFlap) * (PI.toFloat() / 180f)
        val sideLeanZ = (state.capeLean2 / 2.0f) * (PI.toFloat() / 180f)

        // Wave animation reacting to movement speed
        val waveSpeed = if (state.walkAnimationSpeed > 0.05f) 8.5f else 2.5f
        val waveAmplitude = if (state.walkAnimationSpeed > 0.05f) 0.14f * capeModule.waveStrength.value else 0.04f * capeModule.waveStrength.value
        val animTime = state.ageInTicks * 0.1f * waveSpeed

        // Calculate segment angles and node positions down the ribbon
        val nodeY = FloatArray(SEGMENTS + 1)
        val nodeZ = FloatArray(SEGMENTS + 1)
        val segAngles = FloatArray(SEGMENTS)
        nodeY[0] = 0f
        nodeZ[0] = 0f

        for (i in 0 until SEGMENTS) {
            val progress = i.toFloat() / SEGMENTS
            val wave = sin(animTime - i * 0.45f) * waveAmplitude * (progress + 0.35f)
            val gravityFactor = (1f - progress * 0.35f) * capeModule.gravity.value
            val segmentLean = baseAngleX * (1f + progress * 0.45f) * capeModule.stiffness.value

            val angle = (segmentLean + wave + (gravityFactor * 0.04f)).coerceIn(-0.3f, 1.7f)
            segAngles[i] = angle
            nodeY[i + 1] = nodeY[i] + cos(angle) * SEGMENT_HEIGHT
            nodeZ[i + 1] = nodeZ[i] + sin(angle) * SEGMENT_HEIGHT
        }

        val renderType: RenderType = RenderTypes.entityCutout(textureId, false)

        poseStack.pushPose()
        // Align to the torso so the cape follows crouching/swimming and body transforms
        body.translateAndRotate(poseStack)
        // Position at the upper back surface (torso depth is 4 pixels: center is 0, back is +2 pixels)
        poseStack.translate(0f, 0f, 2.01f / 16f)
        if (sideLeanZ != 0f) {
            poseStack.rotate(Quaternionf().rotateZ(sideLeanZ))
        }

        collector.submitCustomGeometry(poseStack, renderType) { pose, consumer ->
            renderSegmentedMesh(pose, consumer, nodeY, nodeZ, segAngles, lightCoords)
        }

        poseStack.popPose()
        return true
    }

    private fun renderSegmentedMesh(
        pose: PoseStack.Pose,
        consumer: VertexConsumer,
        nodeY: FloatArray,
        nodeZ: FloatArray,
        segAngles: FloatArray,
        light: Int
    ) {
        val mat = pose.pose()
        val halfW = CAPE_WIDTH / 2f
        val depth = CAPE_DEPTH

        // Cape texture coords (64x32 standard Mojang cape map):
        // Back face (outside visible behind player): u [1..11]/64, v [1..17]/32
        // Front face (inside facing back of player): u [12..22]/64, v [1..17]/32
        // Sides: Left u [11..12]/64, Right u [0..1]/64
        // Caps: Top u [1..11]/64, v [0..1]/32, Bottom u [11..21]/64, v [0..1]/32
        val uBackMin = 1f / 64f
        val uBackMax = 11f / 64f
        val uFrontMin = 12f / 64f
        val uFrontMax = 22f / 64f
        val uRightMin = 0f / 64f
        val uRightMax = 1f / 64f
        val uLeftMin = 11f / 64f
        val uLeftMax = 12f / 64f

        val vMin = 1f / 32f
        val vMax = 17f / 32f
        val vStep = (vMax - vMin) / SEGMENTS

        // Top cap on segment 0
        quad(consumer, pose, mat,
            -halfW, 0f, 0f, uBackMin, 0f / 32f,
            halfW, 0f, 0f, uBackMax, 0f / 32f,
            halfW, 0f, depth, uBackMax, 1f / 32f,
            -halfW, 0f, depth, uBackMin, 1f / 32f,
            0f, -1f, 0f, light)

        // Bottom cap on last segment
        val lastY = nodeY[SEGMENTS]
        val lastZ = nodeZ[SEGMENTS]
        quad(consumer, pose, mat,
            -halfW, lastY, lastZ + depth, 11f / 64f, 1f / 32f,
            halfW, lastY, lastZ + depth, 21f / 64f, 1f / 32f,
            halfW, lastY, lastZ, 21f / 64f, 0f / 32f,
            -halfW, lastY, lastZ, 11f / 64f, 0f / 32f,
            0f, 1f, 0f, light)

        // Draw each horizontal segment
        for (i in 0 until SEGMENTS) {
            val y1 = nodeY[i]
            val z1 = nodeZ[i]
            val y2 = nodeY[i + 1]
            val z2 = nodeZ[i + 1]
            val v1 = vMin + i * vStep
            val v2 = vMin + (i + 1) * vStep
            val angle = segAngles[i]
            val normalY = -sin(angle)
            val normalZ = cos(angle)

            // Back face (facing backward towards viewer, CCW winding)
            quad(consumer, pose, mat,
                -halfW, y1, z1 + depth, uBackMin, v1,
                -halfW, y2, z2 + depth, uBackMin, v2,
                halfW, y2, z2 + depth, uBackMax, v2,
                halfW, y1, z1 + depth, uBackMax, v1,
                0f, normalY, normalZ, light)

            // Front face (facing player torso, CCW winding)
            quad(consumer, pose, mat,
                halfW, y1, z1, uFrontMin, v1,
                halfW, y2, z2, uFrontMin, v2,
                -halfW, y2, z2, uFrontMax, v2,
                -halfW, y1, z1, uFrontMax, v1,
                0f, -normalY, -normalZ, light)

            // Left side edge (+X)
            quad(consumer, pose, mat,
                halfW, y1, z1 + depth, uLeftMin, v1,
                halfW, y2, z2 + depth, uLeftMin, v2,
                halfW, y2, z2, uLeftMax, v2,
                halfW, y1, z1, uLeftMax, v1,
                1f, 0f, 0f, light)

            // Right side edge (-X)
            quad(consumer, pose, mat,
                -halfW, y1, z1, uRightMin, v1,
                -halfW, y2, z2, uRightMin, v2,
                -halfW, y2, z2 + depth, uRightMax, v2,
                -halfW, y1, z1 + depth, uRightMax, v1,
                -1f, 0f, 0f, light)
        }
    }

    private fun quad(
        consumer: VertexConsumer,
        pose: PoseStack.Pose,
        mat: Matrix4f,
        x1: Float, y1: Float, z1: Float, u1: Float, v1: Float,
        x2: Float, y2: Float, z2: Float, u2: Float, v2: Float,
        x3: Float, y3: Float, z3: Float, u3: Float, v3: Float,
        x4: Float, y4: Float, z4: Float, u4: Float, v4: Float,
        nx: Float, ny: Float, nz: Float,
        light: Int
    ) {
        val overlay = OverlayTexture.NO_OVERLAY
        consumer.addVertex(mat, x1, y1, z1).setColor(255, 255, 255, 255).setUv(u1, v1).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz)
        consumer.addVertex(mat, x2, y2, z2).setColor(255, 255, 255, 255).setUv(u2, v2).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz)
        consumer.addVertex(mat, x3, y3, z3).setColor(255, 255, 255, 255).setUv(u3, v3).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz)
        consumer.addVertex(mat, x4, y4, z4).setColor(255, 255, 255, 255).setUv(u4, v4).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz)
    }
}
