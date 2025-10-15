#############################################################################
#############################################################################

<#

    Updates employee identifiers in Microsoft Entra ID from a CSV file.

.DESCRIPTION
    This script reads employee data from a CSV file and updates the EmployeeID 
    attribute in Microsoft Entra ID (formerly Azure AD). It attempts to find users 
    by email, UPN, or name, and generates reports of successful and failed updates.
    The script automatically installs required Microsoft Graph modules if needed.

.PARAMETER FileName
    The name of the CSV file containing employee data (must be in the same directory as the script).

.EXAMPLE
    .\EntraIDEmployeeIdMigration.ps1 -FileName "migration.csv"
    Updates EmployeeID attribute for users listed in migration.csv
#>

param(   
    [string] $FileName  # Name of the CSV file containing employee data
)



# Ensure Microsoft Graph module is installed
if (-not (Get-Module -ListAvailable -Name Microsoft.Graph.Users)) {
    Write-Host "Installing Microsoft Graph module..."
    Install-Module Microsoft.Graph -Scope CurrentUser -Force
}

# Set execution policy to allow running scripts
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser

# Connect to Microsoft Graph with appropriate permissions
# User.ReadWrite.All is required to update user properties
Connect-MgGraph -Scopes "User.ReadWrite.All"


# Validate FileName parameter
if (-not $FileName) {
    Write-Host "ERROR: You must provide the CSV file name using the -FileName parameter."
    Write-Host "Example: .\EntraIDEmployeeIdMigration.ps1 -FileName 'migration.csv'"
    "ERROR: You must provide the CSV file name using the -FileName parameter." | Export-Csv (Join-Path $scriptPath "FailedEmployeeStatus.csv") -NoTypeInformation -Encoding UTF8
    exit 1
}
# Define script path and file path
$scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path
$FilePath   = Join-Path $scriptPath $FileName

# Check if the file exists
if (-not (Test-Path $FilePath)) {
    Write-Host "ERROR: The file '$FileName' does not exist in the script directory: $scriptPath"
   "ERROR: The file '$FileName' does not exist in the script directory: $scriptPath" | Export-Csv (Join-Path $scriptPath "FailedEmployeeStatus.csv") -NoTypeInformation -Encoding UTF8
    exit 1
}
# Read user details from the CSV file
$EmployeeFileExtract = Import-Csv -Path $FilePath

# Arrays for tracking results
$UpdateResult = @() # Stores successful updates
$FailedResult = @() # Stores failed updates

# Process each employee record in the CSV
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

        # Step 1: Try lookup by Work Email (primary search method)
        # Using Microsoft Graph API to query Entra ID
        $userDetails = Get-MgUser -Filter "Mail eq '$Mail'" -ConsistencyLevel eventual -CountVariable count
        $searchMethod = "Mail"

        # Step 2: If not found by Email, try lookup by UPN
        if (-not $userDetails) {
            $userDetails = Get-MgUser -Filter "UserPrincipalName eq '$Mail'" -ConsistencyLevel eventual -CountVariable count
            $searchMethod = "UserPrincipalName"
        }

        # Step 3: If still not found, try lookup by First + Last Name
        # This is a fallback method and may return multiple users
        if (-not $userDetails -and $FirstName -and $LastName) {
            $filter = "givenName eq '$FirstName' and surname eq '$LastName'"
            $userDetails = Get-MgUser -Filter $filter -ConsistencyLevel eventual -CountVariable count
            $searchMethod = "First+Last Name"
        }

        # Handle multiple users case - skip update to avoid incorrect assignments
        if ($count -gt 1) {
            Write-Host "Multiple users found for $FirstName $LastName. Skipping update. `n"
            
            # Record the error in the failed results
            $row | Add-Member -NotePropertyName "Error" -NotePropertyValue "Multiple users found in Entra ID using $searchMethod" -Force
            $FailedResult += $row
            continue
        }

        # If user was found, update the employeeId attribute
        if ($null -ne $userDetails) {
            # Create parameter object for Microsoft Graph API
            $updateBody = @{
                employeeId = $employeeIdentifierInCSV
            }

            # Update the user in Entra ID using Microsoft Graph API
            Set-MgUser -UserId $userDetails.Id -BodyParameter $updateBody
            
            # Record success
            $row | Add-Member -NotePropertyName "Success" -NotePropertyValue "User updated in Entra ID using $searchMethod" -Force
            $UpdateResult += $row
        }
        else {
            # User not found in Entra ID after all lookup attempts
            $row | Add-Member -NotePropertyName "Error" -NotePropertyValue "User not found in Entra ID using $searchMethod" -Force
            $FailedResult += $row
            Write-Host "User not found in Entra ID using $searchMethod (Mail: $Mail, Name: $FirstName $LastName) `n"
        }
    }
    catch {
        # Handle any exceptions that occur during processing
        $row | Add-Member -NotePropertyName "Error" -NotePropertyValue $_.Exception.Message -Force
        $FailedResult += $row
        Write-Host "Failed to update user (Mail: $Mail, Name: $FirstName $LastName)... Error: $_ `n"
    }
}

# Export results to CSV files for reporting and auditing
$UpdateResult | Export-Csv (Join-Path $scriptPath "UpdatedEmployeeStatus.csv") -NoTypeInformation -Encoding UTF8
$FailedResult | Export-Csv (Join-Path $scriptPath "FailedEmployeeStatus.csv") -NoTypeInformation -Encoding UTF8