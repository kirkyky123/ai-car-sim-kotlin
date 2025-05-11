package com.example.aicar.neat

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.example.aicar.models.Car
import com.example.aicar.models.NeuralNetwork

/**
 * Manages a generation of Cars, each with a NEAT-evolved brain.
 */
class Population {
    /** Raw genomes for NEAT evolution */
    val genomes: MutableList<Genome> = mutableListOf()

    /** All AI-controlled cars in the current generation, linked to genomes */
    val cars: MutableList<Car> = mutableListOf()

    /** Current generation number */
    var generation: Int = 0
        internal set

    /**
     * Initializes the population with cars, each getting a NeuralNetwork from a new Genome.
     * @param mapPixmap Pixmap of the track for collision detection.
     * @param worldHeight Height of the game world.
     * @param aiCarSpriteSheet Texture sheet for AI cars.
     */
    fun initialize(mapPixmap: Pixmap, worldHeight: Float, aiCarSpriteSheet: Texture) {
        cars.clear()
        genomes.clear()
        generation = 0

        val startX = Config.LIBGDX_START_X
        val startY = Config.LIBGDX_START_Y

        repeat(Config.POPULATION_SIZE) {
            val genome = Genome()
            // Initial genomes typically start with no connections or a predefined minimal set.
            // Connections are added via mutation.
            genomes.add(genome)

            val brain = NeuralNetwork(genome)
            cars.add(
                Car(
                    x = startX,
                    y = startY,
                    spriteSheet = aiCarSpriteSheet,
                    brain = brain,
                    mapPixmap = mapPixmap,
                    worldHeight = worldHeight
                )
            )
        }
    }

    /**
     * Replaces current cars with a new set based on evolved genomes.
     * Called by NEATAlgorithm after a generation's evolution.
     * @param mapPixmap Pixmap of the track for collision detection.
     * @param worldHeight Height of the game world.
     * @param aiCarSpriteSheet Texture sheet for AI cars.
     */
    fun createCarsFromGenomes(mapPixmap: Pixmap, worldHeight: Float, aiCarSpriteSheet: Texture) {
        cars.clear()

        val startX = Config.LIBGDX_START_X
        val startY = Config.LIBGDX_START_Y

        genomes.forEach { genome ->
            val brain = NeuralNetwork(genome)
            cars.add(
                Car(
                    x = startX,
                    y = startY,
                    spriteSheet = aiCarSpriteSheet,
                    brain = brain,
                    mapPixmap = mapPixmap,
                    worldHeight = worldHeight
                )
            )
        }
    }
}
