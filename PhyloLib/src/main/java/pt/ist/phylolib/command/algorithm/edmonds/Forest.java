package pt.ist.phylolib.command.algorithm.edmonds;

import pt.ist.phylolib.data.matrix.Matrix;
import pt.ist.phylolib.data.tree.Edge;
import pt.ist.phylolib.data.tree.Tree;

import java.util.*;
import java.util.stream.Collectors;

final class Forest {

	private final Map<Edge, EdgeNode> edges;
	private final EdgeNode[] pi;
	private final Set<Integer> rset;
	private final int[] max;

	Forest(int size) {
		this.edges = new HashMap<>(size * size - size);
		this.pi = new EdgeNode[size];
		this.rset = new HashSet<>();
		this.max = new int[size];
		for (int i = 0; i < size; i++)
			this.max[i] = i;
	}

	void add(EdgeNode node) {
		edges.put(node.getEdge(), node);
	}

	void addPi(int p, EdgeNode node) {
		pi[p] = node;
	}

	void addEntryToRset(int root) {
		rset.add(root);
	}

	void updateMax(int p1, int p2) {
		max[p1] = max[p2];
	}

	Tree expansion(Matrix matrix) {
		Tree tree = new Tree(matrix.ids());
		List<EdgeNode> nodes = edges.values().stream().filter(EdgeNode::isRoot).collect(Collectors.toList());
		rset.stream().map(node -> pi[max[node]]).filter(Objects::nonNull).forEach(node -> removeFromRoots(node, nodes));
		while (!nodes.isEmpty()) {
			EdgeNode node = nodes.remove(0);
			if (node.isRemove())
				continue;
			Edge edge = node.getEdge();
			tree.add(edge);
			removeFromRoots(pi[edge.to()], nodes);
		}
		return tree;
	}

	private void removeFromRoots(EdgeNode node, List<EdgeNode> roots) {
		for (; node != null; node = node.getParent()) {
			node.setRemove();
			for (EdgeNode child : node.getChildren()) {
				child.setParent(null);
				roots.add(child);
			}
		}
	}

}
