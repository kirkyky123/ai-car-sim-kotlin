package com.example.aicar.neat

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.example.aicar.models.Car
import kotlin.math.abs
import kotlin.random.Random

class NEATAlgorithm {
    // Global counters for innovation numbers and node IDs.
    private var globalInnovationCounter: Int = 0
    private var globalNodeIdCounter: Int = Config.NUM_NN_INPUTS + Config.NUM_NN_OUTPUTS

    private val speciesList: MutableList<Species> = mutableListOf()

    /**
     * Resets the global NEAT state. Call before starting a new evolution process.
     */
    fun resetGlobalState() {
        globalInnovationCounter = 0
        globalNodeIdCounter = Config.NUM_NN_INPUTS + Config.NUM_NN_OUTPUTS
        speciesList.clear()
        Gdx.app.log("NEATAlgorithm", "Global NEAT state reset.")
    }

    /**
     * Initializes the population and creates initial cars.
     * @param mapPixmap The track Pixmap for collision detection.
     * @param worldHeight Height of the game world.
     * @param aiCarSpriteSheet Texture sheet for AI cars.
     * @return Initialized Population object.
     */
    fun initializePopulation(mapPixmap: Pixmap, worldHeight: Float, aiCarSpriteSheet: Texture): Population {
        resetGlobalState()
        val population = Population()
        population.initialize(mapPixmap, worldHeight, aiCarSpriteSheet)
        speciate(population)
        Gdx.app.log("NEATAlgorithm", "Population initialized and speciated.")
        return population
    }

    /**
     * Evolves the population for one generation.
     * Involves fitness evaluation, speciation, reproduction, and mutation.
     * @param population The Population to evolve.
     */
    fun evolvePopulation(population: Population) {
        // Transfer fitness from cars to their genomes.
        if (population.genomes.size != population.cars.size) {
            Gdx.app.error("NEATAlgorithm", "Genome count (${population.genomes.size}) != car count (${population.cars.size}) before fitness transfer.")
        }
        population.genomes.forEachIndexed { index, genome ->
            if (index < population.cars.size) {
                val car = population.cars[index]
                if (car.brain != null) {
                    genome.fitness = car.fitness
                } else {
                    genome.fitness = 0f // Low fitness for genomes without a car
                }
            } else {
                genome.fitness = 0f // Low fitness for excess genomes
            }
        }
        Gdx.app.log("NEATAlgorithm", "Fitness transferred to genomes.")

        speciate(population)
        Gdx.app.log("NEATAlgorithm", "Population speciated into ${speciesList.size} species.")

        speciesList.forEach { it.updateStalenessAndBestFitness() }

        val initialSpeciesCount = speciesList.size
        speciesList.removeAll { it.staleGenerations >= Config.MAX_STAGNATION_GENERATIONS && speciesList.size > Config.MIN_SPECIES_COUNT }
        if (speciesList.size < initialSpeciesCount) {
            Gdx.app.log("NEATAlgorithm", "Removed ${initialSpeciesCount - speciesList.size} stale species. Remaining: ${speciesList.size}")
        }

        calculateSpeciesOffspring(population)
        Gdx.app.log("NEATAlgorithm", "Calculated species offspring.")

        reproduce(population)
        Gdx.app.log("NEATAlgorithm", "Reproduced next generation.")

        mutate(population)
        Gdx.app.log("NEATAlgorithm", "Mutated next generation.")

        population.generation++
        Gdx.app.log("NEATAlgorithm", "Completed generation ${population.generation - 1}. Starting generation ${population.generation}.")
    }

    /**
     * Assigns genomes to species based on compatibility distance.
     * @param population The Population containing genomes to speciate.
     */
    private fun speciate(population: Population) {
        speciesList.forEach { it.resetForNextGeneration() }

        for (genome in population.genomes) {
            var placed = false
            for (s in speciesList) {
                if (s.isCompatible(genome, this)) {
                    s.addMember(genome)
                    placed = true
                    break
                }
            }
            if (!placed) {
                speciesList.add(Species(genome.copy()))
            }
        }
        speciesList.removeAll { it.members.isEmpty() }
    }

