package pt.ist.phylolib.command.algorithm.edmonds;

import pt.ist.phylolib.command.algorithm.Algorithm;
import pt.ist.phylolib.data.matrix.Matrix;
import pt.ist.phylolib.data.matrix.MemoryMappedMatrix;
import pt.ist.phylolib.data.tree.Edge;
import pt.ist.phylolib.data.tree.Tree;

import java.io.IOException;
import java.util.*;

/**
 * Responsible for calculating a {@link Tree phylogenetic tree} from a
 * {@link Matrix distance matrix} using the Edmonds algorithm.
 */
public final class Edmonds extends Algorithm {

	private Comparator<EdgeNode> comparator;
	private Comparator<Edge> maxDisjointCmp;

	/** A union-find data structure to maintain the weakly connected components of the forest */
	private DisjointSet weaklyConnected;

	/** A union-find data structure to maintain the strongly connected components of the forest */
	private WeightedDisjointSet stronglyConnected;

	/** A list of vertices to be processed. Initialized with all the vertices in 𝑉 */
	private LinkedList<Integer> roots;
	private Forest forest;

	/**  Array that for each i stores a node from the forest which is associated with the minimum weight edge incident in node i */
	private EdgeNode[] inEdgeNode;

	/** array stores the leaf nodes of the forest */
    protected EdgeNode[] leaves;

	/** A list that stores for each representative cycle vertex 𝑣 the list of cycle edge nodes in F */
	private List<List<EdgeNode>> edgeNodeCycle;

	/********************************************
	 * External Memory auxiliary data structures
	 ********************************************/


    /**
     * Maps SCC representative ID to the set of all node IDs that have been merged into this SCC.
     * Updated during contractionPhase when cycles are detected and nodes are unified.
     * Used during queue re-initialization to load edges for all nodes in the SCC.
     */
    private Map<Integer, Set<Integer>> sccComposition;

    /**
     * Tracks the number of edges examined for each node when running with lazy loading with on-demand edge computation.
     * If a node's numExaminedEdges reaches the numNeighbors limit, no more nearest neighbor searches will be
     * performed for that node and instead we compute the entire list of incoming edges
     */
    private int[] numExaminedEdges;

    /**
     * Used to track if a SCC has been previously initialized with on-demand edge computation and 
     * with nearest neighbor search. If it has and it failed to find an adequate edge during the
     * contraction phase, prevFailure[root] is set to true and the queue must be re-initialized
     * with the complete list of incoming edges to that SCC
     */
    private boolean[] prevFailure = null;

	private Map<Integer, Integer> nodeMap;

	/** The base file name for the externally stored input graph */
	private String baseFileName;

	private MemoryMappedMatrix matrix;


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
		if (matrix instanceof MemoryMappedMatrix) {
			this.matrix = (MemoryMappedMatrix) matrix;
		}
		initComparator();
		this.stronglyConnected = new WeightedDisjointSet(size);
		this.weaklyConnected = new DisjointSet(size);
		this.inEdgeNode = new EdgeNode[size];
		this.forest = new Forest(size);
		this.roots = new LinkedList<>();
		this.edgeNodeCycle = new ArrayList<>(size);
		for (int i = 0; i < size; i++) {
			this.roots.add(i);
			this.edgeNodeCycle.add(i, null);
		}
	}

	private void initComparator() {
		this.comparator = Comparator.comparing(EdgeNode::getEdge, Comparator.comparingDouble(this::getAdjustedWeight)
				.thenComparingInt(i -> Integer.min(i.from(), i.to()))
				.thenComparingInt(i -> Integer.max(i.from(), i.to())));

		this.maxDisjointCmp = (a, b) -> Double.compare(
			getAdjustedWeight(a),
			getAdjustedWeight(b)
		);
	}

	private void contract(int u, int v, int root, EdgeNode min) {
		// store nodes in cycle
		List<Integer> contractionSet = new ArrayList<>();
		contractionSet.add(stronglyConnected.findSet(v));

		// keep track of the edges in the cycle
		List<EdgeNode> nodes = new ArrayList<>();
		nodes.add(min);

		// map the EdgeNode incident in a node
		Map<Integer, EdgeNode> map = new HashMap<>();
		map.put(stronglyConnected.findSet(v), min);

		// since a cycle as arisen we need to choose a new minimum weight edge incident in node root
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

		updateReducedCosts(contractionSet, map, max);

		for (EdgeNode node : nodes) { // Perform union of the nodes in the cycle
			stronglyConnected.unionSet(node.getEdge().from(), node.getEdge().to());
		}

		int rep = stronglyConnected.findSet(edge.to());
		updateSCCComposition(rep, contractionSet);
		roots.add(0, rep);
		forest.updateMax(rep, dst);
		edgeNodeCycle.set(rep, nodes);
	}

	private void updateReducedCosts(List<Integer> contractionSet, Map<Integer, EdgeNode> map, double sigma) {
		for (Integer node : contractionSet) {
			stronglyConnected.addWeight(node, sigma - getAdjustedWeight(map.get(node).getEdge()));
		}
	}

	private void updateSCCComposition(int rep, List<Integer> contractionSet) {
		Set<Integer> sccSet = sccComposition.getOrDefault(rep, new HashSet<>());
		for (Integer node : contractionSet) {
			if (rep != node) {
				sccSet.add(node);
				if (sccComposition.containsKey(node)) {
					sccSet.addAll(sccComposition.get(node));
					sccComposition.remove(node);
				}
			}
		}
		sccComposition.put(rep, sccSet);
	}

    private EdgeNode getMinEdgeNode(int root) {
        Set<Integer> nodesInSCC = sccComposition.getOrDefault(
			stronglyConnected.findSet(root),
			Set.of(stronglyConnected.findSet(root))
		);
        
		Edge e;
		try {
        	e = matrix.getGraphMapper().findMinSafeEdgeIncomingToSCC(baseFileName, stronglyConnected, nodesInSCC, maxDisjointCmp);    		
		} catch (IOException ioe) {
			ioe.printStackTrace();
			throw new RuntimeException("Caught an IOException when finding a min safe edge from the memory mapped files");
		}
			if (e == null || stronglyConnected.sameSet(e.from(), e.to())) {
			forest.addEntryToRset(root);
			return null;
		}

		return new EdgeNode(e);
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
