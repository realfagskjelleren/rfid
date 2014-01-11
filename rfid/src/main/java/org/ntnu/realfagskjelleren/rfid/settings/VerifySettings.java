package org.ntnu.realfagskjelleren.rfid.settings;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ntnu.realfagskjelleren.rfid.helpers.JSONStorage;
import org.ntnu.realfagskjelleren.rfid.helpers.SimpleIO;

import java.io.IOException;

/**
 * @author Håvard Slettvold
 */
public class VerifySettings {

    private static Logger logger = LogManager.getLogger(JSONStorage.class.getName());

    private static final String SETTINGS_FOLDER = "settings/";
    private static final String SETTINGS_FILE = SETTINGS_FOLDER + "rfid.conf";

    private static Settings settings;

    public static Settings readSettings() {
        settings = (Settings) JSONStorage.load(SETTINGS_FILE, Settings.class);

        if (settings == null) {
            createSettings();
            return null;
        }

        // If these fields aren't filled out the application will not work.
        if (settings.getDbUsername().startsWith("** ") ||
                settings.getDbPassword().startsWith("** ") ||
                settings.getDbHost().startsWith("** ") ||
                settings.getDbName().startsWith("** ")
                ) {
            updateSettings();
            return null;
        }

        // If optional values still have their default, make sure they are stripped and settings rewritten.
        if (settings.cleanOptionalFields()) {
            JSONStorage.save(SETTINGS_FILE, settings);
            logger.debug("Uncleaned optional value detected. Assuming updated settings file. Saving.");
        }

        return settings;
    }

    private static void updateSettings() {
        JSONStorage.save(SETTINGS_FILE, settings);
        logger.error("Some fields were not filled out. Please fill out at least all fields prefixed with '**' in '" + SETTINGS_FILE + "'.");
    }

    private static void createSettings() {
        Settings newSettings = new Settings();
        JSONStorage.save(SETTINGS_FILE, newSettings);
        logger.error("No settings found for RFID application. Please fill out '" + SETTINGS_FILE + "'.");
    }

}
