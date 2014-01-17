package org.ntnu.realfagskjelleren.rfid.db.model;

import java.sql.Timestamp;

/**
 * @author Håvard Slettvold
 */
public class Version {

    private String version;
    private Timestamp executedOn;

    public Version(String version, Timestamp executedOn) {
        this.version = version;
        this.executedOn = executedOn;
    }

    public String getVersion() {
        return version;
    }

    public Timestamp getExecutedOn() {
        return executedOn;
    }

    @Override
    public String toString() {
        return this.version;
    }
}