    /**
     * Calculates expected offspring for each species based on adjusted fitness.
     * @param population The Population object.
     */
    private fun calculateSpeciesOffspring(population: Population) {
        speciesList.forEach { it.calculateAdjustedFitnesses() }
        val totalAverageAdjustedFitness = speciesList.sumOf { it.getAverageAdjustedFitness() }
        var numTotalOffspring = 0

        if (totalAverageAdjustedFitness > 0.00001) {
            speciesList.forEach { s ->
                val offspringForSpecies = (s.getAverageAdjustedFitness() / totalAverageAdjustedFitness * Config.POPULATION_SIZE).toInt()
                s.expectedOffspring = offspringForSpecies
                numTotalOffspring += offspringForSpecies
            }
        } else if (speciesList.isNotEmpty()) {
            val offspringPerSpecies = Config.POPULATION_SIZE / speciesList.size
            speciesList.forEach { it.expectedOffspring = offspringPerSpecies }
            numTotalOffspring = offspringPerSpecies * speciesList.size
        }

        val sortedSpecies = speciesList.sortedByDescending { it.getAverageAdjustedFitness() }
        var speciesIdx = 0
        while (numTotalOffspring < Config.POPULATION_SIZE && sortedSpecies.isNotEmpty()) {
            sortedSpecies[speciesIdx % sortedSpecies.size].expectedOffspring++
            numTotalOffspring++
            speciesIdx++
        }
        speciesList.forEach { it.expectedOffspring = maxOf(0, it.expectedOffspring) }
    }

    /**
     * Selects a genome using tournament selection.
     * @param pool List of genomes to select from.
     * @param k Tournament size.
     * @return The winning (fittest) genome.
     */
    private fun tournamentSelect(pool: List<Genome>, k: Int = 3): Genome {
        if (pool.isEmpty()) {
            Gdx.app.error("NEATAlgorithm", "Tournament selection on empty pool!")
            return Genome() // Fallback
        }
        val tournamentParticipants = List(k) { pool.random() }
        return tournamentParticipants.maxByOrNull { it.fitness } ?: pool.first()
    }

    /**
     * Creates the next generation of genomes via elitism and crossover.
     * @param population The Population to update.
     */
    private fun reproduce(population: Population) {
        val nextGenerationGenomes = mutableListOf<Genome>()

        speciesList.forEach { sp ->
            if (sp.members.isNotEmpty() && sp.expectedOffspring > 0) {
                sp.members.sortByDescending { it.fitness }
                val numElites = Config.SPECIES_ELITISM.coerceAtMost(sp.members.size).coerceAtMost(sp.expectedOffspring)
                for (i in 0 until numElites) {
                    if (i < sp.members.size) {
                        nextGenerationGenomes.add(sp.members[i].copy())
                    }
                }

                val breedingPoolSize = (sp.members.size * Config.SPECIES_SURVIVAL_THRESHOLD_PERCENT / 100.0).toInt().coerceAtLeast(1)
                val breedingPool = sp.members.take(breedingPoolSize)
                val numOffspringToCreate = sp.expectedOffspring - numElites

                if (breedingPool.size < 2 && numOffspringToCreate > 0) {
                    repeat(numOffspringToCreate) {
                        if (breedingPool.isNotEmpty()) nextGenerationGenomes.add(breedingPool.first().copy())
                    }
                } else {
                    repeat(numOffspringToCreate) {
                        if (breedingPool.isEmpty()) return@repeat // Should not happen
                        val parent1 = tournamentSelect(breedingPool)
                        val parent2 = tournamentSelect(breedingPool)
                        nextGenerationGenomes.add(crossover(parent1, parent2))
                    }
                }
            }
        }

        val sortedAllPreviousGenomes = population.genomes.sortedByDescending { it.fitness }
        var bestParentIdx = 0
        while (nextGenerationGenomes.size < Config.POPULATION_SIZE) {
            if (sortedAllPreviousGenomes.isNotEmpty()) {
                val parent = sortedAllPreviousGenomes[bestParentIdx % sortedAllPreviousGenomes.size]
                nextGenerationGenomes.add(parent.copy())
                bestParentIdx++
            } else {
                Gdx.app.error("NEATAlgorithm", "Reproduce fallback: Creating new default genome.")
                nextGenerationGenomes.add(Genome()) // Fallback
            }
        }

        population.genomes.clear()
        population.genomes.addAll(nextGenerationGenomes.take(Config.POPULATION_SIZE))
    }

    private val connectionMutationRate = Config.MUTATION_RATE * 0.5f
    private val nodeMutationRate = Config.MUTATION_RATE * 0.3f

