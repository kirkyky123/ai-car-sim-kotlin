package com.example.aicar.neat

import com.badlogic.gdx.graphics.Color

object Config {
    // Screen Dimensions
    const val SCREEN_WIDTH: Int = 1920
    const val SCREEN_HEIGHT: Int = 1080
    const val SCREEN_WIDTH_FLOAT: Float = SCREEN_WIDTH.toFloat()
    const val SCREEN_HEIGHT_FLOAT: Float = SCREEN_HEIGHT.toFloat()

    // Car dimensions
    const val CAR_WIDTH: Float = 40f
    const val CAR_HEIGHT: Float = 40f

    // AI Car movement parameters
    const val AI_CAR_SPEED: Float = 210f // Units per second
    const val AI_CAR_TURN_RATE_DEGREES_PER_SEC: Float = 300f // Degrees per second

    // User Car Physics
    const val USER_CAR_MAX_SPEED: Float        = 4.5f * 60f // units per second
    const val USER_CAR_ACCELERATION: Float     = 2f * 60f // units per second^2
    const val USER_CAR_BRAKE_DECELERATION: Float  = 0.2f * 60f // units per second^2
    const val USER_CAR_COAST_DECELERATION: Float  = 0.4f * 60f // units per second^2
    const val USER_CAR_TURN_RATE: Float        = 3.0f * 60f  // degrees per second

    const val MAX_RADAR_DISTANCE: Float        = 250f
    val RADAR_DEGREES: FloatArray              = floatArrayOf(-90f, -45f, 0f, 45f, 90f) // 5 radar inputs

    // Start Position
    const val LIBGDX_START_X = 915f
    const val LIBGDX_START_Y = 170f


    // Colors
    val BORDER_COLOR_RGB = Color(119/255f, 194/255f, 119/255f, 1f)
    const val BORDER_R: Int = 119
    const val BORDER_G: Int = 194
    const val BORDER_B: Int = 119
    const val COLOR_TOLERANCE_WALL: Int = 30

    val RADAR_LINE_COLOR = Color(0f, 255f/255f, 127f/255f, 1f) // Spring Green
    val CHECKPOINT_COLOR = Color(1f, 1f, 0f, 0.3f) // Yellowish, 30% transparent
    val NEXT_CHECKPOINT_COLOR = Color(1f, 165f/255f, 0f, 0.5f) // Orangish, 50% transparent


    // NEAT Configuration
    const val POPULATION_SIZE: Int = 100
    const val MUTATION_RATE: Float = 0.8f
    const val CROSSOVER_RATE: Float = 0.75f

    // Compatibility Distance Coefficients
    const val C1: Float = 1.0f // Excess genes coefficient
    const val C2: Float = 1.0f // Disjoint genes coefficient
    const val C3: Float = 0.6f // Weight difference coefficient
    const val COMPATIBILITY_THRESHOLD: Float = 3.5f // Threshold for speciation

    // Neural Network specific
    val NUM_NN_INPUTS: Int = RADAR_DEGREES.size
    const val NUM_NN_OUTPUTS: Int = 2 // Steering: turn left, turn right

    // Mutation Power and Rates
    const val WEIGHT_MUTATION_POWER: Float = 0.5f // How much weights are perturbed
    const val WEIGHT_REPLACE_RATE: Float = 0.1f // Probability of replacing a weight

    // Speciation and Reproduction
    const val MAX_STAGNATION_GENERATIONS: Int = 25
    const val MIN_SPECIES_COUNT: Int = 2
    const val SPECIES_ELITISM: Int = 1
    const val SPECIES_SURVIVAL_THRESHOLD_PERCENT: Int = 20

    // Crossover specific
    const val REENABLE_MUTATION_RATE: Float = 0.75f

    // Fitness Rewards and Penalties
    const val MOVEMENT_REWARD: Float = 0.001f
    const val PROGRESS_REWARD_SCALE: Float = 0.01f
    const val CHECKPOINT_REWARD: Float = 50.0f
    const val LAP_REWARD: Float = 500.0f
    const val WIN_REWARD: Float = 10000.0f

    // Penalties
    const val STUCK_CAR_THRESHOLD_SPEED: Float = 0.05f * 60f
    const val STUCK_CAR_PENALTY: Float = -1000.0f
    const val MAX_DIST_FROM_START_THRESHOLD: Float = CAR_WIDTH * 3f
    const val STUCK_NO_MOVEMENT_PENALTY: Float = -500.0f
    const val MAX_TIME_BETWEEN_CHECKPOINTS: Float = 10f // seconds
    const val STUCK_TIME_PENALTY: Float = -50.0f


    // UI Colors
    const val HOME_SCREEN_BG_COLOR = 0x1E1E32FF.toInt()
    const val HOME_TEXT_COLOR      = 0xDCDCFFFF.toInt()
    const val END_SCREEN_BG_COLOR  = 0x28283EFF.toInt()

    // Simulation Limits
    const val MAX_NEAT_GENERATIONS = 200
}
