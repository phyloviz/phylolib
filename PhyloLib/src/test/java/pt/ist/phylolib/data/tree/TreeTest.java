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
    public void rootAtMidpoint_EvenTopologicalDiameter_RootsAtExistingNode() {
        Tree tree = new Tree(new String[]{"A", "B"});
        tree.add(new Edge(2, 0, 100));
        tree.add(new Edge(2, 1, 0.1));

        tree.rootAtMidpoint();

        List<Edge> edges = tree.edges().toList();
        assertEquals(edges.size(), 2);
        assertEquals(edges.get(0), new Edge(2, 0, 100));
        assertEquals(edges.get(1), new Edge(2, 1, 0.1));
    }

    @Test
    public void rootAtMidpoint_OddTopologicalDiameter_SplitsCentralEdgeWeight() {
        Tree tree = new Tree(new String[]{"A", "B", "C"});
        tree.add(new Edge(3, 0, 100));
        tree.add(new Edge(3, 4, 2));
        tree.add(new Edge(4, 1, 1));
        tree.add(new Edge(4, 2, 1));

        tree.rootAtMidpoint();

        List<Edge> edges = tree.edges().toList();
        assertEquals(edges.size(), 5);
        assertEquals(edges.get(0), new Edge(5, 3, 1));
        assertEquals(edges.get(1), new Edge(3, 0, 100));
        assertEquals(edges.get(2), new Edge(5, 4, 1));
        assertEquals(edges.get(3), new Edge(4, 1, 1));
        assertEquals(edges.get(4), new Edge(4, 2, 1));
    }

    @Test
    public void rootAtMidpoint_LongPath_DoesNotOverflowStack() {
        int depth = 200_000;
        Tree tree = new Tree(new String[]{"A", "B"});
        int previous = 0;
        for (int i = 0; i < depth; i++) {
            int next = i == depth - 1 ? 1 : i + 2;
            tree.add(new Edge(previous, next, 1));
            previous = next;
        }

        tree.rootAtMidpoint();

        assertEquals(tree.edges().count(), depth);
    }

}
