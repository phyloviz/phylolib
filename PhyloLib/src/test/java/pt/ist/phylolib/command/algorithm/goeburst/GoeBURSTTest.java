package pt.ist.phylolib.command.algorithm.goeburst;

import pt.ist.phylolib.cli.Option;
import pt.ist.phylolib.cli.Options;
import pt.ist.phylolib.data.Context;
import pt.ist.phylolib.data.matrix.DistanceScope;
import pt.ist.phylolib.data.matrix.Matrix;
import pt.ist.phylolib.data.matrix.ThresholdSparseMatrix;
import pt.ist.phylolib.data.tree.Edge;
import pt.ist.phylolib.data.tree.Tree;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class GoeBURSTTest {

	@DataProvider
	public Object[][] data() {
		return new Object[][] {
				{ 1, new double[][] {
						{ },
						{ 1.0 } },
				  new Edge(0, 1, 1) },
				{ 2, new double[][] {
						{ },
						{ 2.0 },
						{ 2.0, 1.0 } },
				  new Edge(0, 1, 2),
				  new Edge(1, 2, 1) },
				{ 3, new double[][] {
						{ },
						{ 3.0 },
						{ 3.0, 3.0 },
						{ 3.0, 3.0, 2.0 },
						{ 3.0, 3.0, 3.0, 3.0 },
						{ 3.0, 3.0, 3.0, 3.0, 3.0 },
						{ 3.0, 2.0, 3.0, 3.0, 3.0, 3.0 },
						{ 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0 } },
				  new Edge(0, 1, 3),
				  new Edge(1, 2, 3),
				  new Edge(1, 4, 3),
				  new Edge(1, 5, 3),
				  new Edge(1, 6, 2),
				  new Edge(1, 7, 3),
				  new Edge(2, 3, 2) }
		};
	}

	@Test(dataProvider = "data")
	public void process_Valid_Success(int lvs, double[][] values, Edge... edges) {
		Matrix matrix = new Matrix(true, IntStream.range(0, values.length).mapToObj(String::valueOf).toArray(String[]::new), values);
		GoeBURST goeburst = new GoeBURST();
		Options options = new Options();
		options.put(Option.LVS, String.valueOf(lvs));
		goeburst.init(new Context(), options);
		List<Edge> expected = Arrays.asList(edges);

		Tree tree = goeburst.process(matrix);

		assertEquals(tree.edges().count(), expected.size());
		assertTrue(tree.edges().allMatch(i -> expected.stream().anyMatch(j -> i.from() == j.from() && i.to() == j.to() && i.distance() == j.distance())));
	}

	@Test
	public void process_ThresholdControlsForestConnectivity() {
		Matrix matrix = new Matrix(true, new String[] { "0", "1", "2" }, new double[][] {
				{},
				{ 1.0 },
				{ 2.0, 2.0 }
		});

		assertEquals(process(1, matrix).edges().count(), 1);
		assertEquals(process(2, matrix).edges().count(), 2);
	}

	@Test
	public void process_RepeatableForSameInput() {
		Matrix matrix = new Matrix(true, new String[] { "0", "1", "2" }, new double[][] {
				{},
				{ 2.0 },
				{ 2.0, 1.0 }
		});

		assertEquals(process(2, matrix).edges().toList(), process(2, matrix).edges().toList());
	}

	@Test
	public void process_AcceptsSufficientBoundedCoverage() {
		Matrix matrix = new ThresholdSparseMatrix(true, new String[] { "0", "1", "2" },
				new int[][] { {}, { 0 }, {} }, new double[][] { {}, { 1.0 }, {} }, 1);

		assertEquals(matrix.distanceScope(), new DistanceScope.Bounded(1));
		assertEquals(process(1, matrix).edges().count(), 1);
	}

	private Tree process(int lvs, Matrix matrix) {
		GoeBURST goeburst = new GoeBURST();
		Options options = new Options();
		options.put(Option.LVS, String.valueOf(lvs));
		goeburst.init(new Context(), options);
		return goeburst.process(matrix);
	}
}
