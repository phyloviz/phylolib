package pt.ist.phylolib.data.dataset;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import pt.ist.phylolib.cli.Options;

import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class MLTest {

	@DataProvider
	public Object[][] invalid() {
		return new Object[][] {
				{ Stream.empty() },
				{ Stream.of(" ") },
				{ Stream.of("ST\taroe\taroi", "1\t1\t1") },
				{ Stream.of("ST\taroe\taroi", "1\t1\t1", "2\tb\t2") }
		};
	}

	@Test(dataProvider = "invalid")
	public void parse_Invalid_Null(Stream<String> profiles) {
		assertNull(new ML().parse(profiles, new Options()));
	}

	@DataProvider
	public Object[][] valid() {
		return new Object[][] {
				{ "ST\taroe\taroi\tarou", "", "1\t1\t2\t1\t1", "2\t \t2\t1\t1" },
				{ "ST\taroe\taroi\tarou", "1", "1\t1\t2\t1\t1", "2\t \t2\t1\t1" },
				{ "ST\taroe\taroi\tarou", "1\t1", "1\t1\t2\t1\t1", "2\t \t2\t1\t1" },
				{ "ST\taroe\taroi\tarou", "1\t1\t2\t1\t1", "2\t \t2\t1\t1", "3\t1\t2\t3" },
				{ "ST\taroe\taroi\tarou", "1\t1\t2\t1\t1", "2\t \t2\t1\t1", "3\t1\t2\t3\t4\t5" },
				{ "ST\taroe\taroi\tarou", "1\t1\t2\t1\t1", "2\t \t2\t1\t1", "3\tx\t2\t3\t4" },
				{ "ST\taroe\taroi\tarou", "1\t1\t2\t1\t1", "2\t \t2\t1\t1" },
				};
	}

	@Test(dataProvider = "valid")
	public void parse_Valid_Success(String... profiles) {
		Stream<String> data = Stream.of(profiles);

		Dataset dataset = new ML().parse(data, new Options());

		assertEquals(dataset.size(), 2);
		assertEquals(dataset.profile(0).size(), 4);
		assertEquals(dataset.profile(0).id(), "1");
		assertEquals(dataset.profile(1).size(), 4);
		assertEquals(dataset.profile(1).id(), "2");
		assertNull(dataset.profile(1).locus(0));
	}

}
