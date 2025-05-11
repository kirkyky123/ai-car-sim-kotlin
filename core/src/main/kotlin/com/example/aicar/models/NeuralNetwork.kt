package com.example.aicar.models

import com.badlogic.gdx.Gdx
import com.example.aicar.neat.Genome
import kotlin.math.exp

/**
 * Represents a neural network built from a Genome.
 * The network is a graph of nodes and connections.
 */
class NeuralNetwork(val genome: Genome) {

    private data class Node(
        val id: Int,
        val type: NodeType,
        var bias: Float = 0f, // Biases can be evolved; initialized to 0 here
        var activation: Float = 0f,
        val incomingConnections: MutableList<Connection> = mutableListOf()
    )

    private data class Connection(
        val fromNodeId: Int,
        val toNodeId: Int,
        val weight: Float,
        val enabled: Boolean // Though only enabled connections are typically processed
    )

    private enum class NodeType { INPUT, HIDDEN, OUTPUT }

    private val nodes: MutableMap<Int, Node> = mutableMapOf()
    private val inputNodeIds: List<Int>
    private val outputNodeIds: List<Int>
    // Hidden nodes are implicitly defined by connections and node IDs not in input/output.
    // For feed-forward, ensuring a processing order (e.g., by node ID or topological sort) is key.

    init {
        inputNodeIds = genome.inputNodes.toList()
        outputNodeIds = genome.outputNodes.toList()

        // Create all nodes defined in the genome (input, output, hidden)
        (genome.inputNodes + genome.hiddenNodes + genome.outputNodes).distinct().forEach { nodeId ->
            val type = when (nodeId) {
                in inputNodeIds -> NodeType.INPUT
                in outputNodeIds -> NodeType.OUTPUT
                else -> NodeType.HIDDEN
            }
            nodes[nodeId] = Node(nodeId, type)
        }

        // Add enabled connections from the genome to the network structure
        genome.connections.filter { it.enabled }.forEach { connGene ->
            if (nodes.containsKey(connGene.inNode) && nodes.containsKey(connGene.outNode)) {
                val connection = Connection(
                    connGene.inNode,
                    connGene.outNode,
                    connGene.weight,
                    connGene.enabled
                )
                nodes[connGene.outNode]?.incomingConnections?.add(connection)
            } else {
                // Log warning for connections to/from non-existent nodes
                Gdx.app.error("NeuralNetwork", "Connection gene references non-existent node(s): ${connGene.inNode} -> ${connGene.outNode}")
            }
        }
    }

    /**
     * Performs a feed-forward pass through the network.
     * @param inputs Input values, size must match number of input nodes.
     * @return Output values from output nodes.
     */
    fun predict(inputs: FloatArray): FloatArray {
        if (inputs.size != inputNodeIds.size) {
            throw IllegalArgumentException("Input array size (${inputs.size}) must match input nodes (${inputNodeIds.size})")
        }

        // Reset activations for all non-input nodes
        nodes.values.forEach { if (it.type != NodeType.INPUT) it.activation = 0f }

        // Set input node activations
        inputNodeIds.forEachIndexed { index, nodeId ->
            nodes[nodeId]?.activation = inputs[index]
        }

        // Propagate activations.
        // A simple processing order: inputs -> hidden (sorted by ID) -> outputs.
        // This works for feed-forward networks where node IDs are assigned sequentially.
        // For more complex topologies (e.g., with recurrent connections, though not typical in basic NEAT),
        // a topological sort or iterative activation updates would be needed.
        val processingOrder = (genome.inputNodes + genome.hiddenNodes.sorted() + genome.outputNodes).distinct()

        for (nodeId in processingOrder) {
            val node = nodes[nodeId] ?: continue // Should not happen if nodes are initialized correctly

            if (node.type == NodeType.INPUT) continue // Input activations are already set

            var sum = node.bias // Start with bias
            node.incomingConnections.forEach { connection ->
                nodes[connection.fromNodeId]?.let { fromNode ->
                    sum += fromNode.activation * connection.weight
                }
            }
            node.activation = sigmoid(sum)
        }

        // Collect output activations
        return FloatArray(outputNodeIds.size) { i ->
            nodes[outputNodeIds[i]]?.activation ?: 0f
        }
    }

    private fun sigmoid(x: Float): Float {
        return 1f / (1f + exp(-x))
    }
}
