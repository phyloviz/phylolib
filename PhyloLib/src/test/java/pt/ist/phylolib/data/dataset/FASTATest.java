package pt.ist.phylolib.data.dataset;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import pt.ist.phylolib.cli.Options;

import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class FASTATest {

	@DataProvider
	public Object[][] invalid() {
		return new Object[][] {
				{ Stream.empty() },
				{ Stream.of(" ") },
				{ Stream.of(">1", "TACGCAGT") },
				{ Stream.of(">1", "TACGCXGT", ">2", "TACGCAGT") }
		};
	}

	@Test(dataProvider = "invalid")
	public void parse_Invalid_Null(Stream<String> profiles) {
		assertNull(new FASTA().parse(profiles, new Options()));
	}

	@DataProvider
	public Object[][] valid() {
		return new Object[][] {
				{ "TACTGATC", "TACTGATC", ">A", "TATTG TC", ">B", "ATCTAGTC" },
				{ ">A", "TACTG TC", ">2 profile", ">B", "ATCTAGTC" },
				{ ">1", "", ">A", "TACTG TC", ">B", "TACTGATC" },
				{ ">1", "A", ">A", "TACTG TC", ">B", "ACTGGATC" },
				{ ">A", "ACTGG-TC", ">2", "TACT", ">B", "ACTGGATC" },
				{ ">A", "ACTGG TC", ">2 profile", "TACTGATC", "ACTGGATC", ">B", "ACTGGATC" },
				{ ">A", "ACTGG-TC", ">2 profile", "ACTGXATC", ">B", "TACTGATC" },
				{ ">A", "ACTGG TC", ">B", "TACTGATC" }
		};
	}

	@Test(dataProvider = "valid")
	public void parse_Valid_Success(String... profiles) {
		Stream<String> data = Stream.of(profiles);

		Dataset dataset = new FASTA().parse(data, new Options());

		assertEquals(dataset.size(), 2);
		assertEquals(dataset.profile(0).size(), 8);
		assertEquals(dataset.profile(0).id(), "A");
		assertNull(dataset.profile(0).locus(5));
		assertEquals(dataset.profile(1).size(), 8);
		assertEquals(dataset.profile(1).id(), "B");
	}

}
