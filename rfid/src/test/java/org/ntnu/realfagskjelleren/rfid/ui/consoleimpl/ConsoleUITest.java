package org.ntnu.realfagskjelleren.rfid.ui.consoleimpl;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * @author Håvard Slettvold
 */
public class ConsoleUITest {

    private ConsoleUI ui;


    @Test
    public void wrapTest() {
        ui = new ConsoleUI(30);

        List<String> testStrings = Arrays.asList("1234567890 1234567890 1234567890 1234567890");
        List<String> desiredResult = Arrays.asList("1234567890 1234567890", "1234567890 1234567890");

        assertEquals(desiredResult, ui.wrap(testStrings));
    }

    @Test
    public void wrapWithLongWordTest() {
        ui = new ConsoleUI(30);

        List<String> testStrings = Arrays.asList("12345678901234567890123456789012345678901234567890");
        List<String> desiredResult = Arrays.asList("12345678901234567890123456", "789012345678901234567890");

        assertEquals(desiredResult, ui.wrap(testStrings));
    }

    @Test
    public void wrapMultipleLines() {
        ui = new ConsoleUI(30);

        List<String> testStrings = Arrays.asList(
                "12345678901234567890123456789012345678901234567890",
                "12345678901234567890123456789012345678901234567890"
        );
        List<String> desiredResult = Arrays.asList(
                "12345678901234567890123456",
                "789012345678901234567890",
                "12345678901234567890123456",
                "789012345678901234567890"
        );

        assertEquals(desiredResult, ui.wrap(testStrings));
    }

    @Test
    public void testLeftAlign() {
        ui = new ConsoleUI(30);
        char[] bdc = ui.getBoxDrawingCharacters();

        String testString = "Here be text";
        String desiredResult = bdc[1] + " Here be text               " + bdc[1];
        assertEquals(desiredResult, ui.leftAlign(testString));
    }

    @Test
    public void testRightAlign() {
        ui = new ConsoleUI(30);
        char[] bdc = ui.getBoxDrawingCharacters();

        String testString = "Here be text";
        String desiredResult = bdc[1] + "               Here be text " + bdc[1];
        assertEquals(desiredResult, ui.rightAlign(testString));
    }

    @Test
    public void testCenterAlign() {
        ui = new ConsoleUI(30);
        char[] bdc = ui.getBoxDrawingCharacters();

        String testString = "Here be text";
        String desiredResult = bdc[1] + "        Here be text        " + bdc[1];
        assertEquals(desiredResult, ui.center(testString));
    }

    @Test
    public void testCenterAlignWithOddNumberOfCharacters() {
        ui = new ConsoleUI(30);
        char[] bdc = ui.getBoxDrawingCharacters();

        String testString = "Here be texts";
        String desiredResult = bdc[1] + "       Here be texts        " + bdc[1];
        assertEquals(desiredResult, ui.center(testString));
    }

    @Test
    public void testTable() {
        ui = new ConsoleUI(120);

        List<String> data = Arrays.asList(
                "Thead1 | Thead2 | Thead",
                "===",
                "Tdata1.1 | Tdatfdsaa2.1 | Tdataaa3.1",
                "Tdata1.2 | Tdataa2.2 | Tdataaa3.2|3",
                "Tda    a1.3 |  | Tdafgrtaaa3.3|4dsa",
                "---",
                "a|b|c|d"
        );
        List<String> desiredResult = Arrays.asList(
                "╔═════════════╦══════════════╦═══════════════╦══════╗" +
                "║ Thead1      ║ Thead2       ║ Thead         ║      ║",
                "╠═════════════╬══════════════╬═══════════════╬══════╣",
                "║ Tdata1.1    ║ Tdatfdsaa2.1 ║ Tdataaa3.1    ║      ║",
                "║ Tdata1.2    ║ Tdataa2.2    ║ Tdataaa3.2    ║ 3    ║",
                "║ Tda    a1.3 ║              ║ Tdafgrtaaa3.3 ║ 4dsa ║",
                "╟─────────────╫──────────────╫───────────────╫──────╢",
                "║ a           ║ b            ║ c             ║ d    ║",
                "╚═════════════╩══════════════╩═══════════════╩══════╝"
        );

//        for (String line : ui.table(data)) {
//            System.out.println(line);
//        }
    }
}
