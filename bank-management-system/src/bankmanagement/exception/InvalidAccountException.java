package bankmanagement.exception;

/**
 * Custom exception thrown when there is an operation involving an invalid account configuration 
 * (e.g., trying to transfer money to the same account).
 */
public class InvalidAccountException extends Exception {
    public InvalidAccountException(String message) {
        super(message);
    }
}
