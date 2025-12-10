package pt.ist.phylolib.command;

import pt.ist.phylolib.cli.Arguments;
import pt.ist.phylolib.cli.Command;
import pt.ist.phylolib.cli.Options;
import pt.ist.phylolib.cli.Parameters;
import pt.ist.phylolib.data.Context;
import pt.ist.phylolib.exception.MissingInputException;
import pt.ist.phylolib.logging.Log;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.function.BiConsumer;

/**
 * Responsible for the common operations of a command.
 *
 * @param <T> the input data type for this command
 * @param <R> the output data type for this command
 */
public interface ICommand<T, R> {

	String COMMAND = "%s running command '%s' with type '%s'";
	String STARTED = "Started";
	String FINISHED = "Finished";
	String TIMING = "Completed command '%s' with type '%s' in %.3f seconds (%.0f ms)";
	String UNUSED = "Ignored unused option '%s'";

	/**
	 * Runs the specified command with that input getter and output setter for the
	 * given arguments and context.
	 *
	 * @param arguments the parsed arguments of the program
	 * @param context   the current context of the program
	 * @param command   the command to run
	 * @param getter    the getter for the data input
	 * @param setter    the setter for the data output
	 * @param <T>       the input type of the command
	 * @param <R>       the output type of the command
	 *
	 * @throws MissingInputException if a mandatory input is not provided in the
	 *                               arguments or context
	 */
	@SuppressWarnings("unchecked")
	static <T, R> void run(Arguments arguments, Context context, Command command, IGetter<T> getter,
			BiConsumer<Options, R> setter) throws MissingInputException {
		for (Parameters parameters : arguments.getOrDefault(command, new ArrayList<>()))
			try {
				ICommand<T, R> component = (ICommand<T, R>) parameters.type().newInstance();
				String type = component.getClass().getSimpleName().toLowerCase();
				Log.info(COMMAND, STARTED, command, type);

				long startTime = System.nanoTime();

				Options options = parameters.options();

				// Set current command in context before getting data
				context.setCurrentCommand(component);
				T data = getter.get(options);
				context.setCurrentCommand(null); // Clear after data is loaded

				component.init(context, options);
				R result = component.process(data);
				setter.accept(options, result);

				long endTime = System.nanoTime();
				double elapsedSeconds = (endTime - startTime) / 1_000_000_000.0;
				double elapsedMillis = (endTime - startTime) / 1_000_000.0;

				options.keys().forEach(option -> Log.warning(UNUSED, option));
				Log.info(COMMAND, FINISHED, command, type);
				Log.info(TIMING, command, type, elapsedSeconds, elapsedMillis);
			} catch (IllegalAccessException | InstantiationException | InvocationTargetException exception) {
				Log.exception(exception);
			}
	}

	/**
	 * Initializes this command with the given context and options.
	 *
	 * @param context the current context of the program
	 * @param options the options for this command
	 *
	 * @throws MissingInputException if a mandatory input is not provided in the
	 *                               context or in this command's options
	 */
	default void init(Context context, Options options) throws MissingInputException {
	}

	/**
	 * Processes this command's input data into an output data.
	 *
	 * @param data the input data of this command
	 *
	 * @return the output data resultant from processing the input data with this
	 *         command
	 */
	R process(T data);

	/**
	 * Represents the input data getter for a command.
	 *
	 * @param <T> the input data type
	 */
	interface IGetter<T> {

		/**
		 * Gets the input data from the given options.
		 *
		 * @param options the options to retrieve the input data from
		 *
		 * @return the input data retrieved from the given options
		 *
		 * @throws MissingInputException if the input is not provided in the given
		 *                               options
		 */
		T get(Options options) throws MissingInputException;

	}

}
