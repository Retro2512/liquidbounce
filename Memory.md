# Memory.md

## User Requirements

- Modify the `ProjectileNotifier` module.
- Include the player's team color in the notification message.
    - Format: `[<Color>] <Player> is holding x`
- Add a proximity-based notification system with per-item control:
    - For projectile-like items (Bow, Pearl, Fireball, Stick):
        - Each item should have its main enable/disable toggle.
        - Under each main toggle, a sub-toggle for "Proximity" check (on/off for that specific item).
    - Global settings for proximity behavior (when a per-item proximity is ON):
        - `proximityDistance`: Configurable distance (Range: 0-200m).
            - If set to 0m, the proximity distance/aim check is bypassed for any item that has its proximity toggle ON.
        - `aimVicinity`: Configurable angle for aim vicinity.
    - If an item's "Proximity" sub-toggle is ON and `proximityDistance` > 0m, notifications for that item should only appear if the other player is within `proximityDistance` OR aiming within `aimVicinity`.
    - For non-projectile items (Obsidian, Swords), notifications should occur regardless of proximity settings (if their respective item toggles are ON).
- Ensure changes are well-integrated and follow existing codebase practices.

## Project File Tree

(As provided by the user in the initial prompt)

## Roadmap/Todo List

### Overall Program Workings
The `ProjectileNotifier` module iterates through players, checks held items, and sends chat notifications with team colors. Projectile-like items (Bow, Pearl, Fireball, Stick) now have individual sub-toggles for enabling proximity/aim checks. Global distance/vicinity settings apply when these sub-toggles are active. If `proximityDistance` is 0, the actual check is bypassed. Non-projectile items are unaffected by proximity settings.

### Features Implemented So Far
- **Team Color in Notifications:**
    - Added `getPlayerTeamColorPrefix` function.
    - Modified chat messages: `[<Color>] <PlayerName>§r`.
- **Per-Item Proximity Check System:**
    - Removed global `proximityCheck` setting.
    - For Fireball, Bow, Pearl, Stick:
        - Changed from simple `boolean` to `ToggleableConfigurable` objects (e.g., `FireballNotifier`).
        - Each now has a main enable toggle (accessed via `.enabled`, e.g., `FireballNotifier.enabled`) and a sub-toggle `proximity` (`FireballNotifier.proximity`).
        - Registered these in an `init` block using `tree()`.
    - Global settings `proximityDistance` (0-200m) and `aimVicinity` remain.
    - Logic in `tickHandler` updated:
        - Helper `checkProximityConditions()` created for distance/aim calculation.
        - For items with proximity sub-toggles: if main toggle is ON (`.enabled`) and proximity sub-toggle is ON, `checkProximityConditions()` is evaluated (unless `proximityDistance` is 0, which bypasses it).
        - Non-projectile items (Obsidian, Swords) are unaffected by proximity logic.

### In-Progress Tasks
- Fix detekt errors in EspGlowMode.kt by adding file-level suppression for unused private properties

### Next Steps
- Test the implemented features thoroughly in-game after successful build.
    - Verify new settings structure in UI for Fireball, Bow, Pearl, Stick.
    - Test team colors.
    - Test per-item proximity toggles: enable/disable for specific items and observe behavior.
    - Confirm `proximityDistance = 0` bypasses the check for items with proximity enabled.
    - Confirm Obsidian and Swords notify regardless of proximity settings.
- Check for any unintended side effects or performance issues.
- Review code for clarity and adherence to project style.
- Remove unused imports if flagged by the compiler.

### How Tasks Connect
- Team color modifies notification strings.
- Proximity check is now a highly granular, per-item conditional filter for specified items, using global parameters for the check itself.
- All changes are within `ModuleProjectileNotifier.kt`.

## Projectile Notifier Module

- Added a toggle (`playSound`) to enable/disable the notification sound for announcements. This affects Fireball, Bow, Pearl, and Stick notifications. 

## Incoming Projectile Notifier

### User Requirements
- Refine the fireball detection in `ModuleIncomingProjectile`:
    - Only notify when fireballs are moving towards the player within the configured `aimVicinity` angle threshold and within a new configurable `detectionRange` (1.0m..200.0m).
- Reduce false positives by requiring both distance and angle checks.

