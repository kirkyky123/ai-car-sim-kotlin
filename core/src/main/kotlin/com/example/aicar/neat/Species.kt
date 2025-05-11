package com.example.aicar.neat

/**
 * Represents a species: a group of similar genomes.
 */
class Species(
    /** The representative genome for this species. Can change. */
    var representative: Genome
) {
    val members: MutableList<Genome> = mutableListOf()

    var bestFitnessEver: Float = 0f
        private set

    var staleGenerations: Int = 0
        private set

    var expectedOffspring: Int = 0

    private var sumOfAdjustedFitnesses: Double = 0.0

    init {
        addMember(representative) // The representative is initially the first member
        bestFitnessEver = representative.fitness
    }

    /**
     * Checks if a genome is compatible with this species.
     * @param genome The genome to check.
     * @param neatAlgorithmInstance Instance of NEATAlgorithm to use for compatibility distance calculation.
     * @return True if compatible, false otherwise.
     */
    fun isCompatible(genome: Genome, neatAlgorithmInstance: NEATAlgorithm): Boolean {
        val distance = neatAlgorithmInstance.compatibilityDistance(genome, representative)
        return distance < Config.COMPATIBILITY_THRESHOLD
    }

    /**
     * Adds a genome to this species.
     * @param genome The genome to add.
     */
    fun addMember(genome: Genome) {
        members.add(genome)
    }

    /**
     * Calculates adjusted fitness for all members and their sum.
     * Adjusted fitness = raw_fitness / number_of_members_in_species.
     */
    fun calculateAdjustedFitnesses() {
        sumOfAdjustedFitnesses = 0.0
        if (members.isEmpty()) return

        val numMembers = members.size.toDouble()
        members.forEach { genome ->
            sumOfAdjustedFitnesses += genome.fitness.toDouble() / numMembers
        }
    }

    /**
     * @return The average adjusted fitness of this species.
     */
    fun getAverageAdjustedFitness(): Double {
        return if (members.isNotEmpty()) sumOfAdjustedFitnesses / members.size else 0.0
    }

    /**
     * Updates the species' best fitness and increments staleness if no improvement.
     */
    fun updateStalenessAndBestFitness() {
        val maxFitnessInSpeciesCurrentGen = members.maxOfOrNull { it.fitness } ?: 0f
        if (maxFitnessInSpeciesCurrentGen > bestFitnessEver) {
            bestFitnessEver = maxFitnessInSpeciesCurrentGen
            staleGenerations = 0
        } else {
            staleGenerations++
        }
    }

    /**
     * Prepares the species for the next generation.
     * A new representative might be chosen, and members list is cleared.
     */
    fun resetForNextGeneration() {
        if (members.isNotEmpty()) {
            // Choose a new representative randomly from current members
            representative = members.random().copy()
        }
        members.clear()
        expectedOffspring = 0
        sumOfAdjustedFitnesses = 0.0
    }
}
