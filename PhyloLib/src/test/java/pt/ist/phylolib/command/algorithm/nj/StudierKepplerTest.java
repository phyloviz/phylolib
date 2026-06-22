package pt.ist.phylolib.command.algorithm.nj;

import pt.ist.phylolib.data.matrix.Matrix;
import pt.ist.phylolib.data.tree.Edge;
import pt.ist.phylolib.data.tree.Tree;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class StudierKepplerTest {

    @DataProvider
    public Object[][] data() {
        return new Object[][]{
                {new double[][]{
                        {},
                        {1.0}}},
                {new double[][]{
                        {},
                        {2.0},
                        {2.0, 1.0}}},
                {new double[][]{
                        {},
                        {5.0},
                        {9.0, 10.0},
                        {9.0, 10.0, 8.0},
                        {8.0, 9.0, 7.0, 3.0}}},
                {new double[][]{
                        {},
                        {3.0},
                        {3.0, 3.0},
                        {3.0, 3.0, 2.0},
                        {3.0, 3.0, 3.0, 3.0},
                        {3.0, 3.0, 3.0, 3.0, 3.0},
                        {3.0, 2.0, 3.0, 3.0, 3.0, 3.0},
                        {3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0}}}
        };
    }

    @Test(dataProvider = "data")
    public void process_Valid_Success(double[][] values) {
        Matrix matrix = new Matrix(true, null, values);

        Tree tree = new StudierKeppler().process(matrix);

        assertRootedAtMidpoint(tree, values.length);
    }

    private void assertRootedAtMidpoint(Tree tree, int terminals) {
        List<Edge> edges = tree.edges().collect(Collectors.toList());
        List<Integer> roots = edges.stream().map(Edge::from).distinct()
                .filter(i -> edges.stream().noneMatch(edge -> edge.to() == i)).toList();

        assertEquals(roots.size(), 1);
        assertTrue(roots.getFirst() >= terminals);
        assertEquals(maxDepth(roots.getFirst(), -1, adjacency(edges), 0), diameter(edges, terminals) / 2);
    }

    private int diameter(List<Edge> edges, int terminals) {
        Map<Integer, List<Edge>> adjacency = adjacency(edges);
        int diameter = 0;
        for (int i = 0; i < terminals; i++)
            for (int j = i + 1; j < terminals; j++)
                diameter = Math.max(diameter, distance(i, j, -1, adjacency, 0));
        return diameter;
    }

    private int distance(int current, int target, int previous, Map<Integer, List<Edge>> adjacency, int distance) {
        if (current == target)
            return distance;
        for (Edge edge : adjacency.getOrDefault(current, List.of())) {
            int next = edge.from() == current ? edge.to() : edge.from();
            if (next == previous)
                continue;
            int found = distance(next, target, current, adjacency, distance + 1);
            if (found >= 0)
                return found;
        }
        return -1;
    }

    private int maxDepth(int current, int previous, Map<Integer, List<Edge>> adjacency, int depth) {
        int max = depth;
        for (Edge edge : adjacency.getOrDefault(current, List.of())) {
            int next = edge.from() == current ? edge.to() : edge.from();
            if (next != previous)
                max = Math.max(max, maxDepth(next, current, adjacency, depth + 1));
        }
        return max;
    }

    private Map<Integer, List<Edge>> adjacency(List<Edge> edges) {
        Map<Integer, List<Edge>> adjacency = new HashMap<>();
        for (Edge edge : edges) {
            adjacency.computeIfAbsent(edge.from(), key -> new ArrayList<>()).add(edge);
            adjacency.computeIfAbsent(edge.to(), key -> new ArrayList<>()).add(edge);
        }
        return adjacency;
    }

}
