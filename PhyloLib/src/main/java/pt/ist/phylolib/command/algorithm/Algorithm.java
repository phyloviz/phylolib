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

    /**
     * Indicates whether this algorithm requires an externally provided matrix
     * (from a preceding distance command or the --matrix option) as its input.
     * Override to return false for algorithms that build their own matrix
     * during init (e.g. Edmonds builds a MemoryMappedMatrix from --input).
     *
     * @return true if an external matrix is required, false otherwise
     */
    public boolean requiresMatrix() {
        return true;
    }

    @Override
    public Tree process(Matrix matrix) {
        // Validate sparse matrix compatibility
        if (matrix instanceof SparseMatrix && !supportsSparseMatrix()) {
            String algorithmName = this.getClass().getSimpleName();
            Log.error("SPARSE_UNSUPPORTED",
                    "Algorithm %s does not support sparse matrices. Use --force-dense flag to load a dense matrix instead.",
                    algorithmName);
            throw new UnsupportedOperationException(
                    String.format(
                            "Algorithm %s requires a dense matrix. The matrix was automatically loaded as sparse due to its size. Use --force-dense to override.",
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
