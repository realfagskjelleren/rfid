package org.ntnu.realfagskjelleren.rfid.ui.model;

import org.ntnu.realfagskjelleren.rfid.db.model.User;

import java.util.List;

/**
 * Interface for UI interaction.
 *
 * The default UI in this version is the console UI, but the interface should also
 * support other UI options, such as JOptionPane.
 *
 * @author Håvard Slettvold
 */
public interface UI {

    public void showWelcomeMessage();
    public void showHelp();

    public String takeInput(boolean has_user);

    public void display(String output);
    public void display(List<String> output);
    public void error(String error);
    public void error(List<String> error);
    public void startTransaction(User user);
    public void endTransaction(String output);
    public void endTransaction(List<String> output);

}
