package bankmanagement.repository;

import bankmanagement.model.Account;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Handles in-memory storage and CRUD operations for bank accounts.
 * Methods are synchronized to make modifications thread-safe.
 */
public class AccountRepository {
    private final List<Account> accounts = new ArrayList<>();

    /**
     * Adds a new account to the repository.
     */
    public synchronized void add(Account account) {
        if (account != null) {
            accounts.add(account);
        }
    }

    /**
     * Searches for an account by its account number.
     * Returns an Optional containing the account if found, or empty if not.
     */
    public synchronized Optional<Account> search(int accountNumber) {
        for (Account account : accounts) {
            if (account.getAccountNumber() == accountNumber) {
                return Optional.of(account);
            }
        }
        return Optional.empty();
    }

    /**
     * Updates an account's mutable details (holder name and account type).
     * Account number and balance cannot be modified via this operation.
     */
    public synchronized boolean update(int accountNumber, String holderName, String accountType) {
        Optional<Account> accountOpt = search(accountNumber);
        if (accountOpt.isPresent()) {
            Account account = accountOpt.get();
            account.setHolderName(holderName);
            account.setAccountType(accountType);
            return true;
        }
        return false;
    }

    /**
     * Deletes an account from the repository by its account number.
     */
    public synchronized boolean delete(int accountNumber) {
        Optional<Account> accountOpt = search(accountNumber);
        if (accountOpt.isPresent()) {
            accounts.remove(accountOpt.get());
            return true;
        }
        return false;
    }

    /**
     * Returns a copy of the list of all accounts.
     * Copying ensures that callers cannot mutate the internal list structure.
     */
    public synchronized List<Account> getAll() {
        return new ArrayList<>(accounts);
    }

    /**
     * Clears all accounts in the repository (primarily used for test cleanup).
     */
    public synchronized void clear() {
        accounts.clear();
    }
}
