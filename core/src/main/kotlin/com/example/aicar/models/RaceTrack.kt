package com.example.aicar.models

import com.badlogic.gdx.math.Vector2

/**
 * Defines the track’s checkpoints.
 * Cars must pass them in order to complete a lap.
 * This object primarily holds data and is not directly involved in UI rendering or input handling.
 */
object RaceTrack {
    /**
     * Ordered list of checkpoint centers (as Vector2).
     * These are logical positions for checkpoint detection.
     * Visual representation and collision rectangles are handled elsewhere (e.g., Car.kt, GameScreen.kt).
     */
    val checkpoints: List<Vector2> = listOf(
        Vector2(200f, 100f),  // Example checkpoint 1
        Vector2(1720f, 100f), // Example checkpoint 2
        Vector2(1720f, 980f), // Example checkpoint 3
        Vector2(200f, 980f)   // Example checkpoint 4
        // Add more checkpoints here if your track design requires them.
        // Ensure these correspond to the CHECKPOINT_RECTS in Car.kt if that's the primary detection method.
    )

    /**
     * Radius within which a checkpoint is considered "passed" if using radial detection.
     * If using rectangular overlap (like in Car.kt with CHECKPOINT_RECTS), this might be less relevant.
     */
    const val CHECKPOINT_RADIUS = 50f
}
