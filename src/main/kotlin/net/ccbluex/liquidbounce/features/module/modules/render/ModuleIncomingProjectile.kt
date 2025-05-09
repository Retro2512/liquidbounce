/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2025 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */

@file:Suppress("MaxLineLength", "NewLineAtEndOfFile")

package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.client.chat
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.entity.rotation
import net.ccbluex.liquidbounce.utils.entity.prevPos
import net.ccbluex.liquidbounce.utils.entity.box
import net.ccbluex.liquidbounce.render.renderEnvironmentForWorld
import net.ccbluex.liquidbounce.render.BoxRenderer
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.render.trajectory.TrajectoryData
import net.ccbluex.liquidbounce.utils.render.trajectory.TrajectoryInfoRenderer
import net.minecraft.client.network.AbstractClientPlayerEntity
import net.minecraft.client.sound.PositionedSoundInstance
import net.minecraft.entity.projectile.AbstractFireballEntity
import net.minecraft.item.BowItem
import net.minecraft.sound.SoundEvents
import net.minecraft.util.Formatting
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import java.util.UUID
import net.minecraft.util.hit.EntityHitResult
import net.minecraft.world.RaycastContext

object ModuleIncomingProjectile : ClientModule("IncomingProjectile", Category.RENDER, aliases = arrayOf("IncomingProjectile")) {
    private val aimVicinity by float("AimVicinity", 15.0f, 1.0f..90.0f, "°")
    private val playSound by boolean("PlaySound", true)
    private val highlightExplosionBlocks by boolean("HighlightExplosionBlocks", false)
    private val predictionHighlightRange by float("PredictionHighlightRange", 30.0f, 5.0f..100.0f, "m")
    private val detectionRange by float("DetectionRange", 30.0f, 1.0f..200.0f, "m")

    private val announcedBow = mutableSetOf<UUID>()
    private val announcedFireball = mutableSetOf<UUID>()
    private val predictedExplosions = mutableMapOf<UUID, List<BlockPos>>()

    private fun getPlayerTeamColorPrefix(player: AbstractClientPlayerEntity): String {
        return player.scoreboardTeam?.color?.toString() ?: ""
    }

    @Suppress("unused")
    val tickHandler = handler<GameTickEvent> {
        // Bow aiming detection
        world.players.forEach { other ->
            if (other == player || other !is AbstractClientPlayerEntity) return@forEach
            val id = other.uuid
            val itemInHand = other.mainHandStack.item
            if (itemInHand is BowItem && other.itemUseTime > 0) {
                val rotationToPlayer = Rotation.lookingAt(point = player.eyePos, from = other.eyePos)
                val angle = other.rotation.angleTo(rotationToPlayer)
                if (angle <= aimVicinity) {
                    if (announcedBow.add(id)) {
                        val prefix = getPlayerTeamColorPrefix(other)
                        val reset = Formatting.RESET.toString()
                        chat("$prefix${other.gameProfile.name}$reset is aiming at you")
                        if (playSound) {
                            mc.soundManager.play(
                                PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_PLING, 0.5f)
                            )
                        }
                    }
                } else {
                    announcedBow.remove(id)
                }
            } else {
                announcedBow.remove(id)
            }
        }

