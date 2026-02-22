package pt.ist.phylolib.cli;

import pt.ist.phylolib.logging.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Represents the options of a command as keys and values.
 */
public final class Options {

	private static final String DEFAULT = "Used default value '%s' for option %s";
	private static final String INVALID_OPTION = "Ignored invalid option '%s'";
	private static final String DUPLICATED_OPTION = "Ignored duplicated option %s";

	private final Map<Option, String> options = new HashMap<>();

	/**
	 * Saves the association between the key-value option given as parameter.
	 * <p>
	 * Throws a warning if the given option is invalid or duplicated.
	 *
	 * @param option the key-value option
	 */
	public void put(String option) {
		String[] parts = option.split("=", 2);
		Option key = Option.get(parts[0].toLowerCase());
		String value = null;
		if (key == null) {
			Log.warning(INVALID_OPTION, option);
			return;
		}

		if (parts.length == 1) {
			if (key.format() == Format.FLAG) {
				value = "true";
			} else {
				Log.warning(INVALID_OPTION, option);
				return;
			}
		} else {
			value = parts[1];
			// Normalize empty value for flags to true: --flag=
			if (key.format() == Format.FLAG && value.isEmpty()) {
				value = "true";
			}
			if (!key.format().matches(value)) {
				Log.warning(INVALID_OPTION, option);
				return;
			}
		}

		if (options.putIfAbsent(key, value) != null)
			Log.warning(DUPLICATED_OPTION, key);
	}

	/**
	 * Programmatically sets an option value (for internal use).
	 * Overwrites any existing value.
	 *
	 * @param option the option to set
	 * @param value  the value to set
	 */
	public void put(Option option, String value) {
		options.put(option, value);
	}

	/**
	 * Returns the value for the given Option without removing it.
	 * <p>
	 * Uses the default value if the option is not present.
	 * 
	 * @param option the option to get
	 */
	public String get(Option option) {
		String value = options.get(option);
		return value != null ? value : option._default();
	}

	/**
	 * Returns a Set object with the keys for these options.
	 *
	 * @return a set with these options' keys
	 */
	public Set<Option> keys() {
		return options.keySet();
	}

	/**
	 * Returns the value and removes the association for the given {@link Option}.
	 *
	 * @param option the option to be removed
	 *
	 * @return the value associated to the option
	 */
	public String remove(Option option) {
		String value = options.remove(option);
		String _default = option._default();
		if (value == null && _default != null) {
			Log.info(DEFAULT, _default, option);
			return _default;
		}
		return value;
	}

}
