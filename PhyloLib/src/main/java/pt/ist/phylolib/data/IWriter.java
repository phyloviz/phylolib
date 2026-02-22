package pt.ist.phylolib.data;

import pt.ist.phylolib.cli.Data;
import pt.ist.phylolib.cli.Option;
import pt.ist.phylolib.cli.Options;
import pt.ist.phylolib.logging.Log;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Responsible for the writing of data into files.
 *
 * @param <T> the type of the data to write
 */
@FunctionalInterface
public interface IWriter<T> {


    String WRITE = "%s writing file '%s'";
    String STARTED = "Started";
    String FINISHED = "Finished";
    String FAILED = "Failed";

    /**
     * Converts the data to a String (In-Memory).
     * Useful for small datasets, logs, or unit tests.
     */
    String parse(T data);

    /**
     * Writes the data directly to a buffered writer (Streaming).
     * Recommended for large datasets to avoid OutOfMemoryError.
     * <p>
     * DEFAULT IMPLEMENTATION: Calls parse(data) and writes the result.
     * Override this method for high-performance streaming logic.
     */
    default void streamParse(T data, BufferedWriter writer) throws IOException {
        String content = data == null ? "" : parse(data);
        writer.write(content);
    }

    @SuppressWarnings("unchecked")
    static <T> void write(Options options, T value, Data data) {
        String output = options.remove(Option.OUT);
        if (output == null)
            return;

        File file = File.get(output, data);
        if (file == null)
            return;

        Path path = file.path();
        Log.info(WRITE, STARTED, path);

        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            IWriter<T> processor = (IWriter<T>) file.processor();
            processor.streamParse(value, writer);
            Log.info(WRITE, FINISHED, path);
        } catch (Exception exception) {
            Log.warning(WRITE, FAILED, path);
            exception.printStackTrace();
        }
    }
}