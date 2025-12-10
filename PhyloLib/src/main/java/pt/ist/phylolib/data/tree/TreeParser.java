package pt.ist.phylolib.data.tree;

import pt.ist.phylolib.data.IReader;
import pt.ist.phylolib.data.IWriter;

/**
 * Responsible for parsing {@link Tree phylogenetic trees} from and to Strings.
 * Supports both traditional and streaming write operations.
 */
public abstract class TreeParser implements IReader<Tree>, IWriter<Tree> {

}
