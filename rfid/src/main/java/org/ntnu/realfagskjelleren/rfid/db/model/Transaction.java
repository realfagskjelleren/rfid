package org.ntnu.realfagskjelleren.rfid.db.model;

import java.sql.Timestamp;

/**
 * @author Håvard Slettvold
 */
public class Transaction {

    private int user_id;
    private int value;
    private int new_balance;
    private boolean is_deposit;
    private Timestamp date;
    private String rfid;

    public Transaction(int user_id, String rfid, int value, int new_balance, boolean is_deposit, Timestamp date) {
        this.user_id = user_id;
        this.rfid = rfid;
        this.value = value;
        this.new_balance = new_balance;
        this.is_deposit = is_deposit;
        this.date = date;
    }

    public int getUserid() {
        return user_id;
    }

    public String getRfid() {
        return rfid;
    }

    public int getValue() {
        return value;
    }

    public int getNew_balance() {
        return new_balance;
    }

    public boolean isDeposit() {
        return is_deposit;
    }

    public Timestamp getDate() {
        return date;
    }

}
