package bankmanagement;

import bankmanagement.menu.MenuHandler;
import bankmanagement.model.Account;
import bankmanagement.repository.AccountRepository;
import bankmanagement.service.AccountService;
import bankmanagement.service.SearchService;
import bankmanagement.web.WebServer;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Main entry point of the Bank Management System.
 * Sets up persistence, starts the background logging daemon, boots the Web Server, 
 * and executes the menu runner.
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("Initializing Bank Management System...");

        // 1. Initialize Components
        AccountRepository repository = new AccountRepository();
        AccountService accountService = new AccountService(repository);
        SearchService searchService = new SearchService(repository);

        // 2. Load historical state from accounts.txt
        accountService.loadFromFile();
        System.out.println("Historical account records loaded successfully.");

        // 3. Start background logging daemon thread
        Thread daemonThread = new Thread(() -> {
            while (true) {
                try {
                    // Periodic log interval: 30 seconds
                    Thread.sleep(30000);
                    writeSnapshotLog(accountService);
                } catch (InterruptedException e) {
                    // Exit cleanly if daemon is interrupted
                    break;
                }
            }
        });
        daemonThread.setDaemon(true); // Designates this thread as a daemon
        daemonThread.setName("AccountSnapshotDaemon");
        daemonThread.start();

        // Write an initial snapshot immediately for instant feedback
        writeSnapshotLog(accountService);

        // 4. Boot Web Server on port 8080
        WebServer webServer = null;
        try {
            webServer = new WebServer(accountService, searchService, 8080);
            webServer.start();
            System.out.println(">>> INTERACTIVE WEB PORTAL RUNNING AT: http://localhost:8080 <<<");
        } catch (IOException e) {
            System.err.println("Warning: Could not start Web Server on port 8080: " + e.getMessage());
        }

        // 5. Launch terminal user interface (main thread blocks here)
        MenuHandler menuHandler = new MenuHandler(accountService, searchService);
        menuHandler.startMenuLoop();

        // 6. Clean resource shutdown on Exit
        System.out.println("Stopping background services...");
        if (webServer != null) {
            webServer.stop();
        }
        System.out.println("Shutting down worker threads...");
        searchService.shutdown();
        System.out.println("Shutdown complete. Exiting.");
    }

    /**
     * Utility method to write in-memory state snapshots to a background log file.
     */
    private static void writeSnapshotLog(AccountService accountService) {
        try (PrintWriter writer = new PrintWriter(new FileWriter("snapshot.log", true))) {
            writer.println("========================================================================");
            writer.println("ACCOUNT SNAPSHOT - TIMESTAMP: " + LocalDateTime.now());
            writer.println("========================================================================");
            List<Account> accounts = accountService.getAllAccounts();
            if (accounts.isEmpty()) {
                writer.println("No account records found in system.");
            } else {
                writer.printf("%-12s | %-20s | %-12s | %12s%n", "Account No", "Holder Name", "Account Type", "Balance");
                writer.println("------------------------------------------------------------------------");
                for (Account acc : accounts) {
                    writer.printf("%-12d | %-20s | %-12s | ₹%11.2f%n",
                            acc.getAccountNumber(),
                            acc.getHolderName(),
                            acc.getAccountType(),
                            acc.getBalance());
                }
            }
            writer.println("========================================================================\n");
            writer.flush();
        } catch (IOException e) {
            // Background daemon logging failure; fail silently so it doesn't disrupt user's console UI
        }
    }
}
