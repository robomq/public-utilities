# Employee Number Migration Utility

A Java command-line application that exports employee information from the Paycor Employees API for a list of employee numbers.

## Features

- Reads employee numbers from a CSV file
- Supports multiple legal entities
- Retrieves employee information using the Paycor Employees API
- Handles paginated API responses
- Exports matching employees to CSV
- Generates a SHA-256 hash of the Person ID
- Logs application progress using `java.util.logging`

---

## Requirements

- Java 17 or later
- Maven
- Paycor API access
- Valid OAuth Access Token
- Subscription Key

---

## Build

``` bash
  mvn clean package
```

The generated JAR will be located in:

```
target/EmployeeNumberMigrationPaycor-1.0.jar
```

---

## Input CSV

The input CSV should contain employee numbers in the first column.

Example:

```csv
EmployeeNumber
1001
1002
1003
```

Only the first column is used.

---

## Usage

```bash
  java -jar target/EmployeeNumberMigrationPaycor-1.0.jar \
  --input data/input.csv \
  --output data/output.csv \
  --token <ACCESS_TOKEN> \
  --subscription-key <SUBSCRIPTION_KEY> \
  --legal-entities 395300,395301 \
  --base-url https://apis-sandbox.paycor.com
```

---

## Command Line Arguments

| Argument | Description | Required |
|----------|-------------|----------|
| `--input` | Input CSV containing employee numbers | Yes |
| `--output` | Output CSV file | Yes |
| `--token` | OAuth Bearer Token | Yes |
| `--subscription-key` | Paycor Subscription Key | Yes |
| `--legal-entities` | Comma-separated list of Legal Entity IDs | Yes |
| `--base-url` | Paycor API base URL | Yes |

---

## Output CSV

The application generates a CSV with the following columns:

| Column |
|---------|
| Legal Entity ID |
| Person ID |
| Hashed Person ID |
| Employee ID |
| Employee Number |
| Work Email |

Example:

```csv
Legal Entity ID,Person ID,Hashed Person ID,Employee ID,Employee Number,Work Email
395300,12345,af823a6d21,56789,E1001,john.doe@company.com
```

---

## Logging

The application logs:

- Number of employee numbers loaded
- Legal entities being processed
- Employees found
- Employees not found
- Output file location

---

## Error Handling

The application throws an exception when:

- Required command-line arguments are missing
- The input file cannot be read
- The Paycor API returns a non-200 response
- JSON parsing fails
- File writing fails

---

## Hashing

The `Hashed Person ID` column contains the first **10 hexadecimal characters** of the SHA-256 hash of the Person ID.

---

## Project Structure

```
src/
└── main/
    └── java/
        └── io/
            └── robomq/
                └── input/
                    ├── Main.java
                    └── EmployeeException.java
```

---

## Notes

- The application searches all specified legal entities until every employee number is found or all entities have been processed.
- Duplicate employee numbers in the input file are ignored.
- Empty lines in the input CSV are skipped.
- API pagination is handled automatically.