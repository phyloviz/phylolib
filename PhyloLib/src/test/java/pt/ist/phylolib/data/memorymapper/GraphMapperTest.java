package pt.ist.phylolib.data.memorymapper;

import pt.ist.phylolib.command.algorithm.edmonds.WeightedDisjointSet;
import pt.ist.phylolib.data.dataset.Profile;
import pt.ist.phylolib.data.tree.Edge;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertTrue;

public class GraphMapperTest {

	private static final List<Profile> PROFILES = List.of(
			new Profile("A", new Integer[] { 1, 2, 3 }),
			new Profile("B", new Integer[] { 4, 5, 6 }),
			new Profile("C", new Integer[] { 7, 8, 9 }),
			new Profile("D", new Integer[] { 10, 11, 12 })
	);
	private static final int SEQ_LEN = 3;

	private Path tempDir;

	@BeforeMethod
	public void setUp() throws IOException {
		tempDir = Files.createTempDirectory("graphmapper-test");
	}

	@AfterMethod
	public void tearDown() throws IOException {
		if (tempDir != null) {
			Files.walk(tempDir)
				.sorted((a, b) -> b.compareTo(a))
				.forEach(path -> {
					try { Files.deleteIfExists(path); } catch (IOException ignored) {}
				});
		}
	}

	private String basePath(String name) {
		return tempDir.resolve(name).toString();
	}

	/**
	 * Writes edges to a per-node file {base}_edges_node{destNode}.dat. Returns the base name.
	 */
	private String writeNodeEdges(String baseName, List<Edge> edges, int destNode) throws IOException {
		String nodeFile = baseName + "_edges_node" + destNode + ".dat";
		EdgeListMapper.writeEdgeArray(nodeFile, edges);
		return baseName;
	}

	/**
	 * Creates empty per-node edge files for the given node IDs. Methods like
	 * getOutgoingEdges/edgeExists/getDistance read every node file, so all files
	 * must exist before those are called.
	 */
	private void createEmptyNodeFiles(String baseName, int... nodeIds) throws IOException {
		for (int id : nodeIds) {
			EdgeListMapper.writeEdgeArray(baseName + "_edges_node" + id + ".dat", List.of());
		}
	}

	// ── Constructors ──────────────────────────────────────────────────

	@Test
	public void constructor_WithProfiles_CountsNodesAndSeqLen() throws IOException {
		GraphMapper mapper = new GraphMapper(basePath("test"), PROFILES, SEQ_LEN);

		assertEquals(4, mapper.getNumNodes());
		assertEquals(SEQ_LEN, mapper.loadSequenceLength());
	}

	// ── saveGraph (full graph with edges) ─────────────────────────────

	@Test
	public void saveGraph_WithEdges_GroupsEdgesByDestinationIntoPerNodeFiles() throws IOException {
		String base = basePath("test");
		GraphMapper mapper = new GraphMapper(base, PROFILES, SEQ_LEN);
		int a = mapper.strIDToIntegerID("A");
		int b = mapper.strIDToIntegerID("B");
		int c = mapper.strIDToIntegerID("C");

		List<Edge> edges = List.of(new Edge(a, b, 1.0), new Edge(a, c, 2.0), new Edge(b, c, 3.0));
		mapper.saveGraph(PROFILES, edges, SEQ_LEN, base);

		List<Edge> incomingB = mapper.getIncomingEdges(b);
		assertEquals(1, incomingB.size());
		assertEquals(a, incomingB.get(0).from());
		assertEquals(b, incomingB.get(0).to());

		List<Edge> incomingC = mapper.getIncomingEdges(c);
		assertEquals(2, incomingC.size());
	}

	@Test
	public void saveGraph_NodesWithoutEdges_ProducesEmptyEdgeFiles() throws IOException {
		String base = basePath("test");
		GraphMapper mapper = new GraphMapper(base, PROFILES, SEQ_LEN);
		int a = mapper.strIDToIntegerID("A");

		mapper.saveGraph(PROFILES, List.of(), SEQ_LEN, base);

		assertTrue(mapper.loadIncidentEdges(a).isEmpty());
	}

	// ── getIncomingEdges / getIncomingEdgesUpToId ─────────────────────

