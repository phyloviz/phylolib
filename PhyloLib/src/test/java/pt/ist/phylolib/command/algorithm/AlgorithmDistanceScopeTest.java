package pt.ist.phylolib.command.algorithm;

import org.testng.annotations.Test;
import pt.ist.phylolib.cli.Option;
import pt.ist.phylolib.cli.Options;
import pt.ist.phylolib.command.algorithm.edmonds.Edmonds;
import pt.ist.phylolib.command.algorithm.goeburst.GoeBURST;
import pt.ist.phylolib.command.algorithm.goeburst.GoeBURSTFullMST;
import pt.ist.phylolib.data.matrix.DistanceScope;

import static org.testng.Assert.assertEquals;

public class AlgorithmDistanceScopeTest {

    @Test
    public void requiredScopes_DefaultToCompleteAndGoeBurstReadsItsConfiguredBound() {
        assertEquals(new Edmonds().requiredDistanceScope(), DistanceScope.Complete.INSTANCE);
        assertEquals(new GoeBURSTFullMST().requiredDistanceScope(), DistanceScope.Complete.INSTANCE);
        assertEquals(configuredGoeBurst(3).requiredDistanceScope(), new DistanceScope.Bounded(3));
        assertEquals(configuredGoeBurst(5).requiredDistanceScope(), new DistanceScope.Bounded(5));
    }

    private GoeBURST configuredGoeBurst(int lvs) {
        GoeBURST algorithm = new GoeBURST();
        Options options = new Options();
        options.put(Option.LVS, String.valueOf(lvs));
        algorithm.configureRequiredDistanceScope(options);
        return algorithm;
    }
}
