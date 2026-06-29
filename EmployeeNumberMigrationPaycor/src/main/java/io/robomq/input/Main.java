package io.robomq.input;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/*
java -jar target/EmployeeNumberMigrationPaycor-1.0.jar --input src/main/java/io/robomq/input/employees.csv --output src/main/java/io/robomq/input/output.csv --token eyJhbGciOiJSUzI1NiIsImtpZCI6ImI4Y2NkMDcwN2JlYzkwNWIxYTBmN2Y0ZjZjYzZhY2ZhIiwidHlwIjoiSldUIn0.eyJpc3MiOiJodHRwczovL2FwaS1kZW1vLnBheWNvci5jb20vc3RzL3YyL2NvbW1vbiIsImV4cCI6MTc4MjIxMTM0NiwiaWF0IjoxNzgyMjA5NTQ2LCJuYmYiOjE3ODIyMDU5NDYsImNsaWVudF9pZCI6ImU5OGQxZTM5OWQ3ZTA0MGMwZjk1Iiwic2tleSI6ImRIbG12d0hZSDJfazdQNEl6djNyS1B2ZVc2YTVfNnd4dEJYV0J6R1AyUDQiLCJzaWQiOiIxMzhmZWE5Ny05ZjM5LTRmODctOGVhNy00Njc0YWIyZjk1Y2IiLCJhdXRoX3RpbWUiOjE3ODE1MDI1NTcsInZlciI6MSwic3ViIjoiMGEwMmRkMTctZDk2Zi00M2U4LWIxNWMtNzNlN2VkNjlkZTJkIiwiaWRwIjozLCJwYXljb3JfdXNlcmlkZW50aXR5IjoiMDAwMDAwMDAtMDAwMC0wMDAwLTAwMDAtMDAwMDAwMDAwMDAwIiwicGF5Y29yX3RpbWVvdXQiOjI0MCwicGF5Y29yX2J2Ijoic0RLNmxOMzVQMHFYZmp3V3Joc0dIUSIsInBheWNvcl9wcml2aWxlZ2VzIjoiSDRzSUFBQUFBQUFBQ2wyUU8zTERNQXhFNzZJYUJUNzh1dE40WENlOXh5ZlI2TzU1VUJQUkJVQXN1THNFZUd6Ny90d2U3NWcxVkNYbTlLRWZvZnREOXlwK0tVeW5tQ2xSaUVaME1UZHhMeEtWNkYyS1Zxa1dNcnJMbUZNbWZxWU5vcmFSaWt1QzFpOHhhcEpuaWt4WEQ1NDdUM21nOVVoS0pDV1NVdk8yVWRVeUJhZEI2cG96ZEFabGkrUGt5SE5qWHFZdU5rcDM3aGg5aFdXRmJZRnVkNGo5SGJMcEhiTDBIYkw3YXVYZk9MN3c0cFkvOEkvWjVzWG5IK2Y1Qno3a3BmdW1BUUFBIiwicGF5Y29yX3ppcCI6WyJwYXljb3JfcHJpdmlsZWdlcyIsInBheWNvcl9jbGllbnRzIl0sIm5hbWUiOiJUZXN0aW5nIEludGVncmF0aW9uIiwiZ2l2ZW5fbmFtZSI6IlRlc3RpbmciLCJmYW1pbHlfbmFtZSI6IkludGVncmF0aW9uIiwiZW1haWwiOiJJVC1JbnRlZ3JhdGlvbnNTdXBwb3J0RExAcGF5Y29yLmNvbSIsImVtYWlsX3ZlcmlmaWVkIjp0cnVlLCJwYXljb3JfY2xpZW50cyI6Ikg0c0lBQUFBQUFBQUNxdFdjbGF5aXE1V2NzN0pUTTByOFV4UnNqSzJORFUyTU5CUmNzN1BMVWpNcXdRSm1SaGFtSmdiNlNpNTVoYms1RmVtcG9abUFnWHpTbk55UUtyeVNoS1RTNUJGQ2hMUkJXdDFVQzJ3TkxLZ3NnV3h0UUEwclBvRXlRQUFBQT09IiwiaXBhZGRyIjoiMTI4LjE4NS4xNzcuMjYiLCJqdGkiOiI4NTM2NWZiODg0MDE3ZmZhZDMxNzJjNjFhOTYwMGYzZCIsInNjb3BlIjpbIjhkYzcwZmUwZDc0YWYxMTE4ZWYzNjA0NWJkNGMyYWViIiwib2ZmbGluZV9hY2Nlc3MiXSwiYW1yIjpbInB3ZCIsIm1mYSJdLCJhdWQiOiJlOThkMWUzOTlkN2UwNDBjMGY5NSJ9.hWrDsG9hVWIndpPzzUqTZhr122wPqOVwWauUa0gA08NujeCQfbWrct4EDFrRJ4uQu93QGamO39xdjDqUTQs-DTsc_dFt6oNpJ8qWpVt_e4Vz-mF_S2bYf06RV0uhTxGOlVQgwfdMVJF-lYawT4fPMmXCt93csU7YkBPO8wHCaUdPVkb41Gh54zYenIXrV_25vR2S-GzO7SUzycWd3GUTxzgzVbxU9ROIlyBCCnBNysB7GTayxa0Zi0oJ8ux5-hmVERaky-01wGW01R7Am_0iw7jz2K0eJO_bo2fx-6jHkt0dzpV0s4EjpVHa1b8yR6XdMdiCoLOkqw45Xmqbt1GoTQ --subscription-key 4758329438cf4c7e97206fe25960f12f --legal-entities 395300 --base-url https://apis-sandbox.paycor.com
 */

