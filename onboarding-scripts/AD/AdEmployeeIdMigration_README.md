# AdEmployeeIdMigration.ps1

## Overview
This PowerShell script updates employee identifiers (EmployeeID or EmployeeNumber) in Active Directory based on data from a CSV file.

## Features
- Updates AD user accounts with employee identifiers
- Supports multiple lookup methods (email, UPN, name)
- Generates success and failure reports
- Option to update either EmployeeID or EmployeeNumber attribute

## Usage
```powershell
.\AdEmployeeIdMigration.ps1 -FileName "migration.csv" [-EmployeeID] [-EmployeeNumber]
```

## Parameters
- `FileName`: (Required) Name of the CSV file containing employee data
- `EmployeeID`: (Switch) Update the EmployeeID attribute (default)
- `EmployeeNumber`: (Switch) Update the EmployeeNumber attribute instead

## CSV File Format
The script expects a CSV file with the following columns:
- `WorkEmail`: User's email address
- `FirstName`: User's first name
- `LastName`: User's last name
- `EmployeeIdentifierValue`: The employee ID value to be updated

## Output Files
The script generates the following output files:
- `UpdatedEmployeeStatus.csv`: Records of successfully updated users
- `FailedEmployeeStatus.csv`: Records of users that could not be updated, with error details

## Requirements
- Active Directory module for Windows PowerShell
- Appropriate AD permissions to modify user attributes

## User Lookup Process
The script attempts to find users in the following order:
1. By email address
2. By UserPrincipalName (UPN)
3. By first and last name

If multiple users are found with the same name, the script will skip the update to avoid incorrect assignments.

## Error Handling
The script handles various error scenarios:
- User not found in AD
- Multiple users found with the same name
- Permissions issues
- Other exceptions during processing

All errors are logged in the FailedEmployeeStatus.csv file with detailed error messages.