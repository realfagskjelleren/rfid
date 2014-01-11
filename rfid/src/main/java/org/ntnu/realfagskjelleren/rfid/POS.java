package org.ntnu.realfagskjelleren.rfid;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ntnu.realfagskjelleren.rfid.db.model.DBHandler;
import org.ntnu.realfagskjelleren.rfid.db.model.User;
import org.ntnu.realfagskjelleren.rfid.db.mysqlimpl.MySQLDBHandler;
import org.ntnu.realfagskjelleren.rfid.settings.Settings;
import org.ntnu.realfagskjelleren.rfid.settings.VerifySettings;

import java.sql.*;
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

    private final String EXIT_SIGNAL = "0000";

    private Scanner scanner = new Scanner(System.in);
    private Settings settings;
    private DBHandler db;

    private String currentRFID = "";

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
                    System.out.println("\nInput card number or type command. Use /*- for help. 0000 to exit.");
                }
                input = scanner.nextLine();
            } catch (NoSuchElementException e) {
                // Occurs when the program is interrupted. Basically means quit.
                break;
            }

            List<String> response = handleInput(input);

            for (String s : response) {
                System.out.println(s);
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
                    response.add("----- Not valid input. -----");
                }
                else {
                    response.add("----- Not valid input. Transaction aborted. -----");
                }
                currentRFID = "";
                break;
            default:
                response = handleTransactions(input);
        }

        return response;
    }

    public List<String> handleTransactions(String input) {
        List<String> response = new ArrayList<>();

        User currentUser = null;

        if (isRFID(input)) {
            if (!currentRFID.isEmpty()) {
                response.add("----- New RFID registered. Transaction aborted. -----");
            }

            currentRFID = input;
            currentUser = db.get_or_create(currentRFID);

            if (currentUser != null) {
                response.add("+++++ Read RFID " + currentRFID + " +++++");

                response.add(String.format("Last used: %s", currentUser.getLastUsed()));
                response.add(String.format("Balance: %d", currentUser.getCredit()));
            }
        }
        else {
            if (currentUser == null) {
                response.add("----- No RFID registered. Transaction aborted. ------");
                return response;
            }

            try {
                int i = Integer.parseInt(input);
                System.out.println("Parsed int: " + i);

                db.deposit(currentRFID, i);
                db.transaction(currentUser.getId(), i, true, currentUser.getCredit()+i);
            } catch (NumberFormatException e) {

            }
        }

        return response;
    }

    private void exit_application() {
        logger.trace("Exited RFID POS application.");
        System.exit(0);
    }

    private boolean isRFID(String s) {
        return s.matches("\\d{8,}");
    }

    public static void main(String[] args) {
        logger.trace("Starting RFID POS application.");
        new POS();
    }

}
