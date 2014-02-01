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
    private Console console = new Console();     
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
        display(table(Arrays.asList(
                "Command | Description",
                "---",
                "| Requires a card to be scanned before use",
                "---",
                "+xxx | Inserts xxx into the currently scanned RFID",
                "xxx | Remove xxx from the currently scanned RFID",
                "--- | Shows the total amount of money spent from currently scanned RFID",
                "| Other commands",
                "---",
                "*** | Quit",
                "/// | Show all users",
                "--X | Show the X most profitable days (If X is empty show all)",
                "++ | Show general stats for the system",
                "/X | Show X latest transactions (If X is empty show 10)",
                "*X | Show the top 10 users over the past X hours (If X is empty show 15 hours)"
        )));
    }

    @Override
    public void invalidCommand() {
        display("Unrecognized command. Use /*- for help.");
    }

    @Override
    public String takeInput() {
        if (active_transaction) {
            return takeInput("- Input amount to withdraw or deposit (+).");
        }
        else {
            return takeInput("- Input card number or type command. Use /*- for help.");
        }
    }

    @Override
    public String takeInput(String question) {
        try {
            display(question);
            console.print("> ");
            String input = scanner.nextLine();

            // If exit signal is found, return exit.
            if (input.equals("***")) return "exit";
            if (input.equals("/*-")) return "/help";
            if (input.equals("+++")) return "/updateRfid";
            if (input.equals("---")) return "/totalSpent";
            if (input.equals("///")) return "/users";
            if (input.startsWith("--")) return "/topDays " + input.substring(2);
            if (input.startsWith("++")) return "/stats " + input.substring(2);
            if (input.startsWith("/")) return "/transactions " + input.substring(1);
            if (input.startsWith("*")) return "/topTen " + input.substring(1);

            return input;
        } catch (NoSuchElementException e) {
            // Occurs when the program is interrupted. Essentially means quit. Returning null will exit.
            return null;
        }
    }

    @Override
    public boolean takeConfirmation(String output) {
        display(Arrays.asList(
                output,
                "Use 5 for yes and anything else for no."
        ));
        console.print("> ");

        String input = scanner.nextLine();

        return input.equals("5");
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
        }
        else {
            println("");
            println(output);
        }

    }

    @Override
    public void showTable(List<String> tableData) {
        display(table(tableData));
    }

    @Override
    public void startTransaction(User user) {
        active_transaction = true;
        frameTop();

        List<String> response = Arrays.asList(
                "RFID: " + user.getRfid(),
                "---",
                "Created: " + user.getCreated(),
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
        error(Arrays.asList(error));
    }

    @Override
    public void error(List<String> errors) {
        List<String> formattedErrors = new ArrayList<>();
        formattedErrors.add(StringUtils.repeat("!", consoleWidth - 4));
        for (String error : errors) {
            formattedErrors.add(StringUtils.center(error, consoleWidth - 4));
        }
        formattedErrors.add(StringUtils.repeat("!", consoleWidth - 4));
        display(formattedErrors);
    }

    /**
     * Show a table of transactions.
     *
     * @param transactions List of transactions to display
     */
    @Override
    public void showTransactions(List<Transaction> transactions) {
        List<String> tableData = new ArrayList<>();

        String rowFormat = "%s|%s|%s|%s";
        String tableHeader = "RFID | Amount | New balance | Date";

        // Generate table
        tableData.add(tableHeader);
        tableData.add("===");
        for (int i=transactions.size() - 1; i >= 0; i--) {
            Transaction t = transactions.get(i);
            String sign = t.isDeposit() ? "+" : "-";
            tableData.add(String.format(
                    rowFormat,
                    t.getRfid(),
                    sign + t.getValue(),
                    t.getNew_balance(),
                    t.getDate()
            ));
            if (i != 0 && i % 5 == 0) {
                tableData.add("---");
            }
        }

        display(table(tableData));
    }

    /**
     * Show a table of users.
     *
     * @param users List of users to display
     */
    @Override
    public void showUsers(List<User> users) {
        List<String> tableData = new ArrayList<>();

        String rowFormat = "%d|%s|%d|%s|%s";
        String tableHeader = "ID | RFID | Balance | Created | Last used";

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
                    u.getCreated(),
                    u.getLastUsed()
            ));
        }

        display(table(tableData));
    }

    /*
        Printing
     */

    /**
     * Regular print method. No borders.
     * This method is used in order to simplify changing the ui at a later
     * point, rather than statically using console.println.
     *
     * @param line Line to be printed
     */
    private void println(String line) {
        console.println(line);
    }

    /**
     * Just a printing call with more than one line.
     *
     * @param lines Lines to be printed.
     */
    private void println(List<String> lines) {
        for (String line : lines) {
            println(line);
        }
    }

    /* Some methods to sort out the printing of transaction specific separation */

    /* Frame parts - frame meaning the full consoleWidth frame */

    /**
     * Inserts a line into the transactionthat is the top of a square frame.
     */
    private void frameTop() {
        console.println(boxDrawingCharacters[2] + StringUtils.repeat(boxDrawingCharacters[0], consoleWidth-2) + boxDrawingCharacters[3]);
    }

    /**
     * Inserts a line into the transactionthat is the bottom of a square frame.
     */
    private void frameBottom() {
        console.println(boxDrawingCharacters[4] + StringUtils.repeat(boxDrawingCharacters[0], consoleWidth-2) + boxDrawingCharacters[5]);
    }

    /**
     * Inserts a line into the transactionwith vertical borders and a horizontal double line.
     */
    private void frameMiddle() {
        console.println(boxDrawingCharacters[6] + StringUtils.repeat(boxDrawingCharacters[0], consoleWidth-2) + boxDrawingCharacters[7]);
    }

    /**
     * Inserts a line into the transactionwith only vertical borders on either side and no content.
     */
    private void frameEmpty() {
        console.println(boxDrawingCharacters[1] + StringUtils.repeat(" ", consoleWidth-2) + boxDrawingCharacters[1]);
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
            console.println(leftAlign(line));
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
            console.println(rightAlign(line));
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
            console.println(center(line));
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
