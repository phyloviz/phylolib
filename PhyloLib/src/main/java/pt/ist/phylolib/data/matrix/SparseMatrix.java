package pt.ist.phylolib.data.matrix;

import java.util.Arrays;

public final class SparseMatrix extends Matrix {

    private final int[][] colIndices;
    private final double[][] values;
    private final int size;
    private final boolean symmetric;

    public SparseMatrix(boolean symmetric, String[] ids, int[][] colIndices, double[][] values) {
        super(symmetric, ids, (double[][]) null); // Pass null to parent
        this.size = ids.length;
        this.colIndices = colIndices;
        this.values = values;
        this.symmetric = symmetric;
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