package pt.ist.phylolib.data.dataset;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import pt.ist.phylolib.cli.Options;

import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class SNPTest {

	@DataProvider
	public Object[][] invalid() {
		return new Object[][] {
				{ Stream.empty() },
				{ Stream.of(" ") },
				{ Stream.of("1\t10100111") },
				{ Stream.of("1\t10100111", "2\t1x100111") }
		};
	}

	@Test(dataProvider = "invalid")
	public void parse_Invalid_Null(Stream<String> profiles) {
		assertNull(new SNP().parse(profiles, new Options()));
	}

	@DataProvider
	public Object[][] valid() {
		return new Object[][] {
				{ "", "X\t10-01", "Y\t10101" },
				{ "1\t", "X\t10 01", "Y\t01001" },
				{ "1\t1", "X\t10 01", "Y\t01001" },
				{ "X\t11-01", "W\t100", "Y\t01001" },
				{ "X\t11 10", "Y\t10001", "3\t010010" },
				{ "1\t11x11", "X\t10-01", "Y\t11010" },
				{ "X\t11 01", "Y\t10010" }
		};
	}

	@Test(dataProvider = "valid")
	public void parse_Valid_Success(String... profiles) {
		Stream<String> data = Stream.of(profiles);

		Dataset dataset = new SNP().parse(data, new Options());

		assertEquals(dataset.size(), 2);
		assertEquals(dataset.profile(0).size(), 5);
		assertEquals(dataset.profile(0).id(), "X");
		assertNull(dataset.profile(0).locus(2));
		assertEquals(dataset.profile(1).size(), 5);
		assertEquals(dataset.profile(1).id(), "Y");
	}

}
