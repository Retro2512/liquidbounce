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
package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.client.chat
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.entity.rotation
import net.ccbluex.liquidbounce.config.types.ToggleableConfigurable
import net.minecraft.client.network.AbstractClientPlayerEntity
import net.minecraft.client.sound.PositionedSoundInstance
import net.minecraft.item.BowItem
import net.minecraft.item.FireChargeItem
import net.minecraft.item.Items
import net.minecraft.scoreboard.Team
import net.minecraft.util.Formatting
import net.minecraft.sound.SoundEvents
import java.util.UUID
import kotlin.math.asin
import kotlin.math.sqrt
import net.ccbluex.liquidbounce.utils.item.getPotionEffects
import net.minecraft.entity.effect.StatusEffects

/**
 * Projectile Notifier module
 *
 * Shows a chat message and plays a sound when another player holds certain items.
 */
object ModuleItemNotifier : ClientModule("ItemNotifier", Category.RENDER, aliases = arrayOf("Notifier")) {

    // Define ToggleableConfigurable for items that will have a proximity sub-setting
    private object FireballNotifier : ToggleableConfigurable(this, "Fireball", true) {
        val proximity by boolean("Proximity", false)
        val sound by boolean("Sound", true)
    }
    private object BowNotifier : ToggleableConfigurable(this, "Bow", true) {
        val proximity by boolean("Proximity", false)
        val sound by boolean("Sound", true)
    }
    private object PearlNotifier : ToggleableConfigurable(this, "Pearl", true) {
        val proximity by boolean("Proximity", false)
        val sound by boolean("Sound", true)
    }
    private object StickNotifier : ToggleableConfigurable(this, "Stick", true) {
        val proximity by boolean("Proximity", false)
        val sound by boolean("Sound", true)
    }
    private object InvisibilityNotifier : ToggleableConfigurable(this, "InvisibilityPotion", true) {
        val proximity by boolean("Proximity", false)
        val sound by boolean("Sound", true)
    }

    // Standard item toggles for non-projectile items
    private val obsidian by boolean("Obsidian", true)
    private val diamondSword by boolean("DiamondSword", true)
    private val ironSword by boolean("IronSword", true)
    private val obsidianSound by boolean("ObsidianSound", true)
    private val diamondSwordSound by boolean("DiamondSwordSound", true)
    private val ironSwordSound by boolean("IronSwordSound", true)

    private val announcedFireball = mutableSetOf<UUID>()
    private val announcedBow = mutableSetOf<UUID>()
    private val announcedPearl = mutableSetOf<UUID>()
    private val announcedObsidian = mutableSetOf<UUID>()
    private val announcedDiamondSword = mutableSetOf<UUID>()
    private val announcedIronSword = mutableSetOf<UUID>()
    private val announcedStick = mutableSetOf<UUID>()
    private val announcedInvisibility = mutableSetOf<UUID>()

    private val proximityDistance by float("ProximityDistance", 7.0f, 0.0f..200.0f, "m")
    private val aimVicinity by float("AimVicinity", 15.0f, 1.0f..90.0f, "°")
    private val playSound by boolean("PlaySound", true)
    private val notificationCooldown by float("NotificationCooldown", 5.0f, 0.0f..60.0f, "s")
    private val lastAnnounceTimes = mutableMapOf<Pair<UUID, String>, Long>()

    init {
        tree(FireballNotifier)
        tree(BowNotifier)
        tree(PearlNotifier)
        tree(StickNotifier)
        tree(InvisibilityNotifier)
    }

    private fun getPlayerTeamColorPrefix(playerEntity: AbstractClientPlayerEntity): String {
        val team = playerEntity.scoreboardTeam
        return team?.color?.toString() ?: ""
    }

    @Suppress("unused")
    val tickHandler = handler<GameTickEvent> {
        world.players.forEach { otherPlayer ->
            if (otherPlayer == player || otherPlayer !is AbstractClientPlayerEntity) return@forEach

            val id = otherPlayer.uuid
            val itemInHand = otherPlayer.mainHandStack.item
            val playerName = otherPlayer.gameProfile.name
            val teamColorPrefix = getPlayerTeamColorPrefix(otherPlayer)
            val resetColor = Formatting.RESET.toString()

            // Helper function for proximity logic, now used per item
            fun checkProximityConditions(): Boolean {
                if (proximityDistance == 0.0f) return true // If distance is 0, proximity/aim is bypassed

                val distance = player.distanceTo(otherPlayer)
                val rotationToPlayer = Rotation.lookingAt(point = player.eyePos, from = otherPlayer.eyePos)
                val angleToPlayer = otherPlayer.rotation.angleTo(rotationToPlayer)
                return distance <= proximityDistance || angleToPlayer <= aimVicinity
            }

            // Fireball
            if (FireballNotifier.enabled && itemInHand is FireChargeItem) {
                var announce = true
                if (FireballNotifier.proximity) {
                    if (!checkProximityConditions()) {
                        announce = false
                    }
                }
                if (announce) {
                    if (shouldNotify(id, "Fireball")) {
                        chat("$teamColorPrefix$playerName$resetColor is holding Fireball")
                        if (playSound && FireballNotifier.sound) {
                            mc.soundManager.play(
                                PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_PLING, 0.7f)
                            )
                        }
                    }
                } else {
                    announcedFireball.remove(id)
                }
            } else {
                announcedFireball.remove(id)
            }

            // Bow
            if (BowNotifier.enabled && itemInHand is BowItem) {
                var announce = true
                if (BowNotifier.proximity) {
                    if (!checkProximityConditions()) {
                        announce = false
                    }
                }
                if (announce) {
                    if (shouldNotify(id, "Bow")) {
                        chat("$teamColorPrefix$playerName$resetColor is holding Bow")
                        if (playSound && BowNotifier.sound) {
                            mc.soundManager.play(
                                PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_PLING, 0.8f)
                            )
                        }
                    }
                } else {
                    announcedBow.remove(id)
                }
            } else {
                announcedBow.remove(id)
            }

