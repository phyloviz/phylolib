package pt.ist.phylolib.data.matrix;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.expectThrows;

public class MatrixStoragePlannerTest {

    private final MatrixStoragePlanner planner = new MatrixStoragePlanner(80);

    @Test
    public void plan_SelectsDenseForSmallMatricesRegardlessOfRequirement() {
        assertEquals(planner.choose(5, true, new DistanceScope.Bounded(3), false),
                MatrixStoragePlanner.Storage.DENSE);
        assertEquals(planner.choose(5, true, DistanceScope.Complete.INSTANCE, false),
                MatrixStoragePlanner.Storage.DENSE);
    }

    @Test
    public void plan_SelectsThresholdSparseForLargeBoundedRequirement() {
        assertEquals(planner.choose(6, true, new DistanceScope.Bounded(3), false),
                MatrixStoragePlanner.Storage.THRESHOLD_SPARSE);
    }

    @Test
    public void plan_ForceDenseOverridesOnlyStorage() {
        assertEquals(planner.choose(6, true, new DistanceScope.Bounded(3), true),
                MatrixStoragePlanner.Storage.DENSE);
        assertEquals(planner.choose(6, true, DistanceScope.Complete.INSTANCE, true),
                MatrixStoragePlanner.Storage.DENSE);
    }

    @Test
    public void plan_FailsClosedForLargeCompleteRequirementWithoutForceDense() {
        IllegalStateException exception = expectThrows(IllegalStateException.class,
                () -> planner.choose(6, true, DistanceScope.Complete.INSTANCE, false));

        assertEquals(exception.getMessage().contains("--force-dense"), true);
    }

    @Test
    public void estimateDenseBytes_UsesTriangularStorageAndSaturates() {
        assertEquals(MatrixStoragePlanner.estimateDenseBytes(3, true), 24L);
        assertEquals(MatrixStoragePlanner.estimateDenseBytes(3, false), 72L);
        assertEquals(MatrixStoragePlanner.estimateDenseBytes(10_000, true), 399_960_000L);
        assertEquals(MatrixStoragePlanner.estimateDenseBytes(Integer.MAX_VALUE, true), Long.MAX_VALUE);
    }
}