	@Test
	public void getIncomingEdges_RoundTrip_PreservesEdges() throws IOException {
		String base = basePath("test");
		GraphMapper mapper = new GraphMapper(base, PROFILES, SEQ_LEN);
		int a = mapper.strIDToIntegerID("A");
		int b = mapper.strIDToIntegerID("B");

		writeNodeEdges(base, List.of(new Edge(a, b, 1.5), new Edge(0, b, 2.5)), b);

		List<Edge> loaded = mapper.getIncomingEdges(b);
		assertEquals(2, loaded.size());
		assertEquals(b, loaded.get(0).to());
		assertEquals(1.5, loaded.get(0).distance(), 0.0);
		assertEquals(2.5, loaded.get(1).distance(), 0.0);
	}

	@Test
	public void getIncomingEdgesUpToId_ReturnsAllWhenNodeIdWithinRange() throws IOException {
		String base = basePath("test");
		GraphMapper mapper = new GraphMapper(base, PROFILES, SEQ_LEN);
		int b = mapper.strIDToIntegerID("B");

		writeNodeEdges(base, List.of(new Edge(0, b, 1.0), new Edge(1, b, 2.0)), b);

		List<Edge> all = mapper.getIncomingEdgesUpToId(b, b);
		assertEquals(2, all.size());
	}

	@Test
	public void getIncomingEdgesUpToId_ReturnsEmptyWhenNodeIdOutOfRange() throws IOException {
		String base = basePath("test");
		GraphMapper mapper = new GraphMapper(base, PROFILES, SEQ_LEN);
		int b = mapper.strIDToIntegerID("B");

		writeNodeEdges(base, List.of(new Edge(0, b, 1.0), new Edge(1, b, 2.0)), b);

		List<Edge> none = mapper.getIncomingEdgesUpToId(b, b - 1);
		assertTrue(none.isEmpty());
	}

	// ── addNode ───────────────────────────────────────────────────────

	@Test
	public void addNode_WithIncomingAndOutgoing_PersistsBoth() throws IOException {
		String base = basePath("test");
		GraphMapper mapper = new GraphMapper(base, PROFILES, SEQ_LEN);
		int a = mapper.strIDToIntegerID("A");
		int b = mapper.strIDToIntegerID("B");
		int c = mapper.strIDToIntegerID("C");
		int d = mapper.strIDToIntegerID("D");
		Profile e = new Profile("E", new Integer[] { 13, 14, 15 });
		// A-D are assigned ids 1-4, so E gets the next incremental id (5).
		int eId = 5;

		mapper.addNode(e, List.of(new Edge(a, eId, 1.0)), List.of(new Edge(eId, b, 2.0)), SEQ_LEN);
		// getOutgoingEdges reads every node's file, so create empty files for A, C, D.
		createEmptyNodeFiles(base, a, c, d);

		assertEquals(eId, mapper.strIDToIntegerID("E"));
		List<Edge> incomingE = mapper.getIncomingEdges(eId);
		assertEquals(1, incomingE.size());
		assertEquals(a, incomingE.get(0).from());
		assertEquals(eId, incomingE.get(0).to());

		List<Edge> outgoingE = mapper.getOutgoingEdges(eId);
		assertEquals(1, outgoingE.size());
		assertEquals(eId, outgoingE.get(0).from());
		assertEquals(b, outgoingE.get(0).to());
	}

	@Test
	public void addNode_Overload_IncomingOnly_NoOutgoing() throws IOException {
		String base = basePath("test");
		GraphMapper mapper = new GraphMapper(base, PROFILES, SEQ_LEN);
		int a = mapper.strIDToIntegerID("A");
		Profile e = new Profile("E", new Integer[] { 13, 14, 15 });
		int eId = 5;

		mapper.addNode(e, List.of(new Edge(a, eId, 1.0)), SEQ_LEN);
		// getOutgoingEdges reads every node's file, so create empty files for A, B, C, D.
		createEmptyNodeFiles(base, a, mapper.strIDToIntegerID("B"),
				mapper.strIDToIntegerID("C"), mapper.strIDToIntegerID("D"));

		assertEquals(1, mapper.getIncomingEdges(eId).size());
		assertTrue(mapper.getOutgoingEdges(eId).isEmpty());
	}

	// ── addNodesBatch ─────────────────────────────────────────────────

