package com.example.aicar.models

import com.badlogic.gdx.graphics.Pixmap
import com.example.aicar.neat.Config
import com.badlogic.gdx.math.MathUtils
import kotlin.math.abs

class Sensor(
    private val numRays: Int = Config.RADAR_DEGREES.size,
    val rayLength: Float = Config.MAX_RADAR_DISTANCE
) {

    /**
     * Casts rays from the car's center to detect obstacles.
     * @param carCenterX World X of car's center.
     * @param carCenterY World Y of car's center.
     * @param carAngleDegrees Car's current heading (0=right, positive CCW).
     * @param mapPixmap The collision map.
     * @param worldHeight Total height of the game world (for y-inversion when sampling pixmap).
     * @return FloatArray of normalized distances (0.0 = immediate collision, 1.0 = max range).
     */
    fun sense(carCenterX: Float, carCenterY: Float, carAngleDegrees: Float, mapPixmap: Pixmap, worldHeight: Float): FloatArray {
        val sensedDistances = FloatArray(numRays) { 1.0f } // Default to max range

        for (i in Config.RADAR_DEGREES.indices) {
            val sensorWorldAngleDegrees = carAngleDegrees + Config.RADAR_DEGREES[i]
            var currentRayLength = 0f

            // Ray marching to find collision
            while (currentRayLength < rayLength) {
                val checkX = carCenterX + MathUtils.cosDeg(sensorWorldAngleDegrees) * currentRayLength
                val checkY = carCenterY + MathUtils.sinDeg(sensorWorldAngleDegrees) * currentRayLength

                val pixmapX = checkX.toInt()
                val pixmapY = (worldHeight - 1 - checkY).toInt() // Invert Y for pixmap coordinates

                // Check bounds
                if (pixmapX < 0 || pixmapX >= mapPixmap.width || pixmapY < 0 || pixmapY >= mapPixmap.height) {
                    sensedDistances[i] = currentRayLength / rayLength // Hit edge of map
                    break
                }

                // Check pixel color for border
                val pixelColor = mapPixmap.getPixel(pixmapX, pixmapY)
                val r = (pixelColor ushr 24) and 0xFF
                val g = (pixelColor ushr 16) and 0xFF
                val b = (pixelColor ushr  8) and 0xFF

                if (abs(r - Config.BORDER_R) <= Config.COLOR_TOLERANCE_WALL &&
                    abs(g - Config.BORDER_G) <= Config.COLOR_TOLERANCE_WALL &&
                    abs(b - Config.BORDER_B) <= Config.COLOR_TOLERANCE_WALL) {
                    sensedDistances[i] = currentRayLength / rayLength // Hit wall
                    break
                }
                currentRayLength += 2f // Step size for ray marching; adjust for precision/performance
            }
        }
        return sensedDistances
    }
}
