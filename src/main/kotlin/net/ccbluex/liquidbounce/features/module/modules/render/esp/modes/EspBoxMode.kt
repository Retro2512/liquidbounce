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

object EspBoxMode : EspMode("Box") {

    private val outline by boolean("Outline", true)
    private val expand by float("Expand", 0.05f, 0f..0.5f)
    private val seeInvis by boolean("See Invis", false)
    private val normalFillAlpha by int("Normal Fill Alpha", 50, 0..255)
    private val normalOutlineAlpha by int("Normal Outline Alpha", 100, 0..255)
    private val invisFillAlpha by int("Invisible Fill Alpha", 30, 0..255)
    private val invisOutlineAlpha by int("Invisible Outline Alpha", 70, 0..255)

    @Suppress("unused")
    private val renderHandler = handler<WorldRenderEvent> { event ->
        val matrixStack = event.matrixStack
        val entitiesWithBoxes = RenderedEntities.filter { !it.isInvisible }.map { entity ->
            val dimensions = entity.getDimensions(entity.pose)
            val d = dimensions.width.toDouble() / 2.0
            val box = Box(
                -d, 0.0, -d,
                d, dimensions.height.toDouble(), d
            ).expand(expand.toDouble())
            entity to box
        }.toMutableList()
        if (seeInvis) {
            val world = MinecraftClient.getInstance().world ?: return@handler
            world.entities
                .filterIsInstance<PlayerEntity>()
                .filter { it.isInvisible }
                .forEach { entity ->
                    val dimensions = entity.getDimensions(entity.pose)
                    val d = dimensions.width.toDouble() / 2.0
                    val box = Box(
                        -d, 0.0, -d,
                        d, dimensions.height.toDouble(), d
                    ).expand(expand.toDouble())
                    entitiesWithBoxes += entity to box
                }
        }

        renderEnvironmentForWorld(matrixStack) {
            BoxRenderer.Companion.drawWith(this) {
                entitiesWithBoxes.forEach { (entity, box) ->
                    val pos = entity.interpolateCurrentPosition(event.partialTicks)
                    val color = getColor(entity)

                    val fillAlpha = if (entity.isInvisible) invisFillAlpha else normalFillAlpha
                    val outlineAlpha = if (entity.isInvisible) invisOutlineAlpha else normalOutlineAlpha
                    val baseColor = color.with(a = fillAlpha)
                    val outlineColor = color.with(a = outlineAlpha)

                    withPositionRelativeToCamera(pos) {
                        drawBox(
                            box,
                            baseColor,
                            outlineColor.takeIf { outline }
                        )
                    }
                }
            }
        }
    }

}
