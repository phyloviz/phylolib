package pt.ist.phylolib.command.algorithm.edmonds;

public final class WeightedDisjointSet extends DisjointSet {

	private final double[] weight;

	public WeightedDisjointSet(int n) {
		super(n);
		this.weight = new double[this.size];
	}

	private double findWeight(int i) {
		return i != pi[i] ? weight[i] + weight[pi[i]] : weight[i];
	}

	private void addWeight(int i, double w) {
		weight[findSet(i)] += w;
	}

	@Override
	public int findSet(int i) {
		for (; pi[i] != pi[pi[i]]; i = pi[i]) {
			weight[i] += weight[pi[i]];
			pi[i] = pi[pi[i]];
		}
		return pi[i];
	}

	@Override
	public void unionSet(int i, int j) {
		i = findSet(i);
		j = findSet(j);
		if (i != j) {
			if (rank[i] > rank[j])
				pi[j] = i;
			else {
				pi[i] = j;
				if (rank[i] == rank[j])
					rank[j]++;
			}
			weight[i] -= weight[j];
		}
	}

}
