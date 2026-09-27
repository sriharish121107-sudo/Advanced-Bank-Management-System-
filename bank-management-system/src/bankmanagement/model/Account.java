package bankmanagement.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a customer bank account.
 */
public class Account {
    private final int accountNumber;
    private String holderName;
    private String accountType; // "Savings" or "Current"
    private double balance;
    private final List<Transaction> transactionHistory;

    // Static counter to generate account numbers automatically
    private static int nextAccountNumber = 1001;

    /**
     * Constructor for creating a brand new account (auto-generates account number).
     */
    public Account(String holderName, String accountType, double balance) {
        validateHolderName(holderName);
        validateAccountType(accountType);
        validateBalance(balance);

        this.accountNumber = nextAccountNumber++;
        this.holderName = holderName;
        this.accountType = accountType;
        this.balance = balance;
        this.transactionHistory = new ArrayList<>();
    }

    /**
     * Constructor used when loading existing accounts from file persistence.
     */
    public Account(int accountNumber, String holderName, String accountType, double balance) {
        validateHolderName(holderName);
        validateAccountType(accountType);
        // Balance validation is omitted here to allow loaded historical states, but we still ensure it is valid
        if (balance < 0) {
            throw new IllegalArgumentException("Balance cannot be negative");
        }

        this.accountNumber = accountNumber;
        this.holderName = holderName;
        this.accountType = accountType;
        this.balance = balance;
        this.transactionHistory = new ArrayList<>();
    }

    // Getters and Setters demonstrating encapsulation
    public int getAccountNumber() {
        return accountNumber;
    }

    public String getHolderName() {
        return holderName;
    }

    public synchronized void setHolderName(String holderName) {
        validateHolderName(holderName);
        this.holderName = holderName;
    }

    public String getAccountType() {
        return accountType;
    }

    public synchronized void setAccountType(String accountType) {
        validateAccountType(accountType);
        this.accountType = accountType;
    }

    public synchronized double getBalance() {
        return balance;
    }

    public synchronized void setBalance(double balance) {
        if (balance < 0) {
            throw new IllegalArgumentException("Balance cannot be negative");
        }
        this.balance = balance;
    }

    public synchronized List<Transaction> getTransactionHistory() {
        // Return an unmodifiable view to preserve encapsulation
        return Collections.unmodifiableList(new ArrayList<>(transactionHistory));
    }

    public synchronized void addTransaction(Transaction transaction) {
        if (transaction != null) {
            this.transactionHistory.add(transaction);
        }
    }

    // Static helper to update the next account number when loading persistence files
    public static synchronized void setNextAccountNumber(int nextNumber) {
        if (nextNumber > nextAccountNumber) {
            nextAccountNumber = nextNumber;
        }
    }

    public static synchronized int getNextAccountNumber() {
        return nextAccountNumber;
    }

    // Validation helper methods
    private void validateHolderName(String holderName) {
        if (holderName == null || holderName.trim().isEmpty()) {
            throw new IllegalArgumentException("Holder name cannot be empty or null");
        }
    }

    private void validateAccountType(String accountType) {
        if (accountType == null || (!accountType.equalsIgnoreCase("Savings") && !accountType.equalsIgnoreCase("Current"))) {
            throw new IllegalArgumentException("Account type must be 'Savings' or 'Current'");
        }
    }

    private void validateBalance(double balance) {
        if (balance < 0) {
            throw new IllegalArgumentException("Opening balance cannot be negative");
        }
    }

    @Override
    public String toString() {
        return String.format("Account Number: %d | Holder: %s | Type: %s | Balance: %.2f",
                accountNumber, holderName, accountType, balance);
    }
}
