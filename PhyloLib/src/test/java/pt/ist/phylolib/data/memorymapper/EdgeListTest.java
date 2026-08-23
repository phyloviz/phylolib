package pt.ist.phylolib.data.memorymapper;

import pt.ist.phylolib.command.algorithm.edmonds.WeightedDisjointSet;
import pt.ist.phylolib.data.tree.Edge;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertTrue;

public class EdgeListTest {

	private static final List<Edge> EDGES = List.of(
			new Edge(0, 1, 0.5),
			new Edge(1, 2, 1.0),
			new Edge(2, 0, 0.75),
			new Edge(0, 2, 0.25)
	);

	private Path tempDir;

	@BeforeMethod
	public void setUp() throws IOException {
		tempDir = Files.createTempDirectory("edgelist-test");
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
	 * Writes edges to a per-node file and returns the base name (without _edges.dat).
	 * Convention: the base name + "_edges.dat" is the "edge file" parameter used by
	 * methods like addEdge/addEdges, which transform it to _edges_node{id}.dat internally.
	 * Here we write directly to _edges_node{dest}.dat for test setup.
	 */
	private String writeNodeEdges(String baseName, List<Edge> edges, int destNode) throws IOException {
		String nodeFile = baseName + "_edges_node" + destNode + ".dat";
		EdgeListMapper.writeEdgeArray(nodeFile, edges);
		return baseName;
	}

	// ── writeEdgeArray + loadEdgeArray (round-trip) ───────────────────

	@Test
	public void writeEdgeArray_MultipleEdges_LoadsCorrectly() throws IOException {
		String file = basePath("test") + "_edges_node1.dat";

		EdgeListMapper.writeEdgeArray(file, EDGES);

		List<Edge> loaded = EdgeListMapper.loadEdgeArray(file);
		assertEquals(4, loaded.size());
		for (int i = 0; i < EDGES.size(); i++) {
			assertEquals(EDGES.get(i).from(), loaded.get(i).from());
			assertEquals(EDGES.get(i).to(), loaded.get(i).to());
			assertEquals(EDGES.get(i).distance(), loaded.get(i).distance(), 0.0);
		}
	}

	@Test
	public void writeEdgeArray_EmptyList_LoadsEmpty() throws IOException {
		String file = basePath("test") + "_edges_node1.dat";

		EdgeListMapper.writeEdgeArray(file, List.of());

		assertEquals(0, EdgeListMapper.getNumEdges(file));
		assertTrue(EdgeListMapper.loadEdgeArray(file).isEmpty());
	}

	@Test
	public void writeEdgeArray_OverwritesPreviousData() throws IOException {
		String file = basePath("test") + "_edges_node1.dat";
		EdgeListMapper.writeEdgeArray(file, EDGES);
		assertEquals(4, EdgeListMapper.getNumEdges(file));

		List<Edge> replacement = List.of(new Edge(0, 1, 9.9), new Edge(1, 0, 8.8));
		EdgeListMapper.writeEdgeArray(file, replacement);

		List<Edge> loaded = EdgeListMapper.loadEdgeArray(file);
		assertEquals(2, loaded.size());
		assertEquals(9.9, loaded.get(0).distance(), 0.0);
		assertEquals(8.8, loaded.get(1).distance(), 0.0);
	}

	// ── getNumEdges ───────────────────────────────────────────────────

	@Test
	public void getNumEdges_AfterWrite_ReturnsCorrectCount() throws IOException {
		String file = basePath("test") + "_edges_node1.dat";
		EdgeListMapper.writeEdgeArray(file, EDGES);

		assertEquals(4, EdgeListMapper.getNumEdges(file));
	}

	@Test
	public void getNumEdges_EmptyFile_ReturnsZero() throws IOException {
		String file = basePath("test") + "_edges_node1.dat";
		EdgeListMapper.writeEdgeArray(file, List.of());

		assertEquals(0, EdgeListMapper.getNumEdges(file));
	}

	// ── addEdge ───────────────────────────────────────────────────────

	@Test
	public void addEdge_SingleEdge_AppendedCorrectly() throws IOException {
		String base = basePath("test");
		String file = base + "_edges.dat";
		String nodeFile = base + "_edges_node1.dat";
		EdgeListMapper.writeEdgeArray(nodeFile, List.of());

		Edge edge = new Edge(0, 1, 3.14);
		EdgeListMapper.addEdge(edge, file);

		assertEquals(1, EdgeListMapper.getNumEdges(nodeFile));
		List<Edge> loaded = EdgeListMapper.loadEdgeArray(nodeFile);
		assertEquals(1, loaded.size());
		assertEquals(0, loaded.get(0).from());
		assertEquals(1, loaded.get(0).to());
		assertEquals(3.14, loaded.get(0).distance(), 0.0);
	}

	@Test
	public void addEdge_MultipleCalls_AppendsAll() throws IOException {
		String base = basePath("test");
		String file = base + "_edges.dat";
		String nodeFile = base + "_edges_node2.dat";
		EdgeListMapper.writeEdgeArray(nodeFile, List.of());

		EdgeListMapper.addEdge(new Edge(0, 2, 1.0), file);
		EdgeListMapper.addEdge(new Edge(1, 2, 2.0), file);
		EdgeListMapper.addEdge(new Edge(3, 2, 3.0), file);

		assertEquals(3, EdgeListMapper.getNumEdges(nodeFile));
		List<Edge> loaded = EdgeListMapper.loadEdgeArray(nodeFile);
		assertEquals(1.0, loaded.get(0).distance(), 0.0);
		assertEquals(2.0, loaded.get(1).distance(), 0.0);
		assertEquals(3.0, loaded.get(2).distance(), 0.0);
	}

	// ── addEdges ──────────────────────────────────────────────────────

	@Test
	public void addEdges_MultipleEdges_AppendedCorrectly() throws IOException {
		String base = basePath("test");
		String file = base + "_edges.dat";
		String nodeFile = base + "_edges_node1.dat";
		EdgeListMapper.writeEdgeArray(nodeFile, List.of());

		List<Edge> batch = List.of(new Edge(0, 1, 1.0), new Edge(2, 1, 2.0), new Edge(3, 1, 3.0));
		EdgeListMapper.addEdges(batch, 1, file);

		assertEquals(3, EdgeListMapper.getNumEdges(nodeFile));
	}

	@Test
	public void addEdges_EmptyList_NoChange() throws IOException {
		String base = basePath("test");
		String file = base + "_edges.dat";
		String nodeFile = base + "_edges_node1.dat";
		EdgeListMapper.writeEdgeArray(nodeFile, List.of(new Edge(0, 1, 1.0), new Edge(2, 1, 2.0)));

		EdgeListMapper.addEdges(List.of(), 1, file);

		assertEquals(2, EdgeListMapper.getNumEdges(nodeFile));
	}

	@Test
	public void addEdges_NullList_NoChange() throws IOException {
		String base = basePath("test");
		String file = base + "_edges.dat";
		String nodeFile = base + "_edges_node1.dat";
		EdgeListMapper.writeEdgeArray(nodeFile, List.of(new Edge(0, 1, 1.0)));

		EdgeListMapper.addEdges(null, 1, file);

		assertEquals(1, EdgeListMapper.getNumEdges(nodeFile));
	}

	// ── addEdgesBatch ─────────────────────────────────────────────────

	@Test
	public void addEdgesBatch_MultipleNodes_CreatesFilesForEachNode() throws IOException {
		String base = basePath("test");
		String file = base + "_edges.dat";
		// Create empty per-node files
		EdgeListMapper.writeEdgeArray(base + "_edges_node1.dat", List.of());
		EdgeListMapper.writeEdgeArray(base + "_edges_node2.dat", List.of());

		Map<Integer, List<Edge>> batch = new HashMap<>();
		batch.put(1, List.of(new Edge(0, 1, 1.0), new Edge(2, 1, 2.0)));
		batch.put(2, List.of(new Edge(0, 2, 3.0), new Edge(1, 2, 4.0)));

		EdgeListMapper.addEdgesBatch(batch, file);

		assertEquals(2, EdgeListMapper.getNumEdges(base + "_edges_node1.dat"));
		assertEquals(2, EdgeListMapper.getNumEdges(base + "_edges_node2.dat"));
	}

	// ── edgeExists ────────────────────────────────────────────────────

	@Test
	public void edgeExists_ExistingEdge_ReturnsTrue() throws IOException {
		String base = basePath("test");
		EdgeListMapper.writeEdgeArray(base + "_edges_node1.dat", EDGES);

		assertTrue(EdgeListMapper.edgeExists(base + "_edges.dat", 0, 1));
	}

	@Test
	public void edgeExists_NonExistingEdge_ReturnsFalse() throws IOException {
		String base = basePath("test");
		EdgeListMapper.writeEdgeArray(base + "_edges_node1.dat", EDGES);

		assertFalse(EdgeListMapper.edgeExists(base + "_edges.dat", 9, 1));
	}

	// ── removeEdge ────────────────────────────────────────────────────

	@Test
	public void removeEdge_ExistingEdge_RemovedAndCountDecremented() throws IOException {
		String base = basePath("test");
		String file = base + "_edges.dat";
		String nodeFile = base + "_edges_node1.dat";
		// Write edges destined to node 1: (0→1, 2→1)
		EdgeListMapper.writeEdgeArray(nodeFile, List.of(new Edge(0, 1, 1.0), new Edge(2, 1, 2.0), new Edge(3, 1, 3.0)));
		assertEquals(3, EdgeListMapper.getNumEdges(nodeFile));

		EdgeListMapper.removeEdge(file, 2, 1);

		assertEquals(2, EdgeListMapper.getNumEdges(nodeFile));
		assertFalse(EdgeListMapper.edgeExists(file, 2, 1));
		assertTrue(EdgeListMapper.edgeExists(file, 0, 1));
		assertTrue(EdgeListMapper.edgeExists(file, 3, 1));
	}

	// ── removeEdges / removeEdgesBatch ────────────────────────────────

	@Test
	public void removeEdges_Node_FileDeleted() throws IOException {
		String base = basePath("test");
		String file = base + "_edges.dat";
		String nodeFile = base + "_edges_node1.dat";
		EdgeListMapper.writeEdgeArray(nodeFile, List.of(new Edge(0, 1, 1.0)));
		assertTrue(new File(nodeFile).exists());

		EdgeListMapper.removeEdges(file, 1);

		assertFalse(new File(nodeFile).exists());
	}

	// ── streamEdges ───────────────────────────────────────────────────

	@Test
	public void streamEdges_AllEdges_StreamedToConsumer() throws IOException {
		String base = basePath("test");
		EdgeListMapper.writeEdgeArray(base + "_edges_node1.dat", EDGES);

		List<Edge> streamed = new ArrayList<>();
		EdgeListMapper.streamEdges(base + "_edges_node1.dat", streamed::add);

		assertEquals(4, streamed.size());
		for (int i = 0; i < EDGES.size(); i++) {
			assertEquals(EDGES.get(i).from(), streamed.get(i).from());
			assertEquals(EDGES.get(i).to(), streamed.get(i).to());
			assertEquals(EDGES.get(i).distance(), streamed.get(i).distance(), 0.0);
		}
	}

	// ── loadEdgeArrayUpToId ───────────────────────────────────────────

	@Test
	public void loadEdgeArrayUpToId_ReturnsSubset() throws IOException {
		String base = basePath("test");
		// Edges with dest IDs 1, 2, 3 — stored in a single file sorted by dest
		List<Edge> edges = List.of(
				new Edge(0, 1, 1.0),
				new Edge(0, 2, 2.0),
				new Edge(0, 3, 3.0)
		);
		EdgeListMapper.writeEdgeArray(base + "_edges_node_all.dat", edges);

		List<Edge> result = EdgeListMapper.loadEdgeArrayUpToId(base + "_edges_node_all.dat", 2);

		assertEquals(2, result.size());
		assertEquals(1, result.get(0).to());
		assertEquals(2, result.get(1).to());
	}

	// ── findMinSafeEdgeInFile ─────────────────────────────────────────

	@Test
	public void findMinSafeEdgeInFile_ReturnsMinCrossComponentEdge() throws IOException {
		String base = basePath("test");
		// Edges pointing to node 2: (0→2, 1.0), (1→2, 0.5)
		List<Edge> edges = List.of(new Edge(0, 2, 1.0), new Edge(1, 2, 0.5));
		EdgeListMapper.writeEdgeArray(base + "_edges_node2.dat", edges);

		// Put nodes 0 and 2 in the same component, node 1 in a different one
		WeightedDisjointSet uf = new WeightedDisjointSet(3);
		uf.unionSet(0, 2);
		// Now: sameSet(0,2)=true, sameSet(1,2)=false

		Comparator<Edge> cmp = Comparator.comparingDouble(Edge::distance);
		Edge result = EdgeListMapper.findMinSafeEdgeInFile(base + "_edges.dat", 2, uf, cmp);

		// Edge from 0→2 is within the same component (skipped)
		// Edge from 1→2 crosses components — should be returned
		assertNotNull(result);
		assertEquals(1, result.from());
		assertEquals(2, result.to());
		assertEquals(0.5, result.distance(), 0.0);
	}

}
