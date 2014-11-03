package org.ntnu.realfagskjelleren.rfid.db.model;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

/**
 * @author Håvard Slettvold
 */
public interface DBHandler {

    public boolean testConnection();
    public boolean createDatabase();
    public Version getVersion();
    public boolean setVersion(String version);

    public User getOrCreate(String rfid) throws SQLException;
    public User getUser(int ecc) throws SQLException;
    public void updateUserRfid(int user_id, String rfid) throws SQLException;
    public boolean rfidExists(String rfid) throws SQLException;
    public boolean eccExists(int ecc) throws SQLException;
    public boolean mergeUser(int toUser, int fromUser) throws SQLException;

    public void deposit(String rfid, int value) throws SQLException;
    public void deduct(String rfid, int value) throws SQLException;

    public List<User> getAllUsers() throws SQLException;
    public int getTotalValue() throws SQLException;
    public int getUserCount() throws SQLException;
    public List<Transaction> getTransactions(int amount) throws SQLException;
    public List<Transaction> getTransactions(int user_id, int amount) throws SQLException;
    public List<Transaction> getTransactionsFromLastHours(int hours) throws SQLException;

    public String getSalesForDate(String date) throws SQLException;
    public List<String> topDays() throws SQLException;
    public int totalSpendings(String rfid) throws SQLException;
    public List<String> getTopTen() throws SQLException;
    public List<String> getTopTenFromLastHours(int hours) throws SQLException;

    public void transaction(int user_id, int value, boolean is_deposit, int new_balance) throws SQLException;

    public int pruneInactiveRFIDs() throws SQLException;

    // Only meant to be used with the import of old sqlite DBs. Remove this when it is no longer needed.
    public boolean create_user_from_previous_db(String rfid, int credit, Timestamp created) throws SQLException;
    public void transaction(int user_id, int value, boolean is_deposit, int new_balance, Timestamp timestamp) throws SQLException;
}
