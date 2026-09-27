package bankmanagement.util;

import java.util.Scanner;

/**
 * Utility class for reading and validating command-line inputs.
 * Reads all input as Strings first to prevent Scanner-blocking exceptions.
 */
public class InputValidator {

    /**
     * Reads a valid integer from the console.
     */
    public static int readInt(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            try {
                return Integer.parseInt(input);
            } catch (NumberFormatException e) {
                System.out.println("Error: Invalid number. Please enter a valid integer.");
            }
        }
    }

    /**
     * Reads a valid double from the console.
     */
    public static double readDouble(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            try {
                return Double.parseDouble(input);
            } catch (NumberFormatException e) {
                System.out.println("Error: Invalid decimal value. Please enter a valid number.");
            }
        }
    }

    /**
     * Reads a non-blank string from the console.
     */
    public static String readString(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) {
                System.out.println("Error: Input cannot be empty. Please try again.");
            } else {
                return input;
            }
        }
    }

    /**
     * Reads and validates an account number.
     */
    public static int readAccountNumber(Scanner scanner, String prompt) {
        while (true) {
            int accNum = readInt(scanner, prompt);
            if (accNum < 1000) {
                System.out.println("Error: Account number must be at least 1000.");
            } else {
                return accNum;
            }
        }
    }

    /**
     * Reads and validates an amount to ensure it is positive.
     */
    public static double readAmount(Scanner scanner, String prompt) {
        while (true) {
            double amount = readDouble(scanner, prompt);
            if (amount <= 0) {
                System.out.println("Error: Amount must be greater than zero.");
            } else {
                return amount;
            }
        }
    }

    /**
     * Reads and validates account types ('Savings' or 'Current').
     */
    public static String readAccountType(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            if (input.equalsIgnoreCase("Savings") || input.equalsIgnoreCase("Current")) {
                // Capitalize first letter (e.g. "Savings" or "Current")
                return input.substring(0, 1).toUpperCase() + input.substring(1).toLowerCase();
            } else {
                System.out.println("Error: Invalid account type. Must enter 'Savings' or 'Current'.");
            }
        }
    }
}
