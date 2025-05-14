# Performance Audit Report

This document outlines potential performance bottlenecks found in the LiquidBounce-nextgen codebase. Findings are ranked from most to least impactful, with estimated performance impacts and suggested solutions.

**Disclaimer:** Estimates are based on static code analysis and may vary depending on hardware, game state, and other factors. Profiling is recommended for precise measurements. 

## Problematic Code Findings

### 1. `RenderedEntities` Update Logic and Frequency
*   **Severity:** High
*   **File(s):** `src/main/kotlin/net/ccbluex/liquidbounce/utils/entity/RenderedEntities.kt`
*   **Description:** The `entities` list in `RenderedEntities` is rebuilt from scratch every `GameTickEvent` (20 times per second) by iterating through all `world.entities`. In scenarios with many entities, this frequent, full iteration and the `entity.shouldBeShown()` check for each entity can consume substantial CPU resources.
*   **Problematic Code Snippet (Simplified from `RenderedEntities.kt`):**
    ```kotlin
    private val tickHandler = handler<GameTickEvent>(priority = FIRST_PRIORITY) {
        // ...
        entities.clear() // Clears the list
        for (entity in world.entities) { // Iterates ALL world entities
            if (entity is LivingEntity && entity.shouldBeShown()) { // Check for every entity
                entities += entity // Adds to list
            }
        }
    }
    ```
*   **Estimated FPS Drop:** Can cause a noticeable to significant FPS drop (5-50+ FPS depending on entity count and complexity of `shouldBeShown()`), especially in crowded servers or areas with many items/mobs.
*   **Solution Suggestions:**
    *   **Incremental Updates:** Modify `RenderedEntities` to update its list incrementally based on entity spawn/despawn events and chunk load/unload events, rather than a full rebuild every tick. This is more complex but significantly more performant.
    *   **Cache `shouldBeShown()` Results:** If the conditions within `entity.shouldBeShown()` (see below) do not change every tick for most entities, its result could be cached per entity, with invalidation logic tied to relevant state changes (e.g., team changes, target settings changes).
    *   **Staggered Updates (Potentially Destructive):** Consider updating portions of the entity list or different types of entities at different intervals if real-time accuracy for all entities isn't strictly needed for all subscribers. This could reduce per-tick load but might introduce slight delays in responsiveness for some visual features. Mark as **destructive** if it impacts core functionality dependent on immediate updates.

### 2. `EntityTaggingManager` Cache Invalidation and Event Firing
*   **Severity:** High
*   **File(s):**
    *   `src/main/kotlin/net/ccbluex/liquidbounce/utils/combat/EntityTaggingManager.kt`
    *   `src/main/kotlin/net/ccbluex/liquidbounce/utils/entity/RenderedEntities.kt` (due to its use of `shouldBeShown` which calls `getTag`)
    *   `src/main/kotlin/net/ccbluex/liquidbounce/utils/combat/CombatExtensions.kt` (location of `shouldBeShown`)
*   **Description:** The `EntityTaggingManager` cache (`cache`) is cleared entirely every `GameTickEvent`. Subsequently, when `RenderedEntities` iterates `world.entities` and calls `entity.shouldBeShown()`, which in turn calls `EntityTaggingManager.getTag(entity)`, it's highly probable that a cache miss occurs for many, if not most, entities. Each cache miss triggers the creation and dispatch of a `TagEntityEvent`. Firing potentially hundreds or thousands of these events every tick is a significant performance drain due to event object creation, dispatch overhead, and the processing by any listeners. The `isInteresting` function within `shouldBeShown` also involves multiple type checks and condition evaluations per entity.
*   **Problematic Code Snippets:**
    ```kotlin
    // In EntityTaggingManager.kt
    val tickHandler = handler<GameTickEvent>(priority = FIRST_PRIORITY) {
        cache.clear() // Cache completely cleared every tick
    }

    fun getTag(suspect: Entity): EntityTag {
        return this.cache.computeIfAbsent(suspect) { // Likely misses for many entities after cache clear
            val targetingInfo = TagEntityEvent(suspect, EntityTargetingInfo.DEFAULT)
            EventManager.callEvent(targetingInfo) // Event fired on each cache miss
            return@computeIfAbsent EntityTag(targetingInfo.targetingInfo, targetingInfo.color.value)
        }
    }

    // In CombatExtensions.kt (within shouldBeShown -> isInteresting)
    // private fun EnumSet<Targets>.isInteresting(suspect: Entity): Boolean {
    //     // ... several checks and type casts ...
    // }
    ```
