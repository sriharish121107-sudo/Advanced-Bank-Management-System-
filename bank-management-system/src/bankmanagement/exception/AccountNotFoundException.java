package bankmanagement.exception;

/**
 * Custom exception thrown when a requested bank account cannot be found in the system.
 */
public class AccountNotFoundException extends Exception {
    public AccountNotFoundException(String message) {
        super(message);
    }
}
