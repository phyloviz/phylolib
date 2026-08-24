package pt.ist.phylolib.data.matrix;

import pt.ist.phylolib.data.IReader;
import pt.ist.phylolib.data.IWriter;

import java.util.stream.Stream;

/**
 * Responsible for parsing {@link Matrix distance matrices} from and to
 * Strings.
 */
public abstract class MatrixParser implements IReader<Matrix>, IWriter<Matrix> {

    /**
     * Parses with the scope and storage override supplied by matrix loading.
     */
    public abstract Matrix parse(Stream<String> data, DistanceScope requiredScope, boolean forceDense);

}
