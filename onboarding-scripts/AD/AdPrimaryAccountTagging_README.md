# ADPrimaryAccountTagging.ps1

## Overview

This PowerShell script updates Active Directory user accounts by assigning a Primary Account Indicator using a customer-specified Active Directory attribute.

The script is intended to support Hire2Retire's manager assignment feature for organizations that maintain multiple accounts associated with the same Employee ID. By identifying a user's primary account, Hire2Retire can correctly resolve manager relationships when multiple accounts are returned during manager lookup.

## Features

* Updates a customer-specified Active Directory attribute
* Marks user accounts as Primary Accounts
* Supports multiple lookup methods (email, UPN, name)
* Generates success and failure reports
* Prevents updates when multiple matching accounts are found

## Usage

```powershell
.\ADPrimaryAccountTagging.ps1 -FileName "report.csv" -PrimaryAttribute "extensionAttribute10"
```

## Parameters

| Parameter | Required | Description |
|----------|----------|-------------|
| FileName | Yes | Name of the CSV file containing user data |
| PrimaryAttribute | Yes | Active Directory attribute that will be used as the Primary Account Indicator |

## CSV File Format

The script expects a CSV file with the following columns:

| Column | Description |
|----------|-------------|
| WorkEmail | User's email address |
| FirstName | User's first name |
| LastName | User's last name |

### Example

```csv
WorkEmail,FirstName,LastName
max.fosh@contoso.com,Max,Fosh
john.stones@contoso.com,John,Stones
```

## Primary Account Indicator Configuration

The script updates the specified Active Directory attribute with the following value:

| Attribute | Value |
|----------|-------|
| Customer Specified | Primary |

The attribute to be updated is provided using the **PrimaryAttribute** parameter when executing the script.

This designation identifies the account as the employee's primary account for manager resolution purposes.

## Output Files

### UpdatedEmployeeTaggingStatus.csv

Contains users that were successfully updated with the Primary Account Indicator.

### FailedEmployeeTaggingStatus.csv

Contains users that could not be updated along with the corresponding error details.

## Requirements

### Active Directory PowerShell Module

The Active Directory PowerShell module must be installed on the machine where the script is executed.

### Active Directory Permissions

The account executing the script must have:

* Read access to Active Directory users.
* Permission to update the specified Active Directory attribute.

### Active Directory Attribute Prerequisites

Before running this script:

* The attribute specified using the **PrimaryAttribute** parameter must exist in the Active Directory schema.
* The executing account must have permission to modify the attribute.

If the attribute does not exist or cannot be modified, the update operation will fail.

## User Lookup Process

The script attempts to locate users in the following order:

1. Work Email
2. UserPrincipalName (UPN)
3. First Name and Last Name

This approach helps maximize the likelihood of locating the correct user account.

## Duplicate User Handling

If multiple users are returned for a lookup:

* The account is not updated.
* The record is written to **FailedEmployeeTaggingStatus.csv**.
* Manual review is required.

This prevents accidental assignment of the Primary Account Indicator to the wrong account.

## Authentication

The script uses the credentials of the currently logged-in Windows user.

No additional authentication is required provided the executing account has sufficient permissions to read and update Active Directory.

## Error Handling

The script handles and reports the following scenarios:

* User not found in Active Directory
* Multiple matching users found
* Insufficient permissions
* Invalid or non-existent Active Directory attribute
* Active Directory update failures
* Other unexpected exceptions

All failures are captured in **FailedEmployeeTaggingStatus.csv** with detailed error information.

## Relationship to Hire2Retire

This script is intended to support the Hire2Retire feature:

**Manager Assignment When Multiple Accounts Exist for the Same Employee ID**

When multiple accounts are associated with a manager's Employee ID, Hire2Retire uses the configured Primary Account Indicator to identify the correct account for manager assignment.

For existing Active Directory environments, this script can be used to backfill the Primary Account Indicator on existing user accounts, enabling successful manager resolution without requiring account consolidation.