    /**
     * Mutates genomes: applies weight and structural mutations.
     * @param population The Population with genomes to mutate.
     */
    private fun mutate(population: Population) {
        population.genomes.forEach { genome ->
            genome.mutateWeights(Config.MUTATION_RATE, Config.WEIGHT_REPLACE_RATE)
            if (Random.nextFloat() < connectionMutationRate) {
                genome.mutateAddConnection { globalInnovationCounter++ }
            }
            if (Random.nextFloat() < nodeMutationRate) {
                genome.mutateAddNode(
                    getNextInnovationId = { globalInnovationCounter++ },
                    getNextNodeId = { globalNodeIdCounter++ }
                )
            }
        }
    }

    /**
     * Calculates compatibility distance between two genomes.
     * @param g1 First genome.
     * @param g2 Second genome.
     * @return Compatibility distance.
     */
    fun compatibilityDistance(g1: Genome, g2: Genome): Float {
        val connections1 = g1.connections.sortedBy { it.innovationID }
        val connections2 = g2.connections.sortedBy { it.innovationID }
        var excessGenes = 0
        var disjointGenes = 0
        var weightDifferenceSum = 0f
        var matchingGenes = 0
        var i1 = 0
        var i2 = 0
        val maxInnovId1 = connections1.lastOrNull()?.innovationID ?: -1
        val maxInnovId2 = connections2.lastOrNull()?.innovationID ?: -1

        while (i1 < connections1.size || i2 < connections2.size) {
            val gene1 = if (i1 < connections1.size) connections1[i1] else null
            val gene2 = if (i2 < connections2.size) connections2[i2] else null

            when {
                gene1 != null && gene2 != null && gene1.innovationID == gene2.innovationID -> {
                    weightDifferenceSum += abs(gene1.weight - gene2.weight)
                    matchingGenes++
                    i1++; i2++
                }
                gene1 != null && (gene2 == null || gene1.innovationID < gene2.innovationID) -> {
                    if (gene1.innovationID > maxInnovId2) excessGenes++ else disjointGenes++
                    i1++
                }
                gene2 != null && (gene1 == null || gene2.innovationID < gene1.innovationID) -> {
                    if (gene2.innovationID > maxInnovId1) excessGenes++ else disjointGenes++
                    i2++
                }
                else -> break // Both lists exhausted
            }
        }

        val N = maxOf(connections1.size, connections2.size).toFloat()
        val normalizationFactor = if (N < 20 && N > 0) 1.0f else N
        val finalNormalizationFactor = if (normalizationFactor == 0f) 1.0f else normalizationFactor
        val avgWeightDiff = if (matchingGenes > 0) weightDifferenceSum / matchingGenes else 0f

        return (Config.C1 * excessGenes / finalNormalizationFactor) +
            (Config.C2 * disjointGenes / finalNormalizationFactor) +
            (Config.C3 * avgWeightDiff)
    }

    /**
     * Performs crossover between two parent genomes.
     * @param parent1 First parent.
     * @param parent2 Second parent.
     * @return Child genome.
     */
    private fun crossover(parent1: Genome, parent2: Genome): Genome {
        val (fitter, other) = if (parent1.fitness >= parent2.fitness) parent1 to parent2 else parent2 to parent1
        val child = Genome()
        child.hiddenNodes.addAll((fitter.hiddenNodes + other.hiddenNodes).distinct().toMutableList())

        val childConnections = mutableListOf<Genome.ConnectionGene>()
        val fitterConnectionsMap = fitter.connections.associateBy { it.innovationID }
        val otherConnectionsMap = other.connections.associateBy { it.innovationID }

        (fitterConnectionsMap.keys + otherConnectionsMap.keys).distinct().sorted().forEach { innovId ->
            val geneFromFitter = fitterConnectionsMap[innovId]
            val geneFromOther = otherConnectionsMap[innovId]
            var chosenGeneSource: Genome.ConnectionGene? = null

            when {
                geneFromFitter != null && geneFromOther != null -> chosenGeneSource = if (Random.nextBoolean()) geneFromFitter else geneFromOther
                geneFromFitter != null -> chosenGeneSource = geneFromFitter
                // geneFromOther != null: Disjoint/Excess from less fit parent is not inherited
            }

            chosenGeneSource?.let { sourceGene ->
                val childGeneCopy = sourceGene.copy()
                if (geneFromFitter != null && geneFromOther != null) { // Matching gene
                    if (!geneFromFitter.enabled || !geneFromOther.enabled) { // Disabled in at least one parent
                        childGeneCopy.enabled = Random.nextFloat() < Config.REENABLE_MUTATION_RATE
                    }
                }
                childConnections.add(childGeneCopy)
            }
        }
        child.connections.addAll(childConnections)
        child.connections.sortBy { it.innovationID }
        return child
    }
}
