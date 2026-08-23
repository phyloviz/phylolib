package pt.ist.phylolib.cli;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class OptionsTest {

	@DataProvider
	public Object[][] options() {
		return new Object[][] {
				{ "--out" },
				{ "--out=" },
				{ "--out= " },
				{ "out=newick:output.txt" },
				{ "-c=3" },
				{ "-l=-4" },
				{ "--matrix=asymmetric-matrix.txt" }
		};
	}

	@Test(dataProvider = "options")
	public void put_Invalid_Ignored(String option) {
		Options options = new Options();

		options.put(option);

		assertTrue(options.keys().isEmpty());
	}

	@Test
	public void put_Repeated_Ignored() {
		Options options = new Options();
		options.put("--out=newick:output.txt");
		options.put("--lvs=3");

		options.put("--out=nexus:out.txt");

		assertEquals(options.keys().size(), 2);
		assertEquals(options.remove(Option.LVS), "3");
		assertEquals(options.remove(Option.OUT), "newick:output.txt");
	}

	@Test
	public void put_Valid_Success() {
		Options options = new Options();

		options.put("-l=7");
		options.put("--matrix=asymmetric:matrix.txt");

		assertEquals(options.keys().size(), 2);
		assertEquals(options.remove(Option.LVS), "7");
		assertEquals(options.remove(Option.MATRIX), "asymmetric:matrix.txt");
	}

	@Test
	public void remove_NoMatchWithDefault_DefaultValue() {
		Options options = new Options();
		options.put("-t=newick:output.txt");

		String lvs = options.remove(Option.LVS);

		assertEquals(lvs, "3");
		assertEquals(options.keys().size(), 1);
		assertTrue(options.keys().contains(Option.TREE));
	}

	@Test
	public void remove_KeyWithDefault_PreviousValue() {
		Options options = new Options();
		options.put("--lvs=5");

		String lvs = options.remove(Option.LVS);

		assertEquals(lvs, "5");
		assertTrue(options.keys().isEmpty());
	}

	@Test
	public void remove_AliasWithDefault_PreviousValue() {
		Options options = new Options();
		options.put("-l=7");

		String lvs = options.remove(Option.LVS);

		assertEquals(lvs, "7");
		assertTrue(options.keys().isEmpty());
	}

	@Test
	public void remove_NoMatch_Empty() {
		Options options = new Options();
		options.put("--tree=nexus:tree.txt");

		String matrix = options.remove(Option.MATRIX);

		assertNull(matrix);
		assertEquals(options.keys().size(), 1);
		assertTrue(options.keys().contains(Option.TREE));
	}

	@Test
	public void remove_Key_PreviousValue() {
		Options options = new Options();
		options.put("--lvs=3");

		String lvs = options.remove(Option.LVS);

		assertEquals(lvs, "3");
		assertTrue(options.keys().isEmpty());
	}

	@Test
	public void remove_Alias_PreviousValue() {
		Options options = new Options();
		options.put("--matrix=asymmetric:matrix.txt");
		options.put("--dataset=mlva:dataset.txt");
		options.put("--tree=newick:tree.txt");

		String dataset = options.remove(Option.DATASET);

		assertEquals(dataset, "mlva:dataset.txt");
		assertEquals(options.keys().size(), 2);
		assertTrue(options.keys().contains(Option.MATRIX));
		assertTrue(options.keys().contains(Option.TREE));
	}

	@Test
	public void put_NewEdmondsOptions_Success() {
		Options options = new Options();
		options.put("--input=fasta:sequences.fasta");
		options.put("--prev-state=binary:/tmp/prev.dat");

		assertEquals(options.keys().size(), 2);
		assertEquals(options.remove(Option.INPUT), "fasta:sequences.fasta");
		assertEquals(options.remove(Option.PREV_STATE), "binary:/tmp/prev.dat");
	}

	@Test
	public void put_NewEdmondsOptions_Alias_Success() {
		Options options = new Options();
		options.put("-i=ml:profiles.txt");
		options.put("-p=newick:/tmp/prev_state");

		assertEquals(options.keys().size(), 2);
		assertEquals(options.remove(Option.INPUT), "ml:profiles.txt");
		assertEquals(options.remove(Option.PREV_STATE), "newick:/tmp/prev_state");
	}

}
