package pt.ist.phylolib.command.algorithm.edmonds;

import pt.ist.phylolib.cli.Data;
import pt.ist.phylolib.cli.Option;
import pt.ist.phylolib.cli.Options;
import pt.ist.phylolib.command.algorithm.Algorithm;
import pt.ist.phylolib.data.Context;
import pt.ist.phylolib.data.File;
import pt.ist.phylolib.data.dataset.Dataset;
import pt.ist.phylolib.data.dataset.DatasetParser;
import pt.ist.phylolib.data.dataset.Profile;
import pt.ist.phylolib.data.matrix.Matrix;
import pt.ist.phylolib.data.matrix.MemoryMappedMatrix;
import pt.ist.phylolib.data.memorymapper.EdgeListMapper;
import pt.ist.phylolib.data.memorymapper.GraphMapper;
import pt.ist.phylolib.data.tree.Edge;
import pt.ist.phylolib.data.tree.Tree;
import pt.ist.phylolib.exception.MissingInputException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Responsible for calculating a {@link Tree phylogenetic tree} from a
 * {@link Matrix distance matrix} using the Edmonds algorithm.
 * <p>
 * Edmonds operates exclusively on a {@link MemoryMappedMatrix}. It reads
 * profiles from {@code --input}, builds or loads graph state from
 * {@code --prev-state}, and produces a tree written via the pipeline.
 */
public final class Edmonds extends Algorithm {

	private static final int BATCH_SIZE = 1000;

	private Comparator<EdgeNode> comparator;
	private Comparator<Edge> maxDisjointCmp;

	/** A union-find data structure to maintain the weakly connected components of the forest */
	private DisjointSet weaklyConnected;

	/** A union-find data structure to maintain the strongly connected components of the forest */
	private WeightedDisjointSet stronglyConnected;

	/** A list of vertices to be processed. Initialized with all the vertices in V */
	private LinkedList<Integer> roots;
	private Forest forest;

	/**  Array that for each i stores a node from the forest which is associated with the minimum weight edge incident in node i */
	private EdgeNode[] inEdgeNode;

	/** array stores the leaf nodes of the forest */
    protected EdgeNode[] leaves;

	/** A list that stores for each representative cycle vertex v the list of cycle edge nodes in F */
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
     * Tracks the number of edges examined for each node.
	 * <p>
	 * This is used to optimize the retrieval of minimum edges during Edmonds' contraction phase.
	 * If |V|-1 edges have been examined
     */
    private int[] numExaminedEdges;

	/** The base file name for the externally stored input graph */
	private String baseFileName;

	private MemoryMappedMatrix matrix;

	@Override
	public void init(Context context, Options options) throws MissingInputException {
		// Read mandatory input file path
		String input = options.remove(Option.INPUT);
		if (input == null)
			throw new MissingInputException("INPUT");

		// Parse input file as a dataset (fasta/ml/snp)
		File inputFile = File.get(input, Data.DATASET);
		if (inputFile == null)
			throw new MissingInputException("INPUT (invalid format, expected format:path)");

		Dataset dataset;
		try (java.util.stream.Stream<String> lines = Files.lines(inputFile.path())) {
			dataset = ((DatasetParser) inputFile.processor()).parse(lines, options);
		} catch (Exception e) {
			throw new MissingInputException("INPUT (file read error: " + e.getMessage() + ")");
		}
		if (dataset == null)
			throw new MissingInputException("INPUT (empty or invalid dataset)");

		// Compute prevStateBase from --prev-state or default
		String prevStatePath = options.remove(Option.PREV_STATE);
		String prevStateBase;
		if (prevStatePath != null) {
			prevStateBase = prevStatePath.contains(":") ? prevStatePath.split(":", 2)[1] : prevStatePath;
		} else {
			String inputPath = inputFile.path().toString();
			int dotIndex = inputPath.lastIndexOf('.');
			prevStateBase = (dotIndex > 0 ? inputPath.substring(0, dotIndex) : inputPath) + "_graph_state";
		}

		// Build or load GraphMapper
		GraphMapper mapper;
		try {
			Path nodesFile = Path.of(prevStateBase + "_nodes.dat");
			if (Files.exists(nodesFile)) {
				mapper = new GraphMapper(prevStateBase);
				buildIncrementalGraph(dataset, mapper, prevStateBase);
			} else {
				mapper = buildGraphFromProfiles(dataset, prevStateBase);
			}
		} catch (IOException e) {
			throw new MissingInputException("INPUT (failed to initialize graph: " + e.getMessage() + ")");
		}

		// Create MemoryMappedMatrix
		String[] ids = dataset.ids();
		this.matrix = new MemoryMappedMatrix(false, ids, prevStateBase, mapper);
		this.baseFileName = prevStateBase;

		// Initialize Edmonds internal state
		initInternal(this.matrix);
	}

