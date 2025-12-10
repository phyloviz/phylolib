package pt.ist.phylolib.data.dataset;

import pt.ist.phylolib.cli.Options;
import pt.ist.phylolib.data.IReader;
import pt.ist.phylolib.logging.Log;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Responsible for parsing {@link Dataset phylogenetic datasets} from Strings.
 */
public abstract class DatasetParser implements IReader<Dataset> {

	private static final String INVALID = "Ignored invalid profile '%s'";

	@Override
	public Dataset parse(Stream<String> data, Options options) {
		Iterator<String> iterator = data.iterator();
		List<Profile> profiles = new ArrayList<>();
		init(iterator);
		while (iterator.hasNext()) {
			Profile profile = parse(iterator);
			if (profile.size() > 1 && (profiles.isEmpty() || profile.size() == profiles.getFirst().size()))
				profiles.add(profile);
			else
				Log.warning(INVALID, profile.id());
		}
		return profiles.size() > 1 ? new Dataset(profiles) : null;
	}

	/**
	 * Initializes the state of the processor by parsing the first lines of the dataset.
	 * <p>
	 * By default, does not parse anything.
	 *
	 * @param iterator the iterator containing the lines of the dataset
	 */
	protected void init(Iterator<String> iterator) { }

	/**
	 * Parses one profile from the given Strings.
	 *
	 * @param iterator the iterator containing the lines of the dataset
	 *
	 * @return a new profile resultant from parsing lines of the dataset
	 */
	protected abstract Profile parse(Iterator<String> iterator);

}
