package pt.ist.phylolib.data.matrix;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import pt.ist.phylolib.cli.Options;

import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class SymmetricTest {

	@DataProvider
	public Object[][] invalid() {
		return new Object[][] {
				{ Stream.empty() },
				{ Stream.of(" ") },
				{ Stream.of("0") },
				{ Stream.of("1") },
				{ Stream.of("1", "0.0") },
				{ Stream.of("2", "1", "2\tb") },
				{ Stream.of("2", "1\t0", "2\t4.5\t0\t0") },
				{ Stream.of("2", "1", "2\t2.7", "3\t1\t1.5") },
				{ Stream.of("3", "1", "2\t4.5", "3\t4.5") }
		};
	}

	 @Test(dataProvider = "invalid")
	 public void parse_Invalid_Null(Stream<String> rows) {
	 	assertNull(new Symmetric().parse(rows, new Options()));
	 }

	@Test()
	public void parse_Valid_Success() {
		Stream<String> data = Stream.of("2", "1\t", "2\t4.5");

		Matrix matrix = new Symmetric().parse(data, new Options());

		assertEquals(matrix.size(), 2);
		assertEquals(matrix.distance(0, 0), 0);
		assertEquals(matrix.distance(0, 1), 4.5);
		assertEquals(matrix.distance(1, 0), 4.5);
		assertEquals(matrix.distance(1, 1), 0);
	}

	@Test
	public void format_Empty_Empty() {
		assertEquals(new Symmetric().parse(new Matrix(false, new String[0], (i, j) -> 0)), "0");
	}

	@Test
	public void format_Valid_Success() {
		Matrix matrix = new Matrix(false, new String[] { "1", "2" }, (i, j) -> 50.36);

		String data = new Symmetric().parse(matrix);

		assertEquals(data, "2\n1\n2\t50.36");
	}

}
