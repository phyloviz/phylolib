package pt.ist.phylolib.command.algorithm;

import pt.ist.phylolib.command.ICommand;
import pt.ist.phylolib.data.matrix.Matrix;
import pt.ist.phylolib.data.matrix.SparseMatrix;
import pt.ist.phylolib.data.tree.Tree;
import pt.ist.phylolib.logging.Log;

/**
 * Responsible for calculating a {@link Tree phylogenetic tree} from a
 * {@link Matrix distance matrix}.
 */
public abstract class Algorithm implements ICommand<Matrix, Tree> {

    /**
     * Indicates whether this algorithm supports sparse matrices.
     * Override to return false for algorithms that require dense matrices.
     * 
     * @return true if sparse matrices are supported, false otherwise
     */
    public boolean supportsSparseMatrix() {
        return true; // Default: assume sparse support (GoeBURST and Edmonds)
    }

    @Override
    public Tree process(Matrix matrix) {
        // Validate sparse matrix compatibility
        if (matrix instanceof SparseMatrix && !supportsSparseMatrix()) {
            String algorithmName = this.getClass().getSimpleName();
            Log.error("SPARSE",
                    "Algorithm %s does not support sparse matrices. Please remove the -s flag or use a different algorithm.",
                    algorithmName);
            throw new UnsupportedOperationException(
                    String.format(
                            "Algorithm %s requires a dense matrix. Sparse matrices (using -s flag) are not compatible with this algorithm.",
                            algorithmName));
        }

        return processImpl(matrix);
    }

    /**
     * Processes the matrix to create a phylogenetic tree.
     * Subclasses should implement their specific algorithm logic here.
     * 
     * @param matrix the distance matrix
     * @return the computed phylogenetic tree
     */
    protected abstract Tree processImpl(Matrix matrix);

}
