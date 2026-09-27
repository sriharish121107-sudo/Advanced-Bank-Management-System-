package bankmanagement.menu;

import bankmanagement.exception.AccountNotFoundException;
import bankmanagement.exception.InvalidAccountException;
import bankmanagement.exception.InvalidAmountException;
import bankmanagement.model.Account;
import bankmanagement.model.Transaction;
import bankmanagement.service.AccountService;
import bankmanagement.service.SearchService;
import bankmanagement.util.InputValidator;

import java.util.List;
import java.util.Optional;
import java.util.Scanner;

/**
 * Handles terminal UI displays, inputs, and routing of menu choices.
 */
public class MenuHandler {
    private final AccountService accountService;
    private final SearchService searchService;
    private final Scanner scanner;

    public MenuHandler(AccountService accountService, SearchService searchService) {
        this.accountService = accountService;
        this.searchService = searchService;
        this.scanner = new Scanner(System.in);
    }

    /**
     * Starts the interactive command-line interface menu loop.
     */
    public void startMenuLoop() {
        boolean exit = false;
        while (!exit) {
            displayMainMenu();
            
            int choice;
            try {
                choice = InputValidator.readInt(scanner, "Enter choice: ");
            } catch (java.util.NoSuchElementException | IllegalStateException e) {
                System.out.println("Console input is unavailable (running in background).");
                System.out.println("WebServer is kept running. Access the application on http://localhost:8080");
                try {
                    // Keep the main thread alive indefinitely to preserve the background HTTP Server
                    Thread.currentThread().join();
                } catch (InterruptedException ie) {
                    break;
                }
                break;
            }
            System.out.println();
            
            try {
                switch (choice) {
                    case 1:
                        handleAddAccount();
                        break;
                    case 2:
                        handleSearchAccount();
                        break;
                    case 3:
                        handleUpdateAccount();
                        break;
                    case 4:
                        handleDeleteAccount();
                        break;
                    case 5:
                        handleDisplayAllAccounts();
                        break;
                    case 6:
                        handleDeposit();
                        break;
                    case 7:
                        handleWithdraw();
                        break;
                    case 8:
                        handleTransfer();
                        break;
                    case 9:
                        handleTransactionHistory();
                        break;
                    case 10:
                        exit = true;
                        System.out.println("Thank you for using the Bank Management System. Goodbye!");
                        break;
                    default:
                        System.out.println("Error: Invalid choice. Please select between 1 and 10.");
                }
            } catch (Exception e) {
                // Outer safety net to guarantee application never terminates unexpectedly
                System.out.println("Error: An unexpected operational error occurred: " + e.getMessage());
            }
            System.out.println();
        }
        scanner.close();
    }

    private void displayMainMenu() {
        System.out.println("==================================================");
        System.out.println("              BANK MANAGEMENT SYSTEM              ");
        System.out.println("==================================================");
        System.out.println("1. Add Account");
        System.out.println("2. Search Account");
        System.out.println("3. Update Account");
        System.out.println("4. Delete Account");
        System.out.println("5. Display All Accounts");
        System.out.println("6. Deposit Money");
        System.out.println("7. Withdraw Money");
        System.out.println("8. Transfer Money");
        System.out.println("9. Transaction History");
        System.out.println("10. Exit");
        System.out.println("==================================================");
    }

    private void handleAddAccount() {
        System.out.println(">>> Create New Account");
        String name = InputValidator.readString(scanner, "Enter holder name: ");
        String type = InputValidator.readAccountType(scanner, "Enter account type (Savings/Current): ");
        double balance = InputValidator.readAmount(scanner, "Enter opening balance: ₹");

        try {
            Account account = accountService.createAccount(name, type, balance);
            System.out.println("\nSuccess: Account " + account.getAccountNumber() + " created successfully!");
        } catch (InvalidAmountException e) {
            System.out.println("Error: " + e.getMessage());
            System.out.println("Returning to main menu...");
        }
    }

    private void handleSearchAccount() {
        System.out.println(">>> Search Account Record");
        int accNum = InputValidator.readAccountNumber(scanner, "Enter account number: ");
        
        // Executes search via executor thread pool inside SearchService
        Optional<Account> result = searchService.searchAccount(accNum);
        
        if (result.isPresent()) {
            Account acc = result.get();
            System.out.println("\n----------------------------------------");
            System.out.println("            Account Details             ");
            System.out.println("----------------------------------------");
            System.out.printf("Account Number : %d%n", acc.getAccountNumber());
            System.out.printf("Holder Name    : %s%n", acc.getHolderName());
            System.out.printf("Account Type   : %s%n", acc.getAccountType());
            System.out.printf("Balance        : ₹%.2f%n", acc.getBalance());
            System.out.println("----------------------------------------");
        } else {
            System.out.println("\nError: Account " + accNum + " not found.");
            System.out.println("Returning to main menu...");
        }
    }

    private void handleUpdateAccount() {
        System.out.println(">>> Update Account Details");
        int accNum = InputValidator.readAccountNumber(scanner, "Enter account number: ");
        
        // Verify account exists before asking for details
        Optional<Account> checkOpt = searchService.searchAccount(accNum);
        if (checkOpt.isEmpty()) {
            System.out.println("\nError: Account " + accNum + " not found.");
            System.out.println("Returning to main menu...");
            return;
        }

        String name = InputValidator.readString(scanner, "Enter new holder name: ");
        String type = InputValidator.readAccountType(scanner, "Enter new account type (Savings/Current): ");

        try {
            accountService.updateAccount(accNum, name, type);
            System.out.println("\nSuccess: Account " + accNum + " updated successfully.");
        } catch (AccountNotFoundException e) {
            System.out.println("Error: " + e.getMessage());
            System.out.println("Returning to main menu...");
        }
    }

