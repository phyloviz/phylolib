package pt.ist.phylolib.command.algorithm.goeburst;

import pt.ist.phylolib.cli.Option;
import pt.ist.phylolib.cli.Options;
import pt.ist.phylolib.command.algorithm.Algorithm;
import pt.ist.phylolib.data.Context;
import pt.ist.phylolib.data.matrix.DistanceScope;
import pt.ist.phylolib.data.matrix.Matrix;
import pt.ist.phylolib.data.tree.Edge;
import pt.ist.phylolib.data.tree.Tree;

import java.util.Arrays;

public class GoeBURST extends Algorithm {

    private int lvs = 3;
    private boolean distanceScopeConfigured;

    @Override
    public DistanceScope requiredDistanceScope() {
        return new DistanceScope.Bounded(lvs);
    }

    @Override
    public void configureRequiredDistanceScope(Options options) {
        if (distanceScopeConfigured)
            return;
        String configuredLvs = options.remove(Option.LVS);
        lvs = Integer.parseInt(configuredLvs);
        distanceScopeConfigured = true;
    }

    @Override
    public void init(Context context, Options options) {
        // Preserve direct programmatic use of init while the CLI configures
        // the scope before loading the matrix.
        configureRequiredDistanceScope(options);
    }

    @Override
    protected Tree processImpl(Matrix matrix) {
        String[] ids = matrix.ids();
        int size = matrix.size();
        Tree tree = new Tree(ids);

        int[][] lv = new int[size][lvs];

        // Pre-calculate LV stats
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (i == j)
                    continue;
                double d = matrix.distance(i, j);
                if (d > 0 && d <= lvs) {
                    lv[i][(int) d - 1]++;
                }
            }
        }

        // Prim's Algorithm
        double[] dist = new double[size];
        int[] parent = new int[size];
        boolean[] visited = new boolean[size];

        Arrays.fill(dist, Double.MAX_VALUE);
        Arrays.fill(parent, -1);
        MinHeap heap = new MinHeap(dist);

        while (!heap.isEmpty()) {
            int u = heap.extractMin();
            if (u == -1)
                break;

            visited[u] = true;
            if (parent[u] != -1) {
                tree.add(new Edge(parent[u], u, dist[u]));
            }

            // Update neighbors
            for (int v = 0; v < size; v++) {
                if (!visited[v]) {
                    double w = matrix.distance(u, v);
                    if (w > 0 && w <= lvs) {
                        if (w < dist[v]) {
                            dist[v] = w;
                            parent[v] = u;
                            heap.decreaseKey(v);
                        } else if (w == dist[v]) {
                            // Tie-break: compare edge (u, v) vs (parent[v], v)
                            // Note: tiebreak returns < 0 if first is better (smaller)
                            if (tiebreak(lv, ids, u, v, parent[v], v) < 0) {
                                parent[v] = u;
                            }
                        }
                    }
                }
            }
        }

        return tree;
    }

    // Tiebreak Logic adapted to use primitives
    private int tiebreak(int[][] lv, String[] ids, int ifrom, int ito, int jfrom, int jto) {
        int diff;
        for (int index = 0; index < lvs; index++) {
            diff = Integer.compare(Math.max(lv[jfrom][index], lv[jto][index]),
                    Math.max(lv[ifrom][index], lv[ito][index]));
            if (diff != 0)
                return diff;
            diff = Integer.compare(Math.min(lv[jfrom][index], lv[jto][index]),
                    Math.min(lv[ifrom][index], lv[ito][index]));
            if (diff != 0)
                return diff;
        }
        diff = Integer.compare(Math.min(ifrom, ito), Math.min(jfrom, jto));
        return diff != 0 ? diff
                : compare(ids[compare(ids[ifrom], ids[ito]) > 0 ? ifrom : ito],
                ids[compare(ids[jfrom], ids[jto]) > 0 ? jfrom : jto]);
    }

    private int compare(String i, String j) {
        return i.length() == j.length() ? i.compareTo(j) : (i.length() - j.length());
    }
}
