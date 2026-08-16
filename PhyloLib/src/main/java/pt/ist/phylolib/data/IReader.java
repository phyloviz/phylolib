package pt.ist.phylolib.data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.ist.phylolib.cli.Data;
import pt.ist.phylolib.cli.Options;
import pt.ist.phylolib.exception.MissingInputException;
import pt.ist.phylolib.logging.Log;
import pt.ist.phylolib.data.matrix.DistanceScope;
import pt.ist.phylolib.data.matrix.Matrix;
import pt.ist.phylolib.data.matrix.MatrixParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Responsible for the reading of data from files.
 *
 * @param <T> the type of the data to read
 */
@FunctionalInterface
public interface IReader<T> {

    String READ = "%s reading file '%s'";
    String STARTED = "Started";
    String FINISHED = "Finished";
    String FAILED = "Failed";
    Logger log = LoggerFactory.getLogger(IReader.class);

    /**
     * Reads data from an option of the given data type in the given options.
     * <p>
     * If there is no option of that data type, then returns the given previous
     * value for this data type.
     * <p>
     * If the previous value is null, then throws a MissingInputException.
     *
     * @param options  the options to search for the specific option
     * @param previous the previous value for this data type
     * @param data     the data type to read
     * @param <T>      the input data type
     * @return the input read from the option for the data type or the previous
     *         value for that data type
     * @throws MissingInputException if the input is not provided in the given
     *                               options and the previous value for this data
     *                               type is null
     */
    @SuppressWarnings("unchecked")
    static <T> T read(Options options, T previous, Data data)
            throws MissingInputException {
        String input = options.remove(data.option());
        if (input != null) {
            File file = File.get(input, data);
            if (file != null) {
                Path path = file.path();
                Log.info(READ, STARTED, path);
                try (Stream<String> lines = Files.lines(path)) {
                    T result = ((IReader<T>) file.processor()).parse(lines, options);
                    if (result != null) {
                        Log.info(READ, FINISHED, path);
                        return result;
                    }
                    Log.warning(READ, FAILED, path);
                } catch (Exception exception) {
                    Log.warning(READ, FAILED, path);
                    Log.exception(exception);
                }
            }
        }

        if (previous == null)
            throw new MissingInputException(data.toString());
        return previous;
    }

    /**
     * Reads a matrix using an explicit distance scope without overloading the
     * generic reader API with matrix-only state.
     */
    static Matrix readMatrix(Options options, Matrix previous, DistanceScope requiredScope, boolean forceDense)
            throws MissingInputException {
        String input = options.remove(Data.MATRIX.option());
        if (input != null) {
            File file = File.get(input, Data.MATRIX);
            if (file != null) {
                Path path = file.path();
                Log.info(READ, STARTED, path);
                try (Stream<String> lines = Files.lines(path)) {
                    Matrix result = ((MatrixParser) file.processor()).parse(lines, requiredScope, forceDense);
                    if (result != null) {
                        Log.info(READ, FINISHED, path);
                        return result;
                    }
                    Log.warning(READ, FAILED, path);
                } catch (IllegalStateException exception) {
                    throw exception;
                } catch (Exception exception) {
                    Log.warning(READ, FAILED, path);
                    Log.exception(exception);
                }
            }
        }

        if (previous == null)
            throw new MissingInputException(Data.MATRIX.toString());
        return previous;
    }

    /**
     * Parses the input data into an object.
     *
     * @param data the input data to parse
     * @return the object resultant from parsing the input data
     */
    T parse(Stream<String> data, Options options);
}
