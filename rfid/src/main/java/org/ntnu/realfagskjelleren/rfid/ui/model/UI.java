package org.ntnu.realfagskjelleren.rfid.ui.model;

import org.ntnu.realfagskjelleren.rfid.db.model.Transaction;
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

    public void showWelcomeMessage(String version);
    public void showHelp();
    public void showVersion(String version);
    public void invalidCommand();

    public String takeInput();
    public String takeInput(String question);
    public boolean takeConfirmation(String output);

    public void display(String output);
    public void display(List<String> output);
    public void showTable(List<String> tableData);
    public void error(String error);
    public void error(List<String> errors);
    public void startTransaction(User user);
    public void endTransaction(String output);
    public void endTransaction(List<String> output);

    public void showTransactions(List<Transaction> transactions);
    public void showUsers(List<User> users);

}
