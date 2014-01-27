package org.ntnu.realfagskjelleren.rfid.db.model.migrations;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ntnu.realfagskjelleren.rfid.db.model.DBHandler;
import org.ntnu.realfagskjelleren.rfid.db.model.User;

import java.sql.*;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author Håvard Slettvold
 */
public class ConvertDataFromRFID1 {

    private static Logger logger = LogManager.getLogger(ConvertDataFromRFID1.class.getName());

    private static String oldDbFile = "pos.unread.db";


    /**
     * Attempts to convert a DB form 1.0 format to the current format.
     *
     * @return 0 = NIL, 1 = successful conversion, 2 = error
     */
    public static int convert(DBHandler db) {

        logger.trace("Attempting to import users..");
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            logger.error("Could not find a driver for SQLite, but found an sqlite DB to convert.");
            logger.error("You should verify the driver or delete 'pos.unread.db' from the folder.");
            return 2;
        }

        try (Connection con = DriverManager.getConnection("jdbc:sqlite:pos.unread.db");
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM pos;")) {

            while (rs.next()) {
                User user = db.get_or_create(""+rs.getInt("id"));

                db.deposit(user.getRfid(), rs.getInt("credits"));
            }
        } catch (SQLException e) {
            logger.error("Something went wrong while importing users.");
            logger.error(e.getMessage(), e.getCause());
            return 2;
        }
        logger.trace("Imported users successfully.");

        logger.trace("Attempting to import transactions..");
        try (Connection con = DriverManager.getConnection("jdbc:sqlite:pos.unread.db");
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM transact;")) {

            while (rs.next()) {
                User user = db.get_or_create(""+rs.getInt("userid"));

                String input = rs.getString("input");
                int value;
                boolean is_deposit;
                int new_balance;

                if (input.startsWith("+")) {
                    value = Integer.parseInt(input.substring(1));
                    new_balance = user.getCredit() + value;
                    is_deposit = true;
                }
                else {
                    value = Integer.parseInt(input);
                    new_balance = user.getCredit() - value;
                    is_deposit = false;
                }

                Date date = null;
                String dateString = "";

                try {
                    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    dateString = rs.getString("date");
                    date = dateFormat.parse(dateString);
                    long time = date.getTime();
                    db.transaction(user.getId(), value, is_deposit, new_balance, new Timestamp(time));
                } catch (ParseException e) {
                    logger.error("Failed to parse date: "+dateString);
                    return 2;
                }
            }
        } catch (SQLException e) {
            logger.error("Something went wrong while importing transactions.");
            logger.error(e.getMessage(), e.getCause());
            return 2;
        }
        logger.trace("Imported transactions successfully.");

        return 1;
    }

}
