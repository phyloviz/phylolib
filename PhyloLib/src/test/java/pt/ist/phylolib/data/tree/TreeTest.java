package pt.ist.phylolib.data.tree;

import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.testng.Assert.assertEquals;

public class TreeTest {

    @Test
    public void rootAtMidpoint_Disconnected_PreservesEdges() {
        List<Edge> edges = List.of(
                new Edge(4, 0, 1),
                new Edge(4, 1, 1),
                new Edge(5, 2, 1),
                new Edge(5, 3, 1));
        Tree tree = new Tree(new String[]{"A", "B", "C", "D"}, new ArrayList<>(edges));

        tree.rootAtMidpoint();

        assertEquals(tree.edges().collect(Collectors.toList()), edges);
    }

    @Test
    public void rootAtMidpoint_MidpointCloseToNode_DoesNotSplitEdge() {
        Tree tree = new Tree(new String[]{"A", "B"});
        tree.add(new Edge(2, 0, 0.3));
        tree.add(new Edge(2, 1, 0.3000000000000001));

        tree.rootAtMidpoint();

        List<Edge> edges = tree.edges().collect(Collectors.toList());
        assertEquals(edges.size(), 2);
        assertEquals(edges.get(0), new Edge(2, 0, 0.3));
        assertEquals(edges.get(1), new Edge(2, 1, 0.3000000000000001));
    }

}