	/**
	 * Builds graph state from profiles when no prev_state exists.
	 * Computes all pairwise GrapeTree distances and writes edges in batches
	 * of BATCH_SIZE destination profiles.
	 */
	private GraphMapper buildGraphFromProfiles(Dataset dataset, String prevStateBase) throws IOException {
		List<Profile> profiles = IntStream.range(0, dataset.size())
				.mapToObj(dataset::profile)
				.collect(Collectors.toList());
		int sequenceLength = dataset.profile(0).size();
		int n = profiles.size();

		// Create node index (writes _nodes.dat, _nodes_idMaps.ser, _nodes_offsetMap.ser)
		GraphMapper mapper = new GraphMapper(prevStateBase, profiles, sequenceLength);

		// Write edges in batches of BATCH_SIZE destination profiles
		for (int batchStart = 0; batchStart < n; batchStart += BATCH_SIZE) {
			int batchEnd = Math.min(batchStart + BATCH_SIZE, n);
			for (int j = batchStart; j < batchEnd; j++) {
				List<Edge> edges = new ArrayList<>(n);
				for (int i = 0; i < n; i++) {
					if (i == j) continue;
					double dist = grapeTreeDistance(profiles.get(i), profiles.get(j));
					if (Double.isFinite(dist) && dist > 0) {
						edges.add(new Edge(i, j, dist));
					}
				}
				String edgeFile = prevStateBase + "_edges.dat";
				if (!edges.isEmpty()) {
					EdgeListMapper.addEdges(edges, j, edgeFile);
				} else {
					String nodeEdgeFile = prevStateBase + "_edges_node" + j + ".dat";
					EdgeListMapper.writeEdgeArray(nodeEdgeFile, List.of());
				}
			}
		}

		return mapper;
	}

	/**
	 * Computes the GrapeTree distance between two profiles.
	 * This is an asymmetric distance: d(i, j) may differ from d(j, i).
	 */
	private static double grapeTreeDistance(Profile i, Profile j) {
		double differences = 0;
		double nonmissing = 0;
		for (int l = 0; l < i.size(); l++) {
			if (j.locus(l) != null) {
				nonmissing++;
				if (!j.locus(l).equals(i.locus(l)))
					differences++;
			}
		}
		return nonmissing == 0 ? Double.POSITIVE_INFINITY : differences / nonmissing;
	}

