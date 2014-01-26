package org.ntnu.realfagskjelleren.rfid;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ntnu.realfagskjelleren.rfid.db.model.DBHandler;
import org.ntnu.realfagskjelleren.rfid.db.model.Transaction;
import org.ntnu.realfagskjelleren.rfid.db.model.User;
import org.ntnu.realfagskjelleren.rfid.db.model.Version;
import org.ntnu.realfagskjelleren.rfid.db.mysqlimpl.MySQLDBHandler;
import org.ntnu.realfagskjelleren.rfid.settings.Settings;
import org.ntnu.realfagskjelleren.rfid.settings.VerifySettings;
import org.ntnu.realfagskjelleren.rfid.ui.consoleimpl.ConsoleUI;
import org.ntnu.realfagskjelleren.rfid.ui.model.UI;

import java.io.File;
import java.sql.SQLException;
import java.util.*;

/**
 * This POS system is made to work with an RFID scanner and numeric pad.
 * Any additional keyboard is not necessary.
 *
 * Other forms of UI, like swing (god forbid), can be implemented via the UI interface.
 * Some tinkering may be needed to allow settings to choose which for m of UI is wanted.
 *
 * Other database solutions may be implemented via the DBHandler interface.
 * As with the UI, some tinkering is needed to make that work dynamically.
 *
 * @author Håvard Slettvold
 */
public class POS {

    private static Logger logger = LogManager.getLogger(POS.class.getName());

    private final String RFID_DATABASE_VERSION = "2.0";

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

        // Attempt to find old data to import into new system
        if (!attemptImport()) exit_application();

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

        Version version = db.getVersion();
        if (version == null) {
            // If version is null, the table was newly created and there is no version yet.
            if (!db.setVersion(RFID_DATABASE_VERSION)) return false;
            logger.debug("Detected new database. Database version set to '"+ RFID_DATABASE_VERSION +"'.");
        }
        else if (!RFID_DATABASE_VERSION.equals(version.toString())) {
            /*
                This case should not happen to anyone yet.

                The plan for having versions is to be able to upgrade existing databases to a new
                schema without requiring the users of this system to actually perform any operations.
                Currently there are no need for such migrations, but considering future development
                it was added.
             */
            logger.error("You have an older version of the DB. Ask realfagskjelleren how to proceed.");
            return false;
        }

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

        logger.trace("Started ConsoleUI with width "+consoleWidth+".");
        return true;
    }

    private boolean attemptImport() {
        File file = new File("pos.unread.db");

        // If there is no old DB file, skip the whole import procedure.
        if (!file.exists()) return true;

        try {
            int userCount = db.getUserCount();

            if (userCount > 0) {
                ui.error("This system already has users stored in it.");

                boolean confirmImport = ui.takeConfirmation("Are you really sure you wish to import old data from pos.unread.db? Make sure you take a backup first!");

                if (!confirmImport) {
                    ui.display("To avoid this popping up every time you start this system, delete 'pos.unread.db' from your RFID folder.");
                    return true;
                }
            }
        } catch (SQLException e) {
            ui.error("Failed to obtain user count. Check database connection.");
            return false;
        }

        ui.display("Attempting to import users..");

        int response = ConvertDataFromRFID1.convert(db);
        switch (response) {
            case 0:
                // Nothing to import, so display nothing.
                return true;
            case 1:
                if (file.renameTo(new File("pos.read.db"))) {
                    ui.display("Import successful!");
                    return true;
                }
                else {
                    ui.error("Finished importing old data from 'pos.unread.db', but could not rename it. Please manually do so and relaunch the application.");
                    return false;
                }
            case 2:
                ui.error("Attempt to convert old database has failed. Check the logs for more information.");
                return false;
        }

        return false;
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
                resetCurrentInfo();
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
        String[] args = input.split(" ");

        switch (args[0]) {
            case "/help":
                ui.showHelp();
                break;
            case "/transactions":
                int transactionsToShow = 10;

                if (args.length > 1) {
                    try {
                        transactionsToShow = Integer.parseInt(args[1]);
                    } catch (NumberFormatException e) {
                        ui.display("Invalid number of transactions, showing 10.");
                    }
                }

                try {
                    List<Transaction> transactions;
                    if (currentUser == null) {
                        transactions = db.getTransactions(transactionsToShow);
                    }
                    else {
                        transactions = db.getTransactions(currentUser.getId(), transactionsToShow);
                    }

                    if (transactions.isEmpty()) {
                        ui.display("No transactions!");
                    }
                    else {
                        ui.showTransactions(transactions);
                    }
                } catch (SQLException e) {
                    ui.error("SQL error occurred while trying to retrieve transactions from the database. Check your connection.");
                    return;
                }
                break;
            case "/users":
                try {
                    List<User> users = db.getAllUsers();
                    if (users.isEmpty()) {
                        ui.display("No users!");
                    }
                    else {
                        ui.showUsers(users);
                    }
                } catch (SQLException e) {
                    ui.error("SQL error occurred while trying to retrieve users from the database. Check your connection.");
                    return;
                }
                break;
            case "/stats":
                int hours = 15;

                List<String> topTen;
                List<String> topTenTable = new ArrayList<>();

                try {
                    if (args.length > 1) {
                        hours = Integer.parseInt(args[1]);
                        topTen = db.getTopTenFromLastHours(hours);
                    }
                    else {
                        topTen = db.getTopTen();
                    }

                    int numberOfUsers = db.getUserCount();
                    int totalValue = db.getTotalValue();

                    List<String> stats = Arrays.asList(
                            "Displaying stats for the last "+hours+" hours.",
                            "",
                            "Total number of registered users: "+ numberOfUsers,
                            "Total value of all users: "+totalValue
                    );

                    topTenTable.add("RFID | Money spent");
                    topTenTable.add("===");
                    for (String line : topTen) {
                        topTenTable.add(line);
                    }

                    ui.showStats(stats, topTenTable);

                } catch (NumberFormatException e) {
                    ui.display("Invalid number of hours, showing 15.");
                } catch (SQLException e) {
                    ui.error("SQL error occurred while trying to create the stats. Check your connection.");
                }
                break;
            default:
                ui.invalidCommand();
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
            }

            try {
                int amount = Integer.parseInt(input);

                if (amount >= 1000) {
                    if (!ui.takeConfirmation("The amount which was input is very high, are you sure it is correct?")) {
                        return;
                    }
                }

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
                    ui.error("SQL error occurred while trying to store log this transaction. The balance of the account has been updated.");
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
