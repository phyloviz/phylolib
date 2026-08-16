package pt.ist.phylolib.data.matrix;

import java.util.Objects;

/**
 * The pairwise distance range required by an algorithm or retained by a
 * matrix. Complete scope contains every pairwise distance.
 */
public sealed interface DistanceScope permits DistanceScope.Bounded, DistanceScope.Complete {

    /**
     * Returns whether this available scope contains every distance required by
     * {@code requiredScope}.
     */
    default boolean covers(DistanceScope requiredScope) {
        Objects.requireNonNull(requiredScope, "required distance scope");
        return switch (this) {
            case Complete ignored -> true;
            case Bounded available -> requiredScope instanceof Bounded required
                    && available.maxDistance() >= required.maxDistance();
        };
    }

    /**
     * Distances through this finite, non-negative bound are available.
     */
    record Bounded(double maxDistance) implements DistanceScope {

        public Bounded {
            if (!Double.isFinite(maxDistance) || maxDistance < 0)
                throw new IllegalArgumentException("A bounded distance scope must be finite and non-negative.");
        }
    }

    /**
     * Every pairwise distance is available.
     */
    enum Complete implements DistanceScope {
        INSTANCE
    }
}