	@Test
	public void addNodesBatch_NewNodesWithEdges_Persisted() throws IOException {
		String base = basePath("test");
		GraphMapper mapper = new GraphMapper(base, PROFILES, SEQ_LEN);
		int a = mapper.strIDToIntegerID("A");
		int b = mapper.strIDToIntegerID("B");
		Profile e = new Profile("E", new Integer[] { 13, 14, 15 });
		Profile f = new Profile("F", new Integer[] { 16, 17, 18 });
		int eId = 5;
		int fId = 6;

		Map<Profile, List<Edge>> nodeEdges = new java.util.HashMap<>();
		nodeEdges.put(e, List.of(new Edge(a, eId, 1.0)));
		nodeEdges.put(f, List.of(new Edge(b, fId, 2.0)));

		mapper.addNodesBatch(List.of(e, f), nodeEdges, Map.of(), SEQ_LEN);

		assertEquals(6, mapper.getNumNodes());
		List<Edge> incomingE = mapper.getIncomingEdges(eId);
		assertEquals(1, incomingE.size());
		assertEquals(a, incomingE.get(0).from());
		List<Edge> incomingF = mapper.getIncomingEdges(fId);
		assertEquals(1, incomingF.size());
		assertEquals(b, incomingF.get(0).from());
	}

	@Test
	public void addNodesBatch_EdgesToExistingNodes_Added() throws IOException {
		String base = basePath("test");
		GraphMapper mapper = new GraphMapper(base, PROFILES, SEQ_LEN);
		int b = mapper.strIDToIntegerID("B");
		Profile e = new Profile("E", new Integer[] { 13, 14, 15 });
		Profile f = new Profile("F", new Integer[] { 16, 17, 18 });
		int eId = 5;
		int fId = 6;

		Map<Profile, List<Edge>> existingNodeNewEdges = new java.util.HashMap<>();
		existingNodeNewEdges.put(PROFILES.get(1), List.of(new Edge(eId, b, 1.0), new Edge(fId, b, 2.0)));

		mapper.addNodesBatch(List.of(e, f), Map.of(), existingNodeNewEdges, SEQ_LEN);

		List<Edge> incomingB = mapper.getIncomingEdges(b);
		boolean fromE = false, fromF = false;
		for (Edge edge : incomingB) {
			if (edge.from() == eId) fromE = true;
			if (edge.from() == fId) fromF = true;
		}
		assertTrue("Edge from E to B should be present", fromE);
		assertTrue("Edge from F to B should be present", fromF);
	}

	// ── removeNode / removeNodesBatch ─────────────────────────────────

	@Test
	public void removeNode_RemovesNodeAndIncidentEdges() throws IOException {
		String base = basePath("test");
		GraphMapper mapper = new GraphMapper(base, PROFILES, SEQ_LEN);
		int a = mapper.strIDToIntegerID("A");
		int b = mapper.strIDToIntegerID("B");
		Profile e = new Profile("E", new Integer[] { 13, 14, 15 });
		int eId = 5;
		mapper.addNode(e, List.of(new Edge(a, eId, 1.0)), List.of(new Edge(eId, b, 2.0)), SEQ_LEN);
		assertEquals(5, mapper.getNumNodes());

		mapper.removeNode(e);

		assertEquals(4, mapper.getNumNodes());
		List<Profile> loaded = mapper.loadProfiles();
		for (Profile p : loaded) {
			assertFalse("Profile E should be removed", "E".equals(p.id()));
		}
		// Incoming edge to E is gone (E's edge file is left empty)
		assertFalse(mapper.edgeExists(a, eId));
		// Outgoing edge E->B removed from B's file
		assertFalse(mapper.edgeExists(eId, b));
	}

	@Test
	public void removeNodesBatch_RemovesMultipleNodesAndIncidentEdges() throws IOException {
		String base = basePath("test");
		GraphMapper mapper = new GraphMapper(base, PROFILES, SEQ_LEN);
		int a = mapper.strIDToIntegerID("A");
		int b = mapper.strIDToIntegerID("B");
		Profile e = new Profile("E", new Integer[] { 13, 14, 15 });
		Profile f = new Profile("F", new Integer[] { 16, 17, 18 });
		int eId = 5;
		int fId = 6;
		mapper.addNode(e, List.of(new Edge(a, eId, 1.0)), List.of(new Edge(eId, b, 2.0)), SEQ_LEN);
		mapper.addNode(f, List.of(new Edge(a, fId, 3.0)), List.of(new Edge(fId, b, 4.0)), SEQ_LEN);
		assertEquals(6, mapper.getNumNodes());

		mapper.removeNodesBatch(List.of(e, f), SEQ_LEN);

		assertEquals(4, mapper.getNumNodes());
		// Incoming edges to E and F are gone
		assertFalse(mapper.edgeExists(a, eId));
		assertFalse(mapper.edgeExists(a, fId));
		// Outgoing edges from E and F are gone
		assertFalse(mapper.edgeExists(eId, b));
		assertFalse(mapper.edgeExists(fId, b));
	}

