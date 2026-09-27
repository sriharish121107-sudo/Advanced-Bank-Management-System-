package bankmanagement.service;

import bankmanagement.exception.AccountNotFoundException;
import bankmanagement.exception.InvalidAmountException;
import bankmanagement.exception.InvalidAccountException;
import bankmanagement.model.Account;
import bankmanagement.model.Transaction;
import bankmanagement.repository.AccountRepository;

import java.io.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Business service class containing the core banking features.
 * Manages accounts, executes financial operations, and handles file persistence.
 */
public class AccountService {
    private final AccountRepository repository;
    private final String persistenceFilePath = "accounts.txt";

    public AccountService(AccountRepository repository) {
        this.repository = repository;
    }

    /**
     * Creates a new account, adds it to the repository, and saves to file.
     */
    public synchronized Account createAccount(String name, String type, double openingBalance) throws InvalidAmountException {
        if (openingBalance < 0) {
            throw new InvalidAmountException("Opening balance cannot be negative: ₹" + openingBalance);
        }
        
        try {
            Account newAccount = new Account(name, type, openingBalance);
            // Record the initial opening deposit transaction if balance > 0
            if (openingBalance > 0) {
                Transaction initTx = new Transaction(
                        "TX-INIT-" + generateShortId(),
                        LocalDateTime.now(),
                        "INITIAL_DEP",
                        openingBalance,
                        openingBalance
                );
                newAccount.addTransaction(initTx);
            }
            repository.add(newAccount);
            saveToFile();
            return newAccount;
        } catch (IllegalArgumentException e) {
            throw new InvalidAmountException(e.getMessage());
        }
    }

    /**
     * Updates account holder name and type.
     */
    public synchronized void updateAccount(int accountNumber, String name, String type) throws AccountNotFoundException {
        Optional<Account> accOpt = repository.search(accountNumber);
        if (accOpt.isEmpty()) {
            throw new AccountNotFoundException("Account " + accountNumber + " not found.");
        }
        try {
            repository.update(accountNumber, name, type);
            saveToFile();
        } catch (IllegalArgumentException e) {
            throw new AccountNotFoundException(e.getMessage());
        }
    }

    /**
     * Deletes an account if it exists.
     */
    public synchronized void deleteAccount(int accountNumber) throws AccountNotFoundException {
        Optional<Account> accOpt = repository.search(accountNumber);
        if (accOpt.isEmpty()) {
            throw new AccountNotFoundException("Account " + accountNumber + " not found.");
        }
        repository.delete(accountNumber);
        saveToFile();
    }

    /**
     * Deposits money into an account.
     */
    public void deposit(int accountNumber, double amount) throws AccountNotFoundException, InvalidAmountException {
        if (amount <= 0) {
            throw new InvalidAmountException("Deposit amount must be greater than zero: ₹" + amount);
        }

        Optional<Account> accOpt = repository.search(accountNumber);
        if (accOpt.isEmpty()) {
            throw new AccountNotFoundException("Account " + accountNumber + " not found.");
        }

        Account account = accOpt.get();
        synchronized (account) {
            double oldBalance = account.getBalance();
            double newBalance = oldBalance + amount;
            account.setBalance(newBalance);
            
            Transaction tx = new Transaction(
                    "TX-DEP-" + generateShortId(),
                    LocalDateTime.now(),
                    "DEPOSIT",
                    amount,
                    newBalance
            );
            account.addTransaction(tx);
        }
        saveToFile();
    }

    /**
     * Withdraws money from an account.
     */
    public void withdraw(int accountNumber, double amount) throws AccountNotFoundException, InvalidAmountException {
        if (amount <= 0) {
            throw new InvalidAmountException("Withdrawal amount must be greater than zero: ₹" + amount);
        }

        Optional<Account> accOpt = repository.search(accountNumber);
        if (accOpt.isEmpty()) {
            throw new AccountNotFoundException("Account " + accountNumber + " not found.");
        }

        Account account = accOpt.get();
        synchronized (account) {
            double currentBalance = account.getBalance();
            if (currentBalance < amount) {
                throw new InvalidAmountException("Insufficient balance in Account " + accountNumber 
                        + ". Current Balance: ₹" + String.format("%.2f", currentBalance) 
                        + ", Attempted Withdrawal: ₹" + String.format("%.2f", amount));
            }
            double newBalance = currentBalance - amount;
            account.setBalance(newBalance);

            Transaction tx = new Transaction(
                    "TX-WTH-" + generateShortId(),
                    LocalDateTime.now(),
                    "WITHDRAW",
                    amount,
                    newBalance
            );
            account.addTransaction(tx);
        }
        saveToFile();
    }

