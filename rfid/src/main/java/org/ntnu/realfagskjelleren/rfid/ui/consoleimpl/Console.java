package org.ntnu.realfagskjelleren.rfid.ui.consoleimpl;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;

/**
 * This class handles output for the ConsoleUI.
 * Special handling for this is needed as UTF-8 output in Windows is unreliable
 * due to a broken unicode page.
 *
 * @author Håvard Slettvold
 */
public class Console {

    private Kernel32 INSTANCE = null;

    public Console() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.startsWith("win")) {
            INSTANCE = (Kernel32) Native.loadLibrary("kernel32", Kernel32.class);
        }
    }

    public interface Kernel32 extends StdCallLibrary {
        public Pointer GetStdHandle(int nStdHandle);

        public boolean WriteConsoleW(Pointer hConsoleOutput, char[] lpBuffer, int nNumberOfCharsToWrite,
                                     IntByReference lpNumberOfCharsWritten, Pointer lpReserved);
    }

    public void print(String message) {
        if (!attemptWindowsprint(message)) {
            System.out.print(message);
        }
    }

    public void println(String message) {
        if (attemptWindowsprint(message)) {
            System.out.println();
        }
        else {
            System.out.println(message);
        }
    }

    /**
     * Attempts to print text to the Windows console.
     *
     * @param message Message to print
     * @return True if text was printed to a windows console
     */
    private boolean attemptWindowsprint(String message) {
        boolean successful = false;

        if (INSTANCE != null) {
            Pointer handle = INSTANCE.GetStdHandle(-11);
            char[] buffer = message.toCharArray();
            IntByReference lpNumberOfCharsWritten = new IntByReference();
            successful = INSTANCE.WriteConsoleW(handle, buffer, buffer.length, lpNumberOfCharsWritten, null);
        }

        return successful;
    }

}