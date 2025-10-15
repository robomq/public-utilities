#############################################################################
<#
    Updates employee identifiers in Active Directory from a CSV file.

.DESCRIPTION
    This script reads employee data from a CSV file and updates either the 
    EmployeeID or EmployeeNumber attribute in Active Directory. It attempts 
    to find users by email, UPN, or name, and generates reports of successful 
    and failed updates.

.PARAMETER FileName
    The name of the CSV file containing employee data (must be in the same directory as the script).

.PARAMETER EmployeeID
    Switch parameter to update the EmployeeID attribute (default if neither switch is specified).

.PARAMETER EmployeeNumber
    Switch parameter to update the EmployeeNumber attribute instead of EmployeeID.

.EXAMPLE
    .\AdEmployeeIdMigration.ps1 -FileName "migration.csv"
    Updates EmployeeID attribute for users listed in migration.csv

.EXAMPLE
    .\AdEmployeeIdMigration.ps1 -FileName "migration.csv" -EmployeeNumber
    Updates EmployeeNumber attribute for users listed in migration.csv
#>

param(   
    [string] $FileName,
    [switch] $EmployeeID = $false,       # Default behavior is to update EmployeeID
    [switch] $EmployeeNumber = $false    # Alternative is to update EmployeeNumber
)

# Validate FileName parameter
if (-not $FileName) {
    Write-Host "ERROR: You must provide the CSV file name using the -FileName parameter."
    Write-Host "Example: .\AdEmployeeIdMigration.ps1 -FileName 'migration.csv'"
    "ERROR: You must provide the CSV file name using the -FileName parameter." | Export-Csv (Join-Path $scriptPath "FailedEmployeeStatus.csv") -NoTypeInformation -Encoding UTF8
    exit 1
}

# Define script path (assuming script is run from a known folder)
$scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path
$FilePath   = Join-Path $scriptPath $FileName

# Read user details from the CSV file
$EmployeeFileExtract = Import-Csv -Path $FilePath

# Arrays for tracking results
$UpdateResult = @() # Stores successful updates
$FailedResult = @() # Stores failed updates

# Process each employee record in the CSV
# Check if the file exists
if (-not (Test-Path $FilePath)) {
    Write-Host "ERROR: The file '$FileName' does not exist in the script directory: $scriptPath"
    "ERROR: The file '$FileName' does not exist in the script directory: $scriptPath" | Export-Csv (Join-Path $scriptPath "FailedEmployeeStatus.csv") -NoTypeInformation -Encoding UTF8
    exit 1
}
foreach ($row in $EmployeeFileExtract) {
    
    # Extract CSV fields
    $Mail      = $row."WorkEmail"        # User's email address
    $FirstName = $row."FirstName"        # User's first name
    $LastName  = $row."LastName"         # User's last name
    $employeeIdentifierInCSV = $row."EmployeeIdentifierValue"  # Employee ID to be updated
    
    # Log the current user being processed
    Write-Host "employeeIdentifierInCSV: $employeeIdentifierInCSV, Mail: $Mail, FirstName: $FirstName, LastName: $LastName `n"
    try {
        $userDetails = $null
        $searchMethod = ""

        # Step 1: Find user by Mail (primary search method)
        $userDetails = Get-ADUser -Filter { EmailAddress -eq $Mail } -ErrorAction SilentlyContinue
        $searchMethod = "Mail"

        # Step 2: If not found by Email, try lookup by UPN
        if (-not $userDetails) {
            $userDetails = Get-ADUser -Filter { UserPrincipalName -eq $Mail } -ErrorAction SilentlyContinue
            $searchMethod = "UserPrincipalName"
        }

        # Step 3: If still not found, try lookup by First + Last Name
        # This is a fallback method and may return multiple users
        if (-not $userDetails -and $FirstName -and $LastName) {
            $userDetails = Get-ADUser -Filter {
                GivenName -eq $FirstName -and Surname -eq $LastName
            } -ErrorAction SilentlyContinue
            $searchMethod = "First+Last Name"
        }

        # Handle multiple users case - skip update to avoid incorrect assignments
        if ($userDetails -is [System.Array] -and $userDetails.Count -gt 1) {
            Write-Host "Multiple users found for $FirstName $LastName. Skipping update. `n"
            
            # Record the error in the failed results
            $row | Add-Member -NotePropertyName "Error" -NotePropertyValue "Multiple users found in AD using $searchMethod" -Force
            $FailedResult += $row
            continue
        }

        # If user was found, update the appropriate attribute
        if ($null -ne $userDetails) {
            # Update the AD attribute based on which switch parameter was used
            if ($EmployeeNumber) {
                # Update EmployeeNumber attribute
                Set-ADUser -Identity $userDetails -EmployeeNumber $employeeIdentifierInCSV
                Write-Host "Updated EmployeeNumber for $Mail `n"
                
                # Record success
                $row | Add-Member -NotePropertyName "Success" -NotePropertyValue "EmployeeNumber updated in AD using $searchMethod" -Force
                $UpdateResult += $row
            }
            else {
                # Update EmployeeID attribute (default behavior)
                Set-ADUser -Identity $userDetails -EmployeeID $employeeIdentifierInCSV
                Write-Host "Updated EmployeeId for $Mail `n"
                
                # Record success
                $row | Add-Member -NotePropertyName "Success" -NotePropertyValue "EmployeeID updated in AD using $searchMethod" -Force
                $UpdateResult += $row
            }
        }
        else {
            # User not found in AD after all lookup attempts
            $row | Add-Member -NotePropertyName "Error" -NotePropertyValue "User not found in AD using Mail, UPN, or Name" -Force
            $FailedResult += $row
            Write-Host "User not found in AD (Mail: $Mail, Name: $FirstName $LastName) `n"
        }
    }
    catch {
        # Handle any exceptions that occur during processing
        $row | Add-Member -NotePropertyName "Error" -NotePropertyValue $_.Exception.Message -Force
        $FailedResult += $row
        Write-Host "Failed to update user (Mail: $Mail, Name: $FirstName $LastName)... Error: $_ `n"
    }
}

# Export results to CSV files
$UpdateResult | Export-Csv (Join-Path $scriptPath "UpdatedEmployeeStatus.csv") -NoTypeInformation -Encoding UTF8
$FailedResult | Export-Csv (Join-Path $scriptPath "FailedEmployeeStatus.csv") -NoTypeInformation -Encoding UTF8