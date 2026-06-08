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

public class SaitouNeiTest {

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

        Tree tree = new SaitouNei().process(matrix);

        assertRootedAtMidpoint(tree, values.length);
    }

    private void assertRootedAtMidpoint(Tree tree, int terminals) {
        List<Edge> edges = tree.edges().collect(Collectors.toList());
        List<Integer> roots = edges.stream().map(Edge::from).distinct()
                .filter(i -> edges.stream().noneMatch(edge -> edge.to() == i)).toList();

        assertEquals(roots.size(), 1);
        assertTrue(roots.getFirst() >= terminals);
        assertEquals(maxDepth(roots.getFirst(), -1, adjacency(edges), 0), diameter(edges, terminals) / 2, 0.0000001);
    }

    private double diameter(List<Edge> edges, int terminals) {
        Map<Integer, List<Edge>> adjacency = adjacency(edges);
        double diameter = 0;
        for (int i = 0; i < terminals; i++)
            for (int j = i + 1; j < terminals; j++)
                diameter = Math.max(diameter, distance(i, j, -1, adjacency, 0));
        return diameter;
    }

    private double distance(int current, int target, int previous, Map<Integer, List<Edge>> adjacency, double distance) {
        if (current == target)
            return distance;
        for (Edge edge : adjacency.getOrDefault(current, List.of())) {
            int next = edge.from() == current ? edge.to() : edge.from();
            if (next == previous)
                continue;
            double found = distance(next, target, current, adjacency, distance + edge.distance());
            if (!Double.isNaN(found))
                return found;
        }
        return Double.NaN;
    }

    private double maxDepth(int current, int previous, Map<Integer, List<Edge>> adjacency, double depth) {
        double max = depth;
        for (Edge edge : adjacency.getOrDefault(current, List.of())) {
            int next = edge.from() == current ? edge.to() : edge.from();
            if (next != previous)
                max = Math.max(max, maxDepth(next, current, adjacency, depth + edge.distance()));
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