            // Pearl
            if (PearlNotifier.enabled && itemInHand == Items.ENDER_PEARL) {
                var announce = true
                if (PearlNotifier.proximity) {
                    if (!checkProximityConditions()) {
                        announce = false
                    }
                }
                if (announce) {
                    if (shouldNotify(id, "Pearl")) {
                        chat("$teamColorPrefix$playerName$resetColor is holding Pearl")
                        if (playSound && PearlNotifier.sound) {
                            mc.soundManager.play(
                                PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_PLING, 1.0f)
                            )
                        }
                    }
                } else {
                    announcedPearl.remove(id)
                }
            } else {
                announcedPearl.remove(id)
            }

            // Stick
            if (StickNotifier.enabled && itemInHand == Items.STICK) {
                var announce = true
                if (StickNotifier.proximity) {
                    if (!checkProximityConditions()) {
                        announce = false
                    }
                }
                if (announce) {
                    if (shouldNotify(id, "Stick")) {
                        chat("$teamColorPrefix$playerName$resetColor is holding Stick")
                        if (playSound && StickNotifier.sound) {
                            mc.soundManager.play(
                                PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_PLING, 1.2f)
                            )
                        }
                    }
                } else {
                    announcedStick.remove(id)
                }
            } else {
                announcedStick.remove(id)
            }

            // Invisibility Potion
            if (InvisibilityNotifier.enabled &&
                otherPlayer.mainHandStack.getPotionEffects()
                    .any { it.effectType == StatusEffects.INVISIBILITY }
            ) {
                var announce = true
                if (InvisibilityNotifier.proximity) {
                    if (!checkProximityConditions()) {
                        announce = false
                    }
                }
                if (announce) {
                    if (shouldNotify(id, "InvisibilityPotion")) {
                        chat("$teamColorPrefix$playerName$resetColor is holding Invisibility Potion")
                        if (playSound && InvisibilityNotifier.sound) {
                            mc.soundManager.play(
                                PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_PLING, 1.5f)
                            )
                        }
                    }
                } else {
                    announcedInvisibility.remove(id)
                }
            } else {
                announcedInvisibility.remove(id)
            }

            // Obsidian (Not projectile-like, proximity/aim condition does not apply here)
            if (obsidian && itemInHand == Items.OBSIDIAN) {
                if (shouldNotify(id, "Obsidian")) {
                    chat("$teamColorPrefix$playerName$resetColor is holding Obsidian")
                    if (playSound && obsidianSound) {
                        mc.soundManager.play(
                            PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_PLING, 0.4f)
                        )
                    }
                }
            } else {
                announcedObsidian.remove(id)
            }

            if (diamondSword && itemInHand == Items.DIAMOND_SWORD) {
                if (shouldNotify(id, "DiamondSword")) {
                    chat("$teamColorPrefix$playerName$resetColor is holding Diamond Sword")
                    if (playSound && diamondSwordSound) {
                        mc.soundManager.play(
                            PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_PLING, 0.9f)
                        )
                    }
                }
            } else {
                announcedDiamondSword.remove(id)
            }

            if (ironSword && itemInHand == Items.IRON_SWORD) {
                if (shouldNotify(id, "IronSword")) {
                    chat("$teamColorPrefix$playerName$resetColor is holding Iron Sword")
                    if (playSound && ironSwordSound) {
                        mc.soundManager.play(
                            PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_PLING, 0.6f)
                        )
                    }
                }
            } else {
                announcedIronSword.remove(id)
            }
        }
    }

    override fun disable() {
        announcedFireball.clear()
        announcedBow.clear()
        announcedPearl.clear()
        announcedObsidian.clear()
        announcedDiamondSword.clear()
        announcedIronSword.clear()
        announcedStick.clear()
        announcedInvisibility.clear()
        super.disable()
    }

    private fun shouldNotify(id: UUID, key: String): Boolean {
        val now = System.currentTimeMillis()
        val last = lastAnnounceTimes[id to key] ?: 0L
        val cooldownMs = (notificationCooldown * 1000).toLong()
        return if (cooldownMs <= 0 || now - last >= cooldownMs) {
            lastAnnounceTimes[id to key] = now
            true
        } else {
            false
        }
    }
} 

