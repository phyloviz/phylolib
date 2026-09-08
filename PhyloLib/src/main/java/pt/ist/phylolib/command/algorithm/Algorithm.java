package pt.ist.phylolib.command.algorithm;

import pt.ist.phylolib.command.ICommand;
import pt.ist.phylolib.cli.Options;
import pt.ist.phylolib.data.matrix.DistanceScope;
import pt.ist.phylolib.data.matrix.Matrix;
import pt.ist.phylolib.data.tree.Tree;

/**
 * Responsible for calculating a {@link Tree phylogenetic tree} from a
 * {@link Matrix distance matrix}.
 */
public abstract class Algorithm implements ICommand<Matrix, Tree> {

    /**
     * Declares the distance scope this algorithm needs. Complete is the
     * conservative default until an algorithm proves a bounded scope.
     */
    public DistanceScope requiredDistanceScope() {
        return DistanceScope.Complete.INSTANCE;
    }

    /**
     * Configures a scope that depends on user input before the matrix is
     * loaded. Most algorithms have a fixed requirement and do nothing here.
     */
    public void configureRequiredDistanceScope(Options options) {
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
        if (!matrix.distanceScope().covers(requiredDistanceScope()))
            throw new IllegalArgumentException("Algorithm " + getClass().getSimpleName() + " requires "
                    + requiredDistanceScope() + " distance scope but matrix provides " + matrix.distanceScope() + ".");

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
