package org.ntnu.realfagskjelleren.rfid.helpers;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * This file contains simple file handling methods.
 *
 * Can create files and folders, and also read from and write to files, ensuring their existence first.
 *
 * @author Håvard Slettvold
 */
public class SimpleIO {

    private static List<String> findFolders(String folder) {
        List<String> folders = new ArrayList<String>();

        String foldername = "";
        for (char c : folder.toCharArray()) {
            foldername += c;
            if (c == '\\' || c == '/') {
                folders.add(foldername);
            }
        }

        return folders;
    }

    private static void createFolders(String folder) {
        List<String> folders = findFolders(folder);

        for (String f : folders) {
            File file = new File(f);
            if (!file.exists()) {
                file.mkdir();
            }
        }
    }

    public static void createFile(String filename) throws IOException {
        createFolders(filename);
        File file = new File(filename);

        if (file.exists()) {
            return;
        } else {
            file.createNewFile();
        }
    }

    public static void writeToFile(String filename, String line) throws FileNotFoundException, IOException {
        File file = new File(filename);

        if (!file.exists()) {
            createFile(filename);
        }

        FileWriter writer = new FileWriter(file);
        writer.write(line);
        writer.close();
    }

    public static String readFileAsString(String filename) throws FileNotFoundException, IOException {
        File file = new File(filename);

        if (!file.exists()) {
            file.createNewFile();
        }
        BufferedReader reader = new BufferedReader(new FileReader(file));
        StringBuffer buffer = new StringBuffer();
        String line = null;

        while ((line = reader.readLine()) != null) {
            buffer.append(line);
        }

        return buffer.toString();
    }

}
