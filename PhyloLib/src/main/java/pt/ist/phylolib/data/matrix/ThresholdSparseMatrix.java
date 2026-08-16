package pt.ist.phylolib.data.matrix;

import java.util.Arrays;

/**
 * A threshold-filtered distance matrix. Values above the retained threshold
 * are unavailable and therefore read as positive infinity.
 */
public final class ThresholdSparseMatrix extends Matrix {

    private final int[][] colIndices;
    private final double[][] values;
    private final int size;
    private final boolean symmetric;
    private final double retainedThreshold;

    public ThresholdSparseMatrix(boolean symmetric, String[] ids, int[][] colIndices, double[][] values,
                                 double retainedThreshold) {
        super(symmetric, ids, (double[][]) null, new DistanceScope.Bounded(retainedThreshold));
        this.size = ids.length;
        this.colIndices = colIndices;
        this.values = values;
        this.symmetric = symmetric;
        this.retainedThreshold = retainedThreshold;
    }

    public double retainedThreshold() {
        return retainedThreshold;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public double distance(int i, int j) {
        if (i == j)
            return 0;
        if (symmetric) {
            int temp = i;
            i = Math.max(i, j);
            j = Math.min(temp, j);
        }

        int k = Arrays.binarySearch(colIndices[i], j);
        return k >= 0 ? values[i][k] : Double.POSITIVE_INFINITY;
    }
}
