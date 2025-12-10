package pt.ist.phylolib.data.tree;

import pt.ist.phylolib.cli.Options;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Responsible for parsing {@link Tree phylogenetic trees} from and to Strings
 * in Nexus format.
 */
public final class Nexus extends Newick {

	@Override
	public Tree parse(Stream<String> data, Options options) {
		Iterator<String> iterator = data
				.dropWhile(line -> !line.equals("BEGIN TREES;"))
				.takeWhile(line -> !line.equals("END;"))
				.dropWhile(line -> !line.contains("("))
				.iterator();
		List<String> lines = new ArrayList<>();
		if (iterator.hasNext()) {
			String next = iterator.next();
			lines.add(next.substring(next.indexOf('(')));
		}
		while (iterator.hasNext()) {
			String next = iterator.next();
			lines.add(next);
		}
		return super.parse(lines.stream(), options);
	}

	@Override
	public String parse(Tree tree) {
		return String.format("BEGIN TREES;\n\tTree result = %s\nEND;", super.parse(tree));
	}

	@Override
	public void streamParse(Tree tree, java.io.BufferedWriter writer) throws java.io.IOException {
		writer.write("BEGIN TREES;\n\tTree result = ");
		super.streamParse(tree, writer);
		writer.write("\nEND;");
	}

}
