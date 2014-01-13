package org.ntnu.realfagskjelleren.rfid.settings;

/**
 * @author Håvard Slettvold
 */
public class Settings {

    /*
     * All fields prefixed with ** are mandatory and need to be changed.
     * All fields prefixed with * are optional. The prefix is only there to see if the value
     * was generated on object creation or actually loaded from the settings.
     *
     * Prefixes for optional fields will be stripped in VerifySettings.
     *
     * The reason for this behaviour is for older settings files to receive newly implemented
     * settings without deleting the file.
     */
    private String dbUsername = "** Replace with database username";
    private String dbPassword = "** Replace with database password";
    private String dbHost = "** Replace with database host";
    private String dbName = "** Replace with name of the database";
    private String dbPort = "* 3306";
    private String consoleWidth = "* 120";

    public Settings() {
    }

    public String getDbUsername() {
        return dbUsername;
    }

    public String getDbPassword() {
        return dbPassword;
    }

    public String getDbHost() {
        return dbHost;
    }

    public String getDbName() {
        return dbName;
    }

    public String getDbPort() {
        return dbPort;
    }

    public boolean cleanOptionalFields() {
        boolean change = false;

        if (this.dbPort.startsWith("* ")) {
            this.dbPort = this.dbPort.substring(2);
            change = true;
        }
        if (this.consoleWidth.startsWith("* ")) {
            this.consoleWidth = this.consoleWidth.substring(2);
            change = true;
        }

        return change;
    }

    public String getConsoleWidth() throws NumberFormatException {
        return consoleWidth;
    }
}
