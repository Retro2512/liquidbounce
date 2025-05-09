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
@file:Suppress("UnusedPrivateProperty")
package net.ccbluex.liquidbounce.features.module.modules.render.esp.modes

object EspGlowMode : EspMode("Glow", requiresTrueSight = true) {
    private val seeInvis by boolean("See Invis", false)
    private val normalGlowAlpha by int("Glow Alpha", 100, 0..255)
    private val invisGlowAlpha by int("Invisible Glow Alpha", 70, 0..255)
}
