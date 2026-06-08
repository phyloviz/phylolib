package pt.ist.phylolib.data.tree;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Represents a phylogenetic tree as a set of profiles identified by their ids and the {@link Edge edges} that connect those profiles.
 */
public final class Tree {

    private final String[] ids;
    private final List<Edge> edges;

    /**
     * Creates a phylogenetic tree corresponding to the given set of ids with no {@link Edge edges} connecting them.
     *
     * @param ids the ids of the profiles of this tree
     */
    public Tree(String[] ids) {
        this.ids = ids;
        this.edges = new ArrayList<>();
    }

    /**
     * Creates a phylogenetic tree corresponding to the given set of ids and {@link Edge edges}.
     *
     * @param ids   the ids of the profiles of this tree
     * @param edges the edges connecting the profiles
     */
    public Tree(String[] ids, List<Edge> edges) {
        this.ids = ids;
        this.edges = edges;
    }

    public String[] ids() {
        return ids;
    }

    public Stream<Edge> edges() {
        return edges.stream();
    }

    /**
     * Adds a given {@link Edge edge} to this tree.
     *
     * @param edge the edge to add to this tree.
     */
    public void add(Edge edge) {
        edges.add(edge);
    }

    /**
     * Removes a given {@link Edge edge} from this tree.
     *
     * @param edge the edge to remove from this tree
     */
    public void remove(Edge edge) {
        edges.remove(edge);
    }

    /**
     * Roots this tree at the midpoint of the longest path between two labelled
     * profiles.
     */
    public void rootAtMidpoint() {
        if (ids == null || ids.length < 2 || edges.isEmpty())
            return;

        Path longest = longestPath();
        if (longest == null)
            return;

        int root = orientAtMidpoint(longest);
        orientFrom(root);
    }

    private Path longestPath() {
        Path longest = null;
        for (int from = 0; from < ids.length; from++) {
            Map<Integer, Step> parents = new HashMap<>();
            Map<Integer, Double> distances = new HashMap<>();
            distances.put(from, 0.0);
            collectPaths(from, -1, 0.0, parents, distances, adjacency());

            for (int to = from + 1; to < ids.length; to++) {
                Double distance = distances.get(to);
                if (distance != null && (longest == null || distance > longest.distance))
                    longest = new Path(from, to, distance, parents);
            }
        }
        return longest;
    }

    private void collectPaths(int current, int previous, double distance, Map<Integer, Step> parents,
                              Map<Integer, Double> distances, Map<Integer, List<Neighbour>> adjacency) {
        for (Neighbour neighbour : adjacency.getOrDefault(current, List.of())) {
            if (neighbour.node == previous)
                continue;
            parents.put(neighbour.node, new Step(current, neighbour.distance));
            distances.put(neighbour.node, distance + neighbour.distance);
            collectPaths(neighbour.node, current, distance + neighbour.distance, parents, distances, adjacency);
        }
    }

    private int orientAtMidpoint(Path path) {
        double midpoint = path.distance / 2;
        double distance = 0.0;
        int node = path.to;
        List<Edge> pathEdges = new ArrayList<>();

        while (node != path.from) {
            Step step = path.parents.get(node);
            pathEdges.add(new Edge(step.parent, node, step.distance));
            node = step.parent;
        }

        for (int i = pathEdges.size() - 1; i >= 0; i--) {
            Edge edge = pathEdges.get(i);
            if (Double.compare(distance, midpoint) == 0)
                return edge.from();
            if (distance + edge.distance() > midpoint) {
                int root = nextNode();
                double fromRoot = midpoint - distance;
                double toRoot = edge.distance() - fromRoot;
                removeUndirected(edge);
                edges.add(new Edge(root, edge.from(), fromRoot));
                edges.add(new Edge(root, edge.to(), toRoot));
                return root;
            }
            distance += edge.distance();
        }

        return path.to;
    }

    private void orientFrom(int root) {
        Map<Integer, List<Neighbour>> adjacency = adjacency();
        List<Edge> oriented = new ArrayList<>(edges.size());
        orient(root, -1, adjacency, oriented);
        edges.clear();
        edges.addAll(oriented);
    }

    private void orient(int current, int previous, Map<Integer, List<Neighbour>> adjacency, List<Edge> oriented) {
        for (Neighbour neighbour : adjacency.getOrDefault(current, List.of())) {
            if (neighbour.node == previous)
                continue;
            oriented.add(new Edge(current, neighbour.node, neighbour.distance));
            orient(neighbour.node, current, adjacency, oriented);
        }
    }

    private Map<Integer, List<Neighbour>> adjacency() {
        Map<Integer, List<Neighbour>> adjacency = new HashMap<>();
        for (Edge edge : edges) {
            adjacency.computeIfAbsent(edge.from(), key -> new ArrayList<>())
                    .add(new Neighbour(edge.to(), edge.distance()));
            adjacency.computeIfAbsent(edge.to(), key -> new ArrayList<>())
                    .add(new Neighbour(edge.from(), edge.distance()));
        }
        return adjacency;
    }

    private void removeUndirected(Edge target) {
        edges.removeIf(edge -> edge.distance() == target.distance()
                && ((edge.from() == target.from() && edge.to() == target.to())
                || (edge.from() == target.to() && edge.to() == target.from())));
    }

    private int nextNode() {
        return edges.stream().flatMap(edge -> Stream.of(edge.from(), edge.to())).max(Comparator.naturalOrder())
                .orElse(ids.length - 1) + 1;
    }

    private record Path(int from, int to, double distance, Map<Integer, Step> parents) {
    }

    private record Step(int parent, double distance) {
    }

    private record Neighbour(int node, double distance) {
    }

}
