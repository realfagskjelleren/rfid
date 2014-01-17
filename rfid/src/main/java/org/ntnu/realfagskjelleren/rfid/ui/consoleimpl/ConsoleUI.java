package org.ntnu.realfagskjelleren.rfid.ui.consoleimpl;

import org.apache.commons.lang3.StringUtils;
import org.ntnu.realfagskjelleren.rfid.db.model.Transaction;
import org.ntnu.realfagskjelleren.rfid.db.model.User;
import org.ntnu.realfagskjelleren.rfid.ui.model.UI;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementation of UI via the console or terminal. The implementation is made to work with only a numpad.
 *
 * @author Håvard Slettvold
 */
public class ConsoleUI implements UI {

    private Scanner scanner = new Scanner(System.in);

    private final String EXIT_SIGNAL = "***";
                                                         // 0    1    2    3    4    5    6    7    8    9    10   11   12   13   14
    private final char[] boxDrawingCharacters = new char[]{'═', '║', '╔', '╗', '╚', '╝', '╠', '╣', '╦', '╩', '╬', '─', '╟', '╢', '╫'};
    private final int consoleWidth;
    private boolean active_transaction = false;

    public ConsoleUI(int consoleWidth) {
        this.consoleWidth = consoleWidth;
    }

    @Override
    public void showWelcomeMessage() {
        frameTop();
        if (consoleWidth < 60) {
            printCenterAligned(Arrays.asList(
                    "    ____  ______________ ",
                    "   / __ \\/ ____/  _/ __ \\",
                    "  / /_/ / /_   / // / / /",
                    " / _, _/ __/ _/ // /_/ / ",
                    "/_/ |_/_/   /___/_____/  ",
                    "                         "
            ));
        }
        else {
            printCenterAligned(Arrays.asList(
                    "      ___           ___                       ___     ",
                    "     /\\  \\         /\\  \\          ___        /\\  \\    ",
                    "    /::\\  \\       /::\\  \\        /\\  \\      /::\\  \\   ",
                    "   /:/\\:\\  \\     /:/\\:\\  \\       \\:\\  \\    /:/\\:\\  \\  ",
                    "  /::\\~\\:\\  \\   /::\\~\\:\\  \\      /::\\__\\  /:/  \\:\\__\\ ",
                    " /:/\\:\\ \\:\\__\\ /:/\\:\\ \\:\\__\\  __/:/\\/__/ /:/__/ \\:|__|",
                    " \\/_|::\\/:/  / \\/__\\:\\ \\/__/ /\\/:/  /    \\:\\  \\ /:/  /",
                    "    |:|::/  /       \\:\\__\\   \\::/__/      \\:\\  /:/  / ",
                    "    |:|\\/__/         \\/__/    \\:\\__\\       \\:\\/:/  /  ",
                    "    |:|  |                     \\/__/        \\::/__/   ",
                    "     \\|__|                                   ~~       "
            ));
        }
        printCenterAligned(Arrays.asList("by realfagskjelleren"));
        frameEmpty();
        frameBottom();
    }

    @Override
    public void showHelp() {
        display("Help...");
    }

    @Override
    public String takeInput() {
        try {
            if (active_transaction) {
                display("- Input amount to withdraw or deposit (+).");
            }
            else {
                display("- Input card number or type command. Use /*- for help.");
            }
            System.out.print("> ");
            String input = scanner.nextLine();

            if (input.equals(EXIT_SIGNAL)) return "exit";

            return input;
        } catch (NoSuchElementException e) {
            // Occurs when the program is interrupted. Essentially means quit. Returning null will exit.
            return null;
        }
    }

    @Override
    public void display(String output) {
        display(Arrays.asList(output));
    }

    @Override
    public void display(List<String> output) {
        if (active_transaction) {
            frameEmpty();
            printLeftAligned(output);
            //frameEmpty();
        }
        else {
            print("");
            print(output);
            //print("");
        }

    }

    @Override
    public void startTransaction(User user) {
        active_transaction = true;
        frameTop();

        List<String> response = Arrays.asList(
                "RFID: " + user.getRfid(),
                "---",
                "Last used: " + user.getLastUsed(),
                "Balance: " + user.getCredit()
        );

        display(table(response));
    }

    @Override
    public void endTransaction(String output) {
        endTransaction(Arrays.asList(output));
    }

    @Override
    public void endTransaction(List<String> output) {
        display(output);
        frameBottom();
        active_transaction = false;
    }

    @Override
    public void error(String error) {
        display(Arrays.asList(
                StringUtils.repeat("!", consoleWidth - 4),
                StringUtils.center(error, consoleWidth - 4),
                StringUtils.repeat("!", consoleWidth - 4)
        ));
    }

    @Override
    public void error(List<String> errors) {
        for (String error : errors) {

        }
    }

