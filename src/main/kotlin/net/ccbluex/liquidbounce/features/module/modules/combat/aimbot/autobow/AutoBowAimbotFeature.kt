package net.ccbluex.liquidbounce.features.module.modules.combat.aimbot.autobow

import net.ccbluex.liquidbounce.config.types.ToggleableConfigurable
import net.ccbluex.liquidbounce.event.events.OverlayRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.modules.combat.aimbot.ModuleAutoBow
import net.ccbluex.liquidbounce.render.renderEnvironmentForGUI
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.RotationsConfigurable
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.aiming.projectiles.SituationalProjectileAngleCalculator
import net.ccbluex.liquidbounce.utils.combat.TargetPriority
import net.ccbluex.liquidbounce.utils.combat.TargetTracker
import net.ccbluex.liquidbounce.utils.entity.PositionExtrapolation
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.ccbluex.liquidbounce.utils.render.OverlayTargetRenderer
import net.ccbluex.liquidbounce.utils.render.trajectory.TrajectoryData
import net.minecraft.item.BowItem
import net.minecraft.item.TridentItem
import net.minecraft.util.math.Vec3d

/**
 * Automatically shoots with your bow when you aim correctly at an enemy or when the bow is fully charged.
 */
object AutoBowAimbotFeature : ToggleableConfigurable(ModuleAutoBow, "BowAimbot", true) {

    /**
     * Scales how aggressively we predict target movement (1.0 = default, <1 under-predict, >1 over-predict)
     */
    val predictionFactor by float("PredictionFactor", 1.0F, 0.0F..2.0F, suffix = "x")

    // Target
    val targetTracker = TargetTracker(TargetPriority.DISTANCE)

    // Rotation
    val rotationConfigurable = RotationsConfigurable(this)

    init {
        tree(targetTracker)
        tree(rotationConfigurable)
    }

    private val targetRenderer = tree(OverlayTargetRenderer(ModuleAutoBow))

    @Suppress("unused")
    private val tickRepeatable = tickHandler {
        targetTracker.reset()

        // Should check if player is using bow
        val activeItem = player.activeItem?.item
        if (activeItem !is BowItem && activeItem !is TridentItem) {
            return@tickHandler
        }

        val projectileInfo = TrajectoryData.getRenderedTrajectoryInfo(
            player,
            activeItem,
            true
        ) ?: return@tickHandler

        var rotation: Rotation? = null
        targetTracker.selectFirst { enemy ->
            // Create a scaled extrapolation to adjust prediction
            val baseExtrap = PositionExtrapolation.getBestForEntity(enemy)
            val factor = predictionFactor.toDouble()
            val scaledExtrap = object : PositionExtrapolation {
                override fun getPositionInTicks(ticks: Double): Vec3d =
                    baseExtrap.getPositionInTicks(ticks * factor)
            }
            // Calculate aim rotation using scaled prediction
            rotation = SituationalProjectileAngleCalculator.calculateAngleFor(
                projectileInfo,
                player.eyePos,
                scaledExtrap,
                enemy.dimensions
            )
            rotation != null
        } ?: return@tickHandler

        RotationManager.setRotationTarget(
            rotation!!,
            priority = Priority.IMPORTANT_FOR_USAGE_1,
            provider = ModuleAutoBow,
            configurable = rotationConfigurable
        )
    }

    @Suppress("unused")
    private val renderHandler = handler<OverlayRenderEvent> { event ->
        val target = targetTracker.target ?: return@handler

        renderEnvironmentForGUI {
            targetRenderer.render(this, target, event.tickDelta)
        }
    }

}
