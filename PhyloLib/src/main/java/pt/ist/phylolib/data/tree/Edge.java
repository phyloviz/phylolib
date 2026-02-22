package pt.ist.phylolib.data.tree;

/**
 * Represents an edge as from and to nodes with a distance between them.
 * <p>
 * Java 21 Record: Provides immutability, automatic equals/hashCode/toString,
 * and eliminates boilerplate code.
 *
 * @param from     the node where this edge starts
 * @param to       the node where this edge ends
 * @param distance the distance between the two given nodes
 */
public record Edge(int from, int to, double distance) {
}
