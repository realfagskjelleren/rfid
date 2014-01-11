package org.ntnu.realfagskjelleren.rfid.helpers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

/**
 * @author Håvard Slettvold
 */
public class JSONStorage {

    private static Logger logger = LogManager.getLogger(JSONStorage.class.getName());

    /* Data load and save */

    public static boolean save(String filename, Object object) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        final String json = gson.toJson(object);
        try {
            SimpleIO.writeToFile(filename, json);
            return true;
        } catch (IOException e) {
            logger.error("Failed to save JSON to "+ filename +".", e.getCause());
            return false;
        }
    }

    public static Object load(String filename, Class c) {
        String json = null;
        try {
            json = SimpleIO.readFileAsString(filename);
        } catch (IOException e) {
            logger.error("Failed to load JSON from "+ filename +".", e.getCause());
        }

        if (json != null) {
            Gson gson = new Gson();
            Object result = gson.fromJson(json, c);
            return result;
        }
        return null;
    }


}