package pt.ist.phylolib.data.dataset;

import java.util.Arrays;
import java.util.Iterator;
import java.util.stream.Stream;

/**
 * Responsible for parsing {@link Dataset phylogenetic datasets} from Strings in
 * MLST or MLVA format.
 */
public final class ML extends DatasetParser {

	@Override
	public Profile parse(Iterator<String> iterator) {
		String[] profile = iterator.next().split("\\t", -1);
		String[] loci = Arrays.copyOfRange(profile, 1, profile.length);
		return new Profile(profile[0],
				Arrays.stream(loci).allMatch(l -> l.matches("^([\\d_]+| |-|N)?$")) ? Arrays.stream(loci)
						: Stream.empty());
	}

}