	// ── Edge CRUD through GraphMapper API ─────────────────────────────

	@Test
	public void edgeExists_ThroughGraphMapper_TrueForExistingFalseForMissing() throws IOException {
		String base = basePath("test");
		GraphMapper mapper = new GraphMapper(base, PROFILES, SEQ_LEN);
		int a = mapper.strIDToIntegerID("A");
		int b = mapper.strIDToIntegerID("B");
		int c = mapper.strIDToIntegerID("C");

		writeNodeEdges(base, List.of(new Edge(a, b, 1.0)), b);
		createEmptyNodeFiles(base, c);

		assertTrue(mapper.edgeExists(a, b));
		assertFalse(mapper.edgeExists(a, c));
	}

	@Test
	public void addEdge_ThenLoadIncidentAndEdgeExists() throws IOException {
		String base = basePath("test");
		GraphMapper mapper = new GraphMapper(base, PROFILES, SEQ_LEN);
		int a = mapper.strIDToIntegerID("A");
		int b = mapper.strIDToIntegerID("B");

		mapper.addEdge(new Edge(a, b, 3.14));

		assertTrue(mapper.edgeExists(a, b));
		List<Edge> incident = mapper.loadIncidentEdges(b);
		assertEquals(1, incident.size());
		assertEquals(3.14, incident.get(0).distance(), 0.0);
	}

	@Test
	public void removeEdge_RemovesEdge() throws IOException {
		String base = basePath("test");
		GraphMapper mapper = new GraphMapper(base, PROFILES, SEQ_LEN);
		int a = mapper.strIDToIntegerID("A");
		int b = mapper.strIDToIntegerID("B");
		int c = mapper.strIDToIntegerID("C");

		writeNodeEdges(base, List.of(new Edge(a, b, 1.0), new Edge(c, b, 2.0)), b);

		mapper.removeEdge(a, b);

		assertFalse(mapper.edgeExists(a, b));
		assertTrue(mapper.edgeExists(c, b));
	}

	// ── getOutgoingEdges / getOutgoingEdgesUpToId ─────────────────────

	@Test
	public void getOutgoingEdges_ReturnsEdgesFromSource() throws IOException {
		String base = basePath("test");
		GraphMapper mapper = new GraphMapper(base, PROFILES, SEQ_LEN);
		int a = mapper.strIDToIntegerID("A");
		int b = mapper.strIDToIntegerID("B");
		int c = mapper.strIDToIntegerID("C");
		int d = mapper.strIDToIntegerID("D");

		writeNodeEdges(base, List.of(new Edge(a, b, 1.0)), b);
		createEmptyNodeFiles(base, c, d);

		List<Edge> outgoing = mapper.getOutgoingEdges(a);
		assertEquals(1, outgoing.size());
		assertEquals(a, outgoing.get(0).from());
		assertEquals(b, outgoing.get(0).to());
	}

	@Test
	public void getOutgoingEdgesUpToId_FiltersByMaxDestId() throws IOException {
		String base = basePath("test");
		GraphMapper mapper = new GraphMapper(base, PROFILES, SEQ_LEN);
		int a = mapper.strIDToIntegerID("A");
		int b = mapper.strIDToIntegerID("B");
		int c = mapper.strIDToIntegerID("C");

		writeNodeEdges(base, List.of(new Edge(a, b, 1.0)), b);
		writeNodeEdges(base, List.of(new Edge(a, c, 2.0)), c);

		List<Edge> filtered = mapper.getOutgoingEdgesUpToId(a, b);

		assertEquals(1, filtered.size());
		assertEquals(b, filtered.get(0).to());
	}

