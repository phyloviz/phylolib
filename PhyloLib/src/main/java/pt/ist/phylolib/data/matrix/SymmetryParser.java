package pt.ist.phylolib.data.matrix;

import pt.ist.phylolib.cli.Format;
import pt.ist.phylolib.cli.Option;
import pt.ist.phylolib.cli.Options;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.stream.Stream;

/**
 * Responsible for parsing {@link Matrix distance matrices} from and to Strings.
 * Implements streaming write support for memory-efficient output of large matrices.
 * Includes automatic optimization for Sparse Matrices on large datasets.
 */
public abstract class SymmetryParser extends MatrixParser {

    // HEURISTIC: If matrix size > 10,000, we force Sparse Mode to avoid OOM.
    private static final int SPARSE_THRESHOLD_SIZE = 10000;

    /**
     * Checks the symmetry of this distance matrix processor.
     *
     * @return true if symmetric (triangle), false if asymmetric (square)
     */
    protected abstract boolean symmetric();

    @Override
    public final Matrix parse(Stream<String> data, Options options) {
        Iterator<String> iterator = data.iterator();
        String start;

        // 1. Parse Header
        if (!iterator.hasNext() || !Format.NATURAL.matches(start = iterator.next()))
            return null;

        int size = Integer.parseInt(start);
        if (size <= 0) return null;

        String[] ids = new String[size];
        boolean forceDense = Boolean.parseBoolean(options.get(Option.FORCE_DENSE));

        // 2. Decide Mode: Sparse vs Dense
        if (size > SPARSE_THRESHOLD_SIZE && !forceDense) {
            Double lvs = Double.parseDouble(options.get(Option.LVS)); // The distance threshold
            return parseSparse(iterator, size, ids, lvs);
        } else {
            return parseDense(iterator, size, ids);
        }
    }

    /**
     * Standard parsing for small/medium matrices.
     * Stores ALL values in a primitive double[][] array.
     */
    private Matrix parseDense(Iterator<String> iterator, int size, String[] ids) {
        double[][] matrix = new double[size][];
        int i = 0;

        while (iterator.hasNext()) {
            if (i >= size) return null;

            String line = iterator.next();
            int tabIndex = line.indexOf('\t');
            if (tabIndex == -1) return null;

            ids[i] = line.substring(0, tabIndex);

            // Allocate row
            int rowSize = symmetric() ? i : size;
            matrix[i] = new double[rowSize];

            // Fast Parse Loop
            int col = 0;
            int lastTab = tabIndex;

            while (col < rowSize && lastTab < line.length()) {
                int nextTab = line.indexOf('\t', lastTab + 1);
                if (nextTab == -1) nextTab = line.length();

                // Validate bounds
                if (lastTab + 1 >= line.length()) {
                    return null;
                }

                String valStr = line.substring(lastTab + 1, nextTab);

                // Try to parse, return null if invalid
                try {
                    matrix[i][col] = Double.parseDouble(valStr);
                } catch (NumberFormatException e) {
                    return null;
                }

                lastTab = nextTab;
                col++;
            }

            // Validation check - must have parsed exactly rowSize values
            if (col != rowSize) return null;

            // If lastTab is not at end of line and there's non-empty content, it's invalid
            if (lastTab < line.length()) {
                String remaining = line.substring(lastTab).trim();
                if (!remaining.isEmpty()) return null;
            }

            i++;
        }

        return i == size ? new Matrix(symmetric(), ids, matrix) : null;
    }

    /**
     * Optimized parsing for massive matrices.
     * Uses Filter-on-Read to discard distances > 500.0.
     * Returns a SparseMatrix.
     */
    private Matrix parseSparse(Iterator<String> iterator, int size, String[] ids, Double lvs) {
        // CSR Storage
        int[][] colIndices = new int[size][];
        double[][] values = new double[size][];

        // Reusable buffers (max possible size per row)
        int[] tempCols = new int[size];
        double[] tempVals = new double[size];

        int i = 0;
        while (iterator.hasNext()) {
            String line = iterator.next();
            int tabIndex = line.indexOf('\t');
            if (tabIndex == -1) return null; // Skip invalid lines

            ids[i] = line.substring(0, tabIndex);

            int rowSize = symmetric() ? i : size;
            int count = 0; // Valid neighbors found

            int col = 0;
            int lastTab = tabIndex;

            while (col < rowSize) {
                int nextTab = line.indexOf('\t', lastTab + 1);
                if (nextTab == -1) nextTab = line.length();

                String valStr = line.substring(lastTab + 1, nextTab);
                double val = Double.parseDouble(valStr);

                // FILTER: Only store relevant edges
                if (val <= lvs) {
                    tempCols[count] = col;
                    tempVals[count] = val;
                    count++;
                }

                lastTab = nextTab;
                col++;
                if (lastTab >= line.length()) break;
            }

            // Commit Compact Row
            colIndices[i] = Arrays.copyOf(tempCols, count);
            values[i] = Arrays.copyOf(tempVals, count);
            i++;
        }

        return i == size ? new SparseMatrix(symmetric(), ids, colIndices, values) : null;
    }

    @Override
    public final String parse(Matrix matrix) {
        // Fallback for small string outputs
        StringBuilder data = new StringBuilder();
        int size = matrix.size();
        data.append(size);
        for (int i = 0; i < size; i++) {
            data.append('\n').append(matrix.ids()[i]);
            for (int j = 0; j < (symmetric() ? i : size); j++)
                data.append('\t').append(matrix.distance(i, j));
        }
        return data.toString();
    }

    @Override
    public void streamParse(Matrix matrix, BufferedWriter writer) throws IOException {
        int size = matrix.size();
        writer.write(String.valueOf(size));

        for (int i = 0; i < size; i++) {
            writer.newLine();
            writer.write(matrix.ids()[i]);

            int cols = symmetric() ? i : size;
            for (int j = 0; j < cols; j++) {
                writer.write('\t');
                // Note: SparseMatrix will return Infinity for missing values.
                // Standard Dense output expects a value here.
                writer.write(String.valueOf(matrix.distance(i, j)));
            }
        }
    }
}