        // Fireball detection and prediction
        world.entities.filterIsInstance<AbstractFireballEntity>().forEach { fireball ->
            val id = fireball.uuid
            // detect approaching fireball via trajectory simulation
            if (fireball.pos.distanceTo(player.pos) > detectionRange) {
                announcedFireball.remove(id)
                return@forEach
            }
            val trajectoryInfo = TrajectoryData.getRenderTrajectoryInfoForOtherEntity(fireball, false, true)
            if (trajectoryInfo == null) {
                announcedFireball.remove(id)
                return@forEach
            }
            val renderer = TrajectoryInfoRenderer(
                owner = fireball,
                velocity = fireball.velocity,
                pos = fireball.pos,
                trajectoryInfo = trajectoryInfo,
                renderOffset = Vec3d.ZERO
            )
            val hitResult = renderer.runSimulation(200)
            val impactVec = when (hitResult) {
                is BlockHitResult -> Vec3d.ofCenter(hitResult.blockPos)
                is EntityHitResult -> hitResult.entity.pos
                else -> null
            }
            if (impactVec == null || impactVec.distanceTo(player.pos) > detectionRange) {
                announcedFireball.remove(id)
                return@forEach
            }
            val losHit = world.raycast(
                RaycastContext(
                    impactVec,
                    player.eyePos,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
                    fireball
                )
            )
            if (losHit.type == HitResult.Type.MISS || (losHit is EntityHitResult && losHit.entity == player)) {
                if (announcedFireball.add(id)) {
                    chat("Incoming fireball")
                    if (playSound) {
                        mc.soundManager.play(
                            PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_PLING, 0.5f)
                        )
                    }
                }
            } else {
                announcedFireball.remove(id)
            }

            // predict explosion blocks
            if (highlightExplosionBlocks) {
                val trajectoryInfo = TrajectoryData.getRenderTrajectoryInfoForOtherEntity(fireball, false, true)
                if (trajectoryInfo != null) {
                    val renderer = TrajectoryInfoRenderer(
                        owner = fireball,
                        velocity = fireball.velocity,
                        pos = fireball.pos,
                        trajectoryInfo = trajectoryInfo,
                        renderOffset = Vec3d.ZERO
                    )
                    val hit = renderer.runSimulation(200)
                    if (hit is BlockHitResult) {
                        val impactPos = hit.blockPos
                        val distanceToPlayer = player.pos.distanceTo(Vec3d.ofCenter(impactPos))

                        if (distanceToPlayer <= predictionHighlightRange) {
                            val explosionBlocks = mutableListOf<BlockPos>()
                            val explosionRadius = 1 // For a 3x3x3 cube

                            for (dx in -explosionRadius..explosionRadius) {
                                for (dy in -explosionRadius..explosionRadius) {
                                    for (dz in -explosionRadius..explosionRadius) {
                                        val currentPos = impactPos.add(dx, dy, dz)
                                        if (!world.getBlockState(currentPos).isAir) {
                                            explosionBlocks.add(currentPos)
                                        }
                                    }
                                }
                            }
                            if (explosionBlocks.isNotEmpty()) {
                                predictedExplosions[id] = explosionBlocks
                            } else {
                                predictedExplosions.remove(id) // No non-air blocks to highlight
                            }
                        } else {
                            predictedExplosions.remove(id) // Outside highlight range
                        }
                    } else {
                        predictedExplosions.remove(id) // No block hit
                    }
                } else {
                    predictedExplosions.remove(id) // No trajectory info
                }
            } else {
                predictedExplosions.clear()
            }
        }

        // cleanup for removed fireballs
        predictedExplosions.keys.removeIf { uuid -> world.entities.none { it.uuid == uuid } }
    }

    @Suppress("unused")
    val renderHandler = handler<WorldRenderEvent> { event ->
        if (!highlightExplosionBlocks) return@handler
        renderEnvironmentForWorld(event.matrixStack) {
            for (blockList in predictedExplosions.values) {
                for (pos in blockList) {
                    BoxRenderer.drawWith(this) {
                        val box = Box(
                            pos.x.toDouble(),
                            pos.y.toDouble(),
                            pos.z.toDouble(),
                            pos.x + 1.0,
                            pos.y + 1.0,
                            pos.z + 1.0
                        )
                        drawBox(box, Color4b(255, 0, 0, 50), Color4b(255, 0, 0, 200))
                    }
                }
            }
        }
    }

    override fun disable() {
        announcedBow.clear()
        announcedFireball.clear()
        predictedExplosions.clear()
        super.disable()
    }
} 