package com.example.aicar.neat

import kotlin.random.Random

/**
 * Represents one candidate solution: a network’s genome.
 * Contains lists of node genes and connection genes.
 */
class Genome {
    /** Node IDs by layer: input, hidden, output */
    val inputNodes: MutableList<Int> = mutableListOf()
    val hiddenNodes: MutableList<Int> = mutableListOf()
    val outputNodes: MutableList<Int> = mutableListOf()

    /** Connection gene data: (inNode, outNode) → weight, enabled, innovationID */
    data class ConnectionGene(
        val inNode: Int,
        val outNode: Int,
        var weight: Float,
        var enabled: Boolean,
        val innovationID: Int
    ) {
        fun copy(): ConnectionGene {
            return ConnectionGene(inNode, outNode, weight, enabled, innovationID)
        }
    }

    val connections: MutableList<ConnectionGene> = mutableListOf()

    /** Fitness score assigned after evaluating the genome */
    var fitness: Float = 0f

    init {
        var nodeIdCounter = 0
        repeat(Config.NUM_NN_INPUTS) {
            inputNodes.add(nodeIdCounter++)
        }
        repeat(Config.NUM_NN_OUTPUTS) {
            outputNodes.add(nodeIdCounter++)
        }
    }

    /**
     * Creates a deep copy of this genome.
     */
    fun copy(): Genome {
        val clone = Genome()
        clone.hiddenNodes.addAll(hiddenNodes)
        connections.forEach {
            clone.connections.add(it.copy())
        }
        clone.fitness = fitness
        return clone
    }

    /**
     * Mutates weights by small random perturbations or replaces them.
     */
    fun mutateWeights(mutateRate: Float = Config.MUTATION_RATE, replaceRate: Float = Config.WEIGHT_REPLACE_RATE) {
        connections.forEach { conn ->
            if (Random.nextFloat() < mutateRate) {
                if (Random.nextFloat() < replaceRate) {
                    conn.weight = Random.nextFloat() * 2f - 1f // Replace weight
                } else {
                    conn.weight += (Random.nextFloat() * 2f - 1f) * Config.WEIGHT_MUTATION_POWER // Perturb weight
                }
            }
        }
    }

    /**
     * Adds a new connection between two random, valid, and unconnected nodes.
     * Ensures connection is feed-forward.
     * @param getNextInnovationId Lambda to provide the next global innovation ID.
     */
    fun mutateAddConnection(getNextInnovationId: () -> Int) {
        val possibleSources = inputNodes + hiddenNodes
        val possibleDestinations = hiddenNodes + outputNodes
        val potentialNewPairs = mutableListOf<Pair<Int,Int>>()
        val existingPairs = connections.map { it.inNode to it.outNode }.toSet()

        for (inNode in possibleSources) {
            for (outNode in possibleDestinations) {
                if (inNode != outNode && inNode < outNode) { // Feed-forward and no self-connection
                    val potentialPair = inNode to outNode
                    if (potentialPair !in existingPairs) {
                        potentialNewPairs.add(potentialPair)
                    }
                }
            }
        }

        if (potentialNewPairs.isEmpty()) return

        val (inN, outN) = potentialNewPairs.random()
        connections.add(
            ConnectionGene(
                inNode       = inN,
                outNode      = outN,
                weight       = Random.nextFloat() * 2f - 1f,
                enabled      = true,
                innovationID = getNextInnovationId()
            )
        )
    }

