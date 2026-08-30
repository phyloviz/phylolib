package pt.ist.phylolib.data.matrix;

import java.util.Objects;

/**
 * Selects matrix storage from the available distance scope and a deterministic
 * estimate of complete heap-backed storage.
 */
public final class MatrixStoragePlanner {

    /**
     * A conservative automatic heap-storage budget of 320 MiB leaves process
     * headroom for array headers, parser buffers, and other application data.
     * It is a storage-policy limit, not a correctness limit.
     */
    public static final long AUTO_HEAP_DENSE_MAX_BYTES = 320L * 1024 * 1024;

    private final long autoHeapDenseMaxBytes;

    public MatrixStoragePlanner() {
        this(AUTO_HEAP_DENSE_MAX_BYTES);
    }

    /**
     * Exposed to make automatic-storage policy testable without allocations.
     */
    public MatrixStoragePlanner(long autoHeapDenseMaxBytes) {
        if (autoHeapDenseMaxBytes < 0)
            throw new IllegalArgumentException("The automatic dense-memory budget cannot be negative.");
        this.autoHeapDenseMaxBytes = autoHeapDenseMaxBytes;
    }

    public Storage choose(int matrixSize, boolean symmetric, DistanceScope requiredScope, boolean forceDense) {
        if (matrixSize < 0)
            throw new IllegalArgumentException("The matrix size cannot be negative.");
        Objects.requireNonNull(requiredScope, "required distance scope");

        long estimatedDenseBytes = estimateDenseBytes(matrixSize, symmetric);
        // Bounded scope permits filtering; it does not require sparse storage
        // when dense primitive arrays remain inexpensive.
        if (forceDense || estimatedDenseBytes <= autoHeapDenseMaxBytes)
            return Storage.DENSE;
        if (requiredScope instanceof DistanceScope.Bounded)
            return Storage.THRESHOLD_SPARSE;

        throw new IllegalStateException("Complete pairwise distance information is required, but estimated "
                + "heap-backed dense storage of " + estimatedDenseBytes + " bytes exceeds the automatic safe "
                + "budget of " + autoHeapDenseMaxBytes + " bytes. No scalable complete representation is available; "
                + "supply --force-dense only when available memory can safely hold the complete matrix.");
    }

    /**
     * Estimates raw primitive-double storage. Saturation makes comparison to a
     * fixed budget safe even for int-sized matrix dimensions.
     */
    public static long estimateDenseBytes(int matrixSize, boolean symmetric) {
        if (matrixSize < 0)
            throw new IllegalArgumentException("The matrix size cannot be negative.");
        long size = matrixSize;
        long values = symmetric ? size * (size - 1) / 2 : size * size;
        return values > Long.MAX_VALUE / Double.BYTES ? Long.MAX_VALUE : values * Double.BYTES;
    }

    public enum Storage {
        DENSE,
        THRESHOLD_SPARSE
    }
}