    /**
     * Transfers money between two accounts safely using lock ordering to prevent deadlocks.
     */
    public void transfer(int senderAccNum, int receiverAccNum, double amount) 
            throws AccountNotFoundException, InvalidAmountException, InvalidAccountException {
        
        if (senderAccNum == receiverAccNum) {
            throw new InvalidAccountException("Sender and receiver accounts must be different.");
        }
        if (amount <= 0) {
            throw new InvalidAmountException("Transfer amount must be positive: ₹" + amount);
        }

        Optional<Account> senderOpt = repository.search(senderAccNum);
        Optional<Account> receiverOpt = repository.search(receiverAccNum);

        if (senderOpt.isEmpty()) {
            throw new AccountNotFoundException("Sender account " + senderAccNum + " not found.");
        }
        if (receiverOpt.isEmpty()) {
            throw new AccountNotFoundException("Receiver account " + receiverAccNum + " not found.");
        }

        Account sender = senderOpt.get();
        Account receiver = receiverOpt.get();

        // Lock ordering logic to prevent deadlocks:
        // Always lock the account with the smaller account number first.
        Account firstLock = sender.getAccountNumber() < receiver.getAccountNumber() ? sender : receiver;
        Account secondLock = sender.getAccountNumber() < receiver.getAccountNumber() ? receiver : sender;

        synchronized (firstLock) {
            synchronized (secondLock) {
                double senderBalance = sender.getBalance();
                if (senderBalance < amount) {
                    throw new InvalidAmountException("Insufficient balance in Sender Account " + senderAccNum 
                            + ". Current Balance: ₹" + String.format("%.2f", senderBalance) 
                            + ", Transfer Amount: ₹" + String.format("%.2f", amount));
                }

                // Deduct from sender
                double newSenderBalance = senderBalance - amount;
                sender.setBalance(newSenderBalance);

                // Add to receiver
                double newReceiverBalance = receiver.getBalance() + amount;
                receiver.setBalance(newReceiverBalance);

                // Generate shared trace suffix to correlate transactions
                String trace = generateShortId();

                // Create and add transactions
                Transaction senderTx = new Transaction(
                        "TX-TRF-OUT-" + trace,
                        LocalDateTime.now(),
                        "TRANSFER_TO_" + receiverAccNum,
                        amount,
                        newSenderBalance
                );
                sender.addTransaction(senderTx);

                Transaction receiverTx = new Transaction(
                        "TX-TRF-IN-" + trace,
                        LocalDateTime.now(),
                        "TRANSFER_FRM_" + senderAccNum,
                        amount,
                        newReceiverBalance
                );
                receiver.addTransaction(receiverTx);
            }
        }
        saveToFile();
    }

    /**
     * Retrieves all accounts in repository.
     */
    public List<Account> getAllAccounts() {
        return repository.getAll();
    }

    /**
     * Saves the current database state to the file `accounts.txt`.
     */
    public synchronized void saveToFile() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(persistenceFilePath))) {
            for (Account account : repository.getAll()) {
                // Write account properties
                writer.println(String.format("ACC|%d|%s|%s|%.2f", 
                        account.getAccountNumber(), 
                        account.getHolderName(), 
                        account.getAccountType(), 
                        account.getBalance()));
                // Write account transaction history
                for (Transaction tx : account.getTransactionHistory()) {
                    writer.println(String.format("TX|%s|%s|%s|%.2f|%.2f",
                            tx.getTransactionId(),
                            tx.getDateTime().toString(),
                            tx.getType(),
                            tx.getAmount(),
                            tx.getBalanceAfter()));
                }
            }
        } catch (IOException e) {
            System.err.println("Error saving account details to file: " + e.getMessage());
        }
    }

    /**
     * Loads the bank database state from `accounts.txt` on startup.
     */
    public synchronized void loadFromFile() {
        File file = new File(persistenceFilePath);
        if (!file.exists()) {
            return;
        }

        repository.clear();
        Account currentAccount = null;
        int maxAccountNumber = 1000;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                
                String[] parts = line.split("\\|");
                if (parts[0].equals("ACC") && parts.length >= 5) {
                    int accNum = Integer.parseInt(parts[1]);
                    String name = parts[2];
                    String type = parts[3];
                    double balance = Double.parseDouble(parts[4]);

                    currentAccount = new Account(accNum, name, type, balance);
                    repository.add(currentAccount);
                    
                    if (accNum > maxAccountNumber) {
                        maxAccountNumber = accNum;
                    }
                } else if (parts[0].equals("TX") && parts.length >= 6 && currentAccount != null) {
                    String txId = parts[1];
                    LocalDateTime dateTime = LocalDateTime.parse(parts[2]);
                    String type = parts[3];
                    double amount = Double.parseDouble(parts[4]);
                    double balanceAfter = Double.parseDouble(parts[5]);

                    Transaction tx = new Transaction(txId, dateTime, type, amount, balanceAfter);
                    currentAccount.addTransaction(tx);
                }
            }
            
            // Set the auto-increment static counter to prevent ID conflicts
            Account.setNextAccountNumber(maxAccountNumber + 1);
            
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("Warning: Error loading records from file (data might be corrupted). " + e.getMessage());
        }
    }

    /**
     * Generates a short unique ID string.
     */
    private String generateShortId() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