	/**
	 * Builds edges incrementally when prev_state exists.
	 * Loads existing profiles into memory, adds new profiles to the node index,
	 * and computes edges between new and existing profiles (both directions)
	 * as well as edges between new profiles themselves.
	 */
	void buildIncrementalGraph(Dataset dataset, GraphMapper mapper,
									String prevStateBase) throws IOException {
		// 1. Load existing profiles into memory (cache)
		List<Profile> existingProfiles = mapper.loadProfiles();
		int sequenceLength = mapper.loadSequenceLength();
		int existingCount = existingProfiles.size();

		// 2. Extract new profiles from dataset
		List<Profile> newProfiles = IntStream.range(0, dataset.size())
				.mapToObj(dataset::profile)
				.collect(Collectors.toList());
		int newCount = newProfiles.size();

		// 3. Add new profiles to the node index
		mapper.addNodeBatch(newProfiles, sequenceLength);

		// 4. Compute edges using 0-based matrix indices (matching the convention
		//    used by buildGraphFromProfiles and MemoryMappedMatrix distance lookups).
		//    New profile j gets matrix index (existingCount + j).

		String edgeFile = prevStateBase + "_edges.dat";

		// 5. For each batch of new destination profiles:
		for (int batchStart = 0; batchStart < newCount; batchStart += BATCH_SIZE) {
			int batchEnd = Math.min(batchStart + BATCH_SIZE, newCount);
			for (int j = batchStart; j < batchEnd; j++) {
				int destIdx = existingCount + j;
				List<Edge> edges = new ArrayList<>();

				// 5a. Edges from existing profiles to new profile j
				for (int i = 0; i < existingCount; i++) {
					if (i == destIdx) continue;
					double dist = grapeTreeDistance(existingProfiles.get(i), newProfiles.get(j));
					if (Double.isFinite(dist) && dist > 0) {
						edges.add(new Edge(i, destIdx, dist));
					}
				}

				// 5b. Edges from other new profiles to new profile j
				for (int k = 0; k < newCount; k++) {
					if (k == j) continue;
					int srcIdx = existingCount + k;
					if (srcIdx == destIdx) continue;
					double dist = grapeTreeDistance(newProfiles.get(k), newProfiles.get(j));
					if (Double.isFinite(dist) && dist > 0) {
						edges.add(new Edge(srcIdx, destIdx, dist));
					}
				}

				// 5c. Write edges for new profile j
				String nodeEdgeFile = prevStateBase + "_edges_node" + destIdx + ".dat";
				if (!edges.isEmpty()) {
					if (Files.exists(Path.of(nodeEdgeFile))) {
						EdgeListMapper.addEdges(edges, destIdx, edgeFile);
					} else {
						EdgeListMapper.writeEdgeArray(nodeEdgeFile, edges);
					}
				} else {
					EdgeListMapper.writeEdgeArray(nodeEdgeFile, List.of());
				}
			}
		}

		// 6. For each existing destination profile j:
		//    Append edges from new profiles to existing profile j
		for (int batchStart = 0; batchStart < existingCount; batchStart += BATCH_SIZE) {
			int batchEnd = Math.min(batchStart + BATCH_SIZE, existingCount);
			for (int j = batchStart; j < batchEnd; j++) {
				List<Edge> edges = new ArrayList<>();
				for (int k = 0; k < newCount; k++) {
					int srcIdx = existingCount + k;
					if (srcIdx == j) continue;
					double dist = grapeTreeDistance(newProfiles.get(k), existingProfiles.get(j));
					if (Double.isFinite(dist) && dist > 0) {
						edges.add(new Edge(srcIdx, j, dist));
					}
				}
				if (!edges.isEmpty()) {
					String nodeEdgeFile = prevStateBase + "_edges_node" + j + ".dat";
					if (Files.exists(Path.of(nodeEdgeFile))) {
						EdgeListMapper.addEdges(edges, j, edgeFile);
					} else {
						EdgeListMapper.writeEdgeArray(nodeEdgeFile, edges);
					}
				}
			}
		}
	}

	@Override
	protected Tree processImpl(Matrix matrix) {
		if (this.matrix == null)
			throw new IllegalStateException(
					"Edmonds requires a MemoryMappedMatrix. Use --input to provide profiles.");

		initInternal(this.matrix);
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
		return forest.expansion(this.matrix);
	}

	void initInternal(Matrix matrix) {
		int size = matrix.size();
		this.matrix = (MemoryMappedMatrix) matrix;
		this.baseFileName = this.matrix.getBaseFileName();
		initComparator();
		this.stronglyConnected = new WeightedDisjointSet(size);
		this.weaklyConnected = new DisjointSet(size);
		this.inEdgeNode = new EdgeNode[size];
		this.forest = new Forest(size);
		this.roots = new LinkedList<>();
		this.edgeNodeCycle = new ArrayList<>(size);
		this.sccComposition = new HashMap<>();
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
			throw new RuntimeException("IOException when finding min safe edge from memory mapped files", ioe);
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