    private void handleDeleteAccount() {
        System.out.println(">>> Delete Account Record");
        int accNum = InputValidator.readAccountNumber(scanner, "Enter account number to delete: ");

        Optional<Account> checkOpt = searchService.searchAccount(accNum);
        if (checkOpt.isEmpty()) {
            System.out.println("\nError: Account " + accNum + " not found.");
            System.out.println("Returning to main menu...");
            return;
        }

        Account acc = checkOpt.get();
        System.out.println("\n--- Confirm Deletion ---");
        System.out.printf("Account No: %d | Holder: %s | Balance: ₹%.2f%n", 
                acc.getAccountNumber(), acc.getHolderName(), acc.getBalance());
        
        String confirmation = InputValidator.readString(scanner, "Are you sure you want to delete this account? (Y/N): ");
        if (confirmation.equalsIgnoreCase("Y")) {
            try {
                accountService.deleteAccount(accNum);
                System.out.println("\nSuccess: Account " + accNum + " deleted successfully.");
            } catch (AccountNotFoundException e) {
                System.out.println("Error: " + e.getMessage());
            }
        } else {
            System.out.println("\nDeletion cancelled. Returning to main menu.");
        }
    }

    private void handleDisplayAllAccounts() {
        System.out.println(">>> Display All Accounts");
        List<Account> accounts = accountService.getAllAccounts();
        if (accounts.isEmpty()) {
            System.out.println("No accounts found in the system.");
            return;
        }

        System.out.println("----------------------------------------------------------------");
        System.out.printf("%-12s  %-20s  %-10s  %12s%n", "Account No", "Holder Name", "Type", "Balance");
        System.out.println("----------------------------------------------------------------");
        for (Account acc : accounts) {
            System.out.printf("%-12d  %-20s  %-10s  ₹%11.2f%n", 
                    acc.getAccountNumber(), 
                    acc.getHolderName(), 
                    acc.getAccountType(), 
                    acc.getBalance());
        }
        System.out.println("----------------------------------------------------------------");
    }

    private void handleDeposit() {
        System.out.println(">>> Deposit Money");
        int accNum = InputValidator.readAccountNumber(scanner, "Enter account number: ");
        double amount = InputValidator.readAmount(scanner, "Enter amount to deposit: ₹");

        try {
            accountService.deposit(accNum, amount);
            System.out.println("\nSuccess: ₹" + String.format("%.2f", amount) 
                    + " deposited successfully into Account " + accNum + ".");
        } catch (AccountNotFoundException | InvalidAmountException e) {
            System.out.println("\nError: " + e.getMessage());
            System.out.println("Returning to main menu...");
        }
    }

    private void handleWithdraw() {
        System.out.println(">>> Withdraw Money");
        int accNum = InputValidator.readAccountNumber(scanner, "Enter account number: ");
        double amount = InputValidator.readAmount(scanner, "Enter amount to withdraw: ₹");

        try {
            accountService.withdraw(accNum, amount);
            System.out.println("\nSuccess: ₹" + String.format("%.2f", amount) 
                    + " withdrawn successfully from Account " + accNum + ".");
        } catch (AccountNotFoundException | InvalidAmountException e) {
            System.out.println("\nError: " + e.getMessage());
            System.out.println("Returning to main menu...");
        }
    }

    private void handleTransfer() {
        System.out.println(">>> Transfer Money");
        int sender = InputValidator.readAccountNumber(scanner, "Sender Account Number: ");
        int receiver = InputValidator.readAccountNumber(scanner, "Receiver Account Number: ");
        double amount = InputValidator.readAmount(scanner, "Transfer Amount: ₹");

        try {
            accountService.transfer(sender, receiver, amount);
            System.out.println("\nSuccess: Transfer successful!");
            System.out.printf("₹%.2f transferred from Account %d to Account %d.%n", amount, sender, receiver);
        } catch (AccountNotFoundException | InvalidAmountException | InvalidAccountException e) {
            System.out.println("\nError: " + e.getMessage());
            System.out.println("Returning to main menu...");
        }
    }

    private void handleTransactionHistory() {
        System.out.println(">>> Transaction History");
        int accNum = InputValidator.readAccountNumber(scanner, "Enter account number: ");

        Optional<Account> searchOpt = searchService.searchAccount(accNum);
        if (searchOpt.isPresent()) {
            Account acc = searchOpt.get();
            List<Transaction> txHistory = acc.getTransactionHistory();
            
            System.out.println("\n===============================================================================");
            System.out.println("            Transaction History for Account: " + accNum + "            ");
            System.out.println("===============================================================================");
            System.out.printf("%-18s  %-19s  %-15s  %10s  %12s%n", 
                    "Transaction ID", "Date & Time", "Type", "Amount", "Balance After");
            System.out.println("===============================================================================");
            if (txHistory.isEmpty()) {
                System.out.println("                     No transactions recorded yet.                     ");
            } else {
                for (Transaction tx : txHistory) {
                    System.out.printf("%-18s  %-19s  %-15s  ₹%9.2f  ₹%11.2f%n", 
                            tx.getTransactionId(), 
                            tx.getFormattedDateTime(), 
                            tx.getType(), 
                            tx.getAmount(), 
                            tx.getBalanceAfter());
                }
            }
            System.out.println("===============================================================================");
        } else {
            System.out.println("\nError: Account " + accNum + " not found.");
            System.out.println("Returning to main menu...");
        }
    }
}