*   **Estimated FPS Drop:** Can cause a significant FPS drop (10-60+ FPS), especially in entity-dense environments, due to the combined cost of cache misses, frequent event storms, and repeated computations in `isInteresting`.
*   **Solution Suggestions:**
    *   **Intelligent Cache Invalidation for `EntityTaggingManager`:**
        *   Remove entities from the `cache` only when they are actually removed from the world (e.g., on entity despawn, player leave, world change).
        *   Implement a mechanism for specific modules/systems to explicitly request a re-tagging for an entity if its state relevant to tagging changes (e.g., friend status update, manual tagging by a module). This avoids blanket cache clearing.
    *   **Reduce `TagEntityEvent` Frequency:**
        *   Decouple the `TagEntityEvent` from being fired on every cache miss if the tag is merely being recomputed due to the aggressive cache clear. The event should ideally signify a *new* entity needing tagging or a significant state change requiring listeners to re-evaluate.
        *   Consider if all listeners to `TagEntityEvent` truly need to run their logic for every entity every tick.
    *   **Optimize `isInteresting`:** While secondary to the caching issue, review `isInteresting` for any micro-optimizations, though the primary gain is from fixing the caching.

### 3. Inefficient Entity Processing and Object Allocation in `EspBoxMode` Render Loop
*   **Severity:** Medium to High
*   **File(s):** `src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/render/esp/modes/EspBoxMode.kt`
*   **Description:** The `EspBoxMode`'s `WorldRenderEvent` handler, which runs every frame, exhibits several inefficiencies:
    1.  **Redundant Iteration/Filtering for Invisibles:** If the `seeInvis` option is enabled, the code first processes entities from `RenderedEntities` and then performs a separate iteration over `world.entities` to find invisible players. This double iteration can be costly.
    2.  **Per-Frame Box Calculation:** New `Box` objects are created and calculations for their dimensions (including expansion) are performed for every entity to be rendered, every single frame.
    3.  **Frequent `getColor()` Calls:** `ModuleESP.getColor(entity)` is invoked for each entity every frame. This function internally calls `FriendManager.isFriend()` and `EntityTaggingManager.getTag()`. Given that `EntityTaggingManager`'s cache is cleared every tick (see Issue #2), these calls can be expensive if the cache isn't repopulated quickly enough or if `FriendManager.isFriend()` involves its own non-trivial lookups.
    4.  **`Color4b` Object Allocation:** New `Color4b` instances are created (e.g., `color.with(a = ...)` for fill and outline) for each entity every frame, contributing to garbage collector pressure.
*   **Problematic Code Snippet (Simplified from `EspBoxMode.kt`):**
    ```kotlin
    private val renderHandler = handler<WorldRenderEvent> { event ->
        // Iteration 1 (visible entities from RenderedEntities)
        val entitiesWithBoxes = RenderedEntities.filter { !it.isInvisible }.map { entity ->
            // ... new Box(...) calculations ...
            entity to box
        }.toMutableList()

        // Iteration 2 (if seeInvis, iterates world.entities for invisible players)
        if (seeInvis) {
            world.entities.filterIsInstance<PlayerEntity>().filter { it.isInvisible }.forEach { entity ->
                // ... new Box(...) calculations ...
                entitiesWithBoxes += entity to box
            }
        }

        entitiesWithBoxes.forEach { (entity, box) ->
            val color = getColor(entity) // Called per entity, per frame; involves FriendManager & EntityTaggingManager
            val baseColor = color.with(a = fillAlpha) // Potential new Color4b object
            val outlineColor = color.with(a = outlineAlpha) // Potential new Color4b object
            // ... drawBox ...
        }
    }
    ```
