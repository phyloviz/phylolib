package pt.ist.phylolib.command.algorithm.nj;

import pt.ist.phylolib.data.matrix.Matrix;
import pt.ist.phylolib.data.tree.Tree;

/**
 * Responsible for calculating a {@link Tree phylogenetic tree} from a {@link Matrix distance matrix} using the Neighbour Joining algorithm by Studier and Kepler.
 */
public final class StudierKepler extends NeighbourJoining {

	@Override
	protected int weight(Cluster i) {
		return 1;
	}

	@Override
	protected double lambda(Cluster ci, Cluster cj) {
		return 0.5;
	}

	@Override
	protected double length(double distance) {
		return distance;
	}

}
