package org.ntnu.realfagskjelleren.rfid;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ntnu.realfagskjelleren.rfid.db.model.DBHandler;
import org.ntnu.realfagskjelleren.rfid.db.model.User;
import org.ntnu.realfagskjelleren.rfid.db.mysqlimpl.MySQLDBHandler;
import org.ntnu.realfagskjelleren.rfid.settings.Settings;
import org.ntnu.realfagskjelleren.rfid.settings.VerifySettings;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;

/**
 * This POS system is made to work with an RFID scanner and numeric pad.
 * Any additional keyboard is not necessary.
 *
 * @author Håvard Slettvold
 */
public class POS {

    private static Logger logger = LogManager.getLogger(POS.class.getName());

    private final String EXIT_SIGNAL = "***";

    private Scanner scanner = new Scanner(System.in);
    private Settings settings;
    private DBHandler db;

    private String currentRFID = "";
    private User currentUser = null;

    public POS() {
        // Attempt to read settings.
        if (!loadSettings()) return;

        // Initiate database Check database connection.
        db = new MySQLDBHandler(settings);
        if (!checkDB()) return;

        // Start program.
        start();
    }

    private boolean loadSettings() {
        settings = VerifySettings.readSettings();

        if (settings == null) return false;

        logger.trace("Settings loaded successfully.");
        return true;
    }

    private boolean checkDB() {
        return db.testConnection();
    }

    private void start() {
        String input;

        while (true) {
            // Attempt to read command.
            try {
                if (currentRFID.isEmpty()) {
                    System.out.println("╠════════════════════════════════════════════════════════════════════" +
                            "            --------------------------------------------------------------------");
                    System.out.println("┃ - Input card number or type command. Use /*- for help, "+ EXIT_SIGNAL +" to exit.");
                }
                else {
                    System.out.println("┃ - Input amount to withdraw or deposit (+).");
                }
                System.out.print("> ");
                input = scanner.nextLine();
            } catch (NoSuchElementException e) {
                // Occurs when the program is interrupted. Essentially means quit.
                break;
            }

            List<String> response = handleInput(input);

            for (String s : response) {
                if (s.startsWith("!")) {
                    resetCurrentInfo();
                    System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                    System.out.println(s);
                    System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                }
                else {
                    System.out.println("┃ " + s);
                }
            }

        }

        exit_application();

    }

    private List<String> handleInput(String input) {
        List<String> response = new ArrayList<>();

        if (input == null) {
            return response;
        }

        switch (input) {
            // If exit signal was input, quit.
            case EXIT_SIGNAL:
                exit_application();
                break;
            case "":
                if (currentRFID.isEmpty()) {
                    response.add("Not valid input.");
                }
                else {
                    response.add("Not valid input. Transaction aborted.");
                }
                currentRFID = "";
                break;
            default:
                if (input.startsWith("/")) {
                    response = handleCommand(input);
                }
                else {
                    response = handleTransactions(input);
                }
        }

        return response;
    }

    private List<String> handleCommand(String input) {
        List<String> response = new ArrayList<>();

        switch (input) {
            case "/*-":
                response.add("Help...");
                break;
            default:
                response.add("Unrecognized command. Use /*- for help.");
        }

        return response;
    }

    public List<String> handleTransactions(String input) {
        List<String> response = new ArrayList<>();

        if (isRFID(input)) {
            if (!currentRFID.isEmpty()) {
                if (input.equals(currentRFID)) {
                    response.add(String.format("%s Read again. Ignoring..", currentRFID));
                    return response;
                }
                else {
                    response.add("New RFID registered. Transaction aborted.");
                    response.add("--------------------------------------------------------------------");
                }
            }

            currentRFID = input;
            try {
                currentUser = db.get_or_create(currentRFID);
            } catch (SQLException e) {
                response.add("! SQL error occurred while trying to retrieve user from the database. Check your connection.");
                return response;
            }

            if (currentUser != null) {
                response.add("");
                response.add("    ┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓");
                response.add("    ┃ Read RFID " + currentRFID);
                response.add("    ┣━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┫");
                response.add(String.format("    ┃ Last used: %s", currentUser.getLastUsed()));
                response.add(String.format("    ┃ Balance: %d", currentUser.getCredit()));
                response.add("    ┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛");
                response.add("    ┗-------------------------------------");
                response.add("");
            }
        }
        else {
            if (currentUser == null) {
                response.add("No RFID registered. Transaction aborted.");
                return response;
            }

            // At this point we assume the transaction is to deposit or deduct money.
            boolean is_deposit = false;
            if (input.startsWith("+")) {
                is_deposit = true;
                input = input.substring(1);
            }

            try {
                int amount = Integer.parseInt(input);
                int new_balance;

                if (is_deposit) {
                    // Make the deposit
                    try {
                        db.deposit(currentRFID, amount);
                    } catch (SQLException e) {
                        response.add("! SQL error occurred while attempting to make deposit. Transaction aborted.");
                        return response;
                    }

                    new_balance = currentUser.getCredit() + amount;
                    response.add("");
                    response.add(String.format("Deposited %d into RFID '%s'. New balance: %d", amount, currentRFID, new_balance));
                    response.add("");
                }
                else {
                    if (amount > currentUser.getCredit()) {
                        response.add("! The balance on this card isn't high enough for that purchase.");
                        return response;
                    }
                    else {
                        try {
                            db.deduct(currentRFID, amount);
                        } catch (SQLException e) {
                            response.add("! SQL error occurred while trying to withdraw money from this account. Transaction aborted.");
                            return response;
                        }
                        new_balance = currentUser.getCredit() - amount;
                        response.add("");
                        response.add(String.format("Withdrew %d from RFID '%s'. New balance: %d", amount, currentRFID, new_balance));
                        response.add("");
                    }
                }

                try {
                    db.transaction(currentUser.getId(), amount, is_deposit, new_balance);
                } catch (SQLException e) {
                    response.add("! SQL error occurred while trying to store log this transaction. The money has been deposited.");
                    return response;
                }

                resetCurrentInfo();
            } catch (NumberFormatException e) {
                logger.error("Not a number.");
            }
        }

        return response;
    }

    private void exit_application() {
        logger.trace("Exited RFID POS application.");
        System.exit(0);
    }

    private void resetCurrentInfo() {
        currentRFID = "";
        currentUser = null;
    }

    private List<String> drawBow(List<String> lines) {
        List<String> boxedLines = new ArrayList<>();

        boxedLines.add("");


        return lines;
    }

    /**
     * This method evaluates what an RFID is.
     * Currently string of a-z 0-9 of length 8 or longer is considered and RFID.
     *
     * @param s Input sting to be checked
     * @return true if the string matched the parameters for RFID.
     */
    private boolean isRFID(String s) {
        return s.matches("[a-zA-Z0-9]{8,}");
    }

    public static void main(String[] args) {
        logger.trace("Starting RFID POS application.");
        new POS();
    }

}