/**
 * Employee Number Migration utility.
 *
 * <p>This application reads a CSV file containing employee numbers, retrieves
 * employee information from the Paycor Employees API for one or more legal
 * entities, and exports matching employees to a CSV file.
 *
 * <p>The output file contains:
 * <ul>
 *     <li>Legal Entity ID</li>
 *     <li>Person ID</li>
 *     <li>Hashed Person ID</li>
 *     <li>Employee ID</li>
 *     <li>Employee Number</li>
 *     <li>Work Email</li>
 * </ul>
 *
 * <p>Command-line arguments:
 * <pre>
 * --input
 * --output
 * --token
 * --subscription-key
 * --legal-entities
 * --base-url
 * </pre>
 */
public class Main {
//    private static String inputFile = "/Users/priyanshulakhwani/Desktop/EmployeeNumberMigrationPaycor/src/main/java/io/robomq/input/employees.csv";
//    private static String outputFile = "/Users/priyanshulakhwani/Desktop/EmployeeNumberMigrationPaycor/src/main/java/io/robomq/input/employee_export.csv";
//    private static String accessToken = "eyJhbGciOiJSUzI1NiIsImtpZCI6ImI4Y2NkMDcwN2JlYzkwNWIxYTBmN2Y0ZjZjYzZhY2ZhIiwidHlwIjoiSldUIn0.eyJpc3MiOiJodHRwczovL2FwaS1kZW1vLnBheWNvci5jb20vc3RzL3YyL2NvbW1vbiIsImV4cCI6MTc4MjIwMDYwNCwiaWF0IjoxNzgyMTk4ODA0LCJuYmYiOjE3ODIxOTUyMDQsImNsaWVudF9pZCI6ImU5OGQxZTM5OWQ3ZTA0MGMwZjk1Iiwic2tleSI6ImRIbG12d0hZSDJfazdQNEl6djNyS1B2ZVc2YTVfNnd4dEJYV0J6R1AyUDQiLCJzaWQiOiIxMzhmZWE5Ny05ZjM5LTRmODctOGVhNy00Njc0YWIyZjk1Y2IiLCJhdXRoX3RpbWUiOjE3ODE1MDI1NTcsInZlciI6MSwic3ViIjoiMGEwMmRkMTctZDk2Zi00M2U4LWIxNWMtNzNlN2VkNjlkZTJkIiwiaWRwIjozLCJwYXljb3JfdXNlcmlkZW50aXR5IjoiMDAwMDAwMDAtMDAwMC0wMDAwLTAwMDAtMDAwMDAwMDAwMDAwIiwicGF5Y29yX3RpbWVvdXQiOjI0MCwicGF5Y29yX2J2Ijoic0RLNmxOMzVQMHFYZmp3V3Joc0dIUSIsInBheWNvcl9wcml2aWxlZ2VzIjoiSDRzSUFBQUFBQUFBQ2wyUU8zTERNQXhFNzZJYUJUNzh1dE40WENlOXh5ZlI2TzU1VUJQUkJVQXN1THNFZUd6Ny90d2U3NWcxVkNYbTlLRWZvZnREOXlwK0tVeW5tQ2xSaUVaME1UZHhMeEtWNkYyS1Zxa1dNcnJMbUZNbWZxWU5vcmFSaWt1QzFpOHhhcEpuaWt4WEQ1NDdUM21nOVVoS0pDV1NVdk8yVWRVeUJhZEI2cG96ZEFabGkrUGt5SE5qWHFZdU5rcDM3aGg5aFdXRmJZRnVkNGo5SGJMcEhiTDBIYkw3YXVYZk9MN3c0cFkvOEkvWjVzWG5IK2Y1Qno3a3BmdW1BUUFBIiwicGF5Y29yX3ppcCI6WyJwYXljb3JfcHJpdmlsZWdlcyIsInBheWNvcl9jbGllbnRzIl0sIm5hbWUiOiJUZXN0aW5nIEludGVncmF0aW9uIiwiZ2l2ZW5fbmFtZSI6IlRlc3RpbmciLCJmYW1pbHlfbmFtZSI6IkludGVncmF0aW9uIiwiZW1haWwiOiJJVC1JbnRlZ3JhdGlvbnNTdXBwb3J0RExAcGF5Y29yLmNvbSIsImVtYWlsX3ZlcmlmaWVkIjp0cnVlLCJwYXljb3JfY2xpZW50cyI6Ikg0c0lBQUFBQUFBQUNxdFdjbGF5aXE1V2NzN0pUTTByOFV4UnNqSzJORFUyTU5CUmNzN1BMVWpNcXdRSm1SaGFtSmdiNlNpNTVoYms1RmVtcG9abUFnWHpTbk55UUtyeVNoS1RTNUJGQ2hMUkJXdDFVQzJ3TkxLZ3NnV3h0UUEwclBvRXlRQUFBQT09IiwiaXBhZGRyIjoiMTI4LjE4NS4xNzcuMjYiLCJqdGkiOiJmNTIwY2Q2OGNkNmFmODk4NTNjMzEzNzIzNzNlZTA4OSIsInNjb3BlIjpbIjhkYzcwZmUwZDc0YWYxMTE4ZWYzNjA0NWJkNGMyYWViIiwib2ZmbGluZV9hY2Nlc3MiXSwiYW1yIjpbInB3ZCIsIm1mYSJdLCJhdWQiOiJlOThkMWUzOTlkN2UwNDBjMGY5NSJ9.BZk20w2-hO0qT1aZH_o2RYewTXQaFRodS5yOnEAWlRnb-qF1IsjSmUyzM9sxv8r-fnuyNV8kU6bElGd7BH4lzAzRw4EcR9Tw3yLuO0Cc6l0sWkQWDTO-4N7Bah4trBSFsIbwDD7QXh8-nernbYjCz1T-WCSUkUEjmwo9bLimlAlYixW4p9Y6-gTS2D0ozVzI6WASIZQRjTtoJlwKJAgBRUU_Cu9lf4LfQe5SgWQ7PdQaCs1RaMnScNu7MZQiDFFbvaoHqgW0kI4upESCGI7kyeZ6TFL9XpGgY9ZTAlPfRWKnll7a89Nnq_6DHCokLqWKXyHKR7YBeaLlnsU-zE1Ucw";
//    private static String employeeUrl = "https://apis-sandbox.paycor.com/v2/legalentities/%s/employees?include=All";
//    private static String subscriptionKey = "4758329438cf4c7e97206fe25960f12f";
//    private static List<String> legalEntities = new ArrayList<>(List.of("395300"));

