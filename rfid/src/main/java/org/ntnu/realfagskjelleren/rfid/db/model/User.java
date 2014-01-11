package org.ntnu.realfagskjelleren.rfid.db.model;

import java.sql.Timestamp;

/**
 * @author Håvard Slettvold
 */
public class User {

    private int id;
    private int ecc;
    private String rfid;
    private int credit;
    private boolean isStaff;
    private Timestamp lastUsed;

    public User(int id, String rfid, boolean isStaff, int credit, Timestamp lastUsed) {
        this.id = id;
        this.rfid = rfid;
        this.isStaff = isStaff;
        this.credit = credit;
        this.lastUsed = lastUsed;
    }

    public int getId() {
        return id;
    }

    public int getEcc() {
        return ecc;
    }

    public String getRfid() {
        return rfid;
    }

    public void setRfid(String rfid) {
        this.rfid = rfid;
    }

    public int getCredit() {
        return credit;
    }

    public void setCredit(int credit) {
        this.credit = credit;
    }

    public Timestamp getLastUsed() {
        return lastUsed;
    }

}
