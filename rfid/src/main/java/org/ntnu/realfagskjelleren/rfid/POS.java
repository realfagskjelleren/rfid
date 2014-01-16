package org.ntnu.realfagskjelleren.rfid;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ntnu.realfagskjelleren.rfid.db.model.DBHandler;
import org.ntnu.realfagskjelleren.rfid.db.model.User;
import org.ntnu.realfagskjelleren.rfid.db.mysqlimpl.MySQLDBHandler;
import org.ntnu.realfagskjelleren.rfid.settings.Settings;
import org.ntnu.realfagskjelleren.rfid.settings.VerifySettings;
import org.ntnu.realfagskjelleren.rfid.ui.consoleimpl.ConsoleUI;
import org.ntnu.realfagskjelleren.rfid.ui.model.UI;

import java.sql.SQLException;

/**
 * This POS system is made to work with an RFID scanner and numeric pad.
 * Any additional keyboard is not necessary.
 *
 * @author Håvard Slettvold
 */
public class POS {

    private static Logger logger = LogManager.getLogger(POS.class.getName());

    private Settings settings;
    private DBHandler db;
    private UI ui;

    private String currentRFID = "";
    private User currentUser = null;

    public POS() {
        // Attempt to read settings.
        if (!loadSettings()) exit_application();

        // Initiate database Check database connection.
        if (!initiateDB()) exit_application();

        // Start the UI
        if (!initiateUI()) exit_application();

        // Start program.
        start();
    }

    /**
     * Loads settings for the POS. This is done in it's own class to perform some validations on the
     * data in the settings file.
     *
     * This is done to discover older version of settings files and to make sure values are valid.
     *
     * @return true if settings checks out
     */
    private boolean loadSettings() {
        settings = VerifySettings.readSettings();

        if (settings == null) return false;

        logger.trace("Settings loaded successfully.");
        return true;
    }

    /**
     * Attempts to setup and test the connection to the database.
     * Default implementation is MySQL.
     *
     * @return true if the database connection was successful and tables exist
     */
    private boolean initiateDB() {
        db = new MySQLDBHandler(settings);
        logger.trace("Database handler created, checking connection ...");

        if (!db.testConnection()) return false;

        // Attempt to create DB. The DBHandler should evaluate whether or not to do this.
        if (!db.createDatabase()) return false;

        logger.trace("Database connection successful.");
        return true;
    }

    /**
     * Initiates the UI.
     * Default is the ConsoleUI.
     *
     * @return true if everything loaded correctly
     */
    private boolean initiateUI() {
        int consoleWidth;

        try {
            consoleWidth = Integer.parseInt(settings.getConsoleWidth());
        } catch (NumberFormatException e) {
            logger.error("Setting for console width needs to be an integer.");
            return false;
        }

        ui = new ConsoleUI(consoleWidth);
        ui.showWelcomeMessage();
        logger.trace("UI started.");

        logger.trace("Started UI with width "+consoleWidth+".");
        return true;
    }

    /**
     * Contains the while loop that keeps the program running.
     */
    private void start() {
        String input;

        while (true) {
            // Attempt to read command.
            input = ui.takeInput();

            if (input == null) break;

            handleInput(input);
        }

        exit_application();
    }

    /**
     * Handles input for the POS.
     *
     * @param input
     */
    private void handleInput(String input) {
        if (input == null) {
            return;
        }

        switch (input) {
            // If exit signal was input, quit.
            case "exit":
                exit_application();
                break;
            case "":
                if (currentRFID.isEmpty()) {
                    ui.display("Not valid input.");
                }
                else {
                    ui.endTransaction("Not valid input. Transaction aborted.");
                }
                currentRFID = "";
                currentUser = null;
                break;
            default:
                if (input.startsWith("/")) {
                    handleCommand(input);
                }
                else {
                    handleTransactions(input);
                }
        }
    }

    /**
     * This method should handle commands only.
     *
     * @param input
     */
    private void handleCommand(String input) {
        switch (input) {
            case "/*-":
                ui.showHelp();
                break;
            case "/1":
                try {
                    if (currentUser == null) {
                        ui.showTransactions(db.getTransactions(10));
                    }
                    else {
                        ui.showTransactions(db.getTransactions(currentUser.getId(), 10));
                    }
                } catch (SQLException e) {
                    ui.error("SQL error occurred while trying to retrieve transactions from the database. Check your connection.");
                    return;
                }
                break;
            default:
                ui.display("Unrecognized command. Use /*- for help.");
        }
    }

    /**
     * Any input received in this method will be part of transactions.
     *
     * @param input
     */
    public void handleTransactions(String input) {
        if (isRFID(input)) {
            if (!currentRFID.isEmpty()) {
                if (input.equals(currentRFID)) {
                    ui.display(String.format("%s Read again. Ignoring..", currentRFID));
                    return;
                }
                else {
                    ui.endTransaction("New RFID registered. Transaction aborted.");
                }
            }

            currentRFID = input;
            try {
                currentUser = db.get_or_create(currentRFID);
            } catch (SQLException e) {
                ui.error("SQL error occurred while trying to retrieve user from the database. Check your connection.");
                return;
            }

            if (currentUser != null) {
                ui.startTransaction(currentUser);
            }
        }
        else {
            if (currentUser == null) {
                ui.display("No transaction. Not a valid command.");
                return;
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
                        ui.error("SQL error occurred while attempting to make deposit. Transaction aborted.");
                        return;
                    }

                    new_balance = currentUser.getCredit() + amount;
                    ui.endTransaction(String.format("Deposited %d into RFID '%s'. New balance: %d", amount, currentRFID, new_balance));
                }
                else {
                    if (amount > currentUser.getCredit()) {
                        ui.error("The balance on this card isn't high enough for that purchase.");
                        return;
                    }
                    else {
                        try {
                            db.deduct(currentRFID, amount);
                        } catch (SQLException e) {
                            ui.error("SQL error occurred while trying to withdraw money from this account. Transaction aborted.");
                            return;
                        }
                        new_balance = currentUser.getCredit() - amount;
                        ui.endTransaction(String.format("Withdrew %d from RFID '%s'. New balance: %d", amount, currentRFID, new_balance));
                    }
                }

                try {
                    db.transaction(currentUser.getId(), amount, is_deposit, new_balance);
                } catch (SQLException e) {
                    ui.error("SQL error occurred while trying to store log this transaction. The money has been deposited.");
                    return;
                }

                resetCurrentInfo();
            } catch (NumberFormatException e) {
                logger.error("Not a number.");
            }
        }
    }

    /**
     * Exists the application with logging trigger.
     */
    private void exit_application() {
        logger.trace("Exited RFID POS application.");
        System.exit(0);
    }

    /**
     * Just wipes all stored information for current transactions.
     */
    private void resetCurrentInfo() {
        currentRFID = "";
        currentUser = null;
    }

    /**
     * This method evaluates what an RFID is.
     * Currently string of a-z 0-9 of length 8 or longer is considered an RFID.
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
