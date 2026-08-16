package pt.ist.phylolib.data.matrix;

import org.testng.annotations.Test;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

public class DistanceScopeTest {

    @Test
    public void covers_UsesAvailableScopeAgainstRequiredScope() {
        assertTrue(DistanceScope.Complete.INSTANCE.covers(DistanceScope.Complete.INSTANCE));
        assertTrue(DistanceScope.Complete.INSTANCE.covers(new DistanceScope.Bounded(3)));
        assertTrue(new DistanceScope.Bounded(10).covers(new DistanceScope.Bounded(3)));
        assertFalse(new DistanceScope.Bounded(3).covers(new DistanceScope.Bounded(10)));
        assertFalse(new DistanceScope.Bounded(10).covers(DistanceScope.Complete.INSTANCE));
    }

    @Test
    public void bounded_RejectsInvalidBounds() {
        assertThrows(IllegalArgumentException.class, () -> new DistanceScope.Bounded(-1));
        assertThrows(IllegalArgumentException.class, () -> new DistanceScope.Bounded(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new DistanceScope.Bounded(Double.POSITIVE_INFINITY));
    }
}