### Roadmap/Todo List

#### Features Implemented So Far
- Introduced `detectionRange` config in `ModuleIncomingProjectile`.
- Updated fireball detection logic to include:
    - Distance check (`distance <= detectionRange`).
    - Angle check (`velocity.normalize().dotProduct(toPlayer.normalize()) >= cos(toRadians(aimVicinity))`).

#### Next Steps
- In-game testing of refined fireball notifications.
- Validate that existing features (Bow aiming detection, explosion block prediction) remain unaffected.  

## AutoBow Module

### User Requirements for AutoBow Feature
- Improve accuracy of the AutoBow module to ensure consistent and precise targeting.
- Address issues with moving targets, particularly overshooting when targets move slightly (e.g., 1-2 blocks back and forth).
- Ensure the solution remains client-side and silent to avoid detection.
- Focus on robust, foolproof solutions rather than temporary fixes or band-aid approaches.

### Detailed Plan for AutoBow Accuracy Improvement
1. **Enhanced Movement Prediction**:
   - Implement an adaptive prediction factor based on velocity history to handle erratic movements.
   - Incorporate lag compensation using ping to align with server-side target positions.
   - Add behavioral pattern detection to adjust aiming lead based on movement type (strafing, sprinting, etc.).
2. **Improved Projectile Trajectory Calculation**:
   - Refine trajectory models in both close-range (Polynomial) and long-range (Cydhranian) calculators to account for velocity.
   - Use dynamic hitbox targeting to adjust aim points based on distance and movement.
   - Include environmental checks for water or obstructions to delay or adjust shots.
3. **Optimized Charge and Release Timing**:
   - Introduce variable charge levels based on target distance for optimal firing rate and accuracy.
   - Synchronize release with stable aim within a tight rotation threshold.
4. **Robustness Against Edge Cases**:
   - Detect velocity spikes to prevent overshooting on sudden movements.
   - Implement a fallback direct aiming mode for unpredictable target behavior.
5. **Silent Operation and Anti-Cheat Evasion**:
   - Maintain silent rotations and hotbar management to avoid detection.
6. **Debugging and Feedback Loop**:
   - Add optional logging of prediction vs. actual outcomes for refinement.
   - Include a toggleable visual trajectory indicator for debugging.

### Context and Integration
- **Past Features**: Builds on existing aiming and shooting logic in AutoBow.
- **Current Task**: Focuses on precision enhancements for moving targets.
- **Future Integration**: Can be combined with other combat modules for seamless melee and ranged combat strategies.

## Project File Tree
- **src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/combat/**: Contains combat-related modules.
  - **aimbot/ModuleAutoBow.kt**: Main AutoBow module.
  - **aimbot/autobow/**: Subdirectory with specific features like AutoShoot, Aimbot, and FastCharge.
- **utils/aiming/**: Contains utilities for rotation and projectile calculations.

## Roadmap and Todo List
- **Overall Program Workings**: LiquidBounce is a Minecraft client mod with various combat and utility modules, including AutoBow for automated ranged attacks.
- **Features Implemented So Far**: Basic AutoBow functionality with aiming and auto-shooting based on bow charge.
- **In-Progress Tasks**: Enhancing AutoBow accuracy with advanced prediction and trajectory calculation.
- **Next Steps**: Implement the proposed changes to movement prediction, trajectory modeling, and release timing. Test extensively with moving targets to validate improvements. Integrate with other combat modules if applicable.

**Roadmap and Todo List**
- **Overall Program Workings**: LiquidBounce is a Minecraft client mod with various combat and utility modules, including AutoBow for automated ranged attacks.
- **Features Implemented So Far**: Basic AutoBow functionality; adaptive movement prediction with erratic movement detection and ping compensation; dynamic partial charge based on target distance; aim stability checks with configurable stable ticks; integrated ping compensation.
- **In-Progress Tasks**: None; AutoBow accuracy improvements have been implemented.
- **Next Steps**: Test in-game and tune configurations: `PingCompensation`, `ErraticReduction`, `VelocityHistoryTicks`, `PartialChargeRange`, `PartialChargeMultiplier`, and `MinStableAimTicks` for optimal performance against moving targets and varying network conditions. 