    @Override
    public void showTransactions(List<Transaction> transactions) {
        List<String> tableData = new ArrayList<>();

        String rowFormat = "%s|%s|%s|%s";
        String tableHeader = "RFID | Amount | New balance | Date";

        // Generate table
        tableData.add(tableHeader);
        tableData.add("===");
        for (int i=0; i < transactions.size(); i++) {
            if (i != 0 && i % 5 == 0) {
                tableData.add("---");
            }
            Transaction t = transactions.get(i);
            String sign = t.isDeposit() ? "+" : "-";
            tableData.add(String.format(
                    rowFormat,
                    t.getRfid(),
                    sign + t.getValue(),
                    t.getNew_balance(),
                    t.getDate()
            ));
        }

        display(table(tableData));
    }

    @Override
    public void showUsers(List<User> users) {
        List<String> tableData = new ArrayList<>();

        String rowFormat = "%d|%s|%s|%s";
        String tableHeader = "ID | RFID | Balance | Last used";

        // Generate table
        tableData.add(tableHeader);
        tableData.add("===");
        for (int i=0; i < users.size(); i++) {
            if (i != 0 && i % 5 == 0) {
                tableData.add("---");
            }
            User u = users.get(i);
            tableData.add(String.format(
                    rowFormat,
                    u.getId(),
                    u.getRfid(),
                    u.getCredit(),
                    u.getLastUsed()
            ));
        }

        display(table(tableData));
    }

    /*
        Printing
     */

    /**
     * Regular print medthos. No borders.
     * This method is used in order to simplify changing the ui at a later
     * point, rather than statically using System.out.println.
     *
     * @param line Line to be printed
     */
    private void print(String line) {
        System.out.println(line);;
    }

    /**
     * Just a printing call with more than one line.
     *
     * @param lines Lines to be printed.
     */
    private void print(List<String> lines) {
        for (String line : lines) {
            print(line);
        }
    }

    /* Some methods to sort out the printing of transaction specific separation */

    /* Frame parts - frame meaning the full consoleWidth frame */

    /**
     * Inserts a line into the transactionthat is the top of a square frame.
     */
    private void frameTop() {
        System.out.println(boxDrawingCharacters[2] + StringUtils.repeat(boxDrawingCharacters[0], consoleWidth-2) + boxDrawingCharacters[3]);
    }

    /**
     * Inserts a line into the transactionthat is the bottom of a square frame.
     */
    private void frameBottom() {
        System.out.println(boxDrawingCharacters[4] + StringUtils.repeat(boxDrawingCharacters[0], consoleWidth-2) + boxDrawingCharacters[5]);
    }

    /**
     * Inserts a line into the transactionwith vertical borders and a horizontal double line.
     */
    private void frameMiddle() {
        System.out.println(boxDrawingCharacters[6] + StringUtils.repeat(boxDrawingCharacters[0], consoleWidth-2) + boxDrawingCharacters[7]);
    }

    /**
     * Inserts a line into the transactionwith only vertical borders on either side and no content.
     */
    private void frameEmpty() {
        System.out.println(boxDrawingCharacters[1] + StringUtils.repeat(" ", consoleWidth-2) + boxDrawingCharacters[1]);
    }

    /* General printing methods */

    /**
     * Alias for printing with left alignment that takes a single String.
     * This includes frame borders and is meant for transactions.
     *
     * @param line Line to be printed
     */
    private void printLeftAligned(String line) {
        printLeftAligned(Arrays.asList(line));
    }

    /**
     * Prints lines with normal left alignment.
     * This includes frame borders and is meant for transactions.
     *
     * @param lines Lines to be printed
     */
    private void printLeftAligned(List<String> lines) {
        List<String> wrappedLines = wrap(lines);

        for (String line : wrappedLines) {
            System.out.println(leftAlign(line));
        }
    }

    /**
     * Alias for printing with right alignment that takes a single String.
     * This includes frame borders and is meant for transactions.
     *
     * @param line Line to be printed
     */
    private void printRightAligned(String line) {
        printRightAligned(Arrays.asList(line));
    }

    /**
     * Prints lines with right alignment.
     * This includes frame borders and is meant for transactions.
     *
     * @param lines Lines to be printed
     */
    private void printRightAligned(List<String> lines) {
        List<String> wrappedLines = wrap(lines);

        for (String line : wrappedLines) {
            System.out.println(rightAlign(line));
        }
    }

    /**
     * Alias for printing with center alignment that takes a single String.
     * This includes frame borders and is meant for transactions.
     *
     * @param line Line to be printed
     */
    public void printCenterAligned(String line) {
            printCenterAligned(Arrays.asList(line));
    }

    /**
     * Prints lines with center alignment.
     * This includes frame borders and is meant for transactions.
     *
     * @param lines Lines to be printed
     */
    private void printCenterAligned(List<String> lines) {
        List<String> wrappedLines = wrap(lines);

        for (String line : wrappedLines) {
            System.out.println(center(line));
        }
    }

