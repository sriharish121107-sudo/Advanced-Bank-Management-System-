package bankmanagement.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Represents a single transaction on a bank account.
 */
public class Transaction {
    private final String transactionId;
    private final LocalDateTime dateTime;
    private final String type;
    private final double amount;
    private final double balanceAfter;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public Transaction(String transactionId, LocalDateTime dateTime, String type, double amount, double balanceAfter) {
        this.transactionId = transactionId;
        this.dateTime = dateTime;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public LocalDateTime getDateTime() {
        return dateTime;
    }

    public String getType() {
        return type;
    }

    public double getAmount() {
        return amount;
    }

    public double getBalanceAfter() {
        return balanceAfter;
    }

    public String getFormattedDateTime() {
        return dateTime.format(FORMATTER);
    }

    @Override
    public String toString() {
        return String.format("%s | %s | %-12s | %8.2f | %8.2f", 
                transactionId, getFormattedDateTime(), type, amount, balanceAfter);
    }
}
