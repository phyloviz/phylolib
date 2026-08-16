package pt.ist.phylolib.command.algorithm.goeburst;

import pt.ist.phylolib.command.algorithm.Algorithm;
import pt.ist.phylolib.data.matrix.DistanceScope;
import pt.ist.phylolib.data.matrix.Matrix;
import pt.ist.phylolib.data.tree.Edge;
import pt.ist.phylolib.data.tree.Tree;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Builds a complete goeBURST Full MST from an LV/Hamming-style distance matrix.
 * <p>
 * Unlike {@link GoeBURST}, this algorithm does not impose a user threshold. It
 * uses every observed LV level and fails if the input does not represent a
 * complete, finite, positive, integral distance graph between distinct
 * profiles.
 */
public final class GoeBURSTFullMST extends Algorithm {

    private static final String PREFIX = "goeBURST Full MST requires ";

    @Override
    public DistanceScope requiredDistanceScope() {
        return DistanceScope.Complete.INSTANCE;
    }

    @Override
    protected Tree processImpl(Matrix matrix) {
        validateMatrixShape(matrix);

        int size = matrix.size();
        String[] ids = matrix.ids();
        int maxLevel = validateDistances(matrix);
        int[][] lv = calculateLVs(matrix, maxLevel);

        // Matrix input does not retain profile frequencies. Equal frequency is
        // the deterministic, explicitly documented matrix-only interpretation.
        int[] frequency = new int[size];
        Arrays.fill(frequency, 1);

        int[] parent = new int[size];
        int[] distance = new int[size];
        boolean[] visited = new boolean[size];
        Arrays.fill(parent, -1);

        CandidateComparator comparator = new CandidateComparator(parent, distance, lv, ids, frequency);
        MinHeap heap = new MinHeap(size, comparator::compareVertices);
        Tree tree = new Tree(ids);

        while (!heap.isEmpty()) {
            int u = heap.extractMin();
            visited[u] = true;

            if (parent[u] != -1) {
                tree.add(new Edge(parent[u], u, distance[u]));
            } else if (tree.edges().findAny().isPresent()) {
                throw new IllegalArgumentException(PREFIX + "a connected complete matrix.");
            }

            for (int v = 0; v < size; v++) {
                if (visited[v])
                    continue;

                int candidateDistance = (int) matrix.distance(u, v);
                if (parent[v] == -1 || comparator.compareEdges(u, v, candidateDistance,
                        parent[v], v, distance[v]) < 0) {
                    parent[v] = u;
                    distance[v] = candidateDistance;
                    // The comparator includes LV/frequency/ID ties, so this is
                    // required even when candidateDistance is unchanged.
                    heap.updateKey(v);
                }
            }
        }

        if (tree.edges().count() != size - 1)
            throw new IllegalStateException(PREFIX + "one connected tree.");
        return tree;
    }

    private void validateMatrixShape(Matrix matrix) {
        if (!matrix.symmetric())
            throw new IllegalArgumentException(PREFIX + "a symmetric matrix.");
        if (matrix.size() < 2)
            throw new IllegalArgumentException(PREFIX + "at least two profiles.");

        Set<String> uniqueIds = new HashSet<>();
        for (String id : matrix.ids()) {
            if (id == null || id.isBlank() || !uniqueIds.add(id))
                throw new IllegalArgumentException(PREFIX + "distinct, non-empty profile IDs.");
        }
    }

    /**
     * Returns the greatest observed LV level. For a valid complete integral
     * matrix, buckets above this level would be zero for every vertex and are
     * therefore comparator-equivalent to knowing the biological locus count.
     */
    private int validateDistances(Matrix matrix) {
        int maxLevel = 0;
        for (int i = 1; i < matrix.size(); i++) {
            for (int j = 0; j < i; j++) {
                double value = matrix.distance(i, j);
                if (!Double.isFinite(value) || value <= 0 || value != Math.rint(value)
                        || value > Integer.MAX_VALUE) {
                    throw new IllegalArgumentException(PREFIX
                            + "finite, positive, integral off-diagonal LV distances; invalid value "
                            + value + " between '" + matrix.ids()[i] + "' and '" + matrix.ids()[j] + "'.");
                }
                maxLevel = Math.max(maxLevel, (int) value);
            }
        }
        return maxLevel;
    }

    private int[][] calculateLVs(Matrix matrix, int maxLevel) {
        int size = matrix.size();
        int[][] lv = new int[size][maxLevel];
        for (int i = 1; i < size; i++) {
            for (int j = 0; j < i; j++) {
                int level = (int) matrix.distance(i, j) - 1;
                lv[i][level]++;
                lv[j][level]++;
            }
        }
        return lv;
    }

    /**
     * Primitive representation of PHYLOViZ's GOeBurstDistance.EdgeComparator.
     * A negative result means that the first candidate edge is preferred.
     */
    private static final class CandidateComparator {

        private final int[] parent;
        private final int[] distance;
        private final int[][] lv;
        private final String[] ids;
        private final int[] frequency;

        private CandidateComparator(int[] parent, int[] distance, int[][] lv, String[] ids, int[] frequency) {
            this.parent = parent;
            this.distance = distance;
            this.lv = lv;
            this.ids = ids;
            this.frequency = frequency;
        }

        private int compareVertices(int first, int second) {
            if (parent[first] == -1 || parent[second] == -1)
                return compareId(ids[first], ids[second]);
            return compareEdges(parent[first], first, distance[first], parent[second], second, distance[second]);
        }

        private int compareEdges(int firstFrom, int firstTo, int firstDistance,
                                 int secondFrom, int secondTo, int secondDistance) {
            int comparison = Integer.compare(firstDistance, secondDistance);
            if (comparison != 0)
                return comparison;

            for (int level = 0; level < lv[firstFrom].length; level++) {
                comparison = Integer.compare(
                        Math.max(lv[secondFrom][level], lv[secondTo][level]),
                        Math.max(lv[firstFrom][level], lv[firstTo][level]));
                if (comparison != 0)
                    return comparison;

                comparison = Integer.compare(
                        Math.min(lv[secondFrom][level], lv[secondTo][level]),
                        Math.min(lv[firstFrom][level], lv[firstTo][level]));
                if (comparison != 0)
                    return comparison;
            }

            comparison = Integer.compare(Math.max(frequency[secondFrom], frequency[secondTo]),
                    Math.max(frequency[firstFrom], frequency[firstTo]));
            if (comparison != 0)
                return comparison;
            comparison = Integer.compare(Math.min(frequency[secondFrom], frequency[secondTo]),
                    Math.min(frequency[firstFrom], frequency[firstTo]));
            if (comparison != 0)
                return comparison;

            int firstMin = compareId(ids[firstFrom], ids[firstTo]) < 0 ? firstFrom : firstTo;
            int firstMax = firstMin == firstFrom ? firstTo : firstFrom;
            int secondMin = compareId(ids[secondFrom], ids[secondTo]) < 0 ? secondFrom : secondTo;
            int secondMax = secondMin == secondFrom ? secondTo : secondFrom;

            // GOeBurstDistance selects endpoint min/max with compareId, then
            // compares the selected strings lexically as its final rule.
            comparison = ids[firstMin].compareTo(ids[secondMin]);
            return comparison != 0 ? comparison : ids[firstMax].compareTo(ids[secondMax]);
        }

        private static int compareId(String first, String second) {
            int comparison = Integer.compare(first.length(), second.length());
            return comparison != 0 ? comparison : first.compareTo(second);
        }
    }
}
