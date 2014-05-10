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

    public User get_or_create(String rfid) throws SQLException;
    public User get_user(int ecc) throws SQLException;
    public void update_user_rfid(int user_id, String rfid) throws SQLException;
    public boolean rfid_exists(String rfid) throws SQLException;
    public boolean ecc_exists(int ecc) throws SQLException;

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

    // Only meant to be used with the import of old sqlite DBs. Remove this when it is no longer needed.
    public boolean create_user_from_previous_db(String rfid, int credit, Timestamp created) throws SQLException;
    public void transaction(int user_id, int value, boolean is_deposit, int new_balance, Timestamp timestamp) throws SQLException;
}
