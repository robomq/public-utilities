function Get-Config {
    param (
        [string]$ConfigPath = (Join-Path $scriptPath '/config/settings.json')
    )
    
    if (-not (Test-Path $ConfigPath)) {
        throw "Configuration file not found"
    }
    
    $config = Get-Content $ConfigPath | ConvertFrom-Json
    
    # Decrypt sensitive values if needed
    # Implementation depends on your security requirements
    
    return $config
}