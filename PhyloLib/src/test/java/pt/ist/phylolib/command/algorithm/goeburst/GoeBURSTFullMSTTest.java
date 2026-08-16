package pt.ist.phylolib.command.algorithm.goeburst;

import org.testng.annotations.Test;
import pt.ist.phylolib.cli.Option;
import pt.ist.phylolib.cli.Options;
import pt.ist.phylolib.data.Context;
import pt.ist.phylolib.data.matrix.DistanceScope;
import pt.ist.phylolib.data.matrix.Matrix;
import pt.ist.phylolib.data.tree.Edge;
import pt.ist.phylolib.data.tree.Newick;
import pt.ist.phylolib.data.tree.Tree;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

public class GoeBURSTFullMSTTest {

    @Test
    public void process_TwoProfiles_ProducesOneEdge() {
        Tree tree = process(new Matrix(true, new String[] { "A", "B" }, new double[][] {
                {},
                { 1.0 }
        }));

        assertEquals(tree.ids().length, 2);
        assertEquals(tree.edges().count(), 1);
        assertTrue(isConnected(tree));
    }

    @Test
    public void process_ConnectsProfilesThatThresholdedGoeBurstLeavesInAForest() {
        Matrix matrix = new Matrix(true, new String[] { "A", "B", "C" }, new double[][] {
                {},
                { 1.0 },
                { 4.0, 4.0 }
        });

        assertEquals(thresholded(1, matrix).edges().count(), 1);

        Tree tree = process(matrix);
        assertEquals(tree.edges().count(), 2);
        assertTrue(isConnected(tree));
    }

    /**
     * Golden fixture validated against PHYLOViZ GOeBurstDistance and
     * MSTAlgorithm. It differs from a comparator truncated at level 3: the
     * expected A--Z link is selected through higher LV rules.
     */
    @Test
    public void process_MatchesPhyloVizReferenceGoldenFixtureBeyondLevelThree() {
        Tree tree = process(referenceFixture());

        assertEquals(undirectedEdges(tree), Set.of("A-C", "A-M", "A-Z", "B-Z"));
        assertTreeInvariants(tree);
    }

    @Test
    public void process_ReordersSameDistanceCandidateAfterTieBreakImprovement() {
        Tree tree = process(referenceFixture());

        // The fixture contains tied level-2 candidate edges. The associated
        // MinHeap regression below verifies reordering when their biological
        // comparison improves without changing that numerical level.
        assertTrue(undirectedEdges(tree).contains("A-C"));
    }

    @Test
    public void minHeap_UpdateKeyReordersSameDistancePriority() {
        int[] distance = { 0, 4, 4 };
        int[] tieRank = { 0, 2, 1 };
        MinHeap heap = new MinHeap(distance.length, (left, right) -> {
            int comparison = Integer.compare(distance[left], distance[right]);
            return comparison != 0 ? comparison : Integer.compare(tieRank[left], tieRank[right]);
        });

        assertEquals(heap.extractMin(), 0);
        tieRank[1] = 0;
        heap.updateKey(1);

        assertEquals(heap.extractMin(), 1);
        assertEquals(heap.extractMin(), 2);
    }

    @Test
    public void process_IsInvariantToMatrixRowPermutation() {
        Matrix original = referenceFixture();
        Matrix permuted = permute(original, 2, 4, 0, 3, 1);

        assertEquals(undirectedEdges(process(permuted)), undirectedEdges(process(original)));
    }

    @Test
    public void process_IsRepeatableAndSerializesOneNewickTree() {
        Tree first = process(referenceFixture());
        Tree second = process(referenceFixture());
        String newick = new Newick().parse(first);

        assertEquals(first.edges().toList(), second.edges().toList());
        assertEquals(newick.chars().filter(character -> character == ';').count(), 1);
        assertTreeInvariants(first);
    }

    @Test
    public void process_RejectsFractionalAndNonFiniteDistances() {
        Matrix fractional = new Matrix(true, new String[] { "A", "B" }, new double[][] {
                {},
                { 0.5 }
        });
        Matrix infinite = new Matrix(true, new String[] { "A", "B" }, new double[][] {
                {},
                { Double.POSITIVE_INFINITY }
        });
        Matrix nan = new Matrix(true, new String[] { "A", "B" }, new double[][] {
                {},
                { Double.NaN }
        });

        assertThrows(IllegalArgumentException.class, () -> process(fractional));
        assertThrows(IllegalArgumentException.class, () -> process(infinite));
        assertThrows(IllegalArgumentException.class, () -> process(nan));
    }

    @Test
    public void process_RejectsBoundedCoverageWithoutCheckingConcreteStorageType() {
        Matrix bounded = new BoundedCoverageMatrix();

        assertThrows(IllegalArgumentException.class, () -> process(bounded));
    }

    private Tree process(Matrix matrix) {
        return new GoeBURSTFullMST().process(matrix);
    }

    private Tree thresholded(int lvs, Matrix matrix) {
        GoeBURST goeburst = new GoeBURST();
        Options options = new Options();
        options.put(Option.LVS, String.valueOf(lvs));
        goeburst.init(new Context(), options);
        return goeburst.process(matrix);
    }

    private Matrix referenceFixture() {
        return new Matrix(true, new String[] { "Z", "A", "C", "M", "B" }, new double[][] {
                {},
                { 4.0 },
                { 4.0, 2.0 },
                { 4.0, 2.0, 2.0 },
                { 2.0, 4.0, 6.0, 4.0 }
        });
    }

    private Matrix permute(Matrix matrix, int... order) {
        String[] ids = Arrays.stream(order).mapToObj(index -> matrix.ids()[index]).toArray(String[]::new);
        double[][] values = new double[order.length][];
        for (int i = 0; i < order.length; i++) {
            values[i] = new double[i];
            for (int j = 0; j < i; j++)
                values[i][j] = matrix.distance(order[i], order[j]);
        }
        return new Matrix(true, ids, values);
    }

    private Set<String> undirectedEdges(Tree tree) {
        Set<String> edges = new HashSet<>();
        tree.edges().forEach(edge -> {
            String first = tree.ids()[edge.from()];
            String second = tree.ids()[edge.to()];
            edges.add(first.compareTo(second) < 0 ? first + "-" + second : second + "-" + first);
        });
        return edges;
    }

    private void assertTreeInvariants(Tree tree) {
        assertEquals(tree.edges().count(), tree.ids().length - 1);
        assertTrue(isConnected(tree));
    }

    private boolean isConnected(Tree tree) {
        List<Edge> edges = tree.edges().toList();
        boolean[] visited = new boolean[tree.ids().length];
        visit(0, edges, visited);
        for (boolean value : visited)
            if (!value)
                return false;
        return true;
    }

    private static final class BoundedCoverageMatrix extends Matrix {

        private BoundedCoverageMatrix() {
            super(true, new String[] { "A", "B" }, new double[][] { {}, { 1.0 } },
                    new DistanceScope.Bounded(1));
        }
    }

    private void visit(int node, List<Edge> edges, boolean[] visited) {
        if (visited[node])
            return;
        visited[node] = true;
        for (Edge edge : edges) {
            if (edge.from() == node)
                visit(edge.to(), edges, visited);
            else if (edge.to() == node)
                visit(edge.from(), edges, visited);
        }
    }
}
