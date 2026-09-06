package pt.ist.phylolib.command.algorithm.edmonds;

import pt.ist.phylolib.data.dataset.Dataset;
import pt.ist.phylolib.data.dataset.Profile;
import pt.ist.phylolib.data.matrix.MemoryMappedMatrix;
import pt.ist.phylolib.data.memorymapper.EdgeListMapper;
import pt.ist.phylolib.data.memorymapper.GraphMapper;
import pt.ist.phylolib.data.tree.Edge;
import pt.ist.phylolib.data.tree.Tree;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;

public class EdmondsTest {

	private Path tempDir;

	@BeforeMethod
	public void setUp() throws IOException {
		tempDir = Files.createTempDirectory("edmonds-test");
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

	private MemoryMappedMatrix buildMatrix(String testName, Profile... profiles) throws IOException {
		String baseName = tempDir.resolve(testName).toString();
		int n = profiles.length;
		int sequenceLength = profiles[0].size();
		String[] ids = new String[n];
		for (int i = 0; i < n; i++) ids[i] = String.valueOf(i);

		List<Profile> profileList = List.of(profiles);
		GraphMapper mapper = new GraphMapper(baseName, profileList, sequenceLength);

		String edgeFile = baseName + "_edges.dat";
		for (int j = 0; j < n; j++) {
			List<Edge> edges = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				if (i == j) continue;
				double dist = grapeTreeDistance(profiles[i], profiles[j]);
				if (Double.isFinite(dist) && dist > 0) {
					edges.add(new Edge(i, j, dist));
				}
			}
			if (!edges.isEmpty()) {
				EdgeListMapper.addEdges(edges, j, edgeFile);
			} else {
				EdgeListMapper.writeEdgeArray(baseName + "_edges_node" + j + ".dat", List.of());
			}
		}

		return new MemoryMappedMatrix(false, ids, baseName, new GraphMapper(baseName));
	}

	@DataProvider
	public Object[][] data() {
		return new Object[][] {
				// 2-node test: profiles [1] and [2]
				// d(0->1) = 1/1 = 1.0, d(1->0) = 1/1 = 1.0
				// MST: single edge connecting both nodes
				{ "twoNode",
				  new Profile[] {
					  new Profile("0", new Integer[] { 1 }),
					  new Profile("1", new Integer[] { 2 })
				  },
				  1 },
				// 3-node test: profiles [1,1], [2,1], [2,2]
				// d(0->1) = 1/2 = 0.5, d(0->2) = 1/2 = 0.5
				// d(1->0) = 1/2 = 0.5, d(1->2) = 1/2 = 0.5
				// d(2->0) = 2/2 = 1.0, d(2->1) = 1/2 = 0.5
				// MST: 2 edges connecting all 3 nodes
				{ "threeNode",
				  new Profile[] {
					  new Profile("0", new Integer[] { 1, 1 }),
					  new Profile("1", new Integer[] { 2, 1 }),
					  new Profile("2", new Integer[] { 2, 2 })
				  },
				  2 },
				// 4-node test
				{ "fourNode",
				  new Profile[] {
					  new Profile("0", new Integer[] { 1, 1, 1 }),
					  new Profile("1", new Integer[] { 2, 1, 1 }),
					  new Profile("2", new Integer[] { 2, 2, 1 }),
					  new Profile("3", new Integer[] { 2, 2, 2 })
				  },
				  3 }
		};
	}

	@Test(dataProvider = "data")
	public void process_Valid_Success(String testName, Profile[] profiles, int expectedEdgeCount) throws IOException {
		MemoryMappedMatrix matrix = buildMatrix(testName, profiles);

		Edmonds edmonds = new Edmonds();
		edmonds.initInternal(matrix);
		Tree tree = edmonds.process(matrix);

		assertEquals(tree.edges().count(), (long) expectedEdgeCount);
		// Verify the tree connects all nodes (n-1 edges for n nodes)
		assertTrue(expectedEdgeCount == profiles.length - 1);
	}

	@Test
	public void process_IncrementalGraph_Success() throws IOException {
		// 1. Build initial graph with profiles [1,1] and [2,1]
		//    d(0->1) = 1/2 = 0.5, d(1->0) = 1/2 = 0.5
		String baseName = tempDir.resolve("incremental").toString();
		Profile p0 = new Profile("0", new Integer[] { 1, 1 });
		Profile p1 = new Profile("1", new Integer[] { 2, 1 });

		List<Profile> initial = List.of(p0, p1);
		GraphMapper mapper = new GraphMapper(baseName, initial, 2);

		String edgeFile = baseName + "_edges.dat";
		for (int j = 0; j < 2; j++) {
			List<Edge> edges = new ArrayList<>();
			for (int i = 0; i < 2; i++) {
				if (i == j) continue;
				double dist = grapeTreeDistance(initial.get(i), initial.get(j));
				if (Double.isFinite(dist) && dist > 0) {
					edges.add(new Edge(i, j, dist));
				}
			}
			EdgeListMapper.addEdges(edges, j, edgeFile);
		}

		// 2. Add new profile [2,2] to existing graph
		//    d(2->0) = 2/2 = 1.0, d(2->1) = 1/2 = 0.5
		//    d(0->2) = 1/2 = 0.5, d(1->2) = 1/2 = 0.5
		Profile p2 = new Profile("2", new Integer[] { 2, 2 });
		Dataset newProfiles = new Dataset(List.of(p2));

		GraphMapper existingMapper = new GraphMapper(baseName);
		Edmonds edmonds = new Edmonds();
		edmonds.buildIncrementalGraph(newProfiles, existingMapper, baseName);

		// 3. Run algorithm with combined 3-node graph
		String[] ids = { "0", "1", "2" };
		MemoryMappedMatrix matrix = new MemoryMappedMatrix(false, ids, baseName, new GraphMapper(baseName));

		edmonds.initInternal(matrix);
		Tree tree = edmonds.process(matrix);

		// MST for 3 nodes: 2 edges
		assertEquals(tree.edges().count(), 2L);
	}

}
