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
     * Roots this tree at the topological midpoint of the longest path between two
     * labelled profiles. Edge weights are preserved but do not influence the
     * choice of root.
     */
    public void rootAtMidpoint() {
        if (ids == null || ids.length < 2 || edges.isEmpty())
            return;

        Map<Integer, List<Neighbour>> adjacency = adjacency();
        if (!isConnected(adjacency))
            return;

        Path longest = longestPath(adjacency);
        if (longest == null)
            return;

        int root = orientAtMidpoint(longest);
        orientFrom(root);
    }

    private boolean isConnected(Map<Integer, List<Neighbour>> adjacency) {
        Map<Integer, Step> parents = new HashMap<>();
        Map<Integer, Integer> depths = new HashMap<>();
        depths.put(0, 0);
        collectPaths(0, -1, 0, parents, depths, adjacency);

        for (int i = 0; i < ids.length; i++)
            if (!depths.containsKey(i))
                return false;

        for (Edge edge : edges)
            if (!depths.containsKey(edge.from()) || !depths.containsKey(edge.to()))
                return false;

        return true;
    }

    private Path longestPath(Map<Integer, List<Neighbour>> adjacency) {
        Path longest = null;
        for (int from = 0; from < ids.length; from++) {
            Map<Integer, Step> parents = new HashMap<>();
            Map<Integer, Integer> depths = new HashMap<>();
            depths.put(from, 0);
            collectPaths(from, -1, 0, parents, depths, adjacency);

            for (int to = from + 1; to < ids.length; to++) {
                Integer depth = depths.get(to);
                if (depth != null && (longest == null || depth > longest.depth))
                    longest = new Path(from, to, depth, parents);
            }
        }
        return longest;
    }

    private void collectPaths(int current, int previous, int depth, Map<Integer, Step> parents,
                              Map<Integer, Integer> depths, Map<Integer, List<Neighbour>> adjacency) {
        for (Neighbour neighbour : adjacency.getOrDefault(current, List.of())) {
            if (neighbour.node == previous)
                continue;
            parents.put(neighbour.node, new Step(current, neighbour.distance));
            depths.put(neighbour.node, depth + 1);
            collectPaths(neighbour.node, current, depth + 1, parents, depths, adjacency);
        }
    }

    private int orientAtMidpoint(Path path) {
        int node = path.to;
        List<Edge> pathEdges = new ArrayList<>();

        while (node != path.from) {
            Step step = path.parents.get(node);
            pathEdges.add(new Edge(step.parent, node, step.distance));
            node = step.parent;
        }

        int midpoint = path.depth / 2;
        if (path.depth % 2 == 0)
            return pathEdges.get(pathEdges.size() - midpoint).to();

        Edge edge = pathEdges.get(pathEdges.size() - midpoint - 1);
        int root = nextNode();
        double halfDistance = edge.distance() / 2;
        removeUndirected(edge);
        edges.add(new Edge(root, edge.from(), halfDistance));
        edges.add(new Edge(root, edge.to(), edge.distance() - halfDistance));
        return root;
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

    private record Path(int from, int to, int depth, Map<Integer, Step> parents) {
    }

    private record Step(int parent, double distance) {
    }

    private record Neighbour(int node, double distance) {
    }

}
