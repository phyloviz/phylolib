package pt.ist.phylolib;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class MainTest {

    @Test
    public void run_InvalidCommand_ReturnsNonZero() {
        assertEquals(Main.run(new String[] { "not-a-command" }), 1);
    }

    @Test
    public void run_Help_ReturnsZero() {
        assertEquals(Main.run(new String[] { "help" }), 0);
    }
}
