package pt.ist.phylolib.command.algorithm.gcp;

import pt.ist.phylolib.command.algorithm.Algorithm;
import pt.ist.phylolib.data.matrix.Matrix;
import pt.ist.phylolib.data.tree.Edge;
import pt.ist.phylolib.data.tree.Tree;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Responsible for calculating a {@link Tree phylogenetic tree} from a
 * {@link Matrix distance matrix} using the Globally Closest Pairs algorithm.
 */
public abstract class GloballyClosestPairs extends Algorithm {

	private int cluster;
	private Map<Integer, Cluster> clusters;
	private PriorityQueue<Edge> edges;

	@Override
	public boolean supportsSparseMatrix() {
		return false; // GCP algorithms (UPGMA, WPGMA, etc.) require dense matrices
	}

	@Override
	protected Tree processImpl(Matrix matrix) {
		Tree tree = init(matrix);
		while (clusters.size() > 1) {
			Edge edge = select();
			Clusters clusters = join(edge, tree);
			reduce(edge, clusters.i, clusters.j);
		}
		return tree;
	}

	private Tree init(Matrix matrix) {
		int size = matrix.size();
		this.cluster = size;
		this.clusters = new HashMap<>(size);
		this.edges = new PriorityQueue<>(size * (size - 1) / 2,
				Comparator.comparingDouble(Edge::distance).thenComparing(this::tiebreak));
		for (int i = 0; i < size; i++) {
			Cluster cluster = new Cluster(1, 0);
			this.clusters.put(i, cluster);
			Map<Integer, Double> distances = cluster.distances;
			for (int j = i + 1; j < size; j++) {
				double distance = Math.min(matrix.distance(i, j), matrix.distance(j, i));
				distances.put(j, distance);
				this.edges.add(new Edge(i, j, distance));
			}
		}
		return new Tree(matrix.ids());
	}

	private int tiebreak(Edge edge) {
		return !clusters.containsKey(edge.from()) || !clusters.containsKey(edge.to())
				? 0
				: -(clusters.get(edge.from()).elements + clusters.get(edge.to()).elements);
	}

	private Edge select() {
		Edge edge = edges.remove();
		while (!clusters.containsKey(edge.from()) || !clusters.containsKey(edge.to()))
			edge = edges.remove();
		return edge;
	}

	private Clusters join(Edge edge, Tree tree) {
		Cluster ci = clusters.remove(edge.from());
		Cluster cj = clusters.remove(edge.to());
		double branch = edge.distance() / 2;
		tree.add(new Edge(cluster, edge.from(), branch - ci.offset));
		tree.add(new Edge(cluster, edge.to(), branch - cj.offset));
		return new Clusters(ci, cj);
	}

	private void reduce(Edge edge, Cluster ci, Cluster cj) {
		int i = edge.from();
		int j = edge.to();
		int u = cluster++;
		clusters.forEach((k, cluster) -> {
			Map<Integer, Double> kd = cluster.distances;
			double ik = k < i ? kd.remove(i) : ci.distances.remove(k);
			double jk = k < j ? kd.remove(j) : cj.distances.remove(k);
			double dissimilarity = dissimilarity(edge.distance(), ik, jk, ci.elements, cj.elements);
			kd.put(u, dissimilarity);
			edges.add(new Edge(k, u, dissimilarity));
		});
		clusters.put(u, new Cluster(ci.elements + cj.elements, edge.distance() / 2));
	}

	/**
	 * Calculates the dissimilarity between a given previously existing node and a
	 * given node created by joining two existing nodes.
	 *
	 * @param ij the distance between the two existing nodes that were joined
	 * @param ik the distance between one of the nodes that was joined and the
	 *           previously existing node
	 * @param jk the distance between another of the nodes that was joined and the
	 *           previously existing node
	 * @param ci the number of elements in the cluster of one of the nodes that were
	 *           joined
	 * @param cj the number of elements in the cluster of another of the nodes that
	 *           were joined
	 *
	 * @return the dissimilarity between the previously existing node and the
	 *         created node
	 */
	protected abstract double dissimilarity(double ij, double ik, double jk, int ci, int cj);

	private static final class Clusters {

		private final Cluster i;
		private final Cluster j;

		private Clusters(Cluster i, Cluster j) {
			this.i = i;
			this.j = j;
		}

	}

	private final class Cluster {

		private final int elements;
		private final double offset;
		private final Map<Integer, Double> distances;

		private Cluster(int elements, double offset) {
			this.elements = elements;
			this.offset = offset;
			this.distances = new HashMap<>(clusters.size());
		}

	}

}
