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
package net.ccbluex.liquidbounce.features.module.modules.render.esp.modes

import net.ccbluex.liquidbounce.event.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.render.esp.ModuleESP.getColor
import net.ccbluex.liquidbounce.render.BoxRenderer
import net.ccbluex.liquidbounce.render.renderEnvironmentForWorld
import net.ccbluex.liquidbounce.render.withPositionRelativeToCamera
import net.ccbluex.liquidbounce.utils.entity.RenderedEntities
import net.ccbluex.liquidbounce.utils.entity.interpolateCurrentPosition
import net.minecraft.util.math.Box
import net.minecraft.client.MinecraftClient
import net.minecraft.entity.player.PlayerEntity
import net.ccbluex.liquidbounce.render.utils.rainbow

object EspOutlineMode : EspMode("Outline", requiresTrueSight = true) {

    private val seeInvis by boolean("See Invis", false)
    private val normalOutlineAlpha by int("Outline Alpha", 100, 0..255)
    private val invisOutlineAlpha by int("Invisible Outline Alpha", 70, 0..255)

    @Suppress("unused")
    private val renderHandler = handler<WorldRenderEvent> { event ->
        val matrixStack = event.matrixStack
        val entitiesWithBoxes = RenderedEntities.filter { !it.isInvisible }.map { entity ->
            val dims = entity.getDimensions(entity.pose)
            val d = dims.width.toDouble() / 2.0
            entity to Box(-d, 0.0, -d, d, dims.height.toDouble(), d)
        }.toMutableList()
        if (seeInvis) {
            val world = MinecraftClient.getInstance().world ?: return@handler
            world.entities.filterIsInstance<PlayerEntity>()
                .filter { it.isInvisible }
                .forEach { entity ->
                    val dims = entity.getDimensions(entity.pose)
                    val d = dims.width.toDouble() / 2.0
                    entitiesWithBoxes += entity to Box(-d, 0.0, -d, d, dims.height.toDouble(), d)
                }
        }

        renderEnvironmentForWorld(matrixStack) {
            BoxRenderer.drawWith(this) {
                entitiesWithBoxes.forEach { (entity, box) ->
                    val colorRaw = if (entity.isInvisible) rainbow() else getColor(entity)
                    val alpha = if (entity.isInvisible) invisOutlineAlpha else normalOutlineAlpha
                    val outlineColor = colorRaw.with(a = alpha)
                    val fillColor = colorRaw.with(a = 0)
                    val pos = entity.interpolateCurrentPosition(event.partialTicks)

                    withPositionRelativeToCamera(pos) {
                        drawBox(
                            box,
                            fillColor,
                            outlineColor
                        )
                    }
                }
            }
        }
    }
}
