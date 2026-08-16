package pt.ist.phylolib.data;

import pt.ist.phylolib.cli.Data;
import pt.ist.phylolib.cli.Option;
import pt.ist.phylolib.cli.Options;
import pt.ist.phylolib.command.algorithm.Algorithm;
import pt.ist.phylolib.command.ICommand;
import pt.ist.phylolib.data.dataset.Dataset;
import pt.ist.phylolib.data.matrix.DistanceScope;
import pt.ist.phylolib.data.matrix.Matrix;
import pt.ist.phylolib.data.tree.Tree;
import pt.ist.phylolib.exception.MissingInputException;

/**
 * Maintains the shared data context for the program, storing references
 * to the current {@link Dataset}, {@link Matrix}, and {@link Tree}.
 * <p>
 * Each getter performs a lazy read: if the corresponding input option is
 * present, the value is reloaded; otherwise, the previous value is reused.
 * If no previous value exists and the required option is missing, a
 * {@link MissingInputException} is thrown.
 */
public final class Context {

	private Dataset dataset;
	private Matrix matrix;
	private Tree tree;
	private ICommand<?, ?> currentCommand;

	/**
	 * Returns the dataset, updating it if a dataset option is provided.
	 *
	 * @param options the command-line options used to look up input files
	 * @return the existing dataset or a newly loaded one
	 * @throws MissingInputException if no dataset option is present and no
	 *                               previous dataset exists
	 */
	public Dataset getDataset(Options options) throws MissingInputException {
		return dataset = IReader.read(options, dataset, Data.DATASET);
	}

	/**
	 * Returns the matrix, updating it if a matrix option is provided.
	 *
	 * @param options the command-line options used to look up input files
	 * @return the existing matrix or a newly loaded one
	 * @throws MissingInputException if no matrix option is present and no
	 *                               previous matrix exists
	 */
	public Matrix getMatrix(Options options) throws MissingInputException {
		DistanceScope requiredScope = currentCommand instanceof Algorithm algorithm
				? algorithm.requiredDistanceScope()
				: DistanceScope.Complete.INSTANCE;
		boolean forceDense = Boolean.parseBoolean(options.remove(Option.FORCE_DENSE));
		return matrix = IReader.readMatrix(options, matrix, requiredScope, forceDense);
	}

	/**
	 * Sets the current command being executed.
	 * Used to obtain the active algorithm's typed matrix scope.
	 *
	 * @param command the command instance
	 */
	public void setCurrentCommand(ICommand<?, ?> command) {
		this.currentCommand = command;
	}

	/**
	 * Gets the current command being executed.
	 *
	 * @return the current command instance, or null if no command is set
	 */
	public ICommand<?, ?> getCurrentCommand() {
		return currentCommand;
	}

	/**
	 * Returns the tree, updating it if a tree option is provided.
	 *
	 * @param options the command-line options used to look up input files
	 * @return the existing tree or a newly loaded one
	 * @throws MissingInputException if no tree option is present and no
	 *                               previous tree exists
	 */
	public Tree getTree(Options options) throws MissingInputException {
		return tree = IReader.read(options, tree, Data.TREE);
	}

	/**
	 * Writes the given matrix using the provided options and updates the context.
	 *
	 * @param options the command-line options used to find the output location
	 * @param value   the matrix to write and store
	 */
	public void setMatrix(Options options, Matrix value) {
		matrix = value;
		IWriter.write(options, matrix, Data.MATRIX);
	}

	/**
	 * Writes the given tree using the provided options and updates the context.
	 *
	 * @param options the command-line options used to find the output location
	 * @param value   the tree to write and store
	 */
	public void setTree(Options options, Tree value) {
		tree = value;
		IWriter.write(options, tree, Data.TREE);
	}
}
