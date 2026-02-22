package pt.ist.phylolib.command.algorithm.edmonds;

import pt.ist.phylolib.command.algorithm.Algorithm;
import pt.ist.phylolib.data.matrix.Matrix;
import pt.ist.phylolib.data.tree.Edge;
import pt.ist.phylolib.data.tree.Tree;

import java.util.*;

/**
 * Responsible for calculating a {@link Tree phylogenetic tree} from a
 * {@link Matrix distance matrix} using the Edmonds algorithm.
 */
public final class Edmonds extends Algorithm {

	private Comparator<EdgeNode> comparator;
	private BinomialHeap[] queues;
	private DisjointSet weaklyConnected;
	private WeightedDisjointSet stronglyConnected;
	private LinkedList<Integer> roots;
	private Forest forest;
	private EdgeNode[] inEdgeNode;
	private List<List<EdgeNode>> edgeNodeCycle;

	@Override
	protected Tree processImpl(Matrix matrix) {
		init(matrix);
		while (!roots.isEmpty()) {
			int root = roots.pop();
			EdgeNode min = getMinEdgeNode(root);
			if (min == null)
				continue;
			processCameriniForest(min, root);
			int u = min.getEdge().from();
			int v = min.getEdge().to();
			if (weaklyConnected.findSet(u) != weaklyConnected.findSet(v)) {
				inEdgeNode[root] = min;
				weaklyConnected.unionSet(u, v);
			} else
				contract(u, v, root, min);
		}
		return forest.expansion(matrix);
	}

	private void init(Matrix matrix) {
		int size = matrix.size();
		this.comparator = Comparator.comparing(EdgeNode::getEdge, Comparator.comparingDouble(this::getAdjustedWeight)
				.thenComparingInt(i -> Integer.min(i.from(), i.to()))
				.thenComparingInt(i -> Integer.max(i.from(), i.to())));
		this.stronglyConnected = new WeightedDisjointSet(size);
		this.weaklyConnected = new DisjointSet(size);
		this.queues = new BinomialHeap[size];
		this.inEdgeNode = new EdgeNode[size];
		this.forest = new Forest(size);
		this.roots = new LinkedList<>();
		this.edgeNodeCycle = new ArrayList<>(size);
		for (int i = 0; i < size; i++) {
			this.roots.add(i);
			this.queues[i] = new BinomialHeap(this.comparator);
			this.edgeNodeCycle.add(i, null);
		}
		for (int i = 0; i < size; i++) {
			for (int j = 0; j < size; j++)
				if (i != j)
					queues[j].push(new EdgeNode(new Edge(i, j, matrix.distance(i, j))));
		}
	}

	private void contract(int u, int v, int root, EdgeNode min) {
		List<Integer> contractionSet = new ArrayList<>();
		contractionSet.add(stronglyConnected.findSet(v));
		List<EdgeNode> nodes = new ArrayList<>();
		nodes.add(min);
		Map<Integer, EdgeNode> map = new HashMap<>();
		map.put(stronglyConnected.findSet(v), min);
		inEdgeNode[root] = null;
		for (int i = stronglyConnected.findSet(u); inEdgeNode[i] != null; i = stronglyConnected
				.findSet(inEdgeNode[i].getEdge().from())) {
			map.put(i, inEdgeNode[i]);
			nodes.add(inEdgeNode[i]);
			contractionSet.add(i);
		}
		Edge edge = Collections.max(nodes, comparator).getEdge();
		int dst = stronglyConnected.findSet(edge.to());
		double max = getAdjustedWeight(edge);
		for (Integer node : contractionSet)
			stronglyConnected.addWeight(node, max - getAdjustedWeight(map.get(node).getEdge()));
		for (EdgeNode node : nodes)
			stronglyConnected.unionSet(node.getEdge().from(), node.getEdge().to());
		int rep = stronglyConnected.findSet(edge.to());
		roots.add(0, rep);
		performHeapUnion(rep, contractionSet);
		forest.updateMax(rep, dst);
		edgeNodeCycle.set(rep, nodes);
	}

	private void performHeapUnion(int rep, List<Integer> contractionSet) {
		BinomialHeap heap = queues[rep];
		for (Integer node : contractionSet)
			if (rep != node)
				heap.union(queues[node]);
	}

	private EdgeNode getMinEdgeNode(int root) {
		BinomialHeap pq = queues[root];
		if (pq.isEmpty()) {
			forest.addEntryToRset(root);
			return null;
		}
		EdgeNode minEdgeNode = pq.pop();
		Edge min = minEdgeNode.getEdge();
		while (!pq.isEmpty() && stronglyConnected.sameSet(min.from(), min.to())) {
			minEdgeNode = pq.pop();
			min = minEdgeNode.getEdge();
		}
		if (stronglyConnected.sameSet(min.from(), min.to())) {
			forest.addEntryToRset(root);
			return null;
		}
		return minEdgeNode;
	}

	private void processCameriniForest(EdgeNode minEdgeNode, int root) {
		forest.add(minEdgeNode);
		if (edgeNodeCycle.get(root) == null)
			forest.addPi(root, minEdgeNode);
		else
			for (EdgeNode node : edgeNodeCycle.get(root)) {
				node.setParent(minEdgeNode);
				minEdgeNode.addChild(node);
			}
	}

	private double getAdjustedWeight(Edge edge) {
		return edge.distance() + stronglyConnected.findWeight(edge.to());
	}

}
