package org.ntnu.realfagskjelleren.rfid.db.model;

import java.sql.SQLException;
import java.util.List;

/**
 * @author Håvard Slettvold
 */
public interface DBHandler {

    public boolean testConnection();
    public boolean createDatabase();

    public User get_or_create(String rfid);
    public User get_user(String ecc);
    public void update_user_rfid(int userid, String rfid);
    public boolean rfid_exists(String rfid);
    public boolean ecc_exists(int ecc);

    public int credit(String rfid);
    public void deposit(String rfid, int value);
    public void deduct(String rfid, int value);

    public List<User> getAllUsers();
    public List<Transaction> getTransactions(int... amount);

    public void transaction(int user_id, int value, boolean is_deposit, int new_balance);
    public void log(String message);
}