package pt.ist.phylolib.data.matrix;

import org.testng.annotations.Test;
import pt.ist.phylolib.cli.Option;
import pt.ist.phylolib.cli.Options;
import pt.ist.phylolib.command.algorithm.goeburst.GoeBURST;

import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class SymmetryParserPolicyTest {

    @Test
    public void parse_ThresholdStorageRetainsOnlyItsScopeBound() {
        Matrix matrix = new TestSymmetric(10).parse(rows(), new DistanceScope.Bounded(2), false);

        assertTrue(matrix instanceof ThresholdSparseMatrix);
        assertEquals(matrix.distanceScope(), new DistanceScope.Bounded(2));
        assertEquals(((ThresholdSparseMatrix) matrix).retainedThreshold(), 2.0);
        assertEquals(matrix.distance(1, 0), 2.0);
        assertEquals(matrix.distance(2, 0), 1.0);
        assertEquals(matrix.distance(2, 1), Double.POSITIVE_INFINITY);
    }

    @Test
    public void parse_DensePlanRetainsCompleteCoverage() {
        Matrix matrix = new TestSymmetric(80).parse(rows(), new DistanceScope.Bounded(1), false);

        assertFalse(matrix instanceof ThresholdSparseMatrix);
        assertEquals(matrix.distanceScope(), DistanceScope.Complete.INSTANCE);
        assertEquals(matrix.distance(2, 1), 4.0);
    }

    @Test
    public void parse_ForceDenseChangesStorageButNotBoundedGoeBurstOutput() {
        Matrix sparse = new TestSymmetric(10).parse(rows(), new DistanceScope.Bounded(2), false);
        Matrix dense = new TestSymmetric(10).parse(rows(), new DistanceScope.Bounded(2), true);

        assertEquals(sparse.distanceScope(), new DistanceScope.Bounded(2));
        assertEquals(dense.distanceScope(), DistanceScope.Complete.INSTANCE);
        assertEquals(goeBurst(2).requiredDistanceScope(), new DistanceScope.Bounded(2));
        assertEquals(goeBurst(2).process(sparse).edges().toList(), goeBurst(2).process(dense).edges().toList());
    }

    private Stream<String> rows() {
        return Stream.of("3", "A", "B\t2", "C\t1\t4");
    }

    private GoeBURST goeBurst(int lvs) {
        GoeBURST algorithm = new GoeBURST();
        Options options = new Options();
        options.put(Option.LVS, String.valueOf(lvs));
        algorithm.configureRequiredDistanceScope(options);
        return algorithm;
    }

    public static final class TestSymmetric extends SymmetryParser {

        private final MatrixStoragePlanner planner;

        public TestSymmetric() {
            this(10);
        }

        private TestSymmetric(long autoDenseMaxBytes) {
            planner = new MatrixStoragePlanner(autoDenseMaxBytes);
        }

        @Override
        protected boolean symmetric() {
            return true;
        }

        @Override
        protected MatrixStoragePlanner storagePlanner() {
            return planner;
        }
    }
}
