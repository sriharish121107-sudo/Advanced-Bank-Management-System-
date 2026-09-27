package bankmanagement.web;

import bankmanagement.model.Account;
import bankmanagement.model.Transaction;
import bankmanagement.service.AccountService;
import bankmanagement.service.SearchService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;

/**
 * Embedded HTTP server utilizing built-in JDK HttpServer APIs.
 * Hosts REST APIs and serves static frontend dashboard components (HTML, CSS, JS) on port 8080.
 */
public class WebServer {
    private final HttpServer server;
    private final AccountService accountService;
    private final SearchService searchService;

    public WebServer(AccountService accountService, SearchService searchService, int port) throws IOException {
        this.accountService = accountService;
        this.searchService = searchService;
        
        // Initialize HTTP Server on specified port
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        
        // Setup Routing context handlers
        server.createContext("/", new StaticFileHandler());
        server.createContext("/api/accounts", new ApiHandler(accountService, searchService));
        
        // Assign thread executor pool for serving requests concurrently
        server.setExecutor(Executors.newFixedThreadPool(8));
    }

    public void start() {
        server.start();
        System.out.println("Web Server running at http://localhost:8080");
    }

    public void stop() {
        server.stop(1);
        System.out.println("Web Server stopped.");
    }

    /**
     * Handler to serve static frontend dashboard files (HTML, CSS, JS).
     */
    private static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            
            // Default index route routing
            if (path.equals("/")) {
                path = "/index.html";
            }

            File file = new File("web" + path);
            if (!file.exists() || file.isDirectory()) {
                // Return 404 page
                String response = "404 Not Found";
                exchange.sendResponseHeaders(404, response.length());
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
                return;
            }

            // Determine mime type
            String contentType = "text/plain";
            if (path.endsWith(".html")) contentType = "text/html; charset=utf-8";
            else if (path.endsWith(".css")) contentType = "text/css";
            else if (path.endsWith(".js")) contentType = "text/javascript";

