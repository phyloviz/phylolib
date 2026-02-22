package pt.ist.phylolib.data.tree;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import pt.ist.phylolib.cli.Options;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class NexusTest {

	@DataProvider
	public Object[][] invalid() {
		return new Object[][] {
				{ Stream.empty() },
				{ Stream.of("") },
				{ Stream.of(" ") },
				{ Stream.of("BEGIN TREES;", "\tTree result = ;", "END;") },
				{ Stream.of("BEGIN TREES;", "\tTree result = 1;", "END;") },
				{ Stream.of("BEGIN TREES;", "\tTree result = ((A:2.3,B:1:3.1,C:0.2)_;", "END;") },
				{ Stream.of("BEGIN TREES;", "\tTree result = (A:2:2)B;", "END;") },
				{ Stream.of("BEGIN TREES;", "\tTree result = (A:2)B:1;", "END;") },
				{ Stream.of("BEGIN TREES;", "\tTree result = (A:2)B:;", "END;") },
				{ Stream.of("BEGIN TREES;", "\tTree result = (A:2)B", "END;") }
		};
	}

	@Test(dataProvider = "invalid")
	public void parse_Invalid_Null(Stream<String> data) {
		assertNull(new Nexus().parse(data, new Options()));
	}

	@Test
	public void parse_Valid_Success() {
		Stream<String> data = Stream.of("BEGIN TAXA;", "\tTaxLabels A B C", "END;", "BEGIN TREES;", "\tTree result = ((A:2.3,B:1.0)_:3.1,C:0.2)_;(D:0.5)E;", "END;");

		Tree tree = new Nexus().parse(data, new Options());

		List<Edge> edges = tree.edges().collect(Collectors.toList());

		assertEquals(edges.size(), 5);
		assertEquals(edges.get(0).from(), 2);
		assertEquals(edges.get(0).to(), 0);
		assertEquals(edges.get(0).distance(), 2.3);
		assertEquals(edges.get(1).from(), 2);
		assertEquals(edges.get(1).to(), 1);
		assertEquals(edges.get(1).distance(), 1);
		assertEquals(edges.get(2).from(), 4);
		assertEquals(edges.get(2).to(), 2);
		assertEquals(edges.get(2).distance(), 3.1);
		assertEquals(edges.get(3).from(), 4);
		assertEquals(edges.get(3).to(), 3);
		assertEquals(edges.get(3).distance(), 0.2);
		assertEquals(edges.get(4).from(), 6);
		assertEquals(edges.get(4).to(), 5);
		assertEquals(edges.get(4).distance(), 0.5);
	}

	@Test
	public void format_Empty_Empty() {
		assertEquals(new Nexus().parse(new Tree(new String[0])), "BEGIN TREES;\n\tTree result = \nEND;");
	}

	@Test
	public void format_Valid_Success() {
		Tree tree = new Tree(new String[] { "A", "B", "C" });
		tree.add(new Edge(3, 0, 2.3));
		tree.add(new Edge(3, 1, 1));
		tree.add(new Edge(4, 3, 3.1));
		tree.add(new Edge(4, 2, 0.2));

		String data = new Nexus().parse(tree);

		assertEquals(data, "BEGIN TREES;\n\tTree result = ((A:2.3,B:1.0)_:3.1,C:0.2)_;\nEND;");
	}

}
