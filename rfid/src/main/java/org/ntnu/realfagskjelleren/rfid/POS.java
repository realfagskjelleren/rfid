package org.ntnu.realfagskjelleren.rfid;


import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.MarkerManager;
import org.ntnu.realfagskjelleren.rfid.db.migrations.ConvertDataFromRFID1;
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
import java.text.DateFormat;
import java.text.SimpleDateFormat;
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

    private final String RFID_VERSION = "2.1";

    private Settings settings;
    private DBHandler db;
    private UI ui;

    private User currentUser = null;

    public POS() {
        // Attempt to read settings
        if (!loadSettings()) exit_application();

        // Initiate database Check database connection
        if (!initiateDB()) exit_application();

        // Start the UI
        if (!initiateUI()) exit_application();

        // Attempt to find old data to import into new system
        if (!attemptImport()) exit_application();

        // Run migrations
        if (!runMigrations()) exit_application();

        // Launch the updater
        if (settings.getAutomaticUpdates()) new Updater(ui, db);

        // Start the RFID scanner app
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
            if (!db.setVersion("2.0")) return false;
            logger.debug("Detected new database. Database version set to '2.0'.");
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
        ui.showWelcomeMessage(db.getVersion().toString());

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

    private boolean runMigrations() {

        Version version = db.getVersion();

        switch (version.getVersion()) {
            case "2.0":
                db.setVersion("2.1");
        }

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

            logger.debug(MarkerManager.getMarker("input"), "INCOMING: " + input);
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
                if (currentUser == null) {
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
            case "/checksum":
                if (currentUser == null) {
                    ui.display("This command requires an RFID to be scanned first.");
                    return;
                }

                int converted = ntnuChecksum(currentUser.getRfid());

                if (converted == -1) {
                    ui.error("Calculated check sum was not an integer value.");
                }
                else {
                    ui.endTransaction("This users checksum is "+converted);
                }

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
                        ui.display("Showing transactions.");
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
                        ui.display("Showing all users.");
                        ui.showUsers(users);
                    }
                } catch (SQLException e) {
                    ui.error("SQL error occurred while trying to retrieve users from the database. Check your connection.");
                    return;
                }
                break;
            case "/stats":
                int numberOfUsers = -1;
                int totalValue = -1;

                try {
                    numberOfUsers = db.getUserCount();
                    totalValue = db.getTotalValue();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
                List<String> stats = Arrays.asList(
                        "Total number of registered users: "+ numberOfUsers,
                        "Total value of all users: "+totalValue
                );

                ui.display(stats);
                break;
            case "/topTen":
                try {
                    int hours = -1;
                    List<String> topTen;

                    try {
                        if (args.length > 1) {
                            hours = Integer.parseInt(args[1]);
                        }
                    } catch (NumberFormatException e) {
                        ui.display("Invalid number of hours, showing all time statistics.");
                    }

                    if (hours > 0) topTen = db.getTopTenFromLastHours(hours);
                    else topTen = db.getTopTen();

                    List<String> topTenTable = new ArrayList<>();

                    if (topTen.size() == 0) {
                        topTenTable.add("No transactions in the past "+hours+" hours to generate a top 10 from.");
                    }
                    else {
                        topTenTable.add("RFID | Money spent");
                        topTenTable.add("===");
                        for (String line : topTen) {
                            topTenTable.add(line);
                        }
                    }

                    ui.showTable(topTenTable);
                } catch (SQLException e) {
                    ui.error("SQL error occurred while trying to create the stats. Check your connection.");
                }
                break;
            case "/updateRfid":
                if (currentUser == null) {
                    ui.display("You need an active user to update RFID on.");
                    return;
                }

                String newRFID = ui.takeInput("Scan new RFID");

                if (!isRFID(newRFID)) {
                    ui.endTransaction("Not a valid RFID, aborting RFID change.");
                    resetCurrentInfo();
                    return;
                }

                try {
                    if (db.rfidExists(newRFID)) {
                        ui.endTransaction("That RFID already exists, aborting RFID change.");
                        resetCurrentInfo();
                        return;
                    }
                } catch (SQLException ex) {
                    ui.error("Failed to check database for existing RFID.");
                }

                if (ui.takeConfirmation("Are you really sure you wish to update this RFID?")) {
                    try {
                        db.updateUserRfid(currentUser.getId(), newRFID);
                        ui.endTransaction("RFID successfully updated.");

                    } catch (SQLException e) {
                        ui.endTransaction("Failed to update RFID for user_id "+currentUser.getId());
                    }
                }
                else {
                    ui.endTransaction("Aborting RFID update.");
                }

                resetCurrentInfo();

                break;
            case "/topDays":
                int amountToShow = -1;

                if (args.length > 1) {
                    try {
                        amountToShow = Integer.parseInt(args[1]);
                    } catch (NumberFormatException e) {
                        ui.display("Invalid number to show. Showing all.");
                    }
                }

                try {
                    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
                    Date curDate = new Date();
                    // Subtract 9 hours, as POS days are from 09:00 - 08:59
                    Date date = new Date(curDate.getTime() - (9 * 3600 * 1000));

                    List<String> topDaysData = new ArrayList<>();
                    List<String> topDays = db.topDays();
                    String today = db.getSalesForDate(dateFormat.format(date));

                    topDaysData.add("# | Date | Sales");
                    topDaysData.add("===");

                    if (amountToShow > topDays.size()) {
                        ui.display("The number of days you requested is larger than the number in the database. Showing all.");
                        amountToShow = topDays.size();
                    }

                    if (amountToShow == -1) amountToShow = topDays.size();

                    for (int i=amountToShow; i > 0; i--) {
                        topDaysData.add(topDays.get(i - 1));
                    }

                    if (today != null) {
                        topDaysData.add("---");
                        topDaysData.add("Today|"+today);
                    }

                    ui.showTable(topDaysData);
                } catch (SQLException e) {
                    ui.error("SQL error occurred while trying to create the stats for top days. Check your connection.");
                }
                break;
            case "/totalSpent":
                if (currentUser == null) {
                    ui.display("This command requires an RFID to be scanned first.");
                    return;
                }
                int totalSpent = -1;

                try {
                    totalSpent = db.totalSpendings(currentUser.getRfid());

                    ui.endTransaction(String.format("Total money spent by %s: %d", currentUser.getRfid(), totalSpent));
                    resetCurrentInfo();
                } catch (SQLException e) {
                    ui.error("SQL error occurred while trying to find total spendings. Check your connection.");
                }
                break;
            case "/merge":
                if (currentUser == null) {
                    ui.display("This command requires an RFID to be scanned first.");
                    return;
                }

                ui.display(Arrays.asList(
                        "You have currently scanned the RFID: " + currentUser.getRfid(),
                        "If you really wish to merge this RFID with another, scan the other card now."
                ));

                String otherRFID = ui.takeInput("Scan RFID to merge:");

                if (otherRFID.isEmpty() || !isRFID(otherRFID)) {
                    ui.endTransaction("No RFID scanned, aborting merge.");
                    resetCurrentInfo();
                    return;
                }

                if (currentUser.getRfid().equals(otherRFID)) {
                    ui.endTransaction("The scanned RFIDs are identical, aborting merge.");
                    resetCurrentInfo();
                    return;
                }

                try {
                    if (!db.rfidExists(otherRFID)) {
                        ui.endTransaction("The scanned RFID to merge with does not exist, aborting merge.");
                        resetCurrentInfo();
                        return;
                    }
                } catch (SQLException e) {
                    ui.error("SQL error occurred when trying to check if '" + otherRFID + "' exists.");
                    return;
                }

                // Be really really sure about this.
                if (!ui.takeConfirmation("This will merge all data from RFIDs " + currentUser.getRfid() + " and " + otherRFID)) {
                    ui.endTransaction("Aborting..");
                    resetCurrentInfo();
                    return;
                }
                if (!ui.takeConfirmation("This cannot be undone, you are 100% sure you want to do this?")) {
                    ui.endTransaction("Aborting..");
                    resetCurrentInfo();
                    return;
                }
                if (!ui.takeConfirmation("Are you really, really sure?")) {
                    ui.endTransaction("Aborting..");
                    resetCurrentInfo();
                    return;
                }

                User otherUser;

                try {
                    otherUser = db.getOrCreate(otherRFID);
                } catch (SQLException e) {
                    ui.error("SQL error occurred while trying to fetch the user to merge with.");
                    return;
                }

                try {
                    int otherCredit = otherUser.getCredit();
                    db.mergeUser(currentUser.getId(), otherUser.getId());
                    db.deposit(currentUser.getRfid(), otherCredit);

                    ui.endTransaction("Successfully merged RFIDs.");
                    resetCurrentInfo();
                    return;
                } catch (SQLException e) {
                    ui.error("SQL error occurred while attempting to merge RFID '"+ otherRFID + "' into '"+ currentUser.getRfid() +"'.");
                }

                break;
            case "/prune":
                if (currentUser != null) {
                    ui.display("This command cannot be run when in a transaction block.");
                    return;
                }

                try {
                    int affectedRows = db.pruneInactiveRFIDs();

                    if (affectedRows == 0) {
                        ui.display("No RFIDs were affected.");
                    }
                    else {
                        ui.display("Removed "+affectedRows+" inactive RFIDs.");
                    }
                } catch (SQLException e) {
                    ui.error("SQL error occurred while trying to prune inactive RFIDs.");
                }
                break;
            case "/version":
                Version version = db.getVersion();

                if (version == null) ui.display("Could not get version. See logs for more info.");
                else ui.display("Current system version: "+version.getVersion());

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
            if (currentUser != null) {
                if (input.equals(currentUser.getRfid())) {
                    ui.display(String.format("%s read again. Ignoring..", currentUser.getRfid()));
                    return;
                }
                else {
                    ui.endTransaction("New RFID registered. Transaction aborted.");
                }
                resetCurrentInfo();
            }

            try {
                if (!db.rfidExists(input)) {
                    // Due to the old database format using int to store RFIDs, we have to
                    // check if the RFID matches if we remove the 0 padding on the left
                    // before getting using the getOrCreate method.
                    String nonPaddedRFID = StringUtils.stripStart(input, "0");

                    if (db.rfidExists(nonPaddedRFID)) {
                        currentUser = db.getOrCreate(nonPaddedRFID);
                        db.updateUserRfid(currentUser.getId(), input);
                    }
                }

                // Fetch it again anyway, so we get the correct object as it exists in the DB now.
                currentUser = db.getOrCreate(input);

            } catch (SQLException e) {
                ui.error("SQL error occurred while trying to retrieve user from the database. Check your connection.");
                resetCurrentInfo();
                return;
            }

            if (currentUser != null) {
                ui.startTransaction(currentUser);
            }
        }
        else if (isECC(input)) {
            // No need for a try/catch, isECC checks if it's 6 digits.
            int ecc = Integer.parseInt(input);

            try {
                currentUser = db.getUser(ecc);
            } catch (SQLException e) {
                ui.error("SQL error occurred while trying to retrieve user from the database. Check your connection.");
                resetCurrentInfo();
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

                if (amount < 0) {
                    ui.error("Negative values are not accepted.");
                    return;
                }

                if (amount >= 1000) {
                    if (!ui.takeConfirmation("The amount which was input is very high, are you sure it is correct?")) {
                        return;
                    }
                }

                int new_balance;

                if (is_deposit) {
                    // Make the deposit
                    try {
                        db.deposit(currentUser.getRfid(), amount);
                    } catch (SQLException e) {
                        ui.error("SQL error occurred while attempting to make deposit. Transaction aborted.");
                        return;
                    }

                    new_balance = currentUser.getCredit() + amount;
                    ui.endTransaction(String.format("Deposited %d into RFID '%s'. New balance: %d", amount, currentUser.getRfid(), new_balance));
                }
                else {
                    if (amount > currentUser.getCredit()) {
                        ui.error("The balance on this card isn't high enough for that purchase.");
                        return;
                    }
                    else {
                        try {
                            db.deduct(currentUser.getRfid(), amount);
                        } catch (SQLException e) {
                            ui.error("SQL error occurred while trying to withdraw money from this account. Transaction aborted.");
                            return;
                        }
                        new_balance = currentUser.getCredit() - amount;
                        ui.endTransaction(String.format("Withdrew %d from RFID '%s'. New balance: %d", amount, currentUser.getRfid(), new_balance));
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
                ui.error("Not a number.");
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
        return s.matches("^[a-zA-Z0-9]{8,}$");
    }

    private boolean isECC(String s) {
        return s.matches("\\d{6}");
    }

    public int ntnuChecksum(String rfid) {
        int int_rfid;

        try {
            int_rfid = Integer.parseInt(rfid);
        } catch (NumberFormatException e) {
            return -1;
        }

        // Make sure 32 bits are allocated every time
        String binary_rfid = StringUtils.leftPad(Integer.toBinaryString(int_rfid), 32, "0");

        // Reverse every 8 bits
        String checkSum = "";
        checkSum += StringUtils.reverse(binary_rfid.substring(0,8));
        checkSum += StringUtils.reverse(binary_rfid.substring(8,16));
        checkSum += StringUtils.reverse(binary_rfid.substring(16, 24));
        checkSum += StringUtils.reverse(binary_rfid.substring(24));

        try {
            return Integer.parseInt(checkSum, 2);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static void main(String[] args) {
        logger.trace("Starting RFID POS application.");
        new POS();
    }

}