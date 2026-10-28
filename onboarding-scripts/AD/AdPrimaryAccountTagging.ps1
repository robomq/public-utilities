#############################################################################
#############################################################################

<#

    Marks Active Directory user accounts as Primary Accounts from a CSV file.

.DESCRIPTION
    This script reads employee data from a CSV file and updates the specified
    Active Directory attribute with the value "Primary".

    It attempts to locate users by:
      1. Work Email
      2. UserPrincipalName (UPN)
      3. First Name + Last Name

    Successful and failed updates are exported into separate CSV reports.

.PARAMETER FileName
    Name of the CSV file containing employee information.

.PARAMETER PrimaryAttribute
    LDAP display name of the Active Directory attribute that will be used
    as the Primary Account Indicator.

.EXAMPLE
    .\ADPrimaryAccountTagging.ps1 `
        -FileName "employeeList.csv" `
        -PrimaryAttribute "extensionAttribute10"

#>

param(
    [Parameter(Mandatory = $true)]
    [string]$FileName,

    [Parameter(Mandatory = $true)]
    [string]$PrimaryAttribute
)

$PrimaryAttributeValue = "Primary"

#############################################################################
# Validate Active Directory Module
#############################################################################

if (-not (Get-Module -ListAvailable ActiveDirectory)) {
    Write-Host "ERROR: Active Directory PowerShell module is not installed." -ForegroundColor Red
    exit 1
}

Import-Module ActiveDirectory

#############################################################################
# Validate Attribute
#############################################################################

try {

    $schema = (Get-ADRootDSE).schemaNamingContext

    $attributeExists = Get-ADObject `
        -SearchBase $schema `
        -LDAPFilter "(lDAPDisplayName=$PrimaryAttribute)"

    if (-not $attributeExists) {
        Write-Host "ERROR: Attribute '$PrimaryAttribute' does not exist in the Active Directory schema." -ForegroundColor Red
        exit 1
    }
}
catch {

    Write-Host "ERROR: Unable to validate Active Directory attribute '$PrimaryAttribute'."
    Write-Host $_.Exception.Message

    exit 1
}

#############################################################################
# Validate Input File
#############################################################################

$scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path
$filePath = Join-Path $scriptPath $FileName

if (-not (Test-Path $filePath)) {

    Write-Host "ERROR: File '$FileName' not found."

    [PSCustomObject]@{
        Error = "File '$FileName' not found."
    } |
    Export-Csv `
        (Join-Path $scriptPath "FailedEmployeeTaggingStatus.csv") `
        -NoTypeInformation `
        -Encoding UTF8

    exit
}

#############################################################################
# Read CSV
#############################################################################

$EmployeeFileExtract = Import-Csv $filePath

$UpdateResult = @()
$FailedResult = @()

#############################################################################
# Process Records
#############################################################################

foreach ($row in $EmployeeFileExtract) {

    $Mail = $row.WorkEmail
    $FirstName = $row.FirstName
    $LastName = $row.LastName

    Write-Host ""
    Write-Host "Processing:"
    Write-Host "Mail      : $Mail"
    Write-Host "FirstName : $FirstName"
    Write-Host "LastName  : $LastName"

    try {

        $userDetails = $null
        $searchMethod = ""

        #####################################################################
        # Search by Mail
        #####################################################################

        $userDetails = Get-ADUser `
            -LDAPFilter "(mail=$Mail)" `
            -Properties Mail,UserPrincipalName

        $searchMethod = "Mail"

        #####################################################################
        # Search by UserPrincipalName
        #####################################################################

        if (-not $userDetails) {

            $userDetails = Get-ADUser `
                -LDAPFilter "(userPrincipalName=$Mail)" `
                -Properties Mail,UserPrincipalName

            $searchMethod = "UserPrincipalName"
        }

        #####################################################################
        # Search by Name
        #####################################################################

        if (-not $userDetails -and $FirstName -and $LastName) {

            $userDetails = Get-ADUser `
                -Filter "GivenName -eq '$FirstName' -and Surname -eq '$LastName'" `
                -Properties Mail,UserPrincipalName

            $searchMethod = "First+Last Name"
        }

        #####################################################################
        # Multiple users found
        #####################################################################

        if (@($userDetails).Count -gt 1) {

            $row |
                Add-Member `
                -NotePropertyName Error `
                -NotePropertyValue "Multiple users found in Active Directory using $searchMethod." `
                -Force

            $FailedResult += $row

            Write-Host "Multiple users found." -ForegroundColor Yellow

            continue
        }

        #####################################################################
        # Update Primary Attribute
        #####################################################################

        if ($null -ne $userDetails) {

            Set-ADUser `
                -Identity $userDetails.DistinguishedName `
                -Replace @{
                    $PrimaryAttribute = $PrimaryAttributeValue
                }

            $row |
                Add-Member `
                -NotePropertyName Success `
                -NotePropertyValue "Primary Account Indicator updated using $searchMethod." `
                -Force

            $UpdateResult += $row

            Write-Host "Primary Account Indicator updated successfully." -ForegroundColor Green
        }
        else {

            $row |
                Add-Member `
                -NotePropertyName Error `
                -NotePropertyValue "User not found in Active Directory using $searchMethod." `
                -Force

            $FailedResult += $row

            Write-Host "User not found." -ForegroundColor Yellow
        }
    }
    catch {

        $row |
            Add-Member `
            -NotePropertyName Error `
            -NotePropertyValue $_.Exception.Message `
            -Force

        $FailedResult += $row

        Write-Host $_.Exception.Message -ForegroundColor Red
    }
}

#############################################################################
# Export Reports
#############################################################################

$UpdateResult |
Export-Csv `
    (Join-Path $scriptPath "UpdatedEmployeeTaggingStatus.csv") `
    -NoTypeInformation `
    -Encoding UTF8

$FailedResult |
Export-Csv `
    (Join-Path $scriptPath "FailedEmployeeTaggingStatus.csv") `
    -NoTypeInformation `
    -Encoding UTF8

Write-Host ""
Write-Host "Completed."
Write-Host "Successful Updates : $($UpdateResult.Count)"
Write-Host "Failed Updates     : $($FailedResult.Count)"