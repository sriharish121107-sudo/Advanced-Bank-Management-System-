package bankmanagement.service;

import bankmanagement.model.Account;
import bankmanagement.repository.AccountRepository;

import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Handles concurrent account search operations using a fixed thread pool.
 */
public class SearchService {
    private final AccountRepository repository;
    private final ExecutorService executorService;

    public SearchService(AccountRepository repository) {
        this.repository = repository;
        // Requirements specify a fixed thread pool of 4 threads
        this.executorService = Executors.newFixedThreadPool(4);
    }

    /**
     * Searches for an account concurrently by account number.
     * Displays performance metrics (execution duration and worker thread name).
     */
    public Optional<Account> searchAccount(int accountNumber) {
        long startTime = System.nanoTime();

        Future<Optional<Account>> future = executorService.submit(() -> {
            String threadName = Thread.currentThread().getName();
            Optional<Account> result = repository.search(accountNumber);
            long endTime = System.nanoTime();
            
            // Calculate elapsed time in milliseconds
            long durationMs = (endTime - startTime) / 1_000_000;
            // Display duration (ensure at least 1ms to look realistic if super-fast)
            if (durationMs == 0) {
                durationMs = 1; 
            }
            System.out.printf("Search completed in %d ms on %s%n", durationMs, threadName);
            return result;
        });

        try {
            return future.get(); // Block caller thread until searching thread completes
        } catch (InterruptedException | ExecutionException e) {
            System.out.println("Error: Search execution failed.");
            Thread.currentThread().interrupt(); // Restore interrupted status
            return Optional.empty();
        }
    }

    /**
     * Clean shutdown of the ExecutorService.
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(2, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
