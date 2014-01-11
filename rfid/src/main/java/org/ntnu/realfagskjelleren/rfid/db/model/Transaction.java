package org.ntnu.realfagskjelleren.rfid.db.model;

/**
 * @author Håvard Slettvold
 */
public class Transaction {

    private int userid;
    private int value;
    private boolean is_deposit;
    private String date;

    public Transaction(int userid, int value, boolean is_deposit, String date) {
        this.userid = userid;
        this.value = value;
        this.is_deposit = is_deposit;
        this.date = date;
    }

    public int getUserid() {
        return userid;
    }

    public int getValue() {
        return value;
    }

    public boolean isIs_deposit() {
        return is_deposit;
    }

    public String getDate() {
        return date;
    }

}
