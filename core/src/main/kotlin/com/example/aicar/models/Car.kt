package com.example.aicar.models

import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.Gdx // Keep Gdx for logging if necessary
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.Sprite
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.example.aicar.neat.Config
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Represents either an AI-driven or player-controlled car.
 */
class Car(
    var x: Float, // Bottom-left corner
    var y: Float, // Bottom-left corner
    private val spriteSheet: Texture, // Sprite sheet with 9 frames
    val brain: NeuralNetwork? = null, // Null for user car
    private val mapPixmap: Pixmap, // Pixmap of the track for collision
    private val worldHeight: Float // World height for Y-inversion in pixmap sampling
) {
    companion object {
        // Checkpoint Rectangles (LibGDX Y-up coordinates)
        // These define the areas cars must pass through.
        val CHECKPOINT_RECTS: List<com.badlogic.gdx.math.Rectangle> = listOf(
            com.badlogic.gdx.math.Rectangle(1000f, Config.SCREEN_HEIGHT_FLOAT - 840f - 140f, 50f, 140f), // CP 0
            com.badlogic.gdx.math.Rectangle(1600f, Config.SCREEN_HEIGHT_FLOAT - 510f - 140f, 50f, 140f), // CP 1
            com.badlogic.gdx.math.Rectangle(1750f, Config.SCREEN_HEIGHT_FLOAT - 300f - 50f, 140f, 50f),  // CP 2
            com.badlogic.gdx.math.Rectangle(1425f, Config.SCREEN_HEIGHT_FLOAT - 60f - 140f, 50f, 140f),   // CP 3
            com.badlogic.gdx.math.Rectangle(720f, Config.SCREEN_HEIGHT_FLOAT - 30f - 140f, 50f, 140f),    // CP 4
            com.badlogic.gdx.math.Rectangle(60f, Config.SCREEN_HEIGHT_FLOAT - 280f - 50f, 190f, 50f),    // CP 5
            com.badlogic.gdx.math.Rectangle(300f, Config.SCREEN_HEIGHT_FLOAT - 590f - 50f, 140f, 50f),   // CP 6
            com.badlogic.gdx.math.Rectangle(600f, Config.SCREEN_HEIGHT_FLOAT - 850f - 140f, 50f, 140f),   // CP 7
            com.badlogic.gdx.math.Rectangle(800f, Config.SCREEN_HEIGHT_FLOAT - 800f - 140f, 50f, 140f)    // CP 8
        )
    }

    private val halfW = Config.CAR_WIDTH  / 2f
    private val halfH = Config.CAR_HEIGHT / 2f

    private val cornerAngles = floatArrayOf(45f, 135f, 225f, 315f) // For collision detection
    private val collisionDist = Config.CAR_WIDTH / 2.2f
    private val worldCorners = Array(4) { Vector2() }

    private val sensor = Sensor()
    private val sensorReadings = FloatArray(Config.RADAR_DEGREES.size) { 1f }

    private val originalLayers: List<Sprite>
    private val rotatedLayers: MutableList<Sprite>

    var angleDegrees: Float = 0f
        private set
    private var speed: Float = 0f

    var nextCpIndex = 0
    var laps = 0
        private set
    var isAlive = true
        private set
    var fitness = 0f

    private var lastDistToCheckpoint = Float.MAX_VALUE
    private var timeSinceLastCheckpoint = 0f
    private var totalDistanceCoveredPositiveOnly = 0f
    private var maxDistFromStart = 0f
    private val startPosition = Vector2(Config.LIBGDX_START_X, Config.LIBGDX_START_Y)

    private val carBounds = com.badlogic.gdx.math.Rectangle(x, y, Config.CAR_WIDTH, Config.CAR_HEIGHT)
    var currentRank: Int = 0

    // --- User Control Flags ---
    // These flags are set by GameScreen based on touch input for user-controlled cars.
    var wantsToAccelerate: Boolean = false
    var wantsToBrake: Boolean = false // Or reverse
    var wantsToTurnLeft: Boolean = false
    var wantsToTurnRight: Boolean = false
    // --- End User Control Flags ---

    init {
        originalLayers = List(9) { i ->
            Sprite(spriteSheet, i * 16, 0, 16, 16).apply {
                setSize(Config.CAR_WIDTH, Config.CAR_HEIGHT)
                setOrigin(width/2f, height/2f)
            }
        }
        rotatedLayers = MutableList(9) { Sprite(originalLayers[it]) }
        reset(Config.LIBGDX_START_X, Config.LIBGDX_START_Y, 0f)
    }

    fun reset(startX: Float, startY: Float, initialAngleDeg: Float = 0f) {
        x = startX
        y = startY
        angleDegrees = initialAngleDeg
        speed = if (brain != null) Config.AI_CAR_SPEED else 0f
        isAlive = true
        laps = 0
        nextCpIndex = 0
        fitness = 0f
        currentRank = 0
        lastDistToCheckpoint = Float.MAX_VALUE
        timeSinceLastCheckpoint = 0f
        totalDistanceCoveredPositiveOnly = 0f
        maxDistFromStart = 0f
        startPosition.set(startX, startY)
        sensorReadings.fill(1f)
        originalLayers.forEachIndexed { i, orig -> rotatedLayers[i].set(orig) }
        carBounds.setPosition(x, y)

        // Reset user control flags
        wantsToAccelerate = false
        wantsToBrake = false
        wantsToTurnLeft = false
        wantsToTurnRight = false
    }

    fun update(delta: Float) {
        if (!isAlive) return

        val prevX = x
        val prevY = y

        if (brain != null) { // AI Controlled
            val cx = x + halfW
            val cy = y + halfH
            val dists = sensor.sense(cx, cy, angleDegrees, mapPixmap, worldHeight)
            System.arraycopy(dists, 0, sensorReadings, 0, dists.size)
            speed = Config.AI_CAR_SPEED
            val outputs = brain.predict(sensorReadings)
            val turnDecision = outputs.withIndex().maxByOrNull { it.value }?.index ?: 0 // 0 for right, 1 for left

            if (turnDecision == 0) angleDegrees -= Config.AI_CAR_TURN_RATE_DEGREES_PER_SEC * delta // Turn Right
            else angleDegrees += Config.AI_CAR_TURN_RATE_DEGREES_PER_SEC * delta // Turn Left

        } else { // User Controlled
            var throttleInput = 0f
            if (wantsToAccelerate) throttleInput = 1f
            if (wantsToBrake) throttleInput = -1f // Braking/Reversing

            var turnInput = 0f
            if (wantsToTurnLeft) turnInput = 1f
            if (wantsToTurnRight) turnInput = -1f

            // Apply acceleration/braking
            speed = when {
                throttleInput > 0f -> (speed + Config.USER_CAR_ACCELERATION * delta).coerceAtMost(Config.USER_CAR_MAX_SPEED)
                throttleInput < 0f -> (speed - Config.USER_CAR_BRAKE_DECELERATION * delta).coerceAtLeast(-Config.USER_CAR_MAX_SPEED / 2f) // Allow reverse
                else -> { // Coasting
                    val decel = Config.USER_CAR_COAST_DECELERATION * delta
                    when {
                        speed > decel -> speed - decel
                        speed < -decel -> speed + decel
                        else -> 0f
                    }
                }
            }
            // Apply turning
            angleDegrees += turnInput * Config.USER_CAR_TURN_RATE * delta
        }

        angleDegrees = (angleDegrees % 360f + 360f) % 360f // Normalize angle

        val moveX = MathUtils.cosDeg(angleDegrees) * speed * delta
        val moveY = MathUtils.sinDeg(angleDegrees) * speed * delta
        x += moveX
        y += moveY
        carBounds.setPosition(x, y)

        updateWorldCorners()
        checkMapCollision()

        if (!isAlive) {
            if (brain != null) { // Penalties for AI
                if (timeSinceLastCheckpoint > 0) {
                    val avgPositiveSpeed = totalDistanceCoveredPositiveOnly / timeSinceLastCheckpoint
                    if (avgPositiveSpeed < Config.STUCK_CAR_THRESHOLD_SPEED) fitness += Config.STUCK_CAR_PENALTY
                }
                val distFromStart = startPosition.dst(x, y)
                if (distFromStart < Config.MAX_DIST_FROM_START_THRESHOLD) fitness += Config.STUCK_NO_MOVEMENT_PENALTY
            }
            return
        }

        rotateAllLayers()

        if (brain != null) { // Fitness updates for AI
            if (speed > 0) {
                val movedDistance = sqrt((x - prevX).pow(2) + (y - prevY).pow(2))
                fitness += Config.MOVEMENT_REWARD * delta
                totalDistanceCoveredPositiveOnly += movedDistance
            }

            if (nextCpIndex < CHECKPOINT_RECTS.size) {
                val nextCpCenter = Vector2(CHECKPOINT_RECTS[nextCpIndex].x + CHECKPOINT_RECTS[nextCpIndex].width / 2f,
                    CHECKPOINT_RECTS[nextCpIndex].y + CHECKPOINT_RECTS[nextCpIndex].height / 2f)
                val currentDistToCp = Vector2.dst(x + halfW, y + halfH, nextCpCenter.x, nextCpCenter.y)
                if (lastDistToCheckpoint != Float.MAX_VALUE) {
                    fitness += (lastDistToCheckpoint - currentDistToCp) * Config.PROGRESS_REWARD_SCALE
                }
                lastDistToCheckpoint = currentDistToCp
            }

            timeSinceLastCheckpoint += delta
            if (timeSinceLastCheckpoint > Config.MAX_TIME_BETWEEN_CHECKPOINTS) {
                fitness += Config.STUCK_TIME_PENALTY
                isAlive = false
                Gdx.app.log("Car", "AI Car died: timeout between checkpoints.")
                return
            }
            maxDistFromStart = maxOf(maxDistFromStart, startPosition.dst(x,y))
        }
        checkCheckpointsAndFitness()
    }

    private fun updateWorldCorners() {
        val cx = x + halfW
        val cy = y + halfH
        for (i in cornerAngles.indices) {
            val rad = MathUtils.degreesToRadians * (angleDegrees - cornerAngles[i])
            worldCorners[i].set(
                cx + collisionDist * MathUtils.cos(rad),
                cy + collisionDist * MathUtils.sin(rad)
            )
        }
    }

    private fun checkMapCollision() {
        for (pt in worldCorners) {
            val px = pt.x.toInt()
            val py = (worldHeight - 1 - pt.y).toInt()

            if (px < 0 || px >= mapPixmap.width || py < 0 || py >= mapPixmap.height) {
                isAlive = false; Gdx.app.debug("CarCollision", "Died: Off map bounds."); return
            }

            val pix = mapPixmap.getPixel(px, py)
            val r = (pix ushr 24) and 0xFF
            val g = (pix ushr 16) and 0xFF
            val b = (pix ushr  8) and 0xFF

            if (abs(r - Config.BORDER_R) <= Config.COLOR_TOLERANCE_WALL &&
                abs(g - Config.BORDER_G) <= Config.COLOR_TOLERANCE_WALL &&
                abs(b - Config.BORDER_B) <= Config.COLOR_TOLERANCE_WALL) {
                isAlive = false; Gdx.app.debug("CarCollision", "Died: Hit border wall."); return
            }
        }
    }

    private fun checkCheckpointsAndFitness() {
        if (CHECKPOINT_RECTS.isEmpty()) return
        val nextCheckpointRect = CHECKPOINT_RECTS.getOrNull(nextCpIndex) ?: return

        if (carBounds.overlaps(nextCheckpointRect)) {
            nextCpIndex++
            if (brain != null) {
                fitness += Config.CHECKPOINT_REWARD
                timeSinceLastCheckpoint = 0f
                lastDistToCheckpoint = Float.MAX_VALUE
            }

            if (nextCpIndex >= CHECKPOINT_RECTS.size) {
                nextCpIndex = 0
                laps++
                if (brain != null) fitness += Config.LAP_REWARD
                // Lap sound effect can be triggered from GameScreen based on lap change
            }
        }
    }

    private fun rotateAllLayers() {
        val offsetScale = -(when { // Reversed offset scale for perspective
            angleDegrees <=  90f   ->  angleDegrees * (3.5f/90f) - 2f
            angleDegrees <= 180f   -> (angleDegrees- 90f) * (1.4f/90f) + 1.5f
            angleDegrees <= 270f   -> (angleDegrees-180f)*(-4.5f/90f)  + 3f
            else                   -> (angleDegrees-270f)*(-0.5f/90f)  - 1.5f
        })
        val spriteRotationAngle = angleDegrees

        rotatedLayers.forEachIndexed { i, sprite ->
            sprite.rotation = spriteRotationAngle
            val offsetAngleRad = MathUtils.degreesToRadians * (angleDegrees - 90f)
            val dx = MathUtils.cos(offsetAngleRad) * i * offsetScale
            val dy = MathUtils.sin(offsetAngleRad) * i * offsetScale
            sprite.setPosition(x + halfW - dx - sprite.originX, y + halfH - dy - sprite.originY)
        }
    }

    fun renderSprites(batch: Batch) {
        if (!isAlive) return
        rotatedLayers.forEach { it.draw(batch) }
    }

    fun drawRank(batch: Batch, font: BitmapFont) {
        if (!isAlive || currentRank <= 0) return
        val rankText = when (currentRank) {
            1 -> "1st"; 2 -> "2nd"; 3 -> "3rd"; else -> "${currentRank}th"
        }
        font.color = Color.WHITE
        // Font scale for rank text should be controlled in GameScreen for consistency with other UI text.
        // Here we just draw it. GameScreen will set font.data.setScale() before calling.
        font.draw(batch, rankText, x + Config.CAR_WIDTH/2, y + Config.CAR_HEIGHT + 20f, 0f, Align.center, false)
    }

    fun drawRadars(sr: ShapeRenderer) {
        if (!isAlive || brain == null) return
        sr.color = Config.RADAR_LINE_COLOR
        val cx = x + halfW
        val cy = y + halfH
        for (i in Config.RADAR_DEGREES.indices) {
            val ang = angleDegrees + Config.RADAR_DEGREES[i]
            val dist = sensorReadings[i] * Config.MAX_RADAR_DISTANCE
            val ex = cx + MathUtils.cosDeg(ang) * dist
            val ey = cy + MathUtils.sinDeg(ang) * dist
            sr.line(cx, cy, ex, ey)
        }
    }

    fun getProgressMetric(): Triple<Int, Int, Float> {
        val distToNext = if (nextCpIndex < CHECKPOINT_RECTS.size) {
            val nextCpCenter = Vector2(CHECKPOINT_RECTS[nextCpIndex].x + CHECKPOINT_RECTS[nextCpIndex].width / 2f,
                CHECKPOINT_RECTS[nextCpIndex].y + CHECKPOINT_RECTS[nextCpIndex].height / 2f)
            Vector2.dst(x + halfW, y + halfH, nextCpCenter.x, nextCpCenter.y)
        } else Float.MAX_VALUE
        return Triple(laps, nextCpIndex, -distToNext)
    }
}
