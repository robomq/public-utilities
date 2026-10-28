#############################################################################
# ADPrimaryAccountVerification.ps1
#############################################################################

<#
.SYNOPSIS
    Verifies the primary account for users in Active Directory.

.DESCRIPTION
    This script validates that the account specified in the input CSV is the
    correct primary account for an Employee ID.

    For each record, the script:
    1. Searches Active Directory using Employee ID.
    2. If exactly one account exists, verifies the Work Email against:
       - Mail
       - UserPrincipalName (UPN)
    3. If multiple accounts exist, identifies the correct account by matching
       the Work Email against Mail or UPN.
    4. Generates success and failure reports for further processing.

.PARAMETER FileName
    CSV file containing employee records.

.EXAMPLE
    .\ADPrimaryAccountVerification.ps1 -FileName "employeeList.csv"
#>

param(
    [Parameter(Mandatory = $true)]
    [string]$FileName
)

#############################################################################
# Validate Active Directory Module
#############################################################################

if (-not (Get-Module -ListAvailable -Name ActiveDirectory)) {

    Write-Host "ERROR: Active Directory PowerShell module is not installed."
    exit 1
}

Import-Module ActiveDirectory

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
    Export-Csv (Join-Path $scriptPath "FailedToVerifyUsers.csv") -NoTypeInformation

    exit
}

#############################################################################
# Read CSV
#############################################################################

$EmployeeFileExtract = Import-Csv $filePath

$UpdateResult = @()
$FailedResult = @()

#############################################################################
# Helper Function
#############################################################################

function Find-MatchingUser {

    param(
        [array]$Users,
        [string]$Mail
    )

    foreach ($user in $Users) {

        # Match against Mail
        if ($user.Mail -eq $Mail) {

            return @{
                Found = $true
                User = $user
                MatchType = "Mail"
            }
        }

        # Match against User Principal Name
        if ($user.UserPrincipalName -eq $Mail) {

            return @{
                Found = $true
                User = $user
                MatchType = "UserPrincipalName"
            }
        }
    }

    return @{
        Found = $false
    }
}

#############################################################################
# Main Processing
#############################################################################

foreach ($row in $EmployeeFileExtract) {

    $mail = $row.WorkEmail
    $employeeId = $row.EmployeeID

    Write-Host ""
    Write-Host "Processing Employee ID : $employeeId, Work Email : $mail"

    try {

        # Search Active Directory using Employee ID
        $users = Get-ADUser `
            -LDAPFilter "(employeeID=$employeeId)" `
            -Properties Mail,UserPrincipalName,EmployeeID

        # No users found
        if (-not $users) {

            $row |
            Add-Member `
                -NotePropertyName Error `
                -NotePropertyValue "No user found with Employee ID '$employeeId'." `
                -Force

            $FailedResult += $row

            continue
        }

        # Attempt to match Mail or UPN
        $match = Find-MatchingUser `
                    -Users @($users) `
                    -Mail $mail

        if ($match.Found) {

            $row |
            Add-Member `
                -NotePropertyName Success `
                -NotePropertyValue "Matched using $($match.MatchType)." `
                -Force

            $UpdateResult += $row

            Write-Host "Matched using $($match.MatchType)." `
                -ForegroundColor Green
        }
        else {

            $row |
            Add-Member `
                -NotePropertyName Error `
                -NotePropertyValue "User(s) found with Employee ID '$employeeId' but none matched Work Email '$mail' using Mail or UserPrincipalName." `
                -Force

            $FailedResult += $row

            Write-Host "No matching Mail or UPN found." `
                -ForegroundColor Yellow
        }
    }
    catch {

        $row |
        Add-Member `
            -NotePropertyName Error `
            -NotePropertyValue $_.Exception.Message `
            -Force

        $FailedResult += $row

        Write-Host $_.Exception.Message `
            -ForegroundColor Red
    }
}

#############################################################################
# Export Reports
#############################################################################

$UpdateResult |
Export-Csv `
    (Join-Path $scriptPath "SucessfullyVerifiedUsers.csv") `
    -NoTypeInformation `
    -Encoding UTF8

$FailedResult |
Export-Csv `
    (Join-Path $scriptPath "FailedToVerifyUsers.csv") `
    -NoTypeInformation `
    -Encoding UTF8