    /**
     * Adds a new node by splitting an existing enabled connection.
     * @param getNextInnovationId Lambda to provide the next global innovation ID.
     * @param getNextNodeId Lambda to provide the next global node ID.
     */
    fun mutateAddNode(getNextInnovationId: () -> Int, getNextNodeId: () -> Int) {
        if (connections.isEmpty()) return

        val enabledConnections = connections.filter { it.enabled }
        if (enabledConnections.isEmpty()) return

        val conn = enabledConnections.random()
        conn.enabled = false

        val newNodeId = getNextNodeId()
        hiddenNodes.add(newNodeId)

        connections.add(
            ConnectionGene(
                inNode       = conn.inNode,
                outNode      = newNodeId,
                weight       = 1f, // Standard NEAT: weight is 1.0 for first part of split
                enabled      = true,
                innovationID = getNextInnovationId()
            )
        )
        connections.add(
            ConnectionGene(
                inNode       = newNodeId,
                outNode      = conn.outNode,
                weight       = conn.weight, // Standard NEAT: new connection gets original weight
                enabled      = true,
                innovationID = getNextInnovationId()
            )
        )
    }

    /**
     * Calculates the number of excess genes compared to another genome.
     * Assumes connection lists are sorted by innovation ID.
     */
    fun countExcessGenes(other: Genome): Int {
        val connections1 = this.connections.sortedBy { it.innovationID }
        val connections2 = other.connections.sortedBy { it.innovationID }
        var excessGenes = 0
        var i1 = 0
        var i2 = 0
        val maxInnovId1 = connections1.lastOrNull()?.innovationID ?: -1
        val maxInnovId2 = connections2.lastOrNull()?.innovationID ?: -1

        while (i1 < connections1.size && i2 < connections2.size) {
            val gene1 = connections1[i1]
            val gene2 = connections2[i2]
            when {
                gene1.innovationID == gene2.innovationID -> { i1++; i2++ }
                gene1.innovationID < gene2.innovationID -> {
                    if (gene1.innovationID > maxInnovId2) excessGenes++
                    i1++
                }
                else -> { // gene2.innovationID < gene1.innovationID
                    if (gene2.innovationID > maxInnovId1) excessGenes++
                    i2++
                }
            }
        }
        excessGenes += (connections1.size - i1)
        excessGenes += (connections2.size - i2)
        return excessGenes
    }

    /**
     * Calculates the number of disjoint genes compared to another genome.
     * Assumes connection lists are sorted by innovation ID.
     */
    fun countDisjointGenes(other: Genome): Int {
        val connections1 = this.connections.sortedBy { it.innovationID }
        val connections2 = other.connections.sortedBy { it.innovationID }
        var disjointGenes = 0
        var i1 = 0
        var i2 = 0
        val maxInnovId1 = connections1.lastOrNull()?.innovationID ?: -1
        val maxInnovId2 = connections2.lastOrNull()?.innovationID ?: -1

        while (i1 < connections1.size && i2 < connections2.size) {
            val gene1 = connections1[i1]
            val gene2 = connections2[i2]
            when {
                gene1.innovationID == gene2.innovationID -> { i1++; i2++ }
                gene1.innovationID < gene2.innovationID -> {
                    if (gene1.innovationID <= maxInnovId2) disjointGenes++
                    i1++
                }
                else -> { // gene2.innovationID < gene1.innovationID
                    if (gene2.innovationID <= maxInnovId1) disjointGenes++
                    i2++
                }
            }
        }
        return disjointGenes
    }

    /**
     * Calculates the average weight difference of matching genes.
     * Assumes connection lists are sorted by innovation ID.
     */
    fun calculateAverageWeightDifference(other: Genome): Float {
        val connections1 = this.connections.sortedBy { it.innovationID }
        val connections2 = other.connections.sortedBy { it.innovationID }
        var weightDifferenceSum = 0f
        var matchingGenes = 0
        var i1 = 0
        var i2 = 0

        while (i1 < connections1.size && i2 < connections2.size) {
            val gene1 = connections1[i1]
            val gene2 = connections2[i2]
            if (gene1.innovationID == gene2.innovationID) {
                weightDifferenceSum += kotlin.math.abs(gene1.weight - gene2.weight)
                matchingGenes++
                i1++
                i2++
            } else if (gene1.innovationID < gene2.innovationID) {
                i1++
            } else {
                i2++
            }
        }
        return if (matchingGenes > 0) weightDifferenceSum / matchingGenes else 0f
    }
}
