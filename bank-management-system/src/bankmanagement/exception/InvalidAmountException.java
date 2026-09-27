package bankmanagement.exception;

/**
 * Custom exception thrown when a transaction amount is invalid (e.g., non-positive or insufficient balance).
 */
public class InvalidAmountException extends Exception {
    public InvalidAmountException(String message) {
        super(message);
    }
}
