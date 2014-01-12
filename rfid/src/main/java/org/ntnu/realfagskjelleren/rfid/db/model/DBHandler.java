package org.ntnu.realfagskjelleren.rfid.db.model;

import java.sql.SQLException;
import java.util.List;

/**
 * @author Håvard Slettvold
 */
public interface DBHandler {

    public boolean testConnection();
    public boolean createDatabase();

    public User get_or_create(String rfid) throws SQLException;
    public User get_user(int ecc) throws SQLException;
    public void update_user_rfid(int userid, String rfid) throws SQLException;
    public boolean rfid_exists(String rfid) throws SQLException;
    public boolean ecc_exists(int ecc) throws SQLException;

    public void deposit(String rfid, int value) throws SQLException;
    public void deduct(String rfid, int value) throws SQLException;

    public List<User> getAllUsers() throws SQLException;
    public List<Transaction> getTransactions(int... amount) throws SQLException;

    public void transaction(int user_id, int value, boolean is_deposit, int new_balance) throws SQLException;
    public void log(String message) throws SQLException;
}