            byte[] fileBytes = Files.readAllBytes(file.toPath());
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, fileBytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(fileBytes);
            os.close();
        }
    }

    /**
     * Handler for bank operation JSON APIs.
     */
    private static class ApiHandler implements HttpHandler {
        private final AccountService accountService;
        private final SearchService searchService;

        public ApiHandler(AccountService accountService, SearchService searchService) {
            this.accountService = accountService;
            this.searchService = searchService;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Enable CORS headers for development/testing
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

            // Handle preflight CORS OPTIONS requests
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();

            String response = "";
            int statusCode = 200;

            try {
                if (path.equals("/api/accounts") && method.equals("GET")) {
                    // Fetch all accounts
                    List<Account> accounts = accountService.getAllAccounts();
                    response = accountsListToJson(accounts);
                } else if (path.equals("/api/accounts") && method.equals("POST")) {
                    // Add new account
                    String body = readBody(exchange);
                    String name = extractJsonField(body, "holderName");
                    String type = extractJsonField(body, "accountType");
                    String balanceStr = extractJsonField(body, "openingBalance");

                    if (name == null || type == null || balanceStr == null) {
                        throw new IllegalArgumentException("Missing fields: holderName, accountType, or openingBalance.");
                    }

                    double balance = Double.parseDouble(balanceStr);
                    Account account = accountService.createAccount(name, type, balance);
                    response = String.format("{\"success\":true,\"message\":\"Account %d created successfully.\",\"accountNumber\":%d}", 
                            account.getAccountNumber(), account.getAccountNumber());
                } else if (path.equals("/api/accounts/search") && method.equals("GET")) {
                    // Search account
                    String query = exchange.getRequestURI().getQuery();
                    int accNum = parseQueryParam(query, "accountNumber");
                    Optional<Account> accOpt = searchService.searchAccount(accNum);

                    if (accOpt.isPresent()) {
                        response = accountToJson(accOpt.get());
                    } else {
                        statusCode = 404;
                        response = String.format("{\"error\":\"Account %d not found.\"}", accNum);
                    }
                } else if (path.equals("/api/accounts/update") && method.equals("PUT")) {
                    // Update account
                    String body = readBody(exchange);
                    String accNumStr = extractJsonField(body, "accountNumber");
                    String name = extractJsonField(body, "holderName");
                    String type = extractJsonField(body, "accountType");

                    if (accNumStr == null || name == null || type == null) {
                        throw new IllegalArgumentException("Missing fields: accountNumber, holderName, or accountType.");
                    }

                    int accNum = Integer.parseInt(accNumStr);
                    accountService.updateAccount(accNum, name, type);
                    response = "{\"success\":true,\"message\":\"Account updated successfully.\"}";
                } else if (path.equals("/api/accounts/delete") && method.equals("DELETE")) {
                    // Delete account
                    String query = exchange.getRequestURI().getQuery();
                    int accNum = parseQueryParam(query, "accountNumber");
                    accountService.deleteAccount(accNum);
                    response = "{\"success\":true,\"message\":\"Account deleted successfully.\"}";
                } else if (path.equals("/api/accounts/deposit") && method.equals("POST")) {
                    // Deposit
                    String body = readBody(exchange);
                    String accNumStr = extractJsonField(body, "accountNumber");
                    String amountStr = extractJsonField(body, "amount");

                    if (accNumStr == null || amountStr == null) {
                        throw new IllegalArgumentException("Missing fields: accountNumber or amount.");
                    }

                    int accNum = Integer.parseInt(accNumStr);
                    double amount = Double.parseDouble(amountStr);
                    accountService.deposit(accNum, amount);
                    response = "{\"success\":true,\"message\":\"Amount deposited successfully.\"}";
                } else if (path.equals("/api/accounts/withdraw") && method.equals("POST")) {
                    // Withdraw
                    String body = readBody(exchange);
                    String accNumStr = extractJsonField(body, "accountNumber");
                    String amountStr = extractJsonField(body, "amount");

                    if (accNumStr == null || amountStr == null) {
                        throw new IllegalArgumentException("Missing fields: accountNumber or amount.");
                    }

                    int accNum = Integer.parseInt(accNumStr);
                    double amount = Double.parseDouble(amountStr);
                    accountService.withdraw(accNum, amount);
                    response = "{\"success\":true,\"message\":\"Amount withdrawn successfully.\"}";
                } else if (path.equals("/api/accounts/transfer") && method.equals("POST")) {
                    // Transfer
                    String body = readBody(exchange);
                    String senderStr = extractJsonField(body, "senderAccountNumber");
                    String receiverStr = extractJsonField(body, "receiverAccountNumber");
                    String amountStr = extractJsonField(body, "amount");

                    if (senderStr == null || receiverStr == null || amountStr == null) {
                        throw new IllegalArgumentException("Missing fields: senderAccountNumber, receiverAccountNumber, or amount.");
                    }

                    int sender = Integer.parseInt(senderStr);
                    int receiver = Integer.parseInt(receiverStr);
                    double amount = Double.parseDouble(amountStr);
                    accountService.transfer(sender, receiver, amount);
                    response = "{\"success\":true,\"message\":\"Money transferred successfully.\"}";
                } else if (path.equals("/api/accounts/history") && method.equals("GET")) {
                    // Transaction history
                    String query = exchange.getRequestURI().getQuery();
                    int accNum = parseQueryParam(query, "accountNumber");
                    Optional<Account> accOpt = searchService.searchAccount(accNum);

                    if (accOpt.isPresent()) {
                        response = transactionsListToJson(accOpt.get().getTransactionHistory());
                    } else {
                        statusCode = 404;
                        response = String.format("{\"error\":\"Account %d not found.\"}", accNum);
                    }
                } else if (path.equals("/api/logs") && method.equals("GET")) {
                    // Fetch latest logs
                    response = getRecentLogsJson("snapshot.log", 15);
                } else {
                    statusCode = 404;
                    response = "{\"error\":\"API route not found.\"}";
                }
            } catch (IllegalArgumentException e) {
                statusCode = 400;
                response = String.format("{\"error\":\"%s\"}", escapeJson(e.getMessage()));
            } catch (Exception e) {
                statusCode = 500;
                response = String.format("{\"error\":\"%s\"}", escapeJson(e.getMessage()));
            }

            // Write output stream
            byte[] respBytes = response.getBytes("UTF-8");
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(statusCode, respBytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(respBytes);
            os.close();
        }

        // --- JSON Processing utilities to avoid external libraries ---

        private static String readBody(HttpExchange exchange) throws IOException {
            InputStream is = exchange.getRequestBody();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len;
            while ((len = is.read(buffer)) != -1) {
                bos.write(buffer, 0, len);
            }
            return bos.toString("UTF-8");
        }

        private static int parseQueryParam(String query, String name) {
            if (query == null) throw new IllegalArgumentException("Query parameters are missing.");
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                String[] parts = pair.split("=");
                if (parts.length == 2 && parts[0].equalsIgnoreCase(name)) {
                    return Integer.parseInt(parts[1]);
                }
            }
            throw new IllegalArgumentException("Query parameter '" + name + "' is missing.");
        }

        private static String extractJsonField(String json, String field) {
            String pattern = "\"" + field + "\"";
            int index = json.indexOf(pattern);
            if (index == -1) return null;

            int colonIdx = json.indexOf(":", index + pattern.length());
            if (colonIdx == -1) return null;

            int valueStart = colonIdx + 1;
            while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
                valueStart++;
            }

            if (valueStart >= json.length()) return null;

            if (json.charAt(valueStart) == '"') {
                int valueEnd = json.indexOf("\"", valueStart + 1);
                if (valueEnd == -1) return null;
                return json.substring(valueStart + 1, valueEnd);
            } else {
                int valueEnd = valueStart;
                while (valueEnd < json.length() && (Character.isDigit(json.charAt(valueEnd)) 
                        || json.charAt(valueEnd) == '.' 
                        || json.charAt(valueEnd) == '-' 
                        || json.charAt(valueEnd) == '+')) {
                    valueEnd++;
                }
                return json.substring(valueStart, valueEnd);
            }
        }

        private static String escapeJson(String s) {
            if (s == null) return "";
            return s.replace("\\", "\\\\").replace("\"", "\\\"");
        }

        private static String accountToJson(Account acc) {
            return String.format("{\"accountNumber\":%d,\"holderName\":\"%s\",\"accountType\":\"%s\",\"balance\":%.2f,\"transactionCount\":%d}",
                    acc.getAccountNumber(),
                    escapeJson(acc.getHolderName()),
                    acc.getAccountType(),
                    acc.getBalance(),
                    acc.getTransactionHistory().size());
        }

        private static String transactionToJson(Transaction tx) {
            return String.format("{\"transactionId\":\"%s\",\"dateTime\":\"%s\",\"type\":\"%s\",\"amount\":%.2f,\"balanceAfter\":%.2f}",
                    tx.getTransactionId(),
                    tx.getFormattedDateTime(),
                    tx.getType(),
                    tx.getAmount(),
                    tx.getBalanceAfter());
        }

        private static String accountsListToJson(List<Account> list) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                sb.append(accountToJson(list.get(i)));
                if (i < list.size() - 1) sb.append(",");
            }
            sb.append("]");
            return sb.toString();
        }

        private static String transactionsListToJson(List<Transaction> list) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                sb.append(transactionToJson(list.get(i)));
                if (i < list.size() - 1) sb.append(",");
            }
            sb.append("]");
            return sb.toString();
        }

        private static String getRecentLogsJson(String filename, int limit) {
            File file = new File(filename);
            if (!file.exists()) {
                return "[]";
            }

            List<String> lines = new java.util.ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
            } catch (IOException e) {
                return "[\"Error reading logs: " + escapeJson(e.getMessage()) + "\"]";
            }

            int start = Math.max(0, lines.size() - limit);
            StringBuilder sb = new StringBuilder("[");
            for (int i = start; i < lines.size(); i++) {
                sb.append("\"").append(escapeJson(lines.get(i))).append("\"");
                if (i < lines.size() - 1) {
                    sb.append(",");
                }
            }
            sb.append("]");
            return sb.toString();
        }
    }
}