	@Test
	public void removeOutgoingEdges_RemovesAllFromSource() throws IOException {
		String base = basePath("test");
		GraphMapper mapper = new GraphMapper(base, PROFILES, SEQ_LEN);
		int a = mapper.strIDToIntegerID("A");
		int b = mapper.strIDToIntegerID("B");
		int c = mapper.strIDToIntegerID("C");

		writeNodeEdges(base, List.of(new Edge(a, b, 1.0)), b);
		writeNodeEdges(base, List.of(new Edge(a, c, 2.0)), c);
		assertTrue(mapper.edgeExists(a, b));
		assertTrue(mapper.edgeExists(a, c));

		mapper.removeOutgoingEdges(a);

		assertFalse(mapper.edgeExists(a, b));
		assertFalse(mapper.edgeExists(a, c));
	}

	// ── saveArborescence ──────────────────────────────────────────────

	@Test
	public void saveArborescence_WritesPhylogenyFile() throws IOException {
		String base = basePath("test");
		GraphMapper mapper = new GraphMapper(base, PROFILES, SEQ_LEN);
		List<Edge> phylogeny = List.of(new Edge(1, 2, 1.0), new Edge(2, 3, 0.5));

		mapper.saveArborescence(phylogeny);

		List<Edge> loaded = EdgeListMapper.loadEdgeArray(base + "_phylogeny_edges.dat");
		assertEquals(2, loaded.size());
		assertEquals(phylogeny.get(0), loaded.get(0));
		assertEquals(phylogeny.get(1), loaded.get(1));
	}

	// ── findMinSafeEdgeIncomingToSCC ──────────────────────────────────

	@Test
	public void findMinSafeEdgeIncomingToSCC_ReturnsMinCrossComponentEdge() throws IOException {
		String base = basePath("test");
		GraphMapper mapper = new GraphMapper(base, PROFILES, SEQ_LEN);
		int a = mapper.strIDToIntegerID("A");
		int b = mapper.strIDToIntegerID("B");
		int c = mapper.strIDToIntegerID("C");
		int d = mapper.strIDToIntegerID("D");

		// A and B belong to the SCC; C and D are outside.
		writeNodeEdges(base, List.of(new Edge(c, a, 0.5), new Edge(d, a, 2.0)), a);
		writeNodeEdges(base, List.of(new Edge(a, b, 1.0), new Edge(d, b, 0.3)), b);

		WeightedDisjointSet uf = new WeightedDisjointSet(5);
		uf.unionSet(a, b);

		Comparator<Edge> cmp = Comparator.comparingDouble(Edge::distance);
		Edge result = mapper.findMinSafeEdgeIncomingToSCC(base, uf, Set.of(a, b), cmp);

		assertNotNull(result);
		assertEquals(d, result.from());
		assertEquals(b, result.to());
		assertEquals(0.3, result.distance(), 0.0);
	}

	// ── getDistance (static) ──────────────────────────────────────────

	@Test
	public void getDistance_Asymmetric_ReturnsDirectDistance() throws IOException {
		String base = basePath("test");
		GraphMapper mapper = new GraphMapper(base, PROFILES, SEQ_LEN);
		int a = mapper.strIDToIntegerID("A");
		int b = mapper.strIDToIntegerID("B");
		writeNodeEdges(base, List.of(new Edge(a, b, 1.0)), b);

		double dist = GraphMapper.getDistance(base + "_edges.dat", a, b, false);

		assertEquals(1.0, dist, 0.0);
	}

	@Test
	public void getDistance_SymmetricMissingDirect_FallsBackToReverse() throws IOException {
		String base = basePath("test");
		GraphMapper mapper = new GraphMapper(base, PROFILES, SEQ_LEN);
		int a = mapper.strIDToIntegerID("A");
		int b = mapper.strIDToIntegerID("B");
		// Only the reverse edge B->A exists.
		writeNodeEdges(base, List.of(new Edge(b, a, 2.5)), a);
		// Node B's file must exist for the asymmetric lookup to return NaN.
		createEmptyNodeFiles(base, b);

		// Asymmetric: missing direct edge returns NaN.
		double asymmetric = GraphMapper.getDistance(base + "_edges.dat", a, b, false);
		assertTrue(Double.isNaN(asymmetric));

		// Symmetric: falls back to the reverse direction.
		double symmetric = GraphMapper.getDistance(base + "_edges.dat", a, b, true);
		assertEquals(2.5, symmetric, 0.0);
	}

}
