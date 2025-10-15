# EntraIDEmployeeIdMigration.ps1

## Overview
This PowerShell script updates employee identifiers in Microsoft Entra ID (formerly Azure AD) based on data from a CSV file.

## Features
- Updates Entra ID user accounts with employee identifiers
- Automatically installs required Microsoft Graph modules
- Supports multiple lookup methods (email, UPN, name)
- Generates success and failure reports

## Usage
```powershell
.\EntraIDEmployeeIdMigration.ps1 -FileName "migration.csv"
```

## Parameters
- `FileName`: (Required) Name of the CSV file containing employee data

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
- Microsoft Graph PowerShell module (automatically installed by the script)
- Appropriate Microsoft Entra ID permissions (User.ReadWrite.All)

## User Lookup Process
The script attempts to find users in the following order:
1. By email address
2. By UserPrincipalName (UPN)
3. By first and last name

If multiple users are found with the same name, the script will skip the update to avoid incorrect assignments.

## Authentication
The script connects to Microsoft Graph API with the User.ReadWrite.All scope, which requires appropriate permissions. You will be prompted to authenticate when running the script.

## Error Handling
The script handles various error scenarios:
- User not found in Entra ID
- Multiple users found with the same name
- Authentication issues
- API errors
- Other exceptions during processing

All errors are logged in the FailedEmployeeStatus.csv file with detailed error messages.