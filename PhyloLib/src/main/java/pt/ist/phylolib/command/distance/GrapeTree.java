package pt.ist.phylolib.command.distance;

import pt.ist.phylolib.data.dataset.Dataset;
import pt.ist.phylolib.data.dataset.Profile;
import pt.ist.phylolib.data.matrix.Matrix;

/**
 * Responsible for calculating a {@link Matrix distance matrix} from a {@link Dataset phylogenetic dataset} using the GrapeTree distance calculation.
 */
public final class GrapeTree extends Distance {

	@Override
	protected boolean symmetric() {
		return false;
	}

	@Override
	protected double distance(Profile i, Profile j) {
		double differences = 0;
		double nonmissing = 0;
		for (int l = 0; l < i.size(); l++) {
			if (j.locus(l) != null) {
				nonmissing++;
				if (i.locus(l) == null || !i.locus(l).equals(j.locus(l)))
					differences++;
			}
		}
		return differences / nonmissing;
	}

}