    private String inputFile = "";
    private String outputFile = "";
    private String accessToken = "";
    private String employeeUrl = "";
    private String subscriptionKey = "";
    private List<String> legalEntities = new ArrayList<>(List.of("395300"));

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();
    private final Logger logger = Logger.getLogger(Main.class.getName());

    /**
     * Application entry point.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        try {
            new Main().run(args);
        } catch (Exception e) {
            Logger.getLogger(Main.class.getName())
                    .log(Level.SEVERE, "Application failed", e);
        }
    }

    private void run(String[] args) throws Exception {
        init(args);
        Set<String> employeeNumbers = loadEmployeeNumbers(inputFile);
        logger.log(Level.INFO, "Loaded {0} employee numbers.", employeeNumbers.size());
        logger.log(Level.INFO, "Found {0} legal entities.", legalEntities.size());
        exportEmployees(employeeNumbers);
        logger.log(Level.INFO, "Output written to {0}", outputFile);
    }

    /**
     * Initializes application configuration from command-line arguments.
     *
     * @param args command-line arguments
     * @throws IllegalArgumentException if a required argument is missing
     */
    private void init(String[] args) {
        Map<String, String> params = parseArgs(args);

        inputFile = require(params, "input");
        outputFile = require(params, "output");
        accessToken = require(params, "token");
        subscriptionKey = require(params, "subscription-key");
        String baseUrl = require(params, "base-url");

        employeeUrl = baseUrl + "/v2/legalentities/%s/employees?include=All";

        legalEntities =
                Arrays.stream(
                                require(params, "legal-entities").split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();
    }

    /**
     * Reads employee numbers from the input CSV file.
     *
     * <p>The first row is treated as the header and ignored.
     * Blank lines are skipped.
     *
     * @param csvFile input CSV file
     * @return unique employee numbers
     * @throws IOException if the file cannot be read
     * @throws EmployeeException if the file contents are invalid
     */
    private Set<String> loadEmployeeNumbers(String csvFile) throws EmployeeException, IOException {
        Set<String> employeeNumbers = new HashSet<>();
        List<String> lines = Files.readAllLines(Paths.get(csvFile));
        boolean header = true;

        for (String line : lines) {
            if (header) {
                header = false;
            } else if (!line.trim().isEmpty()) {
                String[] columns = line.split(",", -1);
                employeeNumbers.add(columns[0].trim());
            }
        }

        return employeeNumbers;
    }

    /**
     * Retrieves employee information from Paycor and exports matching employees
     * to the output CSV.
     *
     * @param employeeNumbers employee numbers to search for
     * @throws Exception if an API or file operation fails
     */
    private void exportEmployees(Set<String> employeeNumbers) throws Exception {
        Set<String> remainingEmployees = new HashSet<>(employeeNumbers);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {

            writer.write("Legal Entity ID," + "Person ID," + "Hashed Person ID," + "Employee ID," + "Employee Number," + "Work Email");
            writer.newLine();

            for (String legalEntityId : legalEntities) {
                if (remainingEmployees.isEmpty()) {
                    logger.log(Level.INFO, "All employees found.");
                    break;
                }

                logger.log(Level.INFO, "Processing Legal Entity: {0}", legalEntityId);

                String nextUrl = String.format(employeeUrl, legalEntityId);

                while (nextUrl != null) {
                    nextUrl = processCurrentResponse(nextUrl, writer, remainingEmployees, legalEntityId);
                }
            }

            if (!remainingEmployees.isEmpty()) {

                logger.log(Level.INFO, "\nEmployees not found:");

                remainingEmployees.forEach(logger::info
                );
            }
        }
    }

    /**
     * Processes a single page of employee records returned by the API.
     *
     * <p>Matching employees are written to the output file and removed from the
     * remaining employee list.
     *
     * @param nextUrl API endpoint to process
     * @param writer CSV writer
     * @param remainingEmployees employee numbers still to be found
     * @param legalEntityId legal entity being processed
     * @return next page URL, or {@code null} if there are no additional pages
     * @throws Exception if an API or I/O error occurs
     */
    private String processCurrentResponse(String nextUrl, BufferedWriter writer,
                                                 Set<String> remainingEmployees, String legalEntityId) throws Exception {
        JsonNode root = executeGet(nextUrl);
        JsonNode records = root.path("records");
        for (JsonNode employee : records) {
            String employeeNumber = employee.path("employeeNumber").asText("");
            if (!remainingEmployees.contains(employeeNumber)) {
                continue;
            }
            String employeeId = employee.path("id").asText("");
            String personId = employee.path("person").path("id").asText("");
            String email = "";
            JsonNode emailNode = employee.path("email");

            if (!emailNode.isMissingNode() && !emailNode.isNull()) {
                String emailType =
                        emailNode.path("type")
                                .asText("");

                if ("Work".equalsIgnoreCase(emailType)) {
                    email =
                            emailNode.path("emailAddress")
                                    .asText("");
                }
            }
            writer.write(csv(legalEntityId) + "," + csv(personId) + "," + csv(hashPersonID(personId)) + "," + csv(employeeId) + "," + csv(employeeNumber) + "," + csv(email));
            writer.newLine();
            remainingEmployees.remove(employeeNumber);
            logger.log(Level.INFO, "Found employee {0}", employeeNumber);
        }
        boolean hasMoreResults = root.path("hasMoreResults").asBoolean(false);

        if (hasMoreResults) {
            return root.path("additionalResultsUrl").asText(null);
        } else {
            return null;
        }
    }

    /**
     * Executes an HTTP GET request against the Paycor API.
     *
     * @param url endpoint URL
     * @return parsed JSON response
     * @throws Exception if the request fails or returns a non-200 response
     */
    private JsonNode executeGet(String url) throws Exception {

        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .header("Ocp-Apim-Subscription-Key", subscriptionKey)
                .GET().build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {

            throw new EmployeeException("API Call Failed\n" + "URL: " + url + "\nStatus: " + response.statusCode() + "\nResponse: " + response.body());
        }

        return mapper.readTree(response.body());
    }

    /**
     * Escapes a value for CSV output.
     *
     * @param value value to escape
     * @return CSV-safe value enclosed in quotes
     */
    private String csv(String value) {

        if (value == null) {
            return "";
        }

        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    /**
     * Generates a SHA-256 hash of a person ID and returns the first
     * ten hexadecimal characters.
     *
     * @param input person ID
     * @return hashed identifier
     */
    private String hashPersonID(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }

            return sb.substring(0, 10);
        } catch (NoSuchAlgorithmException e) {
            logger.log(Level.INFO, "SHA-256 algorithm not available {0}", e.getMessage());
            return "";
        }
    }

    /**
     * Parses command-line arguments into key/value pairs.
     *
     * @param args command-line arguments
     * @return parsed argument map
     * @throws IllegalArgumentException if an argument is malformed
     */
    private Map<String, String> parseArgs(String[] args) {

        Map<String, String> params = new HashMap<>();

        for (int i = 0; i < args.length; i += 2) {

            if (!args[i].startsWith("--")) {
                throw new IllegalArgumentException(
                        "Invalid argument: " + args[i]);
            }

            if (i + 1 >= args.length) {
                throw new IllegalArgumentException(
                        "Missing value for " + args[i]);
            }

            params.put(
                    args[i].substring(2),
                    args[i + 1]);
        }

        return params;
    }

    /**
     * Retrieves a required command-line parameter.
     *
     * @param params parsed parameters
     * @param key parameter name
     * @return parameter value
     * @throws IllegalArgumentException if the parameter is missing or blank
     */
    private String require(Map<String, String> params, String key) {
        String value = params.get(key);

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Missing required parameter --" + key);
        }
        return value;
    }

}