*   **Estimated FPS Drop:** Medium. Cumulatively, these allocations and repeated calculations for many entities per frame can lead to noticeable FPS degradation (5-30+ FPS), increased GC pauses, and higher CPU usage, especially with many entities on screen.
*   **Solution Suggestions:**
    1.  **Consolidate Entity Collection for ESP:** Streamline the collection of entities (both visible and, if enabled, invisible) to avoid multiple full iterations. This might involve changes to how `RenderedEntities` provides data or a more unified filtering approach within `EspBoxMode`.
    2.  **Cache Bounding Boxes:** Calculate and cache entity bounding boxes (perhaps in `RenderedEntities` or an ESP-specific cache) and update them less frequently (e.g., on entity tick or when significant pose/size changes occur) rather than every frame. The `expand` operation can be applied to the cached box.
    3.  **Cache Entity Colors More Aggressively:** The result of `ModuleESP.getColor(entity)` should be cached more effectively, possibly per tick rather than being re-fetched (and potentially re-computed due to `EntityTaggingManager`'s behavior) every frame. This cache should be invalidated when underlying data (friend status, tags) changes.
    4.  **Minimize `Color4b` Allocations:** 
        *   Investigate if `BoxRenderer.drawBox` can accept color components (like alpha) directly to avoid creating new `Color4b` objects just to change alpha.
        *   If `Color4b` objects are mutable and it's safe to do so, modify an existing instance instead of creating a new one.
        *   Consider an object pool for `Color4b` if profiling confirms it as a major allocation source, though this adds complexity.
*   **Destructive/Feature Breaking:** Caching colors or bounding boxes requires robust invalidation logic. If not implemented correctly, it could lead to stale or incorrect visuals (e.g., wrong box size, wrong color after a status change). Mark as **potentially destructive** if caching logic is not perfectly synchronized with state changes.

### 4. Inefficient Entity Processing and Object Allocation in `EspOutlineMode` Render Loop (Similar to EspBoxMode)
*   **Severity:** Medium to High
*   **File(s):** `src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/render/esp/modes/EspOutlineMode.kt`
*   **Description:** This mode shares most of the inefficiencies of `EspBoxMode` regarding its per-frame operations within the `WorldRenderEvent` handler:
    1.  **Redundant Iteration/Filtering for Invisibles:** If `seeInvis` is enabled, it also iterates `world.entities` after processing `RenderedEntities`.
    2.  **Per-Frame Box Calculation:** New `Box` objects are created for every rendered entity, every frame.
    3.  **Frequent `getColor()` / `rainbow()` Calls:** `ModuleESP.getColor(entity)` (for visible entities) or `rainbow()` (for invisible entities if `seeInvis` is on and the entity is invisible) is called per entity per frame. `rainbow()` likely involves color calculations or object creation each time it's invoked.
    4.  **`Color4b` Object Allocation:** At least two new `Color4b` instances are created per entity per frame (`outlineColor` and `fillColor`, even if fill alpha is zero).
*   **Problematic Code Snippet (Simplified from `EspOutlineMode.kt`):
    ```kotlin
    private val renderHandler = handler<WorldRenderEvent> { event ->
        // Iteration 1 (visible entities from RenderedEntities) - similar to EspBoxMode
        // ... new Box(...) calculations ...

        // Iteration 2 (if seeInvis, iterates world.entities) - similar to EspBoxMode
        // ... new Box(...) calculations ...

        entitiesWithBoxes.forEach { (entity, box) ->
            val colorRaw = if (entity.isInvisible) rainbow() else getColor(entity) // getColor or rainbow() per entity/frame
            val outlineColor = colorRaw.with(a = alpha) // New Color4b
            val fillColor = colorRaw.with(a = 0)    // New Color4b
            // ... drawBox ...
        }
    }
    ```
*   **Estimated FPS Drop:** Medium. Similar to `EspBoxMode`, this can degrade FPS (5-30+ FPS) and increase GC activity, especially with many entities.
*   **Solution Suggestions:** The solutions are largely the same as for `EspBoxMode`:
    1.  **Consolidate Entity Collection.**
    2.  **Cache Bounding Boxes.**
    3.  **Cache Entity Colors / `rainbow()` Result:** If `rainbow()` produces a color that cycles over time but is the same for all entities at a given moment, its result could be fetched once per frame. If it's unique per entity or changes rapidly, caching strategies similar to `getColor` would apply.
    4.  **Minimize `Color4b` Allocations.**
*   **Destructive/Feature Breaking:** Same as `EspBoxMode`; caching requires careful implementation. If `rainbow()` is intended to be unique per entity even when called in the same frame, caching its result once per frame would break that feature (mark as **destructive** in that specific scenario).

### 5. Performance of Vanilla Glow via `MixinWorldRenderer` for `EspGlowMode`
*   **Severity:** Medium (impact heavily influenced by Issues #1 and #2)
*   **File(s):**
    *   `src/main/java/net/ccbluex/liquidbounce/injection/mixins/minecraft/render/MixinWorldRenderer.java`
    *   `src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/render/esp/modes/EspGlowMode.kt` (as it enables this logic)
*   **Description:** `EspGlowMode` integrates with Minecraft's vanilla entity outline/glow rendering. While this leverages existing systems, performance issues arise from how LiquidBounce hooks into it:
    1.  **Frequent `shouldBeShown()` Calls:** The `shouldRenderOutline()` method in `MixinWorldRenderer` (which determines if an entity gets the vanilla glow for ESP) calls `CombatExtensionsKt.shouldBeShown(entity)`. This check, as detailed in Issues #1 and #2, is expensive due to the `EntityTaggingManager`'s cache being cleared every tick and the subsequent event firing/re-computation.
    2.  **Frequent `getColor()` Calls:** The `injectTeamColor()` method in `MixinWorldRenderer` calls `ModuleESP.INSTANCE.getColor(livingEntity)` for each entity that is made to glow. This again suffers from the same problems as `shouldBeShown()` regarding `EntityTaggingManager` and `FriendManager` lookups.
    3.  **Vanilla Outline Overhead:** While generally optimized, Minecraft's own outline effect has a cost, especially when many entities are made to glow simultaneously. The performance here is additive to any overhead from the client's logic.
*   **Problematic Code Snippets (Key parts from `MixinWorldRenderer.java`):
    ```java
    // Determines if an entity *should* glow for ESP
    @Unique
    private boolean shouldRenderOutline(Entity entity) {
        // ...
        else if (EspGlowMode.INSTANCE.getRunning() && CombatExtensionsKt.shouldBeShown(entity)) { // shouldBeShown called frequently
            return true;
        }
        // ...
    }

    // Injects the ESP color for the glow
    @ModifyExpressionValue(method = "renderEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getTeamColorValue()I"))
    private int injectTeamColor(int original, @Local Entity entity) {
        if (entity instanceof LivingEntity livingEntity && EspGlowMode.INSTANCE.getRunning()) {
            return ModuleESP.INSTANCE.getColor(livingEntity).toARGB(); // getColor called frequently
        }
        // ...
        return original;
    }
    ```
*   **Estimated FPS Drop:** Low to Medium directly from the glow itself if few entities glow, but the impact is significantly amplified by the high cost of `shouldBeShown()` and `getColor()` calls (due to Issues #1 & #2). If many entities glow, these repeated expensive calls can cause a substantial drop (5-40+ FPS).
*   **Solution Suggestions:**
    1.  **Address Core Issues (#1 and #2):** The most significant improvements for `EspGlowMode` will come from fixing the inefficiencies in `RenderedEntities` and `EntityTaggingManager`. This will make the `shouldBeShown()` and `getColor()` calls much cheaper.
    2.  **Cache `shouldRenderOutline` Result (Potentially Destructive):** If the conditions for an entity glowing (ESP active, `shouldBeShown` true) don't change rapidly within a frame or between vanilla's checks for `hasOutline`, the result of `shouldRenderOutline(entity)` could potentially be cached (e.g., per entity per tick). This is complex and depends heavily on vanilla's internal calling patterns. Mark as **potentially destructive**.

### 6. Custom Outline Shader Re-Rendering Entities for `EspOutlineMode`
*   **Severity:** High to Very High
*   **File(s):** `src/main/java/net/ccbluex/liquidbounce/injection/mixins/minecraft/render/MixinWorldRenderer.java` (specifically the `injectOutlineESP` method and `OutlineShader` interactions)
*   **Description:** The `EspOutlineMode` implemented via `MixinWorldRenderer` and `OutlineShader` re-renders entities that require an outline. This is a very costly approach:
    1.  **Entity Re-Rendering:** The `renderEntity(...)` method is effectively called a second time for each entity that needs this custom outline. Rendering an entity (mesh, textures, transformations) is typically one of the most performance-intensive parts of the game. Doubling this work for multiple entities drastically reduces FPS.
    2.  **`shouldBeShown()` and `getColor()` Calls:** Before re-rendering, `CombatExtensionsKt.shouldBeShown(entity)` and `ModuleESP.INSTANCE.getColor()` are called, incurring the overhead discussed in Issues #1, #2, and #5.
    3.  **Custom Shader Operations:** The `OutlineShader` itself adds GPU and CPU load for its processing and framebuffer management.
*   **Problematic Code Snippet (Simplified from `injectOutlineESP` in `MixinWorldRenderer.java`):
    ```java
    @Inject(method = "renderEntity", at = @At("HEAD"))
    private void injectOutlineESP(Entity entity, double cameraX, double cameraY, double cameraZ, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, CallbackInfo info) {
        // ... Prevent stack overflow ...
        Color4b color;
        if (EspOutlineMode.INSTANCE.getRunning() && entity instanceof LivingEntity && CombatExtensionsKt.shouldBeShown(entity)) { // Expensive checks
            color = ModuleESP.INSTANCE.getColor((LivingEntity) entity); // Expensive call
        } else {
            return;
        }
        // ... swap framebuffers to OutlineShader's buffer ...
        outlineShader.setColor(color);
        outlineShader.setDirty(true);
        try {
            renderEntity(entity, cameraX, cameraY, cameraZ, tickDelta, matrices, outlineShader.getVertexConsumerProvider()); // Entity is RE-RENDERED here
        } finally {
            // ... restore RenderingFlags ...
        }
        // ... restore original framebuffers ...
    }
    ```
*   **Estimated FPS Drop:** High to Very High. Re-rendering even a moderate number of entities can easily cause FPS to drop by 30-70% or more (e.g., 20-100+ FPS loss). The impact scales directly with the number of entities being outlined this way.
*   **Solution Suggestions:**
    1.  **Avoid Re-Rendering (Major Refactor):** This is the most critical fix. The current approach of a full second render pass for outlines is unsustainable for good performance.
        *   **Explore Modern Outline Techniques:** Investigate techniques that don't require re-rendering, such as:
            *   **Stencil Buffer Outlines:** If the target Minecraft version supports it effectively, use stencil operations to draw outlines. This is often much more performant.
            *   **Post-Processing Edge Detection:** Apply a screen-space edge detection filter (e.g., using Sobel operator on depth/normal buffers) to create outlines. This has a fixed cost per frame, independent of entity count, but might look different and can outline non-entities too if not masked.
            *   **Geometry Shader Expansion:** Use geometry shaders to expand silhouettes. (May have compatibility/performance variances across GPUs).
            *   **Modified Entity Shaders:** Inject logic into the primary entity shaders to output an outline. This is complex.
        *   This would be a **destructive** change in terms of implementation and potentially visual style, but necessary for performance.
    2.  **Address Core `shouldBeShown` / `getColor`:** Fixing Issues #1 and #2 will alleviate some of the preparatory cost but won't solve the fundamental re-rendering problem.
    3.  **Simplify Outline Pass (Minor Optimization, if re-rendering kept):** If re-rendering is absolutely maintained (not recommended), ensure the `outlineShader.getVertexConsumerProvider()` uses the simplest possible rendering state (e.g., minimal texturing, simplified shaders for the outline pass itself if the shader is complex). This is a minor mitigation compared to avoiding re-rendering. Mark as **potentially destructive** if it significantly degrades outline quality.


</rewritten_file> 