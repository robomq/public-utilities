function Get-SasToken {
    param (
        [string]$Namespace,
        [string]$AccessPolicyName,
        [string]$AccessPolicyKey
    )

    # Check if cached token is still valid (with 5-minute buffer)
    if ($script:cachedToken -and $script:tokenExpiry -gt ([DateTime]::UtcNow.AddMinutes(5))) {
        return $script:cachedToken
    }

    try {
        [Reflection.Assembly]::LoadWithPartialName("System.Web") | Out-Null
        $validityInSeconds = 31557600
        $Expires = ([DateTimeOffset]::Now.ToUnixTimeSeconds()) + $validityInSeconds # 1 year validity
        $SignatureString = [System.Web.HttpUtility]::UrlEncode($Namespace) + "`n" + [string]$Expires

        $HMAC = New-Object System.Security.Cryptography.HMACSHA256
        $HMAC.Key = [Text.Encoding]::ASCII.GetBytes($AccessPolicyKey)

        $Signature = $HMAC.ComputeHash([Text.Encoding]::ASCII.GetBytes($SignatureString))
        $Signature = [Convert]::ToBase64String($Signature)

        $script:cachedToken = "SharedAccessSignature sr=" + [System.Web.HttpUtility]::UrlEncode($Namespace) + 
                             "&sig=" + [System.Web.HttpUtility]::UrlEncode($Signature) + 
                             "&se=" + $Expires + "&skn=" + $AccessPolicyName
        $script:tokenExpiry = [DateTime]::UtcNow.AddSeconds($validityInSeconds)

        return $script:cachedToken
    }
    catch {
        Write-Log -Level "ERROR" -Message "Failed to generate SAS token: $_"
        throw
    }
}