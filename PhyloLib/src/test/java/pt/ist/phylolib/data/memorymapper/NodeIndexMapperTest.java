package pt.ist.phylolib.data.memorymapper;

import pt.ist.phylolib.data.dataset.Profile;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertTrue;

public class NodeIndexMapperTest {

	private static final List<Profile> PROFILES = List.of(
			new Profile("A", new Integer[] { 1, 2, 3 }),
			new Profile("B", new Integer[] { 4, 5, 6 }),
			new Profile("C", new Integer[] { 7, null, 9 }),
			new Profile("D", new Integer[] { 10, 11, 12 })
	);
	private static final int SEQ_LEN = 3;

	private Path tempDir;

	@BeforeMethod
	public void setUp() throws IOException {
		tempDir = Files.createTempDirectory("nodeindex-test");
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

	// ── Constructors ──────────────────────────────────────────────────

	@Test
	public void constructor_WithProfiles_CreatesFilesAndCountsNodes() throws IOException {
		NodeIndexMapper mapper = new NodeIndexMapper(basePath("test.dat"), PROFILES, SEQ_LEN);

		assertEquals(4, mapper.getNumNodes());
		assertEquals(SEQ_LEN, mapper.getSequenceLength());
	}

	@Test
	public void constructor_ExistingFile_RestoresState() throws IOException {
		new NodeIndexMapper(basePath("test.dat"), PROFILES, SEQ_LEN);

		NodeIndexMapper loaded = new NodeIndexMapper(basePath("test.dat"));

		assertEquals(4, loaded.getNumNodes());
		assertEquals(SEQ_LEN, loaded.getSequenceLength());
	}

	// ── saveGraph ─────────────────────────────────────────────────────

	@Test
	public void saveGraph_OverwritesFile_UpdatesHeader() throws IOException {
		NodeIndexMapper mapper = new NodeIndexMapper(basePath("test.dat"), PROFILES.subList(0, 2), SEQ_LEN);
		assertEquals(2, mapper.getNumNodes());

		mapper.saveGraph(PROFILES, SEQ_LEN);

		assertEquals(4, mapper.getNumNodes());
		List<Profile> loaded = mapper.loadProfiles();
		assertEquals(4, loaded.size());
	}

	// ── loadNodeIDs ───────────────────────────────────────────────────

	@Test
	public void loadNodeIDs_ReturnsAllIds() throws IOException {
		NodeIndexMapper mapper = new NodeIndexMapper(basePath("test.dat"), PROFILES, SEQ_LEN);

		int[] ids = mapper.loadNodeIDs();

		assertEquals(4, ids.length);
		for (int id : ids) {
			assertTrue(id > 0);
		}
	}

	// ── loadProfiles ──────────────────────────────────────────────────

	@Test
	public void loadProfiles_RoundTrip_PreservesAllData() throws IOException {
		NodeIndexMapper mapper = new NodeIndexMapper(basePath("test.dat"), PROFILES, SEQ_LEN);

		List<Profile> loaded = mapper.loadProfiles();

		assertEquals(4, loaded.size());
		for (int i = 0; i < PROFILES.size(); i++) {
			Profile original = PROFILES.get(i);
			Profile actual = loaded.get(i);
			assertEquals(original.id(), actual.id());
			assertEquals(original.size(), actual.size());
			for (int l = 0; l < original.size(); l++) {
				assertEquals(original.locus(l), actual.locus(l));
			}
		}
	}

	// ── addNode ───────────────────────────────────────────────────────

	@Test
	public void addNode_AppendsAndIncrementsCount() throws IOException {
		NodeIndexMapper mapper = new NodeIndexMapper(basePath("test.dat"), PROFILES, SEQ_LEN);
		Profile extra = new Profile("E", new Integer[] { 13, 14, 15 });

		mapper.addNode(extra, SEQ_LEN);

		assertEquals(5, mapper.getNumNodes());
		List<Profile> loaded = mapper.loadProfiles();
		boolean found = false;
		for (Profile p : loaded) {
			if ("E".equals(p.id())) {
				assertEquals(Integer.valueOf(13), p.locus(0));
				assertEquals(Integer.valueOf(14), p.locus(1));
				assertEquals(Integer.valueOf(15), p.locus(2));
				found = true;
			}
		}
		assertTrue("New profile E should be present", found);
	}

	// ── addNodesBatch ─────────────────────────────────────────────────

	@Test
	public void addNodesBatch_AppendsMultipleAndIncrementsCount() throws IOException {
		NodeIndexMapper mapper = new NodeIndexMapper(basePath("test.dat"), PROFILES, SEQ_LEN);
		List<Profile> extra = List.of(
				new Profile("E", new Integer[] { 13, 14, 15 }),
				new Profile("F", new Integer[] { 16, 17, 18 })
		);

		mapper.addNodesBatch(extra, SEQ_LEN);

		assertEquals(6, mapper.getNumNodes());
		List<Profile> loaded = mapper.loadProfiles();
		boolean foundE = false, foundF = false;
		for (Profile p : loaded) {
			if ("E".equals(p.id())) foundE = true;
			if ("F".equals(p.id())) foundF = true;
		}
		assertTrue("New profile E should be present", foundE);
		assertTrue("New profile F should be present", foundF);
	}

	@Test
	public void addNodesBatch_EmptyList_NoChange() throws IOException {
		NodeIndexMapper mapper = new NodeIndexMapper(basePath("test.dat"), PROFILES, SEQ_LEN);

		mapper.addNodesBatch(List.of(), SEQ_LEN);

		assertEquals(4, mapper.getNumNodes());
	}

	// ── removeNode ────────────────────────────────────────────────────

	@Test
	public void removeNode_DecrementsCountAndRemovesProfile() throws IOException {
		NodeIndexMapper mapper = new NodeIndexMapper(basePath("test.dat"), PROFILES, SEQ_LEN);

		mapper.removeNode(PROFILES.get(2)); // remove "C" (middle)

		assertEquals(3, mapper.getNumNodes());
		List<Profile> loaded = mapper.loadProfiles();
		boolean foundC = false;
		for (Profile p : loaded) {
			if ("C".equals(p.id())) foundC = true;
		}
		assertFalse("Profile C should be removed", foundC);
	}

	@Test
	public void removeNode_LastProfile_DecrementsCount() throws IOException {
		NodeIndexMapper mapper = new NodeIndexMapper(basePath("test.dat"), PROFILES, SEQ_LEN);

		mapper.removeNode(PROFILES.get(3)); // remove "D" (last)

		assertEquals(3, mapper.getNumNodes());
		List<Profile> loaded = mapper.loadProfiles();
		boolean foundD = false;
		for (Profile p : loaded) {
			if ("D".equals(p.id())) foundD = true;
		}
		assertFalse("Profile D should be removed", foundD);
	}

	// ── removeNodesBatch ──────────────────────────────────────────────

	@Test
	public void removeNodesBatch_MultipleProfiles_AllRemoved() throws IOException {
		NodeIndexMapper mapper = new NodeIndexMapper(basePath("test.dat"), PROFILES, SEQ_LEN);

		mapper.removeNodesBatch(List.of(PROFILES.get(1), PROFILES.get(3))); // remove "B" and "D"

		assertEquals(2, mapper.getNumNodes());
		List<Profile> loaded = mapper.loadProfiles();
		boolean foundB = false, foundD = false;
		for (Profile p : loaded) {
			if ("B".equals(p.id())) foundB = true;
			if ("D".equals(p.id())) foundD = true;
		}
		assertFalse("Profile B should be removed", foundB);
		assertFalse("Profile D should be removed", foundD);
	}

	@Test
	public void removeNodesBatch_EmptyList_NoChange() throws IOException {
		NodeIndexMapper mapper = new NodeIndexMapper(basePath("test.dat"), PROFILES, SEQ_LEN);

		mapper.removeNodesBatch(List.of());

		assertEquals(4, mapper.getNumNodes());
	}

	// ── buildNodePositionIndex / getNodePositionIndex ─────────────────

	@Test
	public void buildNodePositionIndex_ContainsAllNodes() throws IOException {
		NodeIndexMapper mapper = new NodeIndexMapper(basePath("test.dat"), PROFILES, SEQ_LEN);

		Map<Integer, Long> index = mapper.buildNodePositionIndex();

		assertEquals(4, index.size());
		for (Map.Entry<Integer, Long> entry : index.entrySet()) {
			assertTrue("Offset should be beyond header", entry.getValue() >= 8);
		}
		int[] nodeIds = mapper.loadNodeIDs();
		for (int id : nodeIds) {
			assertTrue("Index should contain node " + id, index.containsKey(id));
		}
	}

	// ── getSequenceLength ─────────────────────────────────────────────

	@Test
	public void getSequenceLength_ReturnsCorrectValue() throws IOException {
		NodeIndexMapper mapper3 = new NodeIndexMapper(basePath("len3.dat"), PROFILES, 3);
		assertEquals(3, mapper3.getSequenceLength());

		List<Profile> fiveLocus = List.of(
				new Profile("X", new Integer[] { 1, 2, 3, 4, 5 })
		);
		NodeIndexMapper mapper5 = new NodeIndexMapper(basePath("len5.dat"), fiveLocus, 5);
		assertEquals(5, mapper5.getSequenceLength());
	}

	// ── Edge case: single profile ─────────────────────────────────────

	@Test
	public void saveGraph_SingleProfile_WorksCorrectly() throws IOException {
		List<Profile> single = List.of(new Profile("S", new Integer[] { 42 }));
		NodeIndexMapper mapper = new NodeIndexMapper(basePath("test.dat"), single, 1);

		assertEquals(1, mapper.getNumNodes());
		int[] ids = mapper.loadNodeIDs();
		assertEquals(1, ids.length);
		List<Profile> loaded = mapper.loadProfiles();
		assertEquals(1, loaded.size());
		assertEquals("S", loaded.get(0).id());
		assertEquals(Integer.valueOf(42), loaded.get(0).locus(0));
	}

}
