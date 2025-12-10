package pt.ist.phylolib.cli;

import pt.ist.phylolib.exception.*;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Represents the parsed arguments of the program as commands and respective parameters.
 */
public final class Arguments extends HashMap<Command, List<Parameters>> {

	private static final String HELP = "help";
	private static final String SEPARATOR = ":";

	/**
	 * Parses the given command line arguments into an Arguments object.
	 * <p>
	 * Immediately returns null if the first command is help.
	 *
	 * @param args the command line arguments to be parsed
	 *
	 * @return an Arguments object with the parsed command line arguments
	 *
	 * @throws NoCommandException       if no command is found
	 * @throws InvalidCommandException  if an invalid command is found
	 * @throws RepeatedCommandException if a non-repeatable command is found twice
	 * @throws MissingTypeException     if no type is found for a command
	 * @throws InvalidTypeException     if a command type is invalid
	 */
	public static Arguments parse(String[] args) throws NoCommandException, InvalidCommandException, RepeatedCommandException, MissingTypeException, InvalidTypeException {
		if (args.length == 0)
			throw new NoCommandException();
		if (args[0].equals(HELP))
			return null;
		Arguments arguments = new Arguments();
		for (int i = 0; i < args.length; i++) {
			Command command = Command.get(args[i++]);
			if (command == null)
				throw new InvalidCommandException(args[i - 1]);
			if (arguments.containsKey(command) && !command.isRepeatable())
				throw new RepeatedCommandException(args[i - 1]);
			if (i == args.length || args[i].startsWith("-") || args[i].equals(SEPARATOR))
				throw new MissingTypeException(args[i - 1]);
			Constructor<?> type = command.type(args[i++]);
			if (type == null)
				throw new InvalidTypeException(args[i - 2], args[i - 1]);
			Options options = new Options();
			while (i < args.length && !args[i].equals(SEPARATOR))
				options.put(args[i++]);
			arguments.computeIfAbsent(command, k -> new ArrayList<>()).add(new Parameters(type, options));
		}
		return arguments;
	}

}