    /**
     * Checks the length of each string in the list against the set width of the console.
     * Any line will be wrapped to the number of new lines required for it to fit inside the console.
     *
     * Wrapping attempts to use spaces to break lines, but will break words into bits that fit on each
     * line if necessary.
     *
     * @param lines Lines to be wrapped
     * @return Wrapped lines
     */
    protected List<String> wrap(List<String> lines) {
        // There should be room for "║ " on the left and " ║" on the right
        int desiredWidth = consoleWidth - 4;
        List<String> wrappedLines = new ArrayList<>();

        for (String line : lines) {
            if (line.length() > desiredWidth) {

                Pattern regex = Pattern.compile("(.{1,"+desiredWidth+"}(?:\\s|$))|(.{0,"+desiredWidth+"})", Pattern.DOTALL);
                Matcher regexMatcher = regex.matcher(line);
                while (regexMatcher.find()) {
                    String result = regexMatcher.group().trim();
                    if (result.isEmpty()) continue;
                    wrappedLines.add(result);
                }

            }
            else {
                wrappedLines.add(line);
            }
        }

        return wrappedLines;
    }

    /**
     * Aligns a line to the right of the console. Alignment should be done after wrapping.
     *
     * @param line Line to be aligned left
     * @return Left aligned line
     */
    protected String leftAlign(String line) {
        return String.format("%s %s %s", boxDrawingCharacters[1], StringUtils.rightPad(line, consoleWidth - 4, " "), boxDrawingCharacters[1]);
    }

    /**
     * Aligns a line to the right of the console. Alignment should be done after wrapping.
     *
     * @param line Line to be aligned right
     * @return Right aligned line
     */
    protected String rightAlign(String line) {
        return String.format("%s %s %s", boxDrawingCharacters[1], StringUtils.leftPad(line, consoleWidth - 4, " "), boxDrawingCharacters[1]);
    }

    /**
     * Alings a line to the center of the console. Alignment should be done after wrapping.
     *
     * @param line Line to be centered
     * @return Centered line
     */
    protected String center(String line) {
        return String.format("%s %s %s", boxDrawingCharacters[1], StringUtils.center(line, consoleWidth - 4, " "), boxDrawingCharacters[1]);
    }

    /* Making tables */

    /**
     * Generates tables from a list of strings where each string is a row.
     * Columns are separated by pipe ('|') and horizontal lines can be inserted by triple dash ('---').
     *
     * @param lines Rows in the table
     * @return List of strings containing the formatted table
     */
    protected List<String> table(List<String> lines) {
        List<String> table = new ArrayList<>();
        String rowFormat = ""+boxDrawingCharacters[1];

        String[][] data = new String[lines.size()][];
        int[] maxLengths = null;
        int mostCells = 0;

        // Find the widest cell in each row
        for (int i=0; i < lines.size(); i++) {
            String[] row = lines.get(i).split("\\s*\\|\\s*");

            if (row.length > mostCells) {
                mostCells = row.length;
            }
            if (maxLengths == null) {
                maxLengths = new int[row.length];
            }
            else if (row.length > maxLengths.length) {
                maxLengths = Arrays.copyOf(maxLengths, row.length);
            }

            data[i] = row;
            for (int j=0; j < row.length; j++) {
                if (row[j].length() > maxLengths[j]) {
                    maxLengths[j] = row[j].length();
                }
            }
        }

        String tableTop = ""+boxDrawingCharacters[2];
        String tableMiddleSingle = ""+boxDrawingCharacters[12];
        String tableMiddleDouble = ""+boxDrawingCharacters[6];
        String tableBottom = ""+boxDrawingCharacters[4];

        for (int i=0; i < maxLengths.length; i++) {
            int colWidth = maxLengths[i];
            rowFormat += " %"+(i+1)+"$-"+colWidth+"s "+ boxDrawingCharacters[1];

            tableTop += StringUtils.repeat(boxDrawingCharacters[0], colWidth + 2) + boxDrawingCharacters[8];
            tableMiddleSingle += StringUtils.repeat(boxDrawingCharacters[11], colWidth + 2) + boxDrawingCharacters[14];
            tableMiddleDouble += StringUtils.repeat(boxDrawingCharacters[0], colWidth + 2) + boxDrawingCharacters[10];
            tableBottom += StringUtils.repeat(boxDrawingCharacters[0], colWidth + 2) + boxDrawingCharacters[9];
        }

        tableTop = tableTop.substring(0, tableTop.length()-1) + boxDrawingCharacters[3];
        tableMiddleSingle = tableMiddleSingle.substring(0, tableMiddleSingle.length()-1) + boxDrawingCharacters[13];
        tableMiddleDouble = tableMiddleDouble.substring(0, tableMiddleDouble.length()-1) + boxDrawingCharacters[7];
        tableBottom = tableBottom.substring(0, tableBottom.length()-1) + boxDrawingCharacters[5];

        table.add(tableTop);
        for (String[] row : data) {
            if (row[0].equals("---")) {
                table.add(tableMiddleSingle);
            }
            else if (row[0].equals("===")) {
                table.add(tableMiddleDouble);
            }
            else {
                if (row.length < mostCells) {
                    int i = row.length;
                    row = Arrays.copyOf(row, mostCells);
                    for (; i < row.length; i++) {
                        row[i] = "";
                    }
                }
                table.add(String.format(rowFormat, row));
            }
        }
        table.add(tableBottom);

        return table;
    }

    /*
        This method is only supposed to be used for testing!
     */

    protected char[] getBoxDrawingCharacters() {
        return this.boxDrawingCharacters;
    }
}
