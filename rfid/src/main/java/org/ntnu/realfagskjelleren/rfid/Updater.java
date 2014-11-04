package org.ntnu.realfagskjelleren.rfid;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.MarkerManager;
import org.ntnu.realfagskjelleren.rfid.db.model.DBHandler;
import org.ntnu.realfagskjelleren.rfid.db.model.Version;
import org.ntnu.realfagskjelleren.rfid.ui.model.UI;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.Arrays;
import java.util.Date;

/**
 * @author Håvard Slettvold
 */
public class Updater implements Runnable {

    private static Logger logger = LogManager.getLogger(POS.class.getName());

    private UI ui;
    private DBHandler db;

    private static String updateURL = "http://org.ntnu.no/realfagskjellern/rfid/rfid.jar";
    private boolean updateDownloaded = false;

    public Updater(UI ui, DBHandler db) {
        this.ui = ui;
        this.db = db;
        new Thread(this).start();
    }

    private boolean hasUpdate() {
        long latestUpdate = -1L;

        try {
            URL url = new URL(updateURL);
            HttpURLConnection httpCon = (HttpURLConnection) url.openConnection();
            latestUpdate = httpCon.getLastModified();
            if (latestUpdate == 0) {
                logger.debug(MarkerManager.getMarker("updater"), "No Last-Modified header found, checking connection.");
                httpCon.connect();
                logger.debug(MarkerManager.getMarker("updater"), "Connection OK.");
            }
        } catch (MalformedURLException ex) {
            logger.debug(MarkerManager.getMarker("updater"), "Malformed URL: "+ updateURL);
            ui.error("Update URL field is improperly configured.");
            return false;
        } catch (IOException ex) {
            logger.debug(MarkerManager.getMarker("updater"), "Error occurred while attempting to open connection to update server.");
            ui.error(Arrays.asList("Attempt to connect to update server failed.", "Do you have internet?", "You can turn updates off in the config."));
            return false;
        }

        // If there's no new update, stop here.
        if (latestUpdate == 0) return false;

        Version version = db.getVersion();

        Date currentVersion = new Date(version.getExecutedOn().getTime());
        Date latestVersion = new Date(latestUpdate);

        if (latestVersion.after(currentVersion)) return true;

        return false;
    }

    private boolean downloadUpdate() {
        try {
            URL website = new URL(updateURL);
            ReadableByteChannel rbc = Channels.newChannel(website.openStream());
            FileOutputStream fos = new FileOutputStream("rfid.jar");
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
        } catch (MalformedURLException ex) {
            logger.debug(MarkerManager.getMarker("updater"), "Malformed URL: "+ updateURL);
            ui.error("Update URL field is improperly configured.");
            return false;
        } catch (FileNotFoundException ex) {
            logger.debug(MarkerManager.getMarker("updater"), "Unable to write to file.");
            return false;
        } catch (IOException ex) {
            logger.debug(MarkerManager.getMarker("updater"), "Error occurred while attempting to open connection to update server.");
            ui.error(Arrays.asList("Attempt to connect to update server failed.", "Do you have internet?", "You can turn updates off in the config."));
            return false;
        }

        return true;
    }

    @Override
    public void run() {
        logger.debug(MarkerManager.getMarker("updater"), "Starting update manager.");

        while (!updateDownloaded) {
            logger.debug(MarkerManager.getMarker("updater"), "Checking for updates.");

            if (hasUpdate()) {
                logger.debug(MarkerManager.getMarker("updater"), "Update found. Downloading update.");
                if (!downloadUpdate()) ui.error("Failed to download update.");
                else {
                    ui.display("New update has been downloaded! Restart application for update to take effect.");
                    updateDownloaded = true;
                }
            }

            try {
                synchronized (this) {
                    // Only poll once per day
                    this.wait(86400000L);
                }
            } catch (InterruptedException e) {
                logger.debug(MarkerManager.getMarker("updater"), "Updater was interupted.");
            }

        }
    }
}
