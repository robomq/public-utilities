# EntraIDPrimaryAccountTagging.ps1

## Overview

This PowerShell script updates Microsoft Entra ID (formerly Azure AD) user accounts by assigning a Primary Account Indicator using Custom Security Attributes.

The script is intended to support Hire2Retire's manager assignment feature for organizations that maintain multiple accounts associated with the same Employee ID. By identifying a user's primary account, Hire2Retire can correctly resolve manager relationships when multiple accounts are returned during manager lookup.

## Features

* Updates Microsoft Entra ID Custom Security Attributes
* Marks user accounts as Primary Accounts
* Automatically installs required Microsoft Graph modules
* Supports multiple lookup methods (email, UPN, name)
* Generates success and failure reports
* Prevents updates when multiple matching accounts are found

## Usage

```powershell
.\EntraIDPrimaryAccountTagging.ps1 -FileName "report.csv"
```

## Parameters

| Parameter | Required | Description                               |
| --------- | -------- | ----------------------------------------- |
| FileName  | Yes      | Name of the CSV file containing user data |

## CSV File Format

The script expects a CSV file with the following columns:

| Column    | Description          |
| --------- | -------------------- |
| WorkEmail | User's email address |
| FirstName | User's first name    |
| LastName  | User's last name     |

### Example

```csv
WorkEmail,FirstName,LastName
max.fosh@contoso.com,Max,Fosh
john.stones@contoso.com,John,Stones
```

## Custom Security Attribute Configuration

The script assigns the following Custom Security Attribute:

| Attribute Set  | Attribute Name | Value   |
| -------------- | -------------- | ------- |
| Hire2RetireSet | UserAccount    | Primary |

This designation identifies the account as the employee's primary account for manager resolution purposes.

## Output Files

### UpdatedEmployeeTaggingStatus.csv

Contains users that were successfully updated with the Primary Account Indicator.

### FailedEmployeeTaggingStatus.csv

Contains users that could not be updated along with the corresponding error details.

## Requirements

### Microsoft Graph PowerShell Module

The script automatically installs the required Microsoft Graph PowerShell modules if they are not already installed.

### Microsoft Entra ID Permissions

The authenticated account must have the following Microsoft Graph permissions:

* User.Read.All
* CustomSecAttributeAssignment.ReadWrite.All

Additionally, the account must have sufficient privileges to update Custom Security Attributes in Microsoft Entra ID.

### Custom Security Attribute Prerequisites

Before running this script, the following Custom Security Attribute must already exist in Microsoft Entra ID:

| Attribute Set  | Attribute Name |
| -------------- | -------------- |
| Hire2RetireSet | UserAccount    |

If the attribute set or attribute definition does not exist, the update operation will fail.

## User Lookup Process

The script attempts to locate users in the following order:

1. Work Email
2. UserPrincipalName (UPN)
3. First Name and Last Name

This approach helps maximize the likelihood of locating the correct user account.

## Duplicate User Handling

If multiple users are returned for a lookup:

* The account is not updated.
* The record is written to FailedEmployeeStatus.csv.
* Manual review is required.

This prevents accidental assignment of the Primary Account Indicator to the wrong account.

## Authentication

The script authenticates to Microsoft Graph using delegated permissions.

When executed, users will be prompted to sign in and consent to the required Microsoft Graph permissions if they have not previously been granted.

## Error Handling

The script handles and reports the following scenarios:

* User not found in Microsoft Entra ID
* Multiple matching users found
* Authentication failures
* Microsoft Graph API errors
* Custom Security Attribute update failures
* Other unexpected exceptions

All failures are captured in FailedEmployeeStatus.csv with detailed error information.

## Relationship to Hire2Retire

This script is intended to support the Hire2Retire feature:

**Manager Assignment When Multiple Accounts Exist for the Same Employee ID**

When multiple accounts are associated with a manager's Employee ID, Hire2Retire uses the configured Primary Account Indicator to identify the correct account for manager assignment.

For existing environments, this script can be used to backfill the Primary Account Indicator on existing user accounts, enabling successful manager resolution without requiring account consolidation.