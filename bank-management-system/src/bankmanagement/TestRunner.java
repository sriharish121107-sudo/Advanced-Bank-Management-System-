package bankmanagement;

import bankmanagement.exception.AccountNotFoundException;
import bankmanagement.exception.InvalidAccountException;
import bankmanagement.exception.InvalidAmountException;
import bankmanagement.model.Account;
import bankmanagement.model.Transaction;
import bankmanagement.repository.AccountRepository;
import bankmanagement.service.AccountService;
import bankmanagement.service.SearchService;

import java.util.List;
import java.util.Optional;

/**
 * Automated test suite to verify all core functional requirements of the Bank Management System.
 */
public class TestRunner {
    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("        STARTING AUTOMATED TEST SUITE             ");
        System.out.println("==================================================");

        int passed = 0;
        int failed = 0;

        try {
            // Test 1: Account Creation & Auto-Incrementing Numbers
            System.out.print("Test 1: Account Creation & Numbering... ");
            AccountRepository repository = new AccountRepository();
            AccountService service = new AccountService(repository);
            
            // Set starting counter to 1001
            Account.setNextAccountNumber(1001);

            Account acc1 = service.createAccount("Harish", "Savings", 5000.00);
            Account acc2 = service.createAccount("Akshay", "Current", 8500.00);

            assertEqual(1001, acc1.getAccountNumber(), "First account number should be 1001");
            assertEqual(1002, acc2.getAccountNumber(), "Second account number should be 1002");
            assertEqual("Harish", acc1.getHolderName(), "Holder name should be Harish");
            assertEqual("Savings", acc1.getAccountType(), "Type should be Savings");
            assertEqual(5000.00, acc1.getBalance(), "Balance should be 5000.00");
            System.out.println("PASSED");
            passed++;

            // Test 2: In-Memory Search
            System.out.print("Test 2: Search Account... ");
            SearchService searchService = new SearchService(repository);
            Optional<Account> searchResult = searchService.searchAccount(1001);
            assertTrue(searchResult.isPresent(), "Account 1001 should be found");
            assertEqual("Harish", searchResult.get().getHolderName(), "Account 1001 holder should be Harish");
            
            Optional<Account> searchResultNotFound = searchService.searchAccount(9999);
            assertTrue(searchResultNotFound.isEmpty(), "Account 9999 should not exist");
            System.out.println("PASSED");
            passed++;

            // Test 3: Account Updating
            System.out.print("Test 3: Update Account Details... ");
            service.updateAccount(1001, "Harish Kumar", "Current");
            Account updatedAcc = repository.search(1001).orElseThrow();
            assertEqual("Harish Kumar", updatedAcc.getHolderName(), "Holder name should be updated to Harish Kumar");
            assertEqual("Current", updatedAcc.getAccountType(), "Account type should be updated to Current");
            System.out.println("PASSED");
            passed++;

            // Test 4: Financial Transactions (Deposit & Withdraw)
            System.out.print("Test 4: Deposit and Withdraw Operations... ");
            service.deposit(1001, 1500.00);
            assertEqual(6500.00, updatedAcc.getBalance(), "Balance after 1500 deposit should be 6500.00");

            service.withdraw(1001, 2000.00);
            assertEqual(4500.00, updatedAcc.getBalance(), "Balance after 2000 withdrawal should be 4500.00");
            
            // Verify transaction history sizes
            List<Transaction> txHistory = updatedAcc.getTransactionHistory();
            // Initial deposit (5000), Deposit (1500), Withdraw (2000) = 3 transactions
            assertEqual(3, txHistory.size(), "Transaction history should contain 3 entries");
            System.out.println("PASSED");
            passed++;

            // Test 5: Money Transfer
            System.out.print("Test 5: Money Transfer between Accounts... ");
            // Sender: 1001 (bal: 4500)
            // Receiver: 1002 (bal: 8500)
            service.transfer(1001, 1002, 1000.00);
            assertEqual(3500.00, repository.search(1001).orElseThrow().getBalance(), "Sender balance should be 3500.00");
            assertEqual(9500.00, repository.search(1002).orElseThrow().getBalance(), "Receiver balance should be 9500.00");
            System.out.println("PASSED");
            passed++;

            // Test 6: Custom Exception Handling Scenarios
            System.out.print("Test 6: Exception Validation Rules... ");
            
            // Case A: Insufficient balance
            try {
                service.withdraw(1001, 4000.00);
                System.out.println("FAILED (Allowed overdraft without exception)");
                failed++;
            } catch (InvalidAmountException e) {
                // Expected
                assertTrue(e.getMessage().contains("Insufficient balance"), "Error msg should mention insufficient balance");
            }

            // Case B: Non-existent account withdrawal
            try {
                service.withdraw(9999, 100.00);
                System.out.println("FAILED (Allowed operations on non-existent account)");
                failed++;
            } catch (AccountNotFoundException e) {
                // Expected
            }

            // Case C: Invalid deposit amount (negative)
            try {
                service.deposit(1001, -50.00);
                System.out.println("FAILED (Allowed negative deposit)");
                failed++;
            } catch (InvalidAmountException e) {
                // Expected
            }

            // Case D: Self transfer
            try {
                service.transfer(1001, 1001, 50.0);
                System.out.println("FAILED (Allowed self transfer)");
                failed++;
            } catch (InvalidAccountException e) {
                // Expected
            }

            System.out.println("PASSED");
            passed++;

            // Test 7: Deletion of Account
            System.out.print("Test 7: Account Deletion... ");
            service.deleteAccount(1001);
            assertTrue(repository.search(1001).isEmpty(), "Account 1001 should be deleted");
            System.out.println("PASSED");
            passed++;

            // Cleanup & Shutdown SearchService Executor
            searchService.shutdown();

        } catch (Exception e) {
            System.out.println("FAILED with unexpected exception:");
            e.printStackTrace();
            failed++;
        }

        System.out.println("==================================================");
        System.out.printf("Test Executions Finished: %d PASSED, %d FAILED%n", passed, failed);
        System.out.println("==================================================");

        if (failed > 0) {
            System.exit(1);
        } else {
            System.exit(0);
        }
    }

    private static void assertEqual(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(String.format("%s: Expected [%s] but got [%s]", message, expected, actual));
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
